package app.inkloom.engine.v3.storage

import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.engine.v3.surface.PixelTileSurfaceSnapshot
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Atomic file store for [PixelTileArchiveCodec] manifests and immutable tile blobs. */
internal class PixelTileArchiveStore(root: File) {
    private val rootDirectory = root.canonicalFile
    private val manifestDirectory = File(rootDirectory, "manifests")
    private val blobDirectory = File(rootDirectory, "blobs")
    private val monitor = Any()

    init {
        require(rootDirectory.mkdirs() || rootDirectory.isDirectory) { "Could not create pixel tile store" }
        require(manifestDirectory.mkdirs() || manifestDirectory.isDirectory) { "Could not create tile manifest store" }
        require(blobDirectory.mkdirs() || blobDirectory.isDirectory) { "Could not create tile blob store" }
    }

    /** Blobs are durable before the manifest is atomically published. */
    fun save(snapshot: PixelTileSurfaceSnapshot): File = synchronized(monitor) {
        val encoded = PixelTileArchiveCodec.encode(snapshot)
        encoded.blobs.forEach { (hash, bytes) ->
            require(hash == sha256(bytes)) { "Codec returned a mismatched tile hash" }
            val target = File(blobDirectory, "$hash.tile")
            if (target.exists()) {
                require(target.length() == bytes.size.toLong() && sha256(target.readBytes()) == hash) {
                    "Existing tile blob failed integrity verification"
                }
            } else {
                atomicWrite(target, bytes)
            }
        }

        val manifestBytes = encoded.manifest.encodeToByteArray()
        val manifestHash = sha256(manifestBytes)
        val manifestFile = File(manifestDirectory, "$manifestHash.surface.json")
        if (manifestFile.exists()) {
            require(manifestFile.readBytes().contentEquals(manifestBytes)) {
                "Existing tile manifest failed integrity verification"
            }
        } else {
            atomicWrite(manifestFile, manifestBytes)
        }
        manifestFile
    }

    fun load(manifestFile: File): PixelTileSurface = synchronized(monitor) {
        val safeManifest = requireInside(manifestDirectory, manifestFile)
        require(safeManifest.isFile) { "Tile manifest does not exist" }
        PixelTileArchiveCodec.decode(safeManifest.readText()) { hash ->
            val blob = File(blobDirectory, "$hash.tile")
            if (blob.isFile) blob.readBytes() else null
        }
    }

    /** Verifies a portable archive closure and republishes it under this store's ownership. */
    fun import(manifestText: String, resolveBlob: (sha256: String) -> ByteArray?): File = synchronized(monitor) {
        val surface = PixelTileArchiveCodec.decode(manifestText, resolveBlob)
        save(surface.snapshot())
    }

    /** Verifies a manifest envelope and returns its document-facing identity. */
    fun inspect(manifestFile: File): PixelTileArchiveCodec.Metadata = synchronized(monitor) {
        val safeManifest = requireInside(manifestDirectory, manifestFile)
        require(safeManifest.isFile) { "Tile manifest does not exist" }
        PixelTileArchiveCodec.inspect(safeManifest.readText())
    }

    /**
     * Resolves every immutable file needed to reopen a surface. This is the single tracing boundary
     * used by bundle export, library backup, and asset garbage collection.
     */
    fun referencedFiles(manifestFile: File): List<File> = synchronized(monitor) {
        val safeManifest = requireInside(manifestDirectory, manifestFile)
        require(safeManifest.isFile) { "Tile manifest does not exist" }
        val metadata = PixelTileArchiveCodec.inspect(safeManifest.readText())
        listOf(safeManifest) + metadata.blobHashes.sorted().map { hash ->
            File(blobDirectory, "$hash.tile").also { blob ->
                require(blob.isFile) { "Retained tile blob $hash does not exist" }
                require(sha256(blob.readBytes()) == hash) { "Retained tile blob $hash failed integrity verification" }
            }
        }
    }

    /**
     * Removes unreferenced manifests and blobs. Callers must supply every manifest still reachable
     * from documents, checkpoints, backups, and retained undo before invoking this method.
     */
    fun garbageCollect(retainedManifests: Collection<File>): GarbageCollectionResult = synchronized(monitor) {
        val retainedManifestPaths = retainedManifests.mapTo(hashSetOf()) {
            requireInside(manifestDirectory, it).canonicalPath
        }
        val retainedBlobHashes = linkedSetOf<String>()
        retainedManifestPaths.forEach { path ->
            val manifest = File(path)
            require(manifest.isFile) { "Retained tile manifest does not exist" }
            retainedBlobHashes += PixelTileArchiveCodec.inspect(manifest.readText()).blobHashes
        }
        retainedBlobHashes.forEach { hash ->
            require(File(blobDirectory, "$hash.tile").isFile) { "Retained tile blob $hash does not exist" }
        }

        var manifestsDeleted = 0
        manifestDirectory.listFiles().orEmpty().filter(File::isFile).forEach { file ->
            if (file.canonicalPath !in retainedManifestPaths && file.delete()) manifestsDeleted++
        }
        var blobsDeleted = 0
        blobDirectory.listFiles().orEmpty().filter(File::isFile).forEach { file ->
            val hash = file.name.removeSuffix(".tile")
            if (hash !in retainedBlobHashes && file.delete()) blobsDeleted++
        }
        GarbageCollectionResult(manifestsDeleted, blobsDeleted)
    }

    data class GarbageCollectionResult(val manifestsDeleted: Int, val blobsDeleted: Int)

    private fun atomicWrite(target: File, bytes: ByteArray) {
        require(target.parentFile?.canonicalFile in setOf(manifestDirectory, blobDirectory)) {
            "Tile write escaped its store"
        }
        val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath())
            }
        } finally {
            temporary.delete()
        }
    }

    private fun requireInside(parent: File, candidate: File): File {
        val safeParent = parent.canonicalFile
        val safeCandidate = candidate.canonicalFile
        require(safeCandidate.parentFile == safeParent) { "Tile path escaped its store" }
        return safeCandidate
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
