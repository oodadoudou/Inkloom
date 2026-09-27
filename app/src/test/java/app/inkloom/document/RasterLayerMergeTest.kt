package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class RasterLayerMergeTest {
    private val reference = RasterTileSurfaceRef("/merge.json", "merged", 1200, 1697, 128, 1)
    private val lower = InkLayer(id = "lower", name = "Lower")
    private val upper = InkLayer(id = "upper", name = "Upper", rasterTileSurface = reference)
    private val page = InkPage(layers = defaultInkLayers() + lower + upper)
    private fun mergeDown(page: InkPage, id: String) = RasterLayerMerge.plan(
        page, RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_DOWN, id))

    @Test fun ordinaryPaintLayersUseTheirNativeResolution() {
        val plan = mergeDown(page, upper.id)
        assertNull(plan.rejection)
        assertEquals(1600, plan.plan?.renderWidth)
    }

    @Test fun colorReplacementIsBakedOnceAndUndoRestoresTheSourceProperty() {
        val tinted = upper.copy(colorReplacement = LayerColorReplacement(0xffff0000L, 0xff0000ffL))
        val original = page.copy(layers = defaultInkLayers() + lower + tinted)
        val plan = requireNotNull(mergeDown(original, tinted.id).plan)
        assertNull(plan.resultPrototype.colorReplacement)
        val edit = RasterLayerMerge.edit(original, plan, reference)
        val merged = edit.apply(original, false)
        assertNull(merged.layers.last().colorReplacement)
        assertEquals(original, edit.apply(merged, true))
    }

    @Test fun mergeAndUndoRestoreBothLayerSettingsAndObjectsExactly() {
        val original = page.copy(
            layers = page.layers.map {
                if (it.id == upper.id) it.copy(opacity = .5f, alphaLock = true, mask = LayerMask()) else it
            },
            elements = listOf(PageElement.Text(id = "note", x = .1f, y = .1f, text = "Test", layerId = upper.id)),
        )
        val edit = RasterLayerMerge.edit(original, requireNotNull(mergeDown(original, upper.id).plan), reference)
        assertTrue(edit.canApply(original, undo = false))
        val merged = edit.apply(original, false)
        assertTrue(edit.canApply(merged, undo = true))
        assertFalse(merged.layers.any { it.id == upper.id })
        assertTrue(merged.elements.isEmpty())
        assertEquals(1f, merged.layers.last().opacity)
        assertNull(merged.layers.last().mask)
        assertEquals(original, edit.apply(merged, true))
        assertEquals(merged, edit.apply(edit.apply(merged, true), false))
    }

    @Test fun mergeDownSupportsBlendMasksAndClippingDependencies() {
        val base = lower.copy(id = "base")
        val clippedLower = lower.copy(id = "clipped-lower", clippedToBelow = true, blendMode = LayerBlendMode.MULTIPLY)
        val maskedUpper = upper.copy(mask = LayerMask(), blendMode = LayerBlendMode.SCREEN)
        val complex = page.copy(
            crop = PageCrop(.1f, .1f, .9f, .9f),
            layers = defaultInkLayers() + base + clippedLower + maskedUpper,
        )
        val plan = requireNotNull(RasterLayerMerge.plan(
            complex,
            RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_DOWN, maskedUpper.id),
        ).plan)
        assertEquals(setOf(base.id, clippedLower.id, maskedUpper.id), plan.removedLayerIds)
        assertEquals(PageCrop(), plan.renderPage.crop)
    }

    @Test fun selectedFolderConsumesItsCompleteTreeAndRestoresItInOneUndo() {
        val folder = InkLayer(id = "folder", name = "Group", role = LayerRole.FOLDER, opacity = .7f)
        val child = lower.copy(id = "child", parentId = folder.id)
        val outside = upper.copy(id = "outside")
        val original = page.copy(layers = defaultInkLayers() + folder + child + outside)
        val plan = requireNotNull(RasterLayerMerge.plan(
            original,
            RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_SELECTED, folder.id, setOf(folder.id, outside.id)),
        ).plan)
        assertTrue(setOf(folder.id, child.id, outside.id).all { it in plan.removedLayerIds })
        val edit = RasterLayerMerge.edit(original, plan, reference)
        val merged = edit.apply(original, false)
        assertEquals(1, merged.layers.count { it.role != LayerRole.BACKGROUND && it.id !in setOf(LayerIds.INK) })
        assertEquals(original, edit.apply(merged, true))
    }

    @Test fun visibleMergePreservesHiddenSourcesWhileVisibleCopyPreservesEverything() {
        val hidden = lower.copy(id = "hidden", visible = false)
        val visible = upper.copy(id = "visible")
        val original = page.copy(layers = defaultInkLayers() + hidden + visible)
        val merge = requireNotNull(RasterLayerMerge.plan(
            original,
            RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_VISIBLE),
        ).plan)
        assertTrue(visible.id in merge.removedLayerIds)
        assertFalse(hidden.id in merge.removedLayerIds)
        val copy = requireNotNull(RasterLayerMerge.plan(
            original,
            RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_VISIBLE_COPY),
        ).plan)
        assertTrue(copy.removedLayerIds.isEmpty())
        val copyEdit = RasterLayerMerge.edit(original, copy, reference)
        assertTrue(copyEdit.canApply(original, undo = false))
        val copied = copyEdit.apply(original, false)
        assertEquals(original.layers.size + 1, copied.layers.size)
        assertFalse(copyEdit.canApply(copied, undo = false))
        assertTrue(copyEdit.canApply(copied, undo = true))
        assertEquals(original, copyEdit.apply(copied, true))
    }

    @Test fun visibleMergeConsumesVisibleCorrectionsAndNeverLeavesAnAdjustmentRoleOnRasterResult() {
        val adjustment = InkLayer(id = "correction", name = "Brightness", role = LayerRole.ADJUSTMENT,
            adjustment = LayerAdjustment(brightness = .4f))
        val hiddenAdjustment = adjustment.copy(id = "hidden-correction", visible = false)
        val original = page.copy(layers = defaultInkLayers() + adjustment + upper + hiddenAdjustment)
        val visible = requireNotNull(RasterLayerMerge.plan(original,
            RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_VISIBLE)).plan)
        assertTrue(adjustment.id in visible.removedLayerIds)
        assertFalse(hiddenAdjustment.id in visible.removedLayerIds)
        val selected = requireNotNull(RasterLayerMerge.plan(original,
            RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_SELECTED, adjustment.id,
                setOf(adjustment.id, upper.id))).plan)
        assertEquals(LayerRole.CUSTOM, selected.resultPrototype.role)
        assertNull(selected.resultPrototype.adjustment)
        val baked = RasterLayerMerge.edit(original, selected, reference).apply(original, false)
        assertEquals(original, RasterLayerMerge.edit(original, selected, reference).apply(baked, true))

        val masked = original.copy(layers = original.layers.map {
            if (it.id == adjustment.id) it.copy(mask = LayerMask()) else it
        })
        assertEquals(RasterLayerMerge.Rejection.INVALID_SELECTION,
            RasterLayerMerge.plan(masked,
                RasterLayerMergeRequest(RasterLayerMergeOperation.APPLY_MASK, adjustment.id)).rejection)
    }

    @Test fun flattenBakesPaperAndHidesTheStructuralBackground() {
        val plan = requireNotNull(RasterLayerMerge.plan(
            page,
            RasterLayerMergeRequest(RasterLayerMergeOperation.FLATTEN),
        ).plan)
        assertTrue(plan.includeBackground)
        val flattened = RasterLayerMerge.edit(page, plan, reference).apply(page, false)
        assertFalse(flattened.layers.first { it.role == LayerRole.BACKGROUND }.visible)
        assertEquals(1, flattened.layers.count(InkLayer::isPaintSurfaceLayer))
    }

    @Test fun transferToLowerKeepsTheSelectedLayerAsAnEmptyEditableLayer() {
        val plan = requireNotNull(RasterLayerMerge.plan(
            page,
            RasterLayerMergeRequest(RasterLayerMergeOperation.TRANSFER_TO_LOWER, upper.id),
        ).plan)
        val edit = RasterLayerMerge.edit(page, plan, reference)
        val transferred = edit.apply(page, false)
        assertEquals(listOf(lower.id, upper.id), transferred.layers.takeLast(2).map(InkLayer::id))
        assertEquals(reference, transferred.layers.first { it.id == lower.id }.rasterTileSurface)
        assertNull(transferred.layers.first { it.id == upper.id }.rasterTileSurface)
        assertEquals(page, edit.apply(transferred, true))
    }

    @Test fun applyMaskBakesOneLayerOrCompleteFolderAndUndoRestoresStructure() {
        val mask = LayerMask(baseVisible = false)
        val folder = InkLayer(id = "masked-folder", name = "Masked group", role = LayerRole.FOLDER, mask = mask)
        val child = lower.copy(id = "masked-child", parentId = folder.id)
        val original = page.copy(layers = defaultInkLayers() + folder + child + upper)
        val plan = requireNotNull(RasterLayerMerge.plan(
            original,
            RasterLayerMergeRequest(RasterLayerMergeOperation.APPLY_MASK, folder.id),
        ).plan)
        assertEquals(setOf(folder.id, child.id), plan.removedLayerIds)
        assertNull(plan.resultPrototype.mask)
        assertEquals(LayerRole.CUSTOM, plan.resultPrototype.role)
        val baked = RasterLayerMerge.edit(original, plan, reference).apply(original, false)
        assertFalse(baked.layers.any { it.id == child.id })
        assertNull(baked.layers.first { it.id == folder.id }.mask)
        assertEquals(original, RasterLayerMerge.edit(original, plan, reference).apply(baked, true))

        val missing = RasterLayerMerge.plan(original,
            RasterLayerMergeRequest(RasterLayerMergeOperation.APPLY_MASK, upper.id))
        assertEquals(RasterLayerMerge.Rejection.INVALID_SELECTION, missing.rejection)
    }

    @Test fun protectedAndUnsupportedPrecisionEndWithExplicitReasons() {
        val locked = page.copy(layers = page.layers.map { if (it.id == upper.id) it.copy(locked = true) else it })
        assertEquals(RasterLayerMerge.Rejection.PROTECTED, mergeDown(locked, upper.id).rejection)
        val highPrecision = page.copy(layers = page.layers.map {
            if (it.id == upper.id) it.copy(rasterTileSurface = reference.copy(pixelFormat = "rgba16f-linear")) else it
        })
        assertEquals(RasterLayerMerge.Rejection.PIXEL_FORMAT, mergeDown(highPrecision, upper.id).rejection)
        val oversized = page.copy(width = 9000f, height = 9000f)
        assertEquals(RasterLayerMerge.Rejection.SIZE_LIMIT, mergeDown(oversized, upper.id).rejection)
    }

    @Test fun undoPreservesUnrelatedLayersAndObjects() {
        val edit = RasterLayerMerge.edit(page, requireNotNull(mergeDown(page, upper.id).plan), reference)
        val elsewhere = InkLayer(name = "Other")
        val objectElsewhere = PageElement.Text(x = .1f, y = .1f, text = "Unrelated", layerId = elsewhere.id)
        val changed = edit.apply(page, false).let {
            it.copy(layers = it.layers + elsewhere, elements = it.elements + objectElsewhere)
        }
        val restored = edit.apply(changed, true)
        assertEquals(elsewhere, restored.layers.last())
        assertTrue(objectElsewhere in restored.elements)
    }
}
