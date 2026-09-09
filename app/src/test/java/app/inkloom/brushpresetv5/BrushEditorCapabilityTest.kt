package app.inkloom.brushpresetv5

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushEditorCapabilityTest {
    @Test fun productionPanelsHideEditsRejectedByTheProductionDispatcher() {
        val preset = inkPreset()
        val optionIds = BrushOptionDescriptors.productionPanels(preset)
            .flatMap { it.second }
            .mapTo(linkedSetOf(), BrushOptionDescriptor::id)

        assertTrue("Supported smoothing remains editable", "smoothing" in optionIds)
        assertFalse("Unsupported realtime taper must not be shown", "taperStartEnabled" in optionIds)
        assertFalse("Unsupported realtime taper must not be shown", "taperEndEnabled" in optionIds)
    }

    @Test fun everyVisibleOptionKeepsTheConcretePresetExecutable() {
        val preset = inkPreset()
        val visible = BrushOptionDescriptors.productionPanels(preset).flatMap { it.second }

        visible.forEach { option ->
            val current = option.get(preset)
            val probe = if (current != option.valueRange.endInclusive) option.valueRange.endInclusive
            else option.valueRange.start
            val result = BrushExecutionCapabilityResolver.resolve(
                CompiledBrushProgramV5.compile(option.apply(preset, probe)),
            )
            assertTrue("${option.id} exposed an unsupported production program: $result", result is BrushExecutionCapabilityResolution.Supported)
        }
    }

    @Test fun sourcesRemainDiscoverableWithoutAFamilyShapeParameterPanel() {
        for(family in listOf(BrushFamily.INK,BrushFamily.WET_PAINT)) {
            val source=inkPreset().let { it.copy(identity=it.identity.copy(family=family),wetMix=if(family==BrushFamily.WET_PAINT)WetMixSettings() else null) }
            assertTrue(BrushOptionDescriptors.productionPanels(source).any {it.first==BrushOptionGroup.SOURCES})
        }
    }
    @Test fun nativeNewBrushStartsWithPressureSizeEnabledAndAnExplicitOffSurvivesSave() {
        val record=BrushPresetV5Import.createUserRecord(app.inkloom.model.ImportedBrush(name="New brush",
            format=app.inkloom.model.BrushFormat.INKLOOM,compatibility=app.inkloom.model.Compatibility.COMPLETE,sizeBytes=0,report=emptyList()))
        val preset=BrushPresetV5Import.requirePreset(record)
        assertTrue(record.sizePressureEnabled)
        assertTrue(preset.dynamics.pressure.widthEnabled)
        assertTrue(preset.dynamics.pressure.widthMinimum<1f)
        val disabled=preset.copy(dynamics=preset.dynamics.copy(pressure=preset.dynamics.pressure.copy(widthEnabled=false)))
        assertFalse(BrushPresetV5Import.requirePreset(BrushPresetV5Import.writePreset(record,disabled)).dynamics.pressure.widthEnabled)
    }

    private fun inkPreset() = BrushPresetV5(
        identity = PresetIdentity("editor-capability", "Editor capability", family = BrushFamily.INK, renderBrushId = "editor-capability"),
        renderSeed = 42,
        tip = TipSettings(size = 12f),
        deposition = DepositionSettings(),
        dynamics = DynamicsSettings(PressureMappings(), TiltMappings()),
        stroke = StrokeSettings(),
    )
}
