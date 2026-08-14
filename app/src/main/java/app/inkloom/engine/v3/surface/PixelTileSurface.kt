package app.inkloom.engine.v3.surface

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** A logical pixel rectangle using an exclusive right/bottom edge. */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right must be >= left" }
        require(bottom >= top) { "bottom must be >= top" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width == 0 || height == 0

    fun union(other: PixelRect): PixelRect = when {
        isEmpty -> other
        other.isEmpty -> this
        else -> PixelRect(
            left = minOf(left, other.left),
            top = minOf(top, other.top),
            right = maxOf(right, other.right),
            bottom = maxOf(bottom, other.bottom),
        )
    }

    fun intersect(other: PixelRect): PixelRect {
        val clippedLeft = maxOf(left, other.left)
        val clippedTop = maxOf(top, other.top)
        return PixelRect(
            left = clippedLeft,
            top = clippedTop,
            right = maxOf(clippedLeft, minOf(right, other.right)),
            bottom = maxOf(clippedTop, minOf(bottom, other.bottom)),
        )
    }
}

data class PixelTileKey(val column: Int, val row: Int)

/** Metadata for one immutable tile generation. Pixel storage is intentionally not exposed. */
data class PixelTileInfo(
    val key: PixelTileKey,
    val generation: Long,
    val nonTransparentPixelCount: Int,
)

data class PixelTileDeltaInfo(
    val key: PixelTileKey,
    val dirtyLocalRect: PixelRect,
    val beforeGeneration: Long?,
    val afterGeneration: Long?,
)

/**
 * One committed pixel edit. The record retains immutable before/after tile generations so applying
 * undo or redo only swaps touched tile references. Call [close] when a history entry is evicted.
 */
class PixelTileTransactionRecord internal constructor(
    val surfaceId: String,
    val transactionId: Long,
    val label: String,
    deltas: List<PixelTileDelta>,
) : AutoCloseable {
    private var retainedDeltas: List<PixelTileDelta> = deltas
    val changedTileCount: Int = deltas.size
    val changedPixelCapacity: Long = deltas.sumOf { it.pixelCapacity.toLong() }
    val retainedByteSize: Long = deltas.sumOf { delta ->
        delta.pixelCapacity.toLong() * Int.SIZE_BYTES *
            ((if (delta.before == null) 0 else 1) + (if (delta.after == null) 0 else 1))
    }
    val deltaInfo: List<PixelTileDeltaInfo> = deltas.map(PixelTileDelta::info)

    @Volatile
    private var closed = false

    val isClosed: Boolean get() = closed

    @Synchronized
    override fun close() {
        closed = true
        retainedDeltas = emptyList()
    }

    internal fun requireOpen() {
        check(!closed) { "Tile transaction record has been released" }
    }

    @Synchronized
    internal fun deltas(): List<PixelTileDelta> {
        requireOpen()
        return retainedDeltas
    }
}

internal data class PixelTileGeneration(
    val id: Long,
    val pixels: IntArray,
    val nonTransparentPixelCount: Int,
)

internal data class PixelTileDelta(
    val key: PixelTileKey,
    val dirtyLocalRect: PixelRect,
    val before: PixelTileGeneration?,
    val after: PixelTileGeneration?,
    val pixelCapacity: Int,
) {
    fun info(): PixelTileDeltaInfo = PixelTileDeltaInfo(
        key = key,
        dirtyLocalRect = dirtyLocalRect,
        beforeGeneration = before?.id,
        afterGeneration = after?.id,
    )
}

/**
 * Immutable view of a published surface generation. Tile arrays are shared internally but no
 * mutable references escape, so renderers can safely retain a snapshot while a later transaction
 * publishes new generations.
 */
