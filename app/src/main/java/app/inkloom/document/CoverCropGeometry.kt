package app.inkloom.document

import kotlin.math.min

/** Source-relative crop for the 92:120 notebook cover frame. */
object CoverCropGeometry {
    const val ASPECT = 92f / 120f

    fun rect(width: Int, height: Int, zoom: Float, centerX: Float, centerY: Float): PageCrop {
        require(width > 0 && height > 0)
        val boundedZoom = zoom.coerceIn(1f, 4f)
        val cropWidth = min(width.toFloat(), height * ASPECT) / boundedZoom / width
        val cropHeight = min(height.toFloat(), width / ASPECT) / boundedZoom / height
        val left = (centerX - cropWidth / 2f).coerceIn(0f, 1f - cropWidth)
        val top = (centerY - cropHeight / 2f).coerceIn(0f, 1f - cropHeight)
        return PageCrop(left, top, left + cropWidth, top + cropHeight)
    }

    fun drag(crop: PageCrop, dx: Float, dy: Float, frameWidth: Float, frameHeight: Float): Pair<Float, Float> {
        val centerX = (crop.left + crop.right) / 2f - dx / frameWidth.coerceAtLeast(1f) * (crop.right - crop.left)
        val centerY = (crop.top + crop.bottom) / 2f - dy / frameHeight.coerceAtLeast(1f) * (crop.bottom - crop.top)
        return centerX to centerY
    }
}
