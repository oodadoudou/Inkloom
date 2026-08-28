package app.inkloom.engine.v5.diagnostics

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Debug-only per-stroke timeline for the realtime landing pipeline.
 *
 * Threads one strokeId through V5 input, transactional deposition, and stable presentation, then
 * derives end-to-end latencies without relying on a compatibility renderer.
 * aggregates P50/P95/P99/MAX across sessions. Every call is a no-op while [enabled] is false so
 * release builds pay nothing. Diagnostics can only be enabled from a debug build.
 */
internal object StrokeTrace {
    const val STAGE_ACTION_DOWN = "ACTION_DOWN"
    const val STAGE_FIRST_SAMPLE = "first_historical_sample"
    const val STAGE_RUNTIME_CREATED = "runtime_created"
    const val STAGE_FIRST_DAB = "first_dab_generated"
    const val STAGE_LAST_MOVE = "last_move"
    const val STAGE_ACTION_UP = "ACTION_UP"
    const val STAGE_FRONT_BUFFER_DRAW = "front_buffer_draw"
    const val STAGE_FRONT_BUFFER_COMMIT = "front_buffer_commit"
    const val STAGE_TRANSIENT_HIDDEN = "transient_layer_hidden"
    const val STAGE_PROJECTION_INVALIDATED = "projection_invalidated"
    const val STAGE_PROJECTION_GENERATED = "projection_generated"
    const val STAGE_RENDERER_DRAW = "renderer_draw"
    const val STAGE_TRANSACTION_COMMIT = "transaction_commit"
    const val STAGE_STABLE_FRAME = "stable_frame"

    private const val TAG = "StrokeTrace"
    private const val MAX_SESSIONS = 128
    private const val SUMMARY_EVERY = 25

    /** Presentation-gap threshold: two 60Hz frames (33.3 ms). */
    private const val GAP_WARNING_MICROS = 33_334L

    @Volatile
    var enabled: Boolean = false

    private val sessions = ConcurrentHashMap<String, Session>()
    private val aggregate = ConcurrentHashMap<String, MutableList<Long>>()
    private val warningCounts = ConcurrentHashMap<String, Int>()
    private var completedCount = 0

    data class Mark(
        val stage: String,
        val nanoTime: Long,
        val thread: String,
        val rect: String? = null,
        val tileCount: Int? = null,
        val generation: Long? = null,
    )

    class Session internal constructor(val strokeId: String) {
        private val marks = mutableListOf<Mark>()
        private val stages = mutableSetOf<String>()
        @Volatile internal var committed = false
        @Volatile internal var transientHidden = false
        @Volatile internal var completed = false
        private var previewPointCount: Int? = null
        private var committedPointCount: Int? = null

        @Synchronized
        fun mark(stage: String, rect: String? = null, tileCount: Int? = null, generation: Long? = null) {
            if (completed) return
            marks += Mark(stage, System.nanoTime(), Thread.currentThread().name, rect, tileCount, generation)
            stages += stage
            when (stage) {
                STAGE_TRANSACTION_COMMIT -> committed = true
                STAGE_TRANSIENT_HIDDEN -> transientHidden = true
            }
            if (committed && transientHidden && STAGE_STABLE_FRAME in stages) complete()
        }

        @Synchronized
        fun markOnce(stage: String, rect: String? = null, tileCount: Int? = null, generation: Long? = null) {
            if (stage in stages) return
            mark(stage, rect, tileCount, generation)
        }

        /** Records the point counts of the transient preview and the committed stroke at commit. */
        @Synchronized
        fun markTransaction(previewPoints: Int, committedPoints: Int) {
            previewPointCount = previewPoints
            committedPointCount = committedPoints
            mark(STAGE_TRANSACTION_COMMIT)
        }

        @Synchronized
        internal fun complete() {
            if (completed) return
            completed = true
            StrokeTrace.finishSession(this)
        }

        @Synchronized
        fun first(stage: String): Mark? = marks.firstOrNull { it.stage == stage }

