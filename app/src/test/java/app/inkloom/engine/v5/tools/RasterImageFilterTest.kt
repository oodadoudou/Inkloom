package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterImageFilterTest {
    @Test fun gaussianBlurCrossesTileBoundaryWithoutSeam() {
        val source = PixelTileSurface(128, 64, tileSize = 64)
        source.beginWrite("edge").run {
            setPixel(63, 24, 0xFFFFFFFF.toInt())
            commit().close()
        }
        val output = RasterImageFilter.apply(source.snapshot(), RasterImageFilter.Effect.GaussianBlur(2f))
        val left = output.pixelAt(62, 24) ushr 24
        val right = output.pixelAt(64, 24) ushr 24
        assertTrue(left > 0)
        assertEquals("opposite sides of a tile boundary should match", left, right)
        assertEquals(0, output.pixelAt(80, 24))
    }

    @Test fun selectionPreservesExcludedPixelsExactlyAndOnlyWeightsFeatheredPixels() {
        val source = PixelTileSurface(64, 64, tileSize = 64)
        source.beginWrite("ink").run {
            setPixel(20, 20, 0xFF4080C0.toInt())
            setPixel(30, 20, 0xFF4080C0.toInt())
            commit().close()
        }
        val mask = MaskTileSurface(64, 64, tileSize = 64, initialDefaultAlpha = 255)
        mask.beginWrite("selection").run {
            setAlpha(20, 20, 0)
            setAlpha(30, 20, 128)
            commit().close()
        }
        val effect = RasterImageFilter.Effect.GaussianBlur(2f)
        val full = RasterImageFilter.apply(source.snapshot(), effect)
        val selected = RasterImageFilter.apply(source.snapshot(), effect, mask.snapshot())
        assertEquals(0xFF4080C0.toInt(), selected.pixelAt(20, 20))
        val originalAlpha = 255
        val fullAlpha = full.pixelAt(30, 20) ushr 24
        val featheredAlpha = selected.pixelAt(30, 20) ushr 24
        assertTrue(featheredAlpha < originalAlpha)
        assertTrue(featheredAlpha > fullAlpha)
        assertEquals(0xFF4080C0.toInt(), source.pixelAt(30, 20))
    }

    @Test fun unsharpKeepsOriginalAlphaAndRejectsUnsupportedParameters() {
        val source = PixelTileSurface(64, 64, tileSize = 64)
        source.beginWrite("gray edge").run {
            for (y in 10..30) for (x in 10..30) setPixel(x, y, 0xFF808080.toInt())
            commit().close()
        }
        val output = RasterImageFilter.apply(source.snapshot(),
            RasterImageFilter.Effect.UnsharpMask(sigma = 2f, amount = 1.2f))
        assertEquals(255, output.pixelAt(10, 20) ushr 24)
        assertEquals(0, output.pixelAt(9, 20))
        assertTrue((output.pixelAt(10, 20) ushr 16 and 255) > 128)
        try {
            RasterImageFilter.apply(source.snapshot(), RasterImageFilter.Effect.GaussianBlur(Float.NaN))
            throw AssertionError("invalid radius was accepted")
        } catch (_: IllegalArgumentException) {
            // The production dispatcher must surface this reason instead of silently doing nothing.
        }
    }
}
