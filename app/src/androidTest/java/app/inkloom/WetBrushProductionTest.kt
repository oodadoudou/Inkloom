package app.inkloom

import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv5.*
import app.inkloom.document.BrushCurvePoint
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.runtime.RawDocumentSample
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.settings.AppSettings
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushPresetV5Configuration
import org.junit.Assert.*
import org.junit.Test

class WetBrushProductionTest {
    @Test fun compiledWetPressureChannelsAffectNativePixels() {
        val curve = listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(.5f, .8f), BrushCurvePoint(1f, 1f))
        val base = BrushPresetV5(
            identity = PresetIdentity("test-wet", "Wet test", family = BrushFamily.WET_PAINT, renderBrushId = "test-wet"),
            renderSeed = 37,
            tip = TipSettings(120f), deposition = DepositionSettings(1f, 1f),
            dynamics = DynamicsSettings(PressureMappings(widthEnabled = false), TiltMappings()),
            wetMix = WetMixSettings(dilution = 0f, charge = 1f, attack = .6f, pull = 0f),
        )
        for (channel in listOf("opacity", "flow")) {
            val pressure = if (channel == "opacity") base.dynamics.pressure.copy(
                depositionEnabled = true, depositionMinimum = .2f, depositionCurve = curve)
            else base.dynamics.pressure.copy(flowEnabled = true, flowMinimum = .2f, flowCurve = curve)
            val enabled = base.copy(dynamics = base.dynamics.copy(pressure = pressure))
            val light = alpha(enabled, .2f)
            val heavy = alpha(enabled, .9f)
            assertTrue("Compiled wet $channel ignored pressure", heavy > light && light > 0)
            assertEquals("Disabled $channel must remain constant", alpha(base, .2f), alpha(base, .9f))
        }
    }

    private fun alpha(preset: BrushPresetV5, pressure: Float): Int {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var view: InkCanvasView
        instrumentation.runOnMainSync {
            view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 160, 160); activeLayerId = "test"
                setRasterLayerSurfaces(mapOf("test" to PixelTileSurface(160, 160)))
                applyBrushPresetV5Configuration(preset, AppSettings())
                prepareBrushAssetsNow()
            }
        }
        val sample = RawDocumentSample(80f, 80f, 8_000_000L, 8_000_000L, pressure, 0f, 0f, InputTool.STYLUS)
        val bitmap = view.renderIsolatedBrushPreview(listOf(sample, sample.copy(eventTimeNanos = 16_000_000L)))
        return try { bitmap.getPixel(80, 80) ushr 24 } finally { bitmap.recycle() }
    }
}
