package app.inkloom.importer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

enum class ProcreateResourceRole { SHAPE, GRAIN }

enum class ProcreateResourceState {
    EXACT_EMBEDDED,
    EXACT_LOCAL_RESOURCE_PACK,
    APPROXIMATED_RESOURCE,
    UNSUPPORTED_MISSING_RESOURCE,
}

data class ProcreateResolvedResource(
    val file: File?,
    val state: ProcreateResourceState,
    val sourceReference: String?,
) {
    val reportLine: String get() = "${state.name}:${sourceReference ?: "<anonymous>"}"
}

/**
 * Resolves Procreate Source Library dependencies without network access. Exact user-supplied
 * bytes always win. The optional procedural path creates original Inkloom substitutes and labels
 * them as approximations; it never claims to reconstruct Apple's proprietary resource.
 */
class ProcreateResourceResolver(
    private val localResourcePack: File? = null,
    private val allowProceduralApproximation: Boolean = false,
) {
    fun resolve(
        embedded: File?,
        sourceReference: String?,
        role: ProcreateResourceRole,
        output: File,
    ): ProcreateResolvedResource {
        if (embedded?.isFile == true && embedded.length() > 0L) {
            return ProcreateResolvedResource(embedded, ProcreateResourceState.EXACT_EMBEDDED, null)
        }
        val reference = sourceReference?.trim()?.takeIf { it.isNotBlank() && it != "\$null" }
            ?: return ProcreateResolvedResource(null, ProcreateResourceState.UNSUPPORTED_MISSING_RESOURCE, null)
        resolveFromLocalPack(reference)?.let { source ->
            output.parentFile?.mkdirs()
            source.copyTo(output, overwrite = true)
            return ProcreateResolvedResource(output, ProcreateResourceState.EXACT_LOCAL_RESOURCE_PACK, reference)
        }
        if (allowProceduralApproximation && REFERENCE_PATTERN.matches(reference.substringAfterLast('/'))) {
            output.parentFile?.mkdirs()
            ProcreateProceduralResource.write(output, reference, role)
            return ProcreateResolvedResource(output, ProcreateResourceState.APPROXIMATED_RESOURCE, reference)
        }
        return ProcreateResolvedResource(null, ProcreateResourceState.UNSUPPORTED_MISSING_RESOURCE, reference)
    }

    private fun resolveFromLocalPack(reference: String): File? {
        val root = localResourcePack?.takeIf(File::isDirectory) ?: return null
        val normalizedReference = reference.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)
        val candidates = root.walkTopDown().maxDepth(MAX_PACK_DEPTH).filter { candidate ->
            candidate.isFile && candidate.length() in 1..MAX_RESOURCE_BYTES &&
                (candidate.relativeTo(root).invariantSeparatorsPath.lowercase(Locale.ROOT) == normalizedReference ||
                    candidate.name.lowercase(Locale.ROOT) == normalizedReference.substringAfterLast('/'))
        }.take(MAX_MATCHES).toList()
        if (candidates.isEmpty()) return null
        val firstBytes = candidates.first().readBytes()
        return candidates.first().takeIf { candidates.drop(1).all { it.readBytes().contentEquals(firstBytes) } }
    }

    companion object {
        private const val MAX_PACK_DEPTH = 8
        private const val MAX_MATCHES = 8
        private const val MAX_RESOURCE_BYTES = 16L * 1024L * 1024L
        private val REFERENCE_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._ ()#-]{0,180}\\.(png|jpe?g)", RegexOption.IGNORE_CASE)
    }
}

/** Tiny dependency-free RGBA PNG generator, usable by Android production code and JVM tests. */
private object ProcreateProceduralResource {
    private const val SIZE = 96

