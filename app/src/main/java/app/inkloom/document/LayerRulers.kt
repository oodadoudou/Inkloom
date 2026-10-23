package app.inkloom.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** CSP's ruler display range: which layers snap to a layer's rulers. */
@Serializable
enum class RulerRange { EDITING_TARGET, SAME_FOLDER, ALL_LAYERS }

/** How a path ruler was made; curves and ellipses are drawn as smooth splines through their points. */
@Serializable
enum class RulerPathKind(val smooth: Boolean) { CURVE(true), POLYLINE(false), RECTANGLE(false), ELLIPSE(true), POLYGON(false), PEN(false), VECTOR(false) }

/** One ruler. Coordinates are fractions of the whole page before any crop, like vector lines. */
@Serializable
sealed interface RulerShape {
    val id: String

    /** A straight ruler; snapping follows the whole line through both points. */
    @Serializable @SerialName("straight")
    data class Straight(override val id: String = UUID.randomUUID().toString(), val x1: Float, val y1: Float, val x2: Float, val y2: Float) : RulerShape

    /** Curve, polyline, figure, ruler-pen and vector rulers: x, y pairs. */
    @Serializable @SerialName("path")
    data class Path(override val id: String = UUID.randomUUID().toString(), val points: List<Float>, val closed: Boolean = false,
                    val kind: RulerPathKind = RulerPathKind.POLYLINE) : RulerShape {
        init { require(points.size >= 4 && points.size % 2 == 0) { "A path ruler needs at least two points" } }
        val pointCount: Int get() = points.size / 2
    }

    /** A guide line across the page at [position] (a fraction of the width or height). */
    @Serializable @SerialName("guide")
    data class Guide(override val id: String = UUID.randomUUID().toString(), val vertical: Boolean, val position: Float) : RulerShape
}

/**
 * A layer's perspective ruler: one to three vanishing points (x, y pairs, page fractions, possibly
 * off the page), each with its own snap switch and fixed flag, plus the guides drawn towards them.
 */
@Serializable
data class PerspectiveRuler(
    val vanishingPoints: List<Float>,
    val snap: List<Boolean> = List(vanishingPoints.size / 2) { true },
    val fixed: List<Boolean> = List(vanishingPoints.size / 2) { false },
    /** Guide directions in degrees, per vanishing point, for drawing the ruler's guides. */
    val guideAngles: List<List<Float>> = List(vanishingPoints.size / 2) { listOf(-20f, 0f, 20f) },
    val grid: Boolean = false,
) {
    init {
        require(vanishingPoints.size in 2..6 && vanishingPoints.size % 2 == 0) { "A perspective ruler has one to three vanishing points" }
        require(snap.size == count && fixed.size == count && guideAngles.size == count)
    }
    val count: Int get() = vanishingPoints.size / 2
    fun x(index: Int) = vanishingPoints[index * 2]
    fun y(index: Int) = vanishingPoints[index * 2 + 1]

    /** As the canvas snapping guide: snapping vanishing points only. */
    fun asGuide(): PerspectiveGuideState {
        val active = (0 until count).filter { snap[it] }
        val mode = when (active.size) { 0 -> PerspectiveGuideMode.NONE; 1 -> PerspectiveGuideMode.ONE_POINT; 2 -> PerspectiveGuideMode.TWO_POINT; else -> PerspectiveGuideMode.THREE_POINT }
        val points = active.map { PerspectiveGuidePoint(x(it), y(it)) }
        return PerspectiveGuideState(mode = mode, points = points + PerspectiveGuideState.defaultPoints(mode).drop(points.size))
    }

    fun moved(index: Int, x: Float, y: Float): PerspectiveRuler {
        if (index !in 0 until count || fixed[index]) return this
        return copy(vanishingPoints = vanishingPoints.toMutableList().apply { this[index * 2] = x; this[index * 2 + 1] = y })
    }

    companion object {
        /** CSP's Create perspective ruler: vanishing points placed for 1, 2 or 3-point perspective. */
        fun create(points: Int): PerspectiveRuler = when (points) {
            1 -> PerspectiveRuler(listOf(.5f, .42f))
            2 -> PerspectiveRuler(listOf(-.15f, .42f, 1.15f, .42f))
            3 -> PerspectiveRuler(listOf(-.15f, .45f, 1.15f, .45f, .5f, -.6f))
            else -> throw IllegalArgumentException("Perspective rulers have 1 to 3 vanishing points")
        }
    }
}

