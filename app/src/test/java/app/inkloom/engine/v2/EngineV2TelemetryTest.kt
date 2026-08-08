package app.inkloom.engine.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineV2TelemetryTest {
    @Test
    fun histogramReportsCountMeanPercentilesAndMaximum() {
        val histogram = EngineV2LatencyHistogram()
        listOf(100L, 200L, 900L, 1_100L, 9_000L).forEach { microseconds ->
            histogram.recordNanos(microseconds * 1_000L)
        }

        val result = histogram.snapshot()

        assertEquals(5L, result.count)
        assertEquals(2_260L, result.meanUs)
        assertEquals(1_000L, result.p50Us)
        assertEquals(12_000L, result.p95Us)
        assertEquals(9_000L, result.maximumUs)
    }

    @Test
    fun histogramHandlesConcurrentWritersWithoutDroppingSamples() {
        val histogram = EngineV2LatencyHistogram()
        val writers = List(4) {
            Thread { repeat(2_000) { histogram.recordNanos(500_000L) } }
        }

        writers.forEach(Thread::start)
        writers.forEach(Thread::join)

        val result = histogram.snapshot()
        assertEquals(8_000L, result.count)
        assertEquals(500L, result.meanUs)
        assertTrue(result.p95Us >= 500L)
    }
}
