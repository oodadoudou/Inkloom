package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeCorrectionTest {
    private val stroke = InkStroke(
        id = "line", width = 10f,
        points = listOf(
            StrokePoint(.1f, .5f), StrokePoint(.25f, .65f), StrokePoint(.4f, .35f),
            StrokePoint(.55f, .62f), StrokePoint(.7f, .38f), StrokePoint(.9f, .5f),
        ),
    )

    @Test fun smoothingPreservesEndpointsAndReducesJitter() {
        val corrected = StrokeCorrection.apply(stroke, CorrectLineMode.SMOOTH, 1f, 1000f, 1000f)
        assertEquals(stroke.points.first(), corrected.points.first())
        assertEquals(stroke.points.last(), corrected.points.last())
        assertTrue(kotlin.math.abs(corrected.points[2].y - .5f) < kotlin.math.abs(stroke.points[2].y - .5f))
    }

    @Test fun simplifyReducesPointCount() {
        val corrected = StrokeCorrection.apply(stroke, CorrectLineMode.SIMPLIFY, 1f, 1000f, 1000f)
        assertTrue(corrected.points.size < stroke.points.size)
    }

    @Test fun widthCorrectionUsesIndependentStrokeWidth() {
        val corrected = StrokeCorrection.apply(stroke, CorrectLineMode.ADJUST_WIDTH, 1f, 1000f, 1000f)
        assertTrue(corrected.width > stroke.width)
        assertEquals(10f, stroke.width, .001f)
    }

    @Test fun redrawWidthChangesPressureWithoutChangingBaseWidth() {
        val corrected = StrokeCorrection.apply(stroke, CorrectLineMode.REDRAW_WIDTH, .2f, 1000f, 1000f)
        assertEquals(stroke.width, corrected.width)
        assertTrue(corrected.points.all { it.pressure < 1f })
    }
}
