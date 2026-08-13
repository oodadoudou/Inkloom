package app.inkloom.engine.v3.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.widget.FrameLayout
import androidx.annotation.WorkerThread
import androidx.graphics.lowlatency.LowLatencyCanvasView
import app.inkloom.document.InkStroke
import app.inkloom.ui.InkCanvasView

/**
 * Stable page-local host used by non-editor canvas previews. The main editor uses the full-window
 * [app.inkloom.ui.InkViewportTouchProxy], while both hosts share the same V3 active-stroke
 * compositor contract and render-thread tail planner.
 */
internal class CanvasHost(context: Context) : FrameLayout(context), ActiveStrokeCompositor {
    val canvasView = InkCanvasView(context)
    private val lowLatencyView = LowLatencyCanvasView(context)

    @Volatile private var activeStrokes: List<InkStroke> = emptyList()
    private val activeLock = Any()
    private val tailPlanner = ActiveStrokeTailPlanner()

    init {
        clipChildren = true
        clipToPadding = true
        lowLatencyView.clipChildren = true
        lowLatencyView.clipToPadding = true
        lowLatencyView.setBackgroundColor(Color.TRANSPARENT)
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(lowLatencyView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        canvasView.activeStrokeSurface = this
        lowLatencyView.setRenderCallback(object : LowLatencyCanvasView.Callback {
            @WorkerThread
            override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) = drawComplete(canvas)

            @WorkerThread
            override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) = drawIncremental(canvas)
        })
    }

    override fun render(strokes: List<InkStroke>) {
        activeStrokes = strokes
        lowLatencyView.renderFrontBufferedLayer()
    }

    override fun commit(strokes: List<InkStroke>) {
        activeStrokes = strokes
        lowLatencyView.renderFrontBufferedLayer()
        lowLatencyView.commit()
        postOnAnimation {
            activeStrokes = emptyList()
            synchronized(activeLock) { tailPlanner.reset() }
            lowLatencyView.clear()
            canvasView.resetActiveRenderState()
        }
    }

    override fun cancel() {
        activeStrokes = emptyList()
        synchronized(activeLock) { tailPlanner.reset() }
        lowLatencyView.clear()
        canvasView.resetActiveRenderState()
    }

    @WorkerThread
    private fun drawComplete(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvasView.resetActiveRenderState()
        val snapshot = activeStrokes
        if (snapshot.isNotEmpty()) canvasView.drawActiveStrokes(canvas, snapshot)
        synchronized(activeLock) { tailPlanner.complete(snapshot) }
    }

    @WorkerThread
    private fun drawIncremental(canvas: Canvas) {
        val snapshot = activeStrokes
        if (snapshot.isEmpty()) return
        val forceComplete = canvasView.requiresCompleteActiveReplay(snapshot)
        val plan = synchronized(activeLock) {
            if (forceComplete) tailPlanner.complete(snapshot) else tailPlanner.incremental(snapshot)
        }
        if (plan.clearBeforeDraw) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (plan.strokes.isNotEmpty()) canvasView.drawActiveStrokes(canvas, plan.strokes, plan.incrementalStampDraw)
    }

    override fun onDetachedFromWindow() {
        canvasView.activeStrokeSurface = null
        activeStrokes = emptyList()
        synchronized(activeLock) { tailPlanner.reset() }
        canvasView.resetActiveRenderState()
        super.onDetachedFromWindow()
    }
}
