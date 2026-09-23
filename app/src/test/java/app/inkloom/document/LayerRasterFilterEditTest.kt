package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerRasterFilterEditTest {
    @Test fun filterCheckpointReplacesOnlyTargetAndOneUndoRestoresOriginal() {
        val original = RasterTileSurfaceRef("before", "surface-before", 64, 64, 64, 1)
        val filtered = RasterTileSurfaceRef("after", "surface-after", 64, 64, 64, 1)
        val ink = InkLayer(id = "ink", name = "Ink", rasterTileSurface = original,
            rasterRotationQuarterTurns = 1)
        val other = InkLayer(id = "other", name = "Other")
        val page = InkPage(layers = listOf(ink, other))
        val edit = LayerRasterFilterEdit(ink.id, original, filtered, beforeRotation = 1)
        val committed = requireNotNull(edit.apply(page))
        assertEquals(filtered, committed.layers.first().rasterTileSurface)
        assertEquals(0, committed.layers.first().rasterRotationQuarterTurns)
        assertEquals(other, committed.layers.last())
        assertEquals(page, edit.apply(committed, undo = true))
        assertNull(edit.apply(page, undo = true))
        assertNull(edit.apply(committed))
    }
}
