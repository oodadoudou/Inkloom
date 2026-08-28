package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.coordinates.DocumentRect
import app.inkloom.engine.v5.material.PressureResponse
import app.inkloom.engine.v5.material.TiltResponse
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.ActiveStrokeDeltaBlend
import app.inkloom.engine.v5.surface.ActiveStrokeDeltaTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

enum class DryBlend { SOURCE_OVER, DESTINATION_OUT, SOURCE_ATOP }

data class DryInkDefinition(
    val diameterPixels: Float,
    val colorArgb: Int,
    val opacity: Float = 1f,
    val minimumPressureScale: Float = .15f,
    val opacityPressureMinimum: Float = 1f,
    val flowPressureMinimum: Float = 1f,
    val hardness: Float = 1f,
    val blend: DryBlend = DryBlend.SOURCE_OVER,
    val pencilGrain: Float = 0f,
    val grainSeed: Int = 0,
    val sizePressure: PressureResponse? = null,
    val sizeTilt: TiltResponse = TiltResponse.CONSTANT,
    val opacityPressure: PressureResponse? = null,
    val flowPressure: PressureResponse? = null,
) {
    init {
        require(diameterPixels.isFinite() && diameterPixels in .1f..4096f)
        require(opacity.isFinite() && opacity in 0f..1f)
        require(minimumPressureScale.isFinite() && minimumPressureScale in 0f..1f)
        require(opacityPressureMinimum.isFinite() && opacityPressureMinimum in 0f..1f)
        require(flowPressureMinimum.isFinite() && flowPressureMinimum in 0f..1f)
        require(hardness.isFinite() && hardness in 0f..1f)
        require(pencilGrain.isFinite() && pencilGrain in 0f..1f)
    }
}

data class PublishedPaintDelta(
    val record: PixelTileTransactionRecord,
    val snapshot: PixelTileSurfaceSnapshot,
    val dirtyBounds: PixelRect?,
)

private data class SegmentFootprint(val coverage: Float, val pressure: Float)

/**
 * Incremental dry ink/pencil/eraser PaintOp. Each batch rasterizes only new segments into one
 * private sparse transaction. Finish performs no geometry reconstruction and no post-UP effect.
 */
