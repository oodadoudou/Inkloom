package app.inkloom.settings

enum class WorkspacePanelKind { PAGES, BRUSHES, LAYERS }

object WorkspacePanelTabs {
    private val defaultOrder = WorkspacePanelKind.entries

    fun normalizeOrder(raw: String): List<WorkspacePanelKind> {
        val decoded = raw.split(',').mapNotNull { value ->
            runCatching { WorkspacePanelKind.valueOf(value.trim()) }.getOrNull()
        }.distinct()
        return decoded + defaultOrder.filterNot(decoded::contains)
    }

    fun encodeOrder(values: List<WorkspacePanelKind>): String =
        (values.distinct() + defaultOrder.filterNot(values::contains)).joinToString(",") { it.name }

    fun move(raw: String, panel: WorkspacePanelKind, delta: Int): String {
        val order = normalizeOrder(raw).toMutableList()
        val from = order.indexOf(panel)
        if (from < 0) return encodeOrder(order)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from != to) {
            order.removeAt(from)
            order.add(to, panel)
        }
        return encodeOrder(order)
    }

    fun isStacked(settings: AppSettings, dock: WorkspacePanelDock): Boolean = when (dock) {
        WorkspacePanelDock.LEFT -> settings.leftDockStacked
        WorkspacePanelDock.RIGHT -> settings.rightDockStacked
        WorkspacePanelDock.BOTTOM -> settings.bottomDockStacked
        WorkspacePanelDock.FREE -> false
    }

    fun active(settings: AppSettings, dock: WorkspacePanelDock): WorkspacePanelKind = when (dock) {
        WorkspacePanelDock.LEFT -> settings.leftDockActivePanel
        WorkspacePanelDock.RIGHT -> settings.rightDockActivePanel
        WorkspacePanelDock.BOTTOM -> settings.bottomDockActivePanel
        WorkspacePanelDock.FREE -> WorkspacePanelKind.PAGES
    }

    fun withStacked(settings: AppSettings, dock: WorkspacePanelDock, stacked: Boolean): AppSettings = when (dock) {
        WorkspacePanelDock.LEFT -> settings.copy(leftDockStacked = stacked)
        WorkspacePanelDock.RIGHT -> settings.copy(rightDockStacked = stacked)
        WorkspacePanelDock.BOTTOM -> settings.copy(bottomDockStacked = stacked)
        WorkspacePanelDock.FREE -> settings
    }

    fun withActive(settings: AppSettings, dock: WorkspacePanelDock, panel: WorkspacePanelKind): AppSettings = when (dock) {
        WorkspacePanelDock.LEFT -> settings.copy(leftDockActivePanel = panel)
        WorkspacePanelDock.RIGHT -> settings.copy(rightDockActivePanel = panel)
        WorkspacePanelDock.BOTTOM -> settings.copy(bottomDockActivePanel = panel)
        WorkspacePanelDock.FREE -> settings
    }
}
