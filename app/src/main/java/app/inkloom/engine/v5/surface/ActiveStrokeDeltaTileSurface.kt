package app.inkloom.engine.v5.surface

/** How the immutable active contribution is applied to its pinned committed layer. */
enum class ActiveStrokeDeltaBlend { SOURCE_OVER, DESTINATION_OUT, SOURCE_ATOP }

/**
 * Gesture-local sparse pixels that never contain or mutate the committed backdrop.
 *
 * The preview and publication consume the same immutable delta generations. Publication is the
 * only point at which the contribution is merged into document truth.
 */
class ActiveStrokeDeltaTileSurface(
    private val document: PixelTileSurface,
    private val label: String,
    val blend: ActiveStrokeDeltaBlend,
) : AutoCloseable {
    init {
        require(document.pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) {
            "Realtime active deltas currently require premultiplied RGBA8"
        }
    }

    val backdropSnapshot: PixelTileSurfaceSnapshot = document.snapshot()
    private val deltaSurface = PixelTileSurface(
        logicalWidth = document.logicalWidth,
        logicalHeight = document.logicalHeight,
        tileSize = document.tileSize,
        surfaceId = "${document.surfaceId}:active:${nextSurfaceId()}",
        pixelFormat = document.pixelFormat,
    )
    private val transaction = deltaSurface.beginWrite(label)
    private var state = State.ACTIVE

    val transactionId: Long get() = transaction.id
    val logicalWidth: Int get() = document.logicalWidth
    val logicalHeight: Int get() = document.logicalHeight
    val tileSize: Int get() = document.tileSize
    val backdropGeneration: Long get() = backdropSnapshot.revision
    val touchedTileCount: Int get() = transaction.touchedTileCount
    val workingByteSize: Long get() = transaction.workingByteSize

    fun editTile(key: PixelTileKey, dirtyLocalRect: PixelRect, block: (IntArray, Int) -> Unit) {
        requireActive()
        transaction.editTile(key, dirtyLocalRect, block)
    }

    /** Samples the exact visible layer result without materializing a preview snapshot. */
    fun compositedPixelAt(x: Int, y: Int): Int {
        requireActive()
        val contribution = transaction.pixelAt(x, y)
        return when (blend) {
            ActiveStrokeDeltaBlend.SOURCE_OVER -> sourceOver(backdropSnapshot.pixelAt(x, y), contribution)
            ActiveStrokeDeltaBlend.DESTINATION_OUT -> destinationOut(backdropSnapshot.pixelAt(x, y), contribution ushr 24)
            ActiveStrokeDeltaBlend.SOURCE_ATOP -> sourceAtop(backdropSnapshot.pixelAt(x, y), contribution)
        }
    }

    /** Transparent source/coverage contribution, never a copy of committed pixels. */
    fun previewSnapshot(): PixelTileSurfaceSnapshot {
        requireActive()
        return transaction.previewSnapshot()
    }

    fun publish(): PixelTileTransactionRecord {
        requireActive()
        check(document.revision == backdropSnapshot.revision) {
            "Committed layer changed during active stroke"
        }
        val frozen = transaction.freezeRgba8WorkingTiles()
        return try {
            document.publishFrozenRgba8Tiles(
                label = label,
                tiles = frozen,
                adoptOnEmpty = blend == ActiveStrokeDeltaBlend.SOURCE_OVER,
                blendMode = blend.ordinal,
            ).also {
                state = State.PUBLISHED
            }
        } catch (failure: Throwable) {
            state = State.ROLLED_BACK
            throw failure
        }
    }

    fun rollback() {
        requireActive()
        state = State.ROLLED_BACK
        transaction.cancel()
    }

    override fun close() {
        if (state == State.ACTIVE) rollback()
    }

    private fun requireActive() = check(state == State.ACTIVE) { "Active stroke delta is $state" }

    private enum class State { ACTIVE, PUBLISHED, ROLLED_BACK }

    private companion object {
        private var surfaceSequence = 0L

        @Synchronized fun nextSurfaceId(): Long = ++surfaceSequence

        fun sourceOver(destination: Int, source: Int): Int {
            val sourceAlpha = (source ushr 24) and 0xff
            if (sourceAlpha == 0) return destination
            if (sourceAlpha == 255) return source
            val inverse = 255 - sourceAlpha
            val destinationAlpha = (destination ushr 24) and 0xff
            return ((sourceAlpha + destinationAlpha * inverse / 255).coerceAtMost(255) shl 24) or
                ((((source ushr 16) and 0xff) + ((destination ushr 16) and 0xff) * inverse / 255).coerceAtMost(255) shl 16) or
                ((((source ushr 8) and 0xff) + ((destination ushr 8) and 0xff) * inverse / 255).coerceAtMost(255) shl 8) or
                (((source and 0xff) + (destination and 0xff) * inverse / 255).coerceAtMost(255))
        }

        fun destinationOut(destination: Int, coverageAlpha: Int): Int {
            val keep = 255 - coverageAlpha.coerceIn(0, 255)
            return ((((destination ushr 24) and 0xff) * keep / 255) shl 24) or
                ((((destination ushr 16) and 0xff) * keep / 255) shl 16) or
                ((((destination ushr 8) and 0xff) * keep / 255) shl 8) or
                ((destination and 0xff) * keep / 255)
        }

        fun sourceAtop(destination: Int, source: Int): Int {
            val destinationAlpha = destination ushr 24 and 0xff
            if (destinationAlpha == 0) return destination
            val sourceAlpha = source ushr 24 and 0xff
            if (sourceAlpha == 0) return destination
            val inverse = 255 - sourceAlpha
            return (destinationAlpha shl 24) or
                (((((source ushr 16) and 0xff) * destinationAlpha + ((destination ushr 16) and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha) shl 16) or
                (((((source ushr 8) and 0xff) * destinationAlpha + ((destination ushr 8) and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha) shl 8) or
                ((((source and 0xff) * destinationAlpha + (destination and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha))
        }

        private fun compositeReference(
            destination: IntArray,
            source: IntArray,
            stride: Int,
            dirtyRect: PixelRect,
            blend: ActiveStrokeDeltaBlend,
        ) {
            for (y in dirtyRect.top until dirtyRect.bottom) {
                val row = y * stride
                for (x in dirtyRect.left until dirtyRect.right) {
                    val index = row + x
                    destination[index] = when (blend) {
                        ActiveStrokeDeltaBlend.SOURCE_OVER -> sourceOver(destination[index], source[index])
                        ActiveStrokeDeltaBlend.DESTINATION_OUT -> destinationOut(destination[index], source[index] ushr 24)
                        ActiveStrokeDeltaBlend.SOURCE_ATOP -> sourceAtop(destination[index], source[index])
                    }
                }
            }
        }
    }
}
