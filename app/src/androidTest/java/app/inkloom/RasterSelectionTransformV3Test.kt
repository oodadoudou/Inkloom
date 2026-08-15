package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v3.android.PixelTileBitmapAdapter
import app.inkloom.engine.v3.surface.MaskComposeMode
import app.inkloom.engine.v3.surface.MaskTileSurface
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.engine.v3.tools.RasterSelectionTransformSession
import app.inkloom.engine.v3.tools.SelectionAffineTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class RasterSelectionTransformV3Test {
    @Test fun translationPreviewAlwaysResamplesGestureStartAndCancelRestoresPixelsAndMask() {
        val surface = selectedSquareSurface()
        val selection = squareSelection().snapshot()
        val display = PixelTileBitmapAdapter.render(surface.snapshot()).copy(Bitmap.Config.ARGB_8888, true)
        try {
            val session = RasterSelectionTransformSession(surface, display, selection)
            val first = session.update(SelectionAffineTransform(12f, 12f, translateX = 16f))
            assertEquals(Color.TRANSPARENT, surface.pixelAt(10, 10))
            assertEquals(Color.RED, surface.pixelAt(26, 10))
            assertEquals(0, first.selection.alphaAt(10, 10))
            assertEquals(255, first.selection.alphaAt(26, 10))

            val second = session.update(SelectionAffineTransform(12f, 12f, translateX = 24f))
            assertEquals(Color.TRANSPARENT, surface.pixelAt(26, 10))
            assertEquals(Color.RED, surface.pixelAt(34, 10))
            assertEquals(0, second.selection.alphaAt(26, 10))
            assertEquals(255, second.selection.alphaAt(34, 10))

            session.cancel()
            assertEquals(Color.RED, surface.pixelAt(10, 10))
            assertEquals(Color.TRANSPARENT, surface.pixelAt(34, 10))
            assertEquals(Color.RED, display.getPixel(10, 10))
        } finally {
            display.recycle()
        }
    }

    @Test fun scaleRotateAndCommitMoveRasterAndSparseAlphaTogether() {
        val surface = selectedSquareSurface()
        val display = PixelTileBitmapAdapter.render(surface.snapshot()).copy(Bitmap.Config.ARGB_8888, true)
        try {
            val session = RasterSelectionTransformSession(surface, display, squareSelection().snapshot())
            val result = session.update(
                SelectionAffineTransform(centerX = 12f, centerY = 12f, scaleX = 2f, scaleY = 1f, rotationDegrees = 90f),
            )
            session.commit()
            assertTrue(result.dirtyBounds.width <= 24 && result.dirtyBounds.height <= 24)
            assertTrue(result.selection.nonZeroBounds() != null)
            val selected = result.selection.nonZeroBounds()!!
            assertEquals(selected, surface.snapshot().let { pixels ->
                var left = 64; var top = 64; var right = 0; var bottom = 0
                for (y in 0 until 64) for (x in 0 until 64) if (pixels.pixelAt(x, y) ushr 24 != 0) {
                    left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
                }
                PixelRect(left, top, right, bottom)
            })
        } finally {
            display.recycle()
        }
    }

    @Test fun fragmentedTwoKSelectionTransformStaysWithinInteractiveGateWithoutFullMaskBitmap() {
        val size = 2048
        val surface = PixelTileSurface(size, size, tileSize = 128)
        val mask = MaskTileSurface(size, size, tileSize = 128)
        mask.beginWrite("fragmented selection").run {
            for (tile in 0 until 32) {
                val x = (tile * 61) % (size - 16)
                val y = (tile * 97) % (size - 16)
                composeAlpha(PixelRect(x, y, x + 16, y + 16), ByteArray(16 * 16) { 255.toByte() }, mode = MaskComposeMode.ADD)
            }
            commit().close()
        }
        val display = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val elapsedMs = measureNanoTime {
                RasterSelectionTransformSession(surface, display, mask.snapshot()).use { session ->
                    session.update(SelectionAffineTransform(1024f, 1024f, translateX = 12f, translateY = 8f))
                    session.commit()
                }
            } / 1_000_000f
            assertTrue("Fragmented 2K selection transform took ${elapsedMs}ms", elapsedMs < 750f)
        } finally {
            display.recycle()
        }
    }

    private fun selectedSquareSurface(): PixelTileSurface = PixelTileSurface(64, 64, tileSize = 64).also { surface ->
        surface.beginWrite("red selected square").run {
            editTile(PixelTileKey(0, 0), PixelRect(8, 8, 16, 16)) { pixels, stride ->
                for (y in 8 until 16) for (x in 8 until 16) pixels[y * stride + x] = Color.RED
            }
            commit().close()
        }
    }

    private fun squareSelection(): MaskTileSurface = MaskTileSurface(64, 64, tileSize = 64).also { mask ->
        mask.beginWrite("selected square").run {
            composeAlpha(PixelRect(8, 8, 16, 16), ByteArray(8 * 8) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
    }
}
