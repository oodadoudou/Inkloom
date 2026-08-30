package app.inkloom.brushpresetv5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushExecutionCapabilityResolverTest {
    @Test fun plainInkIsSupported() {
        assertEquals(
            BrushExecutionCapabilityResolution.Supported,
            BrushExecutionCapabilityResolver.resolve(CompiledBrushProgramV5.compile(preset())),
        )
    }

    @Test fun texturedWetTipAndStampDynamicsAreSupportedButRemainingFamiliesStayExplicit() {
        val program = CompiledBrushProgramV5.compile(
            preset(
                family = BrushFamily.WET_PAINT,
                tipTexture = TipTextureSettings(assetPaths = listOf("shape.png")),
                textureGrain = TextureGrainSettings("grain.png"),
                stamp = StampSettings(),
                wetMix = WetMixSettings(),
                dual = DualBrushSettings(tipAssetPaths = listOf("dual.png")),
                stroke = StrokeSettings(taperStartEnabled = true),
            ),
        )

        val reasons = (BrushExecutionCapabilityResolver.resolve(program) as
            BrushExecutionCapabilityResolution.Unsupported).reasons

        assertTrue(BrushExecutionCapabilityReason.REALTIME_TAPER_REQUIRED in reasons)
        assertTrue(BrushExecutionCapabilityReason.WET_PAINT_TEXTURE_GRAIN_REQUIRED in reasons)
        assertTrue(BrushExecutionCapabilityReason.WET_PAINT_DUAL_BRUSH_REQUIRED in reasons)
    }

    @Test fun wetPaintWithoutWetSettingsIsRejectedExplicitly() {
        val program = CompiledBrushProgramV5.compile(preset(family = BrushFamily.WET_PAINT))
        val reasons = (BrushExecutionCapabilityResolver.resolve(program) as
            BrushExecutionCapabilityResolution.Unsupported).reasons
        assertEquals(setOf(BrushExecutionCapabilityReason.WET_PAINT_SETTINGS_REQUIRED), reasons)
    }

    @Test fun decorationWithoutAnAssetTipIsRejectedExplicitly() {
        val program = CompiledBrushProgramV5.compile(preset(family = BrushFamily.DECORATION))
        val reasons = (BrushExecutionCapabilityResolver.resolve(program) as
            BrushExecutionCapabilityResolution.Unsupported).reasons
        assertEquals(setOf(BrushExecutionCapabilityReason.ASSET_STAMP_TIP_REQUIRED), reasons)
    }

    private fun preset(
        family: BrushFamily = BrushFamily.INK,
        tipTexture: TipTextureSettings? = null,
        textureGrain: TextureGrainSettings? = null,
        stamp: StampSettings? = null,
        wetMix: WetMixSettings? = null,
        dual: DualBrushSettings? = null,
        stroke: StrokeSettings? = null,
    ) = BrushPresetV5(
        identity = PresetIdentity(
            id = "capability-test",
            name = "Capability test",
            family = family,
            renderBrushId = "capability-test",
        ),
        renderSeed = 1,
        tip = TipSettings(),
        deposition = DepositionSettings(),
        dynamics = DynamicsSettings(PressureMappings(), TiltMappings()),
        tipTexture = tipTexture,
        textureGrain = textureGrain,
        stamp = stamp,
        wetMix = wetMix,
        dual = dual,
        stroke = stroke,
    )
}
