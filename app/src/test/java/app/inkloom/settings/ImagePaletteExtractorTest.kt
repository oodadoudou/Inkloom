package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePaletteExtractorTest {
    @Test fun extractsDominantOpaqueColorsAndIgnoresTransparentPixels() {
        val pixels = IntArray(1_000) { index ->
            when {
                index < 600 -> 0xffff2020.toInt()
                index < 900 -> 0xff2050ff.toInt()
                else -> 0x0000ff00
            }
        }
        val colors = ImagePaletteExtractor.extract(pixels, 4)
        assertEquals(2, colors.size)
        assertNear(0xffff2020.toInt(), colors[0])
        assertNear(0xff2050ff.toInt(), colors[1])
    }

    @Test fun outputIsDeterministicAndBounded() {
        val pixels = IntArray(80_000) { index ->
            val channel = index % 256
            0xff000000.toInt() or (channel shl 16) or ((255 - channel) shl 8) or (channel / 2)
        }
        val first = ImagePaletteExtractor.extract(pixels, 8)
        val second = ImagePaletteExtractor.extract(pixels, 8)
        assertEquals(first, second)
        assertTrue(first.size in 2..8)
    }

    private fun assertNear(expected: Int, actual: Int) {
        fun channel(color: Int, shift: Int) = color ushr shift and 0xff
        assertTrue(kotlin.math.abs(channel(expected, 16) - channel(actual, 16)) <= 8)
        assertTrue(kotlin.math.abs(channel(expected, 8) - channel(actual, 8)) <= 8)
        assertTrue(kotlin.math.abs(channel(expected, 0) - channel(actual, 0)) <= 8)
    }
}
