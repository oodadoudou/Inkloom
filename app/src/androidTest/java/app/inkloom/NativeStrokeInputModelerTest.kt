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
}
