package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import app.inkloom.settings.RasterFilterPreset
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RasterEffectFilterTest {
    private fun surface(tile: Int = 64, color: (Int, Int) -> Int): PixelTileSurfaceSnapshot = PixelTileSurface(128, 128, tile).apply {
        beginWrite("fixture").use { w -> for (y in 0 until 128) for (x in 0 until 128) color(x, y).takeIf { it != 0 }?.let { w.setPixel(x, y, it) }; w.commit().close() }
    }.snapshot()
    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    /** A dark square on white paper. */
    private fun square(tile: Int = 64) = surface(tile) { x, y -> if (x in 40..87 && y in 40..87) grey(30) else grey(255) }
    private fun apply(source: PixelTileSurfaceSnapshot, effect: RasterImageFilter.Effect) = RasterImageFilter.apply(source, effect).snapshot()
    private fun red(s: PixelTileSurfaceSnapshot, x: Int, y: Int) = s.pixelAt(x, y) ushr 16 and 255
    private fun green(s: PixelTileSurfaceSnapshot, x: Int, y: Int) = s.pixelAt(x, y) ushr 8 and 255
    private fun blue(s: PixelTileSurfaceSnapshot, x: Int, y: Int) = s.pixelAt(x, y) and 255

    @Test fun everyEffectIsIndependentOfTileSize() {
        listOf(RasterImageFilter.Effect.RemoveJpegNoise, RasterImageFilter.Effect.Crystallize(12f, 80f, false),
            RasterImageFilter.Effect.NormalMap(40f), RasterImageFilter.Effect.Artistic(ArtisticParameters(lineWidth = 3f)),
            RasterImageFilter.Effect.PencilDrawing(PencilParameters()), RasterImageFilter.Effect.Film(FilmParameters())).forEach { effect ->
            assertArrayEquals(effect.toString(), apply(square(64), effect).copyPremultipliedPixels(PixelRect(0, 0, 128, 128)),
                apply(square(128), effect).copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
        }
    }

    @Test fun jpegNoiseRemovalSoftensBlockStepsButKeepsDrawnEdges() {
        // Faint 8 × 8 blocking on grey, plus a hard black/white edge.
        val source = surface { x, y -> if (x >= 100) grey(0) else grey(120 + if ((x / 8 + y / 8) % 2 == 0) 6 else -6) }
        val cleaned = apply(source, RasterImageFilter.Effect.RemoveJpegNoise)
        assertTrue("block step is reduced", abs(red(cleaned, 7, 20) - red(cleaned, 8, 20)) < abs(red(source, 7, 20) - red(source, 8, 20)))
        assertEquals("the drawn edge stays hard", 0, red(cleaned, 101, 20)); assertTrue(red(cleaned, 97, 20) > 100)
    }

    @Test fun crystallizeMakesFlatCellsAndTilingWraps() {
        val gradient = surface { x, y -> grey((x * 2) and 255) }
        val cells = apply(gradient, RasterImageFilter.Effect.Crystallize(16f, 0f, false))
        assertEquals("a cell is one colour", red(cells, 2, 2), red(cells, 10, 10))
        assertTrue("there are fewer colours than columns", (0 until 128).map { red(cells, it, 50) }.toSet().size <= 9)
        val tiled = apply(gradient, RasterImageFilter.Effect.Crystallize(16f, 60f, true))
        assertEquals(apply(gradient, RasterImageFilter.Effect.Crystallize(16f, 60f, true)).copyPremultipliedPixels(PixelRect(0, 0, 128, 128)).toList(),
            tiled.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)).toList())
    }

    @Test fun normalMapIsFlatOnPlainPaperAndTiltsAtEdges() {
        val map = apply(square(), RasterImageFilter.Effect.NormalMap(50f))
        fun near(expected: Int, actual: Int) = assertTrue("$actual is near $expected", abs(expected - actual) <= 1)
        near(128, red(map, 10, 10)); near(128, green(map, 10, 10)); near(255, blue(map, 10, 10))
        assertTrue("the left edge tilts in x", abs(red(map, 40, 60) - 128) > 20)
        val down = apply(square(), RasterImageFilter.Effect.NormalMap(50f, yUp = false))
        assertTrue("Y down flips the green channel", (green(map, 60, 40) - 128) * (green(down, 60, 40) - 128) < 0)
    }

    @Test fun artisticExtractsLinesAndLimitsColours() {
        val lines = apply(square(), RasterImageFilter.Effect.Artistic(ArtisticParameters(ArtisticProcess.LINES_ONLY)))
        assertTrue("an outline is drawn", (lines.pixelAt(40, 60) ushr 24) > 128)
        assertEquals("flat areas carry no line", 0, lines.pixelAt(64, 64) ushr 24)
        val gradient = surface { x, y -> (0xFF shl 24) or ((x * 2) shl 16) or ((y * 2) shl 8) or 128 }
        val colours = apply(gradient, RasterImageFilter.Effect.Artistic(ArtisticParameters(ArtisticProcess.COLOR_ONLY, colors = 8, colorBlur = 0f)))
        assertTrue((0 until 128).flatMap { x -> (0 until 128 step 8).map { y -> colours.pixelAt(x, y) } }.toSet().size <= 27)
    }

    @Test fun pencilDrawingHatchesToneInGreyAndOutlinesEdges() {
        val pencil = apply(square(), RasterImageFilter.Effect.PencilDrawing(PencilParameters(size = 2f)))
        val inside = (40..87).map { red(pencil, it, 64) }
        assertTrue("hatching alternates graphite and paper", inside.max() - inside.min() > 60)
        assertTrue("paper stays white", red(pencil, 10, 10) > 240)
        assertTrue("greyscale", (40..87).all { abs(red(pencil, it, 64) - blue(pencil, it, 64)) < 8 })
        val outlineOnly = apply(square(), RasterImageFilter.Effect.PencilDrawing(PencilParameters(hatching = false)))
        assertTrue("the edge is outlined", red(outlineOnly, 40, 64) < 200); assertTrue(red(outlineOnly, 64, 64) > 240)
    }

    @Test fun filmLooksDifferAndAreSeeded() {
        val mid = surface { _, _ -> grey(128) }
        val warm = apply(mid, RasterImageFilter.Effect.Film(FilmParameters(FilmLook.WARM, noise = 0f)))
        assertTrue("the warm look is sepia", red(warm, 64, 64) > blue(warm, 64, 64) + 20)
        val vintage = apply(mid, RasterImageFilter.Effect.Film(FilmParameters(FilmLook.VINTAGE, noise = 80f, seed = 3)))
        assertArrayEquals(vintage.copyPremultipliedPixels(PixelRect(0, 0, 128, 128)),
            apply(mid, RasterImageFilter.Effect.Film(FilmParameters(FilmLook.VINTAGE, noise = 80f, seed = 3))).copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
        assertTrue("the vignette darkens corners", red(vintage, 1, 1) < red(vintage, 64, 64))
    }

    @Test fun presetsMapToTheEffectKinds() {
        val first = RasterFilterPreset.FIRST_EFFECT_KIND
        assertEquals(RasterImageFilter.Effect.RemoveJpegNoise, RasterFilterPreset(kind = first).effect())
        assertTrue(RasterFilterPreset(kind = first + 3, artProcess = 2).effect().let { it is RasterImageFilter.Effect.Artistic && it.parameters.process == ArtisticProcess.LINES_ONLY })
        assertTrue(RasterFilterPreset(kind = first + 5, mode = 2).effect().let { it is RasterImageFilter.Effect.Film && it.parameters.look == FilmLook.WARM })
        assertEquals(RasterFilterPreset.LAST_KIND, RasterFilterPreset(kind = 999).normalized().kind)
    }
}
