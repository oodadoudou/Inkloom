package app.inkloom.engine.v3.render

import org.junit.Assert.assertEquals
import org.junit.Test

class BrushProgramBatchRendererTest {
    @Test
    fun randomAccessMatchesJavaRandomAcrossLongStroke() {
        val seeds = listOf(0L, 1L, -1L, 0x49A2B31DL, Int.MAX_VALUE.toLong())
        val indices = listOf(0, 1, 2, 31, 256, 4_096, 100_000)
        seeds.forEach { seed ->
            val reference = java.util.Random(seed)
            var nextIndex = 0
            indices.forEach { index ->
                while (nextIndex < index) {
                    reference.nextFloat()
                    nextIndex++
                }
                assertEquals(reference.nextFloat(), BrushProgramBatchRenderer.legacyRandomFloatAt(seed, index), 0f)
                nextIndex++
            }
        }
    }
}
