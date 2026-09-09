package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.document.LayerIds
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.settings.SelectionFilter
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.AppSettingsStore
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LassoSelectionModesTest {
    @Test fun selectionModeAndFilterPersistAcrossSettingsReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppSettingsStore(context)
        val original = store.load()
        try {
            store.save(original.copy(selectionShape = SelectionShape.RECTANGLE, selectionThreshold = .63f, selectionMode = SelectionMode.SUBTRACT, selectionFilter = SelectionFilter.CURRENT_BRUSH))
            val restored = store.load()
            assertEquals(SelectionShape.RECTANGLE, restored.selectionShape)
            assertEquals(.63f, restored.selectionThreshold, .001f)
            assertEquals(SelectionMode.SUBTRACT, restored.selectionMode)
            assertEquals(SelectionFilter.CURRENT_BRUSH, restored.selectionFilter)
        } finally {
            store.save(original)
        }
    }

    @Test fun magicWandTapSelectsConnectedRenderedInk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val surface = PixelTileSurface(1_000, 1_000).also { pixels ->
                pixels.beginWrite("magic wand connected islands").use { transaction ->
                    for (y in 490..510) for (x in 350..650) transaction.setPixel(x, y, Color.BLACK)
                    for (y in 740..760) for (x in 350..650) transaction.setPixel(x, y, Color.BLACK)
                    transaction.commit().close()
                }
            }
            val selection = DocumentSelectionRuntime().stateFor("document", "page", 1_000, 1_000)
            val view = configuredView().apply {
                pageModelWidth = 1_000f
                pageModelHeight = 1_000f
                pagePaperColor = Color.WHITE
                activeLayerId = LayerIds.INK
                layerOrder = listOf(LayerIds.INK)
                setRasterLayerSurfaces(mapOf(LayerIds.INK to surface))
                bindDocumentSelectionState(selection)
                onRasterLayerCommitted = { }
                selectionShape = SelectionShape.MAGIC_WAND
                selectionThreshold = .2f
            }
            val rendered = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(rendered))
            assertTrue("V5 selection source must include active raster pixels", Color.alpha(rendered.getPixel(500, 500)) > 0)
            assertTrue("V5 selection source must preserve the authored black island", Color.red(rendered.getPixel(500, 500)) < 16)
            rendered.recycle()

            view.dispatchStylus(MotionEvent.ACTION_DOWN, 500f, 500f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 500f, 500f, 20L)
            val selected = requireNotNull(view.selectionMaskSurfaceSnapshot())
            val targetAlpha = (492..508).maxOf { y -> (492..508).maxOf { x -> selected.alphaAt(x, y) } }
            assertTrue(
                "Magic wand mask must contain the touched island; bounds=${selected.nonZeroBounds()}",
                targetAlpha > 0,
            )
            assertEquals("Magic wand mask must exclude the disconnected island", 0, selected.alphaAt(500, 750))
            view.deleteSelection()

            assertEquals(0, Color.alpha(surface.pixelAt(500, 500)))
            assertEquals(255, Color.alpha(surface.pixelAt(500, 750)))
        }
    }

    @Test fun rectangleGestureDeletesOnlySelectedRasterPixels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val surface = PixelTileSurface(1_000, 1_000).also { pixels ->
                pixels.beginWrite("crossing raster line").run {
                    for (x in 100..900) setPixel(x, 500, Color.BLACK)
                    commit().close()
                }
            }
            val view = configuredView().apply {
                selectionShape = SelectionShape.RECTANGLE
                activeLayerId = LayerIds.INK
                setRasterLayerSurfaces(mapOf(LayerIds.INK to surface))
                onRasterLayerCommitted = { }
            }

            view.rectangle(.4f, .3f, .6f, .7f)
            assertEquals(Color.BLACK, surface.pixelAt(500, 500))
            view.deleteSelection()
            assertEquals(Color.TRANSPARENT, surface.pixelAt(500, 500))
            assertEquals(Color.BLACK, surface.pixelAt(300, 500))
            assertEquals(Color.BLACK, surface.pixelAt(700, 500))
        }
    }

    @Test fun rectangleGestureDrawsAClosedLivePreview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView().apply { selectionShape = SelectionShape.RECTANGLE }
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 200f, 250f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 750f, 20L)
            val bitmap = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))

                val topBorderPixels = (190..810).count { x -> bitmap.getPixel(x, 250) ushr 24 != 0 }
                assertTrue("Rectangle preview top edge was not rendered", topBorderPixels > 500)
                assertTrue("Rectangle preview did not show the region being selected", Color.alpha(bitmap.getPixel(500, 500)) in 16..64)
            } finally {
                bitmap.recycle()
                view.dispatchStylus(MotionEvent.ACTION_CANCEL, 800f, 750f, 30L)
            }
        }
    }

    @Test fun freeLassoLivePreviewKeepsOpenSegmentsUntilExplicitClosure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView().apply { selectionShape = SelectionShape.FREEFORM }
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 200f, 200f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 200f, 20L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 800f, 30L)
            val bitmap = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))

                assertTrue("An unfinished freehand contour must not invent a closing edge", pixelsNear(bitmap, 500, 500, 6).none(::opaque))
                val originPixels = pixelsNear(bitmap, 200, 200, 6).count(::opaque)
                assertTrue("Free lasso origin was not marked", originPixels >= 8)
                assertTrue("Free lasso origin must stay subtle: $originPixels pixels", originPixels <= 72)
            } finally {
                bitmap.recycle()
                view.dispatchStylus(MotionEvent.ACTION_CANCEL, 800f, 800f, 40L)
            }
        }
    }

    @Test fun committedLassoRendersPersistentHighContrastBoundaryOnEverySide() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView().apply { selectionShape = SelectionShape.FREEFORM }
            view.lasso(.2f, .2f, .8f, .8f)
            val bitmap = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))

                val boundaryPixels = buildList {
                    for (position in 190..810) {
                        addAll(pixelsNear(bitmap, position, 200, 3))
                        addAll(pixelsNear(bitmap, position, 800, 3))
                        addAll(pixelsNear(bitmap, 200, position, 3))
                        addAll(pixelsNear(bitmap, 800, position, 3))
                    }
                }.filter(::opaque)
                assertTrue("Committed selection boundary was too sparse", boundaryPixels.size > 2_000)
                assertTrue("Committed selection boundary had no black contrast pixels", boundaryPixels.any { Color.red(it) < 24 })
                assertTrue(
                    "Committed selection boundary had no white marching-ant pixels; reds=${boundaryPixels.map(Color::red).distinct().sorted()}",
                    boundaryPixels.any { Color.red(it) > 200 },
                )
                assertEquals("Selection interior must remain unobscured", 0, Color.alpha(bitmap.getPixel(500, 500)))
                assertEquals("Committed selection must not hatch the protected exterior", 0, Color.alpha(bitmap.getPixel(100, 100)))
                val horizontalThickness = (190..210).count { x -> Color.alpha(bitmap.getPixel(x, 500)) > 0 }
                assertTrue("Committed selection boundary must be a fine 1-3 px line: $horizontalThickness", horizontalThickness in 1..3)
            } finally {
                bitmap.recycle()
                view.clearSelection()
            }
        }
    }

    @Test fun committedLassoFeedbackStaysVisibleAtExtremeViewportScales() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            listOf(.25f, 12f).forEach { scale ->
                val view = configuredView().apply {
                    selectionShape = SelectionShape.FREEFORM
                }
                view.lasso(.2f, .2f, .8f, .8f)
                view.viewportScale = scale
                val bitmap = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    val boundary = pixelsNear(bitmap, 200, 500, if (scale < 1f) 12 else 3).filter(::opaque)
                    assertTrue(
                        "Selection boundary must stay fine and visible at viewport scale $scale: ${boundary.size}",
                        boundary.size in 4..75,
                    )
                    assertEquals("Selection exterior must remain unobscured at viewport scale $scale", 0, Color.alpha(bitmap.getPixel(100, 100)))
                    assertEquals("Selection interior was obscured at viewport scale $scale", 0, Color.alpha(bitmap.getPixel(500, 500)))
                } finally {
                    bitmap.recycle()
                    view.clearSelection()
                }
            }
        }
    }

    @Test fun committedSelectionOverlayStaysWithinInteractiveFrameBudget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView().apply { selectionShape = SelectionShape.FREEFORM; selectionMaskVisibility = .25f }
            view.lasso(.2f, .2f, .8f, .8f)
            val bitmap = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            try {
                repeat(4) { bitmap.eraseColor(Color.TRANSPARENT); view.draw(canvas) }
                assertTrue("The protected exterior must show the configured hatch", pixelsNear(bitmap, 100, 100, 8).any(::opaque))
                assertEquals("The selected interior must remain clear", 0, Color.alpha(bitmap.getPixel(500, 500)))
                val elapsed = ArrayList<Long>(40)
                repeat(40) {
                    bitmap.eraseColor(Color.TRANSPARENT)
                    val started = SystemClock.elapsedRealtimeNanos()
                    view.draw(canvas)
                    elapsed += SystemClock.elapsedRealtimeNanos() - started
                }
                val sorted = elapsed.sorted()
                val nearestRankP95 = ((sorted.size * 95 + 99) / 100 - 1).coerceIn(0, sorted.lastIndex)
                val p95Ms = sorted[nearestRankP95] / 1_000_000f
                val worstMs = sorted.last() / 1_000_000f
                android.util.Log.i(
                    "InkloomPerf",
                    "selection_overlay_1000px_p95_ms=$p95Ms worst_ms=$worstMs samples=${sorted.size}",
                )
                assertTrue("Selection overlay P95 was ${p95Ms}ms", p95Ms < 33.3f)
            } finally {
                bitmap.recycle()
                view.clearSelection()
            }
        }
    }

    @Test fun replaceAddSubtractAndIntersectComposePredictably() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView()

            view.selectionMode = SelectionMode.REPLACE
            view.lasso(.2f, .2f, .8f, .4f)
            view.selectionMode = SelectionMode.ADD
            view.lasso(.2f, .5f, .8f, .7f)
            view.selectionMode = SelectionMode.SUBTRACT
            view.lasso(.2f, .2f, .8f, .4f)
            view.selectionMode = SelectionMode.INTERSECT
            view.lasso(.2f, .5f, .8f, .7f)
            val mask = requireNotNull(view.selectionMaskSurfaceSnapshot())
            assertEquals(0, mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight * 3 / 10))
            assertTrue(mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight * 6 / 10) > 0)
        }
    }

    @Test fun cancellingSelectionInputPreservesCommittedSelectionAndDisarmsTransientMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView().apply { selectionShape = SelectionShape.FREEFORM }
            view.lasso(.2f, .2f, .8f, .8f)
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 50f, 50f, 70L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 150f, 50f, 80L)

            view.cancelSelectionInput()

            assertTrue("Cancelling transient lasso must retain the committed alpha selection", view.hasActiveSelection())
            view.tool = EditorTool.PEN
            val bitmap = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                assertEquals("Cancelled transient lasso must not remain visible", 0, Color.alpha(bitmap.getPixel(100, 50)))
                assertEquals("Committed selection interior must remain clear", 0, Color.alpha(bitmap.getPixel(500, 500)))
            } finally {
                bitmap.recycle()
                view.clearSelection()
            }
        }
    }

    @Test fun activeLayerFilterLeavesOverlappingOtherLayerInkUntouched() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val target = PixelTileSurface(1_000, 1_000)
            val other = PixelTileSurface(1_000, 1_000)
            listOf(target, other).forEach { surface ->
                surface.beginWrite("overlapping layer fixture").run {
                    setPixel(500, 500, Color.BLACK)
                    commit().close()
                }
            }
            val view = configuredView().apply {
                activeLayerId = "target-layer"
                selectionFilter = SelectionFilter.ACTIVE_LAYER
                setRasterLayerSurfaces(mapOf("target-layer" to target, "other-layer" to other))
                onRasterLayerCommitted = { }
            }

            view.lasso(.2f, .3f, .8f, .7f)
            view.deleteSelection()

            assertEquals(Color.TRANSPARENT, target.pixelAt(500, 500))
            assertEquals(Color.BLACK, other.pixelAt(500, 500))
        }
    }

    private fun configuredView(): InkCanvasView {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return InkCanvasView(context).apply {
            layout(0, 0, 1_000, 1_000)
            selectionMaskVisibility = 0f // Boundary-specific assertions isolate the contour from the optional exterior overlay.
            tool = EditorTool.SELECT
        }
    }

    private fun InkCanvasView.lasso(left: Float, top: Float, right: Float, bottom: Float) {
        dispatchStylus(MotionEvent.ACTION_DOWN, left * 1_000f, top * 1_000f, 10L)
        dispatchStylus(MotionEvent.ACTION_MOVE, right * 1_000f, top * 1_000f, 20L)
        dispatchStylus(MotionEvent.ACTION_MOVE, right * 1_000f, bottom * 1_000f, 30L)
        dispatchStylus(MotionEvent.ACTION_MOVE, left * 1_000f, bottom * 1_000f, 40L)
        dispatchStylus(MotionEvent.ACTION_UP, left * 1_000f, top * 1_000f, 50L)
    }

    private fun InkCanvasView.rectangle(left: Float, top: Float, right: Float, bottom: Float) {
        dispatchStylus(MotionEvent.ACTION_DOWN, left * 1_000f, top * 1_000f, 10L)
        dispatchStylus(MotionEvent.ACTION_MOVE, right * 1_000f, bottom * 1_000f, 20L)
        dispatchStylus(MotionEvent.ACTION_UP, right * 1_000f, bottom * 1_000f, 30L)
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .01f; touchMajor = 1f }
        MotionEvent.obtain(10L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0).also { event -> try { onTouchEvent(event) } finally { event.recycle() } }
    }

    private fun pixelsNear(bitmap: Bitmap, centerX: Int, centerY: Int, radius: Int): List<Int> = buildList {
        for (y in centerY - radius..centerY + radius) for (x in centerX - radius..centerX + radius) {
            if (x in 0 until bitmap.width && y in 0 until bitmap.height) add(bitmap.getPixel(x, y))
        }
    }

    private fun opaque(color: Int): Boolean = Color.alpha(color) != 0

}
