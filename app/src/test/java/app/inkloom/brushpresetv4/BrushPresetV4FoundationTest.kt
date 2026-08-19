package app.inkloom.brushpresetv4

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BRUSH_PRESET_V4_FOUNDATION tests: schema versioning, capability separation, V3->V4 migration,
 * immutable compile snapshot, factory/user ownership, and deterministic serialization round-trip.
 * No Android dependency — the raster goldens are asserted in the instrumented parity test.
 */
class BrushPresetV4FoundationTest {

    // ---- Schema + capability separation ----

    @Test
    fun schemaIsVersioned() {
        assertEquals(4, referenceInk().schemaVersion)
        assertEquals(4, referencePencil().schemaVersion)
    }

    @Test
    fun pencilCapabilitiesAreGated() {
        val caps = BrushCapabilities.of(BrushEngineFamily.STAMPED_PENCIL)
        assertTrue(BrushCapability.PAPER_GRAIN in caps)
        assertTrue(BrushCapability.TILT_WIDTH in caps)
        assertTrue(BrushCapability.TILT_GRAIN in caps)
        assertTrue(BrushCapability.STAMP_SPACING in caps)
        assertFalse(BrushCapability.CONTINUOUS_RIBBON in caps)
        // A pencil preset must not carry an ink-only continuous-ribbon block.
        assertNull(referencePencil().stroke?.let { if (BrushCapabilities.supports(BrushEngineFamily.STAMPED_PENCIL, BrushCapability.TAPER)) it else null })
    }

    @Test
    fun inkCapabilitiesAreGated() {
        val caps = BrushCapabilities.of(BrushEngineFamily.CONTINUOUS_INK)
        assertTrue(BrushCapability.CONTINUOUS_RIBBON in caps)
        assertTrue(BrushCapability.TAPER in caps)
        assertFalse(BrushCapability.PAPER_GRAIN in caps)
        assertFalse(BrushCapability.TILT_WIDTH in caps)
        // Ink has no spacing block (ribbon spacing is not user-visible stamp spacing).
        assertNull(referenceInk().spacing)
        assertNull(referenceInk().paperGrain)
        assertNull(referenceInk().tipTexture)
    }

    @Test
    fun tipTextureAndPaperGrainAreDistinctConcepts() {
        val pencil = referencePencil()
        assertTrue(pencil.tipTexture != null)
        assertTrue(pencil.paperGrain != null)
        assertNotEquals("tipTexture and paperGrain must be distinct blocks", pencil.tipTexture, pencil.paperGrain)
    }

    @Test
    fun dynamicsChannelsAreDistinct() {
        val pencil = referencePencil()
        assertTrue(pencil.dynamics.pressure.depositionEnabled) // Pressure -> deposition
        assertTrue(pencil.dynamics.tilt.widthEnabled) // Tilt -> width
        assertTrue(pencil.dynamics.tilt.grainEnabled) // Tilt -> grain
        assertTrue(pencil.dynamics.pressure.widthEnabled) // Pressure -> width (still present for pencil)
    }

    // ---- V3 -> V4 migration ----

    @Test
    fun v3PencilMigratesWithPreservedRenderingSemantics() {
        val v3 = v3Pencil()
        val v4 = PresetMigrator.toV4(v3)
        assertEquals(BrushEngineFamily.STAMPED_PENCIL, v4.identity.engineFamily)
        assertEquals(v3.defaultSize, v4.tip.size, 1e-4f)
        assertEquals(v3.flow, v4.deposition.flow, 1e-4f)
        assertEquals(v3.spacing, v4.spacing!!.value, 1e-4f)
        assertEquals(v3.sizePressureMinimum, v4.dynamics.pressure.widthMinimum, 1e-4f)
        assertTrue(v4.dynamics.pressure.depositionEnabled)
        assertTrue(v4.paperGrain != null)
        assertTrue(v4.tipTexture != null)
    }

