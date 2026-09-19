package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerTreeEditTest {
    private val folder = InkLayer(id = "folder", name = "Art", role = LayerRole.FOLDER)
    private val nested = InkLayer(id = "nested", name = "Detail", role = LayerRole.FOLDER, parentId = folder.id)
    private val ink = InkLayer(id = "paint", name = "Paint", parentId = nested.id)
    private val objectElement = PageElement.Image(id = "image", layerId = ink.id, x = .1f, y = .1f,
        width = .2f, height = .2f, assetPath = "/tmp/inkloom-layer-image")
    private val page = InkPage(layers = defaultInkLayers() + folder + nested + ink,
        elements = listOf(objectElement))

    @Test fun `duplicate nested group copies all IDs and objects and undo restores original tree`() {
        val ids = ArrayDeque(listOf("folder-copy", "nested-copy", "paint-copy", "image-copy"))
        val edit = LayerTreeEdit.create(page, LayerTreeAction.DUPLICATE, folder.id,
            nextId = ids::removeFirst)
        val copied = edit.apply(page, false)!!
        assertEquals("folder-copy", edit.afterSelection)
        assertEquals("Art copy", copied.layers.first { it.id == "folder-copy" }.name)
        assertEquals("folder-copy", copied.layers.first { it.id == "nested-copy" }.parentId)
        assertEquals("nested-copy", copied.layers.first { it.id == "paint-copy" }.parentId)
        assertEquals("paint-copy", copied.elements.first { it.id == "image-copy" }.layerId)
        assertEquals(objectElement.assetPath, (copied.elements.first { it.id == "image-copy" } as PageElement.Image).assetPath)
        assertEquals(page, edit.apply(copied, true))
        assertEquals(copied, edit.apply(page, false))
    }

    @Test fun `delete group removes full subtree and one undo restores pixels and elements`() {
        val edit = LayerTreeEdit.create(page, LayerTreeAction.DELETE, folder.id)
        val deleted = edit.apply(page, false)!!
        assertTrue(deleted.layers.none { it.id in setOf("folder", "nested", "paint") })
        assertTrue(deleted.elements.isEmpty())
        assertEquals(page, edit.apply(deleted, true))
    }

    @Test fun `ungroup promotes immediate children and does not silently delete them`() {
        val edit = LayerTreeEdit.create(page, LayerTreeAction.UNGROUP, folder.id)
        val ungrouped = edit.apply(page, false)!!
        assertTrue(ungrouped.layers.none { it.id == folder.id })
        assertNull(ungrouped.layers.first { it.id == nested.id }.parentId)
        assertEquals(nested.id, ungrouped.layers.first { it.id == ink.id }.parentId)
        assertEquals(page, edit.apply(ungrouped, true))
    }

    @Test fun `group selection preserves live pixels in surviving layers during history`() {
        val bottom = InkLayer(id = "bottom", name = "Bottom")
        val top = InkLayer(id = "top", name = "Top")
        val original = InkPage(layers = defaultInkLayers() + bottom + top)
        val edit = LayerTreeEdit.create(original, LayerTreeAction.GROUP_SELECTED, top.id,
            setOf(bottom.id, top.id), { "new-folder" })
        val grouped = edit.apply(original, false)!!
        assertEquals("new-folder", grouped.layers.first { it.id == bottom.id }.parentId)
        assertEquals("new-folder", grouped.layers.first { it.id == top.id }.parentId)
        val later = grouped.copy(layers = grouped.layers.map {
            if (it.id == top.id) it.copy(name = "Renamed after grouping", opacity = .4f) else it
        })
        val restored = edit.apply(later, true)!!
        assertEquals("Renamed after grouping", restored.layers.first { it.id == top.id }.name)
        assertEquals(.4f, restored.layers.first { it.id == top.id }.opacity)
    }

    @Test fun `locked and effect bearing folders are rejected explicitly`() {
        try {
            LayerTreeEdit.create(page.copy(layers = page.layers.map { if (it.id == folder.id) it.copy(mask = LayerMask()) else it }),
                LayerTreeAction.UNGROUP, folder.id)
            fail("Group mask cannot disappear silently")
        } catch (error: LayerTreeRejected) { assertEquals(LayerTreeRejection.GROUP_EFFECTS, error.reason) }
        try {
            LayerTreeEdit.create(page.copy(layers = page.layers.map { if (it.id == folder.id) it.copy(locked = true) else it }),
                LayerTreeAction.DELETE, folder.id)
            fail("Locked group cannot be deleted")
        } catch (error: LayerTreeRejected) { assertEquals(LayerTreeRejection.LOCKED, error.reason) }
    }
}
