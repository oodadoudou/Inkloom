package app.inkloom.document

import app.inkloom.engine.v5.surface.*
import kotlin.math.*

/** Converts retained ColorDrop masks without resampling the existing paint or the page crop. */
internal class LegacyFillMigration(private val assets: DocumentAssetService) {
    sealed interface Result {
        data class Executed(val document: InkDocument, val count: Int) : Result
        data object Unchanged : Result
        data class Rejected(val reason: String) : Result
    }

    /** Assets stay reserved until the caller has atomically saved the complete document. */
    fun migrate(document: InkDocument, save: (InkDocument) -> Unit): Result {
        val fills = document.pages.flatMap { it.elements.filterIsInstance<PageElement.Fill>() }
        if (fills.isEmpty()) return Result.Unchanged
        val reservations = mutableListOf<RasterTileSurfaceRef>()
        return try {
            val masks = fills.associate { fill ->
                require(listOf(fill.x, fill.y, fill.width, fill.height, fill.rotationDegrees, fill.opacity).all(Float::isFinite) &&
                    fill.width > 0 && fill.height > 0) { "A fill has invalid geometry" }
                fill.maskAssetPath to requireNotNull(assets.loadSparseFillMask(fill.maskAssetPath)) { "A fill mask is missing or damaged" }.snapshot()
            }
            // Validate every layer before preparing any replacement assets.
            document.pages.forEach { page ->
                page.elements.filterIsInstance<PageElement.Fill>().groupBy { it.layerId }.forEach { (id, _) ->
                    val layer = requireNotNull(page.layers.find { it.id == id }) { "A fill's layer is missing" }
                    require(layer.isPaintSurfaceLayer()) { "A fill belongs to a non-paint layer" }
                    require(!layer.alphaLock || layer.alphaLockBaseContentIds.isEmpty()) {
                        "This legacy fill uses an object-based alpha lock; its original contents were preserved"
                    }
                    val items = page.elements.filter { it.layerId == id }
                    require(items.dropWhile { it is PageElement.Fill }.none { it is PageElement.Fill }) {
                        "This legacy fill overlaps retained objects; its original layer order was preserved"
                    }
                }
            }
            val pages = document.pages.map { page ->
                val byLayer = page.elements.filterIsInstance<PageElement.Fill>().groupBy { it.layerId }
                if (byLayer.isEmpty()) return@map page
                val layers = page.layers.map { layer ->
                    val layerFills = byLayer[layer.id] ?: return@map layer
                    val surface = layer.rasterTileSurface?.let { reference ->
                        requireNotNull(assets.loadRasterTileSurface(reference)) { "The original paint surface is missing or damaged" }
                    } ?: run {
                        require(page.width.isFinite() && page.height.isFinite() && page.width in 1f..65536f && page.height in 1f..65536f) {
                            "The page has invalid dimensions"
                        }
                        val swapped = layer.rasterRotationQuarterTurns.mod(2) != 0
                        PixelTileSurface(ceil(if (swapped) page.height else page.width).toInt(),
                            ceil(if (swapped) page.width else page.height).toInt())
                    }
                    layerFills.forEach { fill ->
                        val mask = mapMask(masks.getValue(fill.maskAssetPath), fill, page, layer, surface)
                        RasterSelectionContent.fill(surface, mask, fill.colorArgb.toInt(), fill.opacity)?.record?.close()
                    }
                    val reference = assets.saveRasterTileSurface(surface, layer.rasterTileSurface)
                    reservations += reference
                    layer.copy(rasterTileSurface = reference)
                }
                page.copy(layers = layers, elements = page.elements.filterNot { it is PageElement.Fill })
            }
            val result = document.copy(pages = pages)
            save(result)
            Result.Executed(result, fills.size)
        } catch (error: Exception) {
            Result.Rejected(error.message ?: "The legacy fill could not be converted; the original document was preserved")
        } finally {
            reservations.forEach(assets::releaseRasterTileReservation)
        }
    }

    /** Inverse map each bounded destination tile into the original immutable mask. */
    private fun mapMask(mask: MaskTileSurfaceSnapshot, fill: PageElement.Fill, page: InkPage,
                        layer: InkLayer, pixels: PixelTileSurface): MaskTileSurfaceSnapshot {
        val result = MaskTileSurface(pixels.logicalWidth, pixels.logicalHeight, pixels.tileSize)
        val turns = layer.rasterRotationQuarterTurns.mod(4)
        val centerX = fill.x * page.width + fill.width / 2f
        val centerY = fill.y * page.height + fill.height / 2f
        val radians = Math.toRadians(fill.rotationDegrees.toDouble())
        val c = cos(radians); val s = sin(radians)
        fun native(x: Double, y: Double): Pair<Double, Double> {
            val u = x / page.width; val v = y / page.height
            val p = when (turns) { 1 -> v to 1.0-u; 2 -> 1.0-u to 1.0-v; 3 -> 1.0-v to u; else -> u to v }
            return p.first * pixels.logicalWidth to p.second * pixels.logicalHeight
        }
        val corners = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (sx, sy) ->
            val x = sx * fill.width / 2.0; val y = sy * fill.height / 2.0
            native(centerX + c*x - s*y, centerY + s*x + c*y)
        }
        val bounds = PixelRect(floor(corners.minOf { it.first }).toInt().coerceIn(0, pixels.logicalWidth),
            floor(corners.minOf { it.second }).toInt().coerceIn(0, pixels.logicalHeight),
            ceil(corners.maxOf { it.first }).toInt().coerceIn(0, pixels.logicalWidth),
            ceil(corners.maxOf { it.second }).toInt().coerceIn(0, pixels.logicalHeight))
        if (bounds.isEmpty) return result.snapshot()
        result.beginWrite("migrate legacy fill coordinates").use { write ->
            for (top in bounds.top until bounds.bottom step pixels.tileSize) {
                for (left in bounds.left until bounds.right step pixels.tileSize) {
                    val rect = PixelRect(left, top, min(left + pixels.tileSize, bounds.right), min(top + pixels.tileSize, bounds.bottom))
                    val alpha = ByteArray(rect.width * rect.height)
                    for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) {
                        val u = (x + .5) / pixels.logicalWidth; val v = (y + .5) / pixels.logicalHeight
                        val p = when (turns) { 1 -> 1.0-v to u; 2 -> 1.0-u to 1.0-v; 3 -> v to 1.0-u; else -> u to v }
                        val dx = p.first * page.width - centerX; val dy = p.second * page.height - centerY
                        val mx = ((c*dx + s*dy) / fill.width + .5) * mask.logicalWidth
                        val my = ((-s*dx + c*dy) / fill.height + .5) * mask.logicalHeight
                        alpha[(y-top)*rect.width+x-left] = mask.alphaAt(floor(mx).toInt(), floor(my).toInt()).toByte()
                    }
                    write.composeAlpha(rect, alpha, mode = MaskComposeMode.REPLACE)
                }
            }
            write.commit().close()
        }
        return result.snapshot()
    }
}
