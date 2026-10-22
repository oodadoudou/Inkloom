package app.inkloom.engine.v5.tools

import app.inkloom.document.LayerAdjustment
import app.inkloom.engine.v5.composite.PixelAdjustmentProjectionCache
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot

/**
 * Destructive tonal correction. It runs the adjustment-layer kernel itself, so a correction
 * baked into pixels matches the same settings on an adjustment layer exactly, including the
 * extended sRGB encoding of F16 pages. A selection weights the result like a layer mask.
 */
internal object RasterTonalCorrection {
    fun apply(source: PixelTileSurfaceSnapshot, adjustment: LayerAdjustment, selection: MaskTileSurfaceSnapshot?): PixelTileSurface {
        val cache = PixelAdjustmentProjectionCache(source.logicalWidth, source.logicalHeight, source.tileSize, source.pixelFormat, "tonal-correction")
        val corrected = cache.update(source, adjustment, 1f, selection).snapshot
        return PixelSurfaceResampling.convert(corrected, source.pixelFormat)
    }
}
