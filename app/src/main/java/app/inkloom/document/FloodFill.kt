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

    fun fill(source: Bitmap, startX: Int, startY: Int, tolerance: Int): Result {
        val w = source.width
        val h = source.height
        if (startX < 0 || startX >= w || startY < 0 || startY >= h) return empty(1, 1)
        val seed = source.getPixel(startX, startY)
        val sr = Color.red(seed); val sg = Color.green(seed); val sb = Color.blue(seed); val sa = Color.alpha(seed)
        val tol = tolerance.coerceIn(0, 255)
        val pixelCount = w * h
        val covered = BitSet(pixelCount)
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
            while (x >= 0 && !covered[y * w + x] && matches(source.getPixel(x, y), sr, sg, sb, sa, tol)) x--
            x++
            var spanUp = false
            var spanDown = false
            while (x < w && !covered[y * w + x] && matches(source.getPixel(x, y), sr, sg, sb, sa, tol)) {
                covered.set(y * w + x)
                if (x < minx) minx = x; if (x > maxx) maxx = x
                if (y < miny) miny = y; if (y > maxy) maxy = y
                if (y > 0) {
                    if (!spanUp && !covered[(y - 1) * w + x] && matches(source.getPixel(x, y - 1), sr, sg, sb, sa, tol)) { push(x, y - 1); spanUp = true }
                    else if (spanUp && (covered[(y - 1) * w + x] || !matches(source.getPixel(x, y - 1), sr, sg, sb, sa, tol))) spanUp = false
                }
                if (y < h - 1) {
                    if (!spanDown && !covered[(y + 1) * w + x] && matches(source.getPixel(x, y + 1), sr, sg, sb, sa, tol)) { push(x, y + 1); spanDown = true }
                    else if (spanDown && (covered[(y + 1) * w + x] || !matches(source.getPixel(x, y + 1), sr, sg, sb, sa, tol))) spanDown = false
                }
                x++
            }
        }
        if (maxx < 0) return empty(w, h)
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

    private const val INITIAL_STACK_CAPACITY = 4_096
}
