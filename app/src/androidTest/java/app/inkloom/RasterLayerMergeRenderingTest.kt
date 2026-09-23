package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.MaskTileSurface
import org.junit.Assert.*
import org.junit.Test

class RasterLayerMergeRenderingTest {
    private val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())
    @Test fun translucentMaskedPixelsKeepTheSameCompositeAndUndoRestoresSources() = verify(objects = false, turns = 0, cropped = false)
    @Test fun retainedObjectsAndPixelsAreBakedOnlyOnce() = verify(objects = true, turns = 0, cropped = false)
    @Test fun rotatedCroppedPageKeepsContentOutsideTheCrop() = verify(objects = false, turns = 1, cropped = true)

    @Test fun selectedGroupBlendClippingAndMaskBakeWithoutChangingComposite() {
        val references = mutableListOf<RasterTileSurfaceRef>()
        fun raster(color: Int, left: Int): RasterTileSurfaceRef {
            val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
            for (y in 5..58) for (x in left..minOf(95, left + 55)) bitmap.setPixel(x, y, color)
            return try { assets.importRasterTileSurface(bitmap).also { references += it } } finally { bitmap.recycle() }
        }
        val mask = MaskTileSurface(96, 64, initialDefaultAlpha = 255)
        mask.beginWrite("group mask").use { write ->
            for (y in 0..63) for (x in 70..95) write.setAlpha(x, y, 96)
            write.commit().close()
        }
        val maskRef = assets.saveMaskTileSurface(mask.snapshot())
        val folder = InkLayer(id = "folder", name = "Group", role = LayerRole.FOLDER, opacity = .78f,
            blendMode = LayerBlendMode.MULTIPLY, mask = LayerMask(surface = maskRef))
        val base = InkLayer(id = "base", name = "Base", parentId = folder.id,
            rasterTileSurface = raster(0xccff8844.toInt(), 8))
        val clipped = InkLayer(id = "clipped", name = "Clipped", parentId = folder.id, clippedToBelow = true,
            blendMode = LayerBlendMode.SCREEN, opacity = .7f, rasterTileSurface = raster(0xaa4488ff.toInt(), 38))
        val outside = InkLayer(id = "outside", name = "Outside", opacity = .62f,
            rasterTileSurface = raster(0xbb22aa66.toInt(), 22))
        val page = InkPage(width = 96f, height = 64f, layers = defaultInkLayers() + folder + base + clipped + outside)
        try {
            val plan = requireNotNull(RasterLayerMerge.plan(page, RasterLayerMergeRequest(
                RasterLayerMergeOperation.MERGE_SELECTED, folder.id, setOf(folder.id, outside.id),
            )).plan)
            val ref = assets.saveRasterTileSurface(assets.renderLayerMergeSurface(plan)).also { references += it }
            val edit = RasterLayerMerge.edit(page, plan, ref)
            val result = edit.apply(page, false)
            compare(page, result)
            assertEquals(page, edit.apply(result, true))
        } finally {
            references.forEach(assets::releaseRasterTileReservation)
            assets.releaseAssetReservation(maskRef.assetPath)
        }
    }

    @Test fun mergeVisibleAndFlattenEachPreserveTheDisplayedPage() {
        val references = mutableListOf<RasterTileSurfaceRef>()
        fun raster(color: Int, inset: Int): RasterTileSurfaceRef {
            val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
            for (y in inset until 64 - inset) for (x in inset until 96 - inset) bitmap.setPixel(x, y, color)
            return try { assets.importRasterTileSurface(bitmap).also { references += it } } finally { bitmap.recycle() }
        }
        val lower = InkLayer(id = "lower-visible", name = "Lower", opacity = .72f,
            rasterTileSurface = raster(0xccff3355.toInt(), 4))
        val upper = InkLayer(id = "upper-visible", name = "Upper", blendMode = LayerBlendMode.SCREEN,
            opacity = .61f, rasterTileSurface = raster(0xbb3377ff.toInt(), 18))
        val hidden = InkLayer(id = "hidden", name = "Hidden", visible = false,
            rasterTileSurface = raster(0xffff00ff.toInt(), 28))
        val page = InkPage(width = 96f, height = 64f, background = PageBackground.Paper(0xfff3ead7),
            layers = defaultInkLayers() + lower + upper + hidden)
        try {
            listOf(RasterLayerMergeOperation.MERGE_VISIBLE, RasterLayerMergeOperation.FLATTEN).forEach { operation ->
                val plan = requireNotNull(RasterLayerMerge.plan(page, RasterLayerMergeRequest(operation)).plan)
                val ref = assets.saveRasterTileSurface(assets.renderLayerMergeSurface(plan)).also { references += it }
                val result = RasterLayerMerge.edit(page, plan, ref).apply(page, false)
                compare(page, result)
                if (operation == RasterLayerMergeOperation.MERGE_VISIBLE) assertTrue(result.layers.any { it.id == hidden.id })
            }
        } finally { references.forEach(assets::releaseRasterTileReservation) }
    }

    @Test fun applyingGroupMaskBakesExactlyTheDisplayedSubtreeAndRestoresOnUndo() {
        val references = mutableListOf<RasterTileSurfaceRef>()
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xffe85d75.toInt()) }
        val raster = try { assets.importRasterTileSurface(bitmap).also { references += it } } finally { bitmap.recycle() }
        val maskSurface = MaskTileSurface(96, 64, initialDefaultAlpha = 255)
        maskSurface.beginWrite("soft half").use { write ->
            for (y in 0..63) for (x in 48..95) write.setAlpha(x, y, 90)
            write.commit().close()
        }
        val maskRef = assets.saveMaskTileSurface(maskSurface.snapshot())
        val group = InkLayer(id = "apply-group", name = "Apply group", role = LayerRole.FOLDER,
            opacity = .72f, blendMode = LayerBlendMode.MULTIPLY, mask = LayerMask(surface = maskRef))
        val child = InkLayer(id = "apply-child", name = "Child", parentId = group.id, rasterTileSurface = raster)
        val page = InkPage(width = 96f, height = 64f, layers = defaultInkLayers() + group + child)
        try {
            val plan = requireNotNull(RasterLayerMerge.plan(page,
                RasterLayerMergeRequest(RasterLayerMergeOperation.APPLY_MASK, group.id)).plan)
            val mergedRef = assets.saveRasterTileSurface(assets.renderLayerMergeSurface(plan)).also { references += it }
            val edit = RasterLayerMerge.edit(page, plan, mergedRef)
            val applied = edit.apply(page, false)
            compare(page, applied)
            assertNull(applied.layers.first { it.id == group.id }.mask)
            assertFalse(applied.layers.any { it.id == child.id })
            assertEquals(page, edit.apply(applied, true))
        } finally {
            references.forEach(assets::releaseRasterTileReservation)
            assets.releaseAssetReservation(maskRef.assetPath)
        }
    }

    private fun verify(objects: Boolean, turns: Int, cropped: Boolean) {
        val references = mutableListOf<RasterTileSurfaceRef>()
        fun raster(color: Int, left: Int): RasterTileSurfaceRef {
            val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
            for (y in 6..56) for (x in left..(left + 48)) bitmap.setPixel(x, y, color)
            return try { assets.importRasterTileSurface(bitmap).also { references += it } } finally { bitmap.recycle() }
        }
        val mask = MaskTileSurface(96, 64, initialDefaultAlpha = 255)
        mask.beginWrite("soft mask").use { write -> for (y in 0..63) for (x in 48..95) write.setAlpha(x, y, 96); write.commit().close() }
        val maskRef = assets.saveMaskTileSurface(mask.snapshot())
        val lower = InkLayer(id = "lower", name = "Lower", opacity = .65f, rasterTileSurface = raster(0xaaee2244.toInt(), 5))
        val upper = InkLayer(id = "upper", name = "Upper", opacity = .72f, rasterTileSurface = raster(0xbb2266ee.toInt(), 40), mask = if (turns == 0 && !cropped) LayerMask(surface = maskRef) else null)
        var page = InkPage(width = 96f, height = 64f, layers = defaultInkLayers() + lower + upper,
            elements = if (objects) listOf(PageElement.Shape(id = "shape", layerId = upper.id, x = .1f, y = .1f,
                width = 26f, height = 26f, kind = ShapeKind.RECTANGLE, colorArgb = 0xff00aa22, fillArgb = 0xff00aa22)) else emptyList())
        repeat(turns) { page = PageGeometry.rotateClockwise(page) }
        if (cropped) page = page.copy(crop = PageCrop(.2f, .1f, .85f, .9f))
        try {
            val plan = requireNotNull(RasterLayerMerge.plan(page,
                RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_DOWN, upper.id)).plan)
            val ref = assets.saveRasterTileSurface(assets.renderLayerMergeSurface(plan)).also { references += it }
            val edit = RasterLayerMerge.edit(page, plan, ref)
            val result = edit.apply(page, false)
            assertTrue(result.elements.none { it.layerId == lower.id || it.layerId == upper.id })
            compare(page, result)
            compare(page.copy(crop = PageCrop()), result.copy(crop = PageCrop()))
            assertEquals(page, edit.apply(result, true))
            assertEquals(0, result.layers.last().rasterRotationQuarterTurns)
        } finally { references.forEach(assets::releaseRasterTileReservation); assets.releaseAssetReservation(maskRef.assetPath) }
    }
    private fun compare(before: InkPage, after: InkPage) {
        val a = assets.renderPageBitmap(before, PageCropTransform.visibleWidth(before).toInt()); val b = assets.renderPageBitmap(after, PageCropTransform.visibleWidth(before).toInt())
        try {
            assertEquals(a.width, b.width); assertEquals(a.height, b.height)
            var worst = 0
            for (y in 0 until a.height) for (x in 0 until a.width) {
                val p = a.getPixel(x, y); val q = b.getPixel(x, y)
                for (shift in listOf(0, 8, 16, 24)) worst = maxOf(worst, kotlin.math.abs((p ushr shift and 255) - (q ushr shift and 255)))
            }
            assertTrue("Composite changed by $worst channel levels", worst <= 3)
        } finally { a.recycle(); b.recycle() }
    }
}
