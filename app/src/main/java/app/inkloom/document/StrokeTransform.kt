package app.inkloom.document

import kotlin.math.cos
import kotlin.math.sin

/** Affine transform (uniform scale + rotation about a centre + translation)
 *  applied to ink strokes. Used by the transform tool to move/scale/rotate a
 *  selection or a layer. Coordinates are normalised page space (0..1). */
object StrokeTransform {
    fun transform(
        strokes: List<InkStroke>,
        centerX: Float,
        centerY: Float,
        scale: Float,
        rotationDegrees: Float,
        translateX: Float,
        translateY: Float,
    ): List<InkStroke> {
        if (scale == 1f && rotationDegrees == 0f && translateX == 0f && translateY == 0f) return strokes
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        return strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { it.copy(x = applyX(it.x, it.y, centerX, centerY, scale, cos, sin, translateX, translateY), y = applyY(it.x, it.y, centerX, centerY, scale, cos, sin, translateX, translateY)) },
                masks = stroke.masks.map { it.copy(
                    x = applyX(it.x, it.y, centerX, centerY, scale, cos, sin, translateX, translateY),
                    y = applyY(it.x, it.y, centerX, centerY, scale, cos, sin, translateX, translateY),
                    radiusX = (it.radiusX * scale).coerceAtLeast(.0001f),
                    radiusY = (it.radiusY * scale).coerceAtLeast(.0001f),
                    rotationDegrees = it.rotationDegrees + rotationDegrees,
                ) },
                width = (stroke.width * scale).coerceAtLeast(.1f),
            )
        }
    }

    private fun applyX(x: Float, y: Float, cx: Float, cy: Float, s: Float, cos: Float, sin: Float, tx: Float, ty: Float): Float {
        val dx = (x - cx) * s
        val dy = (y - cy) * s
        return (dx * cos - dy * sin + cx + tx).coerceIn(0f, 1f)
    }

    private fun applyY(x: Float, y: Float, cx: Float, cy: Float, s: Float, cos: Float, sin: Float, tx: Float, ty: Float): Float {
        val dx = (x - cx) * s
        val dy = (y - cy) * s
        return (dx * sin + dy * cos + cy + ty).coerceIn(0f, 1f)
    }
}
