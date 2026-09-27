package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerColorReplacementEditTest {
    @Test fun enablingAndResettingColorPreservesSourceLayerAndUndo() {
        val page = InkPage(layers = listOf(InkLayer(id = "paint", name = "Paint")))
        val color = LayerColorReplacement(0xff2255ccL, 0xffffddaaL)
        val edit = LayerColorReplacementEdit("paint", null, color)
        val tinted = requireNotNull(edit.apply(page, false))
        assertEquals(color, tinted.layers.single().colorReplacement)
        assertEquals(page, edit.apply(tinted, true))
        val reset = LayerColorReplacementEdit("paint", color, null)
        val plain = requireNotNull(reset.apply(tinted, false))
        assertNull(plain.layers.single().colorReplacement)
        assertEquals(tinted, reset.apply(plain, true))
    }
}
