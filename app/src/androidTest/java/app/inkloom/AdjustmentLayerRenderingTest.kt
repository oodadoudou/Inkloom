package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.LayerAdjustment
import app.inkloom.document.LayerAdjustmentKind
import app.inkloom.document.LayerMask
import app.inkloom.document.LayerRole
import app.inkloom.document.PageBackground
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdjustmentLayerRenderingTest {
    @Test fun newAdjustmentsRespectMasksAndUpperLayersInCanvasAndExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        val sourceColor = Color.rgb(64, 128, 192)
        val ink = PixelTileSurface(64, 64, 64, "new-adjustment-fixture")
        ink.beginWrite("fixture").use { write ->
            write.editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(sourceColor) }
            write.commit().close()
        }
        val top = PixelTileSurface(64, 64, 64, "top")
        top.beginWrite("top").use { write -> write.setPixel(48, 32, Color.RED); write.commit().close() }
        val mask = MaskTileSurface(64, 64, 64, initialDefaultAlpha = 255)
        mask.beginWrite("mask").use { write ->
            for (y in 0 until 64) for (x in 32 until 64) write.setAlpha(x, y, 0)
            write.commit().close()
        }
        val inkRef = assets.saveRasterTileSurface(ink)
        val topRef = assets.saveRasterTileSurface(top)
        val maskRef = assets.saveMaskTileSurface(mask.snapshot())
        val cases = listOf(
            LayerAdjustment(LayerAdjustmentKind.GRADIENT_MAP, gradient = listOf(
                app.inkloom.document.GradientColorStop(0f, 0xFF000000L),
                app.inkloom.document.GradientColorStop(128f / 255f, 0xFFFF0000L),
                app.inkloom.document.GradientColorStop(1f, 0xFFFFFFFFL))) to Color.rgb(237, 0, 0),
            LayerAdjustment(LayerAdjustmentKind.CURVES, redCurve = listOf(
                app.inkloom.document.AdjustmentCurvePoint(0f, 0f), app.inkloom.document.AdjustmentCurvePoint(1f, 0f)),
                redCurveSmooth = true) to Color.rgb(0, 128, 192),
            LayerAdjustment(LayerAdjustmentKind.LEVELS,
                redLevels = app.inkloom.document.AdjustmentLevels(outputWhite = 0f)) to Color.rgb(0, 128, 192),
            LayerAdjustment(LayerAdjustmentKind.INVERT) to Color.rgb(191, 127, 63),
            LayerAdjustment(LayerAdjustmentKind.POSTERIZE, posterizeLevels = 3) to Color.rgb(128, 128, 255),
            LayerAdjustment(LayerAdjustmentKind.THRESHOLD) to Color.BLACK,
            LayerAdjustment(LayerAdjustmentKind.HUE_SATURATION, luminosity = .5f) to Color.rgb(160, 192, 224))
        try {
            for ((adjustment, expected) in cases) {
                val layers = listOf(InkLayer("ink", "Ink", rasterTileSurface = inkRef),
                    InkLayer("adjust", "Adjust", role = LayerRole.ADJUSTMENT, adjustment = adjustment,
                        mask = LayerMask(surface = maskRef)), InkLayer("top", "Top", rasterTileSurface = topRef))
                val page = InkPage(width = 64f, height = 64f, layers = layers)
                val exported = assets.renderPageBitmap(page, 64)
                try {
                    assertEquals("${adjustment.kind} exports real corrected pixels", expected, exported.getPixel(16, 32))
                    assertEquals(sourceColor, exported.getPixel(40, 32))
                    assertEquals(Color.RED, exported.getPixel(48, 32))
                    instrumentation.runOnMainSync {
                        val view = InkCanvasView(instrumentation.targetContext).apply {
                            layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                            setDocumentLayers(layers); setRasterLayerSurfaces(mapOf("ink" to ink, "top" to top))
                            layerOrder = listOf("ink", "top")
                            layerMasks = mapOf("adjust" to requireNotNull(layers[1].mask))
                            layerMaskSurfaces = mapOf("adjust" to mask.snapshot())
                        }
                        val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                        try {
                            view.draw(Canvas(live))
                            for (x in listOf(16, 40, 48)) assertEquals(exported.getPixel(x, 32), live.getPixel(x, 32))
                        } finally { live.recycle() }
                    }
                } finally { exported.recycle() }
            }
        } finally {
            assets.releaseRasterTileReservation(inkRef); assets.releaseRasterTileReservation(topRef)
        }
    }

    @Test fun folderIsolationControlsWhetherAdjustmentReachesLowerOutsideLayers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        val black = PixelTileSurface(64, 64, 64, "outside").also { surface ->
            surface.beginWrite("fixture").run {
                editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(Color.BLACK) }
                commit().close()
            }
        }
        val blackRef = assets.saveRasterTileSurface(black)
        try {
            for (passThrough in listOf(false, true)) {
                val layers = listOf(
                    InkLayer("outside", "Outside", rasterTileSurface = blackRef),
                    InkLayer("folder", "Folder", role = LayerRole.FOLDER, passThrough = passThrough),
                    InkLayer("adjust", "Brightness", role = LayerRole.ADJUSTMENT, parentId = "folder",
                        adjustment = LayerAdjustment(LayerAdjustmentKind.BRIGHTNESS_CONTRAST, brightness = .5f)),
                )
                val page = InkPage(width = 64f, height = 64f, background = PageBackground.Paper(0xFFFFFFFF), layers = layers)
                val exported = assets.renderPageBitmap(page, 64)
                try {
                    val exportRed = Color.red(exported.getPixel(16, 32))
                    if (passThrough) assertTrue(exportRed in 126..129) else assertEquals(0, exportRed)
                    instrumentation.runOnMainSync {
                        val view = InkCanvasView(instrumentation.targetContext).apply {
                            layout(0, 0, 64, 64)
                            pagePaperColor = Color.WHITE
                            setDocumentLayers(layers)
                            setRasterLayerSurfaces(mapOf("outside" to black))
                            layerOrder = listOf("outside")
                        }
                        val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                        try {
                            view.draw(Canvas(live))
                            assertEquals(exported.getPixel(16, 32), live.getPixel(16, 32))
                        } finally { live.recycle() }
                    }
                } finally { exported.recycle() }
            }
        } finally { assets.releaseRasterTileReservation(blackRef) }
    }

    @Test fun maskedAdjustmentAffectsOnlyLayersBelowAndMatchesExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        val black = PixelTileSurface(64, 64, 64, "black").also { surface ->
            surface.beginWrite("fixture").run {
                editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(Color.BLACK) }
                commit().close()
            }
        }
        val red = PixelTileSurface(64, 64, 64, "red").also { surface ->
            surface.beginWrite("fixture").run { setPixel(48, 32, Color.RED); commit().close() }
        }
        val mask = MaskTileSurface(64, 64, 64, initialDefaultAlpha = 255).also { surface ->
            surface.beginWrite("hide right").run {
                for (y in 0 until 64) for (x in 32 until 64) setAlpha(x, y, 0)
                commit().close()
            }
        }
        val blackRef = assets.saveRasterTileSurface(black)
        val redRef = assets.saveRasterTileSurface(red)
        val maskRef = assets.saveMaskTileSurface(mask.snapshot())
        val layers = listOf(
            InkLayer("black", "Black", rasterTileSurface = blackRef),
            InkLayer("adjust", "Brightness", role = LayerRole.ADJUSTMENT,
                adjustment = LayerAdjustment(LayerAdjustmentKind.BRIGHTNESS_CONTRAST, brightness = .5f),
                mask = LayerMask(surface = maskRef)),
            InkLayer("red", "Red", rasterTileSurface = redRef),
        )
        val page = InkPage(width = 64f, height = 64f, background = PageBackground.Paper(0xFFFFFFFF), layers = layers)
        val exported = assets.renderPageBitmap(page, 64)
        try {
            assertTrue(Color.red(exported.getPixel(16, 32)) in 126..129)
            assertEquals(Color.BLACK, exported.getPixel(40, 32))
            assertEquals(Color.RED, exported.getPixel(48, 32))
            instrumentation.runOnMainSync {
                val view = InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 64, 64)
                    pagePaperColor = Color.WHITE
                    setDocumentLayers(layers)
                    setRasterLayerSurfaces(mapOf("black" to black, "red" to red))
                    layerOrder = listOf("black", "red")
                    layerMasks = mapOf("adjust" to requireNotNull(layers[1].mask))
                    layerMaskSurfaces = mapOf("adjust" to mask.snapshot())
                }
                val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(live))
                    for (x in listOf(16, 40, 48)) assertEquals(exported.getPixel(x, 32), live.getPixel(x, 32))
                    view.draw(Canvas(live))
                    assertEquals(0, view.lastRasterProjectionRecomposedTileCount)
                } finally { live.recycle() }
            }
            val raw = assets.renderRasterLayerBitmap(page, "black", 64)
            try { assertEquals(Color.BLACK, raw.getPixel(16, 32)) } finally { raw.recycle() }
        } finally {
            exported.recycle()
            assets.releaseRasterTileReservation(blackRef)
            assets.releaseRasterTileReservation(redRef)
        }
    }
    @Test fun clippedAdjustmentChangesOnlyItsBaseInCanvasAndExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        val gray = Color.rgb(64, 64, 64)
        fun filled(id: String, color: Int, right: Int) = PixelTileSurface(64, 64, 64, id).also { surface ->
            surface.beginWrite("fixture").run {
                editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, stride ->
                    for (y in 0 until 64) for (x in 0 until right) pixels[y * stride + x] = color
                }
                commit().close()
            }
        }
        val below = filled("clip-below", gray, 64)
        val base = filled("clip-base", Color.WHITE, 32)
        val belowRef = assets.saveRasterTileSurface(below)
        val baseRef = assets.saveRasterTileSurface(base)
        try {
            for (clipped in listOf(true, false)) {
                val layers = listOf(
                    InkLayer("below", "Below", rasterTileSurface = belowRef),
                    InkLayer("base", "Base", rasterTileSurface = baseRef),
                    InkLayer("adjust", "Invert", role = LayerRole.ADJUSTMENT, clippedToBelow = clipped,
                        adjustment = LayerAdjustment(LayerAdjustmentKind.INVERT)),
                )
                val page = InkPage(width = 64f, height = 64f, background = PageBackground.Paper(0xFFFFFFFF), layers = layers)
                val exported = assets.renderPageBitmap(page, 64)
                try {
                    assertEquals(Color.BLACK, exported.getPixel(16, 32))
                    assertEquals("clipping keeps the correction away from layers below its base",
                        if (clipped) gray else Color.rgb(191, 191, 191), exported.getPixel(48, 32))
                    instrumentation.runOnMainSync {
                        val view = InkCanvasView(instrumentation.targetContext).apply {
                            layout(0, 0, 64, 64)
                            pagePaperColor = Color.WHITE
                            setDocumentLayers(layers)
                            setRasterLayerSurfaces(mapOf("below" to below, "base" to base))
                            layerOrder = listOf("below", "base")
                            clippedLayers = if (clipped) setOf("adjust") else emptySet()
                        }
                        val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                        try {
                            view.draw(Canvas(live))
                            for (x in listOf(16, 48)) assertEquals(exported.getPixel(x, 32), live.getPixel(x, 32))
                        } finally { live.recycle() }
                    }
                } finally { exported.recycle() }
            }
        } finally {
            assets.releaseRasterTileReservation(belowRef)
            assets.releaseRasterTileReservation(baseRef)
        }
    }

    @Test fun adjustmentsCorrectRetainedObjectsAndClippedObjectLayersInCanvasExportAndComposite() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        val below = InkLayer(id = "below", name = "Below")
        val objects = InkLayer(id = "objects", name = "Objects")
        fun shape(id: String, x: Float, color: Long) = app.inkloom.document.PageElement.Shape(x = x, y = 0f, width = 32f, height = 64f,
            layerId = id, kind = app.inkloom.document.ShapeKind.RECTANGLE, colorArgb = color, fillArgb = color, strokeWidth = 0f)
        val elements = listOf(shape(below.id, .5f, 0xFF404040), shape(objects.id, 0f, 0xFF404040))
        for (clipped in listOf(false, true)) {
            val adjust = InkLayer(id = "adjust", name = "Invert", role = LayerRole.ADJUSTMENT, clippedToBelow = clipped,
                adjustment = LayerAdjustment(LayerAdjustmentKind.INVERT))
            val page = InkPage(width = 64f, height = 64f, background = PageBackground.Paper(0xFFFFFFFF),
                layers = listOf(below, objects, adjust), elements = elements)
            val inverted = Color.rgb(191, 191, 191); val original = Color.rgb(64, 64, 64)
            val exported = assets.renderPageBitmap(page, 64)
            val composite = assets.renderPageCompositeSurface(page, 64, 64)
            try {
                assertEquals("object pixels are corrected (clipped=$clipped)", inverted, exported.getPixel(16, 32))
                assertEquals("objects on other layers follow the clipping scope", if (clipped) original else inverted, exported.getPixel(48, 32))
                assertEquals(exported.getPixel(16, 32), composite.pixelAt(16, 32))
                assertEquals(exported.getPixel(48, 32), composite.pixelAt(48, 32))
                instrumentation.runOnMainSync {
                    val bitmaps = listOf(below, objects).associate { it.id to app.inkloom.ui.LayerElementBitmaps(assets.renderElementLayerBitmap(page, it.id, 64), null) }
                    val view = InkCanvasView(instrumentation.targetContext).apply {
                        layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                        setDocumentLayers(page.layers); layerOrder = listOf(below.id, objects.id)
                        clippedLayers = if (clipped) setOf(adjust.id) else emptySet()
                        setLayerElementBitmaps(bitmaps)
                    }
                    val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(live))
                        for (x in listOf(16, 48)) assertEquals("canvas matches export at $x", exported.getPixel(x, 32), live.getPixel(x, 32))
                    } finally { live.recycle(); bitmaps.values.forEach { it.base?.recycle() } }
                }
            } finally { exported.recycle() }
        }
    }
}
