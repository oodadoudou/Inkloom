package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RasterMatchColorTest {
    private fun surface(color: (Int, Int) -> Int) = PixelTileSurface(96, 96).apply {
        beginWrite("fixture").use { w -> for (y in 0 until 96) for (x in 0 until 96) color(x, y).takeIf { it != 0 }?.let { w.setPixel(x, y, it) }; w.commit().close() }
    }.snapshot()
    private fun red(p: Int) = p ushr 16 and 255
    private fun blue(p: Int) = p and 255

    @Test fun labRoundTripsColours() {
        val lab = FloatArray(3); val rgb = FloatArray(3)
        listOf(floatArrayOf(.2f, .5f, .8f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 0f)).forEach { c ->
            Lab.fromLinear(c[0], c[1], c[2], lab); Lab.toLinear(lab[0], lab[1], lab[2], rgb)
            (0..2).forEach { assertEquals(c[it], rgb[it], 1e-3f) }
        }
    }

    @Test fun aBluishLayerTakesOnAWarmReferencesColours() {
        val cool = surface { x, _ -> (0xFF shl 24) or ((60 + x / 3) shl 16) or (90 shl 8) or (180 + x / 3) }
        val warm = ColorStatistics.of(surface { x, _ -> (0xFF shl 24) or ((200 + x / 4) shl 16) or (120 shl 8) or 50 })
        val matched = RasterImageFilter.apply(cool, RasterImageFilter.Effect.MatchColor(warm)).snapshot()
        assertTrue("now warm", red(matched.pixelAt(48, 48)) > blue(matched.pixelAt(48, 48)))
        assertTrue("it was cool", red(cool.pixelAt(48, 48)) < blue(cool.pixelAt(48, 48)))
        val half = RasterImageFilter.apply(cool, RasterImageFilter.Effect.MatchColor(warm, strength = .5f)).snapshot()
        assertTrue("half strength lies between", red(half.pixelAt(48, 48)) in red(cool.pixelAt(48, 48))..red(matched.pixelAt(48, 48)))
    }

    @Test fun keepLuminanceChangesOnlyColourAndNoReferenceChangesNothing() {
        val cool = surface { _, _ -> (0xFF shl 24) or (60 shl 16) or (90 shl 8) or 200 }
        val warm = ColorStatistics.of(surface { _, _ -> (0xFF shl 24) or (220 shl 16) or (120 shl 8) or 40 })
        val kept = RasterImageFilter.apply(cool, RasterImageFilter.Effect.MatchColor(warm, keepLuminance = true)).snapshot()
        fun lightness(p: Int): Float { val lab = FloatArray(3)
            Lab.fromLinear(ExtendedSrgbTest.decode(red(p)), ExtendedSrgbTest.decode(p ushr 8 and 255), ExtendedSrgbTest.decode(blue(p)), lab); return lab[0] }
        assertEquals(lightness(cool.pixelAt(5, 5)), lightness(kept.pixelAt(5, 5)), 1.5f)
        assertArrayEquals(cool.copyPremultipliedPixels(PixelRect(0, 0, 96, 96)),
            RasterImageFilter.apply(cool, RasterImageFilter.Effect.MatchColor(null)).snapshot().copyPremultipliedPixels(PixelRect(0, 0, 96, 96)))
    }

    @Test fun selectionLimitsTheChangeExactly() {
        val cool = surface { x, _ -> (0xFF shl 24) or ((60 + x) shl 16) or (90 shl 8) or 200 }
        val warm = ColorStatistics.of(surface { _, _ -> (0xFF shl 24) or (220 shl 16) or (120 shl 8) or 40 })
        val mask = MaskTileSurface(96, 96).apply { beginWrite("left").use { w -> for (y in 0 until 96) for (x in 0 until 48) w.setAlpha(x, y, 255); w.commit().close() } }.snapshot()
        val matched = RasterImageFilter.apply(cool, RasterImageFilter.Effect.MatchColor(warm), mask).snapshot()
        assertNotEquals(cool.pixelAt(10, 10), matched.pixelAt(10, 10))
        assertEquals("unselected pixels are copied exactly", cool.pixelAt(80, 10), matched.pixelAt(80, 10))
    }

    private object ExtendedSrgbTest {
        fun decode(v: Int): Float { val c = v / 255f; return if (c <= .04045f) c / 12.92f else Math.pow(((c + .055f) / 1.055f).toDouble(), 2.4).toFloat() }
    }
}
