package app.inkloom.document

import kotlin.math.max

/** Geometry shared by the page-local cursor and the full-viewport low-latency cursor surface.
 * It deliberately models the deterministic footprint only: random per-dab scatter/rotation does
 * not have a single truthful outline. */
data class BrushCursorFootprint(
    val width: Float,
    val height: Float,
)

object BrushCursorGeometry {
    /** Mirrors StampPaintOp's resolved footprint: the source mask keeps its aspect ratio and the
     * authored V5 roundness compresses the local Y axis. */
    fun stampFootprint(
        diameter: Float,
        tipWidth: Int?,
        tipHeight: Int?,
        roundness: Float,
    ): BrushCursorFootprint {
        val safeDiameter = diameter.takeIf(Float::isFinite)?.coerceAtLeast(.1f) ?: .1f
        val width = tipWidth?.coerceAtLeast(1) ?: 1
        val height = tipHeight?.coerceAtLeast(1) ?: 1
        val aspect = width.toFloat() / height
        return BrushCursorFootprint(
            width = (if (aspect >= 1f) safeDiameter else safeDiameter * aspect).coerceAtLeast(.1f),
            height = (if (aspect <= 1f) safeDiameter else safeDiameter / aspect)
                .times(roundness.takeIf(Float::isFinite)?.coerceIn(.01f, 1f) ?: 1f)
                .coerceAtLeast(.1f),
        )
    }

    fun footprint(
        diameter: Float,
        tipWidth: Int?,
        tipHeight: Int?,
        thickness: Float,
        thicknessVertical: Boolean,
    ): BrushCursorFootprint {
        val safeDiameter = diameter.takeIf(Float::isFinite)?.coerceAtLeast(.1f) ?: .1f
        val width = tipWidth?.coerceAtLeast(1) ?: 1
        val height = tipHeight?.coerceAtLeast(1) ?: 1
        val scale = BrushTipTransform.scale(width, height, safeDiameter, thickness, thicknessVertical)
        return BrushCursorFootprint(
            width = (width * scale.x).coerceAtLeast(.1f),
            height = (height * scale.y).coerceAtLeast(.1f),
        )
    }

    /** Fixed brush-tip direction on the physical screen. Dynamic stylus/path direction is added
     * separately from the raw proxy packet so it never inherits stabilization delay. */
    fun fixedScreenRotation(
        brushRotationDegrees: Float,
        viewportRotationDegrees: Float,
        orientToScreen: Boolean,
    ): Float = normalizeDegrees(
        brushRotationDegrees.takeIf(Float::isFinite) ?: 0f,
        if (orientToScreen) 0f else viewportRotationDegrees.takeIf(Float::isFinite) ?: 0f,
    )

    fun normalizeDegrees(vararg values: Float): Float {
        var degrees = values.filter(Float::isFinite).sum()
        while (degrees >= 180f) degrees -= 360f
        while (degrees < -180f) degrees += 360f
        return degrees
    }

    fun hasMeaningfulDirection(deltaX: Float, deltaY: Float): Boolean =
        max(kotlin.math.abs(deltaX), kotlin.math.abs(deltaY)) >= DIRECTION_EPSILON_PX

    private const val DIRECTION_EPSILON_PX = .25f
}
