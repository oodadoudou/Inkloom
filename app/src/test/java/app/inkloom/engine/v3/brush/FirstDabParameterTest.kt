package app.inkloom.engine.v3.brush

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushSensorFlags
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audits first-dab initialization: the first dab must be derived from the first modeled input
 * point through the same parameter program as every later dab, never a special case. These tests
 * pin the exact behavior of the known device-independent first-dab extremes (zero DOWN pressure,
 * velocity == 0 on the first sample, tilt == 0 until the pen moves) so any future brush refactor
 * that changes first-dab continuity fails loudly here.
 */
class FirstDabParameterTest {
    @Before
    fun setUp() = BrushProgramRuntime.clearForTests()

    @After
    fun tearDown() = BrushProgramRuntime.clearForTests()

    private val viewportWidth = 1600f
    private val viewportHeight = 1200f

    @Test
    fun firstDabIsAlwaysEmittedAtTheContactPoint() {
        val points = listOf(
            StrokePoint(.5f, .5f, pressure = .7f, timeMs = 0L),
            StrokePoint(.5f, .5f, pressure = .7f, timeMs = 8L),
        )
        val stroke = InkStroke(id = "tap", points = points, width = 64f, spacing = .08f, sizePressureEnabled = false)
        val sampled = dabs(stroke)
        assertEquals("A tap must deposit exactly the contact dab", 1, sampled.size)
        assertEquals(points.first(), sampled.first())
    }

    @Test
    fun constantInputProducesNoParameterJumpAcrossDabs() {
        val stroke = InkStroke(
            id = "constant",
            points = line { .6f },
            width = 64f,
            spacing = .08f,
            sizePressureEnabled = false,
            opacity = 1f,
            flow = 1f,
        )
        val sampled = dabs(stroke)
        assertTrue("Expected several dabs for a constant line", sampled.size >= 5)
        val first = BrushProgramRuntime.evaluateDab(stroke, sampled.first())
        sampled.drop(1).forEach { point ->
            val dab = BrushProgramRuntime.evaluateDab(stroke, point)
            assertEquals("size must stay constant", first.size, dab.size, 1e-4f)
            assertEquals("opacity must stay constant", first.opacity, dab.opacity, 1e-4f)
            assertEquals("flow must stay constant", first.flow, dab.flow, 1e-4f)
        }
    }

    @Test
    fun pressureRampIsMonotonicAndFirstDabUsesTheFirstInputPointProgram() {
        val points = line { index -> index / 23f }
        val stroke = InkStroke(
            id = "ramp",
            points = points,
            width = 64f,
            spacing = .08f,
            sizePressureEnabled = true,
            sizePressureMinimum = .2f,
            sizePressureCurve = listOf(BrushCurvePoint(0f, .25f), BrushCurvePoint(1f, 1f)),
        )
        val sampled = dabs(stroke)
        val sizes = sampled.map { BrushProgramRuntime.evaluateDab(stroke, it).size }
        assertTrue("Pressure-driven size must never shrink on a rising ramp", sizes.zipWithNext().all { (a, b) -> b >= a - 1e-4f })
        assertEquals(
            "The first dab must be evaluated exactly like any later dab at the first point",
            BrushProgramRuntime.evaluateDab(stroke, points.first()).size,
            sizes.first(),
            1e-4f,
        )
    }

    @Test
    fun interpolatedDabPressureStaysWithinItsSegmentBounds() {
        val points = line { index -> if (index < 8) .2f else .9f }
        val stroke = InkStroke(
            id = "bounds",
            points = points,
            width = 64f,
            spacing = .08f,
            sizePressureEnabled = true,
            sizePressureMinimum = 0f,
        )
        val sampled = dabs(stroke)
        val pressures = sampled.map { it.pressure }
        assertTrue(pressures.all { it in .19f..0.91f })
        assertTrue("Pressure must rise monotonically across a single rise", pressures.zipWithNext().all { (a, b) -> b >= a - 1e-4f })
    }

