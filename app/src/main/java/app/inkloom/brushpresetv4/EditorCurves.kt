package app.inkloom.brushpresetv4

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushDynamics

/**
 * Pressure-curve presets for the Brush Editor.
 *
 * The editor must preview with the exact same evaluator the production renderer uses, so the
 * applied curve is evaluated with the same piecewise-linear contract consumed by
 * [app.inkloom.engine.v5.material.PressureResponse]. Linear is the empty control-point list, which
 * the evaluator treats as identity (`curve([], x) == x`).
 */
enum class PressureCurveShape(val label: String) {
    LINEAR("Linear"),
    SOFT("Soft"),
    HARD("Hard"),
    CUSTOM("Custom"),
}

object EditorCurves {

    /** Control points for a preset shape. Linear is empty (identity) by design. */
    fun controlPoints(shape: PressureCurveShape): List<BrushCurvePoint> = when (shape) {
        PressureCurveShape.LINEAR -> emptyList()
        PressureCurveShape.SOFT -> listOf(
            BrushCurvePoint(0f, 0f), BrushCurvePoint(.5f, .72f), BrushCurvePoint(1f, 1f),
        )
        PressureCurveShape.HARD -> listOf(
            BrushCurvePoint(0f, 0f), BrushCurvePoint(.5f, .26f), BrushCurvePoint(1f, 1f),
        )
        PressureCurveShape.CUSTOM -> listOf(
            BrushCurvePoint(0f, 0f), BrushCurvePoint(.25f, .25f), BrushCurvePoint(.5f, .5f),
            BrushCurvePoint(.75f, .75f), BrushCurvePoint(1f, 1f),
        )
    }

    /** Detects which preset a control-point list matches (CUSTOM when nothing else fits). */
    fun shapeOf(points: List<BrushCurvePoint>): PressureCurveShape {
        val linear: List<BrushCurvePoint> = emptyList()
        return when (points) {
            linear -> PressureCurveShape.LINEAR
            controlPoints(PressureCurveShape.SOFT) -> PressureCurveShape.SOFT
            controlPoints(PressureCurveShape.HARD) -> PressureCurveShape.HARD
            else -> PressureCurveShape.CUSTOM
        }
    }

    /** Evaluates a curve with the production evaluator (linear interpolation, out-of-range clamped). */
    fun evaluate(points: List<BrushCurvePoint>, input: Float): Float =
        BrushDynamics.curve(points, input.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    /** Applies the pressure mapping formula exactly as production: minimum + (1-minimum)*curve. */
    fun pressureFactor(minimum: Float, points: List<BrushCurvePoint>, input: Float): Float =
        BrushDynamics.pressure(enabled = true, minimum = minimum, curve = points, input = input)
}
