package app.inkloom.importer

import java.nio.charset.StandardCharsets

data class PlistUid(val value: Int)

/**
 * Bounds-checked reader for the binary-plist subset used by NSKeyedArchiver.
 * It deliberately returns UIDs without resolving them because keyed archives
 * use UIDs as references into their `$objects` table.
 */
class BinaryPlistReader(private val bytes: ByteArray) {
    private val offsetSize: Int
    private val objectReferenceSize: Int
    private val objectCount: Int
    private val topObject: Int
    private val offsetTableOffset: Int
    private val offsets: IntArray
    private val cache: Array<Any?>
    private val parsed: BooleanArray

    init {
        require(bytes.size in MINIMUM_SIZE..MAX_BYTES) { "Binary plist is empty or exceeds the safety limit" }
        require(bytes.copyOfRange(0, HEADER.size).contentEquals(HEADER)) { "Binary plist signature is missing" }
        val trailer = bytes.size - TRAILER_SIZE
        offsetSize = unsignedByte(trailer + 6)
        objectReferenceSize = unsignedByte(trailer + 7)
        require(offsetSize in 1..8 && objectReferenceSize in 1..8) { "Binary plist uses unsupported index widths" }
        val rawCount = unsignedLong(trailer + 8, 8)
        val rawTop = unsignedLong(trailer + 16, 8)
        val rawOffsetTable = unsignedLong(trailer + 24, 8)
        require(rawCount in 1..MAX_OBJECTS.toLong()) { "Binary plist has too many objects" }
        require(rawTop in 0 until rawCount) { "Binary plist root object is invalid" }
        require(rawOffsetTable in HEADER.size.toLong() until trailer.toLong()) { "Binary plist offset table is invalid" }
        objectCount = rawCount.toInt()
        topObject = rawTop.toInt()
        offsetTableOffset = rawOffsetTable.toInt()
        require(offsetTableOffset.toLong() + objectCount.toLong() * offsetSize <= trailer.toLong()) { "Binary plist offset table is truncated" }
        offsets = IntArray(objectCount) { index ->
            val value = unsignedLong(offsetTableOffset + index * offsetSize, offsetSize)
            require(value in HEADER.size.toLong() until offsetTableOffset.toLong()) { "Binary plist object offset is invalid" }
            value.toInt()
        }
        cache = arrayOfNulls(objectCount)
        parsed = BooleanArray(objectCount)
    }

    fun read(): Any? = parseObject(topObject, 0)

    private fun parseObject(index: Int, depth: Int): Any? {
        require(index in 0 until objectCount) { "Binary plist object reference is invalid" }
        require(depth <= MAX_DEPTH) { "Binary plist nesting is too deep" }
        if (parsed[index]) return cache[index]
        val offset = offsets[index]
        val marker = unsignedByte(offset)
        val type = marker ushr 4
        val info = marker and 0x0f
        val result: Any? = when (type) {
            0x0 -> when (info) {
                0x0 -> null
                0x8 -> false
                0x9 -> true
                else -> error("Unsupported binary plist simple value")
            }
            0x1 -> readSignedInteger(offset + 1, byteCount(info))
            0x2 -> readReal(offset + 1, byteCount(info))
            0x3 -> {
                require(info == 3) { "Binary plist date has an invalid width" }
                readDouble(offset + 1)
            }
            0x4 -> readSized(offset, info) { start, length -> bytes.copyOfRange(start, checkedEnd(start, length)) }
            0x5 -> readSized(offset, info) { start, length ->
                String(bytes, start, checkedEnd(start, length) - start, StandardCharsets.US_ASCII)
            }
            0x6 -> readSized(offset, info) { start, length ->
                val byteLength = checkedMultiply(length, 2)
                String(bytes, start, checkedEnd(start, byteLength) - start, StandardCharsets.UTF_16BE)
            }
            0x8 -> {
                val width = info + 1
                val value = unsignedLong(offset + 1, width)
                require(value <= Int.MAX_VALUE) { "Binary plist UID is too large" }
                PlistUid(value.toInt())
            }
            0xA, 0xC -> readSized(offset, info) { start, length ->
                require(length <= MAX_COLLECTION_ITEMS) { "Binary plist collection is too large" }
                List(length) { item -> parseObject(readReference(start + item * objectReferenceSize), depth + 1) }
            }
            0xD -> readSized(offset, info) { start, length ->
                require(length <= MAX_COLLECTION_ITEMS) { "Binary plist dictionary is too large" }
                val valuesStart = checkedEnd(start, checkedMultiply(length, objectReferenceSize))
                buildMap<String, Any?> {
                    repeat(length) { item ->
                        val key = parseObject(readReference(start + item * objectReferenceSize), depth + 1) as? String
                            ?: error("Binary plist dictionary key is not text")
                        val value = parseObject(readReference(valuesStart + item * objectReferenceSize), depth + 1)
                        put(key, value)
                    }
                }
            }
            else -> error("Unsupported binary plist object type 0x${type.toString(16)}")
        }
        parsed[index] = true
        cache[index] = result
        return result
    }

