package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextureSamplingTest {
    @Test fun alphaOnlyCspTextureRetainsDensity() {
        assertEquals(1f, TextureSampling.effectiveLuminance(0f, 0f), .0001f)
        assertEquals(.75f, TextureSampling.effectiveLuminance(0f, .25f), .0001f)
        assertEquals(0f, TextureSampling.effectiveLuminance(0f, 1f), .0001f)
        assertEquals(.25f, TextureSampling.mask(TextureSampling.effectiveLuminance(0f, .25f), 0f, 0f, false, false), .0001f)
    }

    @Test fun coordinatesTileAndStayInsideTexture() {
        val result = TextureSampling.coordinate(999, -400, 300, 300, 197, 179, 153f, 35f)
        assertTrue(result.x in 0 until 197)
        assertTrue(result.y in 0 until 179)
    }

    @Test fun reverseFlipsTheTextureMask() {
        assertEquals(.8f, TextureSampling.mask(.2f, 0f, 0f, false, false), .001f)
        assertEquals(.2f, TextureSampling.mask(.2f, 0f, 0f, true, false), .001f)
    }

    @Test fun brightnessAndContrastAreBounded() {
        assertEquals(0f, TextureSampling.mask(1f, 100f, 100f, false, true), .001f)
    }
}
