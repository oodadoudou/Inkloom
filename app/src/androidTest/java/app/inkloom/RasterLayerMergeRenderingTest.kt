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
            val ref = assets.saveRasterTileSurface(assets.renderMergedLayerPair(page, upper.id)).also { references += it }
            val edit = RasterLayerMerge.edit(page, upper.id, ref)
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
