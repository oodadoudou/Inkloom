package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v3.tools.RasterSelectionMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionMaskRoiSamplingTest {
    @Test fun rasterConsumerSamplesOnlyItsDirtyDocumentRegion() {
        val selection = MaskTileSurface(100, 100, tileSize = 64)
        selection.beginWrite("left half").run {
            val alpha = ByteArray(50 * 100) { 255.toByte() }
            replaceAlpha(app.inkloom.engine.v5.surface.PixelRect(0, 0, 50, 100), alpha)
            commit().close()
        }
        val target = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            assertTrue(RasterSelectionMask(selection.snapshot(), 100, 100).apply(target, Rect(40, 40, 60, 60)))
            assertEquals(Color.RED, target.getPixel(2, 10))
            assertEquals(Color.TRANSPARENT, target.getPixel(18, 10))
        } finally {
            target.recycle()
        }
    }

    @Test fun eightKSelectionDirtyRoiSamplingStaysInsideFrameBudget() {
        val selection = MaskTileSurface(8192, 8192, tileSize = 128)
        selection.beginWrite("local island").run {
            editTile(app.inkloom.engine.v5.surface.PixelTileKey(32, 32)) { alpha, _ -> alpha.fill(255.toByte()) }
            commit().close()
        }
        val sampler = RasterSelectionMask(selection.snapshot(), 8192, 8192)
        val target = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            repeat(3) { target.eraseColor(Color.WHITE); sampler.apply(target, Rect(4096, 4096, 4224, 4224)) }
            val elapsed = ArrayList<Long>(12)
            repeat(12) {
                target.eraseColor(Color.WHITE)
                val started = SystemClock.elapsedRealtimeNanos()
                sampler.apply(target, Rect(4096, 4096, 4224, 4224))
                elapsed += SystemClock.elapsedRealtimeNanos() - started
            }
            val p95Ms = elapsed.sorted()[(elapsed.size * 95 / 100).coerceAtMost(elapsed.lastIndex)] / 1_000_000f
            android.util.Log.i("InkloomPerf", "selection_8k_roi_sample_p95_ms=$p95Ms")
            assertTrue("8K selection ROI sampling P95 was ${p95Ms}ms", p95Ms < 33.3f)
        } finally {
            target.recycle()
        }
    }

    @Test fun eightKImplicitSelectAllAndSparseInvertStayAllocationBoundedAndSampleCorrectly() {
        val selection = MaskTileSurface(8192, 8192, tileSize = 128)
        selection.beginWrite("select all").run { fill(255); commit().close() }
        assertEquals(0, selection.tileCount)
        assertEquals(0L, selection.retainedByteSize)

        selection.beginWrite("one hole").run {
            setAlpha(4097, 4097, 0)
            commit().close()
        }
        selection.beginWrite("invert").run { invert(); commit().close() }

        assertEquals(0, selection.defaultAlpha)
        assertEquals(1, selection.tileCount)
        assertEquals(255, selection.alphaAt(4097, 4097))
        assertEquals(0, selection.alphaAt(7000, 7000))

        val target = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            assertTrue(RasterSelectionMask(selection.snapshot(), 8192, 8192).apply(target, Rect(4096, 4096, 4224, 4224)))
            assertEquals(Color.RED, target.getPixel(1, 1))
            assertEquals(Color.TRANSPARENT, target.getPixel(100, 100))
        } finally {
            target.recycle()
        }
    }
}
