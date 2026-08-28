package app.inkloom

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import app.inkloom.document.LayerMask
import app.inkloom.document.defaultInkLayers
import app.inkloom.engine.v5.surface.MaskTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class LayerMaskRenderingTest {
    @Test fun sparseLayerMaskSurvivesDocumentBundleRoundTrip() {
        val assets = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val surface = MaskTileSurface(400, 400, tileSize = 128, initialDefaultAlpha = 255)
        surface.beginWrite("mask hole").run { setAlpha(300, 200, 0); commit().close() }
        val reference = assets.saveMaskTileSurface(surface.snapshot())
        val inkLayer = defaultInkLayers().first { it.id == "ink" }
        val document = InkDocument(
            title = "Layer mask bundle",
            pages = listOf(
                InkPage(
                    width = 400f,
                    height = 400f,
                    layers = defaultInkLayers().map { layer ->
                        if (layer.id == inkLayer.id) layer.copy(mask = LayerMask(surface = reference)) else layer
                    },
                ),
            ),
        )
        val archive = ByteArrayOutputStream().also { assets.exportBundle(document, it) }.toByteArray()
        val restored = assets.importBundle(ByteArrayInputStream(archive))
        val restoredReference = requireNotNull(
            restored.pages.single().layers.first { it.id == inkLayer.id }.mask?.surface,
        )
        try {
            assertTrue(java.io.File(restoredReference.assetPath).isFile)
            val restoredSurface = assets.loadMaskTileSurface(restoredReference)
            assertEquals(0, restoredSurface.alphaAt(300, 200))
            assertEquals(255, restoredSurface.alphaAt(100, 200))
        } finally {
            java.io.File(reference.assetPath).delete()
            java.io.File(restoredReference.assetPath).delete()
        }
    }
}
