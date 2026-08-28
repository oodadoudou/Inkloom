package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.LayerIds
import app.inkloom.document.SelectionMaskCompositor
import app.inkloom.engine.v5.selection.AlphaMaskOperation
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.engine.v5.selection.SparseAlphaMask
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Production selection tests. There is deliberately no InkStroke/vector compatibility fixture. */
@RunWith(AndroidJUnit4::class)
class InkCanvasLassoTest {
    @Test fun lassoPublishesAndClearsDocumentSelection() = onMain {
        val fixture = configuredView("page")
        fixture.view.drawSquareLasso()
        assertTrue(!fixture.state.isEmpty)
        assertTrue(fixture.state.snapshot().tileCount > 0)
        assertTrue(fixture.view.clearSelection())
        assertTrue(fixture.state.isEmpty)
    }

    @Test fun committedLassoHasExactRasterBounds() = onMain {
        val fixture = configuredView("bounds")
        fixture.view.drawSquareLasso()
        val bounds = requireNotNull(fixture.view.selectionMaskSurfaceSnapshot()?.nonZeroBounds())
        assertTrue(kotlin.math.abs(bounds.left - fixture.state.logicalWidth * .2f) <= 2f)
        assertTrue(kotlin.math.abs(bounds.top - fixture.state.logicalHeight * .2f) <= 2f)
        assertTrue(kotlin.math.abs(bounds.right - fixture.state.logicalWidth * .8f) <= 2f)
        assertTrue(kotlin.math.abs(bounds.bottom - fixture.state.logicalHeight * .8f) <= 2f)
    }

    @Test fun selectionCanExpandShrinkFeatherDeselectAndReselect() = onMain {
        val fixture = configuredView("lifecycle")
        fixture.view.drawSquareLasso()
        val original = requireNotNull(fixture.view.selectionMaskSurfaceSnapshot()?.nonZeroBounds())
        assertTrue(fixture.view.expandSelection(80f))
        val expanded = requireNotNull(fixture.view.selectionMaskSurfaceSnapshot()?.nonZeroBounds())
        assertTrue(expanded.left < original.left)
        assertTrue(fixture.view.shrinkSelection(40f))
        val shrunk = requireNotNull(fixture.view.selectionMaskSurfaceSnapshot()?.nonZeroBounds())
        assertTrue(shrunk.left > expanded.left)
        assertTrue(fixture.view.featherSelection(24f))
        assertEquals(24f, fixture.view.selectionFeatherPixels, .001f)
        assertTrue(fixture.view.clearSelection())
        assertTrue(fixture.view.reselect())
        assertEquals(24f, fixture.view.selectionFeatherPixels, .001f)
    }

    @Test fun cancellingReplacementLassoPreservesCommittedMask() = onMain {
        val fixture = configuredView("cancel")
        fixture.view.drawSquareLasso()
        val before = requireNotNull(fixture.view.selectionMaskSurfaceSnapshot())
        fixture.view.dispatchStylus(MotionEvent.ACTION_DOWN, 20f, 20f, 60L)
        fixture.view.dispatchStylus(MotionEvent.ACTION_MOVE, 100f, 20f, 70L)
        fixture.view.dispatchStylus(MotionEvent.ACTION_CANCEL, 100f, 100f, 80L)
        val after = requireNotNull(fixture.view.selectionMaskSurfaceSnapshot())
        assertEquals(before.alphaAt(500, 500), after.alphaAt(500, 500))
        assertEquals(before.alphaAt(50, 50), after.alphaAt(50, 50))
    }

    @Test fun borderSelectionProducesRingMask() = onMain {
        val fixture = configuredView("border")
        fixture.view.drawSquareLasso()
        assertTrue(fixture.view.borderSelection(80f))
        val mask = requireNotNull(fixture.view.selectionMaskSurfaceSnapshot())
        assertTrue(mask.alphaAt(200, 500) >= 128)
        assertEquals(0, mask.alphaAt(500, 500))
    }

    @Test fun rasterSelectionDeleteAndUndoUseSameDocumentAlpha() = onMain {
        val fixture = configuredView("delete")
        val surface = PixelTileSurface(fixture.state.logicalWidth, fixture.state.logicalHeight)
        fixture.view.setRasterLayerSurfaces(mapOf(LayerIds.INK to surface))
        fixture.view.activeLayerId = LayerIds.INK
        fixture.view.onRasterLayerCommitted = { }
        fixture.view.drawSquareLasso()
        val bounds = requireNotNull(fixture.state.snapshot().nonZeroBounds())
        val x = bounds.left + bounds.width / 2
        val y = bounds.top + bounds.height / 2
        surface.beginWrite("fixture").run { setPixel(x, y, 0xff2255aa.toInt()); commit().close() }
        fixture.view.deleteSelection()
        assertEquals(0, surface.pixelAt(x, y))
        fixture.view.undo()
        assertEquals(0xff2255aa.toInt(), surface.pixelAt(x, y))
    }

