package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

data class SelectionAffineTransform(
    val centerX: Float,
    val centerY: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
) {
    init {
        require(centerX.isFinite() && centerY.isFinite())
        require(scaleX.isFinite() && scaleX != 0f && scaleY.isFinite() && scaleY != 0f)
        require(rotationDegrees.isFinite() && translateX.isFinite() && translateY.isFinite())
    }
}

data class RasterSelectionTransformResult(
    val selection: MaskTileSurfaceSnapshot,
    val dirtyBounds: PixelRect,
    val transactionRecord: PixelTileTransactionRecord? = null,
)

/**
 * One interactive free-transform over sparse V5 pixels and sparse selection alpha.
 *
 * Every preview is rebuilt from the immutable gesture-start generations. Previous previews are
 * undone by generation swap, so repeated MOVE packets never resample already transformed pixels.
 * The implementation walks destination tiles and samples selection alpha directly; it never owns
 * or creates a logical-page selection bitmap.
 */
class RasterSelectionTransformSession(
    private val surface: PixelTileSurface,
    private val originalSelection: MaskTileSurfaceSnapshot,
) : AutoCloseable {
    private val originalPixels: PixelTileSurfaceSnapshot = surface.snapshot()
    private val sourceBounds = requireNotNull(originalSelection.nonZeroBounds()) { "Selection is empty" }
    private var previewRecord: PixelTileTransactionRecord? = null
    private var previewDirty: PixelRect? = null
    private var previewSelection: MaskTileSurfaceSnapshot = originalSelection
    private var closed = false

    init {
        require(surface.logicalWidth == originalSelection.logicalWidth && surface.logicalHeight == originalSelection.logicalHeight)
    }

    fun update(transform: SelectionAffineTransform): RasterSelectionTransformResult {
        check(!closed) { "Transform session is closed" }
        previewRecord?.let { record ->
            surface.apply(record, PixelTileSurface.HistoryDirection.UNDO)
            record.close()
        }
        previewRecord = null

        val transformedBounds = transformedBounds(sourceBounds, transform, surface.logicalWidth, surface.logicalHeight)
        val dirty = sourceBounds.union(transformedBounds)
        val sourceKeys = sourceTileKeys(originalSelection)
        val destinationKeys = transformedTileKeys(sourceKeys, originalSelection.tileSize, transform, surface.logicalWidth, surface.logicalHeight)
        val affectedKeys = sourceKeys + destinationKeys
        val transaction = surface.beginWrite("transform sparse selection")
        try {
            // An empty paint layer has no selected pixels to clear or move. Keep an empty
            // transaction for the unified undo contract, but do not scan every sparse mask tile.
            if (originalPixels.tileCount > 0) affectedKeys.forEach { key ->
                val tileLeft = key.column * surface.tileSize
                val tileTop = key.row * surface.tileSize
                val tileBounds = PixelRect(
                    tileLeft,
                    tileTop,
                    minOf(tileLeft + surface.tileSize, surface.logicalWidth),
                    minOf(tileTop + surface.tileSize, surface.logicalHeight),
                )
                val localDirty = PixelRect(
                    tileBounds.left - tileLeft,
                    tileBounds.top - tileTop,
                    tileBounds.right - tileLeft,
                    tileBounds.bottom - tileTop,
                )
                transaction.editTile(key, localDirty) { pixels, stride ->
                    for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                        val original = originalPixels.pixelAt(x, y)
                        val retained = scalePremultiplied(original, 255 - originalSelection.alphaAt(x, y))
                        val source = inversePoint(x + .5f, y + .5f, transform)
                        val sx = floor(source.first).toInt()
                        val sy = floor(source.second).toInt()
                        val moved = if (sx in 0 until surface.logicalWidth && sy in 0 until surface.logicalHeight) {
                            scalePremultiplied(originalPixels.pixelAt(sx, sy), originalSelection.alphaAt(sx, sy))
                        } else 0
                        pixels[(y - tileTop) * stride + x - tileLeft] = sourceOver(moved, retained)
                    }
                }
            }
            previewRecord = transaction.commit()
        } catch (failure: Throwable) {
            transaction.close()
            throw failure
        }
        previewDirty = dirty
        previewSelection = integerTranslation(transform)?.let { (dx, dy) ->
            transformMaskTranslation(originalSelection, sourceKeys, dx, dy)
        } ?: transformMask(originalSelection, destinationKeys, transform)
        return RasterSelectionTransformResult(previewSelection, dirty)
    }

    fun commit(): RasterSelectionTransformResult? {
        check(!closed) { "Transform session is closed" }
        val dirty = previewDirty ?: return null
        val record = previewRecord ?: return null
        previewRecord = null
        closed = true
        return RasterSelectionTransformResult(previewSelection, dirty, record)
    }

    fun cancel() {
        if (closed) return
        previewRecord?.let { record ->
            surface.apply(record, PixelTileSurface.HistoryDirection.UNDO)
            record.close()
        }
        previewRecord = null
        closed = true
    }

    override fun close() = cancel()

    private fun transformMask(
        source: MaskTileSurfaceSnapshot,
        destinationKeys: Set<PixelTileKey>,
        transform: SelectionAffineTransform,
    ): MaskTileSurfaceSnapshot {
        val destination = MaskTileSurface(source.logicalWidth, source.logicalHeight, source.tileSize)
        if (destinationKeys.isEmpty()) return destination.snapshot()
        val transaction = destination.beginWrite("transform sparse selection alpha")
        try {
            destinationKeys.forEach { key ->
                val tileLeft = key.column * source.tileSize
                val tileTop = key.row * source.tileSize
                val tileBounds = PixelRect(
                    tileLeft,
                    tileTop,
                    minOf(tileLeft + source.tileSize, source.logicalWidth),
                    minOf(tileTop + source.tileSize, source.logicalHeight),
                )
                val alpha = ByteArray(tileBounds.width * tileBounds.height)
                var nonZero = false
                for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                    val sourcePoint = inversePoint(x + .5f, y + .5f, transform)
                    val sx = floor(sourcePoint.first).toInt()
                    val sy = floor(sourcePoint.second).toInt()
                    val value = if (sx in 0 until source.logicalWidth && sy in 0 until source.logicalHeight) source.alphaAt(sx, sy) else 0
                    alpha[(y - tileBounds.top) * tileBounds.width + x - tileBounds.left] = value.toByte()
                    nonZero = nonZero || value != 0
                }
                if (nonZero) transaction.replaceAlpha(tileBounds, alpha)
            }
            transaction.commit().close()
        } catch (failure: Throwable) {
            transaction.close()
            throw failure
        }
        return destination.snapshot()
    }

    private fun transformMaskTranslation(
        source: MaskTileSurfaceSnapshot,
        sourceKeys: Set<PixelTileKey>,
        dx: Int,
        dy: Int,
    ): MaskTileSurfaceSnapshot {
        val destination = MaskTileSurface(source.logicalWidth, source.logicalHeight, source.tileSize)
        if (sourceKeys.isEmpty()) return destination.snapshot()
        val transaction = destination.beginWrite("translate sparse selection alpha")
        try {
            sourceKeys.forEach { key ->
                val sourceBounds = PixelRect(
                    key.column * source.tileSize,
                    key.row * source.tileSize,
                    minOf((key.column + 1) * source.tileSize, source.logicalWidth),
                    minOf((key.row + 1) * source.tileSize, source.logicalHeight),
                )
                val shifted = PixelRect(
                    sourceBounds.left + dx,
                    sourceBounds.top + dy,
                    sourceBounds.right + dx,
                    sourceBounds.bottom + dy,
                )
                val clipped = shifted.intersect(PixelRect(0, 0, source.logicalWidth, source.logicalHeight))
                if (clipped.isEmpty) return@forEach
                val alpha = ByteArray(sourceBounds.width * sourceBounds.height)
                source.copyAlpha(sourceBounds, alpha, destinationOffset = 0, destinationRowStride = sourceBounds.width)
                val sourceLeft = clipped.left - shifted.left
                val sourceTop = clipped.top - shifted.top
                val clippedAlpha = if (clipped.width == sourceBounds.width && clipped.height == sourceBounds.height) {
                    alpha
                } else ByteArray(clipped.width * clipped.height).also { cropped ->
                    for (row in 0 until clipped.height) {
                        alpha.copyInto(
                            cropped,
                            destinationOffset = row * clipped.width,
                            startIndex = (sourceTop + row) * sourceBounds.width + sourceLeft,
                            endIndex = (sourceTop + row) * sourceBounds.width + sourceLeft + clipped.width,
                        )
                    }
                }
                transaction.composeAlpha(
                    clipped,
                    clippedAlpha,
                    mode = app.inkloom.engine.v5.surface.MaskComposeMode.ADD,
                )
            }
            transaction.commit().close()
        } catch (failure: Throwable) {
            transaction.close()
            throw failure
        }
        return destination.snapshot()
    }

    private fun integerTranslation(transform: SelectionAffineTransform): Pair<Int, Int>? {
        if (transform.scaleX != 1f || transform.scaleY != 1f || transform.rotationDegrees.mod(360f) != 0f) return null
        val dx = transform.translateX.toInt()
        val dy = transform.translateY.toInt()
        return (dx to dy).takeIf {
            kotlin.math.abs(transform.translateX - dx) < .0001f &&
                kotlin.math.abs(transform.translateY - dy) < .0001f
        }
    }

    private fun sourceTileKeys(selection: MaskTileSurfaceSnapshot): Set<PixelTileKey> {
        if (selection.defaultAlpha == 0) return selection.tileInfo.asSequence()
            .filter { it.nonZeroAlphaCount > 0 }
            .mapTo(linkedSetOf()) { it.key }
        val columns = (selection.logicalWidth + selection.tileSize - 1) / selection.tileSize
        val rows = (selection.logicalHeight + selection.tileSize - 1) / selection.tileSize
        return buildSet(columns * rows) {
            for (row in 0 until rows) for (column in 0 until columns) add(PixelTileKey(column, row))
        }
    }

    private fun transformedTileKeys(
        sourceKeys: Set<PixelTileKey>,
        tileSize: Int,
        transform: SelectionAffineTransform,
        width: Int,
        height: Int,
    ): Set<PixelTileKey> = buildSet {
        sourceKeys.forEach { key ->
            val sourceTile = PixelRect(
                key.column * tileSize,
                key.row * tileSize,
                minOf((key.column + 1) * tileSize, width),
                minOf((key.row + 1) * tileSize, height),
            )
            val bounds = transformedBounds(sourceTile, transform, width, height)
            if (bounds.isEmpty) return@forEach
            for (row in bounds.top / tileSize..(bounds.bottom - 1) / tileSize) {
                for (column in bounds.left / tileSize..(bounds.right - 1) / tileSize) add(PixelTileKey(column, row))
            }
        }
    }

    companion object {
        internal fun transformedBounds(bounds: PixelRect, transform: SelectionAffineTransform, width: Int, height: Int): PixelRect {
            val corners = arrayOf(
                bounds.left.toFloat() to bounds.top.toFloat(),
                bounds.right.toFloat() to bounds.top.toFloat(),
                bounds.right.toFloat() to bounds.bottom.toFloat(),
                bounds.left.toFloat() to bounds.bottom.toFloat(),
            ).map { forwardPoint(it.first, it.second, transform) }
            return PixelRect(
                floor(corners.minOf { it.first }).toInt().coerceIn(0, width),
                floor(corners.minOf { it.second }).toInt().coerceIn(0, height),
                ceil(corners.maxOf { it.first }).toInt().coerceIn(0, width),
                ceil(corners.maxOf { it.second }).toInt().coerceIn(0, height),
            )
        }

        private fun forwardPoint(x: Float, y: Float, transform: SelectionAffineTransform): Pair<Float, Float> {
            val radians = Math.toRadians(transform.rotationDegrees.toDouble())
            val cos = cos(radians).toFloat()
            val sin = sin(radians).toFloat()
            val dx = (x - transform.centerX) * transform.scaleX
            val dy = (y - transform.centerY) * transform.scaleY
            return transform.centerX + transform.translateX + dx * cos - dy * sin to
                transform.centerY + transform.translateY + dx * sin + dy * cos
        }

        private fun inversePoint(x: Float, y: Float, transform: SelectionAffineTransform): Pair<Float, Float> {
            val radians = Math.toRadians(-transform.rotationDegrees.toDouble())
            val cos = cos(radians).toFloat()
            val sin = sin(radians).toFloat()
            val dx = x - transform.centerX - transform.translateX
            val dy = y - transform.centerY - transform.translateY
            return transform.centerX + (dx * cos - dy * sin) / transform.scaleX to
                transform.centerY + (dx * sin + dy * cos) / transform.scaleY
        }

        private fun scalePremultiplied(color: Int, alpha: Int): Int {
            if (alpha <= 0 || color == 0) return 0
            if (alpha >= 255) return color
            fun scaled(channel: Int): Int = (channel * alpha + 127) / 255
            return (scaled(color ushr 24 and 0xff) shl 24) or
                (scaled(color ushr 16 and 0xff) shl 16) or
                (scaled(color ushr 8 and 0xff) shl 8) or scaled(color and 0xff)
        }

        private fun sourceOver(source: Int, destination: Int): Int {
            val sourceAlpha = source ushr 24 and 0xff
            if (sourceAlpha == 0) return destination
            if (sourceAlpha == 255) return source
            val inverse = 255 - sourceAlpha
            fun composed(shift: Int): Int = ((source ushr shift and 0xff) + ((destination ushr shift and 0xff) * inverse + 127) / 255).coerceAtMost(255)
            return (composed(24) shl 24) or (composed(16) shl 16) or (composed(8) shl 8) or composed(0)
        }
    }
}
