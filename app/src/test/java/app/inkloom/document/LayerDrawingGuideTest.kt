package app.inkloom.document

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerDrawingGuideTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun eachLayerRetainsItsOwnGuideGeometry() {
        val first = InkLayer(name = "first", drawingAssist = true, drawingGuide = LayerDrawingGuide(
            symmetryMode = "VERTICAL", symmetryCenterX = .2f, rulerAngleDegrees = 15f,
        ))
        val second = InkLayer(name = "second", drawingAssist = true, drawingGuide = LayerDrawingGuide(
            symmetryMode = "RADIAL_6", symmetryCenterX = .8f, rulerAngleDegrees = -45f,
        ))

        val restored = json.decodeFromString<InkPage>(json.encodeToString(InkPage(layers = listOf(first, second))))

        assertEquals(.2f, restored.layers[0].drawingGuide?.symmetryCenterX)
        assertEquals(15f, restored.layers[0].drawingGuide?.rulerAngleDegrees)
        assertEquals("RADIAL_6", restored.layers[1].drawingGuide?.symmetryMode)
        assertEquals(-45f, restored.layers[1].drawingGuide?.rulerAngleDegrees)
    }

    @Test fun legacyLayerWithoutAttachedGeometryStillDecodes() {
        val restored = json.decodeFromString<InkLayer>("""{"name":"legacy","drawingAssist":true}""")

        assertEquals(true, restored.drawingAssist)
        assertNull(restored.drawingGuide)
    }
}
