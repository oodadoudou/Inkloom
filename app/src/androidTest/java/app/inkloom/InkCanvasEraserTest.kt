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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasEraserTest {
    @Test fun canceledV5EraserContactRollsBackWithoutPublishing() {
        val surface = opaqueSurface(400, 400)
        val initialRevision = surface.revision
        var commits = 0
        val view = configuredView(mapOf("paint" to surface)).apply {
            onRasterLayerCommitted = { commits++ }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 200f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 320f, 200f, 20L)
            view.dispatchStylus(MotionEvent.ACTION_CANCEL, 320f, 200f, 30L)
        }

        assertEquals(initialRevision, surface.revision)
        assertEquals(255, Color.alpha(surface.pixelAt(200, 200)))
        assertEquals(0, commits)
    }

    @Test fun eraserPublishesOnlyTheActiveRasterLayer() {
        val note = opaqueSurface(400, 400)
        val highlight = opaqueSurface(400, 400)
        val committedLayers = mutableListOf<String>()
        val view = configuredView(mapOf("note" to note, "highlight" to highlight)).apply {
            activeLayerId = "highlight"
            onRasterLayerCommitted = { committedLayers += it.layerId }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 200f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 320f, 200f, 20L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 320f, 200f, 30L)
        }
        settlePublishedStroke(view)

        assertEquals(listOf("highlight"), committedLayers)
        assertEquals(0, Color.alpha(highlight.pixelAt(200, 200)))
        assertEquals(255, Color.alpha(note.pixelAt(200, 200)))
    }

    @Test fun rapidProductionEraserPacketsStayInsideOne120HzFrameAtP95() {
        val surface = opaqueSurface(1_000, 1_000)
        val view = configuredView(mapOf("paint" to surface)).apply { eraserWidth = 72f }
        val packetTimes = ArrayList<Long>(120)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 500f, 10L)
            repeat(120) { index ->
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 84f + index * 7f, 500f + index % 5, 20L + index)
                packetTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
            }
            view.dispatchStylus(MotionEvent.ACTION_UP, 930f, 500f, 200L)
        }
        settlePublishedStroke(view)

        val sorted = packetTimes.drop(8).sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "v5_production_eraser_input_p95_ms=$p95Ms")
        assertTrue("V5 production eraser p95 was ${p95Ms}ms", p95Ms < 8.33f)
    }

    private fun configuredView(surfaces: Map<String, PixelTileSurface>): InkCanvasView {
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, surfaces.values.first().logicalWidth, surfaces.values.first().logicalHeight)
                tool = EditorTool.ERASER
                eraserWidth = 160f
                eraserHardness = 1f
                eraserOpacity = 1f
                activeLayerId = surfaces.keys.first()
                setRasterLayerSurfaces(surfaces)
            }
        }
        return view
    }

    private fun settlePublishedStroke(view: InkCanvasView) {
        view.awaitV5StrokeWorkerIdleForTest()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
    }

    private fun opaqueSurface(width: Int, height: Int) = PixelTileSurface(width, height).also { surface ->
        surface.beginWrite("seed eraser fixture").use { transaction ->
            for (y in 0 until height) for (x in 0 until width) transaction.setPixel(x, y, Color.BLACK)
            transaction.commit().close()
        }
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = .8f
            size = .01f
            touchMajor = 1f
        }
        MotionEvent.obtain(
            10L,
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
        ).also { event -> try { onTouchEvent(event) } finally { event.recycle() } }
    }
}
