package app.inkloom.brushpresetv5

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.ImportedBrushGrain
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

    @Test fun importBoundaryWritesV5AndRejectsLegacyInkloomPackage() {
        val decodedAbr = ImportedBrush(
            id = "abr-1",
            name = "Round",
            format = BrushFormat.ABR,
            compatibility = Compatibility.HIGH,
            sizeBytes = 120,
            report = emptyList(),
            defaultSize = 18f,
        )
        val converted = BrushPresetV5Import.convertOrReject(decodedAbr)
        val oldInkloom = BrushPresetV5Import.convertOrReject(decodedAbr.copy(format = BrushFormat.INKLOOM))

        assertEquals(5, BrushPresetV5Import.requirePreset(converted).schemaVersion)
        assertEquals(Compatibility.FAILED, oldInkloom.compatibility)
        assertEquals(null, oldInkloom.v5PresetJson)
    }

    @Test fun importBoundaryPreservesEraserMultiTipSprayAndWetProgramsInV5Truth() {
        val decoded = ImportedBrush(
            id = "sut-native",
            name = "Native SUT",
            format = BrushFormat.SUT,
            compatibility = Compatibility.HIGH,
            sizeBytes = 42,
            report = emptyList(),
            isEraser = true,
            tipPreviewPath = "/tips/primary.png",
            tipPreviewPaths = listOf("/tips/primary.png", "/tips/alternate.png"),
            spray = true,
            sprayParticleSize = 18f,
            sprayDensity = 2.5f,
            spraySpread = .8f,
            stampCount = 3,
            grainPreviewPath = "/grains/primary.png",
            grainDensity = .65f,
            grainScale = 175f,
            dualBrush = true,
            dualTipPreviewPaths = listOf("/tips/secondary.png"),
            dualSizeScale = .7f,
            dualCompositeMode = 2,
            dualGrain = ImportedBrushGrain(
                previewPath = "/grains/secondary.png",
                density = .42f,
                scale = 130f,
            ),
        )

        val preset = BrushPresetV5Import.requirePreset(BrushPresetV5Import.convertDecodedSource(decoded))

        assertEquals(BrushOutput.ERASE, preset.output)
        assertEquals(listOf("/tips/primary.png", "/tips/alternate.png"), preset.tipTexture?.assetPaths)
        assertEquals(3, preset.stamp?.particlesPerPlot)
        assertEquals(18f, preset.stamp?.spray?.particleSize)
        assertEquals(2.5f, preset.stamp?.spray?.density)
        assertEquals("/grains/primary.png", preset.textureGrain?.assetPath)
        assertEquals(.65f, preset.textureGrain?.density)
        assertEquals(listOf("/tips/secondary.png"), preset.dual?.tipAssetPaths)
        assertEquals(.7f, preset.dual?.sizeScale)
        assertEquals(DualCompositeMode.MULTIPLY, preset.dual?.compositeMode)
        assertEquals("/grains/secondary.png", preset.dual?.textureGrain?.assetPath)
    }

    @Test fun importBoundaryRejectsUnsupportedDualRgbInsteadOfFallingBack() {
        val rejected = BrushPresetV5Import.convertOrReject(
            ImportedBrush(
                name = "RGB dual",
                format = BrushFormat.SUT,
                compatibility = Compatibility.HIGH,
                sizeBytes = 12,
                report = emptyList(),
                dualBrush = true,
                dualTipPreviewPaths = listOf("/tips/secondary.png"),
                dualApplyRgb = true,
            ),
        )

        assertEquals(Compatibility.FAILED, rejected.compatibility)
        assertEquals(null, rejected.v5PresetJson)
        assertTrue(rejected.report.any { it.contains("RGB compositing") })
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
