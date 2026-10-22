package app.inkloom

import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import app.inkloom.ui.prepareFitCanvasToSelection
import org.junit.Assert.*
import org.junit.Test

/** Fit canvas to selection and the paper colour of the basic canvas settings, at the document level. */
class CanvasCommandsAssetTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun fitCanvasToSelectionCropsToTheSelectedAreaAsOneEdit() {
        val assets = DocumentAssetService(context)
        // Inkloom's smallest canvas side is 32 px, so the selected half is 128 × 64.
        val source = PixelTileSurface(256, 128)
        source.beginWrite("ink").use { it.setPixel(160, 40, Color.RED); it.setPixel(5, 5, Color.BLUE); it.commit().close() }
        val ref = assets.saveRasterTileSurface(source)
        val page = InkPage(width = 256f, height = 128f, layers = listOf(InkLayer(id = "ink", name = "Ink", rasterTileSurface = ref)))
        try {
            prepareFitCanvasToSelection(assets, page, android.graphics.RectF(.5f, 0f, 1f, .5f)).use { prepared ->
                val after = prepared.edit.after
                assertEquals(128f, after.width); assertEquals(64f, after.height)
                val pixels = assets.loadRasterTileSurface(requireNotNull(after.layers.single().rasterTileSurface))
                assertEquals("The selected ink keeps its place inside the new canvas", Color.RED, pixels.pixelAt(32, 40))
                assertEquals("Ink outside the selection is cropped away", 0, (0 until 128).sumOf { x -> (0 until 64).count { y -> pixels.pixelAt(x, y) == Color.BLUE } })
                assertEquals(page, prepared.edit.apply(after, true))
            }
        } finally { assets.releaseRasterTileReservation(ref) }
    }

    @Test fun basicSettingsChangeOnlyThePaperColour() {
        val assets = DocumentAssetService(context)
        val page = InkPage(width = 64f, height = 32f, background = PageBackground.Paper(colorArgb = PaperColorCatalog.WHITE))
        assets.prepareCanvasSize(page, CanvasSizeRequest(64f, 32f, page.dpi, paperColorArgb = PaperColorCatalog.SOFT_BLUE)).use { prepared ->
            assertEquals(PaperColorCatalog.SOFT_BLUE, (prepared.edit.after.background as PageBackground.Paper).colorArgb)
            assertEquals(page.layers, prepared.edit.after.layers)
            assertEquals(page, prepared.edit.apply(prepared.edit.after, true))
        }
    }
}
