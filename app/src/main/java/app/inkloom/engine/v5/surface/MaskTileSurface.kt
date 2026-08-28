package app.inkloom.engine.v5.surface

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

enum class MaskComposeMode { REPLACE, ADD, SUBTRACT, INTERSECT }

data class MaskTileInfo(
    val key: PixelTileKey,
    val generation: Long,
    val nonZeroAlphaCount: Int,
)

data class MaskTileDeltaInfo(
    val key: PixelTileKey,
    val dirtyLocalRect: PixelRect,
    val beforeGeneration: Long?,
    val afterGeneration: Long?,
)

internal data class MaskTileGeneration(
    val id: Long,
    val alpha: ByteArray,
    val nonZeroAlphaCount: Int,
)

internal data class MaskTileDelta(
    val key: PixelTileKey,
    val dirtyLocalRect: PixelRect,
    val before: MaskTileGeneration?,
    val after: MaskTileGeneration?,
    val tileCapacity: Int,
)

/** Immutable document-coordinate alpha. Missing tiles resolve to [defaultAlpha]. */
class MaskTileSurfaceSnapshot internal constructor(
    val surfaceId: String,
    val logicalWidth: Int,
    val logicalHeight: Int,
    val tileSize: Int,
    val revision: Long,
    val defaultAlpha: Int,
    private val tiles: Map<PixelTileKey, MaskTileGeneration>,
) {
    val isEmpty: Boolean get() = defaultAlpha == 0 && tiles.isEmpty()
    val tileCount: Int get() = tiles.size
    val retainedByteSize: Long get() = tiles.size.toLong() * tileSize * tileSize
    val tileInfo: List<MaskTileInfo> get() = tiles.map { (key, tile) ->
        MaskTileInfo(key, tile.id, tile.nonZeroAlphaCount)
    }

    fun alphaAt(x: Int, y: Int): Int {
        if (x !in 0 until logicalWidth || y !in 0 until logicalHeight) return 0
        val key = PixelTileKey(x / tileSize, y / tileSize)
        return tiles[key]?.alpha?.get((y % tileSize) * tileSize + x % tileSize)?.toInt()?.and(0xff) ?: defaultAlpha
    }

    fun copyTileAlpha(key: PixelTileKey): ByteArray? = tiles[key]?.alpha?.copyOf()

    /**
     * Exact non-zero bounds without materializing the logical page. An implicit non-zero default
     * necessarily covers the page edge even when explicit tiles punch holes into the interior.
     */
    fun nonZeroBounds(): PixelRect? {
        if (defaultAlpha > 0) return PixelRect(0, 0, logicalWidth, logicalHeight)
        var left = logicalWidth
        var top = logicalHeight
        var right = 0
        var bottom = 0
        var found = false
        tiles.forEach { (key, generation) ->
            if (generation.nonZeroAlphaCount == 0) return@forEach
            val tileLeft = key.column * tileSize
            val tileTop = key.row * tileSize
            val tileRight = minOf(tileLeft + tileSize, logicalWidth)
            val tileBottom = minOf(tileTop + tileSize, logicalHeight)
            for (y in tileTop until tileBottom) {
                val rowOffset = (y - tileTop) * tileSize
                for (x in tileLeft until tileRight) {
                    if (generation.alpha[rowOffset + x - tileLeft].toInt().and(0xff) == 0) continue
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x + 1)
                    bottom = maxOf(bottom, y + 1)
                    found = true
                }
            }
        }
        return if (found) PixelRect(left, top, right, bottom) else null
    }

    /** Zero-copy internal read. The alpha array must not be retained or modified. */
    internal fun <T> readTile(key: PixelTileKey, block: (ByteArray) -> T): T? =
        tiles[key]?.alpha?.let(block)

    fun copyAlpha(source: PixelRect): ByteArray {
        requireInside(source, logicalWidth, logicalHeight)
        val output = ByteArray(Math.multiplyExact(source.width, source.height)) { defaultAlpha.toByte() }
        copyAlpha(source, output, destinationOffset = 0, destinationRowStride = source.width)
        return output
    }

    /** Copies a bounded ROI into caller-owned scratch storage to keep frame loops allocation-free. */
    fun copyAlpha(
        source: PixelRect,
        output: ByteArray,
        destinationOffset: Int,
        destinationRowStride: Int,
    ) {
        requireInside(source, logicalWidth, logicalHeight)
        require(destinationOffset >= 0)
        require(destinationRowStride >= source.width)
        val required = if (source.isEmpty) destinationOffset else
            destinationOffset + (source.height - 1) * destinationRowStride + source.width
        require(required <= output.size) { "Destination alpha buffer is too small" }
        for (y in 0 until source.height) {
            output.fill(
                defaultAlpha.toByte(),
                destinationOffset + y * destinationRowStride,
                destinationOffset + y * destinationRowStride + source.width,
            )
        }
        if (source.isEmpty) return
        val firstColumn = source.left / tileSize
        val lastColumn = (source.right - 1) / tileSize
        val firstRow = source.top / tileSize
        val lastRow = (source.bottom - 1) / tileSize
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val alpha = tiles[PixelTileKey(column, row)]?.alpha ?: continue
            val tileLeft = column * tileSize
            val tileTop = row * tileSize
            val overlap = logicalTileRect(column, row, tileSize, logicalWidth, logicalHeight).intersect(source)
            for (y in overlap.top until overlap.bottom) {
                val sourceOffset = (y - tileTop) * tileSize + overlap.left - tileLeft
                val rowOffset = destinationOffset + (y - source.top) * destinationRowStride + overlap.left - source.left
                alpha.copyInto(output, rowOffset, sourceOffset, sourceOffset + overlap.width)
            }
        }
    }
}

