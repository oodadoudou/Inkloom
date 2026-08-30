package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.material.PressureResponse
import app.inkloom.engine.v5.material.TiltResponse
import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.ActiveStrokeDeltaBlend
import app.inkloom.engine.v5.surface.ActiveStrokeDeltaTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import kotlin.math.ceil
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class WetPaintDefinition(
    val diameterPixels: Float,
    val colorArgb: Int,
    val opacity: Float = 1f,
    val hardness: Float = .7f,
    val dilution: Float = .35f,
    val charge: Float = .65f,
    val attack: Float = .7f,
    val pull: Float = .35f,
    val tips: List<BrushTipMask> = emptyList(),
    val spacingRatio: Float = .16f,
    val roundness: Float = 1f,
    val rotationRadians: Float = 0f,
    val followStrokeDirection: Boolean = false,
    val followStylusOrientation: Boolean = false,
    val randomRotationRadians: Float = 0f,
    val renderSeed: Int = 0,
    val particlesPerPlot: Int = 1,
    val sizePressure: PressureResponse = PressureResponse.CONSTANT,
    val sizeTilt: TiltResponse = TiltResponse.CONSTANT,
    val blend: DryBlend = DryBlend.SOURCE_OVER,
) {
    init {
        require(diameterPixels.isFinite() && diameterPixels in .1f..4096f)
        require(opacity.isFinite() && opacity in 0f..1f)
        require(hardness.isFinite() && hardness in 0f..1f)
        require(dilution.isFinite() && dilution in 0f..1f)
        require(charge.isFinite() && charge in 0f..1f)
        require(attack.isFinite() && attack in 0f..1f)
        require(pull.isFinite() && pull in 0f..1f)
        require(spacingRatio.isFinite() && spacingRatio in .005f..4f)
        require(roundness.isFinite() && roundness in .01f..1f)
        require(rotationRadians.isFinite())
        require(randomRotationRadians.isFinite() && randomRotationRadians >= 0f)
        require(particlesPerPlot in 1..32)
    }
}

/**
 * Incremental wet-pigment PaintOp. A gesture owns one reservoir and one private tile delta:
 * destination colour is picked up before each dab, carried along the path, and deposited into the
 * same generations that are presented live and eventually published. There is no pen-up replay.
 */
