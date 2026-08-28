package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkDocument
import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.PageGeometry
import app.inkloom.document.PageCrop
import app.inkloom.document.defaultInkLayers
import app.inkloom.engine.v5.surface.LinearRgba
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileSurface
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

    @Test fun incrementalDirtyTileWritebackStaysInsideTheBackgroundIoGate() {
        val surface = PixelTileSurface(2048, 2048, tileSize = 128, surfaceId = "writeback-gate")
        surface.beginWrite("seed 64 sparse tiles").run {
            repeat(64) { index ->
                setPixel((index % 8) * 128 + 4, (index / 8) * 128 + 4, Color.rgb(index * 3, index * 5, index * 7))
            }
            commit().close()
        }
        var reference = assets.saveRasterTileSurface(surface)
        val elapsed = ArrayList<Long>(24)
        repeat(24) { index ->
            surface.beginWrite("dirty tile $index").run {
                setPixel(8 + index, 8, Color.rgb(20 + index, 80, 160))
                commit().close()
            }
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            val next = assets.saveRasterTileSurface(surface, reference)
            elapsed += android.os.SystemClock.elapsedRealtimeNanos() - started
            assets.releaseRasterTileReservation(reference)
            reference = next
        }
        val sorted = elapsed.drop(4).sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "incremental_tile_writeback_p95_ms=$p95Ms")
        assertTrue("Incremental dirty-tile writeback p95 was ${p95Ms}ms", p95Ms < 100f)
        assertEquals(Color.rgb(43, 80, 160), assets.loadRasterTileSurface(reference).pixelAt(31, 8))
        assets.releaseRasterTileReservation(reference)
    }

    @Test fun linearF16RasterReferenceSurvivesDocumentBundleWithoutLosingItsFormat() {
        val surface = PixelTileSurface(
            logicalWidth = 128,
            logicalHeight = 128,
            tileSize = 64,
            surfaceId = "bundle-hdr",
            pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
        ).also {
            it.beginWrite("HDR bundle fixture").run {
                setLinearPixel(7, 9, LinearRgba(3f, .25f, -.1f, .5f))
                commit().close()
            }
        }
        val reference = assets.saveRasterTileSurface(surface)
        assets.releaseRasterTileReservation(reference)
        val document = InkDocument(
            title = "HDR",
            pages = listOf(InkPage(layers = defaultInkLayers() + InkLayer(
                name = "HDR paint",
                rasterTileSurface = reference,
            ))),
        )

        val bundle = ByteArrayOutputStream().also { assets.exportBundle(document, it) }.toByteArray()
        val restoredDocument = assets.importBundle(ByteArrayInputStream(bundle))
        val restoredReference = requireNotNull(
            restoredDocument.pages.single().layers.last().rasterTileSurface,
        )
        val restored = assets.loadRasterTileSurface(restoredReference)

        assertEquals(PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16.manifestId, reference.pixelFormat)
        assertEquals(reference.pixelFormat, restoredReference.pixelFormat)
        assertEquals(PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16, restored.pixelFormat)
        assertEquals(1.5f, restored.linearPixelAt(7, 9).red, .002f)
        assets.releaseRasterTileReservation(restoredReference)
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
            restored.pages.single().layers.first { it.name == "Sparse pixels" }.rasterTileSurface,
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
        val reference = assets.importRasterTileSurface(source)
        source.recycle()
        val layer = InkLayer(id = "crop-raster", name = "Paint", rasterTileSurface = reference)
        val page = InkPage(width = 100f, height = 100f, crop = PageCrop(.25f, .25f, .75f, .75f), layers = defaultInkLayers() + layer)
        val visible = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val merged = assets.composeVisibleRasterTileSurface(page, layer.id, visible)
        try {
            assertTrue("Outside the active crop is preserved", Color.red(merged.pixelAt(20, 20)) > 240)
            assertTrue("The edited crop is replaced", Color.blue(merged.pixelAt(merged.logicalWidth / 2, merged.logicalHeight / 2)) > 240)
        } finally {
            visible.recycle()
            assets.releaseRasterTileReservation(reference)
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

    @Test fun dirtyPatchUpdatesOnlyItsMappedAreaInsideTheVisibleCrop() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val reference = assets.importRasterTileSurface(source)
        source.recycle()
        val layer = InkLayer(
            id = "patch-raster",
            name = "Patch raster",
            rasterTileSurface = reference,
        )
        val page = InkPage(
            width = 100f,
            height = 100f,
            crop = PageCrop(.25f, .25f, .75f, .75f),
            layers = defaultInkLayers() + layer,
        )
        val patch = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val edited = assets.composeVisibleRasterTilePatch(
            page = page,
            layerId = layer.id,
            patch = patch,
            patchLeft = 80,
            patchTop = 80,
            visibleSourceWidth = 200,
            visibleSourceHeight = 200,
        )
        patch.recycle()

        assertEquals("Pixels outside the crop remain untouched", Color.RED, edited.pixelAt(10, 10))
        assertEquals("Pixels inside the crop but outside the patch remain untouched", Color.RED, edited.pixelAt(30, 30))
        assertEquals("The dirty patch maps through the visible crop", Color.GREEN, edited.pixelAt(50, 50))
        assertEquals("The patch must not clear adjacent pixels", Color.RED, edited.pixelAt(60, 60))
        assets.releaseRasterTileReservation(reference)
    }

    @Test fun dirtyPatchCoordinatesFollowTheLayersNonDestructiveRotation() {
        val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val reference = assets.importRasterTileSurface(source)
        source.recycle()
        val layer = InkLayer(
            id = "rotated-patch-raster",
            name = "Rotated patch raster",
            rasterTileSurface = reference,
            rasterRotationQuarterTurns = 1,
        )
        val page = InkPage(width = 80f, height = 40f, layers = defaultInkLayers() + layer)
        val patch = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val edited = assets.composeVisibleRasterTilePatch(
            page = page,
            layerId = layer.id,
            patch = patch,
            patchLeft = 10,
            patchTop = 20,
            visibleSourceWidth = 40,
            visibleSourceHeight = 80,
        )
        patch.recycle()

        assertEquals("The rotated presentation patch maps back into document orientation", Color.GREEN, edited.pixelAt(25, 25))
        assertEquals("Adjacent document pixels are retained", Color.RED, edited.pixelAt(15, 25))
        assets.releaseRasterTileReservation(reference)
    }
}
