package app.inkloom.engine.v5.input

import java.util.ArrayDeque

/**
 * Bounded input mailbox. It never drops contact boundaries or pressure/direction extrema. When an
 * overwhelmed producer provides consecutive ordinary MOVE samples, only the middle of that run is
 * coalesced. Consumers still receive a strictly ordered stream.
 */
class InputBackpressureController(
    private val softLimit: Int = 64,
    private val hardLimit: Int = 256,
) {
    init {
        require(softLimit >= 4)
        require(hardLimit >= softLimit)
    }

    data class Snapshot(
        val depth: Int,
        val peakDepth: Int,
        val oldestInputAgeNanos: Long,
        val coalescedMoveCount: Long,
    )

    private val pending = ArrayDeque<InputSample>()
    private var peakDepth = 0
    private var coalescedMoveCount = 0L

    @Synchronized
    fun enqueue(batch: InputSampleBatch) {
        batch.samples.forEach(::enqueueOne)
        peakDepth = maxOf(peakDepth, pending.size)
        check(pending.size <= hardLimit) {
            "Input backlog exceeded hard limit $hardLimit; PaintOp is not keeping up"
        }
    }

    @Synchronized
    fun drain(maxSamples: Int): InputSampleBatch? {
        require(maxSamples > 0)
        if (pending.isEmpty()) return null
        val count = minOf(maxSamples, pending.size)
        return InputSampleBatch.owned(List(count) { pending.removeFirst() })
    }

    @Synchronized
    fun snapshot(nowNanos: Long): Snapshot {
        require(nowNanos >= 0L)
        val oldest = pending.peekFirst()?.let { (nowNanos - it.eventTimeNanos).coerceAtLeast(0L) } ?: 0L
        return Snapshot(pending.size, peakDepth, oldest, coalescedMoveCount)
    }

    @Synchronized
    fun clear() {
        pending.clear()
        peakDepth = 0
        coalescedMoveCount = 0
    }

    private fun enqueueOne(sample: InputSample) {
        if (pending.size >= softLimit && sample.canReplace(pending.peekLast())) {
            pending.removeLast()
            pending.addLast(sample)
            coalescedMoveCount++
        } else {
            pending.addLast(sample)
        }
    }

    private fun InputSample.canReplace(previous: InputSample?): Boolean {
        previous ?: return false
        if (phase != InputPhase.MOVE || previous.phase != InputPhase.MOVE) return false
        if (predicted != previous.predicted || tool != previous.tool) return false
        // Preserve pressure extrema and a meaningful direction corner. Coalesce only a very close
        // continuation that cannot change the visible envelope appreciably.
        if (kotlin.math.abs(pressure - previous.pressure) > .025f) return false
        val dx = point.x - previous.point.x
        val dy = point.y - previous.point.y
        return dx * dx + dy * dy < 0.25f
    }
}

