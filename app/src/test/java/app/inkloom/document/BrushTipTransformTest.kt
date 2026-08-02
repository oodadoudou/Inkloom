package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class BrushTipTransformTest {
    @Test fun preservesTallTipAspectRatio() {
        val scale = BrushTipTransform.scale(width = 33, height = 300, diameter = 120f, thickness = 1f, thicknessVertical = false)
        assertEquals(13.2f, 33f * scale.x, .001f)
        assertEquals(120f, 300f * scale.y, .001f)
    }

    @Test fun appliesThicknessAlongConfiguredAxis() {
        val horizontal = BrushTipTransform.scale(100, 200, 100f, .5f, thicknessVertical = false)
        val vertical = BrushTipTransform.scale(100, 200, 100f, .5f, thicknessVertical = true)
        assertEquals(25f, 100f * horizontal.x, .001f)
        assertEquals(100f, 200f * horizontal.y, .001f)
        assertEquals(50f, 100f * vertical.x, .001f)
        assertEquals(50f, 200f * vertical.y, .001f)
    }
}
