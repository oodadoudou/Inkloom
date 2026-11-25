package app.inkloom.engine.v5.material

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralShapeTipTest {
    private fun alpha(mask: BrushTipMask, x: Int, y: Int) = (mask.coverageAt((x + .5f) / mask.width, (y + .5f) / mask.height) * 255f).toInt()

    @Test
    fun watercolorRingPoolsPigmentAtTheRim() {
        val mask = BrushTipMask.watercolorRing(96, seed = 4, hardness = .5f)
        val centre = alpha(mask, 48, 48)
        val nearRim = (0 until 360 step 30).map { degrees ->
            val angle = Math.toRadians(degrees.toDouble())
            alpha(mask, (48 + 36 * kotlin.math.cos(angle)).toInt(), (48 + 36 * kotlin.math.sin(angle)).toInt())
        }
        assertTrue("a thin wash in the middle: $centre", centre in 40..140)
        assertTrue("pooled at the rim: $nearRim vs $centre", nearRim.average() > centre * 1.5)
        assertEquals(0, alpha(mask, 1, 1))
    }

    @Test
    fun triangleAndSquareHaveTheirShapes() {
        val triangle = BrushTipMask.polygon(96, seed = 2, sides = 3, hardness = 1f)
        assertTrue("flat bottom edge", alpha(triangle, 30, 68) > 150 && alpha(triangle, 66, 68) > 150)
        assertTrue("narrow at the top", alpha(triangle, 30, 20) == 0 && alpha(triangle, 48, 20) > 150)
        val square = BrushTipMask.polygon(96, seed = 2, sides = 4, hardness = 1f)
        assertTrue("square sits square, corners filled", alpha(square, 20, 20) > 150 && alpha(square, 76, 76) > 150)
        assertEquals("outside the square", 0, alpha(square, 2, 48))
    }

    @Test
    fun bandsCutATipIntoRowsTopFirst() {
        val tip = BrushTipMask(2, 4, byteArrayOf(1, 1, 2, 2, 3, 3, 4, 4))
        val bands = (0 until 2).map { tip.band(it, 2) }
        assertEquals(listOf(2, 2), bands.map { it.height })
        assertTrue(bands[0].maximumCoverage < bands[1].maximumCoverage)
    }
}
