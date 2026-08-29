package app.inkloom.document

object PageGeometry {
    fun rotateClockwise(page: InkPage): InkPage {
        val oldWidth = page.width.coerceAtLeast(1f)
        val oldHeight = page.height.coerceAtLeast(1f)
        return page.copy(
            width = oldHeight,
            height = oldWidth,
            backgroundRotationQuarterTurns = (page.backgroundRotationQuarterTurns + 1).mod(4),
            crop = PageCropTransform.rotateClockwise(page.crop),
            layers = page.layers.map { layer ->
                layer.copy(
                    rasterRotationQuarterTurns = if (layer.isPaintSurfaceLayer()) {
                        (layer.rasterRotationQuarterTurns + 1).mod(4)
                    } else layer.rasterRotationQuarterTurns,
                )
            },
            elements = page.elements.map { rotateElementClockwise(it, oldWidth, oldHeight) },
            pdfTextBlocks = page.pdfTextBlocks.map { block -> block.copy(bounds = block.bounds.map(::rotateTextBoundsClockwise)) },
            pdfGotoLinks = page.pdfGotoLinks.map { link -> link.copy(bounds = link.bounds.map(::rotateTextBoundsClockwise)) },
        )
    }

    fun rotateTextBoundsClockwise(bounds: PdfTextBounds) = PdfTextBounds(
        left = 1f - bounds.bottom,
        top = bounds.left,
        right = 1f - bounds.top,
        bottom = bounds.right,
    )

    private fun rotateElementClockwise(element: PageElement, oldWidth: Float, oldHeight: Float): PageElement {
        val newX = (1f - element.y - element.height / oldHeight).coerceIn(0f, 1f)
        val newY = element.x.coerceIn(0f, 1f)
        val rotation = normalizeDegrees(element.rotationDegrees + 90f)
        return when (element) {
            is PageElement.Text -> element.copy(x = newX, y = newY, width = element.height, height = element.width, rotationDegrees = rotation)
            is PageElement.Sticky -> element.copy(x = newX, y = newY, width = element.height, height = element.width, rotationDegrees = rotation)
            is PageElement.Shape -> element.copy(x = newX, y = newY, width = element.height, height = element.width, rotationDegrees = rotation)
            is PageElement.Image -> element.copy(x = newX, y = newY, width = element.height, height = element.width, rotationDegrees = rotation)
            is PageElement.Fill -> element.copy(x = newX, y = newY, width = element.height, height = element.width, rotationDegrees = rotation)
            is PageElement.Link -> element.copy(x = newX, y = newY, width = element.height, height = element.width, rotationDegrees = rotation)
        }
    }

    private fun normalizeDegrees(value: Float): Float = ((value + 180f).mod(360f)) - 180f
}