        @Synchronized
        fun last(stage: String): Mark? = marks.lastOrNull { it.stage == stage }

        @Synchronized
        fun timeline(): List<Mark> = marks.toList()

        @Synchronized
        fun hasStage(stage: String): Boolean = stage in stages

        @Synchronized
        internal fun collectWarnings(): List<String> {
            val result = mutableListOf<String>()
            val down = first(STAGE_ACTION_DOWN)
            val firstTransient = first(STAGE_FRONT_BUFFER_DRAW)
            if (down != null && firstTransient != null) {
                val micros = (firstTransient.nanoTime - down.nanoTime) / 1000L
                if (micros > GAP_WARNING_MICROS) result += "GAP DOWN->first transient=$micros us (>${GAP_WARNING_MICROS} us)"
            }
            val up = first(STAGE_ACTION_UP)
            val stable = first(STAGE_STABLE_FRAME)
            if (up != null && stable != null) {
                val micros = (stable.nanoTime - up.nanoTime) / 1000L
                if (micros > GAP_WARNING_MICROS) result += "GAP UP->stable=$micros us (>${GAP_WARNING_MICROS} us)"
            }
            if (hasStage(STAGE_PROJECTION_INVALIDATED) && !hasStage(STAGE_PROJECTION_GENERATED)) {
                result += "GENERATION_STALL projection invalidated but never generated"
            }
            val commit = first(STAGE_FRONT_BUFFER_COMMIT)
            if (commit != null) {
                val projection = first(STAGE_PROJECTION_GENERATED)
                if (projection == null || projection.nanoTime > commit.nanoTime) {
                    result += "HANDOFF_BEFORE_PROJECTION front buffer committed before the V3 projection was generated"
                }
            }
            if (hasStage(STAGE_ACTION_UP) && !hasStage(STAGE_FRONT_BUFFER_DRAW)) {
                result += "INFO NO_TRANSIENT_FRAME no front-buffer frame before commit (view-preview path)"
            }
            val firstDab = first(STAGE_FIRST_DAB)
            if (firstDab != null && firstTransient != null && firstDab.nanoTime > firstTransient.nanoTime) {
                result += "FIRST_DAB_LATE first dab generated after the first front-buffer draw"
            }
            val preview = previewPointCount
            val committedCount = committedPointCount
            if (preview != null && committedCount != null && preview != committedCount) {
                result += "PREVIEW_FINAL_GEOMETRY_DIFF preview points=$preview committed points=$committedCount"
            }
            return result
        }
    }

    /** Maps a mirror copy id back to the base stroke session it belongs to. */
    fun traceBaseId(strokeId: String): String {
        val separator = strokeId.indexOf("-sym-")
        return if (separator >= 0) strokeId.substring(0, separator) else strokeId
    }

    fun begin(strokeId: String): Session? {
        if (!enabled) return null
        if (sessions.size > MAX_SESSIONS) sessions.entries.firstOrNull()?.let { sessions.remove(it.key) }
        return Session(strokeId).also { it.mark(STAGE_ACTION_DOWN) }.also { sessions[strokeId] = it }
    }

    fun mark(strokeId: String, stage: String, rect: String? = null, tileCount: Int? = null, generation: Long? = null) {
        if (!enabled) return
        sessions[strokeId]?.mark(stage, rect, tileCount, generation)
    }

    fun markOnce(strokeId: String, stage: String, rect: String? = null, tileCount: Int? = null, generation: Long? = null) {
        if (!enabled) return
        sessions[strokeId]?.markOnce(stage, rect, tileCount, generation)
    }

    /** Records the point counts of the transient preview and the committed stroke at commit. */
    fun markTransaction(strokeId: String, previewPoints: Int, committedPoints: Int) {
        if (!enabled) return
        sessions[strokeId]?.markTransaction(previewPoints, committedPoints)
    }

    /** Ends a stroke that was cancelled before commit so its partial timeline never aggregates. */
    fun abandon(strokeId: String) {
        if (!enabled) return
        sessions.remove(strokeId)
    }

