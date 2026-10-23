package app.inkloom.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import app.inkloom.document.ComicFrame
import app.inkloom.document.FrameGeometry
import app.inkloom.document.FrameToolMode
import app.inkloom.document.InkLayer
import kotlin.math.abs
import kotlin.math.hypot

/** A frame tool gesture for the host to turn into a page edit on the frame folder. */
sealed interface FrameEditRequest {
    /** Divide the frame under the line's start (or middle) along the line; page fractions. */
    data class Divide(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : FrameEditRequest
    /** Add a rectangle frame. */
    data class AddRectangle(val left: Float, val top: Float, val right: Float, val bottom: Float) : FrameEditRequest
}

/** The frame border folder that the active layer is, or lies in. */
internal fun activeFrameFolder(layers: List<InkLayer>, activeLayerId: String): InkLayer? {
    var layer = layers.firstOrNull { it.id == activeLayerId }
    while (layer != null) {
        if (layer.frame != null) return layer
        layer = layer.parentId?.let { id -> layers.firstOrNull { it.id == id } }
    }
    return null
}

/** The frame tool on the canvas; coordinates are view pixels, frames visible-page fractions. */
internal class FrameToolController(private val density: Float) {
    var mode = FrameToolMode.DIVIDE
    var folderId: String? = null
    var frames: List<ComicFrame> = emptyList()
        set(value) { field = value; selected.retainAll(value.mapTo(hashSetOf()) { it.id }) }
    val selected = linkedSetOf<String>()
    var onRequest: (String, FrameEditRequest) -> Unit = { _, _ -> }
    var onChanged: () -> Unit = {}
    var onRejected: () -> Unit = {}

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var dragging = false

    fun down(x: Float, y: Float) {
        if (folderId == null) { onRejected(); return }
        startX = x; startY = y; currentX = x; currentY = y; dragging = true; onChanged()
    }

    fun move(x: Float, y: Float) { if (dragging) { currentX = x; currentY = y; onChanged() } }

    fun up(x: Float, y: Float, width: Float, height: Float, scale: Float) {
        if (!dragging) return
        dragging = false; currentX = x; currentY = y
        val folder = folderId ?: return
        val tap = hypot(x - startX, y - startY) < TAP_DP * density / scale
        when (mode) {
            FrameToolMode.DIVIDE -> if (!tap) {
                // As in CSP, Shift-free 45° steps: near-horizontal or near-vertical drags are straightened.
                var ex = x; var ey = y
                if (abs(ey - startY) < abs(ex - startX) * .08f) ey = startY else if (abs(ex - startX) < abs(ey - startY) * .08f) ex = startX
                onRequest(folder, FrameEditRequest.Divide(startX / width, startY / height, ex / width, ey / height))
            }
            FrameToolMode.RECTANGLE -> if (!tap) onRequest(folder, FrameEditRequest.AddRectangle(
                minOf(startX, x) / width, minOf(startY, y) / height, maxOf(startX, x) / width, maxOf(startY, y) / height))
            FrameToolMode.SELECT -> if (tap) FrameGeometry.frameAt(frames, x / width, y / height)?.let { index ->
                val id = frames[index].id
                if (!selected.remove(id)) selected += id
            }
        }
        onChanged()
    }

    fun cancel() { dragging = false; onChanged() }

    fun draw(canvas: Canvas, width: Float, height: Float, scale: Float) {
        if (folderId == null) return
        paint.strokeWidth = 1.5f * density / scale
        frames.forEach { frame ->
            path.reset(); path.moveTo(frame.x(0) * width, frame.y(0) * height)
            for (i in 1 until frame.count) path.lineTo(frame.x(i) * width, frame.y(i) * height)
            path.close()
            if (frame.id in selected) canvas.drawPath(path, fill)
            canvas.drawPath(path, paint)
        }
        if (!dragging) return
        paint.pathEffect = DashPathEffect(floatArrayOf(6f * density / scale, 4f * density / scale), 0f)
        when (mode) {
            FrameToolMode.DIVIDE -> canvas.drawLine(startX, startY, currentX, currentY, paint)
            FrameToolMode.RECTANGLE -> canvas.drawRect(minOf(startX, currentX), minOf(startY, currentY), maxOf(startX, currentX), maxOf(startY, currentY), paint)
            FrameToolMode.SELECT -> Unit
        }
        paint.pathEffect = null
    }

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xE02F80ED.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(60, 47, 128, 237) }

    private companion object { const val TAP_DP = 6f }
}
