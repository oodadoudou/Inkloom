package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeGeometryTest {
    @Test
    fun nearHorizontalLineSnapsToHorizontal() {
        val result = ShapeGeometry.snapEnd(ShapePoint(.1f, .2f), ShapePoint(.6f, .215f), 1000f, 1000f)
        assertEquals(.2f, result.y, .0001f)
    }

    @Test
    fun lineOutsideToleranceKeepsOriginalAngle() {
        val end = ShapePoint(.6f, .32f)
        val result = ShapeGeometry.snapEnd(ShapePoint(.1f, .2f), end, 1000f, 1000f)
        assertEquals(end.x, result.x, .0001f)
        assertEquals(end.y, result.y, .0001f)
    }

    @Test
    fun lineCreatesRotatedHorizontalObject() {
        val shape = ShapeGeometry.create(ShapeKind.LINE, ShapePoint(.1f, .1f), ShapePoint(.4f, .4f), 1000f, 1000f, 0xFF000000, 5f)!!
        assertEquals(45f, shape.rotationDegrees, .001f)
        assertEquals(424.264f, shape.width, .01f)
        assertEquals(24f, shape.height, .001f)
    }

    @Test
    fun tinyGestureDoesNotCreateShape() {
        assertNull(ShapeGeometry.create(ShapeKind.RECTANGLE, ShapePoint(.1f, .1f), ShapePoint(.101f, .101f), 1000f, 1000f, 0, 5f))
    }

    @Test
    fun createsNormalizedRetainedFreeformPolygon() {
        val shape = ShapeGeometry.createFreeform(
            listOf(ShapePoint(.2f, .2f), ShapePoint(.8f, .25f), ShapePoint(.7f, .8f), ShapePoint(.25f, .7f), ShapePoint(.2f, .2f)),
            1_000f,
            2_000f,
            0xFF123456,
            9f,
        )!!

        assertEquals(ShapeKind.FREEFORM, shape.kind)
        assertEquals(.2f, shape.x, .0001f)
        assertEquals(.2f, shape.y, .0001f)
        assertEquals(600f, shape.width, .001f)
        assertEquals(1_200f, shape.height, .001f)
        assertTrue(shape.path.size >= 4)
        assertTrue(shape.path.all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(0xFF123456, shape.colorArgb)
        assertEquals(9f, shape.strokeWidth)
    }

    @Test
    fun rejectsFreeformLinesWithoutPolygonArea() {
        assertNull(ShapeGeometry.createFreeform(listOf(ShapePoint(.1f, .1f), ShapePoint(.5f, .5f), ShapePoint(.9f, .9f)), 1_000f, 1_000f, 0, 5f))
    }
}
