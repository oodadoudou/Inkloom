package app.inkloom.engine.v3.brush

import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sin

/**
 * Proves the transient (live incremental) dab sequence is byte-for-byte identical to the final
 * stateless commit dab sequence for the same StrokeInput, which is a precondition for the
 * front-buffer→V3 handoff ever producing the same raster. Any divergence here is a
 * device-independent parity bug that no MovinkPad measurement could explain.
 */
class StrokeStampSamplerParityTest {
    @Before
    fun setUp() = BrushProgramRuntime.clearForTests()

    @After
    fun tearDown() = BrushProgramRuntime.clearForTests()

    private val viewportWidth = 1600f
    private val viewportHeight = 1200f

    @Test
    fun horizontalLineIncrementalMatchesFinal() = assertParity(horizontalLine())

    @Test
    fun diagonalLineIncrementalMatchesFinal() = assertParity(diagonalLine())

    @Test
    fun slowCurveIncrementalMatchesFinal() = assertParity(slowCurve())

    @Test
    fun fastSparseCurveIncrementalMatchesFinal() = assertParity(fastSparseCurve())

    @Test
    fun sCurveIncrementalMatchesFinal() = assertParity(sCurve())

    @Test
    fun sharpTurnIncrementalMatchesFinal() = assertParity(sharpTurn())

    @Test
    fun pressureRampUpIncrementalMatchesFinal() = assertParity(pressureRampUp())

    @Test
    fun pressureRampDownIncrementalMatchesFinal() = assertParity(pressureRampDown())

    @Test
    fun pressurePulseIncrementalMatchesFinal() = assertParity(pressurePulse())

    @Test
    fun tiltRampIncrementalMatchesFinal() = assertParity(tiltRamp())

    @Test
    fun tinyTwoMillimeterStrokeIncrementalMatchesFinal() = assertParity(tinyStroke())

    @Test
    fun tapIncrementalMatchesFinal() = assertParity(tap())

    private fun assertParity(points: List<StrokePoint>) {
        val stroke = InkStroke(
            id = "parity",
            points = points,
            width = 64f,
            spacing = .08f,
            sizePressureEnabled = true,
            sizePressureMinimum = .2f,
            sizePressureCurve = listOf(app.inkloom.document.BrushCurvePoint(0f, .25f), app.inkloom.document.BrushCurvePoint(1f, 1f)),
            renderSeed = 12345,
        )
        val sampler = BrushProgramRuntime.newIncrementalSampler(stroke, viewportWidth, viewportHeight, dual = false)
        var previous: List<StrokePoint> = emptyList()
        val progress = mutableListOf<StrokePoint>()
        for (point in points) {
            progress += point
            val dabs = BrushProgramRuntime.updateIncrementalSampler(
                sampler,
                stroke.copy(points = progress),
                viewportWidth,
                dual = false,
            )
            assertTrue("Incremental dab list must grow without rewriting earlier dabs", dabs.startsWith(previous))
            previous = dabs
        }
        val finalDabs = BrushProgramRuntime.sampleStampPoints(stroke, viewportWidth, viewportHeight, dual = false)
        assertEquals("Stateless final sample must equal the live incremental sequence", finalDabs, previous)
    }

    private fun List<StrokePoint>.startsWith(prefix: List<StrokePoint>): Boolean {
        if (prefix.size > size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private fun horizontalLine() = (0..24).map { index ->
        StrokePoint(.1f + index * .03f, .5f, pressure = .6f, timeMs = (index * 6L).toLong())
    }

    private fun diagonalLine() = (0..24).map { index ->
        val t = index / 24f
        StrokePoint(.1f + t * .8f, .6f - t * .4f, pressure = .5f, timeMs = (index * 5L).toLong())
    }

    private fun slowCurve() = (0..60).map { index ->
        val t = index / 60f
        StrokePoint(.1f + t * .8f, .5f + .06f * sin(t * 6f), pressure = .55f, timeMs = (index * 8L).toLong())
    }

    private fun fastSparseCurve() = listOf(0, 8, 17, 29, 44, 63).map { index ->
        val t = index / 63f
        StrokePoint(.1f + t * .8f, .5f + .08f * sin(t * 5f), pressure = .5f, timeMs = (index * 3L).toLong())
    }

    private fun sCurve() = (0..48).map { index ->
        val t = index / 48f
        StrokePoint(.1f + t * .8f, .5f + .18f * sin(t * (Math.PI * 2).toFloat()), pressure = .5f, timeMs = (index * 6L).toLong())
    }

    private fun sharpTurn() = (0..20).map { index ->
        val x = if (index <= 10) .1f + index * .06f else .7f + (index - 10) * .01f
        val y = if (index <= 10) .5f else .5f + (index - 10) * .045f
        StrokePoint(x, y, pressure = .6f, timeMs = (index * 5L).toLong())
    }

    private fun pressureRampUp() = (0..24).map { index ->
        StrokePoint(.1f + index * .03f, .5f, pressure = index / 24f, timeMs = (index * 6L).toLong())
    }

    private fun pressureRampDown() = (0..24).map { index ->
        StrokePoint(.1f + index * .03f, .5f, pressure = 1f - index / 24f, timeMs = (index * 6L).toLong())
    }

    private fun pressurePulse() = (0..24).map { index ->
        val pulse = if (index in 8..14) .85f else .2f
        StrokePoint(.1f + index * .03f, .5f, pressure = pulse, timeMs = (index * 6L).toLong())
    }

    private fun tiltRamp() = (0..24).map { index ->
        StrokePoint(.1f + index * .03f, .5f, pressure = .6f, tilt = index / 24f * .6f, timeMs = (index * 6L).toLong())
    }

    private fun tinyStroke() = (0..2).map { index ->
        StrokePoint(.49f + index * .004f, .5f, pressure = .5f, timeMs = (index * 4L).toLong())
    }

    private fun tap() = listOf(
        StrokePoint(.5f, .5f, pressure = .7f, timeMs = 0L),
        StrokePoint(.50001f, .5f, pressure = .7f, timeMs = 8L),
    )
}
