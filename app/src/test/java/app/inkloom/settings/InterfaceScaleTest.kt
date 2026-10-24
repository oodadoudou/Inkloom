package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class InterfaceScaleTest {
    @Test fun aLargeHighResolutionPenDisplayIsDrawnSmallerThanAndroidsBucketSize() {
        // MovinkPad Pro 14: 2880 × 1800 at about 243 ppi, drawn at 2× or 2.25×.
        assertEquals(.75f, InterfaceScale.auto(2f, 243f, 243f, 900), .001f)
        assertEquals(.65f, InterfaceScale.auto(2.25f, 243f, 243f, 800), .001f)
        // A 10-inch tablet at its nominal size is barely changed; an emulator reporting 320 dpi at 2× is unchanged.
        assertEquals(.9f, InterfaceScale.auto(2f, 287f, 287f, 800), .001f)
        assertEquals(1f, InterfaceScale.auto(2f, 320f, 320f, 800), .001f)
    }

    @Test fun phonesAndScreensWithoutAPlausibleDensityKeepAndroidsSizes() {
        assertEquals(1f, InterfaceScale.auto(2.75f, 400f, 400f, 411), .001f)
        assertEquals(1f, InterfaceScale.auto(2f, 0f, 0f, 900), .001f)
        assertEquals(1f, InterfaceScale.auto(2f, 2000f, 2000f, 900), .001f)
        // Never larger than Android's own size, never below the minimum.
        assertEquals(1f, InterfaceScale.auto(1f, 400f, 400f, 900), .001f)
        assertEquals(InterfaceScale.MIN, InterfaceScale.auto(4f, 200f, 200f, 900), .001f)
    }

    @Test fun aChosenSizeOverridesTheMeasurementAndIsKeptInRangeAndSteps() {
        assertEquals(.8f, InterfaceScale.resolve(.8f, 2f, 320f, 320f, 800), .001f)
        assertEquals(.85f, InterfaceScale.resolve(.86f, 2f, 320f, 320f, 800), .001f)
        assertEquals(InterfaceScale.MAX, InterfaceScale.resolve(3f, 2f, 320f, 320f, 800), .001f)
        assertEquals(.75f, InterfaceScale.resolve(InterfaceScale.AUTO, 2f, 243f, 243f, 900), .001f)
        assertEquals(InterfaceScale.AUTO, InterfaceScale.stored(-1f), 0f)
        assertEquals(InterfaceScale.AUTO, InterfaceScale.stored(Float.NaN), 0f)
        assertEquals(InterfaceScale.MIN, InterfaceScale.stored(.1f), .001f)
        assertEquals(70, InterfaceScale.percent(InterfaceScale.snap(.7f)))
    }
}
