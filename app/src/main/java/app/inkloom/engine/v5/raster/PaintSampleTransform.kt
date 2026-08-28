package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.stroke.StrokeSample
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Immutable document-pixel affine used to create independent paint streams inside one V5
 * transaction. The point and authored brush orientation are transformed together; pressure and
 * tilt magnitude remain properties of the physical contact.
 */
data class PaintSampleTransform(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val tx: Float,
    val ty: Float,
) {
    fun apply(sample: StrokeSample): StrokeSample {
        val directionX = cos(sample.orientationRadians)
        val directionY = sin(sample.orientationRadians)
        return sample.copy(
            point = DocumentPoint(
                a * sample.point.x + c * sample.point.y + tx,
                b * sample.point.x + d * sample.point.y + ty,
            ),
            orientationRadians = atan2(
                b * directionX + d * directionY,
                a * directionX + c * directionY,
            ),
        )
    }

    companion object {
        val Identity = PaintSampleTransform(1f, 0f, 0f, 1f, 0f, 0f)

        fun horizontalAxis(height: Float) = PaintSampleTransform(1f, 0f, 0f, -1f, 0f, height)
        fun verticalAxis(width: Float) = PaintSampleTransform(-1f, 0f, 0f, 1f, width, 0f)
        fun bothAxes(width: Float, height: Float) = PaintSampleTransform(-1f, 0f, 0f, -1f, width, height)

        fun rotation(angleRadians: Float, centerX: Float, centerY: Float): PaintSampleTransform {
            val cosine = cos(angleRadians)
            val sine = sin(angleRadians)
            return PaintSampleTransform(
                a = cosine,
                b = sine,
                c = -sine,
                d = cosine,
                tx = centerX - cosine * centerX + sine * centerY,
                ty = centerY - sine * centerX - cosine * centerY,
            )
        }
    }
}
