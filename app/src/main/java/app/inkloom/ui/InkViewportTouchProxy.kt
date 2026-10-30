package app.inkloom.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.PointerIcon
import android.widget.FrameLayout
import androidx.annotation.WorkerThread
import androidx.graphics.lowlatency.LowLatencyCanvasView
import app.inkloom.document.BrushCursorGeometry
import app.inkloom.engine.v5.coordinates.ViewportFrame
import app.inkloom.engine.v5.input.PointerPacketMapper
import kotlin.math.atan2

/**
 * Extends [InkCanvasView]'s input surface to the full editor viewport.
 *
 * The page View keeps its fitted layout size while its content can be zoomed and panned beyond
 * those bounds. Android hit testing does not follow the page transform into its visible overflow,
 * so this proxy maps the complete event through the actual source and destination View matrices.
 */
class InkViewportTouchProxy(context: Context) : FrameLayout(context) {
    var imageAtPoint: (Float, Float) -> String? = { _, _ -> null }
    var onImageLongPress: (String) -> Unit = {}
    private var heldImageId: String? = null
    private var imageHoldDown: MotionEvent? = null
    private var imageHoldClaimed = false
    private val imageHoldSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private val imageHold = Runnable {
        val id = heldImageId ?: return@Runnable
        val down = imageHoldDown ?: return@Runnable
        // Cancel any provisional ink before handing the contact to the image editor.
        val cancel = MotionEvent.obtain(down).apply { action = MotionEvent.ACTION_CANCEL }
        try { forward(cancel, retainDrawingFrame = true) { canvas, packet -> canvas.dispatchTouchEvent(packet) } }
        finally { cancel.recycle() }
        imageHoldClaimed = true
        clearHoverCursor()
        onImageLongPress(id)
    }

    private fun cancelImageHold() {
        removeCallbacks(imageHold)
        heldImageId = null
        imageHoldDown?.recycle()
        imageHoldDown = null
    }

