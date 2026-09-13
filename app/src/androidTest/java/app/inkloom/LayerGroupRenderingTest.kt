package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerGroupRenderingTest {
    @Test fun groupMaskOpacityAndIsolationMatchCanvasAndExportForNestedRasterLayers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        fun solid(id: String, color: Int) = PixelTileSurface(64, 64, tileSize = 64, surfaceId = id).also {
            it.beginWrite("fixture").run { editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { p, _ -> p.fill(color) }; commit().close() }
        }
        val blue = solid("blue", Color.BLUE); val red = solid("red", Color.RED)
        val blueRef = assets.saveRasterTileSurface(blue); val redRef = assets.saveRasterTileSurface(red)
        val mask = MaskTileSurface(64, 64, tileSize = 64, initialDefaultAlpha = 255)
        mask.beginWrite("hole").run { setAlpha(16, 16, 0); commit().close() }
        val maskRef = assets.saveMaskTileSurface(mask.snapshot())
        val group = InkLayer(id = "group", name = "Group", role = LayerRole.FOLDER, opacity = .5f, mask = LayerMask(surface = maskRef))
        val inner = InkLayer(id = "inner", name = "Inner", role = LayerRole.FOLDER, parentId = group.id)
        val layers = listOf(InkLayer(id = blue.surfaceId, name = "Blue", rasterTileSurface = blueRef), group, inner,
            InkLayer(id = red.surfaceId, name = "Red", rasterTileSurface = redRef, parentId = inner.id, blendMode = LayerBlendMode.MULTIPLY))
        val page = InkPage(width = 64f, height = 64f, background = PageBackground.Paper(0xFFFFFFFF), layers = layers)
        val exported = assets.renderPageBitmap(page, 64)
        try {
            instrumentation.runOnMainSync {
                val view = InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                    setDocumentLayers(layers)
                    setRasterLayerSurfaces(mapOf(blue.surfaceId to blue, red.surfaceId to red))
                    layerOrder = listOf(blue.surfaceId, red.surfaceId)
                    layerOpacities = layers.associate { it.id to it.opacity }
                    layerBlendModes = layers.associate { it.id to it.blendMode }
                    layerMasks = mapOf(group.id to requireNotNull(group.mask))
                    layerMaskSurfaces = mapOf(group.id to mask.snapshot())
                }
                val output = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(output))
                    for ((x, y) in listOf(32 to 32, 16 to 16)) assertEquals(exported.getPixel(x, y), output.getPixel(x, y))
                    val pixel = output.getPixel(32, 32)
                    assertTrue(Color.red(pixel) in 127..128 && Color.blue(pixel) in 127..128)
                    assertEquals(Color.BLUE, output.getPixel(16, 16))
                    view.draw(Canvas(output)); assertEquals(0, view.lastRasterProjectionRecomposedTileCount)
                } finally { output.recycle() }
            }
            val raw = assets.renderRasterLayerBitmap(page, red.surfaceId, 64)
            try { assertEquals("Editing child pixels must not bake group alpha or mask", Color.RED, raw.getPixel(16, 16)) }
            finally { raw.recycle() }
        } finally { exported.recycle(); assets.releaseRasterTileReservation(blueRef); assets.releaseRasterTileReservation(redRef) }
    }

    @Test fun retainedShapesUseTheSameIsolatedGroupOpacityDuringExport() {
        val assets = DocumentAssetService(InstrumentationRegistry.getInstrumentation().targetContext)
        val group = InkLayer(id = "group", name = "Group", role = LayerRole.FOLDER, opacity = .5f)
        val a = InkLayer(id = "a", name = "A", parentId = group.id)
        val b = InkLayer(id = "b", name = "B", parentId = group.id)
        fun shape(layerId: String, x: Float) = PageElement.Shape(x = x, y = .2f, width = 32f, height = 32f,
            layerId = layerId, kind = ShapeKind.RECTANGLE, colorArgb = 0xFF000000, fillArgb = 0xFF000000, strokeWidth = 0f)
        val page = InkPage(width = 64f, height = 64f, layers = listOf(group, a, b),
            background = PageBackground.Paper(0xFFFFFFFF), elements = listOf(shape(a.id, .1f), shape(b.id, .35f)))
        val result = assets.renderPageBitmap(page, 64)
        try {
            for (x in listOf(12, 28, 48)) assertTrue("Overlapping retained shapes must not darken at $x", Color.red(result.getPixel(x, 20)) in 127..128)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val elements = listOf(a, b).associate { it.id to app.inkloom.ui.LayerElementBitmaps(assets.renderElementLayerBitmap(page, it.id, 64), null) }
                val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                    layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                    setDocumentLayers(page.layers); layerOrder = listOf(a.id, b.id)
                    layerOpacities = page.layers.associate { it.id to it.opacity }
                    setLayerElementBitmaps(elements)
                }
                val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(live))
                    for (x in listOf(12, 28, 48)) assertEquals("Retained content live/export parity", result.getPixel(x, 20), live.getPixel(x, 20))
                } finally { live.recycle(); elements.values.forEach { it.base?.recycle() } }
            }
        } finally { result.recycle() }
    }
}
