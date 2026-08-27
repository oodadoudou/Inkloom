package app.inkloom.engine.v5.tools

import app.inkloom.document.LiquifyMode
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SparseDisplacementFieldTest {
    @Test fun missingTilesAreIdentityAndOneLocalStrokeStaysSparseOn8kPage() {
        val field = SparseDisplacementField(8192, 8192)
        assertEquals(0f, field.displacementAt(4000f, 4000f).first, 0f)
        assertNotNull(field.appendSegment(120f, 120f, 150f, 125f, 48f, .8f, .5f, LiquifyMode.PUSH))
        assertTrue(field.tileCount in 1..4)
        assertTrue(field.retainedByteSize <= 4L * 64 * 64 * 2 * Float.SIZE_BYTES)
        assertEquals(0f, field.displacementAt(4000f, 4000f).second, 0f)
    }

    @Test fun appendedInputProcessesOnlyTheNewSegmentAndAccumulatesDisplacement() {
        val field = SparseDisplacementField(1024, 1024)
        field.appendSegment(100f, 200f, 140f, 200f, 40f, 1f, .2f, LiquifyMode.PUSH)
        val afterFirst = field.displacementAt(140f, 200f).first
        val firstStampCount = field.processedStampCount
        field.appendSegment(140f, 200f, 144f, 200f, 40f, 1f, .2f, LiquifyMode.PUSH)
        val afterSecond = field.displacementAt(142f, 200f).first
        assertTrue(afterFirst > 0f)
        assertTrue(afterSecond > afterFirst)
        assertEquals("Only the newly appended short segment emits one stamp", firstStampCount + 1, field.processedStampCount)
    }

    @Test fun reconstructReducesTheStoredWarpRatherThanResamplingPixels() {
        val field = SparseDisplacementField(512, 512)
        field.appendSegment(180f, 256f, 240f, 256f, 80f, 1f, .3f, LiquifyMode.PUSH)
        val before = field.displacementAt(230f, 256f)
        repeat(6) {
            field.appendSegment(230f, 256f, 230f, 256f, 80f, .45f, .3f, LiquifyMode.RECONSTRUCT)
        }
        val after = field.displacementAt(230f, 256f)
        assertTrue(hypot(after.first, after.second) < hypot(before.first, before.second))
    }

    @Test fun stationaryProfessionalModesProduceFiniteLocalFields() {
        val modes = listOf(
            LiquifyMode.TWIRL_CLOCKWISE,
            LiquifyMode.TWIRL_COUNTERCLOCKWISE,
            LiquifyMode.PINCH,
            LiquifyMode.EXPAND,
            LiquifyMode.CRYSTALS,
        )
        modes.forEach { mode ->
            val field = SparseDisplacementField(512, 512)
            assertNotNull(field.appendSegment(256f, 256f, 256f, 256f, 96f, .8f, .45f, mode))
            val value = field.displacementAt(300f, 256f)
            assertTrue("$mode x must be finite", value.first.isFinite())
            assertTrue("$mode y must be finite", value.second.isFinite())
            assertTrue("$mode must remain local", field.tileCount <= 4)
        }
    }
}
