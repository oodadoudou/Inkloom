package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test

class SelectionLayerTransferTest {
    @Test fun copyAndCutPreservePositionSoftAlphaAndBothPixelFormats() {
        PixelTileFormat.entries.forEach { format ->
            val source = PixelTileSurface(80, 80, 64, pixelFormat = format)
            source.beginWrite("source").use { tx ->
                if (format == PixelTileFormat.PREMULTIPLIED_SRGB_8888) tx.setPixel(20, 30, 0xff804020.toInt())
                else tx.setLinearPixel(20, 30, LinearRgba(1.5f, .5f, -.2f, 1f))
                tx.commit().close()
            }
            val mask = MaskTileSurface(80, 80, 64)
            mask.beginWrite("soft selection").use { it.setAlpha(20, 30, 128); it.commit().close() }
            val before = source.snapshot()
            val copy = SelectionLayerTransfer.create(source, mask.snapshot(), false)!!
            assertEquals(before.pixelAt(20, 30), source.pixelAt(20, 30))
            assertEquals(0, copy.destination.pixelAt(21, 30))
            assertTrue(copy.destination.pixelAt(20, 30) ushr 24 in 127..128)
            val cut = SelectionLayerTransfer.create(source, mask.snapshot(), true)!!
            assertEquals(copy.destination.pixelAt(20, 30), cut.destination.pixelAt(20, 30))
            if (format == PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
                assertTrue(cut.destination.linearPixelAt(20, 30).let { it.red > it.alpha && it.blue < 0f })
            cut.undoSource()
            assertEquals(before.pixelAt(20, 30), source.pixelAt(20, 30))
            cut.redoSource()
            assertTrue(source.pixelAt(20, 30) ushr 24 in 126..128)
            cut.close(); copy.close()
        }
    }
}
