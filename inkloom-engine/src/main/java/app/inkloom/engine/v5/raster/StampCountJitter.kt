package app.inkloom.engine.v5.raster

import kotlin.math.ceil

/** Count is an upper bound: 10 at 50% varies from 5 through 10, never above 10. */
fun jitteredStampCount(count: Int, jitter: Float, randomUnit: Float): Int {
    if (jitter <= 0f || count == 1) return count
    val minimum = ceil(count * (1f - jitter)).toInt().coerceIn(1, count)
    return (minimum + randomUnit * (count - minimum + 1)).toInt().coerceIn(minimum, count)
}
