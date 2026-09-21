package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerPaletteColorEditTest {
    @Test fun `palette color applies to a folder and round trips through one undo`() {
        val folder = InkLayer(id = "folder", name = "Group", role = LayerRole.FOLDER)
        val page = InkPage(layers = defaultInkLayers() + folder)
        val edit = LayerPaletteColorEdit(folder.id, LayerPaletteColor.NONE, LayerPaletteColor.RED)

        val changed = edit.apply(page)!!
        assertEquals(LayerPaletteColor.RED, changed.layers.first { it.id == folder.id }.paletteColor)
        assertEquals(page, edit.apply(changed, undo = true))
    }

    @Test fun `palette color edit rejects stale layer state`() {
        val layer = InkLayer(id = "paint", name = "Paint", paletteColor = LayerPaletteColor.GREEN)
        val page = InkPage(layers = defaultInkLayers() + layer)
        val stale = LayerPaletteColorEdit(layer.id, LayerPaletteColor.NONE, LayerPaletteColor.RED)
        assertNull(stale.apply(page))
    }

    @Test fun `old documents keep a neutral palette color`() {
        assertEquals(LayerPaletteColor.NONE, InkLayer(name = "Legacy").paletteColor)
    }
}
