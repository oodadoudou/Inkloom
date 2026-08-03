package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerShortcutSelectionTest {
    @Test fun `moves through editable layers while skipping protected stack entries`() {
        val layers = listOf(
            InkLayer(id = "background", name = "Background", role = LayerRole.BACKGROUND),
            InkLayer(id = "ink", name = "Ink", role = LayerRole.INK),
            InkLayer(id = "locked", name = "Locked", role = LayerRole.CUSTOM, locked = true),
            InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER),
            InkLayer(id = "paint", name = "Paint", role = LayerRole.CUSTOM),
        )

        assertEquals("paint", LayerShortcutSelection.adjacentEditable(layers, "ink", 1))
        assertEquals("ink", LayerShortcutSelection.adjacentEditable(layers, "paint", -1))
        assertNull(LayerShortcutSelection.adjacentEditable(layers, "paint", 1))
    }

    @Test fun `skips children of hidden or locked folders`() {
        val hiddenFolder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER, visible = false)
        val child = InkLayer(id = "child", name = "Child", role = LayerRole.CUSTOM, parentId = hiddenFolder.id)
        val layers = listOf(InkLayer(id = "ink", name = "Ink", role = LayerRole.INK), hiddenFolder, child)

        assertNull(LayerShortcutSelection.adjacentEditable(layers, "ink", 1))
    }
}
