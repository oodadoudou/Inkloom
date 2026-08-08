package app.inkloom.document

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object RecognizedTextGeometry {
    fun mapImageBlock(page: InkPage, image: PageElement.Image, block: RecognizedTextBlock): RecognizedTextBlock? {
        val crop = image.crop.normalized()
        val source = block.bounds
        val visibleLeft = max(source.left, crop.left)
        val visibleTop = max(source.top, crop.top)
        val visibleRight = min(source.right, crop.right)
        val visibleBottom = min(source.bottom, crop.bottom)
        if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) return null

        val cropWidth = crop.right - crop.left
        val cropHeight = crop.bottom - crop.top
        fun point(sourceX: Float, sourceY: Float): Pair<Float, Float> {
            val localX = (sourceX - crop.left) / cropWidth * image.width / page.width
            val localY = (sourceY - crop.top) / cropHeight * image.height / page.height
            val centerX = image.x + image.width / page.width / 2f
            val centerY = image.y + image.height / page.height / 2f
            val x = image.x + localX
            val y = image.y + localY
            val radians = Math.toRadians(image.rotationDegrees.toDouble())
            val dx = x - centerX
            val dy = y - centerY
            return Pair(
                (centerX + dx * cos(radians).toFloat() - dy * sin(radians).toFloat()).coerceIn(0f, 1f),
                (centerY + dx * sin(radians).toFloat() + dy * cos(radians).toFloat()).coerceIn(0f, 1f),
            )
        }

        val corners = listOf(
            point(visibleLeft, visibleTop),
            point(visibleRight, visibleTop),
            point(visibleRight, visibleBottom),
            point(visibleLeft, visibleBottom),
        )
        return block.copy(
            bounds = PdfTextBounds(
                left = corners.minOf(Pair<Float, Float>::first),
                top = corners.minOf(Pair<Float, Float>::second),
                right = corners.maxOf(Pair<Float, Float>::first),
                bottom = corners.maxOf(Pair<Float, Float>::second),
            ),
        )
    }
}
