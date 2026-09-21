package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import app.inkloom.document.PageElement
import app.inkloom.engine.v5.surface.*
import app.inkloom.engine.v5.tools.*
import app.inkloom.ui.MixedSelectionDraft
import app.inkloom.ui.MixedSelectionSource
import org.junit.Assert.*
import org.junit.Test

class SelectionMeshRasterTest {
    @Test fun meshMovesPixelsAndMaskAndCancelsExactlyInBothPrecisions() {
        for(format in PixelTileFormat.entries) for(filter in SelectionInterpolation.entries) {
            val surface=PixelTileSurface(96,96,pixelFormat=format)
            val area=PixelRect(12,12,72,72)
            surface.beginWrite("solid").use { t ->
                for(y in 12..71) for(x in 12..71) if(format.isHighPrecision) t.setLinearPixel(x,y,LinearRgba(1.5f,.2f,.1f,1f)) else t.setPixel(x,y,Color.RED)
                t.commit().close()
            }
            val before=surface.snapshot()
            val mask=MaskTileSurface(96,96)
            mask.beginWrite("selection").use { it.replaceAlpha(area,ByteArray(3600) { 255.toByte() });it.commit().close() }
            val original=SelectionTransformPose(42f,42f,60f,60f)
            val map=original.warp(5,15f,7f).rasterMappingFrom(original)
            val session=RasterSelectionTransformSession(surface,mask.snapshot())
            val result=session.update(map,filter)
            for(y in 0..95) for(x in 0..95) {
                val alpha=if(format.isHighPrecision) (surface.snapshot().linearPixelAt(x,y).alpha*255).toInt() else surface.pixelAt(x,y) ushr 24
                assertTrue("$filter/$format $x,$y alpha", kotlin.math.abs(alpha-result.selection.alphaAt(x,y))<=1)
            }
            session.update(original.warp(10,-10f,8f).rasterMappingFrom(original),filter)
            session.cancel()
            if(format.isHighPrecision) assertArrayEquals(before.copyPremultipliedF16(PixelRect(0,0,96,96)),surface.snapshot().copyPremultipliedF16(PixelRect(0,0,96,96)))
            else assertArrayEquals(before.copyPremultipliedPixels(PixelRect(0,0,96,96)),surface.snapshot().copyPremultipliedPixels(PixelRect(0,0,96,96)))
        }
    }

    @Test fun snappingUsesScreenDistanceAndNavigationCancelsOnlyTheCurrentDrag() {
        fun gesture(zoom:Float, enabled:Boolean): SelectionTransformPose {
            val controller=app.inkloom.ui.SelectionCanvasTransform(SelectionTransformPose(250f,250f,100f,100f),1000,1000,1f,{})
            controller.snapping=enabled
            fun send(action:Int,x:Float) { android.view.MotionEvent.obtain(0,1,action,x,250f,0).also {
                controller.touch(it,x,250f,1000,1000,zoom);it.recycle() } }
            send(android.view.MotionEvent.ACTION_DOWN,250f);send(android.view.MotionEvent.ACTION_MOVE,493f);send(android.view.MotionEvent.ACTION_UP,493f)
            return controller.pose
        }
        assertEquals(500f,gesture(1f,true).centerX,.001f)
        assertEquals(493f,gesture(1f,false).centerX,.001f)
        assertEquals(493f,gesture(4f,true).centerX,.001f)
        val original=SelectionTransformPose(250f,250f,100f,100f)
        val controller=app.inkloom.ui.SelectionCanvasTransform(original,1000,1000,1f,{})
        android.view.MotionEvent.obtain(0,1,0,250f,250f,0).also {controller.touch(it,250f,250f,1000,1000,1f);it.recycle()}
        android.view.MotionEvent.obtain(0,2,2,300f,280f,0).also {controller.touch(it,300f,280f,1000,1000,1f);it.recycle()}
        val event=android.view.MotionEvent.obtain(0,3,android.view.MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),2,
            Array(2) { android.view.MotionEvent.PointerProperties().apply {id=it;toolType=android.view.MotionEvent.TOOL_TYPE_FINGER} },
            Array(2) { android.view.MotionEvent.PointerCoords().apply {x=300f+it*20;y=280f} },0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
        assertFalse(controller.touch(event,300f,280f,1000,1000,1f));event.recycle()
        assertEquals(original,controller.pose)
    }

    @Test fun mixedImageAndPaintTransformKeepsTheImageStructuredThroughUndoRedo() {
        for(format in PixelTileFormat.entries) {
            val surface=PixelTileSurface(64,64,pixelFormat=format)
            surface.beginWrite("paint").use { if(format.isHighPrecision) it.setLinearPixel(20,20,LinearRgba(1.5f,0f,0f,1f)) else it.setPixel(20,20,Color.RED);it.commit().close() }
            val image=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
            for(y in 8..16) for(x in 8..48) image.setPixel(x,y,Color.BLUE)
            var released=false
            val elements=listOf(PageElement.Image(x=0f,y=0f,width=64f,height=64f,assetPath="fixture.png"))
            val mixed=MixedSelectionDraft(MixedSelectionSource(elements,image,release={ released=true }))
            if(format.isHighPrecision) assertEquals(1.5f,surface.snapshot().linearPixelAt(20,20).red,.002f)
            val mask=MaskTileSurface(64,64)
            mask.beginWrite("half image and paint").use { it.replaceAlpha(PixelRect(8,8,25,25),ByteArray(17*17) { 255.toByte() });it.commit().close() }
            val session=RasterSelectionTransformSession(surface,mask.snapshot())
            val transform=SelectionAffineTransform(16f,16f,translateY=25f)
            session.update(transform)
            assertEquals(0,surface.pixelAt(10,35)); assertEquals(0,surface.pixelAt(10,10)); assertEquals(0,surface.pixelAt(40,10))
            val transformed=mixed.transformed(transform,64,64,64f,64f).single() as PageElement.Image
            assertEquals(25f/64f,transformed.y,.001f)
            assertEquals("fixture.png",transformed.assetPath)
            val record=session.commit()!!.transactionRecord!!
            surface.apply(record,PixelTileSurface.HistoryDirection.UNDO)
            assertEquals(0,surface.pixelAt(10,10)); assertEquals(Color.RED,surface.pixelAt(20,20))
            surface.apply(record,PixelTileSurface.HistoryDirection.REDO)
            assertEquals(Color.RED,surface.pixelAt(20,45)); assertEquals(0,surface.pixelAt(40,10))
            record.close(); mixed.close(); assertTrue(released)
        }
    }
}
