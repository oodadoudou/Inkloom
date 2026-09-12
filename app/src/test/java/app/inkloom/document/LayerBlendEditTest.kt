package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerBlendEditTest {
    private val layer = InkLayer(id = "ink", name = "Ink")
    private val page = InkPage(layers = listOf(layer))

    @Test fun undoPreservesLaterPaintMaskOpacityAndHierarchy() {
        val edit = LayerBlendEdit(layer.id, LayerBlendMode.NORMAL, LayerBlendMode.MULTIPLY)
        val changed = requireNotNull(edit.apply(page))
        val later = changed.copy(layers = listOf(changed.layers.single().copy(
            opacity = .37f, name = "Later name", locked = true, parentId = "folder",
            mask = LayerMask(baseVisible = false),
            rasterTileSurface = RasterTileSurfaceRef("new-stroke.json", "pixels", 100, 100, 64, 9),
        )), elements = listOf(PageElement.Text(x = 1f, y = 2f, text = "New text")))
        val undone = requireNotNull(edit.apply(later, undo = true))
        assertEquals(later.copy(layers = later.layers.map { it.copy(blendMode = LayerBlendMode.NORMAL) }), undone)
        assertEquals(later, edit.apply(undone))
    }

    @Test fun everyExecutableModeRoundTripsAndNoOpIsExplicit() {
        for (mode in LayerBlendMode.entries) {
            val edit = LayerBlendEdit(layer.id, LayerBlendMode.NORMAL, mode)
            val changed = requireNotNull(edit.apply(page))
            assertEquals(mode == LayerBlendMode.NORMAL, edit.isNoOp)
            assertEquals(mode, changed.layers.single().blendMode)
            assertEquals(page, edit.apply(changed, undo = true))
        }
    }

    @Test fun rejectsMissingStaleAndStructuralTargets() {
        val edit = LayerBlendEdit(layer.id, LayerBlendMode.NORMAL, LayerBlendMode.MULTIPLY)
        assertNull(edit.apply(page.copy(layers = emptyList())))
        assertNull(edit.apply(page.copy(layers = listOf(layer.copy(blendMode = LayerBlendMode.SCREEN)))))
        assertNull(edit.apply(page, undo = true))
        for (role in listOf(LayerRole.FOLDER, LayerRole.BACKGROUND))
            assertNull(edit.apply(page.copy(layers = listOf(layer.copy(role = role)))))
    }
}
