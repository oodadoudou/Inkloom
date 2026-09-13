package app.inkloom.engine.v5.composite

import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.*
import org.junit.Test

class PixelGroupProjectionCacheTest {
    private fun surface(vararg points: Pair<Int, Int>) = PixelTileSurface(128, 64, tileSize = 64).also { surface ->
        surface.beginWrite("fixture").run { points.forEach { (x, color) -> setPixel(x, 10, color) }; commit().close() }
    }
    private fun paint(id: String, surface: PixelTileSurface) = PixelProjectionNode.Paint(PixelLayerProjectionInput(id, surface.snapshot()))

    @Test fun groupOpacityIsAppliedOnceAfterChildrenOverlap() {
        val red = surface(10 to 0xffff0000.toInt(), 20 to 0xffff0000.toInt())
        val blue = surface(20 to 0xff0000ff.toInt(), 30 to 0xff0000ff.toInt())
        val cache = PixelGroupProjectionCache(128, 64, tileSize = 64)
        val result = cache.update(listOf(PixelProjectionNode.Group("group", listOf(paint("red", red), paint("blue", blue)), .5f))).snapshot
        assertEquals(result.pixelAt(10, 10) ushr 24, result.pixelAt(20, 10) ushr 24)
        assertEquals(128, result.pixelAt(20, 10) ushr 24)
        assertEquals(0, result.pixelAt(20, 10) ushr 16 and 255)
        assertEquals(128, result.pixelAt(20, 10) and 255)
        assertEquals(0xffff0000.toInt(), red.pixelAt(20, 10))
        assertEquals(0xff0000ff.toInt(), blue.pixelAt(20, 10))
    }

    @Test fun childBlendIsIsolatedFromLayersOutsideItsGroup() {
        val blue = surface(10 to 0xff0000ff.toInt())
        val red = surface(10 to 0xffff0000.toInt())
        val multiply = PixelProjectionNode.Paint(PixelLayerProjectionInput("red", red.snapshot(), blendMode = PixelBlendMode.MULTIPLY))
        val cache = PixelGroupProjectionCache(128, 64, tileSize = 64)
        val isolated = cache.update(listOf(paint("blue", blue), PixelProjectionNode.Group("group", listOf(multiply)))).snapshot
        assertEquals(0xffff0000.toInt(), isolated.pixelAt(10, 10))
        val groupMultiply = cache.update(listOf(paint("blue", blue), PixelProjectionNode.Group("group", listOf(multiply), blendMode = PixelBlendMode.MULTIPLY))).snapshot
        assertEquals(0xff000000.toInt(), groupMultiply.pixelAt(10, 10))
    }

    @Test fun nestedOpacityAndMaskApplyToTheWholeSubtreeAndCacheUnchangedTiles() {
        val red = surface(10 to 0xffff0000.toInt(), 80 to 0xffff0000.toInt())
        val mask = MaskTileSurface(128, 64, tileSize = 64, initialDefaultAlpha = 255)
        val cache = PixelGroupProjectionCache(128, 64, tileSize = 64)
        fun nodes() = listOf(PixelProjectionNode.Group("outer", listOf(PixelProjectionNode.Group("inner", listOf(paint("red", red)), .5f)), .5f, mask = mask.snapshot()))
        val first = cache.update(nodes())
        assertEquals(64, first.snapshot.pixelAt(10, 10) ushr 24)
        val untouched = first.snapshot.tileInfo.single { it.key == PixelTileKey(1, 0) }.generation
        assertEquals(0, cache.update(nodes()).recomposedTileCount)
        mask.beginWrite("hide left").run { setAlpha(10, 10, 0); commit().close() }
        val masked = cache.update(nodes())
        assertEquals(0, masked.snapshot.pixelAt(10, 10))
        assertEquals(64, masked.snapshot.pixelAt(80, 10) ushr 24)
        assertEquals(1, masked.recomposedTileCount)
        assertEquals(untouched, masked.snapshot.tileInfo.single { it.key == PixelTileKey(1, 0) }.generation)
        assertEquals(0, cache.update(emptyList()).snapshot.tileCount)
    }

    @Test fun f16GroupsKeepExtendedChannelsAndFractionalOpacity() {
        val format = app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16
        val ink = PixelTileSurface(128, 64, tileSize = 64, pixelFormat = format)
        ink.beginWrite("HDR paint").run {
            setLinearPixel(10, 10, app.inkloom.engine.v5.surface.LinearRgba(2.5f, -.25f, .5f, 1f))
            commit().close()
        }
        val result = PixelGroupProjectionCache(128, 64, tileSize = 64, format = format).update(
            listOf(PixelProjectionNode.Group("group", listOf(paint("ink", ink)), .25f))).snapshot.linearPixelAt(10, 10)
        assertEquals(2.5f, result.red / result.alpha, .005f)
        assertEquals(-.25f, result.green / result.alpha, .005f)
        assertEquals(.25f, result.alpha, .005f)
    }

    @Test fun groupActiveEraseDeltaUpdatesOnlyItsTileWithoutChangingCommittedInk() {
        val ink = surface(10 to 0xffff0000.toInt(), 80 to 0xffff0000.toInt())
        val delta = surface()
        val cache = PixelGroupProjectionCache(128, 64, tileSize = 64)
        fun nodes() = listOf(PixelProjectionNode.Group("group", listOf(PixelProjectionNode.Paint(
            PixelLayerProjectionInput("ink", ink.snapshot(), activeDelta = delta.snapshot(), activeDeltaBlend = PixelActiveDeltaBlend.DESTINATION_OUT))), .5f))
        val before = cache.update(nodes()).snapshot
        val right = before.tileInfo.single { it.key == PixelTileKey(1, 0) }.generation
        delta.beginWrite("erase left").run { setPixel(10, 10, 0xffffffff.toInt()); commit().close() }
        val after = cache.update(nodes())
        assertEquals(0, after.snapshot.pixelAt(10, 10))
        assertEquals(128, after.snapshot.pixelAt(80, 10) ushr 24)
        assertEquals(2, after.recomposedTileCount)
        assertEquals(right, after.snapshot.tileInfo.single { it.key == PixelTileKey(1, 0) }.generation)
        assertEquals(0xffff0000.toInt(), ink.pixelAt(10, 10))
    }
}
