package app.inkloom

import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.LayerIds
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.engine.v5.surface.*
import app.inkloom.model.EditorTool
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.*
import org.junit.Test

class SelectionInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun fixture(): Pair<InkCanvasView, PixelTileSurface> {
        val pixels = PixelTileSurface(128, 128, 64)
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 400, 400)
            pageModelWidth = 128f; pageModelHeight = 128f
            pagePaperColor = Color.WHITE
            setRasterLayerSurfaces(mapOf(LayerIds.INK to pixels))
            layerOrder = listOf(LayerIds.INK)
            bindDocumentSelectionState(DocumentSelectionRuntime(64).stateFor("interaction", "page", 128, 128))
            tool = EditorTool.SELECT
        }
        return view to pixels
    }

    @Test fun outsideDeletePreservesSelectionAndOtherLayersAndUndoRestoresInOneStep() {
        instrumentation.runOnMainSync {
            val (view, pixels) = fixture()
            val other = PixelTileSurface(128, 128, 64)
            pixels.beginWrite("three points").use {
                it.setPixel(20, 20, Color.RED); it.setPixel(40, 40, Color.GREEN); it.setPixel(90, 90, Color.BLUE); it.commit().close()
            }
            other.beginWrite("other layer").use { it.setPixel(90, 90, Color.WHITE); it.commit().close() }
            view.setRasterLayerSurfaces(mapOf(LayerIds.INK to pixels, "other" to other))
            val mask = MaskTileSurface(128, 128, 64)
            mask.beginWrite("selection").use { it.setAlpha(20, 20, 255); it.setAlpha(40, 40, 128); it.commit().close() }
            view.replaceSelectionFromMask(mask.snapshot())
            assertTrue(view.deleteOutsideSelection())
            assertEquals(Color.RED, pixels.pixelAt(20, 20))
            assertEquals(128, Color.alpha(pixels.pixelAt(40, 40)))
            assertEquals(0, pixels.pixelAt(90, 90))
            assertEquals(Color.WHITE, other.pixelAt(90, 90))
            assertEquals(128, view.selectionMaskSurfaceSnapshot()!!.alphaAt(40, 40))
            view.undo()
            assertEquals(Color.GREEN, pixels.pixelAt(40, 40)); assertEquals(Color.BLUE, pixels.pixelAt(90, 90))
            view.redo(); assertEquals(0, pixels.pixelAt(90, 90))
            view.setProtectedLayerIds(setOf(LayerIds.INK))
            assertFalse(view.deleteOutsideSelection())
            view.setProtectedLayerIds(emptySet())
            view.alphaLockedLayers = setOf(LayerIds.INK); assertFalse(view.deleteOutsideSelection())
            view.alphaLockedLayers = emptySet()
            view.clearSelection(); assertFalse(view.deleteOutsideSelection())
        }
    }

    @Test fun colorFillChangingColorResamplesOriginalSoftAlphaAndCancelUndoRestoreExactly() {
        instrumentation.runOnMainSync {
            val (view, pixels) = fixture()
            val mask = MaskTileSurface(128, 128, 64)
            mask.beginWrite("soft").use { it.setAlpha(40, 40, 128); it.commit().close() }
            view.replaceSelectionFromMask(mask.snapshot())
            view.inkColor = Color.RED
            assertTrue(view.setSelectionColorFill(true))
            val red = pixels.pixelAt(40, 40)
            assertEquals(128, Color.alpha(red))
            view.inkColor = Color.BLUE
            assertEquals(128, Color.alpha(pixels.pixelAt(40, 40)))
            assertEquals(0, Color.red(pixels.pixelAt(40, 40)))
            view.finishSelectionColorFill(false)
            assertEquals(0, pixels.pixelAt(40, 40))
            view.setSelectionColorFill(true)
            val applied = pixels.pixelAt(40, 40)
            view.finishSelectionColorFill(true)
            view.undo(); assertEquals(0, pixels.pixelAt(40, 40))
            view.redo(); assertEquals(applied, pixels.pixelAt(40, 40))
            view.setSelectionColorFill(false)
        }
    }

    @Test fun layerContentsPreserveSoftAlphaAndExcludePaperAndOtherLayers() {
        instrumentation.runOnMainSync {
            val (view, pixels) = fixture()
            pixels.beginWrite("soft content").use { it.setPixel(40, 40, 0x80000080.toInt()); it.commit().close() }
            assertTrue(view.selectLayerContents(LayerIds.INK))
            val mask = view.selectionMaskSurfaceSnapshot()!!
            assertEquals(128, mask.alphaAt(40, 40))
            assertEquals(0, mask.alphaAt(0, 0))
            view.undo(); assertTrue(view.selectionMaskSurfaceSnapshot()!!.isEmpty)
            view.redo(); assertEquals(128, view.selectionMaskSurfaceSnapshot()!!.alphaAt(40, 40))
            assertEquals(0x80000080.toInt(), pixels.pixelAt(40, 40))
        }
    }

    @Test fun layerContentsComposeAllModesAndEmptyIntersectionHasOneUndo() {
        instrumentation.runOnMainSync {
            val (view, pixels) = fixture()
            val other = PixelTileSurface(128, 128, 64)
            pixels.beginWrite("first").use { it.setPixel(40, 40, Color.BLACK); it.commit().close() }
            other.beginWrite("second").use { it.setPixel(90, 90, Color.RED); it.commit().close() }
            view.setRasterLayerSurfaces(mapOf(LayerIds.INK to pixels, "second" to other))
            assertFalse(view.selectLayerContents("second", MaskComposeMode.ADD))
            assertTrue(view.selectLayerContents(LayerIds.INK))
            assertTrue(view.selectLayerContents("second", MaskComposeMode.ADD))
            assertEquals(255, view.selectionMaskSurfaceSnapshot()!!.alphaAt(40, 40))
            assertEquals(255, view.selectionMaskSurfaceSnapshot()!!.alphaAt(90, 90))
            assertTrue(view.selectLayerContents("second", MaskComposeMode.SUBTRACT))
            assertEquals(0, view.selectionMaskSurfaceSnapshot()!!.alphaAt(90, 90))
            assertTrue(view.selectLayerContents("second", MaskComposeMode.INTERSECT))
            assertFalse(view.hasActiveSelection())
            view.undo(); assertEquals(255, view.selectionMaskSurfaceSnapshot()!!.alphaAt(40, 40))
            view.redo(); assertFalse(view.hasActiveSelection())
            assertTrue(view.selectLayerContents(setOf(LayerIds.INK, "second")))
            assertEquals(255, view.selectionMaskSurfaceSnapshot()!!.alphaAt(40, 40))
            assertEquals(255, view.selectionMaskSurfaceSnapshot()!!.alphaAt(90, 90))
            assertTrue(view.selectLayerContents("empty"))
            assertFalse(view.hasActiveSelection())
            view.undo(); assertEquals(255, view.selectionMaskSurfaceSnapshot()!!.alphaAt(90, 90))
        }
    }

    @Test fun emptySavedSelectionIntersectionCanBeUndoneAndRedone() {
        instrumentation.runOnMainSync {
            val (view, _) = fixture()
            val a = MaskTileSurface(128, 128, 64)
            val b = MaskTileSurface(128, 128, 64)
            a.beginWrite("a").use { it.setAlpha(1, 1, 128); it.commit().close() }
            b.beginWrite("b").use { it.setAlpha(100, 100, 255); it.commit().close() }
            view.replaceSelectionFromMask(a.snapshot())
            assertTrue(view.loadSavedSelection(b.snapshot(), MaskComposeMode.INTERSECT))
            assertFalse(view.hasActiveSelection())
            view.undo(); assertEquals(128, view.selectionMaskSurfaceSnapshot()!!.alphaAt(1, 1))
            view.redo(); assertFalse(view.hasActiveSelection())
        }
    }

    @Test fun sparse8kLayerSamplingDoesNotVisitBlankPageTilesAndIncludesEdgeAlpha() {
        instrumentation.runOnMainSync {
            var draws = 0
            val mask = app.inkloom.ui.LayerContentsSelection.sample(8192, 8192,
                listOf(PixelRect(8000, 8000, 8010, 8010), PixelRect(8180, 8180, 8192, 8192))) { canvas ->
                draws++
                canvas.drawRect(8000f, 8000f, 8010f, 8010f, android.graphics.Paint().apply { color = 0x80000000.toInt() })
                canvas.drawRect(8180f, 8180f, 8192f, 8192f, android.graphics.Paint().apply { color = Color.BLACK })
            }
            assertEquals(1, draws)
            assertEquals(128, mask.alphaAt(8001, 8001))
            assertEquals(255, mask.alphaAt(8191, 8191))
            assertEquals(0, mask.alphaAt(4000, 4000))
            assertEquals(2, mask.tileCount) // Sampling blocks are 256 px; persistent mask tiles are 128 px.
        }
    }

    @Test fun retainedObjectsHaveAnExplicitTransferRejectionInsteadOfPartialCopy() {
        instrumentation.runOnMainSync {
            val (view, pixels) = fixture()
            val bitmap = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            view.setLayerElementBitmaps(mapOf(LayerIds.INK to app.inkloom.ui.LayerElementBitmaps(bitmap, null)))
            assertTrue(view.selectLayerContents(LayerIds.INK))
            var rejection = ""
            var dispatched = false
            view.onPaintCapabilityRejected = { rejection = it }
            view.onSelectionLayerTransferRequested = { dispatched = true }
            view.requestSelectionLayerTransfer(false)
            assertFalse(dispatched); assertTrue(rejection.contains("objects"))
            assertFalse(view.enterFreeTransform())
            assertEquals(0, pixels.snapshot().tileCount)
            view.setLayerElementBitmaps(emptyMap()); bitmap.recycle()
        }
    }

    @Test fun automaticDragRetainsItsSeedAndCancellationRestoresThePriorMask() {
        instrumentation.runOnMainSync {
            val (view, pixels) = fixture()
            pixels.beginWrite("black square").use { tx ->
                for (y in 32 until 96) for (x in 32 until 96) tx.setPixel(x, y, Color.BLACK)
                tx.commit().close()
            }
            view.refreshSelectionTransferPixels()
            view.selectionShape = SelectionShape.MAGIC_WAND
            view.selectionMode = SelectionMode.ADD
            var threshold = 0f
            view.onSelectionThresholdChanged = { threshold = it }
            fun contact(action: Int, x: Float, y: Float) {
                val time = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(time, time, action, 1,
                    arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
                    arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .7f }),
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
                try { view.onTouchEvent(event) } finally { event.recycle() }
            }
            contact(MotionEvent.ACTION_DOWN, 200f, 200f)
            contact(MotionEvent.ACTION_MOVE, 325f, 200f)
            contact(MotionEvent.ACTION_UP, 325f, 200f)
            val selected = view.selectionMaskSurfaceSnapshot()!!
            assertEquals(255, selected.alphaAt(64, 64))
            assertEquals(0, selected.alphaAt(5, 5))
            assertTrue(threshold > .25f)
            val original = selected.copyAlpha(PixelRect(0, 0, 128, 128))
            contact(MotionEvent.ACTION_DOWN, 10f, 10f)
            contact(MotionEvent.ACTION_CANCEL, 10f, 10f)
            assertArrayEquals(original, view.selectionMaskSurfaceSnapshot()!!.copyAlpha(PixelRect(0, 0, 128, 128)))
            view.undo(); assertTrue(view.selectionMaskSurfaceSnapshot()!!.isEmpty)
        }
    }
}
