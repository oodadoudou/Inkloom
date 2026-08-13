package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CspScreenBrushSizeTest {
    @Test fun `screen-sized brush stores inverse zoom diameter`() {
        assertEquals(10f, CspScreenBrushSize.documentSize(40f, 4f, enabled = true), .0001f)
        assertEquals(40f, CspScreenBrushSize.physicalSize(40f, 4f, enabled = true), .0001f)
    }

    @Test fun `ordinary brush remains document-sized and follows canvas zoom`() {
        assertEquals(40f, CspScreenBrushSize.documentSize(40f, 4f, enabled = false), .0001f)
        assertEquals(160f, CspScreenBrushSize.physicalSize(40f, 4f, enabled = false), .0001f)
    }

    @Test fun `malformed size and scale remain finite and bounded`() {
        assertEquals(.01f, CspScreenBrushSize.documentSize(Float.NaN, Float.NaN, enabled = true), .0001f)
        assertEquals(40f, CspScreenBrushSize.documentSize(40f, Float.POSITIVE_INFINITY, enabled = true), .0001f)
    }
}
