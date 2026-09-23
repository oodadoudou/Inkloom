package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverCropGeometryTest {
    @Test fun landscapePhotoStartsWithCenteredPortraitCrop() {
        val crop = CoverCropGeometry.rect(1600, 900, 1f, .5f, .5f)
        assertTrue(crop.left > 0f)
        assertEquals(0f, crop.top, 0.0001f)
        assertEquals(1f, crop.bottom, 0.0001f)
        assertEquals(CoverCropGeometry.ASPECT, (crop.right - crop.left) * 1600f / 900f, .001f)
    }

    @Test fun dragAndZoomStayWithinImageBounds() {
        val cropped = CoverCropGeometry.rect(900, 1600, 2f, .5f, .5f)
        val (x, y) = CoverCropGeometry.drag(cropped, -1000f, 1000f, 230f, 300f)
        val moved = CoverCropGeometry.rect(900, 1600, 2f, x, y)
        assertEquals(1f, moved.right, .0001f)
        assertEquals(0f, moved.top, .0001f)
        assertTrue(moved.left >= 0f && moved.bottom <= 1f)
    }
}
