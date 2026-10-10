package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test fun aFolderClippedToAnOmittedDraftLeavesWithItsWholeSubtree() {
        val base = InkLayer(id = "base", name = "Draft", draft = true)
        val group = InkLayer(id = "group", name = "Shading", role = LayerRole.FOLDER, clippedToBelow = true)
        val inner = InkLayer(id = "inner", name = "Inner", role = LayerRole.FOLDER, parentId = group.id)
        val leaf = InkLayer(id = "leaf", name = "Leaf", parentId = inner.id)
        val adjust = InkLayer(id = "adjust", name = "Adjust", role = LayerRole.ADJUSTMENT, parentId = group.id, adjustment = LayerAdjustment())
        val chained = InkLayer(id = "chained", name = "Chained", clippedToBelow = true)
        val kept = InkLayer(id = "kept", name = "Kept")
        val keptClip = InkLayer(id = "kept-clip", name = "Kept clip", clippedToBelow = true)
        val layers = listOf(base, group, inner, leaf, adjust, chained, kept, keptClip)
        val elements = listOf(shape(leaf), shape(kept))
        // The flat store may interleave a folder's descendants with other layers; only sibling order matters.
        for (order in listOf(layers, listOf(leaf, inner, adjust, base, group, chained, kept, keptClip),
            listOf(base, leaf, group, inner, chained, adjust, kept, keptClip))) {
            val output = DraftLayerOutput.withoutDrafts(InkPage(layers = order, elements = elements))
            assertEquals("order ${order.map(InkLayer::id)}", setOf("kept", "kept-clip"), output.layers.mapTo(hashSetOf(), InkLayer::id))
            assertEquals(listOf("kept"), output.elements.map(PageElement::layerId))
            output.layers.forEach { layer -> assertTrue(layer.parentId == null || output.layers.any { it.id == layer.parentId }) }
        }
    }

    @Test fun omittingADraftInsideAFolderKeepsTheFolderAndUnrelatedClipChains() {
        val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER)
        val draft = InkLayer(id = "draft", name = "Draft", parentId = folder.id, draft = true)
        val clippedToDraft = InkLayer(id = "clip", name = "Clip", parentId = folder.id, clippedToBelow = true)
        val inkInFolder = InkLayer(id = "ink", name = "Ink", parentId = folder.id)
        val clippedToInk = InkLayer(id = "ink-clip", name = "Ink clip", parentId = folder.id, clippedToBelow = true)
        val output = DraftLayerOutput.withoutDrafts(InkPage(layers = listOf(folder, draft, clippedToDraft, inkInFolder, clippedToInk)))
        assertEquals(listOf("folder", "ink", "ink-clip"), output.layers.map(InkLayer::id))
    }
}
