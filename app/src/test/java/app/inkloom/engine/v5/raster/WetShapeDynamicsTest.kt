package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.*
import org.junit.Test

class WetShapeDynamicsTest {
    @Test fun countJitterNeverExceedsAuthoredCountAndReachesDocumentedLimits() {
        for (count in 1..32) for (step in 0..10) {
            val jitter = step / 10f
            for (r in 0..100) assertTrue(jitteredStampCount(count, jitter, r / 100f) in 1..count)
        }
        assertEquals(10, jitteredStampCount(10, 0f, 0f))
        assertEquals(5, jitteredStampCount(10, .5f, 0f))
        assertEquals(10, jitteredStampCount(10, .5f, 1f))
        assertEquals(1, jitteredStampCount(10, 1f, 0f))
    }

    @Test fun everyWetShapeDynamicChangesPixelsAndRemainsIndependentOfBatchBoundaries() {
        val base = definition()
        val normal = draw(base)
        for (changed in listOf(base.copy(horizontalRoundnessJitter = .8f),
            base.copy(verticalRoundnessJitter = .8f), base.copy(directionRotationFactor = -.75f),
            base.copy(strokeRotationJitterRadians = 2f), base.copy(particlesPerPlotJitter = .9f))) {
            val pixels = draw(changed)
            assertFalse(normal.contentEquals(pixels))
            assertArrayEquals(pixels, draw(changed, split = true))
        }
    }

    @Test fun randomizedStartChangesBetweenTransactionsButRepeatsForTheSameTransaction() {
        val definition = definition().copy(strokeRotationJitterRadians = 2f)
        assertArrayEquals(draw(definition, transaction = 3), draw(definition, transaction = 3))
        assertFalse(draw(definition, transaction = 3).contentEquals(draw(definition, transaction = 4)))
        assertArrayEquals(draw(definition(), transaction = 3), draw(definition(), transaction = 4))
    }

    // Count jitter is a flow/build-up effect: leave the master opacity ceiling open.
    private fun definition() = WetPaintDefinition(36f, 0xff305090.toInt(), opacity = 1f, flow = .12f,
        charge = 1f, dilution = 0f, attack = 1f, pull = 0f,
        tips = listOf(BrushTipMask(8, 4, ByteArray(32) { 255.toByte() })),
        particlesPerPlot = 8, spacingRatio = .3f, renderSeed = 72)

    private fun draw(definition: WetPaintDefinition, split: Boolean = false, transaction: Long = 3): IntArray {
        val surface = PixelTileSurface(160, 160)
        val session = WetPaintOp(surface, definition).begin(PaintOpContext(transaction, 0, "wet-shape")) as WetPaintOp.Session
        val points = listOf(StrokeSample(1, DocumentPoint(35f, 35f), 1, 1f, 0f, 0f),
            StrokeSample(2, DocumentPoint(80f, 100f), 2, 1f, 0f, 0f),
            StrokeSample(3, DocumentPoint(125f, 60f), 3, 1f, 0f, 0f))
        val batches = if (split) points.map(::listOf) else listOf(points)
        batches.forEachIndexed { i, samples -> session.apply(PaintBatch(transaction, i.toLong(), samples, null, false)) }
        session.finish(PaintBatch.empty(transaction, batches.size.toLong(), true))
        session.publish().record.close()
        return surface.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 160, 160))
    }
}
