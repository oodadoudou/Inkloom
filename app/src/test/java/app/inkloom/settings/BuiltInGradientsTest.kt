package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInGradientsTest {
    private val main = 0xFF112233.toInt()
    private val secondary = 0xFFAABBCC.toInt()

    @Test fun theColourSubToolsFollowTheCurrentColours() {
        val presets = BuiltInGradients.all(main, secondary).associateBy { it.id }
        val toSecondary = presets.getValue("builtin:main-to-secondary").definition.stops
        assertEquals(0xFF112233L, toSecondary.first().colorArgb)
        assertEquals(0xFFAABBCCL, toSecondary.last().colorArgb)
        val toTransparent = presets.getValue("builtin:main-to-transparent").definition.stops
        assertEquals(0xFF112233L, toTransparent.first().colorArgb)
        assertEquals("Same colour, fully transparent", 0x00112233L, toTransparent.last().colorArgb)
    }

    @Test fun everyBuiltInIsAValidPresetAndCanBeSavedToTheUserLibrary() {
        val presets = BuiltInGradients.all(main, secondary)
        assertEquals(presets.size, presets.map { it.id }.distinct().size)
        assertTrue(presets.all { BuiltInGradients.isBuiltIn(it.id) })
        // The user library validates names, stop counts and positions; every built-in passes.
        val library = presets.fold(GradientPresetLibrary()) { acc, preset -> acc.add(preset.name, preset.definition) }
        assertEquals(presets.size, library.presets.size)
        assertTrue(library.presets.none { BuiltInGradients.isBuiltIn(it.id) })
        presets.forEach { preset ->
            val stops = preset.definition.stops
            assertEquals(0f, stops.first().position, 0f); assertEquals(1f, stops.last().position, 0f)
            assertTrue(stops.zipWithNext().all { (a, b) -> a.position <= b.position })
        }
    }
}
