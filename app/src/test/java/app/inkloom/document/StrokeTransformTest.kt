package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeTransformTest {
    private fun stroke(x: Float, y: Float, width: Float = 5f) = InkStroke(
        id = "s", points = listOf(StrokePoint(x, y)), colorArgb = 0xFF111111L, width = width,
        brushId = "pen", layerId = LayerIds.INK,
    )

    @Test fun pureTranslationMovesPoints() {
        val out = StrokeTransform.transform(listOf(stroke(.2f, .3f)), .5f, .5f, scale = 1f, rotationDegrees = 0f, translateX = .1f, translateY = -.05f)
        assertEquals(.3f, out[0].points[0].x, .0001f)
        assertEquals(.25f, out[0].points[0].y, .0001f)
    }

    @Test fun uniformScaleScalesWidthAndDistance() {
        val out = StrokeTransform.transform(listOf(stroke(.5f, .0f, width = 4f)), centerX = .5f, centerY = .5f, scale = 2f, rotationDegrees = 0f, translateX = 0f, translateY = 0f)
        assertEquals(8f, out[0].width, .001f)
        // point (.5, 0) scaled about (.5,.5) by 2 -> (.5, -.5) clamped to (.5, 0)
        assertEquals(.5f, out[0].points[0].x, .0001f)
        assertEquals(0f, out[0].points[0].y, .0001f)
    }

    @Test fun rotationRotatesAboutCenter() {
        val out = StrokeTransform.transform(listOf(stroke(1f, .5f)), centerX = .5f, centerY = .5f, scale = 1f, rotationDegrees = 90f, translateX = 0f, translateY = 0f)
        // (1,.5) rotated 90° about (.5,.5) -> (.5, 1) but clamped; x should become .5
        assertEquals(.5f, out[0].points[0].x, .001f)
    }

    @Test fun identityReturnsSameStrokes() {
        val s = listOf(stroke(.2f, .3f))
        assertEquals(s, StrokeTransform.transform(s, .5f, .5f, 1f, 0f, 0f, 0f))
    }
}
