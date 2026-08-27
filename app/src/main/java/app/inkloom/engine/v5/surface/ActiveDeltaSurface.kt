package app.inkloom.engine.v5.surface

/**
 * One gesture-local sparse delta over an immutable committed backdrop. Preview snapshots expose
 * the private working generations; only [publish] changes document truth.
 */
class ActiveDeltaSurface(
    private val document: PixelTileSurface,
    label: String,
) : AutoCloseable {
    private val transaction = document.beginWrite(label)
    private var state = State.ACTIVE

    val transactionId: Long get() = transaction.id
    val logicalWidth: Int get() = document.logicalWidth
    val logicalHeight: Int get() = document.logicalHeight
    val tileSize: Int get() = document.tileSize
    val backdropGeneration: Long = document.revision
    val touchedTileCount: Int get() = transaction.touchedTileCount
    val workingByteSize: Long get() = transaction.workingByteSize

    fun editTile(key: PixelTileKey, dirtyLocalRect: PixelRect, block: (IntArray, Int) -> Unit) {
        requireActive()
        transaction.editTile(key, dirtyLocalRect, block)
    }

    fun previewSnapshot(): PixelTileSurfaceSnapshot {
        requireActive()
        return transaction.previewSnapshot()
    }

    fun publish(): PixelTileTransactionRecord {
        requireActive()
        state = State.PUBLISHED
        return transaction.commit()
    }

    fun rollback() {
        requireActive()
        state = State.ROLLED_BACK
        transaction.cancel()
    }

    override fun close() {
        if (state == State.ACTIVE) rollback()
    }

    private fun requireActive() = check(state == State.ACTIVE) { "Active delta is $state" }

    private enum class State { ACTIVE, PUBLISHED, ROLLED_BACK }
}

