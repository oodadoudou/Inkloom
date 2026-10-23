package app.inkloom.document

import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One vector line: its centre line as x, y and pressure triples, x and y being fractions of the
 * whole page before any crop. Lines are drawn with Inkloom's round vector pen, so the pixels of a
 * vector layer can always be rebuilt from its lines.
 */
@Serializable
data class VectorStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Float>,
    val colorArgb: Int,
    /** Full-pressure line width in page units. */
    val diameter: Float,
    val opacity: Float = 1f,
    /** Width at zero pressure as a fraction of [diameter]; 1 ignores pressure. */
    val minimumPressureScale: Float = 1f,
    val hardness: Float = 1f,
) {
    init {
        require(points.size >= 3 && points.size % 3 == 0) { "A vector line needs at least one point" }
        require(diameter.isFinite() && diameter > 0f && opacity in 0f..1f && minimumPressureScale in 0f..1f && hardness in 0f..1f) {
            "Vector line settings are out of range"
        }
    }

    val pointCount: Int get() = points.size / 3
    fun x(index: Int) = points[index * 3]
    fun y(index: Int) = points[index * 3 + 1]
    fun pressure(index: Int) = points[index * 3 + 2]
    fun widthScale(pressure: Float) = minimumPressureScale + (1f - minimumPressureScale) * pressure.coerceIn(0f, 1f)
}

/** A vector layer's lines, bottom to top. */
@Serializable
data class VectorLayerContent(val strokes: List<VectorStroke> = emptyList())

/** CSP's vector eraser: cut away what the eraser touches, or remove every line it touches. */
enum class VectorEraseMode { TOUCHED_AREA, WHOLE_LINE }

/**
 * A pixel surface showing the page area [crop] at [width] × [height] pixels, [pixelsPerUnit]
 * pixels per page unit. Vector geometry is computed in these pixels.
 */
data class VectorFrame(val width: Int, val height: Int, val pixelsPerUnit: Float, val crop: PageCrop = PageCrop()) {
    init { require(width > 0 && height > 0 && pixelsPerUnit.isFinite() && pixelsPerUnit > 0f) }
    private val area = PageCropTransform.normalized(crop)
    private val areaWidth = area.right - area.left
    private val areaHeight = area.bottom - area.top

    fun pixelX(pageX: Float) = (pageX - area.left) / areaWidth * width
    fun pixelY(pageY: Float) = (pageY - area.top) / areaHeight * height
    fun pageX(pixelX: Float) = area.left + pixelX / width * areaWidth
    fun pageY(pixelY: Float) = area.top + pixelY / height * areaHeight
    fun radius(stroke: VectorStroke, pressure: Float) = stroke.diameter * pixelsPerUnit * .5f * stroke.widthScale(pressure)
}

