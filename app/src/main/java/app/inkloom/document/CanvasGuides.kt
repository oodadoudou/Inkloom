package app.inkloom.document

import kotlin.math.abs
import kotlin.math.round

data class CanvasGuideState(
    val gridSnapEnabled: Boolean = false,
    val gridSpacing: Float = 64f,
    val alignmentGuidesEnabled: Boolean = false,
    val verticalGuideX: Float = .5f,
    val horizontalGuideY: Float = .5f,
) {
    fun normalized(): CanvasGuideState = copy(
        gridSpacing = gridSpacing.finiteOr(64f).coerceIn(16f, 400f),
        verticalGuideX = verticalGuideX.finiteOr(.5f).coerceIn(0f, 1f),
        horizontalGuideY = horizontalGuideY.finiteOr(.5f).coerceIn(0f, 1f),
    )
}

enum class CanvasGuideAxis { X, Y }
enum class CanvasGuideSource { GRID, ALIGNMENT_GUIDE }
enum class CanvasGuideGestureMode { VERTICAL, HORIZONTAL }

data class CanvasSnapConstraint(
    val axis: CanvasGuideAxis,
    val value: Float,
    val source: CanvasGuideSource,
) {
    fun apply(x: Float, y: Float): RulerGuidePoint = when (axis) {
        CanvasGuideAxis.X -> RulerGuidePoint(value, y)
        CanvasGuideAxis.Y -> RulerGuidePoint(x, value)
    }
}

object CanvasGuides {
    fun constraintAt(
        state: CanvasGuideState,
        x: Float,
        y: Float,
        pageWidth: Float,
        pageHeight: Float,
        viewWidth: Float,
        viewHeight: Float,
        tolerancePx: Float,
    ): CanvasSnapConstraint? {
        val normalized = state.normalized()
        if (viewWidth <= 0f || viewHeight <= 0f || pageWidth <= 0f || pageHeight <= 0f || tolerancePx < 0f) return null
        val candidates = buildList {
            if (normalized.gridSnapEnabled) {
                val gridX = nearestGrid(x, normalized.gridSpacing / pageWidth)
                val gridY = nearestGrid(y, normalized.gridSpacing / pageHeight)
                add(Candidate(CanvasGuideAxis.X, gridX, abs(x - gridX) * viewWidth, CanvasGuideSource.GRID))
                add(Candidate(CanvasGuideAxis.Y, gridY, abs(y - gridY) * viewHeight, CanvasGuideSource.GRID))
            }
            if (normalized.alignmentGuidesEnabled) {
                add(Candidate(CanvasGuideAxis.X, normalized.verticalGuideX, abs(x - normalized.verticalGuideX) * viewWidth, CanvasGuideSource.ALIGNMENT_GUIDE))
                add(Candidate(CanvasGuideAxis.Y, normalized.horizontalGuideY, abs(y - normalized.horizontalGuideY) * viewHeight, CanvasGuideSource.ALIGNMENT_GUIDE))
            }
        }
        return candidates.minByOrNull(Candidate::distancePx)
            ?.takeIf { it.distancePx <= tolerancePx }
            ?.let { CanvasSnapConstraint(it.axis, it.value.coerceIn(0f, 1f), it.source) }
    }

    fun snapPoint(
        state: CanvasGuideState,
        x: Float,
        y: Float,
        pageWidth: Float,
        pageHeight: Float,
        viewWidth: Float,
        viewHeight: Float,
        tolerancePx: Float,
    ): RulerGuidePoint {
        val normalized = state.normalized()
        if (viewWidth <= 0f || viewHeight <= 0f || pageWidth <= 0f || pageHeight <= 0f || tolerancePx < 0f) return RulerGuidePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val xCandidates = buildList {
            if (normalized.gridSnapEnabled) add(nearestGrid(x, normalized.gridSpacing / pageWidth))
            if (normalized.alignmentGuidesEnabled) add(normalized.verticalGuideX)
        }
        val yCandidates = buildList {
            if (normalized.gridSnapEnabled) add(nearestGrid(y, normalized.gridSpacing / pageHeight))
            if (normalized.alignmentGuidesEnabled) add(normalized.horizontalGuideY)
        }
        val snappedX = xCandidates.minByOrNull { abs(x - it) }?.takeIf { abs(x - it) * viewWidth <= tolerancePx } ?: x
        val snappedY = yCandidates.minByOrNull { abs(y - it) }?.takeIf { abs(y - it) * viewHeight <= tolerancePx } ?: y
        return RulerGuidePoint(snappedX.coerceIn(0f, 1f), snappedY.coerceIn(0f, 1f))
    }

