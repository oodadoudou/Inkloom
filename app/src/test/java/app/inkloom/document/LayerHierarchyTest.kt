package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerHierarchyTest {
    private val folder = InkLayer(id = "folder", name = "Sketches", role = LayerRole.FOLDER)
    private val child = InkLayer(id = "child", name = "Pencil", parentId = folder.id)

    @Test fun childInheritsFolderVisibilityAndLockWithoutLosingOwnState() {
        val hiddenFolder = folder.copy(visible = false, locked = true)
        val layers = listOf(hiddenFolder, child)

        assertFalse(LayerHierarchy.effectiveVisible(layers, child))
        assertTrue(LayerHierarchy.effectiveLocked(layers, child))
        assertTrue(child.visible)
        assertFalse(child.locked)
    }

    @Test fun referenceAndDraftFlagsInheritNestedFoldersWithoutMutatingChildren() {
        val marked = folder.copy(reference = true, draft = true)
        val nested = InkLayer(id = "nested", name = "Nested", role = LayerRole.FOLDER, parentId = folder.id)
        val leaf = child.copy(parentId = nested.id)
        val sibling = InkLayer(id = "sibling", name = "Sibling")
        val layers = listOf(marked, nested, leaf, sibling)
        assertTrue(LayerHierarchy.effectiveReference(layers, leaf))
        assertTrue(LayerHierarchy.effectiveDraft(layers, leaf))
        assertFalse(leaf.reference); assertFalse(leaf.draft)
        assertFalse(LayerHierarchy.effectiveDraft(layers, sibling))
        val unmarked = layers.map { if (it.id == folder.id) folder else it }
        assertFalse(LayerHierarchy.effectiveReference(unmarked, leaf))
        assertFalse(LayerHierarchy.effectiveDraft(unmarked, leaf))
        assertTrue(LayerHierarchy.effectiveDraft(unmarked, leaf.copy(draft = true)))
        // Cyclic or missing imported parents terminate without inventing flags.
        assertFalse(LayerHierarchy.effectiveDraft(listOf(nested.copy(parentId = nested.id)), nested))
    }

    @Test fun panelOrderNestsChildrenAndHonorsCollapsedFolders() {
        val other = InkLayer(id = "other", name = "Top")
        assertEquals(listOf("other", "folder", "child"), LayerHierarchy.panelOrder(listOf(folder, child, other)).map(InkLayer::id))
        assertEquals(listOf("other", "folder"), LayerHierarchy.panelOrder(listOf(folder.copy(collapsed = true), child, other)).map(InkLayer::id))
    }

    @Test fun removingFolderPromotesItsChildren() {
        val nested = InkLayer(id = "nested", name = "Nested", role = LayerRole.FOLDER, parentId = folder.id)
        val page = InkPage(layers = defaultInkLayers() + folder + child + nested)

        assertEquals(null, LayerHierarchy.removeFolder(page, folder.id).layers.first { it.id == child.id }.parentId)
    }
}
