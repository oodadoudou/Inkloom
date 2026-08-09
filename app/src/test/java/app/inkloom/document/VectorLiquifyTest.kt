package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorLiquifyTest {
    private fun stroke(id: String, x: Float, y: Float, layerId: String = LayerIds.INK) = InkStroke(
        id = id,
        points = listOf(StrokePoint(x, y, pressure = 1f)),
        colorArgb = 0xFF111111,
        width = 20f,
        brushId = "brush",
        layerId = layerId,
    )

    @Test fun pushMovesOnlyGeometryInsideRadius() {
        val near = stroke("near", .5f, .5f)
        val far = stroke("far", .9f, .9f)
        val result = VectorLiquify.apply(
            listOf(near, far), .5f, .5f, .1f, 0f, .2f, 1f, 0f,
            pageWidth = 1000f, pageHeight = 1000f, mode = LiquifyMode.PUSH,
        )

        assertTrue(result[0].points.single().x > .5f)
        assertEquals(far, result[1])
    }

    @Test fun protectedStrokeCanBeExcluded() {
        val editable = stroke("editable", .5f, .5f)
        val protected = stroke("protected", .5f, .5f)
        val result = VectorLiquify.apply(
            listOf(editable, protected), .5f, .5f, .1f, 0f, .2f, 1f, 0f,
            pageWidth = 1000f, pageHeight = 1000f, mode = LiquifyMode.PUSH,
        ) { it.id != "protected" }

        assertNotEquals(editable, result[0])
        assertEquals(protected, result[1])
    }

    @Test fun pinchAndExpandMoveInOppositeDirections() {
        val original = stroke("stroke", .55f, .5f)
        fun apply(mode: LiquifyMode) = VectorLiquify.apply(
            listOf(original), .5f, .5f, 0f, 0f, .2f, 1f, 0f,
            pageWidth = 1000f, pageHeight = 1000f, mode = mode,
        ).single().points.single().x

        assertTrue(apply(LiquifyMode.PINCH) < original.points.single().x)
        assertTrue(apply(LiquifyMode.EXPAND) > original.points.single().x)
    }
}
