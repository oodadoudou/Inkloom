package app.inkloom.ui

import app.inkloom.document.PageElement
import app.inkloom.document.VectorFrame
import app.inkloom.document.VectorLayerContent
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.tools.SelectionRasterTransform
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Selection transforms on vector layers move the lines themselves. */
internal object TransformedVectors {
    private const val EPSILON = 1e-3f
    /** The [selected] lines mapped through [transform]; widths follow the transform's local scale. */
    fun apply(content: VectorLayerContent, selected: Set<String>, frame: VectorFrame, transform: SelectionRasterTransform): VectorLayerContent =
        content.copy(strokes = content.strokes.map { stroke ->
            if (stroke.id !in selected) return@map stroke
            val points = ArrayList<Float>(stroke.points.size)
            for (i in 0 until stroke.pointCount) {
                val (x, y) = transform.forward(frame.pixelX(stroke.x(i)), frame.pixelY(stroke.y(i)))
                points += frame.pageX(x); points += frame.pageY(y); points += stroke.pressure(i)
            }
            val ox = frame.pixelX(stroke.x(0)); val oy = frame.pixelY(stroke.y(0))
            val o = transform.forward(ox, oy); val a = transform.forward(ox + 1f, oy); val b = transform.forward(ox, oy + 1f)
            val area = abs((a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first))
            stroke.copy(points = points, diameter = (stroke.diameter * sqrt(area)).coerceIn(.01f, 2000f))
        })

    /** Pixel bounds of the [selected] lines' centre lines (CSP's Align to vector paths). */
    fun pathBounds(content: VectorLayerContent, selected: Set<String>, frame: VectorFrame): PixelRect? {
        var left = Float.MAX_VALUE; var top = Float.MAX_VALUE; var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        content.strokes.filter { it.id in selected }.forEach { stroke ->
            for (i in 0 until stroke.pointCount) {
                val x = frame.pixelX(stroke.x(i)); val y = frame.pixelY(stroke.y(i))
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        if (right < left) return null
        // Fractions round-trip with float error; do not let 60.00001 px grow the box to 61.
        val l = floor(left + EPSILON).toInt(); val t = floor(top + EPSILON).toInt()
        return PixelRect(l, t, maxOf(l + 1, ceil(right - EPSILON).toInt()), maxOf(t + 1, ceil(bottom - EPSILON).toInt()))
    }

    /** Pixel bounds of objects' frames, rotation included (text aligned by its frame, not its glyphs). */
    fun frameBounds(elements: List<PageElement>, pixelsPerUnitX: Float, pixelsPerUnitY: Float): PixelRect? {
        var left = Float.MAX_VALUE; var top = Float.MAX_VALUE; var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        elements.forEach { element ->
            val cx = element.x + element.width / 2f; val cy = element.y + element.height / 2f
            val radians = Math.toRadians(element.rotationDegrees.toDouble())
            val c = cos(radians).toFloat(); val s = sin(radians).toFloat()
            for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                val hx = dx * element.width / 2f; val hy = dy * element.height / 2f
                val x = (cx + hx * c - hy * s) * pixelsPerUnitX; val y = (cy + hx * s + hy * c) * pixelsPerUnitY
                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        if (right < left) return null
        return PixelRect(floor(left).toInt(), floor(top).toInt(), ceil(right).toInt(), ceil(bottom).toInt())
    }
}
