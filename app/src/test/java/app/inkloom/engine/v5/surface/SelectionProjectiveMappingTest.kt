package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.tools.*
import org.junit.Assert.*
import org.junit.Test

class SelectionProjectiveMappingTest {
    @Test fun perspectiveMapsCornersAndRoundTripsInteriorIncludingMirrors() {
        val source = listOf(10f to 20f, 110f to 20f, 110f to 100f, 10f to 100f)
        val target = listOf(35f to 5f, 160f to 35f, 125f to 150f, -20f to 95f)
        for (quad in listOf(target, target.reversed())) {
            val mapping = SelectionProjectiveMapping(10f,20f,100f,80f,quad)
            source.zip(quad).forEach { (a,b) ->
                val actual=mapping.forward(a.first,a.second)
                assertEquals(b.first,actual.first,.001f); assertEquals(b.second,actual.second,.001f)
            }
            val m=mapping.inverseCoefficients()
            for (x in 10..110 step 10) for (y in 20..100 step 10) {
                val (u,v)=mapping.forward(x.toFloat(),y.toFloat()); val w=m[6]*u+m[7]*v+m[8]
                assertEquals(x.toFloat(),(m[0]*u+m[1]*v+m[4])/w,.001f)
                assertEquals(y.toFloat(),(m[2]*u+m[3]*v+m[5])/w,.001f)
            }
        }
    }
    @Test fun cornerAndEdgeEditsRetainOtherNodesAndRejectCrossing() {
        val start=SelectionTransformPose(50f,50f,100f,100f)
        assertSame(start,start.distort(SelectionTransformNode.TOP_LEFT,0f,0f))
        assertTrue(SelectionProjectiveMapping(0f,0f,100f,100f,start.sourceCorners()).isIdentity)
        val corner=start.distort(SelectionTransformNode.TOP_LEFT,20f,10f)!!
        assertEquals(20f to 10f,corner.corners!![0])
        assertEquals(start.sourceCorners().drop(1),corner.corners.drop(1))
        val shear=start.distort(SelectionTransformNode.TOP,20f,0f)!!
        assertEquals(listOf(20f to 0f,120f to 0f,100f to 100f,0f to 100f),shear.corners)
        assertNull(start.distort(SelectionTransformNode.TOP_LEFT,120f,120f))
        val moved=corner.drag(SelectionTransformNode.MOVE,0f,0f,10f,15f,SelectionTransformMode.FREEFORM)
        corner.corners.zip(moved.corners!!).forEach { (a,b) -> assertEquals(a.first+10f,b.first,.001f); assertEquals(a.second+15f,b.second,.001f) }
        val flip=corner.withPose(corner.copy(flippedX=true))
        val unflip=flip.withPose(flip.copy(flippedX=false))
        corner.corners.zip(unflip.corners!!).forEach { (a,b) -> assertEquals(a.first,b.first,.001f); assertEquals(a.second,b.second,.001f) }
    }
}
