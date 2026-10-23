package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BookPreviewTest {
    @Test fun coversWrapThePagesAndAnOddPageEndsOnABlank() {
        val leaves = BookLayout.leaves(3, cover = true, firstPageOnLeft = false)
        assertEquals(listOf(
            BookLeaf(BookFace.FrontCover, BookFace.InsideCover),
            BookLeaf(BookFace.Page(0), BookFace.Page(1)),
            BookLeaf(BookFace.Page(2), BookFace.Blank),
            BookLeaf(BookFace.InsideCover, BookFace.BackCover)), leaves)
        assertEquals(BookFace.InsideCover to BookFace.Page(0), BookLayout.opening(leaves, 1))
        assertEquals(listOf(1, 2), BookLayout.pagesAt(leaves, 2))
        assertEquals(null to BookFace.FrontCover, BookLayout.opening(leaves, 0))
        assertEquals(BookFace.BackCover to null, BookLayout.opening(leaves, leaves.size))
    }

    @Test fun firstPageOnTheLeftOpensBesideTheSecond() {
        val leaves = BookLayout.leaves(4, cover = true, firstPageOnLeft = true)
        val turned = BookLayout.turnedFor(leaves, 0)
        assertEquals(listOf(0, 1), BookLayout.pagesAt(leaves, turned))
        assertEquals(BookFace.Page(0), BookLayout.opening(leaves, turned).first)
        assertEquals(turned, BookLayout.turnedFor(leaves, 1))
        assertEquals(turned + 1, BookLayout.turnedFor(leaves, 2))
    }

    @Test fun withoutACoverTheFirstPageIsTheFront() {
        val leaves = BookLayout.leaves(2, cover = false, firstPageOnLeft = false)
        assertEquals(listOf(BookLeaf(BookFace.Page(0), BookFace.Page(1))), leaves)
        assertEquals(0, BookLayout.turnedFor(leaves, 0))
    }

    @Test fun aTurningLeafLiesRightStandsUpAndLiesLeft() {
        BookStitch.entries.forEach { stitch ->
            val book = BookGeometry(1.4f, 10, stitch)
            val flat = book.profile(4, 0f, 0f, 1f)
            assertTrue("$stitch reaches the fore-edge", flat[flat.size - 2] > .95f)
            val upright = book.profile(4, .5f, 0f, 1f)
            assertTrue("$stitch stands up", abs(upright[upright.size - 2]) < .2f && upright.last() > .9f)
            val turned = book.profile(4, 1f, 0f, 1f)
            assertTrue("$stitch lies on the other side", turned[turned.size - 2] < -.95f)
        }
    }

    @Test fun aTurningLeafNeverSinksIntoTheStacks() {
        val book = BookGeometry(1.4f, 30, BookStitch.SADDLE)
        for (lag in listOf(-1f, 1f)) for (step in 0..20) {
            val profile = book.profile(12, step / 20f, lag, 1f)
            for (i in 1 until profile.size / 2) assertTrue("turn ${step / 20f} lag $lag point $i", profile[2 * i + 1] >= -1e-4f)
        }
    }

    @Test fun lyingLeavesStackInOrder() {
        val book = BookGeometry(1.4f, 8, BookStitch.SIDE)
        val top = book.profile(0, 0f, 0f, 0f); val below = book.profile(1, 0f, 0f, 0f)
        assertTrue(top.last() > below.last())
        assertEquals(book.thickness - book.leafThickness, top.last(), 1e-3f)
    }

    @Test fun theCameraSeesTheFrontFromTheFrontAndTheBackFromBehind() {
        assertTrue(BookCamera(0f, -20f).faces(0f, 0f, 0f, 0f, 0f, 1f))
        assertFalse(BookCamera(180f, -20f).faces(0f, 0f, 0f, 0f, 0f, 1f))
        assertTrue(BookCamera(180f, -20f).faces(0f, 0f, 0f, 0f, 0f, -1f))
    }
}
