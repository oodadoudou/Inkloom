package app.inkloom.engine.v5.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterSelectionContentTest {
    @Test fun featheredCopyClearPasteAndUndoShareTheSameAlphaMask() {
        val pixels = PixelTileSurface(256, 256, tileSize = 64)
        pixels.beginWrite("seed").run {
            setPixel(80, 90, 0x80800000.toInt())
            commit().close()
        }
        val selection = MaskTileSurface(256, 256, tileSize = 64)
        selection.beginWrite("feathered point").run {
            setAlpha(80, 90, 128)
            commit().close()
        }

        val clipboard = requireNotNull(RasterSelectionContent.copy(pixels.snapshot(), selection.snapshot()))
        assertEquals(0x40400000, clipboard.rgba8!!.single())

        val cleared = requireNotNull(RasterSelectionContent.clear(pixels, selection.snapshot()))
        assertEquals(0x40400000, pixels.pixelAt(80, 90))
        pixels.apply(cleared.record, PixelTileSurface.HistoryDirection.UNDO)
        assertEquals(0x80800000.toInt(), pixels.pixelAt(80, 90))
        pixels.apply(cleared.record, PixelTileSurface.HistoryDirection.REDO)
        assertEquals(0x40400000, pixels.pixelAt(80, 90))
        cleared.record.close()

        val destination = PixelTileSurface(256, 256, tileSize = 64)
        val pasted = requireNotNull(RasterSelectionContent.paste(destination, clipboard, 10, 20))
        assertEquals(0x40400000, destination.pixelAt(10, 20))
        destination.apply(pasted.record, PixelTileSurface.HistoryDirection.UNDO)
        assertEquals(0, destination.pixelAt(10, 20))
        pasted.record.close()
    }

    @Test fun highPrecisionSelectionPreservesLinearExtendedColor() {
        val pixels = PixelTileSurface(
            128,
            128,
            tileSize = 64,
            pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
        )
        pixels.beginWrite("extended red").run {
            setLinearPixel(20, 30, LinearRgba(2f, .25f, .5f, .8f))
            commit().close()
        }
        val selection = MaskTileSurface(128, 128, tileSize = 64)
        selection.beginWrite("half alpha").run { setAlpha(20, 30, 128); commit().close() }

        val clipboard = requireNotNull(RasterSelectionContent.copy(pixels.snapshot(), selection.snapshot()))
        val destination = PixelTileSurface(
            128,
            128,
            tileSize = 64,
            pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
        )
        val paste = requireNotNull(RasterSelectionContent.paste(destination, clipboard, 50, 50))
        val result = destination.linearPixelAt(50, 50)

        assertEquals(2f * .8f * 128f / 255f, result.red, .005f)
        assertEquals(.8f * 128f / 255f, result.alpha, .005f)
        paste.record.close()
    }

    @Test fun sparseSelectionWorkIsBoundedToItsDirtyTiles() {
        val pixels = PixelTileSurface(8192, 8192, tileSize = 128)
        pixels.beginWrite("local fixture").run {
            for (y in 4000 until 4064) for (x in 4000 until 4064) setPixel(x, y, 0xffffffff.toInt())
            commit().close()
        }
        val selection = MaskTileSurface(8192, 8192, tileSize = 128)
        selection.beginWrite("local selection").run {
            val region = PixelRect(4010, 4010, 4050, 4050)
            replaceAlpha(region, ByteArray(region.width * region.height) { 255.toByte() })
            commit().close()
        }

        val started = System.nanoTime()
        val edit = requireNotNull(RasterSelectionContent.clear(pixels, selection.snapshot()))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000f

        assertTrue("Selection clear touched ${edit.record.changedTileCount} tiles", edit.record.changedTileCount <= 4)
        assertTrue("Sparse selection clear took ${elapsedMs}ms", elapsedMs < 100f)
        edit.record.close()
    }

    @Test fun boundedSelectionPlaneScalesIntoALargerRasterSurface() {
        val pixels = PixelTileSurface(400, 300, tileSize = 64)
        pixels.beginWrite("scaled fixture").run { setPixel(200, 150, 0xffabcdef.toInt()); commit().close() }
        val selection = MaskTileSurface(200, 150, tileSize = 64)
        selection.beginWrite("center selection").run {
            val region = PixelRect(90, 65, 110, 85)
            replaceAlpha(region, ByteArray(region.width * region.height) { 255.toByte() })
            commit().close()
        }

        val edit = requireNotNull(RasterSelectionContent.clear(pixels, selection.snapshot()))

        assertEquals(0, pixels.pixelAt(200, 150))
        assertTrue(edit.dirtyBounds.left <= 180 && edit.dirtyBounds.right >= 220)
        edit.record.close()
    }
}
