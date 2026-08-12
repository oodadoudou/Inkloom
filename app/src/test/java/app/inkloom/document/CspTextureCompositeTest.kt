package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CspTextureCompositeTest {
    @Test fun `normal preserves plateaus and balanced mid density`() {
        assertEquals(0f, CspTextureComposite.density(0f, 1f, 1f, CspTextureComposite.NORMAL), .0001f)
        assertEquals(1f, CspTextureComposite.density(1f, 0f, 1f, CspTextureComposite.NORMAL), .0001f)
        assertEquals(.6f, CspTextureComposite.density(.6f, .5f, 1f, CspTextureComposite.NORMAL), .0001f)
    }

    @Test fun `multiply subtract and compare use deterministic normalized density equations`() {
        assertEquals(.24f, CspTextureComposite.density(.6f, .4f, 1f, CspTextureComposite.MULTIPLY), .0001f)
        assertEquals(.2f, CspTextureComposite.density(.6f, .4f, 1f, CspTextureComposite.SUBTRACT), .0001f)
        assertEquals(0f, CspTextureComposite.density(.4f, .6f, 1f, CspTextureComposite.SUBTRACT), .0001f)
        assertEquals(.4f, CspTextureComposite.density(.6f, .4f, 1f, CspTextureComposite.COMPARE), .0001f)
    }

    @Test fun `compare selects texture without double-multiplying soft tip density`() {
        assertEquals(.4f, CspTextureComposite.density(.6f, .4f, 1f, CspTextureComposite.COMPARE), .0001f)
        assertEquals(.6f, CspTextureComposite.density(.6f, .8f, 1f, CspTextureComposite.COMPARE), .0001f)
    }

    @Test fun `height textures the antialiased edge but preserves the solid center`() {
        assertEquals(1f, CspTextureComposite.density(1f, 0f, 1f, CspTextureComposite.HEIGHT), .0001f)
        assertEquals(0f, CspTextureComposite.density(.5f, 0f, 1f, CspTextureComposite.HEIGHT), .0001f)
        assertEquals(.25f, CspTextureComposite.density(.5f, .5f, 1f, CspTextureComposite.HEIGHT), .0001f)
    }

    @Test fun `outline responds to texture edges only on the soft brush edge`() {
        assertEquals(.5f, CspTextureComposite.density(.5f, .2f, 1f, CspTextureComposite.OUTLINE, textureEdge = 1f), .0001f)
        assertEquals(0f, CspTextureComposite.density(.5f, .8f, 1f, CspTextureComposite.OUTLINE, textureEdge = 0f), .0001f)
        assertEquals(1f, CspTextureComposite.density(1f, .2f, 1f, CspTextureComposite.OUTLINE, textureEdge = 0f), .0001f)
    }

    @Test fun `overlay color dodge and hard mix use their standard scalar blends`() {
        assertEquals(.375f, CspTextureComposite.density(.25f, .75f, 1f, CspTextureComposite.OVERLAY), .0001f)
        assertEquals(.5f, CspTextureComposite.density(.25f, .5f, 1f, CspTextureComposite.COLOR_DODGE), .0001f)
        assertEquals(0f, CspTextureComposite.density(.25f, .25f, 1f, CspTextureComposite.HARD_MIX), .0001f)
        assertEquals(1f, CspTextureComposite.density(.25f, .75f, 1f, CspTextureComposite.HARD_MIX), .0001f)
    }

    @Test fun `strength interpolates from authored tip to composed density`() {
        assertEquals(.375f, CspTextureComposite.density(.5f, .5f, .5f, CspTextureComposite.MULTIPLY), .0001f)
        assertEquals(.5f, CspTextureComposite.density(.5f, 0f, 0f, CspTextureComposite.HEIGHT), .0001f)
    }

    @Test fun `all nine documented texture modes are recognized`() {
        assertEquals(
            listOf(
                CspTextureComposite.NORMAL,
                CspTextureComposite.MULTIPLY,
                CspTextureComposite.SUBTRACT,
                CspTextureComposite.COMPARE,
                CspTextureComposite.OUTLINE,
                CspTextureComposite.OVERLAY,
                CspTextureComposite.COLOR_DODGE,
                CspTextureComposite.HARD_MIX,
                CspTextureComposite.HEIGHT,
            ),
            CspTextureComposite.documentedModes,
        )
        CspTextureComposite.documentedModes.forEach { assertTrue(CspTextureComposite.isDocumented(it)) }
        assertTrue(!CspTextureComposite.isDocumented(0))
        assertTrue(!CspTextureComposite.isDocumented(10))
    }
}
