package app.inkloom.document

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * A GIF89a writer for animation export: each frame gets its own 256-colour palette by median cut,
 * pixels below half opacity become transparent, and the animation loops forever (NETSCAPE2.0).
 * Delays are in hundredths of a second, as GIF stores them.
 */
class GifEncoder(private val out: OutputStream, private val width: Int, private val height: Int) {
    init { require(width in 1..65535 && height in 1..65535) { "GIF frames are 1 to 65535 pixels" } }
    private var started = false
    private var finished = false

    fun addFrame(argb: IntArray, delayCentiseconds: Int) {
        check(!finished) { "The GIF is finished" }
        require(argb.size == width * height) { "A frame must be $width × $height pixels" }
        if (!started) { header(); started = true }
        val transparent = argb.any { it ushr 24 < 128 }
        val palette = GifPalette.build(argb, if (transparent) 255 else 256)
        val indices = palette.indices(argb, transparentIndex = if (transparent) palette.size else -1)
        val colors = palette.colors + if (transparent) listOf(0) else emptyList()
        val bits = colorTableBits(colors.size)
        // Graphic control: delay, disposal (restore to background when the frame has holes), transparency.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4))
        out.write(((if (transparent) 2 else 1) shl 2) or (if (transparent) 1 else 0))
        short(delayCentiseconds.coerceIn(2, 65535))
        out.write(if (transparent) palette.size else 0); out.write(0)
        // Image descriptor with a local colour table.
        out.write(0x2C); short(0); short(0); short(width); short(height)
        out.write(0x80 or (bits - 1))
        repeat(1 shl bits) { i -> val c = colors.getOrElse(i) { 0 }; out.write(c ushr 16 and 255); out.write(c ushr 8 and 255); out.write(c and 255) }
        val minimumCodeSize = maxOf(2, bits)
        out.write(minimumCodeSize)
        val compressed = GifLzw.encode(indices, minimumCodeSize)
        var offset = 0
        while (offset < compressed.size) {
            val length = minOf(255, compressed.size - offset)
            out.write(length); out.write(compressed, offset, length); offset += length
        }
        out.write(0)
    }

    fun finish() {
        check(started) { "A GIF needs at least one frame" }
        if (!finished) { out.write(0x3B); out.flush(); finished = true }
    }

    private fun header() {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        short(width); short(height)
        out.write(0x70) // no global colour table; 8-bit colour resolution
        out.write(0); out.write(0)
        // Loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 11)); out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(3, 1, 0, 0, 0))
    }

    private fun short(value: Int) { out.write(value and 255); out.write(value ushr 8 and 255) }

    private fun colorTableBits(count: Int): Int { var bits = 1; while ((1 shl bits) < count) bits++; return bits }
}

/** A frame's palette: up to [colors]·size opaque RGB colours chosen by median cut on a 5-bit-per-channel histogram. */
internal class GifPalette(val colors: List<Int>) {
    val size get() = colors.size
    private val nearest = HashMap<Int, Int>()

    fun indices(argb: IntArray, transparentIndex: Int): ByteArray = ByteArray(argb.size) { i ->
        val pixel = argb[i]
        if (transparentIndex >= 0 && pixel ushr 24 < 128) transparentIndex.toByte()
        else nearest.getOrPut(pixel and 0xFFFFFF) { closest(pixel) }.toByte()
    }

    private fun closest(pixel: Int): Int {
        val r = pixel ushr 16 and 255; val g = pixel ushr 8 and 255; val b = pixel and 255
        var best = 0; var distance = Int.MAX_VALUE
        colors.forEachIndexed { i, c ->
            val dr = r - (c ushr 16 and 255); val dg = g - (c ushr 8 and 255); val db = b - (c and 255)
            val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (d < distance) { distance = d; best = i }
        }
        return best
    }

    companion object {
        fun build(argb: IntArray, maximum: Int): GifPalette {
            val histogram = IntArray(32768)
            argb.forEach { p -> if (p ushr 24 >= 128) histogram[(p ushr 9 and 0x7C00) or (p ushr 6 and 0x3E0) or (p ushr 3 and 0x1F)]++ }
            val buckets = histogram.indices.filter { histogram[it] > 0 }
            if (buckets.isEmpty()) return GifPalette(listOf(0))
            // Few colours: use them exactly.
            val exact = argb.asSequence().filter { it ushr 24 >= 128 }.map { it and 0xFFFFFF }.distinct().take(maximum + 1).toList()
            if (exact.size <= maximum) return GifPalette(exact)
            var boxes = listOf(buckets)
            while (boxes.size < maximum) {
                val (index, axis) = boxes.withIndex().filter { it.value.size > 1 }.map { (i, box) ->
                    val ranges = (0 until 3).map { a -> box.maxOf { channel(it, a) } - box.minOf { channel(it, a) } }
                    Triple(i, ranges.indices.maxBy { ranges[it] }, ranges.max() * box.sumOf { histogram[it] }.toLong())
                }.maxByOrNull { it.third }?.let { it.first to it.second } ?: break
                val sorted = boxes[index].sortedBy { channel(it, axis) }
                val total = sorted.sumOf { histogram[it] }
                var running = 0; var cut = 1
                for ((i, bucket) in sorted.withIndex()) { running += histogram[bucket]; if (running * 2 >= total) { cut = (i + 1).coerceIn(1, sorted.size - 1); break } }
                boxes = boxes.toMutableList().apply { removeAt(index); add(sorted.subList(0, cut)); add(sorted.subList(cut, sorted.size)) }
            }
            return GifPalette(boxes.map { box ->
                val weight = box.sumOf { histogram[it].toLong() }.coerceAtLeast(1)
                fun mean(axis: Int) = ((box.sumOf { channel(it, axis).toLong() * histogram[it] } * 255 / 31) / weight).toInt().coerceIn(0, 255)
                (mean(0) shl 16) or (mean(1) shl 8) or mean(2)
            })
        }
        private fun channel(bucket: Int, axis: Int) = when (axis) { 0 -> bucket ushr 10 and 31; 1 -> bucket ushr 5 and 31; else -> bucket and 31 }
    }
}

/** GIF's variable-length LZW with clear codes when the 12-bit table fills. */
internal object GifLzw {
    fun encode(indices: ByteArray, minimumCodeSize: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val clear = 1 shl minimumCodeSize; val end = clear + 1
        var codeSize = minimumCodeSize + 1
        var nextCode = end + 1
        val table = HashMap<Long, Int>()
        var buffer = 0L; var bufferBits = 0
        fun emit(code: Int) {
            buffer = buffer or (code.toLong() shl bufferBits); bufferBits += codeSize
            while (bufferBits >= 8) { output.write((buffer and 255).toInt()); buffer = buffer ushr 8; bufferBits -= 8 }
        }
        emit(clear)
        if (indices.isEmpty()) { emit(end); if (bufferBits > 0) output.write((buffer and 255).toInt()); return output.toByteArray() }
        var prefix = indices[0].toInt() and 255
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 255
            val key = (prefix.toLong() shl 8) or k.toLong()
            val existing = table[key]
            if (existing != null) { prefix = existing; continue }
            emit(prefix)
            if (nextCode < 4096) {
                table[key] = nextCode++
                if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clear); table.clear(); codeSize = minimumCodeSize + 1; nextCode = end + 1
            }
            prefix = k
        }
        emit(prefix); emit(end)
        if (bufferBits > 0) output.write((buffer and 255).toInt())
        return output.toByteArray()
    }
}
