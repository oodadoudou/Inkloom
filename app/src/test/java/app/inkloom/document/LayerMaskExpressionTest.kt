package app.inkloom.document

import app.inkloom.engine.v5.surface.MaskTileSurface
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LayerMaskExpressionTest {
    @Test fun thresholdChangesOnlyTheCompositeViewAndKeepsSoftMaskEditable() {
        val surface = MaskTileSurface(64, 64, 64, initialDefaultAlpha = 255)
        surface.beginWrite("soft mask").use { write ->
            write.setAlpha(4, 4, 40)
            write.setAlpha(5, 5, 120)
            write.setAlpha(6, 6, 220)
            write.setAlpha(7, 7, 0)
            write.commit().close()
        }
        val source = surface.snapshot()
        val soft = LayerMask(showGradients = true, expressionThreshold = 128).expressed(source)
        assertSame(source, soft)
        assertEquals(120, soft.alphaAt(5, 5))
        val hard = LayerMask(showGradients = false, expressionThreshold = 128).expressed(source)
        assertEquals(0, hard.alphaAt(4, 4))
        assertEquals(0, hard.alphaAt(5, 5))
        assertEquals(255, hard.alphaAt(6, 6))
        assertEquals(0, hard.alphaAt(7, 7))
        assertSame(hard, LayerMask(showGradients = false, expressionThreshold = 128).expressed(source))
        assertEquals(120, source.alphaAt(5, 5))
        assertEquals(255, source.defaultAlpha)
    }

    @Test fun thresholdPreservesSparseImplicitDefault() {
        val surface = MaskTileSurface(8192, 8192, initialDefaultAlpha = 96)
        val raw = surface.snapshot()
        val hard = LayerMask(showGradients = false, expressionThreshold = 128).expressed(raw)
        assertEquals(0, hard.defaultAlpha)
        assertEquals(0, hard.tileCount)
        assertEquals(96, raw.defaultAlpha)
    }

    @Test fun legacyMasksKeepSoftGradientsWhenDecoded() {
        val old = Json.decodeFromString<LayerMask>("""{"baseVisible":true,"enabled":true,"linked":true}""")
        assertTrue(old.showGradients)
        assertEquals(128, old.expressionThreshold)
    }
}
