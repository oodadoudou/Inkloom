package app.inkloom.document

import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** RGBA scanlines encoded incrementally. Memory is independent of the full image height. */
object StreamingPng {
    fun write(output: OutputStream, width: Int, height: Int, dpi: Float = 300f, iccProfile: ByteArray? = null,
              row: (Int, IntArray) -> Unit) {
        require(width in 1..8192 && height in 1..8192)
        val stream = DataOutputStream(output)
        stream.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        fun chunk(type: String, bytes: ByteArray, length: Int = bytes.size) {
            val name = type.toByteArray(Charsets.US_ASCII)
            stream.writeInt(length); stream.write(name); stream.write(bytes, 0, length)
            val crc = CRC32().also { it.update(name); it.update(bytes, 0, length) }
            stream.writeInt(crc.value.toInt())
        }
        val header = java.io.ByteArrayOutputStream().also { DataOutputStream(it).use { data ->
            data.writeInt(width); data.writeInt(height); data.write(byteArrayOf(8, 6, 0, 0, 0))
        } }.toByteArray()
        chunk("IHDR", header)
        if(iccProfile==null) chunk("sRGB", byteArrayOf(0)) else {
            require(iccProfile.size in 128..16*1024*1024)
            val profile=java.io.ByteArrayOutputStream()
            profile.write("Inkloom ICC".toByteArray(Charsets.US_ASCII));profile.write(0);profile.write(0)
            java.util.zip.DeflaterOutputStream(profile).use { it.write(iccProfile) }
            chunk("iCCP",profile.toByteArray())
        }
        val density = (dpi * 1000 / 25.4f).toInt().coerceAtLeast(1)
        val physical = java.io.ByteArrayOutputStream().also { DataOutputStream(it).use { data ->
            data.writeInt(density); data.writeInt(density); data.writeByte(1)
        } }.toByteArray()
        chunk("pHYs", physical)
        val deflater = Deflater(6)
        val pixels = IntArray(width); val scanline = ByteArray(1 + width * 4); val compressed = ByteArray(64 * 1024)
        try {
            for (y in 0 until height) {
                row(y, pixels); scanline[0] = 0
                for (x in pixels.indices) {
                    val pixel = pixels[x]; val i = 1 + x * 4
                    scanline[i] = (pixel ushr 16).toByte(); scanline[i+1] = (pixel ushr 8).toByte()
                    scanline[i+2] = pixel.toByte(); scanline[i+3] = (pixel ushr 24).toByte()
                }
                deflater.setInput(scanline)
                while (!deflater.needsInput()) { val count = deflater.deflate(compressed); if (count > 0) chunk("IDAT", compressed, count) }
            }
            deflater.finish()
            while (!deflater.finished()) { val count = deflater.deflate(compressed); if (count > 0) chunk("IDAT", compressed, count) }
            chunk("IEND", byteArrayOf()); stream.flush()
        } finally { deflater.end() }
    }
}
