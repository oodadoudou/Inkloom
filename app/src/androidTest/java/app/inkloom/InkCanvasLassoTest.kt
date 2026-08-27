package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokeMaskDab
import app.inkloom.document.StrokePoint
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import app.inkloom.engine.v5.selection.SparseAlphaMask
import app.inkloom.engine.v5.selection.AlphaMaskOperation
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.document.SelectionMaskCompositor
import app.inkloom.document.LayerIds
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasLassoTest {
    @Test fun productionViewPublishesAndClearsSelectionThroughDocumentRuntime() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = configuredView(horizontalStroke("runtime-source"), mutableListOf())
            val size = requireNotNull(SelectionMaskCompositor.maskSize(view.pageModelWidth, view.pageModelHeight))
            val runtime = DocumentSelectionRuntime()
            val state = runtime.stateFor("document", "page", size.width, size.height)
            view.bindDocumentSelectionState(state)

            view.drawSquareLasso()

            assertTrue(!state.isEmpty)
            assertTrue(state.snapshot().tileCount > 0)
            assertTrue(view.clearSelection())
            assertTrue(state.isEmpty)
        }
    }

    @Test fun sparseBooleanSelectionCompositionTouchesOnlyParticipatingTiles() {
        val baseBitmap = Bitmap.createBitmap(1_024, 768, Bitmap.Config.ARGB_8888)
        val region = Bitmap.createBitmap(1_024, 768, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(baseBitmap).drawRect(40f, 40f, 100f, 100f, android.graphics.Paint().apply { color = Color.WHITE })
        android.graphics.Canvas(region).drawRect(820f, 560f, 900f, 640f, android.graphics.Paint().apply { color = Color.WHITE })
        val base = requireNotNull(SparseAlphaMask.fromBitmap(baseBitmap))
        val added = requireNotNull(base.composed(region, AlphaMaskOperation.ADD))
        try {
            assertEquals(2, added.tileCount)
            val snapshot = added.surface.snapshot()
            assertEquals(255, snapshot.alphaAt(60, 60))
            assertEquals(255, snapshot.alphaAt(850, 600))
            assertEquals(0, snapshot.alphaAt(500, 300))
            val subtracted = requireNotNull(added.composed(region, AlphaMaskOperation.SUBTRACT))
            try {
                assertEquals("Subtract discards the tile that became transparent", 1, subtracted.tileCount)
                val intersected = subtracted.composed(region, AlphaMaskOperation.INTERSECT)
                assertTrue("Disjoint sparse intersection is empty", intersected == null)
            } finally { subtracted.close() }
        } finally {
            base.close(); added.close(); baseBitmap.recycle(); region.recycle()
        }
    }

    @Test fun sparseInvertBuildsTheLogicalComplementWithoutAFullBitmapIntermediate() {
        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(source).drawRect(0f, 0f, 256f, 256f, android.graphics.Paint().apply { color = Color.WHITE })
        val mask = requireNotNull(SparseAlphaMask.fromBitmap(source))
        val inverted = mask.inverted()
        try {
            assertEquals("The source island remains one explicit hole over an implicit selected default", 1, inverted.tileCount)
            val snapshot = inverted.surface.snapshot()
            assertEquals(0, snapshot.alphaAt(100, 100))
            assertEquals(255, snapshot.alphaAt(400, 400))
        } finally {
            source.recycle(); mask.close(); inverted.close()
        }
    }

    @Test fun sparseMorphUsesSelectionBoundsAndHaloInsteadOfTheWholePage() {
        val source = Bitmap.createBitmap(2_048, 2_048, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(source).drawRect(980f, 980f, 1_068f, 1_068f, android.graphics.Paint().apply { color = Color.WHITE })
        val mask = requireNotNull(SparseAlphaMask.fromBitmap(source))
        val expanded = requireNotNull(mask.morphed(24, dilate = true))
        val shrunk = requireNotNull(expanded.morphed(24, dilate = false))
        try {
            assertTrue("A local morph must not retain the 8x8 page tile grid", expanded.tileCount <= 4)
            assertTrue("A local morph must retain bounded pixel storage", expanded.retainedPixels < source.width.toLong() * source.height / 8L)
            val expandedSnapshot = expanded.surface.snapshot()
            val shrunkSnapshot = shrunk.surface.snapshot()
            assertEquals(255, expandedSnapshot.alphaAt(965, 1_024))
            assertEquals(0, shrunkSnapshot.alphaAt(965, 1_024))
            assertEquals(255, shrunkSnapshot.alphaAt(1_024, 1_024))
        } finally {
            source.recycle(); mask.close(); expanded.close(); shrunk.close()
        }
    }

    @Test fun emptySparseAlphaPlaneDoesNotRetainTiles() {
        val bitmap = Bitmap.createBitmap(1_024, 768, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(SparseAlphaMask.fromBitmap(bitmap) == null)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun sparseAlphaTileDiscoveryStaysWithinSelectionFrameBudget() {
        val bitmap = Bitmap.createBitmap(2_048, 2_048, Bitmap.Config.ARGB_8888)
        try {
            android.graphics.Canvas(bitmap).drawCircle(1_024f, 1_024f, 16f, android.graphics.Paint().apply { color = Color.WHITE })
            repeat(2) { SparseAlphaMask.fromBitmap(bitmap)?.close() }
            val elapsed = ArrayList<Long>(8)
            repeat(8) {
                val started = SystemClock.elapsedRealtimeNanos()
                SparseAlphaMask.fromBitmap(bitmap)?.close()
                elapsed += SystemClock.elapsedRealtimeNanos() - started
            }
            val sorted = elapsed.sorted()
            val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
            android.util.Log.i("InkloomPerf", "sparse_alpha_tile_scan_p95_ms=$p95Ms")
            assertTrue("Sparse alpha tile scan P95 was ${p95Ms}ms", p95Ms < 33.3f)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun sparseAlphaPlaneRetainsOnlyNonEmptySelectionTiles() {
        val bitmap = Bitmap.createBitmap(2_048, 2_048, Bitmap.Config.ARGB_8888)
        try {
            android.graphics.Canvas(bitmap).drawRect(1_020f, 1_020f, 1_040f, 1_040f, android.graphics.Paint().apply { color = Color.WHITE })
            val mask = requireNotNull(SparseAlphaMask.fromBitmap(bitmap))
            try {
                assertEquals(4, mask.tileCount)
                assertTrue(mask.retainedPixels < bitmap.width.toLong() * bitmap.height / 8L)
                val restored = mask.surface.snapshot()
                assertEquals(255, restored.alphaAt(1_030, 1_030))
                assertEquals(0, restored.alphaAt(100, 100))
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

            var selection: app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot? = null
            view.onSparseFillCommitted = { mask, _, _ -> selection = mask }
            assertTrue(view.fillSelection())
            val mask = requireNotNull(selection)
            assertTrue(mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight / 2) > 0)
            assertEquals(0, mask.alphaAt(mask.logicalWidth / 20, mask.logicalHeight / 20))
        }
    }

    @Test fun borderSelectionKeepsOnlyContentCrossingTheSelectionEdge() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = configuredView(horizontalStroke("border-source"), mutableListOf())
            view.drawSquareLasso()

            assertTrue(view.borderSelection(80f))
            assertTrue(view.selectionMaskRegions().isNotEmpty())
            var mask: app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot? = null
            view.onGradientCommitted = { _, _, _, _, _, value -> mask = value }
            view.tool = EditorTool.GRADIENT
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 100f, 500f, 60L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 900f, 500f, 70L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 900f, 500f, 80L)
            val committed = requireNotNull(mask)
            assertTrue(committed.alphaAt(committed.logicalWidth / 5, committed.logicalHeight / 2) >= 128)
            assertEquals(0, committed.alphaAt(committed.logicalWidth / 2, committed.logicalHeight / 2))
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

    @Test fun partialLassoDoesNotMutateArtworkUntilDeleteThenRemovesOnlyEnclosedSegment() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val original = horizontalStroke("original")
            val edits = mutableListOf<CapturedEdit>()
            val view = configuredView(original, edits)

            view.drawSquareLasso()

            assertTrue("Creating a selection must not rewrite document content", edits.isEmpty())

            view.deleteSelection()

            assertEquals(1, edits.size)
            assertEquals(listOf("original"), edits[0].removedIds)
            assertEquals(2, edits[0].replacements.size)
            assertEquals(.1f, edits[0].replacements.first().points.first().x, .001f)
            assertEquals(.2f, edits[0].replacements.first().points.last().x, .001f)
            assertEquals(.8f, edits[0].replacements.last().points.first().x, .001f)
            assertEquals(.9f, edits[0].replacements.last().points.last().x, .001f)

            view.undo()
            assertEquals(2, edits.size)
            assertEquals(edits[0].replacements.map(InkStroke::id), edits[1].removedIds)
            assertEquals(listOf(original), edits[1].replacements)
        }
    }

    @Test fun pageSwitchAndViewRebindDoNotDestroyDocumentSelection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val runtime = DocumentSelectionRuntime()
            val view = configuredView(horizontalStroke("page-a-stroke"), mutableListOf())
            val size = requireNotNull(SelectionMaskCompositor.maskSize(view.pageModelWidth, view.pageModelHeight))
            val pageA = runtime.stateFor("document", "page-a", size.width, size.height)
            val pageB = runtime.stateFor("document", "page-b", size.width, size.height)

            view.bindDocumentSelectionState(pageA)
            view.setDocumentStrokes(listOf(horizontalStroke("page-a-stroke")), "document:page-a")
            view.drawSquareLasso()
            assertTrue(!pageA.isEmpty)

            view.bindDocumentSelectionState(pageB)
            view.setDocumentStrokes(listOf(horizontalStroke("page-b-stroke")), "document:page-b")
            assertTrue(pageB.isEmpty)
            assertTrue("Switching pages must not clear page A's document selection", !pageA.isEmpty)

            view.bindDocumentSelectionState(pageA)
            view.setDocumentStrokes(listOf(horizontalStroke("page-a-stroke")), "document:page-a")
            assertTrue(view.hasActiveSelection())
        }
    }

    @Test fun productionRasterSelectionDeleteAndUndoConsumeDocumentAlpha() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = configuredView(horizontalStroke("vector-companion"), mutableListOf())
            view.setDocumentStrokes(emptyList(), "document:raster-page")
            val size = requireNotNull(SelectionMaskCompositor.maskSize(view.pageModelWidth, view.pageModelHeight))
            val runtime = DocumentSelectionRuntime()
            view.bindDocumentSelectionState(runtime.stateFor("document", "raster-page", size.width, size.height))
            val surface = PixelTileSurface(size.width, size.height)
            view.setRasterLayerSurfaces(mapOf(LayerIds.INK to surface))
            view.activeLayerId = LayerIds.INK
            view.onRasterLayerCommitted = { it.close() }
            view.drawSquareLasso()
            assertTrue(view.hasActiveSelection())
            val selectedMask = requireNotNull(view.selectionMaskSurfaceSnapshot())
            val bounds = requireNotNull(selectedMask.nonZeroBounds())
            val centerX = bounds.left + bounds.width / 2
            val centerY = bounds.top + bounds.height / 2
            assertTrue("Selection bounds must contain their center", selectedMask.alphaAt(centerX, centerY) > 0)
            surface.beginWrite("raster selection fixture").run {
                setPixel(centerX, centerY, 0xff2255aa.toInt())
                commit().close()
            }

            view.deleteSelection()
            assertEquals(0, surface.pixelAt(centerX, centerY))
            assertTrue("Delete keeps selection active for repeated operations", view.hasActiveSelection())

            view.undo()
            assertEquals(0xff2255aa.toInt(), surface.pixelAt(centerX, centerY))
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
