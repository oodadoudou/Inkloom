package app.inkloom

import android.graphics.*
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CanvasSizingExportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun anchoredCanvasResizeKeepsPixelsMasksTextAndOneReversibleEdit() {
        val assets = DocumentAssetService(context)
        val source = PixelTileSurface(64, 64)
        source.beginWrite("ink").use { it.setPixel(10, 20, Color.RED); it.commit().close() }
        val ref = assets.saveRasterTileSurface(source)
        val mask = MaskTileSurface(64, 64, initialDefaultAlpha = 255)
        mask.beginWrite("hole").use { it.setAlpha(10, 20, 0); it.commit().close() }
        val maskRef = assets.saveMaskTileSurface(mask.snapshot())
        val text = PageElement.Text(x = .1f, y = .2f, width = 20f, height = 16f, text = "Keep")
        val page = InkPage(width = 64f, height = 64f, elements = listOf(text),
            layers = listOf(InkLayer(id = "ink", name = "Ink", rasterTileSurface = ref, mask = LayerMask(surface = maskRef))))
        try { assets.prepareCanvasSize(page, CanvasSizeRequest(128f, 96f, 144f)).use { prepared ->
            val after = prepared.edit.after
            assertEquals(144f, after.dpi, 0f)
            val layer = after.layers.single()
            assertEquals(Color.RED, assets.loadRasterTileSurface(requireNotNull(layer.rasterTileSurface)).pixelAt(42, 36))
            val alpha = assets.loadMaskTileSurface(requireNotNull(layer.mask?.surface)).snapshot()
            assertEquals(0, alpha.alphaAt(42, 36)); assertEquals(255, alpha.alphaAt(0, 0))
            assertEquals("Keep", (after.elements.single() as PageElement.Text).text)
            assertEquals((text.x * 64 + 32) / 128, after.elements.single().x, .001f)
            assertEquals(page, prepared.edit.apply(after, true))
            assertEquals(after, prepared.edit.apply(page, false))
            assertNull(prepared.edit.apply(page.copy(title = "Changed"), false))
        } } finally { assets.releaseRasterTileReservation(ref); assets.releaseAssetReservation(maskRef.assetPath) }
    }

    @Test fun transparentNative8kPngRetainsFarEdgeInkWithoutFullImageDecode() {
        val assets = DocumentAssetService(context)
        val source = PixelTileSurface(8192, 8192)
        source.beginWrite("far edge").use { it.setPixel(8191, 8191, Color.RED); it.commit().close() }
        val ref = assets.saveRasterTileSurface(source)
        val page = InkPage(width = 8192f, height = 8192f,
            layers = listOf(InkLayer(id = "ink", name = "Ink", rasterTileSurface = ref)))
        val file = File(context.cacheDir, "native8k-stream-test.png")
        try {
            assets.exportPagePng(page, Uri.fromFile(file), PageRasterExportOptions(includeBackground = false))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            assertEquals(8192, bounds.outWidth); assertEquals(8192, bounds.outHeight)
            val decoder = BitmapRegionDecoder.newInstance(file.path, false)
            try {
                val edge = decoder.decodeRegion(Rect(8190, 8190, 8192, 8192), null)
                try { assertEquals(Color.RED, edge.getPixel(1, 1)); assertEquals(0, edge.getPixel(0, 0)) } finally { edge.recycle() }
            } finally { decoder.recycle() }
        } finally { file.delete(); assets.releaseRasterTileReservation(ref) }
    }

    @Test fun native8kRetainedContentUsesSparseProjectionAndImageRegionDecoding() {
        val assets = DocumentAssetService(context)
        val file = File(context.cacheDir, "large-image-region-test.png")
        try {
            file.outputStream().use { out -> StreamingPng.write(out, 8192, 8192) { y, row ->
                row.fill(0); if (y == 8191) row[8191] = Color.BLUE
            } }
            val image = PageElement.Image(assetPath = file.path, x = 0f, y = 0f, width = 8192f, height = 8192f, layerId = "image")
            val page = InkPage(width = 8192f, height = 8192f, elements = listOf(image), layers = listOf(InkLayer(id = "image", name = "Image")))
            val snapshot = assets.renderElementLayerSurface(page, "image", 8192, 8192)
            assertEquals(Color.BLUE, snapshot.pixelAt(8191, 8191))
            assertTrue("Transparent image must not retain page-sized storage", snapshot.tileCount <= 1)
            val view = app.inkloom.ui.InkCanvasView(context)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view.layout(0, 0, 128, 128); view.layerOrder = listOf("image")
                view.setLayerElementBitmaps(mapOf("image" to app.inkloom.ui.LayerElementBitmaps(null, null, snapshot)))
                assertEquals(listOf("image"), view.layerCandidatesAt(.99999f, .99999f))
            }
        } finally { file.delete() }
    }

    @Test fun savedSelectionKeepsItsCropCoordinatesThroughCanvasResize() {
        val assets = DocumentAssetService(context)
        val mask = MaskTileSurface(32, 64)
        mask.beginWrite("selection").use { it.setAlpha(8, 20, 255); it.commit().close() }
        val ref = assets.saveMaskTileSurface(mask.snapshot())
        val page = InkPage(width = 64f, height = 64f,
            savedSelections = listOf(SavedSelection(name = "Cropped", surface = ref, crop = PageCrop(.5f, 0f, 1f, 1f))))
        try { assets.prepareCanvasSize(page, CanvasSizeRequest(96f, 64f, 300f)).use { prepared ->
            val saved = prepared.edit.after.savedSelections.single()
            assertEquals(PageCrop(), saved.crop)
            val projected = assets.loadMaskTileSurface(saved.surface).snapshot()
            assertEquals(255, projected.alphaAt(56, 20))
            assertEquals(0, projected.alphaAt(24, 20))
        } } finally { assets.releaseAssetReservation(ref.assetPath) }
    }

    @Test fun customExportSizeAndBackgroundMatchesAuthoredComposite() {
        val assets = DocumentAssetService(context)
        val shape = PageElement.Shape(x = .25f, y = .25f, width = 32f, height = 32f,
            layerId = "shape", kind = ShapeKind.RECTANGLE, fillArgb = 0xff336699, strokeWidth = 0f)
        val page = InkPage(width = 64f, height = 64f, layers = listOf(InkLayer(id = "shape", name = "Shape")), elements = listOf(shape))
        val file = File(context.cacheDir, "custom-export-test.png")
        try {
            assets.exportPagePng(page, Uri.fromFile(file), PageRasterExportOptions(128, 96, false))
            val output = BitmapFactory.decodeFile(file.path)
            try { assertEquals(128, output.width); assertEquals(96, output.height); assertEquals(0, output.getPixel(0, 0)); assertEquals(0xff336699.toInt(), output.getPixel(64, 48)) }
            finally { output.recycle() }
        } finally { file.delete() }
    }
}
