package app.inkloom.document

import android.graphics.BlendMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcreateBrushBlendTest {
    @Test fun coreGraphicsRawValuesResolveWithoutUsingAndroidEnumOrdinals() {
        val expected = listOf(
            BlendMode.SRC_OVER,
            BlendMode.MULTIPLY,
            BlendMode.SCREEN,
            BlendMode.OVERLAY,
            BlendMode.DARKEN,
            BlendMode.LIGHTEN,
            BlendMode.COLOR_DODGE,
            BlendMode.COLOR_BURN,
            BlendMode.SOFT_LIGHT,
            BlendMode.HARD_LIGHT,
            BlendMode.DIFFERENCE,
            BlendMode.EXCLUSION,
            BlendMode.HUE,
            BlendMode.SATURATION,
            BlendMode.COLOR,
            BlendMode.LUMINOSITY,
        )

        expected.forEachIndexed { raw, mode -> assertEquals("raw value $raw", mode, ProcreateBrushBlend.resolve(raw)) }
        assertEquals(ProcreateBrushBlend.NORMAL, ProcreateBrushBlend.normalize(99))
        assertEquals(BlendMode.SRC_OVER, ProcreateBrushBlend.resolve(99))
    }
}
