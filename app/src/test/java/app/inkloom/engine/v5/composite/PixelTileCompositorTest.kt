package app.inkloom.engine.v5.composite

import app.inkloom.engine.v5.surface.LinearRgba
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTileCompositorTest {
    @Test fun rgba8CompositeTouchesOnlyOccupiedSourceTilesAndUndoRestoresExactDestination() {
        val source = PixelTileSurface(256, 128, tileSize = 64)
        val destination = PixelTileSurface(256, 128, tileSize = 64)
        source.beginWrite("source").run {
            setPixel(130, 70, 0x80800000.toInt())
            commit().close()
        }
        destination.beginWrite("destination").run {
            setPixel(5, 5, 0xff00ff00.toInt())
            setPixel(130, 70, 0xff0000ff.toInt())
            commit().close()
        }
        val before = destination.snapshot()
        val untouchedGeneration = before.tileInfo.single { it.key == PixelTileKey(0, 0) }.generation

        val record = PixelTileCompositor.composite(source.snapshot(), destination, PixelBlendMode.NORMAL)

        assertEquals(1, record.changedTileCount)
        assertEquals(0xff80007f.toInt(), destination.pixelAt(130, 70))
        assertEquals(0xff00ff00.toInt(), destination.pixelAt(5, 5))
        assertEquals(
            untouchedGeneration,
            destination.snapshot().tileInfo.single { it.key == PixelTileKey(0, 0) }.generation,
        )
        destination.apply(record, HistoryDirection.UNDO)
        assertEquals(0xff0000ff.toInt(), destination.pixelAt(130, 70))
        destination.apply(record, HistoryDirection.REDO)
        assertEquals(0xff80007f.toInt(), destination.pixelAt(130, 70))
        record.close()
    }

    @Test fun perPixelCoverageCombinesWithLayerOpacityWithoutChangingSourceHue() {
        val source = PixelTileSurface(64, 64, tileSize = 64)
        val destination = PixelTileSurface(64, 64, tileSize = 64)
        source.beginWrite("red").run { setPixel(10, 10, 0xffff0000.toInt()); commit().close() }
        destination.beginWrite("blue").run { setPixel(10, 10, 0xff0000ff.toInt()); commit().close() }

        PixelTileCompositor.composite(
            source.snapshot(),
            destination,
            opacity = .5f,
            coverage = CompositeCoverage { x, y -> if (x == 10 && y == 10) .5f else 0f },
        ).close()

        assertEquals(0xff4000bf.toInt(), destination.pixelAt(10, 10))
    }

    @Test fun regionClipsWritesWithinAnOccupiedTile() {
        val source = PixelTileSurface(64, 64, tileSize = 64)
        val destination = PixelTileSurface(64, 64, tileSize = 64)
        source.beginWrite("two pixels").run {
            setPixel(10, 10, 0xffffffff.toInt())
            setPixel(20, 20, 0xffffffff.toInt())
            commit().close()
        }

        PixelTileCompositor.composite(
            source.snapshot(),
            destination,
            region = PixelRect(0, 0, 15, 15),
        ).close()

        assertEquals(0xffffffff.toInt(), destination.pixelAt(10, 10))
        assertEquals(0, destination.pixelAt(20, 20))
    }

    @Test fun f16CompositePreservesHdrAndNegativeLinearChannelsAcrossUndo() {
        val source = f16Surface()
        val destination = f16Surface()
        source.beginWrite("HDR source").run {
            setLinearPixel(8, 9, LinearRgba(2.5f, -.25f, .5f, 1f))
            commit().close()
        }
        destination.beginWrite("HDR destination").run {
            setLinearPixel(8, 9, LinearRgba(1.5f, .5f, -.2f, 1f))
            commit().close()
        }
        val before = destination.snapshot().copyTileF16(PixelTileKey(0, 0))

        val record = PixelTileCompositor.composite(source.snapshot(), destination, PixelBlendMode.ADD)

        assertEquals(4f, destination.linearPixelAt(8, 9).red, .01f)
        assertEquals(.25f, destination.linearPixelAt(8, 9).green, .01f)
        assertEquals(.3f, destination.linearPixelAt(8, 9).blue, .01f)
        destination.apply(record, HistoryDirection.UNDO)
        assertTrue(requireNotNull(before).contentEquals(destination.snapshot().copyTileF16(PixelTileKey(0, 0))))
        record.close()
    }

    @Test fun snapshotAlphaCoverageProvidesClippingMaskSemantics() {
        val source = PixelTileSurface(64, 64, tileSize = 64)
        val mask = PixelTileSurface(64, 64, tileSize = 64)
        val destination = PixelTileSurface(64, 64, tileSize = 64)
        source.beginWrite("source").run {
            setPixel(1, 1, 0xffff0000.toInt())
            setPixel(2, 2, 0xffff0000.toInt())
            commit().close()
        }
        mask.beginWrite("base alpha").run { setPixel(1, 1, 0x80808080.toInt()); commit().close() }

        PixelTileCompositor.composite(
            source.snapshot(),
            destination,
            coverage = SnapshotAlphaCoverage(mask.snapshot()),
        ).close()

        assertEquals(128, destination.pixelAt(1, 1) ushr 24)
        assertEquals(0, destination.pixelAt(2, 2))
    }

    private fun f16Surface() = PixelTileSurface(
        logicalWidth = 64,
        logicalHeight = 64,
        tileSize = 64,
        pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
    )
}
