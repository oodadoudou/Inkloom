package app.inkloom.document

import kotlin.math.abs
import kotlin.math.hypot

enum class PerspectiveGuideMode(val vanishingPointCount: Int) {
    NONE(0),
    ONE_POINT(1),
    TWO_POINT(2),
    THREE_POINT(3),
}

data class PerspectiveGuidePoint(val x: Float, val y: Float)

data class PerspectiveGuideState(
    val mode: PerspectiveGuideMode = PerspectiveGuideMode.NONE,
    val assisted: Boolean = true,
    val editing: Boolean = false,
    val points: List<PerspectiveGuidePoint> = defaultPoints(PerspectiveGuideMode.ONE_POINT),
) {
    fun normalized(): PerspectiveGuideState {
        val defaults = defaultPoints(mode)
        val safe = List(3) { index ->
            val source = points.getOrNull(index) ?: defaults[index]
            PerspectiveGuidePoint(
                source.x.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: defaults[index].x,
                source.y.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: defaults[index].y,
            )
        }
        return copy(points = safe)
    }

    fun activePoints(): List<PerspectiveGuidePoint> = normalized().points.take(mode.vanishingPointCount)

    fun movePoint(index: Int, x: Float, y: Float): PerspectiveGuideState {
        if (index !in 0 until mode.vanishingPointCount) return normalized()
        val changed = normalized().points.toMutableList()
        changed[index] = PerspectiveGuidePoint(
            x.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: changed[index].x,
            y.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: changed[index].y,
        )
        return copy(points = changed).normalized()
    }

    companion object {
        fun defaultFor(mode: PerspectiveGuideMode): PerspectiveGuideState = PerspectiveGuideState(
            mode = mode,
            points = defaultPoints(mode),
        )

        fun defaultPoints(mode: PerspectiveGuideMode): List<PerspectiveGuidePoint> = when (mode) {
            PerspectiveGuideMode.NONE, PerspectiveGuideMode.ONE_POINT -> listOf(
                PerspectiveGuidePoint(.5f, .42f),
                PerspectiveGuidePoint(.86f, .42f),
                    PerspectiveGuidePoint(.5f, .12f),
            )
            PerspectiveGuideMode.TWO_POINT -> listOf(
                PerspectiveGuidePoint(.12f, .42f),
                PerspectiveGuidePoint(.88f, .42f),
                    PerspectiveGuidePoint(.5f, .12f),
            )
            PerspectiveGuideMode.THREE_POINT -> listOf(
                PerspectiveGuidePoint(.1f, .48f),
                PerspectiveGuidePoint(.9f, .48f),
                    PerspectiveGuidePoint(.5f, .12f),
            )
        }
    }
}

class PerspectiveSnapConstraint internal constructor(
    private val startX: Float,
    private val startY: Float,
    private val directionX: Float,
    private val directionY: Float,
    private val pageWidth: Float,
    private val pageHeight: Float,
) {
    fun apply(x: Float, y: Float): RulerGuidePoint {
        val px = (x - startX) * pageWidth
        val py = (y - startY) * pageHeight
        val denominator = directionX * directionX + directionY * directionY
        if (denominator <= .000001f) return RulerGuidePoint(startX, startY)
        val amount = (px * directionX + py * directionY) / denominator
        return RulerGuidePoint(
            (startX + directionX * amount / pageWidth).coerceIn(0f, 1f),
            (startY + directionY * amount / pageHeight).coerceIn(0f, 1f),
        )
    }
}

object PerspectiveGuides {
    /** Locks one assisted direction from the first meaningful movement of a contact. */
    fun constraintAt(
        state: PerspectiveGuideState,
        startX: Float,
        startY: Float,
        movementX: Float,
        movementY: Float,
        pageWidth: Float,
        pageHeight: Float,
        minimumMovementPixels: Float = 3f,
    ): PerspectiveSnapConstraint? {
        val normalized = state.normalized()
        if (normalized.mode == PerspectiveGuideMode.NONE || !normalized.assisted || pageWidth <= 0f || pageHeight <= 0f) return null
        val moveX = (movementX - startX) * pageWidth
        val moveY = (movementY - startY) * pageHeight
        val moveLength = hypot(moveX.toDouble(), moveY.toDouble()).toFloat()
        if (!moveLength.isFinite() || moveLength < minimumMovementPixels.coerceAtLeast(0f)) return null
        val candidates = buildList {
            normalized.activePoints().forEach { point ->
                add((point.x - startX) * pageWidth to (point.y - startY) * pageHeight)
            }
            when (normalized.mode) {
                PerspectiveGuideMode.ONE_POINT -> { add(1f to 0f); add(0f to 1f) }
                PerspectiveGuideMode.TWO_POINT -> add(0f to 1f)
                PerspectiveGuideMode.THREE_POINT, PerspectiveGuideMode.NONE -> Unit
            }
        }.filter { (x, y) -> hypot(x.toDouble(), y.toDouble()) > .0001 }
        val direction = candidates.maxByOrNull { (x, y) ->
            abs(moveX * x + moveY * y) / (moveLength * hypot(x.toDouble(), y.toDouble()).toFloat())
        } ?: return null
        return PerspectiveSnapConstraint(startX, startY, direction.first, direction.second, pageWidth, pageHeight)
    }

    fun hitPoint(state: PerspectiveGuideState, xPx: Float, yPx: Float, width: Float, height: Float, radiusPx: Float): Int? {
        if (!state.editing || state.mode == PerspectiveGuideMode.NONE || width <= 0f || height <= 0f) return null
        return state.activePoints().indices.minByOrNull { index ->
            val point = state.activePoints()[index]
            hypot((xPx - point.x * width).toDouble(), (yPx - point.y * height).toDouble())
        }?.takeIf { index ->
            val point = state.activePoints()[index]
            hypot((xPx - point.x * width).toDouble(), (yPx - point.y * height).toDouble()) <= radiusPx.coerceAtLeast(0f)
        }
    }
}
