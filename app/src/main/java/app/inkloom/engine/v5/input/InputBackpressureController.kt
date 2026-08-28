package app.inkloom.engine.v5.input

import app.inkloom.engine.v5.coordinates.DocumentPoint
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
        val previous = pending.peekLast()
        val beforePrevious = if (pending.size >= 2) pending.elementAt(pending.size - 2) else null
        if (pending.size >= softLimit && sample.canReplace(previous, beforePrevious)) {
            pending.removeLast()
            pending.addLast(sample)
            coalescedMoveCount++
        } else {
            pending.addLast(sample)
        }
    }

    private fun InputSample.canReplace(previous: InputSample?, beforePrevious: InputSample?): Boolean {
        previous ?: return false
        if (phase != InputPhase.MOVE || previous.phase != InputPhase.MOVE) return false
        if (predicted != previous.predicted || tool != previous.tool) return false
        beforePrevious ?: return point.distanceSquared(previous.point) < .25f
        if (beforePrevious.phase != InputPhase.MOVE || beforePrevious.tool != tool) return false

        // Preserve local pressure/tilt extrema. A middle report may disappear only when its
        // dynamics are already represented by linear interpolation of the two retained reports.
        val totalDistance = kotlin.math.sqrt(point.distanceSquared(beforePrevious.point))
        if (totalDistance <= 1e-3f) return false
        val middleFraction = (kotlin.math.sqrt(previous.point.distanceSquared(beforePrevious.point)) / totalDistance)
            .coerceIn(0f, 1f)
        val pressureExpected = beforePrevious.pressure + (pressure - beforePrevious.pressure) * middleFraction
        if (kotlin.math.abs(previous.pressure - pressureExpected) > .025f) return false
        val tiltExpected = beforePrevious.tiltRadians +
            (tiltRadians - beforePrevious.tiltRadians) * middleFraction
        if (kotlin.math.abs(previous.tiltRadians - tiltExpected) > .025f) return false

        // Geometric error, not report spacing, is the quality boundary. Fast straight motion can
        // span many pixels without carrying extra shape information; a real corner cannot.
        val ax = beforePrevious.point.x
        val ay = beforePrevious.point.y
        val bx = point.x
        val by = point.y
        val px = previous.point.x
        val py = previous.point.y
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-6f) return false
        val projection = (((px - ax) * dx + (py - ay) * dy) / lengthSquared)
        if (projection !in 0f..1f) return false
        val errorX = px - (ax + dx * projection)
        val errorY = py - (ay + dy * projection)
        return errorX * errorX + errorY * errorY <= MAX_COALESCE_ERROR_SQUARED
    }

    private fun DocumentPoint.distanceSquared(other: DocumentPoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        return dx * dx + dy * dy
    }

    private companion object {
        const val MAX_COALESCE_ERROR_SQUARED = .75f * .75f
    }
}