    private fun <T> readSized(offset: Int, info: Int, block: (Int, Int) -> T): T {
        val (length, payloadStart) = if (info < 0xF) {
            info to (offset + 1)
        } else {
            val lengthMarker = unsignedByte(offset + 1)
            require(lengthMarker ushr 4 == 0x1) { "Binary plist extended length is not an integer" }
            val width = byteCount(lengthMarker and 0x0f)
            val rawLength = unsignedLong(offset + 2, width)
            require(rawLength <= Int.MAX_VALUE) { "Binary plist value is too large" }
            rawLength.toInt() to (offset + 2 + width)
        }
        require(length >= 0) { "Binary plist value has a negative length" }
        return block(payloadStart, length)
    }

    private fun readReference(offset: Int): Int {
        val value = unsignedLong(offset, objectReferenceSize)
        require(value < objectCount) { "Binary plist object reference is out of range" }
        return value.toInt()
    }

    private fun byteCount(power: Int): Int {
        require(power in 0..3) { "Binary plist numeric width is unsupported" }
        return 1 shl power
    }

    private fun readSignedInteger(offset: Int, count: Int): Long {
        val unsigned = unsignedLong(offset, count)
        if (count == 8) return unsigned
        val signBit = 1L shl (count * 8 - 1)
        return if (unsigned and signBit == 0L) unsigned else unsigned - (1L shl (count * 8))
    }

    private fun readReal(offset: Int, count: Int): Double = when (count) {
        4 -> Float.fromBits(unsignedLong(offset, count).toInt()).toDouble()
        8 -> readDouble(offset)
        else -> error("Binary plist real width is unsupported")
    }

    private fun readDouble(offset: Int): Double = Double.fromBits(unsignedLong(offset, 8))

    private fun unsignedLong(offset: Int, count: Int): Long {
        require(count in 1..8) { "Binary plist integer width is invalid" }
        checkedEnd(offset, count)
        var value = 0L
        repeat(count) { value = (value shl 8) or unsignedByte(offset + it).toLong() }
        return value
    }

    private fun checkedEnd(start: Int, length: Int): Int {
        require(start >= 0 && length >= 0 && start.toLong() + length <= bytes.size.toLong()) { "Binary plist value is truncated" }
        return start + length
    }

    private fun checkedMultiply(left: Int, right: Int): Int {
        val value = left.toLong() * right
        require(value <= Int.MAX_VALUE) { "Binary plist value is too large" }
        return value.toInt()
    }

    private fun unsignedByte(index: Int): Int {
        require(index in bytes.indices) { "Binary plist is truncated" }
        return bytes[index].toInt() and 0xff
    }

    private companion object {
        val HEADER = "bplist00".encodeToByteArray()
        const val TRAILER_SIZE = 32
        const val MINIMUM_SIZE = 40
        const val MAX_BYTES = 16 * 1024 * 1024
        const val MAX_OBJECTS = 20_000
        const val MAX_COLLECTION_ITEMS = 20_000
        const val MAX_DEPTH = 64
    }
}
