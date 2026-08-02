package app.inkloom.document

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class SprayOffset(val x: Float, val y: Float)

object SprayGeometry {
    fun particleDiameter(brushDiameter: Float, size: Float, unit: Int, syncBrushSize: Boolean, referenceWidth: Float): Float =
        if (syncBrushSize) brushDiameter * (size / 100f).coerceIn(.01f, 8f)
        else WaterEdge.distancePixels(size, unit, referenceWidth).coerceAtLeast(.25f)

    fun particlesPerPlot(density: Float): Int = ceil(density.coerceIn(1f, 100f) / 20f).toInt().coerceIn(1, 5)

    /** Bias follows CSP's documented direction: positive values concentrate toward center. */
    fun offset(radius: Float, angleUnit: Float, distanceUnit: Float, bias: Float): SprayOffset {
        val normalizedBias = bias.coerceIn(-100f, 100f) / 100f
        val exponent = when {
            normalizedBias >= 0f -> .5f + normalizedBias * 3.5f
            else -> .5f / (1f + -normalizedBias * 3f)
        }
        val distance = distanceUnit.coerceIn(0f, 1f).pow(exponent) * radius.coerceAtLeast(0f)
        val angle = angleUnit.coerceIn(0f, 1f) * (Math.PI * 2.0)
        return SprayOffset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
    }
}
