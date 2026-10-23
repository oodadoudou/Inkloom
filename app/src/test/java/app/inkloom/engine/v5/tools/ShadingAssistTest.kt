package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class ShadingAssistTest {
    private fun surface(size: Int = 256, paint: (Int, Int) -> Int): PixelTileSurface = PixelTileSurface(size, size).also { s ->
        val pixels = IntArray(size * size) { i -> paint(i % size, i / size) }
        s.beginWrite("fixture").run { replacePremultipliedPixels(PixelRect(0, 0, size, size), pixels); commit().close() }
    }

    private val disc = surface { x, y -> if (hypot(x - 128f, y - 128f) < 100f) 0xFFE0C090.toInt() else 0 }

    @Test fun shadowFallsAwayFromTheLight() {
        val shade = ShadingAssist.render(disc.snapshot(), null, ShadingSettings(angleDegrees = 180f, elevation = .3f, softness = .1f))
        fun alpha(x: Int, y: Int) = shade[y * 256 + x] ushr 24
        assertTrue("the far side is in shadow", alpha(215, 128) > 60)
        assertEquals("the lit side is clear", 0, alpha(45, 128))
        assertEquals("nothing outside the colours", 0, alpha(5, 5))
    }

    @Test fun reverseShadesTheMiddleAndLightsTheRims() {
        val shade = ShadingAssist.render(disc.snapshot(), null, ShadingSettings(angleDegrees = 180f, elevation = .9f, reverse = true, amount = .5f, softness = .1f))
        fun alpha(x: Int, y: Int) = shade[y * 256 + x] ushr 24
        assertTrue(alpha(128, 128) > alpha(31, 128))
    }

    @Test fun celShadingHasOneHardEdge() {
        val shade = ShadingAssist.render(disc.snapshot(), null, ShadingSettings(angleDegrees = 180f, cel = true, opacity = 1f))
        val levels = (40..216).map { shade[128 * 256 + it] ushr 24 }.toSet()
        assertTrue("only clear and full shadow: $levels", levels.all { it == 0 || it == 255 })
        assertTrue(levels.size == 2)
    }

    @Test fun lineArtSplitsRegionsIntoSeparateShapes() {
        // A vertical line across the disc makes two halves, each shaded as its own shape.
        val lines = surface { x, _ -> if (x in 126..130) 0xFF000000.toInt() else 0 }
        val shade = ShadingAssist.render(disc.snapshot(), lines.snapshot(), ShadingSettings(angleDegrees = 180f, elevation = .3f, softness = .1f))
        fun alpha(x: Int) = shade[128 * 256 + x] ushr 24
        assertTrue("the left half's far side, by the line, is shaded", alpha(120) > 60)
        assertEquals("the line itself is left alone", 0, alpha(128))
        assertEquals("the right half's lit side is clear", 0, alpha(134))
    }

    @Test fun distanceTransformIsEuclidean() {
        val values = FloatArray(9 * 9) { 1e12f }.also { it[4 * 9 + 4] = 0f }
        EuclideanDistance.transform(values, 9, 9)
        assertEquals(25f, values[0 * 9 + 1], 1e-3f) // (3, 4) away
        assertEquals(32f, values[0], 1e-3f)
    }
}
