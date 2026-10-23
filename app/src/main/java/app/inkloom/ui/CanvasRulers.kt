package app.inkloom.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import app.inkloom.document.ApplicableRuler
import app.inkloom.document.LayerRulers
import app.inkloom.document.PageCrop
import app.inkloom.document.PageCropTransform
import app.inkloom.document.RulerGeometry
import app.inkloom.document.RulerPathKind
import app.inkloom.document.RulerShape
import app.inkloom.document.RulerToolMode
import kotlin.math.abs
import kotlin.math.hypot

/** Rulers are kept in whole-page fractions; the canvas works in the visible (cropped) page. */
internal object RulerCrop {
    fun project(rulers: LayerRulers, crop: PageCrop): LayerRulers {
        val c = PageCropTransform.normalized(crop)
        if (c == PageCrop()) return rulers
        return RulerGeometry.mapped(rulers, { x, y -> (x - c.left) / (c.right - c.left) to (y - c.top) / (c.bottom - c.top) })
    }

    fun unproject(rulers: LayerRulers, crop: PageCrop): LayerRulers {
        val c = PageCropTransform.normalized(crop)
        if (c == PageCrop()) return rulers
        return RulerGeometry.mapped(rulers, { x, y -> c.left + x * (c.right - c.left) to c.top + y * (c.bottom - c.top) })
    }
}

/**
 * The ruler tool on the canvas: creates rulers on the active layer and edits them. Coordinates are
 * view pixels; [scale] is the viewport zoom, so hit distances stay the same on screen.
 */
internal class RulerToolController(private val density: Float) {
    var mode = RulerToolMode.STRAIGHT
    var polygonSides = 6
    /** The active layer's rulers in visible-page fractions. */
    var rulers: LayerRulers? = null
    var selectedId: String? = null
        private set
    var onCommit: (LayerRulers) -> Unit = {}
    var onChanged: () -> Unit = {}

    private val draft = mutableListOf<Float>()
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragX = 0f
    private var dragY = 0f
    private var dragging = false
    private var editPoint = -1
    private var editOriginal: RulerShape? = null
    private var editVanishingPoint = -1
    private var editPerspective: app.inkloom.document.PerspectiveRuler? = null
    private var lastTapTime = 0L

    val hasDraft: Boolean get() = draft.isNotEmpty()

    fun down(x: Float, y: Float, width: Float, height: Float, scale: Float) {
        dragStartX = x; dragStartY = y; dragX = x; dragY = y; dragging = true
        if (mode != RulerToolMode.EDIT) {
            if (mode == RulerToolMode.PEN) { draft.clear(); draft += x; draft += y }
            onChanged(); return
        }
        val shapes = rulers?.shapes.orEmpty()
        val radius = HANDLE_DP * density / scale
        rulers?.perspective?.let { perspective ->
            val index = (0 until perspective.count).firstOrNull { hypot(perspective.x(it) * width - x, perspective.y(it) * height - y) <= radius }
            if (index != null && !perspective.fixed[index]) { editVanishingPoint = index; editPerspective = perspective; selectedId = null; onChanged(); return }
        }
        // Control points first, then the line itself.
        for (shape in shapes.asReversed()) {
            val index = handles(shape, width, height).withIndex().firstOrNull { (_, p) -> hypot(p.first - x, p.second - y) <= radius }?.index
            if (index != null) { select(shape, index); return }
        }
        val hit = shapes.asReversed().firstOrNull { RulerGeometry.distance(it, x, y, width, height) <= LINE_DP * density / scale }
        if (hit != null) select(hit, -1) else { selectedId = null; editOriginal = null; dragging = false; onChanged() }
    }

