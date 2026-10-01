package app.inkloom.engine.v5.composite

import app.inkloom.document.GradientColorStop
import app.inkloom.document.LayerAdjustment
import app.inkloom.document.LayerAdjustmentKind
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelAdjustmentTest {
    @Test fun channelAdjustmentsPreserveF16AlphaMasksAndSourcePixels() {
        val surface = PixelTileSurface(64, 64, 64, pixelFormat = app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
        val color = app.inkloom.engine.v5.surface.PackedRgbaF16.packPremultiplied(.3f, .2f, .1f, .5f)
        surface.beginWrite("fixture").use {
            it.editTileF16(app.inkloom.engine.v5.surface.PixelTileKey(0, 0), app.inkloom.engine.v5.surface.PixelRect(0, 0, 64, 64)) { pixels, stride ->
                pixels[10 * stride + 10] = color; pixels[10 * stride + 40] = color
            }
            it.commit().close()
        }
        val mask = MaskTileSurface(64, 64, 64, initialDefaultAlpha = 255)
        mask.beginWrite("mask").use { it.setAlpha(40, 10, 0); it.commit().close() }
        val adjustments = listOf(
            LayerAdjustment(LayerAdjustmentKind.LEVELS, redLevels = app.inkloom.document.AdjustmentLevels(outputWhite = 0f)),
            LayerAdjustment(LayerAdjustmentKind.CURVES, redCurve = listOf(app.inkloom.document.AdjustmentCurvePoint(0f, 0f),
                app.inkloom.document.AdjustmentCurvePoint(1f, 0f)), redCurveSmooth = true))
        adjustments.forEach { value ->
            val output = PixelAdjustmentProjectionCache(64, 64, 64, surface.pixelFormat, "f16-${value.kind}")
                .update(surface.snapshot(), value, 1f, mask.snapshot()).snapshot
            val changed = output.packedF16At(10, 10)
            assertEquals(.5f, app.inkloom.engine.v5.surface.PackedRgbaF16.alpha(changed), .001f)
            assertEquals(0f, app.inkloom.engine.v5.surface.PackedRgbaF16.premultipliedRed(changed), 0f)
            assertEquals(.2f, app.inkloom.engine.v5.surface.PackedRgbaF16.premultipliedGreen(changed), .001f)
            assertEquals(.1f, app.inkloom.engine.v5.surface.PackedRgbaF16.premultipliedBlue(changed), .001f)
            assertEquals(color, output.packedF16At(40, 10))
            assertEquals(color, surface.snapshot().packedF16At(10, 10))
        }
    }

    @Test fun singleChannelLevelsLeaveOtherColorsAndAlphaUntouched() {
        val value = LayerAdjustment(LayerAdjustmentKind.LEVELS,
            redLevels = app.inkloom.document.AdjustmentLevels(outputWhite = 0f))
        org.junit.Assert.assertArrayEquals(floatArrayOf(0f, .4f, .8f),
            PixelAdjustmentMath.apply(.2f, .4f, .8f, value), .00001f)
        val kernel = PixelAdjustmentKernel(value); val result = FloatArray(3)
        kernel.applyInto(.2f, .4f, .8f, result)
        org.junit.Assert.assertArrayEquals(PixelAdjustmentMath.apply(.2f, .4f, .8f, value), result, .00001f)
        val legacy = LayerAdjustment(LayerAdjustmentKind.LEVELS, inputBlack = .1f, inputGamma = 2f,
            inputWhite = .9f, outputBlack = .2f, outputWhite = .8f)
        val neutral = app.inkloom.document.AdjustmentLevels()
        assertEquals(legacy, legacy.copy(redLevels = neutral, greenLevels = neutral, blueLevels = neutral))
    }

    @Test fun levelsCombineRgbMasterWithAllChannelsAndSanitizeInvalidParameters() {
        val value = LayerAdjustment(LayerAdjustmentKind.LEVELS, outputBlack = .2f, outputWhite = .8f,
            redLevels = app.inkloom.document.AdjustmentLevels(outputWhite = 0f),
            greenLevels = app.inkloom.document.AdjustmentLevels(outputBlack = 1f),
            blueLevels = app.inkloom.document.AdjustmentLevels(inputGamma = 2f))
        org.junit.Assert.assertArrayEquals(floatArrayOf(.2f, .8f, .5f),
            PixelAdjustmentMath.apply(.2f, .4f, .25f, value), .00001f)
        val malformed = value.copy(redLevels = app.inkloom.document.AdjustmentLevels(Float.NaN,
            Float.POSITIVE_INFINITY, Float.NaN, Float.NEGATIVE_INFINITY, Float.NaN))
        val kernel = PixelAdjustmentKernel(malformed); val result = FloatArray(3)
        for (n in 0..100) {
            kernel.applyInto(n / 100f, .4f, .25f, result)
            org.junit.Assert.assertArrayEquals(PixelAdjustmentMath.apply(n / 100f, .4f, .25f, malformed), result, .00001f)
            assertTrue(result.all { it.isFinite() && it in 0f..1f })
        }
    }

    @Test fun channelLevelsRoundTripAndOldDocumentsDefaultToNeutral() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val value = LayerAdjustment(LayerAdjustmentKind.LEVELS,
            redLevels = app.inkloom.document.AdjustmentLevels(inputBlack = .2f),
            blueLevels = app.inkloom.document.AdjustmentLevels(outputWhite = .7f))
        val encoded = json.encodeToString(LayerAdjustment.serializer(), value)
        assertEquals(value, json.decodeFromString(LayerAdjustment.serializer(), encoded))
        val old = json.decodeFromString(LayerAdjustment.serializer(), """{"kind":"LEVELS","inputGamma":2.0}""")
        assertEquals(app.inkloom.document.AdjustmentLevels(), old.redLevels)
        assertEquals(.5f, PixelAdjustmentMath.apply(.25f, .25f, .25f, old)[0], .00001f)
    }

    @Test fun posterizeUsesExactPerChannelLevelsAndClampsInvalidCounts() {
        for (levels in listOf(-1, 2, 3, 8, 20, 200)) {
            val actual = levels.coerceIn(2, 20)
            val values = (0..1000).map { n ->
                PixelAdjustmentMath.apply(n / 1000f, n / 1000f, n / 1000f,
                    LayerAdjustment(LayerAdjustmentKind.POSTERIZE, posterizeLevels = levels))[0]
            }
            assertEquals(actual, values.distinct().size)
            assertEquals(0f, values.first(), 0f); assertEquals(1f, values.last(), 0f)
        }
    }

    @Test fun thresholdUsesLuminanceAndIncludesItsBoundary() {
        val value = LayerAdjustment(LayerAdjustmentKind.THRESHOLD, threshold = .5f)
        // Green is lighter than red despite the same channel magnitude.
        org.junit.Assert.assertArrayEquals(floatArrayOf(0f, 0f, 0f), PixelAdjustmentMath.apply(1f, 0f, 0f, value), 0f)
        org.junit.Assert.assertArrayEquals(floatArrayOf(1f, 1f, 1f), PixelAdjustmentMath.apply(0f, 1f, 0f, value), 0f)
        org.junit.Assert.assertArrayEquals(floatArrayOf(1f, 1f, 1f), PixelAdjustmentMath.apply(.5f, .5f, .5f, value), 0f)
    }

    @Test fun invertIsAnInvolutionAndLuminosityHasNeutralAndBlackWhiteEndpoints() {
        val rgb = floatArrayOf(.1f, .4f, .8f)
        val invert = LayerAdjustment(LayerAdjustmentKind.INVERT)
        val result = PixelAdjustmentMath.apply(rgb[0], rgb[1], rgb[2], invert)
        org.junit.Assert.assertArrayEquals(rgb, PixelAdjustmentMath.apply(result[0], result[1], result[2], invert), .00001f)
        val hsv = LayerAdjustment(LayerAdjustmentKind.HUE_SATURATION)
        org.junit.Assert.assertArrayEquals(rgb, PixelAdjustmentMath.apply(rgb[0], rgb[1], rgb[2], hsv), .00001f)
        for (amount in listOf(-1f, 1f)) {
            org.junit.Assert.assertArrayEquals(FloatArray(3) { if (amount > 0f) 1f else 0f },
                PixelAdjustmentMath.apply(rgb[0], rgb[1], rgb[2], hsv.copy(luminosity = amount)), .00001f)
        }
    }

    @Test fun newAdjustmentsPreserveAlphaAndMasksWithoutEditingOriginalPixels() {
        val ink = PixelTileSurface(64, 64, tileSize = 64)
        ink.beginWrite("fixture").use { write ->
            write.setPixel(10, 10, 0x80402010.toInt()); write.setPixel(40, 10, 0x80402010.toInt())
            write.commit().close()
        }
        val mask = MaskTileSurface(64, 64, 64, initialDefaultAlpha = 255)
        mask.beginWrite("mask").use { write -> write.setAlpha(40, 10, 0); write.commit().close() }
        for (kind in listOf(LayerAdjustmentKind.POSTERIZE, LayerAdjustmentKind.INVERT, LayerAdjustmentKind.THRESHOLD)) {
            val output = PixelAdjustmentProjectionCache(64, 64, 64, ink.pixelFormat, "test-$kind")
                .update(ink.snapshot(), LayerAdjustment(kind), 1f, mask.snapshot()).snapshot
            assertEquals(128, output.pixelAt(10, 10) ushr 24)
            assertEquals(0, output.pixelAt(0, 0))
            assertEquals(0x80402010.toInt(), output.pixelAt(40, 10))
            assertEquals(0x80402010.toInt(), ink.pixelAt(10, 10))
        }
    }

    @Test fun allAdjustmentKindsProduceFiniteBoundedRgb() {
        LayerAdjustmentKind.entries.forEach { kind ->
            val adjustment = LayerAdjustment(
                kind = kind,
                brightness = .2f,
                contrast = .25f,
                hueDegrees = 90f,
                saturation = .3f,
                inputBlack = .1f,
                inputGamma = .8f,
                inputWhite = .9f,
                shadowsRed = .2f,
                midtonesGreen = -.2f,
                highlightsBlue = .15f,
                gradient = listOf(GradientColorStop(0f, 0xFF102040), GradientColorStop(1f, 0xFFFFC080)),
            )
            val output = PixelAdjustmentMath.apply(.2f, .4f, .8f, adjustment)
            assertEquals(3, output.size)
            assertTrue("$kind $output", output.all { it.isFinite() && it in 0f..1f })
        }
    }

    @Test fun preparedPixelKernelMatchesReferenceOperationsWithoutPerPixelArrays() {
        val random = kotlin.random.Random(7)
        for (kind in LayerAdjustmentKind.entries) {
            val value = LayerAdjustment(kind = kind, brightness = .2f, contrast = -.3f,
                hueDegrees = 47f, saturation = .4f, luminosity = .25f, posterizeLevels = 5, threshold = .6f, inputBlack = .1f, inputGamma = 1.3f,
                inputWhite = .9f, outputBlack = .08f, outputWhite = .95f,
                shadowsRed = .2f, midtonesGreen = -.15f, highlightsBlue = .3f,
                gradient = listOf(GradientColorStop(0f, 0xFF102040), GradientColorStop(.6f, 0xFF55A0F0),
                    GradientColorStop(1f, 0xFFFFDD20)))
            val kernel = PixelAdjustmentKernel(value)
            val reused = FloatArray(3)
            repeat(100) {
                val red = random.nextFloat(); val green = random.nextFloat(); val blue = random.nextFloat()
                val reference = PixelAdjustmentMath.apply(red, green, blue, value)
                kernel.applyInto(red, green, blue, reused)
                for (channel in 0..2) assertEquals("$kind channel $channel", reference[channel], reused[channel], 0.00001f)
            }
        }
    }

    @Test fun adjustmentAffectsOnlyCompositedLayersBelowAndKeepsAlpha() {
        val ink = PixelTileSurface(64, 64, tileSize = 64)
        ink.beginWrite("gray").run { setPixel(10, 10, 0x80404040.toInt()); commit().close() }
        val cache = PixelGroupProjectionCache(64, 64, tileSize = 64)
        val adjustment = PixelProjectionNode.Adjustment("bright", LayerAdjustment(
            kind = LayerAdjustmentKind.BRIGHTNESS_CONTRAST,
            brightness = .4f,
        ))

        val changed = cache.update(listOf(
            PixelProjectionNode.Paint(PixelLayerProjectionInput("ink", ink.snapshot())), adjustment,
        )).snapshot.pixelAt(10, 10)

        assertEquals(128, changed ushr 24)
        assertTrue((changed ushr 16 and 255) > 64)
        assertEquals(0x80404040.toInt(), cache.update(listOf(adjustment, PixelProjectionNode.Paint(PixelLayerProjectionInput("ink", ink.snapshot())))).snapshot.pixelAt(10, 10))
    }

    @Test fun maskAndOpacityInterpolateWithoutChangingSourceSurface() {
        val ink = PixelTileSurface(64, 64, tileSize = 64)
        ink.beginWrite("black").run {
            setPixel(10, 10, 0xff000000.toInt()); setPixel(20, 10, 0xff000000.toInt()); commit().close()
        }
        val mask = MaskTileSurface(64, 64, tileSize = 64, initialDefaultAlpha = 255)
        mask.beginWrite("hide").run { setAlpha(20, 10, 0); commit().close() }
        val cache = PixelGroupProjectionCache(64, 64, tileSize = 64)
        fun nodes() = listOf(
            PixelProjectionNode.Paint(PixelLayerProjectionInput("ink", ink.snapshot())),
            PixelProjectionNode.Adjustment("levels", LayerAdjustment(kind = LayerAdjustmentKind.LEVELS, outputBlack = 1f), .5f, mask = mask.snapshot()),
        )

        val first = cache.update(nodes())
        assertEquals(128, first.snapshot.pixelAt(10, 10) ushr 16 and 255)
        assertEquals(0xff000000.toInt(), first.snapshot.pixelAt(20, 10))
        assertEquals(0, cache.update(nodes()).recomposedTileCount)
        assertEquals(0xff000000.toInt(), ink.pixelAt(10, 10))
    }

    @Test fun adjustmentBlendModeChangesResultAndInvalidatesOnlyChangedTile() {
        val ink = PixelTileSurface(64, 64, tileSize = 64)
        ink.beginWrite("gray").run { setPixel(10, 10, 0xff404040.toInt()); commit().close() }
        val cache = PixelGroupProjectionCache(64, 64, tileSize = 64)
        fun nodes(mode: PixelBlendMode) = listOf(
            PixelProjectionNode.Paint(PixelLayerProjectionInput("ink", ink.snapshot())),
            PixelProjectionNode.Adjustment("bright", LayerAdjustment(brightness = .5f), blendMode = mode),
        )
        val normal = cache.update(nodes(PixelBlendMode.NORMAL)).snapshot.pixelAt(10, 10)
        val multiply = cache.update(nodes(PixelBlendMode.MULTIPLY)).snapshot.pixelAt(10, 10)
        assertTrue((multiply ushr 16 and 255) < (normal ushr 16 and 255))
        assertEquals(0, cache.update(nodes(PixelBlendMode.MULTIPLY)).recomposedTileCount)
    }
}
