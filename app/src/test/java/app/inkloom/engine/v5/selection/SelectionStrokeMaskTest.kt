package app.inkloom.engine.v5.selection

import app.inkloom.engine.v5.surface.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SelectionStrokeMaskTest {
    private fun rectangle(width: Int, height: Int, region: PixelRect) = MaskTileSurface(width, height, 64).apply {
        beginWrite("rectangle").use { it.composeAlpha(region, ByteArray(region.width * region.height) { 255.toByte() }, mode = MaskComposeMode.REPLACE); it.commit().close() }
    }

    @Test fun positionsHaveDistinctPixelWidthsAndFractionalCenteredCoverage() {
        val mask = rectangle(96, 64, PixelRect(20, 10, 80, 55))
        val inside = SelectionStrokeMask.create(mask.snapshot(), 96, 64, 2f, SelectionStrokePlacement.INSIDE)
        val outside = SelectionStrokeMask.create(mask.snapshot(), 96, 64, 2f, SelectionStrokePlacement.OUTSIDE)
        val center = SelectionStrokeMask.create(mask.snapshot(), 96, 64, 1f, SelectionStrokePlacement.CENTER)
        assertEquals(listOf(0, 0, 255, 255, 0), (18..22).map { inside.alphaAt(it, 30) })
        assertEquals(listOf(255, 255, 0, 0, 0), (18..22).map { outside.alphaAt(it, 30) })
        assertEquals(listOf(0, 128, 128, 0), (18..21).map { center.alphaAt(it, 30) })
        assertEquals(255, mask.alphaAt(40, 30)); assertEquals(0, center.alphaAt(40, 30))
    }

    @Test fun holesDiagonalContoursAndTileSeamsMatchIndependentDistanceOracle() {
        val mask = rectangle(132, 96, PixelRect(52, 21, 86, 72))
        mask.beginWrite("hole and island").use { write ->
            write.composeAlpha(PixelRect(61, 35, 73, 52), ByteArray(12 * 17), mode = MaskComposeMode.REPLACE)
            write.composeAlpha(PixelRect(103, 58, 114, 65), ByteArray(11 * 7) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            write.commit().close()
        }
        for (placement in SelectionStrokePlacement.entries) {
            val width = 3.5f
            val a = SelectionStrokeMask.create(mask.snapshot(), 132, 96, width, placement, tileSize = 64)
            val b = SelectionStrokeMask.create(mask.snapshot(), 132, 96, width, placement, tileSize = 128)
            assertArrayEquals(a.snapshot().copyAlpha(PixelRect(0, 0, 132, 96)), b.snapshot().copyAlpha(PixelRect(0, 0, 132, 96)))
            val reach = if (placement == SelectionStrokePlacement.CENTER) width / 2 else width
            val radius = ceil(reach + 2).toInt()
            for (y in 0 until 96) for (x in 0 until 132) {
                val selected = mask.alphaAt(x, y) >= 128
                var distance = Float.POSITIVE_INFINITY
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    val px = x + dx; val py = y + dy
                    val other = px in 0 until 132 && py in 0 until 96 && mask.alphaAt(px, py) >= 128
                    if (other != selected) distance = minOf(distance, sqrt((dx * dx + dy * dy).toFloat()))
                }
                val signed = if (selected) distance - .5f else .5f - distance
                val low = if (placement == SelectionStrokePlacement.INSIDE) 0f else -reach
                val high = if (placement == SelectionStrokePlacement.OUTSIDE) 0f else reach
                val expected = (minOf(signed - low + .5f, high - signed + .5f).coerceIn(0f, 1f) * 255).roundToInt()
                assertEquals("$placement at $x,$y", expected, a.alphaAt(x, y))
            }
        }
    }

    @Test fun sparse8KIslandsAndImplicitFullSelectionRetainOnlyBorderTiles() {
        val sparse = rectangle(8192, 8192, PixelRect(7980, 7980, 8010, 8010))
        val stroked = SelectionStrokeMask.create(sparse.snapshot(), 8192, 8192, 4f, SelectionStrokePlacement.OUTSIDE)
        assertTrue(stroked.tileCount in 1..4)
        assertEquals(255, stroked.alphaAt(7979, 7990)); assertEquals(0, stroked.alphaAt(8000, 8000))
        val full = MaskTileSurface(8192, 8192, initialDefaultAlpha = 255)
        val edge = SelectionStrokeMask.create(full.snapshot(), 8192, 8192, 1f, SelectionStrokePlacement.INSIDE)
        assertTrue(edge.tileCount <= 4 * (8192 / edge.tileSize))
        assertEquals(255, edge.alphaAt(0, 4096)); assertEquals(0, edge.alphaAt(1, 4096))
        assertEquals(0, edge.alphaAt(4096, 4096)); assertEquals(255, full.defaultAlpha)
    }

    @Test fun scaledSoftEdgesCancelAndEmptySelectionsDoNotMutateSource() {
        val mask = rectangle(32, 32, PixelRect(8, 8, 24, 24))
        val before = mask.snapshot().copyAlpha(PixelRect(0, 0, 32, 32))
        val outline = SelectionStrokeMask.create(mask.snapshot(), 64, 64, 2f, SelectionStrokePlacement.INSIDE)
        assertTrue(outline.alphaAt(16, 30) in 1..254)
        assertEquals(0, outline.alphaAt(12, 30)); assertEquals(0, outline.alphaAt(32, 32))
        try {
            SelectionStrokeMask.create(mask.snapshot(), 64, 64, 2f, SelectionStrokePlacement.CENTER, cancelled = { true })
            fail("Cancelled work must never return a partial stroke")
        } catch (_: CancellationException) { }
        assertArrayEquals(before, mask.snapshot().copyAlpha(PixelRect(0, 0, 32, 32)))
        assertTrue(SelectionStrokeMask.create(MaskTileSurface(32, 32).snapshot(), 64, 64, 2f, SelectionStrokePlacement.CENTER).isEmpty)
    }
}
