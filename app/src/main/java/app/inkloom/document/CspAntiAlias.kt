package app.inkloom.document

import kotlin.math.sqrt

/** Deterministic interpretation of CSP's four AntiAlias modes. */
object CspAntiAlias {
    const val NONE = 0
    const val WEAK = 1
    const val MEDIUM = 2
    const val STRONG = 3

    fun normalize(mode: Int): Int = mode.coerceIn(NONE, STRONG)

    fun enabled(mode: Int): Boolean = normalize(mode) != NONE

    /**
     * Adjusts shape coverage before grain composition. This preserves internal grain density while
     * progressively retaining more partially covered edge pixels from Weak through Strong.
     */
    fun shapeCoverage(value: Float, mode: Int): Float {
        val coverage = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        return when (normalize(mode)) {
            NONE -> if (coverage >= .5f) 1f else 0f
            WEAK -> coverage * coverage
            STRONG -> sqrt(coverage)
            else -> coverage
        }
    }
}
