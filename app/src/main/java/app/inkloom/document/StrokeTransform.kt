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
        pageWidth: Float = 1f,
        pageHeight: Float = 1f,
    ): List<InkStroke> {
        if (scale == 1f && rotationDegrees == 0f && translateX == 0f && translateY == 0f) return strokes
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        return strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { it.copy(x = applyX(it.x, it.y, centerX, centerY, scale, cos, sin, translateX, pageWidth, pageHeight), y = applyY(it.x, it.y, centerX, centerY, scale, cos, sin, translateY, pageWidth, pageHeight)) },
                masks = stroke.masks.map { it.copy(
                    x = applyX(it.x, it.y, centerX, centerY, scale, cos, sin, translateX, pageWidth, pageHeight),
                    y = applyY(it.x, it.y, centerX, centerY, scale, cos, sin, translateY, pageWidth, pageHeight),
                    radiusX = (it.radiusX * scale).coerceAtLeast(.0001f),
                    radiusY = (it.radiusY * scale).coerceAtLeast(.0001f),
                    rotationDegrees = it.rotationDegrees + rotationDegrees,
                ) },
                width = (stroke.width * scale).coerceAtLeast(.1f),
            )
        }
    }

    private fun applyX(x: Float, y: Float, cx: Float, cy: Float, s: Float, cos: Float, sin: Float, tx: Float, pageWidth: Float, pageHeight: Float): Float {
        val safeWidth = pageWidth.coerceAtLeast(.0001f)
        val dx = (x - cx) * safeWidth * s
        val dy = (y - cy) * pageHeight.coerceAtLeast(.0001f) * s
        return cx + (dx * cos - dy * sin) / safeWidth + tx
    }

    private fun applyY(x: Float, y: Float, cx: Float, cy: Float, s: Float, cos: Float, sin: Float, ty: Float, pageWidth: Float, pageHeight: Float): Float {
        val safeHeight = pageHeight.coerceAtLeast(.0001f)
        val dx = (x - cx) * pageWidth.coerceAtLeast(.0001f) * s
        val dy = (y - cy) * safeHeight * s
        return cy + (dx * sin + dy * cos) / safeHeight + ty
    }
}
