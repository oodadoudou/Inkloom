package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test

class ExtendedRasterFiltersTest {
    private fun stripe(tile:Int=64,high:Boolean=false):PixelTileSurface {
        val s=PixelTileSurface(128,128,tile,pixelFormat=if(high)PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 else PixelTileFormat.PREMULTIPLIED_SRGB_8888)
        s.beginWrite("line").use{w->for(y in 12..100){if(high)w.setLinearPixel(63,y,LinearRgba(1.4f,-.05f,.3f,1f))else w.setPixel(63,y,0xffff0000.toInt())};w.commit().close()};return s
    }
    @Test fun motionAngleAndLensSpreadInkAcrossTilesWithoutSeams(){
        val source=stripe().snapshot()
        val horizontal=RasterImageFilter.apply(source,RasterImageFilter.Effect.MotionBlur(8f,0f)).snapshot()
        val vertical=RasterImageFilter.apply(source,RasterImageFilter.Effect.MotionBlur(8f,90f)).snapshot()
        assertTrue(horizontal.pixelAt(68,60) ushr 24>0);assertEquals(0,vertical.pixelAt(68,60))
        assertTrue(RasterImageFilter.apply(source,RasterImageFilter.Effect.LensBlur(6f)).pixelAt(66,60) ushr 24>0)
        assertEquals(0xffff0000.toInt(),source.pixelAt(63,60))
    }
    @Test fun globalMosaicNoiseAndRadialAreIndependentOfTileSize(){
        val a=stripe(64).snapshot();val b=stripe(128).snapshot()
        listOf(RasterImageFilter.Effect.Mosaic(7f),RasterImageFilter.Effect.Noise(4f,seed=123,monochrome=false),
            RasterImageFilter.Effect.RadialBlur(10f,.2f,.7f),RasterImageFilter.Effect.RadialBlur(10f,.8f,.1f,zoom=true)).forEach{effect->
            val ra=RasterImageFilter.apply(a,effect).snapshot();val rb=RasterImageFilter.apply(b,effect).snapshot()
            assertArrayEquals(effect.toString(),ra.copyPremultipliedPixels(PixelRect(0,0,128,128)),rb.copyPremultipliedPixels(PixelRect(0,0,128,128)))
        }
    }
    @Test fun masksCancellationAndFloatRangeArePreserved(){
        val source=stripe(high=true).snapshot()
        val mask=MaskTileSurface(128,128,initialDefaultAlpha=0)
        mask.beginWrite("selected").use{it.setAlpha(63,60,255);it.commit().close()}
        val result=RasterImageFilter.apply(source,RasterImageFilter.Effect.Noise(1f),mask.snapshot()).snapshot()
        assertEquals(source.packedF16At(63,61),result.packedF16At(63,61))
        assertTrue(result.linearPixelAt(63,60).red>1f);assertTrue(result.linearPixelAt(63,60).green<0f)
        val sharpen=RasterImageFilter.apply(source,RasterImageFilter.Effect.UnsharpMask(2f,2f)).snapshot()
        assertTrue(sharpen.linearPixelAt(63,60).red>1.4f)
        try{RasterImageFilter.apply(source,RasterImageFilter.Effect.RadialBlur(20f),cancelled={true});fail("cancelled")}
        catch(_:java.util.concurrent.CancellationException){}
    }
    @Test fun chromaticAberrationSeparatesChannelsAndNeverCreatesOpaqueEmptyBackground(){
        val source=PixelTileSurface(128,128,64);source.beginWrite("white").use{it.setPixel(63,61,-1);it.commit().close()}
        val result=RasterImageFilter.apply(source.snapshot(),RasterImageFilter.Effect.ChromaticAberration(4f)).snapshot()
        assertTrue(result.pixelAt(59,61) ushr 24>0);assertTrue(result.pixelAt(67,61) ushr 24>0)
        assertEquals(0,result.pixelAt(1,1));assertNotEquals(result.pixelAt(59,61),result.pixelAt(67,61))
    }
    private fun gray(values:IntArray,tile:Int=64):PixelTileSurface {
        val s=PixelTileSurface(128,128,tile)
        s.beginWrite("steps").use{w->for(y in 0 until 128)for(x in 0 until 128){val v=values[(x/8)%values.size];w.setPixel(x,y,(0xff shl 24) or (v shl 16) or (v shl 8) or v)};w.commit().close()}
        return s
    }
    @Test fun fixedSharpenStrengthsRaiseEdgeContrastAcrossTilesAndKeepFlatAreas(){
        val source=gray(intArrayOf(96,160)).snapshot()
        val normal=RasterImageFilter.apply(source,RasterImageFilter.Effect.Sharpen(strong=false)).snapshot()
        val strong=RasterImageFilter.apply(source,RasterImageFilter.Effect.Sharpen(strong=true)).snapshot()
        fun red(s:PixelTileSurfaceSnapshot,x:Int)=s.pixelAt(x,40) ushr 16 and 255
        assertEquals("flat areas are unchanged",96,red(normal,3))
        assertTrue(red(normal,7)<96);assertTrue(red(normal,8)>160)
        assertTrue("Sharpen More is stronger",red(strong,7)<red(normal,7) && red(strong,8)>red(normal,8))
        assertEquals("tile seams do not change the result",
            RasterImageFilter.apply(gray(intArrayOf(96,160),128).snapshot(),RasterImageFilter.Effect.Sharpen(true)).snapshot().copyPremultipliedPixels(PixelRect(0,0,128,128)).toList(),
            strong.copyPremultipliedPixels(PixelRect(0,0,128,128)).toList())
        assertEquals(255,strong.pixelAt(64,40) ushr 24)
    }
    @Test fun sharpenTreatsTransparencyAsMissingColorAndRespectsSelectionAndF16(){
        val ink=PixelTileSurface(128,128,64)
        ink.beginWrite("dot").use{it.setPixel(63,60,0x80404040.toInt());it.commit().close()}
        val dot=RasterImageFilter.apply(ink.snapshot(),RasterImageFilter.Effect.Sharpen(true)).snapshot()
        assertEquals("no bright halo or alpha change on a transparent edge",0x80404040.toInt(),dot.pixelAt(63,60))
        assertEquals(0,dot.pixelAt(64,60))
        val source=gray(intArrayOf(96,160)).snapshot()
        val mask=MaskTileSurface(128,128,initialDefaultAlpha=0)
        mask.beginWrite("one edge").use{it.setAlpha(8,40,255);it.commit().close()}
        val selected=RasterImageFilter.apply(source,RasterImageFilter.Effect.Sharpen(false),mask.snapshot()).snapshot()
        assertTrue((selected.pixelAt(8,40) ushr 16 and 255)>160);assertEquals(source.pixelAt(7,40),selected.pixelAt(7,40))
        val high=stripe(high=true).snapshot()
        assertTrue(RasterImageFilter.apply(high,RasterImageFilter.Effect.Sharpen(true)).snapshot().linearPixelAt(63,60).red>1.4f)
    }
    @Test fun everyFilterRespectsSelectionPrecisionAndTransparentSurroundings(){
        for(high in listOf(false,true)) {
            val format=if(high)PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 else PixelTileFormat.PREMULTIPLIED_SRGB_8888
            val source=PixelTileSurface(128,128,64,pixelFormat=format)
            source.beginWrite("content").use { w ->
                for(y in 20 until 60) for(x in 20 until 60) {
                    val v=((x*7+y*3)%40)/60f+.2f
                    if(high) w.setLinearPixel(x,y,LinearRgba(v*1.3f,v*.7f,.4f,if(x<40) 1f else .5f))
                    else {
                        // Premultiplied ARGB without android.graphics in a JVM test.
                        val a=if(x<40) 255 else 128; val r=(v*255).toInt().coerceAtMost(255); val g=(v*180).toInt()
                        w.setPixel(x,y,(a shl 24) or (r*a/255 shl 16) or (g*a/255 shl 8) or (90*a/255))
                    }
                }
                w.commit().close()
            }
            val snapshot=source.snapshot()
            assertTrue("fixture has content",if(high) snapshot.linearPixelAt(30,30).alpha>0f else snapshot.pixelAt(30,30) ushr 24>0)
            val selection=MaskTileSurface(128,128,64)
            selection.beginWrite("left half").use { w -> for(y in 0 until 128) for(x in 0 until 40) w.setAlpha(x,y,255); w.commit().close() }
            for(kind in 0..15) {
                val effect=app.inkloom.settings.RasterFilterPreset(kind=kind,strength=4f,colorArgb=0xffff0000.toInt()).effect()
                val output=RasterImageFilter.apply(snapshot,effect,selection.snapshot()).snapshot()
                assertEquals("$effect keeps the layer precision",format,output.pixelFormat)
                for(y in 20 until 60) for(x in 44 until 60) {
                    if(high) assertEquals("$effect high precision unselected $x,$y",snapshot.packedF16At(x,y),output.packedF16At(x,y))
                    else assertEquals("$effect unselected $x,$y",snapshot.pixelAt(x,y),output.pixelAt(x,y))
                }
                val whole=RasterImageFilter.apply(snapshot,effect).snapshot()
                for((x,y) in listOf(100 to 100,120 to 5,5 to 120)) {
                    if(high) assertEquals("$effect transparent surroundings stay empty",0f,PackedRgbaF16.alpha(whole.packedF16At(x,y)),0f)
                    else assertEquals("$effect transparent surroundings stay empty",0,whole.pixelAt(x,y))
                }
                if(high) for(y in 20 until 60 step 7) for(x in 20 until 60 step 7) {
                    val p=whole.linearPixelAt(x,y)
                    assertTrue("$effect finite F16",p.red.isFinite()&&p.green.isFinite()&&p.blue.isFinite()&&p.alpha in 0f..1f)
                }
            }
        }
    }
}
