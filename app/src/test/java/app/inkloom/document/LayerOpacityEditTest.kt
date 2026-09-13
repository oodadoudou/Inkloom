package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerOpacityEditTest {
    private val layer = InkLayer(id = "ink", name = "Ink")
    private val page = InkPage(layers = listOf(layer))

    @Test fun undoChangesOnlyOpacityAndPreservesLaterContentAndProperties() {
        val edit = LayerOpacityEdit(layer.id, 1f, .5f)
        val changed = requireNotNull(edit.apply(page))
        val laterLayer = changed.layers.single().copy(name = "Renamed", locked = true,
            mask = LayerMask(baseVisible = false), parentId = "group", rasterRotationQuarterTurns = 1,
            rasterTileSurface = RasterTileSurfaceRef("new-stroke.json", "pixels", 100, 100, 64, 9))
        val later = changed.copy(title = "Later page", layers = listOf(laterLayer,
            InkLayer(id = "another", name = "New content")), elements = listOf(PageElement.Text(x = 1f, y = 2f, text = "New text")))
        val undone = requireNotNull(edit.apply(later, undo = true))
        assertEquals(later.copy(layers = listOf(laterLayer.copy(opacity = 1f), later.layers.last())), undone)
        assertEquals(later, edit.apply(undone))
    }

    @Test fun fullRangeAndNoOpRoundTripWithoutChangingLayerOrder() {
        for (opacity in listOf(0f, .01f, .5f, .99f, 1f)) {
            val edit = LayerOpacityEdit(layer.id, 1f, opacity)
            val changed = requireNotNull(edit.apply(page))
            assertEquals(opacity, changed.layers.single().opacity)
            assertEquals(page, edit.apply(changed, undo = true))
        }
    }

    @Test fun staleMissingAndStructuralTargetsAreRejected() {
        val edit = LayerOpacityEdit(layer.id, 1f, .5f)
        assertNull(edit.apply(page.copy(layers = emptyList())))
        assertNull(edit.apply(page.copy(layers = listOf(layer.copy(opacity = .7f)))))
        assertNull(edit.apply(page, undo = true))
        for (role in listOf(LayerRole.BACKGROUND)) {
            assertNull(edit.apply(page.copy(layers = listOf(layer.copy(role = role)))))
        }
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f)) {
            assertThrows(IllegalArgumentException::class.java) { LayerOpacityEdit(layer.id, 1f, invalid) }
        }
    }
}
