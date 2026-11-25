package app.inkloom.engine.v5.material

import app.inkloom.brushpresetv5.ProceduralTipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChalkTipTest {
    private fun alpha(mask: BrushTipMask, x: Int, y: Int) = (mask.coverageAt((x + .5f) / 96f, (y + .5f) / 96f) * 255f).toInt()

    @Test
    fun chalkIsDeterministicPerSeedAndDiffersBetweenSeeds() {
        val a = BrushTipMask.chalk(96, seed = 11, toothStrength = .6f)
        assertEquals(a.contentHash(), BrushTipMask.chalk(96, seed = 11, toothStrength = .6f).contentHash())
        assertNotEquals(a.contentHash(), BrushTipMask.chalk(96, seed = 12, toothStrength = .6f).contentHash())
    }

    @Test
    fun chalkSkipsInsideAndMoreToothSkipsMore() {
        fun inside(tooth: Float): List<Int> {
            val mask = BrushTipMask.chalk(96, seed = 5, toothStrength = tooth)
            return (20 until 76).flatMap { y -> (20 until 76).map { x -> alpha(mask, x, y) } }
        }
        val light = inside(.1f)
        val heavy = inside(.9f)
        assertTrue("a crayon leaves skips even with little tooth", light.any { it < 200 })
        assertTrue("more tooth, more skips", heavy.average() < light.average() - 20)
        assertTrue("never fully empty inside", heavy.average() > 40)
    }

    @Test
    fun chalkHasALumpyRimNotACircle() {
        val mask = BrushTipMask.chalk(96, seed = 3, toothStrength = 0f)
        // Rim radius along 36 directions: the first step where coverage drops below 40/255.
        val radii = (0 until 36).map { step ->
            val angle = step * Math.PI / 18
            (0 until 48).first { r ->
                r == 47 || alpha(mask, (48 + r * kotlin.math.cos(angle)).toInt().coerceIn(0, 95),
                    (48 + r * kotlin.math.sin(angle)).toInt().coerceIn(0, 95)) < 40
            }
        }
        assertTrue("rim varies: $radii", radii.max() - radii.min() >= 3)
        assertEquals(BrushTipMask.chalk(96, 3, .4f, 1f).contentHash(),
            BrushTipMask.procedural(ProceduralTipKind.CHALK, 96, 3, .4f, 1f).contentHash())
    }
}