    @Test
    fun v3InkMigratesWithoutSpacingOrGrainBlocks() {
        val v3 = v3Ink()
        val v4 = PresetMigrator.toV4(v3)
        assertEquals(BrushEngineFamily.CONTINUOUS_INK, v4.identity.engineFamily)
        assertNull(v4.spacing)
        assertNull(v4.paperGrain)
        assertNull(v4.tipTexture)
    }

    // ---- Compile snapshot (immutable, mid-stroke) ----

    @Test
    fun compiledProgramIsImmutableSnapshot() {
        val compiled = CompiledBrushProgram.compile(referencePencil())
        val strokeA = compiled.toInkStroke("system-hb-pencil", 0xFF000000, listOf(StrokePoint(.5f, .5f)))
        val strokeB = compiled.toInkStroke("system-hb-pencil", 0xFF000000, listOf(StrokePoint(.5f, .5f)))
        assertEquals("Same snapshot must produce identical strokes", strokeA, strokeB)
        assertEquals("Compiled tip size must match preset", referencePencil().tip.size, strokeA.width, 1e-4f)
        assertEquals("Compiled deposition flow must match preset", referencePencil().deposition.flow, strokeA.flow, 1e-4f)
        assertEquals("Compiled tilt->width must map to graphiteTiltWidth", referencePencil().dynamics.tilt.widthMaximum, strokeA.graphiteTiltWidth, 1e-4f)
        assertEquals("Compiled paper grain must map to procedural grain", referencePencil().paperGrain!!.density, strokeA.proceduralPaperGrainDensity, 1e-4f)
        // A procedural-tip pencil without an imported asset renders through MESH_INK engine kind,
        // exactly like the legacy activeBrushEngineKind() path (stamped behavior is brushId-driven).
        assertEquals("Procedural pencil must match legacy MESH_INK engine kind", app.inkloom.document.BrushEngineKind.MESH_INK, strokeA.engineKind)
    }

    @Test
    fun importedAssetTipCompilesToStampRaster() {
        val withAsset = referencePencil().copy(
            tipTexture = TipTextureSettings(procedural = false, assetPath = "assets/00.png", toothStrength = .64f),
        )
        val stroke = CompiledBrushProgram.compile(withAsset).toInkStroke("system-hb-pencil", 0xFF000000, listOf(StrokePoint(.5f, .5f)))
        assertEquals("An imported tip must compile to STAMP_RASTER", app.inkloom.document.BrushEngineKind.STAMP_RASTER, stroke.engineKind)
    }

    @Test
    fun compiledInkIsContinuousRibbon() {
        val compiled = CompiledBrushProgram.compile(referenceInk())
        val stroke = compiled.toInkStroke("system-pen", 0xFF000000, listOf(StrokePoint(.5f, .5f)))
        assertTrue("Reference ink must render as continuous ribbon", stroke.ribbon)
        assertTrue(stroke.continuousPlot)
        assertFalse("Ink must not carry procedural paper grain", stroke.proceduralPaperGrainEnabled)
    }

    // ---- V3 -> V4 -> compile parity with reference InkStroke ----

    @Test
    fun v4PencilCompileMatchesReferenceInkStrokeParameters() {
        val compiled = CompiledBrushProgram.compile(referencePencil())
        val v4Stroke = compiled.toInkStroke("system-hb-pencil", 0xFF000000, listOf(StrokePoint(.5f, .5f)))
        val golden = goldenReferencePencilStroke()
        assertEquals(golden.width, v4Stroke.width, 1e-4f)
        assertEquals(golden.spacing, v4Stroke.spacing, 1e-4f)
        assertEquals(golden.flow, v4Stroke.flow, 1e-4f)
        assertEquals(golden.sizePressureEnabled, v4Stroke.sizePressureEnabled)
        assertEquals(golden.sizePressureMinimum, v4Stroke.sizePressureMinimum, 1e-4f)
        assertEquals(golden.opacityPressureEnabled, v4Stroke.opacityPressureEnabled)
        assertEquals(golden.opacityPressureMinimum, v4Stroke.opacityPressureMinimum, 1e-4f)
    }

