package app.inkloom.engine.v2.render

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
 * The single editor surface for Engine V2. Active ink is presented in the hardware front buffer;
 * committed scene content remains in [canvasView]'s stable View layer.
 */
internal class EngineV2CanvasHost(context: Context) : FrameLayout(context), EngineV2ActiveStrokeSurface {
    val canvasView = InkCanvasView(context)
    private val lowLatencyView = LowLatencyCanvasView(context)

    @Volatile
    private var activeStrokes: List<InkStroke> = emptyList()
    private val activeLock = Any()
    private val tailPlanner = EngineV2ActiveTailPlanner()

    init {
        clipChildren = false
        clipToPadding = false
        lowLatencyView.clipChildren = false
        lowLatencyView.clipToPadding = false
        lowLatencyView.setBackgroundColor(Color.TRANSPARENT)
        addView(
            canvasView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        // LowLatencyCanvasView is a render surface, not a generic ViewGroup. It must overlay the
        // stable scene as a sibling so the front buffer can be retired independently on commit.
        addView(
            lowLatencyView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        canvasView.activeStrokeSurface = this
        lowLatencyView.setRenderCallback(object : LowLatencyCanvasView.Callback {
            @WorkerThread
            override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) {
                drawComplete(canvas)
            }

            @WorkerThread
            override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) {
                drawIncremental(canvas)
            }
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
        // The stable child has been invalidated by the stroke commit. Retire the transient layer
        // on the following display frame, after stable content has had an opportunity to draw.
        postOnAnimation {
            activeStrokes = emptyList()
            synchronized(activeLock) { tailPlanner.reset() }
            lowLatencyView.clear()
            canvasView.resetEngineV2ActiveRenderState()
        }
    }

    override fun cancel() {
        activeStrokes = emptyList()
        synchronized(activeLock) { tailPlanner.reset() }
        // cancel() is best-effort and may leave an already submitted front-buffer frame visible.
        // A navigation gesture must remove that frame immediately or it appears as a stationary
        // ghost while the page underneath pans or zooms.
        lowLatencyView.clear()
        canvasView.resetEngineV2ActiveRenderState()
    }

    @WorkerThread
    private fun drawComplete(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvasView.resetEngineV2ActiveRenderState()
        val snapshot = activeStrokes
        if (snapshot.isNotEmpty()) canvasView.drawEngineV2ActiveStrokes(canvas, snapshot)
        synchronized(activeLock) { tailPlanner.complete(snapshot) }
    }

    /**
     * Front-buffer contents persist between callbacks. Simple vector ink can therefore append only
     * its new tail instead of clearing and replaying an ever-growing stroke. Complex stamp brushes
     * retain full deterministic replay until their native incremental dab planner lands.
     */
    @WorkerThread
    private fun drawIncremental(canvas: Canvas) {
        val snapshot = activeStrokes
        if (snapshot.isEmpty()) return
        val plan = synchronized(activeLock) { tailPlanner.incremental(snapshot) }
        if (plan.clearBeforeDraw) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (plan.strokes.isNotEmpty()) {
            canvasView.drawEngineV2ActiveStrokes(canvas, plan.strokes, plan.incrementalStampDraw)
        }
    }

    override fun onDetachedFromWindow() {
        canvasView.activeStrokeSurface = null
        activeStrokes = emptyList()
        synchronized(activeLock) { tailPlanner.reset() }
        canvasView.resetEngineV2ActiveRenderState()
        super.onDetachedFromWindow()
    }
}
