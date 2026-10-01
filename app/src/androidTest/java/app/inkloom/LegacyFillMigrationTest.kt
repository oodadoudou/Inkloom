package app.inkloom

import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test

class LegacyFillMigrationTest {
    private val assets = DocumentAssetService(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun migrationPreservesRenderedPixelsAcrossMaskTileBoundaries() {
        val mask = MaskTileSurface(128, 192, 64)
        mask.beginWrite("legacy soft boundary").use { write ->
            val alpha = ByteArray(128 * 192) { i -> if (i % 128 < 80) 255.toByte() else 128.toByte() }
            write.composeAlpha(PixelRect(0, 0, 128, 192), alpha, mode = MaskComposeMode.REPLACE)
            write.commit().close()
        }
        val path = assets.saveFillMask(mask.snapshot())
        val fill = PageElement.Fill(x = 0f, y = 0f, width = 128f, height = 192f,
            maskAssetPath = path, colorArgb = 0xFF224477)
        val original = InkPage(width = 128f, height = 192f, elements = listOf(fill))
        var migrated: InkDocument? = null
        assertTrue(LegacyFillMigration(assets).migrate(InkDocument(title = "Tile boundary", pages = listOf(original))) {
            migrated = it
        } is LegacyFillMigration.Result.Executed)
        val before = assets.renderPageBitmap(original, 128)
        val after = assets.renderPageBitmap(requireNotNull(migrated).pages.single(), 128)
        try {
            for (y in 0 until 192) for (x in 0 until 128) {
                val expected = before.getPixel(x, y); val actual = after.getPixel(x, y)
                for (shift in listOf(0, 8, 16, 24)) assertTrue("Pixel ($x,$y), channel $shift changed",
                    kotlin.math.abs((expected ushr shift and 255) - (actual ushr shift and 255)) <= 1)
            }
        } finally { before.recycle(); after.recycle(); assets.releaseAssetReservation(path) }
    }

    @Test fun sameElementIdOnDifferentPagesDoesNotReuseTheWrongMask() {
        val paths = listOf(64, 255).map { alpha -> assets.saveFillMask(MaskTileSurface(8, 8, initialDefaultAlpha = alpha).snapshot()) }
        val pages = paths.map { path -> InkPage(width = 8f, height = 8f, elements = listOf(
            PageElement.Fill(id = "shared-old-id", x = 0f, y = 0f, width = 8f, height = 8f, maskAssetPath = path))) }
        var migrated: InkDocument? = null
        assertTrue(LegacyFillMigration(assets).migrate(InkDocument(title = "Repeated identifiers", pages = pages)) { migrated = it }
            is LegacyFillMigration.Result.Executed)
        val alpha = requireNotNull(migrated).pages.map { page ->
            val ref = requireNotNull(page.layers.first { it.id == LayerIds.INK }.rasterTileSurface)
            assets.loadRasterTileSurface(ref).snapshot().pixelAt(4, 4) ushr 24
        }
        assertEquals(listOf(64, 255), alpha)
        paths.forEach(assets::releaseAssetReservation)
    }

    @Test fun migrationKeepsNativePrecisionCropRotationAndHiddenLayerProperties() {
        for (turns in 0..3) {
            val surface = PixelTileSurface(if (turns % 2 == 0) 64 else 80,
                if (turns % 2 == 0) 80 else 64, 64,
                pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
            surface.beginWrite("HDR outside crop").use { write ->
                write.setLinearPixel(0, 0, LinearRgba(2f, -.2f, .3f, .8f))
                write.commit().close()
            }
            val before = surface.snapshot().copyPremultipliedF16(PixelRect(0, 0, 1, 1)).single()
            val reference = assets.saveRasterTileSurface(surface)
            val maskPath = assets.saveFillMask(MaskTileSurface(8, 10, initialDefaultAlpha = 255).snapshot())
            val layer = InkLayer(id = "paint", name = "Hidden HDR", rasterTileSurface = reference,
                rasterRotationQuarterTurns = turns, visible = false, locked = true,
                opacity = .4f, blendMode = LayerBlendMode.MULTIPLY, clippedToBelow = true)
            val fill = PageElement.Fill(x = .25f, y = .25f, width = 16f, height = 20f,
                layerId = layer.id, colorArgb = 0xFFFF0000, maskAssetPath = maskPath)
            val page = InkPage(width = 64f, height = 80f, crop = PageCrop(.1f, .1f, .9f, .9f),
                layers = listOf(layer), elements = listOf(fill))
            val document = InkDocument(title = "Native fill migration", pages = listOf(page))
            var saved: InkDocument? = null
            val result = LegacyFillMigration(assets).migrate(document) { saved = it }
            assertTrue(result.toString(), result is LegacyFillMigration.Result.Executed)
            val migratedPage = requireNotNull(saved).pages.single()
            val migratedLayer = migratedPage.layers.single()
            assertEquals(layer.copy(rasterTileSurface = migratedLayer.rasterTileSurface), migratedLayer)
            assertEquals(page.crop, migratedPage.crop)
            assertTrue(migratedPage.elements.isEmpty())
            val pixels = assets.loadRasterTileSurface(requireNotNull(migratedLayer.rasterTileSurface)).snapshot()
            assertEquals(before, pixels.copyPremultipliedF16(PixelRect(0, 0, 1, 1)).single())
            val point = when (turns) { 1 -> 30 to 39; 2 -> 39 to 49; 3 -> 49 to 24; else -> 24 to 30 }
            assertEquals(0xFFFF0000.toInt(), pixels.pixelAt(point.first, point.second))
            assertEquals(LegacyFillMigration.Result.Unchanged,
                LegacyFillMigration(assets).migrate(requireNotNull(saved)) { fail("Migration must be idempotent") })
            assets.releaseRasterTileReservation(reference); assets.releaseAssetReservation(maskPath)
        }
    }

    @Test fun damagedMaskRejectsTheWholeDocumentBeforeAnySave() {
        val path = assets.saveFillMask(MaskTileSurface(4, 4, initialDefaultAlpha = 255).snapshot())
        val valid = PageElement.Fill(x = 0f, y = 0f, width = 8f, height = 8f, maskAssetPath = path)
        val invalid = valid.copy(id = "missing", maskAssetPath = "/missing/fill-mask.v5")
        val document = InkDocument(title = "Invalid mask", pages = listOf(InkPage(elements = listOf(valid)), InkPage(elements = listOf(invalid))))
        val result = LegacyFillMigration(assets).migrate(document) { fail("Invalid document must not be saved") }
        assertTrue(result is LegacyFillMigration.Result.Rejected)
        assertTrue((result as LegacyFillMigration.Result.Rejected).reason.contains("missing"))
        assertEquals(2, document.pages.flatMap { it.elements }.size)
        assets.releaseAssetReservation(path)
    }

    @Test fun retainedObjectsKeepTheirOrderOrReceiveAnExplicitRejection() {
        val path = assets.saveFillMask(MaskTileSurface(4, 4, initialDefaultAlpha = 255).snapshot())
        val fill = PageElement.Fill(x = 0f, y = 0f, width = 8f, height = 8f, maskAssetPath = path)
        val image = PageElement.Image(x = 0f, y = 0f, width = 8f, height = 8f, assetPath = "unchanged-image", layerId = fill.layerId)
        val page = InkPage(width = 16f, height = 16f, elements = listOf(fill, image))
        var saved: InkDocument? = null
        assertTrue(LegacyFillMigration(assets).migrate(InkDocument(title = "Ordered", pages = listOf(page))) { saved = it } is LegacyFillMigration.Result.Executed)
        assertEquals(listOf(image), requireNotNull(saved).pages.single().elements)
        val result = LegacyFillMigration(assets).migrate(InkDocument(title = "Mixed", pages = listOf(page.copy(elements = listOf(image, fill))))) { saved=it }
        assertTrue(result is LegacyFillMigration.Result.Executed)
        val elements=saved!!.pages.single().elements
        assertEquals(image,elements[0]);assertEquals(fill.id,elements[1].id)
        assertTrue(elements[1] is PageElement.Image)
        assertEquals(fill.opacity,elements[1].opacity);assertEquals(fill.rotationDegrees,elements[1].rotationDegrees)
        assets.releaseAssetReservation(path)
    }

    @Test fun interleavedFillAndObjectAlphaLockKeepPixelsAndIdentity() {
        val path=assets.saveFillMask(MaskTileSurface(64,64,initialDefaultAlpha=128).snapshot())
        val shape=PageElement.Shape(x=.2f,y=.2f,width=32f,height=32f,fillArgb=0xff00ff00,strokeWidth=0f)
        val fill=PageElement.Fill(id="alpha-identity",x=0f,y=0f,width=64f,height=64f,opacity=.6f,colorArgb=0x99ff3300,maskAssetPath=path)
        val layer=InkLayer(id=fill.layerId,name="Locked",alphaLock=true,alphaLockBaseContentIds=listOf(shape.id,fill.id))
        val page=InkPage(width=64f,height=64f,layers=listOf(layer),elements=listOf(shape,fill))
        var migrated:InkDocument?=null
        try {
            assertTrue(LegacyFillMigration(assets).migrate(InkDocument(title="Interleaved",pages=listOf(page))) { migrated=it } is LegacyFillMigration.Result.Executed)
            val afterPage=migrated!!.pages.single()
            assertEquals(page.layers,afterPage.layers);assertEquals(page.elements.map{it.id},afterPage.elements.map{it.id})
            assertEquals(shape,afterPage.elements.first());assertTrue(afterPage.elements.last() is PageElement.Image)
            val before=assets.renderPageBitmap(page,64);val after=assets.renderPageBitmap(afterPage,64)
            try {
                for(y in 0 until 64)for(x in 0 until 64)for(shift in listOf(0,8,16,24))
                    assertTrue("Pixel ($x,$y) channel $shift",kotlin.math.abs((before.getPixel(x,y) ushr shift and 255)-(after.getPixel(x,y) ushr shift and 255))<=2)
            }finally{before.recycle();after.recycle()}
        }finally{assets.releaseAssetReservation(path)}
    }

    @Test fun failedCheckpointLeavesTheOriginalReferenceAndFillUntouched() {
        val maskPath = assets.saveFillMask(MaskTileSurface(4, 4, initialDefaultAlpha = 255).snapshot())
        val fill = PageElement.Fill(x = 0f, y = 0f, width = 8f, height = 8f, maskAssetPath = maskPath)
        val document = InkDocument(title = "Failed checkpoint", pages = listOf(InkPage(width = 16f, height = 16f, elements = listOf(fill))))
        val result = LegacyFillMigration(assets).migrate(document) { throw java.io.IOException("Storage unavailable") }
        assertEquals(LegacyFillMigration.Result.Rejected("Storage unavailable"), result)
        assertEquals(listOf(fill), document.pages.single().elements)
        assertTrue(document.pages.single().layers.all { it.rasterTileSurface == null })
        assets.releaseAssetReservation(maskPath)
    }
}
