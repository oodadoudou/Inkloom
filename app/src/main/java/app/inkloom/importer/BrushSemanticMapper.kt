package app.inkloom.importer

import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.ProcreateBrushBlend
import app.inkloom.document.ProcreateRenderingMode
import app.inkloom.model.BrushFormat
import app.inkloom.model.BrushSemanticMapping
import app.inkloom.model.ImportedBrush
import app.inkloom.model.BrushToolRole
import app.inkloom.model.SemanticExecutionDisposition
import app.inkloom.model.SemanticFidelity

/**
 * Audits decoded source semantics against the immutable V5 production program.
 *
 * A semantic is reported as executed only when it is encoded in BrushPresetV5 and consumed by
 * the V5 renderer. Parsed values kept in ImportedBrush for future conversion are explicitly
 * source-only; they are never presented as approximate runtime support.
 */
object BrushSemanticMapper {
    fun apply(brush: ImportedBrush): ImportedBrush = brush.copy(semanticMappings = describe(brush))

    fun describe(brush: ImportedBrush): List<BrushSemanticMapping> = buildList {
        val prefix = brush.format.name
        // Keep the audit aligned with the import boundary: SUT amounts do not enable mixing.
        val wetPrimary = brush.watercolor || (brush.format != BrushFormat.SUT && brush.paintMix > .0001f)
        fun executed(
            source: String,
            target: String,
            fidelity: SemanticFidelity = SemanticFidelity.EXACT,
            note: String = "",
        ) = add(BrushSemanticMapping(source, target, fidelity, note, SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED))
        fun preserved(source: String, note: String) = add(
            BrushSemanticMapping(
                source,
                "ImportedBrush.retainedSourceSemantic",
                SemanticFidelity.UNSUPPORTED,
                note,
                SemanticExecutionDisposition.PRESERVED_SOURCE_ONLY,
            ),
        )
        fun rejected(source: String, note: String) = add(
            BrushSemanticMapping(
                source,
                "BrushPresetV5Import.explicitRejection",
                SemanticFidelity.UNSUPPORTED,
                note,
                SemanticExecutionDisposition.UNSUPPORTED_WITH_EXPLICIT_REASON,
            ),
        )
        fun importedResourceFidelity(role: String): SemanticFidelity =
            if (brush.report.any { it.startsWith("APPROXIMATED_RESOURCE:$role:") }) {
                SemanticFidelity.APPROXIMATE
            } else {
                SemanticFidelity.EXACT
            }

        executed("$prefix.tipGeometry", "BrushPresetV5.tip")
        executed("$prefix.opacity", "BrushPresetV5.deposition.opacity")
        executed("$prefix.flow", "BrushPresetV5.deposition.flow")
        executed("$prefix.spacing", "BrushPresetV5.spacing")
        executed("$prefix.output", "BrushPresetV5.output")

        if (brush.tipPreviewPaths.isNotEmpty() || brush.tipPreviewPath != null) {
            executed(
                "$prefix.shape",
                "StampPaintOp.tipTexture",
                importedResourceFidelity("shape"),
                brush.report.firstOrNull { it.startsWith("APPROXIMATED_RESOURCE:shape:") }.orEmpty(),
            )
        } else if (brush.format == BrushFormat.PROCREATE) {
            rejected(
                "PROCREATE.shape.asset",
                "The brush references ${brush.dualGrain.sourceTipReference ?: "a missing shape"}, but the source container does not embed Shape.png.",
            )
        }
        if (brush.format == BrushFormat.PROCREATE && brush.grainPreviewPath == null &&
            (brush.dualGrain.sourceGrainReference != null || brush.grainDensity > .0001f)
        ) rejected(
            "PROCREATE.grain.asset",
            "The brush references ${brush.dualGrain.sourceGrainReference ?: "a missing grain"}, but the source container does not embed Grain.png.",
        )
        if (brush.grainPreviewPath != null) {
            executed(
                "$prefix.grain.basic",
                if (wetPrimary) "WetPaintOp.textureGrain" else "StampPaintOp.textureGrain",
                importedResourceFidelity("grain"),
                brush.report.firstOrNull { it.startsWith("APPROXIMATED_RESOURCE:grain:") }.orEmpty(),
            )
        }
        if (brush.dualGrainPreviewPath != null) {
            if (wetPrimary) preserved(
                "$prefix.dualGrain.basic",
                "The wet secondary grain is retained but is not part of the V5 primary wet PaintOp.",
            ) else executed("$prefix.dualGrain.basic", "StampPaintOp.dual.textureGrain")
        }
        if (brush.continuousPlot) executed("$prefix.continuousPlot", "StampPaintOp.continuousPlot")
        if (brush.spray) executed("$prefix.spray.basic", "StampPaintOp.spray")
        if (brush.dualBrush && !brush.dualApplyRgb) {
            if (wetPrimary) preserved(
                "$prefix.dualBrush.basic",
                "The secondary wet stream is retained; V5 executes the primary wet tip and pigment program.",
            ) else executed("$prefix.dualBrush.basic", "StampPaintOp.dual")
        }
        if (brush.toolBinding.role == BrushToolRole.ERASE) executed("$prefix.eraser", "BrushPresetV5.output.ERASE")

        if (brush.sizePressureEnabled) executed("$prefix.pressure.size", "CompiledBrushProgramV5.pressure.width")
        if (brush.opacityPressureEnabled) executed("$prefix.pressure.opacity", "CompiledBrushProgramV5.pressure.deposition")
        if (brush.flowPressureEnabled) executed("$prefix.pressure.flow", "CompiledBrushProgramV5.pressure.flow")
        if (brush.graphiteTiltWidth > 0f) executed("$prefix.tilt.width", "CompiledBrushProgramV5.tilt.width")
        if (brush.graphiteTiltGrain > 0f) executed("$prefix.tilt.grain", "CompiledBrushProgramV5.tilt.grain")
        if (brush.proceduralPaperGrainEnabled) executed("$prefix.paperGrain", "CompiledBrushProgramV5.grainSampler")
        if (brush.taperStartEnabled || brush.taperEndEnabled) {
            preserved(
                "$prefix.taper.basic",
                "Start/end taper is retained but not executed because the current append-only renderer never rewrites the completed stroke.",
            )
        }

        if (!brush.procreateWetMix.isDisabled()) {
            executed(
                "PROCREATE.wetMix.basic",
                "WetPaintOp.dilutionChargeAttackPull",
                SemanticFidelity.APPROXIMATE,
                "V5 executes dilution, charge, attack and pull with Inkloom's deterministic pigment transport.",
            )
            if (brush.procreateWetMix.grade != 0f || brush.procreateWetMix.blur != 0f ||
                brush.procreateWetMix.blurJitter != 0f || brush.procreateWetMix.wetnessJitter != 0f ||
                brush.procreateWetMix.legacyMixSoftening != 0f
            ) preserved("PROCREATE.wetMix.advanced", "Grade, blur, jitter and legacy softening are retained but are not in the V5 wet program.")
        } else if (brush.format == BrushFormat.SUT && !brush.watercolor &&
            (brush.colorMix > 0f || brush.paintMix > 0f)
        ) {
            preserved(
                "SUT.wetMix.basic",
                "Color mixing is disabled; retained amount and density of paint do not enable wet transport.",
            )
        } else if (brush.watercolor || brush.colorMix > 0f || brush.paintMix > 0f) {
            executed(
                "$prefix.wetMix.basic",
                "WetPaintOp.deterministicPigmentTransport",
                SemanticFidelity.APPROXIMATE,
                "V5 maps source color/paint mix to its native wet-paint program.",
            )
        }

        if (brush.strokeFalloff != 0f) preserved("$prefix.strokeFalloff", "Distance-based stroke falloff is parsed but not executed by V5.")
        if (brush.strokePathJitter != 0f || brush.strokePathLongitudinalJitter != 0f) {
            if (wetPrimary) preserved("$prefix.pathJitter", "Wet Paint path jitter is retained but not executed by the wet depositor.")
            else executed("$prefix.pathJitter", "StampSettings.lateralPathJitter/longitudinalPathJitter")
        }
        if (brush.shapeRoundnessHorizontalJitter != 0f || brush.shapeRoundnessVerticalJitter != 0f || brush.shapeTouchRotation != 0f) {
            executed("$prefix.shapeDynamics", if (wetPrimary) "WetPaintOp.roundnessJitter/directionRotationFactor"
                else "StampSettings.roundnessJitter/directionRotationFactor", SemanticFidelity.APPROXIMATE,
                "Independent shape compression and signed direction rotation; source random sequence is not reproduced.")
        }
        if (brush.shapeOrientToScreen) preserved("$prefix.orientToScreen", "Screen-locked tip orientation is retained but not yet projected through the viewport rotation.")
        if (brush.strokeRotationJitterDegrees != 0f || brush.stampCountJitter != 0f) {
            executed("$prefix.stampJitter", if (wetPrimary) "WetPaintOp.strokeRotationJitter/particlesPerPlotJitter"
                else "StampSettings.strokeRotationJitter/particlesPerPlotJitter", SemanticFidelity.APPROXIMATE,
                "Count remains bounded by authored maximum; rotation is stable within each transaction. Source random sequence is not reproduced.")
        }
        if (hasColorDynamics(brush)) preserved("$prefix.colorDynamics", "Stamp, stroke, pressure, tilt and secondary-color dynamics are retained but not executed by V5.")
        if (hasAdvancedMainDynamics(brush)) preserved("$prefix.advancedDynamics", "Spacing, thickness, velocity, random and non-graphite tilt dynamics are retained but not executed by V5.")
        if (brush.streamLinePressure != 0f || brush.motionFilteringExpression != 0f || brush.tiltGradation != 0f ||
            brush.tiltBleed != 0f || brush.tiltSizeCompression || brush.stabilizationBySpeed
        ) preserved("$prefix.inputDynamics", "Pressure smoothing, expression, tilt gradation/bleed and speed-dependent stabilization are retained but not executed by V5.")
        if (brush.postCorrectionEnabled && brush.postCorrection > 0f) preserved("$prefix.postCorrection", "V5 never rewrites a completed stroke; post-correction remains source-only.")
        if (brush.blur > 0f || brush.blurPressureEnabled || secondary(brush.blurSecondaryDynamics)) {
            preserved("$prefix.blur", "Brush-tip blur and its dynamics are retained but not executed by V5.")
        }
        if (brush.alphaThresholdEnabled) executed("$prefix.alphaThreshold", "TipTextureSettings.alphaThreshold")
        if (brush.antiAliasMode != CspAntiAlias.MEDIUM) preserved("$prefix.antiAlias", "Source anti-alias mode is retained; V5 owns its tip sampling policy.")
        if (brush.format == BrushFormat.PROCREATE && brush.procreateRenderingMode != ProcreateRenderingMode.UNIFORM_GLAZE) {
            executed(
                "PROCREATE.renderingMode",
                "StampSettings.transferMode",
                SemanticFidelity.APPROXIMATE,
                "V5 reconstructs the archived maximum/modulated/recursive transfer flags as deterministic per-dab alpha transfer.",
            )
        }
        if (brush.format == BrushFormat.PROCREATE &&
            (brush.procreateBrushBlendMode != ProcreateBrushBlend.NORMAL || brush.procreateBurntEdgeBlendMode != ProcreateBrushBlend.MULTIPLY || brush.procreateLuminanceBlending)
        ) preserved("PROCREATE.blendModes", "Brush/burnt-edge blend and luminance behavior are retained but not executed by V5.")
        if (!brush.procreateTaper.isDisabled() && (brush.taperStartMinimum != .3f || brush.taperEndMinimum != .3f ||
            brush.taperStartLengthUnit != 0 || brush.taperEndLengthUnit != 0
        )) preserved("$prefix.taper.advanced", "Pressure/touch programs, minimums and units are retained beyond V5's basic taper.")
        if (hasAdvancedColorMix(brush)) preserved("$prefix.wetMix.advancedCsp", "CSP mix mode, dynamics, stretch, pigment, brightness and blur controls remain source-only.")
        if (hasAdvancedGrain(brush)) preserved("$prefix.grain.advanced", "Minimum/dynamic depth, stroke rotation, composite, filtering and per-plot grain behavior remain source-only.")
        if (hasAdvancedSpray(brush)) preserved("$prefix.spray.dynamics", "Pressure/tilt/velocity/random spray size and density programs remain source-only.")
        if (brush.ribbon || brush.tipSequenceMode != 0 || brush.tipFlipHorizontalMode != 0 ||
            brush.tipFlipVerticalMode != 0 || brush.blendTipsWithDarken
        ) preserved("$prefix.tipSequence", "Ribbon, ordered tip selection, flip and darken-composite semantics remain source-only.")
        if (brush.waterEdgeEnabled) preserved("$prefix.waterEdge", "Water-edge radius, opacity, darkness and blur remain source-only.")

        if (brush.dualBrush && !wetPrimary) {
            if (brush.dualTipPreviewPaths.isEmpty()) rejected("$prefix.dualBrush.asset", "Dual brush is enabled but its secondary tip asset is missing.")
            if (brush.dualGrainPreviewPath == null &&
                (brush.dualGrain.dualSourceGrainReference != null ||
                    (brush.format != BrushFormat.SUT && brush.dualGrainDensity > .0001f))
            ) rejected("$prefix.dualGrain.asset", "Dual brush grain is referenced but not embedded in the source container.")
            if (brush.dualApplyRgb) rejected("$prefix.dualBrush.rgb", "Dual-brush RGB compositing is not supported by Paint Engine V5.")
            if (hasAdvancedDual(brush)) preserved("$prefix.dualBrush.advanced", "Dual dynamics, jitter, ribbon, sequence and flip semantics remain source-only.")
        }
        if (brush.format == BrushFormat.UNKNOWN) rejected("UNKNOWN.container", "The source is retained, but no executable brush semantics were recognized.")
    }.distinctBy { Triple(it.sourceSemantic, it.targetRuntime, it.executionDisposition) }

