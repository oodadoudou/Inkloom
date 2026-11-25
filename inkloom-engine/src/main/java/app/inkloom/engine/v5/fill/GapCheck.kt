package app.inkloom.engine.v5.fill

/**
 * Gap check (in the manner of the popular 隙間チェック assets): finds the breaks in line art that a
 * fill would leak through. Two areas count as leaking into each other when closing gaps up to
 * [maxGapPixels] would separate them; the places where they meet across the break are the gaps.
 * A neck narrower than [maxGapPixels] inside one area is reported too: a fill with that close gap
 * stops there. Pure pixel-buffer code, shared by Inkloom and 豆豆像素.
 */
object GapCheck {
    /** A break centred at ([x], [y]) in pixels, about [radius] pixels across from its centre. */
    data class Gap(val x: Float, val y: Float, val radius: Float)

    fun find(
        reference: IntArray,
        width: Int,
        height: Int,
        maxGapPixels: Int,
        lineRule: EncloseFill.LineRule = EncloseFill.LineRule.ALPHA,
        lineAlpha: Int = 128,
        minAreaPixels: Int = 64,
    ): List<Gap> {
        require(width > 0 && height > 0 && reference.size == width * height) { "buffer does not match $width x $height" }
        require(maxGapPixels in 1..EncloseFill.MAX_PIXELS) { "maxGapPixels must be 1..${EncloseFill.MAX_PIXELS}" }
        require(lineAlpha in 1..255 && minAreaPixels >= 1) { "lineAlpha or minAreaPixels out of range" }
        val count = width * height
        val line = BooleanArray(count) { EncloseFill.lineStrength(reference[it], lineRule) >= lineAlpha }
        val radius = (maxGapPixels + 1) / 2
        val closed = line.copyOf().also { MaskGrowth.grow(it, width, height, radius) }
        // Areas once gaps are closed; only areas big enough to be filled take part.
        val owner = IntArray(count) { -1 }
        val sizes = labelAreas(closed, width, height, owner)
        for (index in 0 until count) if (owner[index] >= 0 && sizes[owner[index]] < minAreaPixels) owner[index] = -1
        // Grow every area back towards its lines at the same speed; where two meet, a gap joins them.
        // Only the band left by gap closing (and areas too small to count) is unowned, so this ends.
        val meeting = BooleanArray(count)
        var frontier = IntList(1024)
        for (index in 0 until count) if (owner[index] >= 0 && touchesUnowned(index, owner, line, width, height)) frontier.add(index)
        while (frontier.size > 0) {
            val next = IntList(frontier.size)
            for (at in 0 until frontier.size) {
                val index = frontier[at]
                val x = index % width
                if (x > 0) reach(index, index - 1, owner, line, meeting, next)
                if (x < width - 1) reach(index, index + 1, owner, line, meeting, next)
                if (index >= width) reach(index, index - width, owner, line, meeting, next)
                if (index + width < count) reach(index, index + width, owner, line, meeting, next)
            }
            frontier = next
        }
        return clusters(meeting, width, height)
    }

    private fun reach(from: Int, to: Int, owner: IntArray, line: BooleanArray, meeting: BooleanArray, next: IntList) {
        if (line[to]) return
        val current = owner[to]
        if (current < 0) {
            owner[to] = owner[from]
            next.add(to)
        } else if (current != owner[from]) {
            meeting[to] = true
            meeting[from] = true
        }
    }

    private fun touchesUnowned(index: Int, owner: IntArray, line: BooleanArray, width: Int, height: Int): Boolean {
        val x = index % width
        val y = index / width
        fun open(other: Int) = owner[other] < 0 && !line[other]
        return (x > 0 && open(index - 1)) || (x < width - 1 && open(index + 1)) ||
            (y > 0 && open(index - width)) || (y < height - 1 && open(index + width))
    }

    /** Labels the 4-connected areas of non-[closed] pixels into [owner]; returns each area's size. */
    private fun labelAreas(closed: BooleanArray, width: Int, height: Int, owner: IntArray): IntArray {
        val count = width * height
        var sizes = IntArray(64)
        var next = 0
        val stack = IntList(1024)
        for (start in 0 until count) {
            if (closed[start] || owner[start] >= 0) continue
            val id = next++
            if (id == sizes.size) sizes = sizes.copyOf(sizes.size * 2)
            owner[start] = id
            stack.clear(); stack.add(start)
            var size = 0
            while (stack.size > 0) {
                val index = stack.pop()
                size += 1
                val x = index % width
                if (x > 0 && !closed[index - 1] && owner[index - 1] < 0) { owner[index - 1] = id; stack.add(index - 1) }
                if (x < width - 1 && !closed[index + 1] && owner[index + 1] < 0) { owner[index + 1] = id; stack.add(index + 1) }
                if (index >= width && !closed[index - width] && owner[index - width] < 0) { owner[index - width] = id; stack.add(index - width) }
                if (index + width < count && !closed[index + width] && owner[index + width] < 0) { owner[index + width] = id; stack.add(index + width) }
            }
            sizes[id] = size
        }
        return sizes
    }

    /** One gap per 8-connected cluster of meeting pixels: its centre and half its extent. */
    private fun clusters(meeting: BooleanArray, width: Int, height: Int): List<Gap> {
        val seen = BooleanArray(meeting.size)
        val stack = IntList(64)
        val gaps = ArrayList<Gap>()
        for (start in meeting.indices) {
            if (!meeting[start] || seen[start]) continue
            seen[start] = true
            stack.clear(); stack.add(start)
            var sumX = 0L; var sumY = 0L; var count = 0
            var left = width; var top = height; var right = -1; var bottom = -1
            while (stack.size > 0) {
                val index = stack.pop()
                val x = index % width; val y = index / width
                sumX += x; sumY += y; count += 1
                if (x < left) left = x; if (x > right) right = x
                if (y < top) top = y; if (y > bottom) bottom = y
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                    val neighbour = ny * width + nx
                    if (meeting[neighbour] && !seen[neighbour]) { seen[neighbour] = true; stack.add(neighbour) }
                }
            }
            val extent = maxOf(right - left, bottom - top) + 1
            gaps += Gap(sumX.toFloat() / count + .5f, sumY.toFloat() / count + .5f, extent / 2f)
        }
        return gaps
    }
}
