package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.LayerElementBitmaps
import org.junit.Assert.assertEquals
import org.junit.Test

class LayerColorRenderingTest {
    @Test fun rasterColorMatchesLiveExportAndThumbnailWithoutBakingSource() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        val source = PixelTileSurface(64, 64, 64)
        source.beginWrite("black pixels").run {
            editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(Color.BLACK) }
            commit().close()
        }
        val reference = assets.saveRasterTileSurface(source)
        val mask = MaskTileSurface(64, 64, 64, initialDefaultAlpha = 255)
        mask.beginWrite("hole").run { setAlpha(10, 10, 0); commit().close() }
        val maskRef = assets.saveMaskTileSurface(mask.snapshot())
        val layer = InkLayer(id = "ink", name = "Ink", rasterTileSurface = reference,
            colorReplacement = LayerColorReplacement(0xffff0000L, 0xff0000ffL),
            mask = LayerMask(surface = maskRef))
        val page = InkPage(width = 64f, height = 64f, background = PageBackground.Paper(0xffffffff), layers = listOf(layer))
        val exported = assets.renderPageBitmap(page, 64)
        val thumbnail = assets.renderLayerThumbnail(page, layer.id, 64)
        try {
            instrumentation.runOnMainSync {
                val view = InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 64, 64)
                    pagePaperColor = Color.WHITE
                    setDocumentLayers(page.layers)
                    setRasterLayerSurfaces(mapOf(layer.id to source))
                    layerOrder = listOf(layer.id)
                    layerColorReplacements = mapOf(layer.id to requireNotNull(layer.colorReplacement))
                    layerMasks = mapOf(layer.id to requireNotNull(layer.mask))
                    layerMaskSurfaces = mapOf(layer.id to mask.snapshot())
                }
                val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(live))
                    for ((x, y) in listOf(10 to 10, 30 to 30)) {
                        assertEquals(exported.getPixel(x, y), live.getPixel(x, y))
                    }
                    assertEquals(Color.RED, live.getPixel(30, 30))
                    assertEquals(Color.WHITE, live.getPixel(10, 10))
                } finally { live.recycle() }
            }
            assertEquals(Color.RED, thumbnail.getPixel(30, 30))
            assertEquals(Color.BLACK, source.pixelAt(30, 30))
        } finally {
            exported.recycle(); thumbnail.recycle()
            assets.releaseRasterTileReservation(reference)
            assets.releaseAssetReservation(maskRef.assetPath)
        }
    }

    @Test fun retainedShapeColorMatchesLiveAndExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        val layer = InkLayer(id = "shape", name = "Shape", colorReplacement = LayerColorReplacement(0xff2266ccL, 0xffffffffL))
        val shape = PageElement.Shape(x = .1f, y = .1f, width = 40f, height = 40f,
            layerId = layer.id, kind = ShapeKind.RECTANGLE, colorArgb = 0xff000000, fillArgb = 0xff000000, strokeWidth = 0f)
        val page = InkPage(width = 64f, height = 64f, background = PageBackground.Paper(0xffffffff),
            layers = listOf(layer), elements = listOf(shape))
        val exported = assets.renderPageBitmap(page, 64)
        val elements = assets.renderElementLayerBitmap(page, layer.id, 64)
        try {
            instrumentation.runOnMainSync {
                val view = InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                    setDocumentLayers(page.layers); layerOrder = listOf(layer.id)
                    setLayerElementBitmaps(mapOf(layer.id to LayerElementBitmaps(elements, null)))
                    layerColorReplacements = mapOf(layer.id to requireNotNull(layer.colorReplacement))
                }
                val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try { view.draw(Canvas(live)); assertEquals(exported.getPixel(20, 20), live.getPixel(20, 20)) }
                finally { live.recycle() }
            }
        } finally { exported.recycle(); elements.recycle() }
    }
}
