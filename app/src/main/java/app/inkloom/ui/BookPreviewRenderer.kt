package app.inkloom.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import app.inkloom.document.BookBinding
import app.inkloom.document.BookCamera
import app.inkloom.document.BookFace
import app.inkloom.document.BookGeometry
import app.inkloom.document.BookLeaf
import kotlin.math.floor
import kotlin.math.max

/** One frame of the 3D book: [position] counts turned leaves, its fraction is the leaf turning now. */
internal class BookScene(val geometry: BookGeometry, val leaves: List<BookLeaf>, val binding: BookBinding,
                         val position: Float, val lag: Float, val camera: BookCamera, val zoom: Float)

/**
 * Draws the book with Android's canvas: every leaf is a bitmap mesh bent along its cross-section
 * and projected in perspective, shaded per column; the page block's edges and spine are filled
 * polygons. Parts are drawn far to near, and each stack bottom to top as seen from the camera.
 */
internal object BookPreviewRenderer {
    private const val ROWS = 8
    private val light = BookCamera.normalize(floatArrayOf(-.35f, .55f, .75f))
    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun draw(canvas: Canvas, width: Float, height: Float, scene: BookScene, image: (BookFace) -> Bitmap, edgeArgb: Int, spineArgb: Int) {
        val view = Projector(scene, width, height)
        val count = scene.leaves.size
        val position = scene.position.coerceIn(0f, count.toFloat())
        val turned = floor(position).toInt().coerceIn(0, count)
        val turn = if (turned == count) 0f else position - turned
        val moving = if (turn > 1e-3f) turned else -1
        val open = position.coerceIn(0f, 1f) * (count - position).coerceIn(0f, 1f)
        val geometry = scene.geometry
        val parts = mutableListOf<Pair<Float, () -> Unit>>()
        fun stack(top: Int, bottom: Int, lying: Float) {
            val topProfile = geometry.profile(top, lying, 0f, open)
            val bottomProfile = if (bottom == top) topProfile else geometry.profile(bottom, lying, 0f, open)
            val up = if (lying == 0f) 1f else -1f
            val seesTop = view.facesSurface(topProfile, up)
            parts += view.depthOf(topProfile) to {
                val order = if (seesTop) listOf(bottom, -1, top) else listOf(top, -1, bottom)
                order.distinct().forEach { leaf ->
                    when {
                        leaf >= 0 -> drawLeaf(canvas, view, leaf, if (leaf == top) topProfile else bottomProfile, image)
                        bottom != top -> drawBlock(canvas, view, topProfile, bottomProfile, lying, edgeArgb, spineArgb)
                    }
                }
            }
        }
        val firstUnturned = if (moving >= 0) moving + 1 else turned
        if (firstUnturned < count) stack(firstUnturned, count - 1, 0f)
        if (turned > 0) stack(turned - 1, 0, 1f)
        if (moving >= 0) {
            val profile = geometry.profile(moving, turn, scene.lag, open)
            parts += view.depthOf(profile) to { drawLeaf(canvas, view, moving, profile, image) }
        }
        parts.sortedBy { it.first }.forEach { it.second() }
    }

    private fun drawLeaf(canvas: Canvas, view: Projector, leaf: Int, profile: FloatArray, image: (BookFace) -> Bitmap) {
        val n = view.geometry.segments; val m = view.mirror
        val front = view.facesSurface(profile, 1f)
        val faces = view.scene.leaves[leaf]
        val bitmap = image(if (front) faces.front else faces.back)
        // A back face is seen from the other side, so its image runs from the fore-edge to the spine.
        val flip = !front xor (m < 0f)
        val sign = if (front) 1f else -1f
        val verts = FloatArray((n + 1) * (ROWS + 1) * 2)
        val colors = IntArray((n + 1) * (ROWS + 1))
        for (c in 0..n) {
            val i = if (flip) n - c else c
            val a = (i - 1).coerceAtLeast(0); val b = (i + 1).coerceAtMost(n)
            val tx = profile[2 * b] - profile[2 * a]; val tz = profile[2 * b + 1] - profile[2 * a + 1]
            val gutter = .8f + .2f * (i / (n * .1f)).coerceAtMost(1f)
            val level = (view.shade(-m * tz * sign, 0f, tx * sign) * gutter * 255f).toInt().coerceIn(0, 255)
            val color = (0xFF shl 24) or (level shl 16) or (level shl 8) or level
            for (r in 0..ROWS) {
                val k = r * (n + 1) + c
                view.project(m * profile[2 * i], view.geometry.aspect * (.5f - r / ROWS.toFloat()), profile[2 * i + 1], verts, 2 * k)
                colors[k] = color
            }
        }
        canvas.drawBitmapMesh(bitmap, n, ROWS, verts, 0, colors, 0, meshPaint)
    }

