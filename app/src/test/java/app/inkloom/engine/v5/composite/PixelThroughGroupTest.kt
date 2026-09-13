package app.inkloom.engine.v5.composite

import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test

class PixelThroughGroupTest {
    private fun pixels(vararg points: Pair<Int, Int>) = PixelTileSurface(128, 64, tileSize = 64).also {
        it.beginWrite("fixture").run { points.forEach { (x, color) -> setPixel(x, 10, color) }; commit().close() }
    }
    private fun paint(id: String, surface: PixelTileSurface, mode: PixelBlendMode = PixelBlendMode.NORMAL) =
        PixelProjectionNode.Paint(PixelLayerProjectionInput(id, surface.snapshot(), blendMode = mode))

    @Test fun throughChildMultiplyUsesTheExternalBackdropAndGroupOpacityOnlyOnce() {
        val blue = pixels(10 to 0xff0000ff.toInt()); val red = pixels(10 to 0xffff0000.toInt())
        val cache = PixelGroupProjectionCache(128, 64, 64)
        fun group(opacity: Float, through: Boolean) = listOf(paint("blue", blue), PixelProjectionNode.Group("group",
            listOf(paint("red", red, PixelBlendMode.MULTIPLY)), opacity, passThrough = through))
        assertEquals(0xffff0000.toInt(), cache.update(group(1f, false)).snapshot.pixelAt(10, 10))
        assertEquals(0xff000000.toInt(), cache.update(group(1f, true)).snapshot.pixelAt(10, 10))
        assertEquals(0xff000080.toInt(), cache.update(group(.5f, true)).snapshot.pixelAt(10, 10))
        assertEquals(0xff0000ff.toInt(), cache.update(group(0f, true)).snapshot.pixelAt(10, 10))
    }

    @Test fun translucentBackdropIsNotRepeatedAndOverlappingChildrenShareOneOpacity() {
        val blue = pixels(10 to 0x80000080.toInt(), 20 to 0x80000080.toInt())
        val red = pixels(10 to 0xffff0000.toInt(), 20 to 0xffff0000.toInt())
        val green = pixels(20 to 0xff00ff00.toInt())
        val result = PixelGroupProjectionCache(128, 64, 64).update(listOf(paint("blue", blue),
            PixelProjectionNode.Group("group", listOf(paint("red", red), paint("green", green)), .5f, passThrough = true))).snapshot
        assertEquals(192, result.pixelAt(10, 10) ushr 24)
        assertEquals(192, result.pixelAt(20, 10) ushr 24)
        assertEquals(0xc0800040.toInt(), result.pixelAt(10, 10))
        assertEquals(0xc0008040.toInt(), result.pixelAt(20, 10))
    }

    @Test fun maskAndLivePreviewInvalidateOnlyTheAffectedContributionTile() {
        val blue = pixels(10 to 0xff0000ff.toInt(), 80 to 0xff0000ff.toInt())
        val red = pixels(10 to 0xffff0000.toInt(), 80 to 0xffff0000.toInt())
        val mask = MaskTileSurface(128, 64, tileSize = 64, initialDefaultAlpha = 255)
        val cache = PixelGroupProjectionCache(128, 64, 64)
        fun nodes(snapshot: MaskTileSurfaceSnapshot) = listOf(paint("blue", blue),
            PixelProjectionNode.Group("group", listOf(paint("red", red)), mask = snapshot, passThrough = true))
        val initial = cache.update(nodes(mask.snapshot())).snapshot
        val untouched = initial.generationAt(PixelTileKey(1, 0))
        mask.beginWrite("live mask gesture").use { write ->
            write.setAlpha(10, 10, 0)
            val first = write.previewSnapshot()
            val hidden = cache.update(nodes(first))
            assertEquals(0xff0000ff.toInt(), hidden.snapshot.pixelAt(10, 10))
            assertEquals(untouched, hidden.snapshot.generationAt(PixelTileKey(1, 0)))
            write.setAlpha(10, 10, 128)
            val second = write.previewSnapshot()
            assertNotEquals(first.revision, second.revision)
            assertNotEquals(first.generationAt(PixelTileKey(0, 0)), second.generationAt(PixelTileKey(0, 0)))
            val partial = cache.update(nodes(second)).snapshot.pixelAt(10, 10)
            assertEquals(0xff80007f.toInt(), partial)
            assertEquals(0, first.alphaAt(10, 10))
            write.cancel()
        }
        assertEquals(0xffff0000.toInt(), cache.update(nodes(mask.snapshot())).snapshot.pixelAt(10, 10))
    }

    @Test fun isolatedChildrenInsideThroughDoNotBlendAgainstTheOutsideAndClippingDoesNotEscapeTheGroup() {
        val blue = pixels(10 to 0xff0000ff.toInt()); val red = pixels(10 to 0xffff0000.toInt())
        val cache = PixelGroupProjectionCache(128, 64, 64)
        val isolated = PixelProjectionNode.Group("isolated", listOf(paint("red", red, PixelBlendMode.MULTIPLY)))
        assertEquals(0xffff0000.toInt(), cache.update(listOf(paint("blue", blue),
            PixelProjectionNode.Group("through", listOf(isolated), passThrough = true))).snapshot.pixelAt(10, 10))
        val orphanClip = PixelProjectionNode.Paint(PixelLayerProjectionInput("red", red.snapshot(), clipToBaseBelow = true))
        assertEquals("A group's first clipped child cannot use the parent backdrop as its base", 0xff0000ff.toInt(),
            cache.update(listOf(paint("blue", blue), PixelProjectionNode.Group("through", listOf(orphanClip), passThrough = true))).snapshot.pixelAt(10, 10))
    }
    @Test fun throughInterpolationPreservesExtendedLinearColorAndScaledMaskCoverage() {
        val format = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16
        fun hdr(color: LinearRgba) = PixelTileSurface(128, 64, 64, pixelFormat = format).also {
            it.beginWrite("HDR").run { for (x in listOf(10, 80)) setLinearPixel(x, 10, color); commit().close() }
        }
        val back = hdr(LinearRgba(0f, 0f, 1f, .5f))
        val front = hdr(LinearRgba(2.5f, -.25f, .5f, 1f))
        val mask = MaskTileSurface(64, 32, 64, initialDefaultAlpha = 255)
        mask.beginWrite("scaled hole").run { setAlpha(5, 5, 0); commit().close() }
        val result = PixelGroupProjectionCache(128, 64, 64, format).update(listOf(paint("back", back),
            PixelProjectionNode.Group("group", listOf(paint("front", front)), .5f, mask = mask.snapshot(), passThrough = true))).snapshot
        val hidden = result.linearPixelAt(10, 10)
        assertEquals(.5f, hidden.alpha, .002f); assertEquals(.5f, hidden.blue, .002f)
        val mixed = result.linearPixelAt(80, 10)
        assertEquals(.75f, mixed.alpha, .002f); assertEquals(1.25f, mixed.red, .005f)
        assertEquals(-.125f, mixed.green, .005f); assertEquals(.5f, mixed.blue, .005f)
    }

}
