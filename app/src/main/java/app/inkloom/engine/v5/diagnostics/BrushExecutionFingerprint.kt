package app.inkloom.engine.v5.diagnostics

import app.inkloom.brushpresetv5.BrushPresetV5Codec
import app.inkloom.brushpresetv5.CompiledBrushProgramV5
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import app.inkloom.engine.v5.tools.NativeRasterOps
import app.inkloom.model.BrushFormat
import app.inkloom.model.ImportedBrush
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Immutable identity of the exact brush program installed for the next contact. */
data class BrushExecutionIdentity(
    val presetId: String,
    val presetRevision: Int,
    val displayName: String,
    val origin: String,
    val sourceFormat: String,
    val sourceBrushId: String,
    val family: String,
    val backend: String,
    val compiledProgramHash: String,
    val tipAssetHash: String,
    val tipSemanticHash: String,
    val grainAssetHash: String,
    val grainSemanticHash: String,
    val dynamicsHash: String,
    val depositionHash: String,
    val spacingHash: String,
    val renderSeed: Int,
)

/** Debug/profile evidence that UI selection, stroke execution and committed pixels agree. */
data class BrushExecutionFingerprint(
    val presetId: String,
    val presetRevision: Int,
    val displayName: String,
    val origin: String,
    val sourceFormat: String,
    val sourceBrushId: String,
    val family: String,
    val backend: String,
    val compiledProgramHash: String,
    val tipAssetHash: String,
    val tipSemanticHash: String,
    val grainAssetHash: String,
    val grainSemanticHash: String,
    val dynamicsHash: String,
    val depositionHash: String,
    val spacingHash: String,
    val renderSeed: Int,
    val paintOpClass: String,
    val strokeId: String,
    val transactionId: Long,
    val committedTileHash: String,
) {
    /** Raster semantics without user/library identity; useful for collapse detection. */
    val semanticExecutionHash: String
        get() = BrushExecutionFingerprints.sha256(
            listOf(
                family, backend, tipAssetHash, tipSemanticHash, grainAssetHash,
                grainSemanticHash, dynamicsHash, depositionHash, spacingHash,
                renderSeed.toString(), paintOpClass,
            ).joinToString("\u001f").toByteArray(),
        )
}

object BrushExecutionFingerprints {
    fun identity(program: CompiledBrushProgramV5, source: ImportedBrush?): BrushExecutionIdentity {
        if (source != null) {
            check(source.id == program.identity.id) {
                "Selected brush ${source.id} compiled as ${program.identity.id}"
            }
        }
        val preset = program.preset
        val tipPaths = program.tip.tipTexture?.assetPaths.orEmpty()
        val grainPaths = buildList {
            program.textureGrain?.assetPath?.let(::add)
            program.dual?.textureGrain?.assetPath?.let(::add)
        }
        val format = source?.format ?: BrushFormat.INKLOOM
        return BrushExecutionIdentity(
            presetId = program.identity.id,
            presetRevision = program.identity.version,
            displayName = program.identity.name,
            origin = when (format) {
                BrushFormat.SYSTEM -> "FACTORY"
                BrushFormat.INKLOOM -> "NATIVE"
                else -> "IMPORTED"
            },
            sourceFormat = format.name,
            sourceBrushId = source?.id ?: program.identity.id,
            family = program.identity.family.name,
            backend = program.backend.name,
            compiledProgramHash = sha256(
                (BrushPresetV5Codec.encode(preset) + "\u001f" + program.backend.name).toByteArray(),
            ),
            tipAssetHash = assetSetHash(tipPaths),
            tipSemanticHash = sha256(
                listOf(program.tip, program.stamp, program.dual?.tipAssetPaths).joinToString("\u001f").toByteArray(),
            ),
            grainAssetHash = assetSetHash(grainPaths),
            grainSemanticHash = sha256(
                listOf(program.grainSampler, program.textureGrain, program.dual?.textureGrain).joinToString("\u001f").toByteArray(),
            ),
            dynamicsHash = sha256(
                listOf(program.pressure, program.tilt, program.strokeShaping, program.stamp).joinToString("\u001f").toByteArray(),
            ),
            depositionHash = sha256(
                listOf(program.output, program.deposition, program.wetMix, program.dual?.flow).joinToString("\u001f").toByteArray(),
            ),
            spacingHash = sha256(
                listOf(program.spacingPolicy, program.stamp?.spray, program.dual?.spacing).joinToString("\u001f").toByteArray(),
            ),
            renderSeed = program.renderSeed,
        )
    }

    fun committed(
        identity: BrushExecutionIdentity,
        paintOpClass: String,
        strokeId: String,
        record: PixelTileTransactionRecord,
        snapshot: PixelTileSurfaceSnapshot,
    ): BrushExecutionFingerprint = BrushExecutionFingerprint(
        presetId = identity.presetId,
        presetRevision = identity.presetRevision,
        displayName = identity.displayName,
        origin = identity.origin,
        sourceFormat = identity.sourceFormat,
        sourceBrushId = identity.sourceBrushId,
        family = identity.family,
        backend = identity.backend,
        compiledProgramHash = identity.compiledProgramHash,
        tipAssetHash = identity.tipAssetHash,
        tipSemanticHash = identity.tipSemanticHash,
        grainAssetHash = identity.grainAssetHash,
        grainSemanticHash = identity.grainSemanticHash,
        dynamicsHash = identity.dynamicsHash,
        depositionHash = identity.depositionHash,
        spacingHash = identity.spacingHash,
        renderSeed = identity.renderSeed,
        paintOpClass = paintOpClass,
        strokeId = strokeId,
        transactionId = record.transactionId,
        committedTileHash = committedTileHash(record, snapshot),
    )

    private fun assetSetHash(paths: List<String>): String {
        if (paths.isEmpty()) return "NONE"
        val digest = MessageDigest.getInstance("SHA-256")
        paths.distinct().sorted().forEach { path ->
            digest.update(path.toByteArray())
            val file = File(path)
            if (file.isFile) file.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            } else digest.update("MISSING".toByteArray())
        }
        return digest.digest().hex()
    }

    private fun committedTileHash(
        record: PixelTileTransactionRecord,
        snapshot: PixelTileSurfaceSnapshot,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        record.deltaInfo.sortedWith(compareBy({ it.key.row }, { it.key.column })).forEach { delta ->
            digest.update("${delta.key.column},${delta.key.row}".toByteArray())
            snapshot.readRgba8Tile(delta.key) { pixels ->
                val nativeHash = NativeRasterOps.hashPremultipliedPixels(pixels)
                if (nativeHash != null) {
                    digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(nativeHash).array())
                } else {
                    // ByteBuffer is big-endian by default, preserving the JVM A,R,G,B stream.
                    val bytes = ByteBuffer.allocate(pixels.size * Int.SIZE_BYTES)
                    bytes.asIntBuffer().put(pixels)
                    digest.update(bytes.array())
                }
            }
        }
        return digest.digest().hex()
    }

    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
