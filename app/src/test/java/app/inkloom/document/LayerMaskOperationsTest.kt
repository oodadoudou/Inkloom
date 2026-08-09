package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerMaskOperationsTest {
    private val layer = InkLayer(id = "paint", name = "Paint")
    private val leftHalf = LassoGeometry.rectangle(StrokePoint(0f, 0f), StrokePoint(.5f, 1f))

    @Test fun `mask outside selection keeps only the selected region non destructively`() {
        val page = InkPage(layers = defaultInkLayers() + layer)

        val masked = LayerMaskOperations.execute(page, layer.id, LayerMaskCommand.CREATE_MASK_OUTSIDE_SELECTION, listOf(leftHalf))
        val mask = masked.layers.first { it.id == layer.id }.mask

        assertNotNull(mask)
        assertTrue(LayerMaskOperations.visible(mask, StrokePoint(.25f, .5f)))
        assertFalse(LayerMaskOperations.visible(mask, StrokePoint(.75f, .5f)))
    }

    @Test fun `hide reveal disable link and delete preserve an editable mask history`() {
        var page = InkPage(layers = defaultInkLayers() + layer)
        page = LayerMaskOperations.execute(page, layer.id, LayerMaskCommand.CREATE_MASK_SELECTION, listOf(leftHalf))
        assertFalse(LayerMaskOperations.visible(page.layers.last().mask, StrokePoint(.25f, .5f)))
        page = LayerMaskOperations.execute(page, layer.id, LayerMaskCommand.REVEAL_SELECTION, listOf(leftHalf))
        assertTrue(LayerMaskOperations.visible(page.layers.last().mask, StrokePoint(.25f, .5f)))
        page = LayerMaskOperations.execute(page, layer.id, LayerMaskCommand.TOGGLE_LINKED)
        assertFalse(requireNotNull(page.layers.last().mask).linked)
        page = LayerMaskOperations.execute(page, layer.id, LayerMaskCommand.TOGGLE_ENABLED)
        assertTrue(LayerMaskOperations.visible(page.layers.last().mask, StrokePoint(.75f, .5f)))
        page = LayerMaskOperations.execute(page, layer.id, LayerMaskCommand.DELETE)
        assertNull(page.layers.last().mask)
    }

    @Test fun `apply destructively clips crossing vector strokes then removes the mask`() {
        val crossing = InkStroke(
            id = "crossing",
            layerId = layer.id,
            points = listOf(StrokePoint(.1f, .5f), StrokePoint(.9f, .5f)),
        )
        var page = InkPage(layers = defaultInkLayers() + layer, strokes = listOf(crossing))
        page = LayerMaskOperations.execute(page, layer.id, LayerMaskCommand.CREATE_MASK_OUTSIDE_SELECTION, listOf(leftHalf))
        page = LayerMaskOperations.execute(page, layer.id, LayerMaskCommand.APPLY, nextId = { "split" })

        assertNull(page.layers.last().mask)
        assertEquals(1, page.strokes.size)
        assertTrue(page.strokes.single().points.all { it.x <= .5001f })
    }

    @Test fun `creating outside selection without a selection masks the whole layer`() {
        val page = LayerMaskOperations.execute(InkPage(layers = defaultInkLayers() + layer), layer.id, LayerMaskCommand.CREATE_MASK_OUTSIDE_SELECTION)
        assertFalse(LayerMaskOperations.visible(page.layers.last().mask, StrokePoint(.5f, .5f)))
    }
}
