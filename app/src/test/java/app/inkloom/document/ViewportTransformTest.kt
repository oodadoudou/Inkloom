package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportTransformTest {
    @Test fun zoomKeepsFocusedPagePointStationary() {
        val before = ViewportState(scale = 1.25f, panX = 18f, panY = -12f)
        val focusX = 240f
        val focusY = -90f
        val after = ViewportTransform.zoomAround(before, 1.6f, focusX, focusY)

        assertEquals(before.panX + before.scale * focusX, after.panX + after.scale * focusX, .001f)
        assertEquals(before.panY + before.scale * focusY, after.panY + after.scale * focusY, .001f)
    }

    @Test fun scaleStopsAtConfiguredBounds() {
        val high = ViewportTransform.zoomAround(ViewportState(3f, 0f, 0f), 3f, 0f, 0f)
        val low = ViewportTransform.zoomAround(ViewportState(1.6f, 340f, -220f), .1f, 200f, -100f)
        assertEquals(4f, high.scale, .001f)
        assertEquals(1f, low.scale, .001f)
        assertEquals(0f, low.panX, .001f)
        assertEquals(0f, low.panY, .001f)
    }

    @Test fun localPanTracksTheFingerAtEveryZoomLevel() {
        val after = ViewportTransform.panBy(ViewportState(2.5f, 10f, -4f), 12f, -8f)
        assertEquals(40f, after.panX, .001f)
        assertEquals(-24f, after.panY, .001f)
    }

    @Test fun pinchHandoffSelectsTheFingerThatRemainsOnScreen() {
        assertEquals(1, ViewportTransform.remainingPointerIndex(2, 0))
        assertEquals(0, ViewportTransform.remainingPointerIndex(2, 1))
        assertEquals(null, ViewportTransform.remainingPointerIndex(1, 0))
    }
}
