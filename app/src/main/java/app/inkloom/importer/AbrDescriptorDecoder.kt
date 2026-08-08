package app.inkloom.importer

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Geometry stored in an ABR 6.x brush preset. Percentage values are normalized to 0..1. */
data class AbrBrushSettings(
    val sampleId: String? = null,
    val name: String? = null,
    val spacingPercent: Float? = null,
    val hardnessPercent: Float? = null,
    val roundnessPercent: Float? = null,
    val angleDegrees: Float? = null,
    val diameterPixels: Float? = null,
)

/**
 * Decoder for Photoshop's ABR 6.x `desc` resource. The resource is an Adobe Action
 * Descriptor: IDs can be either four-byte OSType values or length-prefixed strings,
 * descriptors contain Unicode names and lists contain typed values. Malformed or unknown
 * files fail closed so sampled-tip extraction can still fall back safely.
 */
object AbrDescriptorDecoder {
    private const val DESCRIPTOR_TAG = "desc"
    private const val DESCRIPTOR_VERSION = 16
    private const val KEY_PRESETS = "Brsh"
    private const val KEY_NAME = "Nm  "
    private const val KEY_SAMPLE_ID = "sampledData"
    private const val KEY_DIAMETER = "Dmtr"
    private const val KEY_SPACING = "Spcn"
    private const val KEY_HARDNESS = "Hrdn"
    private const val KEY_ROUNDNESS = "Rndn"
    private const val KEY_ANGLE = "Angl"

    fun decode(file: File): AbrBrushSettings? = decodeAll(file).firstOrNull()

    fun decodeAll(file: File): List<AbrBrushSettings> = runCatching { decodeAllStrict(file) }.getOrDefault(emptyList())

    internal fun decodeAllStrict(file: File): List<AbrBrushSettings> = RandomAccessFile(file, "r").use { input ->
        require(input.length() >= 16) { "ABR file is truncated" }
        val version = input.readUnsignedShort()
        val subVersion = input.readUnsignedShort()
        require(version == 6 && subVersion in 1..2) { "ABR $version.$subVersion descriptors are unsupported" }
        val block = findBlock(input, DESCRIPTOR_TAG) ?: return@use emptyList()
        val size = block.second - block.first
        require(size in 4..MAX_DESCRIPTOR_BYTES.toLong()) { "ABR descriptor block is too large" }
        val bytes = ByteArray(size.toInt())
        input.seek(block.first)
        input.readFully(bytes)
        val reader = DescriptorReader(bytes)
        require(reader.readInt() == DESCRIPTOR_VERSION) { "Unsupported Action Descriptor version" }
        val top = reader.readDescriptor()
        val presets = top[KEY_PRESETS] as? List<*> ?: return@use emptyList()
        presets.mapNotNull(::settingsFromPreset)
    }

    private fun settingsFromPreset(value: Any?): AbrBrushSettings? {
        val preset = value as? Map<*, *> ?: return null
        val brush = preset[KEY_PRESETS] as? Map<*, *> ?: preset
        fun unit(key: String): UnitValue? = brush[key] as? UnitValue
        val sampleId = (brush[KEY_SAMPLE_ID] as? String)?.trim()?.takeIf(String::isNotBlank)
        val name = ((preset[KEY_NAME] ?: brush[KEY_NAME]) as? String)?.trimEnd('\u0000')?.takeIf(String::isNotBlank)
        return AbrBrushSettings(
            sampleId = sampleId,
            name = name,
            spacingPercent = unit(KEY_SPACING)?.normalizedPercent(),
            hardnessPercent = unit(KEY_HARDNESS)?.normalizedPercent(),
            roundnessPercent = unit(KEY_ROUNDNESS)?.normalizedPercent(),
            angleDegrees = unit(KEY_ANGLE)?.value?.toFloat(),
            diameterPixels = unit(KEY_DIAMETER)?.value?.toFloat(),
        )
    }

    private fun findBlock(input: RandomAccessFile, tag: String): Pair<Long, Long>? {
        input.seek(4)
        while (input.filePointer + 12 <= input.length()) {
            val signature = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
            val blockTag = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
            val size = input.readInt().toLong() and 0xffff_ffffL
            val start = input.filePointer
            require(size <= input.length() - start) { "ABR resource exceeds the file boundary" }
            val end = start + size
            if (signature == "8BIM" && blockTag == tag) return start to end
            input.seek(end)
        }
        return null
    }

    private data class UnitValue(val unit: String, val value: Double) {
        fun normalizedPercent(): Float = when (unit) {
            "#Prc", "#Pct", "#Rlt" -> (value / 100.0).toFloat()
            else -> value.toFloat()
        }
    }

