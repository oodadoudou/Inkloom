package app.inkloom.document

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

data class StrokeTileKey(val column: Int, val row: Int)

data class StrokeTileRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Spatial index for the completed-ink cache. Bounds are deliberately conservative: extra tiles may
 * be redrawn, but pixels from a moved, erased, or replaced stroke must never survive in a stale tile.
 */
class StrokeTileIndex(val tileSizePx: Int = DEFAULT_TILE_SIZE_PX) {
    private val strokeTiles = mutableMapOf<String, Set<StrokeTileKey>>()
    private val tileStrokeIds = mutableMapOf<StrokeTileKey, MutableSet<String>>()

    var widthPx: Int = 0
        private set
    var heightPx: Int = 0
        private set

    val columnCount: Int get() = if (widthPx <= 0) 0 else ceil(widthPx / tileSizePx.toDouble()).toInt()
    val rowCount: Int get() = if (heightPx <= 0) 0 else ceil(heightPx / tileSizePx.toDouble()).toInt()

    init { require(tileSizePx > 0) }

    fun rebuild(strokes: List<InkStroke>, widthPx: Int, heightPx: Int): Set<StrokeTileKey> {
        this.widthPx = widthPx.coerceAtLeast(0)
        this.heightPx = heightPx.coerceAtLeast(0)
        strokeTiles.clear()
        tileStrokeIds.clear()
        strokes.forEach(::add)
        return allTiles()
    }

    fun update(before: Collection<InkStroke>, after: Collection<InkStroke>): Set<StrokeTileKey> {
        if (widthPx <= 0 || heightPx <= 0) return emptySet()
        val dirty = linkedSetOf<StrokeTileKey>()
        before.distinctBy(InkStroke::id).forEach { stroke ->
            val occupied = strokeTiles.remove(stroke.id) ?: tilesFor(stroke)
            dirty += occupied
            occupied.forEach { key ->
                tileStrokeIds[key]?.let { ids ->
                    ids.remove(stroke.id)
                    if (ids.isEmpty()) tileStrokeIds.remove(key)
                }
            }
        }
        after.distinctBy(InkStroke::id).forEach { stroke ->
            val occupied = tilesFor(stroke)
            strokeTiles[stroke.id] = occupied
            dirty += occupied
            occupied.forEach { key -> tileStrokeIds.getOrPut(key, ::linkedSetOf).add(stroke.id) }
        }
        return dirty
    }

    fun candidates(key: StrokeTileKey): Set<String> = tileStrokeIds[key].orEmpty()

    fun allTiles(): Set<StrokeTileKey> = buildSet {
        repeat(rowCount) { row -> repeat(columnCount) { column -> add(StrokeTileKey(column, row)) } }
    }

    fun rect(key: StrokeTileKey): StrokeTileRect {
        val left = key.column * tileSizePx
        val top = key.row * tileSizePx
        return StrokeTileRect(
            left = left,
            top = top,
            right = (left + tileSizePx).coerceAtMost(widthPx),
            bottom = (top + tileSizePx).coerceAtMost(heightPx),
        )
    }

