package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StrokePathJitterTest {
    @Test fun lateralAndLongitudinalJitterUseIndependentPathLocalAxes() {
        val lateralX = StrokePathJitter.x(3, 7, 0f, 100f, .5f, 0f)
        val lateralY = StrokePathJitter.y(3, 7, 0f, 100f, .5f, 0f)
        val longitudinalX = StrokePathJitter.x(3, 7, 0f, 100f, 0f, .5f)
        val longitudinalY = StrokePathJitter.y(3, 7, 0f, 100f, 0f, .5f)

        assertEquals(0f, lateralX, .0001f)
        assertNotEquals(0f, lateralY, .0001f)
        assertNotEquals(0f, longitudinalX, .0001f)
        assertEquals(0f, longitudinalY, .0001f)
    }

    @Test fun jitterIsDeterministicAndRotatesWithTheStroke() {
        val horizontal = StrokePathJitter.y(11, 13, 0f, 80f, .4f, 0f)
        val vertical = StrokePathJitter.x(11, 13, 90f, 80f, .4f, 0f)

        assertEquals(horizontal, StrokePathJitter.y(11, 13, 0f, 80f, .4f, 0f), 0f)
        assertEquals(-horizontal, vertical, .0001f)
    }
}
