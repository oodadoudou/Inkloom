package app.inkloom.engine.v5.material

/** Immutable renderer-owned alpha coverage for one resolved brush shape. */
class BrushTipMask(
    val width: Int,
    val height: Int,
    alpha: ByteArray,
) {
    private val coverage = alpha.copyOf()
    private val mipLevels: List<MaskLevel>

    init {
        require(width > 0 && height > 0)
        require(coverage.size == Math.multiplyExact(width, height))
        mipLevels = buildMipLevels(width, height, coverage)
    }

    /** Rows [index]·h/[count] until ([index]+1)·h/[count]: one of [count] horizontal bands, top first. */
    fun band(index: Int, count: Int): BrushTipMask {
        require(count in 1..height && index in 0 until count) { "band $index of $count out of range for height $height" }
        val top = index * height / count
        val bottom = (index + 1) * height / count
        return BrushTipMask(width, bottom - top, coverage.copyOfRange(top * width, bottom * width))
    }

    val byteSize: Long get() = mipLevels.sumOf { it.coverage.size.toLong() }
    val maximumCoverage: Float get() = (coverage.maxOfOrNull { it.toInt() and 0xff } ?: 0) * BYTE_TO_UNIT

    /** Bilinear coverage at normalized brush-local coordinates. Outside the shape is transparent. */
    fun coverageAt(u: Float, v: Float): Float {
        return sample(mipLevels.first(), u, v)
    }

    /**
     * Area-preserving lookup for a source mask rendered smaller than its decoded dimensions.
     * Selecting a prefiltered level prevents thin freckles, fibres, and decoration marks from
     * disappearing between bilinear sample points at tablet-sized brush diameters.
     */
    fun coverageAtForOutput(u: Float, v: Float, outputWidthPixels: Float, outputHeightPixels: Float): Float {
        return coverageAtLevel(u, v, levelForOutput(outputWidthPixels, outputHeightPixels))
    }

    fun levelForOutput(outputWidthPixels: Float, outputHeightPixels: Float): Int {
        if (!outputWidthPixels.isFinite() || !outputHeightPixels.isFinite() ||
            outputWidthPixels <= 0f || outputHeightPixels <= 0f
        ) return 0
        val reduction = maxOf(width / outputWidthPixels, height / outputHeightPixels)
        var levelIndex = 0
        var scale = 1f
        while (levelIndex + 1 < mipLevels.size && scale * 2f <= reduction) {
            levelIndex++
            scale *= 2f
        }
        return levelIndex
    }

    fun coverageAtLevel(u: Float, v: Float, levelIndex: Int): Float =
        sample(mipLevels[levelIndex.coerceIn(0, mipLevels.lastIndex)], u, v)

    fun coverageAtPixel(localX: Float, localY: Float, radiusX: Float, radiusY: Float,
                                 cosine: Float, sine: Float, level: Int): Float {
        val u = (localX / radiusX + 1f) * .5f
        val v = (localY / radiusY + 1f) * .5f
        val footprint = kotlin.math.abs(cosine) + kotlin.math.abs(sine)
        // Interior samples retain the authored texture. Only the source rectangle's edge
        // needs pixel footprint coverage, even when the stamp is larger than two pixels.
        if (kotlin.math.abs(localX) <= radiusX - footprint * .5f &&
            kotlin.math.abs(localY) <= radiusY - footprint * .5f) return coverageAtLevel(u, v, level)
        fun overlap(position: Float, radius: Float): Float =
            (minOf(position + footprint * .5f, radius) - maxOf(position - footprint * .5f, -radius))
                .coerceAtLeast(0f) / footprint
        val area = overlap(localX, radiusX) * overlap(localY, radiusY)
        if (area <= 0f) return 0f
        return coverageAtLevel(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f), level) * area
    }

    /** Zero-copy immutable view used only while a bounded native raster call is active. */
    fun nativeLevel(levelIndex: Int): NativeLevel =
        mipLevels[levelIndex.coerceIn(0, mipLevels.lastIndex)].let {
            NativeLevel(it.width, it.height, it.coverage)
        }

    private fun sample(level: MaskLevel, u: Float, v: Float): Float {
        // Ordered comparisons reject NaN/infinity as well as the transparent exterior, without
        // Float.isFinite's Math.abs call on every sampled pixel.
        if (!(u >= 0f && u <= 1f && v >= 0f && v <= 1f)) return 0f
        // u/v are non-negative after the guard, so Float.toInt() is the same floor operation
        // without the Math.floor/coerce chain that used to run for every stamp pixel.
        val x = u * (level.width - 1)
        val y = v * (level.height - 1)
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = if (x0 + 1 < level.width) x0 + 1 else x0
        val y1 = if (y0 + 1 < level.height) y0 + 1 else y0
        val tx = x - x0
        val ty = y - y0
        val topLeft = level.coverage[y0 * level.width + x0].toInt() and 0xff
        val topRight = level.coverage[y0 * level.width + x1].toInt() and 0xff
        val bottomLeft = level.coverage[y1 * level.width + x0].toInt() and 0xff
        val bottomRight = level.coverage[y1 * level.width + x1].toInt() and 0xff
        val top = topLeft + (topRight - topLeft) * tx
        val bottom = bottomLeft + (bottomRight - bottomLeft) * tx
        return (top + (bottom - top) * ty) * BYTE_TO_UNIT
    }

    fun contentHash(): Int = coverage.contentHashCode()

    private data class MaskLevel(val width: Int, val height: Int, val coverage: ByteArray)
    data class NativeLevel(val width: Int, val height: Int, val coverage: ByteArray)

    companion object {
        private const val BYTE_TO_UNIT = 1f / 255f

        private fun buildMipLevels(width: Int, height: Int, base: ByteArray): List<MaskLevel> = buildList {
            var current = MaskLevel(width, height, base)
            add(current)
            while (current.width > 1 || current.height > 1) {
                val nextWidth = (current.width + 1) / 2
                val nextHeight = (current.height + 1) / 2
                val next = ByteArray(nextWidth * nextHeight)
                for (y in 0 until nextHeight) for (x in 0 until nextWidth) {
                    var sum = 0
                    var count = 0
                    for (offsetY in 0..1) for (offsetX in 0..1) {
                        val sourceX = x * 2 + offsetX
                        val sourceY = y * 2 + offsetY
                        if (sourceX < current.width && sourceY < current.height) {
                            sum += current.coverage[sourceY * current.width + sourceX].toInt() and 0xff
                            count++
                        }
                    }
                    // Round up non-zero area coverage so a sub-pixel authored mark remains
                    // representable instead of being quantized to transparent in deeper levels.
                    next[y * nextWidth + x] = ((sum + count - 1) / count).toByte()
                }
                current = MaskLevel(nextWidth, nextHeight, next)
                add(current)
            }
        }

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
        fun graphite(size: Int = 96, seed: Int, toothStrength: Float, hardness: Float = 1f): BrushTipMask {
            require(size in 8..1024)
            require(toothStrength.isFinite() && toothStrength in 0f..1f)
            require(hardness.isFinite() && hardness in 0f..1f)
            val edgeWidth = .14f + (1f - hardness) * .72f
            val values = ByteArray(size * size)
            val radius = size * .5f
            for (y in 0 until size) for (x in 0 until size) {
                val dx = (x + .5f - radius) / radius
                val dy = (y + .5f - radius) / radius
                val radial = kotlin.math.sqrt(dx * dx + dy * dy)
                val edge = ((1.04f - radial) / edgeWidth).coerceIn(0f, 1f)
                val noise = noise01(x, y, seed)
                val tooth = (1f - toothStrength * (.18f + noise * .72f)).coerceIn(0f, 1f)
                values[y * size + x] = (255f * edge * tooth + .5f).toInt().toByte()
            }
            return BrushTipMask(size, size, values)
        }

        /** Directional multi-fibre footprint for the first-party dry painting brush. */
        fun bristle(size: Int = 96, seed: Int, hardness: Float = 1f): BrushTipMask {
            require(size in 8..1024)
            require(hardness.isFinite() && hardness in 0f..1f)
            val edgeWidth = .16f + (1f - hardness) * .7f
            val values = ByteArray(size * size)
            val radius = size * .5f
            for (y in 0 until size) for (x in 0 until size) {
                val nx = (x + .5f - radius) / radius
                val ny = (y + .5f - radius) / radius
                val body = ((1f - maxOf(kotlin.math.abs(nx), kotlin.math.abs(ny))) / edgeWidth).coerceIn(0f, 1f)
                val fibre = .34f + .66f * kotlin.math.abs(
                    kotlin.math.sin((x + seed.and(31)) * .43f + noise01(x / 5, 0, seed) * 2.1f),
                )
                val brokenEdge = (.7f + .3f * noise01(x, y / 4, seed xor 0x51A7)).coerceIn(0f, 1f)
                values[y * size + x] = (255f * body * fibre * brokenEdge + .5f).toInt().toByte()
            }
            return BrushTipMask(size, size, values)
        }

        /** The first-party procedural footprint for [kind], as every renderer and preview builds it. */
        fun procedural(kind: app.inkloom.brushpresetv5.ProceduralTipKind, size: Int, seed: Int, toothStrength: Float, hardness: Float): BrushTipMask =
            when (kind) {
                app.inkloom.brushpresetv5.ProceduralTipKind.ROUND -> solidCircle(size, hardness)
                app.inkloom.brushpresetv5.ProceduralTipKind.GRAPHITE -> graphite(size, seed, toothStrength, hardness)
                app.inkloom.brushpresetv5.ProceduralTipKind.BRISTLE -> bristle(size, seed, hardness)
                app.inkloom.brushpresetv5.ProceduralTipKind.CHALK -> chalk(size, seed, toothStrength, hardness)
            }

        /**
         * Wax crayon / soft pastel stick: a lumpy rim (low-order angular waves) and blotchy skips from
         * two octaves of smooth value noise. [toothStrength] sets how much of the stick skips.
         */
        fun chalk(size: Int = 96, seed: Int, toothStrength: Float, hardness: Float = 1f): BrushTipMask {
            require(size in 8..1024)
            require(toothStrength.isFinite() && toothStrength in 0f..1f)
            require(hardness.isFinite() && hardness in 0f..1f)
            val edgeWidth = .08f + (1f - hardness) * .6f
            val phase = FloatArray(3) { noise01(it, 7, seed) * 6.2831855f }
            val threshold = .2f + .4f * toothStrength
            val values = ByteArray(size * size)
            val radius = size * .5f
            for (y in 0 until size) for (x in 0 until size) {
                val nx = (x + .5f - radius) / radius
                val ny = (y + .5f - radius) / radius
                val distance = kotlin.math.sqrt(nx * nx + ny * ny)
                val angle = kotlin.math.atan2(ny, nx)
                val rim = .92f + .06f * kotlin.math.sin(3f * angle + phase[0]) + .04f * kotlin.math.sin(5f * angle + phase[1]) +
                    .025f * kotlin.math.sin(9f * angle + phase[2])
                val edge = ((rim - distance) / edgeWidth).coerceIn(0f, 1f)
                if (edge <= 0f) continue
                val blot = smoothNoise(x / 3.5f, y / 3.5f, seed) * .55f + smoothNoise(x / 1.4f, y / 1.4f, seed xor 0x2C1B) * .45f
                val wax = ((blot - threshold + .12f) / .24f).coerceIn(0f, 1f)
                val waxSmooth = wax * wax * (3f - 2f * wax)
                values[y * size + x] = (255f * edge * (.18f + .82f * waxSmooth) + .5f).toInt().toByte()
            }
            return BrushTipMask(size, size, values)
        }

        private fun smoothNoise(x: Float, y: Float, seed: Int): Float {
            val x0 = kotlin.math.floor(x).toInt(); val y0 = kotlin.math.floor(y).toInt()
            val fx = x - x0; val fy = y - y0
            val sx = fx * fx * (3f - 2f * fx); val sy = fy * fy * (3f - 2f * fy)
            val top = noise01(x0, y0, seed) + (noise01(x0 + 1, y0, seed) - noise01(x0, y0, seed)) * sx
            val bottom = noise01(x0, y0 + 1, seed) + (noise01(x0 + 1, y0 + 1, seed) - noise01(x0, y0 + 1, seed)) * sx
            return top + (bottom - top) * sy
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
