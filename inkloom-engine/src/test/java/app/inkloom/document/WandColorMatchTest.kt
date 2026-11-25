package app.inkloom.document

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WandColorMatchTest {
    @Test fun everyChannelIncludingAlphaMustBeWithinTolerance() {
        val seed = 0xFF336699.toInt()
        assertTrue(WandColorMatch.matches(seed, seed, 0))
        assertTrue(WandColorMatch.matches(0xFF3A6094.toInt(), seed, 8))
        assertFalse(WandColorMatch.matches(0xFF3A6094.toInt(), seed, 6))
        assertFalse("transparency differs", WandColorMatch.matches(0x80336699.toInt(), seed, 32))
    }

    @Test fun separateAreasOfTheSameColourAreAllSelected() {
        val red = 0xFFFF0000.toInt(); val blue = 0xFF0000FF.toInt()
        val pixels = intArrayOf(red, blue, red, blue, 0xFFF80808.toInt())
        assertArrayEquals(intArrayOf(-1, 0, -1, 0, -1), WandColorMatch.select(pixels, red, 10))
    }

    @Test fun closeGapLevelsGrowAndOffIsZero() {
        assertTrue(WandColorMatch.closeGapPixels(0) == 0f)
        assertTrue(WandColorMatch.closeGapPixels(1) < WandColorMatch.closeGapPixels(2) && WandColorMatch.closeGapPixels(2) < WandColorMatch.closeGapPixels(3))
        assertTrue(WandColorMatch.closeGapPixels(9) == WandColorMatch.closeGapPixels(3))
    }
}
