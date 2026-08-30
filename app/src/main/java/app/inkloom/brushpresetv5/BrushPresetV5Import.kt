package app.inkloom.brushpresetv5

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.ImportedBrushGraphite
import app.inkloom.model.SemanticExecutionDisposition
import app.inkloom.document.CspBrushGap
import app.inkloom.document.ProcreateRenderingMode
import app.inkloom.importer.BrushSemanticMapper

/**
 * Import-boundary conversion from decoded third-party brush semantics into Brush Preset V5.
 *
 * This object is never used by the contact/render path. A source file is parsed once, converted
 * once, and persisted with [ImportedBrush.v5PresetJson]. Existing Inkloom presets without a V5
 * payload remain unsupported instead of being silently migrated at runtime.
 */
object BrushPresetV5Import {
    fun convertOrReject(source: ImportedBrush): ImportedBrush {
        val audited = BrushSemanticMapper.apply(source)
        return runCatching {
            require(audited.compatibility != Compatibility.FAILED) {
                audited.report.firstOrNull { it.contains("failed", ignoreCase = true) }
                    ?: "SOURCE_DECODE_OR_ASSET_VALIDATION_FAILED"
            }
            requireFullyExecutableSemantics(audited)
            val converted = convertDecodedSource(audited)
            when (val capability = BrushExecutionCapabilityResolver.resolve(
                CompiledBrushProgramV5.compile(requirePreset(converted)),
            )) {
                BrushExecutionCapabilityResolution.Supported -> converted
                is BrushExecutionCapabilityResolution.Unsupported -> error(
                    capability.reasons.joinToString(",") { it.name },
                )
            }
        }.getOrElse { failure ->
            audited.copy(
                compatibility = Compatibility.FAILED,
                report = (audited.report + BrushSemanticMapper.compatibilityReport(audited) +
                    "UNSUPPORTED_WITH_EXPLICIT_REASON:${failure.message ?: "Brush Preset Format V5 conversion failed"}")
                    .distinct(),
                v5PresetJson = null,
            )
        }
    }

    /**
     * External formats are admitted only when every decoded semantic reaches a production
     * consumer. Keeping a source field for a future renderer is useful for diagnostics, but it
     * must never turn a behaviorally partial brush into a selectable library preset.
     */
    private fun requireFullyExecutableSemantics(source: ImportedBrush) {
        if (source.format in setOf(BrushFormat.SYSTEM, BrushFormat.INKLOOM)) return
        val gaps = source.semanticMappings
            .filter { it.executionDisposition != SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED }
            .map { it.sourceSemantic }
            .distinct()
            .sorted()
        require(gaps.isEmpty()) {
            "DECODED_BUT_NOT_EXECUTED:${gaps.joinToString(",")}"
        }
    }

