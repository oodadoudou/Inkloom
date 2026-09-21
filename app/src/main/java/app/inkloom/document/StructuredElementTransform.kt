package app.inkloom.document

import app.inkloom.engine.v5.tools.SelectionAffineTransform
import kotlin.math.abs

/** Non-destructive affine group transform for editable text, images, shapes and notebook objects. */
object StructuredElementTransform {
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
