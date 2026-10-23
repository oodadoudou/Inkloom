package app.inkloom.engine.v5.tools

import app.inkloom.document.VectorFrame
import app.inkloom.document.VectorGeometry
import app.inkloom.document.VectorLayerContent
import app.inkloom.document.VectorStroke
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

/**
 * Renders vector lines with the same dry-ink paint operation that draws them live, so a layer
 * rebuilt from its lines matches the strokes that were drawn.
 */
object VectorLayerRenderer {
    fun definition(stroke: VectorStroke, pixelsPerUnit: Float) = DryInkDefinition(
        diameterPixels = (stroke.diameter * pixelsPerUnit).coerceIn(.1f, 4096f),
        colorArgb = stroke.colorArgb,
        opacity = stroke.opacity,
        minimumPressureScale = stroke.minimumPressureScale,
        hardness = stroke.hardness,
    )

    fun samples(stroke: VectorStroke, frame: VectorFrame): List<StrokeSample> = List(stroke.pointCount) { i ->
        StrokeSample(i.toLong(), DocumentPoint(frame.pixelX(stroke.x(i)), frame.pixelY(stroke.y(i))), i * 1_000_000L, stroke.pressure(i), 0f, 0f)
    }

    /** Paints one line onto [surface] as one transaction; null when it lands off the surface. */
    fun paint(surface: PixelTileSurface, stroke: VectorStroke, frame: VectorFrame): PixelTileTransactionRecord? {
        val session = DryInkPaintOp(surface, definition(stroke, frame.pixelsPerUnit))
            .begin(PaintOpContext(1L, surface.revision, "vector-pen")) as PublishablePaintOpSession
        session.apply(PaintBatch(1L, 0L, samples(stroke, frame), null, false))
        session.finish(PaintBatch.empty(1L, 1L, true))
        val published = session.publish()
        if (published.record.changedTileCount == 0) { published.record.close(); return null }
        return published.record
    }

    /** All lines on a new transparent surface of the frame's size. */
    fun render(content: VectorLayerContent, frame: VectorFrame): PixelTileSurface =
        PixelTileSurface(frame.width, frame.height).also { surface ->
            content.strokes.forEach { stroke -> paint(surface, stroke, frame)?.close() }
        }

    /**
     * Rebuilds [region] of [target] from [content] as one undoable transaction. Only lines
     * reaching the region are painted, on a scratch surface, and only the region is replaced.
     */
    fun rerender(target: PixelTileSurface, content: VectorLayerContent, frame: VectorFrame, region: PixelRect): PixelTileTransactionRecord? {
        val clipped = region.intersect(PixelRect(0, 0, target.logicalWidth, target.logicalHeight))
        if (clipped.isEmpty) return null
        val scratch = PixelTileSurface(target.logicalWidth, target.logicalHeight, target.tileSize)
        content.strokes.forEach { stroke ->
            val bounds = VectorGeometry.bounds(stroke, frame) ?: return@forEach
            if (!PixelRect(bounds[0], bounds[1], bounds[2], bounds[3]).intersect(clipped).isEmpty) paint(scratch, stroke, frame)?.close()
        }
        val pixels = scratch.snapshot().copyPremultipliedPixels(clipped)
        val write = target.beginWrite("vector re-render")
        write.replacePremultipliedPixels(clipped, pixels)
        val record = write.commit()
        if (record.changedTileCount == 0) { record.close(); return null }
        return record
    }

    /**
     * Draws [strokes] over the existing pixels of [target] (source-over) as one undoable
     * transaction, for Draw along ruler on pixel layers. Without [antiAlias] edges are hard.
     */
    fun drawOnto(target: PixelTileSurface, strokes: List<VectorStroke>, frame: VectorFrame, antiAlias: Boolean): Pair<PixelTileTransactionRecord, PixelRect>? {
        var region: PixelRect? = null
        strokes.forEach { stroke -> VectorGeometry.bounds(stroke, frame)?.let { b -> val r = PixelRect(b[0], b[1], b[2], b[3]); region = region?.union(r) ?: r } }
        val bounds = region?.intersect(PixelRect(0, 0, target.logicalWidth, target.logicalHeight))?.takeUnless { it.isEmpty } ?: return null
        val scratch = PixelTileSurface(target.logicalWidth, target.logicalHeight, target.tileSize)
        strokes.forEach { paint(scratch, it, frame)?.close() }
        val ink = scratch.snapshot().copyPremultipliedPixels(bounds)
        val pixels = target.snapshot().copyPremultipliedPixels(bounds)
        val peak = strokes.maxOf { (it.colorArgb ushr 24) * it.opacity }.toInt().coerceIn(1, 255)
        for (i in pixels.indices) {
            var source = ink[i]
            if (!antiAlias) {
                // Hard edges: at least half coverage becomes the line's full colour, the rest nothing.
                val alpha = source ushr 24
                source = if (alpha * 2 < peak) 0 else {
                    val scale = peak.toFloat() / alpha
                    (peak shl 24) or (((source ushr 16 and 255) * scale).toInt().coerceAtMost(peak) shl 16) or
                        (((source ushr 8 and 255) * scale).toInt().coerceAtMost(peak) shl 8) or ((source and 255) * scale).toInt().coerceAtMost(peak)
                }
            }
            val sa = source ushr 24
            if (sa == 0) continue
            val d = pixels[i]; val inverse = 255 - sa
            fun channel(shift: Int) = ((source ushr shift and 255) + ((d ushr shift and 255) * inverse + 127) / 255).coerceAtMost(255)
            pixels[i] = (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        val write = target.beginWrite("draw along ruler")
        write.replacePremultipliedPixels(bounds, pixels)
        val record = write.commit()
        if (record.changedTileCount == 0) { record.close(); return null }
        return record to bounds
    }
}
