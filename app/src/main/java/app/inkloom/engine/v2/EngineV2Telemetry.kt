package app.inkloom.engine.v2

import android.os.Trace
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Allocation-free timing used by the V2 input and active-stroke hot paths.
 *
 * Values are grouped into fixed logarithmic buckets so production builds can keep measuring
 * without retaining individual samples or producing logcat traffic. Perfetto sections are only
 * emitted while platform tracing is enabled.
 */
internal class EngineV2LatencyHistogram {
    private val buckets = AtomicLongArray(BUCKET_UPPER_BOUNDS_US.size)
    private val count = AtomicLong()
    private val totalUs = AtomicLong()
    private val maximumUs = AtomicLong()

    fun recordNanos(durationNanos: Long) {
        val durationUs = (durationNanos.coerceAtLeast(0L) + 999L) / 1_000L
        val bucket = BUCKET_UPPER_BOUNDS_US.indexOfFirst { durationUs <= it }
            .let { if (it >= 0) it else BUCKET_UPPER_BOUNDS_US.lastIndex }
        buckets.incrementAndGet(bucket)
        count.incrementAndGet()
        totalUs.addAndGet(durationUs)
        maximumUs.accumulateAndGet(durationUs, ::maxOf)
    }

    fun snapshot(): EngineV2LatencySnapshot {
        val sampleCount = count.get()
        if (sampleCount == 0L) return EngineV2LatencySnapshot.EMPTY
        return EngineV2LatencySnapshot(
            count = sampleCount,
            meanUs = totalUs.get() / sampleCount,
            p50Us = percentileUpperBound(sampleCount, 50),
            p95Us = percentileUpperBound(sampleCount, 95),
            maximumUs = maximumUs.get(),
        )
    }

    private fun percentileUpperBound(sampleCount: Long, percentile: Int): Long {
        val target = ((sampleCount * percentile) + 99L) / 100L
        var accumulated = 0L
        for (index in BUCKET_UPPER_BOUNDS_US.indices) {
            accumulated += buckets.get(index)
            if (accumulated >= target) return BUCKET_UPPER_BOUNDS_US[index]
        }
        return BUCKET_UPPER_BOUNDS_US.last()
    }

    private companion object {
        val BUCKET_UPPER_BOUNDS_US = longArrayOf(
            125L, 250L, 500L, 1_000L, 2_000L, 4_000L, 8_000L, 12_000L,
            16_000L, 24_000L, 32_000L, 48_000L, 64_000L, 96_000L, 128_000L,
            Long.MAX_VALUE,
        )
    }
}

internal data class EngineV2LatencySnapshot(
    val count: Long,
    val meanUs: Long,
    val p50Us: Long,
    val p95Us: Long,
    val maximumUs: Long,
) {
    companion object {
        val EMPTY = EngineV2LatencySnapshot(0L, 0L, 0L, 0L, 0L)
    }
}

internal class EngineV2Metrics {
    val inputDispatch = EngineV2LatencyHistogram()
    val inputModel = EngineV2LatencyHistogram()
    val activeStrokeDraw = EngineV2LatencyHistogram()
}

internal inline fun <T> traceEngineV2(section: String, block: () -> T): T {
    if (!Trace.isEnabled()) return block()
    Trace.beginSection(section)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}
