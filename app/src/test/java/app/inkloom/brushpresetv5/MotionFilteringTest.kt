package app.inkloom.brushpresetv5

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.stroke.StrokeCoordinateStabilizer
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class MotionFilteringTest {
    @Test fun zeroPreservesInputAndNewContactsDoNotInheritHistory() {
        val raw=StrokeCoordinateStabilizer(0f,0f)
        val points=(0..100).map { DocumentPoint(it.toFloat(),if(it%2==0) 2f else -2f) }
        assertEquals(points,points.map(raw::apply))
        val filter=StrokeCoordinateStabilizer(0f,0f,MotionFilteringSettings(.8f))
        val result=points.map(filter::apply)
        assertTrue(result.drop(10).sumOf { kotlin.math.abs(it.y).toDouble() } < points.drop(10).sumOf { kotlin.math.abs(it.y).toDouble() })
        result.zip(points).forEach { (a,b) -> assertTrue(hypot(a.x-b.x,a.y-b.y)<=5.051f) }
        assertEquals(points.last(),StrokeCoordinateStabilizer(0f,0f,MotionFilteringSettings(.8f)).apply(points.last()))
    }
    @Test fun globalStagePrecedesCompleteBrushStage() {
        val global=StrokeCoordinateStabilizer(.3f,0f,MotionFilteringSettings(.7f,.2f))
        val brush=StrokeCoordinateStabilizer(0f,.6f,brushMotion=MotionFilteringSettings(.4f,.8f))
        val combined=StrokeCoordinateStabilizer(.3f,.6f,MotionFilteringSettings(.7f,.2f),MotionFilteringSettings(.4f,.8f))
        repeat(60) { i -> val point=DocumentPoint(i.toFloat(),if(i%2==0)3f else -3f)
            assertEquals(brush.apply(global.apply(point)),combined.apply(point)) }
    }
    @Test fun expressionRetainsTurnsAndSerializedSettingsRemainExact() {
        fun atTurn(expression:Float):DocumentPoint {
            val f=StrokeCoordinateStabilizer(0f,0f,brushMotion=MotionFilteringSettings(.8f,expression))
            repeat(20) { f.apply(DocumentPoint(it.toFloat(),0f)) }
            return f.apply(DocumentPoint(19f,3f))
        }
        assertTrue(atTurn(1f).y > atTurn(0f).y)
        val preset=BrushPresetV5Import.requirePreset(BrushPresetV5Import.createUserRecord(app.inkloom.model.ImportedBrush(name="test",format=app.inkloom.model.BrushFormat.INKLOOM,compatibility=app.inkloom.model.Compatibility.COMPLETE,sizeBytes=0,report=emptyList())))
            .copy(stroke=StrokeSettings(motionFiltering=MotionFilteringSettings(.73f,.41f)))
        assertEquals(preset,BrushPresetV5Codec.decodeOrNull(BrushPresetV5Codec.encode(preset)))
    }
}
