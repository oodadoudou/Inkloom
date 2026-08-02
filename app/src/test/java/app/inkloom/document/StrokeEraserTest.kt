package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeEraserTest {
    @Test
    fun cutsAThinlySampledSegmentAtExactBoundaries() {
        val stroke = InkStroke(
            id = "original",
            points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)),
        )

        val result = StrokeEraser.erase(stroke, .5f, .5f, .1f, .1f) { "piece-$it" }!!

        assertTrue(StrokeEraser.intersects(stroke, .5f, .5f, .1f, .1f))
        assertEquals(2, result.replacements.size)
        assertEquals(.4f, result.replacements[0].points.last().x, .0001f)
        assertEquals(.6f, result.replacements[1].points.first().x, .0001f)
        assertEquals(listOf("piece-0", "piece-1"), result.replacements.map(InkStroke::id))
    }

    @Test
    fun interpolatesInputChannelsAtCutBoundaries() {
        val stroke = InkStroke(
            points = listOf(
                StrokePoint(.1f, .5f, pressure = 0f, timeMs = 0, tilt = 0f, velocity = 0f),
                StrokePoint(.9f, .5f, pressure = 1f, timeMs = 800, tilt = .8f, velocity = 1f),
            ),
        )

        val result = StrokeEraser.erase(stroke, .5f, .5f, .1f, .1f) { "piece-$it" }!!
        val leftBoundary = result.replacements.first().points.last()
        val rightBoundary = result.replacements.last().points.first()

        assertEquals(.375f, leftBoundary.pressure, .0001f)
        assertEquals(300, leftBoundary.timeMs)
        assertEquals(.3f, leftBoundary.tilt, .0001f)
        assertEquals(.625f, rightBoundary.pressure, .0001f)
        assertEquals(500, rightBoundary.timeMs)
        assertEquals(.625f, rightBoundary.velocity, .0001f)
    }

    @Test
    fun erasesAStrokeThatIsEntirelyInside() {
        val stroke = InkStroke(points = listOf(StrokePoint(.48f, .5f), StrokePoint(.52f, .5f)))
        val result = StrokeEraser.erase(stroke, .5f, .5f, .1f, .1f)!!
        assertTrue(result.replacements.isEmpty())
    }

    @Test
    fun returnsNullWhenTheEraserMisses() {
        val stroke = InkStroke(points = listOf(StrokePoint(.1f, .1f), StrokePoint(.2f, .2f)))
        assertNull(StrokeEraser.erase(stroke, .8f, .8f, .05f, .05f))
    }

    @Test
    fun usesIndependentRadiiForPortraitPageGeometry() {
        val horizontal = InkStroke(points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)))
        val vertical = InkStroke(points = listOf(StrokePoint(.5f, .1f), StrokePoint(.5f, .9f)))

        val horizontalResult = StrokeEraser.erase(horizontal, .5f, .5f, .2f, .05f) { "h-$it" }!!
        val verticalResult = StrokeEraser.erase(vertical, .5f, .5f, .2f, .05f) { "v-$it" }!!

        assertEquals(.3f, horizontalResult.replacements.first().points.last().x, .0001f)
        assertEquals(.45f, verticalResult.replacements.first().points.last().y, .0001f)
    }

    @Test
    fun preservesTheCompleteBrushSnapshotOnEveryPiece() {
        val stroke = InkStroke(
            id = "source",
            renderSeed = 77_707,
            points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)),
            width = 42f,
            brushId = "imported-sut",
            grainDensity = .72f,
            dualBrush = true,
            watercolor = true,
        )

        val pieces = StrokeEraser.erase(stroke, .5f, .5f, .1f, .1f) { "piece-$it" }!!.replacements

        pieces.forEach { piece ->
            assertEquals(42f, piece.width)
            assertEquals("imported-sut", piece.brushId)
            assertEquals(.72f, piece.grainDensity)
            assertTrue(piece.dualBrush)
            assertTrue(piece.watercolor)
            assertEquals(77_707, piece.renderSeed)
        }
    }
}
