package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
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
            val target = stroke("target", .5f, "ink").copy(width = 18f)
            val distant = stroke("distant", .75f, "ink").copy(width = 18f)
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(listOf(target, distant), edits).apply {
                selectionShape = SelectionShape.MAGIC_WAND
                selectionThreshold = .2f
            }

            view.dispatchStylus(MotionEvent.ACTION_DOWN, 500f, 500f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 500f, 500f, 20L)
            view.deleteSelection()

            assertEquals(listOf("target"), edits.single().removedIds)
        }
    }

    @Test fun rectangleGestureDefersContentSplittingUntilDelete() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val crossing = InkStroke(
                id = "crossing",
                points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)),
            )
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(listOf(crossing), edits).apply { selectionShape = SelectionShape.RECTANGLE }

            view.rectangle(.4f, .3f, .6f, .7f)

            assertTrue("Creating a rectangle selection must not rewrite the stroke", edits.isEmpty())

            view.deleteSelection()
            assertEquals(listOf("crossing"), edits.single().removedIds)
            assertEquals(2, edits.single().replacements.size)
            assertEquals(.4f, edits.single().replacements.first().points.last().x, .001f)
            assertEquals(.6f, edits.single().replacements.last().points.first().x, .001f)
        }
    }

    @Test fun rectangleGestureDrawsAClosedLivePreview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView(emptyList(), mutableListOf()).apply { selectionShape = SelectionShape.RECTANGLE }
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

    @Test fun freeLassoLivePreviewClosesToItsVisibleOrigin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView(emptyList(), mutableListOf()).apply { selectionShape = SelectionShape.FREEFORM }
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 200f, 200f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 200f, 20L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 800f, 30L)
            val bitmap = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))

                assertTrue("Free lasso did not preview its closing edge", pixelsNear(bitmap, 500, 500, 6).any(::opaque))
                assertTrue("Free lasso origin was not marked", pixelsNear(bitmap, 200, 200, 9).count(::opaque) > 80)
            } finally {
                bitmap.recycle()
                view.dispatchStylus(MotionEvent.ACTION_CANCEL, 800f, 800f, 40L)
            }
        }
    }

    @Test fun committedLassoRendersPersistentHighContrastBoundaryOnEverySide() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView(emptyList(), mutableListOf()).apply { selectionShape = SelectionShape.FREEFORM }
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
                assertTrue("Committed selection boundary was too sparse", boundaryPixels.size > 8_000)
                assertTrue("Committed selection boundary had no black contrast pixels", boundaryPixels.any { Color.red(it) < 24 })
                assertTrue("Committed selection boundary had no white marching-ant pixels", boundaryPixels.any { Color.red(it) > 230 })
                assertEquals("Selection interior must remain unobscured", 0, Color.alpha(bitmap.getPixel(500, 500)))
                assertTrue(
                    "Selection mode must distinguish the protected exterior",
                    pixelsNear(bitmap, 100, 100, 16).any { Color.alpha(it) in 16..80 },
                )
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
                val view = configuredView(emptyList(), mutableListOf()).apply {
                    selectionShape = SelectionShape.FREEFORM
                    viewportScale = scale
                }
                view.lasso(.2f, .2f, .8f, .8f)
                val bitmap = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    val boundary = pixelsNear(bitmap, 200, 500, if (scale < 1f) 12 else 3).filter(::opaque)
                    assertTrue("Selection boundary disappeared at viewport scale $scale", boundary.size > 16)
                    assertTrue(
                        "Protected exterior disappeared at viewport scale $scale",
                        pixelsNear(bitmap, 100, 100, 16).any { Color.alpha(it) in 16..80 },
                    )
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
            val view = configuredView(emptyList(), mutableListOf()).apply { selectionShape = SelectionShape.FREEFORM }
            view.lasso(.2f, .2f, .8f, .8f)
            val bitmap = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            try {
                repeat(2) { bitmap.eraseColor(Color.TRANSPARENT); view.draw(canvas) }
                val elapsed = ArrayList<Long>(10)
                repeat(10) {
                    bitmap.eraseColor(Color.TRANSPARENT)
                    val started = SystemClock.elapsedRealtimeNanos()
                    view.draw(canvas)
                    elapsed += SystemClock.elapsedRealtimeNanos() - started
                }
                val sorted = elapsed.sorted()
                val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
                android.util.Log.i("InkloomPerf", "selection_overlay_1000px_p95_ms=$p95Ms")
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
            val first = stroke("first", .3f, "ink")
            val second = stroke("second", .6f, "ink")
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(listOf(first, second), edits)

            view.selectionMode = SelectionMode.REPLACE
            view.lasso(.2f, .2f, .8f, .4f)
            view.selectionMode = SelectionMode.ADD
            view.lasso(.2f, .5f, .8f, .7f)
            view.selectionMode = SelectionMode.SUBTRACT
            view.lasso(.2f, .2f, .8f, .4f)
            view.selectionMode = SelectionMode.INTERSECT
            view.lasso(.2f, .5f, .8f, .7f)
            view.deleteSelection()

            assertEquals(1, edits.size)
            assertEquals(listOf("second"), edits.single().removedIds)
        }
    }

    @Test fun cancellingSelectionInputPreservesCommittedSelectionAndDisarmsTransientMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = configuredView(emptyList(), mutableListOf()).apply { selectionShape = SelectionShape.FREEFORM }
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
            val target = stroke("target", .5f, "target-layer")
            val other = stroke("other", .5f, "other-layer")
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(listOf(target, other), edits).apply {
                activeLayerId = "target-layer"
                selectionFilter = SelectionFilter.ACTIVE_LAYER
            }

            view.lasso(.2f, .3f, .8f, .7f)
            view.deleteSelection()

            assertEquals(listOf("target"), edits.single().removedIds)
        }
    }

    private fun configuredView(strokes: List<InkStroke>, edits: MutableList<CapturedEdit>): InkCanvasView {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return InkCanvasView(context).apply {
            layout(0, 0, 1_000, 1_000)
            tool = EditorTool.SELECT
            setDocumentStrokes(strokes, "selection-modes")
            onStrokesReplaced = { removed, replacements, indices -> edits += CapturedEdit(removed, replacements, indices) }
        }
    }

    private fun stroke(id: String, y: Float, layer: String) = InkStroke(id = id, layerId = layer, brushId = "brush", colorArgb = 0xFF111111, points = listOf(StrokePoint(.35f, y), StrokePoint(.65f, y)))

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

    private data class CapturedEdit(val removedIds: List<String>, val replacements: List<InkStroke>, val indices: List<Int>)
}
