package app.inkloom.ui

import app.inkloom.document.InkPage
import app.inkloom.document.PageCropTransform
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Chooses the editable raster size independently from the current screen zoom.
 *
 * The old fixed 1200 px projection downsampled Inkloom's 1600 px default paper before the first
 * mark was stored. That made a correctly authored hard brush look soft when the page was presented
 * on a high-density display. Keep normal pages at their authored resolution, give smaller imported
 * pages the same useful drawing density as the default paper, and bound genuinely large pages by
 * the limits already enforced by the raster compositor.
 */
internal object CanvasRasterResolution {
    const val BASELINE_DRAWING_WIDTH = 1600
    const val MAXIMUM_DIMENSION = 8192
    const val MAXIMUM_PIXELS = 67_108_864L

    fun forPage(page: InkPage): Int = forDimensions(
        PageCropTransform.visibleWidth(page),
        PageCropTransform.visibleHeight(page),
    )

    fun forDimensions(visibleWidth: Float, visibleHeight: Float): Int {
        require(visibleWidth.isFinite() && visibleHeight.isFinite() && visibleWidth > 0f && visibleHeight > 0f)
        val requestedWidth = maxOf(visibleWidth.toDouble(), BASELINE_DRAWING_WIDTH.toDouble())
        val requestedHeight = requestedWidth * visibleHeight / visibleWidth
        val boundedScale = minOf(
            1.0,
            MAXIMUM_DIMENSION / requestedWidth,
            MAXIMUM_DIMENSION / requestedHeight,
            sqrt(MAXIMUM_PIXELS / (requestedWidth * requestedHeight)),
        )
        return floor(requestedWidth * boundedScale)
            .toInt()
            .coerceIn(1, MAXIMUM_DIMENSION)
    }
}
