package app.inkloom.document

import kotlin.math.hypot

data class LaserTrailPoint(
    val x: Float,
    val y: Float,
    val timeMs: Long,
    val gestureId: Int,
)

data class LaserTrailRenderPoint(
    val x: Float,
    val y: Float,
    val alpha: Float,
    val gestureId: Int,
)

/** Bounded, transient laser-pointer state. Nothing in this model is serialized. */
class LaserTrail(
    private val lifetimeMs: Long = DEFAULT_LIFETIME_MS,
    private val maximumPoints: Int = DEFAULT_MAXIMUM_POINTS,
    private val minimumDistance: Float = DEFAULT_MINIMUM_DISTANCE,
) {
    private val points = ArrayDeque<LaserTrailPoint>()
    private var activeGestureId: Int? = null
    private var nextGestureId = 1

    init {
        require(lifetimeMs > 0)
        require(maximumPoints >= 2)
        require(minimumDistance >= 0f && minimumDistance.isFinite())
    }

    fun begin(x: Float, y: Float, timeMs: Long) {
        activeGestureId = nextGestureId++
        appendInternal(x, y, timeMs, force = true)
    }

    fun append(x: Float, y: Float, timeMs: Long) {
        if (activeGestureId == null) begin(x, y, timeMs) else appendInternal(x, y, timeMs, force = false)
    }

    fun end() {
        activeGestureId = null
    }

    fun cancelActive() {
        val cancelledId = activeGestureId ?: return
        points.removeAll { it.gestureId == cancelledId }
        activeGestureId = null
    }

    fun clear() {
        points.clear()
        activeGestureId = null
    }

    fun renderPoints(nowMs: Long): List<LaserTrailRenderPoint> {
        prune(nowMs)
        val activeId = activeGestureId
        val activeHead = if (activeId == null) null else points.lastOrNull { it.gestureId == activeId }
        return points.map { point ->
            val alpha = if (point === activeHead) 1f else {
                (1f - (nowMs - point.timeMs).coerceAtLeast(0L).toFloat() / lifetimeMs).coerceIn(0f, 1f)
            }
            LaserTrailRenderPoint(point.x, point.y, alpha, point.gestureId)
        }.filter { it.alpha > 0f }
    }

    private fun appendInternal(x: Float, y: Float, timeMs: Long, force: Boolean) {
        val gestureId = activeGestureId ?: return
        val normalizedX = x.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: return
        val normalizedY = y.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: return
        val previous = points.lastOrNull { it.gestureId == gestureId }
        if (!force && previous != null && hypot((normalizedX - previous.x).toDouble(), (normalizedY - previous.y).toDouble()) < minimumDistance) return
        points.addLast(LaserTrailPoint(normalizedX, normalizedY, timeMs.coerceAtLeast(previous?.timeMs ?: 0L), gestureId))
        while (points.size > maximumPoints) points.removeFirst()
    }

    private fun prune(nowMs: Long) {
        val activeId = activeGestureId
        while (points.isNotEmpty()) {
            val first = points.first()
            val keepActiveHead = first.gestureId == activeId && points.none { it !== first && it.gestureId == activeId }
            if (keepActiveHead || nowMs - first.timeMs < lifetimeMs) break
            points.removeFirst()
        }
    }

    companion object {
        const val DEFAULT_LIFETIME_MS = 1_200L
        const val DEFAULT_MAXIMUM_POINTS = 2_048
        const val DEFAULT_MINIMUM_DISTANCE = .0008f
    }
}
