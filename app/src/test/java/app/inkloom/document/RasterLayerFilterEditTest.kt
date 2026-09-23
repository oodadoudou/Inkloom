package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class RasterLayerFilterEditTest {
    private val before = RasterTileSurfaceRef("/before.json", "before", 64, 64, 64, 1)
    private val after = RasterTileSurfaceRef("/after.json", "after", 64, 64, 64, 2)

    @Test fun oneEditReplacesOnlyTargetPixelsAndUndoRestoresRotationAndLayerStructure() {
        val target = InkLayer(id = "ink-filter", name = "Ink", rasterTileSurface = before,
            rasterRotationQuarterTurns = 1, opacity = .4f, blendMode = LayerBlendMode.MULTIPLY)
        val untouched = InkLayer(id = "other", name = "Other")
        val page = InkPage(layers = defaultInkLayers() + target + untouched)
        val edit = RasterLayerFilterEdit(target.id, before, 1, after)
        val filtered = requireNotNull(edit.apply(page, false))
        assertEquals(after, filtered.layers.first { it.id == target.id }.rasterTileSurface)
        assertEquals(0, filtered.layers.first { it.id == target.id }.rasterRotationQuarterTurns)
        assertEquals(target.opacity, filtered.layers.first { it.id == target.id }.opacity)
        assertEquals(page, edit.apply(filtered, true))
        assertEquals(filtered, edit.apply(page, false))
        assertNull(edit.apply(filtered, false))
        assertNull(edit.apply(page, true))
    }
}
