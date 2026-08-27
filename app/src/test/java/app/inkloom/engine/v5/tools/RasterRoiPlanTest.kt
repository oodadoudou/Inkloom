package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RasterRoiPlanTest {
    @Test fun clipsWritesButPreservesRequestedHaloAndReadTiles() {
        val plan = RasterRoiPlan.create(
            writeBounds = PixelRect(0, 120, 300, 280),
            logicalWidth = 500,
            logicalHeight = 400,
            tileSize = 128,
            readCore = PixelRect(-5, 10, 60, 80),
            halo = RasterHalo.uniform(4),
        )

        assertEquals(PixelRect(0, 120, 300, 280), plan.writeBounds)
        assertEquals(PixelRect(-9, 6, 64, 84), plan.requestedReadBounds)
        assertEquals(PixelRect(0, 6, 64, 84), plan.clippedReadBounds)
        assertEquals(setOf(PixelTileKey(0, 0)), plan.readTileKeys)
        assertEquals(9, plan.writeUnits.size)
    }

    @Test fun independentLargeRoiUsesBoundedParallelTileUnits() {
        val plan = RasterRoiPlan.writeOnly(
            PixelRect(0, 0, 512, 512),
            logicalWidth = 512,
            logicalHeight = 512,
            tileSize = 128,
            dependency = RasterDependency.INDEPENDENT,
        )

        assertEquals(RasterExecutionMode.PARALLEL, plan.executionMode)
        assertEquals(16, plan.writeUnits.size)
        assertEquals(PixelRect(0, 0, 128, 128), plan.writeUnits.first().writeBounds)
        assertEquals(PixelRect(384, 384, 512, 512), plan.writeUnits.last().writeBounds)
    }

    @Test fun sequentialKernelNeverEntersParallelExecution() {
        val plan = RasterRoiPlan.writeOnly(
            PixelRect(0, 0, 1024, 1024),
            logicalWidth = 1024,
            logicalHeight = 1024,
            tileSize = 128,
        )
        assertEquals(RasterExecutionMode.SERIAL, plan.executionMode)
    }

    @Test fun rejectsUnboundedReadMaterialization() {
        assertThrows(IllegalArgumentException::class.java) {
            RasterRoiPlan.create(
                PixelRect(0, 0, 16, 16),
                logicalWidth = 6000,
                logicalHeight = 6000,
                tileSize = 128,
                readCore = PixelRect(0, 0, 5000, 5000),
            )
        }
    }
}
