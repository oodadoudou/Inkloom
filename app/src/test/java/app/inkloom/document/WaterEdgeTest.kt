package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class WaterEdgeTest {
    @Test
    fun pixelAndMillimeterUnitsScaleToOutputWidth() {
        assertEquals(6f, WaterEdge.distancePixels(12f, 0, 800f), .0001f)
        assertEquals(3.7795275f, WaterEdge.distancePixels(2f, 2, 800f), .0001f)
    }

    @Test
    fun darkeningPreservesAlpha() {
        val color = WaterEdge.darkenArgb(0x804080C0.toInt(), .5f)
        assertEquals(0x80204060.toInt(), color)
    }
}
