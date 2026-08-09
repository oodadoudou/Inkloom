package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoGeometryTest {
    private val square = listOf(
        StrokePoint(.2f, .2f), StrokePoint(.8f, .2f),
        StrokePoint(.8f, .8f), StrokePoint(.2f, .8f),
    )

    @Test fun detectsPointsInsideAClosedFreeformPath() {
        assertTrue(LassoGeometry.contains(square, StrokePoint(.5f, .5f)))
        assertFalse(LassoGeometry.contains(square, StrokePoint(.1f, .5f)))
        assertFalse(LassoGeometry.contains(square.take(2), StrokePoint(.5f, .5f)))
    }

    @Test fun rectangleNormalizesReverseDragCorners() {
        val rectangle = LassoGeometry.rectangle(StrokePoint(.8f, .7f), StrokePoint(.2f, .3f))

        assertEquals(
            listOf(.2f to .3f, .8f to .3f, .8f to .7f, .2f to .7f),
            rectangle.map { it.x to it.y },
        )
        assertTrue(LassoGeometry.contains(rectangle, StrokePoint(.5f, .5f)))
    }

    @Test fun rectangleRejectsTapSizedGestures() {
        assertTrue(LassoGeometry.rectangle(StrokePoint(.5f, .5f), StrokePoint(.5001f, .5001f)).isEmpty())
    }

    @Test fun ellipseUsesDraggedBoundsWithoutSelectingItsCorners() {
        val ellipse = LassoGeometry.ellipse(StrokePoint(.2f, .3f), StrokePoint(.8f, .7f))

        assertTrue(LassoGeometry.contains(ellipse, StrokePoint(.5f, .5f)))
        assertFalse(LassoGeometry.contains(ellipse, StrokePoint(.21f, .31f)))
    }

    @Test fun selectionBrushBuildsAStrokeWidthCorridor() {
        val corridor = LassoGeometry.brushCorridor(
            listOf(StrokePoint(.2f, .5f), StrokePoint(.8f, .5f)),
            diameter = 100f,
            modelWidth = 1_000f,
            modelHeight = 1_000f,
        )

        assertTrue(LassoGeometry.contains(corridor, StrokePoint(.5f, .54f)))
        assertFalse(LassoGeometry.contains(corridor, StrokePoint(.5f, .57f)))
    }

    @Test fun selectsAStrokeWhenAnySampleFallsInside() {
        assertTrue(LassoGeometry.selects(square, InkStroke(points = listOf(StrokePoint(.1f, .1f), StrokePoint(.4f, .4f)))))
        assertFalse(LassoGeometry.selects(square, InkStroke(points = listOf(StrokePoint(.05f, .05f), StrokePoint(.1f, .1f)))))
    }

    @Test fun handlesConcaveLassosWithoutSelectingTheCutout() {
        val concave = listOf(StrokePoint(.1f, .1f), StrokePoint(.9f, .1f), StrokePoint(.9f, .9f), StrokePoint(.5f, .5f), StrokePoint(.1f, .9f))
        assertTrue(LassoGeometry.contains(concave, StrokePoint(.25f, .5f)))
        assertFalse(LassoGeometry.contains(concave, StrokePoint(.5f, .75f)))
    }

    @Test fun splitsASparseCrossingStrokeIntoOutsideAndSelectedPieces() {
        val stroke = InkStroke(
            id = "source",
            renderSeed = 42_424,
            points = listOf(
                StrokePoint(.1f, .5f, pressure = .2f, timeMs = 0L, tilt = .1f, velocity = .2f),
                StrokePoint(.9f, .5f, pressure = 1f, timeMs = 100L, tilt = .5f, velocity = .8f),
            ),
        )
        val ids = ArrayDeque(listOf("left", "inside", "right"))

        val partition = LassoGeometry.partition(square, stroke, ids::removeFirst)

        assertTrue(partition.changed)
        assertEquals(listOf(false, true, false), partition.pieces.map(LassoStrokePiece::selected))
        assertEquals(listOf("left", "inside", "right"), partition.pieces.map { it.stroke.id })
        assertEquals(listOf(42_424, 42_424, 42_424), partition.pieces.map { it.stroke.renderSeed })
        val selected = partition.pieces[1].stroke.points
        assertEquals(.2f, selected.first().x, .0001f)
        assertEquals(.8f, selected.last().x, .0001f)
        assertEquals(.3f, selected.first().pressure, .0001f)
        assertEquals(.9f, selected.last().pressure, .0001f)
        assertEquals(13L, selected.first().timeMs)
        assertEquals(88L, selected.last().timeMs)
    }

    @Test fun keepsWholeInsideAndOutsideStrokesByteForByteCompatible() {
        val inside = InkStroke(id = "inside", points = listOf(StrokePoint(.3f, .4f), StrokePoint(.7f, .6f)))
        val outside = InkStroke(id = "outside", points = listOf(StrokePoint(.05f, .05f), StrokePoint(.1f, .1f)))

        val insidePartition = LassoGeometry.partition(square, inside)
        val outsidePartition = LassoGeometry.partition(square, outside)

        assertFalse(insidePartition.changed)
        assertEquals(inside, insidePartition.pieces.single().stroke)
        assertTrue(insidePartition.pieces.single().selected)
        assertFalse(outsidePartition.changed)
        assertEquals(outside, outsidePartition.pieces.single().stroke)
        assertFalse(outsidePartition.pieces.single().selected)
    }

    @Test fun segmentIntersectionsSelectSparseStrokesWithoutInteriorSamples() {
        val crossing = InkStroke(points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)))

        assertTrue(LassoGeometry.selects(square, crossing))
    }
}
