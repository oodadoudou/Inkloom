package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun procreatePressureTaperControlsSizeAndOpacityIndependently() {
        val stroke = InkStroke(
            points = points,
            procreateTaper = ProcreateTaper(
                pressureStartLength = .5f,
                pressureEndLength = .5f,
                pressureSize = .8f,
                pressureOpacity = .6f,
                pressureTip = .5f,
            ),
        )

        val taper = StrokeTaper.evaluate(stroke, points, 1000f, 1000f)

        assertEquals(.2f, taper.size.first(), .0001f)
        assertEquals(.4f, taper.opacity!!.first(), .0001f)
        assertEquals(1f, taper.size[2], .0001f)
        assertEquals(1f, taper.opacity!![2], .0001f)
        assertEquals(.2f, taper.size.last(), .0001f)
        assertEquals(.4f, taper.opacity!!.last(), .0001f)
    }

    @Test
    fun touchAndStylusUseTheirOwnAuthoredTaperPrograms() {
        val taper = ProcreateTaper(
            pressureStartLength = .5f,
            pressureSize = .9f,
            touchStartLength = .5f,
            touchSize = .2f,
        )
        val stylus = StrokeTaper.evaluate(InkStroke(points = points, inputKind = StrokeInputKind.STYLUS, procreateTaper = taper), points, 1000f, 1000f)
        val touch = StrokeTaper.evaluate(InkStroke(points = points, inputKind = StrokeInputKind.TOUCH, procreateTaper = taper), points, 1000f, 1000f)

        assertEquals(.1f, stylus.size.first(), .0001f)
        assertEquals(.8f, touch.size.first(), .0001f)
    }

    @Test
    fun tipAndClassicControlsChangeTheTaperProfileWithoutMovingItsBounds() {
        val dense = (0..8).map { StrokePoint(it / 8f, 0f, pressure = .5f) }
        fun atQuarter(tip: Float, classic: Boolean): Float = StrokeTaper.evaluate(
            InkStroke(points = dense, procreateTaper = ProcreateTaper(pressureStartLength = .5f, pressureSize = 1f, pressureTip = tip, classic = classic)),
            dense,
            1000f,
            1000f,
        ).size[1]

        assertTrue(atQuarter(0f, false) < atQuarter(1f, false))
        assertTrue(kotlin.math.abs(atQuarter(.5f, false) - atQuarter(.5f, true)) > .01f)
    }
}
