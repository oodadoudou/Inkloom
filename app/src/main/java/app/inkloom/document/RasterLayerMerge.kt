package app.inkloom.document

import java.util.UUID

enum class RasterLayerMergeOperation {
    MERGE_DOWN,
    MERGE_SELECTED,
    MERGE_VISIBLE,
    MERGE_VISIBLE_COPY,
    FLATTEN,
    TRANSFER_TO_LOWER,
    /** Bakes one layer or folder subtree together with its enabled mask into editable pixels. */
    APPLY_MASK,
}

data class RasterLayerMergeRequest(
    val operation: RasterLayerMergeOperation,
    val primaryLayerId: String? = null,
    val selectedLayerIds: Set<String> = emptySet(),
)

/** Pure structural planning. Pixel rendering and publication remain one guarded transaction. */
object RasterLayerMerge {
    enum class Rejection {
        NO_LOWER_LAYER,
        NOT_ENOUGH_LAYERS,
        NO_VISIBLE_LAYERS,
        PROTECTED,
        INVALID_SELECTION,
        PIXEL_FORMAT,
        SIZE_LIMIT,
    }

    data class Plan(
        val request: RasterLayerMergeRequest,
        val sourceLayerIds: Set<String>,
        val removedLayerIds: Set<String>,
        val renderPage: InkPage,
        val includeBackground: Boolean,
        val resultPrototype: InkLayer,
        val insertionIndex: Int,
        val renderWidth: Int,
        val backgroundBefore: InkLayer? = null,
        val backgroundAfter: InkLayer? = null,
    )

    data class PlanningResult(val plan: Plan? = null, val rejection: Rejection? = null) {
        init { require((plan == null) != (rejection == null)) }
    }

