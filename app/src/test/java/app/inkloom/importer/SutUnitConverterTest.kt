package app.inkloom.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class SutUnitConverterTest {
    @Test fun retainsPixelsAndConvertsMillimetersToReferencePixels() {
        assertEquals(10f, SutUnitConverter.referencePixels(10f, 0), .0001f)
        assertEquals(37.795275f, SutUnitConverter.referencePixels(10f, SutUnitConverter.MILLIMETERS), .0001f)
    }
}
