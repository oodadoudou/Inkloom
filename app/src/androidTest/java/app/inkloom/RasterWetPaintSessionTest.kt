package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.inkloom.engine.v3.android.PixelTileBitmapAdapter
import app.inkloom.engine.v3.surface.PixelTileSurface.HistoryDirection
import app.inkloom.engine.v3.tools.RasterTilePaintSession
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterWetPaintSessionTest {
    private fun paintSession(
        bitmap: Bitmap,
        selection: Bitmap? = null,
        preserveDestinationAlpha: Boolean = false,
    ) = RasterTilePaintSession(
        PixelTileBitmapAdapter.import(bitmap),
        bitmap,
        selection,
        preserveDestinationAlpha,
    )

    @Test fun wetTransportAndFinalBrushPatchPublishAsOneUndoableTransaction() {
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(38f, 48f, 14f, Paint().apply { color = Color.RED })
        val surface = PixelTileBitmapAdapter.import(bitmap)
        val session = RasterTilePaintSession(surface, bitmap)
        session.advanceWetTransport(38f, 48f, 70f, 48f, 20f, pull = 1f, blur = .2f, hardness = .75f)
        val patch = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawCircle(12f, 12f, 8f, Paint().apply { color = Color.BLUE })
        }
        val stroke = InkStroke(points = listOf(StrokePoint(.55f, .5f)))
        assertNotNull(session.compositePaintPatch(patch, 58, 36, listOf(stroke), wetComposite = false))
        assertEquals("Gesture pixels are private until the atomic commit", Color.TRANSPARENT, surface.pixelAt(70, 48))

        val record = session.commit()
        assertTrue(Color.alpha(surface.pixelAt(70, 48)) > 0)
        surface.apply(record, HistoryDirection.UNDO)
        assertEquals(Color.TRANSPARENT, surface.pixelAt(70, 48))

        record.close()
        session.close()
        patch.recycle()
        bitmap.recycle()
    }

    @Test fun beginningWetPaintDoesNotCopyTheWholePage() {
        val bitmap = Bitmap.createBitmap(4096, 4096, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(2048f, 2048f, 36f, Paint().apply { color = Color.BLUE })
        val surface = PixelTileBitmapAdapter.import(bitmap)
        val started = android.os.SystemClock.elapsedRealtimeNanos()
        RasterTilePaintSession(surface, bitmap).use { session ->
            val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
            assertTrue("Wet Mix pen-down initialization was ${elapsedMs}ms", elapsedMs < 8f)
            assertEquals("Pen-down must not allocate writable tiles", 0L, session.workingByteSize)
            assertNotNull(session.advanceWetTransport(2048f, 2048f, 2052f, 2048f, 24f, pull = 1f, blur = .3f, hardness = .5f))
            assertTrue("A local wet dab must not copy the page", session.workingByteSize <= 4L * 128 * 128 * Int.SIZE_BYTES)
        }
        bitmap.recycle()
    }

    @Test fun boundedWetFramesStayWithinTabletFrameBudget() {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(80f, 300f, 1120f, 600f, Paint().apply { color = Color.rgb(80, 120, 220) })
        val elapsed = ArrayList<Long>(80)
        paintSession(bitmap).use { session ->
            repeat(88) { index ->
                val x = 120f + index * 10f
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                session.advanceWetTransport(x, 450f, x + 10f, 452f, 28f, pull = .7f, blur = .35f, hardness = .55f)
                if (index >= 8) elapsed += android.os.SystemClock.elapsedRealtimeNanos() - started
            }
        }
        val sorted = elapsed.sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "wet_mix_frame_p95_ms=$p95Ms")
        assertTrue("Wet Mix frame p95 was ${p95Ms}ms", p95Ms < 16.7f)
        bitmap.recycle()
    }

    @Test fun pullTransportsExistingPigmentAlongTheLivePath() {
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(38f, 48f, 14f, Paint().apply { color = Color.RED })
        val before = Color.alpha(bitmap.getPixel(67, 48))

        paintSession(bitmap).use { session ->
            assertNotNull(session.advanceWetTransport(38f, 48f, 70f, 48f, 20f, pull = 1f, blur = 0f, hardness = .75f))
            session.commit().close()
        }

        assertTrue(Color.alpha(bitmap.getPixel(67, 48)) > before)
        bitmap.recycle()
    }

    @Test fun pickupWellSamplesExistingPigmentBeforePenUp() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(48f, 48f, 18f, Paint().apply { color = Color.rgb(32, 160, 224) })
        paintSession(bitmap).use { session ->
            val sampled = session.samplePigment(48f, 48f, 12f)
            assertTrue(Color.alpha(sampled) > 0)
            assertTrue(Color.blue(sampled) > Color.red(sampled))
        }
        bitmap.recycle()
    }

    @Test fun fixedRunningColorBlurUsesAbsolutePixelRadius() {
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(42f, 35f, 48f, 61f, Paint().apply { color = Color.BLUE })
        paintSession(bitmap).use { session ->
            assertNotNull(session.advanceWetTransport(48f, 48f, 49f, 48f, 28f, pull = 0f, blur = .4f, hardness = .2f, absoluteBlurRadiusPixels = 12f))
            session.commit().close()
        }
        val spread = bitmap.getPixel(58, 48)
        assertTrue("Expected fixed blur to reach x=58 but color was ${Integer.toHexString(spread)}", Color.alpha(spread) > 0)
        bitmap.recycle()
    }

    @Test fun blurSoftensPigmentWithoutInjectingTheBrushColor() {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(30f, 30f, 48f, 66f, Paint().apply { color = Color.BLUE })

        paintSession(bitmap).use { session ->
            assertNotNull(session.advanceWetTransport(48f, 48f, 52f, 48f, 25f, pull = 0f, blur = 1f, hardness = .2f))
            session.commit().close()
        }

        val spread = bitmap.getPixel(53, 48)
        assertTrue(Color.alpha(spread) > 0)
        assertTrue(Color.blue(spread) > Color.red(spread))
        bitmap.recycle()
    }

    @Test fun cancellationRestoresTheExactRasterSurface() {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(28f, 40f, 12f, Paint().apply { color = Color.MAGENTA })
        val before = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }

        paintSession(bitmap).use { session ->
            session.advanceWetTransport(28f, 40f, 58f, 40f, 18f, pull = 1f, blur = .6f, hardness = .5f)
            session.restoreOriginal()
        }

        val after = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        assertEquals(before.toList(), after.toList())
        bitmap.recycle()
    }

    @Test fun selectionMaskPreventsWetTransportOutsideTheSelection() {
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(40f, 48f, 16f, Paint().apply { color = Color.RED })
        val selection = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        Canvas(selection).drawRect(0f, 0f, 55f, 96f, Paint().apply { color = Color.WHITE })
        val outsideBefore = bitmap.getPixel(65, 48)

        paintSession(bitmap, selection).use { session ->
            session.advanceWetTransport(40f, 48f, 72f, 48f, 22f, pull = 1f, blur = .5f, hardness = .4f)
            session.commit().close()
        }

        assertEquals(outsideBefore, bitmap.getPixel(65, 48))
        bitmap.recycle()
        selection.recycle()
    }

    @Test fun alphaLockKeepsTheExactDestinationAlphaBoundaryDuringTransport() {
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawCircle(40f, 48f, 16f, Paint().apply { color = Color.argb(120, 255, 0, 0) })
        val beforeAlpha = IntArray(bitmap.width * bitmap.height) { index -> Color.alpha(bitmap.getPixel(index % bitmap.width, index / bitmap.width)) }

        paintSession(bitmap, preserveDestinationAlpha = true).use { session ->
            session.advanceWetTransport(40f, 48f, 74f, 48f, 24f, pull = 1f, blur = .5f, hardness = .4f)
            session.commit().close()
        }

        val afterAlpha = IntArray(bitmap.width * bitmap.height) { index -> Color.alpha(bitmap.getPixel(index % bitmap.width, index / bitmap.width)) }
        assertEquals(beforeAlpha.toList(), afterAlpha.toList())
        bitmap.recycle()
    }
}
