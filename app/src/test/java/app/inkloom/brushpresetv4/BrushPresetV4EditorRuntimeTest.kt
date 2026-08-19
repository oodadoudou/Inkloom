package app.inkloom.brushpresetv4

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushDynamics
import app.inkloom.model.BrushFormat
import app.inkloom.model.ImportedBrush
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BRUSH_EDITOR_V1 runtime tests: WorkingCopy session semantics, capability-driven descriptors,
 * pressure curve presets (using the production evaluator), V4->V3 reverse migration, and user
 * preset persistence round-trip.
 */
class BrushPresetV4EditorRuntimeTest {

    // ---- EditorWorkingCopy ----

    @Test
    fun cancelReturnsSourceUnchanged() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        val session = EditorWorkingCopy.start(factory)
        val edited = session.update { it.copy(tip = it.tip.copy(size = 40f)) }
        assertEquals("Edit must be visible in the session", 40f, edited.current.tip.size, 1e-4f)
        assertEquals("Source must stay unchanged while editing", 24f, factory.preset.tip.size, 1e-4f)
        val cancelled = edited.cancel()
        assertEquals("Cancel must return the original preset untouched", factory, cancelled)
    }

    @Test
    fun factoryCommitIsSaveAsOnly() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        val session = EditorWorkingCopy.start(factory).update { it.copy(tip = it.tip.copy(size = 40f)) }
        val saved = session.commit("My Pencil")
        assertFalse("Committing a factory must produce a user copy", saved.isFactory)
        assertEquals("My Pencil", saved.preset.identity.name)
        assertEquals("Edit must be applied to the new copy", 40f, saved.preset.tip.size, 1e-4f)
        assertEquals("Factory must remain untouched", 24f, factory.preset.tip.size, 1e-4f)
        assertEquals("Render brush id must survive", "system-hb-pencil", saved.preset.identity.renderBrushId)
        assertEquals("Seed must survive", factory.preset.renderSeed, saved.preset.renderSeed)
        assertEquals("Lineage must be tracked", "system-hb-pencil", (saved.ownership as PresetOwnership.User).derivedFromFactoryId)
    }

    @Test
    fun userCommitSavesInPlace() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        val user = PresetOwnershipOperations.duplicate(factory)
        val session = EditorWorkingCopy.start(user).update { it.copy(deposition = it.deposition.copy(flow = .5f)) }
        val saved = session.commit()
        assertFalse(saved.isFactory)
        assertEquals("In-place save must keep the user id", user.id, saved.id)
        assertEquals(.5f, saved.preset.deposition.flow, 1e-4f)
    }

    @Test
    fun userCommitAsNewPreservesSeedAndRenderBrushId() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        val user = PresetOwnershipOperations.duplicate(factory)
        val session = EditorWorkingCopy.start(user).update { it.copy(tip = it.tip.copy(hardness = .9f)) }
        val asNew = session.commitAsNew("HB - Dark")
        assertNotEquals("Save As must create a new identity", user.id, asNew.id)
        assertEquals("HB - Dark", asNew.preset.identity.name)
        assertEquals("renderBrushId must survive Save As", "system-hb-pencil", asNew.preset.identity.renderBrushId)
        assertEquals("seed must survive Save As", user.preset.renderSeed, asNew.preset.renderSeed)
    }

    @Test
    fun compiledSnapshotIsImmutableAcrossSessionEdits() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        var session = EditorWorkingCopy.start(factory)
        val before = session.compiled()
        val beforeStroke = before.toInkStroke(0xFF000000, emptyList())
        session = session.update { it.copy(tip = it.tip.copy(size = 60f)) }
        val afterStroke = session.compiled().toInkStroke(0xFF000000, emptyList())
        assertNotEquals("A later edit must produce a new compiled width", beforeStroke.width, afterStroke.width)
        assertEquals("The old snapshot must keep its frozen width", beforeStroke.width, before.tip.size, 1e-4f)
    }

    // ---- Capability-driven descriptors ----

    @Test
    fun descriptorsAreCapabilityDriven() {
        val pencilOptions = BrushOptionDescriptors.forFamily(BrushFamily.PENCIL)
        val inkOptions = BrushOptionDescriptors.forFamily(BrushFamily.INK)
        val pencilIds = pencilOptions.map { it.id }.toSet()
        val inkIds = inkOptions.map { it.id }.toSet()
        // Common basics for both.
        assertTrue(pencilIds.containsAll(listOf("size", "opacity", "flow")))
        assertTrue(inkIds.containsAll(listOf("size", "opacity", "flow")))
        // Pencil: spacing, pressure, tilt, paper.
        assertTrue("spacing" in pencilIds)
        assertTrue("tiltWidthEnabled" in pencilIds)
        assertTrue("tiltGrainEnabled" in pencilIds)
        assertTrue("paperGrainDensity" in pencilIds)
        // Ink must NOT show pencil-only options (no spacing/tilt/paper).
        assertFalse("spacing" in inkIds)
        assertFalse("tiltWidthEnabled" in inkIds)
        assertFalse("paperGrainDensity" in inkIds)
    }

    @Test
    fun descriptorGetSetRoundTrips() {
        val ink = referenceInk()
        val sizeDescriptor = BrushOptionDescriptors.option("size")
        assertEquals(5f, sizeDescriptor.get(ink), 1e-4f)
        assertEquals(32f, sizeDescriptor.get(sizeDescriptor.set(ink, 32f)), 1e-4f)
        val flowDescriptor = BrushOptionDescriptors.option("flow")
        assertEquals(1f, flowDescriptor.get(ink), 1e-4f)
        assertEquals(.4f, flowDescriptor.get(flowDescriptor.set(ink, .4f)), 1e-4f)
    }

    @Test
    fun panelsGroupByCapability() {
        val pencilPanels = BrushOptionDescriptors.panels(BrushFamily.PENCIL).map { it.first }
        assertTrue(BrushOptionGroup.BASIC in pencilPanels)
        assertTrue(BrushOptionGroup.SPACING in pencilPanels)
        assertTrue(BrushOptionGroup.TILT in pencilPanels)
        assertTrue(BrushOptionGroup.PAPER in pencilPanels)
        val inkPanels = BrushOptionDescriptors.panels(BrushFamily.INK).map { it.first }
        assertTrue(BrushOptionGroup.BASIC in inkPanels)
        assertFalse("Ink must have no paper panel", BrushOptionGroup.PAPER in inkPanels)
        assertFalse("Ink must have no spacing panel", BrushOptionGroup.SPACING in inkPanels)
        assertFalse("Ink must have no tilt panel", BrushOptionGroup.TILT in inkPanels)
    }

    // ---- Pressure curves (production evaluator) ----

    @Test
    fun linearCurveIsIdentityThroughProductionEvaluator() {
        val points = EditorCurves.controlPoints(PressureCurveShape.LINEAR)
        assertTrue(points.isEmpty())
        // Production BrushDynamics.curve on empty points is identity.
        assertEquals(.5f, EditorCurves.evaluate(points, .5f), 1e-4f)
        assertEquals(.2f, EditorCurves.evaluate(points, .2f), 1e-4f)
    }

    @Test
    fun softAndHardCurvesDivergeFromLinear() {
        val soft = EditorCurves.controlPoints(PressureCurveShape.SOFT)
        val hard = EditorCurves.controlPoints(PressureCurveShape.HARD)
        // Soft: early pressure produces more size/opacity than hard.
        assertTrue(EditorCurves.evaluate(soft, .3f) > EditorCurves.evaluate(hard, .3f))
        // Both pass through (0,0) and (1,1).
        assertEquals(0f, EditorCurves.evaluate(soft, 0f), 1e-3f)
        assertEquals(1f, EditorCurves.evaluate(soft, 1f), 1e-3f)
        assertEquals(0f, EditorCurves.evaluate(hard, 0f), 1e-3f)
        assertEquals(1f, EditorCurves.evaluate(hard, 1f), 1e-3f)
    }

    @Test
    fun curveShapeDetection() {
        assertEquals(PressureCurveShape.LINEAR, EditorCurves.shapeOf(emptyList()))
        assertEquals(PressureCurveShape.SOFT, EditorCurves.shapeOf(EditorCurves.controlPoints(PressureCurveShape.SOFT)))
        assertEquals(PressureCurveShape.HARD, EditorCurves.shapeOf(EditorCurves.controlPoints(PressureCurveShape.HARD)))
        assertEquals(PressureCurveShape.CUSTOM, EditorCurves.shapeOf(listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f))))
    }

    @Test
    fun pressureFactorMatchesProductionFormula() {
        val points = EditorCurves.controlPoints(PressureCurveShape.HARD)
        val factor = EditorCurves.pressureFactor(minimum = .3f, points = points, input = .5f)
        val expected = BrushDynamics.pressure(enabled = true, minimum = .3f, curve = points, input = .5f)
        assertEquals("Editor preview must use the production pressure formula", expected, factor, 1e-5f)
        // At full pressure the factor reaches 1.
        assertEquals(1f, EditorCurves.pressureFactor(.3f, points, 1f), 1e-3f)
    }

    // ---- V4 -> V3 reverse migration ----

    @Test
    fun reverseMigrationCarriesReferenceSemantics() {
        val v4 = referencePencil()
        val v3 = PresetReverseMigrator.toV3(v4)
        assertEquals("system-hb-pencil", v3.id)
        assertEquals(v4.tip.size, v3.defaultSize, 1e-4f)
        assertEquals(v4.deposition.flow, v3.flow, 1e-4f)
        assertEquals(v4.tip.hardness, v3.hardness, 1e-4f)
        assertEquals(v4.tip.aspect, v3.thickness, 1e-4f)
        assertEquals(v4.spacing!!.value, v3.spacing, 1e-4f)
        assertEquals(v4.dynamics.pressure.widthEnabled, v3.sizePressureEnabled)
        assertEquals(v4.dynamics.pressure.widthMinimum, v3.sizePressureMinimum, 1e-4f)
        assertEquals(v4.dynamics.pressure.depositionEnabled, v3.opacityPressureEnabled)
        assertEquals(v4.dynamics.pressure.depositionMinimum, v3.opacityPressureMinimum, 1e-4f)
        assertEquals(v4.dynamics.tilt.widthMaximum, v3.graphite.tiltWidth, 1e-4f)
        assertEquals(v4.dynamics.tilt.grainMaximum, v3.graphite.tiltGrain, 1e-4f)
        assertEquals(true, v3.graphite.paperGrainEnabled)
        assertEquals(v4.paperGrain!!.scale, v3.graphite.paperGrainScale, 1e-4f)
        assertEquals(v4.paperGrain!!.density, v3.graphite.paperGrainDensity, 1e-4f)
    }

    @Test
    fun reverseMigrationRoundTripsThroughMigrator() {
        // V4 -> V3 -> V4 must preserve the user-configurable semantics.
        val v4 = referencePencil()
        val v3 = PresetReverseMigrator.toV3(v4)
        val v4again = PresetMigrator.toV4(v3)
        assertEquals(v4.identity.renderBrushId, v4again.identity.renderBrushId)
        assertEquals(v4.tip.size, v4again.tip.size, 1e-4f)
        assertEquals(v4.tip.hardness, v4again.tip.hardness, 1e-4f)
        assertEquals(v4.deposition.flow, v4again.deposition.flow, 1e-4f)
        assertEquals(v4.dynamics.pressure.widthEnabled, v4again.dynamics.pressure.widthEnabled)
        assertEquals(v4.dynamics.tilt.widthEnabled, v4again.dynamics.tilt.widthEnabled)
        assertEquals(v4.dynamics.tilt.grainEnabled, v4again.dynamics.tilt.grainEnabled)
    }

    @Test
    fun reverseMigratedInkStaysContinuousRibbon() {
        val v3 = PresetReverseMigrator.toV3(referenceInk())
        assertTrue("Reverse-migrated ink must render as ribbon", v3.ribbon)
        assertTrue(v3.continuousPlot)
        assertEquals("system-pen", v3.id)
    }

    // ---- User preset persistence round-trip ----

    @Test
    fun presetJsonRoundTripIsSemanticallyIdentical() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = true }
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        val user = PresetOwnershipOperations.duplicate(factory)
        val encoded = json.encodeToString(BrushPresetV4.serializer(), user.preset)
        val decoded = json.decodeFromString(BrushPresetV4.serializer(), encoded)
        assertEquals("User preset must survive JSON round-trip", user.preset, decoded)
    }

    @Test
    fun factorySourceNeverMutatedByEditorChain() {
        val factory = OwnedBrushPreset(referencePencil(), PresetOwnership.Factory("system-hb-pencil"))
        val before = factory.preset
        val session = EditorWorkingCopy.start(factory)
        session.update { it.copy(tip = it.tip.copy(size = 90f)) }
        session.commit("Copy")
        session.commitAsNew("Copy 2")
        assertEquals("Factory preset must never be mutated through the editor", before, factory.preset)
    }

    // ---- Fixtures (mirror reference goldens) ----

    private fun referenceInk(): BrushPresetV4 = BrushPresetV4(
        identity = PresetIdentity("system-pen", "Reference Ink", 1, BrushFamily.INK, "system-pen"),
        renderSeed = "system-pen".hashCode(),
        tip = TipSettings(size = 5f, aspect = 1f, rotationDegrees = 0f, hardness = 1f),
        deposition = DepositionSettings(opacity = 1f, flow = 1f, buildup = 0f),
        spacing = null,
        dynamics = DynamicsSettings(
            pressure = PressureMappings(widthEnabled = true, widthMinimum = .35f, depositionEnabled = false),
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
        identity = PresetIdentity("system-hb-pencil", "Reference Pencil", 1, BrushFamily.PENCIL, "system-hb-pencil"),
        renderSeed = "system-hb-pencil".hashCode(),
        tip = TipSettings(size = 24f, aspect = 1f, rotationDegrees = 0f, hardness = .64f),
        deposition = DepositionSettings(opacity = 1f, flow = .68f, buildup = 0f),
        spacing = SpacingSettings(mode = SpacingMode.RELATIVE, value = .26f),
        dynamics = DynamicsSettings(
            pressure = PressureMappings(
                widthEnabled = true, widthMinimum = .3f,
                depositionEnabled = true, depositionMinimum = .2f,
            ),
            tilt = TiltMappings(
                widthEnabled = true, widthStart = .2f, widthMaximum = 1f,
                grainEnabled = true, grainStart = .2f, grainMaximum = .8f,
            ),
            velocity = null,
            direction = null,
        ),
        paperGrain = PaperGrainSettings(scale = 50f, density = .3f, strength = 1f),
        tipTexture = TipTextureSettings(procedural = true, assetPath = null, toothStrength = .64f),
        stroke = StrokeSettings(smoothing = 0f, postCorrection = 0f),
        engineOptions = EngineOptions(),
    )
}
