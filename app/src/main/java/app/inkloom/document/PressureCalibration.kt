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
    // Alternating input/output coordinates. Endpoints are anchored at (0,0) and (1,1).
    val LINEAR = listOf(0f, 0f, 1f, 1f)
    val SOFT = listOf(0f, 0f, .25f, .42f, .5f, .68f, .75f, .86f, 1f, 1f)
    val FIRM = listOf(0f, 0f, .25f, .08f, .5f, .25f, .75f, .55f, 1f, 1f)

    fun normalize(values: List<Float>): List<Float> {
        // Preserve the user's existing five fixed-axis handles when opening the new editor.
        if (values.size == 5) {
            var previous = 0f
            return values.flatMapIndexed { i, value ->
                val y = when (i) { 0 -> 0f; 4 -> 1f; else -> (value.takeIf(Float::isFinite) ?: i / 4f).coerceIn(previous, 1f) }
                previous = y
                listOf(i / 4f, y)
            }
        }
        if (values.size !in 4..16 || values.size % 2 != 0 || values.any { !it.isFinite() }) return LINEAR
        if (values[0] != 0f || values[1] != 0f || values[values.size - 2] != 1f || values.last() != 1f) return LINEAR
        for (i in 2 until values.size step 2) {
            if (values[i] - values[i - 2] < .001f || values[i] > 1f ||
                values[i + 1] < values[i - 1] || values[i + 1] > 1f) return LINEAR
        }
        return values
    }

    fun apply(input: Float, values: List<Float>): Float {
        val curve = normalize(values)
        val x = (input.takeIf(Float::isFinite) ?: 0f).coerceIn(0f, 1f)
        val count = curve.size / 2
        var left = 0
        while (left < count - 2 && x > curve[(left + 1) * 2]) left++
        fun width(i: Int) = curve[(i + 1) * 2] - curve[i * 2]
        fun slope(i: Int) = (curve[(i + 1) * 2 + 1] - curve[i * 2 + 1]) / width(i)
        fun tangent(i: Int): Float {
            if (i == 0) return slope(0)
            if (i == count - 1) return slope(count - 2)
            val before = slope(i - 1); val after = slope(i)
            if (before <= 0f || after <= 0f) return 0f
            val a = 2f * width(i) + width(i - 1)
            val b = width(i) + 2f * width(i - 1)
            return (a + b) / (a / before + b / after)
        }
        val h = width(left)
        val t = (x - curve[left * 2]) / h
        val t2 = t * t; val t3 = t2 * t
        val y0 = curve[left * 2 + 1]; val y1 = curve[(left + 1) * 2 + 1]
        return ((2f * t3 - 3f * t2 + 1f) * y0 + (t3 - 2f * t2 + t) * h * tangent(left) +
            (-2f * t3 + 3f * t2) * y1 + (t3 - t2) * h * tangent(left + 1)).coerceIn(y0, y1)
    }

    fun encode(values: List<Float>): String = normalize(values).joinToString(",")
    fun decode(raw: String?): List<Float> = normalize(raw.orEmpty().split(',').mapNotNull(String::toFloatOrNull))
}
