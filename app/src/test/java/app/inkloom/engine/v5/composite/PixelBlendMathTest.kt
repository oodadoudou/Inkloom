package app.inkloom.engine.v5.composite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelBlendMathTest {
    @Test fun sourceOverSeparatesStraightBlendFromPremultipliedAlphaComposition() {
        val source = PremultipliedRgba(.5f, 0f, 0f, .5f)
        val destination = PremultipliedRgba(0f, 0f, .5f, .5f)

        val output = PixelBlendMath.composite(source, destination, PixelBlendMode.NORMAL)

        assertColor(output, .5f, 0f, .25f, .75f)
    }

    @Test fun layerOpacityScalesSourceAlphaAndPremultipliedColorTogether() {
        val output = PixelBlendMath.composite(
            source = PremultipliedRgba(.5f, 0f, 0f, .5f),
            destination = PremultipliedRgba(0f, 0f, .5f, .5f),
            mode = PixelBlendMode.NORMAL,
            opacity = .5f,
        )

        assertColor(output, .25f, 0f, .375f, .625f)
    }

    @Test fun opaqueSeparableModesMatchTheirReferenceEquations() {
        val backdrop = PremultipliedRgba(.2f, .4f, .8f, 1f)
        val source = PremultipliedRgba(.5f, .25f, .75f, 1f)

        assertColor(PixelBlendMath.composite(source, backdrop, PixelBlendMode.MULTIPLY), .1f, .1f, .6f, 1f)
        assertColor(PixelBlendMath.composite(source, backdrop, PixelBlendMode.SCREEN), .6f, .55f, .95f, 1f)
        assertColor(PixelBlendMath.composite(source, backdrop, PixelBlendMode.ADD), .7f, .65f, 1.55f, 1f)
        assertColor(PixelBlendMath.composite(source, backdrop, PixelBlendMode.LINEAR_BURN), -.3f, -.35f, .55f, 1f)
        assertColor(PixelBlendMath.composite(source, backdrop, PixelBlendMode.DIFFERENCE), .3f, .15f, .05f, 1f)
    }

    @Test fun normalAndAddPreserveLinearHdrAndNegativeValues() {
        val hdr = PremultipliedRgba(2.5f, -.25f, .5f, 1f)
        val destination = PremultipliedRgba(1.5f, .5f, -.2f, 1f)

        assertColor(PixelBlendMath.composite(hdr, destination, PixelBlendMode.NORMAL), 2.5f, -.25f, .5f, 1f)
        assertColor(PixelBlendMath.composite(hdr, destination, PixelBlendMode.ADD), 4f, .25f, .3f, 1f)
    }

    @Test fun everyModeProducesFiniteOutputForTransparentAndBoundedInputs() {
        val inputs = listOf(
            PremultipliedRgba(0f, 0f, 0f, 0f) to PremultipliedRgba(0f, 0f, 0f, 0f),
            PremultipliedRgba(.1f, .2f, .3f, .4f) to PremultipliedRgba(.45f, .2f, .05f, .5f),
            PremultipliedRgba(1f, 1f, 1f, 1f) to PremultipliedRgba(0f, 0f, 0f, 1f),
        )
        PixelBlendMode.entries.forEach { mode ->
            inputs.forEach { (source, destination) ->
                val output = PixelBlendMath.composite(source, destination, mode)
                assertTrue("$mode red", output.red.isFinite())
                assertTrue("$mode green", output.green.isFinite())
                assertTrue("$mode blue", output.blue.isFinite())
                assertTrue("$mode alpha", output.alpha.isFinite() && output.alpha in 0f..1f)
            }
        }
    }

    @Test fun nonSeparableModesRetainTheSpecifiedLuminosityRelationship() {
        val backdrop = PremultipliedRgba(.15f, .7f, .3f, 1f)
        val source = PremultipliedRgba(.8f, .2f, .6f, 1f)
        val color = PixelBlendMath.composite(source, backdrop, PixelBlendMode.COLOR)
        val luminosity = PixelBlendMath.composite(source, backdrop, PixelBlendMode.LUMINOSITY)

        assertEquals(lum(backdrop), lum(color), .0001f)
        assertEquals(lum(source), lum(luminosity), .0001f)
    }

    @Test fun sdrGlowKeepsUnclippedLightUntilAfterAlphaInterpolation() {
        val backdrop = PremultipliedRgba(.8f, .8f, .8f, 1f)
        val source = PremultipliedRgba(.16f, .16f, .16f, .2f)
        assertColor(PixelBlendMath.composite(source, backdrop, PixelBlendMode.ADD, bounded = true), .84f, .84f, .84f, 1f)
        assertColor(PixelBlendMath.composite(source, backdrop, PixelBlendMode.ADD_GLOW, bounded = true), .96f, .96f, .96f, 1f)
        assertColor(PixelBlendMath.composite(source, backdrop, PixelBlendMode.COLOR_DODGE, bounded = true), .84f, .84f, .84f, 1f)
        assertColor(PixelBlendMath.composite(source, backdrop, PixelBlendMode.GLOW_DODGE, bounded = true), 1.44f, 1.44f, 1.44f, 1f)
    }

    @Test fun extendedContrastAndArithmeticModesMatchKnownColors() {
        val b = PremultipliedRgba(.2f, .4f, .8f, 1f)
        val s = PremultipliedRgba(.25f, .5f, .75f, 1f)
        assertColor(PixelBlendMath.composite(s, b, PixelBlendMode.SUBTRACT, bounded = true), 0f, 0f, .05f, 1f)
        assertColor(PixelBlendMath.composite(s, b, PixelBlendMode.VIVID_LIGHT), 0f, .4f, 1f, 1f)
        assertColor(PixelBlendMath.composite(s, b, PixelBlendMode.LINEAR_LIGHT, bounded = true), 0f, .4f, 1f, 1f)
        assertColor(PixelBlendMath.composite(s, b, PixelBlendMode.PIN_LIGHT), .2f, .4f, .8f, 1f)
        assertColor(PixelBlendMath.composite(s, b, PixelBlendMode.HARD_MIX), 0f, 0f, 1f, 1f)
        assertColor(PixelBlendMath.composite(s, b, PixelBlendMode.DIVIDE), .8f, .8f, 1f, 1f)
    }

    @Test fun darkerAndLighterColorChooseOneWholeRgbTriplet() {
        val b = PremultipliedRgba(.1f, .8f, .1f, 1f)
        val s = PremultipliedRgba(.8f, .1f, .8f, 1f)
        assertColor(PixelBlendMath.composite(s, b, PixelBlendMode.DARKER_COLOR), .8f, .1f, .8f, 1f)
        assertColor(PixelBlendMath.composite(s, b, PixelBlendMode.LIGHTER_COLOR), .1f, .8f, .1f, 1f)
    }

    private fun assertColor(
        actual: PremultipliedRgba,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        assertEquals(red, actual.red, .0001f)
        assertEquals(green, actual.green, .0001f)
        assertEquals(blue, actual.blue, .0001f)
        assertEquals(alpha, actual.alpha, .0001f)
    }

    private fun lum(color: PremultipliedRgba): Float = .3f * color.red + .59f * color.green + .11f * color.blue
}
