package app.inkloom

import kotlin.math.ceil

/** Nearest-rank percentile index for an ascending, non-empty sample window. */
internal fun nearestRankPercentileIndex(size: Int, percentile: Double): Int {
    require(size > 0)
    require(percentile > 0.0 && percentile <= 1.0)
    return (ceil(size * percentile).toInt() - 1).coerceIn(0, size - 1)
}

internal fun p95Index(size: Int): Int = nearestRankPercentileIndex(size, .95)