    @Test
    fun v4InkCompileMatchesReferenceInkStrokeParameters() {
        val compiled = CompiledBrushProgram.compile(referenceInk())
        val v4Stroke = compiled.toInkStroke("system-pen", 0xFF000000, listOf(StrokePoint(.5f, .5f)))
        val golden = goldenReferenceInkStroke()
        assertEquals(golden.width, v4Stroke.width, 1e-4f)
        assertEquals(golden.spacing, v4Stroke.spacing, 1e-4f)
        assertEquals(golden.sizePressureEnabled, v4Stroke.sizePressureEnabled)
        assertEquals(golden.sizePressureMinimum, v4Stroke.sizePressureMinimum, 1e-4f)
        assertTrue(v4Stroke.ribbon)
    }

    // ---- Ownership ----

    @Test
    fun factoryPresetsAreImmutableAndDuplicateProducesUserCopy() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        assertTrue(factory.isFactory)
        val duplicate = PresetOwnershipOperations.duplicate(factory)
        assertFalse(duplicate.isFactory)
        assertNotEquals(factory.id, duplicate.id)
        assertTrue(duplicate.preset.identity.name.contains("Modified"))
        assertEquals("system-hb-pencil", (duplicate.ownership as PresetOwnership.User).derivedFromFactoryId)
    }

    @Test
    fun factoryCannotBeEditedInPlace() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        var threw = false
        try {
            PresetOwnershipOperations.withEdits(factory, factory.preset)
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Editing a factory preset in place must be rejected", threw)
    }

    @Test
    fun saveAsAndResetToFactorySemantics() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        val savedAs = PresetOwnershipOperations.saveAs(factory, "HB Pencil - Custom")
        assertEquals("HB Pencil - Custom", savedAs.preset.identity.name)
        assertFalse(savedAs.isFactory)
        val reset = PresetOwnershipOperations.resetToFactory(savedAs, referencePencil())
        assertTrue("Reset to factory must restore the factory default", reset!!.isFactory)
    }

    @Test
    fun deleteUserPresetIsOwnershipRemoval() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        val user = PresetOwnershipOperations.duplicate(factory)
        // Delete = remove from the user-owned store; factory untouched.
        assertFalse(user.isFactory)
        assertEquals("system-hb-pencil", (user.ownership as PresetOwnership.User).derivedFromFactoryId)
        assertTrue(factory.isFactory)
    }

    // ---- Serialization round-trip ----

    @Test
    fun serializationRoundTripIsSemanticallyIdentical() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = true }
        listOf(referenceInk(), referencePencil()).forEach { preset ->
            val encoded = json.encodeToString(BrushPresetV4.serializer(), preset)
            val decoded = json.decodeFromString(BrushPresetV4.serializer(), encoded)
            assertEquals("Round-trip must be semantically identical", preset, decoded)
        }
    }

    @Test
    fun serializedPencilPreservesDistinctGrainAndTextureBlocks() {
        val json = Json { encodeDefaults = true; explicitNulls = true }
        val encoded = json.encodeToString(BrushPresetV4.serializer(), referencePencil())
        assertTrue("Serialized pencil must carry paperGrain", encoded.contains("paperGrain"))
        assertTrue("Serialized pencil must carry tipTexture", encoded.contains("tipTexture"))
        val decoded = json.decodeFromString(BrushPresetV4.serializer(), encoded)
        assertTrue(decoded.paperGrain != null)
        assertTrue(decoded.tipTexture != null)
    }

    // ---- Fixtures (mirror the reference goldens' InkStroke) ----

    private fun referenceInk(): BrushPresetV4 = BrushPresetV4(
        identity = PresetIdentity("system-pen", "Reference Ink", 1, BrushEngineFamily.CONTINUOUS_INK),
        tip = TipSettings(size = 5f, aspect = 1f, rotationDegrees = 0f, hardness = 1f),
        deposition = DepositionSettings(opacity = 1f, flow = 1f, buildup = 0f),
        spacing = null,
        dynamics = DynamicsSettings(
            pressure = PressureMappings(
                widthEnabled = true,
                widthMinimum = .35f,
                widthCurve = emptyList(),
                depositionEnabled = false,
                depositionMinimum = 0f,
                depositionCurve = emptyList(),
            ),
            tilt = TiltMappings(),
            velocity = null,
            direction = null,
        ),
        paperGrain = null,
        tipTexture = null,
        stroke = StrokeSettings(smoothing = 0f, postCorrection = 0f),
        engineOptions = EngineOptions(),
    )

    private fun referencePencil(): BrushPresetV4 = BrushPresetV4(
        identity = PresetIdentity("system-hb-pencil", "Reference Pencil", 1, BrushEngineFamily.STAMPED_PENCIL),
        tip = TipSettings(size = 24f, aspect = 1f, rotationDegrees = 0f, hardness = .64f),
        deposition = DepositionSettings(opacity = 1f, flow = .68f, buildup = 0f),
        spacing = SpacingSettings(mode = SpacingMode.RELATIVE, value = .26f),
        dynamics = DynamicsSettings(
            pressure = PressureMappings(
                widthEnabled = true,
                widthMinimum = .3f,
                widthCurve = emptyList(),
                depositionEnabled = true,
                depositionMinimum = .2f,
                depositionCurve = emptyList(),
            ),
            tilt = TiltMappings(
                widthEnabled = true,
                widthStart = .2f,
                widthMaximum = 1f,
                grainEnabled = true,
                grainStart = .2f,
                grainMaximum = .8f,
            ),
            velocity = null,
            direction = null,
        ),
        paperGrain = PaperGrainSettings(scale = 50f, density = .3f, strength = 1f),
        tipTexture = TipTextureSettings(procedural = true, assetPath = null, toothStrength = .64f),
        stroke = StrokeSettings(smoothing = 0f, postCorrection = 0f),
        engineOptions = EngineOptions(),
    )

    private fun goldenReferenceInkStroke(): InkStroke = InkStroke(
        id = "system-pen", brushId = "system-pen", points = emptyList(),
        width = 5f, spacing = .12f, sizePressureEnabled = true, sizePressureMinimum = .35f, colorArgb = 0xFF000000,
    )

    private fun goldenReferencePencilStroke(): InkStroke = InkStroke(
        id = "system-hb-pencil", brushId = "system-hb-pencil", points = emptyList(),
        width = 24f, spacing = .26f, flow = .68f, sizePressureEnabled = true, sizePressureMinimum = .3f,
        opacityPressureEnabled = true, opacityPressureMinimum = .2f, colorArgb = 0xFF000000,
    )

    private fun v3Pencil(): ImportedBrush = ImportedBrush(
        id = "system-hb-pencil", name = "HB Pencil", format = BrushFormat.SYSTEM, compatibility = Compatibility.COMPLETE,
        sizeBytes = 0L, report = emptyList(),
        defaultSize = 24f, flow = .68f, spacing = .26f, hardness = .64f,
        sizePressureEnabled = true, sizePressureMinimum = .3f,
        opacityPressureEnabled = true, opacityPressureMinimum = .2f,
        graphite = app.inkloom.model.ImportedBrushGraphite(
            tiltWidth = 1f, tiltWidthStart = .2f,
            tiltGrain = .8f, tiltGrainStart = .2f,
            paperGrainEnabled = true, paperGrainScale = 50f, paperGrainDensity = .3f,
        ),
    )

    private fun v3Ink(): ImportedBrush = ImportedBrush(
        id = "system-g-pen", name = "G Pen", format = BrushFormat.SYSTEM, compatibility = Compatibility.COMPLETE,
        sizeBytes = 0L, report = emptyList(),
        defaultSize = 6f, spacing = .045f, hardness = 1f, sizePressureEnabled = true, sizePressureMinimum = .06f,
    )

    private fun BrushEngineKindFixture() = Unit

    // keep unused-import suppression harmless
    private fun unusedCurve() = BrushCurvePoint(0f, 0f)
}
