package app.inkloom.document

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The edge a book is bound on: LEFT for left-to-right reading, RIGHT for right-to-left (manga). */
enum class BookBinding { LEFT, RIGHT }

/** Saddle stitch folds the sheets round a curved spine; side stitch binds them flat with a square spine. */
enum class BookStitch { SADDLE, SIDE }

/** Softcover bends like the pages (paperback); hardcover boards are rigid, thicker and overhang the pages. */
enum class BookCoverType { SOFT, HARD }

/** How much the cover shines, as Clip Studio Share's 3D books offer: no finish, coated (light shine) or gloss. */
enum class BookFinish(val shine: Float, val sharpness: Float) { NONE(0f, 8f), COATED(.32f, 9f), GLOSS(.85f, 26f) }

/** Inside pages: matte book paper with a fine fibre, or coated paper with a slight shine. */
enum class BookPaper(val shine: Float) { BOOK(0f), COATED(.14f) }

/** The book's make-up for the preview. */
data class BookMaterials(val coverType: BookCoverType = BookCoverType.SOFT, val coverFinish: BookFinish = BookFinish.COATED,
                         val paper: BookPaper = BookPaper.BOOK)

/** Light on the book: diffuse shade plus a Blinn–Phong highlight for shiny surfaces, all in view space. */
object BookLight {
    /** Light from the upper left, in front of the book. */
    val direction: FloatArray = BookCamera.normalize(floatArrayOf(-.35f, .55f, .75f))

    fun diffuse(normal: FloatArray): Float = .6f + .4f * maxOf(0f, dot(normal, direction))

    /**
     * Highlight strength (0–1) at a surface point [toCamera] from its unit [normal]: the light's
     * reflection, plus the sheen a shiny surface takes on at glancing angles (Fresnel).
     */
    fun highlight(normal: FloatArray, toCamera: FloatArray, shine: Float, sharpness: Float): Float {
        if (shine <= 0f) return 0f
        val half = BookCamera.normalize(floatArrayOf(direction[0] + toCamera[0], direction[1] + toCamera[1], direction[2] + toCamera[2]))
        val glancing = (1f - maxOf(0f, dot(normal, toCamera))).pow(2)
        return (shine * (maxOf(0f, dot(normal, half)).pow(sharpness) + .6f * glancing)).coerceIn(0f, 1f)
    }

    /**
     * How much a turning leaf darkens the stack beneath it, per point of the stack's cross-section:
     * strongest where the leaf is closest above, fading as it lifts away (ambient occlusion).
     */
    fun occlusion(stack: FloatArray, turning: FloatArray, reach: Float = .45f, strength: Float = .42f): FloatArray {
        val points = stack.size / 2
        return FloatArray(points) { i ->
            val x = stack[2 * i]; val z = stack[2 * i + 1]
            var gap = Float.MAX_VALUE
            for (j in 0 until turning.size / 2) {
                val dx = turning[2 * j] - x; val dz = turning[2 * j + 1] - z
                if (dz > 0f) gap = minOf(gap, sqrt(dx * dx + dz * dz))
            }
            if (gap == Float.MAX_VALUE) 0f else strength * (1f - gap / reach).coerceIn(0f, 1f).pow(2)
        }
    }

    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}

/** What one side of a leaf shows. */
sealed interface BookFace {
    data object FrontCover : BookFace
    data object InsideCover : BookFace
    data object BackCover : BookFace
    data object Blank : BookFace
    data class Page(val index: Int) : BookFace
}

/** One sheet of the bound book: its [front] shows before it is turned, its [back] after. */
data class BookLeaf(val front: BookFace, val back: BookFace)

/** A notebook bound as a book (CSP's 3D preview for binding): its leaves and what each opening shows. */
object BookLayout {
    fun leaves(pageCount: Int, cover: Boolean, firstPageOnLeft: Boolean): List<BookLeaf> {
        require(pageCount >= 0) { "Page count cannot be negative" }
        val faces = buildList {
            if (firstPageOnLeft) add(BookFace.Blank)
            repeat(pageCount) { add(BookFace.Page(it)) }
            if (size % 2 == 1) add(BookFace.Blank)
        }
        val inner = faces.chunked(2) { BookLeaf(it[0], it[1]) }
        return if (!cover) inner.ifEmpty { listOf(BookLeaf(BookFace.Blank, BookFace.Blank)) }
        else listOf(BookLeaf(BookFace.FrontCover, BookFace.InsideCover)) + inner + BookLeaf(BookFace.InsideCover, BookFace.BackCover)
    }

    /** How many leaves are turned when [pageIndex] shows. */
    fun turnedFor(leaves: List<BookLeaf>, pageIndex: Int): Int {
        leaves.forEachIndexed { index, leaf ->
            if (leaf.front == BookFace.Page(pageIndex)) return index
            if (leaf.back == BookFace.Page(pageIndex)) return index + 1
        }
        return 0
    }

    /** The two faces of the opening after [turned] leaves: the turned leaf's back and the next leaf's front. */
    fun opening(leaves: List<BookLeaf>, turned: Int): Pair<BookFace?, BookFace?> =
        leaves.getOrNull(turned - 1)?.back to leaves.getOrNull(turned)?.front

    /** Page indices shown at that opening, in reading order. */
    fun pagesAt(leaves: List<BookLeaf>, turned: Int): List<Int> =
        opening(leaves, turned).toList().filterIsInstance<BookFace.Page>().map { it.index }.sorted()
}