    fun plan(page: InkPage, request: RasterLayerMergeRequest): PlanningResult {
        val byId = page.layers.associateBy(InkLayer::id)
        val initial = when (request.operation) {
            RasterLayerMergeOperation.APPLY_MASK -> {
                val target = request.primaryLayerId?.let(byId::get)
                    ?: return rejected(Rejection.INVALID_SELECTION)
                if (target.role in setOf(LayerRole.BACKGROUND, LayerRole.ADJUSTMENT) || target.mask == null)
                    return rejected(Rejection.INVALID_SELECTION)
                setOf(target.id)
            }
            RasterLayerMergeOperation.MERGE_DOWN,
            RasterLayerMergeOperation.TRANSFER_TO_LOWER -> {
                val source = request.primaryLayerId?.let(byId::get)
                    ?: return rejected(Rejection.NO_LOWER_LAYER)
                if (source.role == LayerRole.BACKGROUND) return rejected(Rejection.NO_LOWER_LAYER)
                val siblings = page.layers.filter { it.parentId == source.parentId && it.role != LayerRole.BACKGROUND }
                val index = siblings.indexOfFirst { it.id == source.id }
                if (index <= 0) return rejected(Rejection.NO_LOWER_LAYER)
                setOf(siblings[index - 1].id, source.id)
            }
            RasterLayerMergeOperation.MERGE_SELECTED ->
                (request.selectedLayerIds + listOfNotNull(request.primaryLayerId)).filterTo(linkedSetOf()) {
                    byId[it]?.role != LayerRole.BACKGROUND
                }.takeIf { it.size >= 2 } ?: return rejected(Rejection.NOT_ENOUGH_LAYERS)
            RasterLayerMergeOperation.MERGE_VISIBLE,
            RasterLayerMergeOperation.MERGE_VISIBLE_COPY -> page.layers.filterTo(linkedSetOf()) {
                (it.isPaintSurfaceLayer() || it.role == LayerRole.ADJUSTMENT) &&
                    LayerHierarchy.effectiveVisible(page.layers, it) && !it.draft
            }.mapTo(linkedSetOf(), InkLayer::id).takeIf(Set<String>::isNotEmpty)
                ?: return rejected(Rejection.NO_VISIBLE_LAYERS)
            RasterLayerMergeOperation.FLATTEN -> page.layers.filterTo(linkedSetOf()) {
                it.role != LayerRole.BACKGROUND
            }.mapTo(linkedSetOf(), InkLayer::id).takeIf(Set<String>::isNotEmpty)
                ?: return rejected(Rejection.NO_VISIBLE_LAYERS)
        }
        if (initial.any { it !in byId }) return rejected(Rejection.INVALID_SELECTION)

        val expanded = expandFolders(page.layers, initial).toMutableSet()
        // A clipped layer cannot be baked without its clipping base. Consume that dependency too,
        // so the result does not duplicate the base or silently lose the clipped pixels.
        var changed: Boolean
        do {
            changed = false
            page.layers.filter { it.id in expanded && it.clippedToBelow }.forEach { clipped ->
                val siblings = page.layers.filter { it.parentId == clipped.parentId && it.role != LayerRole.BACKGROUND }
                val index = siblings.indexOfFirst { it.id == clipped.id }
                val base = (index - 1 downTo 0).map(siblings::get).firstOrNull { !it.clippedToBelow }
                if (base != null && expanded.addAll(expandFolders(page.layers, setOf(base.id)))) changed = true
            }
        } while (changed)

        val copy = request.operation == RasterLayerMergeOperation.MERGE_VISIBLE_COPY
        val removed = if (copy) emptySet() else expanded.toSet()
        if (removed.mapNotNull(byId::get).any { LayerHierarchy.effectiveLocked(page.layers, it) })
            return rejected(Rejection.PROTECTED)
        if (expanded.mapNotNull(byId::get).filter(InkLayer::isPaintSurfaceLayer).any {
                it.rasterTileSurface?.pixelFormat?.let { format -> format != "argb8888-premultiplied-srgb" } == true
            }) return rejected(Rejection.PIXEL_FORMAT)

        val selectedRoots = expanded.mapNotNull(byId::get).filter { it.parentId !in expanded }
        val commonParent = selectedRoots.map(InkLayer::parentId).distinct().singleOrNull()
        val resultParent = when (request.operation) {
            RasterLayerMergeOperation.MERGE_DOWN,
            RasterLayerMergeOperation.TRANSFER_TO_LOWER,
            RasterLayerMergeOperation.MERGE_SELECTED,
            RasterLayerMergeOperation.APPLY_MASK -> commonParent
            else -> null
        }
        val bottom = expanded.mapNotNull { id ->
            page.layers.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { it to page.layers[it] }
        }.minByOrNull { it.first } ?: return rejected(Rejection.INVALID_SELECTION)
        val resultId = if (copy) UUID.randomUUID().toString() else bottom.second.id
        val resultName = when (request.operation) {
            RasterLayerMergeOperation.MERGE_VISIBLE_COPY -> "Visible copy"
            RasterLayerMergeOperation.FLATTEN -> "Flattened image"
            RasterLayerMergeOperation.APPLY_MASK -> bottom.second.name
            else -> bottom.second.name
        }
        val insertionIndex = if (resultParent == null && request.operation !in setOf(
                RasterLayerMergeOperation.MERGE_DOWN,
                RasterLayerMergeOperation.TRANSFER_TO_LOWER,
                RasterLayerMergeOperation.MERGE_SELECTED,
                RasterLayerMergeOperation.APPLY_MASK,
            )) page.layers.count { it.id !in removed }
            else page.layers.take(bottom.first).count { it.id !in removed }

        val isolatedLayers = page.layers.filter { it.id in expanded }.map { layer ->
            layer.copy(parentId = layer.parentId.takeIf { it in expanded })
        }
        val renderPage = when (request.operation) {
            RasterLayerMergeOperation.MERGE_VISIBLE,
            RasterLayerMergeOperation.MERGE_VISIBLE_COPY,
            RasterLayerMergeOperation.FLATTEN -> page
            else -> page.copy(layers = page.layers.filter { it.role == LayerRole.BACKGROUND } + isolatedLayers)
        }.copy(crop = PageCrop())
        val width = renderWidth(page, expanded)
        val height = (width * page.height.toDouble() / page.width.coerceAtLeast(1f)).toInt()
        if (width !in 1..8192 || height !in 1..8192 || width.toLong() * height > 67_108_864L)
            return rejected(Rejection.SIZE_LIMIT)

        val background = page.layers.firstOrNull { it.role == LayerRole.BACKGROUND }
        val flatten = request.operation == RasterLayerMergeOperation.FLATTEN
        val prototype = bottom.second.copy(
            id = resultId,
            name = resultName,
            role = bottom.second.role.takeIf { it != LayerRole.FOLDER && it != LayerRole.ADJUSTMENT } ?: LayerRole.CUSTOM,
            adjustment = null,
            rasterTileSurface = null,
            rasterRotationQuarterTurns = 0,
            visible = true,
            locked = false,
            opacity = 1f,
            blendMode = LayerBlendMode.NORMAL,
            alphaLock = false,
            alphaLockBaseContentIds = emptyList(),
            clippedToBelow = false,
            mask = null,
            parentId = resultParent,
            collapsed = false,
            passThrough = false,
            colorReplacement = null,
        )
        return PlanningResult(Plan(
            request = request,
            sourceLayerIds = expanded,
            removedLayerIds = removed,
            renderPage = renderPage,
            includeBackground = flatten,
            resultPrototype = prototype,
            insertionIndex = insertionIndex,
            renderWidth = width,
            backgroundBefore = background.takeIf { flatten },
            backgroundAfter = background?.copy(visible = false).takeIf { flatten },
        ))
    }

    fun renderWidth(page: InkPage, layerIds: Set<String>): Int = maxOf(
        page.width.toInt().coerceAtLeast(1),
        layerIds.mapNotNull { id -> page.layers.firstOrNull { it.id == id }?.let { layer ->
            layer.rasterTileSurface?.let { if (layer.rasterRotationQuarterTurns.mod(2) == 0) it.logicalWidth else it.logicalHeight }
        } }.maxOrNull() ?: 0,
    )

