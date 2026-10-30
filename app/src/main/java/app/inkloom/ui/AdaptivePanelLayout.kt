package app.inkloom.ui

internal enum class BrushLibraryLayout {
    AUTO,
    COMPACT,
    LIST,
    GRID,
}

internal enum class ToolLibraryLayout {
    LIST,
    GRID,
    COMPACT,
}

internal enum class ToolLibrarySort {
    DEFAULT,
    NAME,
}

internal object AdaptivePanelLayout {
    /** Keeps a docked palette useful without letting it swallow the document on a tablet. */
    fun sidePanelWidthDp(screenWidthDp: Int): Float =
        (screenWidthDp * 0.27f).coerceIn(240f, 420f)

    /** Clamps a user-resized dock without forcing it back to the automatic preset on recomposition. */
    fun dockedSidePanelWidthDp(requestedWidthDp: Float, workspaceWidthDp: Float, minimumWidthDp: Float): Float {
        val safeWorkspace = workspaceWidthDp.takeIf(Float::isFinite)?.coerceAtLeast(minimumWidthDp) ?: minimumWidthDp
        val maximum = (safeWorkspace * .62f).coerceIn(minimumWidthDp, 640f)
        val fallback = sidePanelWidthDp(safeWorkspace.toInt())
        return requestedWidthDp.takeIf(Float::isFinite)?.coerceIn(minimumWidthDp, maximum)
            ?: fallback.coerceIn(minimumWidthDp, maximum)
    }

    fun brushLayout(
        requested: BrushLibraryLayout,
        availableWidthDp: Float,
        bottomDocked: Boolean,
    ): BrushLibraryLayout {
        if (requested != BrushLibraryLayout.AUTO) return requested
        return when {
            bottomDocked || availableWidthDp >= 330f -> BrushLibraryLayout.GRID
            availableWidthDp < 270f -> BrushLibraryLayout.COMPACT
            else -> BrushLibraryLayout.LIST
        }
    }
}