    fun move(x: Float, y: Float, width: Float, height: Float) {
        if (!dragging) return
        dragX = x; dragY = y
        if (mode == RulerToolMode.PEN) { draft += x; draft += y }
        if (mode == RulerToolMode.EDIT) editOriginal?.let { original -> replace(edited(original, width, height)) }
        if (mode == RulerToolMode.EDIT) editPerspective?.let { original ->
            rulers = rulers?.copy(perspective = original.moved(editVanishingPoint,
                original.x(editVanishingPoint) + (x - dragStartX) / width, original.y(editVanishingPoint) + (y - dragStartY) / height))
        }
        onChanged()
    }

    fun up(x: Float, y: Float, width: Float, height: Float, scale: Float, timeMillis: Long) {
        if (!dragging) return
        move(x, y, width, height)
        dragging = false
        val tap = hypot(x - dragStartX, y - dragStartY) < TAP_DP * density / scale
        when (mode) {
            RulerToolMode.EDIT -> {
                editOriginal?.let { original ->
                    val shape = rulers?.shapes?.firstOrNull { it.id == original.id }
                    if (shape != null && shape != original) rulers?.let(onCommit)
                    editOriginal = null
                }
                editPerspective?.let { original ->
                    if (rulers?.perspective != original) rulers?.let(onCommit)
                    editPerspective = null; editVanishingPoint = -1
                }
            }
            RulerToolMode.STRAIGHT -> if (!tap) add(RulerShape.Straight(x1 = dragStartX / width, y1 = dragStartY / height, x2 = x / width, y2 = y / height))
            RulerToolMode.RECTANGLE, RulerToolMode.ELLIPSE, RulerToolMode.POLYGON -> if (!tap) add(RulerGeometry.figure(
                RulerPathKind.valueOf(mode.name), dragStartX / width, dragStartY / height, x / width, y / height, width, height, polygonSides))
            RulerToolMode.PEN -> {
                val simplified = RulerGeometry.simplify(draft.toFloatArray(), PEN_TOLERANCE_PX)
                draft.clear()
                if (!tap && simplified.size >= 4) add(RulerShape.Path(points = simplified.mapIndexed { i, v -> v / if (i % 2 == 0) width else height },
                    kind = RulerPathKind.PEN))
            }
            RulerToolMode.GUIDE -> if (!tap) {
                // As with CSP's guide tool, the drag direction decides the guide's direction.
                val vertical = abs(y - dragStartY) > abs(x - dragStartX)
                add(RulerShape.Guide(vertical = vertical, position = if (vertical) x / width else y / height))
            }
            RulerToolMode.CURVE, RulerToolMode.POLYLINE -> if (tap) {
                val near = TAP_DP * 2 * density / scale
                val closesShape = draft.size >= 6 && hypot(x - draft[0], y - draft[1]) <= near
                val doubleTap = draft.size >= 4 && timeMillis - lastTapTime <= DOUBLE_TAP_MS &&
                    hypot(x - draft[draft.size - 2], y - draft[draft.size - 1]) <= near
                lastTapTime = timeMillis
                when {
                    closesShape -> finish(width, height, closed = true)
                    doubleTap -> finish(width, height, closed = false)
                    else -> { draft += x; draft += y }
                }
            }
        }
        onChanged()
    }

    fun cancel() {
        if (mode == RulerToolMode.EDIT) editOriginal?.let { replace(it) }
        if (mode == RulerToolMode.EDIT) editPerspective?.let { rulers = rulers?.copy(perspective = it) }
        editOriginal = null; editPerspective = null; dragging = false
        if (mode == RulerToolMode.PEN) draft.clear()
        onChanged()
    }

    /** Ends a curve or polyline that is being placed point by point. */
    fun finishDraft(width: Float, height: Float): Boolean = finish(width, height, closed = false)

    fun cancelDraft() { draft.clear(); onChanged() }

    fun deleteSelected(): Boolean {
        val id = selectedId ?: return false
        val current = rulers ?: return false
        selectedId = null
        onCommit(current.copy(shapes = current.shapes.filterNot { it.id == id }))
        return true
    }

