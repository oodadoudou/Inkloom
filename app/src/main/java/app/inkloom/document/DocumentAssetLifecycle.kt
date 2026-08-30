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
    private val pendingDocumentAssets = linkedSetOf<String>()
    private val pendingRasterTileManifests = linkedSetOf<String>()

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
            synchronized(lock) { pendingDocumentAssets += target.canonicalPath }
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
        pendingRasterTileManifests += manifest.canonicalPath
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
            pendingRasterTileManifests += imported.canonicalPath
        }
    }

    fun releaseAssetReservation(path: String?) {
        if (path.isNullOrBlank()) return
        synchronized(lock) {
            runCatching { File(path).canonicalPath }.getOrNull()?.let(pendingDocumentAssets::remove)
        }
    }

    fun releaseRasterTileReservation(reference: RasterTileSurfaceRef?) {
        if (reference == null) return
        synchronized(lock) {
            runCatching { File(reference.manifestAssetPath).canonicalPath }.getOrNull()
                ?.let(pendingRasterTileManifests::remove)
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
            pendingDocumentAssets.toSet(),
        )
        val retainedManifests = documents.flatMap { document ->
            document.pages.flatMap { page ->
                page.layers.mapNotNull { layer -> layer.rasterTileSurface?.manifestAssetPath }
            }
        }.map(::File) + pendingRasterTileManifests.map(::File)
        val tileResult = pixelTileStore.garbageCollect(retainedManifests)
        documentAssetsDeleted + tileResult.manifestsDeleted + tileResult.blobsDeleted
    }
}
