package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.tools.*
import org.junit.Assert.*
import org.junit.Test

class SelectionTransformGeometryTest {
    @Test fun skewKeepsParallelogramAndDoesNotBecomeFreeDistortion() {
        val start = SelectionTransformPose(50f, 50f, 40f, 20f)
        val skewed = requireNotNull(start.skew(SelectionTransformNode.TOP, 10f, 0f))
        val q = skewed.sourceCorners()
        assertEquals(40f, q[0].second, 0f)
        assertEquals(40f, q[1].second, 0f)
        assertEquals(60f, q[2].second, 0f)
        assertEquals(60f, q[3].second, 0f)
        assertEquals(10f, q[0].first - 30f, 0f)
        assertEquals(10f, q[1].first - 70f, 0f)
        assertEquals(30f, q[3].first, 0f)
        val inverse = skewed.rasterMappingFrom(start)
        assertNotNull(inverse)
    }

    @Test fun everyResizeNodeKeepsOppositeAnchorAcrossRotationsAndModes() {
        for (angle in listOf(0f, 33f, 90f, 217f)) for (mode in listOf(SelectionTransformMode.FREEFORM, SelectionTransformMode.UNIFORM)) {
            val start = SelectionTransformPose(500f, 300f, 180f, 95f, angle)
            for (node in SelectionTransformNode.entries.take(8)) {
                val pointer = start.point(node.x, node.y)
                val anchor = start.point(1f - node.x, 1f - node.y)
                val end = start.drag(node, pointer.first, pointer.second, pointer.first + 46, pointer.second - 21, mode)
                val after = end.point(1f - node.x, 1f - node.y)
                assertEquals("$angle/$mode/$node anchor x", anchor.first, after.first, .001f)
                assertEquals("$angle/$mode/$node anchor y", anchor.second, after.second, .001f)
                if (mode == SelectionTransformMode.UNIFORM) assertEquals(start.width / start.height, end.width / end.height, .0001f)
            }
        }
    }

    @Test fun cornerFollowsPointerInFreeformAndRotationDoesNotMoveCenter() {
        val start = SelectionTransformPose(200f, 200f, 180f, 90f, 35f)
        val p = start.point(1f, 1f)
        val end = start.drag(SelectionTransformNode.BOTTOM_RIGHT, p.first, p.second, p.first + 30f, p.second + 10f, SelectionTransformMode.FREEFORM)
        val q = end.point(1f, 1f)
        assertEquals(p.first + 30f, q.first, .001f); assertEquals(p.second + 10f, q.second, .001f)
        val rotated = start.drag(SelectionTransformNode.ROTATE, 200f, 100f, 300f, 200f, SelectionTransformMode.FREEFORM)
        assertEquals(125f, rotated.angle, .001f)
        assertEquals(start.centerX, rotated.centerX, 0f); assertEquals(start.centerY, rotated.centerY, 0f)
    }

    @Test fun crossingAnAnchorRemainsFiniteAndPreservesItsPosition() {
        val start = SelectionTransformPose(200f, 200f, 5f, 80f)
        val end = start.drag(SelectionTransformNode.TOP_LEFT, 197.5f, 160f, 600f, 600f, SelectionTransformMode.UNIFORM)
        assertTrue(end.width >= 1f && end.height >= 1f)
        assertEquals(start.width / start.height, end.width / end.height, .0001f)
        assertEquals(start.point(1f, 1f), end.point(1f, 1f))
    }
}