object VectorGeometry {
    /** Pixel bounds (left, top, right, bottom) of a line including its width, or null off the frame. */
    fun bounds(stroke: VectorStroke, frame: VectorFrame): IntArray? {
        var left = Float.MAX_VALUE; var top = Float.MAX_VALUE; var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        val padding = stroke.diameter * frame.pixelsPerUnit * .5f + 2f
        for (i in 0 until stroke.pointCount) {
            val x = frame.pixelX(stroke.x(i)); val y = frame.pixelY(stroke.y(i))
            left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y)
        }
        val result = intArrayOf(
            (left - padding).toInt().coerceIn(0, frame.width), (top - padding).toInt().coerceIn(0, frame.height),
            ceil(right + padding).toInt().coerceIn(0, frame.width), ceil(bottom + padding).toInt().coerceIn(0, frame.height),
        )
        return result.takeIf { it[2] > it[0] && it[3] > it[1] }
    }

    /**
     * Applies an eraser that travelled along [path] (pixel x, y pairs) with [radius] pixels.
     * Touched-area erasing cuts the centre line where the eraser passed; whole-line erasing
     * removes every line whose visible width the eraser touched.
     */
    fun erase(content: VectorLayerContent, path: List<Float>, radius: Float, mode: VectorEraseMode, frame: VectorFrame): VectorLayerContent {
        if (path.size < 2) return content
        val eraser = Polyline(path)
        return VectorLayerContent(content.strokes.flatMap { stroke ->
            val reach = radius + if (mode == VectorEraseMode.WHOLE_LINE) stroke.diameter * frame.pixelsPerUnit * .5f else 0f
            if (!eraser.near(stroke, frame, reach)) return@flatMap listOf(stroke)
            when (mode) {
                VectorEraseMode.WHOLE_LINE -> {
                    val touched = densified(stroke, frame, max(.5f, radius * .5f)).let { dense ->
                        (0 until dense.size / 3).any { i ->
                            val r = radius + frame.radius(stroke, dense[i * 3 + 2])
                            eraser.distance(frame.pixelX(dense[i * 3]), frame.pixelY(dense[i * 3 + 1])) <= r
                        }
                    }
                    if (touched) emptyList() else listOf(stroke)
                }
                VectorEraseMode.TOUCHED_AREA -> split(stroke, frame, max(.5f, radius * .25f)) { x, y -> eraser.distance(x, y) <= radius } ?: listOf(stroke)
            }
        })
    }

    /** Cuts away every part of every line whose centre line lies where [inside] holds. */
    fun eraseWhere(content: VectorLayerContent, frame: VectorFrame, inside: (Float, Float) -> Boolean): VectorLayerContent =
        VectorLayerContent(content.strokes.flatMap { stroke -> split(stroke, frame, 1f, inside) ?: listOf(stroke) })

    /** CSP's vector selection: lines crossing the selection, or only lines entirely inside it. */
    fun select(content: VectorLayerContent, frame: VectorFrame, containedOnly: Boolean, inside: (Float, Float) -> Boolean): Set<String> =
        content.strokes.filter { stroke ->
            val dense = densified(stroke, frame, 1.5f)
            val hits = (0 until dense.size / 3).map { i -> inside(frame.pixelX(dense[i * 3]), frame.pixelY(dense[i * 3 + 1])) }
            if (containedOnly) hits.all { it } else hits.any { it }
        }.mapTo(linkedSetOf()) { it.id }

    /** The lines with page fractions moved by [transform], their widths scaled by [widthScale]. */
    fun mapped(content: VectorLayerContent, widthScale: Float = 1f, transform: (Float, Float) -> Pair<Float, Float>): VectorLayerContent =
        VectorLayerContent(content.strokes.map { stroke ->
            val points = ArrayList<Float>(stroke.points.size)
            for (i in 0 until stroke.pointCount) {
                val (x, y) = transform(stroke.x(i), stroke.y(i))
                points += x; points += y; points += stroke.pressure(i)
            }
            stroke.copy(points = points, diameter = (stroke.diameter * widthScale).coerceAtLeast(.01f))
        })

    fun rotatedClockwise(content: VectorLayerContent) = mapped(content) { x, y -> 1f - y to x }
    fun flipped(content: VectorLayerContent, flip: CanvasFlip) = mapped(content) { x, y ->
        if (flip == CanvasFlip.HORIZONTAL) 1f - x to y else x to 1f - y
    }

    /** SVG of [strokes] in page units; pressure becomes each line's average width. */
    fun svg(strokes: List<VectorStroke>, pageWidth: Float, pageHeight: Float): String = buildString {
        fun n(value: Float) = String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${n(pageWidth)}\" height=\"${n(pageHeight)}\" viewBox=\"0 0 ${n(pageWidth)} ${n(pageHeight)}\">\n")
        strokes.forEach { stroke ->
            val path = (0 until stroke.pointCount).joinToString(" ") { i -> "${if (i == 0) "M" else "L"}${n(stroke.x(i) * pageWidth)} ${n(stroke.y(i) * pageHeight)}" } +
                if (stroke.pointCount == 1) " L${n(stroke.x(0) * pageWidth)} ${n(stroke.y(0) * pageHeight)}" else ""
            val width = stroke.diameter * (0 until stroke.pointCount).map { stroke.widthScale(stroke.pressure(it)) }.average().toFloat()
            val alpha = (stroke.colorArgb ushr 24 and 255) / 255f * stroke.opacity
            append("  <path d=\"$path\" fill=\"none\" stroke=\"#${String.format(Locale.ROOT, "%06x", stroke.colorArgb and 0xFFFFFF)}\"")
            if (alpha < 1f) append(" stroke-opacity=\"${n(alpha)}\"")
            append(" stroke-width=\"${n(width)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n")
        }
        append("</svg>\n")
    }

    /** Page-fraction triples with no gap longer than [step] pixels between neighbours. */
    private fun densified(stroke: VectorStroke, frame: VectorFrame, step: Float): FloatArray {
        val out = ArrayList<Float>(stroke.points.size)
        out += stroke.x(0); out += stroke.y(0); out += stroke.pressure(0)
        for (i in 1 until stroke.pointCount) {
            val length = hypot(frame.pixelX(stroke.x(i)) - frame.pixelX(stroke.x(i - 1)), frame.pixelY(stroke.y(i)) - frame.pixelY(stroke.y(i - 1)))
            val steps = max(1, ceil(length / step).roundToInt().coerceAtMost(100_000))
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                out += stroke.x(i - 1) + (stroke.x(i) - stroke.x(i - 1)) * t
                out += stroke.y(i - 1) + (stroke.y(i) - stroke.y(i - 1)) * t
                out += stroke.pressure(i - 1) + (stroke.pressure(i) - stroke.pressure(i - 1)) * t
            }
        }
        return out.toFloatArray()
    }

    /** The pieces left once points where [removed] holds are cut away; null when nothing is cut. */
    private fun split(stroke: VectorStroke, frame: VectorFrame, step: Float, removed: (Float, Float) -> Boolean): List<VectorStroke>? {
        val dense = densified(stroke, frame, step)
        val count = dense.size / 3
        val keep = BooleanArray(count) { i -> !removed(frame.pixelX(dense[i * 3]), frame.pixelY(dense[i * 3 + 1])) }
        if (keep.all { it }) return null
        val pieces = mutableListOf<List<Float>>()
        var start = -1
        for (i in 0..count) {
            if (i < count && keep[i]) { if (start < 0) start = i; continue }
            if (start >= 0 && (i - start >= 2 || stroke.pointCount == 1)) pieces += dense.copyOfRange(start * 3, i * 3).toList()
            start = -1
        }
        return pieces.mapIndexed { index, points -> stroke.copy(id = if (index == 0) stroke.id else UUID.randomUUID().toString(), points = points) }
    }

    /** An eraser's path in pixels. */
    private class Polyline(private val xy: List<Float>) {
        private val left = (xy.indices step 2).minOf { xy[it] }
        private val right = (xy.indices step 2).maxOf { xy[it] }
        private val top = (xy.indices step 2).minOf { xy[it + 1] }
        private val bottom = (xy.indices step 2).maxOf { xy[it + 1] }

        fun near(stroke: VectorStroke, frame: VectorFrame, reach: Float): Boolean {
            val bounds = VectorGeometry.bounds(stroke, frame) ?: return false
            return bounds[0] - reach <= right && bounds[2] + reach >= left && bounds[1] - reach <= bottom && bounds[3] + reach >= top
        }

        fun distance(x: Float, y: Float): Float {
            if (xy.size == 2) return hypot(x - xy[0], y - xy[1])
            var best = Float.MAX_VALUE
            for (i in 0 until xy.size / 2 - 1) {
                val ax = xy[i * 2]; val ay = xy[i * 2 + 1]; val bx = xy[i * 2 + 2]; val by = xy[i * 2 + 3]
                val dx = bx - ax; val dy = by - ay
                val lengthSquared = dx * dx + dy * dy
                val t = if (lengthSquared <= 1e-6f) 0f else (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
                best = min(best, hypot(x - (ax + dx * t), y - (ay + dy * t)))
            }
            return best
        }
    }
}
