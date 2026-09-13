package app.inkloom.document

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LayerGroupCompositingEditTest {
    private val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER)
    private val child = InkLayer(id = "child", name = "Child", parentId = folder.id)
    private val page = InkPage(layers = listOf(folder, child))

    @Test fun throughAndNormalChoicesHaveScopedUndoAndSerializeIndependentlyFromChildBlend() {
        val edit = LayerGroupCompositingEdit(folder.id, false, true)
        val through = requireNotNull(edit.apply(page))
        assertTrue(through.layers.first().passThrough)
        assertEquals(through, Json.decodeFromString<InkPage>(Json.encodeToString(through)))
        val normal = LayerBlendEdit(folder.id, LayerBlendMode.NORMAL, LayerBlendMode.NORMAL, beforePassThrough = true)
        assertFalse(normal.isNoOp)
        assertEquals(page, normal.apply(through))
        assertEquals(through, normal.apply(page, true))
        assertEquals(page, edit.apply(through, true))
        assertEquals(child, through.layers.last())
    }

    @Test fun throughCannotBeAClippingBaseEvenWhenHiddenAndAClippedChildCannotEscapeItsFolder() {
        val clipped = InkLayer(id = "clipped", name = "Clipped", clippedToBelow = true)
        for (visible in listOf(true, false)) {
            val layers = listOf(folder.copy(visible = visible), child, clipped)
            assertFalse(LayerGroupPolicy.canPassThrough(layers, folder.id))
            assertNull(LayerGroupCompositingEdit(folder.id, false, true).apply(page.copy(layers = layers)))
        }
        assertTrue(LayerGroupPolicy.canPassThrough(listOf(folder, child.copy(clippedToBelow = true)), folder.id))
        assertFalse(LayerGroupPolicy.canPassThrough(listOf(folder.copy(clippedToBelow = true), child), folder.id))
        assertFalse(LayerGroupPolicy.canPassThrough(page.layers, child.id))
    }

    @Test fun undoPreservesLaterChildPixelsAndDetectsStaleMode() {
        val edit = LayerGroupCompositingEdit(folder.id, false, true)
        assertNull(edit.apply(page, true))
        val paintedChild = child.copy(rasterRotationQuarterTurns = 2, mask = LayerMask())
        val through = requireNotNull(edit.apply(page.copy(layers = listOf(folder, paintedChild))))
        assertEquals(paintedChild, requireNotNull(edit.apply(through, true)).layers.last())
        assertNull(edit.apply(through))
    }
}
