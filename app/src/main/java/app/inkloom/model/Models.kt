package app.inkloom.model

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushTaperMode
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.CspBrushGap
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class BrushFormat(val label: String) {
    SYSTEM("System"), INKLOOM("Inkloom"), ABR("ABR"), SUT("CSP / SUT"), PROCREATE("Procreate"), IMAGE("Image tip"), UNKNOWN("Unknown")
}

@Serializable
enum class Compatibility(val label: String) {
    COMPLETE("Complete conversion"), HIGH("Highly compatible"), BASIC("Basic conversion"), EXTRACTED("Tip extracted"), FAILED("Unsupported")
}

@Serializable
enum class SemanticFidelity { EXACT, APPROXIMATE, UNSUPPORTED }

/** A durable, user-auditable source-semantic to Engine V2 runtime mapping. */
@Serializable
data class BrushSemanticMapping(
    val sourceSemantic: String,
    val targetRuntime: String,
    val fidelity: SemanticFidelity,
    val note: String = "",
)

@Serializable
data class ImportedBrush(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val format: BrushFormat,
    val compatibility: Compatibility,
    val sizeBytes: Long,
    val report: List<String>,
    val sourcePath: String? = null,
    val sha256: String? = null,
    val tipPreviewPath: String? = null,
    val tipPreviewPaths: List<String> = emptyList(),
    val dualTipPreviewPaths: List<String> = emptyList(),
    val grainPreviewPath: String? = null,
    val grainDensity: Float = 0f,
    val grainDensityDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val grainScale: Float = 100f,
    /** Procreate Grain > Movement. 0 drags with the tip; 1 rolls like Texturized grain. */
    val grainMovement: Float = 0f,
    /** Procreate Grain > Zoom. 0 follows brush size; 1 is Cropped at a fixed texture size. */
    val grainZoom: Float = 0f,
    val grainRotationDegrees: Float = 0f,
    val grainBrightness: Float = 0f,
    val grainContrast: Float = 0f,
    val grainReversed: Boolean = false,
    val grainEmphasized: Boolean = false,
    val grainOffsetJitter: Boolean = false,
    val grainCompositeMode: Int = CspTextureComposite.MULTIPLY,
    val defaultSize: Float = 8f,
    /** Procreate StreamLine > Amount. Independent from CSP stabilization. */
    val streamLine: Float = 0f,
    /** Procreate StreamLine > Pressure. Smooths pressure onset separately from path input. */
    val streamLinePressure: Float = 0f,
    /** Procreate Stabilization > Amount (moving-average path correction). */
    val procreateStabilization: Float = 0f,
    /** Procreate Motion Filtering > Amount. */
    val motionFiltering: Float = 0f,
    /** Procreate Motion Filtering > Expression. */
    val motionFilteringExpression: Float = 0f,
    /** CSP Correction > Stabilization. */
    val stabilization: Float = 0f,
    val stabilizationBySpeed: Boolean = false,
    val stabilizationBySpeedMode: Int = 1,
    val spacing: Float = .12f,
    val spacingMode: Int = CspBrushGap.FIXED,
    val flow: Float = 1f,
    val hardness: Float = 1f,
    val antiAliasMode: Int = CspAntiAlias.MEDIUM,
    val blur: Float = 0f,
    val blurUnit: Int = 0,
    val blurLinkedToSize: Boolean = false,
    val thickness: Float = 1f,
    val thicknessVertical: Boolean = false,
    val rotationDegrees: Float = 0f,
    val followStylusOrientation: Boolean = false,
    val followStrokeDirection: Boolean = false,
    val randomRotationDegrees: Float = 0f,
    val strokeRotationJitterDegrees: Float = 0f,
    val stampCount: Int = 1,
    val stampCountJitter: Float = 0f,
    val continuousPlot: Boolean = false,
    val colorJitterHueDegrees: Float = 0f,
    val colorJitterSaturation: Float = 0f,
    val colorJitterValue: Float = 0f,
    val colorJitterTarget: Int = 0,
    val colorJitterHueDynamics: BrushColorDynamics = BrushColorDynamics(),
    val colorJitterSaturationDynamics: BrushColorDynamics = BrushColorDynamics(),
    val colorJitterValueDynamics: BrushColorDynamics = BrushColorDynamics(),
    val watercolor: Boolean = false,
    val colorMix: Float = 0f,
    val colorMixPressureEnabled: Boolean = false,
    val colorMixPressureMinimum: Float = 0f,
    val colorMixPressureCurve: List<BrushCurvePoint> = emptyList(),
    val colorMixSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val paintMix: Float = 0f,
    val paintMixPressureEnabled: Boolean = false,
    val paintMixPressureMinimum: Float = 0f,
    val paintMixPressureCurve: List<BrushCurvePoint> = emptyList(),
    val paintMixSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val spray: Boolean = false,
    val sprayParticleSize: Float = 100f,
    val sprayParticleSizeUnit: Int = 0,
    val sprayParticleSizeSyncBrushSize: Boolean = true,
    val sprayParticleSizePressureEnabled: Boolean = false,
    val sprayParticleSizePressureMinimum: Float = 0f,
    val sprayParticleSizePressureCurve: List<BrushCurvePoint> = emptyList(),
    val sprayParticleSizeSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val sprayDensity: Float = 1f,
    val sprayDensityPressureEnabled: Boolean = false,
    val sprayDensityPressureMinimum: Float = 0f,
    val sprayDensityPressureCurve: List<BrushCurvePoint> = emptyList(),
    val sprayDensitySecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val sprayBias: Float = 0f,
    val spraySpread: Float = 1f,
    val sprayParticleRotationDegrees: Float = 0f,
    val sprayParticleFollowStylusOrientation: Boolean = false,
    val sprayParticleFollowStrokeDirection: Boolean = false,
    val sprayParticleRandomRotationDegrees: Float = 0f,
    val ribbon: Boolean = false,
    val tipSequenceMode: Int = 0,
    val tipFlipHorizontalMode: Int = 0,
    val tipFlipVerticalMode: Int = 0,
    val blendTipsWithDarken: Boolean = false,
    val dualBrush: Boolean = false,
    val dualSizeScale: Float = 1f,
    val dualSizeDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualSpacing: Float = .12f,
    val dualSpacingMode: Int = CspBrushGap.FIXED,
    val dualSpacingDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualFlow: Float = 1f,
    val dualFlowDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualHardness: Float = 1f,
    val dualAntiAliasMode: Int = CspAntiAlias.MEDIUM,
    val dualThickness: Float = 1f,
    val dualThicknessDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualThicknessVertical: Boolean = false,
    val dualRotationDegrees: Float = 0f,
    val dualFollowStylusOrientation: Boolean = false,
    val dualFollowStrokeDirection: Boolean = false,
    val dualRandomRotationDegrees: Float = 0f,
    val dualStrokeRotationJitterDegrees: Float = 0f,
    val dualStampCount: Int = 1,
    val dualStampCountJitter: Float = 0f,
    val dualRibbon: Boolean = false,
    val dualSpray: Boolean = false,
    val dualSprayParticleSize: Float = 100f,
    val dualSprayParticleSizeUnit: Int = 0,
    val dualSprayParticleSizeSyncBrushSize: Boolean = true,
    val dualSprayParticleSizeDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualSprayDensity: Float = 1f,
    val dualSprayDensityDynamics: BrushParameterDynamics = BrushParameterDynamics(),
    val dualSprayBias: Float = 0f,
    val dualSpraySpread: Float = 1f,
    val dualSprayParticleRotationDegrees: Float = 0f,
    val dualSprayParticleFollowStylusOrientation: Boolean = false,
    val dualSprayParticleFollowStrokeDirection: Boolean = false,
    val dualSprayParticleRandomRotationDegrees: Float = 0f,
    val dualTipSequenceMode: Int = 0,
    val dualTipFlipHorizontalMode: Int = 0,
    val dualTipFlipVerticalMode: Int = 0,
    val dualBlendTipsWithDarken: Boolean = false,
    val dualCompositeMode: Int = 0,
    val dualApplyRgb: Boolean = false,
    val taperMode: BrushTaperMode = BrushTaperMode.PERCENTAGE,
    val taperStartEnabled: Boolean = false,
    val taperStartLength: Float = .2f,
    val taperStartLengthUnit: Int = 0,
    val taperStartMinimum: Float = .3f,
    val taperEndEnabled: Boolean = false,
    val taperEndLength: Float = .2f,
    val taperEndLengthUnit: Int = 0,
    val taperEndMinimum: Float = .3f,
    val waterEdgeEnabled: Boolean = false,
    val waterEdgeRadius: Float = 3f,
    val waterEdgeRadiusUnit: Int = 0,
    val waterEdgeOpacity: Float = .2f,
    val waterEdgeDarkness: Float = 0f,
    val waterEdgeBlur: Float = 0f,
    val waterEdgeBlurUnit: Int = 0,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val isEraser: Boolean = false,
    val groupName: String = "",
    val tags: List<String> = emptyList(),
    val lastUsedAt: Long = 0L,
    val sortOrder: Int = 0,
    val defaultOpacity: Float = 1f,
    val sizePressureEnabled: Boolean = true,
    val sizePressureMinimum: Float = .35f,
    val sizePressureCurve: List<BrushCurvePoint> = emptyList(),
    val sizeSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val opacityPressureEnabled: Boolean = false,
    val opacityPressureMinimum: Float = 0f,
    val opacityPressureCurve: List<BrushCurvePoint> = emptyList(),
    val opacitySecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val flowPressureEnabled: Boolean = false,
    val flowPressureMinimum: Float = 0f,
    val flowPressureCurve: List<BrushCurvePoint> = emptyList(),
    val flowSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val spacingPressureEnabled: Boolean = false,
    val spacingPressureMinimum: Float = 0f,
    val spacingPressureCurve: List<BrushCurvePoint> = emptyList(),
    val spacingSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val thicknessPressureEnabled: Boolean = false,
    val thicknessPressureMinimum: Float = 0f,
    val thicknessPressureCurve: List<BrushCurvePoint> = emptyList(),
    val thicknessSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val blurPressureEnabled: Boolean = false,
    val blurPressureMinimum: Float = 0f,
    val blurPressureCurve: List<BrushCurvePoint> = emptyList(),
    val blurSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics(),
    val parserVersion: Int = 0,
    val semanticMappings: List<BrushSemanticMapping> = emptyList(),
)

enum class EditorTool { PEN, HIGHLIGHTER, ERASER, EYEDROPPER, SELECT, SHAPE, TEXT, NOTE, LINK, IMAGE, AUDIO, LASER, FILL, GRADIENT, SMUDGE, LIQUIFY, CORRECT_LINE }

enum class SymmetryMode { NONE, HORIZONTAL, VERTICAL, QUADRANT, RADIAL_4, RADIAL_6, RADIAL_8 }
