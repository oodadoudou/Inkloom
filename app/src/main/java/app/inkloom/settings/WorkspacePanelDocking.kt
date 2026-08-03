package app.inkloom.settings

object WorkspacePanelDocking {
    const val SNAP_DISTANCE_DP = 44f

    fun target(
        bounds: WorkspacePanelBounds,
        containerWidthDp: Float,
        containerHeightDp: Float,
        dragDeltaXDp: Float = 0f,
        dragDeltaYDp: Float = 0f,
    ): WorkspacePanelDock? {
        if (containerWidthDp <= 0f || containerHeightDp <= 0f) return null
        val leftDistance = (bounds.xDp - WorkspacePanelPlacement.MARGIN_DP).coerceAtLeast(0f)
        val rightDistance = (containerWidthDp - WorkspacePanelPlacement.MARGIN_DP - bounds.xDp - bounds.widthDp).coerceAtLeast(0f)
        val bottomDistance = (containerHeightDp - WorkspacePanelPlacement.MARGIN_DP - bounds.yDp - bounds.heightDp).coerceAtLeast(0f)
        if (kotlin.math.abs(dragDeltaXDp) > kotlin.math.abs(dragDeltaYDp)) {
            return when {
                dragDeltaXDp < 0f && leftDistance <= SNAP_DISTANCE_DP -> WorkspacePanelDock.LEFT
                dragDeltaXDp > 0f && rightDistance <= SNAP_DISTANCE_DP -> WorkspacePanelDock.RIGHT
                else -> null
            }
        }
        if (dragDeltaYDp > 0f) return WorkspacePanelDock.BOTTOM.takeIf { bottomDistance <= SNAP_DISTANCE_DP }
        return listOf(
            WorkspacePanelDock.BOTTOM to bottomDistance,
            WorkspacePanelDock.LEFT to leftDistance,
            WorkspacePanelDock.RIGHT to rightDistance,
        ).filter { (_, distance) -> distance <= SNAP_DISTANCE_DP }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    fun previewBounds(
        dock: WorkspacePanelDock,
        containerWidthDp: Float,
        containerHeightDp: Float,
    ): WorkspacePanelBounds? {
        if (dock == WorkspacePanelDock.FREE || containerWidthDp <= 0f || containerHeightDp <= 0f) return null
        val margin = WorkspacePanelPlacement.MARGIN_DP
        val usableWidth = (containerWidthDp - margin * 2f).coerceAtLeast(1f)
        val usableHeight = (containerHeightDp - margin * 2f).coerceAtLeast(1f)
        return when (dock) {
            WorkspacePanelDock.LEFT -> {
                val width = (containerWidthDp * .28f).coerceIn(180f.coerceAtMost(usableWidth), 360f.coerceAtMost(usableWidth))
                WorkspacePanelBounds(margin, margin, width, usableHeight)
            }
            WorkspacePanelDock.RIGHT -> {
                val width = (containerWidthDp * .28f).coerceIn(180f.coerceAtMost(usableWidth), 360f.coerceAtMost(usableWidth))
                WorkspacePanelBounds(containerWidthDp - margin - width, margin, width, usableHeight)
            }
            WorkspacePanelDock.BOTTOM -> {
                val height = (containerHeightDp * .34f).coerceIn(180f.coerceAtMost(usableHeight), 320f.coerceAtMost(usableHeight))
                WorkspacePanelBounds(margin, containerHeightDp - margin - height, usableWidth, height)
            }
            WorkspacePanelDock.FREE -> null
        }
    }
}
