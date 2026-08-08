package app.inkloom.importer

import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile

data class AbrSample(
    val id: String,
    val width: Int,
    val height: Int,
    /** Photoshop grayscale: 0 is transparent and 255 is fully painted. */
    val opacity: ByteArray,
    val compressed: Boolean,
)

/** Bounds-checked decoder for the sampled-brush block used by ABR 6.1 and 6.2 files. */
object AbrSampleDecoder {
    private const val MAX_SAMPLES = 512
    private const val MAX_DIMENSION = 16_384
    private const val MAX_PIXELS = 16_777_216L

    fun decode(file: File): List<AbrSample> = RandomAccessFile(file, "r").use { input ->
        require(input.length() >= 16) { "ABR file is truncated" }
        val version = input.readUnsignedShort()
        val subVersion = input.readUnsignedShort()
        require(version == 6 && subVersion in 1..2) { "ABR $version.$subVersion sampled tips are not supported" }
        val section = findSampleSection(input) ?: error("ABR contains no sampled-brush section")
        val samples = ArrayList<AbrSample>()
        input.seek(section.first)
        while (input.filePointer + 4 <= section.second && samples.size < MAX_SAMPLES) {
            val encodedSize = input.readUnsignedIntChecked()
            if (encodedSize == 0L) break
            val dataStart = input.filePointer
            val dataEnd = checkedAdd(dataStart, encodedSize, section.second, "ABR sample")
            val paddedEnd = checkedAdd(dataStart, (encodedSize + 3L) and -4L, section.second, "ABR sample padding")
            val descriptorBytes = 37L + if (subVersion == 2) 264L else 10L
            require(input.filePointer + descriptorBytes + 19L <= dataEnd) { "ABR sample metadata is truncated" }
            val sampleId = ByteArray(37).also(input::readFully).toString(Charsets.ISO_8859_1).removePrefix("$").trimEnd('\u0000')
            input.seek(input.filePointer + descriptorBytes - 37L)
            val top = input.readInt()
            val left = input.readInt()
            val bottom = input.readInt()
            val right = input.readInt()
            val depth = input.readUnsignedShort()
            val compression = input.readUnsignedByte()
            val width = right.toLong() - left
            val height = bottom.toLong() - top
            require(width in 1..MAX_DIMENSION.toLong() && height in 1..MAX_DIMENSION.toLong()) { "ABR tip dimensions are invalid" }
            val pixelCount = width * height
            require(pixelCount <= MAX_PIXELS) { "ABR tip exceeds the 16 megapixel safety limit" }
            require(depth == 8 || depth == 16) { "ABR grayscale depth $depth is not supported" }
            val bytesPerPixel = depth / 8
            val rowBytes = Math.multiplyExact(width.toInt(), bytesPerPixel)
            val decoded = when (compression) {
                0 -> readRaw(input, dataEnd, width.toInt(), height.toInt(), bytesPerPixel)
                1 -> readPackBits(input, dataEnd, width.toInt(), height.toInt(), bytesPerPixel)
                else -> error("ABR compression type $compression is not supported")
            }
            samples += AbrSample(sampleId, width.toInt(), height.toInt(), decoded, compression == 1)
            input.seek(paddedEnd)
        }
        require(samples.isNotEmpty()) { "ABR contains no decodable sampled tips" }
        samples
    }

    private fun findSampleSection(input: RandomAccessFile): Pair<Long, Long>? {
        input.seek(4)
        while (input.filePointer + 12L <= input.length()) {
            val signature = ByteArray(4).also(input::readFully).decodeToString()
            val tag = ByteArray(4).also(input::readFully).decodeToString()
            val size = input.readUnsignedIntChecked()
            val start = input.filePointer
            val end = checkedAdd(start, size, input.length(), "ABR section")
            if (signature == "8BIM" && tag == "samp") return start to end
            input.seek(end)
        }
        return null
    }

    private fun readRaw(input: RandomAccessFile, end: Long, width: Int, height: Int, bytesPerPixel: Int): ByteArray {
        val byteCount = Math.multiplyExact(Math.multiplyExact(width, height), bytesPerPixel)
        require(input.filePointer + byteCount <= end) { "ABR raw tip is truncated" }
        val raw = ByteArray(byteCount).also(input::readFully)
        return toOpacity(raw, bytesPerPixel)
    }

    private fun readPackBits(input: RandomAccessFile, end: Long, width: Int, height: Int, bytesPerPixel: Int): ByteArray {
        require(input.filePointer + height * 2L <= end) { "ABR RLE row table is truncated" }
        val rowSizes = IntArray(height) { input.readUnsignedShort() }
        val rowBytes = Math.multiplyExact(width, bytesPerPixel)
        val result = ByteArray(Math.multiplyExact(width, height))
        var outputOffset = 0
        rowSizes.forEach { encodedRowSize ->
            val rowEnd = input.filePointer + encodedRowSize
            require(rowEnd <= end) { "ABR RLE row exceeds its sample" }
            val row = ByteArray(rowBytes)
            var rowOffset = 0
            while (input.filePointer < rowEnd) {
                val control = input.readByte().toInt()
                when {
                    control == -128 -> Unit
                    control < 0 -> {
                        val count = 1 - control
                        require(input.filePointer < rowEnd && rowOffset + count <= row.size) { "ABR RLE repeat run is invalid" }
                        val value = input.readByte()
                        repeat(count) { row[rowOffset++] = value }
                    }
                    else -> {
                        val count = control + 1
                        require(input.filePointer + count <= rowEnd && rowOffset + count <= row.size) { "ABR RLE literal run is invalid" }
                        input.readFully(row, rowOffset, count)
                        rowOffset += count
                    }
                }
            }
            require(rowOffset == row.size) { "ABR RLE row decoded to the wrong width" }
            for (x in 0 until width) result[outputOffset++] = row[x * bytesPerPixel]
        }
        return result
    }

    private fun toOpacity(raw: ByteArray, bytesPerPixel: Int): ByteArray =
        if (bytesPerPixel == 1) raw else ByteArray(raw.size / bytesPerPixel) { raw[it * bytesPerPixel] }

    private fun RandomAccessFile.readUnsignedIntChecked(): Long = readInt().toLong() and 0xffff_ffffL

    private fun checkedAdd(start: Long, size: Long, limit: Long, label: String): Long {
        if (size < 0 || start > limit - size) throw EOFException("$label exceeds the file boundary")
        return start + size
    }
}
