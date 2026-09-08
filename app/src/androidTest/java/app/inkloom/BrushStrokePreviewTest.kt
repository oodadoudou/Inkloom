package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.runtime.RawDocumentSample
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.BrushToolRole
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.settings.AppSettings
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushPresetV5Configuration
import org.junit.Assert.*
import org.junit.Test

class BrushStrokePreviewTest {
    @Test fun previewEqualsRealStylusRasterForPencilAndEraser() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val brushes = listOf(SystemBrushCatalog.brushes.first { it.id == "system-hb-pencil" },
            SystemBrushCatalog.brushes.first { it.toolBinding.role == BrushToolRole.ERASE })
        for (brush in brushes) {
            val preset = BrushPresetV5Import.requirePreset(brush)
            fun surface(): PixelTileSurface = if (brush.toolBinding.role == BrushToolRole.ERASE) {
                Bitmap.createBitmap(512, 112, Bitmap.Config.ARGB_8888).let {
                    try { it.eraseColor(Color.BLACK); PixelTileBitmapAdapter.import(it) } finally { it.recycle() }
                }
            } else PixelTileSurface(512, 112)
            val realSurface = surface()
            lateinit var preview: InkCanvasView
            lateinit var real: InkCanvasView
            instrumentation.runOnMainSync {
                fun create(target: PixelTileSurface) = InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 512, 112)
                    activeLayerId = "test"; layerOrder = listOf("test")
                    pageModelWidth = 512f; pageModelHeight = 112f
                    setRasterLayerSurfaces(mapOf("test" to target))
                    applyBrushPresetV5Configuration(preset, AppSettings(), brushSize = 120f)
                    prepareBrushAssetsNow()
                }
                preview = create(surface()); real = create(realSurface)
            }
            val samples = (0..30).map {
                RawDocumentSample(40f + it * 14f, 45f + (it % 4), (it + 1) * 8_000_000L,
                    (it + 1) * 8_000_000L, .8f, .25f, 0f, InputTool.STYLUS)
            }
            val image = preview.renderIsolatedBrushPreview(samples)
            samples.forEachIndexed { index, sample ->
                val action = when (index) { 0 -> MotionEvent.ACTION_DOWN; samples.lastIndex -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val motion = MotionEvent.obtain(8, sample.eventTimeNanos / 1_000_000L, action, 1,
                    arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
                    arrayOf(MotionEvent.PointerCoords().apply {
                        x = sample.x; y = sample.y; pressure = sample.pressure
                        setAxisValue(MotionEvent.AXIS_TILT, sample.tiltRadians)
                    }), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
                try { instrumentation.runOnMainSync { real.onTouchEvent(motion) } } finally { motion.recycle() }
            }
            real.awaitV5StrokeWorkerIdleForTest()
            instrumentation.runOnMainSync { }
            val actual = PixelTileBitmapAdapter.render(realSurface.snapshot())
            try {
                assertTrue("Preview diverged from actual contact: ${brush.name}", image.sameAs(actual))
                assertTrue("The real gesture must execute", realSurface.revision > if (brush.toolBinding.role == BrushToolRole.ERASE) 1L else 0L)
            } finally { image.recycle(); actual.recycle() }
        }
    }
}
