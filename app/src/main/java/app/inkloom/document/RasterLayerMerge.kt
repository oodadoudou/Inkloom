package app.inkloom.document

/** Eligibility is shared by the visible menu and the transaction boundary. */
object RasterLayerMerge {
    enum class Rejection { NO_LOWER_LAYER, PROTECTED, DIFFERENT_GROUP, HIDDEN_OR_DRAFT_MISMATCH, BLEND_MODE, CLIPPING, MASK_GEOMETRY, PIXEL_FORMAT, SIZE_LIMIT }
    fun rejection(page: InkPage, sourceId: String): Rejection? {
        val index = page.layers.indexOfFirst { it.id == sourceId }
        if (index <= 0) return Rejection.NO_LOWER_LAYER
        val source = page.layers[index]; val target = page.layers[index - 1]
        if (listOf(source, target).any { it.role in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) }) return Rejection.NO_LOWER_LAYER
        if (listOf(source, target).any { LayerHierarchy.effectiveLocked(page.layers, it) }) return Rejection.PROTECTED
        if (source.parentId != target.parentId) return Rejection.DIFFERENT_GROUP
        if (source.visible != target.visible || source.draft != target.draft) return Rejection.HIDDEN_OR_DRAFT_MISMATCH
        if (source.blendMode != LayerBlendMode.NORMAL || target.blendMode != LayerBlendMode.NORMAL) return Rejection.BLEND_MODE
        if (source.clippedToBelow || target.clippedToBelow || page.layers.getOrNull(index + 1)?.clippedToBelow == true) return Rejection.CLIPPING
        if (listOf(source, target).any { it.rasterTileSurface?.pixelFormat?.let { format -> format != "argb8888-premultiplied-srgb" } == true }) return Rejection.PIXEL_FORMAT
        if (listOf(source, target).any { it.mask?.enabled == true && (page.crop != PageCrop() || it.rasterRotationQuarterTurns.mod(4) != 0 || (it.mask.surface == null && !it.mask.baseVisible)) }) return Rejection.MASK_GEOMETRY
        val width = renderWidth(page, sourceId)
        val height = width * page.height.toDouble() / page.width
        if (width !in 1..4096 || !height.isFinite() || height < 1 || height > 4096 || width * height > 16_777_216) return Rejection.SIZE_LIMIT
        return null
    }
    fun renderWidth(page: InkPage, sourceId: String): Int {
        val index = page.layers.indexOfFirst { it.id == sourceId }
        if (index <= 0) return 0
        return page.layers.subList(index - 1, index + 1).mapNotNull { layer ->
            layer.rasterTileSurface?.let { if (layer.rasterRotationQuarterTurns.mod(2) == 0) it.logicalWidth else it.logicalHeight }
        }.maxOrNull() ?: page.width.toInt()
    }
    fun edit(page: InkPage, sourceId: String, merged: RasterTileSurfaceRef): RasterLayerMergeEdit {
        require(rejection(page, sourceId) == null)
        val index = page.layers.indexOfFirst { it.id == sourceId }
        val source = page.layers[index]; val target = page.layers[index - 1]
        return RasterLayerMergeEdit(source, target, target.copy(opacity = 1f, blendMode = LayerBlendMode.NORMAL,
            mask = null, alphaLock = false, alphaLockBaseContentIds = emptyList(), clippedToBelow = false,
            rasterTileSurface = merged, rasterRotationQuarterTurns = 0),
            page.elements.filter { it.layerId == source.id || it.layerId == target.id })
    }
}

data class RasterLayerMergeEdit(
    val source: InkLayer,
    val target: InkLayer,
    val merged: InkLayer,
    val originalElements: List<PageElement>,
) {
    fun apply(page: InkPage, undo: Boolean): InkPage {
        val targetIndex = page.layers.indexOfFirst { it.id == target.id }
        require(targetIndex >= 0) { "The merge target no longer exists" }
        val pairIds = setOf(source.id, target.id)
        val insertion = page.layers.take(targetIndex).count { it.id !in pairIds }
        val layers = page.layers.filterNot { it.id in pairIds }.toMutableList()
        layers.addAll(insertion, if (undo) listOf(target, source) else listOf(merged))
        return page.copy(layers = layers,
            elements = page.elements.filterNot { it.layerId in pairIds } + if (undo) originalElements else emptyList())
    }
}
