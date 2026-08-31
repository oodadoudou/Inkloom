package app.inkloom.brushpresetv5

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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

    @Test fun `A B A switching never returns the stale selected program`() {
        val cache = CompiledBrushProgramV5Cache()
        val a = preset.copy(identity = preset.identity.copy(id = "A", name = "A", renderBrushId = "A"))
        val b = preset.copy(
            identity = preset.identity.copy(id = "B", name = "B", renderBrushId = "B"),
            tip = preset.tip.copy(size = 41f),
        )

        assertEquals("A", cache.resolvePreset(a).identity.id)
        assertEquals("B", cache.resolvePreset(b).identity.id)
        assertEquals("A", cache.resolvePreset(a).identity.id)
    }

    @Test fun `revision and raster semantic edits invalidate the single selection cache`() {
        val cache = CompiledBrushProgramV5Cache()
        val first = cache.resolvePreset(preset)
        val revised = cache.resolvePreset(
            preset.copy(
                identity = preset.identity.copy(version = preset.identity.version + 1),
                deposition = preset.deposition.copy(flow = .31f),
            ),
        )

        assertNotSame(first, revised)
        assertEquals(2, revised.identity.version)
        assertEquals(.31f, revised.deposition.flow)
    }

    @Test fun `one hundred random brush switches preserve exact requested identity`() {
        val cache = CompiledBrushProgramV5Cache()
        val random = kotlin.random.Random(731)
        val presets = List(17) { index ->
            preset.copy(
                identity = preset.identity.copy(id = "random-$index", name = "Random $index", renderBrushId = "random-$index"),
                renderSeed = index * 97,
                tip = preset.tip.copy(size = 2f + index),
                deposition = preset.deposition.copy(flow = .15f + index * .04f),
            )
        }
        repeat(100) {
            val expected = presets[random.nextInt(presets.size)]
            val actual = cache.resolveEncoded(BrushPresetV5Codec.encode(expected))
            assertEquals(expected, actual?.preset)
        }
    }
}
