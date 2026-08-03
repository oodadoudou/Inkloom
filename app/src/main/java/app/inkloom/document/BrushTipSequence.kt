package app.inkloom.document

/** CSP's Stroke > Repeat method values used by SUT brushes. */
object BrushTipSequence {
    const val REPEAT = 0
    const val REVERSE = 1
    const val DO_NOT_REPEAT = 2
    const val RANDOM = 3
    const val ONE_TIME_ONLY = 4
    const val ONE_RANDOM_CYCLE = 5

    fun index(mode: Int, stampIndex: Int, count: Int, seed: Int, ribbon: Boolean = false): Int? {
        if (count <= 0 || stampIndex < 0) return null
        if (count == 1) return if (!ribbon && mode in setOf(ONE_TIME_ONLY, ONE_RANDOM_CYCLE) && stampIndex > 0) null else 0
        return when (mode) {
            REVERSE -> {
                val period = count * 2 - 2
                val position = stampIndex % period
                if (position < count) position else period - position
            }
            DO_NOT_REPEAT -> stampIndex.coerceAtMost(count - 1)
            RANDOM -> java.util.Random(seed.toLong() xor stampIndex.toLong() * 0x9E3779B9L).nextInt(count)
            ONE_TIME_ONLY -> stampIndex.takeIf { it < count }
            ONE_RANDOM_CYCLE -> if (ribbon) shuffledIndex(stampIndex % count, count, seed) else stampIndex.takeIf { it < count }?.let { shuffledIndex(it, count, seed) }
            else -> stampIndex % count
        }
    }

    /** True while Reverse repeat is traversing the material list backwards. */
    fun isReversed(mode: Int, stampIndex: Int, count: Int): Boolean {
        if (mode != REVERSE || stampIndex < 0 || count < 2) return false
        val period = count * 2 - 2
        return stampIndex % period >= count
    }

    private fun shuffledIndex(position: Int, count: Int, seed: Int): Int {
        val values = IntArray(count) { it }
        val random = java.util.Random(seed.toLong())
        for (index in values.lastIndex downTo 1) {
            val swap = random.nextInt(index + 1)
            val value = values[index]
            values[index] = values[swap]
            values[swap] = value
        }
        return values[position]
    }
}
