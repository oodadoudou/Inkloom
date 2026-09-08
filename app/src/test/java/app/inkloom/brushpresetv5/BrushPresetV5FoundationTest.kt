package app.inkloom.brushpresetv5

import app.inkloom.model.BrushToolBinding
import app.inkloom.model.BrushToolRole

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
    @Test fun sutMixEnableControlsTransportIndependentlyFromRetainedPaintDensity() {
        val source = ImportedBrush(
            id = "sut-disabled-mix", name = "Disabled mixing", format = BrushFormat.SUT,
            compatibility = Compatibility.HIGH, sizeBytes = 42, report = emptyList(),
            watercolor = false, colorMix = .3f, paintMix = .5f,
            dualBrush = true, dualTipPreviewPaths = listOf("/secondary.png"),
        )
        val dry = BrushPresetV5Import.convertOrReject(source)
        val dryPreset = BrushPresetV5Import.requirePreset(dry)
        assertEquals(BrushFamily.INK, dryPreset.identity.family)
        assertEquals(null, dryPreset.wetMix)
        assertEquals(listOf("/secondary.png"), dryPreset.dual?.tipAssetPaths)
        assertEquals(.3f, dry.colorMix, 0f)
        assertEquals(.5f, dry.paintMix, 0f)
        assertEquals(
            app.inkloom.model.SemanticExecutionDisposition.PRESERVED_SOURCE_ONLY,
            dry.semanticMappings.single { it.sourceSemantic == "SUT.wetMix.basic" }.executionDisposition,
        )
        assertTrue(dry.report.any { it.contains("Color mixing is disabled") })
        val wet = BrushPresetV5Import.convertOrReject(source.copy(watercolor = true))
        val wetPreset = BrushPresetV5Import.requirePreset(wet)
        assertEquals(BrushFamily.WET_PAINT, wetPreset.identity.family)
        assertEquals(.5f, wetPreset.wetMix?.charge ?: 0f, 0f)
        assertEquals(.3f, wetPreset.wetMix?.pull ?: 0f, 0f)
        assertEquals(null, wetPreset.dual)
    }

    @Test fun absentSutDualTextureDoesNotBecomeAMissingResourceFromRetainedDensity() {
        val source = ImportedBrush(
            id = "sut-no-dual-texture", name = "No second texture", format = BrushFormat.SUT,
            compatibility = Compatibility.HIGH, sizeBytes = 42, report = emptyList(),
            dualBrush = true, dualTipPreviewPaths = listOf("/secondary.png"),
            dualGrain = ImportedBrushGrain(density = .5f),
        )
        val absent = BrushPresetV5Import.convertOrReject(source)
        assertTrue(absent.compatibility != Compatibility.FAILED)
        assertEquals(null, BrushPresetV5Import.requirePreset(absent).dual?.textureGrain)
        assertEquals(.5f, absent.dualGrainDensity, 0f)
        val missing = BrushPresetV5Import.convertOrReject(source.copy(
            dualGrain = source.dualGrain.copy(dualSourceGrainReference = "SUT.DualTextureImage"),
        ))
        assertEquals(Compatibility.FAILED, missing.compatibility)
        assertTrue(missing.report.any { it.contains("SUT.dualGrain.asset") })
        val otherFormat = BrushPresetV5Import.convertOrReject(source.copy(format = BrushFormat.ABR))
        assertEquals(Compatibility.FAILED, otherFormat.compatibility)
    }

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
            toolBinding = BrushToolBinding(BrushToolRole.ERASE),
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

    @Test fun importBoundaryKeepsPostCorrectionOutOfTheRealtimeCausalProgram() {
        val decoded = ImportedBrush(
            id = "sut-smoothed",
            name = "Smoothed SUT",
            format = BrushFormat.SUT,
            compatibility = Compatibility.HIGH,
            sizeBytes = 42,
            report = emptyList(),
            stabilization = .2f,
            streamLine = .55f,
            procreateStabilization = .35f,
            motionFiltering = .4f,
            postCorrectionEnabled = true,
            postCorrection = .72f,
        )

        val preset = BrushPresetV5Import.requirePreset(BrushPresetV5Import.convertDecodedSource(decoded))

        assertEquals(.55f, preset.stroke?.smoothing ?: 0f, .0001f)
        assertEquals(
            "V5 may not request a second full-stroke pass after pen-up",
            0f,
            preset.stroke?.postCorrection ?: -1f,
            .0001f,
        )
        assertTrue(decoded.let(BrushPresetV5Import::convertDecodedSource).report.any {
            it.contains("SEMANTIC_CAPABILITY:PRESERVED_SOURCE_ONLY:SUT.postCorrection:")
        })
    }

    @Test fun importBoundaryExecutesWetPrimaryWithoutClaimingTaperGrainOrDualSupport() {
        val converted = BrushPresetV5Import.convertDecodedSource(
            ImportedBrush(
                id = "procreate-wet-primary",
                name = "Wet primary",
                format = BrushFormat.PROCREATE,
                compatibility = Compatibility.BASIC,
                sizeBytes = 42,
                report = emptyList(),
                watercolor = true,
                procreateWetMix = app.inkloom.document.ProcreateWetMix(pull = .4f),
                tipPreviewPath = "/tips/primary.png",
                tipPreviewPaths = listOf("/tips/primary.png"),
                grainPreviewPath = "/grain.png",
                grainDensity = .8f,
                dualBrush = true,
                dualTipPreviewPaths = listOf("/tips/secondary.png"),
                taperStartEnabled = true,
                taperEndEnabled = true,
            ),
        )
        val preset = BrushPresetV5Import.requirePreset(converted)

        assertEquals(BrushFamily.WET_PAINT, preset.identity.family)
        assertEquals("/grain.png", preset.textureGrain?.assetPath)
        assertEquals(null, preset.dual)
        assertEquals(.15f, preset.wetMix?.charge ?: 0f, .0001f)
        assertEquals(.7f, preset.wetMix?.attack ?: 0f, .0001f)
        assertFalse(preset.stroke?.taperStartEnabled == true)
        assertFalse(preset.stroke?.taperEndEnabled == true)
        assertEquals(
            BrushExecutionCapabilityResolution.Supported,
            BrushExecutionCapabilityResolver.resolve(CompiledBrushProgramV5.compile(preset)),
        )
        listOf("PROCREATE.taper.basic", "PROCREATE.dualBrush.basic").forEach { semantic ->
            assertTrue(converted.report.any {
                it.startsWith("SEMANTIC_CAPABILITY:PRESERVED_SOURCE_ONLY:$semantic:")
            })
        }
        assertTrue(converted.semanticMappings.any {
            it.sourceSemantic == "PROCREATE.grain.basic" &&
                it.executionDisposition == app.inkloom.model.SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED
        })
    }

    @Test fun importBoundarySuppliesWetDepositionIndependentlyFromAuthoredCharge() {
        val converted = BrushPresetV5Import.convertDecodedSource(
            ImportedBrush(
                id = "procreate-wet-charge-only",
                name = "Wet charge only",
                format = BrushFormat.PROCREATE,
                compatibility = Compatibility.BASIC,
                sizeBytes = 42,
                report = emptyList(),
                watercolor = true,
                procreateWetMix = app.inkloom.document.ProcreateWetMix(
                    charge = .5f,
                    attack = 0f,
                    pull = .75f,
                ),
            ),
        )
        val preset = BrushPresetV5Import.requirePreset(converted)

        assertEquals(.5f, preset.wetMix?.charge ?: 0f, .0001f)
        assertEquals(.7f, preset.wetMix?.attack ?: 0f, .0001f)
    }

    @Test fun importBoundaryKeepsAnUprightFootprintForMaximumTiltDynamics() {
        val converted = BrushPresetV5Import.convertDecodedSource(
            ImportedBrush(
                id = "procreate-maximum-tilt",
                name = "Maximum tilt",
                format = BrushFormat.PROCREATE,
                compatibility = Compatibility.BASIC,
                sizeBytes = 42,
                report = emptyList(),
                sizeSecondaryDynamics = app.inkloom.document.BrushSecondaryDynamics(
                    tiltEnabled = true,
                    tiltMinimum = 0f,
                    tiltStart = .1f,
                    tiltCurve = listOf(
                        app.inkloom.document.BrushCurvePoint(0f, 0f),
                        app.inkloom.document.BrushCurvePoint(1f, 1f),
                    ),
                ),
            ),
        )
        val preset = BrushPresetV5Import.requirePreset(converted)

        assertEquals(.2f, preset.dynamics.tilt.widthMinimum, .0001f)
        assertTrue(preset.dynamics.tilt.widthEnabled)
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

    @Test fun importBoundaryAdmitsExecutableCoreAndReportsNonBlockingSourceSemantics() {
        val converted = BrushPresetV5Import.convertOrReject(
            ImportedBrush(
                id = "procreate-source-only",
                name = "Source-only color dynamics",
                format = BrushFormat.PROCREATE,
                compatibility = Compatibility.HIGH,
                sizeBytes = 12,
                report = emptyList(),
                tipPreviewPath = "/shape.png",
                tipPreviewPaths = listOf("/shape.png"),
                colorJitterHueDegrees = 15f,
            ),
        )

        assertTrue(converted.compatibility != Compatibility.FAILED)
        assertTrue(converted.v5PresetJson != null)
        assertTrue(converted.report.any {
            it.startsWith("SEMANTIC_CAPABILITY:PRESERVED_SOURCE_ONLY:PROCREATE.colorDynamics:")
        })
        assertEquals(
            BrushExecutionCapabilityResolution.Supported,
            BrushExecutionCapabilityResolver.resolve(CompiledBrushProgramV5.compile(BrushPresetV5Import.requirePreset(converted))),
        )
    }

    @Test fun importBoundaryRejectsAnEncodedPresetThatProductionCannotExecute() {
        val unsupported = referencePencil().copy(
            stroke = StrokeSettings(taperStartEnabled = true),
        )
        val rejected = BrushPresetV5Import.convertOrReject(
            ImportedBrush(
                name = "Encoded unsupported taper",
                format = BrushFormat.INKLOOM,
                compatibility = Compatibility.COMPLETE,
                sizeBytes = 12,
                report = emptyList(),
                v5PresetJson = BrushPresetV5Codec.encode(unsupported),
            ),
        )

        assertEquals(Compatibility.FAILED, rejected.compatibility)
        assertEquals(null, rejected.v5PresetJson)
        assertTrue(rejected.report.any { it.contains("REALTIME_TAPER_REQUIRED") })
    }

    @Test fun importBoundaryRejectsRecognizedContainerWithoutExecutableSemantics() {
        val rejected = BrushPresetV5Import.convertOrReject(
            ImportedBrush(
                name = "Opaque container",
                format = BrushFormat.UNKNOWN,
                compatibility = Compatibility.EXTRACTED,
                sizeBytes = 12,
                report = emptyList(),
            ),
        )

        assertEquals(Compatibility.FAILED, rejected.compatibility)
        assertEquals(null, rejected.v5PresetJson)
        assertTrue(rejected.report.any { it.startsWith("UNSUPPORTED_WITH_EXPLICIT_REASON:") })
        assertTrue(rejected.semanticMappings.any {
            it.executionDisposition == app.inkloom.model.SemanticExecutionDisposition.UNSUPPORTED_WITH_EXPLICIT_REASON
        })
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
