package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.LiquifyMode
import app.inkloom.engine.v3.tools.RasterTileLiquifySession
import app.inkloom.engine.v3.tools.RasterAbyssPolicy
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterLiquifySessionTest {
    @Test fun surfaceOnlyLiquifyOnEightKAllocatesOnlyLocalTiles() {
        val surface = PixelTileSurface(8192, 8192)
        surface.beginWrite("seed local pigment").use { seed ->
            for (y in 3980 until 4020) for (x in 3960 until 4000) seed.setPixel(x, y, Color.RED)
            seed.commit().close()
        }
        RasterTileLiquifySession(surface).use { session ->
            assertTrue(session.deform(3980f, 4000f, 4020f, 4000f, 48f, .8f, .5f, LiquifyMode.PUSH) != null)
            assertTrue(session.touchedTileCount in 1..9)
            assertTrue(session.workingByteSize < 1_000_000L)
            assertTrue("Liquify preview must differ from the published generation", session.previewSnapshot().tileInfo != surface.snapshot().tileInfo)
            session.commit().close()
        }
        assertTrue(surface.tileCount < 16)
    }

    @Test fun tileLiquifyCommitsAndUndoesOneAtomicDeformation() {
        val display = fixture()
        val original = checksum(display)
        val surface = PixelTileBitmapAdapter.import(display)
        val session = RasterTileLiquifySession(surface)
        assertTrue(session.deform(75f, 90f, 135f, 90f, 68f, 1f, .3f, LiquifyMode.PUSH) != null)
        assertEquals(RasterAbyssPolicy.CLAMP_TO_EDGE, session.lastRoiPlan?.abyssPolicy)
        assertNotEquals(original, checksum(session.previewSnapshot()))
        val unpublished = PixelTileBitmapAdapter.render(surface.snapshot())
        assertEquals("Published tiles stay at gesture-start until commit", original, checksum(unpublished))
        unpublished.recycle()

        val record = session.commit()
        val committed = PixelTileBitmapAdapter.render(surface.snapshot())
        assertNotEquals(original, checksum(committed))
        committed.recycle()
        surface.apply(record, HistoryDirection.UNDO)
        val undone = PixelTileBitmapAdapter.render(surface.snapshot())
        assertEquals(original, checksum(undone))
        undone.recycle()
        record.close()
        session.close()
        display.recycle()
    }

    @Test fun penDownDoesNotCopyThePageAndFirstDeformBacksUpOnlyDirtyTiles() {
        val bitmap = Bitmap.createBitmap(4096, 4096, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(80, 100, 120))
        }
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileLiquifySession(surface).use { session ->
            assertEquals(0L, session.workingByteSize)
            session.deform(200f, 200f, 230f, 210f, 24f, .8f, .7f, LiquifyMode.PUSH)
            assertTrue(session.workingByteSize in 1..(4L * 128 * 128 * 4))
            assertTrue(session.displacementTileCount in 1..4)
            assertTrue(session.displacementByteSize <= 4L * 64 * 64 * 2 * Float.SIZE_BYTES)
        }
        bitmap.recycle()
    }

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

    private fun checksum(surface: PixelTileSurface): Long = checksum(surface.snapshot())

    private fun checksum(snapshot: PixelTileSurfaceSnapshot): Long {
        val bitmap = PixelTileBitmapAdapter.render(snapshot)
        return try { checksum(bitmap) } finally { bitmap.recycle() }
    }

    private fun checksumRows(surface: PixelTileSurface, top: Int, bottom: Int): Long {
        val bitmap = PixelTileBitmapAdapter.render(surface.snapshot())
        return try { checksumRows(bitmap, top, bottom) } finally { bitmap.recycle() }
    }

    @Test fun everyRasterLiquifyModeProducesDeterministicLocalPixelDeformation() {
        val deformingModes = LiquifyMode.entries - LiquifyMode.RECONSTRUCT
        deformingModes.forEach { mode ->
            fun result(): Long {
                val bitmap = fixture()
                val before = checksum(bitmap)
                val surface = PixelTileBitmapAdapter.import(bitmap)
                RasterTileLiquifySession(surface).use { session ->
                    val previousX = if (mode in setOf(LiquifyMode.TWIRL_CLOCKWISE, LiquifyMode.TWIRL_COUNTERCLOCKWISE, LiquifyMode.PINCH, LiquifyMode.EXPAND, LiquifyMode.CRYSTALS)) 120f else 82f
                    val dirty = session.deform(previousX, 90f, 130f, 90f, 64f, .8f, .45f, mode)
                    assertTrue("$mode must report a dirty local region", dirty != null)
                    session.commit().close()
                }
                val after = checksum(surface)
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
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileLiquifySession(surface).use { session ->
            session.deform(75f, 90f, 135f, 90f, 68f, 1f, .3f, LiquifyMode.PUSH)
            val deformed = checksum(session.previewSnapshot())
            assertNotEquals(original, deformed)
            repeat(8) { session.deform(130f, 90f, 130f, 90f, 80f, 1f, .2f, LiquifyMode.RECONSTRUCT) }
            assertNotEquals("Reconstruct is local and need not reset pixels outside its brush", deformed, checksum(session.previewSnapshot()))
        }
        bitmap.recycle()
    }

    @Test fun longInputSegmentIsDistanceSampledOnceAndLaterInputOnlyAppendsItsTail() {
        val bitmap = fixture()
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileLiquifySession(surface).use { session ->
            session.deform(60f, 90f, 150f, 90f, 42f, .8f, .4f, LiquifyMode.PUSH)
            val prefixCount = session.processedStampCount
            assertTrue(prefixCount > 1)
            session.deform(150f, 90f, 154f, 90f, 42f, .8f, .4f, LiquifyMode.PUSH)
            assertEquals(prefixCount + 1, session.processedStampCount)
        }
        bitmap.recycle()
    }

    @Test fun selectionAlphaClipsEveryLiquifyCompositeToTheActiveRegion() {
        val bitmap = fixture()
        val originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val selection = app.inkloom.engine.v5.surface.MaskTileSurface(48, 36, tileSize = 64).also { mask ->
            mask.beginWrite("upper selection").run {
                composeAlpha(
                    app.inkloom.engine.v5.surface.PixelRect(0, 0, 48, 18),
                    ByteArray(48 * 18) { 255.toByte() },
                    mode = app.inkloom.engine.v5.surface.MaskComposeMode.REPLACE,
                )
                commit().close()
            }
        }
        val upperBefore = checksumRows(bitmap, 0, 90)
        val lowerBefore = checksumRows(bitmap, 90, bitmap.height)

        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileLiquifySession(surface, selection.snapshot()).use { session ->
            assertTrue(session.deform(82f, 90f, 130f, 90f, 64f, .8f, .45f, LiquifyMode.PUSH) != null)
            session.commit().close()
        }

        assertNotEquals("Selected pixels must deform", upperBefore, checksumRows(surface, 0, 90))
        val rendered = PixelTileBitmapAdapter.render(surface.snapshot())
        val firstChangedOutside = (90 until bitmap.height).firstNotNullOfOrNull { y ->
            (0 until bitmap.width).firstOrNull { x -> rendered.getPixel(x, y) != originalBitmap.getPixel(x, y) }?.let { x -> x to y }
        }
        assertEquals("Pixels outside the selection must remain byte-identical", null, firstChangedOutside)
        assertEquals(lowerBefore, checksumRows(surface, 90, bitmap.height))
        rendered.recycle()
        originalBitmap.recycle()
        bitmap.recycle()
    }

    @Test fun rasterLiquifyLocalFrameMeetsRealtimeBudget() {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(110, 130, 170)) }
        val times = ArrayList<Long>(96)
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileLiquifySession(surface).use { session ->
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
