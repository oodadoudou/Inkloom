package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerSoloVisibilityEditTest {
    @Test fun soloRestoresOriginalHiddenStateAndUndoesAsOneEdit() {
        val bottom = InkLayer(id = "bottom", name = "Bottom", visible = false)
        val top = InkLayer(id = "top", name = "Top")
        val page = InkPage(layers = listOf(bottom, top))
        val solo = requireNotNull(LayerSoloVisibilityEdit.create(page, "bottom"))
        val isolated = requireNotNull(solo.apply(page, false))
        assertEquals(listOf(true, false), isolated.layers.map { it.visible })
        assertEquals("bottom", isolated.soloLayerId)
        assertEquals(page, solo.apply(isolated, true))

        val restore = requireNotNull(LayerSoloVisibilityEdit.create(isolated, "bottom"))
        val restored = requireNotNull(restore.apply(isolated, false))
        assertEquals(listOf(false, true), restored.layers.map { it.visible })
        assertNull(restored.soloLayerId)
        assertEquals(isolated, restore.apply(restored, true))
    }

    @Test fun soloFolderRetainsHiddenChildrenAndCanRestoreAfterReorder() {
        val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER)
        val shown = InkLayer(id = "shown", name = "Shown", parentId = folder.id)
        val hidden = InkLayer(id = "hidden", name = "Hidden", parentId = folder.id, visible = false)
        val other = InkLayer(id = "other", name = "Other")
        val page = InkPage(layers = listOf(folder, shown, hidden, other))
        val solo = requireNotNull(LayerSoloVisibilityEdit.create(page, folder.id))
        val isolated = requireNotNull(solo.apply(page, false))
        assertEquals(mapOf("folder" to true, "shown" to true, "hidden" to false, "other" to false),
            isolated.layers.associate { it.id to it.visible })
        val reordered = isolated.copy(layers = isolated.layers.reversed())
        val restore = requireNotNull(LayerSoloVisibilityEdit.create(reordered, folder.id))
        val result = requireNotNull(restore.apply(reordered, false))
        assertEquals(mapOf("folder" to true, "shown" to true, "hidden" to false, "other" to true),
            result.layers.associate { it.id to it.visible })
    }

    @Test fun newLayersOutsideSoloStayHiddenAndRestoreTheirOriginalVisibility() {
        val page = InkPage(layers = listOf(InkLayer(id = "a", name = "A"), InkLayer(id = "b", name = "B")))
        val solo = requireNotNull(LayerSoloVisibilityEdit.create(page, "a")).apply(page)!!
        val added = LayerSoloStructure.reconcile(solo,
            solo.copy(layers = solo.layers + InkLayer(id = "new", name = "New")))
        assertFalse(added.layers.first { it.id == "new" }.visible)
        assertEquals(true, added.soloVisibilityBefore["new"])
        val restored = requireNotNull(LayerSoloVisibilityEdit.create(added, "a")).apply(added)!!
        assertTrue(restored.layers.first { it.id == "new" }.visible)
    }

    @Test fun deletingSoloTargetRestoresSurvivorsAndClearsSoloState() {
        val page = InkPage(layers = listOf(
            InkLayer(id = "a", name = "A"), InkLayer(id = "b", name = "B", visible = false),
            InkLayer(id = "c", name = "C")))
        val solo = requireNotNull(LayerSoloVisibilityEdit.create(page, "a")).apply(page)!!
        val removed = LayerSoloStructure.reconcile(solo,
            solo.copy(layers = solo.layers.filterNot { it.id == "a" }))
        assertNull(removed.soloLayerId)
        assertTrue(removed.soloVisibilityBefore.isEmpty())
        assertEquals(mapOf("b" to false, "c" to true), removed.layers.associate { it.id to it.visible })
    }

    @Test fun movingLayerIntoSoloFolderUsesOriginalVisibility() {
        val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER)
        val child = InkLayer(id = "child", name = "Child")
        val page = InkPage(layers = listOf(folder, child))
        val solo = requireNotNull(LayerSoloVisibilityEdit.create(page, "folder")).apply(page)!!
        val moved = LayerSoloStructure.reconcile(solo, solo.copy(layers = solo.layers.map {
            if (it.id == "child") it.copy(parentId = "folder") else it
        }))
        assertTrue(moved.layers.first { it.id == "child" }.visible)
        val restored = requireNotNull(LayerSoloVisibilityEdit.create(moved, "folder")).apply(moved)!!
        assertTrue(restored.layers.first { it.id == "child" }.visible)
    }
}
