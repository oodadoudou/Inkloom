package app.inkloom.document

import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPaintLayerV5Test {
    @Test fun newNotesLayerUsesTheOnlyV5PaintLayerContract() {
        val notes = defaultInkLayers().single { it.id == LayerIds.INK }
        assertTrue(notes.isPaintSurfaceLayer())
    }

    @Test fun structuralLayersCannotBecomePaintSurfaces() {
        assertTrue(!InkLayer(id = "background", name = "Background", role = LayerRole.BACKGROUND).isPaintSurfaceLayer())
        assertTrue(!InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER).isPaintSurfaceLayer())
    }
}
