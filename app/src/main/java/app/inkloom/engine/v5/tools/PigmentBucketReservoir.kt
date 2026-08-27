package app.inkloom.engine.v5.tools

import kotlin.math.ceil
import kotlin.math.floor

/** Persistent spatial pigment carried by one Blend/Smudge contact. */
internal class PigmentBucketReservoir(
    val columns: Int = DEFAULT_BUCKETS,
    val rows: Int = DEFAULT_BUCKETS,
) {
    init {
        require(columns in 2..32 && rows in 2..32)
    }

    // Premultiplied linear-in-encoded-space RGBA. This preserves transparent pigment rules and
    // keeps the reservoir independent of Android Bitmap storage.
    private val red = FloatArray(columns * rows)
    private val green = FloatArray(columns * rows)
    private val blue = FloatArray(columns * rows)
    private val alpha = FloatArray(columns * rows)
    private var initialized = false

    val bucketCount: Int get() = columns * rows
    val retainedByteSize: Long get() = bucketCount.toLong() * 4 * Float.SIZE_BYTES
    val isInitialized: Boolean get() = initialized
    val hasPigment: Boolean get() = alpha.any { it > MIN_ALPHA }

    /**
     * Downsamples one brush footprint and mixes it into persistent buckets. [pixels] contains only
     * the bounded pickup rectangle and may be reused by the caller after this function returns.
     */
    fun pickup(pixels: IntArray, width: Int, height: Int, rate: Float) {
        require(width > 0 && height > 0 && pixels.size >= width * height)
        val mix = if (initialized) rate.coerceIn(0f, 1f) else 1f
        val retain = 1f - mix
        for (row in 0 until rows) {
            val top = floor(row * height / rows.toFloat()).toInt().coerceIn(0, height - 1)
            val bottom = ceil((row + 1) * height / rows.toFloat()).toInt().coerceIn(top + 1, height)
            for (column in 0 until columns) {
                val left = floor(column * width / columns.toFloat()).toInt().coerceIn(0, width - 1)
                val right = ceil((column + 1) * width / columns.toFloat()).toInt().coerceIn(left + 1, width)
                var sampleA = 0f
                var sampleR = 0f
                var sampleG = 0f
                var sampleB = 0f
                var count = 0
                for (y in top until bottom) for (x in left until right) {
                    val color = pixels[y * width + x]
                    val a = ((color ushr 24) and 0xff) / 255f
                    sampleA += a
                    sampleR += ((color ushr 16) and 0xff) / 255f * a
                    sampleG += ((color ushr 8) and 0xff) / 255f * a
                    sampleB += (color and 0xff) / 255f * a
                    count++
                }
                val index = row * columns + column
                val scale = 1f / count.coerceAtLeast(1)
                alpha[index] = alpha[index] * retain + sampleA * scale * mix
                red[index] = red[index] * retain + sampleR * scale * mix
                green[index] = green[index] * retain + sampleG * scale * mix
                blue[index] = blue[index] * retain + sampleB * scale * mix
            }
        }
        initialized = true
    }

    /** Caller-owned output avoids allocating on the frame path. */
    fun copyArgb(output: IntArray) {
        require(output.size >= bucketCount)
        for (index in 0 until bucketCount) {
            val a = alpha[index].coerceIn(0f, 1f)
            if (a <= MIN_ALPHA) {
                output[index] = 0
                continue
            }
            val inverse = 1f / a
            val outA = (a * 255f + .5f).toInt().coerceIn(0, 255)
            val outR = (red[index] * inverse * 255f + .5f).toInt().coerceIn(0, 255)
            val outG = (green[index] * inverse * 255f + .5f).toInt().coerceIn(0, 255)
            val outB = (blue[index] * inverse * 255f + .5f).toInt().coerceIn(0, 255)
            output[index] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }

    private companion object {
        const val DEFAULT_BUCKETS = 8
        const val MIN_ALPHA = 1f / 1024f
    }
}
