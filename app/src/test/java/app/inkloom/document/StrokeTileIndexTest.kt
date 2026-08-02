package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class StrokeTileIndexTest {
    @Test fun movingAStrokeInvalidatesOnlyItsOldAndNewNeighborhoods() {
        val index = StrokeTileIndex(tileSizePx = 256)
        val old = stroke("moving", .1f, .1f)
        index.rebuild(listOf(old), 1024, 1024)
        val moved = stroke("moving", .85f, .85f)

        val dirty = index.update(listOf(old), listOf(moved))

        assertEquals(setOf(StrokeTileKey(0, 0), StrokeTileKey(3, 3)), dirty)
        assertTrue(index.candidates(StrokeTileKey(0, 0)).isEmpty())
        assertEquals(setOf("moving"), index.candidates(StrokeTileKey(3, 3)))
    }

    @Test fun aStrokeAcrossATileBoundaryIsIndexedIntoBothTiles() {
        val index = StrokeTileIndex(tileSizePx = 512)
        val crossing = InkStroke(
            id = "crossing",
            width = 20f,
            points = listOf(StrokePoint(.45f, .5f), StrokePoint(.55f, .5f)),
        )

        index.rebuild(listOf(crossing), 1024, 1024)

        assertEquals(setOf(StrokeTileKey(0, 0), StrokeTileKey(1, 0), StrokeTileKey(0, 1), StrokeTileKey(1, 1)), index.tilesFor(crossing))
    }

    @Test fun fiveThousandStrokeEditKeepsRenderingCandidatesLocal() {
        val strokes = List(5_000) { index ->
            val column = index % 100
            val row = index / 100
            stroke("s$index", (column + .5f) / 100f, (row + .5f) / 50f)
        }
        val index = StrokeTileIndex(tileSizePx = 256)
        val elapsed = measureTimeMillis { index.rebuild(strokes, 2_560, 1_600) }
        val original = strokes[2_550]
        val replacement = original.copy(points = original.points.map { it.copy(x = (it.x + .01f).coerceAtMost(1f)) })

        val dirty = index.update(listOf(original), listOf(replacement))
        val candidates = dirty.flatMap(index::candidates).toSet()

        assertTrue("Tile index build took ${elapsed}ms", elapsed < 1_000L)
        assertTrue(dirty.size <= 4)
        assertTrue("A local edit selected ${candidates.size} of ${strokes.size} strokes", candidates.size < 300)
        assertTrue(replacement.id in candidates)
    }

    @Test fun sprayAndDualFootprintsConservativelyReachNeighboringTiles() {
        val index = StrokeTileIndex(tileSizePx = 256)
        val broad = InkStroke(
            id = "broad",
            points = listOf(StrokePoint(.5f, .5f)),
            width = 200f,
            spray = true,
            sprayParticleSize = 200f,
            dualBrush = true,
            dualSizeScale = 2f,
            dualSpray = true,
            dualSprayParticleSize = 200f,
        )

        index.rebuild(listOf(broad), 1024, 1024)

        assertEquals(16, index.tilesFor(broad).size)
    }

    private fun stroke(id: String, x: Float, y: Float) = InkStroke(
        id = id,
        width = 2f,
        points = listOf(StrokePoint(x, y)),
    )
}
