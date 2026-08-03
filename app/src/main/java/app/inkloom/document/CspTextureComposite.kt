package app.inkloom.document

import kotlin.math.min

/**
 * CSP Texture > Texture mode values observed in SUT Variant.TextureCompositeMode.
 *
 * Compare and Height are the two non-default modes present in Inkloom's current
 * real-device corpus. Height follows CSP's documented edge-only behavior. Compare
 * uses a conservative density comparison without claiming exact raster parity.
 * Unknown modes keep the legacy multiplication until independently verified.
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

    fun isVerified(mode: Int): Boolean = mode == HEIGHT

    /** Combines authored tip density with the normalized CSP texture density. */
    fun density(baseDensity: Float, textureDensity: Float, strength: Float, mode: Int): Float {
        val base = baseDensity.coerceIn(0f, 1f)
        val texture = textureDensity.coerceIn(0f, 1f)
        val amount = strength.coerceIn(0f, 1f)
        if (amount <= 0f || base <= 0f) return base
        val target = when (mode) {
            // Compare selects the lower density without multiplying two partially
            // transparent values, preserving more of an authored soft tip edge.
            COMPARE -> min(base, texture)
            // CSP documents Height as applying texture only at the stroke edge.
            // 4b(1-b) is zero at the transparent/solid plateaus and peaks on the
            // antialiased edge, so the opaque center remains authored-tip solid.
            HEIGHT -> {
                val edgeWeight = (4f * base * (1f - base)).coerceIn(0f, 1f)
                base * (1f - edgeWeight + edgeWeight * texture)
            }
            // Historical Inkloom behavior: texture modulates the entire tip.
            else -> base * texture
        }
        return (base + (target - base) * amount).coerceIn(0f, 1f)
    }
}
