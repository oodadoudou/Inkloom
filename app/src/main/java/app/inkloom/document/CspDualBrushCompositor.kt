package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * CSP Dual Brush combines two complete brush streams before the result is deposited on the page.
 * This compositor deliberately operates on straight RGBA samples instead of Android's layer
 * BlendMode: layer blend modes always use source-over alpha, while CSP's Apply RGB = off contract
 * applies the selected mode directly to the two brush-density channels.
 */
object CspDualBrushCompositor {
    private const val EPSILON = 1f / 65_535f

    private class Scratch {
        var primary = IntArray(0)
        var secondary = IntArray(0)

        fun ensure(size: Int) {
            if (primary.size < size) primary = IntArray(size)
            if (secondary.size < size) secondary = IntArray(size)
        }
    }

    private val scratch = ThreadLocal.withInitial(::Scratch)

    /** CSP's density-only equations used when Apply RGB value is disabled. */
    fun density(modeCode: Int, primary: Float, secondary: Float): Float {
        val base = primary.coerceIn(0f, 1f)
        val blend = secondary.coerceIn(0f, 1f)
        return when (CspDualBrushMode.fromCode(modeCode) ?: CspDualBrushMode.NORMAL) {
            CspDualBrushMode.NORMAL -> blend + base * (1f - blend)
            CspDualBrushMode.MULTIPLY -> base * blend
            CspDualBrushMode.ADD_GLOW -> min(1f, base + blend)
            CspDualBrushMode.SUBTRACT -> max(0f, base - blend)
            CspDualBrushMode.DARKEN -> min(base, blend)
            CspDualBrushMode.LIGHTEN -> max(base, blend)
            CspDualBrushMode.SCREEN -> 1f - (1f - base) * (1f - blend)
            CspDualBrushMode.OVERLAY -> if (base <= .5f) 2f * base * blend else 1f - 2f * (1f - base) * (1f - blend)
            CspDualBrushMode.COLOR_DODGE -> when {
                blend >= 1f - EPSILON -> if (base <= EPSILON) 0f else 1f
                else -> min(1f, base / (1f - blend))
            }
            CspDualBrushMode.COLOR_BURN -> when {
                base >= 1f - EPSILON -> 1f
                blend <= EPSILON -> 0f
                else -> 1f - min(1f, (1f - base) / blend)
            }
            CspDualBrushMode.LINEAR_BURN -> max(0f, base + blend - 1f)
            CspDualBrushMode.HARD_MIX -> if (base + blend >= 1f) 1f else 0f
            // CSP documents this as Subtract with a darker result. Multiplying the primary
            // density by the inverse secondary density is the linear-height equation: it is
            // always at least as dense as max(primary - secondary, 0).
            CspDualBrushMode.HEIGHT_LINEAR -> base * (1f - blend)
        }.coerceIn(0f, 1f)
    }

    /** Standard per-channel blend equation used when the secondary material's RGB is enabled. */
    fun blendChannel(modeCode: Int, primary: Float, secondary: Float): Float {
        val base = primary.coerceIn(0f, 1f)
        val blend = secondary.coerceIn(0f, 1f)
        return when (CspDualBrushMode.fromCode(modeCode) ?: CspDualBrushMode.NORMAL) {
            CspDualBrushMode.NORMAL -> blend
            CspDualBrushMode.MULTIPLY -> base * blend
            CspDualBrushMode.ADD_GLOW -> min(1f, base + blend)
            CspDualBrushMode.SUBTRACT -> max(0f, base - blend)
            CspDualBrushMode.DARKEN -> min(base, blend)
            CspDualBrushMode.LIGHTEN -> max(base, blend)
            CspDualBrushMode.SCREEN -> 1f - (1f - base) * (1f - blend)
            CspDualBrushMode.OVERLAY -> if (base <= .5f) 2f * base * blend else 1f - 2f * (1f - base) * (1f - blend)
            CspDualBrushMode.COLOR_DODGE -> when {
                blend >= 1f - EPSILON -> if (base <= EPSILON) 0f else 1f
                else -> min(1f, base / (1f - blend))
            }
            CspDualBrushMode.COLOR_BURN -> when {
                base >= 1f - EPSILON -> 1f
                blend <= EPSILON -> 0f
                else -> 1f - min(1f, (1f - base) / blend)
            }
            CspDualBrushMode.LINEAR_BURN -> max(0f, base + blend - 1f)
            CspDualBrushMode.HARD_MIX -> if (base + blend >= 1f) 1f else 0f
            CspDualBrushMode.HEIGHT_LINEAR -> base * (1f - blend)
        }.coerceIn(0f, 1f)
    }

