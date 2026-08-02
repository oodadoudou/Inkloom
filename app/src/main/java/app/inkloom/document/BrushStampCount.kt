package app.inkloom.document

import kotlin.math.ceil

object BrushStampCount {
    fun resolve(maximum: Int, jitter: Float, renderSeed: Int, plotIndex: Int, dual: Boolean = false): Int {
        val boundedMaximum = maximum.coerceIn(1, 16)
        val minimum = ceil(boundedMaximum * (1f - jitter.coerceIn(0f, 1f))).toInt().coerceIn(1, boundedMaximum)
        if (minimum == boundedMaximum) return boundedMaximum
        val salt = if (dual) DUAL_SEED else MAIN_SEED
        val random = java.util.Random(renderSeed.toLong() xor salt xor plotIndex.toLong() * PLOT_MULTIPLIER)
        return minimum + random.nextInt(boundedMaximum - minimum + 1)
    }

    private const val MAIN_SEED = 0x21C76A4DL
    private const val DUAL_SEED = 0x6B3D91E7L
    private const val PLOT_MULTIPLIER = 0x9E3779B9L
}
