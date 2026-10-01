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
            val mapped = image.contentPoint((sourceX - crop.left) / cropWidth, (sourceY - crop.top) / cropHeight, page.width, page.height)
            return (mapped.x / page.width).coerceIn(0f, 1f) to (mapped.y / page.height).coerceIn(0f, 1f)
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
