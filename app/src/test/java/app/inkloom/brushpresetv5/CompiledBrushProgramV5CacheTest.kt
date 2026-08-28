package app.inkloom.brushpresetv5

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CompiledBrushProgramV5CacheTest {
    private val preset = BrushPresetV5(
        identity = PresetIdentity("cache", "Cache", family = BrushFamily.INK, renderBrushId = "cache"),
        renderSeed = 7,
        tip = TipSettings(),
        deposition = DepositionSettings(),
        dynamics = DynamicsSettings(PressureMappings(), TiltMappings()),
    )

    @Test fun `unchanged encoded selection reuses the exact compiled program`() {
        val cache = CompiledBrushProgramV5Cache()
        val encoded = BrushPresetV5Codec.encode(preset)

        assertSame(cache.resolveEncoded(encoded), cache.resolveEncoded(encoded))
    }

    @Test fun `unchanged native selection reuses the exact compiled program`() {
        val cache = CompiledBrushProgramV5Cache()

        assertSame(cache.resolvePreset(preset), cache.resolvePreset(preset.copy()))
    }

    @Test fun `invalid or cleared payload cannot retain a previous executable`() {
        val cache = CompiledBrushProgramV5Cache()
        cache.resolvePreset(preset)

        assertNull(cache.resolveEncoded("{\"schemaVersion\":4}"))
        assertNull(cache.resolveEncoded(null))
    }
}
