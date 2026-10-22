package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class RasterDistortFilterTest {
    private fun surface(tile: Int = 64, high: Boolean = false, draw: (PixelTileWriteTransaction) -> Unit): PixelTileSurfaceSnapshot =
        PixelTileSurface(128, 128, tile, pixelFormat = if (high) PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 else PixelTileFormat.PREMULTIPLIED_SRGB_8888)
            .apply { beginWrite("fixture").use { draw(it); it.commit().close() } }.snapshot()
    private fun dot(x: Int, y: Int, tile: Int = 64) = surface(tile) { it.setPixel(x, y, 0xFF000000.toInt()) }
    private fun apply(source: PixelTileSurfaceSnapshot, effect: RasterImageFilter.Effect, selection: MaskTileSurfaceSnapshot? = null) =
        RasterImageFilter.apply(source, effect, selection).snapshot()
    private fun alpha(s: PixelTileSurfaceSnapshot, x: Int, y: Int) = s.pixelAt(x, y) ushr 24
    private fun inked(s: PixelTileSurfaceSnapshot) = (0 until 128).flatMap { y -> (0 until 128).map { x -> x to y } }.filter { (x, y) -> alpha(s, x, y) > 40 }
    private fun distort(kind: DistortKind, p: DistortParameters = DistortParameters()) = RasterImageFilter.Effect.Distort(kind, p)

    @Test fun fixedBlursHaveTheirKernelSizes() {
        val source = dot(64, 64)
        val blur = apply(source, RasterImageFilter.Effect.FixedBlur(FixedBlurKind.BLUR))
        assertTrue(alpha(blur, 65, 65) > 0); assertEquals(0, alpha(blur, 66, 64))
        val strong = apply(source, RasterImageFilter.Effect.FixedBlur(FixedBlurKind.STRONG_BLUR))
        assertTrue(alpha(strong, 66, 66) > 0); assertEquals(0, alpha(strong, 67, 64))
        val smooth = apply(source, RasterImageFilter.Effect.FixedBlur(FixedBlurKind.SMOOTH))
        assertTrue("centre weighs most", alpha(smooth, 64, 64) > alpha(smooth, 65, 64))
        assertTrue("edges weigh more than corners", alpha(smooth, 65, 64) > alpha(smooth, 65, 65))
    }

    @Test fun resultsDoNotDependOnTileSize() {
        listOf(RasterImageFilter.Effect.FixedBlur(FixedBlurKind.STRONG_BLUR), RasterImageFilter.Effect.PerlinNoise(20f, .8f, .5f, 3, 7),
            distort(DistortKind.TWIRL, DistortParameters(strength = 60f)), distort(DistortKind.WAVE, DistortParameters(seed = 4, wrap = true))).forEach { effect ->
            val a = apply(dot(63, 64, 64), effect); val b = apply(dot(63, 64, 128), effect)
            assertArrayEquals(effect.toString(), a.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)), b.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
        }
    }

    @Test fun perlinNoiseIsOpaqueSeededAndVaried() {
        val empty = surface { }
        val noise = apply(empty, RasterImageFilter.Effect.PerlinNoise(16f, 1f, .5f, 4, 1))
        assertEquals(255, alpha(noise, 0, 0)); assertEquals(255, alpha(noise, 127, 127))
        val greys = (0 until 128 step 4).map { noise.pixelAt(it, 40) and 255 }.toSet()
        assertTrue("noise varies", greys.size > 8)
        assertArrayEquals(noise.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)),
            apply(empty, RasterImageFilter.Effect.PerlinNoise(16f, 1f, .5f, 4, 1)).copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
        assertFalse(noise.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)).contentEquals(
            apply(empty, RasterImageFilter.Effect.PerlinNoise(16f, 1f, .5f, 4, 2)).copyPremultipliedPixels(PixelRect(0, 0, 128, 128))))
        val mask = MaskTileSurface(128, 128).apply { beginWrite("left").use { w -> for (y in 0 until 128) for (x in 0 until 64) w.setAlpha(x, y, 255); w.commit().close() } }.snapshot()
        val limited = apply(empty, RasterImageFilter.Effect.PerlinNoise(16f, 1f, .5f, 4, 1), mask)
        assertEquals(255, alpha(limited, 10, 10)); assertEquals("unselected pixels are untouched", 0, alpha(limited, 100, 10))
    }

    @Test fun pinchBulgesOrShrinksAroundTheCentre() {
        // A blob rather than a dot: shrinking a single pixel can fall between samples.
        val source = surface { w -> for (y in 61..67) for (x in 81..87) w.setPixel(x, y, 0xFF000000.toInt()) }
        fun distance(s: PixelTileSurfaceSnapshot) = inked(s).also { assertTrue(it.isNotEmpty()) }.map { (x, y) -> hypot(x - 63.5f, y - 63.5f) }.average()
        val out = apply(source, distort(DistortKind.PINCH, DistortParameters(strength = 80f)))
        val inward = apply(source, distort(DistortKind.PINCH, DistortParameters(strength = -40f)))
        assertTrue("positive pinch moves content outward", distance(out) > 22f)
        assertTrue("negative pinch pulls it in", distance(inward) < 19f)
    }

    @Test fun twirlTurnsContentAboutTheCentreAtTheSameRadius() {
        val turned = inked(apply(dot(84, 64), distort(DistortKind.TWIRL, DistortParameters(strength = 50f))))
        assertTrue(turned.isNotEmpty())
        turned.forEach { (x, y) -> assertEquals(20.5f, hypot(x - 63.5f, y - 63.5f), 2.5f) }
        assertTrue("it moved off the horizontal", turned.any { (_, y) -> kotlin.math.abs(y - 64) > 3 })
    }

    @Test fun fishEyeLeavesOutsideTheLensTransparent() {
        val full = surface { w -> for (y in 0 until 128) for (x in 0 until 128) w.setPixel(x, y, 0xFF808080.toInt()) }
        val lens = apply(full, distort(DistortKind.FISHEYE, DistortParameters(strength = 60f, radius = .6f)))
        assertEquals(0, alpha(lens, 0, 0)); assertEquals(255, alpha(lens, 64, 64))
    }

    @Test fun geometricDistortionPushesOutwardForPositiveValues() {
        val source = dot(94, 64)
        val pushed = inked(apply(source, distort(DistortKind.GEOMETRIC, DistortParameters(strength = 80f))))
        val pulled = inked(apply(source, distort(DistortKind.GEOMETRIC, DistortParameters(strength = -40f))))
        assertTrue(pushed.all { (x, _) -> x > 94 }); assertTrue(pulled.all { (x, _) -> x < 94 })
    }

    @Test fun polarCoordinatesWrapTheTopRowIntoTheCentre() {
        val topRow = surface { w -> for (x in 0 until 128) for (y in 0 until 3) w.setPixel(x, y, 0xFF000000.toInt()) }
        val polar = apply(topRow, distort(DistortKind.POLAR, DistortParameters(mode = DistortModes.POLAR_FROM_RECTANGULAR)))
        assertTrue(alpha(polar, 64, 64) > 0); assertEquals(0, alpha(polar, 64, 2))
        val back = apply(polar, distort(DistortKind.POLAR, DistortParameters(mode = DistortModes.POLAR_TO_RECTANGULAR)))
        assertTrue("polar to rectangular brings it back to the top", alpha(back, 64, 0) > 0)
    }

    @Test fun waveIsSeededRespectsRatiosAndEdgeModes() {
        val line = surface { w -> for (y in 0 until 128) w.setPixel(64, y, 0xFF000000.toInt()) }
        val p = DistortParameters(count = 2, amplitudeMin = 8f, amplitudeMax = 12f, seed = 3)
        val wave = apply(line, distort(DistortKind.WAVE, p))
        assertTrue("the vertical line bends", inked(wave).map { it.first }.toSet().size > 3)
        assertArrayEquals(wave.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)), apply(line, distort(DistortKind.WAVE, p)).copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
        val still = apply(line, distort(DistortKind.WAVE, p.copy(horizontalRatio = 0f, verticalRatio = 0f)))
        assertArrayEquals(line.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)), still.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
        val edge = surface { w -> for (y in 0 until 128) w.setPixel(0, y, 0xFF000000.toInt()) }
        val shifted = DistortParameters(count = 1, wavelengthMin = 2000f, wavelengthMax = 2000f, amplitudeMin = 40f, amplitudeMax = 40f, verticalRatio = 0f, seed = 9)
        val repeat = apply(edge, distort(DistortKind.WAVE, shifted)); val wrap = apply(edge, distort(DistortKind.WAVE, shifted.copy(wrap = true)))
        assertFalse(repeat.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)).contentEquals(wrap.copyPremultipliedPixels(PixelRect(0, 0, 128, 128))))
    }

    @Test fun zigZagBendsAHorizontalLineAlongItsAngle() {
        val line = surface { w -> for (x in 0 until 128) w.setPixel(x, 64, 0xFF000000.toInt()) }
        val zig = inked(apply(line, distort(DistortKind.ZIGZAG, DistortParameters(amplitudeMax = 10f, count = 4))))
        assertTrue(zig.map { it.second }.toSet().size > 8)
    }

    @Test fun curvedCylinderOnlyMovesHorizontally() {
        val line = surface { w -> for (y in 20 until 108) w.setPixel(90, y, 0xFF000000.toInt()) }
        val curved = inked(apply(line, distort(DistortKind.CURVED_SURFACE, DistortParameters(strength = 100f, mode = DistortModes.SURFACE_CYLINDER))))
        assertTrue(curved.isNotEmpty()); assertTrue(curved.all { (x, _) -> x != 90 })
        assertEquals(setOf(20, 107), setOf(curved.minOf { it.second }, curved.maxOf { it.second }))
    }

    @Test fun panoramaKeepsLinesParallelToTheAxisStraight() {
        val line = surface { w -> for (y in 0 until 128) w.setPixel(90, y, 0xFF000000.toInt()) }
        val pano = inked(apply(line, distort(DistortKind.PANORAMA)))
        assertTrue(pano.isNotEmpty())
        // Resampling may split the line over two neighbouring columns, but every row agrees.
        assertTrue(pano.maxOf { it.first } - pano.minOf { it.first } <= 1); assertTrue(pano.all { it.first < 90 })
    }

    @Test fun highPrecisionRangeSurvivesAndSettingsAreValidated() {
        val hdr = surface(high = true) { it.setLinearPixel(64, 64, LinearRgba(2f, .5f, -.1f, 1f)) }
        val blurred = apply(hdr, RasterImageFilter.Effect.FixedBlur(FixedBlurKind.BLUR))
        assertTrue(blurred.linearPixelAt(64, 64).red > 1f / 9f * 2f - .01f)
        assertTrue(runCatching { DistortParameters(strength = 150f) }.isFailure)
        assertTrue(runCatching { DistortParameters(wavelengthMin = 50f, wavelengthMax = 10f) }.isFailure)
        try { RasterImageFilter.apply(hdr, distort(DistortKind.TWIRL), cancelled = { true }); fail("cancelled") }
        catch (_: java.util.concurrent.CancellationException) {}
    }
}
