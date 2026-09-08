package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.tools.NativeRasterOps
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeStampCoverageParityTest {
    @Test fun nativeDualCoverageMatchesKotlinSamplingAndByteAccumulationExactly() {
        val random = java.util.Random(481209L)
        val tip = BrushTipMask(79, 53, ByteArray(79 * 53).also(random::nextBytes))
        val grain = BrushTipMask(17, 23, ByteArray(17 * 23).also(random::nextBytes))
        val stride = 32
        // Each case has its own incoming backdrop; opaque accumulation must not hide later errors.
        repeat(240) { case ->
            val expected = ByteArray(stride * stride).also {
                if (case % 3 != 0) random.nextBytes(it)
            }
            val actual = expected.copyOf()
            val expectedPaint = IntArray(stride * stride) { 0x40201008 }
            val actualPaint = expectedPaint.copyOf()
            val color = if (case % 2 == 0) 0xff29a5de.toInt() else 0x804123ef.toInt()
            val tileLeft = if (case % 2 == 0) 128 else 0
            val tileTop = 64
            val left = tileLeft + case % 5
            val top = tileTop + case % 7
            val right = tileLeft + stride - case % 3
            val bottom = tileTop + stride - case % 4
            val cx = tileLeft + random.nextFloat() * 48f - 8f
            val cy = tileTop + random.nextFloat() * 48f - 8f
            val rx = .05f + random.nextFloat() * 30f
            val ry = .05f + random.nextFloat() * 30f
            val angle = random.nextFloat() * 6.2831855f
            val cosine = cos(angle)
            val sine = sin(angle)
            val alpha = when (case % 8) { 0 -> 0f; 1 -> 1f; else -> random.nextFloat() }
            val density = random.nextFloat()
            val grainAngle = random.nextFloat() * 6.2831855f
            val gc = cos(grainAngle)
            val gs = sin(grainAngle)
            val wavelength = 1f + random.nextFloat() * 73f
            val movement = random.nextFloat()
            val phaseX = random.nextFloat() * 8f - 4f
            val phaseY = random.nextFloat() * 8f - 4f
            val hasGrain = case % 4 != 0
            val level = tip.levelForOutput(rx * 2f, ry * 2f)
            val nativeTip = tip.nativeLevel(level)
            val nativeGrain = grain.nativeLevel(0)
            for (y in top until bottom) for (x in left until right) {
                val dx = x + .5f - cx
                val dy = y + .5f - cy
                val localX = cosine * dx + sine * dy
                val localY = -sine * dx + cosine * dy
                var coverage = tip.coverageAtLevel((localX / rx + 1f) * .5f,
                    (localY / ry + 1f) * .5f, level) * alpha
                if (coverage <= 0f) continue
                if (hasGrain) {
                    val gx = x + .5f - cx * (1f - movement)
                    val gy = y + .5f - cy * (1f - movement)
                    val u = (gc * gx + gs * gy) / wavelength + phaseX
                    val v = (-gs * gx + gc * gy) / wavelength + phaseY
                    val sampled = grain.coverageAt(u - floor(u), v - floor(v))
                    coverage *= 1f - density + sampled * density
                }
                val paintIndex = (y - tileTop) * stride + x - tileLeft
                val paintCoverage = (1f - (1f - coverage.coerceIn(0f, 1f))).coerceIn(0f, 1f)
                val sourceAlpha = ((color ushr 24) * paintCoverage).toInt().coerceIn(0, 255)
                val inverse = 255 - sourceAlpha
                val oldPixel = expectedPaint[paintIndex]
                fun channel(shift: Int) = (((color ushr shift) and 255) * sourceAlpha / 255 +
                    ((oldPixel ushr shift) and 255) * inverse / 255).coerceAtMost(255)
                expectedPaint[paintIndex] = ((sourceAlpha + (oldPixel ushr 24) * inverse / 255).coerceAtMost(255) shl 24) or
                    (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                val incoming = (coverage.coerceIn(0f, 1f) * 255f + .5f).toInt().coerceIn(0, 255)
                if (incoming == 0) continue
                val index = (y - tileTop) * stride + x - tileLeft
                val old = expected[index].toInt() and 0xff
                expected[index] = (incoming + old * (255 - incoming) / 255).coerceAtMost(255).toByte()
            }
            assertTrue("Native kernel must execute case $case", NativeRasterOps.accumulateStampCoverage(
                actual, stride, tileLeft, tileTop, left, top, right, bottom,
                cx, cy, rx, ry, cosine, sine, alpha,
                nativeTip.coverage, nativeTip.width, nativeTip.height,
                if (hasGrain) nativeGrain.coverage else null, nativeGrain.width, nativeGrain.height,
                density, gc, gs, wavelength, movement, phaseX, phaseY,
            ))
            assertArrayEquals("Byte-exact dual coverage case $case", expected, actual)
            assertTrue(NativeRasterOps.depositUniformStamp(
                actualPaint, stride, tileLeft, tileTop, left, top, right, bottom,
                cx, cy, rx, ry, cosine, sine, alpha,
                nativeTip.coverage, nativeTip.width, nativeTip.height,
                if (hasGrain) nativeGrain.coverage else null, nativeGrain.width, nativeGrain.height,
                density, gc, gs, wavelength, movement, phaseX, phaseY, color,
            ))
            assertArrayEquals("Byte-exact uniform stamp case $case", expectedPaint, actualPaint)
        }
    }

    @Test fun everyDualAlphaPairMatchesAllModesPaintAndEraserExactly() {
        val primary = ByteArray(65536) { (it % 256).toByte() }
        val secondary = ByteArray(65536) { (it / 256).toByte() }
        val colors = listOf(0xff29a5de.toInt(), 0x804123ef.toInt(), 0x00123456)
        for (mode in app.inkloom.engine.v5.raster.DualBrushCompositeMode.entries) {
            val coverage = IntArray(65536) {
                app.inkloom.engine.v5.raster.StampPaintOp.combineDualCoverage(it % 256, it / 256, mode)
            }
            for (color in colors) for (erase in listOf(false, true)) {
                val expected = IntArray(65536) { index ->
                    val alpha = (((if (erase) 255 else color ushr 24) * (coverage[index] / 255f)) + .5f).toInt()
                    if (erase) (alpha shl 24) or (alpha shl 16) or (alpha shl 8) or alpha
                    else (alpha shl 24) or ((((color ushr 16) and 255) * alpha / 255) shl 16) or
                        ((((color ushr 8) and 255) * alpha / 255) shl 8) or ((color and 255) * alpha / 255)
                }
                val actual = IntArray(65536)
                assertTrue(NativeRasterOps.compositeDualCoverage(actual, primary, secondary, 256,
                    0, 0, 256, 256, mode.ordinal, color, erase))
                assertArrayEquals("Dual mode=$mode color=$color erase=$erase", expected, actual)
            }
        }
        val clipped = IntArray(16) { 0x12345678 }
        assertTrue(NativeRasterOps.compositeDualCoverage(clipped, null, null, 4,
            1, 1, 3, 3, 0, -1, false))
        assertArrayEquals(IntArray(16) { if (it / 4 in 1..2 && it % 4 in 1..2) 0 else 0x12345678 }, clipped)
        val beforeInvalid = clipped.copyOf()
        assertFalse(NativeRasterOps.compositeDualCoverage(clipped, null, null, 4,
            0, 0, 4, 4, 13, -1, false))
        assertArrayEquals(beforeInvalid, clipped)
    }

    @Test fun invalidNativeCoverageArgumentsDoNotPartiallyWrite() {
        val original = byteArrayOf(0, 12, 64, -1)
        for (invalid in 0..3) {
            val actual = original.copyOf()
            assertFalse(NativeRasterOps.accumulateStampCoverage(
                actual, 2, 0, 0, 0, 0, if (invalid == 0) 3 else 2, 2,
                if (invalid == 1) Float.NaN else 1f, 1f, 1f, 1f, 1f, 0f, 1f,
                byteArrayOf(-1), if (invalid == 2) 2 else 1, 1,
                byteArrayOf(-1), 1, 1, 1f, 1f, 0f, if (invalid == 3) 0f else 1f, 0f, 0f, 0f,
            ))
            assertArrayEquals(original, actual)
        }
    }
}
