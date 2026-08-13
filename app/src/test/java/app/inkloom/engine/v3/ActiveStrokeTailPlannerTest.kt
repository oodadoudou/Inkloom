package app.inkloom.engine.v3

import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v3.render.ActiveStrokeTailPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveStrokeTailPlannerTest {
    @Test
    fun vectorInkAppendsOnlyTheNewSegment() {
        val planner = ActiveStrokeTailPlanner()
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
    fun ordinaryImportedStampBrushUsesIncrementalDabDrawingWithoutClearing() {
        val planner = ActiveStrokeTailPlanner()
        val stamped = stroke(8).copy(tipAssetPath = "/tmp/tip.png", tipAssetPaths = listOf("/tmp/tip.png"))

        val plan = planner.incremental(listOf(stamped))

        assertFalse(plan.clearBeforeDraw)
        assertTrue(plan.incrementalStampDraw)
        assertEquals(stamped.points, plan.strokes.single().points)
    }

    @Test
    fun dualBrushAppendsDabsWithoutQuadraticReplay() {
        val planner = ActiveStrokeTailPlanner()
        val dual = stroke(8).copy(
            tipAssetPath = "/tmp/tip.png",
            tipAssetPaths = listOf("/tmp/tip.png"),
            dualBrush = true,
        )

        val plan = planner.incremental(listOf(dual))

        assertFalse(plan.clearBeforeDraw)
        assertTrue(plan.incrementalStampDraw)
    }

    @Test
    fun translucentVectorInkStillAppendsOnlyItsTail() {
        val planner = ActiveStrokeTailPlanner()
        val translucent = stroke(8).copy(opacity = .35f)

        val plan = planner.incremental(listOf(translucent))

        assertFalse(plan.clearBeforeDraw)
        assertFalse(plan.incrementalStampDraw)
        assertEquals(translucent.points, plan.strokes.single().points)
    }

    @Test
    fun importedBrushWithEndingTaperNeverReplaysItsGrowingHistory() {
        val planner = ActiveStrokeTailPlanner()
        val first = stroke(40).copy(tipAssetPath = "/tmp/sut-tip.png", taperEndEnabled = true)
        val second = stroke(90).copy(tipAssetPath = "/tmp/sut-tip.png", taperEndEnabled = true)

        planner.incremental(listOf(first))
        val plan = planner.incremental(listOf(second))

        assertFalse(plan.clearBeforeDraw)
        assertTrue(plan.incrementalStampDraw)
    }

    @Test
    fun opaqueWholeStrokeTextureUsesItsRetainedUnionSurface() {
        val planner = ActiveStrokeTailPlanner()
        val first = stroke(8).copy(grainAssetPath = "/tmp/grain.png", grainDensity = .7f, grainApplyPerPlot = false)
        val second = first.copy(points = stroke(16).points)

        planner.incremental(listOf(first))
        val plan = planner.incremental(listOf(second))

        assertFalse(plan.clearBeforeDraw)
        assertTrue(plan.incrementalStampDraw)
        assertEquals(second.points, plan.strokes.single().points)
    }

    @Test
    fun translucentWholeStrokeTextureClearsBecauseSourceReplacementIsNotAssociative() {
        val planner = ActiveStrokeTailPlanner()
        val first = stroke(8).copy(grainAssetPath = "/tmp/grain.png", grainDensity = .7f, grainApplyPerPlot = false, opacity = .5f)
        val second = first.copy(points = stroke(16).points)

        planner.incremental(listOf(first))
        val plan = planner.incremental(listOf(second))

        assertTrue(plan.clearBeforeDraw)
        assertFalse(plan.incrementalStampDraw)
    }

    private fun stroke(count: Int) = InkStroke(
        id = "active",
        points = List(count) { index -> StrokePoint(index / 20f, .5f, timeMs = index.toLong()) },
    )
}
