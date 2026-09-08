package app.inkloom

import app.inkloom.engine.v5.tools.NativeRasterOps
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class SelectionSamplerNativeTest {
    @Test fun nativeAffinePixelsMatchTheSparseColorOracleForEveryFormatAndFilter() {
        for (format in app.inkloom.engine.v5.surface.PixelTileFormat.entries) for (mode in app.inkloom.engine.v5.tools.SelectionInterpolation.entries) {
            val surface = app.inkloom.engine.v5.surface.PixelTileSurface(32, 32, 64, pixelFormat = format)
            surface.beginWrite("source").use { write ->
                for (y in 0 until 32) for (x in 0 until 32) {
                    if (format.isHighPrecision) write.setLinearPixel(x, y, app.inkloom.engine.v5.surface.LinearRgba(x / 10f, -.25f, y / 100f, (x + y + 1) / 64f))
                    else { val a = (x + y + 1) * 4; write.setPixel(x, y, (a shl 24) or ((a / 2) shl 16) or ((a / 3) shl 8)) }
                }
                write.commit().close()
            }
            val mask = app.inkloom.engine.v5.surface.MaskTileSurface(32, 32, 64)
            mask.beginWrite("soft selection").use { write ->
                for (y in 0 until 32) for (x in 0 until 32) write.setAlpha(x, y, (x * 13 + y * 23) % 256)
                write.commit().close()
            }
            val pixels = surface.snapshot(); val selection = mask.snapshot()
            val bounds = app.inkloom.engine.v5.surface.PixelRect(0, 0, 32, 32)
            val alpha = selection.copyAlpha(bounds)
            val source: Any = if (format.isHighPrecision) pixels.copyPremultipliedF16(bounds) else pixels.copyPremultipliedPixels(bounds)
            val output: Any = if (source is LongArray) source.copyOf() else (source as IntArray).copyOf()
            val inverse = floatArrayOf(.8f, .3f, -.2f, 1.1f, -3.25f, 2.3f)
            assertTrue(NativeRasterOps.affineSelectionTile(source, alpha, 0, 0, 32, 32, output, alpha, 0, 0, 32, 32, 32, inverse, mode.ordinal, format.isHighPrecision))
            val sampler = app.inkloom.engine.v5.tools.SelectionPixelSampler(pixels, selection, mode)
            for (y in 0 until 32) for (x in 0 until 32) {
                val sx = inverse[0]*(x+.5f) + inverse[1]*(y+.5f) + inverse[4]
                val sy = inverse[2]*(x+.5f) + inverse[3]*(y+.5f) + inverse[5]
                val retainedAlpha = 255 - selection.alphaAt(x, y)
                if (output is LongArray) {
                    val expected = app.inkloom.engine.v5.tools.SelectionPixelSampler.overF16(sampler.selectedF16(sx, sy),
                        app.inkloom.engine.v5.tools.SelectionPixelSampler.scaleF16(pixels.packedF16At(x, y), retainedAlpha / 255f))
                    val a = app.inkloom.engine.v5.surface.PackedRgbaF16.premultiplied(expected)
                    val b = app.inkloom.engine.v5.surface.PackedRgbaF16.premultiplied(output[y*32+x])
                    assertEquals(a.red, b.red, .002f); assertEquals(a.green, b.green, .002f)
                    assertEquals(a.blue, b.blue, .002f); assertEquals(a.alpha, b.alpha, .002f)
                } else {
                    val moved = sampler.selectedArgb(sx, sy)
                    val retained = app.inkloom.engine.v5.tools.SelectionPixelSampler.scaleArgb(pixels.pixelAt(x, y), retainedAlpha)
                    for (shift in listOf(0, 8, 16, 24)) {
                        val expected = (moved ushr shift and 255) + ((retained ushr shift and 255) * (255 - (moved ushr 24)) + 127) / 255
                        assertTrue("$mode $x,$y channel $shift", abs(expected - ((output as IntArray)[y*32+x] ushr shift and 255)) <= 1)
                    }
                }
            }
        }
    }

    @Test fun nativeAffineAlphaMatchesMaskOracleIncludingPageAndTileEdges() {
        val source = app.inkloom.engine.v5.surface.MaskTileSurface(129, 97, 64)
        source.beginWrite("fractional selection").use { write ->
            for (y in 0 until 97) for (x in 0 until 129) write.setAlpha(x, y, (x*13 + y*29) % 256)
            write.commit().close()
        }
        val mask = source.snapshot()
        val pixels = app.inkloom.engine.v5.surface.PixelTileSurface(129, 97, 64).snapshot()
        val full = app.inkloom.engine.v5.surface.PixelRect(0, 0, 129, 97)
        for (mode in app.inkloom.engine.v5.tools.SelectionInterpolation.entries) {
            val sampler = app.inkloom.engine.v5.tools.SelectionPixelSampler(pixels, mask, mode)
            for (m in listOf(floatArrayOf(.75f, .35f, -.35f, .75f, -10.25f, 19.75f),
                floatArrayOf(-1.25f, .17f, .3f, 1.5f, 130.5f, -30.25f))) {
                val out = ByteArray(137 * 103)
                val count = NativeRasterOps.affineSelectionAlpha(mask.copyAlpha(full), 0, 0, 129, 97,
                    out, 0, 0, 137, 103, m, mode.ordinal)
                assertTrue("Android must execute native affine alpha", count >= 0)
                var nonZero = 0
                for (y in 0 until 103) for (x in 0 until 137) {
                    val gx = x + .5f; val gy = y + .5f
                    val expected = sampler.maskAlpha(m[0]*gx + m[1]*gy + m[4], m[2]*gx + m[3]*gy + m[5])
                    val actual = out[y * 137 + x].toInt() and 255
                    assertTrue("$mode $x,$y expected $expected actual $actual", abs(expected - actual) <= 1)
                    if (actual != 0) nonZero++
                }
                assertEquals(nonZero, count)
            }
        }
    }

    @Test fun nativeAlphaFilteringMatchesThePremultipliedMaskOracle() {
        val random = Random(617)
        for (count in listOf(2, 4)) for (fraction in listOf(0f, .25f, .5f, .99f)) {
            val width = 129; val height = 65; val stride = width + count - 1
            val input = ByteArray(stride * (height + count - 1)) { random.nextInt(256).toByte() }
            fun weight(t: Float, i: Int): Float {
                if (count == 2) return if (i == 0) 1f - t else t
                val x = abs(t - i + 1)
                return when { x < 1f -> 1.5f*x*x*x - 2.5f*x*x + 1f; x < 2f -> -.5f*x*x*x + 2.5f*x*x - 4f*x + 2f; else -> 0f }
            }
            val wx = FloatArray(count) { weight(fraction, it) }
            val wy = FloatArray(count) { weight(1f - fraction, it) }
            val actual = ByteArray(width * height)
            val nativeCount = NativeRasterOps.translatedSelectionAlpha(input, stride, actual, width, height, wx, wy)
            assertTrue("Android must exercise the native filter", nativeCount >= 0)
            var nonZero = 0
            for (y in 0 until height) for (x in 0 until width) {
                var alpha = 0f
                for (row in 0 until count) for (column in 0 until count)
                    alpha += (input[(y + row) * stride + x + column].toInt() and 255) * wx[column] * wy[row]
                val expected = alpha.roundToInt().coerceIn(0, 255)
                val value = actual[y * width + x].toInt() and 255
                assertTrue("Native/reference rounding must stay within one alpha unit", abs(expected - value) <= 1)
                if (value > 0) nonZero++
            }
            assertEquals(nonZero, nativeCount)
        }
    }
}
