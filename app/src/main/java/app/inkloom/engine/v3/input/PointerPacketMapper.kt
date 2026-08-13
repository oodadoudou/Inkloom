package app.inkloom.engine.v3.input

import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import app.inkloom.document.ViewportState
import app.inkloom.engine.v3.geometry.PagePoint
import app.inkloom.engine.v3.geometry.ViewportFrame
import app.inkloom.engine.v3.geometry.ViewportFrameId
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the only production conversion of complete Android pointer packets into page space.
 * [MotionEvent.transform] maps every pointer and historical sample with the exact inverse carried
 * by the returned immutable [ViewportFrame]. Callers must retain that frame for every render
 * derived from the mapped packet; mixing a mapped packet with a newer frame is invalid.
 */
internal class PointerPacketMapper {
    private val frameCounter = AtomicLong(0L)
    private val viewportToPageScratch = Matrix()

    data class MappedPacket(
        val frame: ViewportFrame,
        val event: MotionEvent,
    ) : AutoCloseable {
        override fun close() = event.recycle()
    }

    fun map(
        sourceEvent: MotionEvent,
        source: View,
        page: View,
        viewport: ViewportState = ViewportState(1f, 0f, 0f),
    ): MappedPacket? {
        val frame = snapshot(source, page, viewport) ?: return null
        return map(sourceEvent, frame)
    }

    fun map(sourceEvent: MotionEvent, frame: ViewportFrame): MappedPacket {
        val mapped = MotionEvent.obtain(sourceEvent)
        mapped.transform(frame.viewportToPageMatrix(viewportToPageScratch))
        return MappedPacket(frame, mapped)
    }

    fun snapshot(source: View, page: View, viewport: ViewportState): ViewportFrame? {
        if (source.width <= 0 || source.height <= 0 || page.width <= 0 || page.height <= 0) return null
        return ViewportFrame.create(
            id = ViewportFrameId(frameCounter.incrementAndGet()),
            viewportWidth = source.width.toFloat(),
            viewportHeight = source.height.toFloat(),
            pageWidth = page.width.toFloat(),
            pageHeight = page.height.toFloat(),
            viewport = viewport,
        )
    }

    /** Test/diagnostic forward mapping. Production cursor feedback remains at the physical
     * viewport packet and therefore does not perform a redundant page round trip. */
    fun pageToViewport(point: PagePoint, source: View, page: View, viewport: ViewportState) =
        snapshot(source, page, viewport)?.pageToViewport(point)
}
