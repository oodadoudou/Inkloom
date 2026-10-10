package app.inkloom.document

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable data class PageBackgroundFrame(val left: Float, val top: Float, val width: Float, val height: Float)

@Serializable enum class CanvasUnit { PIXELS, MILLIMETERS, CENTIMETERS, INCHES }
@Serializable enum class CanvasAnchor { TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT }
@Serializable enum class CanvasResizeMode { RESAMPLE, CANVAS }

data class CanvasSizeRequest(val width: Float, val height: Float, val dpi: Float,
    val unit: CanvasUnit = CanvasUnit.PIXELS, val mode: CanvasResizeMode = CanvasResizeMode.CANVAS,
    val anchor: CanvasAnchor = CanvasAnchor.CENTER) {
    fun pixels(): Pair<Int, Int> {
        require(dpi.isFinite() && dpi in 1f..2400f && width.isFinite() && height.isFinite())
        val w = CanvasSizing.toPixels(width, unit, dpi).roundToInt()
        val h = CanvasSizing.toPixels(height, unit, dpi).roundToInt()
        require(w in 32..8192 && h in 32..8192 && w.toLong() * h <= 67_108_864L) { "Canvas supports 32–8192 pixels per side" }
        return w to h
    }
}

object CanvasSizing {
    fun toPixels(value: Float, unit: CanvasUnit, dpi: Float): Float = when (unit) {
        CanvasUnit.PIXELS -> value
        CanvasUnit.MILLIMETERS -> value * dpi / 25.4f
        CanvasUnit.CENTIMETERS -> value * dpi / 2.54f
        CanvasUnit.INCHES -> value * dpi
    }
    fun fromPixels(value: Float, unit: CanvasUnit, dpi: Float): Float = value / toPixels(1f, unit, dpi)
    fun anchorOffset(anchor: CanvasAnchor, oldWidth: Float, oldHeight: Float, width: Float, height: Float): Pair<Float, Float> {
        val column = anchor.ordinal % 3; val row = anchor.ordinal / 3
        return (width - oldWidth) * column / 2 to (height - oldHeight) * row / 2
    }
}

data class PageGeometryEdit(val before: InkPage, val after: InkPage) {
    init { require(before.id == after.id) }
    fun apply(current: InkPage, undo: Boolean): InkPage? =
        if (current == if (undo) after else before) (if (undo) before else after) else null
}

class PreparedPageGeometry(val edit: PageGeometryEdit, private val release: () -> Unit) : AutoCloseable {
    override fun close() = release()
}

data class PageRasterExportOptions(val width: Int? = null, val height: Int? = null, val includeBackground: Boolean = true,
    val format: RasterFileFormat = RasterFileFormat.PNG, val quality: Int = 95,
    /** Off by default: draft layers are left out, as in Clip Studio Paint's export settings. */
    val includeDraftLayers: Boolean = false)
