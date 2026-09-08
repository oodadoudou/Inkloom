package app.inkloom.document

import kotlin.math.pow

/**
 * How the very first DOWN pressure is treated. Zero-pressure contacts are common on some styli and
 * were previously stacked with the velocity/tilt first-sample extremes. The final default is
 * device-calibrated; the live pipeline currently uses [InitialPressurePolicy.USE_REPORTED], and
 * this policy surface keeps every option explicit and testable without re-tuning the pipeline.
 */
enum class InitialPressurePolicy {
    /** Pass the raw DOWN pressure through normal calibration unchanged (current default). */
    USE_REPORTED,

    /** Hold the DOWN sample at the first non-zero pressure reported later in the stroke. */
    WAIT_FOR_FIRST_NONZERO,

    /** Bootstrap the DOWN sample from the first MOVE pressure. */
    BOOTSTRAP_FROM_FIRST_VALID_MOVE,

    /** Floor zero-pressure contacts at the calibrated contact minimum. */
    CALIBRATED_CONTACT_MINIMUM,
}

object PressureCalibration {
    /** Calibrated floor for zero-pressure contacts under [InitialPressurePolicy.CALIBRATED_CONTACT_MINIMUM]. */
    const val CALIBRATED_CONTACT_MINIMUM_PRESSURE = .08f

    /** Resolves the effective DOWN pressure for a policy. [establishedPressure] is the first valid
     * non-zero pressure seen on the stroke (from the first MOVE when bootstrapping). */
    fun resolveInitialPressure(rawDownPressure: Float, policy: InitialPressurePolicy, establishedPressure: Float): Float = when (policy) {
        InitialPressurePolicy.USE_REPORTED -> rawDownPressure
        InitialPressurePolicy.WAIT_FOR_FIRST_NONZERO -> if (rawDownPressure > 0f) rawDownPressure else establishedPressure
        InitialPressurePolicy.BOOTSTRAP_FROM_FIRST_VALID_MOVE -> establishedPressure
        InitialPressurePolicy.CALIBRATED_CONTACT_MINIMUM ->
            if (rawDownPressure > 0f) rawDownPressure else establishedPressure.coerceAtLeast(CALIBRATED_CONTACT_MINIMUM_PRESSURE)
    }

    fun apply(
        raw: Float,
        minimumInput: Float,
        maximumInput: Float,
        sensitivity: Float,
        responseCurve: List<Float> = PressureResponseCurve.LINEAR,
    ): Float {
        val minimum = minimumInput.coerceIn(0f, .95f)
        val maximum = maximumInput.coerceIn(minimum + .05f, 1f)
        val normalized = ((raw.coerceIn(0f, 1f) - minimum) / (maximum - minimum)).coerceIn(0f, 1f)
        val sensitivityAdjusted = normalized.toDouble().pow(1.0 / sensitivity.coerceIn(.25f, 2.5f)).toFloat()
        return PressureResponseCurve.apply(sensitivityAdjusted, responseCurve)
    }
}

object PressureResponseCurve {
    val LINEAR = listOf(0f, .25f, .5f, .75f, 1f)
    val SOFT = listOf(0f, .42f, .68f, .86f, 1f)
    val FIRM = listOf(0f, .08f, .25f, .55f, 1f)

    fun normalize(values: List<Float>): List<Float> {
        if (values.size != LINEAR.size) return LINEAR
        val result = values.mapIndexed { index, value ->
            when (index) {
                0 -> 0f
                LINEAR.lastIndex -> 1f
                else -> value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: LINEAR[index]
            }
        }.toMutableList()
        for (index in 1 until result.size) result[index] = result[index].coerceAtLeast(result[index - 1])
        return result
    }

    fun apply(input: Float, values: List<Float>): Float {
        val curve = normalize(values)
        val position = (input.takeIf(Float::isFinite) ?: 0f).coerceIn(0f, 1f) * curve.lastIndex
        val left = position.toInt().coerceIn(0, curve.lastIndex - 1)
        val t = position - left
        // Monotone cubic Hermite: shared tangents are continuous at each control point.
        // The harmonic mean prevents overshoot across a flat or steep pressure segment.
        fun tangent(index: Int): Float {
            if (index == 0) return curve[1] - curve[0]
            if (index == curve.lastIndex) return curve[index] - curve[index - 1]
            val before = curve[index] - curve[index - 1]
            val after = curve[index + 1] - curve[index]
            return if (before <= 0f || after <= 0f) 0f else 2f * before * after / (before + after)
        }
        val t2 = t * t
        val t3 = t2 * t
        return ((2f * t3 - 3f * t2 + 1f) * curve[left] +
            (t3 - 2f * t2 + t) * tangent(left) +
            (-2f * t3 + 3f * t2) * curve[left + 1] +
            (t3 - t2) * tangent(left + 1)).coerceIn(curve[left], curve[left + 1])
    }

    fun encode(values: List<Float>): String = normalize(values).joinToString(",") { it.toString() }

    fun decode(raw: String?): List<Float> = normalize(raw.orEmpty().split(',').mapNotNull(String::toFloatOrNull))
}
