package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.SelectionMaskCompositor
import app.inkloom.document.StrokePoint
import app.inkloom.settings.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionMaskCompositorTest {
    @Test fun addSubtractAndIntersectPreserveExactBooleanGeometry() {
        val left = rectangle(.1f, .1f, .6f, .9f)
        val right = rectangle(.4f, .1f, .9f, .9f)
        var combined: Bitmap? = null
        try {
            combined = SelectionMaskCompositor.compose(left, right, SelectionMode.ADD)
            assertSelected(combined, .2f, .5f)
            assertSelected(combined, .8f, .5f)

            val subtracted = SelectionMaskCompositor.compose(combined, right, SelectionMode.SUBTRACT)
            combined?.recycle(); combined = subtracted
            assertSelected(combined, .2f, .5f)
            assertNotSelected(combined, .5f, .5f)
            assertNotSelected(combined, .8f, .5f)

            val intersected = SelectionMaskCompositor.compose(left, right, SelectionMode.INTERSECT)
            combined?.recycle(); combined = intersected
            assertNotSelected(combined, .2f, .5f)
            assertSelected(combined, .5f, .5f)
            assertNotSelected(combined, .8f, .5f)
        } finally {
            left.recycle()
            right.recycle()
            combined?.recycle()
        }
    }

    @Test fun borderCreatesARingAndFeatherRunsAfterUnionWithoutInternalSeam() {
        val left = rectangle(.1f, .2f, .5f, .8f)
        val right = rectangle(.5f, .2f, .9f, .8f)
        val union = SelectionMaskCompositor.compose(left, right, SelectionMode.ADD)
        val feathered = SelectionMaskCompositor.feather(union, 8f, 100f)
        val ring = SelectionMaskCompositor.border(union, 12f, 100f)
        try {
            assertTrue(Color.alpha(pixel(feathered, .5f, .5f)) > 240)
            assertTrue(Color.alpha(pixel(feathered, .08f, .5f)) in 1..254)
            assertSelected(ring, .1f, .5f)
            assertNotSelected(ring, .5f, .5f)
        } finally {
            left.recycle(); right.recycle(); union?.recycle(); feathered?.recycle(); ring?.recycle()
        }
    }

    @Test fun maskDimensionsRejectInvalidPagesAndBoundExtremeAspectRatios() {
        assertNull(SelectionMaskCompositor.maskSize(Float.NaN, 100f))
        assertNull(SelectionMaskCompositor.maskSize(100f, Float.POSITIVE_INFINITY))
        val size = requireNotNull(SelectionMaskCompositor.maskSize(2_000f, 200_000f))
        assertTrue(size.width <= SelectionMaskCompositor.MAX_WIDTH)
        assertTrue(size.height <= SelectionMaskCompositor.MAX_HEIGHT)
        assertTrue(size.width * size.height <= SelectionMaskCompositor.MAX_PIXELS)
    }

    @Test fun invertingAFullMaskProducesAnEmptySelection() {
        val full = requireNotNull(SelectionMaskCompositor.full(1_600f, 2_263f))
        try {
            assertNull(SelectionMaskCompositor.invert(full))
        } finally {
            full.recycle()
        }
    }

    @Test fun croppedFillRegionIsClippedByTheFullPageSelection() {
        val region = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val selection = rectangle(.5f, 0f, 1f, 1f)
        val clipped = SelectionMaskCompositor.clipRegion(region, 20, 20, 100, 100, selection)
        try {
            assertNotSelected(clipped, .1f, .5f)
            assertSelected(clipped, .9f, .5f)
        } finally {
            region.recycle(); selection.recycle(); clipped?.recycle()
        }
    }

    @Test fun boundaryMaskIsAClosedTwoSidedRingWithTransparentInterior() {
        val selection = rectangle(.2f, .2f, .8f, .8f)
        val boundary = SelectionMaskCompositor.boundaryMask(selection)
        try {
            assertSelected(boundary, .2f, .5f)
            assertSelected(boundary, .8f, .5f)
            assertSelected(boundary, .5f, .2f)
            assertSelected(boundary, .5f, .8f)
            assertNotSelected(boundary, .5f, .5f)
            assertNotSelected(boundary, .05f, .05f)
        } finally {
            selection.recycle()
            boundary?.recycle()
        }
    }

    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float): Bitmap =
        requireNotNull(SelectionMaskCompositor.polygon(
            listOf(StrokePoint(left, top), StrokePoint(right, top), StrokePoint(right, bottom), StrokePoint(left, bottom)),
            100f,
            100f,
        ))

    private fun assertSelected(bitmap: Bitmap?, x: Float, y: Float) =
        assertTrue("expected selected at $x,$y", Color.alpha(pixel(bitmap, x, y)) >= 128)

    private fun assertNotSelected(bitmap: Bitmap?, x: Float, y: Float) =
        assertEquals("expected transparent at $x,$y", 0, Color.alpha(pixel(bitmap, x, y)))

    private fun pixel(bitmap: Bitmap?, x: Float, y: Float): Int {
        val value = requireNotNull(bitmap)
        return value.getPixel((x * (value.width - 1)).toInt(), (y * (value.height - 1)).toInt())
    }
}
