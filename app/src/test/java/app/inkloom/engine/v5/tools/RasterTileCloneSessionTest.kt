package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test

class RasterTileCloneSessionTest {
    private val red = 0xFFFF0000.toInt()
    private fun surface(tile: Int = 64, color: (Int, Int) -> Int): PixelTileSurface = PixelTileSurface(128, 64, tile).apply {
        beginWrite("fixture").use { w -> for (y in 0 until 64) for (x in 0 until 128) color(x, y).takeIf { it != 0 }?.let { w.setPixel(x, y, it) }; w.commit().close() }
    }
    private fun commit(session: RasterTileCloneSession) = session.commit().close()

    @Test fun theSourceSidePaintsUnderTheBrushAcrossTiles() {
        val layer = surface { x, _ -> if (x in 10..29) red else 0 }
        RasterTileCloneSession(layer, offsetX = -60, offsetY = 0).also { it.stroke(70f, 10f, 80f, 50f, 8f, 1f, 1f); commit(it) }
        val after = layer.snapshot()
        assertEquals("copied from 60 px to the left", red, after.pixelAt(75, 30))
        assertEquals("outside the brush nothing changes", 0, after.pixelAt(110, 30))
        assertEquals("the source stays", red, after.pixelAt(15, 30))
    }

    @Test fun aStrokeNeverCopiesWhatItHasJustPainted() {
        // With a live source, a stroke moving away from a 10 px offset would drag the red stripe along.
        val layer = surface { x, _ -> if (x in 8..11) red else 0 }
        RasterTileCloneSession(layer, offsetX = -10, offsetY = 0).also { it.stroke(20f, 32f, 110f, 32f, 4f, 1f, 1f); commit(it) }
        val after = layer.snapshot()
        assertEquals(red, after.pixelAt(20, 32))
        assertEquals("the stripe is not dragged along", 0, after.pixelAt(60, 32))
    }

    @Test fun overlappingDabsKeepTheStrokeOpacity() {
        val layer = surface { x, _ -> if (x < 32) red else 0xFF0000FF.toInt() }
        RasterTileCloneSession(layer, offsetX = -64, offsetY = 0).also { session ->
            repeat(6) { session.stroke(80f, 32f, 90f, 32f, 6f, .5f, 1f); session.stroke(90f, 32f, 80f, 32f, 6f, .5f, 1f) }
            commit(session)
        }
        val mixed = layer.snapshot().pixelAt(85, 32)
        assertEquals("half red", 128f, (mixed ushr 16 and 255).toFloat(), 1f)
        assertEquals("half blue", 127f, (mixed and 255).toFloat(), 1f)
    }

    @Test fun selectionAndTransparencyLockLimitTheCopy() {
        val mask = MaskTileSurface(128, 64, tileSize = 64, initialDefaultAlpha = 0)
        mask.beginWrite("left half").run { for (y in 0 until 64) for (x in 0 until 90) setAlpha(x, y, 255); commit().close() }
        val layer = surface { x, _ -> if (x < 32) red else if (x in 60..127) 0x800000FF.toInt() else 0 }
        RasterTileCloneSession(layer, mask.snapshot(), preserveDestinationAlpha = true, offsetX = -60, offsetY = 0)
            .also { it.stroke(70f, 32f, 110f, 32f, 6f, 1f, 1f); commit(it) }
        val after = layer.snapshot()
        assertTrue("inside the selection the colour is copied", (after.pixelAt(80, 32) ushr 16 and 255) >= 0x7F && (after.pixelAt(80, 32) and 255) == 0)
        assertEquals("transparency lock keeps the coverage", 0x80, after.pixelAt(80, 32) ushr 24)
        assertEquals("outside the selection nothing changes", 0x800000FF.toInt(), after.pixelAt(100, 32))
    }

    @Test fun anEmptySelectionMeansTheWholeLayer() {
        val layer = surface { x, _ -> if (x < 32) red else 0 }
        val empty = MaskTileSurface(128, 64, tileSize = 64, initialDefaultAlpha = 0).snapshot()
        RasterTileCloneSession(layer, empty, preserveDestinationAlpha = false, offsetX = -64, offsetY = 0).also { it.stroke(70f, 32f, 80f, 32f, 6f, 1f, 1f); commit(it) }
        assertEquals(red, layer.snapshot().pixelAt(75, 32))
    }

    @Test fun cancellingLeavesTheLayerUntouched() {
        val layer = surface { x, _ -> if (x < 32) red else 0 }
        val before = layer.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 128, 64))
        RasterTileCloneSession(layer, offsetX = -64, offsetY = 0).use { it.stroke(70f, 10f, 90f, 50f, 8f, 1f, 1f) }
        assertArrayEquals(before, layer.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 128, 64)))
    }
}
