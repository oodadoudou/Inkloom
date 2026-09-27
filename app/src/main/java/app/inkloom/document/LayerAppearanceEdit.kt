package app.inkloom.document

/** A scoped appearance patch in the same chronological history as paint, never a page snapshot. */
sealed interface LayerAppearanceEdit {
    val layerId: String
    val isNoOp: Boolean
    fun apply(page: InkPage, undo: Boolean = false): InkPage?
}

/** Solo is a reversible visibility transaction, including the original hidden layers. */
data class LayerSoloVisibilityEdit(
    override val layerId: String,
    val before: Map<String, Boolean>,
    val after: Map<String, Boolean>,
    val beforeSoloLayerId: String?,
    val afterSoloLayerId: String?,
    val beforeRestore: Map<String, Boolean>,
    val afterRestore: Map<String, Boolean>,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after && beforeSoloLayerId == afterSoloLayerId

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val expected = if (undo) after else before
        val replacement = if (undo) before else after
        if (page.soloLayerId != if (undo) afterSoloLayerId else beforeSoloLayerId) return null
        if (page.soloVisibilityBefore != if (undo) afterRestore else beforeRestore) return null
        if (page.layers.any { expected[it.id]?.let { visible -> visible != it.visible } == true }) return null
        return page.copy(
            layers = page.layers.map { layer -> replacement[layer.id]?.let { layer.copy(visible = it) } ?: layer },
            soloLayerId = if (undo) beforeSoloLayerId else afterSoloLayerId,
            soloVisibilityBefore = if (undo) beforeRestore else afterRestore,
        )
    }

    companion object {
        fun create(page: InkPage, layerId: String): LayerSoloVisibilityEdit? {
            val target = page.layers.firstOrNull { it.id == layerId } ?: return null
            val before = page.layers.associate { it.id to it.visible }
            if (page.soloLayerId == layerId) {
                return LayerSoloVisibilityEdit(layerId, before,
                    before.mapValues { (id, current) -> page.soloVisibilityBefore[id] ?: current },
                    layerId, null, page.soloVisibilityBefore, emptyMap())
            }
            val original = if (page.soloLayerId == null) before else page.soloVisibilityBefore
            val forced = (LayerHierarchy.ancestors(page.layers, target).map { it.id } + target.id).toSet()
            val show = buildSet {
                add(target.id)
                LayerHierarchy.ancestors(page.layers, target).forEach { add(it.id) }
                if (target.role == LayerRole.FOLDER) page.layers.filter {
                    LayerHierarchy.ancestors(page.layers, it).any { ancestor -> ancestor.id == target.id }
                }.forEach { add(it.id) }
                page.layers.filter { it.role == LayerRole.BACKGROUND }.forEach { add(it.id) }
            }
            val after = before.mapValues { (id, _) -> id in show && (id in forced || original[id] != false) }
            return LayerSoloVisibilityEdit(layerId, before, after, page.soloLayerId, layerId,
                page.soloVisibilityBefore, original)
        }
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

data class LayerColorReplacementEdit(
    override val layerId: String,
    val before: LayerColorReplacement?,
    val after: LayerColorReplacement?,
) : LayerAppearanceEdit {
    override val isNoOp: Boolean get() = before == after

    override fun apply(page: InkPage, undo: Boolean): InkPage? {
        val expected = if (undo) after else before
        val replacement = if (undo) before else after
        val layer = page.layers.firstOrNull { it.id == layerId && it.role != LayerRole.BACKGROUND } ?: return null
        if (layer.colorReplacement != expected) return null
        return page.copy(layers = page.layers.map {
            if (it.id == layerId) it.copy(colorReplacement = replacement) else it
        })
    }
}
