package app.inkloom

import android.graphics.*
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.color.*
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import app.inkloom.engine.v5.tools.PixelSurfaceResampling
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.zip.*

class ColorManagementTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    // Generated with CreateFakeCMYK in Little CMS 2.19 MIT testbed. Synthetic validation fixture,
    // never a supplied printer profile or a production color-space approximation.
    private fun cmyk()=InstrumentationRegistry.getInstrumentation().context.assets.open("synthetic-cmyk.icc").use{it.readBytes()}
    @Test fun p3ConversionPreservesExtendedRgbAndAlphaBeforeOutput() {
        assertEquals(1,NativeColorManagement.profileKind(NativeColorManagement.builtinProfile(2)))
        val input=floatArrayOf(1f,0f,0f,.37f);val linear=FloatArray(4)
        IccTransform(sourceBuiltin=2).use{it.rgb(input,linear)}
        assertTrue(linear[0]>1f);assertTrue(linear[1]<0f);assertEquals(.37f,linear[3],.0001f)
        val output=FloatArray(4);IccTransform(destinationBuiltin=2).use{it.rgb(linear,output)}
        assertEquals(1f,output[0],.001f);assertEquals(0f,output[1],.001f)
        assertEquals(.37f,output[3],.0001f)
        assertEquals(0,NativeColorManagement.profileKind(ByteArray(128)))
    }
    @Test fun assignedProfileAndProofChangeOnlyPreviewNotSource() {
        val file=File(context.cacheDir,"test-p3.icc").apply{writeBytes(NativeColorManagement.builtinProfile(2))}
        val source=PixelTileSurface(32,32,pixelFormat=PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
        source.beginWrite("red").use{it.setLinearPixel(1,1,LinearRgba(1f,0f,0f,1f));it.commit().close()}
        val before=source.snapshot();val revision=source.revision
        try {
            IccPreviewCache(PageColorManagement(assignedProfile=file.path)).use { cache->
                val preview=cache.update(before);assertTrue(preview.linearPixelAt(1,1).red>1f)
                assertEquals(source.revision,revision);assertEquals(before.packedF16At(1,1),source.snapshot().packedF16At(1,1))
                assertEquals(preview.revision,cache.update(before).revision)
            }
            val proof=File(context.cacheDir,"proof-fixture.icc").apply{writeBytes(cmyk())}
            try { IccPreviewCache(PageColorManagement(proofProfile=proof.path,softProof=true)).use { cache->
                assertNotEquals(before.packedF16At(1,1),cache.update(before).packedF16At(1,1))
                assertEquals(revision,source.revision)
            } } finally{proof.delete()}
        }finally{file.delete()}
    }
    @Test fun highPrecisionResizeAndCheckpointRemainExtended() {
        val source=PixelTileSurface(32,32,pixelFormat=PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
        source.beginWrite("extended").use{it.setLinearPixel(5,6,LinearRgba(1.4f,-.1f,.5f,.5f));it.commit().close()}
        val projected=PixelSurfaceResampling.project(source.snapshot(),32,32,1).snapshot()
        val c=projected.linearPixelAt(25,5);assertTrue(c.red>.69f);assertTrue(c.green<0)
        val assets=DocumentAssetService(context);val ref=assets.saveRasterTileSurface(source)
        try {
            val page=InkPage(width=32f,height=32f,colorManagement=PageColorManagement(highPrecision=true),layers=listOf(InkLayer(name="Ink",rasterTileSurface=ref)))
            assertTrue(assets.renderPageCompositeSurface(page,32,32).linearPixelAt(5,6).red>.69f)
            assets.prepareCanvasSize(page,CanvasSizeRequest(64f,64f,300f,mode=CanvasResizeMode.RESAMPLE)).use{
                val result=assets.loadRasterTileSurface(it.edit.after.layers.single().rasterTileSurface!!)
                assertTrue(result.snapshot().linearPixelAt(10,12).green<0)
                assertEquals(page,it.edit.apply(it.edit.after,true))
            }
        }finally{assets.releaseRasterTileReservation(ref)}
    }
    @Test fun profileIsEmbeddedAndPortableInNativeBundle() {
        val assets=DocumentAssetService(context)
        val file=File(context.cacheDir,"p3-input.icc").apply{writeBytes(NativeColorManagement.builtinProfile(2))}
        val path=assets.importColorProfile(Uri.fromFile(file),true)
        val page=InkPage(width=32f,height=32f,colorManagement=PageColorManagement(outputProfile=path),layers=listOf(InkLayer(name="Ink")))
        val png=File(context.cacheDir,"profile-output.png")
        try {
            assets.exportPagePng(page,Uri.fromFile(png),PageRasterExportOptions(includeBackground=false))
            DataInputStream(png.inputStream()).use{input->input.skipBytes(8);var profile:ByteArray?=null
                while(true){val n=input.readInt();val name=ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII);val data=ByteArray(n).also(input::readFully);input.readInt()
                    if(name=="iCCP"){val start=data.indexOf(0.toByte())+2;profile=InflaterInputStream(ByteArrayInputStream(data,start,data.size-start)).readBytes()};if(name=="IEND")break}
                assertArrayEquals(File(path).readBytes(),profile)
            }
            val bytes=ByteArrayOutputStream();assets.exportBundle(InkDocument(title="ICC",pages=listOf(page)),bytes)
            val restored=assets.importBundle(ByteArrayInputStream(bytes.toByteArray()))
            val restoredProfile=restored.pages.single().colorManagement.outputProfile!!
            assertTrue(File(restoredProfile).exists());assertArrayEquals(File(path).readBytes(),File(restoredProfile).readBytes())
        }finally{file.delete();png.delete()}
    }
    @Test fun cmykTiffContainsSeparatedChannelsAndExactPrinterProfile() {
        val profile=cmyk();assertEquals(2,NativeColorManagement.profileKind(profile))
        val input=floatArrayOf(0f,0f,0f,1f,1f,1f,1f,1f);val converted=ByteArray(8)
        IccTransform(destination=profile,cmyk=true).use{it.cmyk(input,converted)}
        assertTrue(converted.take(4).any{it.toInt().and(255)>0});assertTrue(converted.drop(4).all{it.toInt().and(255)<5})
        val bytes=ByteArrayOutputStream();TiffRasterCodec.writeCmyk(bytes,2,1,300f,profile){_,row->converted.copyInto(row)}
        val data=bytes.toByteArray();val inputFile=File(context.cacheDir,"cmyk-test.tif").apply{writeBytes(data)}
        try{RandomAccessFile(inputFile,"r").use{f->f.seek(8);val n=f.readUnsignedShort();var embedded=false
            repeat(n){val id=f.readUnsignedShort();val type=f.readUnsignedShort();val count=f.readInt();val value=f.readInt()
                if(id==262)assertEquals(5,value ushr 16)
                if(id==34675){val resume=f.filePointer;assertEquals(7,type);assertEquals(profile.size,count);f.seek(value.toLong());val got=ByteArray(count);f.readFully(got);assertArrayEquals(profile,got);f.seek(resume);embedded=true}}
            assertTrue(embedded)
        }}finally{inputFile.delete()}
    }
}
