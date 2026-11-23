package app.inkloom.engine.v5.input

import app.inkloom.engine.v5.coordinates.DocumentPoint
import java.util.ArrayDeque

/** Shared geometry policy for every queue before the ordered stroke modeler. */
object GeometricMoveCoalescer {
    fun canReplace(
        before: DocumentPoint,
        middle: DocumentPoint,
        after: DocumentPoint,
        beforePressure: Float,
        middlePressure: Float,
        afterPressure: Float,
        beforeTilt: Float,
        middleTilt: Float,
        afterTilt: Float,
    ): Boolean {
        val totalDistance = kotlin.math.sqrt(distanceSquared(after, before))
        if (totalDistance <= 1e-3f) return false
        val middleFraction = (kotlin.math.sqrt(distanceSquared(middle, before)) / totalDistance)
            .coerceIn(0f, 1f)
        val pressureExpected = beforePressure + (afterPressure - beforePressure) * middleFraction
        if (kotlin.math.abs(middlePressure - pressureExpected) > .025f) return false
        val tiltExpected = beforeTilt + (afterTilt - beforeTilt) * middleFraction
        if (kotlin.math.abs(middleTilt - tiltExpected) > .025f) return false

        // Geometric error, not report spacing, is the quality boundary. Fast straight motion can
        // span many pixels without carrying extra shape information; a real corner cannot.
        val dx = after.x - before.x
        val dy = after.y - before.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-6f) return false
        val projection = (((middle.x - before.x) * dx + (middle.y - before.y) * dy) / lengthSquared)
        if (projection !in 0f..1f) return false
        val errorX = middle.x - (before.x + dx * projection)
        val errorY = middle.y - (before.y + dy * projection)
        return errorX * errorX + errorY * errorY <= MAX_COALESCE_ERROR_SQUARED
    }

    fun distanceSquared(left: DocumentPoint, right: DocumentPoint): Float {
        val dx = left.x - right.x
        val dy = left.y - right.y
        return dx * dx + dy * dy
    }

    private const val MAX_COALESCE_ERROR_SQUARED = .75f * .75f
}

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
        // Arrival order can interleave timer ticks with older device history. The oldest source
        // time may therefore be inside this bounded queue rather than at its head.
        val oldest = pending.minOfOrNull { it.eventTimeNanos }
            ?.let { (nowNanos - it).coerceAtLeast(0L) } ?: 0L
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
        beforePrevious ?: return GeometricMoveCoalescer.distanceSquared(point, previous.point) < .25f
        if (beforePrevious.phase != InputPhase.MOVE || beforePrevious.tool != tool) return false

        return GeometricMoveCoalescer.canReplace(
            before = beforePrevious.point,
            middle = previous.point,
            after = point,
            beforePressure = beforePrevious.pressure,
            middlePressure = previous.pressure,
            afterPressure = pressure,
            beforeTilt = beforePrevious.tiltRadians,
            middleTilt = previous.tiltRadians,
            afterTilt = tiltRadians,
        )
    }
}
