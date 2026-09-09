package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.tools.RasterTileBlendSession
import app.inkloom.engine.v5.tools.RasterAbyssPolicy
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterBlendSessionTest {
    @Test fun surfaceOnlyBlendOnEightKUsesLocalTransactionStorage() {
        val surface = PixelTileSurface(8192, 8192)
        surface.beginWrite("seed local pigment").use { seed ->
            for (y in 3980 until 4020) for (x in 3940 until 3990) seed.setPixel(x, y, Color.BLUE)
            seed.commit().close()
        }
        RasterTileBlendSession(surface).use { session ->
            assertTrue(session.blend(3970f, 4000f, 4020f, 4000f, 44f, 1f, .7f) != null)
            assertTrue(session.touchedTileCount in 1..9)
            assertTrue(session.workingByteSize < 1_000_000L)
            assertEquals("Published surface stays atomic", Color.TRANSPARENT, surface.pixelAt(4020, 4000))
            assertTrue("Sparse preview exposes transported pigment", Color.alpha(session.previewSnapshot().pixelAt(4020, 4000)) > 0)
            session.commit().close()
        }
        assertTrue(Color.alpha(surface.pixelAt(4020, 4000)) > 0)
        assertTrue(surface.tileCount < 16)
    }

    @Test fun tileBlendSamplesEarlierGesturePixelsAndCommitsOneUndoDelta() {
        val display = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
        for (y in 16 until 48) for (x in 10 until 42) display.setPixel(x, y, Color.argb(200, 240, 20, 30))
        val surface = PixelTileBitmapAdapter.import(display)
        val session = RasterTileBlendSession(surface)

        session.blend(34f, 32f, 66f, 32f, 24f, 1f, .8f)
        assertEquals(RasterAbyssPolicy.TRANSPARENT, session.lastRoiPlan?.abyssPolicy)
        val preview = session.previewSnapshot().pixelAt(66, 32)
        assertTrue(Color.alpha(preview) > 100)
        assertEquals("Published tiles remain unchanged before the gesture commits", Color.TRANSPARENT, surface.pixelAt(66, 32))

        val record = session.commit()
        assertTrue(Color.alpha(surface.pixelAt(66, 32)) > 100)
        surface.apply(record, HistoryDirection.UNDO)
        assertEquals(Color.TRANSPARENT, surface.pixelAt(66, 32))
        record.close()
        session.close()
        display.recycle()
    }

    @Test fun penDownDoesNotCopyThePageAndFirstBlendBacksUpOnlyDirtyTiles() {
        val bitmap = Bitmap.createBitmap(4096, 4096, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(80, 100, 120))
        }
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileBlendSession(surface).use { session ->
            assertEquals(0L, session.workingByteSize)
            session.blend(200f, 200f, 230f, 210f, 24f, .8f, .7f)
            assertTrue(session.workingByteSize in 1..(4L * 128 * 128 * 4))
            assertEquals(64, session.pigmentBucketCount)
            assertEquals(64L * 8 * Float.SIZE_BYTES, session.pigmentReservoirByteSize)
        }
        bitmap.recycle()
    }

    @Test fun smudgePullsExistingPremultipliedPigmentWithoutAddingBrushColor() {
        val bitmap = Bitmap.createBitmap(96, 48, Bitmap.Config.ARGB_8888)
        for (y in 12 until 36) for (x in 10 until 34) bitmap.setPixel(x, y, Color.argb(180, 240, 20, 30))
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileBlendSession(surface).use { session ->
            val dirty = session.blend(26f, 24f, 48f, 24f, 18f, 1f, .75f)
            assertTrue(dirty != null)
            session.commit().close()
        }
        val pulled = surface.pixelAt(48, 24)
        assertTrue("Pigment is pulled into the destination", Color.alpha(pulled) > 100)
        assertTrue("The sampled red pigment is preserved", Color.red(pulled) > 200)
        assertTrue("No active brush color is injected", Color.blue(pulled) < 80)
        bitmap.recycle()
    }

    @Test fun spatialBucketsCarryRedAndBlueWithoutCollapsingThemIntoOneAverageColor() {
        val bitmap = Bitmap.createBitmap(128, 80, Bitmap.Config.ARGB_8888)
        for (y in 12 until 40) for (x in 12 until 42) bitmap.setPixel(x, y, Color.RED)
        for (y in 40 until 68) for (x in 12 until 42) bitmap.setPixel(x, y, Color.BLUE)
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileBlendSession(surface).use { session ->
            session.blend(30f, 40f, 78f, 40f, 28f, 1f, .8f)
            session.commit().close()
        }
        val upper = surface.pixelAt(78, 28)
        val lower = surface.pixelAt(78, 52)
        assertTrue("Upper carried bucket must remain red: ${Integer.toHexString(upper)}", Color.red(upper) > Color.blue(upper))
        assertTrue("Lower carried bucket must remain blue: ${Integer.toHexString(lower)}", Color.blue(lower) > Color.red(lower))
        bitmap.recycle()
    }

    @Test fun carriedPigmentPersistsAcrossTransparentTravelButNeverUsesBrushColor() {
        val bitmap = Bitmap.createBitmap(160, 64, Bitmap.Config.ARGB_8888)
        for (y in 18 until 46) for (x in 10 until 38) bitmap.setPixel(x, y, Color.GREEN)
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileBlendSession(surface).use { session ->
            session.blend(26f, 32f, 62f, 32f, 22f, 1f, .7f)
            session.blend(62f, 32f, 98f, 32f, 22f, 1f, .7f)
            session.commit().close()
        }
        val carried = surface.pixelAt(96, 32)
        assertTrue(Color.alpha(carried) > 0)
        assertTrue(Color.green(carried) > Color.red(carried))
        assertTrue(Color.green(carried) > Color.blue(carried))
        bitmap.recycle()
    }

    @Test fun oneFastCoalescedInputProducesAContinuousSmudgeCapsule() {
        val bitmap = Bitmap.createBitmap(256, 64, Bitmap.Config.ARGB_8888)
        for (y in 18 until 46) for (x in 8 until 38) bitmap.setPixel(x, y, Color.MAGENTA)
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileBlendSession(surface).use { session ->
            val startedCpu = android.os.Debug.threadCpuTimeNanos()
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            val dirty = session.blend(26f, 32f, 220f, 32f, 18f, 1f, .72f)
            val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
            val cpuMs = (android.os.Debug.threadCpuTimeNanos() - startedCpu) / 1_000_000f
            android.util.Log.i("InkloomSmudgePerf", "194px capsule wall=${elapsedMs}ms cpu=${cpuMs}ms")
            assertTrue(dirty != null)
            assertTrue("A 194px coalesced smudge segment took ${elapsedMs}ms", elapsedMs < 16.7f)
            session.commit().close()
        }
        for (x in 40..216 step 8) {
            assertTrue("Fast Blend left a gap at x=$x", Color.alpha(surface.pixelAt(x, 32)) > 0)
        }
        bitmap.recycle()
    }

    @Test fun selectionAlphaClipsBlendAndCancellationRestoresTheSurface() {
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        for (y in 8 until 32) for (x in 8 until 30) bitmap.setPixel(x, y, Color.GREEN)
        val original = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val selection = app.inkloom.engine.v5.surface.MaskTileSurface(20, 10, tileSize = 64).also { mask ->
            mask.beginWrite("upper selection").run {
                composeAlpha(
                    app.inkloom.engine.v5.surface.PixelRect(0, 0, 20, 5),
                    ByteArray(20 * 5) { 255.toByte() },
                    mode = app.inkloom.engine.v5.surface.MaskComposeMode.REPLACE,
                )
                commit().close()
            }
        }
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileBlendSession(surface, selection.snapshot()).use { session ->
            session.blend(24f, 20f, 46f, 20f, 18f, 1f, 1f)
            val preview = session.previewSnapshot()
            assertTrue(Color.alpha(preview.pixelAt(46, 14)) > 0)
            assertEquals("Pixels outside the selection stay untouched", Color.TRANSPARENT, preview.pixelAt(46, 28))
            session.restoreOriginal()
            assertEquals(original.getPixel(46, 14), surface.pixelAt(46, 14))
        }
        original.recycle()
        bitmap.recycle()
    }

    @Test fun alphaLockPreservesTheExactDestinationAlphaBoundaryDuringBlend() {
        val bitmap = Bitmap.createBitmap(128, 80, Bitmap.Config.ARGB_8888)
        for (y in 22 until 58) for (x in 12 until 42) bitmap.setPixel(x, y, Color.argb(120, 220, 30, 40))
        val beforeAlpha = IntArray(bitmap.width * bitmap.height) { index ->
            Color.alpha(bitmap.getPixel(index % bitmap.width, index / bitmap.width))
        }
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileBlendSession(surface, preserveDestinationAlpha = true).use { session ->
            session.blend(30f, 40f, 78f, 40f, 28f, 1f, .6f)
            session.commit().close()
        }
        val afterAlpha = IntArray(bitmap.width * bitmap.height) { index ->
            Color.alpha(surface.pixelAt(index % bitmap.width, index / bitmap.width))
        }
        assertEquals(beforeAlpha.toList(), afterAlpha.toList())
        bitmap.recycle()
    }

    @Test fun localRasterBlendStaysInsideAFrameBudget() {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 120, 180)) }
        val frameTimes = ArrayList<Long>(96)
        val surface = PixelTileBitmapAdapter.import(bitmap)
        RasterTileBlendSession(surface).use { session ->
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
