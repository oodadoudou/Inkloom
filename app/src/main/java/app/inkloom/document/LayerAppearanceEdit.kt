package app.inkloom.document

/** A scoped appearance patch in the same chronological history as paint, never a page snapshot. */
sealed interface LayerAppearanceEdit {
    val layerId: String
    val isNoOp: Boolean
    fun apply(page: InkPage, undo: Boolean = false): InkPage?
}

/** One persisted raster filter result; the original checkpoint remains available for undo. */
data class LayerRasterFilterEdit(
    override val layerId: String,
    val before: RasterTileSurfaceRef?,
    val after: RasterTileSurfaceRef,
    val beforeRotation: Int,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after && beforeRotation == 0

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val layer = page.layers.firstOrNull { it.id == layerId && it.isPaintSurfaceLayer() } ?: return null
        val expected = if (undo) after else before
        val expectedRotation = if (undo) 0 else beforeRotation.mod(4)
        if (layer.rasterTileSurface != expected || layer.rasterRotationQuarterTurns != expectedRotation) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(rasterTileSurface = if (undo) before else after,
                rasterRotationQuarterTurns = if (undo) beforeRotation.mod(4) else 0)
            else it
        })
    }
}

/** Parameter edits retain the source pixels and can be previewed before one history commit. */
data class LayerAdjustmentEdit(
    override val layerId: String,
    val before: LayerAdjustment,
    val after: LayerAdjustment,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val layer = page.layers.firstOrNull { it.id == layerId && it.role == LayerRole.ADJUSTMENT } ?: return null
        if (layer.adjustment != if (undo) after else before) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(adjustment = if (undo) before else after) else it
        })
    }
}

data class LayerBlendEdit(
    override val layerId: String,
    val before: LayerBlendMode,
    val after: LayerBlendMode,
    val beforePassThrough: Boolean = false,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after && !beforePassThrough

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return null
        if (layer.role == LayerRole.BACKGROUND) return null
        if (layer.blendMode != if (undo) after else before) return null
        if (layer.passThrough != if (undo) false else beforePassThrough) return null
        if (undo && beforePassThrough && !LayerGroupPolicy.canPassThrough(page.layers, layerId)) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(blendMode = if (undo) before else after, passThrough = undo && beforePassThrough) else it
        })
    }
}

data class LayerPaletteColorEdit(
    override val layerId: String,
    val before: LayerPaletteColor,
    val after: LayerPaletteColor,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val expected = if (undo) after else before
        val replacement = if (undo) before else after
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return null
        if (layer.paletteColor != expected) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(paletteColor = replacement) else it
        })
    }
}
