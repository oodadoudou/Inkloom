package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HandwritingIndexTest {
    @Test fun nearbyStrokesAreGroupedIntoStableWritingLines() {
        val first = stroke("a", .1f, .20f, .18f, .22f, 10)
        val second = stroke("b", .21f, .19f, .29f, .23f, 20)
        val nextLine = stroke("c", .1f, .42f, .20f, .46f, 30)

        val groups = HandwritingIndex.groups(listOf(nextLine, second, first))

        assertEquals(2, groups.size)
        assertEquals(listOf("a", "b"), groups.first().strokeIds)
        assertEquals(listOf("c"), groups.last().strokeIds)
    }

    @Test fun fingerprintChangesWhenStrokeGeometryChanges() {
        val original = stroke("a", .1f, .2f, .3f, .2f, 10)
        val changed = stroke("a", .1f, .2f, .4f, .2f, 10)
        assertNotEquals(HandwritingIndex.fingerprint(listOf(original)), HandwritingIndex.fingerprint(listOf(changed)))
    }

    private fun stroke(id: String, x1: Float, y1: Float, x2: Float, y2: Float, time: Long) = InkStroke(
        id = id,
        points = listOf(StrokePoint(x1, y1, timeMs = time), StrokePoint(x2, y2, timeMs = time + 1)),
    )
}
