package app.inkloom.ui

internal enum class BrushLibraryLayout {
    AUTO,
    COMPACT,
    LIST,
    GRID,
}

internal enum class BrushLibrarySort {
    CUSTOM,
    RECENT,
    NAME,
    SIZE,
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
