package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v3.android.PixelTileBitmapAdapter
import app.inkloom.engine.v3.surface.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelTileBitmapAdapterTest {
    @Test fun transparentSparseImportAndCropRenderRoundTripWithoutFullPageMaterialization() {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(150, 100, Color.argb(128, 200, 100, 50))

        val surface = PixelTileBitmapAdapter.import(bitmap)
        val rendered = PixelTileBitmapAdapter.render(
            surface.snapshot(),
            source = PixelRect(100, 50, 200, 150),
            targetWidth = 200,
            targetHeight = 200,
        )

        try {
            assertEquals(1, surface.tileCount)
            assertEquals(200, rendered.width)
            assertEquals(200, rendered.height)
            val restored = rendered.getPixel(101, 101)
            assertTrue(kotlin.math.abs(128 - Color.alpha(restored)) <= 1)
            assertTrue(kotlin.math.abs(200 - Color.red(restored)) <= 2)
            assertTrue(kotlin.math.abs(100 - Color.green(restored)) <= 2)
            assertTrue(kotlin.math.abs(50 - Color.blue(restored)) <= 2)
            assertTrue(Color.alpha(rendered.getPixel(0, 0)) == 0)
        } finally {
            bitmap.recycle()
            rendered.recycle()
        }
    }

    @Test fun replacingRotatedRegionWritesAndClearsTheCorrectDocumentPixels() {
        val surface = app.inkloom.engine.v3.surface.PixelTileSurface(80, 40)
        surface.beginWrite("existing pixel").run {
            setPixel(0, 39, Color.RED)
            commit().close()
        }
        val replacement = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
            setPixel(0, 0, Color.TRANSPARENT)
        }

        PixelTileBitmapAdapter.replaceRotatedRegion(
            surface,
            rotatedRegion = PixelRect(0, 0, 40, 20),
            rotationQuarterTurns = 1,
            bitmap = replacement,
        )
        val rendered = PixelTileBitmapAdapter.render(surface.snapshot())

        try {
            assertEquals(Color.BLUE, rendered.getPixel(1, 39))
            assertEquals(Color.BLUE, rendered.getPixel(19, 0))
            assertTrue(Color.alpha(rendered.getPixel(0, 39)) == 0)
            assertTrue(Color.alpha(rendered.getPixel(79, 39)) == 0)
        } finally {
            replacement.recycle()
            rendered.recycle()
        }
    }
}
