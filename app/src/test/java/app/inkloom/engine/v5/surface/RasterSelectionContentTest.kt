package app.inkloom.engine.v5.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterSelectionContentTest {
    @Test fun outsideClearKeepsSoftSelectionAndExtendedColorWithOneTransaction() {
        for (format in PixelTileFormat.entries) {
            val pixels = PixelTileSurface(128, 128, 64, pixelFormat = format)
            pixels.beginWrite("content").use { write ->
                if (format == PixelTileFormat.PREMULTIPLIED_SRGB_8888) {
                    write.setPixel(20, 20, 0x80800000.toInt())
                    write.setPixel(90, 90, 0xFF0000FF.toInt())
                } else {
                    write.setLinearPixel(20, 20, LinearRgba(2f, .25f, .5f, .8f))
                    write.setLinearPixel(90, 90, LinearRgba(0f, 0f, 1f, 1f))
                }
                write.commit().close()
            }
            val before = pixels.snapshot()
            val selection = MaskTileSurface(128, 128, 64)
            selection.beginWrite("soft selection").use { it.setAlpha(20, 20, 128); it.commit().close() }
            val edit = requireNotNull(RasterSelectionContent.clear(pixels, selection.snapshot(), outside = true))
            assertEquals(2, edit.record.changedTileCount)
            if (format == PixelTileFormat.PREMULTIPLIED_SRGB_8888) {
                assertEquals(0x40400000, pixels.pixelAt(20, 20))
                assertEquals(0, pixels.pixelAt(90, 90))
            } else {
                assertEquals(2f * .8f * 128f / 255f, pixels.linearPixelAt(20, 20).red, .005f)
                assertEquals(.8f * 128f / 255f, pixels.linearPixelAt(20, 20).alpha, .003f)
                assertEquals(0f, pixels.linearPixelAt(90, 90).alpha, .001f)
            }
            pixels.apply(edit.record, PixelTileSurface.HistoryDirection.UNDO)
            assertEquals(before.pixelAt(20, 20), pixels.pixelAt(20, 20))
            assertEquals(before.pixelAt(90, 90), pixels.pixelAt(90, 90))
            pixels.apply(edit.record, PixelTileSurface.HistoryDirection.REDO)
            assertEquals(0, pixels.pixelAt(90, 90))
            assertEquals(128, selection.alphaAt(20, 20))
            edit.record.close()
        }
    }

    @Test fun outsideClearOnlyTouchesOccupiedTilesEvenAcrossSparse8KPage() {
        val pixels = PixelTileSurface(8192, 8192, 64)
        pixels.beginWrite("distant pixels").use { it.setPixel(20, 20, -1); it.setPixel(8000, 8000, -1); it.commit().close() }
        val selection = MaskTileSurface(8192, 8192, 64)
        selection.beginWrite("keep point").use { it.setAlpha(20, 20, 255); it.commit().close() }
        val edit = requireNotNull(RasterSelectionContent.clear(pixels, selection.snapshot(), outside = true))
        assertEquals(-1, pixels.pixelAt(20, 20))
        assertEquals(0, pixels.pixelAt(8000, 8000))
        assertEquals(1, edit.record.changedTileCount)
        assertEquals(1, pixels.tileCount)
        edit.record.close()
        assertEquals(null, RasterSelectionContent.clear(pixels, MaskTileSurface(8192, 8192, 64).snapshot(), outside = true))
    }

    @Test fun fillUnderpaintClosesAntialiasHaloAndIsErasableAndUndoable() {
        val pixels = PixelTileSurface(128, 64, tileSize = 64)
        pixels.beginWrite("antialiased line").use { write ->
            for (y in 0 until 64) { write.setPixel(63, y, 0xFF000000.toInt()); write.setPixel(62, y, 0x80000000.toInt()) }
            write.commit().close()
        }
        val mask = MaskTileSurface(128, 64, tileSize = 64)
        mask.beginWrite("outside line").use { write ->
            for (y in 0 until 64) for (x in 0 until 128) if (x !in 62..63) write.setAlpha(x, y, 255)
            write.commit().close()
        }
        val edit = requireNotNull(RasterSelectionContent.fill(pixels, mask.snapshot(), 0xFF000000.toInt(), 1f, 2))
        assertEquals(0xFF000000.toInt(), pixels.pixelAt(62, 30))
        assertEquals(0xFF000000.toInt(), pixels.pixelAt(64, 30))
        pixels.apply(edit.record, PixelTileSurface.HistoryDirection.UNDO)
        assertEquals(0x80000000.toInt(), pixels.pixelAt(62, 30))
        pixels.apply(edit.record, PixelTileSurface.HistoryDirection.REDO)
        val cleared = requireNotNull(RasterSelectionContent.clear(pixels, mask.snapshot()))
        assertEquals(0, pixels.pixelAt(64, 30))
        cleared.record.close(); edit.record.close()
    }

    @Test fun fillRespectsSoftSelectionAndLockedAlphaForBothColorFormats() {
        for (format in PixelTileFormat.entries) {
            val pixels = PixelTileSurface(64, 64, tileSize = 64, pixelFormat = format)
            pixels.beginWrite("half coverage").use { write ->
                if (format == PixelTileFormat.PREMULTIPLIED_SRGB_8888) write.setPixel(32, 32, 0x80000000.toInt())
                else write.setLinearPixel(32, 32, LinearRgba(0f, 0f, 0f, .5f))
                write.commit().close()
            }
            val mask = MaskTileSurface(64, 64, tileSize = 64)
            mask.beginWrite("selection").use { write -> write.setAlpha(32, 32, 128); write.setAlpha(33, 32, 255); write.commit().close() }
            val edit = requireNotNull(RasterSelectionContent.fill(pixels, mask.snapshot(), 0xFFFF0000.toInt(), 1f, preserveAlpha = true))
            if (format == PixelTileFormat.PREMULTIPLIED_SRGB_8888) {
                assertEquals(128, pixels.pixelAt(32, 32) ushr 24); assertEquals(0, pixels.pixelAt(33, 32))
            } else {
                assertEquals(.5f, pixels.linearPixelAt(32, 32).alpha, .002f); assertEquals(0f, pixels.linearPixelAt(33, 32).alpha, .002f)
            }
            edit.record.close()
        }
    }

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
