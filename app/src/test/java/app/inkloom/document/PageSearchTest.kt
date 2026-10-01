package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSearchTest {
    @Test
    fun findsNativePdfTextWithItsApproximatePageBounds() {
        val bounds = PdfTextBounds(.12f, .22f, .72f, .28f)
        val page = InkPage(
            id = "pdf-page",
            pdfTextBlocks = listOf(PdfTextBlock("The transformer uses multi-head attention.", listOf(bounds))),
            pdfTextIndexVersion = 1,
        )

        val hit = PageSearch.find(page, "MULTI-head")!!

        assertEquals(PageSearchSource.PDF, hit.source)
        assertEquals(bounds, hit.bounds)
        assertTrue(hit.snippet.contains("multi-head", ignoreCase = true))
    }

    @Test
    fun pageMetadataAndObjectsRemainSearchable() {
        val metadata = InkPage(id = "metadata", title = "Lecture 7", tags = listOf("systems"))
        val objectText = InkPage(id = "object", elements = listOf(PageElement.Sticky(x = .1f, y = .1f, text = "Review theorem")))

        assertEquals(PageSearchSource.PAGE, PageSearch.find(metadata, "systems")?.source)
        assertEquals(PageSearchSource.OBJECT, PageSearch.find(objectText, "theorem")?.source)
    }

    @Test
    fun blankAndMissingQueriesDoNotProduceHits() {
        val page = InkPage(pdfTextBlocks = listOf(PdfTextBlock("Known text")))
        assertNull(PageSearch.find(page, ""))
        assertNull(PageSearch.find(page, "missing"))
    }

    @Test fun findsLinkTitlesAndDestinationsAsObjectText() {
        val page = InkPage(elements = listOf(PageElement.Link(x = .1f, y = .1f, title = "Research portal", url = "https://library.example.edu/papers")))

        assertEquals(PageSearchSource.OBJECT, PageSearch.find(page, "Research portal")?.source)
        assertEquals(PageSearchSource.OBJECT, PageSearch.find(page, "library.example")?.source)
    }

    @Test fun recognizedHandwritingIsSearchableOnlyWhileItsRasterSnapshotIsCurrent() {
        val bounds = PdfTextBounds(.1f, .18f, .4f, .25f)
        val rasterRef = RasterTileSurfaceRef("/safe/page.json", "surface", 1600, 2263, 256, 7)
        val base = InkPage(layers = defaultInkLayers().map { layer ->
            if (layer.id == LayerIds.INK) layer.copy(rasterTileSurface = rasterRef) else layer
        })
        val indexed = base.copy(
            recognizedTextBlocks = listOf(RecognizedTextBlock(text = "离线手写识别", bounds = bounds, source = RecognizedTextSource.HANDWRITING, sourceIds = listOf(rasterRef.surfaceId), languageTag = "zh-Hans")),
            handwritingIndexFingerprint = HandwritingIndex.fingerprint(base),
        )

        val hit = PageSearch.find(indexed, "手写")
        assertEquals(PageSearchSource.HANDWRITING, hit?.source)
        assertEquals(bounds, hit?.bounds)
        val changed = indexed.copy(layers = indexed.layers.map { layer ->
            if (layer.id == LayerIds.INK) layer.copy(rasterTileSurface = rasterRef.copy(revision = 8)) else layer
        })
        assertNull(PageSearch.find(changed, "手写"))
    }
    @Test fun allOccurrencesAndNavigationWrapWithoutSkippingRepeatedWords() {
        val page = InkPage(title="Unrelated", pdfTextBlocks=listOf(PdfTextBlock("needle, NEEDLE, needle",listOf(PdfTextBounds(.1f,.2f,.8f,.3f)))))
        val hits = PageSearch.findAll(page,"needle")
        assertEquals(3,hits.size); assertEquals(listOf(0,8,16),hits.map { it.matchStart })
        assertEquals(listOf(0,1,2),hits.map { it.occurrenceIndex })
        assertEquals(0,PageSearch.nextIndex(2,3,1)); assertEquals(2,PageSearch.nextIndex(0,3,-1))
        assertNull(PageSearch.nextIndex(0,0,1))
    }
    @Test fun scannedPdfFingerprintInvalidatesRotatedCroppedOrReplacedScan() {
        val page = InkPage(background=PageBackground.Pdf("/safe/scanned.pdf",3))
        val base = ImageOcrIndex.fingerprint(page)
        assertTrue(base.isNotBlank())
        org.junit.Assert.assertNotEquals(base,ImageOcrIndex.fingerprint(page.copy(backgroundRotationQuarterTurns=1)))
        org.junit.Assert.assertNotEquals(base,ImageOcrIndex.fingerprint(page.copy(crop=PageCrop(.2f,0f,1f,1f))))
        org.junit.Assert.assertNotEquals(base,ImageOcrIndex.fingerprint(page.copy(background=PageBackground.Pdf("/safe/scanned.pdf",4))))
    }

}