    fun draw(canvas: Canvas, width: Float, height: Float, scale: Float, others: List<ApplicableRuler>, activeLayerId: String, toolActive: Boolean,
             perspective: app.inkloom.document.PerspectiveRuler?) {
        val stroke = 1.5f * density / scale
        paint.strokeWidth = stroke
        val ownPerspective = rulers?.takeIf { toolActive && it.visible }?.perspective
        (ownPerspective ?: perspective)?.let { drawPerspective(canvas, it, width, height, scale, editable = ownPerspective != null && mode == RulerToolMode.EDIT) }
        paint.strokeWidth = stroke
        others.filter { it.ownerId != activeLayerId || !toolActive }.forEach { ruler ->
            paint.color = if (ruler.ownerId == activeLayerId) RULER_COLOR else OTHER_LAYER_COLOR
            drawShape(canvas, ruler.shape, width, height)
        }
        if (!toolActive) return
        rulers?.takeIf { it.visible }?.shapes?.forEach { shape ->
            paint.color = RULER_COLOR
            paint.strokeWidth = if (shape.id == selectedId) stroke * 2f else stroke
            drawShape(canvas, shape, width, height)
            if (mode == RulerToolMode.EDIT) handles(shape, width, height).forEach { (x, y) ->
                canvas.drawCircle(x, y, 4f * density / scale, handleFill)
                canvas.drawCircle(x, y, 4f * density / scale, paint)
            }
        }
        paint.strokeWidth = stroke
        paint.color = RULER_COLOR
        draftShape(width, height)?.let { shape ->
            paint.pathEffect = DashPathEffect(floatArrayOf(6f * density / scale, 4f * density / scale), 0f)
            drawShape(canvas, shape, width, height)
            paint.pathEffect = null
        }
        if (mode == RulerToolMode.CURVE || mode == RulerToolMode.POLYLINE) for (i in 0 until draft.size / 2)
            canvas.drawCircle(draft[i * 2], draft[i * 2 + 1], 3.5f * density / scale, handleFill)
    }

    /** Eye level, guide lines towards each vanishing point, the optional ground grid and the point markers. */
    private fun drawPerspective(canvas: Canvas, ruler: app.inkloom.document.PerspectiveRuler, width: Float, height: Float, scale: Float, editable: Boolean) {
        paint.color = PERSPECTIVE_COLOR
        paint.strokeWidth = 1f * density / scale
        val reach = hypot(width, height) * 3f
        fun ray(x: Float, y: Float, tx: Float, ty: Float) {
            val dx = tx - x; val dy = ty - y; val length = hypot(dx, dy).coerceAtLeast(1e-3f)
            canvas.drawLine(x, y, x + dx / length * reach, y + dy / length * reach, paint)
        }
        val points = (0 until ruler.count).map { ruler.x(it) * width to ruler.y(it) * height }
        // Eye level: through the first two points, or level through a single point.
        if (points.size >= 2) { ray(points[0].first, points[0].second, points[1].first, points[1].second); ray(points[1].first, points[1].second, points[0].first, points[0].second) }
        else { canvas.drawLine(-reach, points[0].second, reach, points[0].second, paint) }
        paint.alpha = 110
        points.forEachIndexed { index, (x, y) ->
            if (!ruler.snap[index]) return@forEachIndexed
            // Guides fan out from the point across the page.
            for (k in 0..8) {
                val t = k / 8f
                ray(x, y, t * width, if (y < height / 2) height else 0f)
                ray(x, y, if (x < width / 2) width else 0f, t * height)
            }
        }
        if (ruler.grid && points.isNotEmpty()) {
            // Ground grid: lines from each of the first two points through evenly spaced marks on the bottom edge.
            points.take(2).forEach { (x, y) -> for (k in -10..20) ray(x, y, k / 10f * width, height * 1.5f) }
        }
        paint.alpha = 255
        points.forEachIndexed { index, (x, y) ->
            val r = (if (editable) 6f else 4f) * density / scale
            if (ruler.snap[index]) canvas.drawCircle(x, y, r, handleFill)
            canvas.drawCircle(x, y, r, paint)
            if (ruler.fixed[index]) canvas.drawLine(x - r, y - r, x + r, y + r, paint)
        }
    }

