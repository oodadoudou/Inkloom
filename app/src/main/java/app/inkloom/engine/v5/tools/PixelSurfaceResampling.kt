package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.*
import kotlin.math.floor
import kotlin.math.ceil

/** Destination tiles are allocated only where the original sparse content can contribute. */
object PixelSurfaceResampling {
    fun convert(source: PixelTileSurfaceSnapshot, format: PixelTileFormat): PixelTileSurface {
        val output=PixelTileSurface(source.logicalWidth,source.logicalHeight,source.tileSize,pixelFormat=format)
        output.beginWrite("change pixel precision").use { write ->
            source.tileInfo.forEach { info ->
                val w=minOf(source.tileSize,source.logicalWidth-info.key.column*source.tileSize)
                val h=minOf(source.tileSize,source.logicalHeight-info.key.row*source.tileSize)
                if(format.isHighPrecision) write.editTileF16(info.key,PixelRect(0,0,w,h)) { p,stride ->
                    for(y in 0 until h) for(x in 0 until w) {
                        val color=source.linearPixelAt(info.key.column*source.tileSize+x,info.key.row*source.tileSize+y)
                        p[y*stride+x]=PackedRgbaF16.packPremultiplied(color.red,color.green,color.blue,color.alpha)
                    }
                } else write.editTile(info.key,PixelRect(0,0,w,h)) { p,stride ->
                    for(y in 0 until h) for(x in 0 until w) p[y*stride+x]=source.pixelAt(info.key.column*source.tileSize+x,info.key.row*source.tileSize+y)
                }
            }; write.commit().close()
        }; return output
    }

    fun project(source: PixelTileSurfaceSnapshot, width: Int, height: Int, turns: Int = 0,
                left: Float = 0f, top: Float = 0f, right: Float = 1f, bottom: Float = 1f): PixelTileSurface {
        val rotation = turns.mod(4)
        val rotatedWidth = if (rotation % 2 == 0) source.logicalWidth else source.logicalHeight
        val rotatedHeight = if (rotation % 2 == 0) source.logicalHeight else source.logicalWidth
        val rotated = if (rotation == 0) source else PixelTileSurface(rotatedWidth, rotatedHeight, source.tileSize, pixelFormat = source.pixelFormat).also { output ->
            output.beginWrite("rotate source").use { write ->
                source.tileInfo.forEach { info ->
                    val pixels = if (source.pixelFormat.isHighPrecision) null else source.copyTilePixels(info.key)
                    val floats = if (source.pixelFormat.isHighPrecision) source.copyTileF16(info.key) else null
                    for (y in 0 until source.tileSize) for (x in 0 until source.tileSize) {
                        val sx = info.key.column * source.tileSize + x; val sy = info.key.row * source.tileSize + y
                        val index = y * source.tileSize + x
                        val pixel = pixels?.get(index) ?: 0
                        val packed = floats?.get(index) ?: 0L
                        if (sx < source.logicalWidth && sy < source.logicalHeight && (pixel != 0 || packed != 0L)) {
                            val point = when (rotation) {
                                1 -> source.logicalHeight - 1 - sy to sx
                                2 -> source.logicalWidth - 1 - sx to source.logicalHeight - 1 - sy
                                else -> sy to source.logicalWidth - 1 - sx
                            }
                            if (floats != null) write.setLinearPixel(point.first, point.second, PackedRgbaF16.straight(packed))
                            else write.setPixel(point.first, point.second, pixel)
                        }
                    }
                }
                write.commit().close()
            }
        }.snapshot()
        val sx = width / (rotatedWidth * (right - left))
        val sy = height / (rotatedHeight * (bottom - top))
        return resample(rotated, width, height, sx, sy, -left * rotatedWidth * sx, -top * rotatedHeight * sy)
    }

