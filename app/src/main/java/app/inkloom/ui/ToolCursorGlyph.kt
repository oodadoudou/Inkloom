package app.inkloom.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.inkloom.model.EditorTool

/** Original monochrome glyphs. The contact origin is never displaced by the tool badge. */
internal class ToolCursorGlyph {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pipette = Path().apply {
        moveTo(3f, 19f); lineTo(5f, 14f); lineTo(15f, 4f); lineTo(19f, 8f)
        lineTo(9f, 18f); close()
        moveTo(13f, 2f); lineTo(21f, 10f)
        moveTo(16f, 5f); lineTo(20f, 1f); lineTo(23f, 4f); lineTo(19f, 8f)
    }
    private val bucket = Path().apply {
        moveTo(2f, 11f); lineTo(11f, 2f); lineTo(20f, 11f); lineTo(11f, 20f); close()
        moveTo(5f, 11f); lineTo(17f, 11f)
        moveTo(8f, 5f); lineTo(8f, 0f)
        moveTo(22f, 12f); cubicTo(16f, 20f, 27f, 20f, 22f, 12f)
    }
    private val gradient = Path().apply {
        addRect(2f, 2f, 22f, 20f, Path.Direction.CW)
        for (x in intArrayOf(5, 7, 9, 12, 16)) { moveTo(x.toFloat(), 5f); lineTo(x.toFloat(), 17f) }
    }
    fun draw(canvas: Canvas, code: Int, x: Float, y: Float, density: Float): Boolean {
        val path = when (code) { 1 -> pipette; 2 -> bucket; 3 -> gradient; else -> return false }
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.scale(density, density)
        // Small cross locates the sampled/fill pixel; badge sits clear of it.
        repeat(2) { pass ->
            val outer = pass == 0
            paint.color = if (outer) Color.WHITE else Color.BLACK
            paint.strokeWidth = if (outer) 3.5f else 1.4f
            canvas.drawLine(-3f, 0f, 3f, 0f, paint)
            canvas.drawLine(0f, -3f, 0f, 3f, paint)
            canvas.save()
            canvas.translate(7f, -29f)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
        canvas.restoreToCount(save)
        return true
    }
    companion object {
        fun code(tool: EditorTool): Int = when (tool) {
            EditorTool.EYEDROPPER -> 1
            EditorTool.FILL -> 2
            EditorTool.GRADIENT -> 3
            else -> 0
        }
    }
}