    private fun drawShape(canvas: Canvas, shape: RulerShape, width: Float, height: Float) {
        val line = if (shape is RulerShape.Straight) {
            // Straight rulers snap along their whole line; draw it across the page.
            val dx = (shape.x2 - shape.x1) * width; val dy = (shape.y2 - shape.y1) * height
            val length = hypot(dx, dy).coerceAtLeast(1e-3f); val reach = hypot(width, height) * 2f
            floatArrayOf(shape.x1 * width - dx / length * reach, shape.y1 * height - dy / length * reach,
                shape.x1 * width + dx / length * reach, shape.y1 * height + dy / length * reach)
        } else RulerGeometry.polyline(shape, width, height)
        path.reset(); path.moveTo(line[0], line[1])
        for (i in 1 until line.size / 2) path.lineTo(line[i * 2], line[i * 2 + 1])
        canvas.drawPath(path, paint)
    }

    private fun draftShape(width: Float, height: Float): RulerShape? = when (mode) {
        RulerToolMode.STRAIGHT -> if (dragging && hypot(dragX - dragStartX, dragY - dragStartY) > 1f)
            RulerShape.Straight(x1 = dragStartX / width, y1 = dragStartY / height, x2 = dragX / width, y2 = dragY / height) else null
        RulerToolMode.RECTANGLE, RulerToolMode.ELLIPSE, RulerToolMode.POLYGON -> if (dragging && abs(dragX - dragStartX) > 1f && abs(dragY - dragStartY) > 1f)
            RulerGeometry.figure(RulerPathKind.valueOf(mode.name), dragStartX / width, dragStartY / height, dragX / width, dragY / height, width, height, polygonSides) else null
        RulerToolMode.PEN -> if (draft.size >= 4) RulerShape.Path(points = draft.mapIndexed { i, v -> v / if (i % 2 == 0) width else height }) else null
        RulerToolMode.GUIDE -> if (dragging) {
            val vertical = abs(dragY - dragStartY) > abs(dragX - dragStartX)
            RulerShape.Guide(vertical = vertical, position = if (vertical) dragX / width else dragY / height)
        } else null
        RulerToolMode.CURVE, RulerToolMode.POLYLINE -> if (draft.size >= 4) RulerShape.Path(points = draft.mapIndexed { i, v -> v / if (i % 2 == 0) width else height },
            kind = if (mode == RulerToolMode.CURVE) RulerPathKind.CURVE else RulerPathKind.POLYLINE) else null
        RulerToolMode.EDIT -> null
    }

    private fun finish(width: Float, height: Float, closed: Boolean): Boolean {
        if (draft.size < 4 || (closed && draft.size < 6)) { draft.clear(); onChanged(); return false }
        val shape = RulerShape.Path(points = draft.mapIndexed { i, v -> v / if (i % 2 == 0) width else height }, closed = closed,
            kind = if (mode == RulerToolMode.CURVE) RulerPathKind.CURVE else RulerPathKind.POLYLINE)
        draft.clear()
        add(shape)
        return true
    }

    private fun add(shape: RulerShape) {
        val current = rulers ?: LayerRulers()
        val next = current.copy(shapes = current.shapes + shape, visible = true)
        rulers = next; selectedId = shape.id
        onCommit(next)
    }

    private fun select(shape: RulerShape, pointIndex: Int) {
        selectedId = shape.id; editOriginal = shape; editPoint = pointIndex; onChanged()
    }

    private fun replace(shape: RulerShape) {
        val current = rulers ?: return
        rulers = current.copy(shapes = current.shapes.map { if (it.id == shape.id) shape else it })
    }

