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
