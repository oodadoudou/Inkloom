package app.inkloom.importer

import java.io.File
import java.io.RandomAccessFile

/** Brush geometry settings carried by a Photoshop ABR brush-preset descriptor ("brsh" block).
 *  Values are nullable: absent when the descriptor or a given key is missing. */
data class AbrBrushSettings(
    val spacingPercent: Float? = null,
    val hardnessPercent: Float? = null,
    val roundnessPercent: Float? = null,
    val angleDegrees: Float? = null,
    val diameterPixels: Float? = null,
)

/**
 * Defensive reader for the Photoshop descriptor block used by ABR 6.x brush presets.
 * It walks the descriptor value tree and pulls the well-known brush keys out of the
 * brush-settings sub-descriptor. Any malformed byte sequence safely yields nulls --
 * it never throws and never invalidates tip extraction.
 */
object AbrDescriptorDecoder {
    private const val BRUSH_TAG = "brsh"
    private const val KEY_SETTINGS = "Brsh"
    private const val KEY_DIAMETER = "Dmtr"
    private const val KEY_SPACING = "Spcn"
    private const val KEY_HARDNESS = "Hrdn"
    private const val KEY_ROUNDNESS = "Rndn"
    private const val KEY_ANGLE = "Angl"

    fun decode(file: File): AbrBrushSettings? = RandomAccessFile(file, "r").use { input ->
        if (input.length() < 12) return@use null
        input.seek(0)
        val version = input.readUnsignedShort()
        val subVersion = input.readUnsignedShort()
        if (version != 6 || subVersion !in 1..2) return@use null
        val block = findBlock(input, BRUSH_TAG) ?: return@use null
        val bytes = ByteArray((block.second - block.first).toInt())
        input.seek(block.first); input.readFully(bytes)
        val reader = DescriptorReader(bytes)
        // The brsh block begins with the brush type OSType (4 bytes) then a descriptor.
        reader.skip(4)
        val top = reader.readDescriptor() ?: return@use null
        val settingsDescriptor = top[KEY_SETTINGS] as? Map<*, *> ?: return@use null
        AbrBrushSettings(
            spacingPercent = settingsDescriptor[KEY_SPACING] as? Float,
            hardnessPercent = settingsDescriptor[KEY_HARDNESS] as? Float,
            roundnessPercent = settingsDescriptor[KEY_ROUNDNESS] as? Float,
            angleDegrees = settingsDescriptor[KEY_ANGLE] as? Float,
            diameterPixels = settingsDescriptor[KEY_DIAMETER] as? Float,
        )
    }

    private fun findBlock(input: RandomAccessFile, tag: String): Pair<Long, Long>? {
        input.seek(4)
        while (input.filePointer + 12 <= input.length()) {
            val signature = ByteArray(4).also(input::readFully).decodeToString()
            val blockTag = ByteArray(4).also(input::readFully).decodeToString()
            val size = readUnsignedInt(input)
            val start = input.filePointer
            val end = start + size
            if (end > input.length()) return null
            if (signature == "8BIM" && blockTag == tag) return start to end
            input.seek(end)
        }
        return null
    }

    private fun readUnsignedInt(input: RandomAccessFile): Long {
        if (input.filePointer + 4 > input.length()) return 0L
        return input.readInt().toLong() and 0xffff_ffffL
    }

    /** Bounded byte cursor that returns null on any out-of-range or malformed read. */
    private class DescriptorReader(private val bytes: ByteArray) {
        private var position: Int = 0
        fun skip(count: Int): Boolean {
            if (position + count > bytes.size || count < 0) return false
            position += count
            return true
        }

        private fun readByte(): Int? {
            if (position >= bytes.size) return null
            return bytes[position++].toInt() and 0xff
        }

        private fun readShort(): Int? {
            if (position + 2 > bytes.size) return null
            val value = ((bytes[position].toInt() and 0xff) shl 8) or (bytes[position + 1].toInt() and 0xff)
            position += 2
            return value
        }

        private fun readInt(): Int? {
            if (position + 4 > bytes.size) return null
            var value = 0
            repeat(4) { value = (value shl 8) or (bytes[position++].toInt() and 0xff) }
            return value
        }

        private fun readDouble(): Double? {
            if (position + 8 > bytes.size) return null
            val bits = java.nio.ByteBuffer.wrap(bytes, position, 8).order(java.nio.ByteOrder.BIG_ENDIAN).double
            position += 8
            return bits
        }

        private fun readOstype(): String? {
            if (position + 4 > bytes.size) return null
            val value = bytes.copyOfRange(position, position + 4).decodeToString()
            position += 4
            return value
        }

        /** Reads a descriptor, returning the key -> value map. Returns null on malformed data. */
        fun readDescriptor(): Map<String, Any?>? {
            val classId = readOstype() ?: return null
            val version = readInt() ?: return null
            val keyType = readOstype() ?: return null
            val count = readInt() ?: return null
            if (count < 0 || count > 10_000) return null
            val result = linkedMapOf<String, Any?>()
            repeat(count) {
                val key = readOstype() ?: return null
                val value = readValue() ?: return null
                result[key] = value
            }
            return result
        }

        private fun readValue(): Any? {
            val type = readOstype() ?: return null
            return when (type) {
                "bool" -> { val b = readByte() ?: return null; b != 0 }
                "type" -> readOstype()
                "enum" -> { readOstype() ?: return null; readOstype() }
                "long" -> readInt()?.toLong()
                "comp" -> readCompressedInt()
                "doub" -> readDouble()
                "UntF" -> {
                    val unit = readOstype() ?: return null
                    val value = readDouble() ?: return null
                    // Map percentage units to 0..1 fractions; pixels/angle to absolute values.
                    when (unit) {
                        "#Pct" -> (value / 100f).toFloat()
                        "#Rlt" -> (value / 100f).toFloat()
                        else -> value.toFloat()
                    }
                }
                "TEXT", "alis", "Pth ", "tdta" -> {
                    val length = readInt() ?: return null
                    if (length < 0 || !skip(length)) return null
                    null
                }
                "VlLs" -> {
                    val count = readInt() ?: return null
                    if (count < 0 || count > 100_000) return null
                    repeat(count) { readValue() ?: return null }
                    null
                }
                "ObJC" -> readDescriptor()
                "FRef", "refe" -> readReference()
                "Idnt" -> { if (!skip(16)) return null; null }
                "indx" -> { if (!skip(2)) return null; readInt(); null }
                else -> null
            }
        }

        private fun readCompressedInt(): Int? {
            val first = readByte() ?: return null
            if (first and 0x80 == 0) return first
            var value = 0
            repeat(4) {
                val b = readByte() ?: return null
                value = (value shl 8) or b
            }
            return value
        }

        private fun readReference(): Any? {
            val count = readInt() ?: return null
            if (count < 0 || count > 10_000) return null
            repeat(count) {
                val type = readOstype() ?: return null
                readValue() ?: return null
            }
            return null
        }
    }
}
