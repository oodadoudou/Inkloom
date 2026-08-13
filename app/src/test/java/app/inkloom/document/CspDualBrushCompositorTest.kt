package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CspDualBrushCompositorTest {
    @Test fun everyDocumentedDensityModeHasItsOwnEquation() {
        val base = .7f
        val blend = .4f
        val expected = mapOf(
            1 to .82f,
            2 to .28f,
            3 to 1f,
            4 to .3f,
            5 to .4f,
            6 to .7f,
            7 to .82f,
            8 to .64f,
            9 to 1f,
            10 to .25f,
            11 to .1f,
            12 to 1f,
            13 to .42f,
        )
        expected.forEach { (mode, result) ->
            assertEquals("mode $mode", result, CspDualBrushCompositor.density(mode, base, blend), .0001f)
        }
    }

    @Test fun heightLinearIsSubtractWithMoreRetainedDensity() {
        for (primaryStep in 0..20) for (secondaryStep in 0..20) {
            val primary = primaryStep / 20f
            val secondary = secondaryStep / 20f
            val subtract = CspDualBrushCompositor.density(CspDualBrushMode.SUBTRACT.code, primary, secondary)
            val height = CspDualBrushCompositor.density(CspDualBrushMode.HEIGHT_LINEAR.code, primary, secondary)
            assertTrue("Height Linear must not be lighter than Subtract", height + .0001f >= subtract)
            assertTrue("Height Linear must not exceed the primary density", height <= primary + .0001f)
        }
    }

    @Test fun alphaOnlyScreenChangesDensityButNeverRequiresRgb() {
        assertEquals(.76f, CspDualBrushCompositor.density(CspDualBrushMode.SCREEN.code, .4f, .6f), .0001f)
        assertEquals(.4f, CspDualBrushCompositor.blendChannel(CspDualBrushMode.SCREEN.code, 0f, .4f), .0001f)
    }

    @Test fun photoshopHardMixUsesTheDocumentedUnitSumThreshold() {
        assertEquals(0f, CspDualBrushCompositor.blendChannel(CspDualBrushMode.HARD_MIX.code, .49f, .5f), 0f)
        assertEquals(1f, CspDualBrushCompositor.blendChannel(CspDualBrushMode.HARD_MIX.code, .5f, .5f), 0f)
    }
}
