package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.material.PressureResponse
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DabSamplingInvarianceTest {
    @Test fun dryTipDoesNotDependOnInputReportDensity() = verify(wet = false, textured = false)
    @Test fun texturedTipDoesNotDependOnInputReportDensity() = verify(wet = false, textured = true)
    @Test fun wetTipDoesNotDependOnInputReportDensity() = verify(wet = true, textured = true)
    @Test fun eraserDoesNotDependOnInputReportDensity() = verify(wet = false, textured = true, eraser = true)

    private fun verify(wet: Boolean, textured: Boolean, eraser: Boolean = false) {
        for (ramp in listOf(false, true)) {
            val sparse = render(wet, textured, eraser, ramp, dense = false)
            val dense = render(wet, textured, eraser, ramp, dense = true)
            val error = sparse.indices.maxOf { abs((sparse[it] ushr 24) - (dense[it] ushr 24)) }
            assertTrue("Report subdivision changed opacity by $error/255: wet=$wet texture=$textured eraser=$eraser pressureRamp=$ramp", error <= 1)
        }
    }

    private fun render(wet: Boolean, textured: Boolean, eraser: Boolean, ramp: Boolean, dense: Boolean): IntArray {
        val surface = PixelTileSurface(104, 48)
        if (eraser) {
            val background = StampPaintOp(surface, StampBrushDefinition(diameterPixels = 200f,
                colorArgb = 0xff203040.toInt(), tips = listOf(BrushTipMask(1, 1, byteArrayOf(-1)))))
                .begin(PaintOpContext(1, surface.revision, "background")) as PublishablePaintOpSession
            background.apply(PaintBatch(1, 0, listOf(sample(50f, false)), null, false))
            background.finish(PaintBatch.empty(1, 1, true))
            background.publish().record.close()
        }
        val tip = BrushTipMask.solidCircle(16)
        val grain = if (textured) BrushTipMask(4, 4, ByteArray(16) { if (it % 3 == 0) 80 else -1 }) else null
        val pressure = PressureResponse(true, .2f)
        val blend = if (eraser) DryBlend.DESTINATION_OUT else DryBlend.SOURCE_OVER
        val operation: PaintOp = if (wet) WetPaintOp(surface, WetPaintDefinition(
            diameterPixels = 10f, colorArgb = 0xff203040.toInt(), opacity = .4f,
            tips = listOf(tip), grain = grain, grainDensity = if (textured) .7f else 0f,
            spacingRatio = .1015625f, sizePressure = pressure, opacityPressure = pressure,
            dilution = .1f, charge = 1f, pull = .2f, blend = blend,
        )) else StampPaintOp(surface, StampBrushDefinition(
            diameterPixels = 10f, colorArgb = 0xff203040.toInt(), opacity = .4f,
            tips = listOf(tip), grain = grain, grainDensity = if (textured) .7f else 0f,
            spacingRatio = .1015625f, sizePressure = pressure, opacityPressure = pressure, blend = blend,
        ))
        val session = operation.begin(PaintOpContext(2, surface.revision, "sampling")) as PublishablePaintOpSession
        val samples = if (dense) (0..512).map { sample(16f + it / 8f, ramp) }
            else listOf(sample(16f, ramp), sample(80f, ramp))
        // Also cross actual batch boundaries: event partition must not reset the dab phase.
        samples.forEachIndexed { index, point -> session.apply(PaintBatch(2, index.toLong(), listOf(point), null, false)) }
        session.finish(PaintBatch.empty(2, samples.size.toLong(), true))
        session.publish().record.close()
        return IntArray(104 * 48) { surface.pixelAt(it % 104, it / 104) }
    }

    private fun sample(x: Float, ramp: Boolean) = StrokeSample(
        (x * 8).toLong(), DocumentPoint(x, 24f), (x * 1_000_000).toLong(),
        if (ramp) .2f + (x - 16f) / 80f else 1f, 0f, 0f,
    )
}
