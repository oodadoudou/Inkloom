package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class LayerMaskTransferEditTest {
    private val mask = LayerMask(baseVisible = false, enabled = true, linked = false)
    private val source = InkLayer(id = "source", name = "Source", mask = mask)
    private val target = InkLayer(id = "target", name = "Target")
    private val page = InkPage(layers = defaultInkLayers() + source + target)

    @Test fun moveAndCopyAreExactSingleStepTransactions() {
        val move = LayerMaskTransferEdit(source.id, target.id, mask, null, copy = false)
        val moved = requireNotNull(move.apply(page, false))
        assertNull(moved.layers.first { it.id == source.id }.mask)
        assertEquals(mask, moved.layers.first { it.id == target.id }.mask)
        assertEquals(page, move.apply(moved, true))
        assertEquals(moved, move.apply(requireNotNull(move.apply(moved, true)), false))

        val copy = LayerMaskTransferEdit(source.id, target.id, mask, null, copy = true)
        val copied = requireNotNull(copy.apply(page, false))
        assertEquals(mask, copied.layers.first { it.id == source.id }.mask)
        assertEquals(mask, copied.layers.first { it.id == target.id }.mask)
        assertEquals(page, copy.apply(copied, true))
    }

    @Test fun staleOrOccupiedTargetsAreRejected() {
        val edit = LayerMaskTransferEdit(source.id, target.id, mask, null, copy = false)
        assertNull(edit.apply(page.copy(layers = page.layers.map {
            if (it.id == target.id) it.copy(mask = LayerMask()) else it
        }), false))
        assertNull(edit.apply(page.copy(layers = page.layers.filterNot { it.id == target.id }), false))
    }
}