    @Test fun selectionStateSurvivesViewRebindAndIsPageScoped() = onMain {
        val runtime = DocumentSelectionRuntime()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = InkCanvasView(context).apply { layout(0, 0, 1_000, 1_000); tool = EditorTool.SELECT }
        val size = requireNotNull(SelectionMaskCompositor.maskSize(view.pageModelWidth, view.pageModelHeight))
        val pageA = runtime.stateFor("document", "page-a", size.width, size.height)
        val pageB = runtime.stateFor("document", "page-b", size.width, size.height)
        view.bindDocumentSelectionState(pageA)
        view.drawSquareLasso()
        assertTrue(!pageA.isEmpty)
        view.bindDocumentSelectionState(pageB)
        assertTrue(pageB.isEmpty)
        assertTrue(!pageA.isEmpty)
        view.bindDocumentSelectionState(pageA)
        assertTrue(view.hasActiveSelection())
    }

    @Test fun sparseBooleanCompositionTouchesOnlyParticipatingTiles() {
        val baseBitmap = Bitmap.createBitmap(1_024, 768, Bitmap.Config.ARGB_8888)
        val region = Bitmap.createBitmap(1_024, 768, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(baseBitmap).drawRect(40f, 40f, 100f, 100f, android.graphics.Paint().apply { color = Color.WHITE })
        android.graphics.Canvas(region).drawRect(820f, 560f, 900f, 640f, android.graphics.Paint().apply { color = Color.WHITE })
        val base = requireNotNull(SparseAlphaMask.fromBitmap(baseBitmap))
        val added = requireNotNull(base.composed(region, AlphaMaskOperation.ADD))
        try {
            assertEquals(2, added.tileCount)
            assertEquals(255, added.surface.snapshot().alphaAt(60, 60))
            assertEquals(255, added.surface.snapshot().alphaAt(850, 600))
            val subtracted = requireNotNull(added.composed(region, AlphaMaskOperation.SUBTRACT))
            try {
                assertEquals(1, subtracted.tileCount)
                assertTrue(subtracted.composed(region, AlphaMaskOperation.INTERSECT) == null)
            } finally { subtracted.close() }
        } finally {
            base.close(); added.close(); baseBitmap.recycle(); region.recycle()
        }
    }

    @Test fun sparseAlphaDiscoveryMeetsInstrumentedPerformanceGate() {
        // Production selection masks are capped at roughly 1.2 MP by SelectionMaskCompositor;
        // benchmark the actual call-path budget instead of a non-production 4.2 MP bitmap.
        val bitmap = Bitmap.createBitmap(1_200, 1_000, Bitmap.Config.ARGB_8888)
        try {
            android.graphics.Canvas(bitmap).drawCircle(600f, 500f, 16f, android.graphics.Paint().apply { color = Color.WHITE })
            repeat(2) { SparseAlphaMask.fromBitmap(bitmap)?.close() }
            val elapsed = ArrayList<Long>(8)
            repeat(8) {
                val started = SystemClock.elapsedRealtimeNanos()
                SparseAlphaMask.fromBitmap(bitmap)?.close()
                elapsed += SystemClock.elapsedRealtimeNanos() - started
            }
            val sorted = elapsed.sorted()
            val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
            android.util.Log.i("InkloomPerf", "selection_sparse_scan_p95_ms=$p95Ms")
            assertTrue("Sparse alpha scan P95 was ${p95Ms}ms", p95Ms < 33.3f)
        } finally { bitmap.recycle() }
    }

    private fun configuredView(pageId: String): Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = InkCanvasView(context).apply { layout(0, 0, 1_000, 1_000); tool = EditorTool.SELECT }
        val size = requireNotNull(SelectionMaskCompositor.maskSize(view.pageModelWidth, view.pageModelHeight))
        val state = DocumentSelectionRuntime().stateFor("document", pageId, size.width, size.height)
        view.bindDocumentSelectionState(state)
        return Fixture(view, state)
    }

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

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private data class Fixture(
        val view: InkCanvasView,
        val state: app.inkloom.engine.v5.selection.DocumentSelectionState,
    )
}
