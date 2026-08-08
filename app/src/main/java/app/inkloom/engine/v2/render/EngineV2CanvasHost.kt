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
                drawLatest(canvas)
            }

            @WorkerThread
            override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) {
                drawLatest(canvas)
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
            lowLatencyView.clear()
        }
    }

    override fun cancel() {
        activeStrokes = emptyList()
        lowLatencyView.cancel()
    }

    @WorkerThread
    private fun drawLatest(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val snapshot = activeStrokes
        if (snapshot.isNotEmpty()) canvasView.drawEngineV2ActiveStrokes(canvas, snapshot)
    }

    override fun onDetachedFromWindow() {
        canvasView.activeStrokeSurface = null
        activeStrokes = emptyList()
        super.onDetachedFromWindow()
    }
}
