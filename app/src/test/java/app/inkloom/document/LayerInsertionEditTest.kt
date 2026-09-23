package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerInsertionEditTest {
    private val paper = InkLayer(id = "paper", name = "Paper", role = LayerRole.BACKGROUND)
    private val lower = InkLayer(id = "lower", name = "Lower")
    private val folder = InkLayer(id = "folder", name = "Folder 1", role = LayerRole.FOLDER, collapsed = true)
    private val child = InkLayer(id = "child", name = "Layer 1", parentId = folder.id)
    private val upper = InkLayer(id = "upper", name = "Upper")
    private val page = InkPage(layers = listOf(paper, lower, folder, child, upper))

    @Test fun insertsAboveSelectedSiblingAndInsideFolderTop() {
        val edit = requireNotNull(LayerInsertionEdit.create(page, lower.id, false, "new"))
        val added = requireNotNull(edit.apply(page))
        assertEquals(listOf("paper", "lower", "new", "folder", "child", "upper"), added.layers.map { it.id })
        assertEquals("Layer 2", edit.layer.name)
        assertEquals(page, edit.apply(added, true))
        val nested = requireNotNull(LayerInsertionEdit.create(page, folder.id, false, "nested"))
        val nestedPage = requireNotNull(nested.apply(page))
        assertEquals(folder.id, nested.layer.parentId)
        assertFalse(nestedPage.layers.first { it.id == folder.id }.collapsed)
        assertEquals(listOf("upper", "folder", "nested", "child", "lower", "paper"), LayerHierarchy.panelOrder(nestedPage.layers).map { it.id })
        assertEquals(page, nested.apply(nestedPage, true))
        assertEquals(nestedPage, nested.apply(requireNotNull(nested.apply(nestedPage, true))))
    }

    @Test fun createsNestedFoldersAndRejectsMissingOrProtectedParents() {
        val edit = requireNotNull(LayerInsertionEdit.create(page, child.id, true, "subfolder"))
        assertEquals(folder.id, edit.layer.parentId)
        assertEquals("Folder 2", edit.layer.name)
        assertEquals(LayerRole.FOLDER, edit.layer.role)
        assertNull(LayerInsertionEdit.create(page, "removed", false))
        val locked = page.copy(layers = page.layers.map { if (it.id == folder.id) it.copy(locked = true) else it })
        assertNull(LayerInsertionEdit.create(locked, child.id, false))
        assertNull(edit.apply(locked))
        // A locked sibling is a valid insertion anchor; its pixels remain untouched.
        assertNotNull(LayerInsertionEdit.create(page.copy(layers = page.layers.map { it.copy(locked = it.id == lower.id) }), lower.id, false))
    }

    @Test fun undoPreservesUnrelatedEditsAndRefusesToDeleteChildren() {
        val edit = requireNotNull(LayerInsertionEdit.create(page, lower.id, true, "new"))
        val added = requireNotNull(edit.apply(page))
        val modified = added.copy(layers = added.layers.map { if (it.id == upper.id) it.copy(opacity = .4f) else it })
        val undone = requireNotNull(edit.apply(modified, true))
        assertEquals(.4f, undone.layers.first { it.id == upper.id }.opacity)
        assertNull(edit.apply(added.copy(layers = added.layers + child.copy(id = "newChild", parentId = "new")), true))
        assertNull(edit.apply(added))
    }

    @Test fun adjustmentCreationPreservesItsRoleParametersAndFolderPlacementAcrossUndo() {
        val correction = LayerAdjustment(kind = LayerAdjustmentKind.HUE_SATURATION, saturation = .35f)
        val edit = requireNotNull(LayerInsertionEdit.create(page, child.id, false, "correction", correction))
        assertEquals(LayerRole.ADJUSTMENT, edit.layer.role)
        assertEquals(correction, edit.layer.adjustment)
        assertEquals(folder.id, edit.layer.parentId)
        val added = requireNotNull(edit.apply(page))
        assertEquals(listOf("folder", "child", "correction", "upper"), added.layers.takeLast(4).map { it.id })
        assertEquals(page, edit.apply(added, true))
        assertEquals(added, edit.apply(requireNotNull(edit.apply(added, true))))
        assertNull(LayerInsertionEdit.create(page, child.id, true, "invalid", correction))
    }
}
