package app.inkloom

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.tools.SelectionProjectiveMapping
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StructuredDeformationRenderingTest {
    @Test fun savedWarpRerendersEditableContentAndPreservesTransparency() {
        val assets = DocumentAssetService(InstrumentationRegistry.getInstrumentation().targetContext)
        val layer = InkLayer(id = "text", name = "Text")
        val original = PageElement.Text(x = .1f, y = .1f, width = 48f, height = 30f,
            layerId = layer.id, text = "A", textSize = 20f, colorArgb = Color.RED.toUInt().toLong())
        val mapping = SelectionProjectiveMapping(0f, 0f, 64f, 64f,
            listOf(8f to 0f, 64f to 8f, 60f to 64f, 0f to 50f))
        val warped = StructuredElementTransform.map(original, mapping, 64, 64, 64f, 64f) as PageElement.Text
        val page = InkPage(width = 64f, height = 64f, layers = listOf(layer), elements = listOf(warped))
        val first = assets.renderElementLayerBitmap(page, layer.id, 64)
        val changed = assets.renderElementLayerBitmap(page.copy(elements = listOf(warped.copy(text = "B"))), layer.id, 64)
        try {
            var colored = 0; var changedPixels = 0; var transparent = 0
            for (y in 0 until 64) for (x in 0 until 64) {
                val pixel = first.getPixel(x, y)
                if (Color.alpha(pixel) != 0) colored++ else transparent++
                if (pixel != changed.getPixel(x, y)) changedPixels++
            }
            assertTrue(colored > 10); assertTrue(transparent > 2000)
            assertTrue("Editing source text must rerender through the retained lattice", changedPixels > 10)
        } finally { first.recycle(); changed.recycle() }
    }
}
