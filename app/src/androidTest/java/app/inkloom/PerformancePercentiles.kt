package app.inkloom

import kotlin.math.ceil

/** Nearest-rank percentile index for an ascending, non-empty sample window. */
internal fun nearestRankPercentileIndex(size: Int, percentile: Double): Int {
    require(size > 0)
    require(percentile > 0.0 && percentile <= 1.0)
    return (ceil(size * percentile).toInt() - 1).coerceIn(0, size - 1)
}

internal fun p95Index(size: Int): Int = nearestRankPercentileIndex(size, .95)

/** Emulator wall time includes host graphics, scheduling and virtual disk latency.
 * Keep the physical-device budget separate; passing an emulator gate is not 120 Hz validation. */
internal val isAndroidEmulator: Boolean
    get() = android.os.Build.FINGERPRINT.startsWith("Android/sdk_") ||
        android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true)

/** A frame budget for the tablet, doubled on an emulator whose host may be busy with other work. */
internal fun frameBudgetMs(deviceMs: Float): Float = if (isAndroidEmulator) deviceMs * 2f else deviceMs
