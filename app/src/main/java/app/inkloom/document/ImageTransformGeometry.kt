package app.inkloom.document

import kotlin.math.*

enum class ImageTransformMode { FREEFORM, UNIFORM }
enum class ImageTransformHandle(val fx: Float, val fy: Float) {
    TOP_LEFT(0f, 0f), TOP(.5f, 0f), TOP_RIGHT(1f, 0f), RIGHT(1f, .5f),
    BOTTOM_RIGHT(1f, 1f), BOTTOM(.5f, 1f), BOTTOM_LEFT(0f, 1f), LEFT(0f, .5f),
    ROTATE(.5f, 0f), MOVE(.5f, .5f),
}

/** Page-unit geometry, independent of viewport scale, origin, crop and Android input. */
object ImageTransformGeometry {
    fun point(image: PageElement.Image, pageWidth: Float, pageHeight: Float, fx: Float, fy: Float): Pair<Float, Float> {
        val local = rotate((fx - .5f) * image.width, (fy - .5f) * image.height, image.rotationDegrees)
        return image.x * pageWidth + image.width / 2f + local.first to
            image.y * pageHeight + image.height / 2f + local.second
    }

    fun drag(original: PageElement.Image, pageWidth: Float, pageHeight: Float,
             handle: ImageTransformHandle, startX: Float, startY: Float, x: Float, y: Float,
             mode: ImageTransformMode): PageElement.Image {
        require(pageWidth > 0f && pageHeight > 0f)
        if (handle == ImageTransformHandle.MOVE) return original.copy(
            x = original.x + (x - startX) / pageWidth,
            y = original.y + (y - startY) / pageHeight,
        )
        if (handle == ImageTransformHandle.ROTATE) {
            val center = point(original, pageWidth, pageHeight, .5f, .5f)
            val angle = atan2(y - center.second, x - center.first) - atan2(startY - center.second, startX - center.first)
            return original.copy(rotationDegrees = original.rotationDegrees + Math.toDegrees(angle.toDouble()).toFloat())
        }
        val dxdy = rotate(x - startX, y - startY, -original.rotationDegrees)
        val sx = (handle.fx - .5f) * 2f
        val sy = (handle.fy - .5f) * 2f
        var w = if (sx == 0f) original.width else (original.width + dxdy.first * sx).coerceAtLeast(1f)
        var h = if (sy == 0f) original.height else (original.height + dxdy.second * sy).coerceAtLeast(1f)
        if (mode == ImageTransformMode.UNIFORM) {
            val factor = when {
                sx == 0f -> h / original.height
                sy == 0f -> w / original.width
                else -> 1f + (dxdy.first * sx * original.width + dxdy.second * sy * original.height) /
                    (original.width * original.width + original.height * original.height)
            }.coerceAtLeast(1f / min(original.width, original.height).coerceAtLeast(1f))
            w = original.width * factor; h = original.height * factor
        }
        val anchor = point(original, pageWidth, pageHeight, 1f - handle.fx, 1f - handle.fy)
        val half = rotate(sx * w / 2f, sy * h / 2f, original.rotationDegrees)
        return original.copy(x = (anchor.first + half.first - w / 2f) / pageWidth,
            y = (anchor.second + half.second - h / 2f) / pageHeight, width = w, height = h)
    }

    private fun rotate(x: Float, y: Float, degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        return (x * cos(radians) - y * sin(radians)).toFloat() to
            (x * sin(radians) + y * cos(radians)).toFloat()
    }
}
