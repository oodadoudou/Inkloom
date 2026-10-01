package app.inkloom.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import app.inkloom.document.PageElement
import app.inkloom.document.StructuredElementTransform
import app.inkloom.engine.v5.tools.SelectionAffineTransform
import app.inkloom.engine.v5.tools.SelectionRasterTransform

internal class MixedSelectionSource(
    val elements: List<PageElement>,
    val bitmap: Bitmap,
    val remainingBitmap: Bitmap? = null,
    val release: () -> Unit = {},
)

/** Presentation-only draft; source objects remain editable and are committed as geometry. */
internal class MixedSelectionDraft(val source: MixedSelectionSource) : AutoCloseable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, transform: SelectionRasterTransform, canvasWidth: Int, canvasHeight: Int) {
        if (source.bitmap.isRecycled || canvasWidth <= 0 || canvasHeight <= 0) return
        val columns = 32
        val rows = 32
        val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
        var index = 0
        for (row in 0..rows) for (column in 0..columns) {
            val x = source.bitmap.width * column / columns.toFloat()
            val y = source.bitmap.height * row / rows.toFloat()
            val mapped = transform.forward(x, y)
            vertices[index++] = mapped.first * canvasWidth / source.bitmap.width
            vertices[index++] = mapped.second * canvasHeight / source.bitmap.height
        }
        canvas.drawBitmapMesh(source.bitmap, columns, rows, vertices, 0, null, 0, paint)
    }

    fun transformed(transform: SelectionRasterTransform, rasterWidth: Int, rasterHeight: Int,
                    pageWidth: Float, pageHeight: Float): List<PageElement> = source.elements.map {
        StructuredElementTransform.map(it, transform, rasterWidth, rasterHeight, pageWidth, pageHeight)
    }

    override fun close() {
        source.bitmap.recycle()
        source.remainingBitmap?.takeIf { it !== source.bitmap }?.recycle()
        source.release()
    }
}
