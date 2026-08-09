package app.inkloom.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import app.inkloom.engine.v2.input.PointerCoordinateMapper
import app.inkloom.document.ViewportState

/**
 * Extends [InkCanvasView]'s input surface to the full editor viewport.
 *
 * The page View keeps its fitted layout size while its content can be zoomed and panned beyond
 * those bounds. Android hit testing does not follow the page transform into its visible overflow,
 * so this proxy maps the complete event through the actual source and destination View matrices.
 */
class InkViewportTouchProxy(context: Context) : View(context) {
    var target: InkCanvasView? = null
        set(value) {
            field?.apply {
                externalHoverCursor = false
                onHoverCursorStyleChanged = null
            }
            field = value
            value?.apply {
                externalHoverCursor = true
                onHoverCursorStyleChanged = { refreshCursorStyle() }
            }
            clearHoverCursor()
        }

    private val coordinateMapper = PointerCoordinateMapper()
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var hoverVisible = false
    private var hoverX = 0f
    private var hoverY = 0f
    private var hoverWidth = 0f
    private var hoverHeight = 0f
    private var activeStylusPointerId = MotionEvent.INVALID_POINTER_ID

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        setWillNotDraw(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val stylusIndex = event.stylusPointerIndex(activeStylusPointerId)
        if (stylusIndex >= 0 && event.actionMasked in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN)) {
            activeStylusPointerId = event.getPointerId(stylusIndex)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.isStylusEvent()) {
            requestUnbufferedDispatch(event)
        }
        val handled = forward(event) { canvas, forwarded -> canvas.dispatchTouchEvent(forwarded) }
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hoverVisible) return
        val radiusX = (hoverWidth / 2f).coerceAtLeast(1.5f)
        val radiusY = (hoverHeight / 2f).coerceAtLeast(1.5f)
        hoverPaint.strokeWidth = 3.5f
        hoverPaint.color = Color.WHITE
        hoverPaint.alpha = 235
        canvas.drawOval(hoverX - radiusX, hoverY - radiusY, hoverX + radiusX, hoverY + radiusY, hoverPaint)
        hoverPaint.strokeWidth = 1.5f
        hoverPaint.color = Color.BLACK
        canvas.drawOval(hoverX - radiusX, hoverY - radiusY, hoverX + radiusX, hoverY + radiusY, hoverPaint)
    }

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

    private inline fun forward(event: MotionEvent, dispatch: (InkCanvasView, MotionEvent) -> Boolean): Boolean {
        val canvas = target?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 } ?: return false
        val forwarded = MotionEvent.obtain(event)
        return try {
            val viewport = if (canvas.proxyContactUsesPageCoordinates(forwarded)) {
                ViewportState(canvas.viewportScale, canvas.viewportPanX, canvas.viewportPanY, canvas.viewportRotationDegrees)
            } else null
            coordinateMapper.mapCenteredViewport(forwarded, this, canvas, viewport) && dispatch(canvas, forwarded)
        } finally {
            forwarded.recycle()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
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
        hoverX = event.getX(pointerIndex)
        hoverY = event.getY(pointerIndex)
        hoverWidth = snapshot[3] * canvas.viewportScale.coerceAtLeast(1f)
        hoverHeight = snapshot.getOrElse(4) { snapshot[3] } * canvas.viewportScale.coerceAtLeast(1f)
        hoverVisible = true
        postInvalidateOnAnimation()
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
        hoverX = event.getX(pointerIndex)
        hoverY = event.getY(pointerIndex)
        hoverWidth = snapshot[3] * canvas.viewportScale.coerceAtLeast(1f)
        hoverHeight = snapshot.getOrElse(4) { snapshot[3] } * canvas.viewportScale.coerceAtLeast(1f)
        hoverVisible = true
        postInvalidateOnAnimation()
    }

    private fun clearHoverCursor() {
        if (!hoverVisible) return
        hoverVisible = false
        postInvalidateOnAnimation()
    }

    private fun refreshCursorStyle() {
        val canvas = target ?: return
        val snapshot = canvas.hoverCursorSnapshot()
        hoverWidth = snapshot[3] * canvas.viewportScale.coerceAtLeast(1f)
        hoverHeight = snapshot.getOrElse(4) { snapshot[3] } * canvas.viewportScale.coerceAtLeast(1f)
        postInvalidateOnAnimation()
    }

    internal fun hoverCursorSnapshot(): FloatArray = floatArrayOf(
        if (hoverVisible) 1f else 0f,
        hoverX,
        hoverY,
        hoverWidth,
        hoverHeight,
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
}
