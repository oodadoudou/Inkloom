package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.tools.NativeRasterOps
import app.inkloom.engine.v5.material.DocumentPaperGrain
import app.inkloom.engine.v5.material.BrushTipMask
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
import app.inkloom.brushpresetv5.DabTransferMode
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.pow

enum class DualBrushCompositeMode {
    NORMAL,
    MULTIPLY,
    ADD_GLOW,
    SUBTRACT,
    DARKEN,
    LIGHTEN,
    SCREEN,
    OVERLAY,
    COLOR_DODGE,
    COLOR_BURN,
    LINEAR_BURN,
    HARD_MIX,
    HEIGHT_LINEAR,
}

data class DualBrushCompositeDefinition(val mode: DualBrushCompositeMode = DualBrushCompositeMode.NORMAL)

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
    val paperGrain: DocumentPaperGrain? = null,
    val grainDensity: Float = 0f,
    val grainScalePercent: Float = 100f,
    val grainMovement: Float = 0f,
    val grainZoom: Float = 0f,
    val grainRotationRadians: Float = 0f,
    val grainPhaseX: Float = 0f,
    val grainPhaseY: Float = 0f,
    val grainOffsetJitter: Boolean = false,
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
    val transferMode: DabTransferMode = DabTransferMode.UNIFORM_GLAZE,
    val lateralPathJitterPixels: Float = 0f,
    val longitudinalPathJitterPixels: Float = 0f,
    val horizontalRoundnessJitter: Float = 0f,
    val verticalRoundnessJitter: Float = 0f,
    val directionRotationFactor: Float = 0f,
    val strokeRotationJitterRadians: Float = 0f,
    val particlesPerPlotJitter: Float = 0f,
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
        require(lateralPathJitterPixels.isFinite() && lateralPathJitterPixels >= 0f)
        require(longitudinalPathJitterPixels.isFinite() && longitudinalPathJitterPixels >= 0f)
        require(horizontalRoundnessJitter.isFinite() && horizontalRoundnessJitter in 0f..1f)
        require(verticalRoundnessJitter.isFinite() && verticalRoundnessJitter in 0f..1f)
        require(directionRotationFactor.isFinite() && directionRotationFactor in -1f..1f)
        require(strokeRotationJitterRadians.isFinite() && strokeRotationJitterRadians >= 0f)
        require(particlesPerPlotJitter.isFinite() && particlesPerPlotJitter in 0f..1f)
        require(kind in setOf(PaintOpKind.PENCIL, PaintOpKind.DRY_BRUSH, PaintOpKind.ERASER, PaintOpKind.AIRBRUSH, PaintOpKind.DECORATION))
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
    dualComposite: DualBrushCompositeDefinition? = null,
) : PaintOp {
    private val streamDefinitions = listOf(definition) + secondaryDefinitions
    private val dualComposite = dualComposite ?: secondaryDefinitions.singleOrNull()?.let {
        DualBrushCompositeDefinition()
    }

    init {
        require(sampleTransforms.isNotEmpty())
        require(this.dualComposite == null || secondaryDefinitions.size == 1) {
            "Dual brush compositing requires exactly one secondary stream"
        }
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
        return Session(
            context,
            ActiveStrokeDeltaTileSurface(
                document,
                "V5 ${capabilities.kind} stamp",
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
        private var state = State.ACTIVE
        private var lastOrdinal = -1L
        private val streams = streamDefinitions.map { StreamRuntime(it, context.transactionId) }
        private val streamStates = sampleTransforms.map { streams.map { StreamState() } }
        private val dualCoverage = if (dualComposite != null) {
            List(2) { linkedMapOf<PixelTileKey, ByteArray>() }
        } else emptyList()
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
            val dualDabs = if (dualComposite != null) List(2) { mutableListOf<ResolvedDab>() } else emptyList()
            sampleTransforms.forEachIndexed { transformIndex, transform ->
                streams.forEachIndexed { streamIndex, stream ->
                    val streamState = streamStates[transformIndex][streamIndex]
                    val dabs = dualDabs.getOrNull(streamIndex) ?: mutableListOf()
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
                    if (dualComposite == null) {
                        rasterize(dabs, stream)?.let { bounds ->
                            batchDirty = batchDirty?.union(bounds) ?: bounds
                        }
                    }
                }
            }
            if (dualComposite != null) {
                val dirtyTiles = linkedMapOf<PixelTileKey, PixelRect>()
                dualDabs.forEachIndexed { streamIndex, dabs ->
                    rasterizeCoverage(dabs, streams[streamIndex], dualCoverage[streamIndex]).forEach { (key, dirty) ->
                        dirtyTiles[key] = dirtyTiles[key]?.union(dirty) ?: dirty
                    }
                }
                compositeDualCoverage(dirtyTiles)?.let { bounds ->
                    batchDirty = batchDirty?.union(bounds) ?: bounds
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
            val definition = stream.definition
            val count = jitteredStampCount(definition.particlesPerPlot, definition.particlesPerPlotJitter,
                randomUnit(streamState.dabSequence, 71, definition.renderSeed))
            repeat(count) { particle ->
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
            val horizontalRoundness = 1f - randomUnit(dabSequence, particle * 13 + 7, definition.renderSeed) *
                definition.horizontalRoundnessJitter
            val verticalRoundness = 1f - randomUnit(dabSequence, particle * 13 + 8, definition.renderSeed) *
                definition.verticalRoundnessJitter
            val radiusX = ((if (aspect >= 1f) size * .5f else size * aspect * .5f) * horizontalRoundness).coerceAtLeast(.05f)
            val radiusY = (if (aspect <= 1f) size * .5f else size / aspect * .5f)
                .times(definition.roundness * verticalRoundness).coerceAtLeast(.05f)
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
            val strokeRotation = streamState.strokeRotationRadians ?: (
                (randomUnit(context.transactionId, 89, definition.renderSeed) * 2f - 1f) * definition.strokeRotationJitterRadians
                ).also { streamState.strokeRotationRadians = it }
            val angle = definition.rotationRadians + randomRotation + when {
                definition.followStylusOrientation -> sample.orientationRadians
                definition.followStrokeDirection -> directionRadians
                else -> directionRadians * definition.directionRotationFactor
            } + strokeRotation
            val lateral = (randomUnit(dabSequence, particle * 17 + 11, definition.renderSeed) * 2f - 1f) *
                definition.lateralPathJitterPixels
            val longitudinal = (randomUnit(dabSequence, particle * 17 + 12, definition.renderSeed) * 2f - 1f) *
                definition.longitudinalPathJitterPixels
            return ResolvedDab(
                centerX = sample.point.x + cos(scatterAngle) * scatterDistance +
                    cos(directionRadians) * longitudinal - sin(directionRadians) * lateral,
                centerY = sample.point.y + sin(scatterAngle) * scatterDistance +
                    sin(directionRadians) * longitudinal + cos(directionRadians) * lateral,
                radiusX = radiusX,
                radiusY = radiusY,
                angleRadians = angle,
                depositedAlpha = definition.opacity * definition.flow *
                    definition.opacityPressure.evaluate(sample.pressure) *
                    definition.flowPressure.evaluate(sample.pressure),
                tip = tip,
                paperAttenuation = definition.paperGrain?.strengthAt(sample.tiltRadians) ?: 0f,
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
                        if (selection == null && definition.paperGrain == null &&
                            definition.transferMode == DabTransferMode.UNIFORM_GLAZE &&
                            definition.blend != DryBlend.DESTINATION_OUT
                        ) {
                            val tip = dab.nativeTip
                            val grain = stream.nativeGrain
                            if (NativeRasterOps.depositUniformStamp(
                                pixels, stride, tileLeft, tileTop,
                                overlap.left, overlap.top, overlap.right, overlap.bottom,
                                dab.centerX, dab.centerY, dab.radiusX, dab.radiusY,
                                dab.cosine, dab.sine, dab.depositedAlpha,
                                tip.coverage, tip.width, tip.height,
                                grain?.coverage, grain?.width ?: 0, grain?.height ?: 0,
                                definition.grainDensity, stream.grainCosine, stream.grainSine,
                                stream.grainWavelength, definition.grainMovement,
                                stream.grainPhaseX, stream.grainPhaseY, definition.colorArgb,
                            )) continue
                        }
                        for (y in overlap.top until overlap.bottom) for (x in overlap.left until overlap.right) {
                            var coverage = transferredCoverage(coverageAt(dab, definition, stream, x, y), definition.transferMode)
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
                                DryBlend.SOURCE_OVER -> if (definition.transferMode.usesMaximumTransfer()) {
                                    maximumTransfer(pixels[index], definition.colorArgb, coverage)
                                } else sourceOver(pixels[index], definition.colorArgb, coverage)
                                DryBlend.DESTINATION_OUT -> eraseCoverageOver(pixels[index], coverage)
                                DryBlend.SOURCE_ATOP -> sourceOver(pixels[index], definition.colorArgb, coverage)
                            }
                        }
                    }
                }
            }
            return allDirty
        }

        private fun rasterizeCoverage(
            dabs: List<ResolvedDab>,
            stream: StreamRuntime,
            coverageTiles: MutableMap<PixelTileKey, ByteArray>,
        ): Map<PixelTileKey, PixelRect> {
            if (dabs.isEmpty()) return emptyMap()
            val definition = stream.definition
            val byTile = linkedMapOf<PixelTileKey, MutableList<ResolvedDab>>()
            val dirtyByTile = linkedMapOf<PixelTileKey, PixelRect>()
            dabs.forEach { dab ->
                val bounds = dab.bounds(document.logicalWidth, document.logicalHeight)
                if (bounds.isEmpty) return@forEach
                for (row in bounds.top / document.tileSize..(bounds.bottom - 1) / document.tileSize) {
                    for (column in bounds.left / document.tileSize..(bounds.right - 1) / document.tileSize) {
                        val key = PixelTileKey(column, row)
                        val tileLeft = column * document.tileSize
                        val tileTop = row * document.tileSize
                        val tileBounds = PixelRect(
                            tileLeft,
                            tileTop,
                            minOf(tileLeft + document.tileSize, document.logicalWidth),
                            minOf(tileTop + document.tileSize, document.logicalHeight),
                        )
                        val overlap = bounds.intersect(tileBounds)
                        if (overlap.isEmpty) continue
                        byTile.getOrPut(key, ::mutableListOf) += dab
                        val local = PixelRect(
                            overlap.left - tileLeft,
                            overlap.top - tileTop,
                            overlap.right - tileLeft,
                            overlap.bottom - tileTop,
                        )
                        dirtyByTile[key] = dirtyByTile[key]?.union(local) ?: local
                    }
                }
            }
            byTile.forEach { (key, tileDabs) ->
                val tileLeft = key.column * document.tileSize
                val tileTop = key.row * document.tileSize
                val localDirty = requireNotNull(dirtyByTile[key])
                val alpha = coverageTiles.getOrPut(key) { ByteArray(document.tileSize * document.tileSize) }
                tileDabs.forEach dabLoop@ { dab ->
                    val overlap = dab.bounds(document.logicalWidth, document.logicalHeight).intersect(
                        PixelRect(
                            tileLeft + localDirty.left,
                            tileTop + localDirty.top,
                            tileLeft + localDirty.right,
                            tileTop + localDirty.bottom,
                        ),
                    )
                    if (definition.paperGrain == null) {
                        val tip = dab.nativeTip
                        val grain = stream.nativeGrain
                        if (NativeRasterOps.accumulateStampCoverage(
                            alpha, document.tileSize, tileLeft, tileTop,
                            overlap.left, overlap.top, overlap.right, overlap.bottom,
                            dab.centerX, dab.centerY, dab.radiusX, dab.radiusY,
                            dab.cosine, dab.sine, dab.depositedAlpha,
                            tip.coverage, tip.width, tip.height,
                            grain?.coverage, grain?.width ?: 0, grain?.height ?: 0,
                            definition.grainDensity, stream.grainCosine, stream.grainSine,
                            stream.grainWavelength, definition.grainMovement,
                            stream.grainPhaseX, stream.grainPhaseY,
                        )) return@dabLoop
                    }
                    for (y in overlap.top until overlap.bottom) for (x in overlap.left until overlap.right) {
                        val incoming = (coverageAt(dab, definition, stream, x, y) * 255f + .5f).toInt().coerceIn(0, 255)
                        if (incoming == 0) continue
                        val index = (y - tileTop) * document.tileSize + x - tileLeft
                        val existing = alpha[index].toInt() and 0xff
                        alpha[index] = (incoming + existing * (255 - incoming) / 255).coerceAtMost(255).toByte()
                    }
                }
            }
            return dirtyByTile
        }

        private fun compositeDualCoverage(dirtyTiles: Map<PixelTileKey, PixelRect>): PixelRect? {
            if (dirtyTiles.isEmpty()) return null
            val composite = requireNotNull(dualComposite)
            var allDirty: PixelRect? = null
            dirtyTiles.forEach { (key, localDirty) ->
                val tileLeft = key.column * document.tileSize
                val tileTop = key.row * document.tileSize
                val primary = dualCoverage[0][key]
                val secondary = dualCoverage[1][key]
                delta.editTile(key, localDirty) { pixels, stride ->
                    if (selection == null && NativeRasterOps.compositeDualCoverage(
                        pixels, primary, secondary, stride,
                        localDirty.left, localDirty.top, localDirty.right, localDirty.bottom,
                        composite.mode.ordinal, definition.colorArgb, definition.blend == DryBlend.DESTINATION_OUT,
                    )) return@editTile
                    for (localY in localDirty.top until localDirty.bottom) {
                        for (localX in localDirty.left until localDirty.right) {
                            val x = tileLeft + localX
                            val y = tileTop + localY
                            val index = localY * stride + localX
                            val alphaIndex = localY * document.tileSize + localX
                            var coverage = combineDualCoverage(
                                (primary?.get(alphaIndex)?.toInt() ?: 0) and 0xff,
                                (secondary?.get(alphaIndex)?.toInt() ?: 0) and 0xff,
                                composite.mode,
                            ) / 255f
                            selection?.let { mask ->
                                val selectionX = ((x + .5f) * mask.logicalWidth / document.logicalWidth)
                                    .toInt().coerceIn(0, mask.logicalWidth - 1)
                                val selectionY = ((y + .5f) * mask.logicalHeight / document.logicalHeight)
                                    .toInt().coerceIn(0, mask.logicalHeight - 1)
                                coverage *= mask.alphaAt(selectionX, selectionY) / 255f
                            }
                            pixels[index] = when (definition.blend) {
                                DryBlend.SOURCE_OVER -> sourcePixel(definition.colorArgb, coverage)
                                DryBlend.DESTINATION_OUT -> coveragePixel(coverage)
                                DryBlend.SOURCE_ATOP -> sourcePixel(definition.colorArgb, coverage)
                            }
                        }
                    }
                }
                val documentDirty = PixelRect(
                    tileLeft + localDirty.left,
                    tileTop + localDirty.top,
                    tileLeft + localDirty.right,
                    tileTop + localDirty.bottom,
                )
                allDirty = allDirty?.union(documentDirty) ?: documentDirty
            }
            return allDirty
        }

        private fun coverageAt(
            dab: ResolvedDab,
            definition: StampBrushDefinition,
            stream: StreamRuntime,
            x: Int,
            y: Int,
        ): Float {
            var coverage = dab.coverageAt(x + .5f, y + .5f) * dab.depositedAlpha
            if (coverage <= 0f) return 0f
            definition.paperGrain?.let { paper ->
                coverage *= paper.coverageAt(x + .5f, y + .5f, dab.paperAttenuation)
            }
            definition.grain?.let { grain ->
                val movement = definition.grainMovement
                val grainX = x + .5f - dab.centerX * (1f - movement)
                val grainY = y + .5f - dab.centerY * (1f - movement)
                val rotatedX = stream.grainCosine * grainX + stream.grainSine * grainY
                val rotatedY = -stream.grainSine * grainX + stream.grainCosine * grainY
                fun repeat(value: Float): Float = value - kotlin.math.floor(value)
                val sampled = grain.coverageAt(
                    repeat(rotatedX / stream.grainWavelength + stream.grainPhaseX),
                    repeat(rotatedY / stream.grainWavelength + stream.grainPhaseY),
                )
                coverage *= 1f - definition.grainDensity + sampled * definition.grainDensity
            }
            return coverage.coerceIn(0f, 1f)
        }

        private fun requireActiveOrFinished() = check(state == State.ACTIVE || state == State.FINISHED)
    }

    private class StreamState {
        var lastSample: StrokeSample? = null
        var distanceToNextDab: Float = 0f
        var dabSequence: Long = 0L
        var strokeRotationRadians: Float? = null
    }

    private data class StreamRuntime(val definition: StampBrushDefinition, val transactionId: Long) {
        val grainPhaseX = definition.grainPhaseX + if (definition.grainOffsetJitter) strokeGrainPhase(transactionId, definition.renderSeed, 101) else 0f
        val grainPhaseY = definition.grainPhaseY + if (definition.grainOffsetJitter) strokeGrainPhase(transactionId, definition.renderSeed, 102) else 0f
        val nativeGrain = definition.grain?.nativeLevel(0)
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
        val paperAttenuation: Float,
        val sequence: Long,
    ) {
        val cosine = cos(angleRadians)
        val sine = sin(angleRadians)
        private val tipLevel = tip.levelForOutput(radiusX * 2f, radiusY * 2f)
        val nativeTip = tip.nativeLevel(tipLevel)

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
            return tip.coverageAtPixel(localX, localY, radiusX, radiusY, cosine, sine, tipLevel)
        }
    }

    private enum class State { ACTIVE, FINISHED, PUBLISHED, CANCELED }

    companion object {
        internal fun transferredCoverage(alpha: Float, mode: DabTransferMode): Float {
            if (mode == DabTransferMode.UNIFORM_GLAZE) {
                // Preserve the original Float cancellation/rounding; returning alpha directly
                // changes coverage for small values. pow(x, 1) itself adds no transformation.
                return (1f - (1f - alpha.coerceIn(0f, 1f))).coerceIn(0f, 1f)
            }
            val exponent = when (mode) {
                DabTransferMode.LIGHT_GLAZE -> .65f
                DabTransferMode.UNIFORM_GLAZE -> 1f
                DabTransferMode.INTENSE_GLAZE -> 1.25f
                DabTransferMode.HEAVY_GLAZE -> 1.5f
                DabTransferMode.UNIFORM_BLENDING -> 1.75f
                DabTransferMode.INTENSE_BLENDING -> 2f
            }
            return (1f - (1f - alpha.coerceIn(0f, 1f)).pow(exponent)).coerceIn(0f, 1f)
        }

        private fun DabTransferMode.usesMaximumTransfer(): Boolean =
            this == DabTransferMode.LIGHT_GLAZE || this == DabTransferMode.HEAVY_GLAZE

        private fun maximumTransfer(destination: Int, colorArgb: Int, coverage: Float): Int {
            val incoming = sourcePixel(colorArgb, coverage)
            return if ((incoming ushr 24) > (destination ushr 24)) incoming else destination
        }

        internal fun combineDualCoverage(primary: Int, secondary: Int, mode: DualBrushCompositeMode): Int {
            val base = primary.coerceIn(0, 255) / 255f
            val blend = secondary.coerceIn(0, 255) / 255f
            val value = when (mode) {
                DualBrushCompositeMode.NORMAL -> blend + base * (1f - blend)
                DualBrushCompositeMode.MULTIPLY -> base * blend
                DualBrushCompositeMode.ADD_GLOW -> minOf(1f, base + blend)
                DualBrushCompositeMode.SUBTRACT -> maxOf(0f, base - blend)
                DualBrushCompositeMode.DARKEN -> minOf(base, blend)
                DualBrushCompositeMode.LIGHTEN -> maxOf(base, blend)
                DualBrushCompositeMode.SCREEN -> 1f - (1f - base) * (1f - blend)
                DualBrushCompositeMode.OVERLAY -> if (base <= .5f) 2f * base * blend else 1f - 2f * (1f - base) * (1f - blend)
                DualBrushCompositeMode.COLOR_DODGE -> if (blend >= 1f - DUAL_EPSILON) {
                    if (base <= DUAL_EPSILON) 0f else 1f
                } else minOf(1f, base / (1f - blend))
                DualBrushCompositeMode.COLOR_BURN -> if (base >= 1f - DUAL_EPSILON) {
                    1f
                } else if (blend <= DUAL_EPSILON) {
                    0f
                } else 1f - minOf(1f, (1f - base) / blend)
                DualBrushCompositeMode.LINEAR_BURN -> maxOf(0f, base + blend - 1f)
                DualBrushCompositeMode.HARD_MIX -> if (base + blend >= 1f) 1f else 0f
                DualBrushCompositeMode.HEIGHT_LINEAR -> base * (1f - blend)
            }
            return (value.coerceIn(0f, 1f) * 255f + .5f).toInt()
        }

        private const val DUAL_EPSILON = 1f / 65_535f

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

        private fun eraseCoverageOver(destination: Int, alpha: Float): Int {
            val incoming = (255f * alpha).toInt().coerceIn(0, 255)
            val existing = (destination ushr 24) and 0xff
            return coveragePixel((incoming + existing * (255 - incoming) / 255) / 255f)
        }

        private fun coveragePixel(alpha: Float): Int {
            val value = (alpha.coerceIn(0f, 1f) * 255f + .5f).toInt()
            return (value shl 24) or (value shl 16) or (value shl 8) or value
        }

        private fun sourcePixel(color: Int, alpha: Float): Int {
            val value = (((color ushr 24) and 0xff) * alpha.coerceIn(0f, 1f) + .5f).toInt().coerceIn(0, 255)
            return (value shl 24) or
                ((((color ushr 16) and 0xff) * value / 255) shl 16) or
                ((((color ushr 8) and 0xff) * value / 255) shl 8) or
                ((color and 0xff) * value / 255)
        }
    }
}
