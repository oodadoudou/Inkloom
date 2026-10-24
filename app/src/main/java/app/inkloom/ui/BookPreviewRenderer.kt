package app.inkloom.ui

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import app.inkloom.document.BookBinding
import app.inkloom.document.BookCamera
import app.inkloom.document.BookFace
import app.inkloom.document.BookGeometry
import app.inkloom.document.BookLeaf
import app.inkloom.document.BookLight
import app.inkloom.document.BookMaterials
import kotlin.math.floor

/** One frame of the 3D book: [position] counts turned leaves, its fraction is the leaf turning now. */
internal class BookScene(val geometry: BookGeometry, val leaves: List<BookLeaf>, val binding: BookBinding,
                         val position: Float, val lag: Float, val camera: BookCamera, val zoom: Float,
                         val materials: BookMaterials = BookMaterials())

/**
 * Draws the book with Android's canvas: every leaf is a bitmap mesh bent along its cross-section
 * and projected in perspective, shaded per column with diffuse light, a highlight for coated and
 * glossy surfaces, the shadow of a turning leaf and the gutter of an open book; the page block's
 * edges (with the sheets' lines), the spine (with the title) and hardcover boards are polygons; a
 * soft shadow lies on the table beneath. Parts are drawn far to near, each stack bottom to top.
 */
