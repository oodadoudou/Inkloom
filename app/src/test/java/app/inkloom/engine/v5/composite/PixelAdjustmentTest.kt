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
                hueDegrees = 47f, saturation = .4f, inputBlack = .1f, inputGamma = 1.3f,
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
