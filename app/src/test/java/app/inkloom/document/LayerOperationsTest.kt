package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerOperationsTest {
    @Test fun duplicateDeepCopiesLayerContentAndPreservesBrushAppearance() {
        val source = InkLayer(id = "sketch", name = "Sketch", locked = true)
        val stroke = InkStroke(id = "stroke", renderSeed = 912, layerId = source.id, points = listOf(StrokePoint(.2f, .3f)), masks = listOf(StrokeMaskDab(.2f, .3f, .01f, .02f)))
        val stickyStroke = InkStroke(id = "sticky-stroke", renderSeed = 411, points = listOf(StrokePoint(.4f, .5f)))
        val sticky = PageElement.Sticky(id = "sticky", x = .2f, y = .2f, layerId = source.id, text = "Draft", strokes = listOf(stickyStroke))
        val page = InkPage(layers = defaultInkLayers() + source, strokes = listOf(stroke), elements = listOf(sticky))
        val ids = ArrayDeque(listOf("copy-layer", "copy-stroke", "copy-sticky", "copy-sticky-stroke"))

        val result = LayerOperations.duplicate(page, source.id, ids::removeFirst)!!

        assertEquals(listOf(source.id, "copy-layer"), result.page.layers.takeLast(2).map(InkLayer::id))
        val copiedLayer = result.page.layers.last()
        assertEquals("Sketch copy", copiedLayer.name)
        assertEquals(LayerRole.CUSTOM, copiedLayer.role)
        assertTrue(copiedLayer.locked)
        val copiedStroke = result.page.strokes.last()
        assertEquals("copy-stroke", copiedStroke.id)
        assertEquals(copiedLayer.id, copiedStroke.layerId)
        assertEquals(stroke.renderSeed, copiedStroke.renderSeed)
        assertEquals(stroke.masks, copiedStroke.masks)
        val copiedSticky = result.page.elements.last() as PageElement.Sticky
        assertEquals("copy-sticky", copiedSticky.id)
        assertEquals(copiedLayer.id, copiedSticky.layerId)
        assertEquals("copy-sticky-stroke", copiedSticky.strokes.single().id)
        assertEquals(stickyStroke.renderSeed, copiedSticky.strokes.single().renderSeed)
        assertNotEquals(sticky.id, copiedSticky.id)
    }

    @Test fun duplicateUsesAUniqueNameAndRejectsBackground() {
        val sketch = InkLayer(id = "sketch", name = "Sketch")
        val page = InkPage(layers = defaultInkLayers() + sketch + InkLayer(name = "sketch COPY") + InkLayer(name = "Sketch copy 2"))

        val result = LayerOperations.duplicate(page, sketch.id)!!

        assertEquals("Sketch copy 3", result.page.layers.first { it.id == result.activeLayerId }.name)
        assertNull(LayerOperations.duplicate(page, LayerIds.BACKGROUND))
    }

    @Test fun mergeDownPreservesTargetThenSourceDrawingOrder() {
        val lower = InkLayer(id = "lower", name = "Line art")
        val upper = InkLayer(id = "upper", name = "Shading")
        val unrelated = InkStroke(id = "notes", layerId = LayerIds.INK, points = listOf(StrokePoint(.1f, .1f)))
        val upperStroke = InkStroke(id = "upper-stroke", layerId = upper.id, points = listOf(StrokePoint(.2f, .2f)))
        val lowerStroke = InkStroke(id = "lower-stroke", layerId = lower.id, points = listOf(StrokePoint(.3f, .3f)))
        val upperText = PageElement.Text(id = "upper-text", x = .2f, y = .2f, layerId = upper.id, text = "Upper")
        val lowerText = PageElement.Text(id = "lower-text", x = .3f, y = .3f, layerId = lower.id, text = "Lower")
        val page = InkPage(
            layers = defaultInkLayers() + lower + upper,
            strokes = listOf(upperStroke, unrelated, lowerStroke),
            elements = listOf(upperText, lowerText),
        )

        val result = LayerOperations.mergeDown(page, upper.id)!!

        assertEquals(lower.id, result.activeLayerId)
        assertFalse(result.page.layers.any { it.id == upper.id })
        assertEquals(listOf("lower-stroke", "upper-stroke"), result.page.strokes.filter { it.layerId == lower.id }.map(InkStroke::id))
        assertEquals(listOf("lower-text", "upper-text"), result.page.elements.filter { it.layerId == lower.id }.map(PageElement::id))
        assertTrue(result.page.strokes.any { it.id == unrelated.id && it.layerId == LayerIds.INK })
    }

    @Test fun mergeDownRejectsLockedMismatchedVisibilityAndBackgroundTargets() {
        val lower = InkLayer(id = "lower", name = "Lower")
        val upper = InkLayer(id = "upper", name = "Upper")
        val page = InkPage(layers = defaultInkLayers() + lower + upper)

        assertTrue(LayerOperations.canMergeDown(page, upper.id))
        assertFalse(LayerOperations.canMergeDown(page.copy(layers = page.layers.map { if (it.id == lower.id) it.copy(locked = true) else it }), upper.id))
        assertFalse(LayerOperations.canMergeDown(page.copy(layers = page.layers.map { if (it.id == upper.id) it.copy(visible = false) else it }), upper.id))
        assertFalse(LayerOperations.canMergeDown(InkPage(), LayerIds.INK))
        assertNull(LayerOperations.mergeDown(page.copy(layers = page.layers.map { if (it.id == upper.id) it.copy(locked = true) else it }), upper.id))
    }

    @Test fun mergeDownBakesBothLayerOpacitiesIntoTheirContent() {
        val lower = InkLayer(id = "lower", name = "Lower", opacity = .5f)
        val upper = InkLayer(id = "upper", name = "Upper", opacity = .25f)
        val page = InkPage(
            layers = defaultInkLayers() + lower + upper,
            strokes = listOf(
                InkStroke(id = "lower-stroke", layerId = lower.id, opacity = .8f, points = listOf(StrokePoint(.1f, .1f))),
                InkStroke(id = "upper-stroke", layerId = upper.id, opacity = .6f, points = listOf(StrokePoint(.2f, .2f))),
            ),
            elements = listOf(PageElement.Text(id = "upper-text", x = .2f, y = .2f, layerId = upper.id, opacity = .4f, text = "Upper")),
        )

        val merged = LayerOperations.mergeDown(page, upper.id)!!.page

        assertEquals(1f, merged.layers.first { it.id == lower.id }.opacity)
        assertEquals(.4f, merged.strokes.first { it.id == "lower-stroke" }.opacity)
        assertEquals(.15f, merged.strokes.first { it.id == "upper-stroke" }.opacity)
        assertEquals(.1f, merged.elements.first { it.id == "upper-text" }.opacity)
    }
}
