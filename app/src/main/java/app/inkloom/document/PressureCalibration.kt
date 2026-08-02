package app.inkloom.document

import kotlin.math.pow

object PressureCalibration {
    fun apply(raw: Float, minimumInput: Float, maximumInput: Float, sensitivity: Float): Float {
        val minimum = minimumInput.coerceIn(0f, .95f)
        val maximum = maximumInput.coerceIn(minimum + .05f, 1f)
        val normalized = ((raw.coerceIn(0f, 1f) - minimum) / (maximum - minimum)).coerceIn(0f, 1f)
        return normalized.toDouble().pow(1.0 / sensitivity.coerceIn(.25f, 2.5f)).toFloat()
    }
}
