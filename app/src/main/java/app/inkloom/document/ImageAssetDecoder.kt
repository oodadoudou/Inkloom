package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object ImageAssetDecoder {
    fun decodeCropped(path: String, crop: ImageCrop): Bitmap? = runCatching {
        val source = BitmapFactory.decodeFile(path) ?: return null
        val bounds = crop.pixelBounds(source.width, source.height)
        if (bounds.x == 0 && bounds.y == 0 && bounds.width == source.width && bounds.height == source.height) return source
        Bitmap.createBitmap(source, bounds.x, bounds.y, bounds.width, bounds.height).also { cropped -> if (cropped !== source) source.recycle() }
    }.getOrNull()
}
