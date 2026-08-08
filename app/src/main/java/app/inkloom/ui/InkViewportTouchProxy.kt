package app.inkloom.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View

/**
 * Extends [InkCanvasView]'s input surface to the full editor viewport.
 *
 * The page View keeps its fitted layout size while its content can be zoomed and panned beyond
 * those bounds. Android hit testing does not follow custom Canvas transforms, so contacts on that
 * visible overflow would otherwise never reach the page. This proxy forwards the untouched
 * pointer stream after converting only its origin from proxy-local to page-View-local space.
 */
class InkViewportTouchProxy(context: Context) : View(context) {
    var target: InkCanvasView? = null

    private val proxyLocation = IntArray(2)
    private val targetLocation = IntArray(2)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val canvas = target?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 } ?: return false
        getLocationInWindow(proxyLocation)
        canvas.getLocationInWindow(targetLocation)
        val forwarded = MotionEvent.obtain(event)
        return try {
            forwarded.offsetLocation(
                (proxyLocation[0] - targetLocation[0]).toFloat(),
                (proxyLocation[1] - targetLocation[1]).toFloat(),
            )
            canvas.dispatchTouchEvent(forwarded)
        } finally {
            forwarded.recycle()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