    @Test
    fun zeroDownPressureClampsTheFirstDabToTheCalibratedMinimum() {
        val points = line { index -> if (index == 0) 0f else .6f }
        val stroke = InkStroke(
            id = "zero-down",
            points = points,
            width = 64f,
            spacing = .08f,
            sizePressureEnabled = true,
            sizePressureMinimum = .3f,
        )
        val sampled = dabs(stroke)
        val first = BrushProgramRuntime.evaluateDab(stroke, sampled.first())
        assertEquals(
            "A zero-pressure DOWN must floor at the calibrated minimum (.3 + .7 * curve(0))",
            .3f,
            first.size,
            1e-3f,
        )
        val steady = BrushProgramRuntime.evaluateDab(stroke, sampled.last())
        assertTrue("The zero-pressure contact dot is smaller than the steady-state dab", first.size < steady.size)
    }

    @Test
    fun defaultBrushZeroPressureDownProducesTheMinimumContactDot() {
        val points = line { index -> if (index == 0) 0f else .6f }
        val stroke = InkStroke(id = "default-dot", points = points, width = 64f, spacing = .08f)
        val sampled = dabs(stroke)
        val first = BrushProgramRuntime.evaluateDab(stroke, sampled.first())
        assertEquals(
            "The default brush floors size pressure at .35, so a zero-pressure DOWN renders the minimum dot",
            .35f,
            first.size,
            1e-3f,
        )
        val steady = BrushProgramRuntime.evaluateDab(stroke, sampled.last())
        assertTrue("The default zero-pressure contact dot must be smaller than the steady-state dab", first.size < steady.size)
    }

    @Test
    fun zeroVelocityOnTheFirstSampleIsNeutralWhenVelocityDynamicsAreEnabled() {
        val points = line { index -> if (index == 0) 0f else .35f }.mapIndexed { index, point ->
            if (index == 0) point.copy(sensorFlags = 0)
            else point.copy(velocity = .5f, sensorFlags = BrushSensorFlags.ALL)
        }
        val stroke = InkStroke(
            id = "velocity-zero",
            points = points,
            width = 64f,
            spacing = .08f,
            sizePressureEnabled = false,
            sizeSecondaryDynamics = BrushSecondaryDynamics(
                velocityEnabled = true,
                velocityMinimum = .4f,
                velocityScale = 1f,
                velocityCurve = listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f)),
            ),
        )
        val sampled = dabs(stroke)
        val first = BrushProgramRuntime.evaluateDab(stroke, sampled.first())
        assertEquals(
            "The first sample's velocity is not yet established and must stay neutral (factor 1), never the curve minimum",
            1f,
            first.size,
            1e-3f,
        )
        val later = BrushProgramRuntime.evaluateDab(stroke, sampled.last())
        assertTrue("Established samples must still apply velocity dynamics", later.size < 1f)
    }

    @Test
    fun zeroTiltOnTheFirstSampleIsNeutralWhenTiltDynamicsAreEnabled() {
        val points = line { index -> if (index == 0) 0f else .5f }.mapIndexed { index, point ->
            if (index == 0) point.copy(sensorFlags = 0)
            else point.copy(tilt = .8f, sensorFlags = BrushSensorFlags.ALL)
        }
        val stroke = InkStroke(
            id = "tilt-zero",
            points = points,
            width = 64f,
            spacing = .08f,
            sizePressureEnabled = false,
            sizeSecondaryDynamics = BrushSecondaryDynamics(
                tiltEnabled = true,
                tiltMinimum = .5f,
                tiltStart = .4f,
            ),
        )
        val sampled = dabs(stroke)
        val first = BrushProgramRuntime.evaluateDab(stroke, sampled.first())
        assertEquals(
            "The first sample's tilt is not yet established and must not modulate the first dab",
            1f,
            first.size,
            1e-3f,
        )
        val later = BrushProgramRuntime.evaluateDab(stroke, sampled.last())
        assertTrue("Established samples must still apply tilt dynamics", later.size < 1f)
    }

    private fun dabs(stroke: InkStroke): List<StrokePoint> =
        BrushProgramRuntime.sampleStampPoints(stroke, viewportWidth, viewportHeight, dual = false)

    private fun line(pressure: (Int) -> Float): List<StrokePoint> = (0 until 24).map { index ->
        StrokePoint(.1f + index * .03f, .5f, pressure = pressure(index), timeMs = (index * 6L).toLong())
    }
}
