package app.inkloom.document

import java.io.*
import java.util.zip.InflaterInputStream

/** TIFF 6.0 chunky 8-bit RGB/RGBA: raw, PackBits and Deflate, multiple IFDs on read. */
object TiffRasterCodec {
    data class Image(val width: Int, val height: Int, val dpi: Float, val pixels: IntArray)
    fun write(output: OutputStream, width: Int, height: Int, dpi: Float, row: (Int, IntArray) -> Unit) {
        require(width in 1..8192 && height in 1..8192 && dpi.isFinite() && dpi > 0)
        val out = DataOutputStream(output)
        // Big endian, one uncompressed RGBA strip. ExtraSamples=2 means straight alpha.
        val count = 13; val extras = 8 + 2 + count * 12 + 4
        val pixelsOffset = extras + 8 + 16
        out.writeBytes("MM"); out.writeShort(42); out.writeInt(8); out.writeShort(count)
        fun tag(id: Int, type: Int, n: Int, value: Int) { out.writeShort(id); out.writeShort(type); out.writeInt(n); out.writeInt(value) }
        tag(256, 4, 1, width); tag(257, 4, 1, height); tag(258, 3, 4, extras)
        tag(259, 3, 1, 1 shl 16); tag(262, 3, 1, 2 shl 16); tag(273, 4, 1, pixelsOffset)
        tag(277, 3, 1, 4 shl 16); tag(278, 4, 1, height); tag(279, 4, 1, width * height * 4)
        tag(282, 5, 1, extras + 8); tag(283, 5, 1, extras + 16); tag(296, 3, 1, 2 shl 16)
        tag(338, 3, 1, 2 shl 16); out.writeInt(0)
        repeat(4) { out.writeShort(8) }; repeat(2) { out.writeInt((dpi * 1000).toInt()); out.writeInt(1000) }
        val line = IntArray(width); val bytes = ByteArray(width * 4)
        for (y in 0 until height) { row(y, line); line.forEachIndexed { x, p ->
            bytes[x*4] = (p ushr 16).toByte(); bytes[x*4+1] = (p ushr 8).toByte(); bytes[x*4+2] = p.toByte(); bytes[x*4+3] = (p ushr 24).toByte()
        }; out.write(bytes) }; out.flush()
    }

    /** TIFF separated CMYK, no alpha, with the actual printer ICC profile embedded. */
    fun writeCmyk(output:OutputStream,width:Int,height:Int,dpi:Float,profile:ByteArray,row:(Int,ByteArray)->Unit) {
        require(width in 1..8192 && height in 1..8192 && dpi.isFinite() && dpi>0 && profile.size in 128..16*1024*1024)
        val out=DataOutputStream(output);val count=14;val extras=8+2+count*12+4;val profileOffset=extras+24;val pixelsOffset=profileOffset+profile.size
        out.writeBytes("MM");out.writeShort(42);out.writeInt(8);out.writeShort(count)
        fun tag(id:Int,type:Int,n:Int,value:Int) { out.writeShort(id);out.writeShort(type);out.writeInt(n);out.writeInt(value) }
        tag(256,4,1,width);tag(257,4,1,height);tag(258,3,4,extras);tag(259,3,1,1 shl 16);tag(262,3,1,5 shl 16)
        tag(273,4,1,pixelsOffset);tag(277,3,1,4 shl 16);tag(278,4,1,height);tag(279,4,1,width*height*4)
        tag(282,5,1,extras+8);tag(283,5,1,extras+16);tag(296,3,1,2 shl 16);tag(332,3,1,1 shl 16)
        tag(34675,7,profile.size,profileOffset);out.writeInt(0)
        repeat(4){out.writeShort(8)};repeat(2){out.writeInt((dpi*1000).toInt());out.writeInt(1000)};out.write(profile)
        val bytes=ByteArray(width*4);for(y in 0 until height){row(y,bytes);out.write(bytes)};out.flush()
    }

