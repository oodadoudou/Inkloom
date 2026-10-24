package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test

class RasterQuickEffectsTest {
    private fun surface(tile: Int = 64, color: (Int, Int) -> Int): PixelTileSurfaceSnapshot = PixelTileSurface(128, 128, tile).apply {
        beginWrite("fixture").use { w -> for (y in 0 until 128) for (x in 0 until 128) color(x, y).takeIf { it != 0 }?.let { w.setPixel(x, y, it) }; w.commit().close() }
    }.snapshot()
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    /** A square of ink on a transparent layer. */
    private fun ink(tile: Int = 64, color: Int = rgb(240, 240, 240)) = surface(tile) { x, y -> if (x in 40..87 && y in 40..87) color else 0 }
    private fun apply(source: PixelTileSurfaceSnapshot, effect: RasterImageFilter.Effect) = RasterImageFilter.apply(source, effect).snapshot()
    private fun alpha(s: PixelTileSurfaceSnapshot, x: Int, y: Int) = s.pixelAt(x, y) ushr 24
    private fun red(s: PixelTileSurfaceSnapshot, x: Int, y: Int) = s.pixelAt(x, y) ushr 16 and 255
    private fun blue(s: PixelTileSurfaceSnapshot, x: Int, y: Int) = s.pixelAt(x, y) and 255
    private val effects = listOf(RasterImageFilter.Effect.Glow(GlowParameters(blur = 12f)), RasterImageFilter.Effect.Inflate(InflateParameters()),
        RasterImageFilter.Effect.DropShadow(DropShadowParameters(distance = 20f)), RasterImageFilter.Effect.Glitch(GlitchParameters(density = 100f)))

    @Test fun everyEffectIsIndependentOfTileSize() = effects.forEach { effect ->
        assertArrayEquals(effect.toString(), apply(ink(64), effect).copyPremultipliedPixels(PixelRect(0, 0, 128, 128)),
            apply(ink(128), effect).copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
    }

    @Test fun glowSpreadsLightFromBrightInkOnly() {
        val glowing = apply(ink(), RasterImageFilter.Effect.Glow(GlowParameters(strength = 200f, area = 60f, blur = 16f)))
        assertTrue("light spreads beyond the ink", alpha(glowing, 34, 64) > 0)
        assertEquals("far away stays empty", 0, alpha(glowing, 2, 2))
        val dark = apply(ink(color = rgb(20, 20, 20)), RasterImageFilter.Effect.Glow(GlowParameters(strength = 200f, area = 10f, blur = 16f)))
        assertEquals("dark ink does not glow", 0, alpha(dark, 34, 64))
    }

    @Test fun inflateLightsOneSideAndShadesTheOther() {
        val puffed = apply(ink(color = rgb(160, 160, 160)), RasterImageFilter.Effect.Inflate(InflateParameters(shadow = 100f, shine = 0f, height = 60f, lightAngle = 180f)))
        assertTrue("the side facing the light is brighter", red(puffed, 42, 64) > red(puffed, 85, 64))
        assertEquals("transparent pixels stay empty", 0, alpha(puffed, 10, 10))
        assertEquals("ink keeps its coverage", 255, alpha(puffed, 64, 64))
    }

    @Test fun dropShadowFallsAlongItsAngleUnderTheInk() {
        val shadowed = apply(ink(color = rgb(255, 0, 0)), RasterImageFilter.Effect.DropShadow(DropShadowParameters(blur = 0f, angle = 0f, distance = 20f, opacity = 100f)))
        assertEquals("the shadow lies to the right", 255, alpha(shadowed, 100, 64)); assertEquals(0, red(shadowed, 100, 64))
        assertEquals("nothing on the far side", 0, alpha(shadowed, 30, 64))
        assertEquals("the ink stays on top", 255, red(shadowed, 64, 64))
    }

    @Test fun glitchShiftsBandsAndSplitsChannels() {
        val source = ink(color = rgb(255, 255, 255))
        val glitched = apply(source, RasterImageFilter.Effect.Glitch(GlitchParameters(shift = 0f, split = 4f)))
        assertTrue("red is pushed right of the ink", red(glitched, 37, 64) == 0 && red(glitched, 89, 64) == 255 && blue(glitched, 89, 64) == 0)
        val still = apply(source, RasterImageFilter.Effect.Glitch(GlitchParameters(shift = 0f, split = 0f)))
        assertArrayEquals(source.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)), still.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
        val sliced = apply(source, RasterImageFilter.Effect.Glitch(GlitchParameters(shift = 30f, bandHeight = 8f, density = 100f, split = 0f, seed = 3)))
        assertNotEquals((40..87).map { sliced.pixelAt(40, it) }.toSet().size, 1)
    }
}
