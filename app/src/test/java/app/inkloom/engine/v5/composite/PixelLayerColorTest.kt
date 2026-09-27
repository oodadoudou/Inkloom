package app.inkloom.engine.v5.composite

import app.inkloom.document.LayerColorReplacement
import app.inkloom.engine.v5.surface.PackedRgbaF16
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PixelLayerColorTest {
    private val colors = LayerColorReplacement(0xffff0000L, 0xff0000ffL)

    @Test fun mapsBlackAndWhiteWithoutChangingPremultipliedAlpha() {
        assertEquals(0xffff0000.toInt(), PixelLayerColor.replaceRgba8(0xff000000.toInt(), colors))
        assertEquals(0xff0000ff.toInt(), PixelLayerColor.replaceRgba8(0xffffffff.toInt(), colors))
        assertEquals(0x80800000.toInt(), PixelLayerColor.replaceRgba8(0x80000000.toInt(), colors))
        assertEquals(0, PixelLayerColor.replaceRgba8(0, colors))
        val half = PackedRgbaF16.packPremultiplied(0f, 0f, 0f, .5f)
        val output = PixelLayerColor.replaceF16(half, colors)
        assertEquals(.5f, PackedRgbaF16.alpha(output), .001f)
        assertEquals(.5f, PackedRgbaF16.premultipliedRed(output), .001f)
    }

    @Test fun projectionRecomputesAfterColorChangeAndNeverMutatesSource() {
        val source = PixelTileSurface(64, 64, 64)
        source.beginWrite("color fixture").run {
            setPixel(10, 10, 0xff000000.toInt())
            commit().close()
        }
        val cache = PixelLayerProjectionCache(64, 64, 64)
        fun input(color: LayerColorReplacement) = PixelLayerProjectionInput("ink", source.snapshot(), colorReplacement = color)
        val red = cache.update(listOf(input(colors))).snapshot.pixelAt(10, 10)
        assertEquals(0xffff0000.toInt(), red)
        assertEquals(0, cache.update(listOf(input(colors))).recomposedTileCount)
        val green = cache.update(listOf(input(colors.copy(darkArgb = 0xff00ff00L)))).snapshot.pixelAt(10, 10)
        assertNotEquals(red, green)
        assertEquals(0xff000000.toInt(), source.pixelAt(10, 10))
    }
}