    fun write(output: File, reference: String, role: ProcreateResourceRole) {
        val seed = reference.lowercase(Locale.ROOT).hashCode()
        val rgba = ByteArray(SIZE * SIZE * 4)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val offset = (y * SIZE + x) * 4
            if (role == ProcreateResourceRole.SHAPE) {
                val alpha = shapeCoverage(reference, x, y, seed)
                rgba[offset] = 0xff.toByte()
                rgba[offset + 1] = 0xff.toByte()
                rgba[offset + 2] = 0xff.toByte()
                rgba[offset + 3] = alpha.toByte()
            } else {
                val value = grainValue(reference, x, y, seed)
                rgba[offset] = value.toByte()
                rgba[offset + 1] = value.toByte()
                rgba[offset + 2] = value.toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
        writePng(output, SIZE, SIZE, rgba)
    }

    private fun shapeCoverage(reference: String, x: Int, y: Int, seed: Int): Int {
        val name = reference.lowercase(Locale.ROOT)
        val nx = (x + .5f - SIZE / 2f) / (SIZE / 2f)
        val ny = (y + .5f - SIZE / 2f) / (SIZE / 2f)
        val oval = when {
            "flat" in name || "rect" in name || "brick" in name -> maxOf(kotlin.math.abs(nx), kotlin.math.abs(ny * 1.8f))
            "oval" in name || "calligraphy" in name -> kotlin.math.sqrt(nx * nx + ny * ny * 3.2f)
            else -> kotlin.math.sqrt(nx * nx + ny * ny)
        }
        val softness = when {
            "ultra-soft" in name -> .52f
            "soft" in name || "cloud" in name -> .34f
            "medium" in name -> .14f
            else -> .055f
        }
        val body = ((1.02f - oval) / softness).coerceIn(0f, 1f)
        val textured = name.containsAny("mess", "ink", "sponge", "chalk", "charcoal", "crust", "blotch", "rust", "paint", "grunge", "splatter")
        val texture = if (textured) .38f + .62f * noise01(x, y, seed) else 1f
        val stripe = if ("bristle" in name || "streak" in name) .4f + .6f * kotlin.math.abs(kotlin.math.sin((x + seed.and(31)) * .47f)) else 1f
        return (255f * body * texture * stripe).toInt().coerceIn(0, 255)
    }

    private fun grainValue(reference: String, x: Int, y: Int, seed: Int): Int {
        val name = reference.lowercase(Locale.ROOT)
        if ("blank" in name) return 0
        val coarse = noise01(x / 5, y / 5, seed)
        val fine = noise01(x, y, seed xor 0x4f1bbcdc)
        val paper = if ("paper" in name) {
            .5f + .22f * kotlin.math.sin(x * .31f + coarse * 2f) * kotlin.math.sin(y * .27f + fine)
        } else .18f + .82f * (coarse * .58f + fine * .42f)
        return (paper.coerceIn(0f, 1f) * 255f).toInt()
    }

    private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)

    private fun noise01(x: Int, y: Int, seed: Int): Float {
        var value = x * 0x1f1f1f1f + y * 0x45d9f3b + seed
        value = value xor (value ushr 16)
        value *= 0x45d9f3b
        value = value xor (value ushr 16)
        return (value and 0xffff) / 65535f
    }

    private fun writePng(output: File, width: Int, height: Int, rgba: ByteArray) {
        val raw = ByteArray((width * 4 + 1) * height)
        for (y in 0 until height) {
            val row = y * (width * 4 + 1)
            raw[row] = 0
            rgba.copyInto(raw, row + 1, y * width * 4, (y + 1) * width * 4)
        }
        val compressed = ByteArrayOutputStream().also { bytes ->
            DeflaterOutputStream(bytes).use { it.write(raw) }
        }.toByteArray()
        FileOutputStream(output).use { stream ->
            stream.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            writeChunk(stream, "IHDR", ByteArrayOutputStream().also { header ->
                header.writeInt(width)
                header.writeInt(height)
                header.write(byteArrayOf(8, 6, 0, 0, 0))
            }.toByteArray())
            writeChunk(stream, "IDAT", compressed)
            writeChunk(stream, "IEND", ByteArray(0))
        }
    }

    private fun writeChunk(output: FileOutputStream, name: String, data: ByteArray) {
        output.writeInt(data.size)
        val type = name.toByteArray(StandardCharsets.US_ASCII)
        output.write(type)
        output.write(data)
        val crc = CRC32().apply { update(type); update(data) }
        output.writeInt(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()))
    }

    private fun FileOutputStream.writeInt(value: Int) {
        write(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()))
    }
}
