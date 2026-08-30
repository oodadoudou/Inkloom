package app.inkloom.importer

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import app.inkloom.brushpresetv5.BrushOutput
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.tools.RasterLayerPatch
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Executes an imported preset through the same InkCanvasView → V5 PaintOp path used by drawing. */
internal class ProductionBrushRenderProbe(context: Context) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    fun probe(brush: ImportedBrush): BrushRenderProbeResult {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return BrushRenderProbeResult.Failed("RENDER_PROBE_MUST_RUN_OFF_MAIN_THREAD")
        }
        val preset = runCatching { BrushPresetV5Import.requirePreset(brush) }.getOrElse {
            return BrushRenderProbeResult.Failed(it.message ?: "INVALID_V5_PRESET")
        }
        val surface = PixelTileSurface(PROBE_SIZE, PROBE_SIZE)
        if (preset.output == BrushOutput.ERASE) prefill(surface)
        val baselineRevision = surface.revision
        val viewRef = AtomicReference<InkCanvasView?>()
        val patchRef = AtomicReference<RasterLayerPatch?>()
        val rejectionRef = AtomicReference<String?>()
        val setupFailure = AtomicReference<Throwable?>()
        val setup = CountDownLatch(1)
        mainHandler.post {
            runCatching {
                InkCanvasView(applicationContext).also { view ->
                    viewRef.set(view)
                    view.layout(0, 0, PROBE_SIZE, PROBE_SIZE)
                    view.activeLayerId = PROBE_LAYER
                    view.setRasterLayerSurfaces(mapOf(PROBE_LAYER to surface))
                    view.onRasterLayerCommitted = { commit -> patchRef.getAndSet(commit.capturePatch())?.close() }
                    view.onPaintCapabilityRejected = rejectionRef::set
                    view.applyBrushConfiguration(
                        EditorTool.PEN,
                        AppSettings(),
                        brush,
                        brush.defaultSize.coerceIn(8f, 64f),
                        1f,
                        Color.BLACK,
                        EraserPreset.NORMAL_FREE,
                    )
                    view.prepareBrushAssetsNow()
                    val now = SystemClock.uptimeMillis()
                    dispatch(view, MotionEvent.ACTION_DOWN, 48f, 128f, now)
                    dispatch(view, MotionEvent.ACTION_MOVE, 128f, 112f, now + 8)
                    dispatch(view, MotionEvent.ACTION_UP, 208f, 128f, now + 16)
                }
            }.onFailure(setupFailure::set)
            setup.countDown()
        }
        if (!setup.await(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return BrushRenderProbeResult.Failed("PRODUCTION_RENDER_PROBE_SETUP_TIMEOUT")
        }
        setupFailure.get()?.let { return BrushRenderProbeResult.Failed(it.message ?: "PRODUCTION_RENDER_PROBE_SETUP_FAILED") }
        val view = viewRef.get() ?: return BrushRenderProbeResult.Failed("PRODUCTION_RENDER_PROBE_VIEW_MISSING")
        return try {
            view.awaitV5StrokeWorkerIdleForTest()
            val mainDrain = CountDownLatch(1)
            mainHandler.post(mainDrain::countDown)
            if (!mainDrain.await(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return BrushRenderProbeResult.Failed("PRODUCTION_RENDER_PROBE_COMMIT_TIMEOUT")
            }
            rejectionRef.get()?.let { return BrushRenderProbeResult.Failed(it) }
            val committed = patchRef.get() != null && surface.revision > baselineRevision
            if (!committed) BrushRenderProbeResult.Failed("EMPTY_PRODUCTION_RASTER_COMMIT")
            else BrushRenderProbeResult.Succeeded
        } catch (failure: Throwable) {
            BrushRenderProbeResult.Failed(failure.message ?: "PRODUCTION_RENDER_PROBE_FAILED")
        } finally {
            patchRef.getAndSet(null)?.close()
            mainHandler.post {
                view.onRasterLayerCommitted = null
                view.onPaintCapabilityRejected = {}
                view.setRasterLayerSurfaces(emptyMap())
            }
        }
    }

    private fun prefill(surface: PixelTileSurface) {
        val transaction = surface.beginWrite("Imported eraser render probe backdrop")
        transaction.editTile(PixelTileKey(0, 0), PixelRect(0, 0, PROBE_SIZE, PROBE_SIZE)) { pixels, _ ->
            pixels.fill(Color.BLACK)
        }
        transaction.commit().close()
    }

    private fun dispatch(view: InkCanvasView, action: Int, x: Float, y: Float, eventTime: Long) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = .75f
            size = .1f
        })
        MotionEvent.obtain(
            eventTime - 16,
            eventTime,
            action,
            1,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        ).also { event ->
            try {
                check(view.onTouchEvent(event)) { "PRODUCTION_INPUT_NOT_CONSUMED" }
            } finally {
                event.recycle()
            }
        }
    }

    private companion object {
        const val PROBE_SIZE = 256
        const val PROBE_LAYER = "import-render-probe"
        const val PROBE_TIMEOUT_SECONDS = 5L
    }
}
