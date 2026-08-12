package app.inkloom.importer

import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.model.BrushFormat
import app.inkloom.model.BrushSemanticMapping
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SemanticFidelity

/** Produces the structured conversion report consumed by Engine V2 and brush-detail UI. */
object BrushSemanticMapper {
    fun apply(brush: ImportedBrush): ImportedBrush = brush.copy(semanticMappings = describe(brush))

    fun describe(brush: ImportedBrush): List<BrushSemanticMapping> = buildList {
        fun exact(source: String, target: String, note: String = "") =
            add(BrushSemanticMapping(source, target, SemanticFidelity.EXACT, note))
        fun approximate(source: String, target: String, note: String) =
            add(BrushSemanticMapping(source, target, SemanticFidelity.APPROXIMATE, note))

        exact("${brush.format.name}.opacity", "Compositor.strokeOpacityCap")
        exact("${brush.format.name}.flow", "BrushBehaviorRuntime.dabFlow")
        exact("${brush.format.name}.spacing", "StampRasterEngine.arcLengthSampler")
        if (brush.tipPreviewPaths.isNotEmpty() || brush.tipPreviewPath != null) {
            exact("${brush.format.name}.shape", "StampRasterEngine.tipTexture")
        }
        if (brush.grainPreviewPath != null) exact("${brush.format.name}.grain", "StampRasterEngine.grainTexture")
        if (brush.format == BrushFormat.PROCREATE) approximate(
            "PROCREATE.renderingMode",
            "StampRasterEngine.${brush.procreateRenderingMode.name}",
            "Archive flags and public mode are exact; transfer strength is calibrated from documented behavior because Procreate's pixel equation is proprietary.",
        )
        if (brush.format == BrushFormat.PROCREATE) {
            exact("PROCREATE.brushBlendMode", "StrokeCompositor.${app.inkloom.document.ProcreateBrushBlend.label(brush.procreateBrushBlendMode)}")
            exact("PROCREATE.burntEdgeBlendMode", "EdgePigmentCompositor.${app.inkloom.document.ProcreateBrushBlend.label(brush.procreateBurntEdgeBlendMode)}")
            if (brush.procreateLuminanceBlending) approximate(
                "PROCREATE.luminanceBlending",
                "StrokeCompositor.sRgbLuminanceCompatibility",
                "The archive toggle is preserved; Android Canvas does not expose Procreate's private luminance equation.",
            )
        }
        if (brush.alphaThresholdEnabled) exact("${brush.format.name}.alphaThreshold", "StampRasterEngine.binaryCoverageCutoff")
        if (brush.continuousPlot) exact("${brush.format.name}.continuousPlot", "AirbrushEngine.timeSampler")
        if (brush.spray) exact("${brush.format.name}.scatter", "StampRasterEngine.scatterCoat")
        if (brush.dualBrush) exact("${brush.format.name}.dualBrush", "StampRasterEngine.secondaryCoat")
        if (brush.antiAliasMode != 2) exact("${brush.format.name}.antiAlias", "StampRasterEngine.tipFilter")
        if (brush.blur > 0f) exact("${brush.format.name}.blur", "Compositor.blurRadius")
        if (brush.isEraser) exact("${brush.format.name}.eraser", "EraseMaskEngine.coverageTarget")
        if (hasDynamics(brush)) exact("${brush.format.name}.dynamics", "BrushBehaviorRuntime.compiledChannels")
        if (!brush.procreateWetMix.isDisabled()) {
            approximate(
                "PROCREATE.wetMix",
                "WetPaintEngine.incrementalPigmentTransport",
                "All authored controls, reservoir depletion and local destination-pigment transport are represented; coefficients are calibrated from official behavior because Procreate's private pigment equation is unavailable.",
            )
        } else if (brush.watercolor || brush.colorMix > 0f || brush.paintMix > 0f) {
            approximate("${brush.format.name}.wetMix", "WetSmudgeEngine.deterministicGlaze", "Source controls are preserved; the pixel equation is an Inkloom implementation of the documented behavior.")
        }
        if (brush.format == BrushFormat.UNKNOWN) {
            add(BrushSemanticMapping("UNKNOWN.container", "BrushImportSandbox.retainedSource", SemanticFidelity.UNSUPPORTED, "Source is retained without executable brush semantics."))
        }
    }.distinctBy { Triple(it.sourceSemantic, it.targetRuntime, it.fidelity) }

    private fun hasDynamics(brush: ImportedBrush): Boolean =
        brush.sizePressureEnabled || brush.opacityPressureEnabled || brush.flowPressureEnabled ||
            brush.spacingPressureEnabled || brush.thicknessPressureEnabled || brush.blurPressureEnabled ||
            dynamic(brush.grainDensityDynamics) || dynamic(brush.dualSizeDynamics) ||
            dynamic(brush.dualSpacingDynamics) || dynamic(brush.dualFlowDynamics) ||
            dynamic(brush.dualThicknessDynamics) || dynamic(brush.colorJitterSubColorMixDynamics) ||
            colorDynamic(brush.colorJitterHueDynamics) || colorDynamic(brush.colorJitterSaturationDynamics) ||
            colorDynamic(brush.colorJitterValueDynamics) || secondary(brush.sizeSecondaryDynamics) ||
            secondary(brush.opacitySecondaryDynamics) || secondary(brush.flowSecondaryDynamics)

    private fun dynamic(value: BrushParameterDynamics): Boolean =
        value.pressureEnabled || secondary(value.secondary)

    private fun secondary(value: BrushSecondaryDynamics): Boolean =
        value.tiltEnabled || value.velocityEnabled || value.randomEnabled

    private fun colorDynamic(value: app.inkloom.document.BrushColorDynamics): Boolean =
        value.pressureEnabled || value.tiltEnabled || value.velocityEnabled || value.randomEnabled
}
