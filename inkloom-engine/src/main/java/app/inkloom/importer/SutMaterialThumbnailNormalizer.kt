package app.inkloom.importer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * CSP's embedded material thumbnail is the only unencrypted raster fallback in
 * many SUT files. The thumbnail canvas can contain large transparent margins;
 * keeping those margins changes the brush-tip aspect ratio and stamp scale.
 */
object SutMaterialThumbnailNormalizer {
    data class Result(
        val png: ByteArray,
        val width: Int,
        val height: Int,
        val trimmed: Boolean,
    )

    fun normalize(png: ByteArray): Result {
        val source = BitmapFactory.decodeByteArray(png, 0, png.size)
            ?: return Result(png, 0, 0, false)
        try {
            var left = source.width
            var top = source.height
            var right = -1
            var bottom = -1
            val row = IntArray(source.width)
            for (y in 0 until source.height) {
                source.getPixels(row, 0, source.width, 0, y, source.width, 1)
                for (x in row.indices) {
                    if (row[x] ushr 24 == 0) continue
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            if (right < left || bottom < top) return Result(png, source.width, source.height, false)
            val width = right - left + 1
            val height = bottom - top + 1
            if (left == 0 && top == 0 && width == source.width && height == source.height) {
                return Result(png, source.width, source.height, false)
            }
            val cropped = Bitmap.createBitmap(source, left, top, width, height)
            return try {
                val output = ByteArrayOutputStream()
                check(cropped.compress(Bitmap.CompressFormat.PNG, 100, output))
                Result(output.toByteArray(), width, height, true)
            } finally {
                cropped.recycle()
            }
        } finally {
            source.recycle()
        }
    }
}
