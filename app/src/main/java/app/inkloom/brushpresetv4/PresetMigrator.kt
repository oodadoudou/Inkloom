package app.inkloom.brushpresetv4

import app.inkloom.model.ImportedBrush

/**
 * Migrates a legacy V3 [ImportedBrush] into the versioned [BrushPresetV4] model.
 *
 * The migration is a *schema* transformation: it maps user-configurable V3 semantics onto the V4
 * capability-gated structure without inventing new behavior. Rendering identity is preserved
 * because [BrushCompiler] reconstructs the same runtime parameters the legacy path produced.
 *
 * FOUNDATION scope: the migrator covers the fields the two reference families actually use
 * (CONTINUOUS_INK reference and STAMPED_PENCIL reference). Fields meaningful only for families
 * not yet migrated are gated by family capability so they never render as inactive slots.
 */
object PresetMigrator {

    fun toV4(v3: ImportedBrush): BrushPresetV4 {
        val family = engineFamily(v3)
        val spacing = if (family == BrushEngineFamily.STAMPED_PENCIL || family == BrushEngineFamily.AIRBRUSH) {
            SpacingSettings(mode = SpacingMode.RELATIVE, value = v3.spacing)
        } else {
            // CONTINUOUS_INK reference uses a ribbon, not user-visible stamp spacing.
            null
        }
        val tiltWidth = v3.graphiteTiltWidth
        val tiltGrain = v3.graphiteTiltGrain
        val paperGrain = if (family == BrushEngineFamily.STAMPED_PENCIL) {
            PaperGrainSettings(
                scale = v3.proceduralPaperGrainScale,
                density = v3.proceduralPaperGrainDensity,
                strength = 1f,
            )
        } else null
        val tipTexture = if (family == BrushEngineFamily.STAMPED_PENCIL) {
            TipTextureSettings(procedural = true, assetPath = null, toothStrength = v3.hardness)
        } else null
        return BrushPresetV4(
            schemaVersion = 4,
            identity = PresetIdentity(
                id = v3.id,
                name = v3.name,
                version = v3.parserVersion,
                engineFamily = family,
            ),
            tip = TipSettings(
                size = v3.defaultSize,
                aspect = v3.thickness,
                rotationDegrees = v3.rotationDegrees,
                hardness = v3.hardness,
            ),
            deposition = DepositionSettings(
                opacity = v3.defaultOpacity,
                flow = v3.flow,
                buildup = 0f,
            ),
            spacing = spacing,
            dynamics = DynamicsSettings(
                pressure = PressureMappings(
                    widthEnabled = v3.sizePressureEnabled,
                    widthMinimum = v3.sizePressureMinimum,
                    widthCurve = v3.sizePressureCurve,
                    depositionEnabled = v3.opacityPressureEnabled || v3.flowPressureEnabled,
                    depositionMinimum = v3.opacityPressureMinimum,
                    depositionCurve = v3.opacityPressureCurve,
                ),
                tilt = TiltMappings(
                    widthEnabled = tiltWidth > .0001f,
                    widthStart = v3.graphiteTiltWidthStart,
                    widthMaximum = tiltWidth,
                    grainEnabled = tiltGrain > .0001f,
                    grainStart = v3.graphiteTiltGrainStart,
                    grainMaximum = tiltGrain,
                ),
                velocity = null,
                direction = null,
            ),
            paperGrain = paperGrain,
            tipTexture = tipTexture,
            stroke = StrokeSettings(
                smoothing = v3.stabilization,
                taperStartEnabled = false,
                taperStartLength = .2f,
                taperEndEnabled = false,
                taperEndLength = .2f,
                postCorrection = if (v3.postCorrectionEnabled) v3.postCorrection else 0f,
            ),
            engineOptions = EngineOptions(),
        )
    }

    private fun engineFamily(v3: ImportedBrush): BrushEngineFamily = when {
        v3.spray -> BrushEngineFamily.AIRBRUSH
        v3.id.startsWith("system-decoration-") -> BrushEngineFamily.DECORATION
        isGraphiteFamily(v3) -> BrushEngineFamily.STAMPED_PENCIL
        else -> BrushEngineFamily.CONTINUOUS_INK
    }

    private fun isGraphiteFamily(v3: ImportedBrush): Boolean =
        v3.graphiteTiltWidth > .0001f || v3.graphiteTiltGrain > .0001f ||
            v3.proceduralPaperGrainEnabled || v3.id.contains("pencil", ignoreCase = true)
}
