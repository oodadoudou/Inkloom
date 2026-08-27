package app.inkloom.engine.v5.selection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor

/** Bounded anti-aliased rasterization of one immutable canonical lasso. */
object SelectionMaskRasterizer {
    fun rasterize(
        contour: CanonicalLassoContour,
        documentWidth: Float,
        documentHeight: Float,
        logicalWidth: Int,
        logicalHeight: Int,
        tileSize: Int = MaskTileSurface.DEFAULT_TILE_SIZE,
    ): MaskTileSurfaceSnapshot? {
        require(contour.closed) { "Only a finalized lasso can become selection truth" }
        require(documentWidth > 0f && documentHeight > 0f)
        require(logicalWidth > 0 && logicalHeight > 0)
        val scaleX = logicalWidth / documentWidth
        val scaleY = logicalHeight / documentHeight
        val left = floor(contour.points.minOf { it.x * scaleX } - AA_HALO).toInt().coerceIn(0, logicalWidth - 1)
        val top = floor(contour.points.minOf { it.y * scaleY } - AA_HALO).toInt().coerceIn(0, logicalHeight - 1)
        val right = ceil(contour.points.maxOf { it.x * scaleX } + AA_HALO).toInt().coerceIn(left + 1, logicalWidth)
        val bottom = ceil(contour.points.maxOf { it.y * scaleY } + AA_HALO).toInt().coerceIn(top + 1, logicalHeight)
        val path = Path().apply {
            fillType = when (contour.windingRule) {
                LassoWindingRule.EVEN_ODD -> Path.FillType.EVEN_ODD
            }
            moveTo(contour.points.first().x * scaleX, contour.points.first().y * scaleY)
            contour.points.drop(1).forEach { point -> lineTo(point.x * scaleX, point.y * scaleY) }
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val surface = MaskTileSurface(logicalWidth, logicalHeight, tileSize)
        var hasCoverage = false
        surface.beginWrite("V5 canonical lasso").run {
            var bandTop = top
            while (bandTop < bottom) {
                val bandBottom = minOf(bottom, bandTop + MAX_BAND_HEIGHT)
                val bitmap = Bitmap.createBitmap(right - left, bandBottom - bandTop, Bitmap.Config.ALPHA_8)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.translate(-left.toFloat(), -bandTop.toFloat())
                    canvas.drawPath(path, paint)
                    val alpha = ByteArray(bitmap.rowBytes * bitmap.height)
                    bitmap.copyPixelsToBuffer(ByteBuffer.wrap(alpha))
                    if (alpha.any { it.toInt() != 0 }) {
                        hasCoverage = true
                        composeAlpha(
                            PixelRect(left, bandTop, right, bandBottom),
                            alpha,
                            rowStride = bitmap.rowBytes,
                            mode = MaskComposeMode.REPLACE,
                        )
                    }
                } finally {
                    bitmap.recycle()
                }
                bandTop = bandBottom
            }
            if (hasCoverage) commit().close() else cancel()
        }
        return if (hasCoverage) surface.snapshot() else null
    }

    private const val AA_HALO = 2f
    private const val MAX_BAND_HEIGHT = 1_024
}
