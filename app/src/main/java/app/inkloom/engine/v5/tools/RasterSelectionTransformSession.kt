package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.RasterSelectionContent
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.roundToInt

data class SelectionAffineTransform(
    val centerX: Float,
    val centerY: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
) : SelectionRasterTransform {
    init {
        require(centerX.isFinite() && centerY.isFinite())
        require(scaleX.isFinite() && scaleX != 0f && scaleY.isFinite() && scaleY != 0f)
        require(rotationDegrees.isFinite() && translateX.isFinite() && translateY.isFinite())
    }

    override val isIdentity get() = scaleX == 1f && scaleY == 1f && rotationDegrees.mod(360f) == 0f && translateX == 0f && translateY == 0f

    override fun forward(x: Float, y: Float): Pair<Float, Float> {
        val cosine = cos(Math.toRadians(rotationDegrees.toDouble())).toFloat()
        val sine = sin(Math.toRadians(rotationDegrees.toDouble())).toFloat()
        val dx = (x-centerX)*scaleX; val dy = (y-centerY)*scaleY
        return centerX+translateX+dx*cosine-dy*sine to centerY+translateY+dx*sine+dy*cosine
    }
    override fun inverseCoefficients(): FloatArray {
        val cosine = cos(Math.toRadians(rotationDegrees.toDouble())).toFloat()
        val sine = sin(Math.toRadians(rotationDegrees.toDouble())).toFloat()
        val a=cosine/scaleX; val b=sine/scaleX; val c=-sine/scaleY; val d=cosine/scaleY
        return floatArrayOf(a,b,c,d,centerX-a*(centerX+translateX)-b*(centerY+translateY),
            centerY-c*(centerX+translateX)-d*(centerY+translateY))
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
    private val sourceBounds = requireNotNull(
        RasterSelectionContent.selectionBoundsInPixels(originalSelection, surface.logicalWidth, surface.logicalHeight),
    ) { "Selection is empty" }
    private val maskScaleX = originalSelection.logicalWidth.toFloat() / surface.logicalWidth
    private val maskScaleY = originalSelection.logicalHeight.toFloat() / surface.logicalHeight
    private val samplingSource = SelectionSamplerSource(originalPixels, originalSelection)
    private val retainedMaskSource = SelectionSamplerSource(originalPixels, originalSelection)
    private var previewRecord: PixelTileTransactionRecord? = null
    private var previewDirty: PixelRect? = null
    private var previewSelection: MaskTileSurfaceSnapshot = originalSelection
    private var closed = false


    fun update(transform: SelectionRasterTransform, interpolation: SelectionInterpolation = SelectionInterpolation.NEAREST): RasterSelectionTransformResult {
        check(!closed) { "Transform session is closed" }
        previewRecord?.let { record ->
            surface.apply(record, PixelTileSurface.HistoryDirection.UNDO)
            record.close()
        }
        previewRecord = null

        val affine = transform as? SelectionAffineTransform
        val identity = transform.isIdentity
        val sampler = SelectionPixelSampler(originalPixels, originalSelection, interpolation, samplingSource)
        val inverse = InverseSelectionMapping(transform)
        val halo = when (interpolation) { SelectionInterpolation.NEAREST -> 0; SelectionInterpolation.BILINEAR -> 1; SelectionInterpolation.BICUBIC -> 2 }
        val samplingBounds = PixelRect(sourceBounds.left - halo, sourceBounds.top - halo, sourceBounds.right + halo, sourceBounds.bottom + halo)
        val transformedBounds = transformedBounds(samplingBounds, transform, surface.logicalWidth, surface.logicalHeight)
        val dirty = sourceBounds.union(transformedBounds)
        val maskSourceKeys = sourceTileKeys(originalSelection)
        val sourceKeys = mapTileKeys(maskSourceKeys, originalSelection.tileSize,
            originalSelection.logicalWidth, originalSelection.logicalHeight,
            surface.tileSize, surface.logicalWidth, surface.logicalHeight)
        val destinationKeys = transformedTileKeys(sourceKeys, surface.tileSize, transform, surface.logicalWidth, surface.logicalHeight, halo)
        val occupiedSourceKeys = sourceKeys.intersect(originalPixels.tileKeys)
        val affectedKeys = occupiedSourceKeys + transformedTileKeys(occupiedSourceKeys, surface.tileSize, transform,
            surface.logicalWidth, surface.logicalHeight, halo)
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
                val nativeSource = if (identity) null else nativeSource(tileBounds, inverse, halo)
                val retainedAlpha = nativeSource?.let { rasterAlpha(tileBounds) }
                when (surface.pixelFormat) {
                    PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> transaction.editTile(key, localDirty) { pixels, stride ->
                        if (identity || (nativeSource != null && nativeTile(nativeSource, pixels, requireNotNull(retainedAlpha), tileBounds, stride, inverse, interpolation))) return@editTile
                        for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                            val original = pixels[(y - tileTop) * stride + x - tileLeft]
                            val value = if (identity) original else {
                                val retained = scalePremultiplied(original, 255 - retainedMaskSource.mask(((x + .5f) * maskScaleX).toInt(), ((y + .5f) * maskScaleY).toInt()))
                                sourceOver(sampler.selectedArgb(inverse.x(x + .5f, y + .5f), inverse.y(x + .5f, y + .5f)), retained)
                            }
                            pixels[(y - tileTop) * stride + x - tileLeft] = value
                        }
                    }
                    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> transaction.editTileF16(key, localDirty) { pixels, stride ->
                        if (identity || (nativeSource != null && nativeTile(nativeSource, pixels, requireNotNull(retainedAlpha), tileBounds, stride, inverse, interpolation))) return@editTileF16
                        for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                            val original = pixels[(y - tileTop) * stride + x - tileLeft]
                            val value = if (identity) original else {
                                val retained = SelectionPixelSampler.scaleF16(original, (255 - retainedMaskSource.mask(((x + .5f) * maskScaleX).toInt(), ((y + .5f) * maskScaleY).toInt())) / 255f)
                                SelectionPixelSampler.overF16(sampler.selectedF16(inverse.x(x + .5f, y + .5f), inverse.y(x + .5f, y + .5f)), retained)
                            }
                            pixels[(y - tileTop) * stride + x - tileLeft] = value
                        }
                    }
                }
            }
            previewRecord = transaction.commit()
        } catch (failure: Throwable) {
            transaction.close()
            throw failure
        }
        previewDirty = dirty
        val maskTranslation = affine?.copy(
            translateX = affine.translateX * maskScaleX,
            translateY = affine.translateY * maskScaleY,
        )
        val maskDestinationKeys = mapTileKeys(destinationKeys, surface.tileSize, surface.logicalWidth, surface.logicalHeight,
            originalSelection.tileSize, originalSelection.logicalWidth, originalSelection.logicalHeight)
        previewSelection = if (identity) originalSelection else maskTranslation?.let(::integerTranslation)?.let { (dx, dy) ->
            transformMaskTranslation(originalSelection, maskSourceKeys, dx, dy)
        } ?: if (maskTranslation != null && affine.scaleX == 1f && affine.scaleY == 1f && affine.rotationDegrees.mod(360f) == 0f) {
            if (interpolation == SelectionInterpolation.NEAREST) transformMaskTranslation(originalSelection, maskSourceKeys,
                ceil(maskTranslation.translateX - .5f).toInt(), ceil(maskTranslation.translateY - .5f).toInt())
            else translateFilteredMask(maskDestinationKeys, maskTranslation.translateX, maskTranslation.translateY, interpolation)
        } else transformMask(originalSelection, maskDestinationKeys, transform, sampler, interpolation)
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

    private data class NativeSource(val bounds: PixelRect, val pixels: Any, val alpha: ByteArray)
    private fun nativeSource(destination: PixelRect, inverse: InverseSelectionMapping, halo: Int): NativeSource? {
        val corners = listOf(destination.left to destination.top, destination.right to destination.top,
            destination.right to destination.bottom, destination.left to destination.bottom)
        if (inverse.crossesHorizon(corners)) return null
        val xs = corners.map { inverse.x(it.first.toFloat(), it.second.toFloat()) }
        val ys = corners.map { inverse.y(it.first.toFloat(), it.second.toFloat()) }
        val bounds = PixelRect(floor(xs.min()).toInt().coerceIn(0, surface.logicalWidth) - halo - 1,
            floor(ys.min()).toInt().coerceIn(0, surface.logicalHeight) - halo - 1,
            ceil(xs.max()).toInt().coerceIn(0, surface.logicalWidth) + halo + 1,
            ceil(ys.max()).toInt().coerceIn(0, surface.logicalHeight) + halo + 1)
            .intersect(PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight))
        // Extreme minification stays sparse and uses the bounded per-pixel sampler instead of
        // allocating an entire large logical page as an input ROI.
        if (bounds.isEmpty || bounds.width.toLong() * bounds.height > 1_048_576L) return null
        val pixels = if (surface.pixelFormat.isHighPrecision) originalPixels.copyPremultipliedF16(bounds)
            else originalPixels.copyPremultipliedPixels(bounds)
        return NativeSource(bounds, pixels, rasterAlpha(bounds))
    }
    private fun nativeTile(source: NativeSource, pixels: Any, retainedAlpha: ByteArray, destination: PixelRect,
                           stride: Int, inverse: InverseSelectionMapping, interpolation: SelectionInterpolation): Boolean =
        NativeRasterOps.affineSelectionTile(source.pixels, source.alpha, source.bounds.left, source.bounds.top, source.bounds.width, source.bounds.height,
            pixels, retainedAlpha, destination.left, destination.top, stride, destination.width, destination.height, inverse.coefficients,
            interpolation.ordinal, surface.pixelFormat.isHighPrecision)

    private fun rasterAlpha(bounds: PixelRect): ByteArray {
        val maskBounds = PixelRect(((bounds.left + .5f) * maskScaleX).toInt(), ((bounds.top + .5f) * maskScaleY).toInt(),
            (((bounds.right - .5f) * maskScaleX).toInt() + 1).coerceAtMost(originalSelection.logicalWidth),
            (((bounds.bottom - .5f) * maskScaleY).toInt() + 1).coerceAtMost(originalSelection.logicalHeight))
        val input = originalSelection.copyAlpha(maskBounds)
        if (bounds.width == maskBounds.width && bounds.height == maskBounds.height) return input
        val xs = IntArray(bounds.width) { ((bounds.left + it + .5f) * maskScaleX).toInt() - maskBounds.left }
        return ByteArray(bounds.width * bounds.height).also { output ->
            for (y in 0 until bounds.height) {
                val row = (((bounds.top + y + .5f) * maskScaleY).toInt() - maskBounds.top) * maskBounds.width
                for (x in 0 until bounds.width) output[y * bounds.width + x] = input[row + xs[x]]
            }
        }
    }

    /** Translation has constant filter weights. Copy one bounded source ROI per destination tile
     * and scan primitive arrays instead of doing four/sixteen sparse lookups for every pixel. */
    private fun translateFilteredMask(keys: Set<PixelTileKey>, dx: Float, dy: Float, interpolation: SelectionInterpolation): MaskTileSurfaceSnapshot {
        val source = originalSelection
        val result = MaskTileSurface(source.logicalWidth, source.logicalHeight, source.tileSize)
        val cubic = interpolation == SelectionInterpolation.BICUBIC
        val first = if (cubic) -1 else 0
        val count = if (cubic) 4 else 2
        val offsetX = floor(-dx).toInt(); val offsetY = floor(-dy).toInt()
        val fx = -dx - offsetX; val fy = -dy - offsetY
        fun weight(t: Float, i: Int): Float {
            if (!cubic) return if (i == 0) 1f - t else t
            val x = kotlin.math.abs(t - i)
            return when { x < 1f -> 1.5f*x*x*x - 2.5f*x*x + 1f; x < 2f -> -.5f*x*x*x + 2.5f*x*x - 4f*x + 2f; else -> 0f }
        }
        val wx = FloatArray(count) { weight(fx, first + it) }
        val wy = FloatArray(count) { weight(fy, first + it) }
        val logical = PixelRect(0, 0, source.logicalWidth, source.logicalHeight)
        result.beginWrite("translate filtered selection alpha").use { transaction ->
            keys.forEach { key ->
                val destination = PixelRect(key.column * source.tileSize, key.row * source.tileSize,
                    minOf((key.column + 1) * source.tileSize, source.logicalWidth), minOf((key.row + 1) * source.tileSize, source.logicalHeight))
                val requested = PixelRect(destination.left + offsetX + first, destination.top + offsetY + first,
                    destination.right + offsetX + first + count - 1, destination.bottom + offsetY + first + count - 1)
                val clipped = requested.intersect(logical)
                if (clipped.isEmpty) return@forEach
                val input = ByteArray(requested.width * requested.height)
                source.copyAlpha(clipped, input, (clipped.top - requested.top) * requested.width + clipped.left - requested.left, requested.width)
                val output = ByteArray(destination.width * destination.height)
                val nativeCount = NativeRasterOps.translatedSelectionAlpha(input, requested.width, output, destination.width, destination.height, wx, wy)
                var nonZero = nativeCount > 0
                if (nativeCount < 0) for (y in 0 until destination.height) for (x in 0 until destination.width) {
                    var alpha = 0f
                    for (row in 0 until count) {
                        if (wy[row] == 0f) continue
                        val offset = (y + row) * requested.width + x
                        for (col in 0 until count) alpha += (input[offset + col].toInt() and 255) * wx[col] * wy[row]
                    }
                    val value = alpha.roundToInt().coerceIn(0, 255)
                    output[y * destination.width + x] = value.toByte()
                    nonZero = nonZero || value != 0
                }
                if (nonZero) transaction.replaceAlpha(destination, output)
            }
            transaction.commit().close()
        }
        return result.snapshot()
    }

    private fun transformMask(
        source: MaskTileSurfaceSnapshot,
        destinationKeys: Set<PixelTileKey>,
        transform: SelectionRasterTransform,
        sampler: SelectionPixelSampler,
        interpolation: SelectionInterpolation,
    ): MaskTileSurfaceSnapshot {
        val inverse = InverseSelectionMapping(transform)
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
                val nativeCount = affineMaskTile(source, tileBounds, alpha, inverse, interpolation)
                var nonZero = nativeCount > 0
                if (nativeCount < 0) for (y in tileBounds.top until tileBounds.bottom) for (x in tileBounds.left until tileBounds.right) {
                    // The transform is in raster pixels; mask storage has independent dimensions.
                    // Sample the original mask directly so previews never resample a previous mask.
                    val px = (x + .5f) / maskScaleX; val py = (y + .5f) / maskScaleY
                    val value = sampler.maskAlpha(inverse.x(px, py) * maskScaleX, inverse.y(px, py) * maskScaleY)
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

    private fun affineMaskTile(source: MaskTileSurfaceSnapshot, tile: PixelRect, output: ByteArray,
                               inverse: InverseSelectionMapping, interpolation: SelectionInterpolation): Int {
        val m = inverse.coefficients.copyOf()
        m[1] *= maskScaleX / maskScaleY; m[2] *= maskScaleY / maskScaleX
        m[4] *= maskScaleX; m[5] *= maskScaleY
        if (m.size == 9) { m[6] /= maskScaleX; m[7] /= maskScaleY }
        val points = listOf(tile.left to tile.top, tile.right to tile.top, tile.left to tile.bottom, tile.right to tile.bottom)
        fun denominator(x: Int, y: Int) = if (m.size == 9) m[6]*x+m[7]*y+m[8] else 1f
        val denominators = points.map { (x,y) -> denominator(x,y) }
        if (denominators.min() <= 0f && denominators.max() >= 0f) return -1
        val xs = points.map { (x, y) -> (m[0]*x + m[1]*y + m[4])/denominator(x,y) }
        val ys = points.map { (x, y) -> (m[2]*x + m[3]*y + m[5])/denominator(x,y) }
        val halo = if (interpolation == SelectionInterpolation.BICUBIC) 2 else 1
        val roi = PixelRect(floor(xs.min()).toInt().coerceIn(0, source.logicalWidth) - halo,
            floor(ys.min()).toInt().coerceIn(0, source.logicalHeight) - halo,
            ceil(xs.max()).toInt().coerceIn(0, source.logicalWidth) + halo,
            ceil(ys.max()).toInt().coerceIn(0, source.logicalHeight) + halo)
            .intersect(PixelRect(0, 0, source.logicalWidth, source.logicalHeight))
        if (roi.isEmpty) return 0
        if (roi.width.toLong() * roi.height > 1_048_576L) return -1
        return NativeRasterOps.affineSelectionAlpha(source.copyAlpha(roi), roi.left, roi.top, roi.width, roi.height,
            output, tile.left, tile.top, tile.width, tile.height, m, interpolation.ordinal)
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

    /** Map occupied tile rectangles, not a page-sized alpha bitmap, between the two grids. */
    private fun mapTileKeys(
        keys: Set<PixelTileKey>, sourceTileSize: Int, sourceWidth: Int, sourceHeight: Int,
        targetTileSize: Int, targetWidth: Int, targetHeight: Int,
    ): Set<PixelTileKey> {
        if (sourceTileSize == targetTileSize && sourceWidth == targetWidth && sourceHeight == targetHeight) return keys
        return buildSet {
            keys.forEach { key ->
                val left = floor(key.column.toDouble() * sourceTileSize * targetWidth / sourceWidth).toInt().coerceIn(0, targetWidth)
                val top = floor(key.row.toDouble() * sourceTileSize * targetHeight / sourceHeight).toInt().coerceIn(0, targetHeight)
                val right = ceil(minOf((key.column + 1) * sourceTileSize, sourceWidth).toDouble() * targetWidth / sourceWidth).toInt().coerceIn(0, targetWidth)
                val bottom = ceil(minOf((key.row + 1) * sourceTileSize, sourceHeight).toDouble() * targetHeight / sourceHeight).toInt().coerceIn(0, targetHeight)
                if (right <= left || bottom <= top) return@forEach
                for (row in top / targetTileSize..(bottom - 1) / targetTileSize) {
                    for (column in left / targetTileSize..(right - 1) / targetTileSize) add(PixelTileKey(column, row))
                }
            }
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
        transform: SelectionRasterTransform,
        width: Int,
        height: Int,
        halo: Int,
    ): Set<PixelTileKey> = buildSet {
        sourceKeys.forEach { key ->
            val sourceTile = PixelRect(
                key.column * tileSize - halo,
                key.row * tileSize - halo,
                minOf((key.column + 1) * tileSize, width) + halo,
                minOf((key.row + 1) * tileSize, height) + halo,
            )
            val selectedTile = if (transform is SelectionProjectiveMapping) sourceTile.intersect(
                PixelRect(sourceBounds.left-halo, sourceBounds.top-halo, sourceBounds.right+halo, sourceBounds.bottom+halo)) else sourceTile
            if (selectedTile.isEmpty) return@forEach
            val bounds = transformedBounds(selectedTile, transform, width, height)
            if (bounds.isEmpty) return@forEach
            for (row in bounds.top / tileSize..(bounds.bottom - 1) / tileSize) {
                for (column in bounds.left / tileSize..(bounds.right - 1) / tileSize) add(PixelTileKey(column, row))
            }
        }
    }

    private class InverseSelectionMapping(transform: SelectionRasterTransform) {
        val coefficients = transform.inverseCoefficients()
        fun denominator(x: Float, y: Float) = if (coefficients.size == 9) coefficients[6]*x+coefficients[7]*y+coefficients[8] else 1f
        fun x(x: Float, y: Float) = (coefficients[0]*x+coefficients[1]*y+coefficients[4])/denominator(x,y)
        fun y(x: Float, y: Float) = (coefficients[2]*x+coefficients[3]*y+coefficients[5])/denominator(x,y)
        fun crossesHorizon(points: List<Pair<Int, Int>>): Boolean {
            if (coefficients.size == 6) return false
            val w = points.map { denominator(it.first.toFloat(), it.second.toFloat()) }
            return w.min() <= 0f && w.max() >= 0f
        }
    }

    companion object {
        internal fun transformedBounds(bounds: PixelRect, transform: SelectionRasterTransform, width: Int, height: Int): PixelRect {
            if (transform is SelectionProjectiveMapping && transform.crossesHorizon(bounds.left,bounds.top,bounds.right,bounds.bottom))
                return PixelRect(0,0,width,height)
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

        private fun forwardPoint(x: Float, y: Float, transform: SelectionRasterTransform) = transform.forward(x, y)

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
