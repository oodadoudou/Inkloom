package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeTaperTest {
    private val points = (0..4).map { StrokePoint(it / 4f, 0f) }

    @Test
    fun startAndEndTaperReachConfiguredMinimums() {
        val values = StrokeTaper.factors(points, 1000f, 1000f, BrushTaperMode.PERCENTAGE, true, .5f, 0, .2f, true, .5f, 0, .4f)
        assertEquals(.2f, values.first(), .0001f)
        assertEquals(1f, values[2], .0001f)
        assertEquals(.4f, values.last(), .0001f)
    }

    @Test
    fun disabledTaperLeavesStrokeUnchanged() {
        val values = StrokeTaper.factors(points, 1000f, 1000f, BrushTaperMode.PERCENTAGE, false, .5f, 0, 0f, false, .5f, 0, 0f)
        values.forEach { assertEquals(1f, it, .0001f) }
    }

    @Test
    fun distanceUsesPhysicalPageAspect() {
        val diagonal = listOf(StrokePoint(0f, 0f), StrokePoint(.5f, .5f), StrokePoint(1f, .5f))
        val values = StrokeTaper.factors(diagonal, 2000f, 500f, BrushTaperMode.PERCENTAGE, true, .75f, 0, 0f, false, .2f, 0, 0f)
        assertEquals(1f, values.last(), .0001f)
        assert(values[1] > .5f)
    }

    @Test
    fun absolutePixelLengthDoesNotScaleWithStrokeLength() {
        val short = listOf(StrokePoint(0f, 0f), StrokePoint(.1f, 0f), StrokePoint(.2f, 0f))
        val values = StrokeTaper.factors(short, 1600f, 1000f, BrushTaperMode.DISTANCE, true, 20f, 0, 0f, false, 20f, 0, 0f)
        assertEquals(1f, values[1], .0001f)
    }
}
