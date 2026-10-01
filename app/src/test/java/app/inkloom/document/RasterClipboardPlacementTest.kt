package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class RasterClipboardPlacementTest {
    @Test fun untransformedViewPlacesPayloadAtCenterWithoutScreenOrigin() {
        assertEquals(400 to 700, RasterClipboardPlacement.atViewCenter(400f, 800f, 1000, 2000, 200, 600, ViewportState(1f, 0f, 0f)))
    }
    @Test fun pannedZoomedAndRotatedPlacementUsesSameInverseAsInput() {
        listOf(ViewportState(2f, 100f, -160f), ViewportState(2f, 100f, -160f, 90f),
            ViewportState(.75f, -50f, 60f, 37f)).forEach { viewport ->
            val position = RasterClipboardPlacement.atViewCenter(400f, 800f, 1000, 2000, 200, 100, viewport)
            val visibleCenter = ViewportTransform.pageToView((position.first + 100) / 2.5f,
                (position.second + 50) / 2.5f, 400f, 800f, viewport)
            assertEquals(200f, visibleCenter.first, 1.5f)
            assertEquals(400f, visibleCenter.second, 1.5f)
        }
    }
    @Test fun offPageViewportAndOversizePayloadStillIntersectPage() {
        assertEquals(-100 to -300, RasterClipboardPlacement.atViewCenter(400f, 800f, 1000, 2000, 200, 600, ViewportState(1f, 5000f, 5000f)))
        assertEquals(-1000 to -1000, RasterClipboardPlacement.atViewCenter(400f, 800f, 1000, 2000, 3000, 4000, ViewportState(1f, 0f, 0f)))
    }
}
