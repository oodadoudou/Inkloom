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

    @Test fun `quadratic Bezier mode preserves endpoints and produces a durable curved path`() {
        val spline = StrokePostCorrection.apply(rough, .7f, false, false, 1f, 1600f, 2263f)
        val bezier = StrokePostCorrection.apply(rough, .7f, false, false, 1f, 1600f, 2263f, quadraticBezier = true)

        assertEquals(rough.points.first(), bezier.points.first())
        assertEquals(rough.points.last(), bezier.points.last())
        assertTrue(bezier.points.size > spline.points.size)
        assertNotEquals(spline.points, bezier.points)
    }

    @Test fun `sharp-angle mode keeps a pointed corner that spline correction would round`() {
        val corner = rough.copy(points = listOf(
            StrokePoint(.1f, .5f),
            StrokePoint(.4f, .5f),
            StrokePoint(.5f, .5f),
            StrokePoint(.5f, .7f),
            StrokePoint(.5f, .9f),
        ))
        val rounded = StrokePostCorrection.apply(corner, .7f, false, false, 1f, 1600f, 1600f)
        val pointed = StrokePostCorrection.apply(corner, .7f, false, false, 1f, 1600f, 1600f, quadraticBezier = true, preserveSharpAngles = true)

        assertNotEquals(.5f, rounded.points.minBy { kotlin.math.abs(it.x - .5f) + kotlin.math.abs(it.y - .5f) }.y, .0001f)
        assertTrue(pointed.points.any { kotlin.math.abs(it.x - .5f) < .0001f && kotlin.math.abs(it.y - .5f) < .0001f })
    }

    @Test fun `speed adjustment preserves more of a fast expressive trajectory`() {
        val slow = rough.copy(points = rough.points.map { it.copy(velocity = .05f) })
        val fast = rough.copy(points = rough.points.map { it.copy(velocity = 1f) })
        val correctedSlow = StrokePostCorrection.apply(slow, .8f, true, false, 1f, 1600f, 2263f)
        val correctedFast = StrokePostCorrection.apply(fast, .8f, true, false, 1f, 1600f, 2263f)
        val slowMovement = kotlin.math.abs(correctedSlow.points[1].y - slow.points[1].y)
        val fastMovement = kotlin.math.abs(correctedFast.points[1].y - fast.points[1].y)
        assertTrue(fastMovement < slowMovement)
    }

    @Test fun `long post-corrected stroke is bounded and does not recurse through point slices`() {
        val longStroke = rough.copy(points = List(20_000) { index ->
            val x = index / 19_999f
            StrokePoint(x, .5f + kotlin.math.sin(index * .031f) * .04f, velocity = .2f, pathDistance = index / 100f)
        })
        lateinit var corrected: InkStroke
        val elapsedMs = kotlin.system.measureTimeMillis {
            corrected = StrokePostCorrection.apply(longStroke, .9f, true, true, 2f, 1600f, 2263f, quadraticBezier = true)
        }
        assertEquals(longStroke.points.first(), corrected.points.first())
        assertEquals(longStroke.points.last(), corrected.points.last())
        assertTrue("Post correction took ${elapsedMs}ms", elapsedMs < 1_000L)
        assertTrue(corrected.points.size < longStroke.points.size)
    }
}
