package app.inkloom.engine.v5.material

import kotlin.math.floor

/** Immutable renderer-owned alpha coverage for one resolved brush shape. */
class BrushTipMask(
    val width: Int,
    val height: Int,
    alpha: ByteArray,
) {
    private val coverage = alpha.copyOf()

    init {
        require(width > 0 && height > 0)
        require(coverage.size == Math.multiplyExact(width, height))
    }

    val byteSize: Long get() = coverage.size.toLong()

    /** Bilinear coverage at normalized brush-local coordinates. Outside the shape is transparent. */
    fun coverageAt(u: Float, v: Float): Float {
        if (!u.isFinite() || !v.isFinite() || u < 0f || u > 1f || v < 0f || v > 1f) return 0f
        val x = u * (width - 1).coerceAtLeast(0)
        val y = v * (height - 1).coerceAtLeast(0)
        val x0 = floor(x).toInt().coerceIn(0, width - 1)
        val y0 = floor(y).toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val tx = x - x0
        val ty = y - y0
        val top = alphaAt(x0, y0) + (alphaAt(x1, y0) - alphaAt(x0, y0)) * tx
        val bottom = alphaAt(x0, y1) + (alphaAt(x1, y1) - alphaAt(x0, y1)) * tx
        return top + (bottom - top) * ty
    }

    fun contentHash(): Int = coverage.contentHashCode()

    private fun alphaAt(x: Int, y: Int): Float = (coverage[y * width + x].toInt() and 0xff) / 255f

    companion object {
        fun solidCircle(size: Int = 64, hardness: Float = 1f): BrushTipMask {
            require(size in 2..1024)
            require(hardness.isFinite() && hardness in 0f..1f)
            val values = ByteArray(size * size)
            val radius = size * .5f
            val edge = maxOf(1f, radius * (1f - hardness) * .85f)
            for (y in 0 until size) for (x in 0 until size) {
                val dx = x + .5f - radius
                val dy = y + .5f - radius
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                val value = ((radius + .5f - distance) / edge).coerceIn(0f, 1f)
                values[y * size + x] = (value * 255f + .5f).toInt().toByte()
            }
            return BrushTipMask(size, size, values)
        }
    }
}

