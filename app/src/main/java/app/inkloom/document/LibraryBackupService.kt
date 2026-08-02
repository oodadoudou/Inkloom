package app.inkloom.document

import app.inkloom.model.ImportedBrush
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class LibraryRestoreResult(
    val documents: List<InkDocument>,
    val folders: List<InkFolder>,
    val brushes: List<ImportedBrush>,
)

/**
 * A portable, versioned backup of the entire user library. Existing content is
 * never overwritten on restore: documents, folders and brush IDs are remapped
 * into a dedicated restored folder while immutable assets are content-addressed.
 */
class LibraryBackupService(private val filesRoot: File) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun export(
        documents: List<InkDocument>,
        folders: List<InkFolder>,
        brushes: List<ImportedBrush>,
        output: OutputStream,
    ) {
        require(documents.size <= MAX_DOCUMENTS) { "Library has too many documents to back up" }
        require(folders.size <= MAX_FOLDERS) { "Library has too many folders to back up" }
        require(brushes.size <= MAX_BRUSHES) { "Library has too many brushes to back up" }
        val assets = linkedMapOf<String, File>()
        fun register(path: String?, folder: String): String? {
            if (path.isNullOrBlank()) return null
            val source = File(path)
            require(source.isFile) { "Backup asset is missing: ${source.name}" }
            require(source.length() <= MAX_SINGLE_ASSET_BYTES) { "Backup asset is too large: ${source.name}" }
            val extension = source.extension.lowercase().filter(Char::isLetterOrDigit).take(12)
            val entry = "$folder/${sha256(source)}${extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()}"
            assets.putIfAbsent(entry, source)
            return entry
        }

        val portableBrushes = brushes.map { brush ->
            brush.copy(
                sourcePath = register(brush.sourcePath, BRUSH_SOURCES),
                tipPreviewPath = register(brush.tipPreviewPath, BRUSH_ASSETS),
                tipPreviewPaths = brush.tipPreviewPaths.mapNotNull { register(it, BRUSH_ASSETS) },
                dualTipPreviewPaths = brush.dualTipPreviewPaths.mapNotNull { register(it, BRUSH_ASSETS) },
                grainPreviewPath = register(brush.grainPreviewPath, BRUSH_ASSETS),
            )
        }
        val portableDocuments = documents.map { document ->
            document.copy(coverImagePath = register(document.coverImagePath, DOCUMENT_ASSETS), pages = document.pages.map { page ->
                page.copy(
                    background = when (val background = page.background) {
                        is PageBackground.Paper -> background
                        is PageBackground.Pdf -> background.copy(assetPath = register(background.assetPath, DOCUMENT_ASSETS)!!)
                        is PageBackground.Image -> background.copy(assetPath = register(background.assetPath, DOCUMENT_ASSETS)!!)
                    },
                    strokes = page.strokes.map { it.portable(::register) },
                    elements = page.elements.map { element -> when (element) {
                        is PageElement.Image -> element.copy(assetPath = register(element.assetPath, DOCUMENT_ASSETS)!!)
                        is PageElement.AudioMarker -> element.copy(assetPath = register(element.assetPath, DOCUMENT_ASSETS)!!)
                        is PageElement.Sticky -> element.copy(strokes = element.strokes.map { it.portable(::register) })
                        else -> element
                    } },
                )
            })
        }
        val manifest = LibraryBackupManifest(
            exportedAt = System.currentTimeMillis(),
            documents = portableDocuments,
            folders = folders,
            brushes = portableBrushes,
        )
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(manifest).encodeToByteArray())
            zip.closeEntry()
            assets.toSortedMap().forEach { (entryName, source) ->
                zip.putNextEntry(ZipEntry(entryName))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun restore(input: InputStream): LibraryRestoreResult {
        filesRoot.mkdirs()
        val staging = File(filesRoot, ".library-backup-import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            extractChecked(input, staging)
            val manifestFile = File(staging, MANIFEST)
            require(manifestFile.isFile && manifestFile.length() <= MAX_MANIFEST_BYTES) { "Backup has no valid manifest" }
            val manifest = json.decodeFromString<LibraryBackupManifest>(manifestFile.readText())
            require(manifest.formatVersion == FORMAT_VERSION) { "Backup version ${manifest.formatVersion} is not supported" }
            require(manifest.documents.size <= MAX_DOCUMENTS && manifest.folders.size <= MAX_FOLDERS && manifest.brushes.size <= MAX_BRUSHES) { "Backup manifest exceeds library limits" }

            fun retained(relativePath: String?, folder: String): String? {
                if (relativePath.isNullOrBlank()) return null
                val normalized = safeArchivePath(relativePath)
                require(normalized.startsWith("$folder/")) { "Backup asset is in the wrong category" }
                val source = File(staging, normalized)
                require(source.canonicalPath.startsWith(staging.canonicalPath + File.separator) && source.isFile) { "Backup is missing $normalized" }
                val expectedHash = source.name.substringBefore('.')
                require(expectedHash.length == 64 && expectedHash == sha256(source)) { "Backup asset failed its integrity check" }
                val targetDirectory = File(filesRoot, folder).apply { mkdirs() }
                val target = File(targetDirectory, source.name)
                if (!target.exists()) source.copyTo(target)
                return target.absolutePath
            }

            val now = System.currentTimeMillis()
            val rootFolder = InkFolder(name = "Restored backup")
            val folderIds = manifest.folders.associate { it.id to UUID.randomUUID().toString() }
            val restoredFolders = listOf(rootFolder) + manifest.folders.map { folder ->
                folder.copy(
                    id = folderIds.getValue(folder.id),
                    parentId = folder.parentId?.let(folderIds::get) ?: rootFolder.id,
                )
            }
            val brushIds = manifest.brushes.associate { it.id to UUID.randomUUID().toString() }
            val restoredBrushes = manifest.brushes.map { brush ->
                brush.copy(
                    id = brushIds.getValue(brush.id),
                    sourcePath = retained(brush.sourcePath, BRUSH_SOURCES),
                    tipPreviewPath = retained(brush.tipPreviewPath, BRUSH_ASSETS),
                    tipPreviewPaths = brush.tipPreviewPaths.mapNotNull { retained(it, BRUSH_ASSETS) },
                    dualTipPreviewPaths = brush.dualTipPreviewPaths.mapNotNull { retained(it, BRUSH_ASSETS) },
                    grainPreviewPath = retained(brush.grainPreviewPath, BRUSH_ASSETS),
                )
            }
            val restoredDocuments = manifest.documents.map { document ->
                document.copy(
                    id = UUID.randomUUID().toString(),
                    coverImagePath = retained(document.coverImagePath, DOCUMENT_ASSETS),
                    folderId = document.folderId?.let(folderIds::get) ?: rootFolder.id,
                    createdAt = now,
                    updatedAt = now,
                    trashedAt = null,
                    archivedAt = null,
                    pages = document.pages.map { page ->
                        page.copy(
                            background = when (val background = page.background) {
                                is PageBackground.Paper -> background
                                is PageBackground.Pdf -> background.copy(assetPath = retained(background.assetPath, DOCUMENT_ASSETS)!!)
                                is PageBackground.Image -> background.copy(assetPath = retained(background.assetPath, DOCUMENT_ASSETS)!!)
                            },
                            strokes = page.strokes.map { it.restored(brushIds, ::retained) },
                            elements = page.elements.map { element -> when (element) {
                                is PageElement.Image -> element.copy(assetPath = retained(element.assetPath, DOCUMENT_ASSETS)!!)
                                is PageElement.AudioMarker -> element.copy(assetPath = retained(element.assetPath, DOCUMENT_ASSETS)!!)
                                is PageElement.Sticky -> element.copy(strokes = element.strokes.map { it.restored(brushIds, ::retained) })
                                else -> element
                            } },
                        )
                    },
                )
            }
            return LibraryRestoreResult(restoredDocuments, restoredFolders, restoredBrushes)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extractChecked(input: InputStream, staging: File) {
        var entryCount = 0
        var expandedBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= MAX_ENTRIES) { "Backup contains too many entries" }
                val normalized = safeArchivePath(entry.name)
                require(
                    normalized == MANIFEST ||
                        normalized.startsWith("$DOCUMENT_ASSETS/") ||
                        normalized.startsWith("$BRUSH_ASSETS/") ||
                        normalized.startsWith("$BRUSH_SOURCES/"),
                ) { "Backup contains an unsupported entry" }
                if (!entry.isDirectory) {
                    val target = File(staging, normalized)
                    require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "Backup path escapes its container" }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            expandedBytes += count
                            require(expandedBytes <= MAX_EXPANDED_BYTES) { "Backup expands beyond the safety limit" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun safeArchivePath(path: String): String {
        val normalized = path.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Backup contains an unsafe path" }
        return normalized
    }

    private fun InkStroke.portable(register: (String?, String) -> String?) = copy(
        masks = masks.map { mask -> mask.copy(tipAssetPath = register(mask.tipAssetPath, BRUSH_ASSETS)) },
        tipAssetPath = register(tipAssetPath, BRUSH_ASSETS),
        tipAssetPaths = tipAssetPaths.mapNotNull { register(it, BRUSH_ASSETS) },
        dualTipAssetPaths = dualTipAssetPaths.mapNotNull { register(it, BRUSH_ASSETS) },
        grainAssetPath = register(grainAssetPath, BRUSH_ASSETS),
    )

    private fun InkStroke.restored(brushIds: Map<String, String>, retained: (String?, String) -> String?) = copy(
        brushId = brushIds[brushId] ?: brushId,
        masks = masks.map { mask -> mask.copy(tipAssetPath = retained(mask.tipAssetPath, BRUSH_ASSETS)) },
        tipAssetPath = retained(tipAssetPath, BRUSH_ASSETS),
        tipAssetPaths = tipAssetPaths.mapNotNull { retained(it, BRUSH_ASSETS) },
        dualTipAssetPaths = dualTipAssetPaths.mapNotNull { retained(it, BRUSH_ASSETS) },
        grainAssetPath = retained(grainAssetPath, BRUSH_ASSETS),
    )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Serializable
    private data class LibraryBackupManifest(
        val formatVersion: Int = FORMAT_VERSION,
        val exportedAt: Long,
        val documents: List<InkDocument>,
        val folders: List<InkFolder>,
        val brushes: List<ImportedBrush>,
    )

    private companion object {
        const val FORMAT_VERSION = 1
        const val MANIFEST = "manifest.json"
        const val DOCUMENT_ASSETS = "document-assets"
        const val BRUSH_ASSETS = "brush-assets"
        const val BRUSH_SOURCES = "brush-sources"
        const val MAX_DOCUMENTS = 10_000
        const val MAX_FOLDERS = 10_000
        const val MAX_BRUSHES = 20_000
        const val MAX_ENTRIES = 50_000
        const val MAX_MANIFEST_BYTES = 128L * 1024 * 1024
        const val MAX_SINGLE_ASSET_BYTES = 512L * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024
    }
}
