package app.inkloom.color

import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.raster.DryInkDefinition
import app.inkloom.engine.v5.raster.DryInkPaintOp
import app.inkloom.engine.v5.raster.PaintOpContext
import app.inkloom.engine.v5.raster.PublishablePaintOpSession
import app.inkloom.engine.v5.stroke.PaintBatch
import app.inkloom.engine.v5.stroke.StrokeSample
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import app.inkloom.engine.v5.tools.NativeRasterOps
import app.inkloom.engine.v5.tools.RasterTileBlendSession
import app.inkloom.engine.v5.tools.RasterBlendMode
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Independent pigment surface using the same incremental paint and spatial blend as the canvas. */
class MixingBoard(val width: Int = 384, val height: Int = 256) : AutoCloseable {
    enum class Tool { PAINT, BLEND, PICK }
    private val surface = PixelTileSurface(width, height, tileSize = 64)
    private val bounds = PixelRect(0, 0, width, height)
    private val undo = ArrayDeque<PixelTileTransactionRecord>()
    private val redo = ArrayDeque<PixelTileTransactionRecord>()
    private var paint: PublishablePaintOpSession? = null
    private var blend: RasterTileBlendSession? = null
    private val contribution = IntArray(width * height)
    private var transactionId = 0L
    private var ordinal = 0L
    private var sequence = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastPressure = 1f
    private var dabX = 0f
    private var dabY = 0f
    private var distanceToNext = 0f
    private var radius = 10f
    private val spacing get() = (radius * .12f).coerceIn(.5f, 4f)
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun snapshot() = IntArray(width * height).also(::copyPixelsTo)
    fun copyPixelsTo(target: IntArray) {
        require(target.size == width * height)
        (blend?.previewSnapshot() ?: surface.snapshot()).copyPremultipliedPixels(bounds, target)
        paint?.let { active ->
            active.previewSnapshot().copyPremultipliedPixels(bounds, contribution)
            if (!NativeRasterOps.compositePremultipliedTile(target, contribution, width, 0, 0, width, height, 0)) {
                for (i in target.indices) target[i] = overPremultiplied(target[i], contribution[i])
            }
        }
        if (!NativeRasterOps.unpremultiplyInPlace(target)) for (i in target.indices) target[i] = straight(target[i])
    }
    fun restore(values: IntArray) {
        require(values.size == width * height)
        cancel(); clearHistory(undo); clearHistory(redo)
        val pixels = values.copyOf()
        if (!NativeRasterOps.premultiplyInPlace(pixels)) for (i in pixels.indices) pixels[i] = premultiplied(pixels[i])
        surface.beginWrite("Restore mixing pigment").apply { replacePremultipliedPixels(bounds, pixels) }.commit().close()
    }
    fun sample(x: Float, y: Float): Int {
        val px = x.toInt().coerceIn(0, width - 1)
        val py = y.toInt().coerceIn(0, height - 1)
        val base = (blend?.previewSnapshot() ?: surface.snapshot()).pixelAt(px, py)
        return straight(paint?.let { overPremultiplied(base, it.previewSnapshot().pixelAt(px, py)) } ?: base)
    }
    fun begin(x: Float, y: Float, tool: Tool, color: Int, radius: Float, pressure: Float = 1f) {
        cancel()
        this.radius = radius.coerceIn(2f, 48f)
        lastX = x; lastY = y; lastPressure = pressure.coerceIn(0f, 1f)
        dabX = x; dabY = y; distanceToNext = spacing
        transactionId++; ordinal = 0; sequence = 0
        when (tool) {
            Tool.PAINT -> {
                paint = DryInkPaintOp(surface, DryInkDefinition(
                    diameterPixels = this.radius * 2f, colorArgb = color or (0xff shl 24),
                    hardness = .65f, minimumPressureScale = .2f,
                )).begin(PaintOpContext(transactionId, surface.revision, "mixing-paint")) as PublishablePaintOpSession
                applyPaint(x, y, lastPressure)
            }
            Tool.BLEND -> {
                blend = RasterTileBlendSession(surface, mode = RasterBlendMode.MIX).also {
                    depositBlend(it, x, y, lastPressure)
                }
            }
            Tool.PICK -> Unit
        }
    }
    fun move(x: Float, y: Float, pressure: Float = lastPressure) {
        val p = pressure.coerceIn(0f, 1f)
        paint?.let { applyPaint(x, y, p) }
        blend?.let { active ->
            val distance = hypot(x - lastX, y - lastY)
            // Carry spacing across packets: repeated/stationary events cannot add pigment,
            // and a fast sparse packet receives exactly the same deposits as dense input.
            var traveled = distanceToNext
            while (distance > 0f && traveled <= distance + .00001f) {
                val t = (traveled / distance).coerceIn(0f, 1f)
                val nextX = lastX + (x - lastX) * t
                val nextY = lastY + (y - lastY) * t
                depositBlend(active, nextX, nextY, lastPressure + (p - lastPressure) * t)
                traveled += spacing
            }
            distanceToNext = traveled - distance
        }
        lastX = x; lastY = y; lastPressure = p
    }
    private fun applyPaint(x: Float, y: Float, pressure: Float) {
        val sample = StrokeSample(sequence++, DocumentPoint(x, y), sequence * 1_000_000L, pressure, 0f, 0f)
        paint?.apply(PaintBatch(transactionId, ordinal++, listOf(sample), null, false))
    }
    private fun depositBlend(active: RasterTileBlendSession, x: Float, y: Float, pressure: Float, fraction: Float = 1f) {
        active.blend(dabX, dabY, x, y, radius * (.25f + pressure * .75f),
            strength = (.16f + .32f * pressure) * fraction, hardness = .25f)
        dabX = x; dabY = y
    }
    fun finish(): Boolean {
        val record = paint?.let { active ->
            active.finish(PaintBatch.empty(transactionId, ordinal++, true))
            active.publish().record.also { paint = null }
        } ?: blend?.let { active ->
            val remainder = hypot(lastX - dabX, lastY - dabY)
            if (remainder > .001f) depositBlend(active, lastX, lastY, lastPressure, (remainder / spacing).coerceAtMost(1f))
            try { active.commit() } finally { active.close(); blend = null }
        } ?: return false
        return record(record)
    }
    fun cancel() {
        paint?.cancel(); paint = null
        blend?.close(); blend = null
    }
    fun clear(): Boolean {
        cancel()
        if (surface.tileCount == 0) return false
        val write = surface.beginWrite("Clear mixing pigment")
        write.replacePremultipliedPixels(bounds, IntArray(width * height))
        return record(write.commit())
    }
    fun undo(): Boolean {
        cancel(); if (undo.isEmpty()) return false
        val record = undo.removeLast()
        surface.apply(record, PixelTileSurface.HistoryDirection.UNDO); redo.addLast(record)
        return true
    }
    fun redo(): Boolean {
        cancel(); if (redo.isEmpty()) return false
        val record = redo.removeLast()
        surface.apply(record, PixelTileSurface.HistoryDirection.REDO); undo.addLast(record)
        return true
    }
    private fun record(record: PixelTileTransactionRecord): Boolean {
        if (record.changedTileCount == 0) { record.close(); return false }
        if (undo.size == 12) undo.removeFirst().close()
        undo.addLast(record); clearHistory(redo)
        return true
    }
    private fun clearHistory(history: ArrayDeque<PixelTileTransactionRecord>) {
        history.forEach { it.close() }; history.clear()
    }
    override fun close() { cancel(); clearHistory(undo); clearHistory(redo) }

