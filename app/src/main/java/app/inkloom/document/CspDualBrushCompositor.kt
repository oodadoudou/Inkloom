package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.Rect
import app.inkloom.engine.v3.tools.NativeRasterOps
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
        check(NativeRasterOps.compositeDualBrush(primary, secondary, output, clipped, modeCode, applyRgb)) {
            "Native dual-brush compositor rejected compatible RGBA surfaces"
        }
    }
}
