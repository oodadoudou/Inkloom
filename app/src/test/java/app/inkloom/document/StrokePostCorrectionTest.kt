package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokePostCorrectionTest {
    private val rough = InkStroke(
        id = "rough",
        width = 12f,
        points = listOf(
            StrokePoint(.1f, .5f, velocity = .2f),
            StrokePoint(.3f, .65f, velocity = .2f),
            StrokePoint(.5f, .35f, velocity = .2f),
            StrokePoint(.7f, .64f, velocity = .2f),
            StrokePoint(.9f, .5f, velocity = .2f),
        ),
    )

    @Test fun `zero correction is an exact no-op`() {
        assertEquals(rough, StrokePostCorrection.apply(rough, 0f, true, true, 8f, 1600f, 2263f))
    }

    @Test fun `correction preserves endpoints while smoothing inner samples`() {
        val corrected = StrokePostCorrection.apply(rough, .7f, false, false, 1f, 1600f, 2263f)
        assertEquals(rough.points.first(), corrected.points.first())
        assertEquals(rough.points.last(), corrected.points.last())
        assertNotEquals(rough.points, corrected.points)
    }

    @Test fun `scale adjustment reduces correction at detailed zoom`() {
        val normal = StrokePostCorrection.apply(rough, .8f, false, true, 1f, 1600f, 2263f)
        val zoomed = StrokePostCorrection.apply(rough, .8f, false, true, 16f, 1600f, 2263f)
        val originalDeviation = rough.points[1].y - .5f
        val normalDeviation = normal.points[1].y - .5f
        val zoomedDeviation = zoomed.points[1].y - .5f
        assertTrue(kotlin.math.abs(normalDeviation) < kotlin.math.abs(zoomedDeviation))
        assertTrue(kotlin.math.abs(zoomedDeviation) < kotlin.math.abs(originalDeviation))
    }
}
