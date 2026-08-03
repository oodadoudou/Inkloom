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
        assertEquals(8f, high.scale, .001f)
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

    @Test fun boundedPanAlwaysLeavesPartOfThePageRecoverable() {
        val geometry = ViewportGeometry(viewportWidth = 1200f, viewportHeight = 800f, pageWidth = 600f, pageHeight = 760f)
        val after = ViewportTransform.panBy(ViewportState(3f, 0f, 0f), 20_000f, -20_000f, geometry = geometry)

        assertEquals(1230f, after.panX, .001f)
        assertEquals(-1198f, after.panY, .001f)
    }

    @Test fun minimumZoomRecentersEvenAfterExtremePan() {
        val geometry = ViewportGeometry(viewportWidth = 1200f, viewportHeight = 800f, pageWidth = 600f, pageHeight = 760f)
        val after = ViewportTransform.zoomAround(ViewportState(2f, 900f, -700f), .01f, 200f, 100f, geometry = geometry)

        assertEquals(ViewportState(1f, 0f, 0f), after)
    }

    @Test fun panningUsesTheRotatedCanvasAxes() {
        val after = ViewportTransform.panBy(ViewportState(2f, 0f, 0f, 90f), 20f, 5f)

        assertEquals(-10f, after.panX, .001f)
        assertEquals(40f, after.panY, .001f)
    }

    @Test fun zoomKeepsTheRotatedFocusPointStationary() {
        val before = ViewportState(1.5f, 30f, -12f, 90f)
        val focusX = 160f
        val focusY = -40f
        val after = ViewportTransform.zoomAround(before, 2f, focusX, focusY)

        assertEquals(before.panX + before.scale * 40f, after.panX + after.scale * 40f, .001f)
        assertEquals(before.panY + before.scale * 160f, after.panY + after.scale * 160f, .001f)
        assertEquals(90f, after.rotationDegrees, .001f)
    }

    @Test fun rotationNormalizesAcrossRepeatedToolboxInput() {
        assertEquals(-170f, ViewportTransform.rotateBy(ViewportState(2f, 0f, 0f, 170f), 20f).rotationDegrees, .001f)
        assertEquals(170f, ViewportTransform.rotateBy(ViewportState(2f, 0f, 0f, -170f), -20f).rotationDegrees, .001f)
    }

    @Test fun actualSizeMapsOneDocumentPixelToOneCanvasPixel() {
        val after = ViewportTransform.actualSize(ViewportState(1.6f, 200f, -100f, 15f), pageModelWidth = 1600f, pageViewWidth = 1000f)

        assertEquals(1.6f, after.scale, .001f)
        assertEquals(0f, after.panX, .001f)
        assertEquals(0f, after.panY, .001f)
        assertEquals(15f, after.rotationDegrees, .001f)
    }

    @Test fun fitWidthUsesTheAvailableViewportAndCentersThePage() {
        val after = ViewportTransform.fitWidth(ViewportState(3f, 500f, -300f), viewportWidth = 1200f, pageViewWidth = 600f)

        assertEquals(1.88f, after.scale, .001f)
        assertEquals(0f, after.panX, .001f)
        assertEquals(0f, after.panY, .001f)
    }

    @Test fun resetRotationKeepsTheCurrentZoomRecoverable() {
        val geometry = ViewportGeometry(viewportWidth = 1200f, viewportHeight = 800f, pageWidth = 600f, pageHeight = 760f)
        val after = ViewportTransform.resetRotation(ViewportState(2f, 900f, -700f, 91f), geometry)

        assertEquals(2f, after.scale, .001f)
        assertEquals(0f, after.rotationDegrees, .001f)
        assertEquals(900f, after.panX, .001f)
        assertEquals(-700f, after.panY, .001f)
    }

    @Test fun invalidGestureValuesCanNeverPoisonTheViewport() {
        val poisoned = ViewportState(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN)
        val zoomed = ViewportTransform.zoomAround(poisoned, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN)
        val panned = ViewportTransform.panBy(poisoned, Float.NaN, Float.POSITIVE_INFINITY, maximumPan = Float.NaN)

        assertEquals(ViewportState(1f, 0f, 0f, 0f), zoomed)
        assertEquals(ViewportState(1f, 0f, 0f, 0f), panned)
    }

    @Test fun rotatedCanvasCanPanAtMinimumZoomToReachClippedCorners() {
        val geometry = ViewportGeometry(viewportWidth = 900f, viewportHeight = 700f, pageWidth = 600f, pageHeight = 850f)
        val after = ViewportTransform.panBy(ViewportState(1f, 0f, 0f, 90f), 24f, 12f, geometry = geometry)

        assertEquals(-12f, after.panX, .001f)
        assertEquals(24f, after.panY, .001f)
    }

    @Test fun repeatedExtremePinchesAlwaysReturnToCenteredFit() {
        val geometry = ViewportGeometry(viewportWidth = 1200f, viewportHeight = 800f, pageWidth = 600f, pageHeight = 760f)
        var state = ViewportState(1f, 0f, 0f)
        repeat(20) { state = ViewportTransform.zoomAround(state, 1.7f, 480f, -260f, geometry = geometry) }
        state = ViewportTransform.panBy(state, 100_000f, -100_000f, geometry = geometry)
        repeat(20) { state = ViewportTransform.zoomAround(state, .4f, -520f, 310f, geometry = geometry) }

        assertEquals(ViewportState(1f, 0f, 0f), state)
    }
}
