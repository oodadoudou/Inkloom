package app.inkloom.engine.v3.storage

import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.surface.PixelTileFormat
import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.engine.v3.surface.PixelTileSurfaceSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Versioned, content-addressed persistence boundary for sparse premultiplied-ARGB tile surfaces.
 *
 * The manifest and every compressed tile blob are independently verified. Decoding has an exact
 * output bound, so a corrupted or hostile blob cannot expand beyond one declared tile. File IO and
 * atomic publication are intentionally owned by the document asset store, not this pure codec.
 */
internal object PixelTileArchiveCodec {
    private const val FORMAT = "inkloom-pixel-tiles"
    private const val SCHEMA_VERSION = 2
    private const val RGBA8_CODEC = "zlib-int32-le"
    private const val F16_CODEC = "zlib-rgba-half4-le"
    private const val MAX_LOGICAL_DIMENSION = 65_536
    private const val MAX_RESIDENT_TILE_COUNT = 16_384
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Envelope(
        val format: String,
        val schemaVersion: Int,
        val checksum: String,
        val payload: String,
    )

    @Serializable
    private data class Manifest(
        val surfaceId: String,
        val logicalWidth: Int,
        val logicalHeight: Int,
        val tileSize: Int,
        val pixelFormat: String,
        val revision: Long,
        val tiles: List<TileEntry>,
    )

    @Serializable
    private data class TileEntry(
        val column: Int,
        val row: Int,
        val blobSha256: String,
        val codec: String,
        val uncompressedBytes: Int,
        val compressedBytes: Int,
        val rawCrc32: String,
    )

    data class Encoded(
        val manifest: String,
        /** Unique content-addressed blobs. Identical tile pixels share one entry. */
        val blobs: Map<String, ByteArray>,
    )

    data class Metadata(
        val surfaceId: String,
        val logicalWidth: Int,
        val logicalHeight: Int,
        val tileSize: Int,
        val pixelFormat: PixelTileFormat,
        val revision: Long,
        val tileCount: Int,
        val blobHashes: Set<String>,
    )

    fun encode(snapshot: PixelTileSurfaceSnapshot): Encoded {
        val codec = codecFor(snapshot.pixelFormat)
        val blobs = linkedMapOf<String, ByteArray>()
        val tileEntries = snapshot.tileInfo
            .sortedWith(compareBy<app.inkloom.engine.v3.surface.PixelTileInfo> { it.key.row }.thenBy { it.key.column })
            .map { info ->
                val raw = when (snapshot.pixelFormat) {
                    PixelTileFormat.PREMULTIPLIED_SRGB_8888 ->
                        encodeLittleEndian(requireNotNull(snapshot.copyTilePixels(info.key)))
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 ->
                        encodeLittleEndian(requireNotNull(snapshot.copyTileF16(info.key)))
                }
                val compressed = deflate(raw)
                val hash = sha256(compressed)
                blobs.putIfAbsent(hash, compressed)
                TileEntry(
                    column = info.key.column,
                    row = info.key.row,
                    blobSha256 = hash,
                    codec = codec,
                    uncompressedBytes = raw.size,
                    compressedBytes = compressed.size,
                    rawCrc32 = crc32(raw),
                )
            }
        val payload = json.encodeToString(
            Manifest(
                surfaceId = snapshot.surfaceId,
                logicalWidth = snapshot.logicalWidth,
                logicalHeight = snapshot.logicalHeight,
                tileSize = snapshot.tileSize,
                pixelFormat = snapshot.pixelFormat.manifestId,
                revision = snapshot.revision,
                tiles = tileEntries,
            ),
        )
        return Encoded(
            manifest = json.encodeToString(Envelope(FORMAT, SCHEMA_VERSION, crc32(payload.encodeToByteArray()), payload)),
            blobs = blobs,
        )
    }

