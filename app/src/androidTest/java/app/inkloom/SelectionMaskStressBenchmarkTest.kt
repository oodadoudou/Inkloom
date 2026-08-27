package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.StrokePoint
import app.inkloom.document.FloodFill
import app.inkloom.engine.v3.scene.SparseAlphaMask
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class SelectionMaskStressBenchmarkTest {
    @Test fun eightKImplicitSparseFragmentedAndBooleanCasesStayBounded() {
        val sparse = MaskTileSurface(8192, 8192, tileSize = 128)
        sparse.beginWrite("128 square").run {
            composeAlpha(PixelRect(4096, 4096, 4224, 4224), ByteArray(128 * 128) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        assertEquals(1, sparse.tileCount)

        val large = MaskTileSurface(8192, 8192, tileSize = 128)
        val startedLarge = SystemClock.elapsedRealtimeNanos()
        large.beginWrite("2048 square").run {
            composeAlpha(PixelRect(3072, 3072, 5120, 5120), ByteArray(2048 * 2048) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        val largeMs = elapsedMs(startedLarge)
        assertEquals(256, large.tileCount)
        assertTrue(large.retainedByteSize <= 4L * 1024 * 1024)

        val all = MaskTileSurface(8192, 8192, tileSize = 128)
        val startedAll = SystemClock.elapsedRealtimeNanos()
        all.beginWrite("select all").run { fill(255); commit().close() }
        val allMs = elapsedMs(startedAll)
        assertEquals(0, all.tileCount)
        assertEquals(255, all.defaultAlpha)

        val startedInvert = SystemClock.elapsedRealtimeNanos()
        sparse.beginWrite("sparse to almost full").run { invert(); commit().close() }
        val invertMs = elapsedMs(startedInvert)
        assertEquals(255, sparse.defaultAlpha)
        assertEquals(1, sparse.tileCount)

        val fragmented = MaskTileSurface(8192, 8192, tileSize = 128)
        fragmented.beginWrite("fragmented islands").run {
            repeat(512) { index ->
                val x = (index * 997) and 8191
                val y = (index * 1597) and 8191
                setAlpha(x, y, 255)
            }
            commit().close()
        }
        assertTrue(fragmented.tileCount <= 512)
        assertTrue(fragmented.retainedByteSize <= 8L * 1024 * 1024)

        val operand = MaskTileSurface(8192, 8192, tileSize = 128)
        operand.beginWrite("boolean operand").run { setAlpha(4000, 4000, 180); commit().close() }
        val startedBoolean = SystemClock.elapsedRealtimeNanos()
        repeat(100) { index ->
            fragmented.beginWrite("boolean $index").run {
                composeSurface(
                    operand.snapshot(),
                    when (index % 3) {
                        0 -> MaskComposeMode.ADD
                        1 -> MaskComposeMode.SUBTRACT
                        else -> MaskComposeMode.INTERSECT
                    },
                )
                commit().close()
            }
        }
        val booleanMs = elapsedMs(startedBoolean)
        android.util.Log.i(
            "InkloomPerf",
            "selection_stress_8k large_ms=$largeMs select_all_ms=$allMs invert_ms=$invertMs boolean100_ms=$booleanMs",
        )
        assertTrue("2048² selection took ${largeMs}ms", largeMs < 5_000f)
        assertTrue("Select All took ${allMs}ms", allMs < 100f)
        assertTrue("Sparse invert took ${invertMs}ms", invertMs < 100f)
        assertTrue("100 boolean operations took ${booleanMs}ms", booleanMs < 10_000f)
    }

    @Test fun complexLassoLargeMagicWandAndExtremeFiltersStayWithinMemoryGate() {
        val complex = List(400) { index ->
            val angle = index.toDouble() / 400.0 * PI * 2.0
            val radius = if (index % 2 == 0) .42 else .19
            StrokePoint((.5 + cos(angle) * radius).toFloat(), (.5 + sin(angle) * radius).toFloat())
        }
        val lassoStarted = SystemClock.elapsedRealtimeNanos()
        val lasso = requireNotNull(SparseAlphaMask.polygon(complex, 8192f, 8192f))
        val lassoMs = elapsedMs(lassoStarted)
        assertTrue(lasso.tileCount > 1)
        assertTrue(lasso.retainedPixels < 2_000_000L)

        val source = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        Canvas(source).drawCircle(512f, 512f, 470f, Paint().apply { color = Color.WHITE })
        val wandStarted = SystemClock.elapsedRealtimeNanos()
        val filled = FloodFill.fill(source, 512, 512, tolerance = 0)
        val wand = requireNotNull(SparseAlphaMask.fromFloodFill(
            filled.mask,
            filled.left,
            filled.top,
            1024,
            1024,
            8192f,
            8192f,
        ))
        val wandMs = elapsedMs(wandStarted)
        filled.mask.recycle()
        source.recycle()
        assertTrue(wand.tileCount > 1)
        assertTrue(wand.retainedPixels < 2_000_000L)

        val island = MaskTileSurface(8192, 8192, tileSize = 256)
        island.beginWrite("filter island").run {
            composeAlpha(PixelRect(4032, 4032, 4160, 4160), ByteArray(128 * 128) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        val base = requireNotNull(SparseAlphaMask.fromSurface(island))
        listOf(8f, 64f, 256f).forEach { radius ->
            val started = SystemClock.elapsedRealtimeNanos()
            val feathered = requireNotNull(base.feathered(radius))
            val elapsed = elapsedMs(started)
            android.util.Log.i("InkloomPerf", "selection_feather_${radius.toInt()}px_ms=$elapsed tiles=${feathered.tileCount}")
            assertTrue("Feather $radius retained too much", feathered.retainedPixels < 8_000_000L)
            assertTrue("Feather $radius took ${elapsed}ms", elapsed < 15_000f)
            feathered.close()
        }
        listOf(8, 64, 256).forEach { radius ->
            listOf(true, false).forEach { dilate ->
                val started = SystemClock.elapsedRealtimeNanos()
                val changed = base.morphed(radius, dilate)
                val elapsed = elapsedMs(started)
                android.util.Log.i("InkloomPerf", "selection_${if (dilate) "grow" else "shrink"}_${radius}px_ms=$elapsed")
                assertTrue("Morph $radius took ${elapsed}ms", elapsed < 15_000f)
                changed?.close()
            }
        }
        lasso.close(); wand.close(); base.close()
        assertTrue("Complex lasso took ${lassoMs}ms", lassoMs < 5_000f)
        assertTrue("Large magic-wand mask import took ${wandMs}ms", wandMs < 5_000f)
    }

    private fun elapsedMs(startedNanos: Long): Float =
        (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000f
}
