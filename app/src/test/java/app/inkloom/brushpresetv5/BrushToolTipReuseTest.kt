package app.inkloom.brushpresetv5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushToolTipReuseTest {
    private val paint = BrushPresetV5(
        identity = PresetIdentity("tip", "Tip", family = BrushFamily.INK, renderBrushId = "system-pen"),
        renderSeed = 37,
        tip = TipSettings(size = 25f, aspect = .7f, hardness = .8f),
        deposition = DepositionSettings(opacity = .6f, flow = .4f),
        dynamics = DynamicsSettings(PressureMappings(widthEnabled = true), TiltMappings()),
    )

    @Test fun `eraser borrows the entire paint tip without changing its source`() {
        val erased = BrushToolTipReuse.forEraser(BrushPresetV5Codec.encode(paint)).getOrThrow()
        assertEquals(BrushOutput.ERASE, erased.output)
        assertEquals(paint.tip, erased.tip)
        assertEquals(paint.deposition, erased.deposition)
        assertEquals(paint.dynamics, erased.dynamics)
        assertEquals(BrushOutput.PAINT, paint.output)
    }

    @Test fun `unavailable and unsupported tips return a reason`() {
        assertTrue(BrushToolTipReuse.forEraser(null).exceptionOrNull()?.message?.contains("V5") == true)
        val wet = paint.copy(identity = paint.identity.copy(family = BrushFamily.WET_PAINT),
            wetMix = WetMixSettings())
        assertTrue(BrushToolTipReuse.forEraser(BrushPresetV5Codec.encode(wet)).isFailure)
    }

    @Test fun `smudge keeps single authored tip shape and rejects unsupported dynamics`() {
        val round = paint.copy(tip = paint.tip.copy(aspect = 1f), deposition = DepositionSettings())
        assertEquals(round, BrushToolTipReuse.forSmudge(BrushPresetV5Codec.encode(round)).getOrThrow())
        assertEquals(paint, BrushToolTipReuse.forSmudge(BrushPresetV5Codec.encode(paint)).getOrThrow())
        val textured = paint.copy(tipTexture = TipTextureSettings(procedural = false,
            assetPath = "/tmp/tip.png", assetPaths = listOf("/tmp/tip.png")))
        assertEquals(textured, BrushToolTipReuse.forSmudge(BrushPresetV5Codec.encode(textured)).getOrThrow())
        val scattered = textured.copy(stamp = StampSettings(lateralPathJitter = .5f))
        assertTrue(BrushToolTipReuse.forSmudge(BrushPresetV5Codec.encode(scattered)).exceptionOrNull()
            ?.message?.contains("unsupported dynamics") == true)
    }
}
