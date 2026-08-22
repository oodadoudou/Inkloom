package app.inkloom.engine.v3.tools

import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey

internal enum class RasterAbyssPolicy { TRANSPARENT, CLAMP_TO_EDGE }
internal enum class RasterDependency { SEQUENTIAL, INDEPENDENT }
internal enum class RasterExecutionMode { SERIAL, PARALLEL }

internal data class RasterHalo(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = left,
    val bottom: Int = top,
) {
    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0)
    }

    companion object {
        fun uniform(pixels: Int) = RasterHalo(pixels, pixels, pixels, pixels)
    }
}

internal data class RasterRoiWorkUnit(
    val tileKey: PixelTileKey,
    val writeBounds: PixelRect,
)

/** Immutable, storage-neutral contract for one raster operation. */
internal class RasterRoiPlan private constructor(
    val logicalBounds: PixelRect,
    val writeBounds: PixelRect,
    val requestedReadBounds: PixelRect?,
    val clippedReadBounds: PixelRect?,
    val abyssPolicy: RasterAbyssPolicy,
    val dependency: RasterDependency,
    val executionMode: RasterExecutionMode,
    val writeUnits: List<RasterRoiWorkUnit>,
    val readTileKeys: Set<PixelTileKey>,
) {
    val materializedReadPixelCount: Long
        get() = requestedReadBounds?.let { it.width.toLong() * it.height } ?: 0L

    companion object {
        const val MAX_MATERIALIZED_READ_PIXELS = 16L * 1024L * 1024L
        private const val PARALLEL_MIN_PIXELS = 4L * 128L * 128L
        private const val PARALLEL_MIN_TILES = 4

        fun writeOnly(
            writeBounds: PixelRect,
            logicalWidth: Int,
            logicalHeight: Int,
            tileSize: Int,
            dependency: RasterDependency = RasterDependency.SEQUENTIAL,
        ): RasterRoiPlan = create(
            writeBounds,
            logicalWidth,
            logicalHeight,
            tileSize,
            readCore = null,
            dependency = dependency,
        )

        fun create(
            writeBounds: PixelRect,
            logicalWidth: Int,
            logicalHeight: Int,
            tileSize: Int,
            readCore: PixelRect? = null,
            halo: RasterHalo = RasterHalo(),
            abyssPolicy: RasterAbyssPolicy = RasterAbyssPolicy.TRANSPARENT,
            dependency: RasterDependency = RasterDependency.SEQUENTIAL,
        ): RasterRoiPlan {
            require(logicalWidth > 0 && logicalHeight > 0)
            require(tileSize > 0)
            val logical = PixelRect(0, 0, logicalWidth, logicalHeight)
            val clippedWrite = writeBounds.intersect(logical)
            val requestedRead = readCore?.let { core ->
                PixelRect(
                    core.left - halo.left,
                    core.top - halo.top,
                    core.right + halo.right,
                    core.bottom + halo.bottom,
                )
            }
            if (requestedRead != null) {
                require(!requestedRead.isEmpty) { "Read ROI must not be empty" }
                val pixels = requestedRead.width.toLong() * requestedRead.height
                require(pixels <= MAX_MATERIALIZED_READ_PIXELS) {
                    "Raster read ROI $pixels exceeds $MAX_MATERIALIZED_READ_PIXELS pixels"
                }
            }
            val clippedRead = requestedRead?.intersect(logical)?.takeUnless(PixelRect::isEmpty)
            val units = tileUnits(clippedWrite, logical, tileSize)
            val mode = if (
                dependency == RasterDependency.INDEPENDENT &&
                units.size >= PARALLEL_MIN_TILES &&
                clippedWrite.width.toLong() * clippedWrite.height >= PARALLEL_MIN_PIXELS
            ) RasterExecutionMode.PARALLEL else RasterExecutionMode.SERIAL
            return RasterRoiPlan(
                logical,
                clippedWrite,
                requestedRead,
                clippedRead,
                abyssPolicy,
                dependency,
                mode,
                units,
                clippedRead?.let { tileKeys(it, tileSize) }.orEmpty(),
            )
        }

        private fun tileUnits(bounds: PixelRect, logical: PixelRect, tileSize: Int): List<RasterRoiWorkUnit> {
            if (bounds.isEmpty) return emptyList()
            val units = ArrayList<RasterRoiWorkUnit>()
            for (row in bounds.top / tileSize..(bounds.bottom - 1) / tileSize) {
                for (column in bounds.left / tileSize..(bounds.right - 1) / tileSize) {
                    val tile = PixelRect(
                        column * tileSize,
                        row * tileSize,
                        minOf((column + 1) * tileSize, logical.right),
                        minOf((row + 1) * tileSize, logical.bottom),
                    )
                    units += RasterRoiWorkUnit(PixelTileKey(column, row), tile.intersect(bounds))
                }
            }
            return units
        }

        private fun tileKeys(bounds: PixelRect, tileSize: Int): Set<PixelTileKey> {
            if (bounds.isEmpty) return emptySet()
            return buildSet {
                for (row in bounds.top / tileSize..(bounds.bottom - 1) / tileSize) {
                    for (column in bounds.left / tileSize..(bounds.right - 1) / tileSize) {
                        add(PixelTileKey(column, row))
                    }
                }
            }
        }
    }
}
