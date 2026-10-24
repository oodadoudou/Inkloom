package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** A minimal GIF reader written independently of the encoder: frames as ARGB, delays, and the loop flag. */
private class GifReading(bytes: ByteArray) {
    val frames = mutableListOf<IntArray>(); val delays = mutableListOf<Int>(); var loops = false
    var width = 0; var height = 0
    init {
        var p = 6
        fun u8() = bytes[p++].toInt() and 255
        fun u16() = u8() or (u8() shl 8)
        width = u16(); height = u16(); val flags = u8(); p += 2
        check(flags and 0x80 == 0) { "unexpected global table" }
        var delay = 0; var transparent = -1; var finished = false
        while (!finished) {
            when (u8()) {
                0x21 -> {
                    val label = u8()
                    val first = u8(); val start = p
                    if (label == 0xF9) { val packed = bytes[start].toInt(); delay = (bytes[start + 1].toInt() and 255) or ((bytes[start + 2].toInt() and 255) shl 8)
                        transparent = if (packed and 1 != 0) bytes[start + 3].toInt() and 255 else -1 }
                    if (label == 0xFF && String(bytes, start, 11, Charsets.US_ASCII) == "NETSCAPE2.0") loops = true
                    p = start + first
                    while (true) { val n = u8(); if (n == 0) break; p += n }
                }
                0x2C -> {
                    p += 8; val packed = u8(); val size = 1 shl ((packed and 7) + 1)
                    val table = IntArray(size) { 0xFF000000.toInt() or (u8() shl 16) or (u8() shl 8) or u8() }
                    val minimum = u8()
                    val data = ByteArrayOutputStream()
                    while (true) { val n = u8(); if (n == 0) break; data.write(bytes, p, n); p += n }
                    val indices = lzw(data.toByteArray(), minimum, width * height)
                    frames += IntArray(width * height) { i -> val k = indices[i]; if (k == transparent) 0 else table[k] }
                    delays += delay
                }
                0x3B -> finished = true
                else -> error("bad block")
            }
        }
    }
    private fun lzw(data: ByteArray, minimum: Int, count: Int): IntArray {
        val clear = 1 shl minimum; val end = clear + 1
        val out = IntArray(count); var written = 0
        var size = minimum + 1; var next = end + 1
        val prefix = IntArray(4096); val suffix = IntArray(4096); val length = IntArray(4096)
        for (i in 0 until clear) { suffix[i] = i; length[i] = 1 }
        var bits = 0L; var available = 0; var offset = 0; var previous = -1
        fun expand(code: Int): IntArray { val r = IntArray(length[code]); var c = code; for (i in r.indices.reversed()) { r[i] = suffix[c]; c = prefix[c] }; return r }
        while (true) {
            while (available < size && offset < data.size) { bits = bits or ((data[offset++].toLong() and 255) shl available); available += 8 }
            if (available < size) break
            val code = (bits and ((1L shl size) - 1)).toInt(); bits = bits ushr size; available -= size
            if (code == clear) { size = minimum + 1; next = end + 1; previous = -1; continue }
            if (code == end) break
            val entry = when {
                code < next -> expand(code)
                code == next && previous >= 0 -> expand(previous).let { it + it[0] }
                else -> error("bad code $code at $next")
            }
            if (previous >= 0 && next < 4096) { prefix[next] = previous; suffix[next] = entry[0]; length[next] = length[previous] + 1; next++ }
            if (next == (1 shl size) && size < 12) size++
            entry.forEach { if (written < count) out[written++] = it }
            previous = code
        }
        check(written == count) { "decoded $written of $count pixels" }
        return out
    }
}

class GifEncoderTest {
    @Test fun framesRoundTripWithTheirDelaysColoursAndTransparency() {
        val w = 40; val h = 24
        val gradient = IntArray(w * h) { i -> val x = i % w; val y = i / w; (0xFF shl 24) or ((x * 6) shl 16) or ((y * 10) shl 8) or 128 }
        val holes = IntArray(w * h) { i -> if (i % w < 20) 0xFFFF0000.toInt() else 0 }
        val out = ByteArrayOutputStream()
        GifEncoder(out, w, h).apply { addFrame(gradient, 8); addFrame(holes, 25); finish() }
        val bytes = out.toByteArray()
        assertEquals("GIF89a", String(bytes, 0, 6, Charsets.US_ASCII))
        assertTrue("loops forever", String(bytes, Charsets.ISO_8859_1).contains("NETSCAPE2.0"))
        val gif = GifReading(bytes)
        assertTrue(gif.loops)
        assertEquals(2, gif.frames.size)
        assertEquals(listOf(8, 25), gif.delays)
        assertEquals(w, gif.width); assertEquals(h, gif.height)
        // A smooth gradient of 960 colours is reduced to 256 closely.
        var error = 0L
        for (y in 0 until h) for (x in 0 until w) {
            val a = gradient[y * w + x]; val b = gif.frames[0][y * w + x]
            error += abs((a ushr 16 and 255) - (b ushr 16 and 255)) + abs((a ushr 8 and 255) - (b ushr 8 and 255)) + abs((a and 255) - (b and 255))
        }
        assertTrue("mean error ${error / (w * h)}", error / (w * h) <= 12)
        assertEquals(0xFFFF0000.toInt(), gif.frames[1][5 * w + 5])
        assertEquals(0, gif.frames[1][5 * w + 30] ushr 24)
    }

    @Test fun largeFlatAndNoisyFramesStayDecodable() {
        val w = 300; val h = 200
        var seed = 7
        val noise = IntArray(w * h) { seed = seed * 1103515245 + 12345; 0xFF000000.toInt() or (seed ushr 8 and 0xFFFFFF) }
        val flat = IntArray(w * h) { 0xFF202020.toInt() }
        val out = ByteArrayOutputStream()
        GifEncoder(out, w, h).apply { addFrame(noise, 4); addFrame(flat, 4); finish() }
        val gif = GifReading(out.toByteArray())
        assertEquals(2, gif.frames.size)
        assertEquals(0xFF202020.toInt(), gif.frames[1][100 * w + 150])
    }

    @Test fun eachCelIsHeldForItsDurationAndRoundingDoesNotDrift() {
        val folder = InkLayer(id = "f", name = "Walk", role = LayerRole.FOLDER)
        val cels = (0 until 3).map { InkLayer(id = "c$it", name = "Cel $it", parentId = "f", visible = it == 0) }
        val page = InkPage(layers = listOf(folder.copy(animation = AnimationFolder(frameRate = 12,
            cels = listOf(AnimationCel("c0", 1), AnimationCel("c1", 2), AnimationCel("c2", 1)), current = "c0"))) + cels)
        val frames = AnimationExport.frames(page, "f")
        assertEquals(listOf("c0", "c1", "c2"), frames.map { it.celId })
        // 1, 2 and 1 frames at 12 fps: 8.33, 16.67 and 8.33 hundredths, 33 in all.
        assertEquals(33, frames.sumOf { it.delayCentiseconds })
        frames.forEach { frame -> assertTrue(frame.page.layers.single { it.id == frame.celId }.visible) }
        assertTrue(frames.all { f -> f.page.layers.filter { it.parentId == "f" && it.id != f.celId }.none { it.visible } })
    }
}
