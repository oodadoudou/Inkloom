package app.inkloom.document

import app.inkloom.engine.v5.storage.DocumentCheckpointCodec
import app.inkloom.engine.v5.surface.PixelTileFormat
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GeneratedLayerTest {
    private fun render(generator: LayerGenerator, w: Int = 200, h: Int = 100, dpi: Float = 300f,
                       format: PixelTileFormat = PixelTileFormat.PREMULTIPLIED_SRGB_8888) =
        GeneratedLayerRenderer.render(generator, w, h, dpi, format).snapshot()

    @Test fun fillCoversThePageWithItsColour() {
        val fill = render(LayerGenerator.Fill(0xFF3366CC))
        assertEquals(0xFF3366CC.toInt(), fill.pixelAt(0, 0)); assertEquals(0xFF3366CC.toInt(), fill.pixelAt(199, 99))
        val half = render(LayerGenerator.Fill(0x80FF0000))
        assertEquals("translucent fills are premultiplied", 0x80, half.pixelAt(10, 10) ushr 24)
        assertEquals(0x80, half.pixelAt(10, 10) ushr 16 and 255)
    }

    @Test fun linearGradientRunsFromStartToEnd() {
        val gradient = render(LayerGenerator.Gradient(GradientDefinition(stops = listOf(GradientColorStop(0f, 0xFF000000), GradientColorStop(1f, 0xFFFFFFFF))),
            startX = 0f, startY = .5f, endX = 1f, endY = .5f))
        assertTrue(gradient.pixelAt(1, 50) and 255 < 10); assertTrue(gradient.pixelAt(198, 50) and 255 > 245)
        assertEquals("a linear gradient is constant across its direction", gradient.pixelAt(100, 0), gradient.pixelAt(100, 99))
        val radial = render(LayerGenerator.Gradient(GradientDefinition(GradientKind.RADIAL, stops = listOf(GradientColorStop(0f, 0xFF000000), GradientColorStop(1f, 0xFFFFFFFF))),
            startX = .5f, startY = .5f, endX = 1f, endY = .5f))
        assertTrue(radial.pixelAt(100, 50) and 255 < 10); assertEquals("pixel centres 40.5 px either side", radial.pixelAt(59, 50), radial.pixelAt(140, 50))
    }

    @Test fun toneCoverageFollowsDensityForEveryDotShape() {
        ToneDotShape.entries.forEach { shape ->
            listOf(.1f, .5f, .8f).forEach { density ->
                val tone = render(LayerGenerator.Tone(linesPerInch = 30f, density = density, dot = shape, angleDegrees = 0f), w = 200, h = 200)
                val coverage = (0 until 200).sumOf { y -> (0 until 200).sumOf { x -> (tone.pixelAt(x, y) ushr 24) } } / (255.0 * 200 * 200)
                assertEquals("$shape $density", density.toDouble(), coverage, .06)
            }
        }
        val zero = render(LayerGenerator.Tone(density = 0f)); assertEquals(0, zero.pixelAt(50, 50))
        val full = render(LayerGenerator.Tone(density = 1f)); assertEquals(255, full.pixelAt(50, 50) ushr 24)
    }

    @Test fun toneScreenPitchFollowsDpiAndLineCount() {
        // 300 dpi / 60 lpi = 5 px per cell: the pattern repeats every five pixels along the screen.
        val tone = render(LayerGenerator.Tone(linesPerInch = 60f, density = .3f, angleDegrees = 0f))
        (0 until 50).forEach { x -> assertTrue(abs((tone.pixelAt(x, 7) ushr 24) - (tone.pixelAt(x + 5, 7) ushr 24)) <= 2) }
    }

    @Test fun highPrecisionPagesGetTheSameColours() {
        val f16 = render(LayerGenerator.Fill(0xFF3366CC), format = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
        val c = f16.linearPixelAt(5, 5)
        assertEquals(1f, c.alpha, 1e-3f)
        assertTrue("linear light, not encoded values", abs(c.blue - .6038f) < .01f)
    }

    @Test fun generatedLayersRefusePaintButKeepTheirMaskPaintable() {
        val layers = listOf(InkLayer(id = "fill", name = "Fill 1", generator = LayerGenerator.Fill()))
        assertEquals(LayerEditingTarget.Rejection.GENERATED, LayerEditingTarget.rejection(layers, "fill"))
        assertNull(LayerEditingTarget.maskRejection(layers, "fill"))
    }

    @Test fun rasterizeKeepsPixelsAndDropsTheSetting() {
        val raster = RasterTileSurfaceRef("/tmp/m", "id", 10, 10, 128, 1)
        val page = InkPage(layers = defaultInkLayers() + InkLayer(id = "g", name = "Gradient 1", rasterTileSurface = raster,
            generator = LayerGenerator.Gradient(GradientDefinition(stops = listOf(GradientColorStop(0f, 0xFF000000), GradientColorStop(1f, 0xFFFFFFFF))))))
        val edit = GeneratedLayerEdits.rasterize(page, "g")
        val layer = edit.after.layers.first { it.id == "g" }
        assertNull(layer.generator); assertEquals(raster, layer.rasterTileSurface)
        assertNull(LayerEditingTarget.rejection(edit.after.layers, "g"))
        assertEquals(page, edit.apply(edit.after, true))
    }

    @Test fun generatorSettingsSurviveSavingTheDocument() {
        val tone = LayerGenerator.Tone(linesPerInch = 85f, density = .4f, dot = ToneDotShape.DIAMOND, angleDegrees = 30f)
        val document = InkDocument(id = "doc", title = "Doc", pages = listOf(InkPage(layers = defaultInkLayers() + InkLayer(name = "Tone 1", generator = tone))))
        val restored = DocumentCheckpointCodec.decode(DocumentCheckpointCodec.encode(document))
        assertEquals(tone, restored.pages.single().layers.last().generator)
    }
}
