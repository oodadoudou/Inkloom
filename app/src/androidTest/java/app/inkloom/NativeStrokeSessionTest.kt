package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.StrokeStabilization
import app.inkloom.engine.v3.input.NativeStrokeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeStrokeSessionTest {
    @Test
    fun nativeSessionModelsFiltersAndResetsWithoutASecondImplementation() {
        NativeStrokeSession().use { modeler ->
            assertTrue(modeler.append(.25f, .75f, 0f, 10L, .2f, .3f, 1200, 1600, 0f, false, StrokeStabilization.REDUCE_WHEN_FAST))
            assertEquals(.25f, modeler.x, .00001f)
            assertEquals(.75f, modeler.y, .00001f)
            assertEquals(.05f, modeler.pressure, .00001f)
            assertFalse(modeler.append(.2501f, .7501f, 1f, 11L, .2f, .3f, 1200, 1600, 0f, false, StrokeStabilization.REDUCE_WHEN_FAST))
            assertEquals(10L, modeler.timeMs)

            modeler.reset()
            assertTrue(modeler.append(.9f, .1f, .8f, 20L, .4f, .5f, 1200, 1600, .8f, false, StrokeStabilization.REDUCE_WHEN_FAST))
            assertEquals(.9f, modeler.x, .00001f)
            assertEquals(.1f, modeler.y, .00001f)
            assertEquals(0f, modeler.velocity, .00001f)
        }
    }

    @Test
    fun historicalSamplesCrossJniAsOneOrderedBatch() {
        NativeStrokeSession().use { modeler ->
            modeler.putSample(0, .1f, .2f, .3f, 10L, .1f, .2f)
            modeler.putSample(1, .2f, .3f, .5f, 12L, .2f, .3f)
            modeler.putSample(2, .4f, .5f, .8f, 14L, .3f, .4f)

            val accepted = modeler.appendBatch(
                sampleCount = 3,
                viewportWidthPx = 1200,
                viewportHeightPx = 1600,
                stabilization = 0f,
                stabilizationBySpeed = false,
                stabilizationBySpeedMode = StrokeStabilization.REDUCE_WHEN_FAST,
            )

            assertEquals(3, accepted)
            assertEquals(.1f, modeler.x(0), .00001f)
            assertEquals(.2f, modeler.x(1), .00001f)
            assertEquals(.4f, modeler.x(2), .00001f)
            assertEquals(10L, modeler.timeMs(0))
            assertEquals(12L, modeler.timeMs(1))
            assertEquals(14L, modeler.timeMs(2))
            assertTrue(modeler.velocity(2) > 0f)
        }
    }

    @Test
    fun allZeroCorrectionPreservesEveryAuthoredTurnAndPenUpEndpoint() {
        val authored = listOf(
            .12f to .22f,
            .23f to .41f,
            .31f to .28f,
            .47f to .63f,
            .58f to .35f,
            .76f to .71f,
        )
        NativeStrokeSession().use { modeler ->
            authored.forEachIndexed { index, point ->
                modeler.putSample(
                    index = index,
                    rawX = point.first,
                    rawY = point.second,
                    adjustedPressure = .7f,
                    sampleTimeMs = 10L + index * 4L,
                    sampleTilt = 0f,
                    sampleOrientation = 0f,
                    force = index == authored.lastIndex,
                )
            }

            assertEquals(
                authored.size,
                modeler.appendBatch(
                    authored.size,
                    1600,
                    1200,
                    stabilization = 0f,
                    stabilizationBySpeed = false,
                    stabilizationBySpeedMode = StrokeStabilization.REDUCE_WHEN_FAST,
                    streamLine = 0f,
                    procreateStabilization = 0f,
                    motionFiltering = 0f,
                ),
            )
            authored.forEachIndexed { index, point ->
                assertEquals("X sample $index", point.first, modeler.x(index), .00001f)
                assertEquals("Y sample $index", point.second, modeler.y(index), .00001f)
            }
        }
    }

    @Test
    fun orientationAndTimeRemainContinuousAcrossDeviceWrapAndOutOfOrderInput() {
        NativeStrokeSession().use { modeler ->
            modeler.putSample(0, .1f, .1f, .5f, 20L, 0f, 3.13f)
            modeler.putSample(1, .3f, .3f, .5f, 19L, 0f, -3.13f)

            assertEquals(
                2,
                modeler.appendBatch(2, 1200, 1600, 0f, false, StrokeStabilization.REDUCE_WHEN_FAST),
            )
            assertTrue("Wrapped orientation must continue forward", modeler.orientation(1) > modeler.orientation(0))
            assertTrue("Modeled timestamps must be monotonic", modeler.timeMs(1) > modeler.timeMs(0))
        }
    }

    @Test
    fun magnifiedCanvasKeepsSubPagePixelSamplesThatAreDistinctOnScreen() {
        NativeStrokeSession().use { modeler ->
            assertTrue(modeler.append(.25f, .75f, .7f, 10L, 0f, 0f, 9_600, 12_800, 0f, false, StrokeStabilization.REDUCE_WHEN_FAST))
            assertTrue(
                "A sample several physical pixels away at 8x zoom must not be discarded by a normalized threshold",
                modeler.append(.2501f, .7501f, .7f, 11L, 0f, 0f, 9_600, 12_800, 0f, false, StrokeStabilization.REDUCE_WHEN_FAST),
            )
            assertEquals(.2501f, modeler.x, .00001f)
            assertEquals(.7501f, modeler.y, .00001f)
        }
    }

    @Test
    fun procreateStreamlineStabilizationAndMotionFilteringRemainIndependent() {
        fun path(streamLine: Float = 0f, movingAverage: Float = 0f, motion: Float = 0f, expression: Float = 0f): List<Pair<Float, Float>> {
            val result = mutableListOf<Pair<Float, Float>>()
            NativeStrokeSession().use { modeler ->
                repeat(12) { index ->
                    val x = .1f + index * .06f
                    val y = .5f + if (index % 2 == 0) .025f else -.025f
                    if (modeler.append(
                            rawX = x,
                            rawY = y,
                            adjustedPressure = .7f,
                            sampleTimeMs = 10L + index,
                            sampleTilt = 0f,
                            sampleOrientation = 0f,
                            viewportWidthPx = 1200,
                            viewportHeightPx = 1600,
                            stabilization = 0f,
                            stabilizationBySpeed = false,
                            stabilizationBySpeedMode = StrokeStabilization.REDUCE_WHEN_FAST,
                            streamLine = streamLine,
                            procreateStabilization = movingAverage,
                            motionFiltering = motion,
                            motionFilteringExpression = expression,
                        )) result += modeler.x to modeler.y
                }
            }
            return result
        }
        fun verticalVariation(points: List<Pair<Float, Float>>): Float = points.zipWithNext().sumOf { (first, second) -> kotlin.math.abs(second.second - first.second).toDouble() }.toFloat()

        val raw = path()
        val streamlined = path(streamLine = .8f)
        val stabilized = path(movingAverage = .8f)
        val filtered = path(motion = .8f)
        val expressive = path(motion = .8f, expression = 1f)

        assertTrue("StreamLine must trail the input path", streamlined.last().first < raw.last().first)
        assertTrue("Moving-average Stabilization must reduce wobble", verticalVariation(stabilized) < verticalVariation(raw))
        assertTrue("Motion Filtering must remove extrema", verticalVariation(filtered) < verticalVariation(raw))
        assertTrue("Expression must restore more path variation", verticalVariation(expressive) > verticalVariation(filtered))
        assertTrue("The three controls must not collapse to the same path", streamlined != stabilized && stabilized != filtered)
    }

    @Test
    fun procreateStreamlinePressureSmoothsPressureWithoutChangingPathSetting() {
        NativeStrokeSession().use { modeler ->
            assertTrue(modeler.append(.1f, .5f, .1f, 10L, 0f, 0f, 1200, 1600, 0f, false, StrokeStabilization.REDUCE_WHEN_FAST, streamLinePressure = .8f))
            assertTrue(modeler.append(.3f, .5f, 1f, 11L, 0f, 0f, 1200, 1600, 0f, false, StrokeStabilization.REDUCE_WHEN_FAST, streamLinePressure = .8f))
            assertTrue(modeler.pressure in .1f..<1f)
            assertEquals(.3f, modeler.x, .0001f)
        }
    }
}
