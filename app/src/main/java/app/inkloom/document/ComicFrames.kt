package app.inkloom.document

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** One comic frame (panel): a convex polygon, x, y pairs in fractions of the whole page. */
@Serializable
data class ComicFrame(val id: String = UUID.randomUUID().toString(), val points: List<Float>) {
    init { require(points.size >= 6 && points.size % 2 == 0) { "A frame needs at least three corners" } }
    val count: Int get() = points.size / 2
    fun x(i: Int) = points[i * 2]
    fun y(i: Int) = points[i * 2 + 1]
}

/**
 * CSP's frame border folder: the folder's contents are masked to its frames and the frame lines
 * are drawn [lineWidth] page units wide.
 */
@Serializable
data class FrameBorder(
    val frames: List<ComicFrame>,
    val drawBorder: Boolean = true,
    val lineWidth: Float = 6f,
    val colorArgb: Int = 0xFF000000.toInt(),
    /** Layers inside the folder snap to the frame outlines. */
    val useAsRuler: Boolean = false,
)

/** How a division treats the folder, as in CSP's Divide frame border. */
enum class FrameDivision { BORDER_ONLY, FOLDERS_EMPTY, FOLDERS_DUPLICATE }

/** CSP's frame border sub tools, plus Select for choosing frames to combine, divide or delete. */
enum class FrameToolMode { DIVIDE, RECTANGLE, SELECT }

object FrameGeometry {
    /** A rectangle frame inset [margin] page units from the page edges. */
    fun pageFrame(pageWidth: Float, pageHeight: Float, margin: Float): ComicFrame {
        val mx = (margin / pageWidth).coerceIn(0f, .45f); val my = (margin / pageHeight).coerceIn(0f, .45f)
        return rectangle(mx, my, 1f - mx, 1f - my)
    }

    fun rectangle(left: Float, top: Float, right: Float, bottom: Float) =
        ComicFrame(points = listOf(min(left, right), min(top, bottom), max(left, right), min(top, bottom), max(left, right), max(top, bottom), min(left, right), max(top, bottom)))

    /**
     * Cuts [frame] along the line through (x1, y1)–(x2, y2) (page fractions), leaving [gutter]
     * page units between the two pieces. Null when the line does not cross the frame.
     */
    fun divide(frame: ComicFrame, x1: Float, y1: Float, x2: Float, y2: Float, gutter: Float, pageWidth: Float, pageHeight: Float): List<ComicFrame>? {
        // Work in page units so the gutter is the same in both directions.
        val ax = x1 * pageWidth; val ay = y1 * pageHeight; val bx = x2 * pageWidth; val by = y2 * pageHeight
        val length = hypot(bx - ax, by - ay)
        if (length < 1e-3f) return null
        val nx = -(by - ay) / length; val ny = (bx - ax) / length
        val polygon = (0 until frame.count).map { frame.x(it) * pageWidth to frame.y(it) * pageHeight }
        val offset = gutter / 2f
        val first = clip(polygon, nx, ny, nx * ax + ny * ay + offset)
        val second = clip(polygon, -nx, -ny, -(nx * ax + ny * ay) + offset)
        if (first.size < 3 || second.size < 3) return null
        fun frameOf(points: List<Pair<Float, Float>>, id: String) = ComicFrame(id, points.flatMap { (x, y) -> listOf(x / pageWidth, y / pageHeight) })
        return listOf(frameOf(first, frame.id), frameOf(second, UUID.randomUUID().toString()))
    }

    /** CSP's Divide frame border equally: [columns] × [rows] frames with the folder's gutters. */
    fun divideEqually(frame: ComicFrame, columns: Int, rows: Int, horizontalGutter: Float, verticalGutter: Float,
                      pageWidth: Float, pageHeight: Float): List<ComicFrame> {
        val polygon = (0 until frame.count).map { frame.x(it) * pageWidth to frame.y(it) * pageHeight }
        val left = polygon.minOf { it.first }; val right = polygon.maxOf { it.first }
        val top = polygon.minOf { it.second }; val bottom = polygon.maxOf { it.second }
        val cellWidth = (right - left - (columns - 1) * horizontalGutter) / columns
        val cellHeight = (bottom - top - (rows - 1) * verticalGutter) / rows
        require(cellWidth > 1f && cellHeight > 1f) { "The gutters leave no room for frames" }
        return (0 until rows).flatMap { row -> (0 until columns).mapNotNull { column ->
            val l = left + column * (cellWidth + horizontalGutter); val t = top + row * (cellHeight + verticalGutter)
            var cell = polygon
            cell = clip(cell, 1f, 0f, l); cell = clip(cell, -1f, 0f, -(l + cellWidth))
            cell = clip(cell, 0f, 1f, t); cell = clip(cell, 0f, -1f, -(t + cellHeight))
            cell.takeIf { it.size >= 3 }?.let { points ->
                ComicFrame(if (row == 0 && column == 0) frame.id else UUID.randomUUID().toString(), points.flatMap { (x, y) -> listOf(x / pageWidth, y / pageHeight) })
            }
        } }
    }

