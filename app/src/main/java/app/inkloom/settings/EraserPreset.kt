package app.inkloom.settings

enum class EraserPreset(
    val usesMask: Boolean,
    val splitsStroke: Boolean,
    val hardness: Float,
    val opacity: Float,
    val pressureSize: Boolean,
) {
    NORMAL_FREE(usesMask = true, splitsStroke = false, hardness = .72f, opacity = 1f, pressureSize = true),
    HARD(usesMask = true, splitsStroke = false, hardness = 1f, opacity = 1f, pressureSize = false),
    SOFT(usesMask = true, splitsStroke = false, hardness = .08f, opacity = .42f, pressureSize = true),
    HARD_EDGE(usesMask = false, splitsStroke = true, hardness = 1f, opacity = 1f, pressureSize = false),
    SOFT_EDGE(usesMask = true, splitsStroke = false, hardness = .35f, opacity = 1f, pressureSize = false),
    STROKE(usesMask = false, splitsStroke = false, hardness = 1f, opacity = 1f, pressureSize = false),
}
