package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.BrushEngineKind
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v3.brush.ProceduralBrushTip
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProceduralBrushTipTest {
    @Test fun builtInDecorationSprayAndGraphiteTipsHaveDistinctRealShapes() {
        val sparkle = InkStroke(brushId = "system-decoration-sparkle", engineKind = BrushEngineKind.AIRBRUSH, spray = true, stampCount = 3, points = listOf(StrokePoint(.5f, .5f)))
        val hatching = InkStroke(brushId = "system-decoration-hatching", engineKind = BrushEngineKind.STAMP_RASTER, stampCount = 4, points = listOf(StrokePoint(.5f, .5f)))
        val spray = InkStroke(brushId = "system-spray-airbrush", engineKind = BrushEngineKind.AIRBRUSH, spray = true, points = listOf(StrokePoint(.5f, .5f)))
        val hb = InkStroke(brushId = "system-hb-pencil", hardness = .64f, points = listOf(StrokePoint(.5f, .5f)))
        val sixB = InkStroke(brushId = "system-6b-pencil", hardness = .3f, points = listOf(StrokePoint(.5f, .5f)))
        val tips = listOf(sparkle, hatching, spray, hb, sixB).map { requireNotNull(ProceduralBrushTip.create(it)) }
        try {
            val hashes = tips.map { bitmap ->
                IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }.contentHashCode()
            }
            assertTrue(tips.all { bitmap -> bitmap.width == 64 && bitmap.height == 64 })
            assertNotEquals(hashes[0], hashes[1])
            assertNotEquals(hashes[1], hashes[2])
            assertNotEquals(hashes[3], hashes[4])
        } finally {
            tips.forEach(android.graphics.Bitmap::recycle)
        }
    }

}
