package app.inkloom.settings

data class FloatingToolbarPosition(val xDp: Float, val yDp: Float)

object FloatingToolbarPlacement {
    const val MARGIN_DP = 8f
    const val SHORT_EDGE_DP = 56f
    const val LONG_EDGE_DP = 360f

    fun clamp(
        xDp: Float,
        yDp: Float,
        containerWidthDp: Float,
        containerHeightDp: Float,
        vertical: Boolean,
    ): FloatingToolbarPosition {
        val paletteWidth = if (vertical) SHORT_EDGE_DP else LONG_EDGE_DP
        val paletteHeight = if (vertical) LONG_EDGE_DP else SHORT_EDGE_DP
        val maximumX = (containerWidthDp - paletteWidth - MARGIN_DP).coerceAtLeast(MARGIN_DP)
        val maximumY = (containerHeightDp - paletteHeight - MARGIN_DP).coerceAtLeast(MARGIN_DP)
        return FloatingToolbarPosition(
            xDp = finiteOrDefault(xDp, MARGIN_DP).coerceIn(MARGIN_DP, maximumX),
            yDp = finiteOrDefault(yDp, MARGIN_DP).coerceIn(MARGIN_DP, maximumY),
        )
    }

    fun defaultPosition(containerWidthDp: Float, containerHeightDp: Float, vertical: Boolean): FloatingToolbarPosition {
        val paletteWidth = if (vertical) SHORT_EDGE_DP else LONG_EDGE_DP
        val paletteHeight = if (vertical) LONG_EDGE_DP else SHORT_EDGE_DP
        return clamp(
            xDp = if (vertical) MARGIN_DP * 2f else (containerWidthDp - paletteWidth) / 2f,
            yDp = if (vertical) (containerHeightDp - paletteHeight) / 2f else MARGIN_DP * 2f,
            containerWidthDp = containerWidthDp,
            containerHeightDp = containerHeightDp,
            vertical = vertical,
        )
    }

    private fun finiteOrDefault(value: Float, fallback: Float) = if (value.isFinite()) value else fallback
}
