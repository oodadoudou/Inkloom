package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class ComicFramesTest {
    private val w = 1000f
    private val h = 1400f
    private val page = FrameGeometry.pageFrame(w, h, 100f)

    private fun bounds(frame: ComicFrame) = floatArrayOf(
        (0 until frame.count).minOf { frame.x(it) } * w, (0 until frame.count).minOf { frame.y(it) } * h,
        (0 until frame.count).maxOf { frame.x(it) } * w, (0 until frame.count).maxOf { frame.y(it) } * h)

    @Test fun pageFramesAreInsetByTheMargin() {
        assertArrayEquals(floatArrayOf(100f, 100f, 900f, 1300f), bounds(page), .01f)
    }

    @Test fun dividingLeavesTheGutterBetweenFrames() {
        // A horizontal cut across the middle, 60 units apart.
        val pieces = requireNotNull(FrameGeometry.divide(page, 0f, .5f, 1f, .5f, 60f, w, h))
        assertEquals(2, pieces.size)
        assertEquals("the original frame keeps its ID", page.id, pieces[0].id)
        val (a, b) = pieces.map(::bounds).sortedBy { it[1] }
        assertArrayEquals(floatArrayOf(100f, 100f, 900f, 670f), a, .05f)
        assertArrayEquals(floatArrayOf(100f, 730f, 900f, 1300f), b, .05f)
        assertNull("a line missing the frame divides nothing", FrameGeometry.divide(page, 0f, .01f, 1f, .02f, 60f, w, h))
    }

    @Test fun slantedCutsMakeQuadrilaterals() {
        val pieces = requireNotNull(FrameGeometry.divide(page, .3f, 0f, .7f, 1f, 40f, w, h))
        assertTrue(pieces.all { it.count == 4 })
    }

    @Test fun divideEquallyMakesAGrid() {
        val cells = FrameGeometry.divideEqually(page, columns = 2, rows = 3, horizontalGutter = 40f, verticalGutter = 50f, pageWidth = w, pageHeight = h)
        assertEquals(6, cells.size)
        val first = bounds(cells[0]); val last = bounds(cells[5])
        assertArrayEquals(floatArrayOf(100f, 100f, 480f, 100f + 1100f / 3f), first, .05f)
        assertArrayEquals(floatArrayOf(520f, 1300f - 1100f / 3f, 900f, 1300f), last, .05f)
        assertEquals(page.id, cells[0].id)
    }

    @Test fun combiningCoversTheFramesAndTheGutterBetween() {
        val pieces = requireNotNull(FrameGeometry.divide(page, 0f, .5f, 1f, .5f, 60f, w, h))
        val combined = FrameGeometry.combine(pieces)
        assertArrayEquals(floatArrayOf(100f, 100f, 900f, 1300f), bounds(combined), .05f)
        assertEquals(4, combined.count)
    }

    @Test fun growingMovesEveryEdgeOutwards() {
        val grown = FrameGeometry.grown(page, 3f, w, h)
        assertEquals(97f, grown.minOf { it.first }, .01f); assertEquals(903f, grown.maxOf { it.first }, .01f)
        assertEquals(97f, grown.minOf { it.second }, .01f); assertEquals(1303f, grown.maxOf { it.second }, .01f)
    }

    @Test fun framesAreFoundByPoint() {
        val pieces = requireNotNull(FrameGeometry.divide(page, 0f, .5f, 1f, .5f, 60f, w, h))
        val lower = pieces.indexOfFirst { bounds(it)[1] > 700f }
        assertEquals(lower, FrameGeometry.frameAt(pieces, .5f, .8f))
        assertNull("the gutter is in no frame", FrameGeometry.frameAt(pieces, .5f, .5f))
    }
}