class PixelTileSurfaceSnapshot internal constructor(
    val surfaceId: String,
    val logicalWidth: Int,
    val logicalHeight: Int,
    val tileSize: Int,
    val revision: Long,
    private val tiles: Map<PixelTileKey, PixelTileGeneration>,
) {
    val tileCount: Int get() = tiles.size
    val retainedByteSize: Long get() = tiles.size.toLong() * tileSize * tileSize * Int.SIZE_BYTES
    val tileInfo: List<PixelTileInfo> get() = tiles.map { (key, tile) ->
        PixelTileInfo(key, tile.id, tile.nonTransparentPixelCount)
    }

    fun pixelAt(x: Int, y: Int): Int {
        if (x !in 0 until logicalWidth || y !in 0 until logicalHeight) return 0
        val key = PixelTileKey(x / tileSize, y / tileSize)
        val tile = tiles[key] ?: return 0
        return tile.pixels[(y % tileSize) * tileSize + (x % tileSize)]
    }

    fun copyTilePixels(key: PixelTileKey): IntArray? = tiles[key]?.pixels?.copyOf()
}

/**
 * Authoritative sparse premultiplied-ARGB pixel storage.
 *
 * Reads never allocate. A transaction clones/allocates a tile only on its first write. Commit
 * publishes immutable tile generations atomically; cancel drops working copies. Transparent tiles
 * are implicit and are removed at commit. Exactly one write transaction may be active per surface,
 * while published snapshots remain readable without observing partial edits.
 */
