package app.inkloom.engine.v5.fill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GapCheckTest {
    private val side = 60
    private val black = 0xff000000.toInt()

    /** A square outline (12..47), [thickness] px, with a break of [gap] px in the top side at x = 28. */
    private fun square(gap: Int, thickness: Int = 2): IntArray = IntArray(side * side).also { pixels ->
        for (y in 12..47) for (x in 12..47) {
            val onLine = x < 12 + thickness || x > 47 - thickness || y < 12 + thickness || y > 47 - thickness
            val inGap = y < 12 + thickness && x in 28 until 28 + gap
            if (onLine && !inGap) pixels[y * side + x] = black
        }
    }

    @Test
    fun aClosedOutlineHasNoGaps() {
        assertTrue(GapCheck.find(square(gap = 0), side, side, maxGapPixels = 8).isEmpty())
    }

    @Test
    fun findsTheBreakAndPlacesItThere() {
        val gaps = GapCheck.find(square(gap = 4), side, side, maxGapPixels = 8)
        assertEquals(1, gaps.size)
        val gap = gaps.single()
        assertTrue("x near the break: $gap", gap.x in 26f..34f)
        assertTrue("y near the top side: $gap", gap.y in 8f..18f)
    }

    @Test
    fun aBreakWiderThanTheCheckIsNotABreak() {
        // Too wide to close: the inside and outside are simply one area.
        assertTrue(GapCheck.find(square(gap = 16), side, side, maxGapPixels = 6).isEmpty())
    }

    @Test
    fun thickLinesStillShowTheirBreak() {
        val gaps = GapCheck.find(square(gap = 4, thickness = 8), side, side, maxGapPixels = 6)
        assertEquals(1, gaps.size)
    }
}
