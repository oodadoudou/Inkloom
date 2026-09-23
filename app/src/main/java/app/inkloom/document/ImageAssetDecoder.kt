package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint

object ImageAssetDecoder {
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
