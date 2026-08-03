package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StrokeStampSamplerTest {
    @Test fun producesUniformStampsAcrossSparseInput() {
        val sampled = StrokeStampSampler.sample(
            listOf(StrokePoint(0f, 0f), StrokePoint(1f, 0f)),
            canvasWidth = 100f,
            canvasHeight = 100f,
            spacingFor = { 20f },
        )

        assertEquals(6, sampled.size)
        sampled.forEachIndexed { index, point -> assertEquals(index * .2f, point.x, .0001f) }
    }

    @Test fun resultDoesNotDependOnMotionEventSampleDensity() {
        val sparse = listOf(StrokePoint(0f, .5f), StrokePoint(1f, .5f))
        val dense = listOf(StrokePoint(0f, .5f), StrokePoint(.13f, .5f), StrokePoint(.51f, .5f), StrokePoint(1f, .5f))

        val sparseResult = StrokeStampSampler.sample(sparse, 100f, 100f) { 17f }
        val denseResult = StrokeStampSampler.sample(dense, 100f, 100f) { 17f }

        assertEquals(sparseResult.size, denseResult.size)
        sparseResult.zip(denseResult).forEach { (left, right) ->
            assertEquals(left.x, right.x, .0001f)
            assertEquals(left.y, right.y, .0001f)
        }
    }

    @Test fun interpolatesPressureAndTimeAtStampLocations() {
        val sampled = StrokeStampSampler.sample(
            listOf(StrokePoint(0f, 0f, pressure = .2f, timeMs = 100L, velocity = .2f), StrokePoint(1f, 0f, pressure = 1f, timeMs = 300L, velocity = 1f)),
            100f,
            100f,
        ) { 50f }

        assertEquals(3, sampled.size)
        assertEquals(.6f, sampled[1].pressure, .0001f)
        assertEquals(.6f, sampled[1].velocity, .0001f)
        assertEquals(200L, sampled[1].timeMs)
    }

    @Test fun reportsDirectionUsingCanvasAspectRatio() {
        val points = listOf(StrokePoint(0f, 0f), StrokePoint(.5f, 1f))
        assertEquals(45f, StrokeStampSampler.directionDegrees(points, 0, 200f, 100f), .0001f)
        assertEquals(45f, StrokeStampSampler.directionDegrees(points, 1, 200f, 100f), .0001f)
    }

    @Test fun continuousPlotPreservesTimeBasedStationaryStamps() {
        val points = listOf(
            StrokePoint(.5f, .5f, timeMs = 0L),
            StrokePoint(.5f, .5f, timeMs = 32L),
            StrokePoint(.5f, .5f, timeMs = 64L),
        )

        val ordinary = StrokeStampSampler.sample(points, 100f, 100f) { 10f }
        val continuous = StrokeStampSampler.sample(
            points,
            100f,
            100f,
            includeStationaryPoints = true,
        ) { 10f }

        assertEquals(1, ordinary.size)
        assertEquals(3, continuous.size)
        assertSame(points[1], continuous[1])
        assertSame(points[2], continuous[2])
    }
}