    fun compatibilityReport(brush: ImportedBrush): List<String> = describe(brush)
        .filter { it.executionDisposition != SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED }
        .map { mapping ->
            "SEMANTIC_CAPABILITY:${mapping.executionDisposition.name}:${mapping.sourceSemantic}:${mapping.note}"
        }

    private fun hasColorDynamics(brush: ImportedBrush): Boolean =
        brush.colorJitterHueDegrees != 0f || brush.colorJitterSaturation != 0f || brush.colorJitterValue != 0f ||
            brush.colorJitterSubColorMix != 0f || brush.colorJitterPerStrokeEnabled ||
            brush.colorJitterStrokeHueDegrees != 0f || brush.colorJitterStrokeSaturation != 0f ||
            brush.colorJitterStrokeValue != 0f || brush.colorJitterStrokeSubColorMix != 0f ||
            !brush.procreateColorDynamics.isDisabled() || colorDynamic(brush.colorJitterHueDynamics) ||
            colorDynamic(brush.colorJitterSaturationDynamics) || colorDynamic(brush.colorJitterValueDynamics) ||
            dynamic(brush.colorJitterSubColorMixDynamics)

    private fun hasAdvancedMainDynamics(brush: ImportedBrush): Boolean =
        brush.spacingPressureEnabled || brush.thicknessPressureEnabled ||
            secondary(brush.sizeSecondaryDynamics) || secondary(brush.opacitySecondaryDynamics) ||
            secondary(brush.flowSecondaryDynamics) || secondary(brush.spacingSecondaryDynamics) ||
            secondary(brush.thicknessSecondaryDynamics)

