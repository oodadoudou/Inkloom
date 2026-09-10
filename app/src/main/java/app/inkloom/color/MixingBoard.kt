package app.inkloom.color

import kotlin.math.*

/** Independent opaque mixing scratchpad. No notebook, brush dispatcher or document history access. */
class MixingBoard(val width: Int = 384, val height: Int = 256) {
    enum class Tool { PAINT, BLEND, PICK }
    private var pixels = IntArray(width * height) { PAPER }
    private val undo = ArrayDeque<IntArray>()
    private val redo = ArrayDeque<IntArray>()
    private var before: IntArray? = null
    private var lastX = 0f
    private var lastY = 0f
    private var carried = PAPER
    private var tool = Tool.PAINT
    private var color = PAPER
    private var radius = 10f
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun snapshot() = pixels.copyOf()
    fun copyPixelsTo(target: IntArray) { pixels.copyInto(target) }
    fun restore(values: IntArray) {
        require(values.size == pixels.size)
        pixels = values.copyOf(); undo.clear(); redo.clear(); before = null
    }
    fun sample(x: Float, y: Float): Int = pixels[y.toInt().coerceIn(0, height - 1) * width + x.toInt().coerceIn(0, width - 1)]
    fun begin(x: Float, y: Float, tool: Tool, color: Int, radius: Float) {
        cancel()
        this.tool = tool; this.color = color or (0xff shl 24); this.radius = radius.coerceIn(2f, 48f)
        lastX = x; lastY = y; carried = sample(x, y)
        if (tool == Tool.PICK) return
        before = pixels.copyOf()
        stamp(x, y)
    }
    fun move(x: Float, y: Float) {
        if (before == null) return
        val distance = hypot(x - lastX, y - lastY)
        val count = ceil(distance / (radius * .15f).coerceAtLeast(.6f)).toInt().coerceAtLeast(1)
        val startX = lastX; val startY = lastY
        for (i in 1..count) stamp(startX + (x - startX) * i / count, startY + (y - startY) * i / count)
        lastX = x; lastY = y
    }
    fun finish(): Boolean {
        val old = before ?: return false
        before = null
        if (old.contentEquals(pixels)) return false
        pushUndo(old); redo.clear(); return true
    }
    fun cancel() { before?.let { pixels = it }; before = null }
    fun clear(): Boolean {
        cancel()
        if (pixels.all { it == PAPER }) return false
        pushUndo(pixels); pixels = IntArray(width * height) { PAPER }; redo.clear(); return true
    }
    fun undo(): Boolean {
        cancel(); if (undo.isEmpty()) return false
        redo.addLast(pixels); pixels = undo.removeLast(); return true
    }
    fun redo(): Boolean {
        cancel(); if (redo.isEmpty()) return false
        pushUndo(pixels); pixels = redo.removeLast(); return true
    }
    private fun pushUndo(value: IntArray) { if (undo.size == 12) undo.removeFirst(); undo.addLast(value) }
    private fun stamp(x: Float, y: Float) {
        if (tool == Tool.BLEND) carried = mix(carried, sample(x, y), .13f)
        val ink = if (tool == Tool.BLEND) carried else color
        val left = floor(x - radius).toInt().coerceAtLeast(0)
        val right = ceil(x + radius).toInt().coerceAtMost(width - 1)
        val top = floor(y - radius).toInt().coerceAtLeast(0)
        val bottom = ceil(y + radius).toInt().coerceAtMost(height - 1)
        for (py in top..bottom) for (px in left..right) {
            val distance = hypot(px + .5f - x, py + .5f - y)
            val coverage = ((radius - distance) / (radius * .2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            if (coverage == 0f) continue
            val index = py * width + px
            pixels[index] = mix(pixels[index], ink, coverage * if (tool == Tool.BLEND) .28f else .5f)
        }
    }
    companion object {
        const val PAPER = 0xffeeeeea.toInt()
        /** Encoded RGB brush mixing, deliberately not advertised as spectral pigment simulation. */
        fun mix(a: Int, b: Int, amount: Float): Int {
            val t = amount.coerceIn(0f, 1f)
            fun channel(shift: Int) = (((a ushr shift and 255) * (1f - t) + (b ushr shift and 255) * t).roundToInt()).coerceIn(0, 255)
            return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}
