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

    private fun inkPreset() = BrushPresetV5(
        identity = PresetIdentity("editor-capability", "Editor capability", family = BrushFamily.INK, renderBrushId = "editor-capability"),
        renderSeed = 42,
        tip = TipSettings(size = 12f),
        deposition = DepositionSettings(),
        dynamics = DynamicsSettings(PressureMappings(), TiltMappings()),
        stroke = StrokeSettings(),
    )
}
