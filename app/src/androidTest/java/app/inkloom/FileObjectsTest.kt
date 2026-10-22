package app.inkloom

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** File objects stay linked to their file: update when it changes, report it when it is gone. */
class FileObjectsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name: String) = File(context.cacheDir, "shared").apply { mkdirs() }.resolve(name)
    private fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    private fun writePng(file: File, width: Int, height: Int, color: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    @Test fun placedObjectsUpdateWhenTheFileChangesAndReportAMissingFile() {
        val assets = DocumentAssetService(context)
        val files = FileObjects(context, assets)
        val source = file("file-object.png").also { writePng(it, 200, 100, Color.RED) }
        val placed = files.place(uri(source))
        assertEquals("file-object.png", placed.source?.name)
        assertEquals(FileObjects.Refresh.Unchanged, files.refresh(placed))
        // The file is edited elsewhere: a different size marks it changed even without a modification time.
        writePng(source, 300, 100, Color.BLUE)
        val updated = files.refresh(placed.copy(width = 400f, height = 200f)) as FileObjects.Refresh.Updated
        assertNotEquals(placed.assetPath, updated.image.assetPath)
        assertEquals("the object keeps its box", 400f, updated.image.width); assertEquals(200f, updated.image.height)
        assertEquals(Color.BLUE, BitmapFactory.decodeFile(updated.image.assetPath).getPixel(5, 5))
        // Replacing with another file keeps the width and follows the new aspect ratio.
        val other = file("other.png").also { writePng(it, 100, 100, Color.GREEN) }
        val replaced = files.replace(updated.image, uri(other))
        assertEquals("other.png", replaced.source?.name); assertEquals(400f, replaced.width); assertEquals(400f, replaced.height, .5f)
        other.delete()
        assertEquals(FileObjects.Refresh.Missing("other.png"), files.refresh(replaced))
        source.delete()
    }

    @Test fun convertingALayerWritesItsPixelsAndLinksTheFile() {
        val assets = DocumentAssetService(context)
        val raster = PixelTileSurface(400, 200).apply { beginWrite("ink").use { it.setPixel(300, 50, Color.RED); it.commit().close() } }
        val ref = assets.saveRasterTileSurface(raster)
        val page = InkPage(width = 400f, height = 200f, layers = defaultInkLayers().map { if (it.id == LayerIds.INK) it.copy(rasterTileSurface = ref) else it })
        val destination = file("layer-object.png").also { it.delete() }
        try {
            FileObjectConversion.prepare(context, assets, page, LayerIds.INK, uri(destination)).use { prepared ->
                val after = prepared.edit.after
                assertNull("the pixels moved into the file", after.layers.first { it.id == LayerIds.INK }.rasterTileSurface)
                val image = after.elements.filterIsInstance<PageElement.Image>().single()
                assertEquals(LayerIds.INK, image.layerId); assertEquals("layer-object.png", image.source?.name)
                assertEquals(400f, image.width); assertEquals(200f, image.height)
                assertEquals(Color.RED, BitmapFactory.decodeFile(destination.path).getPixel(300, 50))
                assertEquals(page, prepared.edit.apply(after, true))
            }
        } finally { destination.delete(); assets.releaseRasterTileReservation(ref) }
    }
}
