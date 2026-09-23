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

    @Test fun `ungroup removes folder effects and one undo restores the exact folder`() {
        val effectedFolder = folder.copy(mask = LayerMask(), opacity = .43f,
            blendMode = LayerBlendMode.MULTIPLY, passThrough = true)
        val effectedPage = page.copy(layers = page.layers.map { if (it.id == folder.id) effectedFolder else it })
        val edit = LayerTreeEdit.create(effectedPage, LayerTreeAction.UNGROUP, folder.id)
        val ungrouped = edit.apply(effectedPage, false)!!
        assertTrue(ungrouped.layers.none { it.id == folder.id })
        assertNull(ungrouped.layers.first { it.id == nested.id }.parentId)
        assertEquals(effectedPage, edit.apply(ungrouped, true))
    }

    @Test fun `locked folders are rejected explicitly`() {
        try {
            LayerTreeEdit.create(page.copy(layers = page.layers.map { if (it.id == folder.id) it.copy(locked = true) else it }),
                LayerTreeAction.DELETE, folder.id)
            fail("Locked group cannot be deleted")
        } catch (error: LayerTreeRejected) { assertEquals(LayerTreeRejection.LOCKED, error.reason) }
    }

    @Test fun `adjustment within a group survives duplicate delete and one-step undo`() {
        val adjustment = InkLayer(id = "adjustment", name = "Hue", role = LayerRole.ADJUSTMENT,
            parentId = nested.id, adjustment = LayerAdjustment(kind = LayerAdjustmentKind.HUE_SATURATION,
                hueDegrees = 20f))
        val source = page.copy(layers = page.layers + adjustment)
        val ids = ArrayDeque(listOf("folder-copy", "nested-copy", "paint-copy", "adjustment-copy", "image-copy"))
        val duplicate = LayerTreeEdit.create(source, LayerTreeAction.DUPLICATE, folder.id, nextId = ids::removeFirst)
        val copied = requireNotNull(duplicate.apply(source, false))
        val copiedAdjustment = copied.layers.first { it.id == "adjustment-copy" }
        assertEquals(LayerRole.ADJUSTMENT, copiedAdjustment.role)
        assertEquals(adjustment.adjustment, copiedAdjustment.adjustment)
        assertEquals("nested-copy", copiedAdjustment.parentId)
        assertEquals(source, duplicate.apply(copied, true))

        val delete = LayerTreeEdit.create(source, LayerTreeAction.DELETE, folder.id)
        val removed = requireNotNull(delete.apply(source, false))
        assertTrue(removed.layers.none { it.id == adjustment.id })
        assertEquals(source, delete.apply(removed, true))
    }
}
