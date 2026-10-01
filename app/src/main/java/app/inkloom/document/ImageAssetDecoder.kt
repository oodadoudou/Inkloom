package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.BitmapRegionDecoder
import kotlin.math.floor
import kotlin.math.ceil

object ImageAssetDecoder {
    /** Decode only the source pixels intersecting the current (possibly rotated) canvas clip. */
    fun drawCropped(canvas: Canvas, path: String, crop: ImageCrop, destination: RectF, paint: Paint, highPrecision: Boolean = false) {
        if (destination.isEmpty) return
        val decoder = BitmapRegionDecoder.newInstance(path, false)
        try {
            val bounds = crop.pixelBounds(decoder.width, decoder.height)
            val clip = RectF(canvas.clipBounds)
            if (!clip.intersect(destination)) return
            val sx = bounds.width / destination.width(); val sy = bounds.height / destination.height()
            val region = Rect(
                floor(bounds.x + (clip.left - destination.left) * sx - 1).toInt().coerceIn(bounds.x, bounds.x + bounds.width),
                floor(bounds.y + (clip.top - destination.top) * sy - 1).toInt().coerceIn(bounds.y, bounds.y + bounds.height),
                ceil(bounds.x + (clip.right - destination.left) * sx + 1).toInt().coerceIn(bounds.x, bounds.x + bounds.width),
                ceil(bounds.y + (clip.bottom - destination.top) * sy + 1).toInt().coerceIn(bounds.y, bounds.y + bounds.height))
            if (region.isEmpty) return
            val bitmap = decoder.decodeRegion(region, BitmapFactory.Options().apply {
                inPreferredConfig = if(highPrecision) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(if(highPrecision) ColorSpace.Named.LINEAR_EXTENDED_SRGB else ColorSpace.Named.SRGB)
            }) ?: error("Image region could not be decoded")
            try {
                canvas.drawBitmap(bitmap, null, RectF(
                    destination.left + (region.left - bounds.x) / sx, destination.top + (region.top - bounds.y) / sy,
                    destination.left + (region.right - bounds.x) / sx, destination.top + (region.bottom - bounds.y) / sy), paint)
            } finally { bitmap.recycle() }
        } finally { decoder.recycle() }
    }

    fun decodeCropped(path: String, crop: ImageCrop): Bitmap? = runCatching {
        // The editable document currently composites in sRGB. Convert tagged RGB imports at
        // decode instead of treating wide-gamut component numbers as if they were sRGB values.
        val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
        val options = BitmapFactory.Options().apply { inPreferredColorSpace = srgb }
        var source = BitmapFactory.decodeFile(path, options) ?: return null
        if (source.colorSpace != srgb) {
            val converted = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            try {
                Canvas(converted).drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            } catch (failure: Throwable) {
                converted.recycle()
                throw failure
            }
            source.recycle()
            source = converted
        }
        val bounds = crop.pixelBounds(source.width, source.height)
        if (bounds.x == 0 && bounds.y == 0 && bounds.width == source.width && bounds.height == source.height) return source
        Bitmap.createBitmap(source, bounds.x, bounds.y, bounds.width, bounds.height).also { cropped -> if (cropped !== source) source.recycle() }
    }.getOrNull()
}
