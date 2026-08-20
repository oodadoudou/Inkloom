package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultLayerV3Test {
    @Test fun newNotesLayerUsesAuthoritativeRasterStorage() {
        val notes = defaultInkLayers().single { it.id == LayerIds.INK }
        assertEquals(LayerContentKind.RASTER, notes.contentKind)
    }

    @Test fun legacyVectorLayerRemainsAnExplicitCompatibilityValue() {
        val legacy = InkLayer(id = "legacy", name = "Legacy", contentKind = LayerContentKind.VECTOR)
        assertEquals(LayerContentKind.VECTOR, legacy.contentKind)
    }
}
