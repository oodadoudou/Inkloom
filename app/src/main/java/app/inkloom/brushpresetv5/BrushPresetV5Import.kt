package app.inkloom.brushpresetv5

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.ImportedBrushGraphite

/**
 * Import-boundary conversion from decoded third-party brush semantics into Brush Preset V5.
 *
 * This object is never used by the contact/render path. A source file is parsed once, converted
 * once, and persisted with [ImportedBrush.v5PresetJson]. Existing Inkloom presets without a V5
 * payload remain unsupported instead of being silently migrated at runtime.
 */
object BrushPresetV5Import {
    fun convertOrReject(source: ImportedBrush): ImportedBrush = runCatching {
        convertDecodedSource(source)
    }.getOrElse { failure ->
        source.copy(
            compatibility = Compatibility.FAILED,
            report = (source.report + (failure.message ?: "Brush Preset Format V5 conversion failed")).distinct(),
            v5PresetJson = null,
        )
    }

    fun convertDecodedSource(source: ImportedBrush): ImportedBrush {
        require(source.format != BrushFormat.INKLOOM || source.v5PresetJson != null) {
            "This brush was created by an unsupported legacy Inkloom engine."
        }
        source.v5PresetJson?.let { encoded ->
            require(BrushPresetV5Codec.decodeOrNull(encoded) != null) { "Invalid Brush Preset Format V5 payload" }
            return source
        }
        return writePreset(source, fromDecodedSource(source), source.format)
    }

    /** Creates a first-party V5 preset at catalog construction, not through legacy migration. */
    fun createFactoryRecord(source: ImportedBrush): ImportedBrush {
        require(source.format == BrushFormat.SYSTEM)
        return writePreset(source, fromDecodedSource(source), BrushFormat.SYSTEM)
    }

    /** Creates a native user preset authored inside Inkloom. */
    fun createUserRecord(source: ImportedBrush): ImportedBrush {
        require(source.format == BrushFormat.INKLOOM)
        return writePreset(source, fromDecodedSource(source), BrushFormat.INKLOOM)
    }

    fun requirePreset(record: ImportedBrush): BrushPresetV5 =
        BrushPresetV5Codec.decodeOrNull(record.v5PresetJson)
            ?: error("This brush was created by an unsupported legacy Inkloom engine.")

    /** Persists the V5 truth and updates library display fields; render code reads only the V5 payload. */
    fun writePreset(base: ImportedBrush, preset: BrushPresetV5, format: BrushFormat = base.format): ImportedBrush {
        require(preset.schemaVersion == 5)
        val family = preset.identity.family
        return base.copy(
            id = preset.identity.id,
            name = preset.identity.name,
            format = format,
            compatibility = if (base.compatibility == Compatibility.FAILED) Compatibility.FAILED else base.compatibility,
            defaultSize = preset.tip.size,
            defaultOpacity = preset.deposition.opacity,
            flow = preset.deposition.flow,
            hardness = preset.tip.hardness,
            thickness = preset.tip.aspect,
            rotationDegrees = preset.tip.rotationDegrees,
            spacing = preset.spacing?.value ?: base.spacing,
            sizePressureEnabled = preset.dynamics.pressure.widthEnabled,
            sizePressureMinimum = preset.dynamics.pressure.widthMinimum,
            sizePressureCurve = preset.dynamics.pressure.widthCurve,
            opacityPressureEnabled = preset.dynamics.pressure.depositionEnabled,
            opacityPressureMinimum = preset.dynamics.pressure.depositionMinimum,
            opacityPressureCurve = preset.dynamics.pressure.depositionCurve,
            flowPressureEnabled = preset.dynamics.pressure.flowEnabled,
            flowPressureMinimum = preset.dynamics.pressure.flowMinimum,
            flowPressureCurve = preset.dynamics.pressure.flowCurve,
            graphite = if (family == BrushFamily.PENCIL) ImportedBrushGraphite(
                tiltWidth = preset.dynamics.tilt.widthMaximum.takeIf { preset.dynamics.tilt.widthEnabled } ?: 0f,
                tiltWidthStart = preset.dynamics.tilt.widthStart,
                tiltGrain = preset.dynamics.tilt.grainMaximum.takeIf { preset.dynamics.tilt.grainEnabled } ?: 0f,
                tiltGrainStart = preset.dynamics.tilt.grainStart,
                paperGrainEnabled = preset.paperGrain != null,
                paperGrainScale = preset.paperGrain?.scale ?: 100f,
                paperGrainDensity = preset.paperGrain?.density ?: .5f,
                paperGrainStrength = preset.paperGrain?.strength ?: 1f,
            ) else base.graphite,
            tipPreviewPath = preset.tipTexture?.assetPath ?: base.tipPreviewPath,
            renderBrushId = preset.identity.renderBrushId,
            renderSeed = preset.renderSeed,
            v5PresetJson = BrushPresetV5Codec.encode(preset),
        )
    }

