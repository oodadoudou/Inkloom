package app.inkloom

import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class RasterFormatRoundTripTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun layeredFormatsRetainGroupsPixelsOpacityAndVisibility() {
        val assets=DocumentAssetService(context); val pixels=PixelTileSurface(64,64)
        pixels.beginWrite("fixture").use { write -> for(y in 10 until 30) for(x in 10 until 30) write.setPixel(x,y,Color.RED); write.commit().close() }
        val ref=assets.saveRasterTileSurface(pixels)
        val group=InkLayer(name="漫画組",role=LayerRole.FOLDER,opacity=.6f)
        val layer=InkLayer(name="线稿",parentId=group.id,rasterTileSurface=ref,blendMode=LayerBlendMode.MULTIPLY,visible=true)
        val page=InkPage(width=64f,height=64f,layers=listOf(layer,group),dpi=144f)
        try { for(format in listOf(RasterFileFormat.OPEN_RASTER,RasterFileFormat.PSD,RasterFileFormat.LAYERED_PNG)) {
            val file=File(context.cacheDir,"stack-roundtrip.${format.extension}")
            try {
                assets.exportPageRaster(page,Uri.fromFile(file),PageRasterExportOptions(64,64,false,format))
                val imported=assets.importArtwork(Uri.fromFile(file),file.name).pages.single()
                val folder=imported.layers.single { it.role==LayerRole.FOLDER }; val child=imported.layers.single { it.role!=LayerRole.FOLDER }
                assertEquals(group.name,folder.name); assertEquals(group.opacity,folder.opacity,.005f)
                assertEquals(folder.id,child.parentId); assertEquals(layer.name,child.name); assertEquals(layer.blendMode,child.blendMode)
                assertEquals(Color.RED,assets.loadRasterTileSurface(requireNotNull(child.rasterTileSurface)).pixelAt(20,20))
                assertEquals(assets.renderPageCompositeSurface(page,64,64).pixelAt(20,20),assets.renderPageCompositeSurface(imported,64,64).pixelAt(20,20))
            } finally { file.delete() }
        } } finally { assets.releaseRasterTileReservation(ref) }
    }
    @Test fun tiffAndJpegUseRealEncodingAndAlphaContract() {
        val assets=DocumentAssetService(context); val shape=PageElement.Shape(x=0f,y=0f,width=64f,height=64f,layerId="ink",kind=ShapeKind.RECTANGLE,fillArgb=0xff336699,strokeWidth=0f)
        val page=InkPage(width=64f,height=64f,layers=listOf(InkLayer(id="ink",name="Ink")),elements=listOf(shape))
        for(format in listOf(RasterFileFormat.TIFF,RasterFileFormat.JPEG)) {
            val file=File(context.cacheDir,"flat-test.${format.extension}")
            try {
                assets.exportPageRaster(page,Uri.fromFile(file),PageRasterExportOptions(64,64,false,format))
                if(format==RasterFileFormat.TIFF) assertEquals(0xff336699.toInt(),TiffRasterCodec.read(file).single().pixels[32*64+32])
                else { val image=android.graphics.BitmapFactory.decodeFile(file.path); try { assertEquals(255,image.getPixel(32,32) ushr 24) } finally { image.recycle() } }
            } finally { file.delete() }
        }
    }
    @Test fun groupMaskAndClippedPixelsRoundTripInPsdAndLayeredPng() {
        val assets=DocumentAssetService(context); val pixels=PixelTileSurface(64,64)
        pixels.beginWrite("fixture").use { write -> for(y in 10 until 30) for(x in 10 until 30) write.setPixel(x,y,Color.RED); write.commit().close() }
        val ref=assets.saveRasterTileSurface(pixels); val mask=MaskTileSurface(64,64,initialDefaultAlpha=255)
        mask.beginWrite("hole").use { it.setAlpha(20,20,0); it.commit().close() }; val maskRef=assets.saveMaskTileSurface(mask.snapshot())
        val group=InkLayer(name="Group",role=LayerRole.FOLDER,mask=LayerMask(surface=maskRef))
        val layer=InkLayer(name="Ink",rasterTileSurface=ref,parentId=group.id)
        val page=InkPage(width=64f,height=64f,layers=listOf(layer,group))
        try { for(format in listOf(RasterFileFormat.PSD,RasterFileFormat.LAYERED_PNG)) {
            val file=File(context.cacheDir,"mask-roundtrip.${format.extension}")
            try { assets.exportPageRaster(page,Uri.fromFile(file),PageRasterExportOptions(64,64,false,format))
                val restored=assets.importArtwork(Uri.fromFile(file),file.name).pages.single()
                assertEquals(0,assets.renderPageCompositeSurface(restored,64,64).pixelAt(20,20))
                assertEquals(Color.RED,assets.renderPageCompositeSurface(restored,64,64).pixelAt(15,15))
                assertNotNull(restored.layers.first { it.role==LayerRole.FOLDER }.mask)
            } finally { file.delete() }
        } } finally { assets.releaseRasterTileReservation(ref); assets.releaseAssetReservation(maskRef.assetPath) }
    }
}
