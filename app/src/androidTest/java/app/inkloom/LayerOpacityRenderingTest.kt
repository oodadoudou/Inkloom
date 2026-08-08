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
import app.inkloom.document.LayerBlendMode
import app.inkloom.document.LayerRole
import app.inkloom.document.PageBackground
import app.inkloom.document.PageElement
import app.inkloom.document.ShapeKind
import app.inkloom.document.StrokePoint
import app.inkloom.document.defaultInkLayers
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.LayerElementBitmaps
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

    @Test
    fun opacityIsAppliedOnceToTheCompositedLayerInsteadOfEveryOverlappingStroke() {
        val layer = translucentLayer.copy(opacity = .5f)
        val first = stroke.copy(id = "first", points = listOf(StrokePoint(.1f, .5f), StrokePoint(.6f, .5f)))
        val second = stroke.copy(id = "second", points = listOf(StrokePoint(.4f, .5f), StrokePoint(.9f, .5f)))
        val page = InkPage(width = 400f, height = 400f, layers = defaultInkLayers() + layer, strokes = listOf(first, second))

        val bitmap = DocumentAssetService(ApplicationProvider.getApplicationContext()).renderPageBitmap(page, 400)
        try {
            val singleStrokeRed = Color.red(bitmap.getPixel(100, 200))
            val overlapRed = Color.red(bitmap.getPixel(200, 200))
            assertTrue("Layer opacity changed at overlap: single=$singleStrokeRed overlap=$overlapRed", kotlin.math.abs(singleStrokeRed - overlapRed) <= 2)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun multiplyAndScreenBlendAgainstLowerLayerInExport() {
        val lower = InkLayer(id = "lower", name = "Lower", role = LayerRole.CUSTOM)
        val upper = InkLayer(id = "upper", name = "Upper", role = LayerRole.CUSTOM, blendMode = LayerBlendMode.MULTIPLY)
        val red = stroke.copy(id = "red", layerId = lower.id, colorArgb = 0xFFFF0000, points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)))
        val blue = stroke.copy(id = "blue", layerId = upper.id, colorArgb = 0xFF0000FF, points = listOf(StrokePoint(.5f, .1f), StrokePoint(.5f, .9f)))
        val service = DocumentAssetService(ApplicationProvider.getApplicationContext())

        val multiplied = service.renderPageBitmap(InkPage(width = 400f, height = 400f, layers = defaultInkLayers() + lower + upper, strokes = listOf(red, blue)), 400)
        val screened = service.renderPageBitmap(InkPage(width = 400f, height = 400f, layers = defaultInkLayers() + lower + upper.copy(blendMode = LayerBlendMode.SCREEN), strokes = listOf(red, blue)), 400)
        try {
            val multiplyCrossing = multiplied.getPixel(200, 200)
            assertTrue(Color.red(multiplyCrossing) < 12 && Color.green(multiplyCrossing) < 12 && Color.blue(multiplyCrossing) < 12)
            val screenCrossing = screened.getPixel(200, 200)
            assertTrue(Color.red(screenCrossing) > 245 && Color.green(screenCrossing) < 12 && Color.blue(screenCrossing) > 245)
        } finally {
            multiplied.recycle()
            screened.recycle()
        }
    }

    @Test
    fun liveCanvasUsesTheSameMultiplyComposition() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val lower = InkLayer(id = "lower", name = "Lower", role = LayerRole.CUSTOM)
            val upper = InkLayer(id = "upper", name = "Upper", role = LayerRole.CUSTOM, blendMode = LayerBlendMode.MULTIPLY)
            val red = stroke.copy(id = "red-live", layerId = lower.id, colorArgb = 0xFFFF0000, points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)))
            val blue = stroke.copy(id = "blue-live", layerId = upper.id, colorArgb = 0xFF0000FF, points = listOf(StrokePoint(.5f, .1f), StrokePoint(.5f, .9f)))
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 400, 400)
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(red, blue), "layer-multiply")
                layerBlendModes = mapOf(lower.id to lower.blendMode, upper.id to upper.blendMode)
            }
            val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val crossing = bitmap.getPixel(200, 200)
                assertTrue(Color.red(crossing) < 12 && Color.green(crossing) < 12 && Color.blue(crossing) < 12)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun clippedStrokeLayerUsesTheLowerLayerAlphaInsteadOfThePaper() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val lower = InkLayer(id = "lower", name = "Lower")
            val upper = InkLayer(id = "upper", name = "Upper", clippedToBelow = true)
            val horizontal = stroke.copy(id = "base", layerId = lower.id, width = 100f, points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)))
            val vertical = stroke.copy(id = "clip", layerId = upper.id, width = 100f, colorArgb = 0xFFFF0000, points = listOf(StrokePoint(.5f, .1f), StrokePoint(.5f, .9f)))
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 400, 400)
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(horizontal, vertical), "clip-live")
                layerOrder = listOf(lower.id, upper.id)
                clippedLayers = setOf(upper.id)
            }
            val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertTrue("Upper content away from lower alpha must be hidden", Color.red(bitmap.getPixel(200, 100)) > 245 && Color.green(bitmap.getPixel(200, 100)) > 245)
            assertTrue("Upper content at the lower-layer crossing must remain", Color.red(bitmap.getPixel(200, 200)) > 220 && Color.green(bitmap.getPixel(200, 200)) < 40)
            bitmap.recycle()
        }
    }

    @Test
    fun alphaLockPreservesAllCapturedHistoryAndClipsOnlyNewContent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val layer = InkLayer(id = "locked", name = "Locked", alphaLock = true, alphaLockBaseContentIds = listOf("left", "right"))
            val left = stroke.copy(id = "left", layerId = layer.id, width = 80f, points = listOf(StrokePoint(.25f, .2f), StrokePoint(.25f, .8f)))
            val right = stroke.copy(id = "right", layerId = layer.id, width = 80f, points = listOf(StrokePoint(.75f, .2f), StrokePoint(.75f, .8f)))
            val added = stroke.copy(id = "added", layerId = layer.id, width = 80f, colorArgb = 0xFFFF0000, points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)))
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 400, 400)
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(left, right, added), "alpha-lock-live")
                layerOrder = listOf(layer.id)
                alphaLockedLayers = setOf(layer.id)
                alphaLockBaseContentIds = mapOf(layer.id to layer.alphaLockBaseContentIds.toSet())
            }
            val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertTrue("The second captured stroke must remain visible", Color.red(bitmap.getPixel(300, 120)) < 30)
            assertTrue("New paint outside the captured alpha must be absent", Color.red(bitmap.getPixel(200, 200)) > 245)
            assertTrue("New paint over captured alpha must be visible", Color.red(bitmap.getPixel(300, 200)) > 220 && Color.green(bitmap.getPixel(300, 200)) < 40)
            bitmap.recycle()
        }
    }

    @Test
    fun elementBitmapsParticipateInLiveClipping() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val lower = InkLayer(id = "lower-element-mask", name = "Lower")
            val upper = InkLayer(id = "upper-element", name = "Upper", clippedToBelow = true)
            val horizontal = stroke.copy(id = "element-mask", layerId = lower.id, width = 100f, points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)))
            val elementBitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 400, 400)
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(horizontal), "element-clip-live")
                setLayerElementBitmaps(mapOf(upper.id to LayerElementBitmaps(elementBitmap, null)))
                layerOrder = listOf(lower.id, upper.id)
                clippedLayers = setOf(upper.id)
            }
            val output = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(output))
            assertTrue(Color.red(output.getPixel(200, 100)) > 245 && Color.green(output.getPixel(200, 100)) > 245)
            assertTrue(Color.red(output.getPixel(200, 200)) > 220 && Color.green(output.getPixel(200, 200)) < 40)
            output.recycle(); elementBitmap.recycle()
        }
    }

    @Test
    fun exportClipsPageElementsToTheLowerLayer() {
        val lower = InkLayer(id = "lower-export", name = "Lower")
        val upper = InkLayer(id = "upper-export", name = "Upper", clippedToBelow = true)
        val horizontal = stroke.copy(id = "export-mask", layerId = lower.id, width = 100f, points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)))
        val shape = PageElement.Shape(id = "shape", x = .4f, y = .1f, width = 80f, height = 320f, layerId = upper.id, kind = ShapeKind.RECTANGLE, colorArgb = 0xFFFF0000, fillArgb = 0xFFFF0000)
        val page = InkPage(width = 400f, height = 400f, background = PageBackground.Paper(), layers = defaultInkLayers() + lower + upper, strokes = listOf(horizontal), elements = listOf(shape))
        val bitmap = DocumentAssetService(ApplicationProvider.getApplicationContext()).renderPageBitmap(page, 400)
        assertTrue("Exported element outside the lower alpha must be hidden", Color.green(bitmap.getPixel(200, 100)) > 245)
        assertTrue("Exported element crossing the lower alpha must remain", Color.red(bitmap.getPixel(200, 200)) > 220 && Color.green(bitmap.getPixel(200, 200)) < 40)
        bitmap.recycle()
    }

    @Test
    fun exportAlphaLockKeepsCapturedStrokesAndClipsOnlyAdditions() {
        val layer = InkLayer(id = "export-alpha", name = "Alpha", alphaLock = true, alphaLockBaseContentIds = listOf("left-export", "right-export"))
        val left = stroke.copy(id = "left-export", layerId = layer.id, width = 80f, points = listOf(StrokePoint(.25f, .2f), StrokePoint(.25f, .8f)))
        val right = stroke.copy(id = "right-export", layerId = layer.id, width = 80f, points = listOf(StrokePoint(.75f, .2f), StrokePoint(.75f, .8f)))
        val added = stroke.copy(id = "added-export", layerId = layer.id, width = 80f, colorArgb = 0xFFFF0000, points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)))
        val page = InkPage(width = 400f, height = 400f, background = PageBackground.Paper(), layers = defaultInkLayers() + layer, strokes = listOf(left, right, added))
        val bitmap = DocumentAssetService(ApplicationProvider.getApplicationContext()).renderPageBitmap(page, 400)
        assertTrue("Captured history must remain intact in export", Color.red(bitmap.getPixel(300, 120)) < 30)
        assertTrue("Added color outside captured alpha must be hidden in export", Color.green(bitmap.getPixel(200, 200)) > 245)
        assertTrue("Added color over captured alpha must remain in export", Color.red(bitmap.getPixel(300, 200)) > 220 && Color.green(bitmap.getPixel(300, 200)) < 40)
        bitmap.recycle()
    }
}
