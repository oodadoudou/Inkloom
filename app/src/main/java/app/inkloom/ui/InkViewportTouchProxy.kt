package app.inkloom.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.view.MotionEvent
import android.view.PointerIcon
import android.widget.FrameLayout
import androidx.annotation.WorkerThread
import androidx.graphics.lowlatency.LowLatencyCanvasView
import app.inkloom.document.InkStroke
import app.inkloom.engine.v3.render.ActiveStrokeCompositor
import app.inkloom.engine.v3.render.ActiveStrokeTailPlanner
import app.inkloom.document.BrushCursorGeometry
import app.inkloom.engine.v3.geometry.ViewportFrame
import app.inkloom.engine.v3.input.PointerPacketMapper
import kotlin.math.atan2

/**
 * Extends [InkCanvasView]'s input surface to the full editor viewport.
 *
 * The page View keeps its fitted layout size while its content can be zoomed and panned beyond
 * those bounds. Android hit testing does not follow the page transform into its visible overflow,
 * so this proxy maps the complete event through the actual source and destination View matrices.
 */
class InkViewportTouchProxy(context: Context) : FrameLayout(context), ActiveStrokeCompositor {
    var target: InkCanvasView? = null
        set(value) {
            if (field === value) return
            field?.apply {
                externalHoverCursor = false
                onHoverCursorStyleChanged = null
                if (activeStrokeSurface === this@InkViewportTouchProxy) activeStrokeSurface = null
            }
            field = value
            value?.apply {
                externalHoverCursor = true
                onHoverCursorStyleChanged = { refreshCursorStyle() }
                // Active ink and the physical cursor must share the same stable viewport Surface.
                // A page-local Surface nested under Compose graphicsLayer can be presented with a
                // different transform/frame than the RenderNode-backed committed page.
                activeStrokeSurface = this@InkViewportTouchProxy
            }
            activeDrawingFrame = null
            cancel()
            clearHoverCursor()
        }

