package app.inkloom

import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import app.inkloom.engine.v5.tools.RasterImageFilter
import org.junit.Assert.*
import org.junit.Test

class FilteredVisibleCopyTest {
    @Test fun reducedPreviewMatchesTheNativeCommittedFilterAndBoundsBothDimensions() {
        val assets=DocumentAssetService(InstrumentationRegistry.getInstrumentation().targetContext)
        val pixels=PixelTileSurface(2048,128)
        pixels.beginWrite("narrow ink").use { write ->
            for(y in 32..96) for(x in 1020..1027) write.setPixel(x,y,0xff0033cc.toInt())
            write.commit().close()
        }
        val ref=assets.saveRasterTileSurface(pixels)
        val page=InkPage(width=2048f,height=128f,layers=listOf(InkLayer(name="Ink",rasterTileSurface=ref)))
        val effect=RasterImageFilter.Effect.MotionBlur(12f)
        try {
            val preview=assets.renderFilteredVisibleCopyPreview(page,effect,null,256,{false})
            try {
                assets.prepareFilteredVisibleCopy(page,effect,null,{false}).use { transaction ->
                    val committed=assets.loadRasterTileSurface(transaction.edit.after.layers.last().rasterTileSurface!!).snapshot()
                    val expected=app.inkloom.engine.v5.android.PixelTileBitmapAdapter.render(
                        app.inkloom.engine.v5.tools.PixelSurfaceResampling.resample(committed,256,16,.125f,.125f).snapshot())
                    try {
                        val actualPixels=IntArray(256*16);val expectedPixels=IntArray(256*16)
                        preview.getPixels(actualPixels,0,256,0,0,256,16)
                        expected.getPixels(expectedPixels,0,256,0,0,256,16)
                        assertArrayEquals(expectedPixels,actualPixels)
                        assertTrue(actualPixels.any { it ushr 24>0 })
                    } finally { expected.recycle() }
                }
            } finally { preview.recycle() }
            val tall=assets.renderFilteredVisibleCopyPreview(InkPage(width=32f,height=2048f),effect,null,256,{false})
            try { assertEquals(4,tall.width);assertEquals(256,tall.height) } finally { tall.recycle() }
            assertTrue(runCatching { assets.renderFilteredVisibleCopyPreview(page,effect,null,256,{true}) }.exceptionOrNull() is java.util.concurrent.CancellationException)
        } finally { assets.releaseRasterTileReservation(ref) }
    }

    @Test fun groupedMaskedVisibleCopyIsNewLayerAndOneReversiblePageEdit(){
        val assets=DocumentAssetService(InstrumentationRegistry.getInstrumentation().targetContext)
        val pixels=PixelTileSurface(64,64);pixels.beginWrite("test").use{for(y in 15..45)it.setPixel(31,y,0xffff0000.toInt());it.commit().close()}
        val ref=assets.saveRasterTileSurface(pixels)
        val mask=MaskTileSurface(64,64,initialDefaultAlpha=128);val m=assets.saveMaskTileSurface(mask.snapshot())
        val group=InkLayer(id="group",name="Group",role=LayerRole.FOLDER,opacity=.5f,mask=LayerMask(surface=m))
        val layer=InkLayer(name="Ink",parentId=group.id,rasterTileSurface=ref)
        val page=InkPage(width=64f,height=64f,layers=listOf(group,layer),elements=listOf(PageElement.Text(layerId=layer.id,text="Text",x=.1f,y=.1f,width=15f,height=10f,textSize=8f)))
        try{assets.prepareFilteredVisibleCopy(page,RasterImageFilter.Effect.MotionBlur(4f),null,{false}).use{prepared->
            val result=prepared.edit.after;assertEquals(page.layers,result.layers.dropLast(1));assertEquals(page.elements,result.elements)
            val surface=assets.loadRasterTileSurface(result.layers.last().rasterTileSurface!!)
            assertTrue(surface.pixelAt(33,30) ushr 24>0);assertTrue(surface.pixelAt(31,30) ushr 24<128)
            assertEquals(page,prepared.edit.apply(result,true));assertEquals(result,prepared.edit.apply(page,false))
            assertNull(prepared.edit.apply(page.copy(title="Changed"),false))
        }}finally{assets.releaseRasterTileReservation(ref);assets.releaseAssetReservation(m.assetPath)}
    }
}
