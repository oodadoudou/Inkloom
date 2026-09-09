package app.inkloom.document

import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot

/** Saved masks follow page rotation; their immutable source asset never changes. */
object SavedSelectionGeometry {
    fun rotated(source: MaskTileSurfaceSnapshot, quarterTurns: Int): MaskTileSurfaceSnapshot {
        val turns = quarterTurns.mod(4)
        if (turns == 0) return source
        val width = if (turns % 2 == 0) source.logicalWidth else source.logicalHeight
        val height = if (turns % 2 == 0) source.logicalHeight else source.logicalWidth
        val output = MaskTileSurface(width, height, source.tileSize)
        output.beginWrite("rotate saved selection").use { write ->
            write.fill(source.defaultAlpha)
            source.tileInfo.forEach { tile ->
                val alpha = source.copyTileAlpha(tile.key) ?: return@forEach
                for (ly in 0 until source.tileSize) for (lx in 0 until source.tileSize) {
                    val x = tile.key.column * source.tileSize + lx
                    val y = tile.key.row * source.tileSize + ly
                    if (x >= source.logicalWidth || y >= source.logicalHeight) continue
                    val value = alpha[ly * source.tileSize + lx].toInt().and(255)
                    if (value == source.defaultAlpha) continue
                    val target = when (turns) {
                        1 -> source.logicalHeight - 1 - y to x
                        2 -> source.logicalWidth - 1 - x to source.logicalHeight - 1 - y
                        else -> y to source.logicalWidth - 1 - x
                    }
                    write.setAlpha(target.first, target.second, value)
                }
            }
            write.commit().close()
        }
        return output.snapshot()
    }
}
