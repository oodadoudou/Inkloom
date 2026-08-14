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
import app.inkloom.document.PageCrop
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
            val liveSurface = assets.renderRasterLayerBitmap(page, raster.id, 400)
            try {
                assertEquals(400, liveSurface.width)
                assertEquals(200, liveSurface.height)
                assertTrue(Color.red(liveSurface.getPixel(80, 100)) > 240)
                assertEquals("Live raster sources stay transparent outside their own pixels", 255, Color.alpha(liveSurface.getPixel(320, 100)))
            } finally {
                liveSurface.recycle()
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

    @Test fun sparseRasterSurfaceIsVerifiedReopenedAndGarbageCollectedByManifestReachability() {
        val firstBitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply {
            setPixel(17, 19, Color.MAGENTA)
        }
        val secondBitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply {
            setPixel(21, 23, Color.CYAN)
        }
        val retained = assets.importRasterTileSurface(firstBitmap)
        val orphan = assets.importRasterTileSurface(secondBitmap)
        firstBitmap.recycle()
        secondBitmap.recycle()
        assets.releaseRasterTileReservation(retained)
        assets.releaseRasterTileReservation(orphan)

        val layer = InkLayer(
            name = "Sparse pixels",
            contentKind = LayerContentKind.RASTER,
            rasterTileSurface = retained,
        )
        val document = InkDocument(title = "Sparse", pages = listOf(InkPage(layers = defaultInkLayers() + layer)))
        val reopened = assets.loadRasterTileSurface(retained)

        assertEquals(Color.MAGENTA, reopened.pixelAt(17, 19))
        assertTrue(File(retained.manifestAssetPath).isFile)
        assertTrue(File(orphan.manifestAssetPath).isFile)
        val bundle = ByteArrayOutputStream().also { assets.exportBundle(document, it) }.toByteArray()
        val restored = assets.importBundle(ByteArrayInputStream(bundle))
        val restoredReference = requireNotNull(
            restored.pages.single().layers.first { it.contentKind == LayerContentKind.RASTER }.rasterTileSurface,
        )
        assertEquals(Color.MAGENTA, assets.loadRasterTileSurface(restoredReference).pixelAt(17, 19))
        assets.releaseRasterTileReservation(restoredReference)
        assertTrue(assets.garbageCollectUnreferencedAssets(listOf(document)) >= 2)
        assertTrue(File(retained.manifestAssetPath).isFile)
        assertTrue(!File(orphan.manifestAssetPath).exists())

        assertTrue(assets.garbageCollectUnreferencedAssets(emptyList()) >= 2)
        assertTrue(!File(retained.manifestAssetPath).exists())
    }

    @Test fun editedVisibleCropIsMergedWithoutDestroyingPixelsOutsideTheCrop() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val path = assets.saveRasterLayer(source)
        source.recycle()
        val layer = InkLayer(id = "crop-raster", name = "Paint", contentKind = LayerContentKind.RASTER, rasterAssetPath = path)
        val page = InkPage(width = 100f, height = 100f, crop = PageCrop(.25f, .25f, .75f, .75f), layers = defaultInkLayers() + layer)
        val visible = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val merged = assets.composeVisibleRasterLayer(page, layer.id, visible)
        try {
            assertTrue("Outside the active crop is preserved", Color.red(merged.getPixel(20, 20)) > 240)
            assertTrue("The edited crop is replaced", Color.blue(merged.getPixel(merged.width / 2, merged.height / 2)) > 240)
        } finally {
            merged.recycle()
            visible.recycle()
            assets.releaseAssetReservation(path)
        }
    }

    @Test fun sparseRasterRendersRotatesAndReplacesOnlyTheVisibleCrop() {
        val source = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            source.setPixel(x, y, if (x < 50) Color.RED else Color.BLUE)
        }
        val reference = assets.importRasterTileSurface(source)
        source.recycle()
        val layer = InkLayer(
            id = "sparse-render",
            name = "Sparse Render",
            contentKind = LayerContentKind.RASTER,
            rasterTileSurface = reference,
        )
        val page = InkPage(width = 100f, height = 60f, layers = defaultInkLayers() + layer)
        val rendered = assets.renderPageBitmap(page, 500)
        val rotated = assets.renderPageBitmap(PageGeometry.rotateClockwise(page), 300)
        try {
            assertTrue(Color.red(rendered.getPixel(80, 150)) > 240)
            assertTrue(Color.blue(rendered.getPixel(420, 150)) > 240)
            assertTrue(Color.red(rotated.getPixel(150, 80)) > 240)
            assertTrue(Color.blue(rotated.getPixel(150, 420)) > 240)
        } finally {
            rendered.recycle()
            rotated.recycle()
        }

        val croppedPage = page.copy(crop = PageCrop(.25f, .25f, .75f, .75f))
        val replacement = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val editedSurface = assets.composeVisibleRasterTileSurface(croppedPage, layer.id, replacement)
        replacement.recycle()
        assertTrue(Color.red(editedSurface.pixelAt(10, 10)) > 240)
        assertTrue(Color.green(editedSurface.pixelAt(40, 30)) > 240)
        assertTrue(Color.blue(editedSurface.pixelAt(90, 50)) > 240)
        assets.releaseRasterTileReservation(reference)
    }
}
