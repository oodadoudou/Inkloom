package app.inkloom.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import app.inkloom.engine.v5.tools.*
import kotlin.math.*

internal data class SelectionTransformUiState(val pose: SelectionTransformPose, val mode: SelectionTransformMode, val snapping: Boolean = false, val magnetics: Boolean = false, val snapDistance: Float = 12f, val mixed: Boolean = false)

/** Geometry owns the draft; its callback always samples the original raster and selection. */
internal class SelectionCanvasTransform(
    val original: SelectionTransformPose,
    private val rasterWidth: Int,
    private val rasterHeight: Int,
    private val density: Float,
    private val changed: (SelectionTransformPose) -> Unit,
    private val rejected: () -> Unit = {},
) {
    var pose = original
        private set
    var mode = SelectionTransformMode.FREEFORM
    var snapping = false
    var magnetics = false
    var snapDistance = 12f
    var contentTargets: List<Pair<Float,Float>> = emptyList()
    private var guideX: Float? = null
    private var guideY: Float? = null
    private var meshNode = -1
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
    fun reset() = replace(original)
    fun nudge(dx: Float, dy: Float) = replace(pose.withPose(pose.copy(centerX=pose.centerX+dx, centerY=pose.centerY+dy)))
    fun cancelGesture() {
        if (dragging) { dragging = false; replace(start) }
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
                val radius = min(24f * density / zoom.coerceAtLeast(.1f),
                    min(pose.width * w / rasterWidth, pose.height * h / rasterHeight) * .3f).coerceAtLeast(6f * density / zoom.coerceAtLeast(.1f))
                meshNode = if (mode == SelectionTransformMode.WARP) pose.lattice().indices.minByOrNull {
                    val p=pose.lattice()[it]; hypot(p.first*w/rasterWidth-x,p.second*h/rasterHeight-y)
                } ?: -1 else -1
                node = handles(w, h, zoom).minByOrNull { hypot(it.second.first - x, it.second.second - y) }
                    ?.takeIf { hypot(it.second.first - x, it.second.second - y) <= radius }?.first ?: SelectionTransformNode.MOVE
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (dragging) {
                val dx=px-startX; val dy=py-startY
                guideX=null; guideY=null
                val tap=event.actionMasked==MotionEvent.ACTION_UP && hypot(dx*w/rasterWidth,dy*h/rasterHeight)*zoom < 3*density
                val outside=startX < start.centerX-start.width/2 || startX > start.centerX+start.width/2 || startY < start.centerY-start.height/2 || startY > start.centerY+start.height/2
                if(tap && outside && mode != SelectionTransformMode.WARP && node==SelectionTransformNode.MOVE) {
                    val vx=startX-start.centerX; val vy=startY-start.centerY
                    replace(start); if(abs(vx/start.width)>abs(vy/start.height)) nudge(sign(vx),0f) else nudge(0f,sign(vy))
                } else if (mode == SelectionTransformMode.WARP && meshNode >= 0) {
                    replace(start.warp(meshNode,dx,dy))
                } else if (mode == SelectionTransformMode.DISTORT) {
                    val next = start.distort(node, px-startX, py-startY)
                    if (next != null) replace(next) else if (event.actionMasked == MotionEvent.ACTION_UP) rejected()
                } else {
                    var tx=px; var ty=py
                    if(node==SelectionTransformNode.MOVE && magnetics && hypot(dx,dy)>0) {
                        val angle=round(atan2(dy,dx)/(PI/12)).toFloat()*(PI/12).toFloat()
                        val length=dx*cos(angle)+dy*sin(angle); tx=startX+length*cos(angle); ty=startY+length*sin(angle)
                    }
                    var next=start.drag(node,startX,startY,tx,ty,mode)
                    if(snapping && node==SelectionTransformNode.MOVE) {
                        val q=next.mesh ?: next.sourceCorners()
                        fun snap(values: List<Float>, targets: List<Float>, threshold: Float): Pair<Float,Float>? =
                            values.flatMap { v -> targets.map { t -> (t-v) to t } }.minByOrNull { abs(it.first) }?.takeIf { abs(it.first)<=threshold }
                        val sx=snap(listOf(q.minOf { it.first },q.maxOf { it.first },next.centerX),listOf(0f,rasterWidth/2f,rasterWidth.toFloat())+contentTargets.map { it.first },snapDistance/zoom*rasterWidth/w)
                        val sy=snap(listOf(q.minOf { it.second },q.maxOf { it.second },next.centerY),listOf(0f,rasterHeight/2f,rasterHeight.toFloat())+contentTargets.map { it.second },snapDistance/zoom*rasterHeight/h)
                        guideX=sx?.second; guideY=sy?.second
                        next=next.withPose(next.copy(centerX=next.centerX+(sx?.first ?: 0f),centerY=next.centerY+(sy?.first ?: 0f)))
                    }
                    replace(next)
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) dragging = false
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun handles(w: Int, h: Int, zoom: Float) = SelectionTransformNode.entries.filter { it != SelectionTransformNode.MOVE && (mode != SelectionTransformMode.DISTORT || it != SelectionTransformNode.ROTATE) }.map { node ->
        val point = if (mode == SelectionTransformMode.DISTORT) {
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
        if(mode==SelectionTransformMode.WARP) {
            val lattice=pose.lattice()
            val mapping=SelectionMeshMapping(0f,0f,1f,1f,lattice)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f*density/zoom; paint.color=Color.rgb(57,103,85)
            for(axis in 0..1) for(line in 0..3) {
                val path=Path()
                for(step in 0..48) {
                    val p=if(axis==0) mapping.forward(line/3f,step/48f) else mapping.forward(step/48f,line/3f)
                    val x=p.first*w/rasterWidth; val y=p.second*h/rasterHeight
                    if(step==0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                canvas.drawPath(path,paint)
            }
            lattice.forEach { p ->
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
        if (mode != SelectionTransformMode.DISTORT) {
            val top = points[1].second; val rotate = points.last().second
            canvas.drawLine(top.first, top.second, rotate.first, rotate.second, paint)
        }
        points.forEach { (_, point) ->
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            canvas.drawCircle(point.first, point.second, 6f * scale, paint)
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(57, 103, 85)
            canvas.drawCircle(point.first, point.second, 6f * scale, paint)
        }
    }
}
