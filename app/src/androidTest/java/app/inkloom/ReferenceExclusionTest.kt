package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.settings.ReferenceTarget
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.LayerElementBitmaps
import app.inkloom.ui.renderRetainedLayerElements
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceExclusionTest {
    @Test fun paperAndImportedBackgroundAreIndependent() = onMain {
        val view = canvas()
        view.pagePaperColor = Color.WHITE
        assertTrue(view.selectColorRange(Color.WHITE, .01f, false))
        view.excludePaperFromReference = true
        assertFalse(view.selectColorRange(Color.WHITE, .01f, false))
        val imported = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            view.setPageBackground(imported)
            view.pageHasImportedBackground = true
            assertTrue("Excluding paper keeps imported PDF/image pixels", view.selectColorRange(Color.BLUE, .01f, false))
            view.excludeImportedBackgroundFromReference = true
            assertFalse(view.selectColorRange(Color.BLUE, .01f, false))
            view.pageHasImportedBackground = false
            view.setPageBackground(null)
            view.excludePaperFromReference = false
            assertTrue("Excluding imported backgrounds keeps paper", view.selectColorRange(Color.WHITE, .01f, false))
        } finally { imported.recycle() }
    }

    @Test fun textExclusionIsOnDemandAndKeepsOtherContentAndLiveRendering() = onMain {
        val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val layer = InkLayer(id = "ink", name = "Ink")
        val page = InkPage(width = 200f, height = 200f, layers = listOf(layer), elements = listOf(
            PageElement.Text(x = .05f, y = .05f, width = 180f, height = 90f, layerId = layer.id,
                text = "TEST", textSize = 45f, colorArgb = Color.BLACK.toUInt().toLong()),
            PageElement.Shape(x = .15f, y = .6f, width = 100f, height = 50f, layerId = layer.id,
                colorArgb = Color.RED.toUInt().toLong(), fillArgb = Color.RED.toUInt().toLong()),
        ))
        val full = renderRetainedLayerElements(assets, page, layer, 200)
        val temporary = mutableListOf<LayerElementBitmaps>()
        val view = canvas().apply {
            setDocumentLayers(page.layers)
            setLayerElementBitmaps(mapOf(layer.id to full), page.elements)
            renderTextFreeReferenceLayer = { id, elements ->
                assertEquals(layer.id, id)
                renderRetainedLayerElements(assets, page.copy(elements = elements), layer, 200, true)
                    .also(temporary::add)
            }
        }
        try {
            assertTrue(view.selectColorRange(Color.BLACK, .01f, false))
            assertTrue(temporary.isEmpty())
            view.excludeTextFromReference = true
            assertFalse("Text no longer affects reference selection", view.selectColorRange(Color.BLACK, .01f, false))
            assertEquals(1, temporary.size)
            assertTrue("Temporary bitmap is released after sampling", temporary.single().base?.isRecycled == true)
            assertTrue("Other retained objects are preserved", view.selectColorRange(Color.RED, .01f, false))
            assertFalse("Live content remains valid", full.base!!.isRecycled)
            view.excludeTextFromReference = false
            assertTrue("The original text remains in the document", view.selectColorRange(Color.BLACK, .01f, false))
            assertEquals(2, temporary.size)
            view.excludeTextFromReference = true
            view.activeLayerId = "other"
            view.referenceTarget = ReferenceTarget.ACTIVE_LAYER
            assertFalse(view.selectColorRange(Color.BLACK, .01f, false))
            assertEquals("Excluded layers are not rendered", 2, temporary.size)
        } finally { full.recycle() }
    }

    @Test fun textOnlyHighPrecisionLayerLeavesNoReferencePixels() = onMain {
        val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val layer = InkLayer(id = "ink", name = "Text")
        val page = InkPage(width = 200f, height = 200f, layers = listOf(layer),
            colorManagement = app.inkloom.color.PageColorManagement(highPrecision = true),
            elements = listOf(PageElement.Text(x = .05f, y = .05f, width = 180f, height = 90f,
                layerId = layer.id, text = "TEST", textSize = 45f, colorArgb = Color.BLACK.toUInt().toLong())))
        val full = renderRetainedLayerElements(assets, page, layer, 200)
        assertNotNull(full.baseSurface)
        val view = canvas().apply {
            setDocumentLayers(page.layers)
            setLayerElementBitmaps(mapOf(layer.id to full), page.elements)
            renderTextFreeReferenceLayer = { _, elements ->
                renderRetainedLayerElements(assets, page.copy(elements = elements), layer, 200, true)
            }
        }
        assertTrue(view.selectColorRange(Color.BLACK, .01f, false))
        view.excludeTextFromReference = true
        assertFalse(view.selectColorRange(Color.BLACK, .01f, false))
        view.excludeTextFromReference = false
        assertTrue(view.selectColorRange(Color.BLACK, .01f, false))
    }

    private fun canvas() = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
        layout(0, 0, 200, 200)
        pageModelWidth = 200f; pageModelHeight = 200f
        pagePaperColor = Color.WHITE
        activeLayerId = "ink"; layerOrder = listOf("ink", "other")
        bindDocumentSelectionState(DocumentSelectionRuntime(64).stateFor("ref", "page", 200, 200))
    }
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
