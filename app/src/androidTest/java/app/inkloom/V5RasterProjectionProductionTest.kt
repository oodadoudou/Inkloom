package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.LayerBlendMode
import app.inkloom.document.LayerContentKind
import app.inkloom.document.LayerMask
import app.inkloom.document.PageBackground
import app.inkloom.document.PageElement
import app.inkloom.document.ShapeKind
import app.inkloom.document.defaultInkLayers
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V5RasterProjectionProductionTest {
    @Test fun productionCanvasCompositesPureRasterLayersThroughV5Projection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val lower = solidSurface("lower", Color.RED)
            val upper = solidSurface("upper", Color.BLUE)
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 256, 256)
                pagePaperColor = Color.WHITE
                setRasterLayerSurfaces(linkedMapOf("lower" to lower, "upper" to upper))
                layerOrder = listOf("lower", "upper")
                layerOpacities = mapOf("lower" to 1f, "upper" to .5f)
                layerBlendModes = mapOf("lower" to LayerBlendMode.NORMAL, "upper" to LayerBlendMode.MULTIPLY)
            }
            val output = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(output))
                val pixel = output.getPixel(128, 128)
                assertTrue("Expected 50% blue Multiply over red, got ${Integer.toHexString(pixel)}", Color.red(pixel) in 125..130)
                assertTrue(Color.green(pixel) <= 2 && Color.blue(pixel) <= 2)
                assertEquals(1, view.v5RasterProjectionDrawCount)
                assertEquals(1, view.lastRasterProjectionRecomposedTileCount)

                view.draw(Canvas(output))
                assertEquals(2, view.v5RasterProjectionDrawCount)
                assertEquals(0, view.lastRasterProjectionRecomposedTileCount)
            } finally {
                output.recycle()
            }
        }
    }

    @Test fun pageExportUsesTheSameSparseRasterProjectionContract() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = DocumentAssetService(context)
        val lowerReference = assets.saveRasterTileSurface(solidSurface("export-lower", Color.RED))
        val upperReference = assets.saveRasterTileSurface(solidSurface("export-upper", Color.BLUE))
        val lower = InkLayer(
            id = "export-lower",
            name = "Lower",
            contentKind = LayerContentKind.RASTER,
            rasterTileSurface = lowerReference,
        )
        val upper = InkLayer(
            id = "export-upper",
            name = "Upper",
            contentKind = LayerContentKind.RASTER,
            rasterTileSurface = upperReference,
            opacity = .5f,
            blendMode = LayerBlendMode.MULTIPLY,
        )
        val page = InkPage(
            width = 64f,
            height = 64f,
            background = PageBackground.Paper(0xFFFFFFFF),
            layers = defaultInkLayers() + lower + upper,
        )
        val output = assets.renderPageBitmap(page, 256)
        try {
            val pixel = output.getPixel(128, 128)
            assertTrue("Export must share V5 blend output, got ${Integer.toHexString(pixel)}", Color.red(pixel) in 125..130)
            assertTrue(Color.green(pixel) <= 2 && Color.blue(pixel) <= 2)
            assertEquals(1, assets.v5RasterProjectionExportCount)
        } finally {
            output.recycle()
            assets.releaseRasterTileReservation(lowerReference)
            assets.releaseRasterTileReservation(upperReference)
        }
    }

    @Test fun rasterAndPageElementsExportWithoutAHiddenVectorRenderer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = DocumentAssetService(context)
        val reference = assets.saveRasterTileSurface(solidSurface("raster-with-element", Color.YELLOW))
        val raster = InkLayer(
            id = "raster-with-element",
            name = "Raster",
            contentKind = LayerContentKind.RASTER,
            rasterTileSurface = reference,
        )
        val shape = PageElement.Shape(
            id = "shape",
            x = .25f,
            y = .25f,
            width = 32f,
            height = 32f,
            layerId = raster.id,
            kind = ShapeKind.RECTANGLE,
            colorArgb = 0xFF0000FF,
            fillArgb = 0xFF0000FF,
            strokeWidth = 0f,
        )
        val output = assets.renderPageBitmap(
            InkPage(
                width = 64f,
                height = 64f,
                background = PageBackground.Paper(0xFFFFFFFF),
                layers = defaultInkLayers() + raster,
                elements = listOf(shape),
            ),
            256,
        )
        try {
            assertEquals(Color.BLUE, output.getPixel(128, 128))
            assertEquals(Color.YELLOW, output.getPixel(16, 16))
            assertEquals(0, assets.v5RasterProjectionExportCount)
        } finally {
            output.recycle()
            assets.releaseRasterTileReservation(reference)
        }
    }

    @Test fun committedTileSurfaceRemainsDisplayTruthAfterEditWorkspaceWasPrewarmed() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val surface = PixelTileSurface(64, 64, tileSize = 64, surfaceId = "authoritative")
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 256, 256)
                pagePaperColor = Color.WHITE
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
                layerOrder = listOf("paint")
            }
            surface.beginWrite("committed after binding").run {
                editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(Color.MAGENTA) }
                commit().close()
            }
            val output = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(output))
                assertEquals(Color.MAGENTA, output.getPixel(128, 128))
                assertEquals(1, view.v5RasterProjectionDrawCount)
            } finally {
                output.recycle()
            }
        }
    }

    @Test fun sparseLayerMasksUseV5Projection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val surface = solidSurface("raster", Color.YELLOW)
            val masked = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 256, 256)
                setRasterLayerSurfaces(mapOf("raster" to surface))
                layerOrder = listOf("raster")
                layerMasks = mapOf("raster" to LayerMask(enabled = true))
            }
            val sparseMask = MaskTileSurface(64, 64, tileSize = 64).also { mask ->
                mask.beginWrite("left half").run {
                    composeAlpha(PixelRect(0, 0, 32, 64), ByteArray(32 * 64) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
                    commit().close()
                }
            }
            val sparseMasked = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 256, 256)
                setRasterLayerSurfaces(mapOf("raster" to surface))
                layerOrder = listOf("raster")
                layerMasks = mapOf("raster" to LayerMask(enabled = true))
                layerMaskSurfaces = mapOf("raster" to sparseMask.snapshot())
            }
            val output = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            try {
                masked.draw(Canvas(output))
                assertEquals(0, masked.v5RasterProjectionDrawCount)
                output.eraseColor(Color.TRANSPARENT)
                sparseMasked.draw(Canvas(output))
                assertEquals(1, sparseMasked.v5RasterProjectionDrawCount)
                assertEquals(Color.YELLOW, output.getPixel(64, 128))
                assertEquals(Color.TRANSPARENT, output.getPixel(192, 128))
            } finally {
                output.recycle()
            }
        }
    }

    private fun solidSurface(id: String, color: Int): PixelTileSurface =
        PixelTileSurface(64, 64, tileSize = 64, surfaceId = id).also { surface ->
            surface.beginWrite("solid $id").run {
                editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(color) }
                commit().close()
            }
        }
}
