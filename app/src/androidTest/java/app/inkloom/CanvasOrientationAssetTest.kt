package app.inkloom

import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test

/** Canvas rotate/flip rewrite the page so that composited pixels, masks and undo stay consistent. */
class CanvasOrientationAssetTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun rotateThenFlipKeepsMaskedInkAlignedAndEachStepUndoes() {
        val assets = DocumentAssetService(context)
        // 64×32 page: two red dots, the right one hidden by the layer mask.
        val source = PixelTileSurface(64, 32)
        source.beginWrite("ink").use { it.setPixel(10, 5, Color.RED); it.setPixel(50, 5, Color.RED); it.commit().close() }
        val ref = assets.saveRasterTileSurface(source)
        val mask = MaskTileSurface(64, 32, initialDefaultAlpha = 255)
        mask.beginWrite("hide right dot").use { it.setAlpha(50, 5, 0); it.commit().close() }
        val maskRef = assets.saveMaskTileSurface(mask.snapshot())
        val page = InkPage(width = 64f, height = 32f, background = PageBackground.Paper(),
            layers = listOf(InkLayer(id = "ink", name = "Ink", rasterTileSurface = ref, mask = LayerMask(surface = maskRef))))
        fun alphaAt(page: InkPage, x: Int, y: Int) =
            assets.renderPageCompositeSurface(page, page.width.toInt(), page.height.toInt()).pixelAt(x, y) ushr 24
        try {
            assertEquals(255, alphaAt(page, 10, 5)); assertEquals(0, alphaAt(page, 50, 5))
            assets.prepareCanvasOrientation(page, CanvasOrientationChange.ROTATE_CLOCKWISE).use { rotate ->
                val rotated = rotate.edit.after
                assertEquals(32f, rotated.width); assertEquals(64f, rotated.height)
                // Clockwise: (x, y) → (height − 1 − y, x). The mask turns with the ink.
                assertEquals(255, alphaAt(rotated, 26, 10)); assertEquals(0, alphaAt(rotated, 26, 50))
                assets.prepareCanvasOrientation(rotated, CanvasOrientationChange.FLIP_HORIZONTAL).use { flip ->
                    val flipped = flip.edit.after
                    // The paint layer is still stored a quarter turn away, so its other stored axis flipped.
                    assertEquals(1, flipped.layers.single().rasterRotationQuarterTurns)
                    assertEquals(255, alphaAt(flipped, 5, 10)); assertEquals(0, alphaAt(flipped, 26, 10))
                    assertEquals(0, alphaAt(flipped, 5, 50))
                    assertEquals(rotated, flip.edit.apply(flipped, true))
                }
                assertEquals(page, rotate.edit.apply(rotated, true))
            }
            assets.prepareCanvasOrientation(page, CanvasOrientationChange.ROTATE_180).use { half ->
                assertEquals(255, alphaAt(half.edit.after, 53, 26)); assertEquals(0, alphaAt(half.edit.after, 13, 26))
            }
            assets.prepareCanvasOrientation(page, CanvasOrientationChange.FLIP_VERTICAL).use { vertical ->
                assertEquals(255, alphaAt(vertical.edit.after, 10, 26)); assertEquals(0, alphaAt(vertical.edit.after, 50, 26))
            }
        } finally { assets.releaseRasterTileReservation(ref); assets.releaseAssetReservation(maskRef.assetPath) }
    }

    @Test fun pagesWithImportedBackgroundsRotateButRefuseToFlip() {
        val assets = DocumentAssetService(context)
        val page = InkPage(width = 64f, height = 32f, background = PageBackground.Pdf("/missing.pdf", 0))
        assets.prepareCanvasOrientation(page, CanvasOrientationChange.ROTATE_COUNTERCLOCKWISE).use { assertEquals(3, it.edit.after.backgroundRotationQuarterTurns) }
        val failure = runCatching { assets.prepareCanvasOrientation(page, CanvasOrientationChange.FLIP_HORIZONTAL) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("can't be flipped"))
    }
}
