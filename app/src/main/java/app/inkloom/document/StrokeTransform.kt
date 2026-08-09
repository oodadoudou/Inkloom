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
    ): List<InkStroke> = transformNonUniform(strokes, centerX, centerY, scale, scale, rotationDegrees, translateX, translateY, pageWidth, pageHeight)

    fun transformNonUniform(
        strokes: List<InkStroke>,
        centerX: Float,
        centerY: Float,
        scaleX: Float,
        scaleY: Float,
        rotationDegrees: Float,
        translateX: Float,
        translateY: Float,
        pageWidth: Float = 1f,
        pageHeight: Float = 1f,
    ): List<InkStroke> {
        if (scaleX == 1f && scaleY == 1f && rotationDegrees == 0f && translateX == 0f && translateY == 0f) return strokes
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val averageScale = ((kotlin.math.abs(scaleX) + kotlin.math.abs(scaleY)) / 2f).coerceAtLeast(.01f)
        return strokes.map { stroke ->
            stroke.copy(
                points = stroke.points.map { it.copy(x = applyX(it.x, it.y, centerX, centerY, scaleX, scaleY, cos, sin, translateX, pageWidth, pageHeight), y = applyY(it.x, it.y, centerX, centerY, scaleX, scaleY, cos, sin, translateY, pageWidth, pageHeight)) },
                masks = stroke.masks.map { it.copy(
                    x = applyX(it.x, it.y, centerX, centerY, scaleX, scaleY, cos, sin, translateX, pageWidth, pageHeight),
                    y = applyY(it.x, it.y, centerX, centerY, scaleX, scaleY, cos, sin, translateY, pageWidth, pageHeight),
                    radiusX = (it.radiusX * kotlin.math.abs(scaleX)).coerceAtLeast(.0001f),
                    radiusY = (it.radiusY * kotlin.math.abs(scaleY)).coerceAtLeast(.0001f),
                    rotationDegrees = it.rotationDegrees + rotationDegrees,
                ) },
                width = (stroke.width * averageScale).coerceAtLeast(.1f),
            )
        }
    }

    private fun applyX(x: Float, y: Float, cx: Float, cy: Float, sx: Float, sy: Float, cos: Float, sin: Float, tx: Float, pageWidth: Float, pageHeight: Float): Float {
        val safeWidth = pageWidth.coerceAtLeast(.0001f)
        val dx = (x - cx) * safeWidth * sx
        val dy = (y - cy) * pageHeight.coerceAtLeast(.0001f) * sy
        return cx + (dx * cos - dy * sin) / safeWidth + tx
    }

    private fun applyY(x: Float, y: Float, cx: Float, cy: Float, sx: Float, sy: Float, cos: Float, sin: Float, ty: Float, pageWidth: Float, pageHeight: Float): Float {
        val safeHeight = pageHeight.coerceAtLeast(.0001f)
        val dx = (x - cx) * pageWidth.coerceAtLeast(.0001f) * sx
        val dy = (y - cy) * safeHeight * sy
        return cy + (dx * sin + dy * cos) / safeHeight + ty
    }
}
