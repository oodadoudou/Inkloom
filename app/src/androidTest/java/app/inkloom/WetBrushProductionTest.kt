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
    @Test fun compiledWetMasterOpacityAndFlowRemainDistinctOnLongStrokes() {
        fun render(opacity: Float, flow: Float): Int {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            lateinit var view: InkCanvasView
            instrumentation.runOnMainSync {
                view = InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 160, 160); activeLayerId = "test"
                    setRasterLayerSurfaces(mapOf("test" to PixelTileSurface(160, 160)))
                    applyBrushPresetV5Configuration(BrushPresetV5(
                        identity = PresetIdentity("wet-opacity", "Wet opacity", family = BrushFamily.WET_PAINT, renderBrushId = "wet-opacity"),
                        renderSeed = 41, tip = TipSettings(20f), deposition = DepositionSettings(opacity, flow),
                        dynamics = DynamicsSettings(PressureMappings(widthEnabled = false), TiltMappings()),
                        wetMix = WetMixSettings(dilution = 0f, charge = 1f, attack = 1f, pull = 0f)), AppSettings())
                    prepareBrushAssetsNow()
                }
            }
            val bitmap = view.renderIsolatedBrushPreview(listOf(20f, 140f).mapIndexed { i, x ->
                RawDocumentSample(x, 80f, (i + 1) * 8_000_000L, (i + 1) * 8_000_000L, 1f, 0f, 0f, InputTool.STYLUS)
            })
            return try { bitmap.getPixel(80, 80) ushr 24 } finally { bitmap.recycle() }
        }
        val opacity = render(.25f, 1f)
        val flow = render(1f, .25f)
        assertTrue("Master opacity saturates: $opacity", opacity in 40..64)
        assertTrue("Flow was incorrectly used as whole-stroke opacity: $flow", flow > opacity + 40)
    }

    @Test fun emptyGrainRemainsTransparentInNativeAndSelectionPaths() {
        for (selected in listOf(false, true)) {
            val surface = PixelTileSurface(96, 96)
            val mask = if (selected) app.inkloom.engine.v5.surface.MaskTileSurface(96, 96, initialDefaultAlpha = 255).snapshot() else null
            val paint = app.inkloom.engine.v5.raster.WetPaintOp(surface,
                app.inkloom.engine.v5.raster.WetPaintDefinition(48f, 0xff2040a0.toInt(),
                    charge = 1f, attack = 1f, dilution = 0f,
                    grain = app.inkloom.engine.v5.material.BrushTipMask(4, 4, ByteArray(16)), grainDensity = 1f), mask)
                .begin(app.inkloom.engine.v5.raster.PaintOpContext(39L, surface.revision, "empty-grain"))
                as app.inkloom.engine.v5.raster.WetPaintOp.Session
            val stroke = app.inkloom.engine.v5.stroke.DirectStrokeSession(39L, 24f)
            fun sample(phase: app.inkloom.engine.v5.input.InputPhase, time: Long) =
                app.inkloom.engine.v5.input.InputSample(sequence = time,
                    phase = phase, tool = InputTool.STYLUS,
                    point = app.inkloom.engine.v5.coordinates.DocumentPoint(48f, 48f),
                    eventTimeNanos = time, enqueuedTimeNanos = time, pressure = .8f,
                    tiltRadians = 0f, orientationRadians = 0f)
            paint.apply(stroke.begin(sample(app.inkloom.engine.v5.input.InputPhase.DOWN, 1L)))
            paint.finish(stroke.finish(sample(app.inkloom.engine.v5.input.InputPhase.UP, 2L)))
            val published = paint.publish()
            assertEquals("Zero grain invented pigment, selected=$selected", 0, published.record.changedTileCount)
            assertEquals(0, surface.pixelAt(48, 48))
            published.record.close()
        }
    }

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

    @Test fun compiledWetShapeDynamicsReachActualNativeTipPixels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tipFile = java.io.File.createTempFile("wet-shape", ".png", context.cacheDir)
        val tip = android.graphics.Bitmap.createBitmap(16, 8, android.graphics.Bitmap.Config.ARGB_8888)
        tip.eraseColor(android.graphics.Color.TRANSPARENT)
        android.graphics.Canvas(tip).drawRect(2f, 1f, 14f, 7f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
        tipFile.outputStream().use { tip.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; tip.recycle()
        try {
            val base = BrushPresetV5(identity = PresetIdentity("wet-shape", "Wet shape", family = BrushFamily.WET_PAINT, renderBrushId = "wet-shape"),
                renderSeed = 72, tip = TipSettings(160f), deposition = DepositionSettings(.12f, 1f),
                dynamics = DynamicsSettings(PressureMappings(widthEnabled = false), TiltMappings()),
                tipTexture = TipTextureSettings(procedural = false, assetPaths = listOf(tipFile.path)),
                stamp = StampSettings(particlesPerPlot = 8),
                wetMix = WetMixSettings(dilution = 0f, charge = 1f, attack = 1f, pull = 0f))
            fun render(stamp: StampSettings): IntArray {
                lateinit var view: InkCanvasView
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    view = InkCanvasView(context).apply {
                        layout(0, 0, 160, 160); activeLayerId = "test"
                        setRasterLayerSurfaces(mapOf("test" to PixelTileSurface(160, 160)))
                        applyBrushPresetV5Configuration(base.copy(stamp = stamp), AppSettings()); prepareBrushAssetsNow()
                    }
                }
                val samples = listOf(35f to 35f, 80f to 100f, 125f to 60f).mapIndexed { i, p ->
                    RawDocumentSample(p.first, p.second, (i + 1) * 8_000_000L, (i + 1) * 8_000_000L, 1f, 0f, 0f, InputTool.STYLUS)
                }
                val bitmap = view.renderIsolatedBrushPreview(samples)
                return try { IntArray(160 * 160).also { bitmap.getPixels(it, 0, 160, 0, 0, 160, 160) } }
                finally { bitmap.recycle() }
            }
            val stamp = requireNotNull(base.stamp)
            val normal = render(stamp)
            assertTrue("Native fixture must contain actual pigment", normal.any { it ushr 24 > 0 })
            for (variant in listOf(stamp.copy(horizontalRoundnessJitter = .8f), stamp.copy(verticalRoundnessJitter = .8f),
                stamp.copy(directionRotationFactor = -.75f), stamp.copy(strokeRotationJitterDegrees = 90f),
                stamp.copy(particlesPerPlotJitter = .9f))) {
                val pixels = render(variant)
                assertFalse("Compiled wet setting failed to reach raster: $variant", normal.contentEquals(pixels))
                assertArrayEquals("Preview replay must keep deterministic shape variation", pixels, render(variant))
            }
        } finally { tipFile.delete() }
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
