package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerMaskAttachmentEditTest {
    @Test fun groupMaskCreateDisableDeleteUndoPreservesDescendantsAndOtherProperties() {
        val group = InkLayer(id = "group", name = "Group", role = LayerRole.FOLDER, passThrough = true)
        val child = InkLayer(id = "child", name = "Child", parentId = group.id)
        val original = InkPage(layers = listOf(group, child))
        val mask = LayerMask()
        val create = LayerMaskAttachmentEdit(group.id, null, mask)
        val created = requireNotNull(create.apply(original))
        val disable = LayerMaskAttachmentEdit(group.id, mask, mask.copy(enabled = false))
        val disabled = requireNotNull(disable.apply(created))
        val delete = LayerMaskAttachmentEdit(group.id, mask.copy(enabled = false), null)
        val removed = requireNotNull(delete.apply(disabled))
        val later = removed.copy(layers = removed.layers.map { if (it.id == child.id) it.copy(name = "Retained child", opacity = .3f) else it.copy(opacity = .5f) })
        val restored = requireNotNull(create.apply(requireNotNull(disable.apply(requireNotNull(delete.apply(later, true)), true)), true))
        assertNull(restored.layers.first().mask)
        assertEquals(.5f, restored.layers.first().opacity)
        assertTrue(restored.layers.first().passThrough)
        assertEquals("Retained child", restored.layers.last().name)
        assertEquals(.3f, restored.layers.last().opacity)
    }

    @Test fun staleMaskAndMissingOrPaperTargetsAreRejectedWithoutMutatingAnything() {
        val layer = InkLayer(id = "layer", name = "Layer", mask = LayerMask())
        val page = InkPage(layers = listOf(layer))
        assertNull(LayerMaskAttachmentEdit(layer.id, null, LayerMask()).apply(page))
        assertNull(LayerMaskAttachmentEdit("missing", null, LayerMask()).apply(page))
        assertNull(LayerMaskAttachmentEdit(layer.id, layer.mask, null).apply(page.copy(layers = listOf(layer.copy(role = LayerRole.BACKGROUND)))))
        assertTrue(LayerMaskAttachmentEdit(layer.id, layer.mask, layer.mask).isNoOp)
    }
}
