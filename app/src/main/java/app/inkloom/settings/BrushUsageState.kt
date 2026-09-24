package app.inkloom.settings

import kotlinx.serialization.Serializable

@Serializable
data class BrushLibraryScrollPosition(val index: Int = 0, val offset: Int = 0)

/** Library references only: using, pinning or clearing a brush never changes its preset. */
@Serializable
data class BrushUsageState(
    val recentIds: List<String> = emptyList(),
    val pinnedIds: List<String> = emptyList(),
    val category: String? = null,
    val categoryPins: Map<String, List<String>> = emptyMap(),
    val lastPaintBrushId: String? = null,
    val lastEraserBrushId: String? = null,
    val eraserUsesPaintTip: Boolean = false,
    val scrollPositions: Map<String, BrushLibraryScrollPosition> = emptyMap(),
    val categoryScroll: BrushLibraryScrollPosition = BrushLibraryScrollPosition(),
) {
    fun toggleCategoryPin(category: String, id: String): BrushUsageState {
        if (id.isBlank()) return this
        val old = categoryPins[category].orEmpty()
        val updated = if (id in old) old - id else listOf(id) + old
        return copy(categoryPins = if (updated.isEmpty()) categoryPins - category
            else categoryPins + (category to updated))
    }

    fun orderInCategory(category: String, ids: List<String>): List<String> {
        val available = ids.toHashSet()
        val pins = categoryPins[category].orEmpty().filter { it in available }
        val pinned = pins.toHashSet()
        return pins + ids.filterNot { it in pinned }
    }

    fun used(id: String, eraser: Boolean? = null): BrushUsageState {
        if (id.isBlank()) return this
        return copy(
            recentIds = if (id in pinnedIds) recentIds else (listOf(id) + recentIds).distinct().take(RECENT_COUNT),
            lastPaintBrushId = if (eraser == false) id else lastPaintBrushId,
            lastEraserBrushId = if (eraser == true) id else lastEraserBrushId,
            eraserUsesPaintTip = if (eraser == true) false else eraserUsesPaintTip,
        )
    }

    fun borrowPaintTipForEraser(id: String): BrushUsageState = copy(
        lastEraserBrushId = id,
        eraserUsesPaintTip = true,
        recentIds = (listOf(id) + recentIds).distinct().take(RECENT_COUNT),
    )

    fun pin(id: String): BrushUsageState = if (id.isBlank()) this else
        copy(pinnedIds = (listOf(id) + pinnedIds).distinct(), recentIds = recentIds - id)

    fun unpin(id: String): BrushUsageState = copy(pinnedIds = pinnedIds - id).used(id)

    fun clear(id: String): BrushUsageState = copy(pinnedIds = pinnedIds - id, recentIds = recentIds - id)

    fun displayedIds(available: Set<String>): List<String> =
        (pinnedIds + recentIds).distinct().filter { it in available }

    companion object {
        const val RECENT_COUNT = 8
        const val RECENT_CATEGORY = "@recent"
    }
}
