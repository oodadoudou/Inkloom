package app.inkloom.brushpresetv5

/**
 * Stable production capability reasons for a compiled V5 brush.
 *
 * Parsing and compiling preserve source semantics. This resolver is the single boundary that
 * decides whether the current production renderer can execute those semantics without silently
 * dropping authored behavior.
 */
enum class BrushExecutionCapabilityReason {
    REALTIME_TAPER_REQUIRED,
    POST_CORRECTION_NOT_REALTIME_SAFE,
    WET_PAINT_SETTINGS_REQUIRED,
    WET_PAINT_ERASER_UNSUPPORTED,
    WET_PAINT_DUAL_BRUSH_REQUIRED,
    ASSET_STAMP_TIP_REQUIRED,
    TEXTURE_GRAIN_REQUIRES_STAMP_BACKEND,
    PAPER_GRAIN_REQUIRES_STAMP_BACKEND,
    STAMP_DYNAMICS_REQUIRE_STAMP_BACKEND,
}

sealed interface BrushExecutionCapabilityResolution {
    data object Supported : BrushExecutionCapabilityResolution

    data class Unsupported(
        val reasons: Set<BrushExecutionCapabilityReason>,
    ) : BrushExecutionCapabilityResolution {
        init {
            require(reasons.isNotEmpty())
        }
    }
}

object BrushExecutionCapabilityResolver {
    fun resolve(program: CompiledBrushProgramV5): BrushExecutionCapabilityResolution {
        val reasons = linkedSetOf<BrushExecutionCapabilityReason>()
        val hasAssetTip = program.tip.tipTexture?.assetPaths?.isNotEmpty() == true
        val hasTaper = program.strokeShaping.taperStartEnabled || program.strokeShaping.taperEndEnabled

        if (hasTaper) reasons += BrushExecutionCapabilityReason.REALTIME_TAPER_REQUIRED
        if (program.strokeShaping.postCorrection > .0001f) {
            reasons += BrushExecutionCapabilityReason.POST_CORRECTION_NOT_REALTIME_SAFE
        }

        if (program.grainSampler != null && program.dual == null &&
            program.backend !in setOf(RenderBackend.ASSET_STAMP, RenderBackend.PROCEDURAL_STAMP)
        ) {
            reasons += BrushExecutionCapabilityReason.PAPER_GRAIN_REQUIRES_STAMP_BACKEND
        }

        when (program.backend) {
            RenderBackend.WET_PAINT -> {
                if (program.wetMix == null) reasons += BrushExecutionCapabilityReason.WET_PAINT_SETTINGS_REQUIRED
                if (program.output == BrushOutput.ERASE) {
                    reasons += BrushExecutionCapabilityReason.WET_PAINT_ERASER_UNSUPPORTED
                }
                if (program.dual != null) reasons += BrushExecutionCapabilityReason.WET_PAINT_DUAL_BRUSH_REQUIRED
            }

            RenderBackend.ASSET_STAMP -> {
                if (!hasAssetTip) reasons += BrushExecutionCapabilityReason.ASSET_STAMP_TIP_REQUIRED
            }

            RenderBackend.CONTINUOUS_RIBBON -> {
                if (program.textureGrain != null && !hasAssetTip) {
                    reasons += BrushExecutionCapabilityReason.TEXTURE_GRAIN_REQUIRES_STAMP_BACKEND
                }
                val unsupportedStampDynamics = program.stamp?.let { stamp ->
                    stamp.followStrokeDirection || stamp.followStylusOrientation ||
                        stamp.randomRotationDegrees > .0001f || stamp.particlesPerPlot > 1 ||
                        stamp.spray != null
                } == true
                if (unsupportedStampDynamics && !hasAssetTip) {
                    reasons += BrushExecutionCapabilityReason.STAMP_DYNAMICS_REQUIRE_STAMP_BACKEND
                }
            }

            RenderBackend.PROCEDURAL_STAMP, RenderBackend.AIRBRUSH -> Unit
        }

        return if (reasons.isEmpty()) BrushExecutionCapabilityResolution.Supported
        else BrushExecutionCapabilityResolution.Unsupported(reasons)
    }
}