/**
 * Authoritative sparse one-byte alpha surface for document selections and masks.
 *
 * Published generations are immutable. One transaction clones only the tiles it writes and
 * publishes all changed generations atomically. An 8K empty selection therefore allocates no
 * page-sized pixel plane, and dirty-ROI consumers can sample a bounded rectangle directly.
 */
class MaskTileSurface(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val tileSize: Int = DEFAULT_TILE_SIZE,
    val surfaceId: String = UUID.randomUUID().toString(),
    initialDefaultAlpha: Int = 0,
) {
    init {
        require(logicalWidth > 0) { "logicalWidth must be positive" }
        require(logicalHeight > 0) { "logicalHeight must be positive" }
        require(tileSize in SUPPORTED_TILE_SIZES) { "Unsupported mask tile size: $tileSize" }
        require(initialDefaultAlpha in 0..255) { "initialDefaultAlpha must be 0..255" }
    }

    private val monitor = Any()
    private val generations = linkedMapOf<PixelTileKey, MaskTileGeneration>()
    private var activeTransactionId: Long? = null
    private var revisionValue = 0L
    private var defaultAlphaValue = initialDefaultAlpha

    val revision: Long get() = synchronized(monitor) { revisionValue }
    val tileCount: Int get() = synchronized(monitor) { generations.size }
    val retainedByteSize: Long get() = synchronized(monitor) { generations.size.toLong() * tileSize * tileSize }
    val defaultAlpha: Int get() = synchronized(monitor) { defaultAlphaValue }
    val isEmpty: Boolean get() = synchronized(monitor) { defaultAlphaValue == 0 && generations.isEmpty() }

    fun alphaAt(x: Int, y: Int): Int = synchronized(monitor) {
        if (x !in 0 until logicalWidth || y !in 0 until logicalHeight) return@synchronized 0
        generations[keyFor(x, y)]?.alpha?.get(localIndex(x, y))?.toInt()?.and(0xff) ?: defaultAlphaValue
    }

    fun snapshot(): MaskTileSurfaceSnapshot = synchronized(monitor) {
        MaskTileSurfaceSnapshot(
            surfaceId,
            logicalWidth,
            logicalHeight,
            tileSize,
            revisionValue,
            defaultAlphaValue,
            generations.toMap(),
        )
    }

    fun beginWrite(label: String): MaskTileWriteTransaction {
        require(label.isNotBlank()) { "A mask transaction needs a diagnostic label" }
        return synchronized(monitor) {
            check(activeTransactionId == null) { "Only one mask write transaction may be active" }
            val id = transactionIds.incrementAndGet()
            activeTransactionId = id
            MaskTileWriteTransaction(this, id, label)
        }
    }

    fun apply(record: MaskTileTransactionRecord, direction: HistoryDirection) {
        record.requireOpen()
        require(record.surfaceId == surfaceId) { "Transaction belongs to another mask surface" }
        synchronized(monitor) {
            check(activeTransactionId == null) { "Cannot apply mask history while a write is active" }
            val deltas = record.deltas()
            val expectedDefault = if (direction == HistoryDirection.UNDO) record.afterDefaultAlpha else record.beforeDefaultAlpha
            check(defaultAlphaValue == expectedDefault) { "Mask default alpha changed after this transaction" }
            deltas.forEach { delta ->
                val expected = if (direction == HistoryDirection.UNDO) delta.after else delta.before
                check(generations[delta.key]?.id == expected?.id) { "Mask tile ${delta.key} changed after this transaction" }
            }
            deltas.forEach { delta ->
                val generation = if (direction == HistoryDirection.UNDO) delta.before else delta.after
                if (generation == null) generations.remove(delta.key) else generations[delta.key] = generation
            }
            defaultAlphaValue = if (direction == HistoryDirection.UNDO) record.beforeDefaultAlpha else record.afterDefaultAlpha
            if (deltas.isNotEmpty() || record.beforeDefaultAlpha != record.afterDefaultAlpha) revisionValue++
        }
    }

    internal fun publishedTile(key: PixelTileKey): MaskTileGeneration? = synchronized(monitor) { generations[key] }
    internal fun publishedKeys(): Set<PixelTileKey> = synchronized(monitor) { generations.keys.toSet() }
    internal fun publishedDefaultAlpha(): Int = synchronized(monitor) { defaultAlphaValue }

    internal fun preview(transaction: MaskTileWriteTransaction): MaskTileSurfaceSnapshot = synchronized(monitor) {
        requireActive(transaction)
        val previewTiles = generations.toMutableMap()
        transaction.workingTiles.forEach { (key, working) ->
            val equalsDefault = working.alpha.all { it.toInt().and(0xff) == transaction.workingDefaultAlpha }
            if (equalsDefault) previewTiles.remove(key) else previewTiles[key] = MaskTileGeneration(
                id = working.before?.id ?: 0L,
                alpha = working.alpha.copyOf(),
                nonZeroAlphaCount = working.alpha.count { it.toInt() != 0 },
            )
        }
        MaskTileSurfaceSnapshot(
            surfaceId,
            logicalWidth,
            logicalHeight,
            tileSize,
            revisionValue,
            transaction.workingDefaultAlpha,
            previewTiles,
        )
    }

    internal fun commit(transaction: MaskTileWriteTransaction): MaskTileTransactionRecord = synchronized(monitor) {
        requireActive(transaction)
        val afterDefaultAlpha = transaction.workingDefaultAlpha
        val deltas = transaction.workingTiles.mapNotNull { (key, working) ->
            val before = working.before
            val equalsBefore = before != null && before.alpha.contentEquals(working.alpha)
            val equalsAfterDefault = working.alpha.all { it.toInt().and(0xff) == afterDefaultAlpha }
            if (equalsBefore && !equalsAfterDefault) return@mapNotNull null
            val nonZero = working.alpha.count { it.toInt() != 0 }
            if (before == null && equalsAfterDefault) return@mapNotNull null
            val after = if (equalsAfterDefault) null else MaskTileGeneration(
                generationIds.incrementAndGet(),
                working.alpha,
                nonZero,
            )
            MaskTileDelta(key, working.dirtyRect ?: fullTileRect(), before, after, tileSize * tileSize)
        }
        deltas.forEach { delta ->
            if (delta.after == null) generations.remove(delta.key) else generations[delta.key] = delta.after
        }
        val beforeDefaultAlpha = defaultAlphaValue
        defaultAlphaValue = afterDefaultAlpha
        activeTransactionId = null
        transaction.markCommitted()
        if (deltas.isNotEmpty() || beforeDefaultAlpha != afterDefaultAlpha) revisionValue++
        MaskTileTransactionRecord(
            surfaceId,
            transaction.id,
            transaction.label,
            beforeDefaultAlpha,
            afterDefaultAlpha,
            deltas,
        )
    }

    internal fun cancel(transaction: MaskTileWriteTransaction) = synchronized(monitor) {
        requireActive(transaction)
        activeTransactionId = null
        transaction.markCancelled()
    }

    internal fun requireCoordinate(x: Int, y: Int) {
        require(x in 0 until logicalWidth && y in 0 until logicalHeight) {
            "Mask pixel ($x,$y) is outside ${logicalWidth}x$logicalHeight"
        }
    }

    internal fun keyFor(x: Int, y: Int) = PixelTileKey(x / tileSize, y / tileSize)
    internal fun localIndex(x: Int, y: Int) = (y % tileSize) * tileSize + x % tileSize
    internal fun fullTileRect() = PixelRect(0, 0, tileSize, tileSize)

    private fun requireActive(transaction: MaskTileWriteTransaction) {
        check(activeTransactionId == transaction.id) { "Mask write transaction is no longer active" }
    }

    enum class HistoryDirection { UNDO, REDO }

    companion object {
        const val DEFAULT_TILE_SIZE = 128
        val SUPPORTED_TILE_SIZES: Set<Int> = setOf(64, 128, 256)
        private val transactionIds = AtomicLong(0L)
        private val generationIds = AtomicLong(0L)
    }
}

