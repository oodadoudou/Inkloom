package app.inkloom.document

import app.inkloom.engine.v5.tools.SelectionAffineTransform
import app.inkloom.engine.v5.tools.SelectionRasterTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Non-destructive affine group transform for editable text, images, shapes and notebook objects. */
object StructuredElementTransform {
    fun map(element: PageElement, transform: SelectionRasterTransform, rasterWidth: Int,
            rasterHeight: Int, pageWidth: Float, pageHeight: Float): PageElement {
        require(rasterWidth > 0 && rasterHeight > 0 && pageWidth > 0 && pageHeight > 0)
        if (transform is SelectionAffineTransform && element.deformation == null &&
            transform.scaleX > 0 && transform.scaleY > 0 &&
            (element.rotationDegrees == 0f || abs(transform.scaleX - transform.scaleY) < .0001f)) {
            return affine(element, transform, rasterWidth, rasterHeight, pageWidth, pageHeight)
        }
        val angle = Math.toRadians(-element.rotationDegrees.toDouble())
        val c = cos(angle).toFloat(); val s = sin(angle).toFloat()
        val centerX = element.x * pageWidth + element.width / 2
        val centerY = element.y * pageHeight + element.height / 2
        val cells = 32
        val vertices = (0..cells).flatMap { row -> (0..cells).map { column ->
            val source = element.contentPoint(column / cells.toFloat(), row / cells.toFloat(), pageWidth, pageHeight)
            val mapped = transform.forward(source.x * rasterWidth / pageWidth, source.y * rasterHeight / pageHeight)
            val dx = mapped.first * pageWidth / rasterWidth - centerX
            val dy = mapped.second * pageHeight / rasterHeight - centerY
            ShapeVertex(.5f + (dx * c - dy * s) / element.width,
                .5f + (dx * s + dy * c) / element.height)
        } }
        return element.withDeformation(ElementDeformation(cells + 1, cells + 1, vertices))
    }

    fun affine(element: PageElement, transform: SelectionAffineTransform, rasterWidth: Int,
               rasterHeight: Int, pageWidth: Float, pageHeight: Float): PageElement {
        require(rasterWidth > 0 && rasterHeight > 0 && pageWidth > 0f && pageHeight > 0f)
        val centerX = (element.x * pageWidth + element.width / 2f) * rasterWidth / pageWidth
        val centerY = (element.y * pageHeight + element.height / 2f) * rasterHeight / pageHeight
        val mapped = transform.forward(centerX, centerY)
        val width = (element.width * abs(transform.scaleX)).coerceAtLeast(1f)
        val height = (element.height * abs(transform.scaleY)).coerceAtLeast(1f)
        val x = (mapped.first / rasterWidth * pageWidth - width / 2f) / pageWidth
        val y = (mapped.second / rasterHeight * pageHeight - height / 2f) / pageHeight
        val rotation = (element.rotationDegrees + transform.rotationDegrees).mod(360f)
        return element.withGeometry(x, y, width, height, rotation)
    }

    private fun PageElement.withGeometry(x: Float, y: Float, width: Float, height: Float, rotation: Float): PageElement = when (this) {
        is PageElement.Text -> copy(x = x, y = y, width = width, height = height, rotationDegrees = rotation)
        is PageElement.Sticky -> copy(x = x, y = y, width = width, height = height, rotationDegrees = rotation)
        is PageElement.Shape -> copy(x = x, y = y, width = width, height = height, rotationDegrees = rotation)
        is PageElement.Image -> copy(x = x, y = y, width = width, height = height, rotationDegrees = rotation)
        is PageElement.Fill -> copy(x = x, y = y, width = width, height = height, rotationDegrees = rotation)
        is PageElement.Link -> copy(x = x, y = y, width = width, height = height, rotationDegrees = rotation)
    }
}
