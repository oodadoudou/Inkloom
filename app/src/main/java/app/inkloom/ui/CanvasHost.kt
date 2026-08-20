package app.inkloom.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.widget.FrameLayout
import androidx.annotation.WorkerThread
import androidx.graphics.lowlatency.LowLatencyCanvasView
import app.inkloom.engine.v3.api.ActiveStrokeCompositor
import app.inkloom.engine.v3.api.ActiveStrokeTailPlanner
import app.inkloom.engine.v3.api.StrokeTrace
import app.inkloom.engine.v3.api.PaintingTruthDiagnostics
import app.inkloom.engine.v3.render.StrokeRenderPacket

/** Android host for the V3 compositor. This adapter belongs to UI so the engine never depends on
 * [InkCanvasView], Compose, panels or another application-owned view type. */
internal class CanvasHost(context: Context) : FrameLayout(context), ActiveStrokeCompositor {
    val canvasView = InkCanvasView(context)
    private val lowLatencyView = LowLatencyCanvasView(context)

    @Volatile private var activePacket: StrokeRenderPacket? = null
    @Volatile private var lastRenderedPacket: StrokeRenderPacket? = null
    @Volatile private var pendingCommittedSequence: Long? = null
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

    override val hasActiveInk: Boolean
        get() = activePacket?.strokes?.isNotEmpty() == true

    override fun render(packet: StrokeRenderPacket) {
        activePacket = packet
        lastRenderedPacket = packet
        lowLatencyView.renderFrontBufferedLayer()
    }

    override fun commit(packet: StrokeRenderPacket) {
        PaintingTruthDiagnostics.compareHandoff(lastRenderedPacket, packet)
        activePacket = packet
        pendingCommittedSequence = packet.sequence
        lowLatencyView.renderFrontBufferedLayer()
        lowLatencyView.commit()
        packet.primaryStrokeId?.let { id -> StrokeTrace.mark(id, StrokeTrace.STAGE_FRONT_BUFFER_COMMIT) }
    }

    override fun onDocumentFramePresented(packetSequence: Long) {
        if (pendingCommittedSequence != packetSequence) return
        val packet = activePacket?.takeIf { it.sequence == packetSequence } ?: return
        pendingCommittedSequence = null
        // The stable View has completed drawPageContent for this packet. Clear after that draw,
        // never merely because ACTION_UP occurred.
        post {
            if (activePacket?.sequence != packetSequence) return@post
            activePacket = null
            synchronized(activeLock) { tailPlanner.reset() }
            lowLatencyView.clear()
            packet.primaryStrokeId?.let { id -> StrokeTrace.mark(id, StrokeTrace.STAGE_TRANSIENT_HIDDEN) }
            canvasView.resetActiveRenderState()
        }
    }

    override fun cancel() {
        activePacket = null
        lastRenderedPacket = null
        pendingCommittedSequence = null
        synchronized(activeLock) { tailPlanner.reset() }
        lowLatencyView.clear()
        canvasView.resetActiveRenderState()
    }

    @WorkerThread
    private fun drawComplete(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvasView.resetActiveRenderState()
        val snapshot = activePacket?.strokes.orEmpty()
        if (snapshot.isNotEmpty()) canvasView.drawActiveStrokes(canvas, snapshot, completeRedraw = true)
        synchronized(activeLock) { tailPlanner.complete(snapshot) }
    }

    @WorkerThread
    private fun drawIncremental(canvas: Canvas) {
        val snapshot = activePacket?.strokes.orEmpty()
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
        activePacket = null
        lastRenderedPacket = null
        pendingCommittedSequence = null
        synchronized(activeLock) { tailPlanner.reset() }
        canvasView.resetActiveRenderState()
        super.onDetachedFromWindow()
    }
}
