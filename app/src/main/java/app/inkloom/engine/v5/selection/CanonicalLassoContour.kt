package app.inkloom.engine.v5.selection

import app.inkloom.engine.v5.coordinates.DocumentPoint
import kotlin.math.hypot

enum class LassoWindingRule { EVEN_ODD }

/**
 * Immutable document-space geometry shared by the live outline and final mask rasterizer.
 * Closing the contour is metadata: no fitted or synthetic points replace the captured trajectory.
 */
data class CanonicalLassoContour(
    val points: List<DocumentPoint>,
    val closed: Boolean,
    val windingRule: LassoWindingRule = LassoWindingRule.EVEN_ODD,
) {
    init {
        require(points.all { it.x.isFinite() && it.y.isFinite() })
        if (closed) require(points.size >= 3) { "A closed lasso needs at least three points" }
    }
}

/** Incremental free-lasso capture. Samples are filtered once in document pixels. */
class LassoSession(
    val documentWidth: Float,
    val documentHeight: Float,
    private val minimumSampleDistance: Float = .75f,
) {
    init {
        require(documentWidth.isFinite() && documentWidth > 0f)
        require(documentHeight.isFinite() && documentHeight > 0f)
        require(minimumSampleDistance.isFinite() && minimumSampleDistance >= 0f)
    }

    private val captured = ArrayList<DocumentPoint>()
    private var state = State.ACTIVE
    private var contactStart: List<DocumentPoint>? = null
    private val undoContacts = ArrayList<List<DocumentPoint>>()
    private val redoContacts = ArrayList<List<DocumentPoint>>()

    fun beginContact() { contactStart = captured.toList() }
    fun endContact() {
        val before = contactStart ?: return
        contactStart = null
        if (before != captured) { undoContacts += before; redoContacts.clear() }
    }
    fun cancelContact() {
        contactStart?.let { captured.clear(); captured.addAll(it) }
        contactStart = null
    }
    fun undoContact(): Boolean {
        if (undoContacts.isEmpty()) return false
        redoContacts += captured.toList()
        captured.clear(); captured.addAll(undoContacts.removeAt(undoContacts.lastIndex))
        return true
    }
    fun redoContact(): Boolean {
        if (redoContacts.isEmpty()) return false
        undoContacts += captured.toList()
        captured.clear(); captured.addAll(redoContacts.removeAt(redoContacts.lastIndex))
        return true
    }

    val isActive: Boolean get() = state == State.ACTIVE

    fun append(point: DocumentPoint, force: Boolean = false): Boolean {
        check(state == State.ACTIVE) { "Lasso session is no longer active" }
        val bounded = DocumentPoint(
            point.x.coerceIn(0f, documentWidth),
            point.y.coerceIn(0f, documentHeight),
        )
        val previous = captured.lastOrNull()
        if (!force && previous != null && hypot(
                (bounded.x - previous.x).toDouble(),
                (bounded.y - previous.y).toDouble(),
            ) < minimumSampleDistance
        ) return false
        if (previous == bounded) return false
        captured += bounded
        return true
    }

    fun preview(): CanonicalLassoContour = CanonicalLassoContour(captured.toList(), closed = false)

    fun finish(finalPoint: DocumentPoint? = null): CanonicalLassoContour? {
        check(state == State.ACTIVE) { "Lasso session is no longer active" }
        finalPoint?.let { append(it, force = true) }
        state = State.FINISHED
        if (captured.size < 3 || absoluteArea(captured) < MINIMUM_AREA_PIXELS) return null
        return CanonicalLassoContour(captured.toList(), closed = true)
    }

    fun cancel() {
        if (state == State.ACTIVE) state = State.CANCELLED
        captured.clear()
    }

    private fun absoluteArea(points: List<DocumentPoint>): Float {
        var twiceArea = 0.0
        var previous = points.last()
        points.forEach { current ->
            twiceArea += previous.x.toDouble() * current.y - current.x.toDouble() * previous.y
            previous = current
        }
        return (kotlin.math.abs(twiceArea) * .5).toFloat()
    }

    private enum class State { ACTIVE, FINISHED, CANCELLED }

    private companion object {
        const val MINIMUM_AREA_PIXELS = 1f
    }
}
