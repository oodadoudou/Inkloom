package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import kotlin.math.roundToInt

/** One orientation-aware, bounded mapping for imported paper previews and page backgrounds. */
object ImagePaperRenderer {
    fun dimensions(file: File): Pair<Int, Int> {
        var dimensions = 0 to 0
        val probe = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            require(info.size.width in 1..20_000 && info.size.height in 1..20_000) { "Image dimensions exceed 20000 px" }
            dimensions = info.size.width to info.size.height
            decoder.setTargetSize(32, 32)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        probe.recycle()
        return dimensions
    }

    fun render(background: PageBackground.Image, requestedWidth: Int): Bitmap {
        val file = File(background.assetPath)
        val dimensions = dimensions(file)
        val ratio = background.paperAspectRatio.takeIf { it.isFinite() && it > 0f }
            ?: (dimensions.first.toFloat() / dimensions.second)
        val width = requestedWidth.coerceIn(1, 4096)
        val height = (width / ratio).roundToInt().coerceAtLeast(1)
        val scale = minOf(1f, 4096f / maxOf(width, height))
        val outputWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val outputHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val source = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val sampling = minOf(1f, maxOf(outputWidth, outputHeight).toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * sampling).roundToInt().coerceAtLeast(1),
                (info.size.height * sampling).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }
        try {
            val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            output.eraseColor(background.paperColorArgb.toInt())
            val sx = outputWidth.toFloat() / source.width
            val sy = outputHeight.toFloat() / source.height
            val fit = if (background.cropToFill) maxOf(sx, sy) else minOf(sx, sy)
            val w = source.width * fit; val h = source.height * fit
            Canvas(output).drawBitmap(source, null,
                RectF((outputWidth-w)/2, (outputHeight-h)/2, (outputWidth+w)/2, (outputHeight+h)/2),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return output
        } finally { source.recycle() }
    }
}
