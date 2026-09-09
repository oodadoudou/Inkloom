package app.inkloom

import app.inkloom.engine.v5.surface.*
import app.inkloom.engine.v5.tools.*
import org.junit.Assert.*
import org.junit.Test

class SelectionPerspectiveRasterTest {
    @Test fun identityPerspectiveDoesNotDarkenSoftSelectedPixels() {
        val surface=PixelTileSurface(16,16)
        surface.beginWrite("source").use { it.setPixel(5,5,0x80402010.toInt());it.commit().close() }
        val mask=MaskTileSurface(16,16)
        mask.beginWrite("soft").use { it.replaceAlpha(PixelRect(4,4,8,8),ByteArray(16) { 128.toByte() });it.commit().close() }
        RasterSelectionTransformSession(surface,mask.snapshot()).use { session ->
            val result=session.update(SelectionProjectiveMapping(4f,4f,4f,4f,listOf(4f to 4f,8f to 4f,8f to 8f,4f to 8f)),SelectionInterpolation.BILINEAR)
            assertEquals(0x80402010.toInt(),surface.pixelAt(5,5))
            assertEquals(128,result.selection.alphaAt(5,5))
        }
    }

    @Test fun perspectiveSamplesSelectedColorAndMaskTogetherAndCancelsExactly() {
        for (format in PixelTileFormat.entries) for (filter in SelectionInterpolation.entries) {
            val surface=PixelTileSurface(64,64,pixelFormat=format)
            val bounds=PixelRect(8,8,32,32)
            surface.beginWrite("source").use { t ->
                if (format.isHighPrecision) for (y in 8..31) for (x in 8..31) t.setLinearPixel(x,y,LinearRgba(1.5f,-.25f,.5f,1f))
                else t.replacePremultipliedPixels(bounds,IntArray(24*24) { 0xffff0000.toInt() })
                t.commit().close()
            }
            val mask=MaskTileSurface(64,64)
            mask.beginWrite("select").use { it.replaceAlpha(bounds,ByteArray(24*24) { 255.toByte() });it.commit().close() }
            val before=surface.snapshot()
            val mapping=SelectionProjectiveMapping(8f,8f,24f,24f,listOf(32f to 6f,58f to 12f,54f to 49f,35f to 35f))
            val originalMask=mask.snapshot()
            val reference=SelectionPixelSampler(before,originalMask,filter)
            val m=mapping.inverseCoefficients()
            val session=RasterSelectionTransformSession(surface,originalMask)
            val result=session.update(mapping,filter)
            val actual=surface.snapshot()
            for (y in 0..63) for (x in 0..63) {
                val px=x+.5f;val py=y+.5f;val w=m[6]*px+m[7]*py+m[8]
                val sx=(m[0]*px+m[1]*py+m[4])/w;val sy=(m[2]*px+m[3]*py+m[5])/w
                assertEquals("alpha $filter $x,$y",reference.maskAlpha(sx,sy),result.selection.alphaAt(x,y))
                if (format.isHighPrecision) assertEquals("F16 $filter $x,$y",reference.selectedF16(sx,sy),actual.packedF16At(x,y))
                else assertEquals("8888 $filter $x,$y",reference.selectedArgb(sx,sy),actual.pixelAt(x,y))
            }
            session.cancel()
            if (format.isHighPrecision) assertArrayEquals(before.copyPremultipliedF16(PixelRect(0,0,64,64)),surface.snapshot().copyPremultipliedF16(PixelRect(0,0,64,64)))
            else assertArrayEquals(before.copyPremultipliedPixels(PixelRect(0,0,64,64)),surface.snapshot().copyPremultipliedPixels(PixelRect(0,0,64,64)))
        }
    }
}
