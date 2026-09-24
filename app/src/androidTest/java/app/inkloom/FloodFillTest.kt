package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.FloodFill
import app.inkloom.settings.FillExpansionShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloodFillTest {
    private fun solid(color: Int, w: Int = 20, h: Int = 20): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test fun fillsAUniformRegionEntirely() {
        val source = solid(Color.WHITE)
        val result = FloodFill.fill(source, 5, 5, tolerance = 0)
        assertEquals(20, result.width)
        assertEquals(20, result.height)
        assertTrue("Every pixel of a uniform source should be covered", result.mask.getPixel(0, 0) == Color.WHITE)
    }

    @Test fun stopsAtAColorBoundary() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) for (x in 0 until 20) source.setPixel(x, y, if (x < 10) Color.WHITE else Color.BLACK)
        val result = FloodFill.fill(source, 0, 0, tolerance = 10)
        assertEquals("Fill should stop at the black boundary", 10, result.width)
    }

    @Test fun toleranceBleedsAcrossAntiAliasedEdges() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) for (x in 0 until 20) {
            source.setPixel(x, y, if (x < 8) Color.WHITE else if (x < 12) Color.rgb(200, 200, 200) else Color.BLACK)
        }
        val tight = FloodFill.fill(source, 0, 0, tolerance = 10)
        val loose = FloodFill.fill(source, 0, 0, tolerance = 80)
        assertTrue("Higher tolerance should cover more pixels", loose.width >= tight.width)
    }

    @Test fun closeGapTreatsASmallBreakInLineArtAsClosed() {
        val source = solid(Color.WHITE)
        for (x in 5..14) { source.setPixel(x, 5, Color.BLACK); source.setPixel(x, 14, Color.BLACK) }
        for (y in 5..14) { source.setPixel(5, y, Color.BLACK); source.setPixel(14, y, Color.BLACK) }
        source.setPixel(10, 5, Color.WHITE)

        val open = FloodFill.fill(source, 10, 10, tolerance = 0)
        val closed = FloodFill.fill(source, 10, 10, tolerance = 0, closeGapPixels = 1)

        assertEquals(20, open.width)
        assertTrue("Closing the one-pixel break must keep fill inside the box", closed.width < 12)
    }

    @Test fun areaScalingCanOverfillOrUnderfillTheDetectedRegion() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until 20) for (x in 0 until 20) source.setPixel(x, y, if (x in 5..14 && y in 5..14) Color.WHITE else Color.BLACK)

        val normal = FloodFill.fill(source, 10, 10, tolerance = 0)
        val expanded = FloodFill.fill(source, 10, 10, tolerance = 0, areaScalingPixels = 2)
        val shrunk = FloodFill.fill(source, 10, 10, tolerance = 0, areaScalingPixels = -2)

        assertEquals(10, normal.width)
        assertTrue(expanded.width > normal.width)
        assertTrue(shrunk.width < normal.width)
    }

    @Test fun areaScalingRoundAndRectangleHaveDistinctCornerCoverage() {
        val source = solid(Color.BLACK, 21, 21).apply { setPixel(10, 10, Color.WHITE) }
        val round = FloodFill.fill(source, 10, 10, 0, areaScalingPixels = 2, expansionShape = FillExpansionShape.ROUND)
        val rectangle = FloodFill.fill(source, 10, 10, 0, areaScalingPixels = 2, expansionShape = FillExpansionShape.RECTANGLE)
        try {
            assertEquals(5, round.width)
            assertEquals(5, rectangle.width)
            assertEquals("A circle of radius two excludes the diagonal corner", 0, Color.alpha(round.mask.getPixel(0, 0)))
            assertEquals("A rectangle of radius two includes the diagonal corner", 255, Color.alpha(rectangle.mask.getPixel(0, 0)))
            assertEquals(255, Color.alpha(round.mask.getPixel(2, 0)))
        } finally {
            round.mask.recycle()
            rectangle.mask.recycle()
            source.recycle()
        }
    }

    @Test fun seedInsideAndOutsideAClosedBoundaryFillOppositeConnectedRegions() {
        val source = solid(Color.WHITE, 30, 30)
        for (x in 8..21) { source.setPixel(x, 8, Color.BLACK); source.setPixel(x, 21, Color.BLACK) }
        for (y in 8..21) { source.setPixel(8, y, Color.BLACK); source.setPixel(21, y, Color.BLACK) }

        val inside = FloodFill.fill(source, 14, 14, tolerance = 0)
        val outside = FloodFill.fill(source, 2, 2, tolerance = 0)

        assertTrue("Inside seed must stay within the outline", inside.left > 8 && inside.right < 21)
        assertEquals("Outside seed must cover the connected exterior bounds", 30, outside.width)
        assertEquals(0, Color.alpha(outside.mask.getPixel(14, 14)))
        assertTrue(Color.alpha(outside.mask.getPixel(2, 2)) > 0)
    }

    @Test fun toleranceComparesUnpremultipliedChannelsForTranslucentArtwork() {
        val source = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.argb(128, 200, 100, 50))
            setPixel(1, 0, Color.argb(128, 210, 105, 55))
            setPixel(2, 0, Color.argb(128, 230, 100, 50))
        }
        val result = FloodFill.fill(source, 0, 0, tolerance = 10)
        try {
            assertEquals(2, result.width)
            assertTrue(Color.alpha(result.mask.getPixel(1, 0)) > 0)
        } finally {
            result.mask.recycle()
            source.recycle()
        }
    }

    @Test fun transparentTargetRejectsOpaqueSeedAndFillsOnlyConnectedClearRegion() {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (coordinate in 4..11) {
            source.setPixel(coordinate, 4, Color.BLACK)
            source.setPixel(coordinate, 11, Color.BLACK)
            source.setPixel(4, coordinate, Color.BLACK)
            source.setPixel(11, coordinate, Color.BLACK)
        }
        val inside = FloodFill.fill(source, 8, 8, 0, targetColor = Color.TRANSPARENT)
        val line = FloodFill.fill(source, 4, 8, 0, targetColor = Color.TRANSPARENT)
        try {
            assertEquals(6, inside.width)
            assertTrue(line.isEmpty)
        } finally {
            inside.mask.recycle()
            line.mask.recycle()
            source.recycle()
        }
    }

    @Test fun specifiedTargetRequiresMatchingSeedAndNeverFloodsAdjacentColor() {
        val source = Bitmap.createBitmap(20, 8, Bitmap.Config.ARGB_8888)
        for (y in 0 until 8) for (x in 0 until 20) source.setPixel(x, y, if (x < 10) Color.RED else Color.BLUE)
        val match = FloodFill.fill(source, 3, 4, 0, targetColor = Color.RED)
        val mismatch = FloodFill.fill(source, 15, 4, 0, targetColor = Color.RED)
        try {
            assertEquals(10, match.width)
            assertTrue(mismatch.isEmpty)
        } finally {
            match.mask.recycle()
            mismatch.mask.recycle()
            source.recycle()
        }
    }

    @Test fun repeatedViewportFillThresholdUpdatesStayInteractive() {
        val source = solid(Color.WHITE, 800, 600)
        for (y in 80 until 520) source.setPixel(400, y, Color.BLACK)
        repeat(4) { FloodFill.fill(source, 100, 300, tolerance = it * 4).mask.recycle() }
        val elapsed = ArrayList<Long>(40)
        repeat(40) { iteration ->
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            FloodFill.fill(source, 100, 300, tolerance = iteration * 2).mask.recycle()
            elapsed += android.os.SystemClock.elapsedRealtimeNanos() - started
        }
        val sorted = elapsed.sorted()
        // Estimate the 95th percentile from enough samples to avoid accidentally measuring the
        // single worst emulator scheduling pause as "P95" (12 samples made index 11 the maximum).
        val p95Ms = sorted[(sorted.lastIndex * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "native_flood_fill_p95_ms=$p95Ms worst_ms=${sorted.last() / 1_000_000f}")
        assertTrue("Native fill threshold P95 was ${p95Ms}ms", p95Ms < 33.3f)
        source.recycle()
    }

    @Test fun roundedExpansionKeepsPreviewWorkBounded() {
        val source = solid(Color.WHITE, 512, 512)
        for (x in 40 until 472) { source.setPixel(x, 40, Color.BLACK); source.setPixel(x, 471, Color.BLACK) }
        for (y in 40 until 472) { source.setPixel(40, y, Color.BLACK); source.setPixel(471, y, Color.BLACK) }
        repeat(3) { FloodFill.fill(source, 256, 256, tolerance = 0, areaScalingPixels = 4).mask.recycle() }
        val elapsed = ArrayList<Long>(16)
        repeat(16) {
            val start = android.os.SystemClock.elapsedRealtimeNanos()
            FloodFill.fill(source, 256, 256, tolerance = 0, areaScalingPixels = 4).mask.recycle()
            elapsed += android.os.SystemClock.elapsedRealtimeNanos() - start
        }
        val sorted = elapsed.sorted()
        val p95Ms = sorted[sorted.lastIndex * 95 / 100] / 1_000_000f
        android.util.Log.i("InkloomPerf", "rounded_fill_expansion_p95_ms=$p95Ms")
        assertTrue("Rounded expansion preview P95 was ${p95Ms}ms", p95Ms < 66.7f)
        source.recycle()
    }
}
