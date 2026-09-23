package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderedColorSamplingTest {
    @Test fun transparentHitDoesNotReplaceTheDrawingColor() {
        assertEquals(null, RenderedColorSampling.pick(0))
        assertEquals(0x80FF0000.toInt(), RenderedColorSampling.pick(0x80FF0000.toInt()))
        assertEquals(0xFF0A141E.toInt(), RenderedColorSampling.pick(0xFF0A141E.toInt()))
    }
}
