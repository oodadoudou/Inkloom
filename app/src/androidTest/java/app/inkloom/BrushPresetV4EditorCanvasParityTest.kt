package app.inkloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.brushpresetv4.BrushFamily
import app.inkloom.brushpresetv4.BrushPresetV4
import app.inkloom.brushpresetv4.CompiledBrushProgram
import app.inkloom.brushpresetv4.DepositionSettings
import app.inkloom.brushpresetv4.DynamicsSettings
import app.inkloom.brushpresetv4.EngineOptions
import app.inkloom.brushpresetv4.PaperGrainSettings
import app.inkloom.brushpresetv4.PresetIdentity
import app.inkloom.brushpresetv4.PresetReverseMigrator
import app.inkloom.brushpresetv4.PressureMappings
import app.inkloom.brushpresetv4.SpacingMode
import app.inkloom.brushpresetv4.SpacingSettings
import app.inkloom.brushpresetv4.StrokeSettings
import app.inkloom.brushpresetv4.TiltMappings
import app.inkloom.brushpresetv4.TipSettings
import app.inkloom.brushpresetv4.TipTextureSettings
import app.inkloom.document.StrokePoint
import app.inkloom.model.EditorTool
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BRUSH_EDITOR_V1 canvas parity: a V4 preset rendered through the compiled path (Scratch Pad) must
 * produce the same renderer inputs as the same preset reverse-migrated into the legacy
 * ImportedBrush -> applyBrushConfiguration path (real canvas stroke). This proves the editor never
 * diverges from the production canvas.
 */
@RunWith(AndroidJUnit4::class)
class BrushPresetV4EditorCanvasParityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun reverseMigratedPencilConfiguresCanvasIdenticallyToCompiledStroke() {
        val v4 = editorPencil()
        val compiledStroke = CompiledBrushProgram.compile(v4).toInkStroke(0xFF000000L, scratchPoints())
        val v3 = PresetReverseMigrator.toV3(v4)

        val view = InkCanvasView(context).apply { layout(0, 0, 900, 900) }
        view.applyBrushConfiguration(
            selectedTool = EditorTool.PEN,
            settings = AppSettings(),
            selectedBrush = v3,
            brushSize = 24f,
            brushOpacity = 1f,
            brushColor = 0xFF000000.toInt(),
            eraserPreset = EraserPreset.NORMAL_FREE,
        )

        // The renderer inputs the canvas will build a stroke from must match the compiled stroke
        // exactly — render identity, seed-free geometry, and graphite/paper semantics.
        assertEquals(compiledStroke.brushId, view.brushId)
        assertEquals(compiledStroke.width, v3.defaultSize, 1e-3f)
        assertEquals(compiledStroke.flow, view.brushFlow, 1e-3f)
        assertEquals(compiledStroke.hardness, view.brushHardness, 1e-3f)
        assertEquals(compiledStroke.thickness, view.brushThickness, 1e-3f)
        assertEquals(compiledStroke.spacing, view.brushSpacing, 1e-3f)
        assertEquals(compiledStroke.sizePressureEnabled, view.sizePressureEnabled)
        assertEquals(compiledStroke.sizePressureMinimum, view.sizePressureMinimum, 1e-3f)
        assertEquals(compiledStroke.opacityPressureEnabled, view.opacityPressureEnabled)
        assertEquals(compiledStroke.opacityPressureMinimum, view.opacityPressureMinimum, 1e-3f)
        assertEquals(compiledStroke.graphiteTiltWidth, view.graphiteTiltWidth, 1e-3f)
        assertEquals(compiledStroke.graphiteTiltGrain, view.graphiteTiltGrain, 1e-3f)
        assertEquals(compiledStroke.proceduralPaperGrainEnabled, view.proceduralPaperGrainEnabled)
        assertEquals(compiledStroke.proceduralPaperGrainDensity, view.proceduralPaperGrainDensity, 1e-3f)
        assertEquals(compiledStroke.proceduralPaperGrainScale, view.proceduralPaperGrainScale, 1e-3f)
    }

    @Test
    fun reverseMigratedInkConfiguresCanvasIdenticallyToCompiledStroke() {
        val v4 = editorInk()
        val compiledStroke = CompiledBrushProgram.compile(v4).toInkStroke(0xFF000000L, scratchPoints())
        val v3 = PresetReverseMigrator.toV3(v4)

        val view = InkCanvasView(context).apply { layout(0, 0, 900, 900) }
        view.applyBrushConfiguration(
            selectedTool = EditorTool.PEN,
            settings = AppSettings(),
            selectedBrush = v3,
            brushSize = 5f,
            brushOpacity = 1f,
            brushColor = 0xFF000000.toInt(),
            eraserPreset = EraserPreset.NORMAL_FREE,
        )
        assertEquals("system-pen", view.brushId)
        assertEquals(compiledStroke.brushId, view.brushId)
        assertEquals(compiledStroke.width, v3.defaultSize, 1e-3f)
        assertEquals(compiledStroke.sizePressureEnabled, view.sizePressureEnabled)
        assertEquals(compiledStroke.sizePressureMinimum, view.sizePressureMinimum, 1e-3f)
    }

    private fun scratchPoints(): List<StrokePoint> = (0..60).map { i ->
        StrokePoint(.2f + i * .008f, .5f, pressure = .6f, timeMs = (i * 2L).toLong())
    }

    private fun editorInk(): BrushPresetV4 = BrushPresetV4(
        identity = PresetIdentity("system-pen", "Reference Ink", 1, BrushFamily.INK, "system-pen"),
        renderSeed = "system-pen".hashCode(),
        tip = TipSettings(size = 5f, aspect = 1f, rotationDegrees = 0f, hardness = 1f),
        deposition = DepositionSettings(opacity = 1f, flow = 1f, buildup = 0f),
        spacing = null,
        dynamics = DynamicsSettings(
            pressure = PressureMappings(widthEnabled = true, widthMinimum = .35f, depositionEnabled = false),
            tilt = TiltMappings(),
        ),
        paperGrain = null,
        tipTexture = null,
        stroke = StrokeSettings(),
        engineOptions = EngineOptions(),
    )

    private fun editorPencil(): BrushPresetV4 = BrushPresetV4(
        identity = PresetIdentity("system-hb-pencil", "Reference Pencil", 1, BrushFamily.PENCIL, "system-hb-pencil"),
        renderSeed = "system-hb-pencil".hashCode(),
        tip = TipSettings(size = 24f, aspect = 1f, rotationDegrees = 0f, hardness = 1f),
        deposition = DepositionSettings(opacity = 1f, flow = .68f, buildup = 0f),
        spacing = SpacingSettings(mode = SpacingMode.RELATIVE, value = .26f),
        dynamics = DynamicsSettings(
            pressure = PressureMappings(
                widthEnabled = true, widthMinimum = .3f,
                depositionEnabled = true, depositionMinimum = .2f,
            ),
            tilt = TiltMappings(
                widthEnabled = true, widthStart = .2f, widthMaximum = 1f,
                grainEnabled = true, grainStart = .2f, grainMaximum = .8f,
            ),
        ),
        paperGrain = PaperGrainSettings(scale = 50f, density = .3f, strength = 1f),
        tipTexture = TipTextureSettings(procedural = true, assetPath = null, toothStrength = .64f),
        stroke = StrokeSettings(),
        engineOptions = EngineOptions(),
    )
}
