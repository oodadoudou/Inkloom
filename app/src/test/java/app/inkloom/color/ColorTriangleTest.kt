package app.inkloom.color

import org.junit.Assert.*
import org.junit.Test

class ColorTriangleTest {
    @Test fun verticesAreWhiteBlackAndPureHue() {
        val t = ColorTriangle(150f, 173.205f)
        assertEquals(ColorTriangle.Weights(0f, 1f, 0f), t.weights(0f, 0f))
        assertEquals(ColorTriangle.Weights(0f, 0f, 1f), t.weights(0f, t.height))
        assertEquals(ColorTriangle.Weights(1f, 0f, 0f), t.weights(t.width, t.height / 2))
    }
    @Test fun everyInteriorCoordinateRoundTripsToTheSameSelectedColor() {
        for ((width, height) in listOf(150f to 173.205f, 263f to 304f)) {
            val t = ColorTriangle(width, height)
            for (s in 0..20) for (v in 1..20) {
                val point = t.position(s / 20f, v / 20f)
                val picked = t.weights(point.x, point.y)
                assertEquals(s / 20f, picked.saturation, .00001f)
                assertEquals(v / 20f, picked.value, .00001f)
                assertEquals(1f, picked.hue + picked.white + picked.black, .00001f)
            }
        }
    }
    @Test fun outsideDragsProjectToTheNearestPixelSpaceEdge() {
        val t = ColorTriangle(150f, 173.205f)
        val neutral = t.weights(-20f, 86.6025f)
        assertEquals(0f, neutral.hue, 0f)
        assertEquals(.5f, neutral.white, .00001f)
        assertEquals(.5f, neutral.black, .00001f)
        assertEquals(ColorTriangle.Weights(1f, 0f, 0f), t.weights(250f, 86.6025f))
        val top = t.weights(75f, -50f)
        assertEquals(0f, top.black, .00001f)
        assertTrue(top.white > 0f && top.hue > 0f)
        val bottom = t.weights(75f, 230f)
        assertEquals(0f, bottom.white, .00001f)
    }
}
