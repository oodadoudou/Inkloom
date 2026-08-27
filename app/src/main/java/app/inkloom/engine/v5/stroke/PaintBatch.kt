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
                input.eventTimeNanos,
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

