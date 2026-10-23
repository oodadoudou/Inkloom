package app.inkloom.document

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DrawingFigureTest {
    private fun bone(figure: DrawingFigure, joint: FigureJoint) = FigureRig.bones(figure).first { it.joint == joint }

    @Test fun standingFigureHasHeadAboveFeet() {
        val figure = DrawingFigure()
        val head = bone(figure, FigureJoint.HEAD).end
        val foot = bone(figure, FigureJoint.LEFT_FOOT).end
        assertTrue(head.y > 1.6f && head.y < 1.9f)
        assertTrue(foot.y < .15f)
        assertTrue("left is +x", bone(figure, FigureJoint.LEFT_HAND).end.x > 0f)
    }

    @Test fun jointsTurnTheirChildren() {
        val tPose = DrawingFigure.pose(FigurePose.T_POSE)
        val hand = bone(tPose, FigureJoint.LEFT_HAND).end
        val shoulder = bone(tPose, FigureJoint.LEFT_UPPER_ARM).start
        assertEquals("the arm is level", shoulder.y, hand.y, .02f)
        val sitting = DrawingFigure.pose(FigurePose.SITTING)
        val knee = bone(sitting, FigureJoint.LEFT_THIGH).end; val hip = bone(sitting, FigureJoint.LEFT_THIGH).start
        assertEquals("the thigh points forward", hip.y, knee.y, .02f)
        assertTrue(knee.z > hip.z + .3f)
        assertTrue("the shin hangs down", bone(sitting, FigureJoint.LEFT_SHIN).end.y < knee.y - .3f)
    }

    @Test fun renderingDrawsTheFigureWhereTheCameraSeesIt() {
        val figure = DrawingFigure(yaw = 0f, pitch = 0f)
        val pixels = DrawingFigureRenderer.render(figure, 200, 300)
        val inked = pixels.indices.filter { pixels[it] ushr 24 > 0 }
        assertTrue(inked.isNotEmpty())
        val top = inked.minOf { it / 200 }; val bottom = inked.maxOf { it / 200 }
        val tall = (bottom - top) / 300f
        assertTrue("the figure fills about its set height: $tall", abs(tall - figure.height) < .12f)
        assertEquals("nothing in the corner", 0, pixels[0])
        val centre = pixels[(165) * 200 + 100]
        assertTrue("the torso is shaded colour", centre ushr 24 == 255)
    }

    @Test fun outlinesOnlyDrawEdges() {
        val pixels = DrawingFigureRenderer.render(DrawingFigure(yaw = 0f, pitch = 0f, style = FigureStyle.OUTLINE), 200, 300)
        val inked = pixels.count { it ushr 24 > 0 }
        val shaded = DrawingFigureRenderer.render(DrawingFigure(yaw = 0f, pitch = 0f), 200, 300).count { it ushr 24 > 0 }
        assertTrue(inked in 1 until shaded / 2)
    }

    @Test fun theCameraGivesAPerspectiveRuler() {
        val ruler = requireNotNull(FigureRig.perspective(DrawingFigure(yaw = 35f, pitch = 15f), 1000f, 1400f))
        assertTrue(ruler.count in 2..3)
    }

    @Test fun figuresSurviveSerialization() {
        val layer = InkLayer(id = "f", name = "Figure", figure = DrawingFigure.pose(FigurePose.RUNNING).copy(style = FigureStyle.SILHOUETTE))
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(layer, json.decodeFromString(InkLayer.serializer(), json.encodeToString(InkLayer.serializer(), layer)))
    }

    @Test fun capsulesAreHitFromOutside() {
        val t = DrawingFigureRenderer.capsule(Vec3(0f, 0f, -5f), Vec3(0f, 0f, 1f), Vec3(-1f, 0f, 0f), Vec3(1f, 0f, 0f), .5f)
        assertEquals(4.5f, requireNotNull(t), 1e-4f)
        assertNull(DrawingFigureRenderer.capsule(Vec3(0f, 2f, -5f), Vec3(0f, 0f, 1f), Vec3(-1f, 0f, 0f), Vec3(1f, 0f, 0f), .5f))
    }
}