/** The rulers on one layer and how other layers see them (CSP's Layer > Ruler/Frame settings). */
@Serializable
data class LayerRulers(
    val shapes: List<RulerShape> = emptyList(),
    val perspective: PerspectiveRuler? = null,
    val visible: Boolean = true,
    val range: RulerRange = RulerRange.EDITING_TARGET,
    /** Moving the whole layer moves its rulers. */
    val linkedToLayer: Boolean = true,
    /** Moving the rulers moves the layer's guides too. */
    val linkGuides: Boolean = false,
) {
    val isEmpty: Boolean get() = shapes.isEmpty() && perspective == null
}

/** A ruler resolved for snapping, with the layer that owns it. */
data class ApplicableRuler(val ownerId: String, val shape: RulerShape)

object RulerScope {
    /** Rulers whose owner is visible, shows them, and whose display range covers [activeLayerId]. */
    fun applicable(layers: List<InkLayer>, activeLayerId: String): List<ApplicableRuler> =
        owners(layers, activeLayerId).flatMap { owner -> owner.rulers!!.shapes.map { ApplicableRuler(owner.id, it) } }

    /** The perspective ruler applying to [activeLayerId], nearest owner first. */
    fun perspective(layers: List<InkLayer>, activeLayerId: String): PerspectiveRuler? =
        owners(layers, activeLayerId).firstNotNullOfOrNull { it.rulers?.perspective }

    private fun owners(layers: List<InkLayer>, activeLayerId: String): List<InkLayer> {
        val active = layers.firstOrNull { it.id == activeLayerId } ?: return emptyList()
        return layers.filter { owner ->
            val rulers = owner.rulers ?: return@filter false
            rulers.visible && !rulers.isEmpty && LayerHierarchy.effectiveVisible(layers, owner) && when (rulers.range) {
                RulerRange.EDITING_TARGET -> owner.id == active.id
                // As in CSP, a ruler outside any folder shows on the whole canvas.
                RulerRange.SAME_FOLDER -> owner.id == active.id || owner.parentId == null || owner.parentId == active.parentId
                RulerRange.ALL_LAYERS -> true
            }
        }.sortedBy { if (it.id == active.id) 0 else 1 }
    }
}

object RulerGeometry {
    /** The ruler's centre line in pixels as x, y pairs; closed paths repeat their first point. */
    fun polyline(shape: RulerShape, width: Float, height: Float): FloatArray = when (shape) {
        is RulerShape.Straight -> floatArrayOf(shape.x1 * width, shape.y1 * height, shape.x2 * width, shape.y2 * height)
        is RulerShape.Guide -> if (shape.vertical) floatArrayOf(shape.position * width, 0f, shape.position * width, height)
            else floatArrayOf(0f, shape.position * height, width, shape.position * height)
        is RulerShape.Path -> {
            val px = FloatArray(shape.points.size) { i -> shape.points[i] * if (i % 2 == 0) width else height }
            val sampled = if (shape.kind.smooth) catmullRom(px, shape.closed) else px
            if (shape.closed) sampled + floatArrayOf(sampled[0], sampled[1]) else sampled
        }
    }

    /** Distance in pixels from a point to the ruler (straight rulers and guides as whole lines). */
    fun distance(shape: RulerShape, x: Float, y: Float, width: Float, height: Float): Float {
        val line = polyline(shape, width, height)
        return if (shape is RulerShape.Path) nearestOnPolyline(line, x, y, 0, line.size / 2 - 1).distance
        else lineProjection(line, x, y).let { hypot(it[0] - x, it[1] - y) }
    }

    /** The ruler nearest to a pen-down point within [capture] pixels, as CSP snaps to the nearest ruler. */
    fun nearest(rulers: List<RulerShape>, x: Float, y: Float, width: Float, height: Float, capture: Float): RulerShape? =
        rulers.map { it to distance(it, x, y, width, height) }.filter { it.second <= capture }.minByOrNull { it.second }?.first

    /** Closed rulers as polygons in page fractions (for Selection from ruler); null for open rulers. */
    fun closedOutline(shape: RulerShape, width: Float, height: Float): List<Pair<Float, Float>>? {
        if (shape !is RulerShape.Path || !shape.closed) return null
        val line = polyline(shape, width, height)
        return (0 until line.size / 2 - 1).map { line[it * 2] / width to line[it * 2 + 1] / height }
    }

    /** Ruler points (in page fractions) moved by [transform]; guides keep their direction. */
    fun mapped(rulers: LayerRulers, transform: (Float, Float) -> Pair<Float, Float>, includeGuides: Boolean = true): LayerRulers {
        fun pairs(values: List<Float>) = values.chunked(2).flatMap { (x, y) -> transform(x, y).toList() }
        return rulers.copy(
            shapes = rulers.shapes.map { shape ->
                when (shape) {
                    is RulerShape.Straight -> { val (a, b) = transform(shape.x1, shape.y1); val (c, d) = transform(shape.x2, shape.y2); shape.copy(x1 = a, y1 = b, x2 = c, y2 = d) }
                    is RulerShape.Path -> shape.copy(points = pairs(shape.points))
                    is RulerShape.Guide -> if (!includeGuides) shape else {
                        val moved = if (shape.vertical) transform(shape.position, .5f).first else transform(.5f, shape.position).second
                        shape.copy(position = moved)
                    }
                }
            },
            perspective = rulers.perspective?.let { it.copy(vanishingPoints = pairs(it.vanishingPoints)) },
        )
    }