    private class DescriptorReader(private val bytes: ByteArray) {
        private var position = 0

        fun readDescriptor(): Map<String, Any?> {
            readUnicodeString() // descriptor display name
            readId() // class ID
            val count = readBoundedCount(MAX_DESCRIPTOR_ITEMS)
            return buildMap(count) {
                repeat(count) {
                    val key = readId()
                    val type = readOstype()
                    put(key, readValue(type))
                }
            }
        }

        private fun readValue(type: String): Any? = when (type) {
            "Objc", "GlbO" -> readDescriptor()
            "VlLs" -> List(readBoundedCount(MAX_LIST_ITEMS)) { readValue(readOstype()) }
            "doub" -> readDouble()
            "UntF" -> UnitValue(readOstype(), readDouble())
            "UnFl" -> UnitValue(readOstype(), readFloat().toDouble())
            "TEXT", "name" -> readUnicodeString()
            "enum" -> readId() to readId()
            "long" -> readInt()
            "comp" -> readLong()
            "bool" -> readUnsignedByte() != 0
            "type", "GlbC", "Clss" -> readId()
            "alis", "tdta", "Pth " -> readByteBlock()
            "obj " -> readReference()
            "Idnt", "indx" -> readInt()
            "rele" -> Triple(readInt(), readInt(), readInt())
            else -> error("Unsupported Action Descriptor value type '$type'")
        }

        private fun readReference(): List<Any?> = List(readBoundedCount(MAX_LIST_ITEMS)) {
            when (val type = readOstype()) {
                "prop" -> listOf(type, readId(), readId())
                "Clss" -> listOf(type, readId())
                "Enmr" -> listOf(type, readId(), readId(), readId())
                "rele" -> listOf(type, readId(), readInt())
                "Idnt", "indx" -> listOf(type, readId(), readInt())
                "name" -> listOf(type, readId(), readUnicodeString())
                else -> error("Unsupported Action Reference type '$type'")
            }
        }

        private fun readUnicodeString(): String {
            val length = readBoundedCount(MAX_STRING_LENGTH)
            val byteCount = Math.multiplyExact(length, 2)
            requireAvailable(byteCount)
            val result = bytes.copyOfRange(position, position + byteCount).toString(Charsets.UTF_16BE)
            position += byteCount
            return result.trimEnd('\u0000')
        }

        private fun readId(): String {
            val length = readInt()
            require(length in 0..MAX_STRING_LENGTH) { "Invalid Action Descriptor ID length" }
            return readAscii(if (length == 0) 4 else length)
        }

        private fun readByteBlock(): ByteArray {
            val length = readBoundedCount(MAX_DESCRIPTOR_BYTES)
            requireAvailable(length)
            return bytes.copyOfRange(position, position + length).also { position += length }
        }

        private fun readBoundedCount(maximum: Int): Int = readInt().also { require(it in 0..maximum) { "Action Descriptor count is invalid" } }

        private fun readOstype(): String = readAscii(4)

        private fun readAscii(length: Int): String {
            requireAvailable(length)
            return bytes.copyOfRange(position, position + length).toString(Charsets.ISO_8859_1).also { position += length }
        }

        fun readInt(): Int {
            requireAvailable(4)
            return ByteBuffer.wrap(bytes, position, 4).order(ByteOrder.BIG_ENDIAN).int.also { position += 4 }
        }

        private fun readLong(): Long {
            requireAvailable(8)
            return ByteBuffer.wrap(bytes, position, 8).order(ByteOrder.BIG_ENDIAN).long.also { position += 8 }
        }

        private fun readFloat(): Float {
            requireAvailable(4)
            return ByteBuffer.wrap(bytes, position, 4).order(ByteOrder.BIG_ENDIAN).float.also { position += 4 }
        }

        private fun readDouble(): Double {
            requireAvailable(8)
            return ByteBuffer.wrap(bytes, position, 8).order(ByteOrder.BIG_ENDIAN).double.also { position += 8 }
        }

        private fun readUnsignedByte(): Int {
            requireAvailable(1)
            return bytes[position++].toInt() and 0xff
        }

        private fun requireAvailable(count: Int) {
            require(count >= 0 && position <= bytes.size - count) { "Action Descriptor is truncated" }
        }
    }

    private const val MAX_DESCRIPTOR_BYTES = 64 * 1024 * 1024
    private const val MAX_DESCRIPTOR_ITEMS = 100_000
    private const val MAX_LIST_ITEMS = 10_000
    private const val MAX_STRING_LENGTH = 1_000_000
}
