package app.inkloom.engine.v5.fill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncloseFillTest {
    private val side = 40
    private val black = 0xff000000.toInt()

    /** A square outline from (10,10) to (29,29), 2 px thick, with an optional gap in the top side. */
    private fun square(gap: Int = 0): IntArray = IntArray(side * side).also { pixels ->
        for (y in 10..29) for (x in 10..29) {
            val onLine = x <= 11 || x >= 28 || y <= 11 || y >= 28
            val inGap = gap > 0 && y <= 11 && x in 20 until 20 + gap
            if (onLine && !inGap) pixels[y * side + x] = black
        }
    }

    private fun lasso(left: Int, top: Int, right: Int, bottom: Int): ByteArray =
        PolygonMask.rasterize(floatArrayOf(left.toFloat(), right.toFloat(), right.toFloat(), left.toFloat()),
            floatArrayOf(top.toFloat(), top.toFloat(), bottom.toFloat(), bottom.toFloat()), side, side)

    private fun ByteArray.at(x: Int, y: Int) = this[y * side + x].toInt() and 0xff

    @Test
    fun fillsTheClosedRegionButNotTheOutsideTheLassoAlsoCovers() {
        val mask = EncloseFill.compute(square(), side, side, lasso(2, 2, 38, 38), EncloseFill.Options(underLinePixels = 0))
        assertEquals(255, mask.at(20, 20))
        assertEquals(0, mask.at(5, 5)) // outside the square reaches the lasso edge
        assertEquals(0, mask.at(10, 20)) // the line itself
    }

    @Test
    fun spreadsUnderTheLinesWithoutCrossingThem() {
        val mask = EncloseFill.compute(square(), side, side, lasso(2, 2, 38, 38), EncloseFill.Options(underLinePixels = 4))
        assertEquals(255, mask.at(11, 20))
        assertEquals(255, mask.at(10, 20))
        assertEquals(0, mask.at(9, 20)) // the empty side of the line stays empty
    }

    @Test
    fun aRegionCutByTheLassoIsLeftAlone() {
        val mask = EncloseFill.compute(square(), side, side, lasso(2, 2, 20, 38))
        assertTrue(mask.none { it.toInt() != 0 })
    }

    @Test
    fun closeGapSealsAShortBreakAndStillFillsToTheLine() {
        val open = EncloseFill.compute(square(gap = 3), side, side, lasso(2, 2, 38, 38), EncloseFill.Options(underLinePixels = 0))
        assertEquals(0, open.at(20, 20))
        val closed = EncloseFill.compute(square(gap = 3), side, side, lasso(2, 2, 38, 38),
            EncloseFill.Options(closeGapPixels = 4, underLinePixels = 0))
        assertEquals(255, closed.at(20, 20))
        assertEquals(255, closed.at(12, 20)) // grown back to the inner edge of the line
        assertEquals(0, closed.at(5, 5))
    }

    @Test
    fun antiAliasedFringeCountsAsUnderLine() {
        val pixels = square()
        pixels[20 * side + 12] = 0x40000000 // a faint fringe inside the left side
        val mask = EncloseFill.compute(pixels, side, side, lasso(2, 2, 38, 38), EncloseFill.Options(underLinePixels = 0))
        assertEquals(255, mask.at(12, 20)) // below the line threshold, so part of the region
    }

    @Test
    fun inkRuleReadsDarkLinesOnAnOpaquePage() {
        val pixels = square().map { if (it == 0) 0xfffafaf6.toInt() else 0xff202020.toInt() }.toIntArray()
        val alpha = EncloseFill.compute(pixels, side, side, lasso(2, 2, 38, 38))
        assertTrue(alpha.none { it.toInt() != 0 }) // every pixel is opaque, so all of it reads as line
        val ink = EncloseFill.compute(pixels, side, side, lasso(2, 2, 38, 38),
            EncloseFill.Options(lineRule = EncloseFill.LineRule.INK, underLinePixels = 4))
        assertEquals(255, ink.at(20, 20))
        assertEquals(255, ink.at(10, 20))
        assertEquals(0, ink.at(9, 20)) // the paper outside is not entered
    }

    @Test
    fun polygonMaskUsesPixelCentres() {
        val mask = PolygonMask.rasterize(floatArrayOf(1f, 4f, 4f, 1f), floatArrayOf(1f, 1f, 3f, 3f), side, side)
        assertEquals(255, mask.at(1, 1))
        assertEquals(255, mask.at(3, 2))
        assertEquals(0, mask.at(4, 2))
        assertEquals(0, mask.at(1, 3))
    }

    @Test
    fun growthStaysInsideAllowedPixels() {
        val width = 9
        val mask = BooleanArray(width * width).also { it[4 * width + 4] = true }
        val allowed = BooleanArray(width * width) { it % width != 6 } // a wall at x = 6
        MaskGrowth.grow(mask, width, width, 4, allowed)
        assertTrue(mask[4 * width + 5])
        assertFalse(mask[4 * width + 6])
        assertFalse(mask[4 * width + 7])
        assertTrue(mask[4 * width + 1])
        assertFalse(mask[0]) // corners are beyond an octagon of radius 4
    }
}
