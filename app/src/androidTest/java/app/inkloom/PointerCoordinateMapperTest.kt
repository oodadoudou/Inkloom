package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.ViewportState
import app.inkloom.document.ViewportTransform
import app.inkloom.engine.v2.input.PointerCoordinateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PointerCoordinateMapperTest {
    @Test fun rotationMapsCurrentHistoryAndCursorThroughTheSameAffinePair() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = View(context).apply { layout(0, 0, 1_200, 900) }
        val target = View(context).apply { layout(0, 0, 800, 600) }
        val viewport = ViewportState(2.35f, 84f, -57f, 37f)
        val mapper = PointerCoordinateMapper()
        val samples = listOf(420f to 330f, 510f to 390f, 640f to 475f)
        val event = movePacket(samples)

        assertTrue(mapper.mapCenteredViewport(event, source, target, viewport))
        samples.forEachIndexed { index, raw ->
            val expected = ViewportTransform.viewToPage(
                raw.first + (target.width - source.width) / 2f,
                raw.second + (target.height - source.height) / 2f,
                target.width.toFloat(),
                target.height.toFloat(),
                viewport,
            )
            val actualX = if (index < samples.lastIndex) event.getHistoricalX(0, index) else event.x
            val actualY = if (index < samples.lastIndex) event.getHistoricalY(0, index) else event.y
            assertEquals(expected.first, actualX, .01f)
            assertEquals(expected.second, actualY, .01f)
            val cursor = requireNotNull(mapper.mapPagePointToCenteredViewport(actualX, actualY, source, target, viewport))
            assertEquals(raw.first, cursor.first, .01f)
            assertEquals(raw.second, cursor.second, .01f)
        }
        event.recycle()
    }

    private fun movePacket(samples: List<Pair<Float, Float>>): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun coordinates(sample: Pair<Float, Float>) = arrayOf(MotionEvent.PointerCoords().apply {
            x = sample.first; y = sample.second; pressure = .75f; size = .04f
        })
        return MotionEvent.obtain(
            1L, 10L, MotionEvent.ACTION_MOVE, 1, properties, coordinates(samples.first()),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        ).apply {
            samples.drop(1).forEachIndexed { index, sample -> addBatch(20L + index * 10L, coordinates(sample), 0) }
        }
    }
}
