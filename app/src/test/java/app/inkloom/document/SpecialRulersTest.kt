package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.hypot

class SpecialRulersTest {
    @Test fun parallelRulerProjectsEverySampleToConfiguredAngle() {
        val constraint = SpecialRulers.constraintAt(SpecialRulerState(SpecialRulerMode.PARALLEL, angleDegrees = 30f), .2f, .3f, .4f, .42f, 1000f, 1000f)!!
        val point = constraint.apply(.8f, .9f)
        assertEquals(kotlin.math.tan(Math.toRadians(30.0)).toFloat(), (point.y - .3f) / (point.x - .2f), .002f)
    }

    @Test fun radialRulerProjectsTowardConfiguredCenter() {
        val constraint = SpecialRulers.constraintAt(SpecialRulerState(SpecialRulerMode.RADIAL, centerX = .5f, centerY = .5f), .2f, .8f, .3f, .7f, 1000f, 1000f)!!
        val point = constraint.apply(.9f, .9f)
        assertEquals(-1f, (point.y - .8f) / (point.x - .2f), .002f)
    }

    @Test fun concentricRulerKeepsTheContactStartRadius() {
        val constraint = SpecialRulers.constraintAt(SpecialRulerState(SpecialRulerMode.CONCENTRIC), .8f, .5f, .79f, .55f, 1000f, 1000f)!!
        val point = constraint.apply(.5f, .9f)
        val radius = hypot(((point.x - .5f) * 1000f).toDouble(), ((point.y - .5f) * 1000f).toDouble()).toFloat()
        assertEquals(300f, radius, .01f)
        assertEquals(.8f, constraint.apply(.8f, .5f).x, .001f)
    }

    @Test fun isometricRulerLocksOneOfThreeAxesForTheWholeStroke() {
        val constraint = SpecialRulers.constraintAt(SpecialRulerState(SpecialRulerMode.ISOMETRIC), .2f, .8f, .35f, .71f, 1000f, 1000f)!!
        val first = constraint.apply(.5f, .2f)
        val later = constraint.apply(.9f, .9f)
        assertEquals((first.y - .8f) / (first.x - .2f), (later.y - .8f) / (later.x - .2f), .001f)
    }

    @Test fun handlesMoveCenterAndRotateIsometricAxes() {
        val state = SpecialRulerState.defaultFor(SpecialRulerMode.ISOMETRIC)
        assertEquals(SpecialRulerHandle.CENTER, SpecialRulers.hitHandle(state, 500f, 500f, 1000f, 1000f, 30f, 120f))
        val moved = SpecialRulers.moveHandle(state, SpecialRulerHandle.CENTER, 350f, 650f, 1000f, 1000f)
        assertEquals(.35f, moved.centerX, .001f)
        assertEquals(.65f, moved.centerY, .001f)
        val rotated = SpecialRulers.moveHandle(state, SpecialRulerHandle.ROTATION, 500f, 700f, 1000f, 1000f)
        assertEquals(60f, rotated.angleDegrees, .001f)
    }

    @Test fun disabledAndTinyMovementNeverCaptureAStroke() {
        assertNull(SpecialRulers.constraintAt(SpecialRulerState(), .5f, .5f, .8f, .8f, 1000f, 1000f))
        assertNull(SpecialRulers.constraintAt(SpecialRulerState(SpecialRulerMode.PARALLEL), .5f, .5f, .501f, .501f, 1000f, 1000f))
    }
}