    fun edit(page: InkPage, plan: Plan, merged: RasterTileSurfaceRef): RasterLayerMergeEdit {
        val result = plan.resultPrototype.copy(rasterTileSurface = merged)
        val removedLayers = page.layers.mapIndexedNotNull { index, layer ->
            LayerAt(index, layer).takeIf { layer.id in plan.removedLayerIds }
        }
        val removedElements = page.elements.mapIndexedNotNull { index, element ->
            ElementAt(index, element).takeIf { element.layerId in plan.removedLayerIds }
        }
        val additionalAfterLayers = if (plan.request.operation == RasterLayerMergeOperation.TRANSFER_TO_LOWER) {
            val source = requireNotNull(plan.request.primaryLayerId?.let { id -> page.layers.firstOrNull { it.id == id } })
            val sourceIndex = page.layers.indexOfFirst { it.id == source.id }
            val afterIndex = page.layers.take(sourceIndex).count { it.id !in plan.removedLayerIds } + 1
            listOf(LayerAt(afterIndex, source.copy(
                rasterTileSurface = null,
                rasterRotationQuarterTurns = 0,
                alphaLock = false,
                alphaLockBaseContentIds = emptyList(),
            )))
        } else emptyList()
        return RasterLayerMergeEdit(
            operation = plan.request.operation,
            removedLayers = removedLayers,
            removedElements = removedElements,
            merged = result,
            insertionIndex = plan.insertionIndex,
            additionalAfterLayers = additionalAfterLayers,
            backgroundBefore = plan.backgroundBefore,
            backgroundAfter = plan.backgroundAfter,
        )
    }

    private fun expandFolders(layers: List<InkLayer>, ids: Set<String>): Set<String> {
        val result = ids.toMutableSet()
        var changed: Boolean
        do {
            changed = false
            layers.filter { it.parentId in result }.forEach { if (result.add(it.id)) changed = true }
        } while (changed)
        return result
    }

    private fun rejected(reason: Rejection) = PlanningResult(rejection = reason)
}

data class LayerAt(val index: Int, val layer: InkLayer)
data class ElementAt(val index: Int, val element: PageElement)

data class RasterLayerMergeEdit(
    val operation: RasterLayerMergeOperation,
    val removedLayers: List<LayerAt>,
    val removedElements: List<ElementAt>,
    val merged: InkLayer,
    val insertionIndex: Int,
    val additionalAfterLayers: List<LayerAt> = emptyList(),
    val backgroundBefore: InkLayer? = null,
    val backgroundAfter: InkLayer? = null,
) {
    /** A visible copy has no removed source, so its new ID must be absent before Apply. */
    fun canApply(page: InkPage, undo: Boolean): Boolean {
        val presentIds = page.layers.mapTo(hashSetOf(), InkLayer::id)
        return if (undo) merged.id in presentIds else
            removedLayers.all { it.layer.id in presentIds } &&
                (removedLayers.isNotEmpty() || merged.id !in presentIds)
    }

    fun apply(page: InkPage, undo: Boolean): InkPage = if (undo) undo(page) else forward(page)

    private fun forward(page: InkPage): InkPage {
        val removedIds = removedLayers.mapTo(hashSetOf()) { it.layer.id }
        require(removedIds.isEmpty() || removedIds.all { id -> page.layers.any { it.id == id } }) {
            "One or more merge sources no longer exist"
        }
        val afterIds = additionalAfterLayers.mapTo(hashSetOf()) { it.layer.id }
        val layers = page.layers.filterNot { it.id in removedIds || it.id == merged.id || it.id in afterIds }.toMutableList()
        backgroundBefore?.let { before ->
            val index = layers.indexOfFirst { it.id == before.id }
            if (index >= 0) layers[index] = requireNotNull(backgroundAfter)
        }
        layers.add(insertionIndex.coerceIn(0, layers.size), merged)
        additionalAfterLayers.sortedBy(LayerAt::index).forEach { entry ->
            layers.add(entry.index.coerceIn(0, layers.size), entry.layer)
        }
        return page.copy(
            layers = layers,
            elements = page.elements.filterNot { it.layerId in removedIds },
        )
    }

    private fun undo(page: InkPage): InkPage {
        val afterIds = additionalAfterLayers.mapTo(hashSetOf()) { it.layer.id }
        val layers = page.layers.filterNot { it.id == merged.id || it.id in afterIds }.toMutableList()
        backgroundAfter?.let { after ->
            val index = layers.indexOfFirst { it.id == after.id }
            if (index >= 0) layers[index] = requireNotNull(backgroundBefore)
        }
        removedLayers.sortedBy(LayerAt::index).forEach { entry ->
            layers.removeAll { it.id == entry.layer.id }
            layers.add(entry.index.coerceIn(0, layers.size), entry.layer)
        }
        val elements = page.elements.toMutableList()
        removedElements.sortedBy(ElementAt::index).forEach { entry ->
            elements.removeAll { it.id == entry.element.id }
            elements.add(entry.index.coerceIn(0, elements.size), entry.element)
        }
        return page.copy(layers = layers, elements = elements)
    }
}
