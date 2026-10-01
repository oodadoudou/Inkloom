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
    @Test fun extendedModesMatchLiveAndExportForRetainedObjectsAndMaskedGroups() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        for (mode in LayerBlendMode.entries.filter { it.requiresPixelCompositor }) {
            val bottom = InkLayer(id = "bottom", name = "Bottom")
            val group = InkLayer(id = "group", name = "Group", role = LayerRole.FOLDER,
                opacity = .5f, blendMode = mode)
            val top = InkLayer(id = "top", name = "Top", parentId = group.id)
            fun shape(id: String, color: Long) = PageElement.Shape(x = 0f, y = 0f, width = 64f, height = 64f,
                layerId = id, kind = ShapeKind.RECTANGLE, colorArgb = color, fillArgb = color, strokeWidth = 0f)
            val page = InkPage(width = 64f, height = 64f, layers = listOf(bottom, group, top),
                elements = listOf(shape(bottom.id, 0xff668899), shape(top.id, 0xff99bb33)))
            val exported = assets.renderPageBitmap(page, 64)
            try { instrumentation.runOnMainSync {
                val bitmaps = listOf(bottom, top).associate { it.id to app.inkloom.ui.LayerElementBitmaps(assets.renderElementLayerBitmap(page, it.id, 64), null) }
                val view = InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                    setDocumentLayers(page.layers); layerOrder = listOf(bottom.id, top.id)
                    layerOpacities = page.layers.associate { it.id to it.opacity }
                    layerBlendModes = page.layers.associate { it.id to it.blendMode }
                    setLayerElementBitmaps(bitmaps)
                }
                val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(live))
                    assertEquals("$mode must use exact projection with retained objects", exported.getPixel(32, 32), live.getPixel(32, 32))
                } finally { live.recycle(); bitmaps.values.forEach { it.base?.recycle() } }
            } } finally { exported.recycle() }
        }
    }

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
    @Test fun throughMixedAndRetainedOnlyContentMatchExportAndCacheBitmapConversions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        for (rasterBackdrop in listOf(false, true)) {
            val blue = PixelTileSurface(64, 64, tileSize = 64, surfaceId = "blue").also {
                it.beginWrite("fixture").run { editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { p, _ -> p.fill(Color.BLUE) }; commit().close() }
            }
            val ref = if (rasterBackdrop) assets.saveRasterTileSurface(blue) else null
            val bottom = InkLayer(id = "blue", name = "Blue", rasterTileSurface = ref)
            val group = InkLayer(id = "group", name = "Group", role = LayerRole.FOLDER, opacity = .5f, passThrough = true)
            val child = InkLayer(id = "red", name = "Red", parentId = group.id, blendMode = LayerBlendMode.MULTIPLY)
            fun shape(id: String, color: Long) = PageElement.Shape(x = 0f, y = 0f, width = 64f, height = 64f,
                layerId = id, kind = ShapeKind.RECTANGLE, colorArgb = color, fillArgb = color, strokeWidth = 0f)
            val page = InkPage(width = 64f, height = 64f, layers = listOf(bottom, group, child),
                elements = buildList { if (!rasterBackdrop) add(shape("blue", 0xFF0000FF)); add(shape("red", 0xFFFF0000)) })
            val exported = assets.renderPageBitmap(page, 64)
            try {
                assertEquals("Half of a multiply contribution over blue", 0xFF000080.toInt(), exported.getPixel(32, 32))
                instrumentation.runOnMainSync {
                    val bitmaps = page.layers.filter { it.id == "red" || (it.id == "blue" && !rasterBackdrop) }
                        .associate { it.id to app.inkloom.ui.LayerElementBitmaps(assets.renderElementLayerBitmap(page, it.id, 64), null) }
                    val view = InkCanvasView(instrumentation.targetContext).apply {
                        layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                        setDocumentLayers(page.layers); layerOrder = listOf("blue", "red")
                        setRasterLayerSurfaces(if (rasterBackdrop) mapOf("blue" to blue) else emptyMap())
                        layerOpacities = page.layers.associate { it.id to it.opacity }
                        layerBlendModes = page.layers.associate { it.id to it.blendMode }
                        setLayerElementBitmaps(bitmaps)
                    }
                    val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(live)); assertEquals(exported.getPixel(32, 32), live.getPixel(32, 32))
                        val conversions = view.retainedBitmapConversionCount
                        assertEquals(bitmaps.size, conversions)
                        view.draw(Canvas(live)); assertEquals(conversions, view.retainedBitmapConversionCount)
                        assertEquals(0, view.lastRasterProjectionRecomposedTileCount)
                        requireNotNull(bitmaps["red"]?.base).eraseColor(Color.WHITE)
                        view.draw(Canvas(live)); assertEquals(Color.BLUE, live.getPixel(32, 32))
                        assertEquals(conversions + 1, view.retainedBitmapConversionCount)
                    } finally { live.recycle(); bitmaps.values.forEach { it.base?.recycle() } }
                }
            } finally { exported.recycle(); ref?.let(assets::releaseRasterTileReservation) }
        }
    }

    @Test fun emptyOrHiddenClippingBaseNeverReusesAnEarlierSiblingInLiveOrExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        for (hidden in listOf(false, true)) {
            val blue = InkLayer(id = "blue", name = "Blue")
            val base = InkLayer(id = "base", name = "Empty base", visible = !hidden)
            val clipped = InkLayer(id = "red", name = "Clipped", clippedToBelow = true)
            fun shape(id: String, color: Long) = PageElement.Shape(x = 0f, y = 0f, width = 64f, height = 64f,
                layerId = id, kind = ShapeKind.RECTANGLE, colorArgb = color, fillArgb = color, strokeWidth = 0f)
            val page = InkPage(width = 64f, height = 64f, layers = listOf(blue, base, clipped),
                elements = listOf(shape("blue", 0xFF0000FF), shape("red", 0xFFFF0000)))
            val exported = assets.renderPageBitmap(page, 64)
            try {
                assertEquals(Color.BLUE, exported.getPixel(32, 32))
                instrumentation.runOnMainSync {
                    val bitmaps = listOf(blue, clipped).associate { it.id to app.inkloom.ui.LayerElementBitmaps(assets.renderElementLayerBitmap(page, it.id, 64), null) }
                    val view = InkCanvasView(instrumentation.targetContext).apply {
                        layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                        setDocumentLayers(page.layers); layerOrder = listOf("blue", "base", "red")
                        clippedLayers = setOf("red"); setLayerElementBitmaps(bitmaps)
                    }
                    val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    try { view.draw(Canvas(live)); assertEquals(Color.BLUE, live.getPixel(32, 32)) }
                    finally { live.recycle(); bitmaps.values.forEach { it.base?.recycle() } }
                }
            } finally { exported.recycle() }
        }
    }

    @Test fun throughSparseMaskChangesStayLocalOnEightKCanvas() {
        val back = PixelTileSurface(8192, 8192, 64)
        val front = PixelTileSurface(8192, 8192, 64)
        for ((surface, color) in listOf(back to Color.BLUE, front to Color.RED)) {
            surface.beginWrite("two tiles").run {
                for (column in 20..21) editTile(PixelTileKey(column, 20), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(color) }
                commit().close()
            }
        }
        val mask = MaskTileSurface(8192, 8192, 64, initialDefaultAlpha = 255)
        val cache = app.inkloom.engine.v5.composite.PixelGroupProjectionCache(8192, 8192, 64)
        fun update() = cache.update(listOf(
            app.inkloom.engine.v5.composite.PixelProjectionNode.Paint(app.inkloom.engine.v5.composite.PixelLayerProjectionInput("back", back.snapshot())),
            app.inkloom.engine.v5.composite.PixelProjectionNode.Group("group", listOf(
                app.inkloom.engine.v5.composite.PixelProjectionNode.Paint(app.inkloom.engine.v5.composite.PixelLayerProjectionInput("front", front.snapshot()))),
                .5f, mask = mask.snapshot(), passThrough = true)))
        val initial = update().snapshot.generationAt(PixelTileKey(21, 20))
        val times = (0 until 30).map { iteration ->
            mask.beginWrite("local alpha").run { setAlpha(20 * 64 + iteration, 20 * 64, iteration * 7); commit().close() }
            val start = android.os.SystemClock.elapsedRealtimeNanos()
            val result = update()
            val elapsed = (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1_000_000f
            assertEquals(initial, result.snapshot.generationAt(PixelTileKey(21, 20)))
            assertEquals(2, result.snapshot.tileCount)
            assertEquals(2, result.recomposedTileCount)
            elapsed
        }.drop(10).sorted()
        val p95 = times[(times.size * .95).toInt().coerceAtMost(times.lastIndex)]
        android.util.Log.i("InkloomPerf", "through_group_8k_local_mask_p95_ms=$p95")
        assertTrue("Through group local mask p95 ${p95}ms", p95 < 16.7f)
        assertEquals(0, update().recomposedTileCount)
    }

}
