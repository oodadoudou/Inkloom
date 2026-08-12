package app.inkloom.document

import kotlin.math.min
import kotlin.math.sqrt

/**
 * CSP Texture > Texture mode values observed in SUT Variant.TextureCompositeMode.
 *
 * CSP publishes the nine mode names and the qualitative edge behavior of Outline
 * and Height, but not its private raster equations. Inkloom applies the corresponding
 * standard scalar blend equation to normalized paint density; Normal uses the
 * neutral-midpoint soft-light transfer so a 50% texture does not change authored
 * density. Outline receives local texture-edge strength from the shared decoder.
 * Unknown source values retain historical Multiply behavior instead of silently
 * being relabeled as one of the documented modes.
 */
object CspTextureComposite {
    const val NORMAL = 1
    const val MULTIPLY = 2
    const val SUBTRACT = 3
    const val COMPARE = 4
    const val OUTLINE = 5
    const val OVERLAY = 6
    const val COLOR_DODGE = 7
    const val HARD_MIX = 8
    const val HEIGHT = 9

    fun displayName(mode: Int): String = when (mode) {
        NORMAL -> "Normal"
        MULTIPLY -> "Multiply"
        SUBTRACT -> "Subtract"
        COMPARE -> "Compare"
        OUTLINE -> "Outline"
        OVERLAY -> "Overlay"
        COLOR_DODGE -> "Color dodge"
        HARD_MIX -> "Hard mix"
        HEIGHT -> "Height"
        else -> "Unknown $mode"
    }

    fun isDocumented(mode: Int): Boolean = mode in NORMAL..HEIGHT

    /** Combines authored tip density with the normalized CSP texture density. */
    fun density(
        baseDensity: Float,
        textureDensity: Float,
        strength: Float,
        mode: Int,
        textureEdge: Float = 0f,
    ): Float {
        val base = baseDensity.coerceIn(0f, 1f)
        val texture = textureDensity.coerceIn(0f, 1f)
        val amount = strength.coerceIn(0f, 1f)
        if (amount <= 0f || base <= 0f) return base
        val target = when (mode) {
            NORMAL -> normal(base, texture)
            MULTIPLY -> base * texture
            SUBTRACT -> (base - texture).coerceAtLeast(0f)
            // The normalized scalar counterpart of Compare keeps the lower density.
            COMPARE -> min(base, texture)
            OUTLINE -> edgeOnly(base, textureEdge.coerceIn(0f, 1f))
            OVERLAY -> overlay(base, texture)
            COLOR_DODGE -> colorDodge(base, texture)
            HARD_MIX -> if (vividLight(base, texture) < .5f) 0f else 1f
            HEIGHT -> edgeOnly(base, texture)
            else -> base * texture
        }
        return (base + (target - base) * amount).coerceIn(0f, 1f)
    }

    /** Normal is intentionally less destructive than Multiply: transparent/opaque
     * plateaus stay fixed and a balanced grain preserves authored mid-density. */
    private fun normal(base: Float, texture: Float): Float = if (texture <= .5f) {
        base - (1f - 2f * texture) * base * (1f - base)
    } else {
        val lifted = if (base <= .25f) ((16f * base - 12f) * base + 4f) * base else sqrt(base)
        base + (2f * texture - 1f) * (lifted - base)
    }

    private fun edgeOnly(base: Float, texture: Float): Float {
        // Zero at the transparent and opaque plateaus, one at a 50% soft edge.
        val edgeWeight = (4f * base * (1f - base)).coerceIn(0f, 1f)
        return base * (1f - edgeWeight + edgeWeight * texture)
    }

    private fun overlay(base: Float, texture: Float): Float =
        if (base <= .5f) 2f * base * texture else 1f - 2f * (1f - base) * (1f - texture)

    private fun colorDodge(base: Float, texture: Float): Float = when {
        base <= 0f -> 0f
        texture >= 1f -> 1f
        else -> (base / (1f - texture)).coerceAtMost(1f)
    }

    private fun colorBurn(base: Float, texture: Float): Float = when {
        base >= 1f -> 1f
        texture <= 0f -> 0f
        else -> (1f - (1f - base) / texture).coerceAtLeast(0f)
    }

    private fun vividLight(base: Float, texture: Float): Float =
        if (texture < .5f) colorBurn(base, texture * 2f) else colorDodge(base, (texture - .5f) * 2f)
}