/**
 * The book's shape in book space, with the page width as the unit: x runs across from the spine
 * (unturned leaves lie at +x), y up the page and z out of the front of the unturned stack. Each leaf
 * is a strip bent only across its width, described by its cross-section from the spine outwards.
 */
class BookGeometry(val aspect: Float, val leafCount: Int, val stitch: BookStitch, val segments: Int = 24,
                   /** The first and last leaves are hardcover boards (needs a cover). */ val hardCover: Boolean = false) {
    init { require(aspect > 0f && leafCount > 0 && segments >= 4) { "A book needs pages and a shape" } }

    /** Leaf thickness, exaggerated a little so thin notebooks still read as a book. */
    val leafThickness: Float = maxOf(.0012f, .012f / leafCount)
    /** A hardcover board: several sheets thick. */
    val boardThickness: Float = maxOf(.012f, leafThickness * 3f)

    fun isBoard(leaf: Int): Boolean = hardCover && leafCount >= 2 && (leaf == 0 || leaf == leafCount - 1)
    fun thicknessOf(leaf: Int): Float = if (isBoard(leaf)) boardThickness else leafThickness
    /** Boards overhang the page block at the fore-edge, head and tail. */
    fun widthOf(leaf: Int): Float = if (isBoard(leaf)) 1.035f else 1f
    fun heightScaleOf(leaf: Int): Float = if (isBoard(leaf)) 1.045f else 1f
    val thickness: Float get() = (0 until leafCount).sumOf { thicknessOf(it).toDouble() }.toFloat()

    fun unturnedHeight(leaf: Int): Float = ((leaf + 1) until leafCount).sumOf { thicknessOf(it).toDouble() }.toFloat()
    fun turnedHeight(leaf: Int): Float = (0 until leaf).sumOf { thicknessOf(it).toDouble() }.toFloat()

    /**
     * The leaf's cross-section as (x, z) pairs from the spine to the fore-edge: lying on the
     * unturned stack at [turn] 0, upright at 0.5 and lying on the turned stack at 1. [lag] bends the
     * free edge behind while it turns (+1 forwards, -1 backwards); [open] (0–1) adds the gutter
     * curve of an open book.
     */
    fun profile(leaf: Int, turn: Float, lag: Float, open: Float): FloatArray {
        val unturned = unturnedHeight(leaf); val turned = turnedHeight(leaf)
        val out = FloatArray((segments + 1) * 2)
        var x = 0f
        var z = if (stitch == BookStitch.SADDLE) 0f else unturned + (turned - unturned) * turn
        out[1] = z
        // A hardcover board stays flat and swings on its hinge; paper bends and lags.
        val board = isBoard(leaf)
        val bend = if (board) 0f else LAG * sin(PI.toFloat() * turn) * lag
        val length = widthOf(leaf)
        for (i in 0 until segments) {
            val s = (i + .5f) / segments
            val angle = if (board) PI.toFloat() * turn
                else (1f - turn) * gutter(unturned, s, open) + turn * (PI.toFloat() - gutter(turned, s, open)) - bend * s.pow(1.5f)
            x += cos(angle) * length / segments; z += sin(angle) * length / segments
            out[2 * i + 2] = x; out[2 * i + 3] = z
        }
        return out
    }

    /** Angle of the leaf rising from the spine to its place in the stack, fading to flat across the gutter. */
    private fun gutter(height: Float, s: Float, open: Float): Float {
        val width = if (stitch == BookStitch.SADDLE) .22f else .1f
        val rise = if (stitch == BookStitch.SADDLE) height + .045f * open else .008f * open
        return (2f * rise / width).coerceAtMost(1.2f) * (1f - s / width).coerceAtLeast(0f)
    }

    private companion object { const val LAG = .45f }
}

/** Orbit camera on the book: yaw turns it round its vertical axis, pitch tips it towards the viewer. */
class BookCamera(yawDegrees: Float, pitchDegrees: Float, val distance: Float = 3.4f) {
    private val cy = cos(Math.toRadians(yawDegrees.toDouble())).toFloat()
    private val sy = sin(Math.toRadians(yawDegrees.toDouble())).toFloat()
    private val cp = cos(Math.toRadians(pitchDegrees.toDouble())).toFloat()
    private val sp = sin(Math.toRadians(pitchDegrees.toDouble())).toFloat()

    /** Rotates a book-space vector into view space (camera on +z, [distance] away), writing x, y, z at [offset]. */
    fun rotate(x: Float, y: Float, z: Float, out: FloatArray, offset: Int = 0) {
        val x1 = x * cy + z * sy; val z1 = -x * sy + z * cy
        out[offset] = x1; out[offset + 1] = y * cp - z1 * sp; out[offset + 2] = y * sp + z1 * cp
    }

    /** Whether a surface at book-space point p with normal n faces the camera. */
    fun faces(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float): Boolean {
        val p = FloatArray(3).also { rotate(px, py, pz, it) }
        val n = FloatArray(3).also { rotate(nx, ny, nz, it) }
        return n[0] * -p[0] + n[1] * -p[1] + n[2] * (distance - p[2]) > 0f
    }

    companion object {
        fun normalize(v: FloatArray): FloatArray {
            val length = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
            return floatArrayOf(v[0] / length, v[1] / length, v[2] / length)
        }
    }
}
