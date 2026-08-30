package app.inkloom.ui

import app.inkloom.document.LayerBlendMode
import app.inkloom.settings.UiLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LayerBlendModeDisplayNameTest {
    @Test fun allModesHaveStableEnglishAndChineseDisplayNames() {
        LayerBlendMode.entries.forEach { mode ->
            assertFalse(LayerBlendModeDisplayName.of(mode, UiLanguage.ENGLISH).isBlank())
            assertFalse(LayerBlendModeDisplayName.of(mode, UiLanguage.SIMPLIFIED_CHINESE).isBlank())
        }
        assertEquals("Soft Light", LayerBlendModeDisplayName.of(LayerBlendMode.SOFT_LIGHT, UiLanguage.ENGLISH))
        assertEquals("柔光", LayerBlendModeDisplayName.of(LayerBlendMode.SOFT_LIGHT, UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("颜色减淡", LayerBlendModeDisplayName.of(LayerBlendMode.COLOR_DODGE, UiLanguage.SIMPLIFIED_CHINESE))
    }
}
