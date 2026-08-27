package app.inkloom.ui

import android.content.Context
import android.widget.FrameLayout

/**
 * Stable V5 page host.
 *
 * Active paint is the sparse delta rendered by [InkCanvasView] itself. There is deliberately no
 * second front-buffer ink Surface here: a second renderer would violate the single-render-product
 * contract and can briefly replace or double the committed page during handoff.
 */
internal class CanvasHost(context: Context) : FrameLayout(context) {
    val canvasView = InkCanvasView(context)

    init {
        clipChildren = true
        clipToPadding = true
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
