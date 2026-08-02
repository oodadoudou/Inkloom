package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlin.math.pow

/**
 * Builds the white alpha mask used for a brush stamp. Live drawing and exported
 * pages deliberately share this decoder so imported textures render identically.
 */
object BrushTipTextureDecoder {
    fun decode(
        tipPath: String,
        hardness: Float,
        grainPath: String?,
        grainDensity: Float,
        grainScale: Float = 100f,
        grainRotation: Float = 0f,
        grainBrightness: Float = 0f,
        grainContrast: Float = 0f,
        grainReversed: Boolean = false,
        grainEmphasized: Boolean = false,
    ): Bitmap? = runCatching {
        val source = BitmapFactory.decodeFile(tipPath) ?: return null
        val grain = grainPath?.takeIf { grainDensity > 0f }?.let(BitmapFactory::decodeFile)
        try {
            val grainPixels = grain?.let { bitmap ->
                IntArray(bitmap.width * bitmap.height).also {
                    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                }
            }
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            pixels.indices.forEach { index ->
                val pixel = pixels[index]
                val luminance = (Color.red(pixel) * .299f + Color.green(pixel) * .587f + Color.blue(pixel) * .114f).toInt()
                val base = (255 - luminance) / 255f
                val shaped = base.toDouble()
                    .pow((1.8f - hardness.coerceIn(0f, 1f) * 1.5f).toDouble())
                    .toFloat()
                val grainMask = if (grain != null && grainPixels != null) {
                    val x = index % source.width
                    val y = index / source.width
                    val coordinate = TextureSampling.coordinate(
                        x,
                        y,
                        source.width,
                        source.height,
                        grain.width,
                        grain.height,
                        grainScale,
                        grainRotation,
                    )
                    val sample = grainPixels[coordinate.y * grain.width + coordinate.x]
                    val grainLuminance = (Color.red(sample) * .299f + Color.green(sample) * .587f + Color.blue(sample) * .114f) / 255f
                    val effectiveLuminance = TextureSampling.effectiveLuminance(
                        grainLuminance,
                        Color.alpha(sample) / 255f,
                    )
                    TextureSampling.mask(
                        effectiveLuminance,
                        grainBrightness,
                        grainContrast,
                        grainReversed,
                        grainEmphasized,
                    )
                } else {
                    1f
                }
                val density = grainDensity.coerceIn(0f, 1f)
                val texture = (1f - density) + density * grainMask
                val alpha = ((shaped * texture * 255).toInt() * Color.alpha(pixel) / 255).coerceIn(0, 255)
                pixels[index] = Color.argb(alpha, 255, 255, 255)
            }
            Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        } finally {
            source.recycle()
            grain?.recycle()
        }
    }.getOrNull()
}
