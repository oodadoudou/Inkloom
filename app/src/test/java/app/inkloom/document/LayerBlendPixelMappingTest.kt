package app.inkloom.document

import app.inkloom.engine.v5.composite.PixelBlendMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LayerBlendPixelMappingTest {
    @Test fun everyDocumentBlendModeMapsToTheSameNamedV3Semantic() {
        LayerBlendMode.entries.forEach { mode ->
            assertEquals(mode.name, mode.toPixelBlendMode().name)
        }
        // V5 may support engine-only modes before the document/UI schema exposes them.
        assertEquals(PixelBlendMode.LINEAR_BURN, PixelBlendMode.valueOf("LINEAR_BURN"))
    }
}
