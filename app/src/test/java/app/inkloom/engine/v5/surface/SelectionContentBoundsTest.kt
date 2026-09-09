package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.tools.selectionContentBounds
import org.junit.Assert.*
import org.junit.Test

class SelectionContentBoundsTest {
    @Test fun findsVisibleEdgesAcrossTilesWithoutRoundingToTileBoundaries() {
        for(format in PixelTileFormat.entries) {
            val surface=PixelTileSurface(300,200,pixelFormat=format)
            assertNull(selectionContentBounds(surface.snapshot()))
            surface.beginWrite("fixture").use { write ->
                if(format.isHighPrecision) {
                    write.setLinearPixel(17,28,LinearRgba(1.5f,0f,0f,.001f))
                    write.setLinearPixel(287,155,LinearRgba(0f,1f,0f,1f))
                } else { write.setPixel(17,28,0x01010000);write.setPixel(287,155,0xff00ff00.toInt()) }
                write.commit().close()
            }
            assertEquals(PixelRect(17,28,288,156),selectionContentBounds(surface.snapshot()))
        }
    }
}
