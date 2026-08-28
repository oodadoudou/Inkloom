package app.inkloom

import android.graphics.Color
import app.inkloom.brushpresetv5.BrushFamily
import app.inkloom.brushpresetv5.BrushOutput
import app.inkloom.brushpresetv5.BrushPresetV5
import app.inkloom.brushpresetv5.DepositionSettings
import app.inkloom.brushpresetv5.DynamicsSettings
import app.inkloom.brushpresetv5.PresetIdentity
import app.inkloom.brushpresetv5.PressureMappings
import app.inkloom.brushpresetv5.SpacingSettings
import app.inkloom.brushpresetv5.StampSettings
import app.inkloom.brushpresetv5.StrokeSettings
import app.inkloom.brushpresetv5.TiltMappings
import app.inkloom.brushpresetv5.TipSettings
import app.inkloom.brushpresetv5.TipTextureSettings
import app.inkloom.brushpresetv5.TextureGrainSettings
import app.inkloom.brushpresetv5.WetMixSettings
import app.inkloom.settings.AppSettings
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushPresetV5Configuration

internal fun InkCanvasView.installV5TestInk(
    size: Float = 18f,
    color: Int = Color.BLACK,
    opacity: Float = 1f,
    hardness: Float = 1f,
    family: BrushFamily = BrushFamily.INK,
    output: BrushOutput = BrushOutput.PAINT,
    tipAssetPaths: List<String> = emptyList(),
    textureGrain: TextureGrainSettings? = null,
    continuousPlot: Boolean = false,
    wetMix: WetMixSettings? = null,
    smoothing: Float = 0f,
): InkCanvasView = apply {
    val drawWithFinger = fingerDrawingEnabled
    val singleFingerUndo = singleFingerDoubleTapUndo
    val twoFingerUndo = twoFingerDoubleTapUndo
    val threeFingerRedoEnabled = threeFingerTapRedo
    applyBrushPresetV5Configuration(
        BrushPresetV5(
            identity = PresetIdentity(
                id = "android-test-reference-ink",
                name = "Android test reference ink",
                family = family,
                renderBrushId = "android-test-reference-ink",
            ),
            renderSeed = 0x51A7E,
            output = output,
            tip = TipSettings(size = size, hardness = hardness),
            deposition = DepositionSettings(opacity = opacity, flow = 1f),
            spacing = if (tipAssetPaths.isNotEmpty() || continuousPlot) SpacingSettings(value = .12f) else null,
            dynamics = DynamicsSettings(
                pressure = PressureMappings(widthEnabled = false),
                tilt = TiltMappings(),
            ),
            tipTexture = tipAssetPaths.takeIf { it.isNotEmpty() }?.let {
                TipTextureSettings(procedural = false, assetPaths = it)
            },
            textureGrain = textureGrain,
            stamp = (tipAssetPaths.isNotEmpty() || continuousPlot).takeIf { it }?.let {
                StampSettings(continuousPlot = continuousPlot)
            },
            wetMix = wetMix,
            stroke = StrokeSettings(smoothing = smoothing),
        ),
        AppSettings(),
        brushColor = color,
    )
    // This fixture installs brush semantics only. Gesture policy belongs to each interaction test
    // and must not be silently overwritten by AppSettings defaults.
    fingerDrawingEnabled = drawWithFinger
    singleFingerDoubleTapUndo = singleFingerUndo
    twoFingerDoubleTapUndo = twoFingerUndo
    threeFingerTapRedo = threeFingerRedoEnabled
}
