package app.inkloom.engine.v3.scene

import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerMaskRuntimeTest {
    @Test fun paintEraseFillInvertUndoRedoAndThumbnailShareOneSparseTruth() {
        val runtime = LayerMaskRuntime(MaskTileSurface(8192, 8192, tileSize = 128))
        val bounds = PixelRect(4000, 4000, 4128, 4128)
        val dab = ByteArray(128 * 128) { 255.toByte() }

        val painted = requireNotNull(runtime.paint(bounds, dab))
        assertEquals(bounds, painted.dirtyBounds)
        assertEquals(255, runtime.snapshot.alphaAt(4050, 4050))
        assertEquals(4, runtime.snapshot.tileCount)

        runtime.erase(PixelRect(4040, 4040, 4080, 4080), ByteArray(40 * 40) { 128.toByte() })
        assertEquals(127, runtime.snapshot.alphaAt(4050, 4050))
        runtime.undo()
        assertEquals(255, runtime.snapshot.alphaAt(4050, 4050))
        runtime.redo()
        assertEquals(127, runtime.snapshot.alphaAt(4050, 4050))

        runtime.fill(255)
        assertEquals(255, runtime.snapshot.defaultAlpha)
        assertEquals(0, runtime.snapshot.tileCount)
        runtime.invert()
        assertTrue(runtime.snapshot.isEmpty)
        runtime.undo()
        assertEquals(255, runtime.snapshot.defaultAlpha)

        val thumbnail = runtime.thumbnail(64, 64)
        assertEquals(64, thumbnail.width)
        assertEquals(64, thumbnail.height)
        assertTrue(thumbnail.alpha.all { it.toInt().and(0xff) == 255 })
        assertTrue(runtime.historyByteSize > 0L)
        runtime.close()
    }

    @Test fun selectionRoundTripPreservesImplicitDefaultAndSparseDeviations() {
        val selection = MaskTileSurface(512, 512, tileSize = 64, initialDefaultAlpha = 255)
        selection.beginWrite("selection hole").run { setAlpha(10, 10, 0); commit().close() }
        val runtime = LayerMaskRuntime(MaskTileSurface(512, 512, tileSize = 64))

        runtime.replaceFromSelection(selection.snapshot())
        val restored = runtime.copyToSelection()

        assertEquals(255, restored.defaultAlpha)
        assertEquals(1, restored.tileCount)
        assertEquals(0, restored.alphaAt(10, 10))
        assertEquals(255, restored.alphaAt(400, 400))
    }

    @Test fun longMaskEditSessionStaysInsideConfiguredUndoMemoryBudget() {
        val budget = 256L * 1024
        val runtime = LayerMaskRuntime(
            MaskTileSurface(8192, 8192, tileSize = 128),
            maxHistoryByteSize = budget,
            maxUndoDepth = 1_000,
        )

        repeat(1_000) { index ->
            val coordinate = index % 128
            runtime.paint(
                PixelRect(coordinate, coordinate, coordinate + 1, coordinate + 1),
                byteArrayOf((index % 255 + 1).toByte()),
            )
            assertTrue("mask undo retained ${runtime.historyByteSize} bytes", runtime.historyByteSize <= budget)
        }

        assertTrue(runtime.historyByteSize > 0L)
        runtime.close()
        assertEquals(0L, runtime.historyByteSize)
    }
}
