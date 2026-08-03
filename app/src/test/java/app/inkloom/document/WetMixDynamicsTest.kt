package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WetMixDynamicsTest {
    @Test fun dryBrushKeepsFullAlpha() {
        assertEquals(1f, WetMixDynamics.alphaMultiplier(false, 1f, 0f), .0001f)
    }

    @Test fun paintDensityAndAmountOfPaintStrengthenDrawingColor() {
        val unloaded = WetMixDynamics.alphaMultiplier(true, 0f, 0f)
        val loaded = WetMixDynamics.alphaMultiplier(true, 0f, 1f)
        val mixed = WetMixDynamics.alphaMultiplier(true, 1f, 1f)

        assertTrue(loaded > unloaded)
        assertTrue(mixed > loaded)
        assertEquals(.9f, mixed, .0001f)
    }

    @Test fun importedPressureDynamicsScaleColorAndPaintMixPerPoint() {
        val stroke = InkStroke(
            points = emptyList(),
            watercolor = true,
            colorMix = .8f,
            colorMixPressureEnabled = true,
            colorMixPressureMinimum = .25f,
            paintMix = .6f,
            paintMixPressureEnabled = true,
            paintMixPressureMinimum = .5f,
        )

        val light = StrokePoint(0f, 0f, pressure = 0f)
        val heavy = StrokePoint(0f, 0f, pressure = 1f)
        assertEquals(.2f, WetMixDynamics.colorMix(stroke, light), .0001f)
        assertEquals(.3f, WetMixDynamics.paintMix(stroke, light), .0001f)
        assertEquals(.8f, WetMixDynamics.colorMix(stroke, heavy), .0001f)
        assertEquals(.6f, WetMixDynamics.paintMix(stroke, heavy), .0001f)
        assertTrue(WetMixDynamics.alphaMultiplier(stroke, light) < WetMixDynamics.alphaMultiplier(stroke, heavy))
    }
}