class WetPaintOp(
    private val document: PixelTileSurface,
    private val definition: WetPaintDefinition,
    private val selection: MaskTileSurfaceSnapshot? = null,
    private val sampleTransforms: List<PaintSampleTransform> = listOf(PaintSampleTransform.Identity),
) : PaintOp {
    init {
        require(sampleTransforms.isNotEmpty())
    }
    override val capabilities = PaintOpCapabilities(
        kind = PaintOpKind.WET,
        destinationSampling = true,
        evolvesAfterInput = false,
        maximumHaloPixels = 2,
    )

    override fun begin(context: PaintOpContext): PaintOpSession {
        require(context.backdropGeneration == document.revision)
        return Session(
            context,
            ActiveStrokeDeltaTileSurface(
                document,
                "V5 wet paint",
                if (definition.blend == DryBlend.SOURCE_ATOP) ActiveStrokeDeltaBlend.SOURCE_ATOP
                else ActiveStrokeDeltaBlend.SOURCE_OVER,
            ),
        )
    }

    inner class Session internal constructor(
        private val context: PaintOpContext,
        private val delta: ActiveStrokeDeltaTileSurface,
    ) : PublishablePaintOpSession, AutoCloseable {
        override val presentationKind: PaintPresentationKind =
            if (definition.blend == DryBlend.SOURCE_ATOP) PaintPresentationKind.SOURCE_ATOP_DELTA
            else PaintPresentationKind.SOURCE_DELTA
        private var state = State.ACTIVE
        private var lastOrdinal = -1L
        private val tracks = sampleTransforms.map { WetTrack() }
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
            return PublishedPaintDelta(delta.publish(), document.snapshot(), dirtyBounds)
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
                val track = tracks[transformIndex]
                batch.samples.forEach { input ->
                    val sample = transform.apply(input)
                    val previous = track.lastSample
                    if (previous == null) {
                        deposit(sample, track, definition.rotationRadians)?.let {
                            batchDirty = batchDirty?.union(it) ?: it
                        }
                        track.distanceToNextDab = spacing(sample)
                    } else if (!sameSample(previous, sample)) {
                        val dx = sample.point.x - previous.point.x
                        val dy = sample.point.y - previous.point.y
                        val length = hypot(dx, dy)
                        val direction = atan2(dy, dx)
                        var travelled = 0f
                        var remaining = track.distanceToNextDab.coerceAtLeast(.25f)
                        while (travelled + remaining <= length) {
                            travelled += remaining
                            val resolved = interpolate(previous, sample, travelled / length)
                            deposit(resolved, track, direction)?.let { batchDirty = batchDirty?.union(it) ?: it }
                            remaining = spacing(resolved)
                        }
                        track.distanceToNextDab = (remaining - (length - travelled)).coerceAtLeast(.25f)
                    }
                    track.lastSample = sample
                }
            }
            if (batchDirty != null) dirtyBounds = dirtyBounds?.union(batchDirty!!) ?: batchDirty
            if (finishing) state = State.FINISHED
            return PaintResult(context.transactionId, batch.ordinal, batchDirty != null, batchDirty)
        }

        private fun deposit(sample: StrokeSample, track: WetTrack, directionRadians: Float): PixelRect? {
            val diameter = definition.diameterPixels * definition.sizePressure.evaluate(sample.pressure) *
                definition.sizeTilt.evaluate(sample.tiltRadians)
            if (diameter <= .1f) return null
            val picked = sampleColour(sample.point.x, sample.point.y, diameter * .5f)
            if (!track.reservoirInitialized) {
                if (picked.a > .001f) track.reservoir = picked
                track.reservoirInitialized = true
            } else if (picked.a > .001f) {
                val pickup = definition.pull * (1f - definition.dilution * .5f)
                track.reservoir = track.reservoir.mix(picked, pickup)
            }
            val fresh = PremultipliedColor.fromStraight(definition.colorArgb)
            val freshRatio = definition.charge * (1f - definition.dilution)
            track.reservoir = track.reservoir.mix(fresh, freshRatio)
            var dirty: PixelRect? = null
            repeat(definition.particlesPerPlot) { particle ->
                rasterizeDeposit(sample, track, directionRadians, diameter, particle)?.let { bounds ->
                    dirty = dirty?.union(bounds) ?: bounds
                }
            }
            track.dabSequence++
            return dirty
        }

        private fun rasterizeDeposit(
            sample: StrokeSample,
            track: WetTrack,
            directionRadians: Float,
            diameter: Float,
            particle: Int,
        ): PixelRect? {
            val tip = definition.tips.takeIf { it.isNotEmpty() }?.let { tips ->
                tips[deterministicTipIndex(track.dabSequence, definition.renderSeed, tips.size)]
            }
            val tipAspect = tip?.let { it.width.toFloat() / it.height } ?: 1f
            val radiusX = (if (tipAspect >= 1f) diameter * .5f else diameter * tipAspect * .5f)
                .coerceAtLeast(.05f)
            val radiusY = (if (tipAspect <= 1f) diameter * .5f else diameter / tipAspect * .5f)
                .times(definition.roundness).coerceAtLeast(.05f)
            val randomRotation = (randomUnit(track.dabSequence, particle, definition.renderSeed) * 2f - 1f) *
                definition.randomRotationRadians
            val angle = definition.rotationRadians + randomRotation + when {
                definition.followStylusOrientation -> sample.orientationRadians
                definition.followStrokeDirection -> directionRadians
                else -> 0f
            }
            val cosine = cos(angle)
            val sine = sin(angle)
            val extentX = kotlin.math.abs(cosine * radiusX) + kotlin.math.abs(sine * radiusY) + 1f
            val extentY = kotlin.math.abs(sine * radiusX) + kotlin.math.abs(cosine * radiusY) + 1f
            val bounds = PixelRect(
                floor(sample.point.x - extentX).toInt().coerceIn(0, document.logicalWidth),
                floor(sample.point.y - extentY).toInt().coerceIn(0, document.logicalHeight),
                ceil(sample.point.x + extentX).toInt().coerceIn(0, document.logicalWidth),
                ceil(sample.point.y + extentY).toInt().coerceIn(0, document.logicalHeight),
            )
            if (bounds.isEmpty) return null
            val firstColumn = bounds.left / document.tileSize
            val lastColumn = (bounds.right - 1) / document.tileSize
            val firstRow = bounds.top / document.tileSize
            val lastRow = (bounds.bottom - 1) / document.tileSize
            val radius = minOf(radiusX, radiusY)
            val outerRadius = radius + .5f
            val outerRadiusSquared = outerRadius * outerRadius
            val edge = maxOf(1f, radius * (1f - definition.hardness) * .85f)
            val innerRadius = (outerRadius - edge).coerceAtLeast(0f)
            val innerRadiusSquared = innerRadius * innerRadius
            val inverseEdge = 1f / edge
            val baseDeposition = definition.opacity * definition.attack * (.3f + .7f * definition.charge)
            val selectionScaleX = selection?.let { it.logicalWidth.toFloat() / document.logicalWidth } ?: 1f
            val selectionScaleY = selection?.let { it.logicalHeight.toFloat() / document.logicalHeight } ?: 1f
            for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
                val tileLeft = column * document.tileSize
                val tileTop = row * document.tileSize
                val overlap = bounds.intersect(PixelRect(
                    tileLeft,
                    tileTop,
                    minOf(tileLeft + document.tileSize, document.logicalWidth),
                    minOf(tileTop + document.tileSize, document.logicalHeight),
                ))
                if (overlap.isEmpty) continue
                val local = PixelRect(overlap.left - tileLeft, overlap.top - tileTop, overlap.right - tileLeft, overlap.bottom - tileTop)
                delta.editTile(PixelTileKey(column, row), local) { pixels, stride ->
                    for (y in overlap.top until overlap.bottom) for (x in overlap.left until overlap.right) {
                        val dx = x + .5f - sample.point.x
                        val dy = y + .5f - sample.point.y
                        var coverage = if (tip != null) {
                            val localX = cosine * dx + sine * dy
                            val localY = -sine * dx + cosine * dy
                            tip.coverageAt((localX / radiusX + 1f) * .5f, (localY / radiusY + 1f) * .5f)
                        } else {
                            val distanceSquared = dx * dx + dy * dy
                            if (distanceSquared >= outerRadiusSquared) continue
                            // Most pixels in a hard or medium-hard dab are fully covered. Avoiding
                            // a sqrt for the interior preserves the original round-tip fast path.
                            if (distanceSquared <= innerRadiusSquared) 1f else {
                                (outerRadius - sqrt(distanceSquared)) * inverseEdge
                            }
                        }
                        if (coverage <= 0f) continue
                        selection?.let { mask ->
                            val sx = ((x + .5f) * selectionScaleX).toInt().coerceIn(0, mask.logicalWidth - 1)
                            val sy = ((y + .5f) * selectionScaleY).toInt().coerceIn(0, mask.logicalHeight - 1)
                            coverage *= mask.alphaAt(sx, sy) / 255f
                        }
                        if (coverage <= 0f) continue
                        val deposition = baseDeposition * coverage
                        val index = (y - tileTop) * stride + x - tileLeft
                        pixels[index] = track.reservoir.sourceOver(pixels[index], deposition)
                    }
                }
            }
            return bounds
        }

        private fun sampleColour(cx: Float, cy: Float, radius: Float): PremultipliedColor {
            val offset = maxOf(1, (radius * .22f).toInt())
            val positions = arrayOf(
                cx.toInt() to cy.toInt(),
                (cx.toInt() - offset) to cy.toInt(),
                (cx.toInt() + offset) to cy.toInt(),
                cx.toInt() to (cy.toInt() - offset),
                cx.toInt() to (cy.toInt() + offset),
            )
            var total = PremultipliedColor.ZERO
            var count = 0
            positions.forEach { (x, y) ->
                if (x !in 0 until document.logicalWidth || y !in 0 until document.logicalHeight) return@forEach
                val value = delta.compositedPixelAt(x, y)
                if ((value ushr 24) != 0) {
                    total += PremultipliedColor.fromPremultiplied(value)
                    count++
                }
            }
            return if (count == 0) PremultipliedColor.ZERO else total / count.toFloat()
        }

        private fun spacing(sample: StrokeSample): Float =
            (definition.diameterPixels * definition.sizePressure.evaluate(sample.pressure) * definition.spacingRatio)
                .coerceAtLeast(.25f)

        private fun requireActiveOrFinished() = check(state == State.ACTIVE || state == State.FINISHED)

        private inner class WetTrack {
            var lastSample: StrokeSample? = null
            var distanceToNextDab = 0f
            var reservoir = PremultipliedColor.fromStraight(definition.colorArgb)
            var reservoirInitialized = false
            var dabSequence = 0L
        }
    }

    private enum class State { ACTIVE, FINISHED, PUBLISHED, CANCELED }

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
}

