package app.inkloom.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import app.inkloom.engine.v5.tools.*
import kotlin.math.*

internal enum class SelectionAlignmentReference { CANVAS, ACTIVE_LAYER, SELECTION, GUIDES, AUTO }

internal data class SelectionTransformUiState(val pose: SelectionTransformPose, val mode: SelectionTransformMode, val snapping: Boolean = false, val magnetics: Boolean = false, val snapDistance: Float = 12f, val mixed: Boolean = false, val layerCount: Int = 1, val alignmentReference: SelectionAlignmentReference = SelectionAlignmentReference.CANVAS, val pivotX: Float = pose.centerX, val pivotY: Float = pose.centerY, val pivotEditing: Boolean = false, val resolvedAlignmentReference: SelectionAlignmentReference = alignmentReference)

/** Geometry owns the draft; its callback always samples the original raster and selection. */
internal class SelectionCanvasTransform(
    val original: SelectionTransformPose,
    private val rasterWidth: Int,
    private val rasterHeight: Int,
    private val density: Float,
    private val changed: (SelectionTransformPose) -> Unit,
    private val rejected: () -> Unit = {},
    private val pivotChanged: () -> Unit = {},
) {
    var pose = original
        private set
    var mode = SelectionTransformMode.FREEFORM
    var pivotEditing = false
    var snapping = false
    var magnetics = false
    var snapDistance = 12f
    var contentTargets: List<Pair<Float,Float>> = emptyList()
    var pivotX = original.centerX
        private set
    var pivotY = original.centerY
        private set
    private var startPivotX = pivotX
    private var startPivotY = pivotY
    private var pivotDragging = false
    private var guideX: Float? = null
    private var guideY: Float? = null
    private var meshNode = -1
    private val puppetPins = linkedSetOf<Int>()
    private var start = original
    private var node = SelectionTransformNode.MOVE
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var navigating = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun replace(next: SelectionTransformPose) {
        if (next == pose) return
        pose = next
        changed(next)
    }
    fun reset() { pivotX = original.centerX; pivotY = original.centerY; replace(original); pivotChanged() }
    fun resetPivot() { pivotX = pose.centerX; pivotY = pose.centerY; pivotChanged() }
    fun setGeometry(x: Float, y: Float, width: Float, height: Float, angle: Float) {
        if (!listOf(x, y, width, height, angle).all(Float::isFinite) || width < 1f || height < 1f) return
        val anchored = anchoredPose(pose, pose.copy(width = width, height = height, angle = angle.mod(360f)), pivotX, pivotY)
        val dx = x - (anchored.centerX - anchored.width / 2f)
        val dy = y - (anchored.centerY - anchored.height / 2f)
        val moved = anchored.withPose(anchored.copy(centerX = anchored.centerX + dx,
            centerY = anchored.centerY + dy))
        pivotX += dx; pivotY += dy
        replace(moved)
        pivotChanged()
    }
    private fun anchoredPose(start: SelectionTransformPose, next: SelectionTransformPose,
                             anchorX: Float, anchorY: Float): SelectionTransformPose {
        val local = SelectionTransformPose.rotate(start.centerX - anchorX, start.centerY - anchorY, -start.angle)
        val rotated = SelectionTransformPose.rotate(local.first * next.width / start.width,
            local.second * next.height / start.height, next.angle)
        return next.withPose(next.copy(centerX = anchorX + rotated.first, centerY = anchorY + rotated.second))
    }
    fun setMeshDensity(columns: Int, rows: Int) = replace(pose.withMeshDensity(columns, rows))
    fun skewBy(horizontalPixels: Float, verticalPixels: Float): Boolean {
        if (!horizontalPixels.isFinite() || !verticalPixels.isFinite()) return false
        val horizontal = pose.skew(SelectionTransformNode.TOP, horizontalPixels, 0f) ?: return false
        val next = horizontal.skew(SelectionTransformNode.LEFT, 0f, verticalPixels) ?: return false
        replace(next)
        return true
    }
    fun clearPuppetPins() { puppetPins.clear(); reset() }
    fun alignToCanvas(alignment: SelectionAlignment) {
        val next = pose.aligned(alignment, rasterWidth.toFloat(), rasterHeight.toFloat())
        pivotX += next.centerX - pose.centerX; pivotY += next.centerY - pose.centerY
        replace(next)
    }
    fun alignToBounds(alignment: SelectionAlignment, referenceLeft: Float, referenceTop: Float,
                      referenceRight: Float, referenceBottom: Float) {
        val corners = pose.sourceCorners()
        val left = corners.minOf { it.first }; val right = corners.maxOf { it.first }
        val top = corners.minOf { it.second }; val bottom = corners.maxOf { it.second }
        val dx = when (alignment) {
            SelectionAlignment.LEFT -> referenceLeft - left
            SelectionAlignment.HORIZONTAL_CENTER -> (referenceLeft + referenceRight - left - right) / 2f
            SelectionAlignment.RIGHT -> referenceRight - right
            else -> 0f
        }
        val dy = when (alignment) {
            SelectionAlignment.TOP -> referenceTop - top
            SelectionAlignment.VERTICAL_CENTER -> (referenceTop + referenceBottom - top - bottom) / 2f
            SelectionAlignment.BOTTOM -> referenceBottom - bottom
            else -> 0f
        }
        nudge(dx, dy)
        guideX = when (alignment) {
            SelectionAlignment.LEFT -> referenceLeft
            SelectionAlignment.HORIZONTAL_CENTER -> (referenceLeft + referenceRight) / 2f
            SelectionAlignment.RIGHT -> referenceRight
            else -> null
        }
        guideY = when (alignment) {
            SelectionAlignment.TOP -> referenceTop
            SelectionAlignment.VERTICAL_CENTER -> (referenceTop + referenceBottom) / 2f
            SelectionAlignment.BOTTOM -> referenceBottom
            else -> null
        }
    }
    fun nudge(dx: Float, dy: Float) {
        pivotX += dx; pivotY += dy
        replace(pose.withPose(pose.copy(centerX=pose.centerX+dx, centerY=pose.centerY+dy)))
    }
    fun cancelGesture() {
        if (dragging) { dragging = false; pivotX = startPivotX; pivotY = startPivotY; replace(start); pivotChanged() }
    }

    fun touch(event: MotionEvent, x: Float, y: Float, w: Int, h: Int, zoom: Float): Boolean {
        if (event.pointerCount > 1) { cancelGesture(); navigating = true; return false }
        if (navigating) {
            if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) navigating = false
            return false
        }
        val px = x * rasterWidth / w.coerceAtLeast(1)
        val py = y * rasterHeight / h.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                start = pose; startX = px; startY = py; dragging = true
                startPivotX = pivotX; startPivotY = pivotY
                val radius = min(24f * density / zoom.coerceAtLeast(.1f),
                    min(pose.width * w / rasterWidth, pose.height * h / rasterHeight) * .3f).coerceAtLeast(6f * density / zoom.coerceAtLeast(.1f))
                pivotDragging = pivotEditing && hypot(pivotX * w / rasterWidth - x, pivotY * h / rasterHeight - y) <= radius
                meshNode = if (mode == SelectionTransformMode.WARP || mode == SelectionTransformMode.PUPPET) pose.lattice().indices.minByOrNull {
                    val p=pose.lattice()[it]; hypot(p.first*w/rasterWidth-x,p.second*h/rasterHeight-y)
                } ?: -1 else -1
                if (mode == SelectionTransformMode.PUPPET && meshNode >= 0) puppetPins += meshNode
                node = handles(w, h, zoom).minByOrNull { hypot(it.second.first - x, it.second.second - y) }
                    ?.takeIf { hypot(it.second.first - x, it.second.second - y) <= radius }?.first ?: SelectionTransformNode.MOVE
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (dragging) {
                val dx=px-startX; val dy=py-startY
                guideX=null; guideY=null
                if (pivotDragging) {
                    pivotX = startPivotX + dx; pivotY = startPivotY + dy
                    pivotChanged()
                    if (event.actionMasked == MotionEvent.ACTION_UP) { dragging = false; pivotDragging = false }
                    return true
                }
                val tap=event.actionMasked==MotionEvent.ACTION_UP && hypot(dx*w/rasterWidth,dy*h/rasterHeight)*zoom < 3*density
                val outside=startX < start.centerX-start.width/2 || startX > start.centerX+start.width/2 || startY < start.centerY-start.height/2 || startY > start.centerY+start.height/2
                if(tap && outside && mode != SelectionTransformMode.WARP && mode != SelectionTransformMode.PUPPET && node==SelectionTransformNode.MOVE) {
                    val vx=startX-start.centerX; val vy=startY-start.centerY
                    replace(start); if(abs(vx/start.width)>abs(vy/start.height)) nudge(sign(vx),0f) else nudge(0f,sign(vy))
                } else if (mode == SelectionTransformMode.WARP && meshNode >= 0) {
                    replace(start.warp(meshNode,dx,dy))
                } else if (mode == SelectionTransformMode.PUPPET && meshNode >= 0) {
                    replace(start.puppetWarp(meshNode, dx, dy, puppetPins - meshNode))
                } else if (mode == SelectionTransformMode.DISTORT || mode == SelectionTransformMode.SKEW) {
                    val next = if (mode == SelectionTransformMode.DISTORT) start.distort(node, px-startX, py-startY)
                        else start.skew(node, px-startX, py-startY)
                    if (next != null) replace(next) else if (event.actionMasked == MotionEvent.ACTION_UP) rejected()
                } else {
                    var tx=px; var ty=py
                    if(node==SelectionTransformNode.MOVE && magnetics && hypot(dx,dy)>0) {
                        val angle=round(atan2(dy,dx)/(PI/12)).toFloat()*(PI/12).toFloat()
                        val length=dx*cos(angle)+dy*sin(angle); tx=startX+length*cos(angle); ty=startY+length*sin(angle)
                    }
                    var next = if (node == SelectionTransformNode.ROTATE) {
                        val delta = atan2(ty - startPivotY, tx - startPivotX) -
                            atan2(startY - startPivotY, startX - startPivotX)
                        anchoredPose(start, start.copy(angle = (start.angle + Math.toDegrees(delta.toDouble()).toFloat()).mod(360f)), startPivotX, startPivotY)
                    } else start.drag(node,startX,startY,tx,ty,mode)
                    if(snapping && node==SelectionTransformNode.MOVE) {
                        val q=next.mesh ?: next.sourceCorners()
                        fun snap(values: List<Float>, targets: List<Float>, threshold: Float): Pair<Float,Float>? =
                            values.flatMap { v -> targets.map { t -> (t-v) to t } }.minByOrNull { abs(it.first) }?.takeIf { abs(it.first)<=threshold }
                        val sx=snap(listOf(q.minOf { it.first },q.maxOf { it.first },next.centerX),listOf(0f,rasterWidth/2f,rasterWidth.toFloat())+contentTargets.map { it.first },snapDistance/zoom*rasterWidth/w)
                        val sy=snap(listOf(q.minOf { it.second },q.maxOf { it.second },next.centerY),listOf(0f,rasterHeight/2f,rasterHeight.toFloat())+contentTargets.map { it.second },snapDistance/zoom*rasterHeight/h)
                        guideX=sx?.second; guideY=sy?.second
                        next=next.withPose(next.copy(centerX=next.centerX+(sx?.first ?: 0f),centerY=next.centerY+(sy?.first ?: 0f)))
                    }
                    // A normal handle drag keeps its opposite edge/corner fixed. The reference
                    // point controls rotation; resizing from it is a separate modifier gesture.
                    if (node != SelectionTransformNode.MOVE && node != SelectionTransformNode.ROTATE) {
                        val local = SelectionTransformPose.rotate(startPivotX - start.centerX,
                            startPivotY - start.centerY, -start.angle)
                        val mapped = SelectionTransformPose.rotate(
                            local.first * next.width / start.width,
                            local.second * next.height / start.height, next.angle)
                        pivotX = next.centerX + mapped.first
                        pivotY = next.centerY + mapped.second
                        pivotChanged()
                    }
                    replace(next)
                    if (node == SelectionTransformNode.MOVE) {
                        pivotX = startPivotX + next.centerX - start.centerX
                        pivotY = startPivotY + next.centerY - start.centerY
                        pivotChanged()
                    }
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) dragging = false
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun handles(w: Int, h: Int, zoom: Float) = SelectionTransformNode.entries.filter { it != SelectionTransformNode.MOVE && (mode !in setOf(SelectionTransformMode.DISTORT, SelectionTransformMode.SKEW) || it != SelectionTransformNode.ROTATE) }.map { node ->
        val point = if (mode == SelectionTransformMode.DISTORT || mode == SelectionTransformMode.SKEW) {
            val q = pose.sourceCorners()
            val index = SelectionTransformNode.entries.indexOf(node)
            if (index % 2 == 0) q[index/2] else {
                val a=q[index/2]; val b=q[(index/2+1)%4]
                (a.first+b.first)/2 to (a.second+b.second)/2
            }
        } else pose.point(node.x, node.y)
        var x = point.first * w / rasterWidth; var y = point.second * h / rasterHeight
        if (node == SelectionTransformNode.ROTATE) {
            val offset = SelectionTransformPose.rotate(0f, -32f * density / zoom.coerceAtLeast(.1f), pose.angle)
            x += offset.first; y += offset.second
        }
        node to (x to y)
    }

    fun draw(canvas: Canvas, w: Int, h: Int, zoom: Float) {
        if(mode==SelectionTransformMode.WARP || mode==SelectionTransformMode.PUPPET) {
            val lattice=pose.lattice()
            val mapping=SelectionMeshMapping(0f,0f,1f,1f,lattice,pose.meshColumns,pose.meshRows)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f*density/zoom; paint.color=Color.rgb(57,103,85)
            for(axis in 0..1) for(line in 0 until if(axis==0) pose.meshColumns else pose.meshRows) {
                val path=Path()
                for(step in 0..48) {
                    val p=if(axis==0) mapping.forward(line/(pose.meshColumns-1f),step/48f)
                        else mapping.forward(step/48f,line/(pose.meshRows-1f))
                    val x=p.first*w/rasterWidth; val y=p.second*h/rasterHeight
                    if(step==0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                canvas.drawPath(path,paint)
            }
            lattice.forEachIndexed { index, p ->
                if (mode == SelectionTransformMode.PUPPET && index !in puppetPins) return@forEachIndexed
                paint.style=Paint.Style.FILL; paint.color=Color.WHITE; canvas.drawCircle(p.first*w/rasterWidth,p.second*h/rasterHeight,6*density/zoom,paint)
                paint.style=Paint.Style.STROKE; paint.color=Color.rgb(57,103,85); canvas.drawCircle(p.first*w/rasterWidth,p.second*h/rasterHeight,6*density/zoom,paint)
            }
            return
        }
        val points = handles(w, h, zoom)
        val scale = density / zoom.coerceAtLeast(.1f)
        paint.color=Color.rgb(238,170,30); paint.strokeWidth=scale
        guideX?.let { canvas.drawLine(it*w/rasterWidth,0f,it*w/rasterWidth,h.toFloat(),paint) }
        guideY?.let { canvas.drawLine(0f,it*h/rasterHeight,w.toFloat(),it*h/rasterHeight,paint) }
        val path = Path()
        points.take(8).forEachIndexed { i, (_, p) -> if (i == 0) path.moveTo(p.first, p.second) else path.lineTo(p.first, p.second) }
        path.close()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * scale; paint.color = Color.rgb(57, 103, 85)
        canvas.drawPath(path, paint)
        if (mode != SelectionTransformMode.DISTORT && mode != SelectionTransformMode.SKEW) {
            val top = points[1].second; val rotate = points.last().second
            canvas.drawLine(top.first, top.second, rotate.first, rotate.second, paint)
        }
        points.forEach { (_, point) ->
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            canvas.drawCircle(point.first, point.second, 6f * scale, paint)
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(57, 103, 85)
            canvas.drawCircle(point.first, point.second, 6f * scale, paint)
        }
        val px = pivotX * w / rasterWidth; val py = pivotY * h / rasterHeight
        paint.color = Color.rgb(238, 170, 30); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * scale
        canvas.drawCircle(px, py, 8f * scale, paint)
        canvas.drawLine(px - 12f * scale, py, px + 12f * scale, py, paint)
        canvas.drawLine(px, py - 12f * scale, px, py + 12f * scale, paint)
    }
}
