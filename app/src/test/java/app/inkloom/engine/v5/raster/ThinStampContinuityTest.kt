package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinStampContinuityTest {
    @Test fun subpixelSolidStrokeCannotDisappearBetweenPixelCenters() {
        for (diameter in listOf(.2f, .5f, 1f, 1.5f)) for (offset in listOf(0f, .2f, .5f, .8f)) for (slope in listOf(0f, .2f)) {
            val surface = PixelTileSurface(96, 48)
            val op = StampPaintOp(surface, StampBrushDefinition(diameterPixels = diameter,
                colorArgb = 0xff000000.toInt(), tips = listOf(BrushTipMask(1, 1, byteArrayOf(-1))), spacingRatio = .1f))
            val session = op.begin(PaintOpContext(1, 0, "thin")) as PublishablePaintOpSession
            val samples = listOf(StrokeSample(1, DocumentPoint(8f, 20f + offset), 1, 1f, 0f, 0f),
                StrokeSample(2, DocumentPoint(88f, 20f + offset + 80f * slope), 2, 1f, 0f, 0f))
            session.apply(PaintBatch(1, 0, samples, null, false))
            session.finish(PaintBatch.empty(1, 1, true))
            session.publish().record.close()
            for (x in 10..86) assertTrue("Missing thin ink: diameter=$diameter offset=$offset x=$x",
                ((18 + ((x - 8) * slope).toInt())..(23 + ((x - 8) * slope).toInt())).any { y -> surface.pixelAt(x, y) ushr 24 > 0 })
        }
    }
}
