package app.inkloom.engine.v5.raster

import app.inkloom.brushpresetv5.DabTransferMode
import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.random.Random

class StampPaintOpTest {
    @Test fun uniformTransferPreservesFloatRoundingAcrossCoverageRange() {
        fun verify(alpha: Float) {
            val previous = (1f - (1f - alpha.coerceIn(0f, 1f)).pow(1f)).coerceIn(0f, 1f)
            val current = StampPaintOp.transferredCoverage(alpha, DabTransferMode.UNIFORM_GLAZE)
            assertEquals("Coverage bits for alpha=$alpha", previous.toBits(), current.toBits())
        }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
            -Float.MAX_VALUE, -1f, -Float.MIN_VALUE, -0f, 0f, Float.MIN_VALUE,
            .00000001f, .05f, .35f, .5f, 1f, Float.MAX_VALUE).forEach(::verify)
        for (value in 0..65535) verify(value / 65535f)
        val random = Random(6028)
        repeat(100_000) { verify(Float.fromBits(random.nextInt())) }
        assertNotEquals("Small coverage must retain cancellation rounding", .00000001f.toBits(),
            StampPaintOp.transferredCoverage(.00000001f, DabTransferMode.UNIFORM_GLAZE).toBits())
    }

    @Test fun documentPaperChangesCoverageAndRemainsInvariantToBatchBoundaries() {
        fun brush(density: Float, strength: Float = .8f) = definition().copy(
            paperGrain = app.inkloom.engine.v5.material.DocumentPaperGrain(100f, density, strength, 42),
        )
        val points = listOf(sample(1, 42f, 28f), sample(2, 76f, 35f), sample(3, 122f, 24f))
        val light = draw(listOf(points), brush(.1f))
        val dense = draw(listOf(points), brush(.9f))
        assertTrue(light.sumOf { (it ushr 24).toLong() } < dense.sumOf { (it ushr 24).toLong() })
        assertArrayEquals(light, draw(points.map(::listOf), brush(.1f)))
        assertArrayEquals(draw(listOf(points), definition()), draw(listOf(points), brush(.1f, 0f)))
    }

    @Test
    fun procreateRenderingModesProduceOrderedDeterministicDabTransfer() {
        val alpha = .35f
        val light = StampPaintOp.transferredCoverage(alpha, DabTransferMode.LIGHT_GLAZE)
        val uniform = StampPaintOp.transferredCoverage(alpha, DabTransferMode.UNIFORM_GLAZE)
        val intense = StampPaintOp.transferredCoverage(alpha, DabTransferMode.INTENSE_GLAZE)
        val blending = StampPaintOp.transferredCoverage(alpha, DabTransferMode.INTENSE_BLENDING)

        assertTrue(light < uniform)
        assertTrue(uniform < intense)
        assertTrue(intense < blending)
        assertEquals(uniform, StampPaintOp.transferredCoverage(alpha, DabTransferMode.UNIFORM_GLAZE), .000001f)
    }

    @Test
    fun importedPathAndShapeDynamicsAreDeterministicAndChangeTheFootprint() {
        val base = definition().copy(diameterPixels = 16f, particlesPerPlot = 4)
        val dynamic = base.copy(
            lateralPathJitterPixels = 12f,
            longitudinalPathJitterPixels = 7f,
            horizontalRoundnessJitter = .6f,
            verticalRoundnessJitter = .4f,
            directionRotationFactor = .75f,
            strokeRotationJitterRadians = 1.2f,
            particlesPerPlotJitter = .5f,
        )
        val points = listOf(sample(1, 42f, 28f), sample(2, 76f, 35f), sample(3, 122f, 24f))
        val first = draw(listOf(points), dynamic)

        assertArrayEquals(first, draw(points.map(::listOf), dynamic))
        assertTrue(!first.contentEquals(draw(listOf(points), base)))
    }

    @Test
    fun dualBrushCombinesTwoPersistentCoverageStreamsBeforeOneLayerDeposit() {
        val surface = PixelTileSurface(64, 64)
        val primary = definition().copy(diameterPixels = 20f, opacity = .4f, spacingRatio = .5f)
        val secondary = primary.copy(opacity = .6f, renderSeed = 92)
        val session = StampPaintOp(
            surface,
            primary,
            secondaryDefinitions = listOf(secondary),
            dualComposite = DualBrushCompositeDefinition(DualBrushCompositeMode.MULTIPLY),
        ).begin(PaintOpContext(191, 0, "dual")) as StampPaintOp.Session

        session.apply(batch(191, 0, false, sample(0, 32f, 32f)))
        session.finish(PaintBatch.empty(191, 1, terminal = true))
        val published = session.publish()
        val alpha = published.snapshot.pixelAt(32, 32) ushr 24 and 0xff
        val expected = StampPaintOp.combineDualCoverage((.4f * 255f + .5f).toInt(), (.6f * 255f + .5f).toInt(), DualBrushCompositeMode.MULTIPLY)

        assertTrue("expected dual density $expected but was $alpha", kotlin.math.abs(expected - alpha) <= 1)
        assertTrue("dual result must not be sequential source-over", alpha < 100)
        published.record.close()
    }

    @Test
    fun activePreviewAndPublicationShareExactlyOneSparseTransaction() {
        val surface = PixelTileSurface(160, 80)
        val definition = definition().copy(
            paperGrain = app.inkloom.engine.v5.material.DocumentPaperGrain(80f, .4f, .6f, 42),
        )
        val session = StampPaintOp(surface, definition).begin(PaintOpContext(1, 0, "stamp")) as StampPaintOp.Session
        session.apply(batch(1, 0, false, sample(0, 12f, 35f)))
        session.apply(batch(1, 1, false, sample(1, 120f, 35f)))
        val preview = session.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 0, 160, 80))
        session.finish(batch(1, 2, true, sample(2, 120f, 35f)))
        val published = session.publish()
        assertArrayEquals(preview, published.snapshot.copyPremultipliedPixels(PixelRect(0, 0, 160, 80)))
        assertNotEquals(0, published.snapshot.pixelAt(64, 35))
        assertEquals(1L, surface.revision)
        published.record.close()
    }

    @Test
    fun fractionalSpacingIsInvariantToMotionEventBatchBoundaries() {
        val once = draw(listOf(listOf(sample(1, 42f, 28f), sample(2, 76f, 35f), sample(3, 122f, 24f))))
        val split = draw(listOf(
            listOf(sample(1, 42f, 28f)),
            listOf(sample(2, 76f, 35f)),
            listOf(sample(3, 122f, 24f)),
        ))
        assertArrayEquals(once, split)
    }

    @Test
    fun finishDoesNotEvolveOrRebuildMaterial() {
        val surface = PixelTileSurface(96, 64)
        val session = StampPaintOp(surface, definition()).begin(PaintOpContext(7, 0, "stamp")) as StampPaintOp.Session
        session.apply(batch(7, 0, false, sample(0, 20f, 20f)))
        session.apply(batch(7, 1, false, sample(1, 70f, 20f)))
        val beforeFinish = session.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 0, 96, 64))
        val finish = session.finish(batch(7, 2, true, sample(2, 70f, 20f)))
        assertTrue(!finish.changed)
        assertArrayEquals(beforeFinish, session.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 0, 96, 64)))
        session.publish().record.close()
    }

    @Test
    fun texturedEraserOnlyRemovesItsOwnFootprint() {
        val surface = PixelTileSurface(64, 64)
        val fill = surface.beginWrite("fill")
        fill.editTile(app.inkloom.engine.v5.surface.PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ ->
            pixels.fill(0xff102030.toInt())
        }
        fill.commit().close()
        val eraser = definition().copy(blend = DryBlend.DESTINATION_OUT, colorArgb = 0)
        val session = StampPaintOp(surface, eraser).begin(PaintOpContext(9, surface.revision, "eraser")) as StampPaintOp.Session
        session.apply(batch(9, 0, false, sample(0, 32f, 32f)))
        session.finish(PaintBatch.empty(9, 1, terminal = true))
        val published = session.publish()
        assertEquals(0, published.snapshot.pixelAt(32, 32))
        assertEquals(0xff102030.toInt(), published.snapshot.pixelAt(3, 3))
        published.record.close()
    }

    @Test
    fun airbrushParticlesAreDeterministicAndBatchInvariant() {
        val airbrush = definition().copy(
            kind = PaintOpKind.AIRBRUSH,
            diameterPixels = 5f,
            spacingReferencePixels = 30f,
            particlesPerPlot = 5,
            scatterRadiusPixels = 18f,
            scatterBias = .35f,
            randomRotationRadians = Math.PI.toFloat(),
        )
        val points = listOf(sample(1, 42f, 28f), sample(2, 76f, 35f), sample(3, 122f, 24f))

        val once = draw(listOf(points), airbrush)
        val split = draw(points.map(::listOf), airbrush)

        assertArrayEquals(once, split)
        assertTrue("Scatter must deposit away from the path centre", once.indices.any { index ->
            val x = index % 144
            val y = index / 144
            once[index] != 0 && kotlin.math.abs(y - 30) > 7 && x in 10..133
        })
    }

    @Test
    fun runtimeGrainIsInvariantToMotionEventBatchBoundaries() {
        val grainBrush = definition().copy(
            grain = checkerGrain(),
            grainDensity = .8f,
            grainScalePercent = 73f,
            grainMovement = .65f,
            grainZoom = .4f,
            grainRotationRadians = .37f,
            grainPhaseX = .125f,
            grainPhaseY = .625f,
        )
        val points = listOf(sample(1, 42f, 28f), sample(2, 76f, 35f), sample(3, 122f, 24f))

        assertArrayEquals(draw(listOf(points), grainBrush), draw(points.map(::listOf), grainBrush))
    }

    @Test
    fun dualStampStreamsShareOneTransactionAndRemainBatchInvariant() {
        val primary = definition().copy(diameterPixels = 8f, colorArgb = 0xff000000.toInt())
        val secondary = definition().copy(
            diameterPixels = 20f,
            colorArgb = 0xffcc3300.toInt(),
            spacingRatio = .18f,
            renderSeed = 991,
            particlesPerPlot = 2,
            scatterRadiusPixels = 4f,
        )
        val points = listOf(sample(1, 42f, 28f), sample(2, 76f, 35f), sample(3, 122f, 24f))

        val once = draw(listOf(points), primary, listOf(secondary))
        val split = draw(points.map(::listOf), primary, listOf(secondary))

        assertArrayEquals(once, split)
        assertTrue("Secondary stream must enlarge the authored footprint", once.count { it != 0 } > draw(listOf(points), primary).count { it != 0 })
    }

    @Test
    fun movingGrainTravelsWithTipWhileTexturizedGrainIsAnchoredToDocument() {
        val base = definition().copy(
            diameterPixels = 18f,
            grain = checkerGrain(),
            grainDensity = 1f,
            grainScalePercent = 100f,
        )
        val movingAt30 = tapCrop(base.copy(grainMovement = 0f), 30)
        val movingAt71 = tapCrop(base.copy(grainMovement = 0f), 71)
        val fixedAt30 = tapCrop(base.copy(grainMovement = 1f), 30)
        val fixedAt71 = tapCrop(base.copy(grainMovement = 1f), 71)

        assertArrayEquals("Moving grain must retain brush-local phase", movingAt30, movingAt71)
        assertTrue("Texturized grain must retain document phase", !fixedAt30.contentEquals(fixedAt71))
    }

    @Test fun tipOrdersWalkTheTipsAsCspDoes() {
        fun walk(order: app.inkloom.brushpresetv5.TipOrder, stroke: Long = 7L) =
            (0L until 8L).map { StampPaintOp.tipIndex(order, it, stroke, 43, 3) }
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0, 1), walk(app.inkloom.brushpresetv5.TipOrder.REPEAT))
        assertEquals(listOf(0, 1, 2, 1, 0, 1, 2, 1), walk(app.inkloom.brushpresetv5.TipOrder.PING_PONG))
        assertEquals("one tip for the whole stroke", 1, walk(app.inkloom.brushpresetv5.TipOrder.PER_STROKE).distinct().size)
        assertTrue("strokes differ", (1L..20L).map { walk(app.inkloom.brushpresetv5.TipOrder.PER_STROKE, it).first() }.distinct().size > 1)
        assertTrue(walk(app.inkloom.brushpresetv5.TipOrder.RANDOM).all { it in 0..2 })
        assertEquals(0, StampPaintOp.tipIndex(app.inkloom.brushpresetv5.TipOrder.PING_PONG, 5L, 1L, 43, 1))
        assertEquals(app.inkloom.brushpresetv5.TipOrder.REPEAT, app.inkloom.brushpresetv5.TipOrder.fromCsp(0))
        assertEquals(app.inkloom.brushpresetv5.TipOrder.PING_PONG, app.inkloom.brushpresetv5.TipOrder.fromCsp(1))
        assertEquals(app.inkloom.brushpresetv5.TipOrder.RANDOM, app.inkloom.brushpresetv5.TipOrder.fromCsp(2))
        assertEquals(app.inkloom.brushpresetv5.TipOrder.PER_STROKE, app.inkloom.brushpresetv5.TipOrder.fromCsp(3))
    }

    @Test fun colourJitterVariesDabsAroundTheBrushColourDeterministically() {
        val brush = definition().copy(colorArgb = 0xff3a7bd5.toInt(), hueJitterDegrees = 12f, saturationJitter = .08f, valueJitter = .06f)
        val colours = (0L until 40L).map { StampPaintOp.jitteredColor(brush, it) }
        assertTrue("dabs differ", colours.distinct().size > 20)
        assertEquals("deterministic", colours, (0L until 40L).map { StampPaintOp.jitteredColor(brush, it) })
        assertTrue("alpha kept", colours.all { it ushr 24 == 0xff })
        // Stays near the brush colour: every channel within a modest distance.
        colours.forEach { c ->
            assertTrue(Integer.toHexString(c), kotlin.math.abs((c ushr 16 and 255) - 0x3a) < 60 &&
                kotlin.math.abs((c and 255) - 0xd5) < 40)
        }
        assertEquals("no jitter, no change", 0xff3a7bd5.toInt(), StampPaintOp.jitteredColor(definition().copy(colorArgb = 0xff3a7bd5.toInt()), 5L))
        val drawn = draw(listOf(listOf(sample(1, 60f, 30f), sample(2, 132f, 30f))), brush.copy(diameterPixels = 18f, spacingRatio = .9f))
        val hues = drawn.filter { it ushr 24 == 255 }.map { it and 0xffffff }.distinct()
        assertTrue("several colours land on the canvas: ${hues.size}", hues.size > 3)
    }

    @Test fun ribbonLaysTipsEndToEndAlongTheStrokeWithoutGaps() {
        // A 1:3 upright bar: as dabs it would be spaced by the brush spacing; as a ribbon its height runs
        // along the stroke and each bar starts where the last ended.
        val bar = BrushTipMask(16, 48, ByteArray(16 * 48) { 255.toByte() })
        val ribbon = definition().copy(diameterPixels = 24f, spacingRatio = 2f, tips = listOf(bar), ribbon = true)
        val sparse = ribbon.copy(ribbon = false)
        val points = listOf(sample(1, 60f, 30f), sample(2, 132f, 30f))
        val laid = draw(listOf(points), ribbon)
        val spaced = draw(listOf(points), sparse)
        fun gapsOnCentreLine(pixels: IntArray) = (16 until 128).count { x -> pixels[30 * 144 + x] ushr 24 < 32 }
        assertEquals("the ribbon is unbroken along the stroke", 0, gapsOnCentreLine(laid))
        assertTrue("the same tip with wide spacing leaves gaps", gapsOnCentreLine(spaced) > 10)
        // The bar's narrow side lies across the stroke: about 8 px high, not 24.
        val column = (0 until 64).count { y -> laid[y * 144 + 90] ushr 24 > 128 }
        assertTrue("ribbon height $column", column in 6..11)
        assertEquals(24f, StampPaintOp.ribbonLength(24f, 1f / 3f), .001f)
        assertEquals(8f, StampPaintOp.ribbonLength(24f, 3f), .001f)
    }

    private fun draw(
        moveBatches: List<List<StrokeSample>>,
        brush: StampBrushDefinition = definition(),
        secondary: List<StampBrushDefinition> = emptyList(),
    ): IntArray {
        val surface = PixelTileSurface(144, 64)
        val session = StampPaintOp(surface, brush, secondaryDefinitions = secondary)
            .begin(PaintOpContext(1, 0, "stamp")) as StampPaintOp.Session
        session.apply(batch(1, 0, false, sample(0, 12f, 30f)))
        moveBatches.forEachIndexed { index, samples -> session.apply(batch(1, index + 1L, false, *samples.toTypedArray())) }
        session.finish(PaintBatch.empty(1, moveBatches.size + 1L, terminal = true))
        val published = session.publish()
        return published.snapshot.copyPremultipliedPixels(PixelRect(0, 0, 144, 64)).also { published.record.close() }
    }

    private fun definition() = StampBrushDefinition(
        diameterPixels = 14f,
        colorArgb = 0xff102030.toInt(),
        spacingRatio = .24f,
        tips = listOf(BrushTipMask.solidCircle(32, .8f)),
        renderSeed = 43,
    )

    private fun tapCrop(brush: StampBrushDefinition, centerX: Int): IntArray {
        val surface = PixelTileSurface(128, 64)
        val session = StampPaintOp(surface, brush).begin(PaintOpContext(81, 0, "grain")) as StampPaintOp.Session
        session.apply(batch(81, 0, false, sample(0, centerX.toFloat(), 32f)))
        session.finish(PaintBatch.empty(81, 1, terminal = true))
        val published = session.publish()
        return published.snapshot.copyPremultipliedPixels(PixelRect(centerX - 10, 22, centerX + 10, 42))
            .also { published.record.close() }
    }

    private fun checkerGrain(): BrushTipMask {
        val alpha = ByteArray(8 * 8) { index ->
            val x = index % 8
            val y = index / 8
            if ((x / 2 + y / 2) % 2 == 0) 255.toByte() else 40.toByte()
        }
        return BrushTipMask(8, 8, alpha)
    }

    private fun sample(sequence: Long, x: Float, y: Float) = StrokeSample(
        sequence,
        DocumentPoint(x, y),
        sequence * 1_000_000L,
        1f,
        0f,
        0f,
    )

    private fun batch(transaction: Long, ordinal: Long, terminal: Boolean, vararg samples: StrokeSample) = PaintBatch(
        transaction,
        ordinal,
        samples.toList(),
        null,
        terminal,
    )
}
