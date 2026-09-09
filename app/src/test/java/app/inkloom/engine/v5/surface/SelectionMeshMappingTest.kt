package app.inkloom.engine.v5.surface
import app.inkloom.engine.v5.tools.*
import org.junit.Assert.*
import org.junit.Test

class SelectionMeshMappingTest {
    @Test fun identityAndInternalControlPointRoundTrip() {
        val original=SelectionTransformPose(150f,150f,240f,240f)
        val identity=SelectionMeshMapping(30f,30f,240f,240f,original.lattice())
        assertTrue(identity.isIdentity)
        for(y in 31..269 step 17) for(x in 31..269 step 17) {
            val p=identity.inverse(x.toFloat(),y.toFloat()); assertEquals(x.toFloat(),p.first,.001f); assertEquals(y.toFloat(),p.second,.001f)
        }
        val warped=original.warp(5,20f,10f)
        val map=warped.rasterMappingFrom(original) as SelectionMeshMapping
        val point=map.forward(110f,110f)
        assertEquals(130f,point.first,.001f); assertEquals(120f,point.second,.001f)
        val inverse=map.inverse(point.first,point.second)
        assertEquals(110f,inverse.first,.001f); assertEquals(110f,inverse.second,.001f)
        assertEquals(-1e10f,map.inverse(-500f,-500f).first,0f)
    }
    @Test fun affineAfterWarpKeepsEveryControlPointAndCancelSourceImmutable() {
        val original=SelectionTransformPose(150f,150f,240f,240f)
        val warp=original.warp(5,20f,10f)
        val moved=warp.withPose(warp.copy(centerX=warp.centerX+15,centerY=warp.centerY-20))
        warp.mesh!!.zip(moved.mesh!!).forEach { (a,b) -> assertEquals(a.first+15,b.first,.001f); assertEquals(a.second-20,b.second,.001f) }
        assertNull(original.mesh)
    }
    @Test fun foldedMeshInverseIsDeterministicAndFinite() {
        val original=SelectionTransformPose(150f,150f,240f,240f)
        val map=original.warp(0,210f,210f).rasterMappingFrom(original) as SelectionMeshMapping
        for(y in 30..270 step 7) for(x in 30..270 step 7) {
            val a=map.inverse(x.toFloat(),y.toFloat()); assertEquals(a,map.inverse(x.toFloat(),y.toFloat())); assertTrue(a.first.isFinite() && a.second.isFinite())
        }
    }
}