    /** The page block between a stack's bottom and top leaves: fore-edge, head, tail and the square spine. */
    private fun drawBlock(canvas: Canvas, view: Projector, top: FloatArray, bottom: FloatArray, lying: Float, edgeArgb: Int, spineArgb: Int) {
        val n = view.geometry.segments; val m = view.mirror; val half = view.geometry.aspect / 2f
        val ex = top[2 * n] - top[2 * n - 2]; val ez = top[2 * n + 1] - top[2 * n - 1]
        polygon(canvas, view, listOf(floatArrayOf(top[2 * n], half, top[2 * n + 1]), floatArrayOf(top[2 * n], -half, top[2 * n + 1]),
            floatArrayOf(bottom[2 * n], -half, bottom[2 * n + 1]), floatArrayOf(bottom[2 * n], half, bottom[2 * n + 1])), floatArrayOf(m * ex, 0f, ez), edgeArgb)
        for (y in floatArrayOf(half, -half)) {
            val outline = (0..n).map { floatArrayOf(top[2 * it], y, top[2 * it + 1]) } + (n downTo 0).map { floatArrayOf(bottom[2 * it], y, bottom[2 * it + 1]) }
            polygon(canvas, view, outline, floatArrayOf(0f, if (y > 0f) 1f else -1f, 0f), edgeArgb)
        }
        if (top[1] - bottom[1] > 1e-4f) polygon(canvas, view, listOf(floatArrayOf(0f, half, top[1]), floatArrayOf(0f, -half, top[1]),
            floatArrayOf(0f, -half, bottom[1]), floatArrayOf(0f, half, bottom[1])), floatArrayOf(if (lying == 0f) -m else m, 0f, 0f), spineArgb)
    }

    /** A flat polygon given in unmirrored book space, drawn only when its [normal] faces the camera. */
    private fun polygon(canvas: Canvas, view: Projector, points: List<FloatArray>, normal: FloatArray, argb: Int) {
        val m = view.mirror
        val center = FloatArray(3).also { c -> points.forEach { p -> c[0] += m * p[0] / points.size; c[1] += p[1] / points.size; c[2] += p[2] / points.size } }
        if (!view.faces(center[0], center[1], center[2], normal[0], normal[1], normal[2])) return
        val screen = FloatArray(2)
        path.rewind()
        points.forEachIndexed { index, p ->
            view.project(m * p[0], p[1], p[2], screen, 0)
            if (index == 0) path.moveTo(screen[0], screen[1]) else path.lineTo(screen[0], screen[1])
        }
        path.close()
        val level = view.shade(normal[0], normal[1], normal[2])
        fun channel(shift: Int) = ((argb ushr shift and 255) * level).toInt().coerceIn(0, 255)
        fillPaint.color = (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        canvas.drawPath(path, fillPaint)
    }

    /** Book space to screen: mirrors right-bound books, centres the visible book and projects in perspective. */
    private class Projector(val scene: BookScene, width: Float, height: Float) {
        val geometry = scene.geometry
        val mirror = if (scene.binding == BookBinding.RIGHT) -1f else 1f
        private val count = scene.leaves.size
        private val position = scene.position.coerceIn(0f, count.toFloat())
        // A closed book is centred on itself, an open one on its spine.
        private val centerX = mirror * (.5f * (1f - position.coerceIn(0f, 1f)) - .5f * (1f - (count - position).coerceIn(0f, 1f)))
        private val centerZ = geometry.thickness / 2f
        private val focal = scene.zoom * scene.camera.distance * minOf(.42f * width, .84f * height / geometry.aspect)
        private val cx = width / 2f; private val cy = height / 2f
        private val v = FloatArray(3)

        fun project(x: Float, y: Float, z: Float, out: FloatArray, at: Int) {
            scene.camera.rotate(x - centerX, y, z - centerZ, v)
            val d = (scene.camera.distance - v[2]).coerceAtLeast(.05f)
            out[at] = cx + focal * v[0] / d; out[at + 1] = cy - focal * v[1] / d
        }

        fun faces(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) = scene.camera.faces(x - centerX, y, z - centerZ, nx, ny, nz)

        /** Whether the camera sees the side of a leaf whose normal is [sign] × its front normal. */
        fun facesSurface(profile: FloatArray, sign: Float): Boolean {
            val i = geometry.segments / 2
            val dx = profile[2 * i + 2] - profile[2 * i]; val dz = profile[2 * i + 3] - profile[2 * i + 1]
            return faces(mirror * profile[2 * i], 0f, profile[2 * i + 1], -mirror * dz * sign, 0f, dx * sign)
        }

        /** View depth of the leaf's middle; larger is nearer. */
        fun depthOf(profile: FloatArray): Float {
            val i = geometry.segments / 2
            scene.camera.rotate(mirror * profile[2 * i] - centerX, 0f, profile[2 * i + 1] - centerZ, v)
            return v[2]
        }

        fun shade(nx: Float, ny: Float, nz: Float): Float {
            scene.camera.rotate(nx, ny, nz, v)
            val n = BookCamera.normalize(v)
            return .6f + .4f * max(0f, n[0] * light[0] + n[1] * light[1] + n[2] * light[2])
        }
    }
}