class DryInkPaintOp(
    private val document: PixelTileSurface,
    private val definition: DryInkDefinition,
    private val selection: MaskTileSurfaceSnapshot? = null,
    private val sampleTransforms: List<PaintSampleTransform> = listOf(PaintSampleTransform.Identity),
) : PaintOp {
    init {
        require(sampleTransforms.isNotEmpty())
    }
    override val capabilities = PaintOpCapabilities(
        kind = when {
            definition.blend == DryBlend.DESTINATION_OUT -> PaintOpKind.ERASER
            definition.pencilGrain > 0f -> PaintOpKind.PENCIL
            else -> PaintOpKind.DRY_INK
        },
        destinationSampling = definition.blend == DryBlend.DESTINATION_OUT,
        evolvesAfterInput = false,
        maximumHaloPixels = 1,
    )

    override fun begin(context: PaintOpContext): PaintOpSession {
        require(context.backdropGeneration == document.revision) {
            "Backdrop changed before PaintOp began"
        }
        return Session(
            context,
            ActiveStrokeDeltaTileSurface(
                document,
                "V5 ${capabilities.kind}",
                when (definition.blend) {
                    DryBlend.SOURCE_OVER -> ActiveStrokeDeltaBlend.SOURCE_OVER
                    DryBlend.DESTINATION_OUT -> ActiveStrokeDeltaBlend.DESTINATION_OUT
                    DryBlend.SOURCE_ATOP -> ActiveStrokeDeltaBlend.SOURCE_ATOP
                },
            ),
        )
    }

    inner class Session internal constructor(
        private val context: PaintOpContext,
        private val delta: ActiveStrokeDeltaTileSurface,
    ) : PublishablePaintOpSession, AutoCloseable {
        override val presentationKind: PaintPresentationKind =
            when (definition.blend) {
                DryBlend.SOURCE_OVER -> PaintPresentationKind.SOURCE_DELTA
                DryBlend.DESTINATION_OUT -> PaintPresentationKind.ERASE_COVERAGE
                DryBlend.SOURCE_ATOP -> PaintPresentationKind.SOURCE_ATOP_DELTA
            }
        private val lastSamples = MutableList<StrokeSample?>(sampleTransforms.size) { null }
        private var lastOrdinal = -1L
        private var dirtyBounds: PixelRect? = null
        private var state = State.ACTIVE

        val transactionId: Long get() = delta.transactionId
        val touchedTileCount: Int get() = delta.touchedTileCount
        val workingByteSize: Long get() = delta.workingByteSize

        override fun apply(batch: PaintBatch): PaintResult = applyInternal(batch, finishing = false)

        override fun finish(batch: PaintBatch): PaintResult = applyInternal(batch, finishing = true)

        override fun previewSnapshot(): PixelTileSurfaceSnapshot {
            requireActiveOrFinished()
            return delta.previewSnapshot()
        }

        override fun publish(): PublishedPaintDelta {
            check(state == State.FINISHED) { "PaintOp must finish before publication" }
            state = State.PUBLISHED
            val record = delta.publish()
            return PublishedPaintDelta(record, document.snapshot(), dirtyBounds)
        }

        override fun cancel() {
            requireActiveOrFinished()
            state = State.CANCELED
            delta.rollback()
        }

        override fun close() {
            if (state == State.ACTIVE || state == State.FINISHED) cancel()
        }

        private fun applyInternal(batch: PaintBatch, finishing: Boolean): PaintResult {
            requireActive()
            require(batch.transactionId == context.transactionId)
            require(batch.ordinal == lastOrdinal + 1L) { "Paint batches must be consecutive" }
            require(finishing == batch.terminal) { "Terminal batch and finish call disagree" }
            lastOrdinal = batch.ordinal
            var changed = false
            var batchDirtyBounds: PixelRect? = null
            sampleTransforms.forEachIndexed { transformIndex, transform ->
                batch.samples.forEach { input ->
                    val sample = transform.apply(input)
                    val previous = lastSamples[transformIndex]
                    val repeatedEndpoint = previous != null && previous.point == sample.point &&
                        previous.pressure == sample.pressure && previous.tiltRadians == sample.tiltRadians &&
                        previous.orientationRadians == sample.orientationRadians
                    if (!repeatedEndpoint) rasterizeSegment(previous ?: sample, sample)?.let { bounds ->
                        dirtyBounds = dirtyBounds?.union(bounds) ?: bounds
                        batchDirtyBounds = batchDirtyBounds?.union(bounds) ?: bounds
                        changed = true
                    }
                    lastSamples[transformIndex] = sample
                }
            }
            if (finishing) state = State.FINISHED
            return PaintResult(
                transactionId = context.transactionId,
                batchOrdinal = batch.ordinal,
                changed = changed,
                dirtyBounds = batchDirtyBounds,
            )
        }

        private fun rasterizeSegment(start: StrokeSample, end: StrokeSample): PixelRect? {
            val startRadius = radius(start.pressure, start.tiltRadians)
            val endRadius = radius(end.pressure, end.tiltRadians)
            val padding = maxOf(startRadius, endRadius) + 1.5f
            val bounds = PixelRect(
                floor(minOf(start.point.x, end.point.x) - padding).toInt().coerceIn(0, document.logicalWidth),
                floor(minOf(start.point.y, end.point.y) - padding).toInt().coerceIn(0, document.logicalHeight),
                ceil(maxOf(start.point.x, end.point.x) + padding).toInt().coerceIn(0, document.logicalWidth),
                ceil(maxOf(start.point.y, end.point.y) + padding).toInt().coerceIn(0, document.logicalHeight),
            )
            if (bounds.isEmpty) return null
            val firstColumn = bounds.left / document.tileSize
            val lastColumn = (bounds.right - 1) / document.tileSize
            val firstRow = bounds.top / document.tileSize
            val lastRow = (bounds.bottom - 1) / document.tileSize
            for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
                val tileLeft = column * document.tileSize
                val tileTop = row * document.tileSize
                val overlap = bounds.intersect(
                    PixelRect(
                        tileLeft,
                        tileTop,
                        minOf(tileLeft + document.tileSize, document.logicalWidth),
                        minOf(tileTop + document.tileSize, document.logicalHeight),
                    )
                )
                if (overlap.isEmpty) continue
                val local = PixelRect(
                    overlap.left - tileLeft,
                    overlap.top - tileTop,
                    overlap.right - tileLeft,
                    overlap.bottom - tileTop,
                )
                delta.editTile(PixelTileKey(column, row), local) { pixels, stride ->
                    for (y in overlap.top until overlap.bottom) for (x in overlap.left until overlap.right) {
                        val footprint = segmentCoverage(x + .5f, y + .5f, start, end, startRadius, endRadius)
                        var coverage = footprint.coverage
                        if (coverage <= 0f) continue
                        selection?.let { mask ->
                            val selectionX = ((x + .5f) * mask.logicalWidth / document.logicalWidth)
                                .toInt().coerceIn(0, mask.logicalWidth - 1)
                            val selectionY = ((y + .5f) * mask.logicalHeight / document.logicalHeight)
                                .toInt().coerceIn(0, mask.logicalHeight - 1)
                            coverage *= mask.alphaAt(selectionX, selectionY) / 255f
                        }
                        if (coverage <= 0f) continue
                        if (definition.pencilGrain > 0f) coverage *= grain(x, y)
                        val index = (y - tileTop) * stride + x - tileLeft
                        pixels[index] = when (definition.blend) {
                            DryBlend.SOURCE_OVER -> sourceOver(pixels[index], coverage, footprint.pressure)
                            DryBlend.DESTINATION_OUT -> eraseCoverageOver(pixels[index], coverage)
                            DryBlend.SOURCE_ATOP -> sourceOver(pixels[index], coverage, footprint.pressure)
                        }
                    }
                }
            }
            return bounds
        }

        private fun radius(pressure: Float, tiltRadians: Float): Float {
            val scale = definition.sizePressure?.evaluate(pressure) ?: (definition.minimumPressureScale +
                (1f - definition.minimumPressureScale) * pressure.coerceIn(0f, 1f))
            return definition.diameterPixels * scale * definition.sizeTilt.evaluate(tiltRadians) * .5f
        }

        private fun segmentCoverage(
            x: Float,
            y: Float,
            start: StrokeSample,
            end: StrokeSample,
            startRadius: Float,
            endRadius: Float,
        ): SegmentFootprint {
            val dx = end.point.x - start.point.x
            val dy = end.point.y - start.point.y
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared <= 1e-6f) 0f else
                (((x - start.point.x) * dx + (y - start.point.y) * dy) / lengthSquared).coerceIn(0f, 1f)
            val centerX = start.point.x + dx * t
            val centerY = start.point.y + dy * t
            val radius = startRadius + (endRadius - startRadius) * t
            val distance = sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY))
            val edge = maxOf(1f, radius * (1f - definition.hardness) * .85f)
            return SegmentFootprint(
                coverage = ((radius + .5f - distance) / edge).coerceIn(0f, 1f),
                pressure = start.pressure + (end.pressure - start.pressure) * t,
            )
        }

        private fun grain(x: Int, y: Int): Float {
            var value = x * 0x1f1f1f1f + y * 0x45d9f3b + definition.grainSeed
            value = value xor (value ushr 16)
            value *= 0x45d9f3b
            value = value xor (value ushr 16)
            val noise = (value and 0xffff) / 65535f
            return 1f - definition.pencilGrain * (.25f + noise * .75f)
        }

        private fun sourceOver(destination: Int, coverage: Float, pressure: Float): Int {
            val pressureOpacity = definition.opacityPressure?.evaluate(pressure) ?: (definition.opacityPressureMinimum +
                (1f - definition.opacityPressureMinimum) * pressure.coerceIn(0f, 1f))
            val pressureFlow = definition.flowPressure?.evaluate(pressure) ?: (definition.flowPressureMinimum +
                (1f - definition.flowPressureMinimum) * pressure.coerceIn(0f, 1f))
            val sourceAlpha = (((definition.colorArgb ushr 24) and 0xff) * definition.opacity * pressureOpacity * pressureFlow * coverage)
                .toInt().coerceIn(0, 255)
            if (sourceAlpha == 0) return destination
            val inverse = 255 - sourceAlpha
            val destinationAlpha = (destination ushr 24) and 0xff
            val sourceRed = ((definition.colorArgb ushr 16) and 0xff) * sourceAlpha / 255
            val sourceGreen = ((definition.colorArgb ushr 8) and 0xff) * sourceAlpha / 255
            val sourceBlue = (definition.colorArgb and 0xff) * sourceAlpha / 255
            val outputAlpha = sourceAlpha + destinationAlpha * inverse / 255
            val outputRed = sourceRed + ((destination ushr 16) and 0xff) * inverse / 255
            val outputGreen = sourceGreen + ((destination ushr 8) and 0xff) * inverse / 255
            val outputBlue = sourceBlue + (destination and 0xff) * inverse / 255
            return (outputAlpha.coerceAtMost(255) shl 24) or
                (outputRed.coerceAtMost(255) shl 16) or
                (outputGreen.coerceAtMost(255) shl 8) or
                outputBlue.coerceAtMost(255)
        }

        private fun eraseCoverageOver(destination: Int, coverage: Float): Int {
            val eraseAlpha = (255f * definition.opacity * coverage).toInt().coerceIn(0, 255)
            val existing = (destination ushr 24) and 0xff
            val accumulated = (eraseAlpha + existing * (255 - eraseAlpha) / 255).coerceAtMost(255)
            return (accumulated shl 24) or (accumulated shl 16) or (accumulated shl 8) or accumulated
        }

        private fun requireActive() = check(state == State.ACTIVE) { "PaintOp session is $state" }
        private fun requireActiveOrFinished() = check(state == State.ACTIVE || state == State.FINISHED) {
            "PaintOp session is $state"
        }

    }

    private enum class State { ACTIVE, FINISHED, PUBLISHED, CANCELED }
}
