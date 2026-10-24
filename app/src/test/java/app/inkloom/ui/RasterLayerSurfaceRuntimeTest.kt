package app.inkloom.ui

import app.inkloom.document.InkLayer
import app.inkloom.document.InkPage
import app.inkloom.document.LayerRole
import app.inkloom.document.RasterTileSurfaceRef
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class RasterLayerSurfaceRuntimeTest {
    @Test
    fun unrelatedUiRecompositionKeepsTheAuthoritativeLiveSurface() {
        var loads = 0
        val runtime = RasterLayerSurfaceRuntime(1200) { _, _, width ->
            PixelTileSurface(width, 800, surfaceId = "load-${++loads}")
        }
        val page = page(reference("initial", 1))

        val first = runtime.reconcile(page).getValue(PAINT)
        val recomposed = runtime.reconcile(page.copy(title = "Unrelated UI state")).getValue(PAINT)

        assertSame(first, recomposed)
        assertEquals(1, loads)
    }

    @Test
    fun hiddenOnionSkinCelsAreLoadedAndHandedToTheCanvasButOtherHiddenLayersAreNot() {
        val runtime = RasterLayerSurfaceRuntime(1200) { _, id, width -> PixelTileSurface(width, 800, surfaceId = "load-$id") }
        val page = InkPage(layers = listOf(InkLayer(id = "shown", name = "Shown"), InkLayer(id = "cel", name = "Cel", visible = false),
            InkLayer(id = "other", name = "Other", visible = false)))
        assertEquals(setOf("shown"), runtime.reconcile(page).keys)
        assertEquals(setOf("shown", "cel"), runtime.reconcile(page, onionSkin = setOf("cel")).keys)
        // Turning onion skin off hands the canvas only the visible layers again.
        assertEquals(setOf("shown"), runtime.reconcile(page).keys)
    }

    @Test
    fun locallyPublishedCheckpointDoesNotReplacePixelsFromLaterGestures() {
        var loads = 0
        val runtime = RasterLayerSurfaceRuntime(1200) { _, _, width ->
            PixelTileSurface(width, 800, surfaceId = "load-${++loads}")
        }
        val initial = page(reference("initial", 1))
        val live = runtime.reconcile(initial).getValue(PAINT)
        val saved = reference("saved", 2)

        runtime.acceptPublished(initial, PAINT, saved, 0)
        val afterCheckpoint = runtime.reconcile(page(saved)).getValue(PAINT)

        assertSame("A disk checkpoint may lag newer live tile generations", live, afterCheckpoint)
        assertEquals(1, loads)
    }

    @Test
    fun intermediateLocalCheckpointCannotReloadOverANewerLiveErase() {
        var loads = 0
        val runtime = RasterLayerSurfaceRuntime(1200) { _, _, width ->
            PixelTileSurface(width, 800, surfaceId = "load-${++loads}")
        }
        val initial = page(reference("initial", 1))
        val live = runtime.reconcile(initial).getValue(PAINT)
        live.beginWrite("newer live erase").use { write ->
            write.setPixel(48, 48, 0)
            write.commit().close()
        }
        val firstCheckpoint = reference("first-checkpoint", 2)
        val secondCheckpoint = reference("second-checkpoint", 3)

        // Persistence may finish both gestures before Compose consumes the first document state.
        runtime.acceptPublished(initial, PAINT, firstCheckpoint, 0)
        runtime.acceptPublished(initial, PAINT, secondCheckpoint, 0)
        val afterIntermediate = runtime.reconcile(page(firstCheckpoint)).getValue(PAINT)
        val afterLatest = runtime.reconcile(page(secondCheckpoint)).getValue(PAINT)

        assertSame("An intermediate local checkpoint must not replace newer erased pixels", live, afterIntermediate)
        assertSame(live, afterLatest)
        assertEquals(1, loads)
    }

    @Test
    fun externalRevisionStillReloadsUndoClearAndLayerReplacement() {
        var loads = 0
        val runtime = RasterLayerSurfaceRuntime(1200) { _, _, width ->
            PixelTileSurface(width, 800, surfaceId = "load-${++loads}")
        }
        val initial = page(reference("initial", 1))
        val live = runtime.reconcile(initial).getValue(PAINT)

        val replaced = runtime.reconcile(page(reference("external", 9))).getValue(PAINT)

        assertNotSame(live, replaced)
        assertEquals(2, loads)
    }

    private fun page(reference: RasterTileSurfaceRef?) = InkPage(
        layers = listOf(
            InkLayer("background", "Background", LayerRole.BACKGROUND),
            InkLayer(PAINT, "Notes", LayerRole.INK, rasterTileSurface = reference),
        ),
    )

    private fun reference(name: String, revision: Long) = RasterTileSurfaceRef(
        manifestAssetPath = "/tmp/$name.json",
        surfaceId = name,
        logicalWidth = 1200,
        logicalHeight = 800,
        tileSize = 128,
        revision = revision,
    )

    private companion object { const val PAINT = "paint" }
}
