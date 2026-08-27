package app.inkloom.engine.v5.storage

import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Bounded, versioned persistence for sparse one-byte alpha surfaces. */
internal object MaskTileArchiveCodec {
    private const val MAGIC = 0x4956354D // IV5M
    private const val VERSION = 5
    private const val MAX_DIMENSION = 65_536
    private const val MAX_TILE_COUNT = 262_144

    fun encode(snapshot: MaskTileSurfaceSnapshot): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeUTF(snapshot.surfaceId)
            output.writeInt(snapshot.logicalWidth)
            output.writeInt(snapshot.logicalHeight)
            output.writeInt(snapshot.tileSize)
            output.writeLong(snapshot.revision)
            output.writeByte(snapshot.defaultAlpha)
            val tiles = snapshot.tileInfo.sortedWith(compareBy({ it.key.row }, { it.key.column }))
            output.writeInt(tiles.size)
            tiles.forEach { info ->
                val raw = requireNotNull(snapshot.copyTileAlpha(info.key))
                val compressed = ByteArrayOutputStream().also { bytes ->
                    DeflaterOutputStream(bytes).use { it.write(raw) }
                }.toByteArray()
                output.writeInt(info.key.column)
                output.writeInt(info.key.row)
                output.writeInt(raw.size)
                output.writeLong(crc32(raw))
                output.writeInt(compressed.size)
                output.write(compressed)
            }
        }
        return payload.toByteArray()
    }

    fun decode(bytes: ByteArray): MaskTileSurface {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Not an Inkloom sparse mask" }
            require(input.readInt() == VERSION) { "Unsupported sparse mask version" }
            val surfaceId = input.readUTF().also { require(it.isNotBlank() && it.length <= 4_096) }
            val width = input.readInt().also { require(it in 1..MAX_DIMENSION) }
            val height = input.readInt().also { require(it in 1..MAX_DIMENSION) }
            val tileSize = input.readInt().also { require(it in MaskTileSurface.SUPPORTED_TILE_SIZES) }
            input.readLong().also { require(it >= 0L) }
            val defaultAlpha = input.readUnsignedByte()
            val tileCount = input.readInt()
            val maximumTiles = ((width + tileSize - 1) / tileSize) * ((height + tileSize - 1) / tileSize)
            require(tileCount in 0..minOf(MAX_TILE_COUNT, maximumTiles)) { "Invalid sparse mask tile count" }
            val expectedBytes = Math.multiplyExact(tileSize, tileSize)
            val decoded = linkedMapOf<PixelTileKey, ByteArray>()
            repeat(tileCount) {
                val key = PixelTileKey(input.readInt(), input.readInt())
                require(key !in decoded && key.column >= 0 && key.row >= 0 &&
                    key.column * tileSize.toLong() < width && key.row * tileSize.toLong() < height
                ) { "Invalid sparse mask tile key $key" }
                require(input.readInt() == expectedBytes) { "Unexpected sparse mask tile capacity" }
                val expectedCrc = input.readLong()
                val compressedSize = input.readInt()
                require(compressedSize in 1..expectedBytes * 2) { "Invalid sparse mask compressed size" }
                val compressed = ByteArray(compressedSize)
                input.readFully(compressed)
                val raw = InflaterInputStream(ByteArrayInputStream(compressed)).use { inflater ->
                    val output = ByteArrayOutputStream(expectedBytes)
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = inflater.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= expectedBytes) { "Sparse mask tile expands beyond its bound" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                require(raw.size == expectedBytes && crc32(raw) == expectedCrc) { "Sparse mask tile integrity failure" }
                decoded[key] = raw
            }
            require(input.available() == 0) { "Sparse mask has trailing data" }
            val surface = MaskTileSurface(width, height, tileSize, surfaceId, defaultAlpha)
            if (decoded.isNotEmpty()) surface.beginWrite("restore sparse mask asset").run {
                decoded.forEach { (key, alpha) ->
                    val tileWidth = minOf(tileSize, width - key.column * tileSize)
                    val tileHeight = minOf(tileSize, height - key.row * tileSize)
                    editTile(key, PixelRect(0, 0, tileWidth, tileHeight)) { target, _ -> alpha.copyInto(target) }
                }
                commit().close()
            }
            return surface
        }
    }

    fun isSparseMask(bytes: ByteArray): Boolean = bytes.size >= Int.SIZE_BYTES &&
        DataInputStream(ByteArrayInputStream(bytes)).use { it.readInt() == MAGIC }

    private fun crc32(bytes: ByteArray): Long = CRC32().run { update(bytes); value }
}
