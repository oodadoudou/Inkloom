package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.ViewportState
import app.inkloom.document.ViewportTransform
import app.inkloom.engine.v5.coordinates.PagePoint
import app.inkloom.engine.v5.coordinates.ViewportFrame
import app.inkloom.engine.v5.coordinates.ViewportFrameId
import app.inkloom.engine.v5.input.PointerPacketMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewportFrameTest {
    @Test fun canonicalAffineRoundTripsEverySupportedZoomPanRotationCombination() {
        val viewportWidth = 1_824f
        val viewportHeight = 1_104f
        val pageWidth = 742f
        val pageHeight = 1_050f
        val points = listOf(0f to 0f, pageWidth to 0f, pageWidth / 2f to pageHeight / 2f, 13.25f to 987.75f, pageWidth to pageHeight)
        val scales = listOf(.25f, 1f, 2f, 4f, 8f, 16f)
        val rotations = listOf(0f, 30f, 37f, 90f, 180f, -135f)
        val pans = listOf(0f to 0f, 231.5f to -178.25f, -640f to 412f)

        scales.forEach { scale -> rotations.forEach { rotation -> pans.forEach { pan ->
            val transform = requireNotNull(ViewportFrame.create(
                ViewportFrameId(1L), viewportWidth, viewportHeight, pageWidth, pageHeight,
                ViewportState(scale, pan.first, pan.second, rotation),
            ))
            points.forEach { page ->
                val viewport = transform.pageToViewport(PagePoint(page.first, page.second))
                val roundTrip = transform.viewportToPage(viewport)
                assertEquals("x at scale=$scale rotation=$rotation pan=$pan", page.first, roundTrip.x, .002f)
                assertEquals("y at scale=$scale rotation=$rotation pan=$pan", page.second, roundTrip.y, .002f)
            }
        } } }
    }

    @Test fun rotationMapsCurrentHistoryAndCursorThroughTheSameAffinePair() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = View(context).apply { layout(0, 0, 1_200, 900) }
        val target = View(context).apply { layout(0, 0, 800, 600) }
        val viewport = ViewportState(2.35f, 84f, -57f, 37f)
        val mapper = PointerPacketMapper()
        val samples = listOf(420f to 330f, 510f to 390f, 640f to 475f)
        val event = movePacket(samples)

        mapper.map(event, source, target, viewport).use { packet ->
            assertTrue(packet != null)
            val mapped = requireNotNull(packet).event
            samples.forEachIndexed { index, raw ->
                val expected = ViewportTransform.viewToPage(
                    raw.first + (target.width - source.width) / 2f,
                    raw.second + (target.height - source.height) / 2f,
                    target.width.toFloat(),
                    target.height.toFloat(),
                    viewport,
                )
                val actualX = if (index < samples.lastIndex) mapped.getHistoricalX(0, index) else mapped.x
                val actualY = if (index < samples.lastIndex) mapped.getHistoricalY(0, index) else mapped.y
                assertEquals(expected.first, actualX, .01f)
                assertEquals(expected.second, actualY, .01f)
                val cursor = packet.frame.pageToViewport(PagePoint(actualX, actualY))
                assertEquals(raw.first, cursor.x, .01f)
                assertEquals(raw.second, cursor.y, .01f)
            }
        }
        event.recycle()
    }

    @Test fun frozenFrameMapsLaterPacketsWithoutReadingMutatedViewportState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = View(context).apply { layout(0, 0, 1_200, 900) }
        val target = View(context).apply { layout(0, 0, 800, 600) }
        val mapper = PointerPacketMapper()
        val frozen = requireNotNull(mapper.snapshot(source, target, ViewportState(4f, 120f, -80f, 22f)))
        val event = movePacket(listOf(510f to 390f))

        mapper.map(event, frozen).use { packet ->
            assertEquals(frozen.id, packet.frame.id)
            val page = frozen.viewportToPage(app.inkloom.engine.v5.coordinates.ViewportPoint(510f, 390f))
            assertEquals(page.x, packet.event.x, .01f)
            assertEquals(page.y, packet.event.y, .01f)
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
