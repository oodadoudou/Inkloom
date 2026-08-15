package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v3.android.PixelTileCanvasRenderer
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelTileCanvasRendererTest {
    @Test fun drawsSparseTilesIntoScaledDestinationWithoutMaterializingThePage() {
        val surface = PixelTileSurface(1024, 768, tileSize = 128)
        surface.beginWrite("two sparse regions").use { write ->
            write.editTile(PixelTileKey(1, 2), PixelRect(0, 0, 128, 128)) { pixels, _ ->
                pixels.fill(premultiplied(Color.RED))
            }
            write.editTile(PixelTileKey(6, 4), PixelRect(0, 0, 128, 128)) { pixels, _ ->
                pixels.fill(premultiplied(Color.BLUE))
            }
            write.commit().close()
        }
        val output = Bitmap.createBitmap(512, 384, Bitmap.Config.ARGB_8888)
        PixelTileCanvasRenderer(maximumCacheBytes = 2L * 128 * 128 * 4).use { renderer ->
            renderer.draw(Canvas(output), surface.snapshot(), RectF(0f, 0f, 512f, 384f))
            assertEquals(Color.RED, output.getPixel(96, 160))
            assertEquals(Color.BLUE, output.getPixel(416, 288))
            assertEquals(Color.TRANSPARENT, output.getPixel(16, 16))
            assertEquals("Only allocated sparse tiles may enter the Android cache", 2, renderer.retainedTileCount)
        }
        output.recycle()
    }

    @Test fun changedGenerationReplacesOnlyItsCachedTile() {
        val surface = PixelTileSurface(256, 128, tileSize = 128)
        surface.beginWrite("initial").use { write ->
            write.editTile(PixelTileKey(0, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.RED)) }
            write.editTile(PixelTileKey(1, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.BLUE)) }
            write.commit().close()
        }
        val renderer = PixelTileCanvasRenderer(maximumCacheBytes = 4L * 128 * 128 * 4)
        val first = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(first), surface.snapshot(), RectF(0f, 0f, 256f, 128f))
        surface.beginWrite("change one tile").use { write ->
            write.editTile(PixelTileKey(0, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.GREEN)) }
            write.commit().close()
        }
        val second = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(second), surface.snapshot(), RectF(0f, 0f, 256f, 128f))

        assertEquals(Color.GREEN, second.getPixel(64, 64))
        assertEquals(Color.BLUE, second.getPixel(192, 64))
        assertEquals("The obsolete generation is recycled instead of accumulating", 2, renderer.retainedTileCount)
        assertTrue(renderer.retainedByteSize <= 2L * 128 * 128 * 4)
        first.recycle(); second.recycle(); renderer.close()
    }

    private fun premultiplied(color: Int): Int {
        val alpha = Color.alpha(color)
        return Color.argb(
            alpha,
            (Color.red(color) * alpha + 127) / 255,
            (Color.green(color) * alpha + 127) / 255,
            (Color.blue(color) * alpha + 127) / 255,
        )
    }
}
