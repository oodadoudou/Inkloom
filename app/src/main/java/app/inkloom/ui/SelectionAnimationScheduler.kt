package app.inkloom.ui

/** One pending selection animation frame, even when input causes many intervening redraws. */
internal class SelectionAnimationScheduler(
    private val post: (Runnable) -> Unit,
    private val remove: (Runnable) -> Unit,
    private val invalidate: () -> Unit,
) {
    private var pending = false
    private val frame = Runnable {
        pending = false
        invalidate()
    }

    fun requestFrame() {
        if (pending) return
        pending = true
        post(frame)
    }

    fun cancel() {
        remove(frame)
        pending = false
    }
}
