package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorPaletteCodecTest {
    @Test fun roundTripsSignedArgbValues() {
        val colors = listOf(0xFF24212A.toInt(), 0xFFFFFFFF.toInt(), 0x80235A9F.toInt())
        assertEquals(colors, ColorPaletteCodec.decode(ColorPaletteCodec.encode(colors)))
    }

    @Test fun rejectsMalformedValuesAndDeduplicates() {
        assertEquals(listOf(0xFF000000.toInt()), ColorPaletteCodec.decode("bad,ff000000,ff000000,not-a-color"))
    }

    @Test fun boundsPaletteSize() {
        val colors = (0 until 40).map { 0xFF000000.toInt() or it }
        assertEquals(32, ColorPaletteCodec.decode(ColorPaletteCodec.encode(colors)).size)
    }
}
