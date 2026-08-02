package app.inkloom.document

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class LargeNotePerformanceTest {
    @Test fun samplesFiveThousandRealisticStrokesWithinRegressionBudget() {
        val stroke = List(32) { index ->
            val amount = index / 31f
            StrokePoint(amount, .5f + kotlin.math.sin(amount * 12f) * .2f, pressure = .2f + amount * .8f, timeMs = index * 8L)
        }
        var generatedStamps = 0
        val elapsed = measureTimeMillis {
            repeat(5_000) {
                generatedStamps += StrokeStampSampler.sample(stroke, 2_560f, 1_600f) { 12f }.size
            }
        }
        assertTrue(generatedStamps > 500_000)
        assertTrue("5,000-stroke sampling took ${elapsed}ms", elapsed < 5_000L)
    }
}
