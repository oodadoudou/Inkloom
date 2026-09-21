package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.RasterSelectionContent
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Sparse, immutable-start transformation of mask values within the current paint selection. */
object LayerMaskSelectionTransform {
    data class Result(val mask: MaskTileSurfaceSnapshot, val selection: MaskTileSurfaceSnapshot)

    fun apply(
        mask: MaskTileSurfaceSnapshot,
        selection: MaskTileSurfaceSnapshot,
        transform: SelectionRasterTransform,
        rasterWidth: Int,
        rasterHeight: Int,
        interpolation: SelectionInterpolation,
    ): Result {
        require(rasterWidth > 0 && rasterHeight > 0)
        if (transform.isIdentity) return Result(mask, selection)
        val sourceBounds = RasterSelectionContent.selectionBoundsInPixels(selection, rasterWidth, rasterHeight)
            ?: return Result(mask, selection)
        val transformed = RasterSelectionTransformSession.transformedBounds(sourceBounds, transform, rasterWidth, rasterHeight)
        val dirtyRaster = sourceBounds.union(transformed)
        val dirtyMask = PixelRect(
            floor(dirtyRaster.left.toDouble() * mask.logicalWidth / rasterWidth).toInt().coerceIn(0, mask.logicalWidth),
            floor(dirtyRaster.top.toDouble() * mask.logicalHeight / rasterHeight).toInt().coerceIn(0, mask.logicalHeight),
            ceil(dirtyRaster.right.toDouble() * mask.logicalWidth / rasterWidth).toInt().coerceIn(0, mask.logicalWidth),
            ceil(dirtyRaster.bottom.toDouble() * mask.logicalHeight / rasterHeight).toInt().coerceIn(0, mask.logicalHeight),
        )
        val inverse = Inverse(transform)
        val output = MaskTileSurface(mask.logicalWidth, mask.logicalHeight, mask.tileSize,
            initialDefaultAlpha = mask.defaultAlpha)
        output.beginWrite("copy layer mask for transform").use { write ->
            if (mask.tileCount > 0) write.composeSurface(mask, MaskComposeMode.REPLACE)
            write.commit().close()
        }
        if (!dirtyMask.isEmpty) output.beginWrite("transform selected layer mask").use { write ->
            for (my in dirtyMask.top until dirtyMask.bottom) for (mx in dirtyMask.left until dirtyMask.right) {
                val dx = (mx + .5f) * rasterWidth / mask.logicalWidth
                val dy = (my + .5f) * rasterHeight / mask.logicalHeight
                val sx = inverse.x(dx, dy)
                val sy = inverse.y(dx, dy)
                val destinationSelection = sample(selection,
                    dx * selection.logicalWidth / rasterWidth,
                    dy * selection.logicalHeight / rasterHeight,
                    interpolation)
                val movedSelection = sample(selection,
                    sx * selection.logicalWidth / rasterWidth,
                    sy * selection.logicalHeight / rasterHeight,
                    interpolation)
                val movedMask = sample(mask,
                    sx * mask.logicalWidth / rasterWidth,
                    sy * mask.logicalHeight / rasterHeight,
                    interpolation)
                val retained = mask.alphaAt(mx, my) * (255 - destinationSelection) / 255
                val moved = movedMask * movedSelection / 255
                val value = (moved + retained * (255 - movedSelection) / 255).coerceIn(0, 255)
                write.setAlpha(mx, my, value)
            }
            write.commit().close()
        }
        return Result(output.snapshot(), transformSelection(selection, transform, rasterWidth, rasterHeight, interpolation))
    }

    fun transformSelection(
        selection: MaskTileSurfaceSnapshot,
        transform: SelectionRasterTransform,
        rasterWidth: Int,
        rasterHeight: Int,
        interpolation: SelectionInterpolation,
    ): MaskTileSurfaceSnapshot {
        val source = RasterSelectionContent.selectionBoundsInPixels(selection, rasterWidth, rasterHeight)
            ?: return selection
        val destinationBounds = RasterSelectionTransformSession.transformedBounds(source, transform, rasterWidth, rasterHeight)
        val result = MaskTileSurface(selection.logicalWidth, selection.logicalHeight, selection.tileSize)
        val inverse = Inverse(transform)
        val target = PixelRect(
            floor(destinationBounds.left.toDouble() * selection.logicalWidth / rasterWidth).toInt().coerceIn(0, selection.logicalWidth),
            floor(destinationBounds.top.toDouble() * selection.logicalHeight / rasterHeight).toInt().coerceIn(0, selection.logicalHeight),
            ceil(destinationBounds.right.toDouble() * selection.logicalWidth / rasterWidth).toInt().coerceIn(0, selection.logicalWidth),
            ceil(destinationBounds.bottom.toDouble() * selection.logicalHeight / rasterHeight).toInt().coerceIn(0, selection.logicalHeight),
        )
        if (!target.isEmpty) result.beginWrite("transform mask selection").use { write ->
            for (y in target.top until target.bottom) for (x in target.left until target.right) {
                val dx = (x + .5f) * rasterWidth / selection.logicalWidth
                val dy = (y + .5f) * rasterHeight / selection.logicalHeight
                val sx = inverse.x(dx, dy) * selection.logicalWidth / rasterWidth
                val sy = inverse.y(dx, dy) * selection.logicalHeight / rasterHeight
                val alpha = sample(selection, sx, sy, interpolation)
                if (alpha != 0) write.setAlpha(x, y, alpha)
            }
            write.commit().close()
        }
        return result.snapshot()
    }

    private fun sample(source: MaskTileSurfaceSnapshot, x: Float, y: Float,
        interpolation: SelectionInterpolation): Int {
        if (!x.isFinite() || !y.isFinite()) return 0
        if (interpolation == SelectionInterpolation.NEAREST) return source.alphaAt(floor(x).toInt(), floor(y).toInt())
        val x0 = floor(x - .5f).toInt(); val y0 = floor(y - .5f).toInt()
        val fx = (x - .5f - x0).coerceIn(0f, 1f); val fy = (y - .5f - y0).coerceIn(0f, 1f)
        val top = source.alphaAt(x0, y0) * (1f - fx) + source.alphaAt(x0 + 1, y0) * fx
        val bottom = source.alphaAt(x0, y0 + 1) * (1f - fx) + source.alphaAt(x0 + 1, y0 + 1) * fx
        return (top * (1f - fy) + bottom * fy).roundToInt().coerceIn(0, 255)
    }

    private class Inverse(transform: SelectionRasterTransform) {
        private val mesh = transform as? SelectionMeshMapping
        private val c = if (mesh == null) transform.inverseCoefficients() else floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        private fun denominator(x: Float, y: Float) = if (c.size == 9) c[6] * x + c[7] * y + c[8] else 1f
        fun x(x: Float, y: Float) = mesh?.inverse(x, y)?.first ?: (c[0] * x + c[1] * y + c[4]) / denominator(x, y)
        fun y(x: Float, y: Float) = mesh?.inverse(x, y)?.second ?: (c[2] * x + c[3] * y + c[5]) / denominator(x, y)
    }
}
