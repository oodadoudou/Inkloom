package app.inkloom.document

import app.inkloom.color.PageColorManagement
import app.inkloom.engine.v5.storage.PixelTileArchiveStore
import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class CrossNotebookAssetLifecycleTest {
    @Test fun sharedAssetSurvivesMoveTrashAndDeletingOnlyOneOwner(){
        val root=Files.createTempDirectory("asset-cross-note").toFile()
        try{
            val assets=File(root,"assets").apply{mkdirs()};val image=File(assets,"shared.image").apply{writeBytes(byteArrayOf(1,2));setLastModified(0)}
            val icc=File(assets,"profile.icc").apply{writeBytes(byteArrayOf(3));setLastModified(0)}
            val page=InkPage(background=PageBackground.Image(image.path),colorManagement=PageColorManagement(proofProfile=icc.path),elements=listOf(PageElement.Image(assetPath=image.path,x=0f,y=0f,width=10f,height=10f)))
            val source=InkDocument(title="Source",pages=listOf(page));val target=InkDocument(title="Target")
            val moved=PageTransfer.apply(source,target,page.id,copy=false)!!
            val copy=moved.target.copy(id="another",trashedAt=1)
            assertEquals(0,DocumentAssetGarbageCollector.collect(assets,listOf(moved.source,moved.target,copy),PageTemplateLibrary()))
            assertEquals(0,DocumentAssetGarbageCollector.collect(assets,listOf(copy),PageTemplateLibrary()))
            assertTrue(image.exists());assertTrue(icc.exists())
            assertEquals(2,DocumentAssetGarbageCollector.collect(assets,emptyList(),PageTemplateLibrary()))
        }finally{root.deleteRecursively()}
    }
    @Test fun concurrentContentAddressedReservationsAreReferenceCounted(){
        val root=Files.createTempDirectory("asset-reservations").toFile()
        try{
            val lifecycle=DocumentAssetLifecycle(root,PixelTileArchiveStore(File(root,"pixel-tiles")))
            val a=lifecycle.saveBytes(byteArrayOf(1,2,3),"mask");val b=lifecycle.saveBytes(byteArrayOf(1,2,3),"mask");assertEquals(a,b)
            File(a).setLastModified(0)
            lifecycle.releaseAssetReservation(a)
            assertEquals(0,lifecycle.garbageCollect(emptyList(),PageTemplateLibrary()));assertTrue(File(a).exists())
            lifecycle.releaseAssetReservation(b)
            assertEquals(1,lifecycle.garbageCollect(emptyList(),PageTemplateLibrary()));assertFalse(File(a).exists())
        }finally{root.deleteRecursively()}
    }
}
