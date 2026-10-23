package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.*
import org.junit.Test

class LineArtColorizeTest {
    private val size = 120
    private fun surface(paint: (Int, Int) -> Int) = PixelTileSurface(size, size).also { s ->
        val pixels = IntArray(size * size) { i -> paint(i % size, i / size) }
        s.beginWrite("fixture").run { replacePremultipliedPixels(PixelRect(0, 0, size, size), pixels); commit().close() }
    }.snapshot()

    // Two boxes side by side; the right one has a 2-pixel gap in its left wall... closed by gap closing.
    private val lines = surface { x, y ->
        val box = (y in 20..100) && (x in 10..55 || x in 64..110)
        val outline = box && (y == 20 || y == 100 || x == 10 || x == 55 || x == 64 || x == 110)
        if (outline && !(x == 64 && y in 59..60)) 0xFF000000.toInt() else 0
    }
    private val red = 0xFFE04040.toInt(); private val blue = 0xFF4060E0.toInt()

    @Test fun hintsFillTheRegionsTheyTouch() {
        val hint = surface { x, y -> if (x in 25..30 && y in 50..55) red else 0 }
        val out = LineArtColorize.render(lines, hint, ColorizeSettings())
        assertEquals("the hinted box is filled", red or 0xFF000000.toInt(), quantisedAt(out, 40, 80))
        assertEquals("the other box stays clear", 0, out[80 * size + 90])
        assertEquals("outside stays clear", 0, out[5 * size + 5])
        assertNotEquals("the colour runs under the line", 0, out[60 * size + 10])
    }

    @Test fun gapClosingKeepsColourInsideALeakyOutline() {
        val hint = surface { x, y -> if (x in 85..90 && y in 50..55) blue else 0 }
        val closed = LineArtColorize.render(lines, hint, ColorizeSettings(gapClosing = 2))
        assertEquals("the leak is closed", 0, closed[60 * size + 59])
        val open = LineArtColorize.render(lines, hint, ColorizeSettings(gapClosing = 0, fillUnderLines = false))
        assertNotEquals("without gap closing the colour leaks out", 0, open[60 * size + 59])
    }

    @Test fun priorityDecidesBetweenSeveralHints() {
        val hint = surface { x, y -> if (x in 20..40 && y in 40..60) red else if (x in 42..44 && y in 40..42) blue else 0 }
        val majority = LineArtColorize.render(lines, hint, ColorizeSettings())
        assertEquals(red, quantisedAt(majority, 30, 30))
        val prioritised = LineArtColorize.render(lines, hint, ColorizeSettings(analyzeColors = false, priority = listOf(blue, red)))
        assertEquals(quantised(blue), quantisedAt(prioritised, 30, 30))
    }

    @Test fun unhintedRegionsCanTakeTheNearestHint() {
        val hint = surface { x, y -> if (x in 25..30 && y in 50..55) red else 0 }
        val out = LineArtColorize.render(lines, hint, ColorizeSettings(fillUnhinted = true))
        assertNotEquals(0, out[80 * size + 90])
    }

    @Test fun colorizeAllUsesThePaletteButLeavesTheBackground() {
        val out = LineArtColorize.render(lines, null, ColorizeSettings(palette = LineArtColorize.palette(red)))
        assertNotEquals(0, out[60 * size + 30]); assertNotEquals(0, out[60 * size + 90])
        assertNotEquals("the two boxes differ", out[60 * size + 30], out[60 * size + 90])
        assertEquals("the background stays clear", 0, out[5 * size + 5])
        assertEquals(8, LineArtColorize.palette(red).distinct().size)
    }

    @Test fun hintColoursAreListedByArea() {
        val hint = surface { x, y -> if (x < 60 && y < 30) red else if (x > 100 && y > 110) blue else 0 }
        assertEquals(listOf(quantised(red), quantised(blue)), LineArtColorize.hintColors(hint))
    }

    private fun quantised(argb: Int): Int {
        fun q(v: Int) = ((v + 8) / 16 * 16).coerceAtMost(255)
        return (0xFF shl 24) or (q(argb ushr 16 and 255) shl 16) or (q(argb ushr 8 and 255) shl 8) or q(argb and 255)
    }
    private fun quantisedAt(pixels: IntArray, x: Int, y: Int) = pixels[y * size + x]
}
