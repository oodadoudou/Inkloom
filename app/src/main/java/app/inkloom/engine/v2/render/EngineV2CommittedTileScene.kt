package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokeTileIndex
import app.inkloom.document.StrokeTileKey

/**
 * Engine V2's stable, disposable raster scene. Canonical strokes remain the source of truth;
 * this object owns every completed-ink tile and the spatial index used by local edits.
 */
internal class EngineV2CommittedTileScene(
    private val maximumCachePixels: Long,
) : AutoCloseable {
    private val index = StrokeTileIndex()
    private val tiles = mutableMapOf<StrokeTileKey, Bitmap>()
    private val dirtyTiles = linkedSetOf<StrokeTileKey>()

    var onTileRendered: ((StrokeTileKey, Int) -> Unit)? = null

    val widthPx: Int get() = index.widthPx
    val heightPx: Int get() = index.heightPx

    fun draw(
        canvas: Canvas,
        strokes: List<InkStroke>,
        width: Int,
        height: Int,
        render: (Canvas, List<InkStroke>) -> Unit,
    ): Boolean {
        if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > maximumCachePixels) return false
        if (index.widthPx != width || index.heightPx != height) reset(strokes, width, height)
        val keys = index.allTiles()
        for (key in keys) {
            val rect = index.rect(key)
            var tile = tiles[key]
            if (tile == null || tile.width != rect.width || tile.height != rect.height) {
                tile?.recycle()
                tile = runCatching {
                    Bitmap.createBitmap(rect.width, rect.height, Bitmap.Config.ARGB_8888)
                }.getOrNull()
                if (tile == null) {
                    close()
                    return false
                }
                tiles[key] = tile
                dirtyTiles += key
            }
        }
        for (key in keys) {
            val rect = index.rect(key)
            val tile = tiles.getValue(key)
            if (dirtyTiles.remove(key)) {
                val candidateIds = index.candidates(key)
                val candidates = strokes.filter { it.id in candidateIds }
                renderTileWithGutter(tile, rect, candidates, render)
                onTileRendered?.invoke(key, candidates.size)
            }
            canvas.drawBitmap(tile, rect.left.toFloat(), rect.top.toFloat(), null)
        }
        return true
    }

    fun reset(strokes: List<InkStroke>, width: Int, height: Int) {
        recycleTiles()
        dirtyTiles += index.rebuild(strokes, width, height)
    }

    fun invalidate(
        before: Collection<InkStroke>,
        after: Collection<InkStroke>,
        allStrokes: List<InkStroke>,
        width: Int,
        height: Int,
        appendCompatible: Boolean,
        render: (Canvas, List<InkStroke>) -> Unit,
    ) {
        if (index.widthPx != width || index.heightPx != height) {
            reset(allStrokes, width, height)
            return
        }
        val affectedTiles = index.update(before, after)
        val canAppend = appendCompatible && before.isEmpty() && after.isNotEmpty() &&
            affectedTiles.isNotEmpty() && affectedTiles.none { it in dirtyTiles } &&
            affectedTiles.all(tiles::containsKey)
        if (!canAppend) {
            dirtyTiles += affectedTiles
            return
        }
        affectedTiles.forEach { key ->
            val rect = index.rect(key)
            val tileCanvas = Canvas(tiles.getValue(key))
            tileCanvas.translate(-rect.left.toFloat(), -rect.top.toFloat())
            render(tileCanvas, after.filter { key in index.tilesFor(it) })
            dirtyTiles.remove(key)
        }
    }

    fun candidates(leftPx: Float, topPx: Float, rightPx: Float, bottomPx: Float): Set<String> =
        index.candidates(leftPx, topPx, rightPx, bottomPx)

    fun ensureIndex(strokes: List<InkStroke>, width: Int, height: Int) {
        if (index.widthPx != width || index.heightPx != height) index.rebuild(strokes, width, height)
    }

    internal data class DebugSnapshot(
        val tileCount: Int,
        val dirtyTileCount: Int,
        val tiles: List<Bitmap>,
    )

    internal fun debugSnapshot(): DebugSnapshot = DebugSnapshot(tiles.size, dirtyTiles.size, tiles.values.toList())

    /**
     * Blur, soft tips, rotated stamps and spray need pixels outside a tile while calculating the
     * pixels inside it. Render dirty content into an ephemeral expanded bitmap and crop only the
     * tile core into the persistent cache. The cache therefore stays bounded while seams vanish.
     */
    private fun renderTileWithGutter(
        tile: Bitmap,
        rect: app.inkloom.document.StrokeTileRect,
        candidates: List<InkStroke>,
        render: (Canvas, List<InkStroke>) -> Unit,
    ) {
        tile.eraseColor(Color.TRANSPARENT)
        if (candidates.isEmpty()) return
        val requestedGutter = candidates.maxOfOrNull(index::influenceRadiusPixels)?.let { kotlin.math.ceil(it).toInt() } ?: 0
        val leftGutter = requestedGutter.coerceAtMost(rect.left)
        val topGutter = requestedGutter.coerceAtMost(rect.top)
        val rightGutter = requestedGutter.coerceAtMost(index.widthPx - rect.right)
        val bottomGutter = requestedGutter.coerceAtMost(index.heightPx - rect.bottom)
        if (leftGutter == 0 && topGutter == 0 && rightGutter == 0 && bottomGutter == 0) {
            Canvas(tile).also { it.translate(-rect.left.toFloat(), -rect.top.toFloat()); render(it, candidates) }
            return
        }
        val scratch = runCatching {
            Bitmap.createBitmap(
                rect.width + leftGutter + rightGutter,
                rect.height + topGutter + bottomGutter,
                Bitmap.Config.ARGB_8888,
            )
        }.getOrNull()
        if (scratch == null) {
            Canvas(tile).also { it.translate(-rect.left.toFloat(), -rect.top.toFloat()); render(it, candidates) }
            return
        }
        try {
            val scratchCanvas = Canvas(scratch)
            scratchCanvas.translate(-(rect.left - leftGutter).toFloat(), -(rect.top - topGutter).toFloat())
            render(scratchCanvas, candidates)
            Canvas(tile).drawBitmap(
                scratch,
                Rect(leftGutter, topGutter, leftGutter + rect.width, topGutter + rect.height),
                Rect(0, 0, rect.width, rect.height),
                null,
            )
        } finally {
            scratch.recycle()
        }
    }

    private fun recycleTiles() {
        tiles.values.forEach(Bitmap::recycle)
        tiles.clear()
        dirtyTiles.clear()
    }

    override fun close() {
        recycleTiles()
        index.rebuild(emptyList(), 0, 0)
    }
}