    private val coordinateMapper = PointerPacketMapper()
    private val activeInkSurface = LowLatencyCanvasView(context)
    private val cursorSurface = LowLatencyCanvasView(context)
    @Volatile private var activeStrokes: List<InkStroke> = emptyList()
    @Volatile private var activeTransform: ViewportFrame? = null
    @Volatile private var activeDrawingFrame: ViewportFrame? = null
    private val activeLock = Any()
    private val activeTailPlanner = ActiveStrokeTailPlanner()
    private val activeRenderMatrix = Matrix()
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    @Volatile private var hoverVisible = false
    @Volatile private var hoverX = 0f
    @Volatile private var hoverY = 0f
    @Volatile private var hoverWidth = 0f
    @Volatile private var hoverHeight = 0f
    @Volatile private var hoverRotationDegrees = 0f
    private var hoverDynamicRotationDegrees = 0f
    private var hoverDirectionDegrees = 0f
    private var hoverLastX = 0f
    private var hoverLastY = 0f
    private var hoverHasDirection = false
    private var activeStylusPointerId = MotionEvent.INVALID_POINTER_ID

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        clipChildren = true
        clipToPadding = true
        activeInkSurface.apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            isFocusable = false
            pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
            setRenderCallback(object : LowLatencyCanvasView.Callback {
                @WorkerThread
                override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) = drawCompleteActiveInk(canvas)

                @WorkerThread
                override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) = drawIncrementalActiveInk(canvas)
            })
        }
        cursorSurface.apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            isFocusable = false
            pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
            setRenderCallback(object : LowLatencyCanvasView.Callback {
                @WorkerThread
                override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) = drawCursor(canvas)

                @WorkerThread
                override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) = drawCursor(canvas)
            })
        }
        addView(activeInkSurface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(cursorSurface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun render(strokes: List<InkStroke>) {
        val canvas = target ?: return
        activeTransform = activeDrawingFrame ?: currentFrame(canvas)
        activeStrokes = strokes
        activeInkSurface.renderFrontBufferedLayer()
    }

    override fun commit(strokes: List<InkStroke>) {
        val canvas = target ?: return
        activeTransform = activeDrawingFrame ?: currentFrame(canvas)
        activeStrokes = strokes
        activeInkSurface.renderFrontBufferedLayer()
        activeInkSurface.commit()
        postOnAnimation {
            activeStrokes = emptyList()
            synchronized(activeLock) { activeTailPlanner.reset() }
            activeInkSurface.clear()
            canvas.resetActiveRenderState()
        }
    }

    override fun cancel() {
        activeStrokes = emptyList()
        activeTransform = null
        synchronized(activeLock) { activeTailPlanner.reset() }
        activeInkSurface.clear()
        target?.resetActiveRenderState()
    }

    @WorkerThread
    private fun drawCompleteActiveInk(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val view = target ?: return
        val strokes = activeStrokes
        val transform = activeTransform ?: return
        view.resetActiveRenderState()
        if (strokes.isNotEmpty()) drawActiveInk(canvas, view, transform, strokes, incrementalStampDraw = false)
        synchronized(activeLock) { activeTailPlanner.complete(strokes) }
    }

    @WorkerThread
    private fun drawIncrementalActiveInk(canvas: Canvas) {
        val view = target ?: return
        val strokes = activeStrokes
        val transform = activeTransform ?: return
        if (strokes.isEmpty()) return
        val forceComplete = view.requiresCompleteActiveReplay(strokes)
        val plan = synchronized(activeLock) {
            if (forceComplete) activeTailPlanner.complete(strokes) else activeTailPlanner.incremental(strokes)
        }
        if (plan.clearBeforeDraw) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (plan.strokes.isNotEmpty()) drawActiveInk(canvas, view, transform, plan.strokes, plan.incrementalStampDraw)
    }

    @WorkerThread
    private fun drawActiveInk(
        canvas: Canvas,
        view: InkCanvasView,
        transform: ViewportFrame,
        strokes: List<InkStroke>,
        incrementalStampDraw: Boolean,
    ) {
        val checkpoint = canvas.save()
        canvas.concat(transform.pageToViewportMatrix(activeRenderMatrix))
        canvas.clipRect(0f, 0f, view.width.toFloat(), view.height.toFloat())
        view.drawActiveStrokes(canvas, strokes, incrementalStampDraw)
        canvas.restoreToCount(checkpoint)
    }

    private fun currentFrame(canvas: InkCanvasView): ViewportFrame? =
        coordinateMapper.snapshot(this, canvas, canvas.viewportState())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val stylusIndex = event.stylusPointerIndex(activeStylusPointerId)
        if (stylusIndex >= 0 && event.actionMasked in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN)) {
            activeStylusPointerId = event.getPointerId(stylusIndex)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.isStylusEvent()) {
            requestUnbufferedDispatch(event)
        }
        val handled = forward(event, retainDrawingFrame = true) { canvas, forwarded -> canvas.dispatchTouchEvent(forwarded) }
        updateContactCursor(event)
        return handled
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER && event.isStylusEvent()) {
            requestUnbufferedDispatch(event)
        }
        val handled = forward(event) { canvas, forwarded -> canvas.onHoverEvent(forwarded) }
        updateHoverCursor(event)
        return handled
    }

    /** Cursor feedback uses the same Android front-buffer path as active ink. A regular View
     * invalidation waits for the next UI frame while active ink can present immediately, creating
     * a visible moving offset even when both coordinates are mathematically identical. */
    @WorkerThread
    private fun drawCursor(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        if (!hoverVisible) return
        val radiusX = (hoverWidth / 2f).coerceAtLeast(1.5f)
        val radiusY = (hoverHeight / 2f).coerceAtLeast(1.5f)
        val checkpoint = canvas.save()
        canvas.rotate(hoverRotationDegrees, hoverX, hoverY)
        hoverPaint.strokeWidth = 3.5f
        hoverPaint.color = Color.WHITE
        hoverPaint.alpha = 235
        canvas.drawOval(hoverX - radiusX, hoverY - radiusY, hoverX + radiusX, hoverY + radiusY, hoverPaint)
        hoverPaint.strokeWidth = 1.5f
        hoverPaint.color = Color.BLACK
        canvas.drawOval(hoverX - radiusX, hoverY - radiusY, hoverX + radiusX, hoverY + radiusY, hoverPaint)
        canvas.restoreToCount(checkpoint)
        if (minOf(hoverWidth, hoverHeight) < SMALL_CURSOR_MARK_THRESHOLD_PX) {
            hoverPaint.style = Paint.Style.FILL
            hoverPaint.color = Color.WHITE
            canvas.drawCircle(hoverX, hoverY, 3f, hoverPaint)
            hoverPaint.color = Color.BLACK
            canvas.drawCircle(hoverX, hoverY, 1.5f, hoverPaint)
            hoverPaint.style = Paint.Style.STROKE
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true
    override fun onInterceptHoverEvent(event: MotionEvent): Boolean = true

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return forward(event) { canvas, forwarded -> canvas.dispatchGenericMotionEvent(forwarded) }
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        val index = pointerIndex.coerceIn(0, event.pointerCount - 1)
        return if (event.getToolType(index) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)) {
            PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
        } else {
            super.onResolvePointerIcon(event, pointerIndex)
        }
    }

    private inline fun forward(
        event: MotionEvent,
        retainDrawingFrame: Boolean = false,
        dispatch: (InkCanvasView, MotionEvent) -> Boolean,
    ): Boolean {
        val canvas = target?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 } ?: return false
        val terminal = event.actionMasked in setOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)
        if (retainDrawingFrame && event.actionMasked == MotionEvent.ACTION_DOWN) activeDrawingFrame = null
        if (retainDrawingFrame && event.pointerCount > 1) activeDrawingFrame = null
        val usesPageCoordinates = canvas.proxyContactUsesPageCoordinates(event)
        val frozenFrame = activeDrawingFrame?.takeIf { retainDrawingFrame && event.pointerCount == 1 }
        val packet = when {
            frozenFrame != null -> coordinateMapper.map(event, frozenFrame)
            usesPageCoordinates -> coordinateMapper.map(event, this, canvas, canvas.viewportState())
            else -> coordinateMapper.map(event, this, canvas)
        } ?: return false
        if (retainDrawingFrame && event.actionMasked == MotionEvent.ACTION_DOWN && usesPageCoordinates) {
            activeDrawingFrame = packet.frame
        }
        return try {
            dispatch(canvas, packet.event)
        } finally {
            packet.close()
            if (retainDrawingFrame && terminal) activeDrawingFrame = null
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        cancel()
        target = null
        super.onDetachedFromWindow()
    }

    private fun updateHoverCursor(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            clearHoverCursor()
            return
        }
        val canvas = target ?: return clearHoverCursor()
        val snapshot = canvas.hoverCursorSnapshot()
        if (snapshot[0] == 0f || !event.isStylusEvent()) return clearHoverCursor()
        val pointerIndex = event.stylusPointerIndex(activeStylusPointerId)
        if (pointerIndex < 0) return clearHoverCursor()
        // The visible cursor is feedback for the physical pen, not for the modeled/stabilized
        // stroke. Keep its centre on the raw proxy-local packet. Round-tripping the accepted page
        // point through a second affine path is mathematically redundant and, on vendor graphics
        // stacks that expose a Compose rotation differently for View location APIs, produced a
        // large rotation-dependent cursor offset even though committed ink was correct.
        updateCursorGeometry(
            event = event,
            pointerIndex = pointerIndex,
            snapshot = snapshot,
            scale = canvas.viewportScale.coerceAtLeast(1f),
            resetDirection = event.actionMasked == MotionEvent.ACTION_HOVER_ENTER || !hoverVisible,
        )
        hoverVisible = true
        cursorSurface.renderFrontBufferedLayer()
    }

    private fun updateContactCursor(event: MotionEvent) {
        if (!event.isStylusEvent()) return
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) return clearHoverCursor()
        if (event.actionMasked !in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP)) return
        val canvas = target ?: return clearHoverCursor()
        val snapshot = canvas.hoverCursorSnapshot()
        if (snapshot[0] == 0f) return clearHoverCursor()
        val pointerIndex = event.stylusPointerIndex(activeStylusPointerId)
        if (pointerIndex < 0) return clearHoverCursor()
        updateCursorGeometry(
            event = event,
            pointerIndex = pointerIndex,
            snapshot = snapshot,
            scale = canvas.viewportScale.coerceAtLeast(1f),
            resetDirection = event.actionMasked in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN) || !hoverVisible,
        )
        hoverVisible = true
        cursorSurface.renderFrontBufferedLayer()
    }

    private fun clearHoverCursor() {
        if (!hoverVisible) return
        hoverVisible = false
        hoverHasDirection = false
        hoverDynamicRotationDegrees = 0f
        hoverDirectionDegrees = 0f
        cursorSurface.clear()
    }

    private fun refreshCursorStyle() {
        val canvas = target ?: return
        val snapshot = canvas.hoverCursorSnapshot()
        hoverWidth = snapshot[3] * canvas.viewportScale.coerceAtLeast(1f)
        hoverHeight = snapshot.getOrElse(4) { snapshot[3] } * canvas.viewportScale.coerceAtLeast(1f)
        hoverRotationDegrees = BrushCursorGeometry.normalizeDegrees(snapshot.getOrElse(5) { 0f }, hoverDynamicRotationDegrees)
        if (hoverVisible) cursorSurface.renderFrontBufferedLayer()
    }

    private fun updateCursorGeometry(
        event: MotionEvent,
        pointerIndex: Int,
        snapshot: FloatArray,
        scale: Float,
        resetDirection: Boolean,
    ) {
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        if (resetDirection) {
            hoverHasDirection = false
        } else {
            val deltaX = x - hoverLastX
            val deltaY = y - hoverLastY
            if (BrushCursorGeometry.hasMeaningfulDirection(deltaX, deltaY)) {
                hoverDirectionDegrees = Math.toDegrees(atan2(deltaY, deltaX).toDouble()).toFloat()
                hoverHasDirection = true
            }
        }
        hoverLastX = x
        hoverLastY = y
        hoverX = x
        hoverY = y
        hoverWidth = snapshot[3] * scale
        hoverHeight = snapshot.getOrElse(4) { snapshot[3] } * scale
        val followsStylusOrientation = snapshot.getOrElse(6) { 0f } != 0f
        val directionStrength = snapshot.getOrElse(7) { 0f }.coerceIn(-1f, 1f)
        hoverDynamicRotationDegrees = when {
            followsStylusOrientation -> Math.toDegrees(event.getOrientation(pointerIndex).toDouble()).toFloat()
            hoverHasDirection -> hoverDirectionDegrees * directionStrength
            else -> 0f
        }
        hoverRotationDegrees = BrushCursorGeometry.normalizeDegrees(snapshot.getOrElse(5) { 0f }, hoverDynamicRotationDegrees)
    }

    internal fun hoverCursorSnapshot(): FloatArray = floatArrayOf(
        if (hoverVisible) 1f else 0f,
        hoverX,
        hoverY,
        hoverWidth,
        hoverHeight,
        hoverRotationDegrees,
    )

    private fun MotionEvent.isStylusEvent(): Boolean =
        (0 until pointerCount).any { index ->
            getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS || getToolType(index) == MotionEvent.TOOL_TYPE_ERASER
        }

    private fun MotionEvent.stylusPointerIndex(preferredPointerId: Int = MotionEvent.INVALID_POINTER_ID): Int {
        if (preferredPointerId != MotionEvent.INVALID_POINTER_ID) {
            val preferred = findPointerIndex(preferredPointerId)
            if (preferred >= 0 && getToolType(preferred) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)) return preferred
        }
        val action = actionIndex.coerceIn(0, pointerCount - 1)
        if (getToolType(action) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)) return action
        return (0 until pointerCount).firstOrNull { getToolType(it) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER) } ?: -1
    }

    private companion object {
        const val SMALL_CURSOR_MARK_THRESHOLD_PX = 8f
    }
}
