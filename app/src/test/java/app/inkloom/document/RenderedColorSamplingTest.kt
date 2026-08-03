package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderedColorSamplingTest {
    @Test fun transparentContentIsCompositedAgainstTheWhitePage() {
        assertEquals(0xFFFF7F7F.toInt(), RenderedColorSampling.opaqueOverWhite(0x80FF0000.toInt()))
        assertEquals(0xFF0A141E.toInt(), RenderedColorSampling.opaqueOverWhite(0xFF0A141E.toInt()))
    }
}
