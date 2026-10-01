package app.inkloom

import android.graphics.BlendMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.LayerBlendMode
import app.inkloom.document.requiresPixelCompositor
import app.inkloom.document.toBlendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerBlendModeTest {
    @Test fun platformModesMapAndSoftwareModesRejectInexactFallback() {
        LayerBlendMode.entries.forEach { mode ->
            if (mode.requiresPixelCompositor) {
                org.junit.Assert.assertTrue(runCatching { mode.toBlendMode() }.isFailure)
            } else assertNotNull(mode.toBlendMode())
        }
    }

    @Test fun knownMappingsAreStable() {
        assertEquals(BlendMode.SRC_OVER, LayerBlendMode.NORMAL.toBlendMode())
        assertEquals(BlendMode.MULTIPLY, LayerBlendMode.MULTIPLY.toBlendMode())
        assertEquals(BlendMode.SCREEN, LayerBlendMode.SCREEN.toBlendMode())
        assertEquals(BlendMode.PLUS, LayerBlendMode.ADD.toBlendMode())
        assertEquals(BlendMode.OVERLAY, LayerBlendMode.OVERLAY.toBlendMode())
        assertEquals(BlendMode.COLOR_DODGE, LayerBlendMode.COLOR_DODGE.toBlendMode())
        assertEquals(BlendMode.LUMINOSITY, LayerBlendMode.LUMINOSITY.toBlendMode())
    }
}
