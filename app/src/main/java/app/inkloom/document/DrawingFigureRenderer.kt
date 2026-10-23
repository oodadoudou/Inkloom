package app.inkloom.document

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws the 3D drawing figure into page pixels by ray tracing its capsule-shaped body parts:
 * shaded (soft light from the upper left), outline (contour and part edges, for tracing) or a flat
 * silhouette. Edges are anti-aliased with four samples where body parts or the background meet.
 */
object DrawingFigureRenderer {
    private class Hit(val bone: Int, val depth: Float, val shade: Float)

    /** Premultiplied ARGB, [width] × [height]. */
    fun render(figure: DrawingFigure, width: Int, height: Int): IntArray {
        val bones = FigureRig.bones(figure)
        val camera = FigureRig.Camera(figure, width.toFloat(), height.toFloat())
        // Screen bounds of each part, so each pixel only tests the parts that can cover it.
        val boxes = bones.map { bone ->
            val a = camera.project(bone.start); val b = camera.project(bone.end)
            if (a == null || b == null) null else {
                val pad = bone.radius * 1.3f * camera.pixelsPerUnit(minOf(a.third, b.third)) + 2f
                floatArrayOf(min(a.first, b.first) - pad, min(a.second, b.second) - pad, max(a.first, b.first) + pad, max(a.second, b.second) + pad)
            }
        }
        val left = boxes.filterNotNull().minOfOrNull { it[0] }?.let { floor(it).toInt().coerceIn(0, width) } ?: return IntArray(width * height)
        val top = boxes.filterNotNull().minOf { it[1] }.let { floor(it).toInt().coerceIn(0, height) }
        val right = boxes.filterNotNull().maxOf { it[2] }.let { ceil(it).toInt().coerceIn(0, width) }
        val bottom = boxes.filterNotNull().maxOf { it[3] }.let { ceil(it).toInt().coerceIn(0, height) }
        val light = Vec3(-.45f, .7f, .55f).normalized()
        fun trace(x: Float, y: Float): Hit? {
            val (origin, direction) = camera.ray(x, y)
            var best: Hit? = null
            bones.forEachIndexed { index, bone ->
                val box = boxes[index] ?: return@forEachIndexed
                if (x < box[0] || x > box[2] || y < box[1] || y > box[3]) return@forEachIndexed
                val t = capsule(origin, direction, bone.start, bone.end, bone.radius) ?: return@forEachIndexed
                if (best != null && t >= best!!.depth) return@forEachIndexed
                val p = origin + direction * t
                val normal = normalAt(p, bone.start, bone.end)
                // Light follows the camera so the figure reads the same from every side.
                val shade = (.35f + .65f * max(0f, normal.dot(cameraLight(direction, light)))).coerceIn(0f, 1f)
                best = Hit(index, t, shade)
            }
            return best
        }
        val w = right - left; val h = bottom - top
        if (w <= 0 || h <= 0) return IntArray(width * height)
        val hits = arrayOfNulls<Hit>(w * h)
        for (y in 0 until h) for (x in 0 until w) hits[y * w + x] = trace(left + x + .5f, top + y + .5f)
        val out = IntArray(width * height)
        val fill = figure.colorArgb; val line = figure.lineArgb
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            fun differs(j: Int) = hits[j]?.bone != hits[i]?.bone
            val edge = (x > 0 && differs(i - 1)) || (x < w - 1 && differs(i + 1)) || (y > 0 && differs(i - w)) || (y < h - 1 && differs(i + w))
            val samples = if (!edge) listOf(hits[i]) else listOf(.25f to .25f, .75f to .25f, .25f to .75f, .75f to .75f).map { (dx, dy) -> trace(left + x + dx, top + y + dy) }
            var r = 0f; var g = 0f; var b = 0f; var a = 0f
            samples.forEach { hit ->
                if (hit == null) {
                    if (figure.style == FigureStyle.OUTLINE && edge && hits[i] != null) { a += 1f; r += channel(line, 16); g += channel(line, 8); b += channel(line, 0) }
                    return@forEach
                }
                val colour = when (figure.style) {
                    FigureStyle.SHADED -> floatArrayOf(channel(fill, 16) * hit.shade, channel(fill, 8) * hit.shade, channel(fill, 0) * hit.shade)
                    FigureStyle.SILHOUETTE -> floatArrayOf(channel(fill, 16), channel(fill, 8), channel(fill, 0))
                    FigureStyle.OUTLINE -> if (edge) floatArrayOf(channel(line, 16), channel(line, 8), channel(line, 0)) else floatArrayOf(1f, 1f, 1f)
                }
                val coverage = if (figure.style == FigureStyle.OUTLINE && !edge) 0f else 1f
                a += coverage; r += colour[0] * coverage; g += colour[1] * coverage; b += colour[2] * coverage
            }
            val n = samples.size.toFloat()
            val alpha = (a / n).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val a8 = (alpha * 255f).roundToInt()
            fun c(v: Float) = (v / n * 255f).roundToInt().coerceIn(0, a8)
            out[(top + y) * width + left + x] = (a8 shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
        }
        return out
    }

    private fun cameraLight(view: Vec3, light: Vec3): Vec3 {
        // A key light above and to the left of the viewing direction.
        val right = view.cross(Vec3(0f, 1f, 0f)).normalized(); val up = right.cross(view).normalized()
        return (view * -light.z + right * light.x + up * light.y).normalized()
    }

    private fun channel(argb: Int, shift: Int) = (argb ushr shift and 255) / 255f

    /** Nearest ray parameter hitting the capsule a–b of radius r (a clipped cylinder and two caps), or null. */
    internal fun capsule(ro: Vec3, rd: Vec3, a: Vec3, b: Vec3, r: Float): Float? {
        var best: Float? = null
        fun consider(t: Float) { if (t > 1e-4f && (best == null || t < best!!)) best = t }
        val ba = b - a; val baba = ba.dot(ba)
        if (baba > 1e-8f) {
            val oa = ro - a
            val bard = ba.dot(rd); val baoa = ba.dot(oa); val rdoa = rd.dot(oa); val oaoa = oa.dot(oa)
            val qa = baba - bard * bard; val qb = baba * rdoa - baoa * bard; val qc = baba * oaoa - baoa * baoa - r * r * baba
            val disc = qb * qb - qa * qc
            if (qa > 1e-8f && disc >= 0f) {
                val t = (-qb - sqrt(disc)) / qa
                val y = baoa + t * bard
                if (y in 0f..baba) consider(t)
            }
        }
        for (c in listOf(a, b)) {
            val oc = ro - c; val bb = rd.dot(oc); val h = bb * bb - (oc.dot(oc) - r * r)
            if (h >= 0f) consider(-bb - sqrt(h))
        }
        return best
    }

    private fun normalAt(p: Vec3, a: Vec3, b: Vec3): Vec3 {
        val ba = b - a; val pa = p - a
        val h = if (ba.dot(ba) < 1e-8f) 0f else (pa.dot(ba) / ba.dot(ba)).coerceIn(0f, 1f)
        return (pa - ba * h).normalized()
    }
}
