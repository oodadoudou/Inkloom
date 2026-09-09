package app.inkloom.document

import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot

/** Saved masks follow page rotation; their immutable source asset never changes. */
object SavedSelectionGeometry {
    /** Project the original saved area into the visible page, without stretching it to fit a crop. */
    fun projected(source: MaskTileSurfaceSnapshot, quarterTurns: Int, savedCrop: PageCrop,
                  currentCrop: PageCrop, width: Int, height: Int): MaskTileSurfaceSnapshot {
        val rotated = rotated(source, quarterTurns)
        var crop = savedCrop
        repeat(quarterTurns.mod(4)) { crop = PageCrop(1f - crop.bottom, crop.left, 1f - crop.top, crop.right) }
        if (crop == currentCrop && width == rotated.logicalWidth && height == rotated.logicalHeight) return rotated
        val output = MaskTileSurface(width, height, source.tileSize)
        val cropWidth = (crop.right - crop.left).coerceAtLeast(.000001f)
        val cropHeight = (crop.bottom - crop.top).coerceAtLeast(.000001f)
        val row = ByteArray(width)
        output.beginWrite("project saved selection").use { write ->
            for (y in 0 until height) {
                val fullY = currentCrop.top + (y + .5f) / height * (currentCrop.bottom - currentCrop.top)
                val sy = kotlin.math.floor((fullY - crop.top) / cropHeight * rotated.logicalHeight).toInt()
                for (x in 0 until width) {
                    val fullX = currentCrop.left + (x + .5f) / width * (currentCrop.right - currentCrop.left)
                    val sx = kotlin.math.floor((fullX - crop.left) / cropWidth * rotated.logicalWidth).toInt()
                    row[x] = if (sx in 0 until rotated.logicalWidth && sy in 0 until rotated.logicalHeight)
                        rotated.alphaAt(sx, sy).toByte() else 0
                }
                write.replaceAlpha(app.inkloom.engine.v5.surface.PixelRect(0, y, width, y + 1), row)
            }
            write.commit().close()
        }
        return output.snapshot()
    }

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
