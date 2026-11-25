package app.inkloom.engine.v5.fill

/** Rasterizes a closed lasso into a hard mask, sampling at pixel centres with the non-zero winding rule. */
object PolygonMask {
    /** [xs] and [ys] are the lasso's vertices in pixels; the last vertex joins the first. 255 inside, 0 outside. */
    fun rasterize(xs: FloatArray, ys: FloatArray, width: Int, height: Int): ByteArray {
        require(xs.size == ys.size) { "xs and ys differ in length" }
        require(width > 0 && height > 0) { "empty raster" }
        val mask = ByteArray(width * height)
        val count = xs.size
        if (count < 3) return mask
        val crossings = FloatArray(count)
        val windings = IntArray(count)
        val order = IntArray(count)
        for (y in 0 until height) {
            val sampleY = y + .5f
            var found = 0
            for (edge in 0 until count) {
                val x0 = xs[edge]; val y0 = ys[edge]
                val x1 = xs[(edge + 1) % count]; val y1 = ys[(edge + 1) % count]
                val upward = y0 <= sampleY && y1 > sampleY
                val downward = y1 <= sampleY && y0 > sampleY
                if (!upward && !downward) continue
                crossings[found] = x0 + (sampleY - y0) / (y1 - y0) * (x1 - x0)
                windings[found] = if (upward) 1 else -1
                found += 1
            }
            if (found < 2) continue
            for (at in 0 until found) order[at] = at
            // Insertion sort: a scanline crosses a hand-drawn lasso only a handful of times.
            for (at in 1 until found) {
                val current = order[at]
                var back = at - 1
                while (back >= 0 && crossings[order[back]] > crossings[current]) { order[back + 1] = order[back]; back -= 1 }
                order[back + 1] = current
            }
            var winding = 0
            for (at in 0 until found - 1) {
                winding += windings[order[at]]
                if (winding == 0) continue
                val from = kotlin.math.ceil(crossings[order[at]] - .5f).toInt().coerceAtLeast(0)
                val to = kotlin.math.floor(crossings[order[at + 1]] - .5f).toInt().coerceAtMost(width - 1)
                for (x in from..to) mask[y * width + x] = 255.toByte()
            }
        }
        return mask
    }
}
