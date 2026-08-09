package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionContourGeometryTest {
    private val square = LassoGeometry.rectangle(StrokePoint(.25f, .25f), StrokePoint(.75f, .75f))

    @Test fun `positive offset expands a contour in model pixels`() {
        val expanded = SelectionContourGeometry.offset(square, 100f, 1_000f, 1_000f)

        assertTrue(expanded.minOf(StrokePoint::x) < .25f)
        assertTrue(expanded.maxOf(StrokePoint::x) > .75f)
        assertEquals(.5f, expanded.map(StrokePoint::x).average().toFloat(), .0001f)
    }

    @Test fun `negative offset shrinks and clamps at the contour center`() {
        val shrunk = SelectionContourGeometry.offset(square, -100f, 1_000f, 1_000f)
        val collapsed = SelectionContourGeometry.offset(square, -10_000f, 1_000f, 1_000f)

        assertTrue(shrunk.minOf(StrokePoint::x) > .25f)
        assertTrue(shrunk.maxOf(StrokePoint::x) < .75f)
        assertTrue(collapsed.all { it.x == .5f && it.y == .5f })
    }
}
