package app.inkloom.document

import app.inkloom.engine.v5.storage.PixelTileArchiveStore
import app.inkloom.engine.v5.surface.PixelTileSurface
import java.io.File
import java.security.MessageDigest

/** Owns asset reservations and garbage collection independently from document rendering/export. */
internal class DocumentAssetLifecycle(
    private val directory: File,
    private val pixelTileStore: PixelTileArchiveStore,
) {
    private val lock = Any()
    private val historyAssets = mutableMapOf<String,Int>()
    fun retainHistoryAssets(paths: List<String>): () -> Unit {
        val retained=paths.map { File(it).canonicalPath }.toSet()
        synchronized(lock) { retained.forEach { historyAssets[it]=(historyAssets[it] ?: 0)+1 } }
        return { synchronized(lock) { retained.forEach { path ->
            val count=(historyAssets[path] ?: 1)-1; if(count==0) historyAssets.remove(path) else historyAssets[path]=count
        } } }
    }

    private val pendingDocumentAssets = mutableMapOf<String,Int>()
    // A content-addressed manifest may be reserved by more than one transaction.
    // Releasing one owner must not expose another owner's copy to collection.
    private val pendingRasterTileManifests = mutableMapOf<String, Int>()

    fun saveBytes(bytes: ByteArray, kind: String): String {
        require(bytes.isNotEmpty()) { "Could not encode $kind" }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val target = File(directory, "$hash.$kind")
        val temporary = File(directory, ".${System.nanoTime()}.tmp")
        try {
            if (!target.exists()) {
                temporary.outputStream().use { output ->
                    output.write(bytes)
                    output.flush()
                }
                check(temporary.renameTo(target)) { "Could not retain $kind" }
            }
            synchronized(lock) { val path=target.canonicalPath;pendingDocumentAssets[path]=(pendingDocumentAssets[path]?:0)+1 }
            return target.absolutePath
        } finally {
            temporary.delete()
        }
    }

    fun saveRasterTileSurface(
        surface: PixelTileSurface,
        previousReference: RasterTileSurfaceRef?,
    ): RasterTileSurfaceRef = synchronized(lock) {
        val manifest = pixelTileStore.save(
            surface.snapshot(),
            previousReference?.manifestAssetPath?.let(::File),
        )
        val metadata = pixelTileStore.inspect(manifest)
        val path = manifest.canonicalPath
        pendingRasterTileManifests[path] = (pendingRasterTileManifests[path] ?: 0) + 1
        RasterTileSurfaceRef(
            manifestAssetPath = manifest.absolutePath,
            surfaceId = metadata.surfaceId,
            logicalWidth = metadata.logicalWidth,
            logicalHeight = metadata.logicalHeight,
            tileSize = metadata.tileSize,
            revision = metadata.revision,
            pixelFormat = metadata.pixelFormat.manifestId,
        )
    }

    fun importRasterTileManifest(
        manifest: String,
        blobLoader: (String) -> ByteArray?,
    ): File = synchronized(lock) {
        pixelTileStore.import(manifest, blobLoader).also { imported ->
            val path = imported.canonicalPath
            pendingRasterTileManifests[path] = (pendingRasterTileManifests[path] ?: 0) + 1
        }
    }

    fun releaseAssetReservation(path: String?) {
        if (path.isNullOrBlank()) return
        synchronized(lock) {
            runCatching { File(path).canonicalPath }.getOrNull()?.let { key ->
                val count=pendingDocumentAssets[key]?:return@let
                if(count<=1)pendingDocumentAssets.remove(key)else pendingDocumentAssets[key]=count-1
            }
        }
    }

    fun releaseRasterTileReservation(reference: RasterTileSurfaceRef?) {
        if (reference == null) return
        synchronized(lock) {
            runCatching { File(reference.manifestAssetPath).canonicalPath }.getOrNull()?.let { path ->
                val count = pendingRasterTileManifests[path] ?: return@let
                if (count == 1) pendingRasterTileManifests.remove(path)
                else pendingRasterTileManifests[path] = count - 1
            }
        }
    }

    fun garbageCollect(
        documents: List<InkDocument>,
        pageTemplates: PageTemplateLibrary,
    ): Int = synchronized(lock) {
        val documentAssetsDeleted = DocumentAssetGarbageCollector.collect(
            directory,
            documents,
            pageTemplates,
            pendingDocumentAssets.keys + historyAssets.keys,
        )
        val retainedManifests = documents.flatMap { document ->
            document.pages.flatMap { page ->
                page.layers.mapNotNull { layer -> layer.rasterTileSurface?.manifestAssetPath }
            }
        }.map(::File) + pendingRasterTileManifests.keys.map(::File) +
            historyAssets.keys.map(::File).filter {
                it.parentFile == File(directory, "pixel-tiles/manifests").canonicalFile
            }
        val tileResult = pixelTileStore.garbageCollect(retainedManifests)
        documentAssetsDeleted + tileResult.manifestsDeleted + tileResult.blobsDeleted
    }
}
