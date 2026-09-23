package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerOperationsTest {
    @Test fun duplicateDeepCopiesLayerContentAndPreservesBrushAppearance() {
        val source = InkLayer(id = "sketch", name = "Sketch", locked = true, reference = true, draft = true)
        val sticky = PageElement.Sticky(id = "sticky", x = .2f, y = .2f, layerId = source.id, text = "Draft")
        val page = InkPage(layers = defaultInkLayers() + source, elements = listOf(sticky))
        val ids = ArrayDeque(listOf("copy-layer", "copy-sticky"))

        val result = LayerOperations.duplicate(page, source.id, ids::removeFirst)!!

        assertEquals(listOf(source.id, "copy-layer"), result.page.layers.takeLast(2).map(InkLayer::id))
        val copiedLayer = result.page.layers.last()
        assertEquals("Sketch copy", copiedLayer.name)
        assertEquals(LayerRole.CUSTOM, copiedLayer.role)
        assertTrue(copiedLayer.locked)
        assertTrue(copiedLayer.reference)
        assertTrue(copiedLayer.draft)
        val copiedSticky = result.page.elements.last() as PageElement.Sticky
        assertEquals("copy-sticky", copiedSticky.id)
        assertEquals(copiedLayer.id, copiedSticky.layerId)
        assertNotEquals(sticky.id, copiedSticky.id)
    }

    @Test fun duplicateUsesAUniqueNameAndRejectsBackground() {
        val sketch = InkLayer(id = "sketch", name = "Sketch")
        val page = InkPage(layers = defaultInkLayers() + sketch + InkLayer(name = "sketch COPY") + InkLayer(name = "Sketch copy 2"))

        val result = LayerOperations.duplicate(page, sketch.id)!!

        assertEquals("Sketch copy 3", result.page.layers.first { it.id == result.activeLayerId }.name)
        assertNull(LayerOperations.duplicate(page, LayerIds.BACKGROUND))
    }

    @Test fun duplicateAdjustmentKeepsItsEditableType() {
        val correction = InkLayer(id = "correction", name = "Levels", role = LayerRole.ADJUSTMENT,
            adjustment = LayerAdjustment(kind = LayerAdjustmentKind.LEVELS, inputGamma = 1.4f))
        val page = InkPage(layers = defaultInkLayers() + correction)
        val copied = requireNotNull(LayerOperations.duplicate(page, correction.id) { "copy" })
        assertEquals(LayerRole.ADJUSTMENT, copied.page.layers.last().role)
        assertEquals(correction.adjustment, copied.page.layers.last().adjustment)
    }

}
