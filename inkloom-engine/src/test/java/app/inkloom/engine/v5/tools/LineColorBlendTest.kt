package app.inkloom.engine.v5.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineColorBlendTest {
    private val side = 30
    private val black = 0xff000000.toInt()
    private val red = 0xffe05050.toInt()
    private val blue = 0xff4070e0.toInt()

    /** A vertical line at x = 14..15; red paint left of x = 15 under it, blue right of it, nothing below y = 20. */
    private fun scene(): Pair<IntArray, IntArray> {
        val lines = IntArray(side * side) { if (it % side in 14..15) black else 0 }
        val colours = IntArray(side * side) { val x = it % side; val y = it / side; if (y >= 20) 0 else if (x <= 14) red else blue }
        return lines to colours
    }

    @Test
    fun linesTakeADarkerShadeOfTheColourBeneath() {
        val (lines, colours) = scene()
        val out = LineColorBlend.render(lines, colours, side, side)
        val onRed = out[5 * side + 14]; val onBlue = out[5 * side + 15]
        assertTrue("red side is a dark red: ${Integer.toHexString(onRed)}", (onRed ushr 16 and 255) > (onRed and 255) + 40 && (onRed ushr 16 and 255) < 0xe0)
        assertTrue("blue side is a dark blue: ${Integer.toHexString(onBlue)}", (onBlue and 255) > (onBlue ushr 16 and 255) + 40 && (onBlue and 255) < 0xe0)
        assertEquals("only line pixels are painted", 0, out[5 * side + 3])
    }

    @Test
    fun linesReachForTheNearestColourOnlySoFar() {
        val (lines, colours) = scene()
        val near = LineColorBlend.render(lines, colours, side, side, LineColorBlend.Settings(reach = 4))
        assertTrue("just past the paint, a line still finds it", near[22 * side + 14] != 0)
        assertEquals("far from any paint, the line keeps its own colour", 0, near[28 * side + 14])
    }

    @Test
    fun shadeDarkensAndEnriches() {
        val shade = LineColorBlend.shade(0xff80a0c0.toInt(), LineColorBlend.Settings(darken = .5f, saturate = .2f))
        assertTrue(Integer.toHexString(shade), (shade and 255) < 0xc0 && (shade and 255) > 0x40)
        assertEquals("grey stays grey", 0xff404040.toInt(), LineColorBlend.shade(0xff808080.toInt(), LineColorBlend.Settings(darken = .5f)))
    }
}