    private fun hasAdvancedColorMix(brush: ImportedBrush): Boolean =
        brush.colorMixPressureEnabled || brush.paintMixPressureEnabled || brush.colorStretch != 0f ||
            brush.colorMixBlur != 0f || dynamic(brush.colorMixBlurDynamics) ||
            secondary(brush.colorMixSecondaryDynamics) || secondary(brush.paintMixSecondaryDynamics)

    private fun hasAdvancedGrain(brush: ImportedBrush): Boolean =
        brush.grainDensityMinimum != 0f || dynamic(brush.grainDensityDynamics) || brush.grainStrokeRotation != 0f ||
            brush.grainCompositeMode != CspTextureComposite.MULTIPLY || !brush.grainApplyPerPlot || brush.grainFilteringMode != 0 ||
            brush.dualGrainDensityMinimum != 0f || dynamic(brush.dualGrainDensityDynamics) ||
            brush.dualGrainStrokeRotation != 0f || brush.dualGrainCompositeMode != CspTextureComposite.MULTIPLY ||
            !brush.dualGrainApplyPerPlot || brush.dualGrainFilteringMode != 0

    private fun hasAdvancedSpray(brush: ImportedBrush): Boolean =
        brush.sprayParticleSizePressureEnabled || brush.sprayDensityPressureEnabled ||
            secondary(brush.sprayParticleSizeSecondaryDynamics) || secondary(brush.sprayDensitySecondaryDynamics) ||
            dynamic(brush.dualSprayParticleSizeDynamics) || dynamic(brush.dualSprayDensityDynamics)

