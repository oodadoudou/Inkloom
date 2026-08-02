package app.inkloom.importer

import app.inkloom.document.BrushTaperMode

data class SutTaperSettings(
    val mode: BrushTaperMode,
    val startEnabled: Boolean,
    val startLength: Float,
    val startLengthUnit: Int,
    val startMinimum: Float,
    val endEnabled: Boolean,
    val endLength: Float,
    val endLengthUnit: Int,
    val endMinimum: Float,
)

object SutTaperDecoder {
    fun decode(
        rawMode: Int,
        useStart: Boolean,
        rawStartLength: Float,
        rawStartRatio: Float,
        startLengthUnit: Int,
        useEnd: Boolean,
        rawEndLength: Float,
        rawEndRatio: Float,
        endLengthUnit: Int,
        sizeTargetEnabled: Boolean,
        sizeTargetMinimum: Float,
    ): SutTaperSettings {
        val mode = when (rawMode) {
            0 -> BrushTaperMode.DISTANCE
            1 -> BrushTaperMode.PERCENTAGE
            else -> BrushTaperMode.FADE
        }
        fun length(rawDistance: Float, rawRatio: Float): Float = if (mode == BrushTaperMode.DISTANCE) rawDistance.coerceIn(.1f, 4000f) else (rawRatio / 100f).coerceIn(.001f, 1f)
        return SutTaperSettings(
            mode = mode,
            startEnabled = useStart && sizeTargetEnabled && mode != BrushTaperMode.FADE,
            startLength = length(rawStartLength, rawStartRatio),
            startLengthUnit = startLengthUnit,
            startMinimum = sizeTargetMinimum.coerceIn(0f, 1f),
            endEnabled = useEnd && sizeTargetEnabled,
            endLength = length(rawEndLength, rawEndRatio),
            endLengthUnit = endLengthUnit,
            endMinimum = sizeTargetMinimum.coerceIn(0f, 1f),
        )
    }
}