    fun resample(source: PixelTileSurfaceSnapshot, width: Int, height: Int,
                 scaleX: Float, scaleY: Float, offsetX: Float = 0f, offsetY: Float = 0f, targetTileSize: Int = source.tileSize): PixelTileSurface {
        require(width in 1..8192 && height in 1..8192 && scaleX > 0 && scaleY > 0)
        val output = PixelTileSurface(width, height, targetTileSize, pixelFormat = source.pixelFormat)
        val candidates = linkedSetOf<PixelTileKey>()
        val tile = output.tileSize
        val sourceTile = source.tileSize
        source.tileInfo.forEach { info ->
            val left = floor(info.key.column * sourceTile * scaleX + offsetX - 1).toInt().coerceIn(0, width)
            val top = floor(info.key.row * sourceTile * scaleY + offsetY - 1).toInt().coerceIn(0, height)
            val right = ceil(minOf(source.logicalWidth, (info.key.column + 1) * sourceTile) * scaleX + offsetX + 1).toInt().coerceIn(0, width)
            val bottom = ceil(minOf(source.logicalHeight, (info.key.row + 1) * sourceTile) * scaleY + offsetY + 1).toInt().coerceIn(0, height)
            if (right > left && bottom > top) for (y in top / tile..(bottom - 1) / tile) for (x in left / tile..(right - 1) / tile) candidates += PixelTileKey(x, y)
        }
        fun pixel(x: Int, y: Int) = if (x in 0 until source.logicalWidth && y in 0 until source.logicalHeight) source.pixelAt(x, y) else 0
        output.beginWrite("resample canvas").use { write ->
            candidates.forEach { key ->
                val tw = minOf(tile, width - key.column * tile); val th = minOf(tile, height - key.row * tile)
                if (source.pixelFormat.isHighPrecision) {
                    write.editTileF16(key, PixelRect(0,0,tw,th)) { pixels,stride ->
                        for(y in 0 until th) for(x in 0 until tw) {
                            val sx=(key.column*tile+x+.5f-offsetX)/scaleX-.5f; val sy=(key.row*tile+y+.5f-offsetY)/scaleY-.5f
                            val ix=floor(sx).toInt(); val iy=floor(sy).toInt(); val tx=sx-ix; val ty=sy-iy
                            val a=source.linearPixelAt(ix,iy); val b=source.linearPixelAt(ix+1,iy)
                            val c=source.linearPixelAt(ix,iy+1); val d=source.linearPixelAt(ix+1,iy+1)
                            fun channel(f:(LinearRgba)->Float)=f(a)*(1-tx)*(1-ty)+f(b)*tx*(1-ty)+f(c)*(1-tx)*ty+f(d)*tx*ty
                            pixels[y*stride+x]=PackedRgbaF16.packPremultiplied(channel{it.red},channel{it.green},channel{it.blue},channel{it.alpha})
                        }
                    }
                } else
                write.editTile(key, PixelRect(0, 0, tw, th)) { pixels, stride ->
                    for (y in 0 until th) for (x in 0 until tw) {
                        val sx = (key.column * tile + x + .5f - offsetX) / scaleX - .5f
                        val sy = (key.row * tile + y + .5f - offsetY) / scaleY - .5f
                        val ix = floor(sx).toInt(); val iy = floor(sy).toInt()
                        val tx = sx - ix; val ty = sy - iy
                        val a = pixel(ix, iy); val b = pixel(ix + 1, iy); val c = pixel(ix, iy + 1); val d = pixel(ix + 1, iy + 1)
                        fun channel(shift: Int): Int {
                            val value = ((a ushr shift and 255) * (1-tx) * (1-ty) + (b ushr shift and 255) * tx * (1-ty) +
                                (c ushr shift and 255) * (1-tx) * ty + (d ushr shift and 255) * tx * ty)
                            return kotlin.math.round(value).toInt().coerceIn(0, 255)
                        }
                        pixels[y * stride + x] = (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                    }
                }
            }
            write.commit().close()
        }
        return output
    }
}
