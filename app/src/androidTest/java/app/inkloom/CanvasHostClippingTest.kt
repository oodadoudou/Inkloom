package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v3.render.CanvasHost
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasHostClippingTest {
    @Test
    fun lowLatencyPageHostClipsActiveInkToItsDockedViewportBounds() {
        val host = CanvasHost(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue(host.clipChildren)
        assertTrue(host.clipToPadding)
    }
}
