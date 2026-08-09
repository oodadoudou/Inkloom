package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PerspectiveGuidesTest {
    @Test fun onePointGuideLocksToVanishingHorizontalOrVerticalDirections() {
        val state = PerspectiveGuideState.defaultFor(PerspectiveGuideMode.ONE_POINT)
        val towardVanishing = PerspectiveGuides.constraintAt(state, .2f, .8f, .42f, .57f, 1000f, 1000f)
        val horizontal = PerspectiveGuides.constraintAt(state, .2f, .8f, .5f, .79f, 1000f, 1000f)
        val vertical = PerspectiveGuides.constraintAt(state, .2f, .8f, .21f, .5f, 1000f, 1000f)

        assertNotNull(towardVanishing)
        assertEquals(.8f, horizontal!!.apply(.8f, .6f).y, .001f)
        assertEquals(.2f, vertical!!.apply(.7f, .3f).x, .001f)
    }

    @Test fun twoPointGuideChoosesOneVanishingDirectionAndNeverSwitchesMidStroke() {
        val state = PerspectiveGuideState.defaultFor(PerspectiveGuideMode.TWO_POINT)
        val constraint = PerspectiveGuides.constraintAt(state, .5f, .8f, .65f, .62f, 1200f, 800f)!!
        val first = constraint.apply(.72f, .55f)
        val later = constraint.apply(.15f, .2f)
        val firstSlope = (first.y - .8f) / (first.x - .5f)
        val laterSlope = (later.y - .8f) / (later.x - .5f)

        assertEquals(firstSlope, laterSlope, .001f)
    }

    @Test fun threePointGuideCanSnapTowardTheVerticalVanishingPoint() {
        val state = PerspectiveGuideState.defaultFor(PerspectiveGuideMode.THREE_POINT)
        val constraint = PerspectiveGuides.constraintAt(state, .7f, .8f, .66f, .45f, 1000f, 1200f)!!
        val projected = constraint.apply(.6f, .1f)

        assertEquals(lineXAtY(.7f, .8f, .5f, -.35f, projected.y), projected.x, .002f)
    }

    @Test fun assistanceOffAndTinyMovementDoNotCaptureAStroke() {
        val off = PerspectiveGuideState.defaultFor(PerspectiveGuideMode.TWO_POINT).copy(assisted = false)
        assertNull(PerspectiveGuides.constraintAt(off, .5f, .5f, .8f, .6f, 1000f, 1000f))
        assertNull(PerspectiveGuides.constraintAt(off.copy(assisted = true), .5f, .5f, .501f, .501f, 1000f, 1000f))
    }

    @Test fun editingHitTargetsOnlyActiveVanishingPoints() {
        val state = PerspectiveGuideState.defaultFor(PerspectiveGuideMode.TWO_POINT).copy(editing = true)
        assertEquals(0, PerspectiveGuides.hitPoint(state, 120f, 420f, 1000f, 1000f, 24f))
        assertNull(PerspectiveGuides.hitPoint(state.copy(editing = false), 120f, 420f, 1000f, 1000f, 24f))
    }

    private fun lineXAtY(startX: Float, startY: Float, endX: Float, endY: Float, y: Float): Float {
        val amount = (y - startY) / (endY - startY)
        return startX + (endX - startX) * amount
    }
}
