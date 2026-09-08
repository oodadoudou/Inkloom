package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionTransformTest {
    private fun canvas(block: InkCanvasView.() -> Unit): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 400, 400); block() }
        }
        return result
    }

    private fun event(time: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }
        return MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun send(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float) {
        val e = event(time, action, x, y)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(e) } } finally { e.recycle() }
    }

    @Test fun freeTransformMapsDocumentMaskAndRasterResolutions() {
        val surface = PixelTileSurface(64, 64, tileSize = 64)
        surface.beginWrite("raster square").run {
            editTile(PixelTileKey(0, 0), PixelRect(8, 8, 16, 16)) { pixels, stride ->
                for (y in 8 until 16) for (x in 8 until 16) pixels[y * stride + x] = Color.RED
            }
            commit().close()
        }
        val mask = MaskTileSurface(32, 32, tileSize = 64)
        mask.beginWrite("alpha-only selection").run {
            composeAlpha(PixelRect(4, 4, 8, 8), ByteArray(16) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        val runtime = DocumentSelectionRuntime(tileSize = 64)
        val view = canvas {
            pageModelWidth = 128f
            pageModelHeight = 128f
            activeLayerId = "raster"
            tool = EditorTool.SELECT
            fingerDrawingEnabled = true
            setRasterLayerSurfaces(mapOf("raster" to surface))
            bindDocumentSelectionState(runtime.stateFor("document", "page", 32, 32))
            replaceSelectionFromMask(mask.snapshot())
            assertTrue(enterFreeTransform())
        }

        send(view, 20L, MotionEvent.ACTION_DOWN, 75f, 75f)
        send(view, 40L, MotionEvent.ACTION_MOVE, 175f, 75f)
        send(view, 50L, MotionEvent.ACTION_UP, 175f, 75f)

        assertEquals(Color.TRANSPARENT, surface.pixelAt(10, 10))
        assertEquals(Color.RED, surface.pixelAt(26, 10))
        val movedMask = view.selectionMaskSurfaceSnapshot()!!
        assertEquals(0, movedMask.alphaAt(5, 5))
        assertEquals(255, movedMask.alphaAt(13, 5))
    }
    @Test fun productionFreeTransformMovesAlphaOnlyRasterSelectionAndKeepsMaskAligned() {
        val surface = PixelTileSurface(64, 64, tileSize = 64)
        surface.beginWrite("raster square").run {
            editTile(PixelTileKey(0, 0), PixelRect(8, 8, 16, 16)) { pixels, stride ->
                for (y in 8 until 16) for (x in 8 until 16) pixels[y * stride + x] = Color.RED
            }
            commit().close()
        }
        val mask = MaskTileSurface(64, 64, tileSize = 64)
        mask.beginWrite("alpha-only selection").run {
            composeAlpha(PixelRect(8, 8, 16, 16), ByteArray(64) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        val runtime = DocumentSelectionRuntime(tileSize = 64)
        val view = canvas {
            pageModelWidth = 64f
            pageModelHeight = 64f
            activeLayerId = "raster"
            tool = EditorTool.SELECT
            fingerDrawingEnabled = true
            setRasterLayerSurfaces(mapOf("raster" to surface))
            bindDocumentSelectionState(runtime.stateFor("document", "page", 64, 64))
            replaceSelectionFromMask(mask.snapshot())
            assertTrue(enterFreeTransform())
        }

        send(view, 20L, MotionEvent.ACTION_DOWN, 75f, 75f)
        send(view, 40L, MotionEvent.ACTION_MOVE, 175f, 75f)
        send(view, 50L, MotionEvent.ACTION_UP, 175f, 75f)

        assertEquals(Color.TRANSPARENT, surface.pixelAt(10, 10))
        assertEquals(Color.RED, surface.pixelAt(26, 10))
        val movedMask = view.selectionMaskSurfaceSnapshot()!!
        assertEquals(0, movedMask.alphaAt(10, 10))
        assertEquals(255, movedMask.alphaAt(26, 10))
    }
}