    fun reset() {
        sessions.clear()
        aggregate.values.forEach(MutableList<Long>::clear)
        aggregate.clear()
        warningCounts.clear()
        completedCount = 0
    }

    internal fun finishSession(session: Session) {
        sessions.remove(session.strokeId)
        val timeline = session.timeline()
        val origin = timeline.firstOrNull()?.nanoTime ?: System.nanoTime()
        val warnings = session.collectWarnings()
        warnings.forEach { warning ->
            val type = warning.substringBefore(' ')
            warningCounts.merge(type, 1, Int::plus)
        }
        Log.d(
            TAG,
            "stroke=${session.strokeId} " +
                timeline.joinToString(" ") { "${it.stage}@${(it.nanoTime - origin) / 1000L}us" } +
                (if (warnings.isEmpty()) "" else " WARNINGS=[${warnings.joinToString("; ")}]"),
        )
        derivedMetrics.forEach { metric ->
            val from = if (metric.fromFirst) session.first(metric.fromStage) else session.last(metric.fromStage)
            val to = if (metric.toFirst) session.first(metric.toStage) else session.last(metric.toStage)
            if (from != null && to != null) {
                aggregate.getOrPut(metric.label) { mutableListOf() } += (to.nanoTime - from.nanoTime) / 1000L
            }
        }
        completedCount++
        if (completedCount % SUMMARY_EVERY == 0) logSummary()
    }

    /** Accumulated microsecond samples for one derived metric, for diagnostics and tests. */
    internal fun samplesFor(label: String): List<Long> = aggregate[label].orEmpty()

    /** Counts of each detected presentation warning type, for diagnostics and tests. */
    internal fun warningCountFor(type: String): Int = warningCounts[type] ?: 0

    fun logSummary() {
        if (!enabled) return
        val builder = StringBuilder()
        derivedMetrics.forEach { metric ->
            val values = aggregate[metric.label].orEmpty().sorted()
            if (values.isNotEmpty()) {
                builder.append("  ").append(metric.label).append(" n=").append(values.size)
                    .append(" p50=").append(values.percentile(.50)).append("us")
                    .append(" p95=").append(values.percentile(.95)).append("us")
                    .append(" p99=").append(values.percentile(.99)).append("us")
                    .append(" max=").append(values.last()).append("us")
                    .append('\n')
            }
        }
        if (warningCounts.isNotEmpty()) {
            builder.append("  warnings=")
                .append(warningCounts.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key}:${it.value}" })
                .append('\n')
        }
        if (builder.isNotEmpty()) Log.d(TAG, "StrokeTrace summary (sessions=$completedCount)\n$builder")
    }

    internal data class Metric(
        val label: String,
        val fromStage: String,
        val toStage: String,
        val fromFirst: Boolean = true,
        val toFirst: Boolean = true,
    )

    internal val derivedMetrics = listOf(
        Metric("DOWN→first transient", STAGE_ACTION_DOWN, STAGE_FRONT_BUFFER_DRAW),
        Metric("DOWN→first V3", STAGE_ACTION_DOWN, STAGE_PROJECTION_GENERATED),
        Metric("DOWN→stable", STAGE_ACTION_DOWN, STAGE_STABLE_FRAME),
        Metric("last MOVE→stable", STAGE_LAST_MOVE, STAGE_STABLE_FRAME, fromFirst = false),
        Metric("UP→transaction commit", STAGE_ACTION_UP, STAGE_TRANSACTION_COMMIT),
        Metric("UP→front-buffer handoff", STAGE_ACTION_UP, STAGE_FRONT_BUFFER_COMMIT),
        Metric("UP→stable", STAGE_ACTION_UP, STAGE_STABLE_FRAME),
    )
}

/** Sorted-sample percentile at [p] in 0..1, clamped to the nearest ranked sample. */
internal fun List<Long>.percentile(p: Double): Long {
    if (isEmpty()) return 0L
    return this[((size - 1) * p).toInt()]
}
