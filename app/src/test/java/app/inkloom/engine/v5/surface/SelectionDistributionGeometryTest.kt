package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.tools.SelectionDistributionGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionDistributionGeometryTest {
    @Test fun equalGapsAccountsForDifferentWidthsWhileCenterSpacingUsesCenters() {
        val bounds = listOf(PixelRect(0, 0, 10, 10), PixelRect(20, 0, 40, 10), PixelRect(80, 0, 110, 10))
        assertEquals(listOf(0f, 15f, 0f), SelectionDistributionGeometry.offsets(bounds, true, true))
        assertEquals(listOf(0f, 20f, 0f), SelectionDistributionGeometry.offsets(bounds, true, false))
        val vertical = listOf(PixelRect(0, 0, 10, 10), PixelRect(0, 20, 10, 40), PixelRect(0, 80, 10, 110))
        assertEquals(listOf(0f, 15f, 0f), SelectionDistributionGeometry.offsets(vertical, false, true))
    }
}
