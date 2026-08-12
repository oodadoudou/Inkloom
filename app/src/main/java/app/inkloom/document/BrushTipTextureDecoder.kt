package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

/**
 * Builds the white alpha mask used for a brush stamp. Live drawing and exported
 * pages deliberately share this decoder so imported textures render identically.
 */
object BrushTipTextureDecoder {
    /**
     * Imported brush archives commonly contain 2048-4096px source textures. A live stamp never
     * needs that many pixels on a tablet, while decoding several full-size tips can exhaust the
     * render heap and block the first stroke. Keep the authored aspect ratio and density while
     * placing a hard upper bound on each cached render asset.
     */
    internal const val MAX_RENDER_TEXTURE_DIMENSION = 512

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
        grainOffsetX: Float = 0f,
        grainOffsetY: Float = 0f,
        grainCompositeMode: Int = CspTextureComposite.MULTIPLY,
        grainFilteringMode: Int = ProcreateFiltering.NONE,
        antiAliasMode: Int = CspAntiAlias.MEDIUM,
        alphaThresholdEnabled: Boolean = false,
        alphaThreshold: Float = 0f,
    ): Bitmap? = runCatching {
        val source = decodeSampled(tipPath, MAX_RENDER_TEXTURE_DIMENSION) ?: return null
        val grain = grainPath?.takeIf { grainDensity > 0f }?.let {
            decodeSampled(it, MAX_RENDER_TEXTURE_DIMENSION)
        }
        try {
            val grainPixels = grain?.let { bitmap ->
                IntArray(bitmap.width * bitmap.height).also {
                    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                }
            }
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            val darkBackground = hasDarkBackground(pixels, source.width, source.height)
            pixels.indices.forEach { index ->
                val pixel = pixels[index]
                val luminance = (Color.red(pixel) * .299f + Color.green(pixel) * .587f + Color.blue(pixel) * .114f).toInt()
                // CSP/ABR commonly store black paint on white. Procreate also
                // permits the inverse convention, so infer it from the quiet
                // corners and normalize both sources to a white alpha mask.
                val base = if (darkBackground) luminance / 255f else (255 - luminance) / 255f
                // Imported bitmap tips already contain their final grayscale
                // density. CSP's Hardness control applies to circular tips, not
                // material tips, so applying another gamma curve here destroys
                // the authored edge and texture values.
                val shaped = CspAntiAlias.shapeCoverage(base, antiAliasMode)
                val grainMask = if (grain != null && grainPixels != null) {
                    val x = index % source.width
                    val y = index / source.width
                    val effectiveLuminance = TextureSampling.sampleEffectiveLuminance(
                        grainPixels,
                        grain.width,
                        grain.height,
                        x,
                        y,
                        source.width,
                        source.height,
                        grainScale,
                        grainRotation,
                        grainOffsetX,
                        grainOffsetY,
                        grainFilteringMode,
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
                val composedDensity = CspTextureComposite.density(shaped, grainMask, grainDensity, grainCompositeMode)
                val sourceAlpha = ((composedDensity * 255).toInt() * Color.alpha(pixel) / 255).coerceIn(0, 255)
                val alpha = if (alphaThresholdEnabled) {
                    if (sourceAlpha > alphaThreshold.coerceIn(0f, 1f) * 255f) 255 else 0
                } else sourceAlpha
                pixels[index] = Color.argb(alpha, 255, 255, 255)
            }
            Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        } finally {
            source.recycle()
            grain?.recycle()
        }
    }.getOrNull()

    fun decodeSampled(path: String, maximumDimension: Int): Bitmap? {
        if (maximumDimension <= 0) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maximumDimension) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        val decodedMaximum = maxOf(decoded.width, decoded.height)
        if (decodedMaximum <= maximumDimension) return decoded
        val scale = maximumDimension.toFloat() / decodedMaximum
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun hasDarkBackground(pixels: IntArray, width: Int, height: Int): Boolean {
        val sampleWidth = (width / 16).coerceIn(1, 64)
        val sampleHeight = (height / 16).coerceIn(1, 64)
        var luminance = 0L
        var samples = 0L
        fun sample(left: Int, top: Int) {
            for (y in top until (top + sampleHeight).coerceAtMost(height)) {
                for (x in left until (left + sampleWidth).coerceAtMost(width)) {
                    val pixel = pixels[y * width + x]
                    if (Color.alpha(pixel) < 16) continue
                    luminance += (Color.red(pixel) * 299L + Color.green(pixel) * 587L + Color.blue(pixel) * 114L) / 1000L
                    samples++
                }
            }
        }
        sample(0, 0)
        sample((width - sampleWidth).coerceAtLeast(0), 0)
        sample(0, (height - sampleHeight).coerceAtLeast(0))
        sample((width - sampleWidth).coerceAtLeast(0), (height - sampleHeight).coerceAtLeast(0))
        if (samples == 0L) return false
        val background = luminance.toFloat() / samples
        if (background >= 127.5f) return false
        val brightThreshold = (background + 32f).coerceAtMost(224f)
        val requiredBrightPixels = (pixels.size / 1_000).coerceAtLeast(1)
        var brightPixels = 0
        for (pixel in pixels) {
            if (Color.alpha(pixel) < 16) continue
            val value = (Color.red(pixel) * 299f + Color.green(pixel) * 587f + Color.blue(pixel) * 114f) / 1000f
            if (value > brightThreshold && ++brightPixels >= requiredBrightPixels) return true
        }
        return false
    }
}
