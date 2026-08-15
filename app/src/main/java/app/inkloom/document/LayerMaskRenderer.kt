package app.inkloom.document

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

/** Read-only compatibility renderer for documents created before sparse V3 layer masks. */
object LayerMaskRenderer {
    /** Projects only regions intersecting the current dirty/visible clip into the mask layer. */
    fun draw(canvas: Canvas, mask: LayerMask, width: Int, height: Int) {
        val clip = canvas.clipBounds
        if (clip.isEmpty) return
        if (!mask.enabled) {
            canvas.drawRect(clip, Paint().apply { color = Color.WHITE })
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        if (mask.baseVisible) canvas.drawRect(clip, paint)
        mask.regions.forEach { region ->
            if (region.points.size < 3) return@forEach
            if (!intersectsClip(region, clip, width, height)) return@forEach
            val path = Path().apply {
                moveTo(region.points.first().x * width, region.points.first().y * height)
                region.points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
                close()
            }
            paint.alpha = (region.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            paint.blendMode = if (region.reveal) BlendMode.SRC_OVER else BlendMode.DST_OUT
            canvas.drawPath(path, paint)
        }
        paint.blendMode = null
        paint.alpha = 255
    }

    private fun intersectsClip(region: LayerMaskRegion, clip: Rect, width: Int, height: Int): Boolean {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        region.points.forEach { point ->
            val x = point.x * width
            val y = point.y * height
            left = minOf(left, x); top = minOf(top, y)
            right = maxOf(right, x); bottom = maxOf(bottom, y)
        }
        return RectF(left, top, right, bottom).intersects(
            clip.left.toFloat(), clip.top.toFloat(), clip.right.toFloat(), clip.bottom.toFloat(),
        )
    }
}
