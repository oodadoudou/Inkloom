package app.inkloom.engine.v3.brush

import app.inkloom.document.GraphiteFootprint
import app.inkloom.document.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MaterialCoordinateContextTest {
    private val pencil = InkStroke(
        id = "pencil",
        points = emptyList(),
        proceduralPaperGrainEnabled = true,
        proceduralPaperGrainScale = 42f,
        proceduralPaperGrainDensity = .9f,
        proceduralPaperGrainStrength = .8f,
    )

    @Test fun documentPaperSampleDoesNotContainViewportState() {
        val documentPoint = MaterialCoordinateContext.document(523.25f, 317.75f)
        val beforeViewportChange = GraphiteFootprint.paperGrainAlpha(pencil, documentPoint)
        // Pan/zoom/rotation deliberately have no input into DOCUMENT material coordinates.
        val afterViewportChange = GraphiteFootprint.paperGrainAlpha(pencil, documentPoint)
        assertEquals(beforeViewportChange, afterViewportChange, 0f)
        assertNotEquals(
            beforeViewportChange,
            GraphiteFootprint.paperGrainAlpha(pencil, MaterialCoordinateContext.document(613.25f, 407.75f)),
        )
    }

    @Test fun coordinateSpacesAreExplicitlyDifferentTypes() {
        assertEquals(MaterialCoordinateSpace.DOCUMENT, MaterialCoordinateContext.document(1f, 2f).space)
        assertEquals(MaterialCoordinateSpace.BRUSH_LOCAL, MaterialCoordinateContext.brushLocal(1f, 2f).space)
        assertEquals(MaterialCoordinateSpace.STROKE, MaterialCoordinateContext.stroke(1f, 2f).space)
    }
}
