package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.tools.RasterTileEraserSession
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterEraserSessionTest {
    @Test fun surfaceOnlyEraserUsesBoundedTilesAndPublishesWithoutADisplayMirror() {
        val surface = PixelTileSurface(8192, 8192)
        surface.beginWrite("seed local pigment").use { seed ->
            for (y in 3950 until 4050) for (x in 3950 until 4050) seed.setPixel(x, y, Color.BLACK)
            seed.commit().close()
        }
        val originalTiles = surface.tileCount
        RasterTileEraserSession(surface).use { session ->
            assertTrue(session.eraseSegment(3980f, 4000f, 4020f, 4000f, 24f, 1f, 1f) != null)
            assertTrue(session.touchedTileCount in 1..4)
            assertEquals("Published pixels stay atomic during preview", 255, Color.alpha(surface.pixelAt(4000, 4000)))
            assertEquals("Sparse preview exposes live erasing", 0, Color.alpha(session.previewSnapshot().pixelAt(4000, 4000)))
            session.commit().close()
        }
        assertEquals(0, Color.alpha(surface.pixelAt(4000, 4000)))
        assertTrue(surface.tileCount <= originalTiles + 4)
    }

    @Test fun tileEraserPublishesOneUndoableSparseTransaction() {
        val display = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val surface = PixelTileBitmapAdapter.import(display)
        val session = RasterTileEraserSession(surface)
        session.eraseSegment(420f, 500f, 600f, 500f, 48f, 1f, 1f)

        assertEquals("The private sparse preview is real-time", 0, Color.alpha(session.previewSnapshot().pixelAt(500, 500)))
        assertEquals("The authoritative surface publishes atomically", 255, Color.alpha(surface.pixelAt(500, 500)))
        assertTrue("A local eraser cannot touch the whole 8x8 tile grid", session.touchedTileCount < 64)

        val record = session.commit()
        assertEquals(0, Color.alpha(surface.pixelAt(500, 500)))
        surface.apply(record, HistoryDirection.UNDO)
        assertEquals(255, Color.alpha(surface.pixelAt(500, 500)))
        record.close()
        session.close()
        display.recycle()
    }

    @Test fun backupIsEmptyAtPenDownAndBoundedToFirstWrittenTiles() {
        val bitmap = Bitmap.createBitmap(4096, 4096, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        val surface = PixelTileBitmapAdapter.import(bitmap)
        val session = RasterTileEraserSession(surface)

        assertEquals(0L, session.workingByteSize)
        session.eraseSegment(200f, 200f, 240f, 240f, 20f, 1f, 1f)

        assertTrue(session.workingByteSize in 1..(4L * 128 * 128 * 4))
        session.close()
        bitmap.recycle()
    }

    @Test fun sparseBackupRestoresOpaqueAndTransparentPixelsExactly() {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply {
            setPixel(120, 100, Color.RED)
        }
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileEraserSession(surface).use { session ->
            session.eraseSegment(100f, 100f, 140f, 100f, 30f, 1f, 1f)
            assertEquals(0, Color.alpha(session.previewSnapshot().pixelAt(120, 100)))
            session.restoreOriginal()
            assertEquals(Color.RED, surface.pixelAt(120, 100))
            assertEquals(0, Color.alpha(surface.pixelAt(100, 80)))
        }
        bitmap.recycle()
    }

    @Test fun softEraserPreservesPartialAlphaAndSelectionIsolation() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val selection = app.inkloom.engine.v5.surface.MaskTileSurface(40, 20, tileSize = 64).also { mask ->
            mask.beginWrite("upper selection").run {
                composeAlpha(
                    app.inkloom.engine.v5.surface.PixelRect(0, 0, 40, 10),
                    ByteArray(40 * 10) { 255.toByte() },
                    mode = app.inkloom.engine.v5.surface.MaskComposeMode.REPLACE,
                )
                commit().close()
            }
        }
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileEraserSession(surface, selection.snapshot()).use { session ->
            session.eraseSegment(30f, 50f, 130f, 50f, 24f, .65f, .35f)
            session.commit().close()
        }
        assertTrue("Soft eraser creates partial alpha", Color.alpha(surface.pixelAt(80, 38)) in 1..254)
        assertEquals("Selection excludes the lower half", 255, Color.alpha(surface.pixelAt(80, 62)))
        bitmap.recycle()
    }

    @Test fun rasterEraserGestureCommitsOneSurfaceAndKeepsOtherLayersOut() {
        val seed = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val surface = PixelTileBitmapAdapter.import(seed)
        seed.recycle()
        var commits = 0
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 400, 400); fingerDrawingEnabled = true; tool = EditorTool.ERASER
                activeLayerId = "pixels"; eraserWidth = 180f; eraserHardness = 1f; eraserOpacity = 1f
                setRasterLayerSurfaces(mapOf("pixels" to surface))
                onRasterLayerCommitted = { commit -> assertEquals("pixels", commit.layerId); commits++ }
            }
        }
        send(view, MotionEvent.ACTION_DOWN, 80f, 200f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 320f, 200f, 20L)
        send(view, MotionEvent.ACTION_UP, 320f, 200f, 40L)
        assertEquals(1, commits)
        assertEquals(0, Color.alpha(surface.pixelAt(200, 200)))
    }

    @Test fun productionSurfaceEraserPresentsPrivatePixelsBeforeActionUp() {
        val surface = PixelTileSurface(400, 400)
        surface.beginWrite("seed visible area").use { seed ->
            for (y in 120 until 280) for (x in 120 until 280) seed.setPixel(x, y, Color.BLACK)
            seed.commit().close()
        }
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 400, 400)
                fingerDrawingEnabled = true
                tool = EditorTool.ERASER
                activeLayerId = "pixels"
                eraserWidth = 180f
                eraserHardness = 1f
                eraserOpacity = 1f
                setRasterLayerSurfaces(mapOf("pixels" to surface))
            }
        }

        send(view, MotionEvent.ACTION_DOWN, 160f, 200f, 0L)
        send(view, MotionEvent.ACTION_MOVE, 240f, 200f, 20L)
        assertEquals("Private gesture must not publish before ACTION_UP", 255, Color.alpha(surface.pixelAt(200, 200)))

        val frame = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(frame)) }
            assertEquals("Stable canvas must present the sparse private eraser transaction", 0, Color.alpha(frame.getPixel(200, 200)))
        } finally {
            frame.recycle()
        }

        send(view, MotionEvent.ACTION_UP, 240f, 200f, 40L)
        assertEquals(0, Color.alpha(surface.pixelAt(200, 200)))
    }

    @Test fun rasterEraserSegmentMeetsRealtimeBudget() {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val times = ArrayList<Long>(96)
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileEraserSession(surface).use { session ->
            var x = 180f
            repeat(96) { index ->
                val next = x + 4f
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                session.eraseSegment(x, 450f, next, 450f + index % 5, 72f, .55f, .5f)
                times += android.os.SystemClock.elapsedRealtimeNanos() - started
                x = next
            }
        }
        val sorted = times.drop(8).sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "raster_eraser_local_p95_ms=$p95Ms")
        assertTrue("Raster Eraser p95 frame was ${p95Ms}ms", p95Ms < 16.7f)
        bitmap.recycle()
    }

    private fun send(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
    }
}
