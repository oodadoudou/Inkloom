package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokeMaskDab
import app.inkloom.document.StrokePoint
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import app.inkloom.engine.v3.scene.SparseAlphaMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasLassoTest {
    @Test fun sparseAlphaPlaneRetainsOnlyNonEmptySelectionTiles() {
        val bitmap = Bitmap.createBitmap(2_048, 2_048, Bitmap.Config.ARGB_8888)
        try {
            android.graphics.Canvas(bitmap).drawRect(1_020f, 1_020f, 1_040f, 1_040f, android.graphics.Paint().apply { color = Color.WHITE })
            val mask = requireNotNull(SparseAlphaMask.fromBitmap(bitmap))
            try {
                assertEquals(4, mask.tileCount)
                assertTrue(mask.retainedPixels < bitmap.width.toLong() * bitmap.height / 8L)
                val restored = mask.snapshot()
                try {
                    assertEquals(Color.WHITE, restored.getPixel(1_030, 1_030))
                    assertEquals(Color.TRANSPARENT, restored.getPixel(100, 100))
                } finally {
                    restored.recycle()
                }
            } finally {
                mask.close()
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun lassoExposesItsExactContourForLayerMaskCommands() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = configuredView(horizontalStroke("mask-source"), mutableListOf())

            view.drawSquareLasso()

            val contour = view.selectionMaskRegions().single()
            assertEquals(.2f, contour.minOf(StrokePoint::x), .001f)
            assertEquals(.8f, contour.maxOf(StrokePoint::x), .001f)
            assertEquals(.2f, contour.minOf(StrokePoint::y), .001f)
            assertEquals(.8f, contour.maxOf(StrokePoint::y), .001f)
        }
    }

    @Test fun selectionCanExpandShrinkFeatherDeselectAndReselect() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = configuredView(horizontalStroke("modify-source"), mutableListOf())
            view.drawSquareLasso()
            val originalMinX = view.selectionMaskRegions().single().minOf(StrokePoint::x)

            assertTrue(view.expandSelection(80f))
            val expandedMinX = view.selectionMaskRegions().single().minOf(StrokePoint::x)
            assertTrue(expandedMinX < originalMinX)
            assertTrue(view.shrinkSelection(40f))
            assertTrue(view.selectionMaskRegions().single().minOf(StrokePoint::x) > expandedMinX)
            assertTrue(view.featherSelection(24f))
            assertEquals(24f, view.selectionFeatherPixels, .001f)
            assertTrue(view.clearSelection())
            assertTrue(view.reselect())
            assertEquals(24f, view.selectionFeatherPixels, .001f)
        }
    }

    @Test fun geometryOnlySelectionCanFeatherDeselectReselectAndDoesNotLeakAcrossPages() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val outside = InkStroke(id = "outside", points = listOf(StrokePoint(.02f, .02f), StrokePoint(.08f, .08f)))
            val view = configuredView(outside, mutableListOf())

            view.drawSquareLasso()

            assertTrue(view.featherSelection(18f))
            assertTrue(view.clearSelection())
            assertTrue(view.reselect())
            assertEquals(18f, view.selectionFeatherPixels, .001f)

            view.setDocumentStrokes(listOf(outside), "document:other-page")
            assertTrue(!view.clearSelection())
        }
    }

    @Test fun selectionActiveCallbackTracksCommittedDeselectReselectAndPageChanges() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = configuredView(horizontalStroke("state-source"), mutableListOf())
            val states = mutableListOf<Boolean>()
            view.onSelectionActiveChanged = { states += it }

            assertEquals(listOf(false), states)
            view.drawSquareLasso()
            assertTrue(view.hasActiveSelection())
            assertEquals(true, states.last())
            assertTrue(view.clearSelection())
            assertEquals(false, states.last())
            assertTrue(view.reselect())
            assertEquals(true, states.last())
            view.setDocumentStrokes(listOf(horizontalStroke("next-page")), "document:next-page")
            assertEquals(false, states.last())
        }
    }

    @Test fun cancellingAReplacementLassoPreservesTheCommittedSelection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = configuredView(horizontalStroke("cancel-source"), mutableListOf())
            view.drawSquareLasso()
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 20f, 20f, 60L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 100f, 20f, 70L)
            view.dispatchStylus(MotionEvent.ACTION_CANCEL, 100f, 100f, 80L)

            var selection: Bitmap? = null
            view.onFillCommitted = { mask, _, _, _, _, _, _ -> selection = mask }
            assertTrue(view.fillSelection())
            val mask = requireNotNull(selection)
            try {
                assertTrue(Color.alpha(mask.getPixel(mask.width / 2, mask.height / 2)) > 0)
                assertEquals(0, Color.alpha(mask.getPixel(mask.width / 20, mask.height / 20)))
            } finally {
                mask.recycle()
            }
        }
    }

    @Test fun borderSelectionKeepsOnlyContentCrossingTheSelectionEdge() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = configuredView(horizontalStroke("border-source"), mutableListOf())
            view.drawSquareLasso()

            assertTrue(view.borderSelection(80f))
            assertTrue(view.selectionMaskRegions().isNotEmpty())
            var mask: Bitmap? = null
            view.onGradientCommitted = { _, _, _, _, _, value -> mask = value }
            view.tool = EditorTool.GRADIENT
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 100f, 500f, 60L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 900f, 500f, 70L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 900f, 500f, 80L)
            val committed = requireNotNull(mask)
            try {
                assertTrue(Color.alpha(committed.getPixel(committed.width / 5, committed.height / 2)) >= 128)
                assertEquals(0, Color.alpha(committed.getPixel(committed.width / 2, committed.height / 2)))
            } finally {
                committed.recycle()
            }
        }
    }

    @Test fun draggingASelectionMovesItsMasksWithItsPoints() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val original = InkStroke(
                id = "masked-drag",
                points = listOf(StrokePoint(.3f, .5f), StrokePoint(.7f, .5f)),
                masks = listOf(StrokeMaskDab(.4f, .5f, .04f, .02f)),
            )
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(original, edits)
            view.drawSquareLasso()
            assertTrue(view.enterFreeTransform())

            view.dispatchStylus(MotionEvent.ACTION_DOWN, 500f, 500f, 60L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 600f, 600f, 70L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 600f, 600f, 80L)

            val moved = edits.last().replacements.single()
            assertEquals(.5f, moved.masks.single().x, .0001f)
            assertEquals(.6f, moved.masks.single().y, .0001f)
            assertEquals(.4f, moved.points.first().x, .0001f)
            assertEquals(.6f, moved.points.first().y, .0001f)
        }
    }

    @Test fun selectionScaleRotateAndCopyKeepMaskGeometryAttached() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val original = InkStroke(
                id = "masked",
                points = listOf(StrokePoint(.3f, .5f), StrokePoint(.7f, .5f)),
                masks = listOf(StrokeMaskDab(.4f, .5f, .04f, .02f, 15f)),
            )
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(original, edits)
            view.drawSquareLasso()

            view.scaleSelection(2f)
            val scaled = edits.last().replacements.single()
            assertEquals(.3f, scaled.masks.single().x, .0001f)
            assertEquals(.5f, scaled.masks.single().y, .0001f)
            assertEquals(.08f, scaled.masks.single().radiusX, .0001f)
            assertEquals(.04f, scaled.masks.single().radiusY, .0001f)

            view.rotateSelection(90f)
            val rotated = edits.last().replacements.single()
            assertEquals(.5f, rotated.masks.single().x, .0001f)
            assertEquals(.3585948f, rotated.masks.single().y, .0001f)
            assertEquals(105f, rotated.masks.single().rotationDegrees, .0001f)

            view.copySelection()
            val copied = edits.last().replacements.single()
            assertEquals(.53f, copied.masks.single().x, .0001f)
            assertEquals(.3885948f, copied.masks.single().y, .0001f)
            assertEquals(rotated.masks.single().radiusX, copied.masks.single().radiusX, .0001f)
            assertEquals(rotated.masks.single().rotationDegrees, copied.masks.single().rotationDegrees, .0001f)
        }
    }

    @Test fun partialLassoSplitsAndDeletesOnlyTheEnclosedSegment() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val original = horizontalStroke("original")
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(original, edits)

            view.drawSquareLasso()

            assertEquals(1, edits.size)
            assertEquals(listOf("original"), edits[0].removedIds)
            assertEquals(3, edits[0].replacements.size)
            assertEquals(listOf(0, 1, 2), edits[0].indices)
            val enclosed = edits[0].replacements[1]
            assertEquals(.2f, enclosed.points.first().x, .001f)
            assertEquals(.8f, enclosed.points.last().x, .001f)

            view.deleteSelection()

            assertEquals(2, edits.size)
            assertEquals(listOf(enclosed.id), edits[1].removedIds)
            assertTrue(edits[1].replacements.isEmpty())

            view.undo()
            assertEquals(3, edits.size)
            assertEquals(listOf(enclosed), edits[2].replacements)
            assertEquals(listOf(1), edits[2].indices)

            view.undo()
            assertEquals(4, edits.size)
            assertEquals(edits[0].replacements.map(InkStroke::id), edits[3].removedIds)
            assertEquals(listOf(original), edits[3].replacements)
        }
    }

    @Test fun partialLassoNeverSplitsOrSelectsAProtectedStroke() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val original = horizontalStroke("locked")
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(original, edits)
            view.setProtectedStrokeIds(setOf(original.id))

            view.drawSquareLasso()
            view.deleteSelection()

            assertTrue(edits.isEmpty())
        }
    }

    private fun configuredView(stroke: InkStroke, edits: MutableList<CapturedEdit>): InkCanvasView {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return InkCanvasView(context).apply {
            layout(0, 0, 1_000, 1_000)
            tool = EditorTool.SELECT
            setDocumentStrokes(listOf(stroke), "document:page")
            onStrokesReplaced = { removed, replacements, indices -> edits += CapturedEdit(removed, replacements, indices) }
        }
    }

    private fun horizontalStroke(id: String) = InkStroke(
        id = id,
        width = 5f,
        points = listOf(StrokePoint(.1f, .5f, pressure = .2f, timeMs = 10L), StrokePoint(.9f, .5f, pressure = .9f, timeMs = 90L)),
    )

    private fun InkCanvasView.drawSquareLasso() {
        dispatchStylus(MotionEvent.ACTION_DOWN, 200f, 200f, 10L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 200f, 20L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 800f, 30L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 200f, 800f, 40L)
        dispatchStylus(MotionEvent.ACTION_UP, 200f, 200f, 50L)
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .01f; touchMajor = 1f }
        MotionEvent.obtain(10L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0).also { event ->
            try { onTouchEvent(event) } finally { event.recycle() }
        }
    }

    private data class CapturedEdit(
        val removedIds: List<String>,
        val replacements: List<InkStroke>,
        val indices: List<Int>,
    )
}
