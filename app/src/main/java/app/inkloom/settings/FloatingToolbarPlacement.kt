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
        measuredWidthDp: Float = if (vertical) SHORT_EDGE_DP else LONG_EDGE_DP,
        measuredHeightDp: Float = if (vertical) LONG_EDGE_DP else SHORT_EDGE_DP,
    ): FloatingToolbarPosition {
        val paletteWidth = finitePositiveOrDefault(measuredWidthDp, if (vertical) SHORT_EDGE_DP else LONG_EDGE_DP)
        val paletteHeight = finitePositiveOrDefault(measuredHeightDp, if (vertical) LONG_EDGE_DP else SHORT_EDGE_DP)
        val maximumX = (containerWidthDp - paletteWidth - MARGIN_DP).coerceAtLeast(MARGIN_DP)
        val maximumY = (containerHeightDp - paletteHeight - MARGIN_DP).coerceAtLeast(MARGIN_DP)
        return FloatingToolbarPosition(
            xDp = finiteOrDefault(xDp, MARGIN_DP).coerceIn(MARGIN_DP, maximumX),
            yDp = finiteOrDefault(yDp, MARGIN_DP).coerceIn(MARGIN_DP, maximumY),
        )
    }

    /** Edge docking is a consequence of a drag, never a separate location menu. */
    fun snapTarget(position: FloatingToolbarPosition, containerWidthDp: Float, containerHeightDp: Float,
                   vertical: Boolean, measuredWidthDp: Float, measuredHeightDp: Float,
                   toleranceDp: Float = 20f): ToolbarDock {
        val width = finitePositiveOrDefault(measuredWidthDp, if (vertical) SHORT_EDGE_DP else LONG_EDGE_DP)
        val height = finitePositiveOrDefault(measuredHeightDp, if (vertical) LONG_EDGE_DP else SHORT_EDGE_DP)
        val distances = if (vertical) listOf(
            ToolbarDock.LEFT to kotlin.math.abs(position.xDp - MARGIN_DP),
            ToolbarDock.RIGHT to kotlin.math.abs(containerWidthDp - MARGIN_DP - position.xDp - width),
        ) else listOf(
            ToolbarDock.TOP to kotlin.math.abs(position.yDp - MARGIN_DP),
            ToolbarDock.BOTTOM to kotlin.math.abs(containerHeightDp - MARGIN_DP - position.yDp - height),
        )
        val closest = distances.minByOrNull { it.second } ?: return ToolbarDock.FREE
        return if (closest.second <= toleranceDp &&
            (if (vertical) containerWidthDp >= width + 2 * MARGIN_DP else containerHeightDp >= height + 2 * MARGIN_DP))
            closest.first else ToolbarDock.FREE
    }

    fun dockOrigin(dock: ToolbarDock, containerWidthDp: Float, containerHeightDp: Float,
                   measuredWidthDp: Float, measuredHeightDp: Float): FloatingToolbarPosition = when (dock) {
        ToolbarDock.LEFT -> FloatingToolbarPosition(MARGIN_DP * 2f, (containerHeightDp - measuredHeightDp) / 2f)
        ToolbarDock.RIGHT -> FloatingToolbarPosition(containerWidthDp - measuredWidthDp - MARGIN_DP * 2f,
            (containerHeightDp - measuredHeightDp) / 2f)
        ToolbarDock.TOP -> FloatingToolbarPosition((containerWidthDp - measuredWidthDp) / 2f, MARGIN_DP * 2f)
        ToolbarDock.BOTTOM -> FloatingToolbarPosition((containerWidthDp - measuredWidthDp) / 2f,
            containerHeightDp - measuredHeightDp - MARGIN_DP * 2f)
        ToolbarDock.FREE -> FloatingToolbarPosition(MARGIN_DP, MARGIN_DP)
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
    private fun finitePositiveOrDefault(value: Float, fallback: Float) = if (value.isFinite() && value > 0f) value else fallback
}
