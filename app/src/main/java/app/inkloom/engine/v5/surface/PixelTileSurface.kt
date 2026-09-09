package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.tools.NativeRasterOps
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
        delta.pixelCapacity.toLong() * delta.bytesPerPixel *
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
    val pixels: PixelTileBuffer,
    val nonTransparentPixelCount: Int,
)

internal data class PixelTileDelta(
    val key: PixelTileKey,
    val dirtyLocalRect: PixelRect,
    val before: PixelTileGeneration?,
    val after: PixelTileGeneration?,
    val pixelCapacity: Int,
    val bytesPerPixel: Int,
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
    val pixelFormat: PixelTileFormat,
    val revision: Long,
    private val tiles: Map<PixelTileKey, PixelTileGeneration>,
) {
    /** Internal immutable views used by frame-time caches without allocating metadata lists. */
    internal val tileKeys: Set<PixelTileKey> get() = tiles.keys
    internal fun generationAt(key: PixelTileKey): Long? = tiles[key]?.id

    val tileCount: Int get() = tiles.size
    val retainedByteSize: Long get() = tiles.size.toLong() * tileSize * tileSize * pixelFormat.bytesPerPixel
    val tileInfo: List<PixelTileInfo> get() = tiles.map { (key, tile) ->
        PixelTileInfo(key, tile.id, tile.nonTransparentPixelCount)
    }

    fun pixelAt(x: Int, y: Int): Int {
        if (x !in 0 until logicalWidth || y !in 0 until logicalHeight) return 0
        val key = PixelTileKey(x / tileSize, y / tileSize)
        val tile = tiles[key] ?: return 0
        return tile.pixels.premultipliedArgbAt((y % tileSize) * tileSize + (x % tileSize))
    }

    /** Exact native half-float bits, including extended-range RGB; never converts through 8-bit. */
    fun packedF16At(x: Int, y: Int): Long {
        require(pixelFormat == PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
        if (x !in 0 until logicalWidth || y !in 0 until logicalHeight) return 0L
        val tile = tiles[PixelTileKey(x / tileSize, y / tileSize)] ?: return 0L
        return (tile.pixels as RgbaF16TileBuffer).values[(y % tileSize) * tileSize + x % tileSize]
    }

    fun linearPixelAt(x: Int, y: Int): LinearRgba {
        if (x !in 0 until logicalWidth || y !in 0 until logicalHeight) return LinearRgba(0f, 0f, 0f, 0f)
        val key = PixelTileKey(x / tileSize, y / tileSize)
        val tile = tiles[key] ?: return LinearRgba(0f, 0f, 0f, 0f)
        return tile.pixels.premultipliedLinearAt((y % tileSize) * tileSize + (x % tileSize))
    }

    fun copyTilePixels(key: PixelTileKey): IntArray? {
        require(pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) { "Surface stores $pixelFormat" }
        return (tiles[key]?.pixels as? Argb8888TileBuffer)?.values?.copyOf()
    }

    fun copyTileF16(key: PixelTileKey): LongArray? {
        require(pixelFormat == PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16) { "Surface stores $pixelFormat" }
        return (tiles[key]?.pixels as? RgbaF16TileBuffer)?.values?.copyOf()
    }

    /** Internal zero-copy read. The array is immutable and must not be retained or modified. */
    internal fun <T> readRgba8Tile(key: PixelTileKey, block: (IntArray) -> T): T? {
        require(pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) { "Surface stores $pixelFormat" }
        val pixels = (tiles[key]?.pixels as? Argb8888TileBuffer)?.values ?: return null
        return block(pixels)
    }

    /** Internal zero-copy read. The array is immutable and must not be retained or modified. */
    internal fun <T> readF16Tile(key: PixelTileKey, block: (LongArray) -> T): T? {
        require(pixelFormat == PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16) { "Surface stores $pixelFormat" }
        val pixels = (tiles[key]?.pixels as? RgbaF16TileBuffer)?.values ?: return null
        return block(pixels)
    }

    /** Copies a logical rectangle without allocating or looking up a tile for every output pixel. */
    fun copyPremultipliedPixels(source: PixelRect): IntArray {
        require(pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) { "ARGB copy requires an RGBA8 surface" }
        val clipped = source.intersect(PixelRect(0, 0, logicalWidth, logicalHeight))
        require(clipped == source && !source.isEmpty) { "Source rectangle must be inside the surface" }
        val output = IntArray(Math.multiplyExact(source.width, source.height))
        tiles.forEach { (key, tile) ->
            val tileLeft = key.column * tileSize
            val tileTop = key.row * tileSize
            val overlap = PixelRect(
                tileLeft,
                tileTop,
                minOf(tileLeft + tileSize, logicalWidth),
                minOf(tileTop + tileSize, logicalHeight),
            ).intersect(source)
            if (overlap.isEmpty) return@forEach
            for (y in overlap.top until overlap.bottom) {
                val sourceOffset = (y - tileTop) * tileSize + overlap.left - tileLeft
                val destinationOffset = (y - source.top) * source.width + overlap.left - source.left
                (tile.pixels as Argb8888TileBuffer).values.copyInto(
                    output,
                    destinationOffset,
                    sourceOffset,
                    sourceOffset + overlap.width,
                )
            }
        }
        return output
    }

    internal fun copyPremultipliedPixels(source: PixelRect, output: IntArray) {
        require(pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) { "ARGB copy requires an RGBA8 surface" }
        require(source == source.intersect(PixelRect(0, 0, logicalWidth, logicalHeight)) && !source.isEmpty)
        require(output.size >= source.width * source.height)
        output.fill(0, 0, source.width * source.height)
        tiles.forEach { (key, tile) ->
            val tileLeft = key.column * tileSize
            val tileTop = key.row * tileSize
            val overlap = PixelRect(
                tileLeft, tileTop, minOf(tileLeft + tileSize, logicalWidth), minOf(tileTop + tileSize, logicalHeight),
            ).intersect(source)
            if (overlap.isEmpty) return@forEach
            copyPixelRows(
                (tile.pixels as Argb8888TileBuffer).values, output,
                (overlap.top - tileTop) * tileSize + overlap.left - tileLeft,
                (overlap.top - source.top) * source.width + overlap.left - source.left,
                overlap.width, overlap.height, tileSize, source.width,
            )
        }
    }

    fun copyPremultipliedF16(source: PixelRect): LongArray {
        require(pixelFormat == PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16) { "F16 copy requires a linear F16 surface" }
        val clipped = source.intersect(PixelRect(0, 0, logicalWidth, logicalHeight))
        require(clipped == source && !source.isEmpty) { "Source rectangle must be inside the surface" }
        val output = LongArray(Math.multiplyExact(source.width, source.height))
        tiles.forEach { (key, tile) ->
            val tileLeft = key.column * tileSize
            val tileTop = key.row * tileSize
            val overlap = PixelRect(
                tileLeft,
                tileTop,
                minOf(tileLeft + tileSize, logicalWidth),
                minOf(tileTop + tileSize, logicalHeight),
            ).intersect(source)
            if (overlap.isEmpty) return@forEach
            for (y in overlap.top until overlap.bottom) {
                val sourceOffset = (y - tileTop) * tileSize + overlap.left - tileLeft
                val destinationOffset = (y - source.top) * source.width + overlap.left - source.left
                (tile.pixels as RgbaF16TileBuffer).values.copyInto(
                    output,
                    destinationOffset,
                    sourceOffset,
                    sourceOffset + overlap.width,
                )
            }
        }
        return output
    }
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
    val pixelFormat: PixelTileFormat = PixelTileFormat.PREMULTIPLIED_SRGB_8888,
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
            generations.size.toLong() * tileSize * tileSize * pixelFormat.bytesPerPixel
        }

    fun pixelAt(x: Int, y: Int): Int = synchronized(monitor) {
        if (x !in 0 until logicalWidth || y !in 0 until logicalHeight) return@synchronized 0
        val key = PixelTileKey(x / tileSize, y / tileSize)
        generations[key]?.pixels?.premultipliedArgbAt((y % tileSize) * tileSize + (x % tileSize)) ?: 0
    }

    fun linearPixelAt(x: Int, y: Int): LinearRgba = synchronized(monitor) {
        if (x !in 0 until logicalWidth || y !in 0 until logicalHeight) return@synchronized LinearRgba(0f, 0f, 0f, 0f)
        val key = PixelTileKey(x / tileSize, y / tileSize)
        generations[key]?.pixels?.premultipliedLinearAt((y % tileSize) * tileSize + (x % tileSize))
            ?: LinearRgba(0f, 0f, 0f, 0f)
    }

    fun snapshot(): PixelTileSurfaceSnapshot = synchronized(monitor) {
        PixelTileSurfaceSnapshot(
            surfaceId = surfaceId,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            tileSize = tileSize,
            pixelFormat = pixelFormat,
            revision = revisionValue,
            tiles = generations.toMap(),
        )
    }

    /**
     * Immutable presentation snapshot of an active transaction. Published document generations
     * remain untouched; only the transaction's bounded working tiles are copied. Preview
     * generations use negative ids so generation-keyed render caches can never confuse them with
     * durable generations or with an earlier MOVE frame.
     */
    internal fun preview(transaction: PixelTileWriteTransaction): PixelTileSurfaceSnapshot = synchronized(monitor) {
        requireActive(transaction)
        val previewTiles = generations.toMutableMap()
        transaction.workingTiles.forEach { (key, working) ->
            val previewGeneration = if (working.previewVersion == working.mutationVersion) {
                working.cachedPreviewGeneration
            } else {
                val pixels = working.pixels.copyBuffer()
                val nonTransparent = pixels.nonTransparentPixelCount()
                val generation = if (nonTransparent == 0) null else PixelTileGeneration(
                    id = previewGenerationIds.getAndDecrement(),
                    pixels = pixels,
                    nonTransparentPixelCount = nonTransparent,
                )
                working.cachedPreviewGeneration = generation
                working.previewVersion = working.mutationVersion
                generation
            }
            if (previewGeneration == null) previewTiles.remove(key) else previewTiles[key] = previewGeneration
        }
        PixelTileSurfaceSnapshot(
            surfaceId = surfaceId,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            tileSize = tileSize,
            pixelFormat = pixelFormat,
            revision = revisionValue,
            tiles = previewTiles,
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

                val nonTransparent = working.pixels.nonTransparentPixelCount()
                if (before == null && nonTransparent == 0) return@mapNotNull null
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
                    bytesPerPixel = pixelFormat.bytesPerPixel,
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

    /**
     * Publishes ownership-transferred active-stroke tiles in one atomic document transaction.
     * Source-over pixels may be adopted when the destination tile is empty; every other case is
     * composited into a private generation so undo/redo snapshots remain immutable.
     */
    internal fun publishFrozenRgba8Tiles(
        label: String,
        tiles: List<FrozenRgba8Tile>,
        adoptOnEmpty: Boolean,
        blendMode: Int,
    ): PixelTileTransactionRecord = synchronized(monitor) {
        require(pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888)
        require(blendMode in 0..2)
        check(activeTransactionId == null) { "Cannot publish an active delta while a write is active" }
        val transactionId = transactionIds.incrementAndGet()
        val deltas = tiles.mapNotNull { tile ->
            val before = generations[tile.key]
            val afterPixels: IntArray
            val nonTransparent: Int
            if (adoptOnEmpty && before == null) {
                if (tile.nonTransparentPixelCount == 0) return@mapNotNull null
                afterPixels = tile.pixels
                nonTransparent = tile.nonTransparentPixelCount
            } else {
                if (before == null) return@mapNotNull null
                afterPixels = tile.pixels
                val backdrop = (before.pixels as Argb8888TileBuffer).values
                val nativeResult = NativeRasterOps.materializePremultipliedTile(
                    contribution = afterPixels,
                    backdrop = backdrop,
                    stride = tileSize,
                    left = tile.dirtyRect.left,
                    top = tile.dirtyRect.top,
                    right = tile.dirtyRect.right,
                    bottom = tile.dirtyRect.bottom,
                    blendMode = blendMode,
                )
                val result = if (nativeResult >= 0) nativeResult else materializeReference(
                    afterPixels,
                    backdrop,
                    tileSize,
                    tile.dirtyRect,
                    blendMode,
                )
                if (result and NativeRasterOps.MATERIALIZE_CHANGED_FLAG == 0) return@mapNotNull null
                nonTransparent = result and NativeRasterOps.MATERIALIZE_CHANGED_FLAG.inv()
            }
            val after = if (nonTransparent == 0) null else PixelTileGeneration(
                id = generationIds.incrementAndGet(),
                pixels = Argb8888TileBuffer(afterPixels),
                nonTransparentPixelCount = nonTransparent,
            )
            PixelTileDelta(
                key = tile.key,
                dirtyLocalRect = tile.dirtyRect,
                before = before,
                after = after,
                pixelCapacity = tileSize * tileSize,
                bytesPerPixel = pixelFormat.bytesPerPixel,
            )
        }
        deltas.forEach { delta ->
            if (delta.after == null) generations.remove(delta.key) else generations[delta.key] = delta.after
        }
        if (deltas.isNotEmpty()) revisionValue++
        PixelTileTransactionRecord(surfaceId, transactionId, label, deltas)
    }

    private fun materializeReference(
        contribution: IntArray,
        backdrop: IntArray,
        stride: Int,
        dirtyRect: PixelRect,
        blendMode: Int,
    ): Int {
        var changed = false
        var nonTransparent = 0
        contribution.indices.forEach { index ->
            val x = index % stride
            val y = index / stride
            val before = backdrop[index]
            val after = if (x in dirtyRect.left until dirtyRect.right && y in dirtyRect.top until dirtyRect.bottom) {
                compositePixel(before, contribution[index], blendMode)
            } else before
            contribution[index] = after
            changed = changed || after != before
            if (after ushr 24 != 0) nonTransparent++
        }
        return (if (changed) NativeRasterOps.MATERIALIZE_CHANGED_FLAG else 0) or nonTransparent
    }

    private fun compositePixel(destination: Int, source: Int, blendMode: Int): Int {
        val sourceAlpha = source ushr 24 and 0xff
        return when (blendMode) {
            0 -> {
                if (sourceAlpha == 0) destination else if (sourceAlpha == 255) source else {
                    val inverse = 255 - sourceAlpha
                    val destinationAlpha = destination ushr 24 and 0xff
                    ((sourceAlpha + destinationAlpha * inverse / 255).coerceAtMost(255) shl 24) or
                        ((((source ushr 16) and 0xff) + ((destination ushr 16) and 0xff) * inverse / 255).coerceAtMost(255) shl 16) or
                        ((((source ushr 8) and 0xff) + ((destination ushr 8) and 0xff) * inverse / 255).coerceAtMost(255) shl 8) or
                        (((source and 0xff) + (destination and 0xff) * inverse / 255).coerceAtMost(255))
                }
            }
            1 -> {
                val keep = 255 - sourceAlpha
                ((((destination ushr 24) and 0xff) * keep / 255) shl 24) or
                    ((((destination ushr 16) and 0xff) * keep / 255) shl 16) or
                    ((((destination ushr 8) and 0xff) * keep / 255) shl 8) or
                    ((destination and 0xff) * keep / 255)
            }
            else -> {
                val destinationAlpha = destination ushr 24 and 0xff
                if (destinationAlpha == 0 || sourceAlpha == 0) destination else {
                    val inverse = 255 - sourceAlpha
                    (destinationAlpha shl 24) or
                        (((((source ushr 16) and 0xff) * destinationAlpha + ((destination ushr 16) and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha) shl 16) or
                        (((((source ushr 8) and 0xff) * destinationAlpha + ((destination ushr 8) and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha) shl 8) or
                        ((((source and 0xff) * destinationAlpha + (destination and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha))
                }
            }
        }
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
        private val previewGenerationIds = AtomicLong(-1L)
        private const val MAX_RESTORED_GENERATION = Long.MAX_VALUE / 2L

        /** Restores immutable published tiles from a verified persistence boundary. */
        internal fun restore(
            logicalWidth: Int,
            logicalHeight: Int,
            tileSize: Int,
            surfaceId: String,
            revision: Long,
            tiles: Map<PixelTileKey, IntArray>,
            tileGenerations: Map<PixelTileKey, Long> = emptyMap(),
            copyTiles: Boolean = true,
        ): PixelTileSurface {
            require(revision >= 0L) { "revision must not be negative" }
            val surface = PixelTileSurface(logicalWidth, logicalHeight, tileSize, surfaceId)
            synchronized(surface.monitor) {
                tiles.forEach { (key, pixels) ->
                    require(pixels.size == tileSize * tileSize) { "Tile $key has invalid pixel capacity" }
                    val tileLeft = key.column * tileSize
                    val tileTop = key.row * tileSize
                    require(tileLeft in 0 until logicalWidth && tileTop in 0 until logicalHeight) {
                        "Tile $key is outside the logical surface"
                    }
                    val buffer = Argb8888TileBuffer(if (copyTiles) pixels.copyOf() else pixels)
                    val nonTransparent = buffer.nonTransparentPixelCount()
                    if (nonTransparent > 0) {
                        val restoredGeneration = tileGenerations[key]?.also { id ->
                            require(id in 0L..MAX_RESTORED_GENERATION) { "Tile $key has an invalid generation" }
                            generationIds.updateAndGet { current -> maxOf(current, id) }
                        } ?: generationIds.incrementAndGet()
                        surface.generations[key] = PixelTileGeneration(
                            id = restoredGeneration,
                            pixels = buffer,
                            nonTransparentPixelCount = nonTransparent,
                        )
                    }
                }
                surface.revisionValue = revision
            }
            return surface
        }

        internal fun restoreF16(
            logicalWidth: Int,
            logicalHeight: Int,
            tileSize: Int,
            surfaceId: String,
            revision: Long,
            tiles: Map<PixelTileKey, LongArray>,
            tileGenerations: Map<PixelTileKey, Long> = emptyMap(),
            copyTiles: Boolean = true,
        ): PixelTileSurface {
            require(revision >= 0L) { "revision must not be negative" }
            val surface = PixelTileSurface(
                logicalWidth,
                logicalHeight,
                tileSize,
                surfaceId,
                PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
            )
            synchronized(surface.monitor) {
                tiles.forEach { (key, pixels) ->
                    require(pixels.size == tileSize * tileSize) { "Tile $key has invalid pixel capacity" }
                    val tileLeft = key.column * tileSize
                    val tileTop = key.row * tileSize
                    require(tileLeft in 0 until logicalWidth && tileTop in 0 until logicalHeight) {
                        "Tile $key is outside the logical surface"
                    }
                    val buffer = RgbaF16TileBuffer(if (copyTiles) pixels.copyOf() else pixels)
                    val nonTransparent = buffer.nonTransparentPixelCount()
                    if (nonTransparent > 0) {
                        val restoredGeneration = tileGenerations[key]?.also { id ->
                            require(id in 0L..MAX_RESTORED_GENERATION) { "Tile $key has an invalid generation" }
                            generationIds.updateAndGet { current -> maxOf(current, id) }
                        } ?: generationIds.incrementAndGet()
                        surface.generations[key] = PixelTileGeneration(
                            id = restoredGeneration,
                            pixels = buffer,
                            nonTransparentPixelCount = nonTransparent,
                        )
                    }
                }
                surface.revisionValue = revision
            }
            return surface
        }
    }
}

internal data class WorkingPixelTile(
    val before: PixelTileGeneration?,
    val pixels: PixelTileBuffer,
    var dirtyRect: PixelRect? = null,
    var mutationVersion: Long = 0L,
    var previewVersion: Long = -1L,
    var cachedPreviewGeneration: PixelTileGeneration? = null,
)

/** Ownership-transferred active-stroke pixels. [pixels] must not be mutated after publication. */
internal data class FrozenRgba8Tile(
    val key: PixelTileKey,
    val dirtyRect: PixelRect,
    val pixels: IntArray,
    val nonTransparentPixelCount: Int,
)

/** Mutable working set for one [PixelTileSurface] transaction. */
class PixelTileWriteTransaction internal constructor(
    private val surface: PixelTileSurface,
    internal val id: Long,
    internal val label: String,
) : AutoCloseable {
    internal val workingTiles = linkedMapOf<PixelTileKey, WorkingPixelTile>()
    private var state = State.ACTIVE
    private var mutationVersion = 0L
    private var previewVersion = -1L
    private var cachedPreview: PixelTileSurfaceSnapshot? = null

    val touchedTileCount: Int get() = workingTiles.size
    val workingByteSize: Long
        get() = workingTiles.size.toLong() * surface.tileSize * surface.tileSize * surface.pixelFormat.bytesPerPixel

    /** Freezes this gesture-local transaction and transfers ownership of its RGBA8 tile arrays. */
    internal fun freezeRgba8WorkingTiles(): List<FrozenRgba8Tile> {
        require(surface.pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888)
        requireActive()
        val frozen = workingTiles.map { (key, working) ->
            FrozenRgba8Tile(
                key = key,
                dirtyRect = working.dirtyRect ?: surface.fullTileRect(),
                pixels = (working.pixels as Argb8888TileBuffer).values,
                nonTransparentPixelCount = working.cachedPreviewGeneration?.nonTransparentPixelCount
                    ?: working.pixels.nonTransparentPixelCount(),
            )
        }
        surface.cancel(this)
        return frozen
    }

    fun pixelAt(x: Int, y: Int): Int {
        surface.requireLogicalCoordinate(x, y)
        requireActive()
        val key = surface.keyFor(x, y)
        return workingTiles[key]?.pixels?.premultipliedArgbAt(surface.localIndex(x, y))
            ?: surface.publishedTile(key)?.pixels?.premultipliedArgbAt(surface.localIndex(x, y))
            ?: 0
    }

    fun linearPixelAt(x: Int, y: Int): LinearRgba {
        surface.requireLogicalCoordinate(x, y)
        requireActive()
        val key = surface.keyFor(x, y)
        return workingTiles[key]?.pixels?.premultipliedLinearAt(surface.localIndex(x, y))
            ?: surface.publishedTile(key)?.pixels?.premultipliedLinearAt(surface.localIndex(x, y))
            ?: LinearRgba(0f, 0f, 0f, 0f)
    }

    fun setPixel(x: Int, y: Int, premultipliedArgb: Int) {
        surface.requireLogicalCoordinate(x, y)
        requireActive()
        val key = surface.keyFor(x, y)
        val tile = writableTile(key)
        val index = surface.localIndex(x, y)
        when (val pixels = tile.pixels) {
            is Argb8888TileBuffer -> pixels.values[index] = premultipliedArgb
            is RgbaF16TileBuffer -> pixels.values[index] = PackedRgbaF16.fromPremultipliedArgb8888(premultipliedArgb)
        }
        val localX = x % surface.tileSize
        val localY = y % surface.tileSize
        markDirty(tile, PixelRect(localX, localY, localX + 1, localY + 1))
    }

    fun setLinearPixel(x: Int, y: Int, straightLinearColor: LinearRgba) {
        require(surface.pixelFormat == PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16) {
            "Linear pixel writes require an F16 surface"
        }
        surface.requireLogicalCoordinate(x, y)
        requireActive()
        val tile = writableTile(surface.keyFor(x, y))
        (tile.pixels as RgbaF16TileBuffer).values[surface.localIndex(x, y)] =
            PackedRgbaF16.packStraight(straightLinearColor)
        val localX = x % surface.tileSize
        val localY = y % surface.tileSize
        markDirty(tile, PixelRect(localX, localY, localX + 1, localY + 1))
    }

    /**
     * Copies the transaction's current pixels for [source] without publishing partial edits.
     * Working tile generations win over the immutable surface snapshot, allowing a tool to sample
     * pigment deposited earlier in the same gesture.
     */
    fun copyPremultipliedPixels(source: PixelRect): IntArray {
        requireActive()
        require(surface.pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) { "ARGB copy requires an RGBA8 surface" }
        val logicalBounds = PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
        require(source == source.intersect(logicalBounds) && !source.isEmpty) {
            "Source rectangle must be inside the surface"
        }
        val output = IntArray(Math.multiplyExact(source.width, source.height))
        val firstColumn = source.left / surface.tileSize
        val lastColumn = (source.right - 1) / surface.tileSize
        val firstRow = source.top / surface.tileSize
        val lastRow = (source.bottom - 1) / surface.tileSize
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val key = PixelTileKey(column, row)
            val pixels = (workingTiles[key]?.pixels ?: surface.publishedTile(key)?.pixels ?: continue) as Argb8888TileBuffer
            val tileLeft = column * surface.tileSize
            val tileTop = row * surface.tileSize
            val overlap = PixelRect(
                tileLeft,
                tileTop,
                minOf(tileLeft + surface.tileSize, surface.logicalWidth),
                minOf(tileTop + surface.tileSize, surface.logicalHeight),
            ).intersect(source)
            for (y in overlap.top until overlap.bottom) {
                val tileOffset = (y - tileTop) * surface.tileSize + overlap.left - tileLeft
                val outputOffset = (y - source.top) * source.width + overlap.left - source.left
                pixels.values.copyInto(output, outputOffset, tileOffset, tileOffset + overlap.width)
            }
        }
        return output
    }

    internal fun copyPremultipliedPixels(source: PixelRect, output: IntArray) {
        requireActive()
        require(surface.pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888)
        require(source == source.intersect(PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)) && !source.isEmpty)
        require(output.size >= source.width * source.height)
        output.fill(0, 0, source.width * source.height)
        val firstColumn = source.left / surface.tileSize
        val lastColumn = (source.right - 1) / surface.tileSize
        val firstRow = source.top / surface.tileSize
        val lastRow = (source.bottom - 1) / surface.tileSize
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val key = PixelTileKey(column, row)
            val pixels = (workingTiles[key]?.pixels ?: surface.publishedTile(key)?.pixels ?: continue) as Argb8888TileBuffer
            val tileLeft = column * surface.tileSize
            val tileTop = row * surface.tileSize
            val overlap = PixelRect(
                tileLeft, tileTop,
                minOf(tileLeft + surface.tileSize, surface.logicalWidth),
                minOf(tileTop + surface.tileSize, surface.logicalHeight),
            ).intersect(source)
            copyPixelRows(
                pixels.values, output,
                (overlap.top - tileTop) * surface.tileSize + overlap.left - tileLeft,
                (overlap.top - source.top) * source.width + overlap.left - source.left,
                overlap.width, overlap.height, surface.tileSize, source.width,
            )
        }
    }

    /** Replaces [destination] from a premultiplied row-major buffer using tile-row copies. */
    fun replacePremultipliedPixels(
        destination: PixelRect,
        pixels: IntArray,
        rowStride: Int = destination.width,
    ) {
        requireActive()
        require(surface.pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) { "ARGB replacement requires an RGBA8 surface" }
        val logicalBounds = PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
        require(destination == destination.intersect(logicalBounds) && !destination.isEmpty) {
            "Destination rectangle must be inside the surface"
        }
        require(rowStride >= destination.width) { "Row stride is smaller than the destination width" }
        require(pixels.size >= (destination.height - 1) * rowStride + destination.width) {
            "Source buffer is too small for the destination"
        }
        val firstColumn = destination.left / surface.tileSize
        val lastColumn = (destination.right - 1) / surface.tileSize
        val firstRow = destination.top / surface.tileSize
        val lastRow = (destination.bottom - 1) / surface.tileSize
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val key = PixelTileKey(column, row)
            val tileLeft = column * surface.tileSize
            val tileTop = row * surface.tileSize
            val overlap = PixelRect(
                tileLeft,
                tileTop,
                minOf(tileLeft + surface.tileSize, surface.logicalWidth),
                minOf(tileTop + surface.tileSize, surface.logicalHeight),
            ).intersect(destination)
            val dirtyLocal = PixelRect(
                overlap.left - tileLeft,
                overlap.top - tileTop,
                overlap.right - tileLeft,
                overlap.bottom - tileTop,
            )
            editTile(key, dirtyLocal) { tilePixels, tileStride ->
                copyPixelRows(
                    pixels, tilePixels,
                    (overlap.top - destination.top) * rowStride + overlap.left - destination.left,
                    (overlap.top - tileTop) * tileStride + overlap.left - tileLeft,
                    overlap.width, overlap.height, rowStride, tileStride,
                )
            }
        }
    }

    /** Bounded immutable presentation state; never publishes or changes undo history. */
    fun previewSnapshot(): PixelTileSurfaceSnapshot {
        requireActive()
        cachedPreview?.takeIf { previewVersion == mutationVersion }?.let { return it }
        return surface.preview(this).also { preview ->
            cachedPreview = preview
            previewVersion = mutationVersion
        }
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
        require(surface.pixelFormat == PixelTileFormat.PREMULTIPLIED_SRGB_8888) { "Int tile editing requires an RGBA8 surface" }
        editTileBuffer(key, dirtyLocalRect) { buffer ->
            block((buffer as Argb8888TileBuffer).values, surface.tileSize)
        }
    }

    fun editTileF16(
        key: PixelTileKey,
        dirtyLocalRect: PixelRect = surface.fullTileRect(),
        block: (pixels: LongArray, rowStride: Int) -> Unit,
    ) {
        requireActive()
        require(surface.pixelFormat == PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16) {
            "F16 tile editing requires a linear F16 surface"
        }
        editTileBuffer(key, dirtyLocalRect) { buffer ->
            block((buffer as RgbaF16TileBuffer).values, surface.tileSize)
        }
    }

    private fun editTileBuffer(
        key: PixelTileKey,
        dirtyLocalRect: PixelRect,
        block: (PixelTileBuffer) -> Unit,
    ) {
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
        block(tile.pixels)
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
        cachedPreview = null
    }

    internal fun markCancelled() {
        state = State.CANCELLED
        workingTiles.clear()
        cachedPreview = null
    }

    private fun writableTile(key: PixelTileKey): WorkingPixelTile = workingTiles.getOrPut(key) {
        val before = surface.publishedTile(key)
        WorkingPixelTile(
            before = before,
            pixels = before?.pixels?.copyBuffer() ?: emptyPixelTileBuffer(surface.pixelFormat, surface.tileSize * surface.tileSize),
        )
    }

    private fun markDirty(tile: WorkingPixelTile, rect: PixelRect) {
        tile.dirtyRect = tile.dirtyRect?.union(rect) ?: rect
        tile.mutationVersion++
        tile.cachedPreviewGeneration = null
        mutationVersion++
        cachedPreview = null
    }

    private fun requireActive() {
        check(state == State.ACTIVE) { "Write transaction is $state" }
    }

    private enum class State { ACTIVE, COMMITTED, CANCELLED }
}

/** Native Android bulk copy; the JVM reference preserves the identical scanline contract. */
private fun copyPixelRows(
    source: IntArray, destination: IntArray, sourceOffset: Int, destinationOffset: Int,
    width: Int, height: Int, sourceStride: Int, destinationStride: Int,
) {
    if (NativeRasterOps.copyPixelRows(source, destination, sourceOffset, destinationOffset,
            width, height, sourceStride, destinationStride)) return
    for (row in 0 until height) {
        val start = sourceOffset + row * sourceStride
        source.copyInto(destination, destinationOffset + row * destinationStride, start, start + width)
    }
}