    private fun fromDecodedSource(source: ImportedBrush): BrushPresetV5 {
        val family = family(source)
        return BrushPresetV5(
            identity = PresetIdentity(
                id = source.id,
                name = source.name,
                version = source.parserVersion.coerceAtLeast(1),
                family = family,
                renderBrushId = source.renderBrushId ?: source.id,
            ),
            renderSeed = source.renderSeed ?: (source.renderBrushId ?: source.id).hashCode(),
            tip = TipSettings(source.defaultSize, source.thickness, source.rotationDegrees, source.hardness),
            deposition = DepositionSettings(source.defaultOpacity, source.flow),
            spacing = if (family in setOf(BrushFamily.PENCIL, BrushFamily.AIRBRUSH, BrushFamily.DECORATION)) {
                SpacingSettings(SpacingMode.RELATIVE, source.spacing)
            } else null,
            dynamics = DynamicsSettings(
                pressure = PressureMappings(
                    source.sizePressureEnabled,
                    source.sizePressureMinimum,
                    source.sizePressureCurve,
                    source.opacityPressureEnabled,
                    source.opacityPressureMinimum,
                    source.opacityPressureCurve,
                    source.flowPressureEnabled,
                    source.flowPressureMinimum,
                    source.flowPressureCurve,
                ),
                tilt = TiltMappings(
                    source.graphiteTiltWidth > .0001f,
                    source.graphiteTiltWidthStart,
                    source.graphiteTiltWidth,
                    source.graphiteTiltGrain > .0001f,
                    source.graphiteTiltGrainStart,
                    source.graphiteTiltGrain,
                ),
            ),
            paperGrain = source.takeIf { family == BrushFamily.PENCIL && it.proceduralPaperGrainEnabled }?.let {
                PaperGrainSettings(it.proceduralPaperGrainScale, it.proceduralPaperGrainDensity, it.proceduralPaperGrainStrength)
            },
            tipTexture = when {
                source.tipPreviewPath != null -> TipTextureSettings(false, source.tipPreviewPath, source.hardness)
                family == BrushFamily.PENCIL -> TipTextureSettings(true, null, source.hardness)
                else -> null
            },
            stroke = StrokeSettings(
                smoothing = source.stabilization,
                taperStartEnabled = source.taperStartEnabled,
                taperStartLength = source.taperStartLength,
                taperEndEnabled = source.taperEndEnabled,
                taperEndLength = source.taperEndLength,
                postCorrection = source.postCorrection.takeIf { source.postCorrectionEnabled } ?: 0f,
            ),
        )
    }

    private fun family(source: ImportedBrush): BrushFamily = when {
        source.watercolor || source.paintMix > .0001f -> BrushFamily.WET_PAINT
        source.spray || source.id.contains("airbrush", true) -> BrushFamily.AIRBRUSH
        source.id.startsWith("system-decoration-") || source.groupName.contains("decoration", true) -> BrushFamily.DECORATION
        source.proceduralPaperGrainEnabled || source.graphiteTiltWidth > .0001f || source.id.contains("pencil", true) -> BrushFamily.PENCIL
        else -> BrushFamily.INK
    }
}
