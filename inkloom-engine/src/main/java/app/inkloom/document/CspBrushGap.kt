package app.inkloom.document

/** CSP Stroke > Gap modes. The integer order follows the SUT Variant schema. */
object CspBrushGap {
    const val FIXED = 0
    const val WIDE = 1
    const val NORMAL = 2
    const val NARROW = 3

    fun normalize(mode: Int): Int = mode.coerceIn(FIXED, NARROW)

    fun ratio(mode: Int, fixedRatio: Float): Float = when (normalize(mode)) {
        WIDE -> .30f
        NORMAL -> .15f
        NARROW -> .05f
        else -> fixedRatio.coerceIn(.01f, 2f)
    }

    fun displayName(mode: Int): String = when (normalize(mode)) {
        WIDE -> "Wide"
        NORMAL -> "Normal"
        NARROW -> "Narrow"
        else -> "Fixed"
    }
}
