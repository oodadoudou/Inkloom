package app.inkloom.engine.v5.fill

/**
 * Close-and-fill without gaps (CSP's Enclose and fill, done the way the popular "fill without gaps"
 * assets set it up). Within an enclosure the user draws, every region that the reference's lines
 * close off and that lies wholly inside the enclosure is filled. Gaps in the lines up to
 * [Options.closeGapPixels] wide count as closed, and the fill then spreads [Options.underLinePixels]
 * under the lines around it, so no light seam shows between colour and line.
 *
 * The same mask serves the erase twin: erase it from the colour layer instead of painting it.
 * Pure pixel-buffer code, so Inkloom and 豆豆像素 share it.
 */
object EncloseFill {
    /** How a reference pixel's line strength is read. */
    enum class LineRule {
        /** Its alpha: line art on a transparent layer. */
        ALPHA,
        /** Its darkness times its alpha: line art on an opaque page, such as a scan. */
        INK,
    }

    data class Options(
        /** Reference pixels with at least this line strength (1..255) are lines. */
        val lineAlpha: Int = 128,
        val lineRule: LineRule = LineRule.ALPHA,
        /** Gaps in the lines up to this many pixels wide are treated as closed. */
        val closeGapPixels: Int = 0,
        /** How far the fill spreads under neighbouring line pixels. */
        val underLinePixels: Int = 2,
    ) {
        init {
            require(lineAlpha in 1..255) { "lineAlpha must be 1..255" }
            require(closeGapPixels in 0..MAX_PIXELS) { "closeGapPixels must be 0..$MAX_PIXELS" }
            require(underLinePixels in 0..MAX_PIXELS) { "underLinePixels must be 0..$MAX_PIXELS" }
        }
    }

    /**
     * [reference] holds unpremultiplied ARGB pixels, read by [Options.lineRule]; [enclosure] the user's enclosure, inside where
     * alpha ≥ 128. Returns a mask the same size: 255 where to fill, 0 elsewhere.
     */
    fun compute(reference: IntArray, width: Int, height: Int, enclosure: ByteArray, options: Options = Options()): ByteArray {
        require(width > 0 && height > 0) { "empty raster" }
        val count = width * height
        require(reference.size == count && enclosure.size == count) { "buffers do not match $width x $height" }
        val inside = BooleanArray(count) { (enclosure[it].toInt() and 0xff) >= 128 }
        val strength = IntArray(count) { lineStrength(reference[it], options.lineRule) }
        val line = BooleanArray(count) { strength[it] >= options.lineAlpha }
        val radius = (options.closeGapPixels + 1) / 2
        val closed = if (radius > 0) line.copyOf().also { MaskGrowth.grow(it, width, height, radius) } else line
        val fill = enclosedRegions(closed, inside, width, height)
        // Gap closing shrank each region away from its lines; grow it back up to the real lines.
        if (radius > 0) {
            val open = BooleanArray(count) { inside[it] && !line[it] }
            MaskGrowth.grow(fill, width, height, radius, open)
        }
        if (options.underLinePixels > 0) {
            // Only line pixels (anti-aliasing included) are entered, so the fill never reaches the
            // empty region on the far side of a line. Paper grain in a scan is not a line.
            val floor = if (options.lineRule == LineRule.INK) INK_FLOOR else 1
            val underLine = BooleanArray(count) { inside[it] && strength[it] >= floor }
            MaskGrowth.grow(fill, width, height, options.underLinePixels, underLine)
        }
        return ByteArray(count) { if (fill[it]) 255.toByte() else 0 }
    }

    /** 0..255: how strongly [argb] reads as line under [rule]. */
    fun lineStrength(argb: Int, rule: LineRule): Int {
        val alpha = argb ushr 24
        if (rule == LineRule.ALPHA) return alpha
        val luma = (54 * (argb ushr 16 and 0xff) + 183 * (argb ushr 8 and 0xff) + 19 * (argb and 0xff)) ushr 8
        return alpha * (255 - luma) / 255
    }

    /** The 4-connected regions of non-[closed] pixels that lie wholly inside the enclosure. */
    private fun enclosedRegions(closed: BooleanArray, inside: BooleanArray, width: Int, height: Int): BooleanArray {
        val count = width * height
        val label = IntArray(count)
        val accepted = BooleanArray(count)
        val stack = IntList(1024)
        val members = IntList(1024)
        var next = 1
        for (start in 0 until count) {
            if (closed[start] || label[start] != 0 || !inside[start]) continue
            val id = next++
            var whollyInside = true
            stack.clear(); members.clear()
            label[start] = id
            stack.add(start)
            while (stack.size > 0) {
                val index = stack.pop()
                members.add(index)
                if (!inside[index]) whollyInside = false
                val x = index % width
                if (x > 0) visit(index - 1, id, closed, label, stack)
                if (x < width - 1) visit(index + 1, id, closed, label, stack)
                if (index >= width) visit(index - width, id, closed, label, stack)
                if (index + width < count) visit(index + width, id, closed, label, stack)
            }
            if (whollyInside) for (at in 0 until members.size) accepted[members[at]] = true
        }
        return accepted
    }

    private fun visit(index: Int, id: Int, closed: BooleanArray, label: IntArray, stack: IntList) {
        if (closed[index] || label[index] != 0) return
        label[index] = id
        stack.add(index)
    }

    const val MAX_PIXELS = 64
    private const val INK_FLOOR = 24
}