    private fun hasAdvancedDual(brush: ImportedBrush): Boolean =
        dynamic(brush.dualSizeDynamics) || dynamic(brush.dualSpacingDynamics) || dynamic(brush.dualFlowDynamics) ||
            dynamic(brush.dualThicknessDynamics) || brush.dualShapeRoundnessHorizontalJitter != 0f ||
            brush.dualShapeRoundnessVerticalJitter != 0f || brush.dualShapeTouchRotation != 0f || brush.dualShapeOrientToScreen ||
            brush.dualStrokeRotationJitterDegrees != 0f || brush.dualStampCountJitter != 0f ||
            brush.dualStrokePathJitter != 0f || brush.dualStrokePathLongitudinalJitter != 0f || brush.dualRibbon ||
            brush.dualTipSequenceMode != 0 || brush.dualTipFlipHorizontalMode != 0 ||
            brush.dualTipFlipVerticalMode != 0 || brush.dualBlendTipsWithDarken

    private fun dynamic(value: BrushParameterDynamics): Boolean = value.pressureEnabled || secondary(value.secondary)
    private fun secondary(value: BrushSecondaryDynamics): Boolean = value.tiltEnabled || value.velocityEnabled || value.randomEnabled
    private fun colorDynamic(value: BrushColorDynamics): Boolean =
        value.pressureEnabled || value.tiltEnabled || value.velocityEnabled || value.randomEnabled
}
