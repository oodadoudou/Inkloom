package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import app.inkloom.settings.RasterFilterPreset
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RasterLayerConversionTest {
    private fun surface(tile: Int = 64, color: (Int, Int) -> Int) = PixelTileSurface(128, 128, tile).apply {
        beginWrite("fixture").use { w -> for (y in 0 until 128) for (x in 0 until 128) color(x, y).takeIf { it != 0 }?.let { w.setPixel(x, y, it) }; w.commit().close() }
    }.snapshot()
    private fun convert(source: PixelTileSurfaceSnapshot, conversion: LayerConversion) =
        RasterImageFilter.apply(source, RasterImageFilter.Effect.ConvertLayer(conversion)).snapshot()
    private val colorful = surface { x, y -> (0xFF shl 24) or ((x * 2) shl 16) or ((y * 2) shl 8) or 60 }

    @Test fun grayKeepsAlphaAndRemovesColour() {
        val gray = convert(colorful, LayerConversion(LayerExpression.GRAY))
        val p = gray.pixelAt(70, 30)
        assertEquals(p ushr 16 and 255, p ushr 8 and 255); assertEquals(p ushr 8 and 255, p and 255); assertEquals(255, p ushr 24)
    }

    @Test fun monochromeIsBlackOrNothing() {
        val ramp = surface { x, _ -> (0xFF shl 24) or ((x * 2) * 0x010101) }
        val mono = convert(ramp, LayerConversion(LayerExpression.MONOCHROME, threshold = .5f))
        assertEquals(0xFF000000.toInt(), mono.pixelAt(10, 10)); assertEquals("light becomes transparent", 0, mono.pixelAt(120, 10))
        val withWhite = convert(ramp, LayerConversion(LayerExpression.MONOCHROME, whiteTransparent = false))
        assertEquals(-1, withWhite.pixelAt(120, 10))
        assertTrue((0 until 128).all { mono.pixelAt(it, 5).let { p -> p == 0 || p == 0xFF000000.toInt() } })
    }

    @Test fun lowerResolutionMakesBlocksOnAWorldGrid() {
        val half = LayerConversion(resolution = .125f)
        val blocky = convert(colorful, half)
        assertEquals("one 8 × 8 block is one colour", blocky.pixelAt(8, 8), blocky.pixelAt(15, 15))
        assertNotEquals(blocky.pixelAt(15, 15), blocky.pixelAt(16, 16))
        assertArrayEquals(blocky.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)),
            convert(surface(128) { x, y -> (0xFF shl 24) or ((x * 2) shl 16) or ((y * 2) shl 8) or 60 }, half).copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
    }

    @Test fun toneTurnsShadesIntoDotsOfMatchingCoverage() {
        val mid = surface { _, _ -> 0xFF808080.toInt() }
        val tone = convert(mid, LayerConversion(tonePitch = 6f))
        val coverage = (0 until 128).sumOf { y -> (0 until 128).sumOf { x -> tone.pixelAt(x, y) ushr 24 } } / (255.0 * 128 * 128)
        assertEquals("50 % grey (about 0.5 in display values) gives about half coverage", .5, coverage, .07)
        assertTrue("dots are black ink", (0 until 128).all { tone.pixelAt(it, 40).let { p -> p ushr 24 == 0 || (p and 0xFFFFFF) == 0 } })
    }

    @Test fun presetMapsToTheConversion() {
        val effect = RasterFilterPreset(kind = RasterFilterPreset.CONVERT_LAYER_KIND, expression = 2, monoThreshold = .3f, tonePitch = 0f).effect()
        assertTrue(effect is RasterImageFilter.Effect.ConvertLayer && effect.conversion.expression == LayerExpression.MONOCHROME && abs(effect.conversion.threshold - .3f) < 1e-6)
    }
}
