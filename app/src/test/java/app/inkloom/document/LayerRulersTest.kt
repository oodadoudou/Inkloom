package app.inkloom.document

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class LayerRulersTest {
    private val w = 200f
    private val h = 100f

    @Test fun straightRulersProjectOntoTheirWholeLine() {
        val ruler = RulerShape.Straight(x1 = .1f, y1 = .5f, x2 = .3f, y2 = .5f)
        val session = RulerSnapSession(ruler, w, h)
        val (x, y) = session.project(180f, 70f)
        assertEquals("beyond the end the line continues", 180f, x, 1e-3f)
        assertEquals(50f, y, 1e-3f)
        assertEquals(20f, RulerGeometry.distance(ruler, 150f, 30f, w, h), 1e-3f)
    }

    @Test fun guidesSnapAcrossThePage() {
        val guide = RulerShape.Guide(vertical = true, position = .25f)
        assertEquals(50f to 80f, RulerSnapSession(guide, w, h).project(57f, 80f))
    }

    @Test fun pathRulersFollowTheLineWithoutJumping() {
        // A U shape: down the left side, along the bottom, up the right side, 20 px apart at the top.
        val u = RulerShape.Path(points = listOf(.45f, .1f, .45f, .9f, .55f, .9f, .55f, .1f))
        val session = RulerSnapSession(u, w, h)
        assertEquals(90f to 15f, session.project(92f, 15f))
        // Moving down the left arm stays on it even when the pen drifts closer to the right arm.
        var point = 90f to 15f
        for (step in 1..10) point = session.project(90f + step * 1.4f, 15f + step * 3f)
        assertEquals("the pen is at x = 104, nearer the right arm", 90f, point.first, 1e-3f)
        assertEquals(45f, point.second, 1e-3f)
        for (step in 1..8) point = session.project(92f, 45f + step * 5f)
        // Continuing round the bottom reaches the right arm.
        for (x in listOf(95f, 100f, 105f, 110f)) point = session.project(x, 95f)
        assertEquals(90f, point.second, 1e-3f)
        point = session.project(111f, 80f)
        assertEquals(110f, point.first, 1e-3f)
        assertEquals(80f, point.second, 1e-3f)
    }

    @Test fun curvesPassThroughTheirPointsSmoothly() {
        val curve = RulerShape.Path(points = listOf(.1f, .5f, .5f, .2f, .9f, .5f), kind = RulerPathKind.CURVE)
        val line = RulerGeometry.polyline(curve, w, h)
        assertTrue("a curve is sampled finely", line.size / 2 > 20)
        assertTrue((0 until line.size / 2).any { hypot(line[it * 2] - 100f, line[it * 2 + 1] - 20f) < .5f })
        assertEquals(180f, line[line.size - 2], 1e-3f)
    }

    @Test fun nearestRulerWithinCaptureDistanceIsChosen() {
        val a = RulerShape.Straight(x1 = 0f, y1 = .2f, x2 = 1f, y2 = .2f)
        val b = RulerShape.Straight(x1 = 0f, y1 = .5f, x2 = 1f, y2 = .5f)
        assertEquals(b, RulerGeometry.nearest(listOf(a, b), 10f, 45f, w, h, 10f))
        assertNull(RulerGeometry.nearest(listOf(a, b), 10f, 35f, w, h, 10f))
    }

    @Test fun figuresAreClosedOutlines() {
        val rectangle = RulerGeometry.figure(RulerPathKind.RECTANGLE, .1f, .1f, .5f, .6f, w, h)
        assertEquals(4, RulerGeometry.closedOutline(rectangle, w, h)!!.size)
        val ellipse = RulerGeometry.figure(RulerPathKind.ELLIPSE, .2f, .2f, .6f, .8f, w, h)
        val outline = RulerGeometry.closedOutline(ellipse, w, h)!!
        assertTrue(outline.size > 16)
        outline.forEach { (x, y) ->
            val nx = (x - .4f) / .2f; val ny = (y - .5f) / .3f
            assertEquals("ellipse samples lie on the ellipse", 1f, nx * nx + ny * ny, .03f)
        }
        assertNull("open rulers cannot make a selection", RulerGeometry.closedOutline(RulerShape.Straight(x1 = 0f, y1 = 0f, x2 = 1f, y2 = 1f), w, h))
        assertEquals(6, RulerGeometry.figure(RulerPathKind.POLYGON, 0f, 0f, 1f, 1f, w, h, sides = 6).pointCount)
    }

    @Test fun displayRangeDecidesWhichLayersSnap() {
        val ruler = RulerShape.Straight(x1 = 0f, y1 = 0f, x2 = 1f, y2 = 1f)
        fun layers(range: RulerRange, visible: Boolean = true) = listOf(
            InkLayer(id = "folder", name = "F", role = LayerRole.FOLDER),
            InkLayer(id = "owner", name = "O", parentId = "folder", rulers = LayerRulers(listOf(ruler), range = range, visible = visible)),
            InkLayer(id = "sibling", name = "S", parentId = "folder"),
            InkLayer(id = "outside", name = "X"),
        )
        fun sees(range: RulerRange, layer: String, visible: Boolean = true) = RulerScope.applicable(layers(range, visible), layer).isNotEmpty()
        assertTrue(sees(RulerRange.EDITING_TARGET, "owner")); assertFalse(sees(RulerRange.EDITING_TARGET, "sibling"))
        assertTrue(sees(RulerRange.SAME_FOLDER, "sibling")); assertFalse(sees(RulerRange.SAME_FOLDER, "outside"))
        assertTrue(sees(RulerRange.ALL_LAYERS, "outside"))
        assertFalse("hidden rulers do not snap", sees(RulerRange.ALL_LAYERS, "owner", visible = false))
    }

    @Test fun rulersMoveWithPageGeometry() {
        val rulers = LayerRulers(listOf(RulerShape.Straight(x1 = .1f, y1 = .2f, x2 = .3f, y2 = .2f),
            RulerShape.Guide(vertical = true, position = .25f), RulerShape.Guide(vertical = false, position = .4f)),
            perspective = PerspectiveRuler.create(1))
        val rotated = RulerGeometry.rotatedClockwise(rulers)
        val straight = rotated.shapes[0] as RulerShape.Straight
        assertEquals(.8f, straight.x1, 1e-5f); assertEquals(.1f, straight.y1, 1e-5f)
        assertEquals(RulerShape.Guide(rulers.shapes[1].id, vertical = false, position = .25f), rotated.shapes[1])
        assertEquals(RulerShape.Guide(rulers.shapes[2].id, vertical = true, position = .6f), rotated.shapes[2])
        assertEquals(.58f, rotated.perspective!!.x(0), 1e-5f)
        val flipped = RulerGeometry.flipped(rulers, CanvasFlip.HORIZONTAL)
        assertEquals(.75f, (flipped.shapes[1] as RulerShape.Guide).position, 1e-5f)
        assertEquals(.4f, (flipped.shapes[2] as RulerShape.Guide).position, 1e-5f)
        val page = InkPage(width = 200f, height = 100f, layers = listOf(InkLayer(id = "a", name = "A", rulers = rulers)))
        assertEquals(.58f, PageGeometry.rotateClockwise(page).layers.single().rulers!!.perspective!!.x(0), 1e-5f)
    }

    @Test fun vectorLinesBecomeRulers() {
        val stroke = VectorStroke(points = (0..20).flatMap { listOf(.1f + it * .02f, .5f, 1f) }, colorArgb = -1, diameter = 2f)
        val rulers = RulerGeometry.fromVector(VectorLayerContent(listOf(stroke)), w, h)
        val path = rulers.single() as RulerShape.Path
        assertEquals("a straight line simplifies to its ends", 2, path.pointCount)
        assertEquals(RulerPathKind.VECTOR, path.kind)
        assertEquals(.5f, path.points[1], 1e-5f)
    }

    @Test fun perspectiveRulersSnapOnlyToEnabledPoints() {
        val ruler = PerspectiveRuler.create(2).copy(snap = listOf(true, false))
        val guide = ruler.asGuide()
        assertEquals(PerspectiveGuideMode.ONE_POINT, guide.mode)
        assertEquals(-.15f, guide.activePoints().single().x, 1e-5f)
        assertEquals("fixed points do not move", ruler, ruler.copy(fixed = listOf(true, false)).moved(0, .3f, .3f).copy(fixed = listOf(false, false)))
    }

    @Test fun rulersSurviveSerialization() {
        val layer = InkLayer(id = "r", name = "R", rulers = LayerRulers(listOf(RulerShape.Straight(x1 = 0f, y1 = 0f, x2 = 1f, y2 = 1f),
            RulerGeometry.figure(RulerPathKind.ELLIPSE, 0f, 0f, .5f, .5f, w, h), RulerShape.Guide(vertical = false, position = .5f)),
            perspective = PerspectiveRuler.create(3), range = RulerRange.SAME_FOLDER, linkGuides = true))
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(layer, json.decodeFromString(InkLayer.serializer(), json.encodeToString(InkLayer.serializer(), layer)))
    }

    @Suppress("unused") private fun close(a: Float, b: Float) = abs(a - b) < 1e-3f
}
