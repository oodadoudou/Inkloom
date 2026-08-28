package app.inkloom.engine.v5.material

import org.junit.Assert.assertEquals
import org.junit.Test

class BrushTipMaskTest {
    @Test
    fun bilinearSamplingPreservesCornersCentreAndTransparentExterior() {
        val mask = BrushTipMask(
            width = 2,
            height = 2,
            alpha = byteArrayOf(0, 64, 128.toByte(), 255.toByte()),
        )

        assertEquals(0f, mask.coverageAt(0f, 0f), EPSILON)
        assertEquals(64f / 255f, mask.coverageAt(1f, 0f), EPSILON)
        assertEquals(128f / 255f, mask.coverageAt(0f, 1f), EPSILON)
        assertEquals(1f, mask.coverageAt(1f, 1f), EPSILON)
        assertEquals((0f + 64f + 128f + 255f) / (4f * 255f), mask.coverageAt(.5f, .5f), EPSILON)
        assertEquals(0f, mask.coverageAt(-.001f, .5f), EPSILON)
        assertEquals(0f, mask.coverageAt(.5f, 1.001f), EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-6f
    }
}
