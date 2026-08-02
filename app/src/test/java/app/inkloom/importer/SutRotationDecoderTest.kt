package app.inkloom.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class SutRotationDecoderTest {
    @Test fun decodesKnownLowNibbleModes() {
        assertEquals(SutRotationMode.FIXED, SutRotationDecoder.decode(0).mode)
        assertEquals(SutRotationMode.DIRECTION_OF_PEN, SutRotationDecoder.decode(1).mode)
        assertEquals(SutRotationMode.PEN_TWIST, SutRotationDecoder.decode(2).mode)
        assertEquals(SutRotationMode.DIRECTION_OF_LINE, SutRotationDecoder.decode(3).mode)
    }

    @Test fun retainsAdditionalDynamicsBitsSeparately() {
        val decoded = SutRotationDecoder.decode(0x93)
        assertEquals(SutRotationMode.DIRECTION_OF_LINE, decoded.mode)
        assertEquals(0x90, decoded.additionalDynamicsFlags)
        assertEquals(true, decoded.randomEnabled)
        assertEquals(0x10, decoded.unsupportedDynamicsFlags)
    }

    @Test fun aStoredRandomScaleDoesNotMatterUnlessItsFlagIsEnabled() {
        assertEquals(false, SutRotationDecoder.decode(3).randomEnabled)
        assertEquals(true, SutRotationDecoder.decode(0x83).randomEnabled)
    }
}
