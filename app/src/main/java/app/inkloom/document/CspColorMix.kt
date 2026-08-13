package app.inkloom.document

/** CSP Ink > Color mixing. Inkloom persists this explicitly; legacy SUT files do not. */
object CspColorMixMode {
    const val BLEND = 0
    const val RUNNING_COLOR = 1
    const val SMEAR = 2

    fun normalize(value: Int): Int = value.coerceIn(BLEND, SMEAR)

    /**
     * CSP's currently exported SUT schema stores three enable flags plus the active parameter
     * family, not a Blend/Running color/Smear enum. Running color uniquely exposes blur. Smear
     * omits density of paint; old Smear presets therefore persist stretch with zero density.
     */
    fun inferFromSut(
        enabled: Boolean,
        amountOfPaint: Float,
        densityOfPaint: Float,
        colorStretch: Float,
        blur: Float,
        blurAutomatic: Boolean,
    ): Int? = when {
        !enabled -> null
        blurAutomatic || blur > .001f -> RUNNING_COLOR
        colorStretch > .001f && densityOfPaint <= .001f -> SMEAR
        else -> BLEND
    }
}

object CspPigmentMixingMode {
    const val STANDARD = 0
    const val PERCEPTUAL = 1

    fun normalize(value: Int): Int = if (value == PERCEPTUAL) PERCEPTUAL else STANDARD
}

object CspBrightnessCorrection {
    /** SUT stores 0 when unavailable and five perceptual levels as 1..5 (3 is neutral). */
    const val NEUTRAL = 3

    fun normalize(value: Int): Int = value.takeIf { it in 1..5 } ?: NEUTRAL
}
