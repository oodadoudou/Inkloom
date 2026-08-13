package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.LiquifyMode
import app.inkloom.engine.v3.tools.RasterLiquifySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterLiquifySessionTest {
    private fun fixture(): Bitmap = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (y in 55 until 125) for (x in 55 until 120) bitmap.setPixel(x, y, Color.argb(220, 240, 70, 20))
        for (y in 78 until 102) for (x in 120 until 165) bitmap.setPixel(x, y, Color.BLUE)
    }

    private fun checksum(bitmap: Bitmap): Long {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.foldIndexed(1125899906842597L) { index, hash, pixel -> hash * 31L + pixel.toLong() * (index + 1L) }
    }

    private fun checksumRows(bitmap: Bitmap, top: Int, bottom: Int): Long {
        var hash = 1125899906842597L
        for (y in top until bottom) for (x in 0 until bitmap.width) {
            hash = hash * 31L + bitmap.getPixel(x, y).toLong() * (y * bitmap.width + x + 1L)
        }
        return hash
    }

    @Test fun everyRasterLiquifyModeProducesDeterministicLocalPixelDeformation() {
        val deformingModes = LiquifyMode.entries - LiquifyMode.RECONSTRUCT
        deformingModes.forEach { mode ->
            fun result(): Long {
                val bitmap = fixture()
                val before = checksum(bitmap)
                RasterLiquifySession(bitmap).use { session ->
                    val previousX = if (mode in setOf(LiquifyMode.TWIRL_CLOCKWISE, LiquifyMode.TWIRL_COUNTERCLOCKWISE, LiquifyMode.PINCH, LiquifyMode.EXPAND, LiquifyMode.CRYSTALS)) 120f else 82f
                    val dirty = session.deform(previousX, 90f, 130f, 90f, 64f, .8f, .45f, mode)
                    assertTrue("$mode must report a dirty local region", dirty != null)
                }
                val after = checksum(bitmap)
                assertNotEquals("$mode must alter raster pixels", before, after)
                bitmap.recycle()
                return after
            }
            assertEquals("$mode must be deterministic", result(), result())
        }
    }

    @Test fun reconstructRestoresTouchedPixelsTowardTheGestureStartSnapshot() {
        val bitmap = fixture()
        val original = checksum(bitmap)
        RasterLiquifySession(bitmap).use { session ->
            session.deform(75f, 90f, 135f, 90f, 68f, 1f, .3f, LiquifyMode.PUSH)
            val deformed = checksum(bitmap)
            assertNotEquals(original, deformed)
            repeat(8) { session.deform(130f, 90f, 130f, 90f, 80f, 1f, .2f, LiquifyMode.RECONSTRUCT) }
            assertNotEquals("Reconstruct is local and need not reset pixels outside its brush", deformed, checksum(bitmap))
        }
        bitmap.recycle()
    }

    @Test fun selectionAlphaClipsEveryLiquifyCompositeToTheActiveRegion() {
        val bitmap = fixture()
        val originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val selection = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(selection).drawRect(0f, 0f, bitmap.width.toFloat(), 90f, Paint().apply { color = Color.WHITE })
        val upperBefore = checksumRows(bitmap, 0, 90)
        val lowerBefore = checksumRows(bitmap, 90, bitmap.height)

        RasterLiquifySession(bitmap, selection).use { session ->
            assertTrue(session.deform(82f, 90f, 130f, 90f, 64f, .8f, .45f, LiquifyMode.PUSH) != null)
        }

        assertNotEquals("Selected pixels must deform", upperBefore, checksumRows(bitmap, 0, 90))
        val firstChangedOutside = (90 until bitmap.height).firstNotNullOfOrNull { y ->
            (0 until bitmap.width).firstOrNull { x -> bitmap.getPixel(x, y) != originalBitmap.getPixel(x, y) }?.let { x -> x to y }
        }
        assertEquals("Pixels outside the selection must remain byte-identical", null, firstChangedOutside)
        assertEquals(lowerBefore, checksumRows(bitmap, 90, bitmap.height))
        originalBitmap.recycle()
        selection.recycle()
        bitmap.recycle()
    }

    @Test fun rasterLiquifyLocalFrameMeetsRealtimeBudget() {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(110, 130, 170)) }
        val times = ArrayList<Long>(96)
        RasterLiquifySession(bitmap).use { session ->
            var previousX = 250f
            repeat(96) { index ->
                val nextX = previousX + 3f
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                session.deform(previousX, 450f, nextX, 450f + index % 5, 72f, .7f, .5f, LiquifyMode.PUSH)
                times += android.os.SystemClock.elapsedRealtimeNanos() - started
                previousX = nextX
            }
        }
        val sorted = times.drop(8).sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "raster_liquify_local_p95_ms=$p95Ms")
        assertTrue("Raster Liquify p95 frame was ${p95Ms}ms", p95Ms < 16.7f)
        bitmap.recycle()
    }
}
