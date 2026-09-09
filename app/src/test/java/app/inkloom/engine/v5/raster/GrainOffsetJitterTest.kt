package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.*
import org.junit.Test

class GrainOffsetJitterTest {
    @Test fun primaryWetAndSecondaryGrainOriginsVaryOnlyBetweenDifferentStrokeTransactions() {
        for (backend in 0..2) {
            val first = draw(backend, true, 31)
            assertTrue(first.any { it ushr 24 != 0 })
            assertArrayEquals("Same transaction must replay, backend=$backend", first, draw(backend, true, 31))
            assertArrayEquals("Input batching cannot move texture, backend=$backend", first, draw(backend, true, 31, true))
            assertFalse("Successive strokes must not repeat the same texture origin, backend=$backend",
                first.contentEquals(draw(backend, true, 32)))
            assertArrayEquals("Disabled offset preserves a fixed texture, backend=$backend",
                draw(backend, false, 31), draw(backend, false, 32))
        }
    }

    private fun draw(backend: Int, jitter: Boolean, transaction: Long, split: Boolean = false): IntArray {
        val surface = PixelTileSurface(96, 48)
        val tip = BrushTipMask(8, 8, ByteArray(64) { 255.toByte() })
        val grain = BrushTipMask(8, 8, ByteArray(64) { i -> if ((i % 8 + i / 8 * 3) % 5 < 2) 0 else 255.toByte() })
        val stamp = StampBrushDefinition(diameterPixels = 24f, colorArgb = 0xff306090.toInt(), opacity = .4f,
            flow = .3f, spacingRatio = .4f, tips = listOf(tip), grain = grain, grainDensity = 1f,
            grainMovement = 1f, grainScalePercent = 75f, grainOffsetJitter = jitter, renderSeed = 77)
        val operation: PaintOp = when (backend) {
            1 -> WetPaintOp(surface, WetPaintDefinition(24f, 0xff306090.toInt(), opacity = .4f,
                charge = 1f, dilution = 0f, attack = 1f, pull = 0f, tips = listOf(tip),
                grain = grain, grainDensity = 1f, grainMovement = 1f, grainScalePercent = 75f,
                grainOffsetJitter = jitter, spacingRatio = .4f, renderSeed = 77))
            2 -> StampPaintOp(surface, stamp.copy(grain = null, grainOffsetJitter = false),
                secondaryDefinitions = listOf(stamp))
            else -> StampPaintOp(surface, stamp)
        }
        val session = operation.begin(PaintOpContext(transaction, 0, "grain")) as PublishablePaintOpSession
        val samples = listOf(StrokeSample(1, DocumentPoint(20f, 24f), 1, 1f, 0f, 0f),
            StrokeSample(2, DocumentPoint(48f, 24f), 2, 1f, 0f, 0f),
            StrokeSample(3, DocumentPoint(76f, 24f), 3, 1f, 0f, 0f))
        val batches = if (split) samples.map(::listOf) else listOf(samples)
        batches.forEachIndexed { i, batch -> session.apply(PaintBatch(transaction, i.toLong(), batch, null, false)) }
        session.finish(PaintBatch.empty(transaction, batches.size.toLong(), true))
        session.publish().record.close()
        return surface.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 96, 48))
    }
}