internal object BookPreviewRenderer {
    private const val ROWS = 8
    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.SCREEN }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val white: Bitmap by lazy { createBitmap(2, 2).apply { eraseColor(android.graphics.Color.WHITE) } }
    private val path = Path()

    fun draw(canvas: Canvas, width: Float, height: Float, scene: BookScene, image: (BookFace) -> Bitmap, edgeArgb: Int, spineArgb: Int,
             spine: Bitmap? = null) {
        val view = Projector(scene, width, height)
        val count = scene.leaves.size
        val position = scene.position.coerceIn(0f, count.toFloat())
        val turned = floor(position).toInt().coerceIn(0, count)
        val turn = if (turned == count) 0f else position - turned
        val moving = if (turn > 1e-3f) turned else -1
        val open = position.coerceIn(0f, 1f) * (count - position).coerceIn(0f, 1f)
        val geometry = scene.geometry
        drawTableShadow(canvas, view, open)
        val movingProfile = if (moving >= 0) geometry.profile(moving, turn, scene.lag, open) else null
        val parts = mutableListOf<Pair<Float, () -> Unit>>()
        fun stack(top: Int, bottom: Int, lying: Float) {
            val topProfile = geometry.profile(top, lying, 0f, open)
            val bottomProfile = if (bottom == top) topProfile else geometry.profile(bottom, lying, 0f, open)
            val up = if (lying == 0f) 1f else -1f
            val seesTop = view.facesSurface(topProfile, up)
            // The turning leaf shades the top of the stack it lifts from or settles on, where that top is seen.
            val shadow = movingProfile?.takeIf { seesTop }?.let { BookLight.occlusion(topProfile, it) }
            parts += view.depthOf(topProfile) to {
                val order = if (seesTop) listOf(bottom, -1, top) else listOf(top, -1, bottom)
                order.distinct().forEach { leaf ->
                    when {
                        leaf >= 0 -> drawLeaf(canvas, view, leaf, if (leaf == top) topProfile else bottomProfile, image, open,
                            if (leaf == top) shadow else null, spineArgb)
                        bottom != top -> drawBlock(canvas, view, topProfile, bottomProfile, lying, edgeArgb, spineArgb, top - bottom, spine)
                    }
                }
            }
        }
        val firstUnturned = if (moving >= 0) moving + 1 else turned
        if (firstUnturned < count) stack(firstUnturned, count - 1, 0f)
        if (turned > 0) stack(turned - 1, 0, 1f)
        if (movingProfile != null) parts += view.depthOf(movingProfile) to { drawLeaf(canvas, view, moving, movingProfile, image, open, null, spineArgb) }
        parts.sortedBy { it.first }.forEach { it.second() }
    }

    private fun drawLeaf(canvas: Canvas, view: Projector, leaf: Int, profile: FloatArray, image: (BookFace) -> Bitmap, open: Float,
                         shadow: FloatArray?, boardArgb: Int) {
        val geometry = view.geometry
        val n = geometry.segments; val m = view.mirror
        val front = view.facesSurface(profile, 1f)
        val faces = view.scene.leaves[leaf]
        val face = if (front) faces.front else faces.back
        val bitmap = image(face)
        // A back face is seen from the other side, so its image runs from the fore-edge to the spine.
        val flip = !front xor (m < 0f)
        val sign = if (front) 1f else -1f
        val board = geometry.isBoard(leaf)
        // A board's outer face lies its thickness further out than its inner face.
        val outward = if (leaf == 0) 1f else -1f
        val lift = if (board && sign == outward) geometry.boardThickness else 0f
        val half = geometry.aspect * geometry.heightScaleOf(leaf) / 2f
        val materials = view.scene.materials
        val shiny = when (face) {
            BookFace.FrontCover, BookFace.BackCover -> materials.coverFinish.shine to materials.coverFinish.sharpness
            BookFace.InsideCover -> 0f to 1f
            else -> materials.paper.shine to 10f
        }
        val verts = FloatArray((n + 1) * (ROWS + 1) * 2)
        val colors = IntArray((n + 1) * (ROWS + 1))
        val shine = if (shiny.first > 0f) IntArray(colors.size) else null
        val normal = FloatArray(3); val toCamera = FloatArray(3); val point = FloatArray(3)
        for (c in 0..n) {
            val i = if (flip) n - c else c
            val a = (i - 1).coerceAtLeast(0); val b = (i + 1).coerceAtMost(n)
            val tx = profile[2 * b] - profile[2 * a]; val tz = profile[2 * b + 1] - profile[2 * a + 1]
            val length = kotlin.math.sqrt(tx * tx + tz * tz).coerceAtLeast(1e-6f)
            val nx = -tz / length * sign; val nz = tx / length * sign
            val px = profile[2 * i] + nx * lift; val pz = profile[2 * i + 1] + nz * lift
            // Paper darkens towards the spine of an open book; a turning leaf shades the page beneath it.
            val gutter = if (board) 1f else 1f - (.08f + .22f * open) * (1f - (i / (n * .16f)).coerceAtMost(1f)).let { it * it }
            val occluded = 1f - (shadow?.getOrNull(i) ?: 0f)
            view.normalInView(-m * tz * sign, 0f, tx * sign, normal)
            val level = (BookLight.diffuse(normal) * gutter * occluded * 255f).toInt().coerceIn(0, 255)
            val color = (0xFF shl 24) or (level shl 16) or (level shl 8) or level
            for (r in 0..ROWS) {
                val k = r * (n + 1) + c
                val y = half - 2f * half * r / ROWS
                view.project(m * px, y, pz, verts, 2 * k)
                colors[k] = color
                if (shine != null) {
                    view.toCamera(m * px, y, pz, point, toCamera)
                    val g = (BookLight.highlight(normal, toCamera, shiny.first, shiny.second) * occluded * 255f).toInt().coerceIn(0, 255)
                    shine[k] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                }
            }
        }
        canvas.drawBitmapMesh(bitmap, n, ROWS, verts, 0, colors, 0, meshPaint)
        if (shine != null) canvas.drawBitmapMesh(white, n, ROWS, verts, 0, shine, 0, shinePaint)
        if (board) drawBoardEdges(canvas, view, profile, half, outward, boardArgb)
    }

    /** A hardcover board's cut edges between its inner and outer faces, in the cover colour. */
    private fun drawBoardEdges(canvas: Canvas, view: Projector, profile: FloatArray, half: Float, outward: Float, argb: Int) {
        val n = view.geometry.segments; val t = view.geometry.boardThickness
        fun outer(i: Int): FloatArray {
            val a = (i - 1).coerceAtLeast(0); val b = (i + 1).coerceAtMost(n)
            val tx = profile[2 * b] - profile[2 * a]; val tz = profile[2 * b + 1] - profile[2 * a + 1]
            val length = kotlin.math.sqrt(tx * tx + tz * tz).coerceAtLeast(1e-6f)
            return floatArrayOf(profile[2 * i] - tz / length * t * outward, profile[2 * i + 1] + tx / length * t * outward)
        }
        val end = outer(n)
        val ex = profile[2 * n] - profile[2 * n - 2]; val ez = profile[2 * n + 1] - profile[2 * n - 1]
        polygon(canvas, view, listOf(floatArrayOf(profile[2 * n], half, profile[2 * n + 1]), floatArrayOf(profile[2 * n], -half, profile[2 * n + 1]),
            floatArrayOf(end[0], -half, end[1]), floatArrayOf(end[0], half, end[1])), floatArrayOf(view.mirror * ex, 0f, ez), argb)
        for (y in floatArrayOf(half, -half)) {
            val outline = (0..n).map { floatArrayOf(profile[2 * it], y, profile[2 * it + 1]) } + (n downTo 0).map { outer(it).let { o -> floatArrayOf(o[0], y, o[1]) } }
            polygon(canvas, view, outline, floatArrayOf(0f, if (y > 0f) 1f else -1f, 0f), argb)
        }
    }

    /** The page block between a stack's bottom and top leaves: fore-edge (with its sheets), head, tail and the square spine. */
    private fun drawBlock(canvas: Canvas, view: Projector, top: FloatArray, bottom: FloatArray, lying: Float, edgeArgb: Int, spineArgb: Int,
                          sheets: Int, spine: Bitmap?) {
        val n = view.geometry.segments; val m = view.mirror; val half = view.geometry.aspect / 2f
        val ex = top[2 * n] - top[2 * n - 2]; val ez = top[2 * n + 1] - top[2 * n - 1]
        val foreEdge = listOf(floatArrayOf(top[2 * n], half, top[2 * n + 1]), floatArrayOf(top[2 * n], -half, top[2 * n + 1]),
            floatArrayOf(bottom[2 * n], -half, bottom[2 * n + 1]), floatArrayOf(bottom[2 * n], half, bottom[2 * n + 1]))
        if (polygon(canvas, view, foreEdge, floatArrayOf(m * ex, 0f, ez), edgeArgb)) {
            // The edges of the sheets, faint lines along the fore-edge.
            val lines = sheets.coerceIn(0, 48)
            val a = FloatArray(2); val b = FloatArray(2)
            linePaint.color = 0x22000000
            for (k in 1 until lines) {
                val f = k / lines.toFloat()
                val x = top[2 * n] + (bottom[2 * n] - top[2 * n]) * f; val z = top[2 * n + 1] + (bottom[2 * n + 1] - top[2 * n + 1]) * f
                view.project(m * x, half, z, a, 0); view.project(m * x, -half, z, b, 0)
                canvas.drawLine(a[0], a[1], b[0], b[1], linePaint)
            }
        }
        for (y in floatArrayOf(half, -half)) {
            val outline = (0..n).map { floatArrayOf(top[2 * it], y, top[2 * it + 1]) } + (n downTo 0).map { floatArrayOf(bottom[2 * it], y, bottom[2 * it + 1]) }
            polygon(canvas, view, outline, floatArrayOf(0f, if (y > 0f) 1f else -1f, 0f), edgeArgb)
        }
        if (top[1] - bottom[1] > 1e-4f) {
            val spineQuad = listOf(floatArrayOf(0f, half, top[1]), floatArrayOf(0f, -half, top[1]), floatArrayOf(0f, -half, bottom[1]), floatArrayOf(0f, half, bottom[1]))
            val normal = floatArrayOf(if (lying == 0f) -m else m, 0f, 0f)
            if (polygon(canvas, view, spineQuad, normal, spineArgb) && spine != null && lying == 0f) drawSpineTitle(canvas, view, spineQuad, normal, spine)
        }
    }

    /** The spine's image (the title running down it) mapped onto the spine of the closed page block. */
    private fun drawSpineTitle(canvas: Canvas, view: Projector, quad: List<FloatArray>, normal: FloatArray, spine: Bitmap) {
        val m = view.mirror
        // The title reads from head to tail with its top towards the front cover; a right-bound book is
        // a mirror image, so its top edge maps to the back to keep the text readable.
        val corners = if (m > 0f) listOf(quad[0], quad[1], quad[3], quad[2]) else listOf(quad[3], quad[2], quad[0], quad[1])
        val verts = FloatArray(8)
        corners.forEachIndexed { index, p -> view.project(m * p[0], p[1], p[2], verts, 2 * index) }
        // On a thin book the spine is a sliver; the title would only be specks there.
        if (kotlin.math.hypot(verts[4] - verts[0], verts[5] - verts[1]) < 14f) return
        val n3 = FloatArray(3); view.normalInView(normal[0], normal[1], normal[2], n3)
        val level = (BookLight.diffuse(n3) * 255f).toInt().coerceIn(0, 255)
        val color = (0xFF shl 24) or (level shl 16) or (level shl 8) or level
        canvas.drawBitmapMesh(spine, 1, 1, verts, 0, IntArray(4) { color }, 0, meshPaint)
    }

    /** A soft shadow on the table under the book, offset away from the light; drawn only when the table is seen from above. */
    private fun drawTableShadow(canvas: Canvas, view: Projector, open: Float) {
        val geometry = view.geometry; val m = view.mirror
        if (!view.faces(0f, 0f, -.001f, 0f, 0f, 1f)) return
        val half = geometry.aspect * (if (geometry.hardCover) 1.045f else 1f) / 2f
        val count = view.scene.leaves.size
        val position = view.scene.position.coerceIn(0f, count.toFloat())
        // Closed it covers one page width, open two.
        val left = -position.coerceIn(0f, 1f); val right = (count - position).coerceIn(0f, 1f)
        val table = if (geometry.hardCover) -geometry.boardThickness - .002f else -.002f
        val dx = .05f; val dy = -.06f
        for (ring in 6 downTo 0) {
            val grow = .012f * ring
            val corners = listOf(floatArrayOf(left - grow + dx, half + grow + dy), floatArrayOf(right + grow + dx, half + grow + dy),
                floatArrayOf(right + grow + dx, -half - grow + dy), floatArrayOf(left - grow + dx, -half - grow + dy))
            val screen = FloatArray(2)
            path.rewind()
            corners.forEachIndexed { index, c ->
                view.project(m * c[0], c[1], table, screen, 0)
                if (index == 0) path.moveTo(screen[0], screen[1]) else path.lineTo(screen[0], screen[1])
            }
            path.close()
            fillPaint.color = ((.05f + .03f * open) * 255).toInt().coerceIn(0, 255) shl 24
            canvas.drawPath(path, fillPaint)
        }
    }

    /** A flat polygon given in unmirrored book space, drawn only when its [normal] faces the camera; returns whether it was drawn. */
    private fun polygon(canvas: Canvas, view: Projector, points: List<FloatArray>, normal: FloatArray, argb: Int): Boolean {
        val m = view.mirror
        val center = FloatArray(3).also { c -> points.forEach { p -> c[0] += m * p[0] / points.size; c[1] += p[1] / points.size; c[2] += p[2] / points.size } }
        if (!view.faces(center[0], center[1], center[2], normal[0], normal[1], normal[2])) return false
        val screen = FloatArray(2)
        path.rewind()
        points.forEachIndexed { index, p ->
            view.project(m * p[0], p[1], p[2], screen, 0)
            if (index == 0) path.moveTo(screen[0], screen[1]) else path.lineTo(screen[0], screen[1])
        }
        path.close()
        val n3 = FloatArray(3); view.normalInView(normal[0], normal[1], normal[2], n3)
        val level = BookLight.diffuse(n3)
        fun channel(shift: Int) = ((argb ushr shift and 255) * level).toInt().coerceIn(0, 255)
        fillPaint.color = (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        canvas.drawPath(path, fillPaint)
        return true
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

        /** The unit direction from a book-space point to the camera, in view space. */
        fun toCamera(x: Float, y: Float, z: Float, scratch: FloatArray, out: FloatArray) {
            scene.camera.rotate(x - centerX, y, z - centerZ, scratch)
            val dx = -scratch[0]; val dy = -scratch[1]; val dz = scene.camera.distance - scratch[2]
            val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
            out[0] = dx / length; out[1] = dy / length; out[2] = dz / length
        }

        fun normalInView(nx: Float, ny: Float, nz: Float, out: FloatArray) {
            scene.camera.rotate(nx, ny, nz, v)
            val unit = BookCamera.normalize(v)
            out[0] = unit[0]; out[1] = unit[1]; out[2] = unit[2]
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
    }
}
