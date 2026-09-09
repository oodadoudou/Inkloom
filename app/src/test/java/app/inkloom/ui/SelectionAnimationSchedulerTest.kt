package app.inkloom.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionAnimationSchedulerTest {
    @Test fun inputRedrawsCannotMultiplyRecurringAnimationCallbacks() {
        val queue = mutableListOf<Runnable>()
        var frames = 0
        val scheduler = SelectionAnimationScheduler({ queue += it }, { queue.remove(it) }, { frames++ })
        repeat(500) { scheduler.requestFrame() }
        assertEquals(1, queue.size)
        repeat(40) {
            queue.removeAt(0).run()
            repeat(100) { scheduler.requestFrame() }
            assertEquals(1, queue.size)
        }
        assertEquals(40, frames)
        scheduler.cancel()
        assertEquals(0, queue.size)
        scheduler.requestFrame()
        assertEquals(1, queue.size)
    }
}
