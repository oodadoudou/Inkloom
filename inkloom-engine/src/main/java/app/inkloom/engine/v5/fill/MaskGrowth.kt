package app.inkloom.engine.v5.fill

/**
 * Grows a pixel set outward in steps, alternating 4- and 8-neighbour steps so the growth front is a
 * near-round octagon rather than a square. Used for gap closing and for spreading fills under lines.
 */
object MaskGrowth {
    /**
     * Adds to [mask] every pixel reachable within [steps] steps of it. When [allowed] is given, growth
     * only enters pixels where it is true (a geodesic growth that cannot cross disallowed pixels).
     */
    fun grow(mask: BooleanArray, width: Int, height: Int, steps: Int, allowed: BooleanArray? = null) {
        require(width > 0 && height > 0 && mask.size == width * height) { "mask size does not match $width x $height" }
        require(allowed == null || allowed.size == mask.size) { "allowed size does not match the mask" }
        if (steps <= 0) return
        var frontier = boundary(mask, width, height)
        var step = 1
        while (step <= steps && frontier.size > 0) {
            val diagonal = step % 2 == 0
            val next = IntList(frontier.size * 2)
            for (at in 0 until frontier.size) {
                val index = frontier[at]
                val x = index % width
                val y = index / width
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    if (!diagonal && dx != 0 && dy != 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                    val neighbour = ny * width + nx
                    if (mask[neighbour] || (allowed != null && !allowed[neighbour])) continue
                    mask[neighbour] = true
                    next.add(neighbour)
                }
            }
            // Pixels that could only grow diagonally keep their turn for the next diagonal step.
            frontier = if (diagonal) next else next.also { it.addAll(frontier) }
            step += 1
        }
    }

    private fun boundary(mask: BooleanArray, width: Int, height: Int): IntList {
        val result = IntList(256)
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            if (!mask[index]) continue
            val edge = (x > 0 && !mask[index - 1]) || (x < width - 1 && !mask[index + 1]) ||
                (y > 0 && !mask[index - width]) || (y < height - 1 && !mask[index + width]) ||
                (x > 0 && y > 0 && !mask[index - width - 1]) || (x < width - 1 && y > 0 && !mask[index - width + 1]) ||
                (x > 0 && y < height - 1 && !mask[index + width - 1]) || (x < width - 1 && y < height - 1 && !mask[index + width + 1])
            if (edge) result.add(index)
        }
        return result
    }
}

/** A growable list of ints without boxing. */
class IntList(capacity: Int = 16) {
    private var values = IntArray(capacity.coerceAtLeast(4))
    var size: Int = 0
        private set

    operator fun get(index: Int): Int = values[index]

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = value
    }

    fun addAll(other: IntList) {
        for (at in 0 until other.size) add(other[at])
    }

    fun pop(): Int = values[--size]

    fun clear() { size = 0 }
}
