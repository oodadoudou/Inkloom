package app.inkloom.ui

import android.content.Context
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

    private val coordinateMapper = PointerCoordinateMapper()

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return forward(event) { canvas, forwarded -> canvas.dispatchTouchEvent(forwarded) }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        return forward(event) { canvas, forwarded -> canvas.onHoverEvent(forwarded) }
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
            coordinateMapper.map(forwarded, this, canvas, viewport) && dispatch(canvas, forwarded)
        } finally {
            forwarded.recycle()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
