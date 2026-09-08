package app.inkloom.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.LayerRole
import app.inkloom.document.RasterTileSurfaceRef
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.installV5TestInk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterSurfaceRecompositionTest {
    @Test
    fun hidingLayerWhileSaveIsPendingCannotRestoreErasedPigment() {
        var loads = 0
        val runtime = RasterLayerSurfaceRuntime(600) { _, _, _ -> opaqueSurface().also { loads++ } }
        val initial = page(reference("before-erase", 1))
        val live = runtime.reconcile(initial).getValue(PAINT)
        live.beginWrite("eraser awaiting disk save").use { write ->
            write.setPixel(300, 300, 0); write.commit().close()
        }
        val hidden = initial.copy(layers = initial.layers.map { if (it.id == PAINT) it.copy(visible = false) else it })
        assertTrue(runtime.reconcile(hidden).isEmpty())
        val saved = reference("after-erase", 2)
        runtime.acceptPublished(initial, PAINT, saved, 0)
        val reopened = runtime.reconcile(page(saved)).getValue(PAINT)
        assertSame("Visibility is presentation state, not a disk reload", live, reopened)
        assertEquals("Hidden layer restored erased pixels", 0, reopened.pixelAt(300, 300))
        assertEquals(1, loads)
        // An actual external undo still reloads the prior checkpoint.
        runtime.reconcile(initial)
        assertEquals(2, loads)
        assertEquals(Color.BLACK, runtime.surfaceForTests(PAINT)!!.pixelAt(300, 300))
    }

    @Test
    fun queuedCheckpointRecompositionsNeverRestoreErasedPixels() {
        var loads = 0
        val runtime = RasterLayerSurfaceRuntime(600) { _, _, _ ->
            opaqueSurface().also { loads++ }
        }
        val initial = page(reference("initial-opaque", 1))
        val live = runtime.reconcile(initial).getValue(PAINT)
        live.beginWrite("erase before queued checkpoints").use { write ->
            write.setPixel(300, 300, Color.TRANSPARENT)
            write.commit().close()
        }
        assertEquals(0, Color.alpha(live.pixelAt(300, 300)))

        val first = reference("local-first", 2)
        val second = reference("local-second", 3)
        runtime.acceptPublished(initial, PAINT, first, 0)
        runtime.acceptPublished(initial, PAINT, second, 0)

        assertSame(live, runtime.reconcile(page(first)).getValue(PAINT))
        assertEquals("Intermediate checkpoint restored erased pigment", 0, Color.alpha(live.pixelAt(300, 300)))
        assertSame(live, runtime.reconcile(page(second)).getValue(PAINT))
        assertEquals(0, Color.alpha(live.pixelAt(300, 300)))
        assertEquals("No local checkpoint may trigger a disk reload", 1, loads)
    }

    @Test
    fun manyStrokesSurviveCheckpointPublicationAndImportedBrushSwitch() {
        val seed = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val live = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        var loads = 0
        val runtime = RasterLayerSurfaceRuntime(600) { _, _, _ -> live.also { loads++ } }
        val initial = page(reference("initial", 0))
        val firstMap = runtime.reconcile(initial)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tipFile = File(context.cacheDir, "runtime-white-alpha-tip.png")
        writeWhiteAlphaTip(tipFile)
        val view = InkCanvasView(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.layout(0, 0, 400, 400)
            view.fingerDrawingEnabled = true
            view.tool = EditorTool.PEN
            view.activeLayerId = PAINT
            view.installV5TestInk(size = 24f)
            view.setRasterLayerSurfaces(firstMap)
            view.onRasterLayerCommitted = { }
        }

        repeat(12) { index ->
            val y = 45f + index * 22f
            send(view, MotionEvent.ACTION_DOWN, 40f, y, index * 10L)
            send(view, MotionEvent.ACTION_MOVE, 160f, y, index * 10L + 3L)
            send(view, MotionEvent.ACTION_UP, 280f, y, index * 10L + 6L)
        }
        settlePublishedStrokes(view)
        val alphaBeforeSwitch = live.snapshot().tileInfo.sumOf { it.nonTransparentPixelCount }
        assertTrue(alphaBeforeSwitch > 0)

        val saved = reference("saved", 12)
        runtime.acceptPublished(initial, PAINT, saved, 0)
        val afterPublication = runtime.reconcile(page(saved))
        assertSame(live, afterPublication.getValue(PAINT))
        assertEquals("Checkpoint publication must not reload a stale projection", 1, loads)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.setRasterLayerSurfaces(afterPublication)
            view.installV5TestInk(size = 24f, tipAssetPaths = listOf(tipFile.absolutePath))
        }
        send(view, MotionEvent.ACTION_DOWN, 200f, 330f, 200L)
        send(view, MotionEvent.ACTION_UP, 260f, 330f, 206L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.installV5TestInk(size = 24f)
        }
        send(view, MotionEvent.ACTION_DOWN, 200f, 360f, 220L)
        send(view, MotionEvent.ACTION_UP, 260f, 360f, 226L)
        settlePublishedStrokes(view)

        val alphaAfterSwitch = live.snapshot().tileInfo.sumOf { it.nonTransparentPixelCount }
        assertTrue("Switching an imported preset must preserve all earlier live pixels", alphaAfterSwitch >= alphaBeforeSwitch)
        assertTrue("The transparent-white imported tip must deposit pigment", Color.alpha(live.pixelAt(345, 495)) > 0)
        tipFile.delete()
    }

    private fun settlePublishedStrokes(view: InkCanvasView) {
        view.awaitV5StrokeWorkerIdleForTest()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
    }

    private fun page(reference: RasterTileSurfaceRef?) = InkPage(
        layers = listOf(
            InkLayer("background", "Background", LayerRole.BACKGROUND),
            InkLayer(PAINT, "Notes", LayerRole.INK, rasterTileSurface = reference),
        ),
    )

    private fun reference(name: String, revision: Long) = RasterTileSurfaceRef(
        manifestAssetPath = "/tmp/$name.json",
        surfaceId = name,
        logicalWidth = 600,
        logicalHeight = 600,
        tileSize = 128,
        revision = revision,
    )

    private fun send(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .1f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) }
        } finally {
            event.recycle()
        }
    }

    private fun writeWhiteAlphaTip(file: File) {
        val pixels = IntArray(64) { index ->
            val x = index % 8
            val y = index / 8
            if (x in 2..5 && y in 2..5) Color.argb(220, 255, 255, 255) else Color.TRANSPARENT
        }
        Bitmap.createBitmap(pixels, 8, 8, Bitmap.Config.ARGB_8888).also { bitmap ->
            file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            bitmap.recycle()
        }
    }

    private fun opaqueSurface() = PixelTileSurface(600, 600).also { surface ->
        surface.beginWrite("opaque persistence fixture").use { write ->
            for (row in 0 until 600) for (column in 0 until 600) {
                write.setPixel(column, row, Color.BLACK)
            }
            write.commit().close()
        }
    }

    private companion object { const val PAINT = "paint" }
}
