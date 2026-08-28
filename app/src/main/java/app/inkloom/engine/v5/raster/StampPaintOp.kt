package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.material.PressureResponse
import app.inkloom.engine.v5.material.TiltResponse
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.ActiveDeltaSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.pow

data class StampBrushDefinition(
    val kind: PaintOpKind = PaintOpKind.DECORATION,
    val diameterPixels: Float,
    val colorArgb: Int,
    val opacity: Float = 1f,
    val flow: Float = 1f,
    val spacingRatio: Float = .12f,
    val spacingReferencePixels: Float = diameterPixels,
    val tips: List<BrushTipMask>,
    val grain: BrushTipMask? = null,
    val grainDensity: Float = 0f,
    val grainScalePercent: Float = 100f,
    val grainMovement: Float = 0f,
    val grainZoom: Float = 0f,
    val grainRotationRadians: Float = 0f,
    val grainPhaseX: Float = 0f,
    val grainPhaseY: Float = 0f,
    val sizePressure: PressureResponse = PressureResponse.CONSTANT,
    val sizeTilt: TiltResponse = TiltResponse.CONSTANT,
    val opacityPressure: PressureResponse = PressureResponse.CONSTANT,
    val flowPressure: PressureResponse = PressureResponse.CONSTANT,
    val roundness: Float = 1f,
    val rotationRadians: Float = 0f,
    val followStrokeDirection: Boolean = false,
    val followStylusOrientation: Boolean = false,
    val renderSeed: Int = 0,
    val particlesPerPlot: Int = 1,
    val scatterRadiusPixels: Float = 0f,
    val scatterBias: Float = 0f,
    val randomRotationRadians: Float = 0f,
    val repeatStationarySamples: Boolean = false,
    val blend: DryBlend = DryBlend.SOURCE_OVER,
) {
    init {
        require(diameterPixels.isFinite() && diameterPixels in .1f..4096f)
        require(opacity.isFinite() && opacity in 0f..1f)
        require(flow.isFinite() && flow in 0f..1f)
        require(spacingRatio.isFinite() && spacingRatio in .005f..4f)
        require(spacingReferencePixels.isFinite() && spacingReferencePixels in .1f..4096f)
        require(tips.isNotEmpty())
        require(grainDensity.isFinite() && grainDensity in 0f..1f)
        require(grainScalePercent.isFinite() && grainScalePercent in 1f..4000f)
        require(grainMovement.isFinite() && grainMovement in 0f..1f)
        require(grainZoom.isFinite() && grainZoom in 0f..1f)
        require(grainRotationRadians.isFinite())
        require(roundness.isFinite() && roundness in .01f..1f)
        require(rotationRadians.isFinite())
        require(particlesPerPlot in 1..32)
        require(scatterRadiusPixels.isFinite() && scatterRadiusPixels >= 0f)
        require(scatterBias.isFinite() && scatterBias in -1f..1f)
        require(randomRotationRadians.isFinite() && randomRotationRadians >= 0f)
        require(kind in setOf(PaintOpKind.PENCIL, PaintOpKind.ERASER, PaintOpKind.AIRBRUSH, PaintOpKind.DECORATION))
    }
}

/**
 * Append-only shape-stamp PaintOp for imported Ink/Pencil/Decoration and brush-shaped erasers.
 * Fractional spacing state crosses batch boundaries, so MOVE and UP never replay older samples.
 */
