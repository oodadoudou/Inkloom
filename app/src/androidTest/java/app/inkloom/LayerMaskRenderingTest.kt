package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.LayerMask
import app.inkloom.document.LayerMaskRegion
import app.inkloom.document.LayerRole
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.document.defaultInkLayers
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerMaskRenderingTest {
    private val region = listOf(
        StrokePoint(0f, 0f), StrokePoint(.5f, 0f), StrokePoint(.5f, 1f), StrokePoint(0f, 1f),
    )
    private val mask = LayerMask(baseVisible = false, regions = listOf(LayerMaskRegion(region, reveal = true)))
    private val layer = InkLayer(id = "paint", name = "Paint", role = LayerRole.CUSTOM, mask = mask)
    private val stroke = InkStroke(
        id = "masked-line",
        layerId = layer.id,
        width = 60f,
        colorArgb = 0xFF000000,
        points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)),
    )

    @Test fun canvasCompositesEnabledLayerMask() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 400, 400)
                setDocumentStrokes(listOf(stroke), "layer-mask")
                layerMasks = mapOf(layer.id to mask)
            }
            val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                assertTrue(Color.alpha(bitmap.getPixel(100, 200)) > 240)
                assertTrue(Color.alpha(bitmap.getPixel(300, 200)) < 15)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun flattenedExportMatchesLayerMask() {
        val page = InkPage(
            width = 400f,
            height = 400f,
            background = PageBackground.Paper(0xFFFFFFFF),
            layers = defaultInkLayers() + layer,
            strokes = listOf(stroke),
        )
        val bitmap = DocumentAssetService(ApplicationProvider.getApplicationContext()).renderPageBitmap(page, 400)
        try {
            assertTrue(Color.red(bitmap.getPixel(100, 200)) < 15)
            assertTrue(Color.red(bitmap.getPixel(300, 200)) > 240)
        } finally {
            bitmap.recycle()
        }
    }
}
