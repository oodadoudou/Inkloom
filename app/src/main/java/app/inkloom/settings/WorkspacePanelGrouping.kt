package app.inkloom.settings

object WorkspacePanelGrouping {
    const val MINIMUM_OVERLAP_RATIO = .42f

    fun target(
        draggedPanel: WorkspacePanelKind,
        draggedBounds: WorkspacePanelBounds,
        candidates: Map<WorkspacePanelKind, WorkspacePanelBounds>,
    ): WorkspacePanelKind? = candidates
        .asSequence()
        .filter { (panel, _) -> panel != draggedPanel }
        .map { (panel, bounds) -> panel to overlapRatio(draggedBounds, bounds) }
        .filter { (_, overlap) -> overlap >= MINIMUM_OVERLAP_RATIO }
        .sortedWith(compareByDescending<Pair<WorkspacePanelKind, Float>> { it.second }.thenBy { it.first.ordinal })
        .firstOrNull()
        ?.first

    fun overlapRatio(first: WorkspacePanelBounds, second: WorkspacePanelBounds): Float {
        val left = maxOf(first.xDp, second.xDp)
        val top = maxOf(first.yDp, second.yDp)
        val right = minOf(first.xDp + first.widthDp, second.xDp + second.widthDp)
        val bottom = minOf(first.yDp + first.heightDp, second.yDp + second.heightDp)
        val intersection = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
        val firstArea = first.widthDp.coerceAtLeast(0f) * first.heightDp.coerceAtLeast(0f)
        val secondArea = second.widthDp.coerceAtLeast(0f) * second.heightDp.coerceAtLeast(0f)
        val referenceArea = minOf(firstArea, secondArea)
        return if (referenceArea <= 0f || !intersection.isFinite()) 0f else (intersection / referenceArea).coerceIn(0f, 1f)
    }
}
