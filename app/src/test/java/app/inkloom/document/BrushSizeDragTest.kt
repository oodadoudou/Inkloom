package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class BrushSizeDragTest {
    @Test fun `horizontal drag changes size proportionally`() {
        assertEquals(20f, BrushSizeDrag.resolve(10f, 180f), .001f)
        assertEquals(5f, BrushSizeDrag.resolve(10f, -180f), .001f)
    }

    @Test fun `drag stays inside every brush legal range`() {
        assertEquals(400f, BrushSizeDrag.resolve(300f, 2_000f), .001f)
        assertEquals(.5f, BrushSizeDrag.resolve(1f, -2_000f), .001f)
    }
}
