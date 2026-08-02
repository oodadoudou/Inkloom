package app.inkloom.document

object PdfGotoLinkNavigation {
    fun hit(links: List<PdfGotoLink>, x: Float, y: Float, tolerance: Float = .008f): PdfGotoLink? = links
        .asSequence()
        .filter { link -> link.bounds.any { bounds -> x >= bounds.left - tolerance && x <= bounds.right + tolerance && y >= bounds.top - tolerance && y <= bounds.bottom + tolerance } }
        .minByOrNull { link -> link.bounds.minOfOrNull { (it.right - it.left).coerceAtLeast(0f) * (it.bottom - it.top).coerceAtLeast(0f) } ?: Float.MAX_VALUE }

    fun rotateDestinationClockwise(link: PdfGotoLink): PdfGotoLink = link.copy(
        destinationX = (1f - link.destinationY).coerceIn(0f, 1f),
        destinationY = link.destinationX.coerceIn(0f, 1f),
    )

    fun destinationBounds(link: PdfGotoLink, radius: Float = .018f): PdfTextBounds =
        PdfTextBounds(
            (link.destinationX - radius).coerceIn(0f, 1f),
            (link.destinationY - radius).coerceIn(0f, 1f),
            (link.destinationX + radius).coerceIn(0f, 1f),
            (link.destinationY + radius).coerceIn(0f, 1f),
        )

    fun remapDestinations(pages: List<InkPage>, pageIds: Map<String, String>): List<InkPage> = pages.map { page ->
        page.copy(pdfGotoLinks = page.pdfGotoLinks.map { link ->
            link.copy(destinationPageId = pageIds[link.destinationPageId] ?: link.destinationPageId)
        })
    }

    fun rotateDestinationCoordinates(pages: List<InkPage>, destinationPageId: String): List<InkPage> = pages.map { page ->
        page.copy(pdfGotoLinks = page.pdfGotoLinks.map { link ->
            if (link.destinationPageId == destinationPageId) rotateDestinationClockwise(link) else link
        })
    }
}
