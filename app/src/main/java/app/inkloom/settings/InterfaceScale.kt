package app.inkloom.settings

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How large the whole interface is drawn. Android sizes a tablet's interface from a density bucket,
 * so on a large high-resolution pen display (such as a 14-inch 2880 × 1800 panel at 2×) every
 * button, row and font comes out noticeably larger than on the 10-inch tablets those sizes were
 * chosen for. Automatic sizing measures the screen's real pixel density and draws one interface
 * unit at [TARGET_MM_PER_DP] millimetres; a person can choose any size in [MIN]..[MAX] instead.
 */
object InterfaceScale {
    /** The setting value meaning "measure this screen". */
    const val AUTO = 0f
    const val MIN = .6f
    const val MAX = 1.2f
    const val STEP = .05f
    /** One interface unit on a tablet, in millimetres; Android's nominal unit is 0.159 mm. */
    const val TARGET_MM_PER_DP = .155f
    const val PREFERENCE_KEY = "interfaceScale"

    /**
     * The scale for a screen: phones keep Android's sizes; a tablet is scaled so one unit is
     * [TARGET_MM_PER_DP] mm, never larger than Android's own size. A screen that does not report a
     * plausible physical density keeps Android's sizes.
     */
    fun auto(density: Float, xdpi: Float, ydpi: Float, smallestWidthDp: Int): Float {
        if (smallestWidthDp < 600 || density <= 0f) return 1f
        if (xdpi !in 100f..800f || ydpi !in 100f..800f) return 1f
        val millimetresPerDp = 25.4f * density / sqrt(xdpi * ydpi)
        return snap(TARGET_MM_PER_DP / millimetresPerDp).coerceIn(MIN, 1f)
    }

    fun resolve(setting: Float, density: Float, xdpi: Float, ydpi: Float, smallestWidthDp: Int): Float =
        if (setting <= AUTO || !setting.isFinite()) auto(density, xdpi, ydpi, smallestWidthDp) else snap(setting).coerceIn(MIN, MAX)

    /** Sizes move in 5% steps so text and icons stay crisp and settings compare exactly. */
    fun snap(scale: Float): Float = (scale / STEP).roundToInt() * STEP

    /** A stored setting: 0 for automatic, otherwise a snapped size within range. */
    fun stored(value: Float): Float = if (value <= AUTO || !value.isFinite()) AUTO else snap(value).coerceIn(MIN, MAX)

    fun percent(scale: Float): Int = (scale * 100f).roundToInt()
}
