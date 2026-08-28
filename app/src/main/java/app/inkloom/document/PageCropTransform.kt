package app.inkloom.document

object PageCropTransform {
    private const val MIN_SIZE = .05f

    fun normalized(value: PageCrop): PageCrop {
        val left = value.left.coerceIn(0f, 1f - MIN_SIZE)
        val top = value.top.coerceIn(0f, 1f - MIN_SIZE)
        val right = value.right.coerceIn(left + MIN_SIZE, 1f)
        val bottom = value.bottom.coerceIn(top + MIN_SIZE, 1f)
        return PageCrop(left, top, right, bottom)
    }

    fun visibleWidth(page: InkPage): Float = page.width * width(page.crop)

    fun visibleHeight(page: InkPage): Float = page.height * height(page.crop)

    fun project(page: InkPage): InkPage {
        val crop = normalized(page.crop)
        return page.copy(
            width = page.width * width(crop),
            height = page.height * height(crop),
            crop = PageCrop(),
            layers = page.layers.map { layer ->
                layer.copy(mask = layer.mask?.copy(regions = layer.mask.regions.map { region ->
                    region.copy(points = region.points.map { point -> project(point, crop) })
                }))
            },
            elements = page.elements.map { project(it, crop) },
            pdfGotoLinks = page.pdfGotoLinks.mapNotNull { link ->
                link.copy(bounds = link.bounds.mapNotNull { project(it, crop) }).takeIf { it.bounds.isNotEmpty() }
            },
        )
    }

    fun unproject(element: PageElement, cropValue: PageCrop): PageElement {
        val crop = normalized(cropValue)
        val x = crop.left + element.x * width(crop)
        val y = crop.top + element.y * height(crop)
        return element.withPosition(x, y)
    }

    fun project(bounds: PdfTextBounds, cropValue: PageCrop): PdfTextBounds? {
        val crop = normalized(cropValue)
        val left = maxOf(bounds.left, crop.left)
        val top = maxOf(bounds.top, crop.top)
        val right = minOf(bounds.right, crop.right)
        val bottom = minOf(bounds.bottom, crop.bottom)
        if (right <= left || bottom <= top) return null
        return PdfTextBounds(
            left = ((left - crop.left) / width(crop)).coerceIn(0f, 1f),
            top = ((top - crop.top) / height(crop)).coerceIn(0f, 1f),
            right = ((right - crop.left) / width(crop)).coerceIn(0f, 1f),
            bottom = ((bottom - crop.top) / height(crop)).coerceIn(0f, 1f),
        )
    }

    fun rotateClockwise(value: PageCrop): PageCrop {
        val crop = normalized(value)
        return normalized(PageCrop(1f - crop.bottom, crop.left, 1f - crop.top, crop.right))
    }

    private fun project(element: PageElement, crop: PageCrop): PageElement = element.withPosition(
        (element.x - crop.left) / width(crop),
        (element.y - crop.top) / height(crop),
    )

    private fun project(point: StrokePoint, crop: PageCrop) = point.copy(
        x = (point.x - crop.left) / width(crop),
        y = (point.y - crop.top) / height(crop),
    )

    private fun PageElement.withPosition(x: Float, y: Float): PageElement = when (this) {
        is PageElement.Text -> copy(x = x, y = y)
        is PageElement.Sticky -> copy(x = x, y = y)
        is PageElement.Shape -> copy(x = x, y = y)
        is PageElement.Image -> copy(x = x, y = y)
        is PageElement.Fill -> copy(x = x, y = y)
        is PageElement.AudioMarker -> copy(x = x, y = y)
        is PageElement.Link -> copy(x = x, y = y)
    }

    private fun width(crop: PageCrop) = (crop.right - crop.left).coerceAtLeast(MIN_SIZE)
    private fun height(crop: PageCrop) = (crop.bottom - crop.top).coerceAtLeast(MIN_SIZE)
}
