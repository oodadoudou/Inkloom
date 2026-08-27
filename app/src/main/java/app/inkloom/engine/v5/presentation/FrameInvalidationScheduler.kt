package app.inkloom.engine.v5.presentation

import app.inkloom.engine.v5.coordinates.DocumentRect

enum class InvalidationReason { ACTIVE_DELTA, DOCUMENT_COMMIT, SELECTION, VIEWPORT, CURSOR, UI_OVERLAY }

data class InvalidationRequest(
    val reason: InvalidationReason,
    val dirtyBounds: DocumentRect,
    val documentGeneration: Long,
    val transactionId: Long? = null,
) {
    init {
        require(documentGeneration >= 0L)
        require(transactionId == null || transactionId > 0L)
    }
}

/** One-frame coalescer. It never silently turns an empty/unknown region into full-canvas damage. */
class FrameInvalidationScheduler {
    private var pending: InvalidationRequest? = null

    @Synchronized
    fun request(value: InvalidationRequest) {
        if (value.dirtyBounds.isEmpty) return
        val previous = pending
        pending = if (previous == null) value else InvalidationRequest(
            reason = if (previous.reason == value.reason) value.reason else InvalidationReason.UI_OVERLAY,
            dirtyBounds = previous.dirtyBounds.union(value.dirtyBounds),
            documentGeneration = maxOf(previous.documentGeneration, value.documentGeneration),
            transactionId = value.transactionId.takeIf { it == previous.transactionId },
        )
    }

    @Synchronized
    fun takeForFrame(): InvalidationRequest? = pending.also { pending = null }
}

/** Tracks the exact document generation and transaction that replaced an active delta. */
class PresentationGenerationBarrier {
    private val awaiting = linkedMapOf<Long, Long>()

    @Synchronized
    fun awaitVisible(documentGeneration: Long, transactionId: Long) {
        require(documentGeneration > 0L && transactionId > 0L)
        awaiting[transactionId] = documentGeneration
    }

    @Synchronized
    fun onPresented(documentGeneration: Long, transactionIds: LongArray): LongArray {
        require(documentGeneration >= 0L)
        val presented = transactionIds.toSet()
        val releasable = awaiting.filter { (transaction, requiredGeneration) ->
            transaction in presented && documentGeneration >= requiredGeneration
        }.keys.toLongArray()
        releasable.forEach(awaiting::remove)
        return releasable
    }

    @Synchronized fun isAwaiting(transactionId: Long): Boolean = transactionId in awaiting
}

