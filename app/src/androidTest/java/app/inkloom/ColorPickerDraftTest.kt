package app.inkloom

import android.graphics.Color
import app.inkloom.ui.ColorPickerDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorPickerDraftTest {
    @Test fun blackRetainsBothHiddenCoordinatesAndAlpha() {
        val draft = ColorPickerDraft.fromColor(0x7f000000).withHsv(hue = 213f, saturation = .75f)
        assertEquals(0x7f000000, draft.color)
        val visible = draft.withHsv(value = .8f)
        assertEquals(213f, visible.hue, 0f)
        assertEquals(.75f, visible.saturation, 0f)
        assertEquals(127, Color.alpha(visible.color))
        val hsv = FloatArray(3).also { Color.colorToHSV(visible.color, it) }
        assertEquals(213f, hsv[0], 1f)
    }

    @Test fun externalRgbOnlyReplacesDefinedCoordinates() {
        val draft = ColorPickerDraft.fromColor(Color.BLUE).withHsv(hue = 217.25f, saturation = .63f)
        val black = draft.withColor(Color.BLACK)
        assertEquals(draft.hue, black.hue, 0f)
        assertEquals(draft.saturation, black.saturation, 0f)
        val gray = black.withColor(Color.GRAY)
        assertEquals(draft.hue, gray.hue, 0f)
        assertEquals(0f, gray.saturation, 0f)
        val green = gray.withColor(Color.GREEN)
        assertEquals(120f, green.hue, 0f)
        assertEquals(1f, green.saturation, 0f)
    }

    @Test fun committedRgbEchoDoesNotQuantizePickerCoordinates() {
        val draft = ColorPickerDraft.fromColor(Color.BLACK).withHsv(359.8f, .002f, .003f)
        assertEquals(draft, draft.withColor(draft.color))
        val redEnd = draft.withHsv(hue = 360f)
        assertEquals(360f, redEnd.withColor(redEnd.color).hue, 0f)
    }
}
