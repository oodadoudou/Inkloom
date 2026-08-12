package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v2.render.RasterBlendSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterBlendSessionTest {
    @Test fun smudgePullsExistingPremultipliedPigmentWithoutAddingBrushColor() {
        val bitmap = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888)
        for (y in 12 until 36) for (x in 10 until 34) bitmap.setPixel(x, y, Color.argb(180, 240, 20, 30))
        RasterBlendSession(bitmap).use { session ->
            val dirty = session.blend(26f, 24f, 48f, 24f, 18f, 1f, .75f)
            assertTrue(dirty != null)
        }
        val pulled = bitmap.getPixel(48, 24)
        assertTrue("Pigment is pulled into the destination", Color.alpha(pulled) > 100)
        assertTrue("The sampled red pigment is preserved", Color.red(pulled) > 200)
        assertTrue("No active brush color is injected", Color.blue(pulled) < 80)
        bitmap.recycle()
    }

    @Test fun selectionAlphaClipsBlendAndCancellationRestoresTheSurface() {
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        for (y in 8 until 32) for (x in 8 until 30) bitmap.setPixel(x, y, Color.GREEN)
        val original = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val selection = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) for (x in 0 until 80) selection.setPixel(x, y, Color.WHITE)
        RasterBlendSession(bitmap, selection).use { session ->
            session.blend(24f, 20f, 46f, 20f, 18f, 1f, 1f)
            assertTrue(Color.alpha(bitmap.getPixel(46, 14)) > 0)
            assertEquals("Pixels outside the selection stay untouched", Color.TRANSPARENT, bitmap.getPixel(46, 28))
            session.restoreOriginal()
            assertEquals(original.getPixel(46, 14), bitmap.getPixel(46, 14))
        }
        selection.recycle()
        original.recycle()
        bitmap.recycle()
    }

    @Test fun localRasterBlendStaysInsideAFrameBudget() {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 120, 180)) }
        val frameTimes = ArrayList<Long>(96)
        RasterBlendSession(bitmap).use { session ->
            var previousX = 180f
            repeat(96) { index ->
                val nextX = previousX + 3f
                val nextY = 430f + index % 7
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                session.blend(previousX, 430f + (index - 1).coerceAtLeast(0) % 7, nextX, nextY, 72f, .7f, .55f)
                frameTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
                previousX = nextX
            }
        }
        val sorted = frameTimes.drop(8).sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "raster_blend_local_p95_ms=$p95Ms")
        assertTrue("Raster Blend p95 frame was ${p95Ms}ms", p95Ms < 16.7f)
        bitmap.recycle()
    }
}