    fun read(file: File): List<Image> = RandomAccessFile(file, "r").use { input ->
        val signature = input.readUnsignedShort(); require(signature == 0x4949 || signature == 0x4d4d) { "Invalid TIFF byte order" }
        val little = signature == 0x4949
        fun short(): Int = input.readUnsignedShort().let { if (little) java.lang.Short.reverseBytes(it.toShort()).toInt().and(65535) else it }
        fun long(): Long = input.readInt().let { if (little) Integer.reverseBytes(it) else it }.toLong().and(0xffffffffL)
        require(short() == 42) { "BigTIFF is not supported" }
        var ifd = long(); val seen = hashSetOf<Long>(); val result = mutableListOf<Image>()
        while (ifd != 0L) {
            require(result.size < 512 && seen.add(ifd) && ifd in 8 until input.length()) { "Invalid TIFF directory" }
            input.seek(ifd); val entries = short(); require(entries <= 4096)
            val tags = mutableMapOf<Int, LongArray>()
            repeat(entries) {
                val id = short(); val type = short(); val count = long(); val valuePos = input.filePointer; val offset = long(); val next = input.filePointer
                require(count <= 1_048_576)
                if (type in listOf(1, 3, 4, 5)) {
                    val size = when(type) { 1 -> 1; 3 -> 2; 4 -> 4; else -> 8 }
                    input.seek(if (count * size <= 4) valuePos else offset)
                    tags[id] = LongArray(count.toInt() * if (type == 5) 2 else 1) { when(type) { 1 -> input.readUnsignedByte().toLong(); 3 -> short().toLong(); else -> long() } }
                }
                input.seek(next)
            }
            ifd = long()
            fun first(id: Int, default: Int = 0) = tags[id]?.firstOrNull()?.toInt() ?: default
            val w = first(256); val h = first(257); val channels = first(277, 3)
            require(w in 1..8192 && h in 1..8192 && w.toLong()*h <= 67_108_864L)
            require(channels in 3..4 && first(262) == 2 && first(284, 1) == 1 && first(274, 1) == 1 && first(317, 1) == 1 && tags[258]?.all { it == 8L } == true) {
                "TIFF requires chunky 8-bit RGB/RGBA, top-left orientation and no predictor" }
            val compression = first(259, 1); require(compression in listOf(1, 8, 32946, 32773)) { "TIFF compression $compression is unsupported (raw, Deflate or PackBits required)" }
            val offsets = requireNotNull(tags[273]) { "Tiled TIFF is unsupported" }; val counts = requireNotNull(tags[279]); require(offsets.size == counts.size)
            val rows = first(278, h).coerceAtMost(h); require(rows > 0)
            val pixels = IntArray(w*h); var y = 0
            offsets.forEachIndexed { index, position ->
                val expected = minOf(rows, h-y)*w*channels
                require(expected >= 0 && counts[index] in 0..(expected.toLong()*2+65536))
                input.seek(position); val bytes = ByteArray(counts[index].toInt()); input.readFully(bytes)
                val data = when(compression) {
                    1 -> bytes
                    8, 32946 -> InflaterInputStream(ByteArrayInputStream(bytes)).use { stream -> readBounded(stream, expected) }
                    else -> unpackBits(bytes, expected)
                }
                require(data.size == expected) { "Incomplete TIFF strip" }
                for (i in 0 until expected/channels) {
                    var r = data[i*channels].toInt().and(255); var g = data[i*channels+1].toInt().and(255); var b = data[i*channels+2].toInt().and(255)
                    val a = if(channels == 4) data[i*channels+3].toInt().and(255) else 255
                    if (channels == 4 && first(338, 2) == 1 && a > 0) { r = (r*255/a).coerceAtMost(255); g = (g*255/a).coerceAtMost(255); b = (b*255/a).coerceAtMost(255) }
                    pixels[y*w+i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                y += minOf(rows, h-y)
            }
            require(y == h)
            val density = tags[282]?.let { if(it.size >= 2 && it[1] != 0L) it[0].toFloat()/it[1] else 72f } ?: 72f
            result += Image(w, h, density * if(first(296, 2) == 3) 2.54f else 1f, pixels)
        }
        require(result.isNotEmpty()) { "TIFF has no image directories" }; result
    }
    private fun readBounded(input: InputStream, size: Int): ByteArray {
        val result = ByteArray(size); DataInputStream(input).readFully(result); require(input.read() == -1) { "TIFF strip exceeds declared dimensions" }; return result
    }
    private fun unpackBits(input: ByteArray, size: Int): ByteArray {
        val out = ByteArray(size); var i = 0; var p = 0
        while(i < input.size) { val n = input[i++].toInt(); when {
            n >= 0 -> { val count = n+1; require(i+count <= input.size && p+count <= size); input.copyInto(out, p, i, i+count); p+=count; i+=count }
            n >= -127 -> { val count = 1-n; require(i < input.size && p+count <= size); out.fill(input[i++], p, p+count); p+=count }
        } }; require(p == size); return out
    }
}
