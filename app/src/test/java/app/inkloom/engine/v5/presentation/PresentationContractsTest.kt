package app.inkloom.engine.v5.presentation

import app.inkloom.engine.v5.coordinates.DocumentRect
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationContractsTest {
    @Test fun `scheduler merges only requested dirty bounds`() {
        val scheduler = FrameInvalidationScheduler()
        scheduler.request(InvalidationRequest(InvalidationReason.ACTIVE_DELTA, DocumentRect(10f, 20f, 30f, 40f), 4L, 9L))
        scheduler.request(InvalidationRequest(InvalidationReason.ACTIVE_DELTA, DocumentRect(25f, 5f, 50f, 35f), 4L, 9L))
        val frame = scheduler.takeForFrame()!!
        assertEquals(DocumentRect(10f, 5f, 50f, 40f), frame.dirtyBounds)
        assertEquals(9L, frame.transactionId)
        assertNull(scheduler.takeForFrame())
    }

    @Test fun `active transaction releases only when its committed generation is presented`() {
        val barrier = PresentationGenerationBarrier()
        barrier.awaitVisible(11L, 7L)
        assertTrue(barrier.isAwaiting(7L))
        assertArrayEquals(longArrayOf(), barrier.onPresented(10L, longArrayOf(7L)))
        assertArrayEquals(longArrayOf(), barrier.onPresented(11L, longArrayOf(8L)))
        assertArrayEquals(longArrayOf(7L), barrier.onPresented(11L, longArrayOf(7L)))
    }
}

