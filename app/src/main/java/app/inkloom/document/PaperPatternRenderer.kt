package app.inkloom.document

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** One page-relative pattern for canvas, thumbnails and exports at every resolution. */
object PaperPatternRenderer {
    fun draw(canvas: Canvas, pattern: PaperPattern, density: PaperDensity, width: Float, height: Float) {
        if (pattern == PaperPattern.PLAIN || width <= 0f || height <= 0f) return
        val scale = width / 1200f
        val step = (when (pattern) {
            PaperPattern.DOT -> 28f
            PaperPattern.GRID -> 32f
            else -> 36f
        }) * density.spacingScale * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(227, 227, 227)
            strokeWidth = (1.5f * scale).coerceAtLeast(.55f)
        }
        when (pattern) {
            PaperPattern.PLAIN -> Unit
            PaperPattern.DOT -> {
                paint.color = Color.rgb(205, 205, 205)
                var x = step / 2f
                while (x < width) {
                    var y = step / 2f
                    while (y < height) { canvas.drawCircle(x, y, (1.4f * scale).coerceAtLeast(.65f), paint); y += step }
                    x += step
                }
            }
            PaperPattern.GRID, PaperPattern.RULED -> {
                if (pattern == PaperPattern.GRID) {
                    var x = step
                    while (x < width) { canvas.drawLine(x, 0f, x, height, paint); x += step }
                }
                var y = step
                while (y < height) { canvas.drawLine(0f, y, width, y, paint); y += step }
            }
            PaperPattern.CORNELL -> {
                val cueX = width * .28f
                val summaryY = height * .78f
                paint.color = Color.rgb(212, 212, 212)
                canvas.drawLine(cueX, 0f, cueX, summaryY, paint)
                canvas.drawLine(0f, summaryY, width, summaryY, paint)
                paint.color = Color.rgb(231, 231, 231)
                var y = step
                while (y < summaryY) { canvas.drawLine(cueX, y, width, y, paint); y += step }
                y = summaryY + step
                while (y < height) { canvas.drawLine(0f, y, width, y, paint); y += step }
            }
        }
    }
}
