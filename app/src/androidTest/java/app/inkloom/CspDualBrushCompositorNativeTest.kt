package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.CspDualBrushCompositor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CspDualBrushCompositorNativeTest {
    @Test fun allModesMatchTheirScalarContractsForDensityAndRgb() {
        val primaryColor = Color.argb(102, 40, 80, 200)
        val secondaryColor = Color.argb(153, 220, 50, 30)
        val primary = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { setPixel(0, 0, primaryColor) }
        val secondary = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { setPixel(0, 0, secondaryColor) }
        val output = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            for (mode in 1..13) {
                CspDualBrushCompositor.compositeRegion(primary, secondary, output, Rect(0, 0, 1, 1), mode, false)
                val densityPixel = output.getPixel(0, 0)
                val expectedDensity = CspDualBrushCompositor.density(mode, 102 / 255f, 153 / 255f)
                assertNear("density alpha mode=$mode", (expectedDensity * 255f + .5f).toInt(), Color.alpha(densityPixel), 1)
                if (expectedDensity > 1f / 65_535f) {
                    // Android stores ARGB_8888 premultiplied; very low output alpha can quantize
                    // an unpremultiplied channel by several code points on readback.
                    assertNear("density red mode=$mode", Color.red(primaryColor), Color.red(densityPixel), 5)
                    assertNear("density green mode=$mode", Color.green(primaryColor), Color.green(densityPixel), 5)
                    assertNear("density blue mode=$mode", Color.blue(primaryColor), Color.blue(densityPixel), 5)
                }

                CspDualBrushCompositor.compositeRegion(primary, secondary, output, Rect(0, 0, 1, 1), mode, true)
                val rgbPixel = output.getPixel(0, 0)
                val baseAlpha = 102 / 255f
                val sourceAlpha = 153 / 255f
                val outputAlpha = sourceAlpha + baseAlpha * (1f - sourceAlpha)
                assertNear("RGB alpha mode=$mode", (outputAlpha * 255f + .5f).toInt(), Color.alpha(rgbPixel), 1)
                listOf(Color::red, Color::green, Color::blue).forEachIndexed { channel, extract ->
                    val base = extract(primaryColor) / 255f
                    val source = extract(secondaryColor) / 255f
                    val blended = CspDualBrushCompositor.blendChannel(mode, base, source)
                    val premultiplied = (1f - sourceAlpha) * base * baseAlpha +
                        (1f - baseAlpha) * source * sourceAlpha + baseAlpha * sourceAlpha * blended
                    val expected = (premultiplied / outputAlpha * 255f + .5f).toInt()
                    assertNear("RGB channel=$channel mode=$mode", expected, extract(rgbPixel), 2)
                }
            }
        } finally {
            primary.recycle(); secondary.recycle(); output.recycle()
        }
    }

    @Test fun boundedDualBrushCompositeMeetsTheSpatialToolFrameBudget() {
        val primary = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.argb(144, 32, 100, 220)) }
        val secondary = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.argb(112, 230, 60, 20)) }
        val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val region = Rect(128, 128, 384, 384)
        repeat(8) { CspDualBrushCompositor.compositeRegion(primary, secondary, output, region, 8, true) }
        val elapsed = ArrayList<Long>(80)
        repeat(80) {
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            CspDualBrushCompositor.compositeRegion(primary, secondary, output, region, 8, true)
            elapsed += android.os.SystemClock.elapsedRealtimeNanos() - started
        }
        val sorted = elapsed.sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "native_dual_brush_p95_ms=$p95Ms")
        assertTrue("Native dual-brush P95 was ${p95Ms}ms", p95Ms < 16.7f)
        primary.recycle(); secondary.recycle(); output.recycle()
    }

    @Test fun overlayFastPathsPreserveTransparentInputsAndBlendOpaqueInputs() {
        val primary = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.TRANSPARENT)
            setPixel(1, 0, Color.argb(170, 30, 90, 210))
            setPixel(2, 0, Color.rgb(40, 100, 220))
        }
        val secondary = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.argb(120, 220, 60, 20))
            setPixel(1, 0, Color.TRANSPARENT)
            setPixel(2, 0, Color.rgb(230, 60, 20))
        }
        val output = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        try {
            CspDualBrushCompositor.compositeRegion(primary, secondary, output, Rect(0, 0, 3, 1), 8, true)
            assertNear("transparent base", secondary.getPixel(0, 0), output.getPixel(0, 0), 0)
            assertNear("transparent source", primary.getPixel(1, 0), output.getPixel(1, 0), 0)
            assertNear("opaque alpha", 255, Color.alpha(output.getPixel(2, 0)), 0)
            listOf(Color::red, Color::green, Color::blue).forEach { extract ->
                val expected = CspDualBrushCompositor.blendChannel(
                    8,
                    extract(primary.getPixel(2, 0)) / 255f,
                    extract(secondary.getPixel(2, 0)) / 255f,
                )
                assertNear("opaque overlay", (expected * 255f + .5f).toInt(), extract(output.getPixel(2, 0)), 1)
            }
        } finally {
            primary.recycle(); secondary.recycle(); output.recycle()
        }
    }

    private fun assertNear(label: String, expected: Int, actual: Int, tolerance: Int) {
        assertTrue("$label expected=$expected actual=$actual", abs(expected - actual) <= tolerance)
    }
}
