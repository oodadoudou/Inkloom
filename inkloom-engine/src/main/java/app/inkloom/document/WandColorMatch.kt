package app.inkloom.document

/**
 * The magic wand with Contiguous off (Photoshop, CSP "Follow adjacent pixels" off): every pixel whose
 * channels, alpha included, are each within [tolerance] (0..255) of the touched colour. Uses the same
 * per-channel rule as the contiguous flood fill, so switching the option keeps the same tolerance.
 */
object WandColorMatch {
    private const val SELECTED = -1 // opaque white

    /** Close gap levels (off, small, medium, large) in page pixels. */
    fun closeGapPixels(level: Int): Float = when (level.coerceIn(0, 3)) { 0 -> 0f; 1 -> 4f; 2 -> 10f; else -> 20f }

    fun matches(color: Int, seed: Int, tolerance: Int): Boolean {
        var shift = 0
        while (shift < 32) {
            if (kotlin.math.abs((color ushr shift and 0xff) - (seed ushr shift and 0xff)) > tolerance) return false
            shift += 8
        }
        return true
    }

    /** A mask of the matching pixels: opaque white where selected, transparent elsewhere. */
    fun select(pixels: IntArray, seed: Int, tolerance: Int): IntArray =
        IntArray(pixels.size) { if (matches(pixels[it], seed, tolerance)) SELECTED else 0 }
}
