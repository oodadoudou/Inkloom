package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import java.io.File
import kotlin.math.roundToInt

/** Bounded cover previews, including the source image's orientation and color profile. */
internal object CoverImageDecoder {
    fun decode(file: File, longestSide: Int = 512): Bitmap {
        require(longestSide in 32..1024)
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val scale = minOf(1f, longestSide.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }
    }
}
