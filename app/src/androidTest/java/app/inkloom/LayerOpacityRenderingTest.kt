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
import app.inkloom.document.LayerRole
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.document.defaultInkLayers
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerOpacityRenderingTest {
    private val translucentLayer = InkLayer(id = "paint", name = "Paint", role = LayerRole.CUSTOM, opacity = .25f)
    private val stroke = InkStroke(
        id = "line",
        layerId = translucentLayer.id,
        width = 80f,
        colorArgb = 0xFF000000,
        points = listOf(StrokePoint(.2f, .5f), StrokePoint(.8f, .5f)),
    )

    @Test
    fun canvasAppliesLayerOpacityWithoutChangingStoredStroke() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 400, 400)
                setDocumentStrokes(listOf(stroke), "layer-opacity")
                layerOpacities = mapOf(translucentLayer.id to translucentLayer.opacity)
            }
            val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                assertTrue(Color.alpha(bitmap.getPixel(200, 200)) in 55..75)
                assertTrue(stroke.opacity == 1f)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun flattenedExportAppliesLayerOpacity() {
        val page = InkPage(
            width = 400f,
            height = 400f,
            background = PageBackground.Paper(0xFFFFFFFF),
            layers = defaultInkLayers() + translucentLayer,
            strokes = listOf(stroke),
        )
        val bitmap = DocumentAssetService(ApplicationProvider.getApplicationContext()).renderPageBitmap(page, 400)
        try {
            val red = Color.red(bitmap.getPixel(200, 200))
            assertTrue("Expected translucent black over white, got red=$red", red in 175..205)
        } finally {
            bitmap.recycle()
        }
    }
}
