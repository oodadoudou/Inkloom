package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WetMixDynamicsTest {
    @Test fun dryBrushKeepsFullAlpha() {
        assertEquals(1f, WetMixDynamics.alphaMultiplier(false, 1f, 0f), .0001f)
    }

    @Test fun paintLoadAddsPigmentAndColorMixRetainsUnderlyingMarks() {
        val unloaded = WetMixDynamics.alphaMultiplier(true, 0f, 0f)
        val loaded = WetMixDynamics.alphaMultiplier(true, 0f, 1f)
        val mixed = WetMixDynamics.alphaMultiplier(true, 1f, 1f)

        assertTrue(loaded > unloaded)
        assertTrue(mixed < loaded)
        assertEquals(.675f, mixed, .0001f)
    }
}
