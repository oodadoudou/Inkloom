package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v3.android.PixelTileBitmapAdapter
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.LinearRgba
import app.inkloom.engine.v3.surface.PixelTileFormat
import app.inkloom.engine.v3.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelTileBitmapAdapterTest {
    @Test fun linearExtendedF16RenderAndImportPreserveHdrValuesAndPixelFormat() {
        val surface = PixelTileSurface(
            logicalWidth = 64,
            logicalHeight = 64,
            tileSize = 64,
            pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
        )
        surface.beginWrite("HDR Android projection").run {
            setLinearPixel(12, 23, LinearRgba(2.25f, -.125f, .5f, .5f))
            commit().close()
        }

        val rendered = PixelTileBitmapAdapter.render(surface.snapshot())
        val imported = try {
            assertEquals(Bitmap.Config.RGBA_F16, rendered.config)
            assertEquals(
                ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB),
                rendered.colorSpace,
            )
            val displayed = rendered.getColor(12, 23)
            assertEquals(2.25f, displayed.red(), .01f)
            assertEquals(-.125f, displayed.green(), .01f)
            assertEquals(.5f, displayed.alpha(), .002f)
            PixelTileBitmapAdapter.import(rendered, tileSize = 64)
        } finally {
            rendered.recycle()
        }

        assertEquals(PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16, imported.pixelFormat)
        assertEquals(1.125f, imported.linearPixelAt(12, 23).red, .01f)
        assertEquals(-.0625f, imported.linearPixelAt(12, 23).green, .01f)
        assertEquals(.5f, imported.linearPixelAt(12, 23).alpha, .002f)
    }

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