    companion object {
        const val PAPER = 0
        private fun premultiplied(color: Int): Int {
            val a = color ushr 24
            if (a == 0) return 0
            if (a == 255) return color
            fun c(shift: Int) = ((color ushr shift and 255) * a + 127) / 255
            return (a shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
        }
        private fun straight(color: Int): Int {
            val a = color ushr 24
            if (a == 0) return 0
            if (a == 255) return color
            fun c(shift: Int) = (((color ushr shift and 255) * 255 + a / 2) / a).coerceAtMost(255)
            return (a shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
        }
        private fun overPremultiplied(destination: Int, source: Int): Int {
            val inverse = 255 - (source ushr 24)
            fun c(shift: Int) = ((source ushr shift and 255) + (destination ushr shift and 255) * inverse / 255).coerceAtMost(255)
            return (c(24) shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
        }
        fun sourceOver(a: Int, b: Int, coverage: Float): Int {
            val ba = (b ushr 24) / 255f * coverage.coerceIn(0f, 1f)
            return weighted(a, b, (a ushr 24) / 255f * (1f - ba), ba)
        }
        private fun weighted(a: Int, b: Int, aa: Float, ba: Float): Int {
            val alpha = aa + ba
            if (alpha <= 0f) return 0
            fun channel(shift: Int) = (((a ushr shift and 255) * aa + (b ushr shift and 255) * ba) / alpha).roundToInt().coerceIn(0, 255)
            return ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
