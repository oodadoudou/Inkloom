package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.os.SystemClock
import android.util.Log
import app.inkloom.engine.v5.tools.RasterLayerPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterLayerPatchTest {
    @Test fun captureOwnsOnlyTheClippedDirtyRectangle() {
        val source = Bitmap.createBitmap(4096, 4096, Bitmap.Config.ARGB_8888)
        source.setPixel(12, 18, Color.MAGENTA)

        val patch = requireNotNull(RasterLayerPatch.capture("paint", source, Rect(-4, 10, 28, 34)))
        assertEquals(Rect(0, 10, 28, 34), patch.bounds)
        assertEquals(4096, patch.sourceWidth)
        assertEquals(4096, patch.sourceHeight)
        assertEquals(Color.MAGENTA, patch.bitmap.getPixel(12, 8))
        assertTrue("A local commit must not retain a 4K bitmap", patch.bitmap.byteCount <= 28 * 24 * 4)

        source.setPixel(12, 18, Color.BLUE)
        assertEquals("The patch is immutable after ownership transfer", Color.MAGENTA, patch.bitmap.getPixel(12, 8))
        patch.close()
        assertTrue(patch.bitmap.isRecycled)
        source.recycle()
    }

    @Test fun emptyOrOutOfBoundsDirtyRectangleDoesNotAllocate() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        assertNull(RasterLayerPatch.capture("paint", source, Rect(80, 80, 90, 90)))
        assertFalse(source.isRecycled)
        source.recycle()
    }

    @Test fun localPatchCaptureMeetsPenUpFrameBudgetOnA4kLayer() {
        val source = Bitmap.createBitmap(4096, 4096, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val samples = ArrayList<Long>(80)
        repeat(80) { index ->
            val left = 100 + index * 17
            val started = SystemClock.elapsedRealtimeNanos()
            RasterLayerPatch.capture("paint", source, Rect(left, 1800, left + 192, 1992))?.close()
            samples += SystemClock.elapsedRealtimeNanos() - started
        }
        val sorted = samples.drop(8).sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        Log.i("InkloomPerf", "raster_dirty_patch_capture_4k_p95_ms=$p95Ms bytes=${192 * 192 * 4}")
        assertTrue("4K dirty-patch capture p95 was ${p95Ms}ms", p95Ms < 8f)
        source.recycle()
    }
}
