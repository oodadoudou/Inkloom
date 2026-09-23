package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerEditingTargetTest {
    private val paper = InkLayer(id = "paper", name = "Paper", role = LayerRole.BACKGROUND)
    private val root = InkLayer(id = "root", name = "Root", role = LayerRole.FOLDER)
    private val child = InkLayer(id = "child", name = "Child", role = LayerRole.FOLDER, parentId = root.id)
    private val ink = InkLayer(id = LayerIds.INK, name = "Ink", parentId = child.id)
    private val other = InkLayer(id = "other", name = "Other")
    private val layers = listOf(paper, root, child, ink, other)

    @Test fun selectionRetainsLockedHiddenFolderAndPaperNodes() {
        for (selected in layers) {
            val changed = layers.map { it.copy(locked = true, visible = false) }
            assertEquals(selected.id, LayerEditingTarget.retainSelection(changed, selected.id))
        }
        assertEquals(ink.id, LayerEditingTarget.retainSelection(layers, "removed"))
        assertEquals(other.id, LayerEditingTarget.retainSelection(listOf(paper, root, other), "removed"))
        assertEquals("", LayerEditingTarget.retainSelection(emptyList(), ink.id))
    }

    @Test fun nestedProtectionNeverRedirectsToAnotherWritableLayer() {
        assertNull(LayerEditingTarget.rejection(layers, ink.id))
        for (ancestor in listOf(root, child)) {
            val locked = layers.map { if (it.id == ancestor.id) it.copy(locked = true) else it }
            assertEquals(LayerEditingTarget.Rejection.LOCKED, LayerEditingTarget.rejection(locked, ink.id))
            val hidden = layers.map { if (it.id == ancestor.id) it.copy(visible = false) else it }
            assertEquals(LayerEditingTarget.Rejection.HIDDEN, LayerEditingTarget.rejection(hidden, ink.id))
            assertNull(LayerEditingTarget.rejection(hidden, other.id))
        }
        assertEquals(LayerEditingTarget.Rejection.FOLDER, LayerEditingTarget.rejection(layers, root.id))
        assertEquals(LayerEditingTarget.Rejection.BACKGROUND, LayerEditingTarget.rejection(layers, paper.id))
        assertEquals(LayerEditingTarget.Rejection.MISSING, LayerEditingTarget.rejection(layers, "removed"))
    }

    @Test fun correctionIsSelectableAndMaskableButCannotReceivePaint() {
        val correction = InkLayer(id = "correction", name = "Correction", role = LayerRole.ADJUSTMENT,
            adjustment = LayerAdjustment())
        val withCorrection = listOf(paper, correction, other)
        assertEquals(correction.id, LayerEditingTarget.retainSelection(withCorrection, correction.id))
        assertEquals(other.id, LayerEditingTarget.retainSelection(withCorrection, "removed"))
        assertEquals(LayerEditingTarget.Rejection.ADJUSTMENT, LayerEditingTarget.rejection(withCorrection, correction.id))
        assertNull(LayerEditingTarget.maskRejection(withCorrection, correction.id))
    }
}
