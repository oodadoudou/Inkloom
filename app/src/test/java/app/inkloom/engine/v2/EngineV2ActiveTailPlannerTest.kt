package app.inkloom.engine.v2

import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v2.render.EngineV2ActiveTailPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineV2ActiveTailPlannerTest {
    @Test
    fun vectorInkAppendsOnlyTheNewSegment() {
        val planner = EngineV2ActiveTailPlanner()
        val first = stroke(4)
        val second = stroke(9)

        val initial = planner.incremental(listOf(first))
        val tail = planner.incremental(listOf(second))

        assertFalse(initial.clearBeforeDraw)
        assertEquals(4, initial.strokes.single().points.size)
        assertFalse(tail.clearBeforeDraw)
        assertEquals("One overlap point plus five new points", 6, tail.strokes.single().points.size)
        assertEquals(first.points.last(), tail.strokes.single().points.first())
    }

    @Test
    fun importedStampBrushKeepsDeterministicWholeStrokeReplay() {
        val planner = EngineV2ActiveTailPlanner()
        val stamped = stroke(8).copy(tipAssetPath = "/tmp/tip.png", tipAssetPaths = listOf("/tmp/tip.png"))

        val plan = planner.incremental(listOf(stamped))

        assertTrue(plan.clearBeforeDraw)
        assertEquals(stamped.points, plan.strokes.single().points)
    }

    private fun stroke(count: Int) = InkStroke(
        id = "active",
        points = List(count) { index -> StrokePoint(index / 20f, .5f, timeMs = index.toLong()) },
    )
}