    fun rotatedClockwise(rulers: LayerRulers): LayerRulers = mapped(rulers, { x, y -> 1f - y to x }, includeGuides = false).let { rotated ->
        // A quarter turn makes vertical guides horizontal and horizontal guides vertical.
        rotated.copy(shapes = rotated.shapes.map { shape ->
            if (shape !is RulerShape.Guide) shape
            else if (shape.vertical) shape.copy(vertical = false) else shape.copy(vertical = true, position = 1f - shape.position)
        })
    }

    fun flipped(rulers: LayerRulers, flip: CanvasFlip): LayerRulers =
        mapped(rulers, { x, y -> if (flip == CanvasFlip.HORIZONTAL) 1f - x to y else x to 1f - y })

    /** CSP's Ruler from vector: each vector line becomes a ruler of the same shape. */
    fun fromVector(content: VectorLayerContent, width: Float, height: Float, tolerancePixels: Float = 1f): List<RulerShape> =
        content.strokes.filter { it.pointCount >= 2 }.map { stroke ->
            val xy = (0 until stroke.pointCount).flatMap { listOf(stroke.x(it) * width, stroke.y(it) * height) }.toFloatArray()
            val kept = simplify(xy, tolerancePixels)
            RulerShape.Path(points = kept.mapIndexed { i, v -> v / if (i % 2 == 0) width else height }, kind = RulerPathKind.VECTOR)
        }

    /** Rectangle, ellipse and regular polygon rulers from a dragged box (page fractions). */
    fun figure(kind: RulerPathKind, x1: Float, y1: Float, x2: Float, y2: Float, width: Float, height: Float, sides: Int = 6): RulerShape.Path {
        val left = min(x1, x2); val right = max(x1, x2); val top = min(y1, y2); val bottom = max(y1, y2)
        val points = when (kind) {
            RulerPathKind.RECTANGLE -> listOf(left, top, right, top, right, bottom, left, bottom)
            RulerPathKind.ELLIPSE, RulerPathKind.POLYGON -> {
                val n = if (kind == RulerPathKind.ELLIPSE) 16 else sides.coerceIn(3, 32)
                val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
                (0 until n).flatMap { i ->
                    val a = -PI / 2 + 2 * PI * i / n
                    listOf(cx + (right - left) / 2f * cos(a).toFloat(), cy + (bottom - top) / 2f * sin(a).toFloat())
                }
            }
            else -> throw IllegalArgumentException("$kind is not a figure ruler")
        }
        return RulerShape.Path(points = points, closed = true, kind = kind)
    }

