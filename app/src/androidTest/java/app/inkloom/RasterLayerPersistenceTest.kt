package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkDocument
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.LayerContentKind
import app.inkloom.document.PageGeometry
import app.inkloom.document.defaultInkLayers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterLayerPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val assets = DocumentAssetService(context)

    @Test fun rasterSurfaceIsContentAddressedRenderedRotatedAndBundled() {
        val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            source.setPixel(x, y, if (x < source.width / 2) Color.RED else Color.BLUE)
        }
        val firstPath = assets.saveRasterLayer(source)
        val secondPath = assets.saveRasterLayer(source)
        source.recycle()
        try {
            assertEquals("Identical raster content must reuse one immutable asset", firstPath, secondPath)
            val raster = InkLayer(
                id = "raster",
                name = "Paint",
                contentKind = LayerContentKind.RASTER,
                rasterAssetPath = firstPath,
            )
            val page = InkPage(width = 40f, height = 20f, layers = defaultInkLayers() + raster)
            val rendered = assets.renderPageBitmap(page, 400)
            try {
                assertTrue(Color.red(rendered.getPixel(80, 100)) > 240)
                assertTrue(Color.blue(rendered.getPixel(320, 100)) > 240)
            } finally {
                rendered.recycle()
            }

            val rotated = assets.renderPageBitmap(PageGeometry.rotateClockwise(page), 200)
            try {
                assertTrue("Clockwise raster rotation must move the red half to the top", Color.red(rotated.getPixel(100, 80)) > 240)
                assertTrue("Clockwise raster rotation must move the blue half to the bottom", Color.blue(rotated.getPixel(100, 320)) > 240)
            } finally {
                rotated.recycle()
            }

            val bundle = ByteArrayOutputStream().also { output ->
                assets.exportBundle(InkDocument(title = "Raster", pages = listOf(page)), output)
            }.toByteArray()
            val restored = assets.importBundle(ByteArrayInputStream(bundle))
            val restoredPath = restored.pages.single().layers.first { it.contentKind == LayerContentKind.RASTER }.rasterAssetPath
            assertTrue(File(requireNotNull(restoredPath)).isFile)
            val restoredBitmap = assets.renderPageBitmap(restored.pages.single(), 400)
            try {
                assertTrue(Color.red(restoredBitmap.getPixel(80, 100)) > 240)
                assertTrue(Color.blue(restoredBitmap.getPixel(320, 100)) > 240)
            } finally {
                restoredBitmap.recycle()
            }
        } finally {
            assets.releaseAssetReservation(firstPath)
            assets.releaseAssetReservation(secondPath)
        }
    }
}
