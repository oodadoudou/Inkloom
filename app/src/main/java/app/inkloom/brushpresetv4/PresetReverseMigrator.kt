package app.inkloom.brushpresetv4

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.ImportedBrushGraphite

/**
 * Reverse migration: [BrushPresetV4] -> legacy [ImportedBrush].
 *
 * The live canvas still renders through the legacy ImportedBrush -> applyBrushConfiguration ->
 * buildStroke path. To guarantee the editor's Scratch Pad (which renders via
 * [CompiledBrushProgram.toInkStroke]) is byte-identical to a real stroke drawn on the canvas, this
 * migrator must project the V4 semantics onto the ImportedBrush fields that buildStroke reads, with
 * the same values [CompiledBrushProgram] would produce.
 *
 * It maps only the fields the V4 common contract + the reference families express; everything else
 * stays at the ImportedBrush default. The render brush id ([PresetIdentity.renderBrushId]) becomes
 * the ImportedBrush id so the procedural-tip / ribbon renderer gates still recognize it.
 */
object PresetReverseMigrator {

    fun toV3(v4: BrushPresetV4, format: BrushFormat = BrushFormat.SYSTEM): ImportedBrush {
        val identity = v4.identity
        val family = identity.family
        val hasImportedTip = v4.tipTexture?.assetPath != null
        val backend = BrushCapabilities.backend(family, hasImportedTip)
        val continuous = family == BrushFamily.INK
        return ImportedBrush(
            id = identity.renderBrushId,
            name = identity.name,
            format = format,
            compatibility = Compatibility.COMPLETE,
            sizeBytes = 0L,
            report = emptyList(),
            defaultSize = v4.tip.size,
            defaultOpacity = v4.deposition.opacity,
            flow = v4.deposition.flow,
            hardness = v4.tip.hardness,
            thickness = v4.tip.aspect,
            rotationDegrees = v4.tip.rotationDegrees,
            spacing = v4.spacing?.value ?: .12f,
            spacingMode = app.inkloom.document.CspBrushGap.FIXED,
            sizePressureEnabled = v4.dynamics.pressure.widthEnabled,
            sizePressureMinimum = v4.dynamics.pressure.widthMinimum,
            sizePressureCurve = v4.dynamics.pressure.widthCurve,
            opacityPressureEnabled = v4.dynamics.pressure.depositionEnabled,
            opacityPressureMinimum = v4.dynamics.pressure.depositionMinimum,
            opacityPressureCurve = v4.dynamics.pressure.depositionCurve,
            flowPressureEnabled = v4.dynamics.pressure.flowEnabled,
            flowPressureMinimum = v4.dynamics.pressure.flowMinimum,
            flowPressureCurve = v4.dynamics.pressure.flowCurve,
            graphite = ImportedBrushGraphite(
                tiltWidth = if (v4.dynamics.tilt.widthEnabled) v4.dynamics.tilt.widthMaximum else 0f,
                tiltWidthStart = v4.dynamics.tilt.widthStart,
                tiltGrain = if (v4.dynamics.tilt.grainEnabled) v4.dynamics.tilt.grainMaximum else 0f,
                tiltGrainStart = v4.dynamics.tilt.grainStart,
                paperGrainEnabled = v4.paperGrain != null,
                paperGrainScale = v4.paperGrain?.scale ?: 100f,
                paperGrainDensity = v4.paperGrain?.density ?: 0f,
            ),
            ribbon = continuous,
            continuousPlot = continuous,
            tipPreviewPath = v4.tipTexture?.assetPath,
            stabilization = v4.stroke?.smoothing ?: 0f,
            postCorrectionEnabled = (v4.stroke?.postCorrection ?: 0f) > .0001f,
            postCorrection = v4.stroke?.postCorrection ?: 0f,
            taperStartEnabled = v4.stroke?.taperStartEnabled ?: false,
            taperStartLength = v4.stroke?.taperStartLength ?: .2f,
            taperEndEnabled = v4.stroke?.taperEndEnabled ?: false,
            taperEndLength = v4.stroke?.taperEndLength ?: .2f,
        )
    }
}
