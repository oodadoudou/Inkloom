package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.tools.RasterSelectionTransformSession
import app.inkloom.engine.v5.tools.SelectionAffineTransform
import org.junit.Assert.*
import org.junit.Test

class RasterSelectionTransformCoordinatesTest {
    @Test fun independentMaskResolutionAndTileSizeSupportRotationAndCancellation() {
        val pixels = PixelTileSurface(84, 126, tileSize = 128)
        pixels.beginWrite("blue square").use { write ->
            for (y in 30..31) for (x in 20..21) write.setPixel(x, y, 0xff0000ff.toInt())
            write.commit().close()
        }
        val mask = MaskTileSurface(42, 63, tileSize = 64)
        mask.beginWrite("one mask texel covers four raster pixels").use { write ->
            write.setAlpha(10, 15, 255)
            write.commit().close()
        }
        val originalPixels = pixels.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 84, 126))
        RasterSelectionTransformSession(pixels, mask.snapshot()).use { session ->
            val rotated = session.update(SelectionAffineTransform(42f, 63f, rotationDegrees = 90f))
            assertEquals(0, pixels.pixelAt(20, 30))
            assertEquals(0xff0000ff.toInt(), pixels.pixelAt(74, 41))
            assertEquals(42, rotated.selection.logicalWidth)
            assertEquals(63, rotated.selection.logicalHeight)
            assertEquals(0, rotated.selection.alphaAt(10, 15))
            assertTrue(rotated.selection.alphaAt(37, 20) > 0)
            session.cancel()
        }
        assertArrayEquals(originalPixels, pixels.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 84, 126)))
    }

    @Test fun repeatedPreviewComesFromOriginalPixelsAndMaskThenCommitsOneUndo() {
        val pixels = PixelTileSurface(128, 128, tileSize = 64)
        pixels.beginWrite("red square").use { write ->
            for (y in 32..39) for (x in 32..39) write.setPixel(x, y, 0xffff0000.toInt())
            write.commit().close()
        }
        val mask = MaskTileSurface(64, 64, tileSize = 128)
        mask.beginWrite("selection").use { write ->
            write.composeAlpha(PixelRect(16, 16, 20, 20), ByteArray(16) { -1 }, mode = MaskComposeMode.REPLACE)
            write.commit().close()
        }
        RasterSelectionTransformSession(pixels, mask.snapshot()).use { session ->
            session.update(SelectionAffineTransform(36f, 36f, scaleX = 2f, scaleY = .5f))
            val final = session.update(SelectionAffineTransform(36f, 36f, translateX = 20f, translateY = 12f))
            assertEquals(0xffff0000.toInt(), pixels.pixelAt(54, 46))
            assertEquals(0, pixels.pixelAt(34, 34))
            assertEquals(255, final.selection.alphaAt(27, 23))
            val committed = requireNotNull(session.commit())
            val record = requireNotNull(committed.transactionRecord)
            pixels.apply(record, PixelTileSurface.HistoryDirection.UNDO)
            assertEquals(0xffff0000.toInt(), pixels.pixelAt(34, 34))
            assertEquals(0, pixels.pixelAt(54, 46))
            pixels.apply(record, PixelTileSurface.HistoryDirection.REDO)
            assertEquals(0xffff0000.toInt(), pixels.pixelAt(54, 46))
            record.close()
        }
    }
}
