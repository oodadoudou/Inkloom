package app.inkloom.document

import kotlin.math.roundToInt

/** Original visible page pixels, without silently scaling the authored canvas. */
internal data class PagePngExportSize(val width: Int, val height: Int) {
    companion object {
        // Mirrors the maximum editable raster budget. Raising this requires a real large-canvas
        // compositor, not a larger Bitmap allocation at export time.
        private const val MAX_DIMENSION = 8192
        private const val MAX_PIXELS = 67_108_864L

        fun from(page: InkPage): PagePngExportSize {
            val visibleWidth = PageCropTransform.visibleWidth(page)
            val visibleHeight = PageCropTransform.visibleHeight(page)
            require(visibleWidth.isFinite() && visibleHeight.isFinite() && visibleWidth > 0f && visibleHeight > 0f) {
                "The visible page has invalid dimensions"
            }
            require(visibleWidth <= MAX_DIMENSION && visibleHeight <= MAX_DIMENSION) {
                "Original-size PNG exceeds the current 8192-pixel canvas limit; reduce the canvas size before exporting"
            }
            val width = visibleWidth.roundToInt().coerceAtLeast(1)
            val height = visibleHeight.roundToInt().coerceAtLeast(1)
            require(width.toLong() * height <= MAX_PIXELS) {
                "Original-size PNG exceeds the current canvas pixel budget"
            }
            return PagePngExportSize(width, height)
        }
    }
}