class PixelTileSurface(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val tileSize: Int = DEFAULT_TILE_SIZE,
    val surfaceId: String = UUID.randomUUID().toString(),
) {
    init {
        require(logicalWidth > 0) { "logicalWidth must be positive" }
        require(logicalHeight > 0) { "logicalHeight must be positive" }
        require(tileSize in SUPPORTED_TILE_SIZES) {
            "tileSize must be one of ${SUPPORTED_TILE_SIZES.sorted()}"
        }
    }

    private val monitor = Any()
    private val generations = linkedMapOf<PixelTileKey, PixelTileGeneration>()
    private var activeTransactionId: Long? = null
    private var revisionValue = 0L

    val revision: Long get() = synchronized(monitor) { revisionValue }
    val tileCount: Int get() = synchronized(monitor) { generations.size }
    val retainedByteSize: Long
        get() = synchronized(monitor) {
            generations.size.toLong() * tileSize * tileSize * Int.SIZE_BYTES
        }

    fun pixelAt(x: Int, y: Int): Int = synchronized(monitor) {
        if (x !in 0 until logicalWidth || y !in 0 until logicalHeight) return@synchronized 0
        val key = PixelTileKey(x / tileSize, y / tileSize)
        generations[key]?.pixels?.get((y % tileSize) * tileSize + (x % tileSize)) ?: 0
    }

    fun snapshot(): PixelTileSurfaceSnapshot = synchronized(monitor) {
        PixelTileSurfaceSnapshot(
            surfaceId = surfaceId,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            tileSize = tileSize,
            revision = revisionValue,
            tiles = generations.toMap(),
        )
    }

    fun beginWrite(label: String): PixelTileWriteTransaction {
        require(label.isNotBlank()) { "A tile transaction needs a diagnostic label" }
        return synchronized(monitor) {
            check(activeTransactionId == null) { "Only one write transaction may be active" }
            val id = transactionIds.incrementAndGet()
            activeTransactionId = id
            PixelTileWriteTransaction(this, id, label)
        }
    }

    /** Applies a previously committed record as an atomic undo or redo reference swap. */
    fun apply(record: PixelTileTransactionRecord, direction: HistoryDirection) {
        record.requireOpen()
        require(record.surfaceId == surfaceId) { "Transaction belongs to another surface" }
        synchronized(monitor) {
            check(activeTransactionId == null) { "Cannot apply history while a write is active" }
            val deltas = record.deltas()
            requireCurrentGenerations(record, deltas, direction)
            deltas.forEach { delta ->
                val generation = when (direction) {
                    HistoryDirection.UNDO -> delta.before
                    HistoryDirection.REDO -> delta.after
                }
                if (generation == null) generations.remove(delta.key)
                else generations[delta.key] = generation
            }
            if (deltas.isNotEmpty()) revisionValue++
        }
    }

    internal fun requireRecordCurrent(
        record: PixelTileTransactionRecord,
        direction: HistoryDirection,
    ) {
        record.requireOpen()
        require(record.surfaceId == surfaceId) { "Transaction belongs to another surface" }
        synchronized(monitor) {
            check(activeTransactionId == null) { "Cannot inspect history while a write is active" }
            requireCurrentGenerations(record, record.deltas(), direction)
        }
    }

    internal fun publishedTile(key: PixelTileKey): PixelTileGeneration? = synchronized(monitor) {
        generations[key]
    }

    internal fun commit(transaction: PixelTileWriteTransaction): PixelTileTransactionRecord =
        synchronized(monitor) {
            requireActive(transaction)
            val deltas = transaction.workingTiles.mapNotNull { (key, working) ->
                val before = working.before
                if (before != null && before.pixels.contentEquals(working.pixels)) return@mapNotNull null
                if (before == null && working.pixels.all(::isTransparent)) return@mapNotNull null

                val nonTransparent = working.pixels.count { !isTransparent(it) }
                val after = if (nonTransparent == 0) null else PixelTileGeneration(
                    id = generationIds.incrementAndGet(),
                    pixels = working.pixels,
                    nonTransparentPixelCount = nonTransparent,
                )
                PixelTileDelta(
                    key = key,
                    dirtyLocalRect = working.dirtyRect ?: fullTileRect(),
                    before = before,
                    after = after,
                    pixelCapacity = tileSize * tileSize,
                )
            }
            deltas.forEach { delta ->
                if (delta.after == null) generations.remove(delta.key)
                else generations[delta.key] = delta.after
            }
            activeTransactionId = null
            transaction.markCommitted()
            if (deltas.isNotEmpty()) revisionValue++
            PixelTileTransactionRecord(surfaceId, transaction.id, transaction.label, deltas)
        }

    internal fun cancel(transaction: PixelTileWriteTransaction) {
        synchronized(monitor) {
            requireActive(transaction)
            activeTransactionId = null
            transaction.markCancelled()
        }
    }

    internal fun requireLogicalCoordinate(x: Int, y: Int) {
        require(x in 0 until logicalWidth && y in 0 until logicalHeight) {
            "Pixel ($x,$y) is outside ${logicalWidth}x$logicalHeight"
        }
    }

    internal fun keyFor(x: Int, y: Int): PixelTileKey =
        PixelTileKey(x / tileSize, y / tileSize)

    internal fun localIndex(x: Int, y: Int): Int =
        (y % tileSize) * tileSize + (x % tileSize)

    internal fun fullTileRect(): PixelRect = PixelRect(0, 0, tileSize, tileSize)

    private fun requireActive(transaction: PixelTileWriteTransaction) {
        check(activeTransactionId == transaction.id) { "Write transaction is no longer active" }
    }

    private fun requireCurrentGenerations(
        record: PixelTileTransactionRecord,
        deltas: List<PixelTileDelta>,
        direction: HistoryDirection,
    ) {
        deltas.forEach { delta ->
            val expected = when (direction) {
                HistoryDirection.UNDO -> delta.after
                HistoryDirection.REDO -> delta.before
            }
            check(generations[delta.key]?.id == expected?.id) {
                "Tile ${delta.key} changed since transaction ${record.transactionId}"
            }
        }
    }

    enum class HistoryDirection { UNDO, REDO }

    companion object {
        const val DEFAULT_TILE_SIZE = 128
        val SUPPORTED_TILE_SIZES: Set<Int> = setOf(64, 128, 256)

        private val transactionIds = AtomicLong(0L)
        private val generationIds = AtomicLong(0L)

        private fun isTransparent(pixel: Int): Boolean = pixel ushr 24 == 0
    }
}

