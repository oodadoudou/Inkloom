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
