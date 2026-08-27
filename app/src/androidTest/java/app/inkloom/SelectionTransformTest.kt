package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import app.inkloom.engine.v3.scene.DocumentSelectionRuntime
import app.inkloom.engine.v3.surface.MaskComposeMode
import app.inkloom.engine.v3.surface.MaskTileSurface
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.surface.PixelTileSurface
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

    @Test fun draggingACornerHandleScalesTheSelectedStroke() {
        var committed = 0
        val view = canvas {
            tool = EditorTool.SELECT
            fingerDrawingEnabled = true
            onStrokesReplaced = { _, _, _ -> committed++ }
        }
        // Plant a stroke near the centre so the selection bbox + handle exist.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.setDocumentStrokes(listOf(app.inkloom.document.InkStroke(id = "a", points = listOf(app.inkloom.document.StrokePoint(.5f, .5f)), colorArgb = 0xFF111111L, width = 6f, brushId = "pen", layerId = app.inkloom.document.LayerIds.INK)), "test")
        }
        // Selection drawing and content transformation are separate modes, matching Procreate.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(view.selectAllEditableStrokes())
            assertTrue(view.enterFreeTransform())
        }
        // The top-right corner handle sits at (0.5, 0.5) in normalized -> (200,200) screen. Drag it outward to scale up.
        send(view, 20L, MotionEvent.ACTION_DOWN, 200f, 200f)
        send(view, 40L, MotionEvent.ACTION_MOVE, 300f, 300f)
        send(view, 50L, MotionEvent.ACTION_UP, 300f, 300f)
        assertTrue("Scaling a selection should commit a stroke edit", committed > 0)
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
            setDocumentStrokes(emptyList(), "raster-selection-transform")
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
