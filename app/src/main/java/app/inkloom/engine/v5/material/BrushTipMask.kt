package app.inkloom.engine.v5.material

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
    val maximumCoverage: Float get() = (coverage.maxOfOrNull { it.toInt() and 0xff } ?: 0) * BYTE_TO_UNIT

    /** Bilinear coverage at normalized brush-local coordinates. Outside the shape is transparent. */
    fun coverageAt(u: Float, v: Float): Float {
        if (!u.isFinite() || !v.isFinite() || u < 0f || u > 1f || v < 0f || v > 1f) return 0f
        // u/v are non-negative after the guard, so Float.toInt() is the same floor operation
        // without the Math.floor/coerce chain that used to run for every stamp pixel.
        val x = u * (width - 1)
        val y = v * (height - 1)
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = if (x0 + 1 < width) x0 + 1 else x0
        val y1 = if (y0 + 1 < height) y0 + 1 else y0
        val tx = x - x0
        val ty = y - y0
        val topLeft = coverage[y0 * width + x0].toInt() and 0xff
        val topRight = coverage[y0 * width + x1].toInt() and 0xff
        val bottomLeft = coverage[y1 * width + x0].toInt() and 0xff
        val bottomRight = coverage[y1 * width + x1].toInt() and 0xff
        val top = topLeft + (topRight - topLeft) * tx
        val bottom = bottomLeft + (bottomRight - bottomLeft) * tx
        return (top + (bottom - top) * ty) * BYTE_TO_UNIT
    }

    fun contentHash(): Int = coverage.contentHashCode()

    companion object {
        private const val BYTE_TO_UNIT = 1f / 255f

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

        /** Stable graphite tooth: irregular edge plus sparse internal deposition gaps. */
        fun graphite(size: Int = 96, seed: Int, toothStrength: Float): BrushTipMask {
            require(size in 8..1024)
            require(toothStrength.isFinite() && toothStrength in 0f..1f)
            val values = ByteArray(size * size)
            val radius = size * .5f
            for (y in 0 until size) for (x in 0 until size) {
                val dx = (x + .5f - radius) / radius
                val dy = (y + .5f - radius) / radius
                val radial = kotlin.math.sqrt(dx * dx + dy * dy)
                val edge = ((1.04f - radial) / .14f).coerceIn(0f, 1f)
                val noise = noise01(x, y, seed)
                val tooth = (1f - toothStrength * (.18f + noise * .72f)).coerceIn(0f, 1f)
                values[y * size + x] = (255f * edge * tooth + .5f).toInt().toByte()
            }
            return BrushTipMask(size, size, values)
        }

        /** Directional multi-fibre footprint for the first-party dry painting brush. */
        fun bristle(size: Int = 96, seed: Int): BrushTipMask {
            require(size in 8..1024)
            val values = ByteArray(size * size)
            val radius = size * .5f
            for (y in 0 until size) for (x in 0 until size) {
                val nx = (x + .5f - radius) / radius
                val ny = (y + .5f - radius) / radius
                val body = ((1f - maxOf(kotlin.math.abs(nx), kotlin.math.abs(ny))) / .16f).coerceIn(0f, 1f)
                val fibre = .34f + .66f * kotlin.math.abs(
                    kotlin.math.sin((x + seed.and(31)) * .43f + noise01(x / 5, 0, seed) * 2.1f),
                )
                val brokenEdge = (.7f + .3f * noise01(x, y / 4, seed xor 0x51A7)).coerceIn(0f, 1f)
                values[y * size + x] = (255f * body * fibre * brokenEdge + .5f).toInt().toByte()
            }
            return BrushTipMask(size, size, values)
        }

        private fun noise01(x: Int, y: Int, seed: Int): Float {
            var value = x * 0x1f1f1f1f + y * 0x45d9f3b + seed
            value = value xor (value ushr 16)
            value *= 0x45d9f3b
            value = value xor (value ushr 16)
            return (value and 0xffff) / 65535f
        }
    }
}