    /**
     * Recomputes [region] in [output] from the authoritative primary and secondary stream
     * surfaces. All bitmaps must share dimensions. Pixels outside [region] remain untouched.
     */
    fun compositeRegion(
        primary: Bitmap,
        secondary: Bitmap,
        output: Bitmap,
        region: Rect,
        modeCode: Int,
        applyRgb: Boolean,
    ) {
        require(primary.width == secondary.width && primary.height == secondary.height)
        require(output.width == primary.width && output.height == primary.height)
        val clipped = Rect(
            region.left.coerceIn(0, primary.width),
            region.top.coerceIn(0, primary.height),
            region.right.coerceIn(0, primary.width),
            region.bottom.coerceIn(0, primary.height),
        )
        if (clipped.isEmpty) return
        val count = clipped.width() * clipped.height()
        val buffers = checkNotNull(scratch.get()).also { it.ensure(count) }
        primary.getPixels(buffers.primary, 0, clipped.width(), clipped.left, clipped.top, clipped.width(), clipped.height())
        secondary.getPixels(buffers.secondary, 0, clipped.width(), clipped.left, clipped.top, clipped.width(), clipped.height())
        for (index in 0 until count) {
            buffers.primary[index] = if (applyRgb) {
                compositeRgb(buffers.primary[index], buffers.secondary[index], modeCode)
            } else {
                compositeDensity(buffers.primary[index], buffers.secondary[index], modeCode)
            }
        }
        output.setPixels(buffers.primary, 0, clipped.width(), clipped.left, clipped.top, clipped.width(), clipped.height())
    }

    private fun compositeDensity(primary: Int, secondary: Int, modeCode: Int): Int {
        val primaryAlpha = Color.alpha(primary) / 255f
        val secondaryAlpha = Color.alpha(secondary) / 255f
        val outputAlpha = density(modeCode, primaryAlpha, secondaryAlpha)
        if (outputAlpha <= EPSILON) return Color.TRANSPARENT
        val color = if (primaryAlpha > EPSILON) primary else secondary
        return Color.argb(
            (outputAlpha * 255f + .5f).toInt().coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color),
        )
    }

    private fun compositeRgb(primary: Int, secondary: Int, modeCode: Int): Int {
        val baseAlpha = Color.alpha(primary) / 255f
        val sourceAlpha = Color.alpha(secondary) / 255f
        val outputAlpha = sourceAlpha + baseAlpha * (1f - sourceAlpha)
        if (outputAlpha <= EPSILON) return Color.TRANSPARENT

        fun channel(extract: (Int) -> Int): Int {
            val base = extract(primary) / 255f
            val source = extract(secondary) / 255f
            val blended = blendChannel(modeCode, base, source)
            val premultiplied =
                (1f - sourceAlpha) * base * baseAlpha +
                    (1f - baseAlpha) * source * sourceAlpha +
                    baseAlpha * sourceAlpha * blended
            return (premultiplied / outputAlpha * 255f + .5f).toInt().coerceIn(0, 255)
        }

        return Color.argb(
            (outputAlpha * 255f + .5f).toInt().coerceIn(0, 255),
            channel(Color::red),
            channel(Color::green),
            channel(Color::blue),
        )
    }
}
