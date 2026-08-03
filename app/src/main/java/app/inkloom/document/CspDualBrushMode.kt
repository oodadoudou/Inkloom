package app.inkloom.document

/**
 * Numeric order stored by CSP's `DualBrushCompositeMode` column. The order is
 * the one exposed by CSP's documented Dual Brush mode selector.
 */
enum class CspDualBrushMode(val code: Int, val displayName: String, val nativeBlendSupported: Boolean) {
    NORMAL(1, "Normal", true),
    MULTIPLY(2, "Multiply", true),
    ADD_GLOW(3, "Add (Glow)", true),
    SUBTRACT(4, "Subtract", false),
    DARKEN(5, "Darken", true),
    LIGHTEN(6, "Lighten", true),
    SCREEN(7, "Screen", true),
    OVERLAY(8, "Overlay", true),
    COLOR_DODGE(9, "Color Dodge", true),
    COLOR_BURN(10, "Color Burn", true),
    LINEAR_BURN(11, "Linear Burn", false),
    HARD_MIX(12, "Hard Mix", false),
    HEIGHT_LINEAR(13, "Height (Linear)", false),
    ;

    companion object {
        fun fromCode(code: Int): CspDualBrushMode? = if (code == 0) NORMAL else entries.firstOrNull { it.code == code }
        fun description(code: Int): String = fromCode(code)?.displayName ?: "Unknown ($code)"
    }
}
