package app.inkloom.document

import android.graphics.Bitmap
data class MaskRegion(val points: List<StrokePoint>)

object SelectionMaskRenderer {
    fun render(regions: List<MaskRegion>, featherPixels: Float, pageWidth: Float, pageHeight: Float): Bitmap? {
        var hardMask: Bitmap? = null
        regions.filter { it.points.size >= 3 }.forEach { region ->
            val addition = SelectionMaskCompositor.polygon(region.points, pageWidth, pageHeight)
            val combined = SelectionMaskCompositor.compose(hardMask, addition, app.inkloom.settings.SelectionMode.ADD)
            hardMask?.recycle()
            addition?.recycle()
            hardMask = combined
        }
        val result = SelectionMaskCompositor.feather(hardMask, featherPixels, pageWidth)
        hardMask?.recycle()
        return result
    }
}
