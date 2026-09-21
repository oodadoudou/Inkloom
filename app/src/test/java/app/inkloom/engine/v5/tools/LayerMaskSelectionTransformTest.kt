package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.MaskTileSurface
import org.junit.Assert.*
import org.junit.Test

class LayerMaskSelectionTransformTest {
    @Test fun selectedMaskValuesMoveWhileUnselectedValuesStayInPlace() {
        val mask = MaskTileSurface(64, 64, tileSize = 64).also { surface ->
            surface.beginWrite("mask values").use { write ->
                for (y in 20..43) for (x in 4..19) write.setAlpha(x, y, 220)
                for (y in 20..43) for (x in 44..55) write.setAlpha(x, y, 90)
                write.commit().close()
            }
        }.snapshot()
        val selection = MaskTileSurface(64, 64, tileSize = 64).also { surface ->
            surface.beginWrite("select left").use { write ->
                for (y in 18..45) for (x in 2..21) write.setAlpha(x, y, 255)
                write.commit().close()
            }
        }.snapshot()
        val result = LayerMaskSelectionTransform.apply(mask, selection,
            SelectionAffineTransform(12f, 32f, translateX = 16f), 64, 64, SelectionInterpolation.NEAREST)
        assertEquals(0, result.mask.alphaAt(8, 32))
        assertEquals(220, result.mask.alphaAt(24, 32))
        assertEquals(90, result.mask.alphaAt(48, 32))
        assertEquals(255, result.selection.alphaAt(24, 32))
        assertEquals(0, result.selection.alphaAt(8, 32))
    }
}
