package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v3.android.PixelTileBitmapAdapter
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileSurface.HistoryDirection
import app.inkloom.engine.v3.tools.RasterTileEditSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterTileEditSessionTest {
    @Test fun noOpScratchRoundTripDoesNotDriftPremultipliedPixels() {
        val display = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        for (y in 0 until display.height) for (x in 0 until display.width) {
            display.setPixel(x, y, Color.argb((17 + x + y) % 255, (x * 7) % 255, (y * 11) % 255, (x + y * 3) % 255))
        }
        val surface = PixelTileBitmapAdapter.import(display)
        val before = surface.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 192, 192))
        val record = RasterTileEditSession(surface, display, "no-op round trip").use { session ->
            session.edit(PixelRect(23, 29, 171, 177)) { }
            session.commit()
        }
        val after = surface.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 192, 192))
        assertTrue("Premultiplied pixels must survive an Android scratch round trip", before.contentEquals(after))
        assertEquals(0, record.changedTileCount)
        record.close()
        display.recycle()
    }

    @Test fun editCommitsOnlyTouchedTilesAndProducesAnExactUndoRecord() {
        val display = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val surface = PixelTileBitmapAdapter.import(display)
        val bounds = PixelRect(110, 120, 190, 210)
        val session = RasterTileEditSession(surface, display, "tile paint")

        session.edit(bounds) { scratch ->
            Canvas(scratch).drawColor(Color.BLUE)
        }
        assertEquals("Display mirror updates before commit", Color.BLUE, display.getPixel(150, 160))
        assertEquals("Published tile surface stays atomic", Color.RED, surface.pixelAt(150, 160))
        assertTrue("Only intersecting tiles are copied", session.touchedTileCount <= 4)

        val record = session.commit()
        assertEquals(Color.BLUE, surface.pixelAt(150, 160))
        assertEquals(Color.RED, surface.pixelAt(40, 40))
        surface.apply(record, HistoryDirection.UNDO)
        assertEquals(Color.RED, surface.pixelAt(150, 160))
        record.close()
        display.recycle()
    }

    @Test fun cancelRestoresTheDisplayMirrorAndLeavesTheSurfaceUnpublished() {
        val display = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val surface = PixelTileBitmapAdapter.import(display)
        RasterTileEditSession(surface, display, "cancelled tile edit").use { session ->
            session.edit(PixelRect(20, 30, 100, 110)) { scratch ->
                Canvas(scratch).drawRect(0f, 0f, scratch.width.toFloat(), scratch.height.toFloat(), Paint().apply { color = Color.MAGENTA })
            }
            assertEquals(Color.MAGENTA, display.getPixel(50, 50))
        }

        assertEquals(Color.GREEN, display.getPixel(50, 50))
        assertEquals(Color.GREEN, surface.pixelAt(50, 50))
        display.recycle()
    }

    @Test fun deferredMirrorPublishesOnlyAfterTheAtomicTileCommit() {
        val display = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val surface = PixelTileBitmapAdapter.import(display)
        val session = RasterTileEditSession(
            surface,
            display,
            "front-buffered paint",
            mirrorWrites = false,
            publishDirtyOnCommit = true,
        )
        session.edit(PixelRect(110, 120, 190, 210)) { scratch -> Canvas(scratch).drawColor(Color.BLUE) }

        assertEquals("The stable display must not double-composite live front-buffer ink", Color.RED, display.getPixel(150, 160))
        assertEquals("The published surface remains atomic until commit", Color.RED, surface.pixelAt(150, 160))
        val record = session.commit()
        assertEquals(Color.BLUE, surface.pixelAt(150, 160))
        assertEquals("Commit publishes just the dirty projection into the stable display", Color.BLUE, display.getPixel(150, 160))
        record.close(); session.close(); display.recycle()
    }
}
