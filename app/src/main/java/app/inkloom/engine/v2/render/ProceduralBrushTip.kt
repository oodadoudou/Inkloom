package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.inkloom.document.BrushEngineKind
import app.inkloom.document.InkStroke

/** Original built-in tips for system/spray presets that do not reference an imported material. */
internal object ProceduralBrushTip {
    fun supports(stroke: InkStroke): Boolean =
        stroke.tipAssetPath == null && stroke.tipAssetPaths.isEmpty() && (
            stroke.spray || stroke.stampCount > 1 || stroke.engineKind == BrushEngineKind.AIRBRUSH ||
                stroke.brushId.startsWith("system-decoration-")
            )

    fun create(stroke: InkStroke): Bitmap? {
        if (!supports(stroke)) return null
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        when {
            stroke.brushId.contains("sparkle", true) -> {
                val center = SIZE / 2f
                val outer = SIZE * .45f
                val inner = SIZE * .11f
                val star = Path()
                repeat(16) { index ->
                    val angle = -Math.PI / 2.0 + index * Math.PI / 8.0
                    val radius = if (index % 2 == 0) outer else inner
                    val x = center + kotlin.math.cos(angle).toFloat() * radius
                    val y = center + kotlin.math.sin(angle).toFloat() * radius
                    if (index == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                canvas.drawPath(star, paint)
            }
            stroke.brushId.contains("hatching", true) -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = SIZE * .1f
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(SIZE * .16f, SIZE * .84f, SIZE * .84f, SIZE * .16f, paint)
                canvas.drawLine(SIZE * .16f, SIZE * .16f, SIZE * .84f, SIZE * .84f, paint)
            }
            else -> canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE * .42f, paint)
        }
        return bitmap
    }

    private const val SIZE = 64
}
