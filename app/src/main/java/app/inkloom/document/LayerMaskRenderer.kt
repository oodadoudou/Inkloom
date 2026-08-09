package app.inkloom.document

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

object LayerMaskRenderer {
    fun draw(canvas: Canvas, mask: LayerMask, width: Int, height: Int) {
        if (!mask.enabled) {
            canvas.drawColor(Color.WHITE)
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        if (mask.baseVisible) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        mask.regions.forEach { region ->
            if (region.points.size < 3) return@forEach
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
}
