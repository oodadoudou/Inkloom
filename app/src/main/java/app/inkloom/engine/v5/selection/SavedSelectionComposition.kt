package app.inkloom.engine.v5.selection

import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot

/** Combines persisted alpha with the current selection without flattening the whole page. */
object SavedSelectionComposition {
    fun apply(current: MaskTileSurfaceSnapshot, saved: MaskTileSurfaceSnapshot,
              mode: MaskComposeMode): MaskTileSurfaceSnapshot {
        require(current.logicalWidth == saved.logicalWidth && current.logicalHeight == saved.logicalHeight)
        if (mode == MaskComposeMode.REPLACE) return saved
        val composed = MaskTileSurface(current.logicalWidth, current.logicalHeight, current.tileSize)
        composed.beginWrite("load saved selection").use { write ->
            write.composeSurface(current, MaskComposeMode.ADD)
            write.composeSurface(saved, mode)
            write.commit().close()
        }
        return composed.snapshot()
    }
}
