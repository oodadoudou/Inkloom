package app.inkloom

import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.LayerMask
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerMaskProductionEditingTest {
    @Test fun stylusPaintEraseUndoRedoPersistSparseMaskWithoutChangingLayerPixels() {
        val raster = PixelTileSurface(64, 64, tileSize = 64).also { surface ->
            surface.beginWrite("red layer").run {
                editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(Color.RED) }
                commit().close()
            }
        }
        val mask = MaskTileSurface(64, 64, tileSize = 64)
        val committed = mutableListOf<MaskTileSurfaceSnapshot>()
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 256, 256)
                pageModelWidth = 64f
                pageModelHeight = 64f
                activeLayerId = "paint"
                tool = EditorTool.PEN
                baseWidth = 12f
                setRasterLayerSurfaces(mapOf("paint" to raster))
                layerMasks = mapOf("paint" to LayerMask(enabled = true))
                layerMaskSurfaces = mapOf("paint" to mask.snapshot())
                onLayerMaskSurfaceCommitted = { _, snapshot -> committed += snapshot }
                assertTrue(toggleLayerMaskEditing("paint"))
            }
        }

        dispatchStylus(view, MotionEvent.ACTION_DOWN, 128f, 128f, 10L)
        dispatchStylus(view, MotionEvent.ACTION_UP, 128f, 128f, 20L)
        assertTrue(committed.last().alphaAt(32, 32) > 0)
        assertEquals(Color.RED, raster.pixelAt(32, 32))

        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(0, committed.last().alphaAt(32, 32))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo() }
        assertTrue(committed.last().alphaAt(32, 32) > 0)

        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.tool = EditorTool.ERASER }
        dispatchStylus(view, MotionEvent.ACTION_DOWN, 128f, 128f, 30L)
        dispatchStylus(view, MotionEvent.ACTION_UP, 128f, 128f, 40L)
        assertEquals(0, committed.last().alphaAt(32, 32))
        assertEquals(Color.RED, raster.pixelAt(32, 32))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(view.fillLayerMask("paint", 255))
        }
        assertEquals(255, committed.last().alphaAt(0, 0))
        assertEquals(0, committed.last().tileCount)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(0, committed.last().alphaAt(0, 0))
    }

    @Test fun eightKDirectMaskStrokePreviewMeetsFrameAndSparseMemoryGate() {
        val size = 8192
        val mask = MaskTileSurface(size, size, tileSize = 128)
        var committed: MaskTileSurfaceSnapshot? = null
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 512, 512)
                pageModelWidth = size.toFloat()
                pageModelHeight = size.toFloat()
                activeLayerId = "paint"
                tool = EditorTool.PEN
                baseWidth = 64f
                layerMasks = mapOf("paint" to LayerMask(enabled = true))
                layerMaskSurfaces = mapOf("paint" to mask.snapshot())
                onLayerMaskSurfaceCommitted = { _, snapshot -> committed = snapshot }
                assertTrue(toggleLayerMaskEditing("paint"))
            }
        }
        dispatchStylus(view, MotionEvent.ACTION_DOWN, 220f, 256f, 10L)
        val elapsed = ArrayList<Long>(40)
        repeat(40) { index ->
            val started = SystemClock.elapsedRealtimeNanos()
            dispatchStylus(view, MotionEvent.ACTION_MOVE, 221f + index, 256f, 20L + index)
            elapsed += SystemClock.elapsedRealtimeNanos() - started
        }
        dispatchStylus(view, MotionEvent.ACTION_UP, 261f, 256f, 70L)
        val p95Ms = elapsed.sorted()[38] / 1_000_000f
        val snapshot = requireNotNull(committed)
        Log.i("InkloomPerf", "layer_mask_8k_live_preview_p95_ms=$p95Ms tiles=${snapshot.tileCount} bytes=${snapshot.retainedByteSize}")
        assertTrue("8K layer-mask preview P95 was ${p95Ms}ms", p95Ms < 16.7f)
        assertTrue("8K layer-mask stroke materialized ${snapshot.tileCount} tiles", snapshot.tileCount <= 16)
        assertTrue(snapshot.retainedByteSize <= 16L * 128 * 128)
    }

    private fun dispatchStylus(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = .1f
        }
        val event = MotionEvent.obtain(
            0L,
            time,
            action,
            1,
            arrayOf(properties),
            arrayOf(coordinates),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) }
        } finally {
            event.recycle()
        }
    }
}
