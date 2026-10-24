package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import app.inkloom.document.*
import app.inkloom.ui.BookPreviewRenderer
import app.inkloom.ui.BookScene
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 3D book's materials and light, rendered to bitmaps and measured. */
class BookPreviewRenderTest {
    private val size = 600
    private val cover = createBitmap(64, 90).apply { eraseColor(0xFF3A4A6A.toInt()) }
    private val paper = createBitmap(64, 90).apply { eraseColor(0xFFF8F6F0.toInt()) }
    private val leaves = BookLayout.leaves(8, cover = true, firstPageOnLeft = false)

    private fun render(position: Float, materials: BookMaterials, yaw: Float = 0f, pitch: Float = -28f): Bitmap {
        val geometry = BookGeometry(1.4f, leaves.size, BookStitch.SIDE, hardCover = materials.coverType == BookCoverType.HARD)
        val scene = BookScene(geometry, leaves, BookBinding.LEFT, position, 1f, BookCamera(yaw, pitch), 1f, materials)
        return createBitmap(size, size).also { bitmap ->
            Canvas(bitmap).apply { drawColor(0xFF3A3A3A.toInt()) }
                .let { BookPreviewRenderer.draw(it, size.toFloat(), size.toFloat(), scene, { face -> if (face == BookFace.FrontCover || face == BookFace.BackCover) cover else paper }, 0xFFEFEBE3.toInt(), 0xFF3A4A6A.toInt()) }
        }
    }
    private fun meanLuma(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Float {
        var sum = 0.0; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { val p = bitmap.getPixel(x, y); sum += .3 * Color.red(p) + .59 * Color.green(p) + .11 * Color.blue(p); n++ }
        return (sum / n).toFloat()
    }
    /** Width of the blue cover across the middle row (the grey table and its shadow are not counted). */
    private fun bookWidth(bitmap: Bitmap): Int {
        val row = size / 2
        val xs = (0 until size).filter { x -> bitmap.getPixel(x, row).let { Color.blue(it) - Color.red(it) > 15 } }
        return if (xs.isEmpty()) 0 else xs.last() - xs.first()
    }

    @Test fun glossShinesMoreThanCoatedWhichShinesMoreThanNoFinish() {
        // Turned to a glancing view of the closed front cover.
        val none = render(0f, BookMaterials(coverFinish = BookFinish.NONE), yaw = 35f, pitch = -50f)
        val coated = render(0f, BookMaterials(coverFinish = BookFinish.COATED), yaw = 35f, pitch = -50f)
        val gloss = render(0f, BookMaterials(coverFinish = BookFinish.GLOSS), yaw = 35f, pitch = -50f)
        val c = size / 2; val r = 40
        val a = meanLuma(none, c - r, c - r, c + r, c + r); val b = meanLuma(coated, c - r, c - r, c + r, c + r); val g = meanLuma(gloss, c - r, c - r, c + r, c + r)
        assertTrue("none $a < coated $b < gloss $g", a < b && b < g && g - a > 12f)
    }

    @Test fun hardcoverBoardsOverhangThePageBlock() {
        val soft = render(0f, BookMaterials(coverType = BookCoverType.SOFT), pitch = 0f)
        val hard = render(0f, BookMaterials(coverType = BookCoverType.HARD), pitch = 0f)
        assertTrue("hard ${bookWidth(hard)} > soft ${bookWidth(soft)}", bookWidth(hard) > bookWidth(soft) + 4)
    }

    @Test fun aTurningLeafShadesThePageBeneathIt() {
        // Open at the middle, then with the right-hand leaf lifted halfway.
        val still = render(3f, BookMaterials(), pitch = -60f)
        val turning = render(3.35f, BookMaterials(), pitch = -60f)
        // The right page near the spine, under where the lifted leaf hangs.
        val x0 = size / 2 + 20; val x1 = size / 2 + 70; val y0 = size / 2 - 40; val y1 = size / 2 + 40
        assertTrue("shadowed ${meanLuma(turning, x0, y0, x1, y1)} < lit ${meanLuma(still, x0, y0, x1, y1)}", meanLuma(turning, x0, y0, x1, y1) < meanLuma(still, x0, y0, x1, y1) - 4f)
    }
}
