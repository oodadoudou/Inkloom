package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class CspBrushGapTest {
    @Test fun fixedUsesStoredRatioAndAutomaticModesIgnoreIt() {
        assertEquals(.42f, CspBrushGap.ratio(CspBrushGap.FIXED, .42f), .0001f)
        assertEquals(.30f, CspBrushGap.ratio(CspBrushGap.WIDE, .01f), .0001f)
        assertEquals(.15f, CspBrushGap.ratio(CspBrushGap.NORMAL, 1.8f), .0001f)
        assertEquals(.05f, CspBrushGap.ratio(CspBrushGap.NARROW, .9f), .0001f)
    }

    @Test fun schemaModesFollowCspWideNormalNarrowOrdering() {
        assertEquals("Fixed", CspBrushGap.displayName(0))
        assertEquals("Wide", CspBrushGap.displayName(1))
        assertEquals("Normal", CspBrushGap.displayName(2))
        assertEquals("Narrow", CspBrushGap.displayName(3))
    }
}
