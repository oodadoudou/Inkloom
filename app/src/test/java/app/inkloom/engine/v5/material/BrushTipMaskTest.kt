package app.inkloom.engine.v5.material

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushTipMaskTest {
    @Test fun `prefiltered lookup preserves a sparse mark when rendered small`() {
        val alpha = ByteArray(64 * 64)
        alpha[32 * 64 + 32] = 0xff.toByte()
        val mask = BrushTipMask(64, 64, alpha)

        assertTrue(mask.coverageAtForOutput(.5f, .5f, 8f, 8f) > 0f)
        assertTrue(mask.byteSize > 64L * 64L)
    }

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

    @Test fun proceduralHardnessSoftensEdgesWithoutChangingCentreTextureOrSeed() {
        val generators: List<(Float) -> BrushTipMask> = listOf(
            { hardness -> BrushTipMask.graphite(seed = 42, toothStrength = .7f, hardness = hardness) },
            { hardness -> BrushTipMask.bristle(seed = 42, hardness = hardness) },
        )
        generators.forEach { generate ->
            val soft = generate(.05f)
            val medium = generate(.5f)
            val hard = generate(1f)
            val repeated = generate(.05f)
            var lower = 0
            for (y in 0..95) for (x in 0..95) {
                val u = x / 95f
                val v = y / 95f
                val a = soft.coverageAt(u, v)
                val b = medium.coverageAt(u, v)
                val c = hard.coverageAt(u, v)
                assertTrue(a <= b + EPSILON && b <= c + EPSILON)
                assertEquals(a, repeated.coverageAt(u, v), 0f)
                if (c - a > .1f) lower++
            }
            assertTrue("Softness must affect the edge", lower > 100)
            assertEquals(hard.coverageAt(.5f, .5f), soft.coverageAt(.5f, .5f), 0f)
        }
    }

    private companion object {
        const val EPSILON = 1e-6f
    }
}
