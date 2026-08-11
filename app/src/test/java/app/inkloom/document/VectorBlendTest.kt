package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorBlendTest {
    private fun stroke(id: String, y: Float, color: Long) = InkStroke(
        id = id, colorArgb = color, width = 30f,
        points = listOf(StrokePoint(.45f, y), StrokePoint(.55f, y)),
        layerId = LayerIds.INK,
    )

    @Test fun blendMixesExistingPigmentWithoutAddingStrokes() {
        val red = stroke("red", .49f, 0xFFFF0000)
        val blue = stroke("blue", .51f, 0xFF0000FF)
        val result = VectorBlend.apply(listOf(red, blue), .5f, .5f, .03f, 0f, .2f, 1f, 0f, 1000f, 1000f)

        assertEquals(2, result.size)
        assertNotEquals(red.colorArgb, result[0].points.last().pigmentArgb)
        assertNotEquals(blue.colorArgb, result[1].points.first().pigmentArgb)
        assertTrue(result[0].points.first().x > red.points.first().x)
    }

    @Test fun blankAreaDoesNotCreatePigment() {
        assertTrue(VectorBlend.apply(emptyList(), .5f, .5f, .1f, 0f, .2f, 1f, 0f, 1000f, 1000f).isEmpty())
    }

    @Test fun lockedStrokeCanBeExcluded() {
        val locked = stroke("locked", .5f, 0xFFFF0000)
        val result = VectorBlend.apply(listOf(locked), .5f, .5f, .1f, 0f, .2f, 1f, 0f, 1000f, 1000f) { false }
        assertEquals(locked, result.single())
    }

    @Test fun longStrokeKeepsPointsOutsideTheBrushRadiusUntouched() {
        val points = List(10_001) { index -> StrokePoint(index / 10_000f, .5f) }
        val long = InkStroke(id = "long", colorArgb = 0xFFFF0000, width = 20f, points = points)

        val result = VectorBlend.apply(
            listOf(long),
            centerX = .5f,
            centerY = .5f,
            deltaX = .01f,
            deltaY = 0f,
            radius = .01f,
            strength = 1f,
            hardness = 0f,
            pageWidth = 1000f,
            pageHeight = 1000f,
        ).single()

        assertEquals("Distant prefix must retain object identity and no pigment allocation", points.first(), result.points.first())
        assertEquals("Distant suffix must retain object identity and no pigment allocation", points.last(), result.points.last())
        assertTrue(result.points.subList(4_900, 5_100).any { it.pigmentArgb != null })
        assertTrue(result.points.take(4_000).all { it.pigmentArgb == null })
        assertTrue(result.points.takeLast(4_000).all { it.pigmentArgb == null })
    }
}
