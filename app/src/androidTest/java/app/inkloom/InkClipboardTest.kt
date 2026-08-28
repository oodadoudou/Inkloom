package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Clipboard integration over the V5 raster document; vector/system-clipboard compatibility is gone. */
@RunWith(AndroidJUnit4::class)
class InkClipboardTest {
    @Test fun copyPasteCreatesOneOffsetRasterEditAndUndoRestoresIt() = onMain {
        val (view, surface) = configuredView()
        view.selectSourceRegion()

        assertTrue(view.copySelectionToClipboard())
        assertTrue(view.pasteSelectionFromClipboard())
        assertEquals(Color.MAGENTA, surface.pixelAt(500, 500))
        assertEquals(Color.MAGENTA, surface.pixelAt(530, 530))

        view.undo()
        assertEquals(Color.MAGENTA, surface.pixelAt(500, 500))
        assertEquals(Color.TRANSPARENT, surface.pixelAt(530, 530))

        view.setProtectedLayerIds(setOf("paint"))
        assertFalse(view.pasteSelectionFromClipboard())
    }

    @Test fun cutCopiesBeforeClearingAndCanBeUndone() = onMain {
        val (view, surface) = configuredView()
        view.selectSourceRegion()

        assertTrue(view.cutSelectionToClipboard())
        assertEquals(Color.TRANSPARENT, surface.pixelAt(500, 500))
        view.undo()
        assertEquals(Color.MAGENTA, surface.pixelAt(500, 500))

        assertTrue("Cut must retain a pasteable raster payload", view.pasteSelectionFromClipboard())
        assertEquals(Color.MAGENTA, surface.pixelAt(530, 530))
    }

    private fun configuredView(): Pair<InkCanvasView, PixelTileSurface> {
        val surface = PixelTileSurface(1_000, 1_000)
        surface.beginWrite("clipboard source").run {
            for (y in 480..520) for (x in 480..520) setPixel(x, y, Color.MAGENTA)
            commit().close()
        }
        val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            layout(0, 0, 1_000, 1_000)
            pageModelWidth = 1_000f
            pageModelHeight = 1_000f
            tool = EditorTool.SELECT
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            onRasterLayerCommitted = { }
        }
        return view to surface
    }

    private fun InkCanvasView.selectSourceRegion() {
        dispatchStylus(MotionEvent.ACTION_DOWN, 450f, 450f, 10L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 550f, 450f, 20L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 550f, 550f, 30L)
        dispatchStylus(MotionEvent.ACTION_MOVE, 450f, 550f, 40L)
        dispatchStylus(MotionEvent.ACTION_UP, 450f, 450f, 50L)
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .01f; touchMajor = 1f }
        MotionEvent.obtain(10L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0).also { event ->
            try { onTouchEvent(event) } finally { event.recycle() }
        }
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
