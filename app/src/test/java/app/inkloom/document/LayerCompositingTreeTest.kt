package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerCompositingTreeTest {
    @Test fun siblingsFollowStoredOrderButChildrenRemainInsideTheirParentScope() {
        val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER, collapsed = true)
        val child = InkLayer(id = "child", name = "Child", parentId = folder.id)
        val top = InkLayer(id = "top", name = "Top")
        val nested = InkLayer(id = "nested", name = "Nested", role = LayerRole.FOLDER, parentId = folder.id)
        val inside = InkLayer(id = "inside", name = "Inside", parentId = nested.id)
        val roots = LayerCompositingTree.roots(listOf(folder, top, child, nested, inside))
        assertEquals(listOf("folder", "top"), roots.map { it.layer.id })
        assertEquals(listOf("child", "nested"), roots.first().children.map { it.layer.id })
        assertEquals("inside", roots.first().children.last().children.single().layer.id)
    }

    @Test fun visibilityRemainsInTheTreeSoHiddenNodesCanStillDefineClippingBases() {
        val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER, collapsed = true)
        val child = InkLayer(id = "child", name = "Child", parentId = folder.id)
        assertEquals(1, LayerCompositingTree.roots(listOf(folder, child)).single().children.size)
        val hidden = LayerCompositingTree.roots(listOf(folder.copy(visible = false), child)).single()
        assertFalse(hidden.layer.visible)
        assertEquals(child.id, hidden.children.single().layer.id)
    }

    @Test fun groupPropertyUndoPreservesChildrenAndTheirLatestPixels() {
        val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER)
        val child = InkLayer(id = "child", name = "Child", parentId = folder.id)
        val page = InkPage(layers = listOf(folder, child))
        val opacity = LayerOpacityEdit(folder.id, 1f, .5f)
        val blend = LayerBlendEdit(folder.id, LayerBlendMode.NORMAL, LayerBlendMode.MULTIPLY)
        val changed = requireNotNull(blend.apply(requireNotNull(opacity.apply(page))))
        assertEquals(child, changed.layers.last())
        assertEquals(page, opacity.apply(requireNotNull(blend.apply(changed, true)), true))
    }
}