    private fun updateImageHold(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelImageHold()
            imageHoldClaimed = false
            val canvas = target ?: return false
            if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || event.buttonState != 0) return false
            val packet = coordinateMapper.map(event, this, canvas, canvas.viewportState()) ?: return false
            try { heldImageId = imageAtPoint(packet.event.x / canvas.width.coerceAtLeast(1), packet.event.y / canvas.height.coerceAtLeast(1)) }
            finally { packet.close() }
            if (heldImageId != null) {
                imageHoldDown = MotionEvent.obtain(event)
                postDelayed(imageHold, android.view.ViewConfiguration.getLongPressTimeout().toLong())
            }
        }
        val claimed = imageHoldClaimed
        val terminal = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
        if (terminal || event.pointerCount > 1 || imageHoldDown?.let {
                kotlin.math.hypot(event.x - it.x, event.y - it.y) > imageHoldSlop
            } == true) cancelImageHold()
        if (terminal) imageHoldClaimed = false
        return claimed
    }
    var target: InkCanvasView? = null
        set(value) {
            if (field === value) return
            field?.apply {
                externalHoverCursor = false
                onHoverCursorStyleChanged = null
            }
            field = value
            value?.apply {
                externalHoverCursor = true
                onHoverCursorStyleChanged = { refreshCursorStyle() }
            }
            activeDrawingFrame = null
            clearHoverCursor()
        }
    /**
     * Continuous pages: offered every contact that starts on a neighbouring page, in this view's
     * coordinates; returns true when it made that page active. A pen press that activates a page is
     * swallowed for the rest of its contact so it never paints; a finger only activates on a tap,
     * so drags starting on a neighbour still scroll.
     */
    var activatePageAt: (Float, Float) -> Boolean = { _, _ -> false }
    private var swallowingPageActivation = false
    private var fingerTapCandidate = false
    private var fingerTapDownX = 0f
    private var fingerTapDownY = 0f
    private var fingerTapDownTime = 0L
    private val tapSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val coordinateMapper = PointerPacketMapper()
    private val cursorSurface = LowLatencyCanvasView(context)
    @Volatile private var activeDrawingFrame: ViewportFrame? = null
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
        addView(cursorSurface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun currentFrame(canvas: InkCanvasView): ViewportFrame? =
        coordinateMapper.snapshot(this, canvas, canvas.viewportState())

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (updatePageActivation(event)) return true
        if (updateImageHold(event)) return true
        val stylusIndex = event.stylusPointerIndex(activeStylusPointerId)
        if (stylusIndex >= 0 && event.actionMasked in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN)) {
            activeStylusPointerId = event.getPointerId(stylusIndex)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.isStylusEvent()) {
            requestUnbufferedDispatch(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) target?.touchHoldBlocked = heldImageId != null
        val handled = forward(event, retainDrawingFrame = true) { canvas, forwarded -> canvas.dispatchTouchEvent(forwarded) }
        updateContactCursor(event)
        updateEyedropperRing(event)
        return handled
    }

    /** Procreate's and Photoshop's sampling ring: the sampled colour above, the colour before it below. */
    private fun updateEyedropperRing(event: MotionEvent) {
        val colors = target?.eyedropperRingColors()
        val terminal = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
        if (colors == null || terminal) {
            if (ringColors != null) { ringColors = null; cursorSurface.renderFrontBufferedLayer() }
            return
        }
        val index = event.stylusPointerIndex(activeStylusPointerId).takeIf { it >= 0 } ?: 0
        ringX = event.getX(index)
        ringY = event.getY(index)
        ringColors = colors
        cursorSurface.renderFrontBufferedLayer()
    }

    @Volatile private var ringColors: IntArray? = null
    @Volatile private var ringX = 0f
    @Volatile private var ringY = 0f
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringBounds = android.graphics.RectF()

    @WorkerThread
    private fun drawEyedropperRing(canvas: Canvas) {
        val colors = ringColors ?: return
        val density = resources.displayMetrics.density
        val radius = EYEDROPPER_RING_RADIUS_DP * density
        val thickness = EYEDROPPER_RING_THICKNESS_DP * density
        ringBounds.set(ringX - radius, ringY - radius, ringX + radius, ringY + radius)
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = thickness
        ringPaint.color = colors[0] or 0xFF000000.toInt()
        canvas.drawArc(ringBounds, 180f, 180f, false, ringPaint)
        ringPaint.color = colors[1] or 0xFF000000.toInt()
        canvas.drawArc(ringBounds, 0f, 180f, false, ringPaint)
        ringPaint.strokeWidth = 1.5f * density
        ringPaint.color = Color.argb(150, 0, 0, 0)
        canvas.drawCircle(ringX, ringY, radius + thickness / 2f, ringPaint)
        canvas.drawCircle(ringX, ringY, radius - thickness / 2f, ringPaint)
        ringPaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawCircle(ringX, ringY, radius + thickness / 2f + density, ringPaint)
        canvas.drawCircle(ringX, ringY, radius - thickness / 2f - density, ringPaint)
    }

    private fun updatePageActivation(event: MotionEvent): Boolean {
        val terminal = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
        if (swallowingPageActivation) { if (terminal) swallowingPageActivation = false; return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (event.isStylusEvent()) {
                fingerTapCandidate = false
                if (activatePageAt(event.x, event.y)) { swallowingPageActivation = true; return true }
            } else {
                fingerTapCandidate = true
                fingerTapDownX = event.x; fingerTapDownY = event.y; fingerTapDownTime = event.eventTime
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> fingerTapCandidate = false
            MotionEvent.ACTION_MOVE -> if (kotlin.math.hypot(event.x - fingerTapDownX, event.y - fingerTapDownY) > tapSlop) fingerTapCandidate = false
            MotionEvent.ACTION_UP -> if (fingerTapCandidate && event.eventTime - fingerTapDownTime < android.view.ViewConfiguration.getLongPressTimeout()) {
                fingerTapCandidate = false
                // The canvas still sees this tap; make sure it cannot pair with a later tap on the
                // newly active page into a double-tap undo.
                if (activatePageAt(event.x, event.y)) post { target?.cancelPendingTapGestures() }
            }
        }
        return false
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
        drawEyedropperRing(canvas)
        if (!hoverVisible) return
        if (toolCursorGlyph.draw(canvas, hoverToolCode, hoverX, hoverY, resources.displayMetrics.density)) return
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
            if (retainDrawingFrame && terminal) {
                activeDrawingFrame = null
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        cancelImageHold()
        imageHoldClaimed = false
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
        hoverToolCode = snapshot.getOrElse(8) { 0f }.toInt()
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
            scale = canvas.viewportScale.coerceAtLeast(app.inkloom.document.ViewportTransform.MINIMUM_SCALE),
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
        hoverToolCode = snapshot.getOrElse(8) { 0f }.toInt()
        if (snapshot[0] == 0f) return clearHoverCursor()
        val pointerIndex = event.stylusPointerIndex(activeStylusPointerId)
        if (pointerIndex < 0) return clearHoverCursor()
        updateCursorGeometry(
            event = event,
            pointerIndex = pointerIndex,
            snapshot = snapshot,
            scale = canvas.viewportScale.coerceAtLeast(app.inkloom.document.ViewportTransform.MINIMUM_SCALE),
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
        if (ringColors != null) cursorSurface.renderFrontBufferedLayer() else cursorSurface.clear()
    }

    private val toolCursorGlyph = ToolCursorGlyph()
    @Volatile private var hoverToolCode = 0

    private fun refreshCursorStyle() {
        val canvas = target ?: return
        val snapshot = canvas.hoverCursorSnapshot()
        hoverToolCode = snapshot.getOrElse(8) { 0f }.toInt()
        hoverWidth = snapshot[3] * canvas.viewportScale.coerceAtLeast(app.inkloom.document.ViewportTransform.MINIMUM_SCALE)
        hoverHeight = snapshot.getOrElse(4) { snapshot[3] } * canvas.viewportScale.coerceAtLeast(app.inkloom.document.ViewportTransform.MINIMUM_SCALE)
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
        const val EYEDROPPER_RING_RADIUS_DP = 52f
        const val EYEDROPPER_RING_THICKNESS_DP = 16f
    }
}
