package app.inkloom.document

/**
 * Numeric order stored by CSP's `DualBrushCompositeMode` column. The order is
 * the one exposed by CSP's documented Dual Brush mode selector.
 */
enum class CspDualBrushMode(val code: Int, val displayName: String) {
    NORMAL(1, "Normal"),
    MULTIPLY(2, "Multiply"),
    ADD_GLOW(3, "Add (Glow)"),
    SUBTRACT(4, "Subtract"),
    DARKEN(5, "Darken"),
    LIGHTEN(6, "Lighten"),
    SCREEN(7, "Screen"),
    OVERLAY(8, "Overlay"),
    COLOR_DODGE(9, "Color Dodge"),
    COLOR_BURN(10, "Color Burn"),
    LINEAR_BURN(11, "Linear Burn"),
    HARD_MIX(12, "Hard Mix"),
    HEIGHT_LINEAR(13, "Height (Linear)"),
    ;

    companion object {
        fun fromCode(code: Int): CspDualBrushMode? = if (code == 0) NORMAL else entries.firstOrNull { it.code == code }
        fun description(code: Int): String = fromCode(code)?.displayName ?: "Unknown ($code)"
    }
}
