package app.inkloom.engine.v5.stroke

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.coordinates.DocumentRect
import app.inkloom.engine.v5.input.InputSample
import app.inkloom.engine.v5.input.InputSampleBatch

data class StrokeSample(
    val inputSequence: Long,
    val point: DocumentPoint,
    val eventTimeNanos: Long,
    val pressure: Float,
    val tiltRadians: Float,
    val orientationRadians: Float,
)

/** Append-only product. [samples] contains only samples not emitted by an earlier batch. */
data class PaintBatch(
    val transactionId: Long,
    val ordinal: Long,
    val samples: List<StrokeSample>,
    val dirtyBounds: DocumentRect?,
    val terminal: Boolean,
) {
    init {
        require(transactionId > 0L)
        require(ordinal >= 0L)
        require(samples.zipWithNext().all { (a, b) -> a.inputSequence < b.inputSequence })
    }

    companion object {
        fun empty(transactionId: Long, ordinal: Long, terminal: Boolean = false) =
            PaintBatch(transactionId, ordinal, emptyList(), null, terminal)
    }
}

interface StrokeSession {
    val transactionId: Long
    fun begin(sample: InputSample): PaintBatch
    fun append(samples: InputSampleBatch): PaintBatch
    fun finish(sample: InputSample? = null): PaintBatch
    fun cancel()
}

/**
 * Optional causal correction for an explicitly authored smoothing value. Unlike post-correction,
 * this session never revisits an emitted point and never rebuilds the trajectory at pen-up. A zero
 * value is represented by [DirectStrokeSession] so the default path remains byte-for-byte raw.
 */
class CausalStrokeSession(
    override val transactionId: Long,
    private val maximumRadius: Float,
    smoothing: Float,
    globalSmoothing: Float = 0f,
) : StrokeSession {
    init {
        require(transactionId > 0L)
        require(maximumRadius.isFinite() && maximumRadius >= 0f)
        require(smoothing.isFinite() && smoothing in 0f..1f)
        require(globalSmoothing.isFinite() && globalSmoothing in 0f..1f)
    }

    private val filters = listOf(globalSmoothing, smoothing).filter { it > 0f }.map(::CoordinateFilter)
    /** Professional realtime correction may soften jitter but must never trail the physical tip
     * by an unbounded distance. This document-space cap keeps even maximum smoothing within a
     * small, predictable tail instead of accumulating the error of an unconstrained EMA. */
    private var state = State.NEW
    private var ordinal = 0L
    private var lastSequence = -1L

    override fun begin(sample: InputSample): PaintBatch {
        check(state == State.NEW)
        check(sample.phase == app.inkloom.engine.v5.input.InputPhase.DOWN)
        state = State.ACTIVE
        return emit(listOf(sample), terminal = false)
    }

    override fun append(samples: InputSampleBatch): PaintBatch {
        check(state == State.ACTIVE)
        check(samples.samples.none { it.phase != app.inkloom.engine.v5.input.InputPhase.MOVE })
        return emit(samples.samples.filterNot(InputSample::predicted), terminal = false)
    }

    override fun finish(sample: InputSample?): PaintBatch {
        check(state == State.ACTIVE)
        check(sample == null || sample.phase == app.inkloom.engine.v5.input.InputPhase.UP)
        state = State.FINISHED
        return emit(listOfNotNull(sample), terminal = true)
    }

    override fun cancel() {
        check(state != State.FINISHED)
        state = State.CANCELED
    }

    private fun emit(inputs: List<InputSample>, terminal: Boolean): PaintBatch {
        require(inputs.all { it.sequence > lastSequence })
        inputs.lastOrNull()?.let { lastSequence = it.sequence }
        val output = inputs.map { input ->
            val point = filters.fold(input.point) { point, filter -> filter.apply(point) }
            StrokeSample(
                input.sequence,
                point,
                input.modelTimeNanos,
                input.pressure,
                input.tiltRadians,
                input.orientationRadians,
            )
        }
        val bounds = output.takeIf(List<StrokeSample>::isNotEmpty)?.let { values ->
            DocumentRect(
                values.minOf { it.point.x },
                values.minOf { it.point.y },
                values.maxOf { it.point.x },
                values.maxOf { it.point.y },
            ).outset(maximumRadius)
        }
        return PaintBatch(transactionId, ordinal++, output, bounds, terminal)
    }

    private class CoordinateFilter(amount: Float) {
        private val response = (1f - amount * .85f).coerceIn(.15f, 1f)
        private val maximumLagPixels = .5f + amount * 3.5f
        private var previous: DocumentPoint? = null

        fun apply(point: DocumentPoint): DocumentPoint {
            val prior = previous
            val filtered = if (prior == null) point else {
                val x = prior.x + (point.x - prior.x) * response
                val y = prior.y + (point.y - prior.y) * response
                val dx = x - point.x; val dy = y - point.y
                val lag = kotlin.math.hypot(dx, dy)
                if (lag <= maximumLagPixels || lag <= 1e-6f) DocumentPoint(x, y)
                else {
                    val scale = maximumLagPixels / lag
                    DocumentPoint(point.x + dx * scale, point.y + dy * scale)
                }
            }
            previous = filtered
            return filtered
        }
    }

    private enum class State { NEW, ACTIVE, FINISHED, CANCELED }
}

/** No-correction reference session. The committed trajectory is byte-for-byte the emitted one. */
class DirectStrokeSession(
    override val transactionId: Long,
    private val maximumRadius: Float,
) : StrokeSession {
    init {
        require(transactionId > 0L)
        require(maximumRadius.isFinite() && maximumRadius >= 0f)
    }

    private var state = State.NEW
    private var ordinal = 0L
    private var lastSequence = -1L

    override fun begin(sample: InputSample): PaintBatch {
        check(state == State.NEW)
        check(sample.phase == app.inkloom.engine.v5.input.InputPhase.DOWN)
        state = State.ACTIVE
        return emit(listOf(sample), terminal = false)
    }

    override fun append(samples: InputSampleBatch): PaintBatch {
        check(state == State.ACTIVE)
        check(samples.samples.none { it.phase != app.inkloom.engine.v5.input.InputPhase.MOVE })
        return emit(samples.samples.filterNot(InputSample::predicted), terminal = false)
    }

    override fun finish(sample: InputSample?): PaintBatch {
        check(state == State.ACTIVE)
        check(sample == null || sample.phase == app.inkloom.engine.v5.input.InputPhase.UP)
        state = State.FINISHED
        return emit(listOfNotNull(sample), terminal = true)
    }

    override fun cancel() {
        check(state != State.FINISHED)
        state = State.CANCELED
    }

    private fun emit(inputs: List<InputSample>, terminal: Boolean): PaintBatch {
        require(inputs.all { it.sequence > lastSequence })
        inputs.lastOrNull()?.let { lastSequence = it.sequence }
        val output = inputs.map { input ->
            StrokeSample(
                input.sequence,
                input.point,
                input.modelTimeNanos,
                input.pressure,
                input.tiltRadians,
                input.orientationRadians,
            )
        }
        val bounds = output.takeIf(List<StrokeSample>::isNotEmpty)?.let { values ->
            DocumentRect(
                values.minOf { it.point.x },
                values.minOf { it.point.y },
                values.maxOf { it.point.x },
                values.maxOf { it.point.y },
            ).outset(maximumRadius)
        }
        return PaintBatch(transactionId, ordinal++, output, bounds, terminal)
    }

    private enum class State { NEW, ACTIVE, FINISHED, CANCELED }
}
