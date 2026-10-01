package app.inkloom

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.PageElement
import app.inkloom.document.ShapeKind
import app.inkloom.document.defaultInkLayers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftLayerExportTest {
    @Test fun nestedDraftFoldersExcludeTheirRetainedContentsInRgba8AndF16() {
        val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val folder = InkLayer(id = "draft-folder", name = "Sketches", role = app.inkloom.document.LayerRole.FOLDER,
            draft = true, passThrough = true)
        val nested = InkLayer(id = "nested", name = "Nested", role = app.inkloom.document.LayerRole.FOLDER, parentId = folder.id)
        val child = InkLayer(id = "child", name = "Sketch", parentId = nested.id)
        val sibling = InkLayer(id = "sibling", name = "Final")
        fun shape(layer: InkLayer, x: Float) = PageElement.Shape(x = x, y = .25f, width = 16f, height = 16f,
            layerId = layer.id, kind = ShapeKind.RECTANGLE, colorArgb = 0xFF000000, fillArgb = 0xFF000000, strokeWidth = 0f)
        for (high in listOf(false, true)) {
            val page = InkPage(width = 64f, height = 64f, layers = listOf(folder, nested, child, sibling),
                colorManagement = app.inkloom.color.PageColorManagement(highPrecision = high),
                elements = listOf(shape(child, .25f), shape(sibling, .75f)))
            val flat = assets.renderPageBitmap(page, 64)
            val composite = assets.renderPageCompositeSurface(page, 64, 64)
            val editable = assets.renderElementLayerBitmap(page, child.id, 64)
            try {
                assertEquals(Color.WHITE, flat.getPixel(20, 20))
                assertEquals(0, composite.pixelAt(20, 20))
                assertEquals(Color.BLACK, composite.pixelAt(52, 20))
                assertEquals(Color.BLACK, editable.getPixel(20, 20))
                val unmarked = page.copy(layers = page.layers.map { if (it.id == folder.id) it.copy(draft = false) else it })
                assertEquals(Color.BLACK, assets.renderPageCompositeSurface(unmarked, 64, 64).pixelAt(20, 20))
            } finally { flat.recycle(); editable.recycle() }
        }
    }

    @Test fun explicitlyMergingSelectedDraftLayersRetainsTheirPixels() {
        val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val lower = InkLayer(id = "draft", name = "Draft", draft = true)
        val upper = InkLayer(id = "final", name = "Final")
        val shape = PageElement.Shape(x = .25f, y = .25f, width = 16f, height = 16f,
            layerId = lower.id, kind = ShapeKind.RECTANGLE, colorArgb = 0xFF000000, fillArgb = 0xFF000000, strokeWidth = 0f)
        val page = InkPage(width = 64f, height = 64f, layers = listOf(lower, upper), elements = listOf(shape))
        val request = app.inkloom.document.RasterLayerMergeRequest(app.inkloom.document.RasterLayerMergeOperation.MERGE_SELECTED,
            upper.id, setOf(lower.id, upper.id))
        val plan = requireNotNull(app.inkloom.document.RasterLayerMerge.plan(page, request).plan)
        assertEquals(Color.BLACK, assets.renderLayerMergeSurface(plan).pixelAt(plan.renderWidth * 5 / 16, plan.renderWidth * 5 / 16))
    }

    @Test fun draftInkStaysVisibleToTheEditorButIsExcludedFromPageOutput() {
        val draft = InkLayer(id = "draft", name = "Rough sketch", draft = true)
        val page = InkPage(
            width = 200f,
            height = 200f,
            layers = defaultInkLayers() + draft,
            elements = listOf(
                PageElement.Shape(
                    id = "draft-box",
                    x = .35f,
                    y = .35f,
                    width = 60f,
                    height = 60f,
                    layerId = draft.id,
                    kind = ShapeKind.RECTANGLE,
                    colorArgb = 0xFF000000,
                    fillArgb = 0xFF000000,
                    strokeWidth = 0f,
                ),
            ),
        )
        val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())

        val exported = assets.renderPageBitmap(page, 200)
        val editorLayer = assets.renderElementLayerBitmap(page, draft.id, 200)

        assertEquals(Color.WHITE, exported.getPixel(100, 100))
        assertEquals(Color.BLACK, editorLayer.getPixel(100, 100))
        exported.recycle()
        editorLayer.recycle()
    }
}