    fun decode(manifestText: String, resolveBlob: (sha256: String) -> ByteArray?): PixelTileSurface {
        val manifest = verifiedManifest(manifestText)
        val pixelFormat = PixelTileFormat.fromManifestId(manifest.pixelFormat)
        val codec = codecFor(pixelFormat)
        val expectedBytes = Math.multiplyExact(
            Math.multiplyExact(manifest.tileSize, manifest.tileSize),
            pixelFormat.bytesPerPixel,
        )
        val restoredRgba8 = linkedMapOf<PixelTileKey, IntArray>()
        val restoredF16 = linkedMapOf<PixelTileKey, LongArray>()
        manifest.tiles.forEach { entry ->
            require(entry.codec == codec) { "Unsupported tile codec ${entry.codec}" }
            require(entry.uncompressedBytes == expectedBytes) { "Unexpected tile byte capacity" }
            require(entry.compressedBytes > 0) { "Invalid compressed tile size" }
            val key = PixelTileKey(entry.column, entry.row)
            require(key !in restoredRgba8 && key !in restoredF16) { "Duplicate tile key $key" }
            require(entry.column >= 0 && entry.row >= 0 &&
                entry.column * manifest.tileSize.toLong() < manifest.logicalWidth &&
                entry.row * manifest.tileSize.toLong() < manifest.logicalHeight
            ) { "Tile $key is outside the logical surface" }
            val blob = requireNotNull(resolveBlob(entry.blobSha256)) { "Missing tile blob ${entry.blobSha256}" }
            require(blob.size == entry.compressedBytes) { "Compressed tile size mismatch" }
            require(sha256(blob) == entry.blobSha256) { "Tile blob SHA-256 mismatch" }
            val raw = inflateExact(blob, expectedBytes)
            require(crc32(raw) == entry.rawCrc32) { "Tile raw CRC mismatch" }
            when (pixelFormat) {
                PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> restoredRgba8[key] = decodeIntLittleEndian(raw)
                PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> restoredF16[key] = decodeLongLittleEndian(raw)
            }
        }
        return when (pixelFormat) {
            PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> PixelTileSurface.restore(
                logicalWidth = manifest.logicalWidth,
                logicalHeight = manifest.logicalHeight,
                tileSize = manifest.tileSize,
                surfaceId = manifest.surfaceId,
                revision = manifest.revision,
                tiles = restoredRgba8,
                copyTiles = false,
            )
            PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> PixelTileSurface.restoreF16(
                logicalWidth = manifest.logicalWidth,
                logicalHeight = manifest.logicalHeight,
                tileSize = manifest.tileSize,
                surfaceId = manifest.surfaceId,
                revision = manifest.revision,
                tiles = restoredF16,
                copyTiles = false,
            )
        }
    }

    fun inspect(manifestText: String): Metadata = verifiedManifest(manifestText).let { manifest ->
        Metadata(
            surfaceId = manifest.surfaceId,
            logicalWidth = manifest.logicalWidth,
            logicalHeight = manifest.logicalHeight,
            tileSize = manifest.tileSize,
            pixelFormat = PixelTileFormat.fromManifestId(manifest.pixelFormat),
            revision = manifest.revision,
            tileCount = manifest.tiles.size,
            blobHashes = manifest.tiles.mapTo(linkedSetOf(), TileEntry::blobSha256),
        )
    }

