package app.inkloom.engine.v5.composite

import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelLayerProjectionCacheTest {
    @Test fun unchangedDependenciesReuseEveryProjectionTileGeneration() {
        val bottom = layerSurface(0xff0000ff.toInt(), 10, 10)
        val top = layerSurface(0x80800000.toInt(), 10, 10)
        val cache = PixelLayerProjectionCache(256, 128, tileSize = 64)
        val layers = listOf(
            PixelLayerProjectionInput("bottom", bottom.snapshot()),
            PixelLayerProjectionInput("top", top.snapshot()),
        )

        val first = cache.update(layers)
        val generations = first.snapshot.tileInfo.associate { it.key to it.generation }
        val second = cache.update(layers)

        assertEquals(1, first.recomposedTileCount)
        assertEquals(0, second.recomposedTileCount)
        assertEquals(1, second.reusedTileCount)
        assertEquals(generations, second.snapshot.tileInfo.associate { it.key to it.generation })
        assertEquals(0xff80007f.toInt(), second.snapshot.pixelAt(10, 10))
    }

    @Test fun changingOneLayerTileRecompositesOnlyItsProjectionTile() {
        val bottom = PixelTileSurface(256, 128, tileSize = 64)
        bottom.beginWrite("two bottom tiles").run {
            setPixel(10, 10, 0xff0000ff.toInt())
            setPixel(130, 10, 0xff0000ff.toInt())
            commit().close()
        }
        val top = PixelTileSurface(256, 128, tileSize = 64)
        top.beginWrite("two top tiles").run {
            setPixel(10, 10, 0x80800000.toInt())
            setPixel(130, 10, 0x80800000.toInt())
            commit().close()
        }
        val cache = PixelLayerProjectionCache(256, 128, tileSize = 64)
        fun inputs() = listOf(
            PixelLayerProjectionInput("bottom", bottom.snapshot()),
            PixelLayerProjectionInput("top", top.snapshot()),
        )
        val initial = cache.update(inputs()).snapshot
        val untouchedBefore = initial.tileInfo.single { it.key == PixelTileKey(2, 0) }.generation

        top.beginWrite("change one top tile").run {
            setPixel(10, 10, 0x80008000.toInt())
            commit().close()
        }
        val updated = cache.update(inputs())

        assertEquals(1, updated.recomposedTileCount)
        assertEquals(1, updated.reusedTileCount)
        assertNotEquals(0xff80007f.toInt(), updated.snapshot.pixelAt(10, 10))
        assertEquals(0xff80007f.toInt(), updated.snapshot.pixelAt(130, 10))
        assertEquals(
            untouchedBefore,
            updated.snapshot.tileInfo.single { it.key == PixelTileKey(2, 0) }.generation,
        )
    }

    @Test fun removingTheLastSourcePixelInvalidatesAndPrunesTheOldProjectionTile() {
        val layer = layerSurface(0xffffffff.toInt(), 70, 10)
        val cache = PixelLayerProjectionCache(256, 128, tileSize = 64)
        cache.update(listOf(PixelLayerProjectionInput("paint", layer.snapshot())))
        assertEquals(1, cache.snapshot.tileCount)

        layer.beginWrite("erase source").run { setPixel(70, 10, 0); commit().close() }
        val update = cache.update(listOf(PixelLayerProjectionInput("paint", layer.snapshot())))

        assertEquals(1, update.recomposedTileCount)
        assertEquals(0, update.snapshot.tileCount)
        assertEquals(0, update.snapshot.pixelAt(70, 10))
    }

    @Test fun masksAndClippingBaseAlphaAreAppliedWithoutMutatingEitherLayer() {
        val base = layerSurface(0x80808080.toInt(), 10, 10)
        val clipped = layerSurface(0xffff0000.toInt(), 10, 10)
        val mask = MaskTileSurface(256, 128, tileSize = 64).also { surface ->
            surface.beginWrite("half mask").run { setAlpha(10, 10, 128); commit().close() }
        }
        val cache = PixelLayerProjectionCache(256, 128, tileSize = 64)

        val update = cache.update(listOf(
            PixelLayerProjectionInput("base", base.snapshot()),
            PixelLayerProjectionInput(
                layerId = "clipped",
                pixels = clipped.snapshot(),
                mask = mask.snapshot(),
                clipToBaseBelow = true,
            ),
        ))

        val result = update.snapshot.pixelAt(10, 10)
        assertEquals(160, result ushr 24)
        assertTrue((result ushr 16 and 0xff) > (result and 0xff))
        assertEquals(0x80808080.toInt(), base.pixelAt(10, 10))
        assertEquals(0xffff0000.toInt(), clipped.pixelAt(10, 10))
    }

    @Test fun layerOpacityOrVisibilityInvalidatesOnlyTilesOwnedByThatLayer() {
        val left = layerSurface(0xffff0000.toInt(), 10, 10)
        val right = layerSurface(0xff0000ff.toInt(), 130, 10)
        val cache = PixelLayerProjectionCache(256, 128, tileSize = 64)
        cache.update(listOf(
            PixelLayerProjectionInput("left", left.snapshot()),
            PixelLayerProjectionInput("right", right.snapshot()),
        ))

        val opacityUpdate = cache.update(listOf(
            PixelLayerProjectionInput("left", left.snapshot(), opacity = .5f),
            PixelLayerProjectionInput("right", right.snapshot()),
        ))
        assertEquals(1, opacityUpdate.recomposedTileCount)
        assertEquals(1, opacityUpdate.reusedTileCount)

        val hiddenUpdate = cache.update(listOf(
            PixelLayerProjectionInput("left", left.snapshot(), visible = false),
            PixelLayerProjectionInput("right", right.snapshot()),
        ))
        assertEquals(1, hiddenUpdate.recomposedTileCount)
        assertEquals(0, hiddenUpdate.snapshot.pixelAt(10, 10))
        assertEquals(0xff0000ff.toInt(), hiddenUpdate.snapshot.pixelAt(130, 10))
    }

    @Test fun changingOneMaskTileInvalidatesOnlyOverlappingProjectionTiles() {
        val left = layerSurface(0xffff0000.toInt(), 10, 10)
        val right = layerSurface(0xff0000ff.toInt(), 130, 10)
        val mask = MaskTileSurface(128, 64, tileSize = 64, initialDefaultAlpha = 255)
        val cache = PixelLayerProjectionCache(256, 128, tileSize = 64)
        fun inputs() = listOf(
            PixelLayerProjectionInput("left", left.snapshot(), mask = mask.snapshot()),
            PixelLayerProjectionInput("right", right.snapshot(), mask = mask.snapshot()),
        )
        cache.update(inputs())

        // Nearest-neighbour sampling maps destination pixel centre (10.5, 10.5) to (5, 5)
        // on this half-resolution mask.
        mask.beginWrite("hide left point").run { setAlpha(5, 5, 0); commit().close() }
        val update = cache.update(inputs())

        assertEquals(1, update.recomposedTileCount)
        assertEquals(1, update.reusedTileCount)
        assertEquals(0, update.snapshot.pixelAt(10, 10))
        assertEquals(0xff0000ff.toInt(), update.snapshot.pixelAt(130, 10))
    }

    @Test fun cachedUnderlayMatchesFreshProjectionAfterTopAndMiddleLayerChanges() {
        val surfaces = MutableList(10) { index ->
            layerSurface(
                0x30000000 or ((index * 23) shl 16) or ((index * 17) shl 8) or (index * 11),
                10,
                10,
            )
        }
        fun inputs() = surfaces.mapIndexed { index, surface ->
            PixelLayerProjectionInput(
                layerId = "layer-$index",
                pixels = surface.snapshot(),
                blendMode = if (index % 3 == 0) PixelBlendMode.MULTIPLY else PixelBlendMode.NORMAL,
            )
        }
        val cached = PixelLayerProjectionCache(256, 128, tileSize = 64)
        cached.update(inputs())

        surfaces.last().beginWrite("edit top layer").run {
            setPixel(10, 10, 0xa0408020.toInt())
            commit().close()
        }
        val topChanged = cached.update(inputs()).snapshot.pixelAt(10, 10)
        val freshAfterTop = PixelLayerProjectionCache(256, 128, tileSize = 64)
            .update(inputs()).snapshot.pixelAt(10, 10)
        assertEquals(freshAfterTop, topChanged)

        surfaces[4].beginWrite("edit middle layer").run {
            setPixel(10, 10, 0x90902070.toInt())
            commit().close()
        }
        val middleChanged = cached.update(inputs()).snapshot.pixelAt(10, 10)
        val freshAfterMiddle = PixelLayerProjectionCache(256, 128, tileSize = 64)
            .update(inputs()).snapshot.pixelAt(10, 10)
        assertEquals(freshAfterMiddle, middleChanged)
    }

    private fun layerSurface(color: Int, x: Int, y: Int): PixelTileSurface =
        PixelTileSurface(256.coerceAtLeast(x + 1), 128.coerceAtLeast(y + 1), tileSize = 64).also { surface ->
            surface.beginWrite("layer fixture").run { setPixel(x, y, color); commit().close() }
        }
}
