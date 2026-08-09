package app.inkloom.document

import kotlin.math.pow

object PressureCalibration {
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
        val position = input.coerceIn(0f, 1f) * (curve.size - 1)
        val left = position.toInt().coerceIn(0, curve.lastIndex)
        val right = (left + 1).coerceAtMost(curve.lastIndex)
        val fraction = position - left
        return curve[left] + (curve[right] - curve[left]) * fraction
    }

    fun encode(values: List<Float>): String = normalize(values).joinToString(",") { it.toString() }

    fun decode(raw: String?): List<Float> = normalize(raw.orEmpty().split(',').mapNotNull(String::toFloatOrNull))
}
