package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaserTrailTest {
    @Test fun `finished trail fades completely without persisted state`() {
        val trail = LaserTrail(lifetimeMs = 1_000L)
        trail.begin(.1f, .2f, 100L)
        trail.append(.8f, .7f, 300L)
        trail.end()

        val fading = trail.renderPoints(800L)
        assertEquals(2, fading.size)
        assertTrue(fading.first().alpha < fading.last().alpha)
        assertTrue(trail.renderPoints(1_301L).isEmpty())
    }

    @Test fun `active pointer head remains visible until release`() {
        val trail = LaserTrail(lifetimeMs = 500L)
        trail.begin(.4f, .5f, 100L)

        val held = trail.renderPoints(10_000L)
        assertEquals(1, held.size)
        assertEquals(1f, held.single().alpha, 0f)

        trail.end()
        assertTrue(trail.renderPoints(10_001L).isEmpty())
    }

    @Test fun `cancelling active gesture keeps an older fading gesture separate`() {
        val trail = LaserTrail(lifetimeMs = 1_000L)
        trail.begin(.1f, .1f, 100L)
        trail.append(.2f, .2f, 120L)
        trail.end()
        trail.begin(.7f, .7f, 200L)
        trail.append(.8f, .8f, 220L)
        trail.cancelActive()

        val remaining = trail.renderPoints(300L)
        assertEquals(2, remaining.size)
        assertEquals(1, remaining.map(LaserTrailRenderPoint::gestureId).distinct().size)
        assertTrue(remaining.all { it.x <= .2f && it.y <= .2f })
    }

    @Test fun `input is normalized and memory stays bounded`() {
        val trail = LaserTrail(lifetimeMs = 10_000L, maximumPoints = 4, minimumDistance = 0f)
        trail.begin(Float.NaN, .5f, 0L)
        trail.begin(-1f, 2f, 1L)
        repeat(10) { index -> trail.append(index / 10f, index / 10f, index + 2L) }

        val points = trail.renderPoints(20L)
        assertEquals(4, points.size)
        assertTrue(points.all { it.x in 0f..1f && it.y in 0f..1f })
    }
}
