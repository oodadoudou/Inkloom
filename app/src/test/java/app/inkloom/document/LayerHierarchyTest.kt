package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerHierarchyTest {
    private val folder = InkLayer(id = "folder", name = "Sketches", role = LayerRole.FOLDER)
    private val child = InkLayer(id = "child", name = "Pencil", parentId = folder.id)

    @Test fun reorderPreservesBackgroundAndParentAndSupportsSingleLongDistanceCommit() {
        val a = InkLayer(id = "a", name = "a")
        val b = InkLayer(id = "b", name = "b")
        val page = InkPage(layers = listOf(InkLayer(id = "paper", name = "paper", role = LayerRole.BACKGROUND), a, b, folder, child))
        val moved = LayerHierarchy.moveBy(page, a.id, 2)
        assertEquals(listOf("paper", "b", "folder", "a", "child"), moved.layers.map(InkLayer::id))
        assertEquals(page, LayerHierarchy.moveBy(page, "paper", 2))
        assertEquals(page, LayerHierarchy.moveBy(page, a.id, -1))
        assertEquals(page, LayerHierarchy.moveBy(page, child.id, -1))
        assertEquals(page, LayerHierarchy.moveBy(page, "missing", 1))
        assertEquals(page, LayerHierarchy.moveBy(moved, a.id, -2))
    }

    @Test fun childInheritsFolderVisibilityAndLockWithoutLosingOwnState() {
        val hiddenFolder = folder.copy(visible = false, locked = true)
        val layers = listOf(hiddenFolder, child)

        assertFalse(LayerHierarchy.effectiveVisible(layers, child))
        assertTrue(LayerHierarchy.effectiveLocked(layers, child))
        assertTrue(child.visible)
        assertFalse(child.locked)
    }

    @Test fun panelOrderNestsChildrenAndHonorsCollapsedFolders() {
        val other = InkLayer(id = "other", name = "Top")
        assertEquals(listOf("other", "folder", "child"), LayerHierarchy.panelOrder(listOf(folder, child, other)).map(InkLayer::id))
        assertEquals(listOf("other", "folder"), LayerHierarchy.panelOrder(listOf(folder.copy(collapsed = true), child, other)).map(InkLayer::id))
    }

    @Test fun movingFoldersRejectsCyclesAndDeletingFolderReparentsChildren() {
        val nested = InkLayer(id = "nested", name = "Nested", role = LayerRole.FOLDER, parentId = folder.id)
        val page = InkPage(layers = defaultInkLayers() + folder + child + nested)

        assertEquals(page, LayerHierarchy.moveToFolder(page, folder.id, nested.id))
        assertEquals(folder.id, LayerHierarchy.moveToFolder(page, child.id, folder.id).layers.first { it.id == child.id }.parentId)
        assertEquals(null, LayerHierarchy.removeFolder(page, folder.id).layers.first { it.id == child.id }.parentId)
    }
}
