package app.inkloom.brushpresetv4

import app.inkloom.model.ImportedBrush

/**
 * Migrates a legacy V3 [ImportedBrush] into the versioned [BrushPresetV4] model.
 *
 * The migration is a *schema* transformation: it maps user-configurable V3 semantics onto the V4
 * capability-gated structure without inventing new behavior. Rendering identity is preserved
 * because [BrushCompiler] reconstructs the same runtime parameters the legacy path produced.
 *
 * Render identity is kept exactly: V3 treated the preset id as the render brush id and derived
 * the stochastic seed from it (`InkStroke.renderSeed = id.hashCode()`); the migration pins that
 * as [PresetIdentity.renderBrushId] and [BrushPresetV4.renderSeed] so a migrated brush rasterizes
 * identically to the legacy path.
 *
 * FOUNDATION scope: the migrator covers the fields the two reference families actually use
 * (INK reference and PENCIL reference). Fields meaningful only for families not yet migrated are
 * gated by family capability so they never render as inactive slots.
 */
object PresetMigrator {

    fun toV4(v3: ImportedBrush): BrushPresetV4 {
        val family = brushFamily(v3)
        val spacing = if (family == BrushFamily.PENCIL || family == BrushFamily.AIRBRUSH) {
            SpacingSettings(mode = SpacingMode.RELATIVE, value = v3.spacing)
        } else {
            // INK reference uses a ribbon, not user-visible stamp spacing.
            null
        }
        val tiltWidth = v3.graphiteTiltWidth
        val tiltGrain = v3.graphiteTiltGrain
        val paperGrain = if (family == BrushFamily.PENCIL) {
            PaperGrainSettings(
                scale = v3.proceduralPaperGrainScale,
                density = v3.proceduralPaperGrainDensity,
                strength = 1f,
            )
        } else null
        val tipTexture = if (family == BrushFamily.PENCIL) {
            TipTextureSettings(procedural = true, assetPath = null, toothStrength = v3.hardness)
        } else null
        return BrushPresetV4(
            schemaVersion = 4,
            identity = PresetIdentity(
                id = v3.id,
                name = v3.name,
                version = v3.parserVersion,
                family = family,
                renderBrushId = v3.id,
            ),
            renderSeed = v3.id.hashCode(),
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

    private fun brushFamily(v3: ImportedBrush): BrushFamily = when {
        v3.spray -> BrushFamily.AIRBRUSH
        v3.id.startsWith("system-decoration-") -> BrushFamily.DECORATION
        isGraphiteFamily(v3) -> BrushFamily.PENCIL
        else -> BrushFamily.INK
    }

    private fun isGraphiteFamily(v3: ImportedBrush): Boolean =
        v3.graphiteTiltWidth > .0001f || v3.graphiteTiltGrain > .0001f ||
            v3.proceduralPaperGrainEnabled || v3.id.contains("pencil", ignoreCase = true)
}