    fun translationForBounds(
        state: CanvasGuideState,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        pageWidth: Float,
        pageHeight: Float,
        viewWidth: Float,
        viewHeight: Float,
        tolerancePx: Float,
    ): RulerGuidePoint {
        val normalized = state.normalized()
        if (viewWidth <= 0f || viewHeight <= 0f || pageWidth <= 0f || pageHeight <= 0f || tolerancePx < 0f) return RulerGuidePoint(0f, 0f)
        val xAnchors = listOf(left, (left + right) / 2f, right)
        val yAnchors = listOf(top, (top + bottom) / 2f, bottom)
        val xOffsets = buildList {
            xAnchors.forEach { anchor ->
                if (normalized.gridSnapEnabled) add(nearestGrid(anchor, normalized.gridSpacing / pageWidth) - anchor)
                if (normalized.alignmentGuidesEnabled) add(normalized.verticalGuideX - anchor)
            }
        }
        val yOffsets = buildList {
            yAnchors.forEach { anchor ->
                if (normalized.gridSnapEnabled) add(nearestGrid(anchor, normalized.gridSpacing / pageHeight) - anchor)
                if (normalized.alignmentGuidesEnabled) add(normalized.horizontalGuideY - anchor)
            }
        }
        val dx = xOffsets.minByOrNull(::abs)?.takeIf { abs(it) * viewWidth <= tolerancePx } ?: 0f
        val dy = yOffsets.minByOrNull(::abs)?.takeIf { abs(it) * viewHeight <= tolerancePx } ?: 0f
        return RulerGuidePoint(dx, dy)
    }

    fun hitTest(state: CanvasGuideState, xPx: Float, yPx: Float, width: Float, height: Float, radiusPx: Float): CanvasGuideGestureMode? {
        if (!state.alignmentGuidesEnabled || width <= 0f || height <= 0f || radiusPx < 0f) return null
        val normalized = state.normalized()
        val verticalDistance = abs(xPx - normalized.verticalGuideX * width)
        val horizontalDistance = abs(yPx - normalized.horizontalGuideY * height)
        return when {
            verticalDistance > radiusPx && horizontalDistance > radiusPx -> null
            verticalDistance <= horizontalDistance -> CanvasGuideGestureMode.VERTICAL
            else -> CanvasGuideGestureMode.HORIZONTAL
        }
    }

    fun move(state: CanvasGuideState, mode: CanvasGuideGestureMode, xPx: Float, yPx: Float, width: Float, height: Float): CanvasGuideState {
        val normalized = state.normalized()
        if (width <= 0f || height <= 0f) return normalized
        return when (mode) {
            CanvasGuideGestureMode.VERTICAL -> normalized.copy(verticalGuideX = (xPx / width).finiteOr(normalized.verticalGuideX).coerceIn(0f, 1f))
            CanvasGuideGestureMode.HORIZONTAL -> normalized.copy(horizontalGuideY = (yPx / height).finiteOr(normalized.horizontalGuideY).coerceIn(0f, 1f))
        }
    }

    private fun nearestGrid(value: Float, step: Float): Float {
        val safeValue = value.finiteOr(0f)
        val safeStep = step.finiteOr(1f).coerceAtLeast(.0001f)
        return round(safeValue / safeStep) * safeStep
    }

    private data class Candidate(val axis: CanvasGuideAxis, val value: Float, val distancePx: Float, val source: CanvasGuideSource)
}

private fun Float.finiteOr(fallback: Float): Float = takeIf(Float::isFinite) ?: fallback
