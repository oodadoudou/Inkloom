package app.inkloom.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import app.inkloom.document.*
import kotlin.math.*

/** One draft owns all corner gestures; only explicit confirmation publishes a document edit. */
internal class ImageCanvasTransform(
    val original: PageElement.Image,
    val pageWidth: Float,
    val pageHeight: Float,
    private val density: Float = 1f,
    private val changed: (PageElement.Image) -> Unit,
) {
    var current = original
        private set
    var mode = ImageTransformMode.FREEFORM
    private var start = original
    private var handle = ImageTransformHandle.MOVE
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var navigating = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun reset() { current = original; dragging = false; changed(current) }
    fun replace(image: PageElement.Image) { current = image; dragging = false; changed(current) }

    fun touch(event: MotionEvent, x: Float, y: Float, viewWidth: Int, viewHeight: Int, zoom: Float): Boolean {
        if (event.pointerCount > 1) {
            if (dragging) { current = start; changed(current); dragging = false }
            navigating = true
            return false
        }
        if (navigating) {
            if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) navigating = false
            return false
        }
        val px = x * pageWidth / viewWidth.coerceAtLeast(1)
        val py = y * pageHeight / viewHeight.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                start = current; downX = px; downY = py; dragging = true
                val radius = 24f * density / zoom.coerceAtLeast(.1f)
                handle = handles(viewWidth, viewHeight, zoom).minByOrNull { hypot(it.second.first - x, it.second.second - y) }
                    ?.takeIf { hypot(it.second.first - x, it.second.second - y) <= radius }?.first ?: ImageTransformHandle.MOVE
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (dragging) {
                current = ImageTransformGeometry.drag(start, pageWidth, pageHeight, handle, downX, downY, px, py, mode)
                changed(current)
                if (event.actionMasked == MotionEvent.ACTION_UP) dragging = false
            }
            MotionEvent.ACTION_CANCEL -> if (dragging) { current = start; changed(current); dragging = false }
        }
        return true
    }

    fun handles(w: Int, h: Int, zoom: Float): List<Pair<ImageTransformHandle, Pair<Float, Float>>> =
        ImageTransformHandle.entries.filter { it != ImageTransformHandle.MOVE }.map { handle ->
            val point = ImageTransformGeometry.point(current, pageWidth, pageHeight, handle.fx, handle.fy)
            var x = point.first * w / pageWidth
            var y = point.second * h / pageHeight
            if (handle == ImageTransformHandle.ROTATE) {
                val angle = Math.toRadians(current.rotationDegrees.toDouble())
                x += (sin(angle) * 32f * density / zoom.coerceAtLeast(.1f)).toFloat()
                y -= (cos(angle) * 32f * density / zoom.coerceAtLeast(.1f)).toFloat()
            }
            handle to (x to y)
        }

    fun draw(canvas: Canvas, w: Int, h: Int, zoom: Float) {
        val nodes = handles(w, h, zoom)
        val path = Path()
        nodes.take(8).forEachIndexed { i, node ->
            if (i == 0) path.moveTo(node.second.first, node.second.second) else path.lineTo(node.second.first, node.second.second)
        }
        path.close()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density / zoom.coerceAtLeast(.1f); paint.color = Color.rgb(57, 103, 85)
        canvas.drawPath(path, paint)
        val top = nodes.first { it.first == ImageTransformHandle.TOP }.second
        val rotate = nodes.last().second
        canvas.drawLine(top.first, top.second, rotate.first, rotate.second, paint)
        nodes.forEach { (_, point) ->
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            canvas.drawCircle(point.first, point.second, 6f * density / zoom.coerceAtLeast(.1f), paint)
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(57, 103, 85)
            canvas.drawCircle(point.first, point.second, 6f * density / zoom.coerceAtLeast(.1f), paint)
        }
    }
}
