package app.inkloom.document

import kotlin.math.roundToInt

/** Same inverse viewport geometry as pen input, without screen origin or system-bar offsets. */
internal object RasterClipboardPlacement {
    fun atViewCenter(viewWidth: Float, viewHeight: Float, rasterWidth: Int, rasterHeight: Int,
        payloadWidth: Int, payloadHeight: Int, viewport: ViewportState): Pair<Int, Int> {
        require(viewWidth > 0 && viewHeight > 0 && rasterWidth > 0 && rasterHeight > 0)
        require(payloadWidth > 0 && payloadHeight > 0)
        val center = ViewportTransform.viewToPage(viewWidth / 2, viewHeight / 2, viewWidth, viewHeight, viewport)
        // When the viewport center falls outside the page, keep the paste attached to its nearest
        // visible page edge instead of creating an entirely inaccessible clipped payload.
        val x = center.first.coerceIn(0f, viewWidth) / viewWidth * rasterWidth
        val y = center.second.coerceIn(0f, viewHeight) / viewHeight * rasterHeight
        return (x - payloadWidth / 2f).roundToInt().coerceIn(-payloadWidth + 1, rasterWidth - 1) to
            (y - payloadHeight / 2f).roundToInt().coerceIn(-payloadHeight + 1, rasterHeight - 1)
    }
}
