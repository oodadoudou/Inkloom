package app.inkloom.engine.v5.tools

import app.inkloom.document.AdjustmentCurvePoint
import app.inkloom.document.LayerAdjustment
import app.inkloom.document.LayerAdjustmentKind
import app.inkloom.engine.v5.composite.PixelAdjustmentProjectionCache
import app.inkloom.engine.v5.surface.*
import app.inkloom.settings.RasterFilterPreset
import app.inkloom.settings.RasterFilterPresetsCodec
import org.junit.Assert.*
import org.junit.Test

class RasterTonalCorrectionTest {
    private fun surface(format: PixelTileFormat = PixelTileFormat.PREMULTIPLIED_SRGB_8888) = PixelTileSurface(96, 64, pixelFormat = format).apply {
        beginWrite("fixture").use { w ->
            for (x in 0 until 96) {
                if (format.isHighPrecision) w.setLinearPixel(x, 10, LinearRgba(x / 95f, .3f, .7f, 1f)) else w.setPixel(x, 10, (0xFF shl 24) or ((x * 2) shl 16) or (80 shl 8) or 200)
            }
            w.setPixel(5, 30, 0x80400000.toInt()); w.commit().close()
        }
    }.snapshot()

    @Test fun bakedCorrectionsMatchAdjustmentLayersPixelForPixel() {
        listOf(LayerAdjustment(LayerAdjustmentKind.INVERT), LayerAdjustment(LayerAdjustmentKind.HUE_SATURATION, hueDegrees = 60f, saturation = .4f),
            LayerAdjustment(LayerAdjustmentKind.CURVES, curve = listOf(AdjustmentCurvePoint(0f, .2f), AdjustmentCurvePoint(1f, .8f))),
            LayerAdjustment(LayerAdjustmentKind.THRESHOLD, threshold = .4f)).forEach { adjustment ->
            listOf(PixelTileFormat.PREMULTIPLIED_SRGB_8888, PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16).forEach { format ->
                val source = surface(format)
                val baked = RasterImageFilter.apply(source, RasterImageFilter.Effect.Tonal(adjustment)).snapshot()
                val layer = PixelAdjustmentProjectionCache(96, 64, source.tileSize, format, "layer").update(source, adjustment, 1f, null).snapshot
                (0 until 96).forEach { x -> assertEquals("$adjustment $format x=$x", layer.packedOrArgb(x, 10), baked.packedOrArgb(x, 10)) }
                assertEquals(0, baked.pixelAt(50, 50))
            }
        }
    }

    @Test fun invertKeepsTransparencyAndASelectionLimitsTheResult() {
        val source = surface()
        val mask = MaskTileSurface(96, 64).apply { beginWrite("left").use { w -> for (y in 0 until 64) for (x in 0 until 48) w.setAlpha(x, y, 255); w.commit().close() } }.snapshot()
        val inverted = RasterImageFilter.apply(source, RasterImageFilter.Effect.Tonal(LayerAdjustment(LayerAdjustmentKind.INVERT)), mask).snapshot()
        assertNotEquals(source.pixelAt(10, 10), inverted.pixelAt(10, 10))
        assertEquals("outside the selection nothing changes", source.pixelAt(80, 10), inverted.pixelAt(80, 10))
        assertEquals("half-transparent pixels keep their alpha", 0x80, inverted.pixelAt(5, 30) ushr 24)
    }

    @Test fun tonalPresetsKeepTheirSettings() {
        val preset = RasterFilterPreset(name = "Warm curve", kind = RasterFilterPreset.FIRST_TONAL_KIND + 3,
            adjustment = LayerAdjustment(curve = listOf(AdjustmentCurvePoint(0f, .1f), AdjustmentCurvePoint(1f, 1f))))
        val restored = RasterFilterPresetsCodec.decode(RasterFilterPresetsCodec.encode(listOf(preset))).single()
        val effect = restored.effect() as RasterImageFilter.Effect.Tonal
        assertEquals(LayerAdjustmentKind.CURVES, effect.adjustment.kind)
        assertEquals(.1f, effect.adjustment.curve.first().output, 0f)
    }

    private fun PixelTileSurfaceSnapshot.packedOrArgb(x: Int, y: Int): Long =
        if (pixelFormat.isHighPrecision) packedF16At(x, y) else pixelAt(x, y).toLong()
}
