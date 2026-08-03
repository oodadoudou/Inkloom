package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class BrushBlurTest {
    @Test fun pixelAndMillimetreUnitsScaleWithPageWidth() {
        assertEquals(5f, BrushBlur.radiusPixels(10f, 0, false, 20f, 20f, 800f), .0001f)
        assertEquals(3.7795275f, BrushBlur.radiusPixels(2f, 2, false, 20f, 20f, 800f), .0001f)
    }

    @Test fun sizeLinkedBlurTracksCurrentBrushDiameter() {
        assertEquals(10f, BrushBlur.radiusPixels(10f, 0, true, 40f, 20f, 800f), .0001f)
        assertEquals(5f, BrushBlur.radiusPixels(10f, 0, false, 40f, 20f, 800f), .0001f)
    }

    @Test fun invalidAndExtremeInputsStayFiniteAndBounded() {
        assertEquals(0f, BrushBlur.radiusPixels(Float.NaN, 0, false, 1f, 1f, 1600f), .0001f)
        assertEquals(400f, BrushBlur.radiusPixels(Float.MAX_VALUE, 0, false, 1f, 1f, 1600f), .0001f)
        assertEquals(0f, BrushBlur.radiusPixels(10f, 0, false, 1f, 1f, Float.NaN), .0001f)
    }
}
