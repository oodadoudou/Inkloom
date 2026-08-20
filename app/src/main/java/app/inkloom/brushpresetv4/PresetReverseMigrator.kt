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
 * It maps the fields the V4 common contract expresses and merges them onto [base], preserving
 * imported SUT/ABR/Procreate semantics outside the current V4 scope. Library identity remains
 * [PresetIdentity.id]; renderer routing identity is stored independently.
 */
object PresetReverseMigrator {

    fun toV3(
        v4: BrushPresetV4,
        format: BrushFormat = BrushFormat.SYSTEM,
        base: ImportedBrush? = null,
    ): ImportedBrush {
        val identity = v4.identity
        val family = identity.family
        val continuous = family == BrushFamily.INK
        val source = base ?: ImportedBrush(
            name = identity.name,
            format = format,
            compatibility = Compatibility.COMPLETE,
            sizeBytes = 0L,
            report = emptyList(),
        )
        return source.copy(
            id = identity.id,
            name = identity.name,
            format = format,
            compatibility = source.compatibility,
            defaultSize = v4.tip.size,
            defaultOpacity = v4.deposition.opacity,
            flow = v4.deposition.flow,
            hardness = v4.tip.hardness,
            thickness = v4.tip.aspect,
            rotationDegrees = v4.tip.rotationDegrees,
            spacing = v4.spacing?.value ?: source.spacing,
            spacingMode = if (v4.spacing != null) app.inkloom.document.CspBrushGap.FIXED else source.spacingMode,
            sizePressureEnabled = v4.dynamics.pressure.widthEnabled,
            sizePressureMinimum = v4.dynamics.pressure.widthMinimum,
            sizePressureCurve = v4.dynamics.pressure.widthCurve,
            opacityPressureEnabled = v4.dynamics.pressure.depositionEnabled,
            opacityPressureMinimum = v4.dynamics.pressure.depositionMinimum,
            opacityPressureCurve = v4.dynamics.pressure.depositionCurve,
            flowPressureEnabled = v4.dynamics.pressure.flowEnabled,
            flowPressureMinimum = v4.dynamics.pressure.flowMinimum,
            flowPressureCurve = v4.dynamics.pressure.flowCurve,
            graphite = if (family == BrushFamily.PENCIL) source.graphite.copy(
                tiltWidth = if (v4.dynamics.tilt.widthEnabled) v4.dynamics.tilt.widthMaximum else 0f,
                tiltWidthStart = v4.dynamics.tilt.widthStart,
                tiltGrain = if (v4.dynamics.tilt.grainEnabled) v4.dynamics.tilt.grainMaximum else 0f,
                tiltGrainStart = v4.dynamics.tilt.grainStart,
                paperGrainEnabled = v4.paperGrain != null,
                paperGrainScale = v4.paperGrain?.scale ?: 100f,
                paperGrainDensity = v4.paperGrain?.density ?: 0f,
                paperGrainStrength = v4.paperGrain?.strength ?: 1f,
            ) else source.graphite,
            ribbon = if (base == null) continuous else source.ribbon,
            continuousPlot = if (base == null) continuous else source.continuousPlot,
            tipPreviewPath = v4.tipTexture?.assetPath ?: source.tipPreviewPath,
            stabilization = v4.stroke?.smoothing ?: 0f,
            postCorrectionEnabled = (v4.stroke?.postCorrection ?: 0f) > .0001f,
            postCorrection = v4.stroke?.postCorrection ?: 0f,
            taperStartEnabled = v4.stroke?.taperStartEnabled ?: false,
            taperStartLength = v4.stroke?.taperStartLength ?: .2f,
            taperEndEnabled = v4.stroke?.taperEndEnabled ?: false,
            taperEndLength = v4.stroke?.taperEndLength ?: .2f,
            renderBrushId = identity.renderBrushId,
            renderSeed = v4.renderSeed,
            derivedFromFactoryId = source.derivedFromFactoryId
                ?: identity.renderBrushId.takeIf { it != identity.id && it.startsWith("system-") },
            v4PresetJson = BrushPresetV4Codec.encode(v4),
        )
    }
}