    /** The original shape with one control point, or the whole shape, moved by the drag. */
    private fun edited(original: RulerShape, width: Float, height: Float): RulerShape {
        val dx = (dragX - dragStartX) / width; val dy = (dragY - dragStartY) / height
        return when (original) {
            is RulerShape.Straight -> when (editPoint) {
                0 -> original.copy(x1 = original.x1 + dx, y1 = original.y1 + dy)
                1 -> original.copy(x2 = original.x2 + dx, y2 = original.y2 + dy)
                else -> original.copy(x1 = original.x1 + dx, y1 = original.y1 + dy, x2 = original.x2 + dx, y2 = original.y2 + dy)
            }
            is RulerShape.Path -> original.copy(points = original.points.mapIndexed { i, v ->
                if (editPoint >= 0 && i / 2 != editPoint) v else v + if (i % 2 == 0) dx else dy
            })
            is RulerShape.Guide -> original.copy(position = original.position + if (original.vertical) dx else dy)
        }
    }

    private fun handles(shape: RulerShape, width: Float, height: Float): List<Pair<Float, Float>> = when (shape) {
        is RulerShape.Straight -> listOf(shape.x1 * width to shape.y1 * height, shape.x2 * width to shape.y2 * height)
        is RulerShape.Path -> if (shape.kind == RulerPathKind.PEN || shape.kind == RulerPathKind.VECTOR) emptyList()
            else (0 until shape.pointCount).map { shape.points[it * 2] * width to shape.points[it * 2 + 1] * height }
        is RulerShape.Guide -> emptyList()
    }

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

    companion object {
        const val RULER_COLOR = 0xE07A4DFF.toInt()
        const val OTHER_LAYER_COLOR = 0x907A4DFF.toInt()
        const val PERSPECTIVE_COLOR = 0xD04D8DFF.toInt()
        const val HANDLE_DP = 14f
        const val LINE_DP = 12f
        const val TAP_DP = 6f
        const val DOUBLE_TAP_MS = 350L
        const val PEN_TOLERANCE_PX = 1.5f
        /** How close to a ruler, on screen, a stroke must start to snap to it. */
        const val SNAP_CAPTURE_DP = 28f
    }
}

/** Ruler tool commands from its properties panel. */
enum class RulerCommand { FINISH, CANCEL, DELETE }

/** Gives the canvas the rulers covering the active layer and the ruler tool settings, and saves ruler edits. */
internal fun bindCanvasRulers(view: InkCanvasView, page: app.inkloom.document.InkPage, activeLayerId: String,
                              settings: app.inkloom.settings.AppSettings, save: (String, LayerRulers?) -> Unit) {
    val projected = page.layers.map { layer -> layer.rulers?.let { layer.copy(rulers = RulerCrop.project(it, page.crop)) } ?: layer }
    // A frame folder set to act as a ruler gives its layers the frame outlines.
    val frameRulers = activeFrameFolder(page.layers, activeLayerId)?.takeIf { it.frame?.useAsRuler == true && it.id != activeLayerId }?.let { folder ->
        app.inkloom.document.FrameGeometry.asRulers(folder.frame!!).map { app.inkloom.document.ApplicableRuler(folder.id, it) }
    }.orEmpty()
    view.setRulers(app.inkloom.document.RulerScope.applicable(projected, activeLayerId) + frameRulers,
        page.layers.mapNotNull { layer -> layer.rulers?.let { layer.id to it } }.toMap(), page.crop,
        app.inkloom.document.RulerScope.perspective(projected, activeLayerId))
    view.snapToRulers = settings.snapToRulers
    if (view.rulerTool.mode != settings.rulerToolMode) { view.rulerTool.cancelDraft(); view.rulerTool.mode = settings.rulerToolMode }
    view.rulerTool.polygonSides = settings.rulerPolygonSides
    view.onSaveLayerRulers = save
    view.alignToTextPixels = settings.alignToTextPixels
    view.alignToVectorPaths = settings.alignToVectorPaths
}