    fun tilesFor(stroke: InkStroke): Set<StrokeTileKey> {
        if (stroke.points.isEmpty() || widthPx <= 0 || heightPx <= 0) return emptySet()
        val expansion = footprintRadiusPixels(stroke, widthPx.toFloat()) + ANTIALIAS_MARGIN_PX
        val left = (stroke.points.minOf(StrokePoint::x) * widthPx - expansion).coerceIn(0f, widthPx.toFloat())
        val top = (stroke.points.minOf(StrokePoint::y) * heightPx - expansion).coerceIn(0f, heightPx.toFloat())
        val right = (stroke.points.maxOf(StrokePoint::x) * widthPx + expansion).coerceIn(0f, widthPx.toFloat())
        val bottom = (stroke.points.maxOf(StrokePoint::y) * heightPx + expansion).coerceIn(0f, heightPx.toFloat())
        val firstColumn = floor(left / tileSizePx).toInt().coerceIn(0, (columnCount - 1).coerceAtLeast(0))
        val lastColumn = floor((right - .001f).coerceAtLeast(left) / tileSizePx).toInt().coerceIn(firstColumn, (columnCount - 1).coerceAtLeast(firstColumn))
        val firstRow = floor(top / tileSizePx).toInt().coerceIn(0, (rowCount - 1).coerceAtLeast(0))
        val lastRow = floor((bottom - .001f).coerceAtLeast(top) / tileSizePx).toInt().coerceIn(firstRow, (rowCount - 1).coerceAtLeast(firstRow))
        return buildSet {
            for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) add(StrokeTileKey(column, row))
        }
    }

    private fun add(stroke: InkStroke) {
        val occupied = tilesFor(stroke)
        strokeTiles[stroke.id] = occupied
        occupied.forEach { key -> tileStrokeIds.getOrPut(key, ::linkedSetOf).add(stroke.id) }
    }

    private fun footprintRadiusPixels(stroke: InkStroke, pageWidthPx: Float): Float {
        val referenceScale = pageWidthPx.coerceAtLeast(1f) / REFERENCE_PAGE_WIDTH
        val brushDiameter = stroke.width.coerceIn(.1f, MAX_BRUSH_WIDTH) * referenceScale
        val edge = if (stroke.waterEdgeEnabled) {
            WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, pageWidthPx) +
                WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, pageWidthPx) * BLUR_EXTENT_MULTIPLIER
        } else 0f
        val mainBlur = BrushBlur.radiusPixels(
            value = stroke.blur,
            unit = stroke.blurUnit,
            linkedToSize = stroke.blurLinkedToSize,
            currentDiameter = brushDiameter,
            baseDiameter = brushDiameter,
            pageWidth = pageWidthPx,
        ) * BLUR_EXTENT_MULTIPLIER
        val main = stampRadius(
            brushDiameter = brushDiameter,
            thickness = stroke.thickness,
            spray = stroke.spray,
            particleSize = stroke.sprayParticleSize,
            particleUnit = stroke.sprayParticleSizeUnit,
            particleSync = stroke.sprayParticleSizeSyncBrushSize,
            spread = stroke.spraySpread,
            pageWidthPx = pageWidthPx,
        ) + edge + mainBlur
        if (!stroke.dualBrush) return main
        val dualDiameter = brushDiameter * stroke.dualSizeScale.coerceIn(.01f, 8f)
        val dual = stampRadius(
            brushDiameter = dualDiameter,
            thickness = stroke.dualThickness,
            spray = stroke.dualSpray,
            particleSize = stroke.dualSprayParticleSize,
            particleUnit = stroke.dualSprayParticleSizeUnit,
            particleSync = stroke.dualSprayParticleSizeSyncBrushSize,
            spread = stroke.dualSpraySpread,
            pageWidthPx = pageWidthPx,
        ) + BrushBlur.radiusPixels(
            value = stroke.blur,
            unit = stroke.blurUnit,
            linkedToSize = stroke.blurLinkedToSize,
            currentDiameter = dualDiameter,
            baseDiameter = dualDiameter,
            pageWidth = pageWidthPx,
        ) * BLUR_EXTENT_MULTIPLIER
        return max(main, dual)
    }

    private fun stampRadius(
        brushDiameter: Float,
        thickness: Float,
        spray: Boolean,
        particleSize: Float,
        particleUnit: Int,
        particleSync: Boolean,
        spread: Float,
        pageWidthPx: Float,
    ): Float {
        val shapedHalfDiagonal = brushDiameter * max(1f, thickness.coerceIn(.05f, 2f)) * HALF_DIAGONAL_FACTOR
        if (!spray) return shapedHalfDiagonal
        val particleDiameter = SprayGeometry.particleDiameter(brushDiameter, particleSize, particleUnit, particleSync, pageWidthPx)
        val particleHalfDiagonal = particleDiameter * max(1f, thickness.coerceIn(.05f, 2f)) * HALF_DIAGONAL_FACTOR
        return brushDiameter / 2f * spread.coerceIn(0f, 4f) + particleHalfDiagonal
    }

    companion object {
        const val DEFAULT_TILE_SIZE_PX = 512
        private const val REFERENCE_PAGE_WIDTH = 1600f
        private const val MAX_BRUSH_WIDTH = 800f
        private const val ANTIALIAS_MARGIN_PX = 3f
        private const val BLUR_EXTENT_MULTIPLIER = 3f
        private val HALF_DIAGONAL_FACTOR = (sqrt(2f) / 2f)
    }
}