    /** Douglas–Peucker on pixel x, y pairs. */
    fun simplify(xy: FloatArray, tolerance: Float): FloatArray {
        val count = xy.size / 2
        if (count <= 2) return xy
        val keep = BooleanArray(count).also { it[0] = true; it[count - 1] = true }
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to count - 1) }
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var worst = -1f; var index = -1
            for (i in a + 1 until b) {
                val d = segmentDistance(xy[i * 2], xy[i * 2 + 1], xy[a * 2], xy[a * 2 + 1], xy[b * 2], xy[b * 2 + 1])
                if (d > worst) { worst = d; index = i }
            }
            if (worst > tolerance) { keep[index] = true; stack.add(a to index); stack.add(index to b) }
        }
        return (0 until count).filter { keep[it] }.flatMap { listOf(xy[it * 2], xy[it * 2 + 1]) }.toFloatArray()
    }

    /** Uniform Catmull–Rom spline through pixel points, sampled every few pixels. */
    private fun catmullRom(p: FloatArray, closed: Boolean): FloatArray {
        val n = p.size / 2
        if (n < 3) return p
        fun point(i: Int): Int = (if (closed) (i % n + n) % n else i.coerceIn(0, n - 1)) * 2
        val out = ArrayList<Float>()
        val segments = if (closed) n else n - 1
        for (s in 0 until segments) {
            val a = point(s - 1); val b = point(s); val c = point(s + 1); val d = point(s + 2)
            val steps = max(2, ceil(hypot(p[c] - p[b], p[c + 1] - p[b + 1]) / 4f).toInt().coerceAtMost(400))
            for (k in 0 until steps) {
                val t = k.toFloat() / steps; val t2 = t * t; val t3 = t2 * t
                fun axis(o: Int) = .5f * (2 * p[b + o] + (-p[a + o] + p[c + o]) * t + (2 * p[a + o] - 5 * p[b + o] + 4 * p[c + o] - p[d + o]) * t2 +
                    (-p[a + o] + 3 * p[b + o] - 3 * p[c + o] + p[d + o]) * t3)
                out += axis(0); out += axis(1)
            }
        }
        if (!closed) { out += p[(n - 1) * 2]; out += p[(n - 1) * 2 + 1] }
        return out.toFloatArray()
    }

    internal data class Nearest(val x: Float, val y: Float, val distance: Float, val arc: Float)

    /** Nearest point on segments [first, last) of a pixel polyline, with its arc length. */
    internal fun nearestOnPolyline(line: FloatArray, x: Float, y: Float, first: Int, last: Int, arcAtFirst: Float = 0f): Nearest {
        var best = Nearest(line[first * 2], line[first * 2 + 1], hypot(line[first * 2] - x, line[first * 2 + 1] - y), arcAtFirst)
        var arc = arcAtFirst
        for (i in first until last) {
            val ax = line[i * 2]; val ay = line[i * 2 + 1]; val bx = line[i * 2 + 2]; val by = line[i * 2 + 3]
            val dx = bx - ax; val dy = by - ay; val length = hypot(dx, dy)
            val t = if (length < 1e-4f) 0f else (((x - ax) * dx + (y - ay) * dy) / (length * length)).coerceIn(0f, 1f)
            val px = ax + dx * t; val py = ay + dy * t
            val d = hypot(px - x, py - y)
            if (d < best.distance) best = Nearest(px, py, d, arc + length * t)
            arc += length
        }
        return best
    }

    internal fun lineProjection(line: FloatArray, x: Float, y: Float): FloatArray {
        val dx = line[2] - line[0]; val dy = line[3] - line[1]
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < 1e-6f) return floatArrayOf(line[0], line[1])
        val t = ((x - line[0]) * dx + (y - line[1]) * dy) / lengthSquared
        return floatArrayOf(line[0] + dx * t, line[1] + dy * t)
    }

    private fun segmentDistance(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay; val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared < 1e-6f) 0f else (((x - ax) * dx + (y - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(x - (ax + dx * t), y - (ay + dy * t))
    }
}

/**
 * One stroke snapping to one ruler. Straight rulers and guides project onto their whole line; path
 * rulers follow the centre line from where the pen went down, moving along it rather than jumping
 * to another part of the ruler that happens to be closer.
 */
class RulerSnapSession(shape: RulerShape, private val width: Float, private val height: Float) {
    private val line = RulerGeometry.polyline(shape, width, height)
    private val path = shape is RulerShape.Path
    private val closed = (shape as? RulerShape.Path)?.closed == true
    private val segmentArcs: FloatArray = FloatArray(line.size / 2).also { arcs ->
        for (i in 1 until arcs.size) arcs[i] = arcs[i - 1] + hypot(line[i * 2] - line[i * 2 - 2], line[i * 2 + 1] - line[i * 2 - 1])
    }
    private val totalArc = segmentArcs.last()
    private var arc = Float.NaN
    private var lastX = 0f
    private var lastY = 0f

    /** Pixel coordinates snapped to the ruler. */
    fun project(x: Float, y: Float): Pair<Float, Float> {
        if (!path) return RulerGeometry.lineProjection(line, x, y).let { it[0] to it[1] }
        val segments = line.size / 2 - 1
        val nearest = if (arc.isNaN()) RulerGeometry.nearestOnPolyline(line, x, y, 0, segments) else {
            // Search only as far along the ruler as the pen has moved, plus some slack.
            val reach = hypot(x - lastX, y - lastY) * 1.5f + 12f
            fun window(from: Float, to: Float): RulerGeometry.Nearest {
                val first = segmentIndex(from); val last = (segmentIndex(to) + 1).coerceAtMost(segments)
                return RulerGeometry.nearestOnPolyline(line, x, y, first, last, segmentArcs[first])
            }
            if (!closed) window(arc - reach, arc + reach)
            else listOf(window(arc - reach, arc + reach), window(arc - reach + totalArc, arc + reach + totalArc),
                window(arc - reach - totalArc, arc + reach - totalArc)).minBy { it.distance }
        }
        arc = nearest.arc; lastX = x; lastY = y
        return nearest.x to nearest.y
    }

    private fun segmentIndex(arcLength: Float): Int {
        val target = arcLength.coerceIn(0f, totalArc)
        var low = 0; var high = segmentArcs.size - 1
        while (low < high) { val mid = (low + high + 1) / 2; if (segmentArcs[mid] <= target) low = mid else high = mid - 1 }
        return low.coerceAtMost(segmentArcs.size - 2).coerceAtLeast(0)
    }
}