internal data class WorkingPixelTile(
    val before: PixelTileGeneration?,
    val pixels: IntArray,
    var dirtyRect: PixelRect? = null,
)

/** Mutable working set for one [PixelTileSurface] transaction. */
class PixelTileWriteTransaction internal constructor(
    private val surface: PixelTileSurface,
    internal val id: Long,
    internal val label: String,
) : AutoCloseable {
    internal val workingTiles = linkedMapOf<PixelTileKey, WorkingPixelTile>()
    private var state = State.ACTIVE

    val touchedTileCount: Int get() = workingTiles.size
    val workingByteSize: Long
        get() = workingTiles.size.toLong() * surface.tileSize * surface.tileSize * Int.SIZE_BYTES

    fun pixelAt(x: Int, y: Int): Int {
        surface.requireLogicalCoordinate(x, y)
        requireActive()
        val key = surface.keyFor(x, y)
        return workingTiles[key]?.pixels?.get(surface.localIndex(x, y))
            ?: surface.publishedTile(key)?.pixels?.get(surface.localIndex(x, y))
            ?: 0
    }

    fun setPixel(x: Int, y: Int, premultipliedArgb: Int) {
        surface.requireLogicalCoordinate(x, y)
        requireActive()
        val key = surface.keyFor(x, y)
        val tile = writableTile(key)
        tile.pixels[surface.localIndex(x, y)] = premultipliedArgb
        val localX = x % surface.tileSize
        val localY = y % surface.tileSize
        markDirty(tile, PixelRect(localX, localY, localX + 1, localY + 1))
    }

    /**
     * Mutates a tile-local rectangle. The array is valid only during [block] and must not be
     * retained. Edge tiles still use a full fixed-size allocation; writes outside logical bounds
     * are rejected by the clipped rectangle.
     */
    fun editTile(
        key: PixelTileKey,
        dirtyLocalRect: PixelRect = surface.fullTileRect(),
        block: (pixels: IntArray, rowStride: Int) -> Unit,
    ) {
        requireActive()
        val logicalTileRect = PixelRect(
            left = key.column * surface.tileSize,
            top = key.row * surface.tileSize,
            right = (key.column + 1) * surface.tileSize,
            bottom = (key.row + 1) * surface.tileSize,
        ).intersect(PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight))
        require(!logicalTileRect.isEmpty) { "Tile $key is outside the logical surface" }
        val validLocalRect = PixelRect(
            0,
            0,
            logicalTileRect.width,
            logicalTileRect.height,
        )
        val clippedDirty = dirtyLocalRect.intersect(validLocalRect)
        require(!clippedDirty.isEmpty) { "Dirty rectangle does not intersect tile $key" }
        val tile = writableTile(key)
        block(tile.pixels, surface.tileSize)
        markDirty(tile, clippedDirty)
    }

    fun commit(): PixelTileTransactionRecord {
        requireActive()
        return surface.commit(this)
    }

    fun cancel() {
        requireActive()
        surface.cancel(this)
    }

    override fun close() {
        if (state == State.ACTIVE) cancel()
    }

    internal fun markCommitted() {
        state = State.COMMITTED
        workingTiles.clear()
    }

    internal fun markCancelled() {
        state = State.CANCELLED
        workingTiles.clear()
    }

    private fun writableTile(key: PixelTileKey): WorkingPixelTile = workingTiles.getOrPut(key) {
        val before = surface.publishedTile(key)
        WorkingPixelTile(
            before = before,
            pixels = before?.pixels?.copyOf() ?: IntArray(surface.tileSize * surface.tileSize),
        )
    }

    private fun markDirty(tile: WorkingPixelTile, rect: PixelRect) {
        tile.dirtyRect = tile.dirtyRect?.union(rect) ?: rect
    }

    private fun requireActive() {
        check(state == State.ACTIVE) { "Write transaction is $state" }
    }

    private enum class State { ACTIVE, COMMITTED, CANCELLED }
}
