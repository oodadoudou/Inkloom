package app.inkloom.engine.v3.tools

import android.graphics.Canvas
import android.graphics.Rect
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot

/** Eraser gesture whose authoritative writes and undo delta live in [PixelTileSurface]. */
class RasterTileEraserSession private constructor(
    surface: PixelTileSurface,
    selection: RasterSelectionMask,
) : AutoCloseable {
    constructor(surface: PixelTileSurface) : this(
        surface,
        RasterSelectionMask(null, surface.logicalWidth, surface.logicalHeight),
    )

    internal constructor(surface: PixelTileSurface, selectionMask: MaskTileSurfaceSnapshot?) : this(
        surface,
        RasterSelectionMask(selectionMask, surface.logicalWidth, surface.logicalHeight),
    )

    private val edits = RasterTileEditSession(surface, "raster eraser")
    private val kernel = RasterEraserKernel(surface.logicalWidth, surface.logicalHeight, selection)
    private var completed = false

    val dirtyBounds: PixelRect? get() = edits.dirtyBounds
    val touchedTileCount: Int get() = edits.touchedTileCount
    val workingByteSize: Long get() = edits.workingByteSize
    fun previewSnapshot(): PixelTileSurfaceSnapshot = edits.previewSnapshot()

    fun eraseSegment(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        radiusPixels: Float,
        opacity: Float,
        hardness: Float,
    ): Rect? {
        check(!completed) { "Raster tile eraser session is complete" }
        val bounds = kernel.boundsFor(startX, startY, endX, endY, radiusPixels)
        if (bounds.isEmpty) return null
        val pixelBounds = PixelRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val changed = edits.edit(pixelBounds) { scratch ->
            kernel.erase(
                target = Canvas(scratch),
                destination = Rect(0, 0, scratch.width, scratch.height),
                globalBounds = bounds,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
                radiusPixels = radiusPixels,
                opacity = opacity,
                hardness = hardness,
            )
        } ?: return null
        return Rect(changed.left, changed.top, changed.right, changed.bottom)
    }

    fun commit(): PixelTileTransactionRecord {
        check(!completed) { "Raster tile eraser session is complete" }
        completed = true
        return edits.commit()
    }

    fun restoreOriginal() {
        if (completed) return
        completed = true
        edits.cancel()
    }

    override fun close() {
        if (!completed) restoreOriginal()
        edits.close()
        kernel.close()
    }
}