class StampPaintOp(
    private val document: PixelTileSurface,
    private val definition: StampBrushDefinition,
    private val selection: MaskTileSurfaceSnapshot? = null,
    secondaryDefinitions: List<StampBrushDefinition> = emptyList(),
    private val sampleTransforms: List<PaintSampleTransform> = listOf(PaintSampleTransform.Identity),
) : PaintOp {
    private val streams = (listOf(definition) + secondaryDefinitions).map(::StreamRuntime)

    init {
        require(sampleTransforms.isNotEmpty())
    }

    override val capabilities = PaintOpCapabilities(
        kind = if (definition.blend == DryBlend.DESTINATION_OUT) PaintOpKind.ERASER else definition.kind,
        destinationSampling = definition.blend == DryBlend.DESTINATION_OUT,
        evolvesAfterInput = false,
        maximumHaloPixels = 1,
    )

    override fun begin(context: PaintOpContext): PaintOpSession {
        require(document.pixelFormat == app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_SRGB_8888)
        require(context.backdropGeneration == document.revision)
        return Session(context, ActiveDeltaSurface(document, "V5 ${capabilities.kind} stamp"))
    }

    inner class Session internal constructor(
        private val context: PaintOpContext,
        private val delta: ActiveDeltaSurface,
    ) : PublishablePaintOpSession, AutoCloseable {
        private var state = State.ACTIVE
        private var lastOrdinal = -1L
        private val streamStates = sampleTransforms.map { streams.map { StreamState() } }
        private var dirtyBounds: PixelRect? = null

        override fun apply(batch: PaintBatch): PaintResult = applyInternal(batch, finishing = false)

        override fun finish(batch: PaintBatch): PaintResult = applyInternal(batch, finishing = true)

        override fun previewSnapshot(): PixelTileSurfaceSnapshot {
            requireActiveOrFinished()
            return delta.previewSnapshot()
        }

        override fun publish(): PublishedPaintDelta {
            check(state == State.FINISHED)
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
            check(state == State.ACTIVE)
            require(batch.transactionId == context.transactionId)
            require(batch.ordinal == lastOrdinal + 1L)
            require(finishing == batch.terminal)
            lastOrdinal = batch.ordinal

            var batchDirty: PixelRect? = null
            sampleTransforms.forEachIndexed { transformIndex, transform ->
                streams.forEachIndexed { streamIndex, stream ->
                    val streamState = streamStates[transformIndex][streamIndex]
                    val dabs = mutableListOf<ResolvedDab>()
                    batch.samples.forEach { input ->
                        val sample = transform.apply(input)
                        val previous = streamState.lastSample
                        if (previous == null) {
                            resolveDabs(sample, directionRadians = stream.definition.rotationRadians, dabs, stream, streamState)
                            streamState.distanceToNextDab = spacingFor(sample, stream.definition)
                        } else if (!sameSample(previous, sample)) {
                            emitSegment(previous, sample, dabs, stream, streamState)
                        } else if (stream.definition.repeatStationarySamples) {
                            resolveDabs(sample, stream.definition.rotationRadians, dabs, stream, streamState)
                        }
                        streamState.lastSample = sample
                    }
                    rasterize(dabs, stream)?.let { bounds ->
                        batchDirty = batchDirty?.union(bounds) ?: bounds
                    }
                }
            }
            if (batchDirty != null) dirtyBounds = dirtyBounds?.union(batchDirty) ?: batchDirty
            if (finishing) state = State.FINISHED
            return PaintResult(context.transactionId, batch.ordinal, batchDirty != null, batchDirty)
        }

        private fun emitSegment(
            start: StrokeSample,
            end: StrokeSample,
            output: MutableList<ResolvedDab>,
            stream: StreamRuntime,
            streamState: StreamState,
        ) {
            val dx = end.point.x - start.point.x
            val dy = end.point.y - start.point.y
            val length = hypot(dx, dy)
            if (length <= 1e-4f) return
            val direction = atan2(dy, dx)
            var travelled = 0f
            var remaining = streamState.distanceToNextDab.coerceAtLeast(.05f)
            while (travelled + remaining <= length) {
                travelled += remaining
                val t = (travelled / length).coerceIn(0f, 1f)
                val sample = interpolate(start, end, t)
                resolveDabs(sample, direction, output, stream, streamState)
                remaining = spacingFor(sample, stream.definition)
            }
            streamState.distanceToNextDab = (remaining - (length - travelled)).coerceAtLeast(.05f)
        }

        private fun resolveDabs(
            sample: StrokeSample,
            directionRadians: Float,
            output: MutableList<ResolvedDab>,
            stream: StreamRuntime,
            streamState: StreamState,
        ) {
            repeat(stream.definition.particlesPerPlot) { particle ->
                output += resolveDab(sample, directionRadians, particle, stream, streamState)
            }
        }

        private fun resolveDab(
            sample: StrokeSample,
            directionRadians: Float,
            particle: Int,
            stream: StreamRuntime,
            streamState: StreamState,
        ): ResolvedDab {
            val definition = stream.definition
            val dabSequence = streamState.dabSequence
            val size = definition.diameterPixels * definition.sizePressure.evaluate(sample.pressure) *
                definition.sizeTilt.evaluate(sample.tiltRadians)
            val tipIndex = deterministicTipIndex(dabSequence, definition.renderSeed, definition.tips.size)
            val tip = definition.tips[tipIndex]
            val aspect = tip.width.toFloat() / tip.height
            val radiusX = (if (aspect >= 1f) size * .5f else size * aspect * .5f).coerceAtLeast(.05f)
            val radiusY = (if (aspect <= 1f) size * .5f else size / aspect * .5f)
                .times(definition.roundness).coerceAtLeast(.05f)
            val scatterAngle = randomUnit(dabSequence, particle * 2, definition.renderSeed) * (Math.PI * 2.0).toFloat()
            val scatterUnit = randomUnit(dabSequence, particle * 2 + 1, definition.renderSeed)
            val scatterExponent = if (definition.scatterBias >= 0f) {
                .5f + definition.scatterBias * 3.5f
            } else {
                .5f / (1f + -definition.scatterBias * 3f)
            }
            val scatterDistance = scatterUnit.pow(scatterExponent) * definition.scatterRadiusPixels
            val randomRotation = (randomUnit(dabSequence, particle * 2 + 2, definition.renderSeed) * 2f - 1f) *
                definition.randomRotationRadians
            val angle = definition.rotationRadians + randomRotation + when {
                definition.followStylusOrientation -> sample.orientationRadians
                definition.followStrokeDirection -> directionRadians
                else -> 0f
            }
            return ResolvedDab(
                centerX = sample.point.x + cos(scatterAngle) * scatterDistance,
                centerY = sample.point.y + sin(scatterAngle) * scatterDistance,
                radiusX = radiusX,
                radiusY = radiusY,
                angleRadians = angle,
                depositedAlpha = definition.opacity * definition.flow *
                    definition.opacityPressure.evaluate(sample.pressure) *
                    definition.flowPressure.evaluate(sample.pressure),
                tip = tip,
                sequence = dabSequence,
            )
                .also { streamState.dabSequence++ }
        }

        private fun spacingFor(sample: StrokeSample, definition: StampBrushDefinition): Float =
            (definition.spacingReferencePixels * definition.sizePressure.evaluate(sample.pressure) * definition.spacingRatio)
                .coerceAtLeast(.25f)

        private fun rasterize(dabs: List<ResolvedDab>, stream: StreamRuntime): PixelRect? {
            if (dabs.isEmpty()) return null
            val definition = stream.definition
            val byTile = linkedMapOf<PixelTileKey, MutableList<ResolvedDab>>()
            val tileDirty = linkedMapOf<PixelTileKey, PixelRect>()
            var allDirty: PixelRect? = null
            dabs.forEach { dab ->
                val bounds = dab.bounds(document.logicalWidth, document.logicalHeight)
                if (bounds.isEmpty) return@forEach
                allDirty = allDirty?.union(bounds) ?: bounds
                val firstColumn = bounds.left / document.tileSize
                val lastColumn = (bounds.right - 1) / document.tileSize
                val firstRow = bounds.top / document.tileSize
                val lastRow = (bounds.bottom - 1) / document.tileSize
                for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
                    val key = PixelTileKey(column, row)
                    val tileBounds = PixelRect(
                        column * document.tileSize,
                        row * document.tileSize,
                        minOf((column + 1) * document.tileSize, document.logicalWidth),
                        minOf((row + 1) * document.tileSize, document.logicalHeight),
                    )
                    val overlap = bounds.intersect(tileBounds)
                    if (overlap.isEmpty) continue
                    byTile.getOrPut(key, ::mutableListOf) += dab
                    val local = PixelRect(
                        overlap.left - tileBounds.left,
                        overlap.top - tileBounds.top,
                        overlap.right - tileBounds.left,
                        overlap.bottom - tileBounds.top,
                    )
                    tileDirty[key] = tileDirty[key]?.union(local) ?: local
                }
            }
            byTile.forEach { (key, tileDabs) ->
                val tileLeft = key.column * document.tileSize
                val tileTop = key.row * document.tileSize
                val localDirty = requireNotNull(tileDirty[key])
                delta.editTile(key, localDirty) { pixels, stride ->
                    for (dab in tileDabs) {
                        val overlap = dab.bounds(document.logicalWidth, document.logicalHeight).intersect(
                            PixelRect(
                                tileLeft + localDirty.left,
                                tileTop + localDirty.top,
                                tileLeft + localDirty.right,
                                tileTop + localDirty.bottom,
                            )
                        )
                        for (y in overlap.top until overlap.bottom) for (x in overlap.left until overlap.right) {
                            var coverage = dab.coverageAt(x + .5f, y + .5f) * dab.depositedAlpha
                            definition.grain?.let { grain ->
                                val movement = definition.grainMovement
                                val grainX = x + .5f - dab.centerX * (1f - movement)
                                val grainY = y + .5f - dab.centerY * (1f - movement)
                                val rotatedX = stream.grainCosine * grainX + stream.grainSine * grainY
                                val rotatedY = -stream.grainSine * grainX + stream.grainCosine * grainY
                                fun repeat(value: Float): Float = value - kotlin.math.floor(value)
                                val sampled = grain.coverageAt(
                                    repeat(rotatedX / stream.grainWavelength + definition.grainPhaseX),
                                    repeat(rotatedY / stream.grainWavelength + definition.grainPhaseY),
                                )
                                coverage *= 1f - definition.grainDensity + sampled * definition.grainDensity
                            }
                            if (coverage <= 0f) continue
                            selection?.let { mask ->
                                val selectionX = ((x + .5f) * mask.logicalWidth / document.logicalWidth)
                                    .toInt().coerceIn(0, mask.logicalWidth - 1)
                                val selectionY = ((y + .5f) * mask.logicalHeight / document.logicalHeight)
                                    .toInt().coerceIn(0, mask.logicalHeight - 1)
                                coverage *= mask.alphaAt(selectionX, selectionY) / 255f
                            }
                            if (coverage <= 0f) continue
                            val index = (y - tileTop) * stride + x - tileLeft
                            pixels[index] = when (definition.blend) {
                                DryBlend.SOURCE_OVER -> sourceOver(pixels[index], definition.colorArgb, coverage)
                                DryBlend.DESTINATION_OUT -> destinationOut(pixels[index], coverage)
                            }
                        }
                    }
                }
            }
            return allDirty
        }

        private fun requireActiveOrFinished() = check(state == State.ACTIVE || state == State.FINISHED)
    }

    private class StreamState {
        var lastSample: StrokeSample? = null
        var distanceToNextDab: Float = 0f
        var dabSequence: Long = 0L
    }

    private data class StreamRuntime(val definition: StampBrushDefinition) {
        val grainCosine = cos(definition.grainRotationRadians)
        val grainSine = sin(definition.grainRotationRadians)
        val grainWavelength = (
            definition.diameterPixels * definition.grainScalePercent / 100f *
                (100f / definition.diameterPixels.coerceAtLeast(.1f)).pow(definition.grainZoom)
            ).coerceAtLeast(1f)
    }

    private data class ResolvedDab(
        val centerX: Float,
        val centerY: Float,
        val radiusX: Float,
        val radiusY: Float,
        val angleRadians: Float,
        val depositedAlpha: Float,
        val tip: BrushTipMask,
        val sequence: Long,
    ) {
        private val cosine = cos(angleRadians)
        private val sine = sin(angleRadians)

        fun bounds(width: Int, height: Int): PixelRect {
            val extentX = kotlin.math.abs(cosine * radiusX) + kotlin.math.abs(sine * radiusY) + 1f
            val extentY = kotlin.math.abs(sine * radiusX) + kotlin.math.abs(cosine * radiusY) + 1f
            return PixelRect(
                floor(centerX - extentX).toInt().coerceIn(0, width),
                floor(centerY - extentY).toInt().coerceIn(0, height),
                ceil(centerX + extentX).toInt().coerceIn(0, width),
                ceil(centerY + extentY).toInt().coerceIn(0, height),
            )
        }

        fun coverageAt(x: Float, y: Float): Float {
            val dx = x - centerX
            val dy = y - centerY
            val localX = cosine * dx + sine * dy
            val localY = -sine * dx + cosine * dy
            return tip.coverageAt((localX / radiusX + 1f) * .5f, (localY / radiusY + 1f) * .5f)
        }
    }

    private enum class State { ACTIVE, FINISHED, PUBLISHED, CANCELED }

    companion object {
        private fun deterministicTipIndex(sequence: Long, seed: Int, count: Int): Int {
            if (count == 1) return 0
            var value = sequence xor seed.toLong().shl(32)
            value = (value xor (value ushr 30)) * -4658895280553007687L
            value = (value xor (value ushr 27)) * -7723592293110705685L
            value = value xor (value ushr 31)
            return ((value and Long.MAX_VALUE) % count).toInt()
        }

        private fun randomUnit(sequence: Long, channel: Int, seed: Int): Float {
            var value = sequence xor seed.toLong().shl(32) xor (channel.toLong() * -7046029254386353131L)
            value = (value xor (value ushr 30)) * -4658895280553007687L
            value = (value xor (value ushr 27)) * -7723592293110705685L
            value = value xor (value ushr 31)
            return ((value ushr 40) and 0xffffff).toFloat() / 0xffffff
        }

        private fun interpolate(start: StrokeSample, end: StrokeSample, t: Float): StrokeSample = StrokeSample(
            inputSequence = end.inputSequence,
            point = app.inkloom.engine.v5.coordinates.DocumentPoint(
                start.point.x + (end.point.x - start.point.x) * t,
                start.point.y + (end.point.y - start.point.y) * t,
            ),
            eventTimeNanos = start.eventTimeNanos + ((end.eventTimeNanos - start.eventTimeNanos) * t).toLong(),
            pressure = start.pressure + (end.pressure - start.pressure) * t,
            tiltRadians = start.tiltRadians + (end.tiltRadians - start.tiltRadians) * t,
            orientationRadians = start.orientationRadians + (end.orientationRadians - start.orientationRadians) * t,
        )

        private fun sameSample(left: StrokeSample, right: StrokeSample): Boolean =
            left.point == right.point && left.pressure == right.pressure &&
                left.tiltRadians == right.tiltRadians && left.orientationRadians == right.orientationRadians

        private fun sourceOver(destination: Int, sourceColor: Int, alpha: Float): Int {
            val sourceAlpha = (((sourceColor ushr 24) and 0xff) * alpha).toInt().coerceIn(0, 255)
            if (sourceAlpha == 0) return destination
            val inverse = 255 - sourceAlpha
            val destinationAlpha = (destination ushr 24) and 0xff
            val sourceRed = ((sourceColor ushr 16) and 0xff) * sourceAlpha / 255
            val sourceGreen = ((sourceColor ushr 8) and 0xff) * sourceAlpha / 255
            val sourceBlue = (sourceColor and 0xff) * sourceAlpha / 255
            return ((sourceAlpha + destinationAlpha * inverse / 255).coerceAtMost(255) shl 24) or
                ((sourceRed + ((destination ushr 16) and 0xff) * inverse / 255).coerceAtMost(255) shl 16) or
                ((sourceGreen + ((destination ushr 8) and 0xff) * inverse / 255).coerceAtMost(255) shl 8) or
                (sourceBlue + (destination and 0xff) * inverse / 255).coerceAtMost(255)
        }

        private fun destinationOut(destination: Int, alpha: Float): Int {
            val keep = 255 - (255f * alpha).toInt().coerceIn(0, 255)
            return ((((destination ushr 24) and 0xff) * keep / 255) shl 24) or
                ((((destination ushr 16) and 0xff) * keep / 255) shl 16) or
                ((((destination ushr 8) and 0xff) * keep / 255) shl 8) or
                ((destination and 0xff) * keep / 255)
        }
    }
}
