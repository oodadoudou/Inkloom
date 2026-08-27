package app.inkloom.engine.v5.storage

import android.graphics.Rect
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.tools.RasterLayerPatch

/** Immutable generation handoff from realtime paint to document persistence. */
data class RasterLayerCommit(
    val layerId: String,
    val snapshot: PixelTileSurfaceSnapshot,
    val dirtyBounds: PixelRect,
) {
    init {
        require(layerId.isNotBlank())
        require(!dirtyBounds.isEmpty)
        require(
            dirtyBounds.left >= 0 && dirtyBounds.top >= 0 &&
                dirtyBounds.right <= snapshot.logicalWidth && dirtyBounds.bottom <= snapshot.logicalHeight,
        )
    }

    /** Materialize only on a persistence worker; never call this from the input thread. */
    fun capturePatch(): RasterLayerPatch = requireNotNull(
        RasterLayerPatch.capture(
            layerId,
            snapshot,
            Rect(dirtyBounds.left, dirtyBounds.top, dirtyBounds.right, dirtyBounds.bottom),
        ),
    )
}
