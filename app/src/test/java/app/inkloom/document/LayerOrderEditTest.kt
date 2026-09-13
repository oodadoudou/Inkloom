package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerOrderEditTest {
    private val paper = InkLayer(id = "paper", name = "Paper", role = LayerRole.BACKGROUND)
    private val lower = InkLayer(id = "lower", name = "Lower")
    private val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER, collapsed = true)
    private val child = InkLayer(id = "child", name = "Child", parentId = folder.id)
    private val upper = InkLayer(id = "upper", name = "Upper")
    private val page = InkPage(layers = listOf(paper, lower, folder, child, upper))
    private fun order(page: InkPage) = LayerHierarchy.panelOrder(page.layers.map { it.copy(collapsed = false) }).map(InkLayer::id)
    private fun move(ids: Set<String>, target: String, position: LayerDropPosition, source: InkPage = page): Pair<LayerOrderEdit, InkPage> {
        val edit = LayerOrderEdit.create(source, ids.first(), ids, LayerDrop(target, position))
        return edit to requireNotNull(edit.apply(source))
    }

    @Test fun multipleRootsFromDifferentParentsKeepVisualOrderInsideDestination() {
        val (edit, after) = move(linkedSetOf("upper", "child", "lower"), "folder", LayerDropPosition.INSIDE)
        assertEquals(listOf("folder", "upper", "child", "lower", "paper"), order(after))
        assertTrue(after.layers.filter { it.id in setOf("upper", "child", "lower") }.all { it.parentId == "folder" })
        assertEquals(page, edit.apply(after, true))
        assertEquals(after, edit.apply(requireNotNull(edit.apply(after, true))))
    }

    @Test fun selectedAncestorCarriesDescendantsExactlyOnceIncludingCollapsedChildren() {
        val nested = folder.copy(id = "nested", parentId = "folder")
        val leaf = child.copy(id = "leaf", parentId = "nested")
        val source = page.copy(layers = page.layers + nested + leaf)
        val (edit, after) = move(linkedSetOf("folder", "child", "nested", "leaf"), "upper", LayerDropPosition.ABOVE, source)
        assertEquals(listOf("folder", "nested", "leaf", "child", "upper", "lower", "paper"), order(after))
        assertEquals(source.layers.size, after.layers.map(InkLayer::id).distinct().size)
        assertEquals("nested", after.layers.single { it.id == "leaf" }.parentId)
        assertEquals(source, edit.apply(after, true))
    }

    @Test fun moveOutPromotesOneLevelAndEdgesKeepSiblingOrder() {
        val (_, after) = move(setOf("child"), "folder", LayerDropPosition.ABOVE)
        assertNull(after.layers.single { it.id == "child" }.parentId)
        assertEquals(listOf("upper", "child", "folder", "lower", "paper"), order(after))
        val (_, below) = move(setOf("upper"), "lower", LayerDropPosition.BELOW)
        assertEquals(listOf("folder", "child", "lower", "upper", "paper"), order(below))
    }

    @Test fun historyPreservesUnrelatedPropertiesMasksAndRejectsStaleStructure() {
        val (edit, after) = move(setOf("upper"), "lower", LayerDropPosition.BELOW)
        val modified = after.copy(layers = after.layers.map { if (it.id == "upper") it.copy(opacity = .35f, mask = LayerMask(), name = "Renamed") else it })
        val undone = requireNotNull(edit.apply(modified, true))
        assertEquals(.35f, undone.layers.single { it.id == "upper" }.opacity)
        assertEquals("Renamed", undone.layers.single { it.id == "upper" }.name)
        assertNotNull(undone.layers.single { it.id == "upper" }.mask)
        assertEquals(page.layers.map { it.id to it.parentId }, undone.layers.map { it.id to it.parentId })
        assertNull(edit.apply(after.copy(layers = after.layers + lower.copy(id = "new")), true))
    }

    @Test fun noOpAdjacentDropDoesNotNeedAHistoryEntry() {
        val source = InkPage(layers = listOf(paper, lower, upper))
        assertTrue(move(setOf("upper"), "lower", LayerDropPosition.ABOVE, source).first.isNoOp)
    }

    @Test fun protectedLockedMissingAndCyclicDestinationsAreExplicitlyRejected() {
        fun rejected(reason: LayerMoveRejection, ids: Set<String>, target: String, position: LayerDropPosition, source: InkPage = page) {
            try { move(ids, target, position, source); fail("Expected $reason") }
            catch (failure: LayerMoveRejected) { assertEquals(reason, failure.reason) }
        }
        rejected(LayerMoveRejection.PAPER, setOf("paper"), "upper", LayerDropPosition.ABOVE)
        rejected(LayerMoveRejection.PAPER, setOf("upper"), "paper", LayerDropPosition.BELOW)
        rejected(LayerMoveRejection.MISSING_TARGET, setOf("absent"), "upper", LayerDropPosition.ABOVE)
        rejected(LayerMoveRejection.CYCLE, setOf("folder"), "child", LayerDropPosition.ABOVE)
        rejected(LayerMoveRejection.CYCLE, setOf("folder"), "folder", LayerDropPosition.INSIDE)
        val locked = page.copy(layers = page.layers.map { if (it.id == "folder") it.copy(locked = true) else it })
        rejected(LayerMoveRejection.LOCKED, setOf("upper"), "folder", LayerDropPosition.INSIDE, locked)
        rejected(LayerMoveRejection.LOCKED, setOf("child"), "upper", LayerDropPosition.BELOW, locked)
    }

    @Test fun clippingCannotBeMovedOntoThroughFolder() {
        val source = page.copy(layers = page.layers.map { when (it.id) {
            "folder" -> it.copy(passThrough = true)
            "upper" -> it.copy(clippedToBelow = true, parentId = "folder")
            else -> it
        } })
        try { move(setOf("upper"), "folder", LayerDropPosition.ABOVE, source); fail("Through cannot be a clipping base") }
        catch (failure: LayerMoveRejected) { assertEquals(LayerMoveRejection.THROUGH_CLIPPING, failure.reason) }
    }
}
