package app.inkloom.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import android.widget.FrameLayout
import app.inkloom.brushpresetv5.BrushOutput
import app.inkloom.brushpresetv5.BrushPresetV5
import app.inkloom.document.LayerIds
import app.inkloom.document.PressureCalibration
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.runtime.RawDocumentSample
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.settings.AppSettings
import app.inkloom.settings.text
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Studio-only input history. Re-rendering never touches a notebook or its history. */
internal class BrushDrawingPadView(context: Context) : FrameLayout(context) {
    internal val canvas = InkCanvasView(context)
    private val strokes = ArrayDeque<List<RawDocumentSample>>()
    private var contact: MutableList<RawDocumentSample>? = null
    private var configuration: Configuration? = null
    private var liveConfiguration: Configuration? = null
    private var generation = 0L
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var replay: Job? = null
    private var replayRequired = false
    internal var publishedReplayGeneration = 0L
        private set
    internal val retainedStrokeCount: Int get() = strokes.size

    private data class Configuration(val preset: BrushPresetV5, val settings: AppSettings, val color: Int)

    init {
        addView(canvas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        canvas.activeLayerId = LayerIds.INK
        canvas.layerOrder = listOf(LayerIds.INK)
        canvas.pageModelWidth = PAD_WIDTH.toFloat()
        canvas.pageModelHeight = PAD_HEIGHT.toFloat()
        canvas.pagePaperColor = Color.WHITE
        canvas.setRasterLayerSurfaces(mapOf(LayerIds.INK to PixelTileSurface(PAD_WIDTH, PAD_HEIGHT)))
        canvas.setOnTouchListener { _, event ->
            val config = configuration ?: return@setOnTouchListener true
            val stylus = event.getToolType(0) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)
            if ((!stylus && !config.settings.fingerDrawing) || event.pointerCount > 1) {
                if (contact != null) scheduleReplay()
                contact = null
                return@setOnTouchListener false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    generation++; replay?.cancel()
                    contact = mutableListOf()
                    applyLive(config)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_CANCEL -> { contact = null; scheduleReplay(); return@setOnTouchListener false }
            }
            val points = contact ?: return@setOnTouchListener false
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                for (index in 0 until event.historySize) points += sample(event, index)
            }
            points += sample(event, null)
            if (points.size > MAX_SAMPLES) {
                contact = null
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                try { canvas.onTouchEvent(cancel) } finally { cancel.recycle() }
                canvas.onPaintCapabilityRejected(config.settings.language.text(
                    "This test stroke is too long. Clear the drawing pad and try again.", "这条试笔笔迹过长，请清空试笔板后重试。"))
                scheduleReplay()
                return@setOnTouchListener true
            }
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                strokes.addLast(points.toList()); contact = null
                var pruned = false
                while (strokes.size > 1 && strokes.sumOf { it.size } > MAX_SAMPLES) {
                    strokes.removeFirst(); pruned = true
                }
                if (pruned || replayRequired || liveConfiguration != configuration) scheduleReplay()
            }
            false
        }
    }

    fun configure(preset: BrushPresetV5, settings: AppSettings, previewColor: Int = Color.rgb(17, 17, 17)) {
        // Regularizing a held stroke changes its recorded trajectory. Studio previews remain raw
        // input tests; the notebook's draw-and-hold preference is not modified.
        val next = Configuration(preset, settings.copy(drawAndHoldEnabled = false), previewColor)
        if (next == configuration) return
        if (configuration == null && preset.output == BrushOutput.ERASE) {
            canvas.setRasterLayerSurfaces(mapOf(LayerIds.INK to background(preset.output)))
        }
        configuration = next
        replayRequired = true
        if (contact == null) {
            applyLive(next)
            scheduleReplay()
        }
    }

    private fun applyLive(config: Configuration) {
        canvas.applyBrushPresetV5Configuration(config.preset, config.settings, brushColor = config.color)
        liveConfiguration = config
    }

    private fun sample(event: MotionEvent, history: Int?): RawDocumentSample {
        val time = if (history == null) event.eventTime else event.getHistoricalEventTime(history)
        fun axis(axis: Int) = if (history == null) event.getAxisValue(axis) else event.getHistoricalAxisValue(axis, history)
        return RawDocumentSample(
            x = (if (history == null) event.x else event.getHistoricalX(history)) / canvas.width.coerceAtLeast(1),
            y = (if (history == null) event.y else event.getHistoricalY(history)) / canvas.height.coerceAtLeast(1),
            eventTimeNanos = time.coerceAtLeast(0) * 1_000_000L,
            enqueuedTimeNanos = time.coerceAtLeast(0) * 1_000_000L,
            pressure = if (history == null) event.pressure else event.getHistoricalPressure(history),
            tiltRadians = axis(MotionEvent.AXIS_TILT), orientationRadians = axis(MotionEvent.AXIS_ORIENTATION),
            tool = if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) InputTool.STYLUS_ERASER else InputTool.STYLUS,
        )
    }

    private fun scheduleReplay() {
        val config = configuration ?: return
        replayRequired = true
        val version = ++generation
        val inputs = strokes.toList()
        replay?.cancel()
        replay = scope.launch {
            delay(60) // Coalesce slider updates; the last setting always gets its own render.
            try {
                workers.withPermit {
                    val renderer = InkCanvasView(context).apply {
                        layout(0, 0, canvas.width.coerceAtLeast(1), canvas.height.coerceAtLeast(1))
                        activeLayerId = LayerIds.INK; layerOrder = listOf(LayerIds.INK)
                        pageModelWidth = PAD_WIDTH.toFloat(); pageModelHeight = PAD_HEIGHT.toFloat()
                        applyBrushPresetV5Configuration(config.preset, config.settings, brushColor = config.color, prepareAssets = false)
                    }
                    val surface = withContext(Dispatchers.Default) {
                        canvas.awaitStudioPaintIdle()
                        ensureActive()
                        val target = background(config.preset.output)
                        renderer.setRasterLayerSurfaces(mapOf(LayerIds.INK to target))
                        renderer.prepareBrushAssetsNow()
                        renderer.renderIsolatedBrushStrokes(inputs.map { stroke -> stroke.map { sample ->
                            sample.copy(x = sample.x * PAD_WIDTH, y = sample.y * PAD_HEIGHT,
                                pressure = PressureCalibration.apply(sample.pressure,
                                    config.settings.pressureMinimumInput, config.settings.pressureMaximumInput,
                                    config.settings.pressureSensitivity, config.settings.pressureResponseCurve))
                        } })
                        target
                    }
                    ensureActive()
                    if (version == generation && contact == null) {
                        canvas.setRasterLayerSurfaces(mapOf(LayerIds.INK to surface))
                        applyLive(config)
                        canvas.invalidate()
                        publishedReplayGeneration = version
                        replayRequired = false
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version == generation) canvas.onPaintCapabilityRejected(config.settings.language.text(
                    "Unable to update the drawing pad", "暂时无法更新试笔板"))
                android.util.Log.w("BrushDrawingPad", "Preview replay failed", error)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        if (configuration != null) scheduleReplay()
    }

    override fun onDetachedFromWindow() {
        generation++; replay?.cancel(); scope.cancel(); contact = null
        super.onDetachedFromWindow()
    }

    private fun background(output: BrushOutput): PixelTileSurface {
        if (output != BrushOutput.ERASE) return PixelTileSurface(PAD_WIDTH, PAD_HEIGHT)
        val bitmap = Bitmap.createBitmap(PAD_WIDTH, PAD_HEIGHT, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb(83, 91, 87))
            PixelTileBitmapAdapter.import(bitmap)
        } finally { bitmap.recycle() }
    }

    private companion object {
        const val PAD_WIDTH = 900
        const val PAD_HEIGHT = 1200
        const val MAX_SAMPLES = 32_768
        val workers = Semaphore(1)
    }
}
