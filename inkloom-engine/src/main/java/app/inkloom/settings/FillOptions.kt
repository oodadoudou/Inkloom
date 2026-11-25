package app.inkloom.settings

/** Which pixels a bucket fill treats as the region (CSP Fill > Target color). */
enum class FillTargetMode { TOUCHED_COLOR, TRANSPARENT_ONLY, SPECIFIED_COLOR }

/** The shape area scaling grows or shrinks a fill with. */
enum class FillExpansionShape { ROUND, RECTANGLE }
