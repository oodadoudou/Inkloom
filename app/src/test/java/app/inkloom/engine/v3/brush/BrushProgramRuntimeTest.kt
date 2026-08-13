package app.inkloom.engine.v3.brush

import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class BrushProgramRuntimeTest {
    @Test
    fun growingContactReusesOneImmutableCompiledProgram() {
        BrushProgramRuntime.clearForTests()
        val stroke = InkStroke(id = "stable-contact", points = listOf(StrokePoint(.1f, .2f)))

        val first = BrushProgramRuntime.compile(stroke)
        val grown = BrushProgramRuntime.compile(stroke.copy(points = stroke.points + StrokePoint(.3f, .4f)))

        assertSame(first, grown)
    }

    @Test
    fun changingBrushParametersInvalidatesTheCompiledProgramWithoutHashingPoints() {
        BrushProgramRuntime.clearForTests()
        val stroke = InkStroke(id = "edited-brush", spacing = .1f, points = listOf(StrokePoint(.1f, .2f)))

        val before = BrushProgramRuntime.compile(stroke)
        val after = BrushProgramRuntime.compile(stroke.copy(spacing = .8f))

        assertNotSame(before, after)
    }

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

        val dab = BrushProgramRuntime.evaluateDab(stroke, point)

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

        val primary = BrushProgramRuntime.evaluateDab(stroke, point)
        val secondary = BrushProgramRuntime.evaluateDab(stroke, point, dual = true)

        assertEquals(1f, primary.size, .0001f)
        assertEquals(.4f, secondary.size, .0001f)
        assertEquals(.55f, secondary.flow, .0001f)
        assertEquals(.7f, secondary.thickness, .0001f)
    }
}