    /** CSP's Combine frames: one frame covering the selected frames (their convex hull). */
    fun combine(frames: List<ComicFrame>): ComicFrame {
        require(frames.size >= 2) { "Choose at least two frames to combine" }
        val hull = convexHull(frames.flatMap { frame -> (0 until frame.count).map { frame.x(it) to frame.y(it) } })
        return ComicFrame(frames.first().id, hull.flatMap { listOf(it.first, it.second) })
    }

    /** Index of the frame containing a point, topmost first. */
    fun frameAt(frames: List<ComicFrame>, x: Float, y: Float): Int? = frames.indices.lastOrNull { contains(frames[it], x, y) }

    fun contains(frame: ComicFrame, x: Float, y: Float): Boolean {
        var inside = false
        var j = frame.count - 1
        for (i in 0 until frame.count) {
            val xi = frame.x(i); val yi = frame.y(i); val xj = frame.x(j); val yj = frame.y(j)
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** A convex frame grown outwards by [distance] page units (mitred corners), so contents reach under the line. */
    fun grown(frame: ComicFrame, distance: Float, pageWidth: Float, pageHeight: Float): List<Pair<Float, Float>> {
        val p = (0 until frame.count).map { frame.x(it) * pageWidth to frame.y(it) * pageHeight }
        val area = p.indices.sumOf { i -> val a = p[i]; val b = p[(i + 1) % p.size]; (a.first * b.second - b.first * a.second).toDouble() }
        val sign = if (area >= 0) 1f else -1f
        // Each edge moved outwards; neighbouring edges meet at the new corner.
        val lines = p.indices.map { i ->
            val a = p[i]; val b = p[(i + 1) % p.size]
            val length = hypot(b.first - a.first, b.second - a.second).coerceAtLeast(1e-4f)
            val nx = sign * (b.second - a.second) / length; val ny = -sign * (b.first - a.first) / length
            floatArrayOf(a.first + nx * distance, a.second + ny * distance, b.first - a.first, b.second - a.second)
        }
        return p.indices.map { i ->
            val l1 = lines[(i + p.size - 1) % p.size]; val l2 = lines[i]
            val denominator = l1[2] * l2[3] - l1[3] * l2[2]
            if (abs(denominator) < 1e-6f) l2[0] to l2[1] else {
                val t = ((l2[0] - l1[0]) * l2[3] - (l2[1] - l1[1]) * l2[2]) / denominator
                l1[0] + l1[2] * t to l1[1] + l1[3] * t
            }
        }
    }

    /** Frames as closed ruler shapes, for drawing along frame lines. */
    fun asRulers(border: FrameBorder): List<RulerShape> = border.frames.map { frame ->
        RulerShape.Path(id = "frame-${frame.id}", points = frame.points, closed = true, kind = RulerPathKind.POLYGON)
    }

    /** The frame lines as vector lines, for the border layer. */
    fun borderStrokes(border: FrameBorder): List<VectorStroke> = border.frames.map { frame ->
        VectorStroke(id = "frame-${frame.id}", points = (0..frame.count).flatMap { i -> listOf(frame.x(i % frame.count), frame.y(i % frame.count), 1f) },
            colorArgb = border.colorArgb, diameter = border.lineWidth.coerceAtLeast(.1f))
    }

    /** Sutherland–Hodgman: keeps the part of [polygon] where n · p ≥ c. */
    private fun clip(polygon: List<Pair<Float, Float>>, nx: Float, ny: Float, c: Float): List<Pair<Float, Float>> {
        if (polygon.isEmpty()) return polygon
        val out = ArrayList<Pair<Float, Float>>()
        for (i in polygon.indices) {
            val current = polygon[i]; val previous = polygon[(i + polygon.size - 1) % polygon.size]
            val dc = nx * current.first + ny * current.second - c
            val dp = nx * previous.first + ny * previous.second - c
            if (dc >= 0f) {
                if (dp < 0f) out += intersection(previous, current, dp, dc)
                out += current
            } else if (dp >= 0f) out += intersection(previous, current, dp, dc)
        }
        // Drop repeated corners left by cuts through a corner.
        val distinct = ArrayList<Pair<Float, Float>>()
        out.forEach { p -> distinct.lastOrNull().let { q -> if (q == null || abs(p.first - q.first) > 1e-3f || abs(p.second - q.second) > 1e-3f) distinct += p } }
        while (distinct.size > 1 && abs(distinct.first().first - distinct.last().first) <= 1e-3f && abs(distinct.first().second - distinct.last().second) <= 1e-3f) distinct.removeAt(distinct.size - 1)
        return distinct
    }

    private fun intersection(a: Pair<Float, Float>, b: Pair<Float, Float>, da: Float, db: Float): Pair<Float, Float> {
        val t = da / (da - db)
        return a.first + (b.first - a.first) * t to a.second + (b.second - a.second) * t
    }

    private fun convexHull(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val sorted = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (sorted.size < 3) return sorted
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Float, Float>>()
        for (p in sorted) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) lower.removeAt(lower.size - 1); lower += p }
        val upper = ArrayList<Pair<Float, Float>>()
        for (p in sorted.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) upper.removeAt(upper.size - 1); upper += p }
        return lower.dropLast(1) + upper.dropLast(1)
    }
}
