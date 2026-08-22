package app.inkloom.engine.v3.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderNode
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokeTileIndex
import app.inkloom.document.StrokeTileKey
import app.inkloom.document.StrokeTileRect
import app.inkloom.engine.v3.android.PixelMemoryPressure
import app.inkloom.engine.v3.android.PixelMemoryPressureCoordinator
import app.inkloom.engine.v3.android.PixelMemoryTrimResult
import app.inkloom.engine.v3.android.PixelMemoryTrimmable

/**
 * Sparse retained scene for committed vector ink. Hardware canvases draw independently recorded
 * [RenderNode] tiles, so production rendering is rasterized and composited by RenderThread rather
 * than retained in page-sized software bitmaps. A bounded software cache remains only for export,
 * deterministic bitmap tests, and other explicitly software Canvas consumers.
 */
internal class SparseTileScene(
    private val maximumCachePixels: Long,
) : AutoCloseable, PixelMemoryTrimmable {
    private val index = StrokeTileIndex()
    private val softwareTiles = mutableMapOf<StrokeTileKey, Bitmap>()
    private val hardwareTiles = mutableMapOf<StrokeTileKey, RenderNode>()
    private val hardwareStrokeNodes = mutableMapOf<String, RenderNode>()
    private val dirtySoftwareTiles = linkedSetOf<StrokeTileKey>()
    private val dirtyHardwareTiles = linkedSetOf<StrokeTileKey>()
    private var hardwareIndependentStrokes: Boolean? = null
    private var generationCounter = 0L
    private val memoryRegistration = PixelMemoryPressureCoordinator.register(this)

    override val memoryTrimPriority: Int get() = 10

    var onTileRendered: ((StrokeTileKey, Int) -> Unit)? = null

    val widthPx: Int get() = index.widthPx
    val heightPx: Int get() = index.heightPx

    /** Monotonic projection generation: incremented every time a tile is re-rendered or the scene is rebuilt. */
    val generation: Long get() = generationCounter

    /** Tiles whose retained contents are stale and will be re-rendered on the next draw. */
    val dirtyTileCount: Int get() = dirtySoftwareTiles.size + dirtyHardwareTiles.size

    fun draw(
        canvas: Canvas,
        strokes: List<InkStroke>,
        width: Int,
        height: Int,
        independentStrokes: Boolean = true,
        render: (Canvas, List<InkStroke>) -> Unit,
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        if (index.widthPx != width || index.heightPx != height) reset(strokes, width, height)
        val keys = index.occupiedTiles()
        val retainedPixels = keys.sumOf { key -> index.rect(key).let { it.width.toLong() * it.height } }
        if (retainedPixels > maximumCachePixels) return false
        return if (canvas.isHardwareAccelerated) {
            drawHardware(canvas, strokes, keys, independentStrokes, render)
        } else {
            drawSoftware(canvas, strokes, keys, render)
        }
    }

    private fun drawHardware(
        canvas: Canvas,
        strokes: List<InkStroke>,
        keys: Set<StrokeTileKey>,
        independentStrokes: Boolean,
        render: (Canvas, List<InkStroke>) -> Unit,
    ): Boolean {
        if (hardwareIndependentStrokes != independentStrokes) {
            hardwareStrokeNodes.values.forEach(RenderNode::discardDisplayList)
            hardwareStrokeNodes.clear()
            dirtyHardwareTiles += keys
            hardwareIndependentStrokes = independentStrokes
        }
        val stale = hardwareTiles.keys - keys
        stale.forEach { key -> hardwareTiles.remove(key)?.discardDisplayList() }
        dirtyHardwareTiles.retainAll(keys)
        keys.forEach { key ->
            val rect = index.rect(key)
            val node = hardwareTiles.getOrPut(key) {
                RenderNode("Inkloom.V3.Tile.${key.column}.${key.row}").also {
                    it.setPosition(rect.left, rect.top, rect.right, rect.bottom)
                    dirtyHardwareTiles += key
                }
            }
            if (node.width != rect.width || node.height != rect.height) {
                node.setPosition(rect.left, rect.top, rect.right, rect.bottom)
                dirtyHardwareTiles += key
            }
            if (key in dirtyHardwareTiles || !node.hasDisplayList()) {
                generationCounter++
                val candidates = candidatesFor(key, strokes)
                val recording = node.beginRecording(rect.width, rect.height)
                try {
                    recording.translate(-rect.left.toFloat(), -rect.top.toFloat())
                    if (independentStrokes) {
                        candidates.forEach { stroke ->
                            recording.drawRenderNode(hardwareStrokeNode(stroke, render))
                        }
                    } else {
                        render(recording, candidates)
                    }
                } finally {
                    node.endRecording()
                }
                dirtyHardwareTiles.remove(key)
                // Re-recording a tile root only changes child references. Brush display lists are
                // immutable per stroke and are not replayed on the UI thread.
                onTileRendered?.invoke(key, if (independentStrokes) 0 else candidates.size)
            }
            canvas.drawRenderNode(node)
        }
        return true
    }

    private fun drawSoftware(
        canvas: Canvas,
        strokes: List<InkStroke>,
        keys: Set<StrokeTileKey>,
        render: (Canvas, List<InkStroke>) -> Unit,
    ): Boolean {
        val stale = softwareTiles.keys - keys
        stale.forEach { key -> softwareTiles.remove(key)?.recycle() }
        dirtySoftwareTiles.retainAll(keys)
        for (key in keys) {
            val rect = index.rect(key)
            var tile = softwareTiles[key]
            if (tile == null || tile.width != rect.width || tile.height != rect.height) {
                tile?.recycle()
                tile = runCatching {
                    Bitmap.createBitmap(rect.width, rect.height, Bitmap.Config.ARGB_8888)
                }.getOrNull()
                if (tile == null) {
                    close()
                    return false
                }
                softwareTiles[key] = tile
                dirtySoftwareTiles += key
            }
        }
        for (key in keys) {
            val rect = index.rect(key)
            val tile = softwareTiles.getValue(key)
            if (dirtySoftwareTiles.remove(key)) {
                generationCounter++
                val candidates = candidatesFor(key, strokes)
                renderTileWithGutter(tile, rect, candidates, render)
                onTileRendered?.invoke(key, candidates.size)
            }
            canvas.drawBitmap(tile, rect.left.toFloat(), rect.top.toFloat(), null)
        }
        return true
    }

    fun reset(strokes: List<InkStroke>, width: Int, height: Int) {
        recycleTiles()
        index.rebuild(strokes, width, height)
        generationCounter++
        dirtySoftwareTiles += index.occupiedTiles()
        dirtyHardwareTiles += index.occupiedTiles()
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
        before.forEach { stroke -> hardwareStrokeNodes.remove(stroke.id)?.discardDisplayList() }
        after.forEach { stroke ->
            hardwareStrokeNodes.remove(stroke.id)?.discardDisplayList()
        }
        dirtyHardwareTiles += affectedTiles
        val canAppendSoftware = appendCompatible && before.isEmpty() && after.isNotEmpty() &&
            affectedTiles.isNotEmpty() && affectedTiles.none { it in dirtySoftwareTiles } &&
            affectedTiles.all(softwareTiles::containsKey)
        if (!canAppendSoftware) {
            dirtySoftwareTiles += affectedTiles
            return
        }
        affectedTiles.filter { it in index.occupiedTiles() }.forEach { key ->
            val rect = index.rect(key)
            val tileCanvas = Canvas(softwareTiles.getValue(key))
            tileCanvas.translate(-rect.left.toFloat(), -rect.top.toFloat())
            generationCounter++
            render(tileCanvas, after.filter { key in index.tilesFor(it) })
            dirtySoftwareTiles.remove(key)
        }
    }

    fun candidates(leftPx: Float, topPx: Float, rightPx: Float, bottomPx: Float): Set<String> =
        index.candidates(leftPx, topPx, rightPx, bottomPx)

    fun ensureIndex(strokes: List<InkStroke>, width: Int, height: Int) {
        if (index.widthPx != width || index.heightPx != height) reset(strokes, width, height)
    }

    fun pixelBoundsFor(stroke: InkStroke, width: Int, height: Int): StrokeTileRect? {
        if (index.widthPx != width || index.heightPx != height) index.rebuild(emptyList(), width, height)
        return index.pixelBoundsFor(stroke)
    }

    internal data class DebugSnapshot(
        val tileCount: Int,
        val dirtyTileCount: Int,
        val tiles: List<Bitmap>,
        val hardwareTileCount: Int,
        val hardwareStrokeCount: Int,
        val approximateGpuDisplayListBytes: Long,
    )

    internal fun debugSnapshot(): DebugSnapshot = DebugSnapshot(
        tileCount = maxOf(softwareTiles.size, hardwareTiles.size),
        dirtyTileCount = (dirtySoftwareTiles + dirtyHardwareTiles).size,
        tiles = softwareTiles.values.toList(),
        hardwareTileCount = hardwareTiles.size,
        hardwareStrokeCount = hardwareStrokeNodes.size,
        approximateGpuDisplayListBytes =
            hardwareTiles.values.sumOf(RenderNode::computeApproximateMemoryUsage) +
                hardwareStrokeNodes.values.sumOf(RenderNode::computeApproximateMemoryUsage),
    )

    private fun candidatesFor(key: StrokeTileKey, strokes: List<InkStroke>): List<InkStroke> {
        val candidateIds = index.candidates(key)
        return strokes.filter { it.id in candidateIds }
    }

    private fun hardwareStrokeNode(
        stroke: InkStroke,
        render: (Canvas, List<InkStroke>) -> Unit,
    ): RenderNode = hardwareStrokeNodes.getOrPut(stroke.id) {
        recordStrokeNode(stroke, index.widthPx, index.heightPx, render)
    }

    private fun recordStrokeNode(
        stroke: InkStroke,
        width: Int,
        height: Int,
        render: (Canvas, List<InkStroke>) -> Unit,
    ): RenderNode = RenderNode("Inkloom.V3.Stroke.${stroke.id}").also { node ->
        node.setPosition(0, 0, width, height)
        val recording = node.beginRecording(width, height)
        try {
            render(recording, listOf(stroke))
        } finally {
            node.endRecording()
        }
    }

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
        softwareTiles.values.forEach(Bitmap::recycle)
        softwareTiles.clear()
        hardwareTiles.values.forEach(RenderNode::discardDisplayList)
        hardwareTiles.clear()
        hardwareStrokeNodes.values.forEach(RenderNode::discardDisplayList)
        hardwareStrokeNodes.clear()
        hardwareIndependentStrokes = null
        dirtySoftwareTiles.clear()
        dirtyHardwareTiles.clear()
    }

    override fun trimPixelMemory(pressure: PixelMemoryPressure): PixelMemoryTrimResult {
        val beforeCpu = softwareTiles.values.sumOf { it.allocationByteCount.toLong() }
        val beforeGpu =
            hardwareTiles.values.sumOf(RenderNode::computeApproximateMemoryUsage) +
                hardwareStrokeNodes.values.sumOf(RenderNode::computeApproximateMemoryUsage)
        val occupied = index.occupiedTiles()
        hardwareTiles.values.forEach(RenderNode::discardDisplayList)
        hardwareTiles.clear()
        hardwareStrokeNodes.values.forEach(RenderNode::discardDisplayList)
        hardwareStrokeNodes.clear()
        hardwareIndependentStrokes = null
        dirtyHardwareTiles.clear()
        dirtyHardwareTiles += occupied
        if (pressure != PixelMemoryPressure.UI_HIDDEN) {
            softwareTiles.values.forEach(Bitmap::recycle)
            softwareTiles.clear()
            dirtySoftwareTiles.clear()
            dirtySoftwareTiles += occupied
        }
        val afterCpu = softwareTiles.values.sumOf { it.allocationByteCount.toLong() }
        return PixelMemoryTrimResult(
            releasedCpuBytes = (beforeCpu - afterCpu).coerceAtLeast(0L),
            releasedGpuBytes = beforeGpu.coerceAtLeast(0L),
        )
    }

    override fun close() {
        memoryRegistration.close()
        recycleTiles()
        index.rebuild(emptyList(), 0, 0)
    }
}
