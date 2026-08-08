package app.inkloom.engine.v2

import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v2.brush.EngineV2BrushEvaluator
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineV2BrushEvaluatorTest {
    @Test
    fun compilesPrimaryPressureChannelsIntoOneDabResult() {
        val point = StrokePoint(.2f, .3f, pressure = .5f)
        val stroke = InkStroke(
            points = listOf(point),
            sizePressureEnabled = true,
            sizePressureMinimum = .2f,
            opacityPressureEnabled = true,
            opacityPressureMinimum = .4f,
            flowPressureEnabled = true,
            flowPressureMinimum = .6f,
            thicknessPressureEnabled = true,
            thicknessPressureMinimum = .1f,
            blurPressureEnabled = true,
            blurPressureMinimum = .3f,
        )

        val dab = EngineV2BrushEvaluator.evaluateDab(stroke, point)

        assertEquals(.6f, dab.size, .0001f)
        assertEquals(.7f, dab.opacity, .0001f)
        assertEquals(.8f, dab.flow, .0001f)
        assertEquals(.55f, dab.thickness, .0001f)
        assertEquals(.65f, dab.blur, .0001f)
        assertEquals(dab.opacity * dab.flow * dab.wetAlpha, dab.depositedAlpha, .0001f)
    }

    @Test
    fun secondaryCoatUsesItsOwnSizeFlowAndThicknessPrograms() {
        val point = StrokePoint(.2f, .3f, pressure = .25f)
        val stroke = InkStroke(
            points = listOf(point),
            sizePressureEnabled = false,
            flowPressureEnabled = false,
            thicknessPressureEnabled = false,
            dualSizeDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .2f),
            dualFlowDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .4f),
            dualThicknessDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .6f),
        )

        val primary = EngineV2BrushEvaluator.evaluateDab(stroke, point)
        val secondary = EngineV2BrushEvaluator.evaluateDab(stroke, point, dual = true)

        assertEquals(1f, primary.size, .0001f)
        assertEquals(.4f, secondary.size, .0001f)
        assertEquals(.55f, secondary.flow, .0001f)
        assertEquals(.7f, secondary.thickness, .0001f)
    }
}
