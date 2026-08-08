package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.StrokeStabilization
import app.inkloom.engine.v2.input.NativeStrokeInputModeler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeStrokeInputModelerTest {
    @Test
    fun nativeSessionModelsFiltersAndResetsWithoutASecondImplementation() {
        NativeStrokeInputModeler().use { modeler ->
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
        NativeStrokeInputModeler().use { modeler ->
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
}
