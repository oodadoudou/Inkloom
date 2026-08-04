package app.inkloom.settings

data class WorkspacePanelBounds(
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)

object WorkspacePanelPlacement {
    const val MARGIN_DP = 8f
    const val MIN_HEIGHT_DP = 220f
    const val MAX_HEIGHT_DP = 1_000f

    fun clamp(
        bounds: WorkspacePanelBounds,
        containerWidthDp: Float,
        containerHeightDp: Float,
        minimumWidthDp: Float,
        maximumWidthDp: Float = 520f,
    ) = clamp(
        bounds.xDp,
        bounds.yDp,
        bounds.widthDp,
        bounds.heightDp,
        containerWidthDp,
        containerHeightDp,
        minimumWidthDp,
        maximumWidthDp,
    )

    fun clamp(
        xDp: Float,
        yDp: Float,
        widthDp: Float,
        heightDp: Float,
        containerWidthDp: Float,
        containerHeightDp: Float,
        minimumWidthDp: Float,
        maximumWidthDp: Float = 520f,
    ): WorkspacePanelBounds {
        val availableWidth = (finite(containerWidthDp, minimumWidthDp + MARGIN_DP * 2f) - MARGIN_DP * 2f).coerceAtLeast(1f)
        val availableHeight = (finite(containerHeightDp, MIN_HEIGHT_DP + MARGIN_DP * 2f) - MARGIN_DP * 2f).coerceAtLeast(1f)
        val minimumWidth = minimumWidthDp.coerceAtMost(availableWidth)
        val maximumWidth = maximumWidthDp.coerceAtMost(availableWidth).coerceAtLeast(minimumWidth)
        val minimumHeight = MIN_HEIGHT_DP.coerceAtMost(availableHeight)
        val maximumHeight = MAX_HEIGHT_DP.coerceAtMost(availableHeight).coerceAtLeast(minimumHeight)
        val width = finite(widthDp, minimumWidth).coerceIn(minimumWidth, maximumWidth)
        val height = finite(heightDp, minimumHeight).coerceIn(minimumHeight, maximumHeight)
        val maximumX = (containerWidthDp - width - MARGIN_DP).coerceAtLeast(MARGIN_DP)
        val maximumY = (containerHeightDp - height - MARGIN_DP).coerceAtLeast(MARGIN_DP)
        return WorkspacePanelBounds(
            xDp = finite(xDp, MARGIN_DP).coerceIn(MARGIN_DP, maximumX),
            yDp = finite(yDp, MARGIN_DP).coerceIn(MARGIN_DP, maximumY),
            widthDp = width,
            heightDp = height,
        )
    }

    fun move(bounds: WorkspacePanelBounds, deltaXDp: Float, deltaYDp: Float, containerWidthDp: Float, containerHeightDp: Float, minimumWidthDp: Float) =
        clamp(bounds.xDp + deltaXDp, bounds.yDp + deltaYDp, bounds.widthDp, bounds.heightDp, containerWidthDp, containerHeightDp, minimumWidthDp)

    fun resize(bounds: WorkspacePanelBounds, deltaWidthDp: Float, deltaHeightDp: Float, containerWidthDp: Float, containerHeightDp: Float, minimumWidthDp: Float) =
        clamp(bounds.xDp, bounds.yDp, bounds.widthDp + deltaWidthDp, bounds.heightDp + deltaHeightDp, containerWidthDp, containerHeightDp, minimumWidthDp)

    /** Resizes from any combination of edges. A zero delta keeps that edge fixed,
     * so dragging a left or top handle does not make the opposite corner jump. */
    fun resizeEdges(
        bounds: WorkspacePanelBounds,
        leftDeltaDp: Float,
        topDeltaDp: Float,
        rightDeltaDp: Float,
        bottomDeltaDp: Float,
        containerWidthDp: Float,
        containerHeightDp: Float,
        minimumWidthDp: Float,
    ): WorkspacePanelBounds {
        val left = finite(bounds.xDp + leftDeltaDp, bounds.xDp)
        val top = finite(bounds.yDp + topDeltaDp, bounds.yDp)
        val right = finite(bounds.xDp + bounds.widthDp + rightDeltaDp, bounds.xDp + bounds.widthDp)
        val bottom = finite(bounds.yDp + bounds.heightDp + bottomDeltaDp, bounds.yDp + bounds.heightDp)
        val resized = clamp(
            xDp = left,
            yDp = top,
            widthDp = right - left,
            heightDp = bottom - top,
            containerWidthDp = containerWidthDp,
            containerHeightDp = containerHeightDp,
            minimumWidthDp = minimumWidthDp,
        )
        val horizontal = if (leftDeltaDp != 0f && rightDeltaDp == 0f) {
            val anchoredRight = bounds.xDp + bounds.widthDp
            resized.copy(xDp = anchoredRight - resized.widthDp)
        } else resized
        val vertical = if (topDeltaDp != 0f && bottomDeltaDp == 0f) {
            val anchoredBottom = bounds.yDp + bounds.heightDp
            horizontal.copy(yDp = anchoredBottom - horizontal.heightDp)
        } else horizontal
        return clamp(vertical, containerWidthDp, containerHeightDp, minimumWidthDp)
    }

    private fun finite(value: Float, fallback: Float) = if (value.isFinite()) value else fallback
}
