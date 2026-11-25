package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.fill.IntList

/**
 * Line colour blend (in the manner of the popular 線画の色を馴染ませる actions): every line pixel takes
 * a darker, slightly richer shade of the colour beneath it, so line art sits into the painting instead
 * of staying flat black. Where a line covers no colour, the nearest colour within [Settings.reach]
 * pixels is used; lines further from any colour are left out. Pure pixel-buffer code.
 */
object LineColorBlend {
    data class Settings(
        /** How much darker than the colour beneath (0..1). */
        val darken: Float = .45f,
        /** How much richer than the colour beneath (0..1). */
        val saturate: Float = .15f,
        /** How far a line reaches for a colour when nothing is directly beneath it. */
        val reach: Int = 12,
    ) {
        init {
            require(darken.isFinite() && darken in 0f..1f && saturate.isFinite() && saturate in 0f..1f) { "darken and saturate must be 0..1" }
            require(reach in 0..256) { "reach must be 0..256" }
        }
    }

    /**
     * [lines] and [colours] are premultiplied ARGB of one size. Returns opaque straight-alpha ARGB
     * (equal to premultiplied) where a line pixel found a colour, 0 elsewhere: a layer to clip to the lines.
     */
    fun render(lines: IntArray, colours: IntArray, width: Int, height: Int, settings: Settings = Settings()): IntArray {
        require(width > 0 && height > 0 && lines.size == width * height && colours.size == lines.size) { "buffers do not match $width x $height" }
        val count = lines.size
        val found = IntArray(count)
        val frontier = IntList(1024)
        for (index in 0 until count) {
            val straight = straight(colours[index]) ?: continue
            found[index] = straight
            frontier.add(index)
        }
        // Carry the nearest colour outward into line pixels only, one ring per step.
        var current = frontier
        var step = 0
        while (current.size > 0 && step < settings.reach) {
            val next = IntList(current.size)
            for (at in 0 until current.size) {
                val index = current[at]
                val x = index % width
                fun spread(target: Int) {
                    if (found[target] != 0 || lines[target] ushr 24 == 0) return
                    found[target] = found[index]
                    next.add(target)
                }
                if (x > 0) spread(index - 1)
                if (x < width - 1) spread(index + 1)
                if (index >= width) spread(index - width)
                if (index + width < count) spread(index + width)
            }
            current = next
            step += 1
        }
        return IntArray(count) { index ->
            if (lines[index] ushr 24 == 0 || found[index] == 0) 0 else shade(found[index], settings)
        }
    }

    /** [argb] darker and richer by the settings, opaque. */
    fun shade(argb: Int, settings: Settings): Int {
        val r = (argb ushr 16 and 255) / 255f; val g = (argb ushr 8 and 255) / 255f; val b = (argb and 255) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }.let { (it + 360f) % 360f }
        val saturation = if (max == 0f) 0f else delta / max
        val richer = if (saturation == 0f) 0f else saturation + settings.saturate * (1f - saturation)
        val darker = max * (1f - settings.darken)
        val chroma = darker * richer
        val x = chroma * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val m = darker - chroma
        val (red, green, blue) = when ((hue / 60f).toInt()) {
            0 -> Triple(chroma, x, 0f); 1 -> Triple(x, chroma, 0f); 2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma); 4 -> Triple(x, 0f, chroma); else -> Triple(chroma, 0f, x)
        }
        fun channel(value: Float) = ((value + m) * 255f + .5f).toInt().coerceIn(0, 255)
        return (0xff shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }

    /** The straight colour of a premultiplied pixel solid enough to count as paint, else null. */
    private fun straight(premultiplied: Int): Int? {
        val alpha = premultiplied ushr 24
        if (alpha < SOLID_ALPHA) return null
        fun channel(shift: Int) = ((premultiplied ushr shift and 255) * 255 / alpha).coerceAtMost(255)
        return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val SOLID_ALPHA = 64
}
