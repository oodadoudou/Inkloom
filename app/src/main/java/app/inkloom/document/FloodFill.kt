package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.Color
import java.util.BitSet
import kotlin.math.abs

/** Scanline flood fill used by the paint-bucket / ColorDrop tool.
 *  [tolerance] is the maximum per-channel difference (0..255) allowed for a pixel to be
 *  considered part of the same region as the seed pixel. */
object FloodFill {
    data class Result(val mask: Bitmap, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
        val isEmpty: Boolean get() = width <= 0 || height <= 0
    }

    fun fill(
        source: Bitmap,
        startX: Int,
        startY: Int,
        tolerance: Int,
        closeGapPixels: Int = 0,
        areaScalingPixels: Int = 0,
    ): Result {
        val w = source.width
        val h = source.height
        if (startX < 0 || startX >= w || startY < 0 || startY >= h) return empty(1, 1)
        val seed = source.getPixel(startX, startY)
        val sr = Color.red(seed); val sg = Color.green(seed); val sb = Color.blue(seed); val sa = Color.alpha(seed)
        val tol = tolerance.coerceIn(0, 255)
        val pixelCount = w * h
        var fillable = BitSet(pixelCount)
        for (y in 0 until h) for (x in 0 until w) {
            if (matches(source.getPixel(x, y), sr, sg, sb, sa, tol)) fillable.set(y * w + x)
        }
        val gap = closeGapPixels.coerceIn(0, MAX_EFFECT_RADIUS)
        if (gap > 0) {
            val barriers = BitSet(pixelCount).apply { set(0, pixelCount); andNot(fillable) }
            val closedBarriers = dilate(barriers, w, h, gap)
            fillable = BitSet(pixelCount).apply { set(0, pixelCount); andNot(closedBarriers) }
        }
        var covered = BitSet(pixelCount)
        val queued = BitSet(pixelCount)
        var stack = IntArray(minOf(pixelCount, INITIAL_STACK_CAPACITY))
        var sp = 0
        fun push(x: Int, y: Int) {
            val index = y * w + x
            if (covered[index] || queued[index]) return
            if (sp == stack.size) stack = stack.copyOf(minOf(pixelCount, stack.size * 2))
            stack[sp++] = index
            queued.set(index)
        }
        push(startX, startY)
        var minx = w; var maxx = -1; var miny = h; var maxy = -1
        while (sp > 0) {
            val index = stack[--sp]
            if (covered[index]) continue
            val y = index / w
            var x = index % w
            // scan left
            while (x >= 0 && !covered[y * w + x] && fillable[y * w + x]) x--
            x++
            var spanUp = false
            var spanDown = false
            while (x < w && !covered[y * w + x] && fillable[y * w + x]) {
                covered.set(y * w + x)
                if (x < minx) minx = x; if (x > maxx) maxx = x
                if (y < miny) miny = y; if (y > maxy) maxy = y
                if (y > 0) {
                    if (!spanUp && !covered[(y - 1) * w + x] && fillable[(y - 1) * w + x]) { push(x, y - 1); spanUp = true }
                    else if (spanUp && (covered[(y - 1) * w + x] || !fillable[(y - 1) * w + x])) spanUp = false
                }
                if (y < h - 1) {
                    if (!spanDown && !covered[(y + 1) * w + x] && fillable[(y + 1) * w + x]) { push(x, y + 1); spanDown = true }
                    else if (spanDown && (covered[(y + 1) * w + x] || !fillable[(y + 1) * w + x])) spanDown = false
                }
                x++
            }
        }
        val scaling = areaScalingPixels.coerceIn(-MAX_EFFECT_RADIUS, MAX_EFFECT_RADIUS)
        if (scaling > 0) covered = dilate(covered, w, h, scaling)
        if (scaling < 0) covered = erode(covered, w, h, -scaling)
        if (covered.isEmpty) return empty(w, h)
        minx = w; maxx = -1; miny = h; maxy = -1
        var next = covered.nextSetBit(0)
        while (next >= 0) {
            val y = next / w
            val x = next % w
            if (x < minx) minx = x; if (x > maxx) maxx = x
            if (y < miny) miny = y; if (y > maxy) maxy = y
            next = covered.nextSetBit(next + 1)
        }
        val bw = maxx - minx + 1
        val bh = maxy - miny + 1
        val mask = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val px = IntArray(bw * bh)
        var i = 0
        for (yy in miny..maxy) for (xx in minx..maxx) {
            px[i++] = if (covered[yy * w + xx]) -1 else 0
        }
        mask.setPixels(px, 0, bw, 0, 0, bw, bh)
        return Result(mask, minx, miny, maxx, maxy)
    }

    private fun matches(pixel: Int, sr: Int, sg: Int, sb: Int, sa: Int, tol: Int): Boolean =
        abs(Color.red(pixel) - sr) <= tol && abs(Color.green(pixel) - sg) <= tol &&
            abs(Color.blue(pixel) - sb) <= tol && abs(Color.alpha(pixel) - sa) <= tol

    private fun empty(w: Int, h: Int) = Result(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 0, 0, -1, -1)

    private fun dilate(source: BitSet, width: Int, height: Int, radius: Int): BitSet {
        if (radius <= 0) return source.clone() as BitSet
        val distance = distanceTo(source, width, height)
        return BitSet(width * height).apply {
            distance.forEachIndexed { index, value -> if (value <= radius) set(index) }
        }
    }

    private fun erode(source: BitSet, width: Int, height: Int, radius: Int): BitSet {
        if (radius <= 0) return source.clone() as BitSet
        val outside = BitSet(width * height).apply { set(0, width * height); andNot(source) }
        val distance = distanceTo(outside, width, height)
        return BitSet(width * height).apply {
            for (y in 0 until height) for (x in 0 until width) {
                val index = y * width + x
                val edgeDistance = minOf(x + 1, y + 1, width - x, height - y)
                if (source[index] && distance[index] > radius && edgeDistance > radius) set(index)
            }
        }
    }

    private fun distanceTo(target: BitSet, width: Int, height: Int): IntArray {
        val infinity = width + height + 1
        val result = IntArray(width * height) { index -> if (target[index]) 0 else infinity }
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            if (x > 0) result[index] = minOf(result[index], result[index - 1] + 1)
            if (y > 0) result[index] = minOf(result[index], result[index - width] + 1)
        }
        for (y in height - 1 downTo 0) for (x in width - 1 downTo 0) {
            val index = y * width + x
            if (x + 1 < width) result[index] = minOf(result[index], result[index + 1] + 1)
            if (y + 1 < height) result[index] = minOf(result[index], result[index + width] + 1)
        }
        return result
    }

    private const val INITIAL_STACK_CAPACITY = 4_096
    private const val MAX_EFFECT_RADIUS = 64
}
