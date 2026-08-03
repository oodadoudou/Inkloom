package app.inkloom.settings

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ShortcutProfileCodecTest {
    @Test fun `portable profile preserves global and device specific bindings`() {
        val bindings = listOf(
            ShortcutBinding(ShortcutAction.BRUSH_GROUP, ShortcutChord(KeyEvent.KEYCODE_B)),
            ShortcutBinding(
                ShortcutAction.PEN_GROUP,
                ShortcutChord(KeyEvent.KEYCODE_F1, scanCode = 59, matchMode = ShortcutMatchMode.SCAN_CODE),
                ShortcutTrigger.HOLD,
                deviceDescriptor = "usb:056a:0397",
                deviceName = "Wacom Movink Toolbox",
            ),
        )

        assertEquals(bindings, ShortcutProfileCodec.decode(ShortcutProfileCodec.encode(bindings)).getOrThrow())
    }

    @Test fun `portable profile preserves semantic Toolbox brush group targets`() {
        val profile = ShortcutProfile(
            bindings = CspShortcutResolver.defaultBindings,
            brushGroupTargets = ShortcutBrushGroupTargets("Favorites/Pens", "Favorites/Pencils", "Paint/Brushes"),
        )

        assertEquals(profile, ShortcutProfileCodec.decodeProfile(ShortcutProfileCodec.encode(profile)).getOrThrow())
    }

    @Test fun `profile is readable JSON with an explicit format version`() {
        val text = ShortcutProfileCodec.encode(CspShortcutResolver.defaultBindings).toString(Charsets.UTF_8)

        assertTrue(text.contains("\"format\": \"inkloom-shortcuts\""))
        assertTrue(text.contains("\"version\": 2"))
        assertTrue(text.contains("\"action\": \"PEN_OR_PENCIL_GROUP\""))
    }

    @Test fun `version one profiles remain compatible with automatic group targeting`() {
        val legacy = """{"format":"inkloom-shortcuts","version":1,"bindings":[{"action":"BRUSH_GROUP","keyCode":30,"ctrl":false,"alt":false,"shift":false,"scanCode":0,"matchMode":"KEY_CODE","trigger":"PRESS"}]}"""

        val decoded = ShortcutProfileCodec.decodeProfile(legacy.toByteArray()).getOrThrow()

        assertEquals(ShortcutBrushGroupTargets(), decoded.brushGroupTargets)
        assertEquals(ShortcutAction.BRUSH_GROUP, decoded.bindings.single().action)
    }

    @Test fun `malformed oversized and unknown version profiles are rejected`() {
        assertTrue(ShortcutProfileCodec.decode("not json".toByteArray()).isFailure)
        assertTrue(ShortcutProfileCodec.decode(ByteArray(ShortcutProfileCodec.MAX_FILE_BYTES + 1)).isFailure)
        assertTrue(ShortcutProfileCodec.decode("""{"format":"inkloom-shortcuts","version":99,"bindings":[]}""".toByteArray()).isFailure)
    }

    @Test fun `stream decoding stops oversized input before accepting it`() {
        val oversized = ByteArrayInputStream(ByteArray(ShortcutProfileCodec.MAX_FILE_BYTES + 1))

        assertTrue(ShortcutProfileCodec.decode(oversized).isFailure)
    }
}
