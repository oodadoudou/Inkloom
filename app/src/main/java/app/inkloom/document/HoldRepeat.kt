package app.inkloom.document

/**
 * Procreate's rapid undo and redo: keep two (or three) fingers down and the step repeats, slowly at
 * first and then faster, until the fingers lift.
 */
object HoldRepeat {
    const val START_DELAY_MS = 500L
    private const val FIRST_INTERVAL_MS = 250L
    private const val FASTEST_INTERVAL_MS = 70L

    /** Delay before the repeat after the [repeated]th one. */
    fun intervalMs(repeated: Int): Long =
        (FIRST_INTERVAL_MS * Math.pow(.8, repeated.coerceAtLeast(0).toDouble())).toLong().coerceAtLeast(FASTEST_INTERVAL_MS)
}
