package app.inkloom.document

import java.util.UUID

object LayerMaskOperations {
    fun execute(
        page: InkPage,
        layerId: String,
        command: LayerMaskCommand,
        selectionRegions: List<List<StrokePoint>> = emptyList(),
        nextId: () -> String = { UUID.randomUUID().toString() },
    ): InkPage {
        val layer = page.layers.firstOrNull { it.id == layerId } ?: return page
        if (layer.role in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) return page
        val regions = selectionRegions.mapNotNull { points -> points.takeIf { it.size >= 3 }?.let(::normalizedPoints) }
        if (command == LayerMaskCommand.DELETE) {
            if (layer.mask == null) return page
            return page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(mask = null) else it })
        }
        val updatedMask = when (command) {
            LayerMaskCommand.CREATE_MASK_SELECTION -> LayerMask(
                baseVisible = true,
                regions = regions.map { LayerMaskRegion(it, reveal = false) },
            )
            LayerMaskCommand.CREATE_MASK_OUTSIDE_SELECTION -> LayerMask(
                baseVisible = false,
                regions = regions.map { LayerMaskRegion(it, reveal = true) },
            )
            LayerMaskCommand.HIDE_SELECTION -> layer.mask?.copy(
                regions = layer.mask.regions + regions.map { LayerMaskRegion(it, reveal = false) },
            )
            LayerMaskCommand.REVEAL_SELECTION -> layer.mask?.copy(
                regions = layer.mask.regions + regions.map { LayerMaskRegion(it, reveal = true) },
            )
            LayerMaskCommand.TOGGLE_ENABLED -> layer.mask?.copy(enabled = !layer.mask.enabled)
            LayerMaskCommand.TOGGLE_LINKED -> layer.mask?.copy(linked = !layer.mask.linked)
            LayerMaskCommand.INVERT -> layer.mask?.copy(
                baseVisible = !layer.mask.baseVisible,
                regions = layer.mask.regions.map { it.copy(reveal = !it.reveal) },
            )
            LayerMaskCommand.LOAD_AS_SELECTION -> return page
            LayerMaskCommand.DELETE -> error("handled above")
            LayerMaskCommand.APPLY -> return applyMask(page, layerId, layer.mask, nextId)
        } ?: return page
        return page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(mask = updatedMask) else it })
    }

    fun visible(mask: LayerMask?, point: StrokePoint): Boolean {
        if (mask == null || !mask.enabled) return true
        var alpha = if (mask.baseVisible) 1f else 0f
        mask.regions.forEach { region ->
            if (LassoGeometry.contains(region.points, point)) {
                alpha = if (region.reveal) region.opacity.coerceIn(0f, 1f) else 1f - region.opacity.coerceIn(0f, 1f)
            }
        }
        return alpha >= .5f
    }

    private fun applyMask(page: InkPage, layerId: String, mask: LayerMask?, nextId: () -> String): InkPage {
        if (mask == null) return page
        if (!mask.enabled) return page.copy(layers = page.layers.map { if (it.id == layerId) it.copy(mask = null) else it })
        val retainedStrokes = page.strokes.flatMap { stroke ->
            if (stroke.layerId != layerId) listOf(stroke) else {
                val pieces = mask.regions.fold(listOf(stroke)) { current, region ->
                    current.flatMap { candidate -> LassoGeometry.partition(region.points, candidate, nextId).pieces.map(LassoStrokePiece::stroke) }
                }
                pieces.filter { piece -> piece.points.getOrNull(piece.points.lastIndex / 2)?.let { visible(mask, it) } == true }
            }
        }
        val retainedElements = page.elements.filter { element ->
            element.layerId != layerId || visible(mask, StrokePoint(element.x + element.width / page.width.coerceAtLeast(1f) / 2f, element.y + element.height / page.height.coerceAtLeast(1f) / 2f))
        }
        return page.copy(
            layers = page.layers.map { if (it.id == layerId) it.copy(mask = null) else it },
            strokes = retainedStrokes,
            elements = retainedElements,
        )
    }

    private fun normalizedPoints(points: List<StrokePoint>): List<StrokePoint> = points.map { point ->
        point.copy(x = point.x.coerceIn(0f, 1f), y = point.y.coerceIn(0f, 1f))
    }
}
