package app.inkloom.document

/** Keeps navigation independent for pages with different aspect ratios and crops. */
class PageViewportStore {
    private val states = mutableMapOf<String, ViewportState>()

    fun save(pageId: String, state: ViewportState) {
        if (pageId.isNotBlank()) states[pageId] = ViewportTransform.sanitize(state)
    }

    fun restore(pageId: String): ViewportState = states[pageId]?.let(ViewportTransform::sanitize) ?: DEFAULT

    fun retainPages(pageIds: Set<String>) {
        states.keys.retainAll(pageIds)
    }

    companion object { val DEFAULT = ViewportState(1f, 0f, 0f, 0f) }
}
