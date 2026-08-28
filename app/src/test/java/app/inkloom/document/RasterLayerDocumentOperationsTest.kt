package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RasterLayerDocumentOperationsTest {
    @Test fun clearUnlockedDropsOnlyUnlockedRasterTruth() {
        val unlocked = InkLayer(
            id = "unlocked",
            name = "Paint",
            rasterTileSurface = reference("unlocked"),
            rasterRotationQuarterTurns = 3,
        )
        val locked = InkLayer(
            id = "locked",
            name = "Locked",
            rasterTileSurface = reference("locked"),
            locked = true,
        )
        val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER)

        val cleared = RasterLayerDocumentOperations.clearUnlocked(
            InkPage(layers = listOf(unlocked, locked, folder)),
        )

        val clearedLayer = cleared.layers.first { it.id == unlocked.id }
        assertNull(clearedLayer.rasterTileSurface)
        assertEquals(0, clearedLayer.rasterRotationQuarterTurns)
        assertNotNull(cleared.layers.first { it.id == locked.id }.rasterTileSurface)
        assertEquals(folder, cleared.layers.first { it.id == folder.id })
    }

    private fun reference(id: String) = RasterTileSurfaceRef(
        manifestAssetPath = "/tiles/$id.json",
        surfaceId = id,
        logicalWidth = 1200,
        logicalHeight = 1600,
        tileSize = 256,
        revision = 4,
    )
}
