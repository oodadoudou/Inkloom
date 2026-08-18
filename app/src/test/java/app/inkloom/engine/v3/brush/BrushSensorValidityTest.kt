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
 * Pins the "not-yet-established sensor stays neutral" contract at the dab-parameter level: the
 * first modeled sample of a stroke carries no sensor validity (velocity is 0 by construction,
 * tilt/direction are device defaults), and brush dynamics must not feed that zero through a
 * response curve as an extreme value. Established samples still apply their dynamics.
 */
class BrushSensorValidityTest {
    @Before
    fun setUp() = BrushProgramRuntime.clearForTests()

    @After
    fun tearDown() = BrushProgramRuntime.clearForTests()

    private val viewportWidth = 1600f
    private val viewportHeight = 1200f

    @Test
    fun notYetEstablishedVelocitySkipsVelocityDynamics() {
        val points = listOf(
            StrokePoint(.1f, .5f, velocity = 0f, sensorFlags = 0, timeMs = 0L),
            StrokePoint(.3f, .5f, velocity = .5f, sensorFlags = BrushSensorFlags.ALL, timeMs = 5L),
            StrokePoint(.6f, .5f, velocity = .5f, sensorFlags = BrushSensorFlags.ALL, timeMs = 10L),
        )
        val stroke = InkStroke(
            id = "sensor-velocity",
            points = points,
            width = 64f,
            spacing = .08f,
            sizePressureEnabled = false,
            sizeSecondaryDynamics = BrushSecondaryDynamics(
                velocityEnabled = true,
                velocityMinimum = .4f,
                velocityCurve = listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f)),
            ),
        )
        val dabs = BrushProgramRuntime.sampleStampPoints(stroke, viewportWidth, viewportHeight, dual = false)
        val first = BrushProgramRuntime.evaluateDab(stroke, dabs.first())
        assertEquals("Unknown velocity must map to neutral factor 1", 1f, first.size, 1e-3f)
        val later = BrushProgramRuntime.evaluateDab(stroke, dabs.last())
        assertTrue("Established velocity must still modulate the dab", later.size < 1f)
    }

    @Test
    fun notYetEstablishedTiltSkipsTiltDynamics() {
        val points = listOf(
            StrokePoint(.1f, .5f, tilt = 0f, sensorFlags = 0, timeMs = 0L),
            StrokePoint(.3f, .5f, tilt = .8f, sensorFlags = BrushSensorFlags.ALL, timeMs = 5L),
            StrokePoint(.6f, .5f, tilt = .8f, sensorFlags = BrushSensorFlags.ALL, timeMs = 10L),
        )
        val stroke = InkStroke(
            id = "sensor-tilt",
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
        val dabs = BrushProgramRuntime.sampleStampPoints(stroke, viewportWidth, viewportHeight, dual = false)
        val first = BrushProgramRuntime.evaluateDab(stroke, dabs.first())
        assertEquals("Unknown tilt must not modulate the first dab", 1f, first.size, 1e-3f)
        val later = BrushProgramRuntime.evaluateDab(stroke, dabs.last())
        assertTrue("Established tilt must still modulate the dab", later.size < 1f)
    }

    @Test
    fun establishedSensorsStillApplyTheirDynamics() {
        val points = listOf(
            StrokePoint(.1f, .5f, velocity = .5f, sensorFlags = BrushSensorFlags.ALL, timeMs = 0L),
            StrokePoint(.3f, .5f, velocity = .5f, sensorFlags = BrushSensorFlags.ALL, timeMs = 5L),
        )
        val stroke = InkStroke(
            id = "sensor-established",
            points = points,
            width = 64f,
            spacing = .08f,
            sizePressureEnabled = false,
            sizeSecondaryDynamics = BrushSecondaryDynamics(
                velocityEnabled = true,
                velocityMinimum = .4f,
                velocityCurve = listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f)),
            ),
        )
        val dabs = BrushProgramRuntime.sampleStampPoints(stroke, viewportWidth, viewportHeight, dual = false)
        val first = BrushProgramRuntime.evaluateDab(stroke, dabs.first())
        assertTrue("Established velocity .5 must modulate below neutral", first.size < 1f)
    }
}
