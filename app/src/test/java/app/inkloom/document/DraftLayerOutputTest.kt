package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DraftLayerOutputTest {
    private val paper = InkLayer(id = "paper", name = "Paper", role = LayerRole.BACKGROUND)
    private val folder = InkLayer(id = "folder", name = "Sketches", role = LayerRole.FOLDER, draft = true)
    private val nested = InkLayer(id = "nested", name = "Rough", parentId = folder.id)
    private val base = InkLayer(id = "base", name = "Base", draft = true)
    private val clipped = InkLayer(id = "clipped", name = "Shade", clippedToBelow = true)
    private val final = InkLayer(id = "final", name = "Final")
    private val keptClip = InkLayer(id = "kept-clip", name = "Highlight", clippedToBelow = true)
    private fun shape(layer: InkLayer) = PageElement.Shape(x = 0f, y = 0f, width = 1f, height = 1f, layerId = layer.id)
    private val page = InkPage(layers = listOf(paper, folder, nested, base, clipped, final, keptClip),
        elements = listOf(shape(nested), shape(clipped), shape(final)))

    @Test fun exportsLeaveOutDraftsDraftFoldersAndLayersClippedToThem() {
        val output = DraftLayerOutput.withoutDrafts(page)
        assertEquals(listOf("paper", "final", "kept-clip"), output.layers.map(InkLayer::id))
        assertEquals(listOf("final"), output.elements.map(PageElement::layerId))
        assertSame(final, output.layers.single { it.id == final.id })
        val clean = page.copy(layers = page.layers.map { it.copy(draft = false) })
        assertSame(clean, DraftLayerOutput.withoutDrafts(clean))
    }

    @Test fun includedDraftsBecomeOrdinaryLayersWithTheirContent() {
        val output = DraftLayerOutput.asOrdinaryLayers(page)
        assertEquals(page.layers.map(InkLayer::id), output.layers.map(InkLayer::id))
        assertEquals(false, output.layers.any(InkLayer::draft))
        assertEquals(page.elements, output.elements)
    }
}
