package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.LayerMask
import app.inkloom.document.LayerMaskRegion
import app.inkloom.document.LayerMaskRenderer
import app.inkloom.document.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerMaskProjectionTest {
    @Test fun dirtyClipRendersTheSameMaskInsideClipAndDoesNotTouchOutside() {
        val mask = LayerMask(
            baseVisible = false,
            regions = listOf(
                LayerMaskRegion(
                    points = listOf(
                        StrokePoint(.1f, .1f), StrokePoint(.9f, .1f),
                        StrokePoint(.9f, .9f), StrokePoint(.1f, .9f),
                    ),
                    reveal = true,
                ),
            ),
        )
        val full = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        LayerMaskRenderer.draw(Canvas(full), mask, 400, 400)
        val projected = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val canvas = Canvas(projected)
        canvas.clipRect(190, 120, 270, 280)
        LayerMaskRenderer.draw(canvas, mask, 400, 400)

        assertEquals("Projection must match the complete mask inside the dirty clip", full.getPixel(220, 200), projected.getPixel(220, 200))
        assertEquals("Pixels outside the dirty clip are not part of this projection", Color.MAGENTA, projected.getPixel(100, 200))
        full.recycle(); projected.recycle()
    }

    @Test fun disabledMaskProjectsOpaqueAlphaOnlyIntoDirtyClip() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipRect(50, 20, 100, 80)
        LayerMaskRenderer.draw(canvas, LayerMask(enabled = false), 200, 100)

        assertEquals(Color.WHITE, bitmap.getPixel(75, 50))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(25, 50))
    }
}
