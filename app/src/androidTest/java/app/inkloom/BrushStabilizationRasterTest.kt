package app.inkloom

import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv5.*
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.runtime.RawDocumentSample
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.settings.AppSettings
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushPresetV5Configuration
import org.junit.Assert.*
import org.junit.Test

class BrushStabilizationRasterTest {
    @Test fun globalAndPerBrushControlsIndependentlyChangeProductionRaster() {
        val raw = render(0f, 0f)
        val global = render(.8f, 0f)
        val brush = render(0f, .8f)
        val both = render(.8f, .8f)
        assertFalse("Global control must change real pixels", raw.contentEquals(global))
        assertArrayEquals("Single active stage must be equivalent", global, brush)
        assertFalse("The two settings must not override each other", global.contentEquals(both))
    }

    private fun render(global: Float, smoothing: Float): IntArray {
        val preset = BrushPresetV5(identity = PresetIdentity("stabilization", "Stabilization", family = BrushFamily.INK, renderBrushId = "stabilization"),
            renderSeed = 17, tip = TipSettings(18f), deposition = DepositionSettings(),
            dynamics = DynamicsSettings(PressureMappings(widthEnabled = false), TiltMappings()), stroke = StrokeSettings(smoothing = smoothing))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var view: InkCanvasView
        instrumentation.runOnMainSync {
            view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 192, 192); activeLayerId = "test"
                setRasterLayerSurfaces(mapOf("test" to PixelTileSurface(192, 192)))
                applyBrushPresetV5Configuration(preset, AppSettings(globalStrokeStabilization = global))
            }
        }
        val samples = (0..60).map { index -> RawDocumentSample(24f + index * 2f, 96f + if (index % 2 == 0) 2f else -2f,
            (index + 1) * 8_000_000L, (index + 1) * 8_000_000L, .7f, .3f, .5f, InputTool.STYLUS) }
        val bitmap = view.renderIsolatedBrushPreview(samples)
        return IntArray(192 * 192).also { bitmap.getPixels(it, 0, 192, 0, 0, 192, 192); bitmap.recycle() }
    }
}