    fun convertDecodedSource(source: ImportedBrush): ImportedBrush {
        val audited = BrushSemanticMapper.apply(source)
        require(audited.format != BrushFormat.INKLOOM || audited.v5PresetJson != null) {
            "This brush was created by an unsupported legacy Inkloom engine."
        }
        require(audited.format != BrushFormat.UNKNOWN) {
            "The brush container was recognized, but no executable source semantics were decoded."
        }
        audited.v5PresetJson?.let { encoded ->
            require(BrushPresetV5Codec.decodeOrNull(encoded) != null) { "Invalid Brush Preset Format V5 payload" }
            return audited
        }
        val auditedSource = audited.copy(
            report = (audited.report + BrushSemanticMapper.compatibilityReport(audited)).distinct(),
        )
        return writePreset(auditedSource, fromDecodedSource(audited), audited.format)
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
            output = if (source.isEraser) BrushOutput.ERASE else BrushOutput.PAINT,
            tip = TipSettings(source.defaultSize, source.thickness, source.rotationDegrees, source.hardness),
            deposition = DepositionSettings(source.defaultOpacity, source.flow),
            spacing = if (family in setOf(BrushFamily.PENCIL, BrushFamily.AIRBRUSH, BrushFamily.DECORATION) ||
                source.tipPreviewPath != null || source.tipPreviewPaths.isNotEmpty() || source.continuousPlot
            ) {
                SpacingSettings(SpacingMode.RELATIVE, CspBrushGap.ratio(source.spacingMode, source.spacing))
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
                    source.sizeSecondaryDynamics.tiltEnabled || source.graphiteTiltWidth > .0001f,
                    if (source.sizeSecondaryDynamics.tiltEnabled && source.format == BrushFormat.PROCREATE) {
                        // Procreate's Tilt Size is an influence around the authored base footprint,
                        // not permission for an upright stylus to collapse the entire dab to zero.
                        // Keep a bounded upright footprint while preserving the imported curve.
                        source.sizeSecondaryDynamics.tiltMinimum.coerceAtLeast(.2f)
                    } else if (source.sizeSecondaryDynamics.tiltEnabled) source.sizeSecondaryDynamics.tiltMinimum else 1f,
                    if (source.sizeSecondaryDynamics.tiltEnabled) source.sizeSecondaryDynamics.tiltStart else source.graphiteTiltWidthStart,
                    source.sizeSecondaryDynamics.tiltCurve,
                    source.graphiteTiltWidth,
                    source.graphiteTiltGrain > .0001f,
                    source.graphiteTiltGrainStart,
                    source.graphiteTiltGrain,
                ),
            ),
            paperGrain = source.takeIf { family == BrushFamily.PENCIL && it.proceduralPaperGrainEnabled }?.let {
                PaperGrainSettings(it.proceduralPaperGrainScale, it.proceduralPaperGrainDensity, it.proceduralPaperGrainStrength)
            },
            textureGrain = source.grainPreviewPath?.takeIf {
                family != BrushFamily.WET_PAINT && source.grainDensity > .0001f
            }?.let { path ->
                TextureGrainSettings(
                    assetPath = path,
                    density = source.grainDensity.coerceIn(0f, 1f),
                    scale = source.grainScale.coerceIn(1f, 4000f),
                    movement = source.grainMovement.coerceIn(0f, 1f),
                    zoom = source.grainZoom.coerceIn(0f, 1f),
                    rotationDegrees = source.grainRotationDegrees,
                    brightness = source.grainBrightness,
                    contrast = source.grainContrast,
                    reversed = source.grainReversed,
                    emphasized = source.grainEmphasized,
                    offsetJitter = source.grainOffsetJitter,
                )
            },
            tipTexture = when {
                source.tipPreviewPaths.isNotEmpty() || source.tipPreviewPath != null -> TipTextureSettings(
                    procedural = false,
                    assetPath = source.tipPreviewPath,
                    assetPaths = source.tipPreviewPaths.ifEmpty { listOfNotNull(source.tipPreviewPath) },
                    toothStrength = source.hardness,
                    alphaThreshold = source.alphaThreshold.takeIf { source.alphaThresholdEnabled },
                )
                family == BrushFamily.PENCIL -> TipTextureSettings(
                    procedural = true,
                    toothStrength = source.hardness,
                )
                else -> null
            },
            stroke = StrokeSettings(
                smoothing = maxOf(
                    source.stabilization,
                    source.streamLine,
                    source.procreateStabilization,
                    source.motionFiltering,
                ).coerceIn(0f, 1f),
                // Imported geometric taper is retained in ImportedBrush and reported source-only.
                // It must not enter the executable program until an append-only realtime taper
                // implementation exists; otherwise one optional semantic rejects the whole brush.
                taperStartEnabled = false,
                taperStartLength = source.taperStartLength,
                taperEndEnabled = false,
                taperEndLength = source.taperEndLength,
                // V5 never replays an entire stroke after pen-up. Post-correction is intentionally
                // not disguised as realtime smoothing because the two controls have different
                // latency and geometry semantics.
                postCorrection = 0f,
            ),
            stamp = source.takeIf {
                family in setOf(BrushFamily.PENCIL, BrushFamily.AIRBRUSH, BrushFamily.DECORATION) ||
                    it.tipPreviewPath != null || it.tipPreviewPaths.isNotEmpty() || it.continuousPlot || it.spray
            }?.let {
                StampSettings(
                    followStrokeDirection = it.followStrokeDirection,
                    followStylusOrientation = it.followStylusOrientation,
                    randomRotationDegrees = kotlin.math.abs(it.randomRotationDegrees),
                    particlesPerPlot = it.stampCount.coerceIn(1, 32),
                    continuousPlot = it.continuousPlot,
                    transferMode = if (source.format == BrushFormat.PROCREATE) {
                        source.procreateRenderingMode.toV5TransferMode()
                    } else DabTransferMode.UNIFORM_GLAZE,
                    lateralPathJitter = it.strokePathJitter.coerceIn(0f, 4f),
                    longitudinalPathJitter = it.strokePathLongitudinalJitter.coerceIn(0f, 4f),
                    horizontalRoundnessJitter = it.shapeRoundnessHorizontalJitter.coerceIn(0f, 1f),
                    verticalRoundnessJitter = it.shapeRoundnessVerticalJitter.coerceIn(0f, 1f),
                    directionRotationFactor = it.shapeTouchRotation.coerceIn(-1f, 1f),
                    strokeRotationJitterDegrees = kotlin.math.abs(it.strokeRotationJitterDegrees).coerceIn(0f, 180f),
                    particlesPerPlotJitter = it.stampCountJitter.coerceIn(0f, 1f),
                    spray = it.takeIf(ImportedBrush::spray)?.let { spray ->
                        SpraySettings(
                            particleSize = spray.sprayParticleSize.coerceAtLeast(.01f),
                            particleSizeUnit = spray.sprayParticleSizeUnit,
                            particleSizeTracksBrush = spray.sprayParticleSizeSyncBrushSize,
                            density = spray.sprayDensity.coerceAtLeast(.01f),
                            bias = spray.sprayBias.coerceIn(-100f, 100f),
                            spread = spray.spraySpread.coerceAtLeast(0f),
                            particleRotationDegrees = spray.sprayParticleRotationDegrees,
                            followStrokeDirection = spray.sprayParticleFollowStrokeDirection,
                            followStylusOrientation = spray.sprayParticleFollowStylusOrientation,
                            randomRotationDegrees = kotlin.math.abs(spray.sprayParticleRandomRotationDegrees),
                        )
                    },
                )
            },
            wetMix = source.takeIf { family == BrushFamily.WET_PAINT }?.let {
                val authored = it.procreateWetMix
                if (!authored.isDisabled()) {
                    // Some real Procreate paint presets encode only wet accumulation/pull while
                    // their proprietary rendering mode supplies color transfer. V5 does not
                    // execute that rendering mode, so a zero required coefficient would create a
                    // misleading supported brush that can never deposit a pixel. Preserve every
                    // authored non-zero value and independently supply each coefficient required
                    // by V5 deposition when the source leaves that coefficient at zero.
                    WetMixSettings(
                        authored.dilution,
                        authored.charge.takeIf { value -> value > .0001f } ?: .15f,
                        authored.attack.takeIf { value -> value > .0001f } ?: .7f,
                        authored.pull,
                    )
                } else WetMixSettings(
                    dilution = it.colorMix,
                    charge = it.paintMix.coerceAtLeast(.15f),
                    attack = .7f,
                    pull = it.colorMix,
                )
            },
            dual = source.takeIf { family != BrushFamily.WET_PAINT && it.dualBrush }?.let {
                require(it.dualTipPreviewPaths.isNotEmpty()) {
                    "Dual brush is enabled but its secondary tip asset is missing"
                }
                require(!it.dualApplyRgb) {
                    "Dual brush RGB compositing is not supported by Paint Engine V5"
                }
                DualBrushSettings(
                    tipAssetPaths = it.dualTipPreviewPaths,
                    sizeScale = it.dualSizeScale.coerceIn(.01f, 8f),
                    spacing = SpacingSettings(
                        SpacingMode.RELATIVE,
                        CspBrushGap.ratio(it.dualSpacingMode, it.dualSpacing),
                    ),
                    flow = it.dualFlow.coerceIn(0f, 1f),
                    hardness = it.dualHardness.coerceIn(0f, 1f),
                    aspect = it.dualThickness.coerceIn(.01f, 1f),
                    rotationDegrees = it.dualRotationDegrees,
                    followStrokeDirection = it.dualFollowStrokeDirection,
                    followStylusOrientation = it.dualFollowStylusOrientation,
                    randomRotationDegrees = kotlin.math.abs(it.dualRandomRotationDegrees),
                    compositeMode = dualCompositeMode(it.dualCompositeMode),
                    textureGrain = it.dualGrainPreviewPath?.takeIf { _ -> it.dualGrainDensity > .0001f }?.let { path ->
                        TextureGrainSettings(
                            assetPath = path,
                            density = it.dualGrainDensity.coerceIn(0f, 1f),
                            scale = it.dualGrainScale.coerceIn(1f, 4000f),
                            movement = it.dualGrainMovement.coerceIn(0f, 1f),
                            zoom = it.dualGrainZoom.coerceIn(0f, 1f),
                            rotationDegrees = it.dualGrainRotationDegrees,
                            brightness = it.dualGrainBrightness,
                            contrast = it.dualGrainContrast,
                            reversed = it.dualGrainReversed,
                            emphasized = it.dualGrainEmphasized,
                            offsetJitter = it.dualGrainOffsetJitter,
                        )
                    },
                    spray = it.takeIf(ImportedBrush::dualSpray)?.let { dualSpray ->
                        SpraySettings(
                            particleSize = dualSpray.dualSprayParticleSize.coerceAtLeast(.01f),
                            particleSizeUnit = dualSpray.dualSprayParticleSizeUnit,
                            particleSizeTracksBrush = dualSpray.dualSprayParticleSizeSyncBrushSize,
                            density = dualSpray.dualSprayDensity.coerceAtLeast(.01f),
                            bias = dualSpray.dualSprayBias.coerceIn(-100f, 100f),
                            spread = dualSpray.dualSpraySpread.coerceAtLeast(0f),
                            particleRotationDegrees = dualSpray.dualSprayParticleRotationDegrees,
                            followStrokeDirection = dualSpray.dualSprayParticleFollowStrokeDirection,
                            followStylusOrientation = dualSpray.dualSprayParticleFollowStylusOrientation,
                            randomRotationDegrees = kotlin.math.abs(dualSpray.dualSprayParticleRandomRotationDegrees),
                        )
                    },
                )
            },
        )
    }

    private fun family(source: ImportedBrush): BrushFamily = when {
        source.watercolor || source.paintMix > .0001f -> BrushFamily.WET_PAINT
        source.spray || source.id.contains("airbrush", true) -> BrushFamily.AIRBRUSH
        source.id.startsWith("system-decoration-") || source.groupName.contains("decoration", true) -> BrushFamily.DECORATION
        source.proceduralPaperGrainEnabled || source.graphiteTiltWidth > .0001f || source.id.contains("pencil", true) -> BrushFamily.PENCIL
        else -> BrushFamily.INK
    }

    private fun ProcreateRenderingMode.toV5TransferMode(): DabTransferMode = when (this) {
        ProcreateRenderingMode.LIGHT_GLAZE -> DabTransferMode.LIGHT_GLAZE
        ProcreateRenderingMode.UNIFORM_GLAZE -> DabTransferMode.UNIFORM_GLAZE
        ProcreateRenderingMode.INTENSE_GLAZE -> DabTransferMode.INTENSE_GLAZE
        ProcreateRenderingMode.HEAVY_GLAZE -> DabTransferMode.HEAVY_GLAZE
        ProcreateRenderingMode.UNIFORM_BLENDING -> DabTransferMode.UNIFORM_BLENDING
        ProcreateRenderingMode.INTENSE_BLENDING -> DabTransferMode.INTENSE_BLENDING
    }

    private fun dualCompositeMode(code: Int): DualCompositeMode = when (code) {
        0, 1 -> DualCompositeMode.NORMAL
        2 -> DualCompositeMode.MULTIPLY
        3 -> DualCompositeMode.ADD_GLOW
        4 -> DualCompositeMode.SUBTRACT
        5 -> DualCompositeMode.DARKEN
        6 -> DualCompositeMode.LIGHTEN
        7 -> DualCompositeMode.SCREEN
        8 -> DualCompositeMode.OVERLAY
        9 -> DualCompositeMode.COLOR_DODGE
        10 -> DualCompositeMode.COLOR_BURN
        11 -> DualCompositeMode.LINEAR_BURN
        12 -> DualCompositeMode.HARD_MIX
        13 -> DualCompositeMode.HEIGHT_LINEAR
        else -> error("Unsupported dual brush composite mode $code")
    }
}