class MaskTileTransactionRecord internal constructor(
    val surfaceId: String,
    val transactionId: Long,
    val label: String,
    val beforeDefaultAlpha: Int,
    val afterDefaultAlpha: Int,
    deltas: List<MaskTileDelta>,
) : AutoCloseable {
    private var retainedDeltas = deltas
    val changedTileCount: Int = deltas.size
    val retainedByteSize: Long = deltas.sumOf { delta ->
        delta.tileCapacity.toLong() * ((if (delta.before == null) 0 else 1) + (if (delta.after == null) 0 else 1))
    }
    val deltaInfo: List<MaskTileDeltaInfo> = deltas.map { delta ->
        MaskTileDeltaInfo(delta.key, delta.dirtyLocalRect, delta.before?.id, delta.after?.id)
    }
    private var closed = false

    override fun close() = synchronized(this) {
        closed = true
        retainedDeltas = emptyList()
    }

    internal fun requireOpen() = check(!closed) { "Mask transaction record has been released" }
    internal fun deltas(): List<MaskTileDelta> = synchronized(this) { requireOpen(); retainedDeltas }
}

internal data class WorkingMaskTile(
    val before: MaskTileGeneration?,
    val alpha: ByteArray,
    var dirtyRect: PixelRect? = null,
)

class MaskTileWriteTransaction internal constructor(
    private val surface: MaskTileSurface,
    internal val id: Long,
    internal val label: String,
) : AutoCloseable {
    internal val workingTiles = linkedMapOf<PixelTileKey, WorkingMaskTile>()
    internal var workingDefaultAlpha: Int = surface.publishedDefaultAlpha()
        private set
    private var state = State.ACTIVE

    val touchedTileCount: Int get() = workingTiles.size
    val workingByteSize: Long get() = workingTiles.size.toLong() * surface.tileSize * surface.tileSize

    /** Immutable transaction preview for renderers; document truth remains unpublished. */
    fun previewSnapshot(): MaskTileSurfaceSnapshot { requireActive(); return surface.preview(this) }

    fun alphaAt(x: Int, y: Int): Int {
        surface.requireCoordinate(x, y)
        requireActive()
        return (workingTiles[surface.keyFor(x, y)]?.alpha?.get(surface.localIndex(x, y))
            ?: surface.publishedTile(surface.keyFor(x, y))?.alpha?.get(surface.localIndex(x, y))
            ?: workingDefaultAlpha.toByte()).toInt().and(0xff)
    }

    fun setAlpha(x: Int, y: Int, alpha: Int) {
        surface.requireCoordinate(x, y)
        require(alpha in 0..255) { "alpha must be 0..255" }
        requireActive()
        val tile = writableTile(surface.keyFor(x, y))
        tile.alpha[surface.localIndex(x, y)] = alpha.toByte()
        markDirty(tile, PixelRect(x % surface.tileSize, y % surface.tileSize, x % surface.tileSize + 1, y % surface.tileSize + 1))
    }

    fun clear() = fill(0)

    /** Replaces the entire logical mask in O(number of explicit tiles), not O(canvas pixels). */
    fun fill(alpha: Int) {
        require(alpha in 0..255) { "alpha must be 0..255" }
        requireActive()
        (surface.publishedKeys() + workingTiles.keys).forEach { key ->
            val tile = writableTile(key)
            tile.alpha.fill(alpha.toByte())
            markDirty(tile, surface.fullTileRect())
        }
        workingDefaultAlpha = alpha
    }

    /** Inverts the implicit default and only the explicit deviation tiles. */
    fun invert() {
        requireActive()
        (surface.publishedKeys() + workingTiles.keys).forEach { key ->
            val tile = writableTile(key)
            for (index in tile.alpha.indices) {
                tile.alpha[index] = (255 - tile.alpha[index].toInt().and(0xff)).toByte()
            }
            markDirty(tile, surface.fullTileRect())
        }
        workingDefaultAlpha = 255 - workingDefaultAlpha
    }

    fun replaceAlpha(destination: PixelRect, alpha: ByteArray, rowStride: Int = destination.width) {
        composeAlpha(destination, alpha, rowStride, MaskComposeMode.REPLACE)
    }

    /** Composes a bounded row-major alpha buffer; no page-sized intermediate is created. */
    fun composeAlpha(
        destination: PixelRect,
        alpha: ByteArray,
        rowStride: Int = destination.width,
        mode: MaskComposeMode,
    ) {
        requireActive()
        requireInside(destination, surface.logicalWidth, surface.logicalHeight)
        if (destination.isEmpty) return
        require(rowStride >= destination.width) { "rowStride is smaller than destination width" }
        require(alpha.size >= (destination.height - 1) * rowStride + destination.width) { "Alpha buffer is too small" }
        val firstColumn = destination.left / surface.tileSize
        val lastColumn = (destination.right - 1) / surface.tileSize
        val firstRow = destination.top / surface.tileSize
        val lastRow = (destination.bottom - 1) / surface.tileSize
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val key = PixelTileKey(column, row)
            val tileLeft = column * surface.tileSize
            val tileTop = row * surface.tileSize
            val overlap = logicalTileRect(column, row, surface.tileSize, surface.logicalWidth, surface.logicalHeight).intersect(destination)
            val dirty = PixelRect(overlap.left - tileLeft, overlap.top - tileTop, overlap.right - tileLeft, overlap.bottom - tileTop)
            editTile(key, dirty) { target, targetStride ->
                for (y in overlap.top until overlap.bottom) {
                    var sourceIndex = (y - destination.top) * rowStride + overlap.left - destination.left
                    var targetIndex = (y - tileTop) * targetStride + overlap.left - tileLeft
                    repeat(overlap.width) {
                        val sourceAlpha = alpha[sourceIndex++].toInt().and(0xff)
                        val destinationAlpha = target[targetIndex].toInt().and(0xff)
                        target[targetIndex++] = compose(destinationAlpha, sourceAlpha, mode).toByte()
                    }
                }
            }
        }
    }

    /** Composes another sparse mask without expanding either logical surface into a page buffer. */
    fun composeSurface(source: MaskTileSurfaceSnapshot, mode: MaskComposeMode) {
        requireActive()
        require(source.logicalWidth == surface.logicalWidth && source.logicalHeight == surface.logicalHeight) {
            "Mask surface geometry must match"
        }
        val destinationDefault = workingDefaultAlpha
        val resultDefault = compose(destinationDefault, source.defaultAlpha, mode)
        if (source.tileSize != surface.tileSize) {
            composeResampledSurface(source, mode, destinationDefault, resultDefault)
            return
        }
        val keys = linkedSetOf<PixelTileKey>().apply {
            addAll(surface.publishedKeys())
            addAll(workingTiles.keys)
            addAll(source.tileInfo.map(MaskTileInfo::key))
        }
        keys.forEach { key ->
            val sourceAlpha = source.copyTileAlpha(key)
            val targetBefore = workingTiles[key]?.alpha?.copyOf()
                ?: surface.publishedTile(key)?.alpha?.copyOf()
                ?: ByteArray(surface.tileSize * surface.tileSize) { destinationDefault.toByte() }
            val tile = writableTile(key)
            for (index in tile.alpha.indices) {
                tile.alpha[index] = compose(
                    targetBefore[index].toInt().and(0xff),
                    sourceAlpha?.get(index)?.toInt()?.and(0xff) ?: source.defaultAlpha,
                    mode,
                ).toByte()
            }
            markDirty(tile, surface.fullTileRect())
        }
        workingDefaultAlpha = resultDefault
    }

    /** Tile size is storage geometry, not document geometry. Selection producers are free to use
     * a different sparse granularity from the document-owned mask, so compose through document
     * coordinates whenever those granularities differ. */
    private fun composeResampledSurface(
        source: MaskTileSurfaceSnapshot,
        mode: MaskComposeMode,
        destinationDefault: Int,
        resultDefault: Int,
    ) {
        val keys = linkedSetOf<PixelTileKey>().apply {
            addAll(surface.publishedKeys())
            addAll(workingTiles.keys)
            source.tileInfo.forEach { info ->
                val sourceBounds = logicalTileRect(
                    info.key.column,
                    info.key.row,
                    source.tileSize,
                    source.logicalWidth,
                    source.logicalHeight,
                )
                if (!sourceBounds.isEmpty) {
                    val firstColumn = sourceBounds.left / surface.tileSize
                    val lastColumn = (sourceBounds.right - 1) / surface.tileSize
                    val firstRow = sourceBounds.top / surface.tileSize
                    val lastRow = (sourceBounds.bottom - 1) / surface.tileSize
                    for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
                        add(PixelTileKey(column, row))
                    }
                }
            }
        }
        keys.forEach { key ->
            val targetBefore = workingTiles[key]?.alpha?.copyOf()
                ?: surface.publishedTile(key)?.alpha?.copyOf()
                ?: ByteArray(surface.tileSize * surface.tileSize) { destinationDefault.toByte() }
            val tile = writableTile(key)
            val tileLeft = key.column * surface.tileSize
            val tileTop = key.row * surface.tileSize
            for (localY in 0 until surface.tileSize) for (localX in 0 until surface.tileSize) {
                val x = tileLeft + localX
                val y = tileTop + localY
                val index = localY * surface.tileSize + localX
                tile.alpha[index] = if (x in 0 until surface.logicalWidth && y in 0 until surface.logicalHeight) {
                    compose(
                        targetBefore[index].toInt().and(0xff),
                        source.alphaAt(x, y),
                        mode,
                    ).toByte()
                } else {
                    resultDefault.toByte()
                }
            }
            markDirty(tile, surface.fullTileRect())
        }
        workingDefaultAlpha = resultDefault
    }

    fun copyAlpha(source: PixelRect): ByteArray {
        requireActive()
        requireInside(source, surface.logicalWidth, surface.logicalHeight)
        val output = ByteArray(Math.multiplyExact(source.width, source.height)) { workingDefaultAlpha.toByte() }
        if (source.isEmpty) return output
        val firstColumn = source.left / surface.tileSize
        val lastColumn = (source.right - 1) / surface.tileSize
        val firstRow = source.top / surface.tileSize
        val lastRow = (source.bottom - 1) / surface.tileSize
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val key = PixelTileKey(column, row)
            val tile = workingTiles[key]?.alpha ?: surface.publishedTile(key)?.alpha ?: continue
            val tileLeft = column * surface.tileSize
            val tileTop = row * surface.tileSize
            val overlap = logicalTileRect(column, row, surface.tileSize, surface.logicalWidth, surface.logicalHeight).intersect(source)
            for (y in overlap.top until overlap.bottom) {
                val tileOffset = (y - tileTop) * surface.tileSize + overlap.left - tileLeft
                val outputOffset = (y - source.top) * source.width + overlap.left - source.left
                tile.copyInto(output, outputOffset, tileOffset, tileOffset + overlap.width)
            }
        }
        return output
    }

    fun editTile(key: PixelTileKey, dirtyLocalRect: PixelRect = surface.fullTileRect(), block: (ByteArray, Int) -> Unit) {
        requireActive()
        val logical = logicalTileRect(key.column, key.row, surface.tileSize, surface.logicalWidth, surface.logicalHeight)
        require(!logical.isEmpty) { "Tile $key is outside the logical surface" }
        val clipped = dirtyLocalRect.intersect(PixelRect(0, 0, logical.width, logical.height))
        require(!clipped.isEmpty) { "Dirty rectangle does not intersect tile $key" }
        val tile = writableTile(key)
        block(tile.alpha, surface.tileSize)
        markDirty(tile, clipped)
    }

    fun commit(): MaskTileTransactionRecord { requireActive(); return surface.commit(this) }
    fun cancel() { requireActive(); surface.cancel(this) }
    override fun close() { if (state == State.ACTIVE) cancel() }

    internal fun markCommitted() { state = State.COMMITTED; workingTiles.clear() }
    internal fun markCancelled() { state = State.CANCELLED; workingTiles.clear() }

    private fun writableTile(key: PixelTileKey): WorkingMaskTile = workingTiles.getOrPut(key) {
        val before = surface.publishedTile(key)
        WorkingMaskTile(
            before,
            before?.alpha?.copyOf() ?: ByteArray(surface.tileSize * surface.tileSize) { workingDefaultAlpha.toByte() },
        )
    }

    private fun markDirty(tile: WorkingMaskTile, rect: PixelRect) {
        tile.dirtyRect = tile.dirtyRect?.union(rect) ?: rect
    }

    private fun requireActive() = check(state == State.ACTIVE) { "Mask transaction is $state" }
    private enum class State { ACTIVE, COMMITTED, CANCELLED }
}

private fun compose(destination: Int, source: Int, mode: MaskComposeMode): Int = when (mode) {
    MaskComposeMode.REPLACE -> source
    MaskComposeMode.ADD -> source + ((destination * (255 - source) + 127) / 255)
    MaskComposeMode.SUBTRACT -> (destination * (255 - source) + 127) / 255
    MaskComposeMode.INTERSECT -> (destination * source + 127) / 255
}

private fun logicalTileRect(column: Int, row: Int, tileSize: Int, width: Int, height: Int): PixelRect {
    val left = column * tileSize
    val top = row * tileSize
    val right = minOf(left + tileSize, width).coerceAtLeast(left)
    val bottom = minOf(top + tileSize, height).coerceAtLeast(top)
    return PixelRect(left, top, right, bottom)
}

private fun requireInside(rect: PixelRect, width: Int, height: Int) {
    require(rect == rect.intersect(PixelRect(0, 0, width, height))) { "Mask rectangle is outside the logical surface" }
}