    private fun verifiedManifest(manifestText: String): Manifest {
        val envelope = json.decodeFromString<Envelope>(manifestText)
        require(envelope.format == FORMAT) { "Unsupported tile manifest format ${envelope.format}" }
        require(envelope.schemaVersion in 1..SCHEMA_VERSION) {
            "Unsupported tile manifest schema ${envelope.schemaVersion}"
        }
        require(envelope.checksum == crc32(envelope.payload.encodeToByteArray())) {
            "Tile manifest checksum mismatch"
        }
        val manifest = json.decodeFromString<Manifest>(envelope.payload)
        require(manifest.logicalWidth in 1..MAX_LOGICAL_DIMENSION &&
            manifest.logicalHeight in 1..MAX_LOGICAL_DIMENSION
        ) { "Invalid logical surface bounds" }
        require(manifest.tileSize in PixelTileSurface.SUPPORTED_TILE_SIZES) { "Unsupported tile size" }
        val pixelFormat = PixelTileFormat.fromManifestId(manifest.pixelFormat)
        val codec = codecFor(pixelFormat)
        require(manifest.revision >= 0L) { "Invalid surface revision" }
        val maximumAddressableTiles =
            ((manifest.logicalWidth + manifest.tileSize - 1L) / manifest.tileSize) *
                ((manifest.logicalHeight + manifest.tileSize - 1L) / manifest.tileSize)
        require(manifest.tiles.size.toLong() <= maximumAddressableTiles) { "Manifest has too many tile entries" }
        require(manifest.tiles.size <= MAX_RESIDENT_TILE_COUNT) { "Manifest exceeds the resident tile safety bound" }
        val seenKeys = hashSetOf<PixelTileKey>()
        manifest.tiles.forEach { entry ->
            val key = PixelTileKey(entry.column, entry.row)
            require(seenKeys.add(key)) { "Duplicate tile key $key" }
            require(entry.column >= 0 && entry.row >= 0 &&
                entry.column * manifest.tileSize.toLong() < manifest.logicalWidth &&
                entry.row * manifest.tileSize.toLong() < manifest.logicalHeight
            ) { "Tile $key is outside the logical surface" }
            require(entry.codec == codec) { "Unsupported tile codec ${entry.codec}" }
            require(entry.uncompressedBytes == manifest.tileSize * manifest.tileSize * pixelFormat.bytesPerPixel) {
                "Unexpected tile byte capacity"
            }
            require(entry.compressedBytes > 0) { "Invalid compressed tile size" }
            require(entry.blobSha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid tile blob hash" }
            require(entry.rawCrc32.matches(Regex("[0-9a-f]{8}"))) { "Invalid tile CRC" }
        }
        return manifest
    }

    private fun encodeLittleEndian(pixels: IntArray): ByteArray {
        val output = ByteArray(Math.multiplyExact(pixels.size, Int.SIZE_BYTES))
        var offset = 0
        pixels.forEach { pixel ->
            output[offset++] = pixel.toByte()
            output[offset++] = (pixel ushr 8).toByte()
            output[offset++] = (pixel ushr 16).toByte()
            output[offset++] = (pixel ushr 24).toByte()
        }
        return output
    }

    private fun encodeLittleEndian(pixels: LongArray): ByteArray {
        val output = ByteArray(Math.multiplyExact(pixels.size, Long.SIZE_BYTES))
        var offset = 0
        pixels.forEach { pixel ->
            repeat(Long.SIZE_BYTES) { byteIndex ->
                output[offset++] = (pixel ushr (byteIndex * 8)).toByte()
            }
        }
        return output
    }

    private fun decodeIntLittleEndian(bytes: ByteArray): IntArray {
        require(bytes.size % Int.SIZE_BYTES == 0)
        return IntArray(bytes.size / Int.SIZE_BYTES) { index ->
            val offset = index * Int.SIZE_BYTES
            (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                ((bytes[offset + 3].toInt() and 0xff) shl 24)
        }
    }

    private fun decodeLongLittleEndian(bytes: ByteArray): LongArray {
        require(bytes.size % Long.SIZE_BYTES == 0)
        return LongArray(bytes.size / Long.SIZE_BYTES) { index ->
            val offset = index * Long.SIZE_BYTES
            var value = 0L
            repeat(Long.SIZE_BYTES) { byteIndex ->
                value = value or ((bytes[offset + byteIndex].toLong() and 0xffL) shl (byteIndex * 8))
            }
            value
        }
    }

    private fun codecFor(pixelFormat: PixelTileFormat): String = when (pixelFormat) {
        PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> RGBA8_CODEC
        PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> F16_CODEC
    }

    private fun deflate(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED, false)
        return try {
            deflater.setInput(raw)
            deflater.finish()
            val output = ByteArrayOutputStream(raw.size / 4)
            val buffer = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                check(count > 0 || deflater.finished()) { "Tile compressor made no progress" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflateExact(compressed: ByteArray, expectedBytes: Int): ByteArray {
        val inflater = Inflater(false)
        return try {
            inflater.setInput(compressed)
            val output = ByteArray(expectedBytes)
            var offset = 0
            while (!inflater.finished() && offset < output.size) {
                val count = try {
                    inflater.inflate(output, offset, output.size - offset)
                } catch (failure: DataFormatException) {
                    throw IllegalArgumentException("Tile blob is not valid zlib data", failure)
                }
                if (count == 0) {
                    require(!inflater.needsDictionary()) { "Tile blob needs an unsupported dictionary" }
                    require(!inflater.needsInput()) { "Tile blob ended before one tile was decoded" }
                    throw IllegalArgumentException("Tile decoder made no progress")
                }
                offset += count
            }
            require(inflater.finished() && offset == expectedBytes) { "Tile blob has an invalid expanded size" }
            require(inflater.remaining == 0) { "Tile blob contains trailing compressed data" }
            output
        } finally {
            inflater.end()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun crc32(bytes: ByteArray): String = CRC32().run {
        update(bytes)
        value.toString(16).padStart(8, '0')
    }
}
