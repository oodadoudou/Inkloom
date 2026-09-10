package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class RasterLayerMergeTest {
    private val reference = RasterTileSurfaceRef("/merge.json", "merged", 1200, 1697, 128, 1)
    private val lower = InkLayer(id = "lower", name = "Lower")
    private val upper = InkLayer(id = "upper", name = "Upper", rasterTileSurface = reference)
    private val page = InkPage(layers = defaultInkLayers() + lower + upper)
    @Test fun ordinaryPaintLayersAreSupportedAtTheirNativeResolution() {
        assertNull(RasterLayerMerge.rejection(page, upper.id))
        assertEquals(1200, RasterLayerMerge.renderWidth(page, upper.id))
    }
    @Test fun mergeAndUndoRestoreBothLayerSettingsAndObjectsExactly() {
        val original = page.copy(layers = page.layers.map { if (it.id == upper.id) it.copy(opacity = .5f, alphaLock = true, mask = LayerMask()) else it },
            elements = listOf(PageElement.Text(id = "note", x = .1f, y = .1f, text = "Test", layerId = upper.id)))
        val edit = RasterLayerMerge.edit(original, upper.id, reference)
        val merged = edit.apply(original, false)
        assertFalse(merged.layers.any { it.id == upper.id })
        assertTrue(merged.elements.isEmpty())
        assertEquals(1f, merged.layers.last().opacity)
        assertNull(merged.layers.last().mask)
        assertEquals(original, edit.apply(merged, true))
        assertEquals(merged, edit.apply(edit.apply(merged, true), false))
    }
    @Test fun mergeNeverCrossesFolderOrClippingDependencies() {
        assertEquals(RasterLayerMerge.Rejection.DIFFERENT_GROUP, RasterLayerMerge.rejection(page.copy(layers = page.layers.map {
            if (it.id == upper.id) it.copy(parentId = "folder") else it
        }), upper.id))
        assertEquals(RasterLayerMerge.Rejection.CLIPPING, RasterLayerMerge.rejection(page.copy(layers = page.layers + InkLayer(name = "Clipped", clippedToBelow = true)), upper.id))
    }
    @Test fun protectedModesAndHighPrecisionAreExplicitlyRejected() {
        fun rejection(layer: InkLayer) = RasterLayerMerge.rejection(page.copy(layers = page.layers.dropLast(1) + layer), upper.id)
        assertEquals(RasterLayerMerge.Rejection.PROTECTED, rejection(upper.copy(locked = true)))
        assertEquals(RasterLayerMerge.Rejection.BLEND_MODE, rejection(upper.copy(blendMode = LayerBlendMode.MULTIPLY)))
        assertEquals(RasterLayerMerge.Rejection.HIDDEN_OR_DRAFT_MISMATCH, rejection(upper.copy(visible = false)))
        assertEquals(RasterLayerMerge.Rejection.PIXEL_FORMAT, rejection(upper.copy(rasterTileSurface = reference.copy(pixelFormat = "rgba16f-linear"))))
        assertEquals(RasterLayerMerge.Rejection.SIZE_LIMIT, rejection(upper.copy(rasterTileSurface = reference.copy(logicalWidth = 8000))))
    }
    @Test fun maskedCropIsRejectedInsteadOfChangingTheVisibleResult() {
        val masked = page.copy(crop = PageCrop(.1f, .1f, .9f, .9f), layers = page.layers.map {
            if (it.id == upper.id) it.copy(mask = LayerMask()) else it
        })
        assertEquals(RasterLayerMerge.Rejection.MASK_GEOMETRY, RasterLayerMerge.rejection(masked, upper.id))
    }
    @Test fun undoPreservesUnrelatedLayersAndObjects() {
        val edit = RasterLayerMerge.edit(page, upper.id, reference)
        val elsewhere = InkLayer(name = "Other")
        val objectElsewhere = PageElement.Text(x = .1f, y = .1f, text = "Unrelated", layerId = elsewhere.id)
        val changed = edit.apply(page, false).let { it.copy(layers = it.layers + elsewhere, elements = it.elements + objectElsewhere) }
        val restored = edit.apply(changed, true)
        assertEquals(elsewhere, restored.layers.last())
        assertTrue(objectElsewhere in restored.elements)
    }
}
