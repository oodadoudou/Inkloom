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
}
