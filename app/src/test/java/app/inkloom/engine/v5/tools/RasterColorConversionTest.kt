package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test

class RasterColorConversionTest {
    @Test fun recolorRetainsAlphaAndUsesSoftSelectionWithoutTouchingUnselectedHDR() {
        for (format in PixelTileFormat.entries) {
            val source = PixelTileSurface(128, 128, 64, pixelFormat = format)
            source.beginWrite("line art").use {
                it.setPixel(20, 20, 0x80000080.toInt())
                if (format.isHighPrecision) it.setLinearPixel(90, 90, LinearRgba(2f, .25f, .5f, .8f))
                else it.setPixel(90, 90, 0xFF00FF00.toInt())
                it.commit().close()
            }
            val selection = MaskTileSurface(128, 128, 64)
            selection.beginWrite("half").use { it.setAlpha(20, 20, 128); it.commit().close() }
            val result = RasterImageFilter.apply(source.snapshot(), RasterImageFilter.Effect.ConvertToDrawingColor(0x00FF0000), selection.snapshot())
            assertEquals(128, result.pixelAt(20, 20) ushr 24)
            assertEquals(source.pixelAt(90, 90), result.pixelAt(90, 90))
            if (format.isHighPrecision) {
                assertEquals(source.snapshot().packedF16At(90, 90), result.snapshot().packedF16At(90, 90))
                assertEquals(.5f * 128f / 255f, result.linearPixelAt(20, 20).red, .004f)
            } else assertEquals(0x80400040.toInt(), result.pixelAt(20, 20))
            assertEquals(0x80000080.toInt(), source.pixelAt(20, 20))
        }
    }

    @Test fun brightnessConvertsWhiteGrayAndBlackToBlackCoverageWithoutIncreasingOpacity() {
        for (format in PixelTileFormat.entries) {
            val source = PixelTileSurface(128, 128, 64, pixelFormat = format)
            source.beginWrite("scan").use {
                it.setPixel(20, 20, -1); it.setPixel(21, 20, 0xFF808080.toInt())
                it.setPixel(22, 20, 0x80000000.toInt()); it.commit().close()
            }
            val result = RasterImageFilter.apply(source.snapshot(), RasterImageFilter.Effect.BrightnessToOpacity)
            assertEquals(0, result.pixelAt(20, 20))
            assertEquals(127f, (result.pixelAt(21, 20) ushr 24).toFloat(), 1f)
            assertEquals(0, result.pixelAt(21, 20) and 0xffffff)
            assertEquals(0x80000000.toInt(), result.pixelAt(22, 20))
            assertEquals(-1, source.pixelAt(20, 20))
        }
    }

    @Test fun conversionsAreSparseAndCancellableAndKeepOutsideSelectionExactly() {
        val source = PixelTileSurface(8192, 8192, 64)
        source.beginWrite("far apart").use { it.setPixel(10, 10, -1); it.setPixel(8000, 8000, -1); it.commit().close() }
        val mask = MaskTileSurface(8192, 8192, 64)
        mask.beginWrite("one point").use { it.setAlpha(10, 10, 255); it.commit().close() }
        val result = RasterImageFilter.apply(source.snapshot(), RasterImageFilter.Effect.BrightnessToOpacity, mask.snapshot())
        assertEquals(1, result.tileCount)
        assertEquals(-1, result.pixelAt(8000, 8000))
        try {
            RasterImageFilter.apply(source.snapshot(), RasterImageFilter.Effect.BrightnessToOpacity, cancelled = { true })
            fail("Must reject a superseded preview")
        } catch (_: java.util.concurrent.CancellationException) { }
        assertEquals(-1, source.pixelAt(10, 10))
    }
}
