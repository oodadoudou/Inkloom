package app.inkloom.brushpresetv5

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BrushSourceImagesTest {
    private fun gray(pixel: Int) = pixel and 255
    private fun columnDifference(image: SourcePixels, a: Int, b: Int) =
        (0 until image.height).sumOf { y -> abs(gray(image.argb[y * image.width + a]) - gray(image.argb[y * image.width + b])) }.toDouble() / image.height
    private fun rowDifference(image: SourcePixels, a: Int, b: Int) =
        (0 until image.width).sumOf { x -> abs(gray(image.argb[a * image.width + x]) - gray(image.argb[b * image.width + x])) }.toDouble() / image.width

    @Test fun builtInPaperTexturesRepeatWithoutASeam() {
        BuiltInBrushSource.entries.filter { it.grain }.forEach { source ->
            val image = BrushSourceImages.generate(source)
            assertEquals(BrushSourceImages.GRAIN_SIZE, image.width)
            assertTrue("$source is opaque", image.argb.all { it ushr 24 == 255 })
            // Across the wrap the change is like any other neighbouring column or row, not a jump.
            val interior = (columnDifference(image, 255, 256) + rowDifference(image, 255, 256)) / 2
            val wrap = maxOf(columnDifference(image, image.width - 1, 0), rowDifference(image, image.height - 1, 0))
            assertTrue("$source wraps: $wrap vs $interior", wrap <= interior * 3 + 3)
            // It has texture, not a flat fill.
            assertTrue("$source varies", image.argb.map(::gray).let { it.max() - it.min() } > 60)
        }
    }

    @Test fun builtInTipsAreWhiteShapesOnTransparencyAndEveryGenerationIsTheSame() {
        BuiltInBrushSource.entries.filterNot { it.grain }.forEach { source ->
            val image = BrushSourceImages.generate(source)
            assertEquals(BrushSourceImages.TIP_SIZE, image.width)
            assertEquals("$source corner", 0, image.argb[0] ushr 24)
            assertTrue("$source has coverage", image.argb.count { it ushr 24 > 128 } > 200)
            assertTrue("$source is white ink", image.argb.all { it and 0xFFFFFF == 0xFFFFFF })
        }
        BuiltInBrushSource.entries.forEach { assertArrayEquals(BrushSourceImages.generate(it).argb, BrushSourceImages.generate(it).argb) }
    }

    @Test fun trimmingKeepsOnlyTheDrawnPartOfTransparentAndWhiteBackedImages() {
        val transparent = SourcePixels(40, 30, IntArray(1200) { i -> if (i % 40 in 10..19 && i / 40 in 5..9) 0xFF000000.toInt() else 0 })
        assertArrayEquals(intArrayOf(10, 5, 20, 10), BrushSourceImages.contentBounds(transparent))
        val trimmed = BrushSourceImages.trimmed(transparent)
        assertEquals(14, trimmed.width); assertEquals(9, trimmed.height)
        val white = SourcePixels(40, 30, IntArray(1200) { i -> if (i % 40 in 30..35 && i / 40 in 20..27) 0xFF202020.toInt() else 0xFFFFFFFF.toInt() })
        assertArrayEquals(intArrayOf(30, 20, 36, 28), BrushSourceImages.contentBounds(white))
        assertNull(BrushSourceImages.contentBounds(SourcePixels(4, 4, IntArray(16) { 0xFFFFFFFF.toInt() })))
        assertThrows(IllegalArgumentException::class.java) { BrushSourceImages.trimmed(SourcePixels(4, 4, IntArray(16))) }
    }

    @Test fun squaringCentresTheImageWithoutCuttingIt() {
        // A transparent image is padded with transparency…
        val wide = SourcePixels(10, 4, IntArray(40) { if (it == 0) 0 else 0xFF000000.toInt() })
        val square = BrushSourceImages.squared(wide)
        assertEquals(10, square.width); assertEquals(10, square.height)
        assertEquals(0xFF000000.toInt(), square.argb[3 * 10 + 5])
        assertEquals(0xFF000000.toInt(), square.argb[6 * 10 + 5])
        assertEquals(39, square.argb.count { it == 0xFF000000.toInt() })
        // …an opaque one with its border colour.
        val paper = SourcePixels(6, 2, IntArray(12) { if (it == 8) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() })
        assertEquals(35, BrushSourceImages.squared(paper).argb.count { it == 0xFFFFFFFF.toInt() })
        val tall = SourcePixels(2, 6, IntArray(12) { if (it == 0) 0xFF000000.toInt() else 0 })
        assertEquals(0xFF000000.toInt(), BrushSourceImages.squared(tall).argb[2])
        assertEquals(36, BrushSourceImages.squared(tall).argb.size)
    }
}
