package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CspTextureCompositeTest {
    @Test fun `compare selects texture without double-multiplying soft tip density`() {
        assertEquals(.4f, CspTextureComposite.density(.6f, .4f, 1f, CspTextureComposite.COMPARE), .0001f)
        assertEquals(.6f, CspTextureComposite.density(.6f, .8f, 1f, CspTextureComposite.COMPARE), .0001f)
    }

    @Test fun `height textures the antialiased edge but preserves the solid center`() {
        assertEquals(1f, CspTextureComposite.density(1f, 0f, 1f, CspTextureComposite.HEIGHT), .0001f)
        assertEquals(0f, CspTextureComposite.density(.5f, 0f, 1f, CspTextureComposite.HEIGHT), .0001f)
        assertEquals(.25f, CspTextureComposite.density(.5f, .5f, 1f, CspTextureComposite.HEIGHT), .0001f)
    }

    @Test fun `strength interpolates from authored tip to composed density`() {
        assertEquals(.375f, CspTextureComposite.density(.5f, .5f, .5f, CspTextureComposite.MULTIPLY), .0001f)
        assertEquals(.5f, CspTextureComposite.density(.5f, 0f, 0f, CspTextureComposite.HEIGHT), .0001f)
    }

    @Test fun `only the documented edge-only height equation is marked verified`() {
        assertFalse(CspTextureComposite.isVerified(CspTextureComposite.COMPARE))
        assertTrue(CspTextureComposite.isVerified(CspTextureComposite.HEIGHT))
        assertFalse(CspTextureComposite.isVerified(CspTextureComposite.OVERLAY))
    }
}
