package app.inkloom.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import app.inkloom.engine.v5.tools.*
import kotlin.math.*

internal data class SelectionTransformUiState(val pose: SelectionTransformPose, val mode: SelectionTransformMode)

/** Geometry owns the draft; its callback always samples the original raster and selection. */
internal class SelectionCanvasTransform(
    val original: SelectionTransformPose,
    private val rasterWidth: Int,
    private val rasterHeight: Int,
    private val density: Float,
    private val changed: (SelectionTransformPose) -> Unit,
) {
    var pose = original
        private set
    var mode = SelectionTransformMode.FREEFORM
    private var start = original
    private var node = SelectionTransformNode.MOVE
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var navigating = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun replace(next: SelectionTransformPose) {
        if (next == pose) return
        pose = next
        changed(next)
    }
    fun reset() = replace(original)
    fun cancelGesture() {
        if (dragging) { dragging = false; replace(start) }
    }

    fun touch(event: MotionEvent, x: Float, y: Float, w: Int, h: Int, zoom: Float): Boolean {
        if (event.pointerCount > 1) { cancelGesture(); navigating = true; return false }
        if (navigating) {
            if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) navigating = false
            return false
        }
        val px = x * rasterWidth / w.coerceAtLeast(1)
        val py = y * rasterHeight / h.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                start = pose; startX = px; startY = py; dragging = true
                val radius = min(24f * density / zoom.coerceAtLeast(.1f),
                    min(pose.width * w / rasterWidth, pose.height * h / rasterHeight) * .3f).coerceAtLeast(6f * density / zoom.coerceAtLeast(.1f))
                node = handles(w, h, zoom).minByOrNull { hypot(it.second.first - x, it.second.second - y) }
                    ?.takeIf { hypot(it.second.first - x, it.second.second - y) <= radius }?.first ?: SelectionTransformNode.MOVE
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (dragging) {
                replace(start.drag(node, startX, startY, px, py, mode))
                if (event.actionMasked == MotionEvent.ACTION_UP) dragging = false
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun handles(w: Int, h: Int, zoom: Float) = SelectionTransformNode.entries.filter { it != SelectionTransformNode.MOVE }.map { node ->
        val point = pose.point(node.x, node.y)
        var x = point.first * w / rasterWidth; var y = point.second * h / rasterHeight
        if (node == SelectionTransformNode.ROTATE) {
            val offset = SelectionTransformPose.rotate(0f, -32f * density / zoom.coerceAtLeast(.1f), pose.angle)
            x += offset.first; y += offset.second
        }
        node to (x to y)
    }

    fun draw(canvas: Canvas, w: Int, h: Int, zoom: Float) {
        val points = handles(w, h, zoom)
        val scale = density / zoom.coerceAtLeast(.1f)
        val path = Path()
        points.take(8).forEachIndexed { i, (_, p) -> if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second) }
        path.close()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * scale; paint.color = Color.rgb(57, 103, 85)
        canvas.drawPath(path, paint)
        val top = points[1].second; val rotate = points.last().second
        canvas.drawLine(top.first, top.second, rotate.first, rotate.second, paint)
        points.forEach { (_, point) ->
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            canvas.drawCircle(point.first, point.second, 6f * scale, paint)
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(57, 103, 85)
            canvas.drawCircle(point.first, point.second, 6f * scale, paint)
        }
    }
}