private data class PremultipliedColor(val a: Float, val r: Float, val g: Float, val b: Float) {
    operator fun plus(other: PremultipliedColor) = PremultipliedColor(a + other.a, r + other.r, g + other.g, b + other.b)
    operator fun div(value: Float) = PremultipliedColor(a / value, r / value, g / value, b / value)

    fun mix(other: PremultipliedColor, amount: Float): PremultipliedColor {
        val t = amount.coerceIn(0f, 1f)
        return PremultipliedColor(a + (other.a - a) * t, r + (other.r - r) * t, g + (other.g - g) * t, b + (other.b - b) * t)
    }

    fun sourceOver(destination: Int, coverage: Float): Int {
        val sourceAlpha = (a * coverage).coerceIn(0f, 1f)
        val inverse = 1f - sourceAlpha
        val da = ((destination ushr 24) and 0xff) / 255f
        val dr = ((destination ushr 16) and 0xff) / 255f
        val dg = ((destination ushr 8) and 0xff) / 255f
        val db = (destination and 0xff) / 255f
        return ((sourceAlpha + da * inverse) * 255f + .5f).toInt().coerceIn(0, 255).shl(24) or
            ((r * coverage + dr * inverse) * 255f + .5f).toInt().coerceIn(0, 255).shl(16) or
            ((g * coverage + dg * inverse) * 255f + .5f).toInt().coerceIn(0, 255).shl(8) or
            ((b * coverage + db * inverse) * 255f + .5f).toInt().coerceIn(0, 255)
    }

    companion object {
        val ZERO = PremultipliedColor(0f, 0f, 0f, 0f)
        fun fromStraight(argb: Int): PremultipliedColor {
            val a = ((argb ushr 24) and 0xff) / 255f
            return PremultipliedColor(
                a,
                ((argb ushr 16) and 0xff) / 255f * a,
                ((argb ushr 8) and 0xff) / 255f * a,
                (argb and 0xff) / 255f * a,
            )
        }

        fun fromPremultiplied(argb: Int) = PremultipliedColor(
            ((argb ushr 24) and 0xff) / 255f,
            ((argb ushr 16) and 0xff) / 255f,
            ((argb ushr 8) and 0xff) / 255f,
            (argb and 0xff) / 255f,
        )
    }
}

private fun sameSample(left: StrokeSample, right: StrokeSample): Boolean =
    left.point == right.point && left.pressure == right.pressure &&
        left.tiltRadians == right.tiltRadians && left.orientationRadians == right.orientationRadians

private fun interpolate(start: StrokeSample, end: StrokeSample, amount: Float): StrokeSample {
    val t = amount.coerceIn(0f, 1f)
    return StrokeSample(
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
}
