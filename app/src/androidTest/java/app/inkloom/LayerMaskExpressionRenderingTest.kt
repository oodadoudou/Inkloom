package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.*
import org.junit.Test

class LayerMaskExpressionRenderingTest {
    @Test fun layerAndGroupThresholdMatchLiveExportAndMaskBaking() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = DocumentAssetService(instrumentation.targetContext)
        val pixels = PixelTileSurface(64, 64, 64)
        pixels.beginWrite("black").use { write ->
            write.editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { tile, _ -> tile.fill(Color.BLACK) }
            write.commit().close()
        }
        val rasterRef = assets.saveRasterTileSurface(pixels)
        val soft = MaskTileSurface(64, 64, 64, initialDefaultAlpha = 255)
        soft.beginWrite("soft halves").use { write ->
            for (y in 0 until 64) for (x in 0 until 64) write.setAlpha(x, y, if (x < 32) 80 else 180)
            write.commit().close()
        }
        val maskRef = assets.saveMaskTileSurface(soft.snapshot())
        val sourceMask = LayerMask(surface = maskRef, showGradients = false, expressionThreshold = 128)
        val bakedRefs = mutableListOf<RasterTileSurfaceRef>()
        try {
            for (groupMask in listOf(false, true)) {
                val group = InkLayer(id = "group", name = "Group", role = LayerRole.FOLDER,
                    mask = sourceMask.takeIf { groupMask })
                val paint = InkLayer(id = "paint", name = "Paint", parentId = group.id,
                    rasterTileSurface = rasterRef, mask = sourceMask.takeUnless { groupMask })
                val page = InkPage(width = 64f, height = 64f,
                    background = PageBackground.Paper(0xffffffffL), layers = listOf(group, paint))
                val exported = assets.renderPageBitmap(page, 64)
                try {
                    assertEquals(Color.WHITE, exported.getPixel(16, 32))
                    assertEquals(Color.BLACK, exported.getPixel(48, 32))
                    instrumentation.runOnMainSync {
                        val view = InkCanvasView(instrumentation.targetContext).apply {
                            layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                            setDocumentLayers(page.layers)
                            setRasterLayerSurfaces(mapOf(paint.id to pixels))
                            layerOrder = listOf(paint.id)
                            layerMasks = mapOf((if (groupMask) group.id else paint.id) to sourceMask)
                            layerMaskSurfaces = mapOf((if (groupMask) group.id else paint.id) to soft.snapshot())
                        }
                        val live = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                        try {
                            view.draw(Canvas(live))
                            assertEquals(exported.getPixel(16, 32), live.getPixel(16, 32))
                            assertEquals(exported.getPixel(48, 32), live.getPixel(48, 32))
                        } finally { live.recycle() }
                    }
                    val target = if (groupMask) group.id else paint.id
                    val plan = requireNotNull(RasterLayerMerge.plan(page,
                        RasterLayerMergeRequest(RasterLayerMergeOperation.APPLY_MASK, target)).plan)
                    val bakedRef = assets.saveRasterTileSurface(assets.renderLayerMergeSurface(plan))
                    bakedRefs += bakedRef
                    val baked = RasterLayerMerge.edit(page, plan, bakedRef).apply(page, false)
                    val bakedImage = assets.renderPageBitmap(baked, 64)
                    try {
                        assertEquals(exported.getPixel(16, 32), bakedImage.getPixel(16, 32))
                        assertEquals(exported.getPixel(48, 32), bakedImage.getPixel(48, 32))
                    } finally { bakedImage.recycle() }
                } finally { exported.recycle() }
            }
            assertEquals(80, soft.alphaAt(16, 32))
            assertEquals(180, soft.alphaAt(48, 32))
        } finally {
            bakedRefs.forEach(assets::releaseRasterTileReservation)
            assets.releaseRasterTileReservation(rasterRef)
            assets.releaseAssetReservation(maskRef.assetPath)
        }
    }
}
