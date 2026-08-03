package app.inkloom.document

/** CSP Brush tip > Flip horizontal/vertical selector values. */
object BrushTipFlip {
    const val OFF = 0
    const val FLIP = 1
    const val RANDOM = 2
    const val ON_REVERSED_SEQUENCE = 3

    data class ScaleSigns(val x: Float, val y: Float)

    fun signs(
        horizontalMode: Int,
        verticalMode: Int,
        sequenceMode: Int,
        stampIndex: Int,
        tipCount: Int,
        seed: Int,
        dual: Boolean = false,
    ): ScaleSigns = ScaleSigns(
        x = sign(horizontalMode, sequenceMode, stampIndex, tipCount, seed, if (dual) DUAL_X_SALT else MAIN_X_SALT),
        y = sign(verticalMode, sequenceMode, stampIndex, tipCount, seed, if (dual) DUAL_Y_SALT else MAIN_Y_SALT),
    )

    private fun sign(mode: Int, sequenceMode: Int, stampIndex: Int, tipCount: Int, seed: Int, salt: Int): Float = when (mode) {
        FLIP -> -1f
        RANDOM -> if (deterministicBit(seed, stampIndex, salt)) -1f else 1f
        ON_REVERSED_SEQUENCE -> if (BrushTipSequence.isReversed(sequenceMode, stampIndex, tipCount)) -1f else 1f
        else -> 1f
    }

    private fun deterministicBit(seed: Int, stampIndex: Int, salt: Int): Boolean {
        var value = seed.toLong() xor (stampIndex.toLong() * 0x9E3779B9L) xor (salt.toLong() shl 32)
        value = value xor (value ushr 33)
        value *= -49064778989728563L
        value = value xor (value ushr 33)
        return value and 1L != 0L
    }

    private const val MAIN_X_SALT = 0x46584D31
    private const val MAIN_Y_SALT = 0x46594D31
    private const val DUAL_X_SALT = 0x46584432
    private const val DUAL_Y_SALT = 0x46594432
}
