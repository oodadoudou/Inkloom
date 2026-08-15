package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v3.composite.PixelBlendMath
import app.inkloom.engine.v3.composite.PixelBlendMode
import app.inkloom.engine.v3.composite.PixelTileCompositor
import app.inkloom.engine.v3.composite.PixelLayerProjectionCache
import app.inkloom.engine.v3.composite.PixelLayerProjectionInput
import app.inkloom.engine.v3.composite.PremultipliedRgba
import app.inkloom.engine.v3.surface.LinearRgba
import app.inkloom.engine.v3.surface.PixelTileFormat
import app.inkloom.engine.v3.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativePixelCompositeParityTest {
    @Test fun batchedProjectionFastPathMatchesSequentialKotlinReference() {
        val firstPixel = 0x80604020.toInt()
        val secondPixel = 0x70402010
        val first = rgba8Surface(firstPixel)
        val second = rgba8Surface(secondPixel)
        val update = PixelLayerProjectionCache(64, 64, tileSize = 64).update(listOf(
            PixelLayerProjectionInput("first", first.snapshot(), opacity = .73f),
            PixelLayerProjectionInput(
                "second",
                second.snapshot(),
                opacity = .61f,
                blendMode = PixelBlendMode.MULTIPLY,
            ),
        ))
        val expected = PixelBlendMath.composite(
            unpackRgba8(secondPixel),
            PixelBlendMath.composite(
                unpackRgba8(firstPixel),
                PremultipliedRgba(0f, 0f, 0f, 0f),
                PixelBlendMode.NORMAL,
                .73f,
            ),
            PixelBlendMode.MULTIPLY,
            .61f,
        )
        val actual = unpackRgba8(update.snapshot.pixelAt(3, 4))

        assertEquals(expected.red, actual.red, 2.1f / 255f)
        assertEquals(expected.green, actual.green, 2.1f / 255f)
        assertEquals(expected.blue, actual.blue, 2.1f / 255f)
        assertEquals(expected.alpha, actual.alpha, 2.1f / 255f)
    }

    @Test fun nativeRgba8SeparableModesMatchKotlinReferenceWithinOneByte() {
        val sourcePixel = 0x80604020.toInt()
        val destinationPixel = 0xc0204080.toInt()
        PixelBlendMode.entries.take(14).forEach { mode ->
            val source = rgba8Surface(sourcePixel)
            val destination = rgba8Surface(destinationPixel)
            PixelTileCompositor.composite(source.snapshot(), destination, mode, opacity = .73f).close()
            val expected = PixelBlendMath.composite(
                unpackRgba8(sourcePixel),
                unpackRgba8(destinationPixel),
                mode,
                .73f,
            )
            val actual = unpackRgba8(destination.pixelAt(3, 4))
            assertEquals("$mode red", expected.red, actual.red, 1.1f / 255f)
            assertEquals("$mode green", expected.green, actual.green, 1.1f / 255f)
            assertEquals("$mode blue", expected.blue, actual.blue, 1.1f / 255f)
            assertEquals("$mode alpha", expected.alpha, actual.alpha, 1.1f / 255f)
        }
    }

    @Test fun nativeF16SeparableModesMatchKotlinReferenceWithoutClampingHdr() {
        val sourceStraight = LinearRgba(1.4f, -.1f, .6f, .6f)
        val destinationStraight = LinearRgba(.8f, .3f, 1.2f, .75f)
        PixelBlendMode.entries.take(14).forEach { mode ->
            val source = f16Surface(sourceStraight)
            val destination = f16Surface(destinationStraight)
            PixelTileCompositor.composite(source.snapshot(), destination, mode, opacity = .8f).close()
            val expected = PixelBlendMath.composite(
                premultiplied(sourceStraight),
                premultiplied(destinationStraight),
                mode,
                .8f,
            )
            val actual = destination.linearPixelAt(3, 4)
            assertEquals("$mode red", expected.red, actual.red, .004f)
            assertEquals("$mode green", expected.green, actual.green, .004f)
            assertEquals("$mode blue", expected.blue, actual.blue, .004f)
            assertEquals("$mode alpha", expected.alpha, actual.alpha, .002f)
        }
    }

    private fun rgba8Surface(pixel: Int): PixelTileSurface = PixelTileSurface(64, 64, tileSize = 64).also {
        it.beginWrite("native parity").run { setPixel(3, 4, pixel); commit().close() }
    }

    private fun f16Surface(color: LinearRgba): PixelTileSurface = PixelTileSurface(
        64,
        64,
        tileSize = 64,
        pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
    ).also {
        it.beginWrite("native F16 parity").run { setLinearPixel(3, 4, color); commit().close() }
    }

    private fun unpackRgba8(pixel: Int): PremultipliedRgba = PremultipliedRgba(
        (pixel ushr 16 and 0xff) / 255f,
        (pixel ushr 8 and 0xff) / 255f,
        (pixel and 0xff) / 255f,
        (pixel ushr 24 and 0xff) / 255f,
    )

    private fun premultiplied(color: LinearRgba): PremultipliedRgba = PremultipliedRgba(
        color.red * color.alpha,
        color.green * color.alpha,
        color.blue * color.alpha,
        color.alpha,
    )
}
