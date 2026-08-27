package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkLayer
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.LayerMask
import app.inkloom.document.LayerMaskRegion
import app.inkloom.document.LayerRole
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.document.defaultInkLayers
import app.inkloom.ui.InkCanvasView
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelRect
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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

    @Test fun v3SparseLayerMaskDrivesLiveCanvasAndFlattenedExport() {
        val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val surface = MaskTileSurface(400, 400, tileSize = 128)
        surface.beginWrite("reveal left half").run {
            composeAlpha(PixelRect(0, 0, 200, 400), ByteArray(200 * 400) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        val reference = assets.saveMaskTileSurface(surface.snapshot())
        val v3Mask = LayerMask(baseVisible = false, regions = emptyList(), surface = reference)
        val v3Layer = layer.copy(mask = v3Mask)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 400, 400)
                setDocumentStrokes(listOf(stroke), "layer-mask-v3")
                layerMasks = mapOf(v3Layer.id to v3Mask)
                layerMaskSurfaces = mapOf(v3Layer.id to surface.snapshot())
            }
            val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                assertTrue(Color.alpha(bitmap.getPixel(100, 200)) > 240)
                assertTrue(Color.alpha(bitmap.getPixel(300, 200)) < 15)
            } finally { bitmap.recycle() }
        }

        val page = InkPage(
            width = 400f,
            height = 400f,
            background = PageBackground.Paper(0xFFFFFFFF),
            layers = defaultInkLayers() + v3Layer,
            strokes = listOf(stroke),
        )
        val exported = assets.renderPageBitmap(page, 400)
        try {
            assertTrue(Color.red(exported.getPixel(100, 200)) < 15)
            assertTrue(Color.red(exported.getPixel(300, 200)) > 240)
        } finally {
            exported.recycle()
            java.io.File(reference.assetPath).delete()
        }
    }

    @Test fun v3LayerMaskSurvivesDocumentBundleRoundTrip() {
        val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val surface = MaskTileSurface(400, 400, tileSize = 128, initialDefaultAlpha = 255)
        surface.beginWrite("mask hole").run { setAlpha(300, 200, 0); commit().close() }
        val reference = assets.saveMaskTileSurface(surface.snapshot())
        val document = InkDocument(
            title = "Layer mask bundle",
            pages = listOf(InkPage(
                width = 400f,
                height = 400f,
                layers = defaultInkLayers() + layer.copy(mask = LayerMask(surface = reference)),
                strokes = listOf(stroke),
            )),
        )
        val archive = ByteArrayOutputStream().also { assets.exportBundle(document, it) }.toByteArray()
        val restored = assets.importBundle(ByteArrayInputStream(archive))
        val restoredReference = requireNotNull(restored.pages.single().layers.first { it.id == layer.id }.mask?.surface)
        try {
            assertTrue(java.io.File(restoredReference.assetPath).isFile)
            val restoredSurface = assets.loadMaskTileSurface(restoredReference)
            assertEquals(0, restoredSurface.alphaAt(300, 200))
            assertEquals(255, restoredSurface.alphaAt(100, 200))
        } finally {
            java.io.File(reference.assetPath).delete()
            java.io.File(restoredReference.assetPath).delete()
        }
    }
}
