package app.inkloom.engine.v5.input

import app.inkloom.engine.v5.coordinates.DocumentPoint

enum class InputPhase { DOWN, MOVE, UP }

enum class InputTool { STYLUS, STYLUS_ERASER, FINGER }

/** Immutable, already-mapped input. Event time always uses the monotonic uptime time base. */
data class InputSample(
    val sequence: Long,
    val phase: InputPhase,
    val tool: InputTool,
    val point: DocumentPoint,
    val eventTimeNanos: Long,
    val enqueuedTimeNanos: Long,
    val pressure: Float,
    val tiltRadians: Float,
    val orientationRadians: Float,
    val predicted: Boolean = false,
    /** Causal model clock; acquisition time above remains unchanged for latency diagnostics. */
    val modelTimeNanos: Long = eventTimeNanos,
) {
    init {
        require(sequence >= 0L)
        require(modelTimeNanos >= eventTimeNanos)
        require(eventTimeNanos >= 0L && enqueuedTimeNanos >= eventTimeNanos)
        require(pressure.isFinite() && pressure in 0f..1f)
        require(tiltRadians.isFinite() && orientationRadians.isFinite())
        require(!predicted || phase == InputPhase.MOVE) { "Only MOVE samples may be predicted" }
    }

    val queueAgeNanos: Long get() = enqueuedTimeNanos - eventTimeNanos
}

class InputSampleBatch private constructor(val samples: List<InputSample>) {
    init {
        require(samples.isNotEmpty())
        require(samples.zipWithNext().all { (a, b) -> a.sequence < b.sequence })
        require(samples.zipWithNext().all { (a, b) -> a.modelTimeNanos <= b.modelTimeNanos })
    }

    companion object {
        fun owned(samples: Collection<InputSample>): InputSampleBatch = InputSampleBatch(samples.toList())
    }
}

