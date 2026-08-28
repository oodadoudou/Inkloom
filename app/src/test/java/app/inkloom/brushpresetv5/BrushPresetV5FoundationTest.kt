package app.inkloom.brushpresetv5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushPresetV5FoundationTest {
    @Test fun codecAcceptsOnlySchemaFive() {
        val preset = referencePencil()
        val encoded = BrushPresetV5Codec.encode(preset)

        assertEquals(preset, BrushPresetV5Codec.decodeOrNull(encoded))
        assertEquals(null, BrushPresetV5Codec.decodeOrNull(encoded.replace("\"schemaVersion\":5", "\"schemaVersion\":4")))
    }

    @Test fun compilerProducesImmutableV5SemanticsWithoutLegacyRendererOutput() {
        val preset = referencePencil()
        val compiled = CompiledBrushProgramV5.compile(preset)
        val edited = preset.copy(tip = preset.tip.copy(size = 88f))

        assertEquals(RenderBackend.PROCEDURAL_STAMP, compiled.backend)
        assertEquals(7f, compiled.tip.size)
        assertEquals(.28f, compiled.pressure.widthMinimum)
        assertTrue(compiled.grainSampler != null)
        assertNotEquals(edited.tip.size, compiled.tip.size)
        assertFalse(CompiledBrushProgramV5::class.java.methods.any { it.name == "toInkStroke" })
    }

    @Test fun compilerRejectsLegacySchemaAndInvalidGeometry() {
        val preset = referencePencil()
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CompiledBrushProgramV5.compile(preset.copy(schemaVersion = 4))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CompiledBrushProgramV5.compile(preset.copy(tip = preset.tip.copy(size = Float.NaN)))
        }
    }

    @Test fun ownershipKeepsFactoryImmutableAndPinsRenderIdentity() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        val user = PresetOwnershipOperations.duplicate(factory)
        val edited = PresetOwnershipOperations.withEdits(
            user,
            user.preset.copy(
                renderSeed = 123,
                identity = user.preset.identity.copy(renderBrushId = "changed"),
                tip = user.preset.tip.copy(size = 20f),
            ),
        )

        assertTrue(user.ownership is PresetOwnership.User)
        assertEquals("system-hb-pencil", edited.preset.identity.renderBrushId)
        assertEquals(factory.preset.renderSeed, edited.preset.renderSeed)
        assertEquals(20f, edited.preset.tip.size)
        assertEquals(7f, factory.preset.tip.size)
    }

    private fun referencePencil() = BrushPresetV5(
        identity = PresetIdentity(
            id = "system-hb-pencil",
            name = "HB Pencil",
            family = BrushFamily.PENCIL,
            renderBrushId = "system-hb-pencil",
        ),
        renderSeed = 0x12345678,
        tip = TipSettings(size = 7f, hardness = .64f),
        deposition = DepositionSettings(opacity = 1f, flow = .68f),
        spacing = SpacingSettings(value = .26f),
        dynamics = DynamicsSettings(
            pressure = PressureMappings(widthMinimum = .28f, depositionEnabled = true, depositionMinimum = .2f),
            tilt = TiltMappings(widthEnabled = true, widthStart = .25f, widthMaximum = .8f),
        ),
        paperGrain = PaperGrainSettings(scale = 96f, density = .5f, strength = .8f),
        tipTexture = TipTextureSettings(procedural = true, toothStrength = .64f),
    )
}
