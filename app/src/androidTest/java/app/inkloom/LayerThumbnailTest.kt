package app.inkloom

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerThumbnailTest {
    @Test fun hiddenLayerHasIsolatedBoundedPreviewWithoutMutatingVisibility() {
        val layer = InkLayer(id = "hidden", name = "Hidden", visible = false, opacity = .1f, draft = true)
        val page = InkPage(width = 200f, height = 200f, layers = defaultInkLayers() + layer,
            elements = listOf(PageElement.Shape(x = .35f, y = .35f, width = 60f, height = 60f,
                layerId = layer.id, kind = ShapeKind.RECTANGLE, colorArgb = 0xFFFF0000,
                fillArgb = 0xFFFF0000, strokeWidth = 0f)))
        val bitmap = DocumentAssetService(ApplicationProvider.getApplicationContext()).renderLayerThumbnail(page, layer.id, 96)
        try {
            assertEquals(96, bitmap.width)
            assertEquals(Color.RED, bitmap.getPixel(48, 48))
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
            assertFalse(page.layers.last().visible)
            assertEquals(.1f, page.layers.last().opacity)
        } finally { bitmap.recycle() }
    }
}
