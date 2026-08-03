package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CspDualBrushModeTest {
    @Test fun `CSP stored mode order resolves documented native modes`() {
        assertEquals(CspDualBrushMode.NORMAL, CspDualBrushMode.fromCode(0))
        assertEquals(CspDualBrushMode.NORMAL, CspDualBrushMode.fromCode(1))
        assertEquals(CspDualBrushMode.MULTIPLY, CspDualBrushMode.fromCode(2))
        assertEquals(CspDualBrushMode.SCREEN, CspDualBrushMode.fromCode(7))
        assertEquals(CspDualBrushMode.HEIGHT_LINEAR, CspDualBrushMode.fromCode(13))
        assertNull(CspDualBrushMode.fromCode(99))
    }

    @Test fun `only independently matched Android equations are advertised as native`() {
        assertTrue(CspDualBrushMode.SCREEN.nativeBlendSupported)
        assertTrue(CspDualBrushMode.COLOR_BURN.nativeBlendSupported)
        assertFalse(CspDualBrushMode.SUBTRACT.nativeBlendSupported)
        assertFalse(CspDualBrushMode.HEIGHT_LINEAR.nativeBlendSupported)
    }
}
