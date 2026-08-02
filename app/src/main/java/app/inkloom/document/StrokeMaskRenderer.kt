package app.inkloom.document

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader

object StrokeMaskRenderer {
    fun draw(canvas: Canvas, paint: Paint, masks: List<StrokeMaskDab>, width: Int, height: Int) {
        paint.style = Paint.Style.FILL
        paint.colorFilter = null
        paint.maskFilter = null
        paint.alpha = 255
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        masks.forEach { mask -> drawDab(canvas, paint, mask, width, height) }
        paint.shader = null
        paint.xfermode = null
        paint.alpha = 255
    }

    private fun drawDab(canvas: Canvas, paint: Paint, mask: StrokeMaskDab, width: Int, height: Int) {
        val radiusX = (mask.radiusX * width).coerceAtLeast(.5f)
        val radiusY = (mask.radiusY * height).coerceAtLeast(.5f)
        val centerX = mask.x * width
        val centerY = mask.y * height
        val opacityAlpha = (mask.opacity.coerceIn(0f, 1f) * 255).toInt()
        val hardness = mask.hardness.coerceIn(0f, 1f)
        paint.shader = if (hardness >= .995f) {
            paint.color = Color.argb(opacityAlpha, 255, 255, 255)
            null
        } else {
            RadialGradient(
                0f,
                0f,
                1f,
                intArrayOf(Color.argb(opacityAlpha, 255, 255, 255), Color.argb(opacityAlpha, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, hardness.coerceAtLeast(.001f), 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(mask.rotationDegrees)
        canvas.scale(radiusX, radiusY)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
    }
}
