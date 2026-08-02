package app.inkloom.document

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

    @Test fun selectsAStrokeWhenAnySampleFallsInside() {
        assertTrue(LassoGeometry.selects(square, InkStroke(points = listOf(StrokePoint(.1f, .1f), StrokePoint(.4f, .4f)))))
        assertFalse(LassoGeometry.selects(square, InkStroke(points = listOf(StrokePoint(.05f, .05f), StrokePoint(.1f, .1f)))))
    }

    @Test fun handlesConcaveLassosWithoutSelectingTheCutout() {
        val concave = listOf(StrokePoint(.1f, .1f), StrokePoint(.9f, .1f), StrokePoint(.9f, .9f), StrokePoint(.5f, .5f), StrokePoint(.1f, .9f))
        assertTrue(LassoGeometry.contains(concave, StrokePoint(.25f, .5f)))
        assertFalse(LassoGeometry.contains(concave, StrokePoint(.5f, .75f)))
    }
}
