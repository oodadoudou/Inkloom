@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app.inkloom.document

import app.inkloom.model.ImportedBrush
import app.inkloom.importer.BrushGroupOperations
import app.inkloom.engine.v5.storage.PixelTileArchiveStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class LibraryRestoreResult(
    val documents: List<InkDocument>,
    val folders: List<InkFolder>,
    val brushes: List<ImportedBrush>,
    val brushGroups: List<String>,
    val preferences: ByteArray? = null,
    val brushIdMappings: Map<String, String> = emptyMap(),
    val pageTemplates: PageTemplateLibrary = PageTemplateLibrary(),
)

/**
 * A portable, versioned backup of the entire user library. Existing content is
 * never overwritten on restore: documents, folders and brush IDs are remapped
 * into a dedicated restored folder while immutable assets are content-addressed.
 */
class LibraryBackupService(private val filesRoot: File) {
    private val pixelTileStore = PixelTileArchiveStore(File(filesRoot, "$DOCUMENT_ASSETS/pixel-tiles"))
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
        brushGroups: List<String> = emptyList(),
        preferences: ByteArray? = null,
        pageTemplates: PageTemplateLibrary = PageTemplateLibrary(),
    ) {
        require(documents.size <= MAX_DOCUMENTS) { "Library has too many documents to back up" }
        require(folders.size <= MAX_FOLDERS) { "Library has too many folders to back up" }
        require(brushes.size <= MAX_BRUSHES) { "Library has too many brushes to back up" }
        require(brushGroups.size <= MAX_BRUSH_GROUPS) { "Library has too many brush groups to back up" }
        require(preferences == null || preferences.size <= MAX_PREFERENCES_BYTES) { "Library preferences are too large to back up" }
        require(pageTemplates.customTemplates.size <= MAX_PAGE_TEMPLATES) { "Library has too many page templates to back up" }
        val portableBrushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, brushes)
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

        fun registerRaster(reference: RasterTileSurfaceRef?): RasterTileSurfaceRef? {
            if (reference == null) return null
            val files = pixelTileStore.referencedFiles(File(reference.manifestAssetPath))
            files.forEach { source ->
                require(source.length() <= MAX_SINGLE_ASSET_BYTES) { "Backup tile asset is too large: ${source.name}" }
                val kind = when (source.parentFile?.name) {
                    "manifests" -> "manifests"
                    "blobs" -> "blobs"
                    else -> error("Raster tile asset escaped its store")
                }
                assets.putIfAbsent("$DOCUMENT_ASSETS/pixel-tiles/$kind/${source.name}", source)
            }
            val metadata = pixelTileStore.inspect(files.first())
            require(metadata.surfaceId == reference.surfaceId &&
                metadata.logicalWidth == reference.logicalWidth &&
                metadata.logicalHeight == reference.logicalHeight &&
                metadata.tileSize == reference.tileSize &&
                metadata.revision == reference.revision
            ) { "Backup raster tile reference does not match its manifest" }
            return reference.copy(
                manifestAssetPath = "$DOCUMENT_ASSETS/pixel-tiles/manifests/${files.first().name}",
            )
        }

        val portableBrushes = brushes.map { brush ->
            brush.copy(
                sourcePath = register(brush.sourcePath, BRUSH_SOURCES),
                tipPreviewPath = register(brush.tipPreviewPath, BRUSH_ASSETS),
                tipPreviewPaths = brush.tipPreviewPaths.mapNotNull { register(it, BRUSH_ASSETS) },
                dualTipPreviewPaths = brush.dualTipPreviewPaths.mapNotNull { register(it, BRUSH_ASSETS) },
                dualGrain = brush.dualGrain.copy(previewPath = register(brush.dualGrainPreviewPath, BRUSH_ASSETS)),
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
                    layers = page.layers.map { layer -> layer.copy(
                        rasterTileSurface = registerRaster(layer.rasterTileSurface),
                        mask = layer.mask?.copy(surface = layer.mask.surface?.let { reference ->
                            reference.copy(assetPath = register(reference.assetPath, DOCUMENT_ASSETS)!!)
                        }),
                    ) },
                    elements = page.elements.map { element -> when (element) {
                        is PageElement.Image -> element.copy(assetPath = register(element.assetPath, DOCUMENT_ASSETS)!!)
                        is PageElement.AudioMarker -> element.copy(assetPath = register(element.assetPath, DOCUMENT_ASSETS)!!)
                        is PageElement.Fill -> element.copy(maskAssetPath = register(element.maskAssetPath, DOCUMENT_ASSETS)!!)
                        is PageElement.Shape -> element.copy(maskAssetPath = register(element.maskAssetPath, DOCUMENT_ASSETS))
                        else -> element
                    } },
                )
            })
        }
        val portablePageTemplates = pageTemplates.copy(customTemplates = pageTemplates.customTemplates.map { template ->
            template.copy(background = when (val background = template.background) {
                is PageBackground.Paper -> background
                is PageBackground.Pdf -> background.copy(assetPath = register(background.assetPath, DOCUMENT_ASSETS)!!)
                is PageBackground.Image -> background.copy(assetPath = register(background.assetPath, DOCUMENT_ASSETS)!!)
            })
        })
        val manifest = LibraryBackupManifest(
            engineVersion = ENGINE_VERSION,
            exportedAt = System.currentTimeMillis(),
            documents = portableDocuments,
            folders = folders,
            brushes = portableBrushes,
            brushGroups = portableBrushGroups,
            preferencesEntry = preferences?.let { PREFERENCES },
            pageTemplates = portablePageTemplates,
        )
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(manifest).encodeToByteArray())
            zip.closeEntry()
            preferences?.let { bytes ->
                zip.putNextEntry(ZipEntry(PREFERENCES))
                zip.write(bytes)
                zip.closeEntry()
            }
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
            require(manifest.engineVersion == ENGINE_VERSION) { "Backup engine version ${manifest.engineVersion} is not supported" }
            require(manifest.documents.size <= MAX_DOCUMENTS && manifest.folders.size <= MAX_FOLDERS && manifest.brushes.size <= MAX_BRUSHES && manifest.brushGroups.size <= MAX_BRUSH_GROUPS && manifest.pageTemplates.customTemplates.size <= MAX_PAGE_TEMPLATES) { "Backup manifest exceeds library limits" }

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

            fun retainedRaster(reference: RasterTileSurfaceRef?): RasterTileSurfaceRef? {
                if (reference == null) return null
                val relative = safeArchivePath(reference.manifestAssetPath)
                require(relative.startsWith("$DOCUMENT_ASSETS/pixel-tiles/manifests/")) {
                    "Backup raster manifest is in the wrong category"
                }
                val sourceManifest = File(staging, relative)
                require(sourceManifest.canonicalPath.startsWith(staging.canonicalPath + File.separator) &&
                    sourceManifest.isFile
                ) { "Backup is missing $relative" }
                val importedManifest = pixelTileStore.import(sourceManifest.readText()) { hash ->
                    File(staging, "$DOCUMENT_ASSETS/pixel-tiles/blobs/$hash.tile")
                        .takeIf(File::isFile)?.readBytes()
                }
                val metadata = pixelTileStore.inspect(importedManifest)
                require(metadata.surfaceId == reference.surfaceId &&
                    metadata.logicalWidth == reference.logicalWidth &&
                    metadata.logicalHeight == reference.logicalHeight &&
                    metadata.tileSize == reference.tileSize &&
                    metadata.revision == reference.revision
                ) { "Backup raster tile reference does not match its verified manifest" }
                return reference.copy(manifestAssetPath = importedManifest.absolutePath)
            }

            val now = System.currentTimeMillis()
            val rootFolder = InkFolder(name = "Restored backup")
            val folderIds = manifest.folders.associate { it.id to UUID.randomUUID().toString() }
            val documentIds = manifest.documents.associate { it.id to UUID.randomUUID().toString() }
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
                    dualGrain = brush.dualGrain.copy(previewPath = retained(brush.dualGrainPreviewPath, BRUSH_ASSETS)),
                    grainPreviewPath = retained(brush.grainPreviewPath, BRUSH_ASSETS),
                )
            }
            val restoredDocuments = manifest.documents.map { document ->
                document.copy(
                    id = documentIds.getValue(document.id),
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
                            layers = page.layers.map { layer -> layer.copy(
                                rasterTileSurface = retainedRaster(layer.rasterTileSurface),
                                mask = layer.mask?.copy(surface = layer.mask.surface?.let { reference ->
                                    reference.copy(assetPath = retained(reference.assetPath, DOCUMENT_ASSETS)!!)
                                }),
                            ) },
                            elements = page.elements.map { element -> when (element) {
                                is PageElement.Image -> element.copy(assetPath = retained(element.assetPath, DOCUMENT_ASSETS)!!)
                                is PageElement.AudioMarker -> element.copy(assetPath = retained(element.assetPath, DOCUMENT_ASSETS)!!)
                                is PageElement.Fill -> element.copy(maskAssetPath = retained(element.maskAssetPath, DOCUMENT_ASSETS)!!)
                                is PageElement.Shape -> element.copy(maskAssetPath = retained(element.maskAssetPath, DOCUMENT_ASSETS))
                                else -> element
                            } },
                        )
                    },
                )
            }
            val templateIds = manifest.pageTemplates.customTemplates.associate { it.id to UUID.randomUUID().toString() }
            fun restoredTemplateId(id: String): String? = when {
                PageTemplateCatalog.isBuiltIn(id) -> id
                else -> templateIds[id]
            }
            val restoredPageTemplates = PageTemplateLibrary(
                customTemplates = manifest.pageTemplates.customTemplates.map { template ->
                    template.copy(
                        id = templateIds.getValue(template.id),
                        background = when (val background = template.background) {
                            is PageBackground.Paper -> background
                            is PageBackground.Pdf -> background.copy(assetPath = retained(background.assetPath, DOCUMENT_ASSETS)!!)
                            is PageBackground.Image -> background.copy(assetPath = retained(background.assetPath, DOCUMENT_ASSETS)!!)
                        },
                    )
                },
                favoriteIds = manifest.pageTemplates.favoriteIds.mapNotNullTo(linkedSetOf(), ::restoredTemplateId),
                notebookDefaults = manifest.pageTemplates.notebookDefaults.mapNotNull { (documentId, templateId) ->
                    val restoredDocumentId = documentIds[documentId] ?: return@mapNotNull null
                    val restoredTemplateId = restoredTemplateId(templateId) ?: return@mapNotNull null
                    restoredDocumentId to restoredTemplateId
                }.toMap(),
            )
            val restoredBrushGroups = BrushGroupOperations.reconcileRegisteredGroups(manifest.brushGroups, restoredBrushes)
            val restoredPreferences = manifest.preferencesEntry?.let { entry ->
                require(entry == PREFERENCES) { "Backup preferences entry is invalid" }
                val file = File(staging, entry)
                require(file.isFile && file.length() in 1..MAX_PREFERENCES_BYTES.toLong()) { "Backup has no valid preferences" }
                file.readBytes()
            }
            return LibraryRestoreResult(restoredDocuments, restoredFolders, restoredBrushes, restoredBrushGroups, restoredPreferences, brushIds, restoredPageTemplates)
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
                        normalized == PREFERENCES ||
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
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val formatVersion: Int = FORMAT_VERSION,
        val engineVersion: Int = 1,
        val exportedAt: Long,
        val documents: List<InkDocument>,
        val folders: List<InkFolder>,
        val brushes: List<ImportedBrush>,
        val brushGroups: List<String> = emptyList(),
        val preferencesEntry: String? = null,
        val pageTemplates: PageTemplateLibrary = PageTemplateLibrary(),
    )

    private companion object {
        const val FORMAT_VERSION = 5
        const val ENGINE_VERSION = 5
        const val MANIFEST = "manifest.json"
        const val PREFERENCES = "preferences.json"
        const val DOCUMENT_ASSETS = "document-assets"
        const val BRUSH_ASSETS = "brush-assets"
        const val BRUSH_SOURCES = "brush-sources"
        const val MAX_DOCUMENTS = 10_000
        const val MAX_FOLDERS = 10_000
        const val MAX_BRUSHES = 20_000
        const val MAX_BRUSH_GROUPS = 20_000
        const val MAX_PAGE_TEMPLATES = 200
        const val MAX_PREFERENCES_BYTES = 512 * 1024
        const val MAX_ENTRIES = 50_000
        const val MAX_MANIFEST_BYTES = 128L * 1024 * 1024
        const val MAX_SINGLE_ASSET_BYTES = 512L * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024
    }
}
