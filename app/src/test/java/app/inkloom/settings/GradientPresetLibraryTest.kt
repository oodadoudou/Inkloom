package app.inkloom.settings

import app.inkloom.document.GradientColorStop
import app.inkloom.document.GradientDefinition
import app.inkloom.document.GradientEdgeMode
import app.inkloom.document.GradientKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientPresetLibraryTest {
    private val definition = GradientDefinition(kind = GradientKind.ELLIPSE, edgeMode = GradientEdgeMode.MIRROR,
        reverse = true, dither = false, ellipseRatio = .42f, stops = listOf(
            GradientColorStop(0f, 0x00FF0000L), GradientColorStop(.4f, 0x88FFFFFFL),
            GradientColorStop(1f, 0xFF0000FFL)))

    @Test fun savedGradientIsIndependentOfCurrentBarAndSurvivesBackup() {
        val saved = GradientPresetLibrary().add("Ink wash", definition, "wash")
        val edited = saved.replace("wash", definition.copy(kind = GradientKind.RADIAL))
        assertEquals(GradientKind.ELLIPSE, saved.presets.single().definition.kind)
        assertEquals(GradientKind.RADIAL, edited.presets.single().definition.kind)
        val settings = AppSettings(gradientPresets = saved)
        val restored = AppSettingsBackupCodec.decode(AppSettingsBackupCodec.encode(settings)).getOrThrow().settings
        assertEquals(saved, restored.gradientPresets)
        assertEquals(0x00FF0000L, restored.gradientPresets.presets.single().definition.stops.first().colorArgb)
    }

    @Test fun importAppendsWithNewIdsAndRejectsMalformedOrOversizedFiles() {
        val original = GradientPresetLibrary().add("Ink wash", definition, "same")
        val decoded = GradientPresetLibraryCodec.decode(GradientPresetLibraryCodec.encode(original)).getOrThrow()
        val appended = original.append(decoded)
        assertEquals(2, appended.presets.size)
        assertNotEquals(appended.presets[0].id, appended.presets[1].id)
        assertEquals(appended.presets[0].definition, appended.presets[1].definition)
        assertTrue(GradientPresetLibraryCodec.decode("{bad".encodeToByteArray()).isFailure)
        assertTrue(GradientPresetLibraryCodec.decode(ByteArray(GradientPresetLibraryCodec.MAX_BYTES + 1)).isFailure)
    }

    @Test fun renameDuplicateAndDeletePreserveOtherPresets() {
        val original = GradientPresetLibrary().add("A", definition, "a").add("B", definition, "b")
        val changed = original.rename("a", "Line art").duplicate("a", "Line art copy").remove("b")
        assertEquals(listOf("Line art", "Line art copy"), changed.presets.map { it.name })
        assertEquals(2, original.presets.size)
    }
}
