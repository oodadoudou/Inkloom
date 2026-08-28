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

    @Test fun handlesConcaveLassosWithoutSelectingTheCutout() {
        val concave = listOf(StrokePoint(.1f, .1f), StrokePoint(.9f, .1f), StrokePoint(.9f, .9f), StrokePoint(.5f, .5f), StrokePoint(.1f, .9f))
        assertTrue(LassoGeometry.contains(concave, StrokePoint(.25f, .5f)))
        assertFalse(LassoGeometry.contains(concave, StrokePoint(.5f, .75f)))
    }

}
