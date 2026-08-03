package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushTipFlipTest {
    @Test fun `fixed and off modes map directly to scale signs`() {
        assertEquals(BrushTipFlip.ScaleSigns(1f, -1f), BrushTipFlip.signs(BrushTipFlip.OFF, BrushTipFlip.FLIP, 0, 1, 1, 7))
    }

    @Test fun `random flip is deterministic and varies across a stroke`() {
        val first = (0 until 64).map { BrushTipFlip.signs(BrushTipFlip.RANDOM, BrushTipFlip.RANDOM, 0, it, 3, 918).let { signs -> signs.x to signs.y } }
        val second = (0 until 64).map { BrushTipFlip.signs(BrushTipFlip.RANDOM, BrushTipFlip.RANDOM, 0, it, 3, 918).let { signs -> signs.x to signs.y } }
        assertEquals(first, second)
        assertTrue(first.map { it.first }.toSet().size > 1)
        assertTrue(first.map { it.second }.toSet().size > 1)
    }

    @Test fun `reverse-only flip follows the descending half of CSP reverse repeat`() {
        val signs = (0..6).map { BrushTipFlip.signs(BrushTipFlip.ON_REVERSED_SEQUENCE, BrushTipFlip.OFF, BrushTipSequence.REVERSE, it, 3, 7).x }
        assertEquals(listOf(1f, 1f, 1f, -1f, 1f, 1f, 1f), signs)
    }
}
