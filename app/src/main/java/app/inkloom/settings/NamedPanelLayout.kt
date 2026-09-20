package app.inkloom.settings

import kotlinx.serialization.Serializable

@Serializable
data class ToolRailLayout(
    val visible: Boolean,
    val vertical: Boolean,
    val dock: String,
    val xDp: Float,
    val yDp: Float,
) {
    fun applyTo(settings: AppSettings): AppSettings = settings.copy(
        showToolbar = visible,
        verticalToolbar = vertical,
        toolbarDock = ToolbarDock.entries.firstOrNull { it.name == dock } ?: ToolbarDock.FREE,
        toolbarOffsetXDp = xDp.takeIf { it.isFinite() } ?: FloatingToolbarPlacement.MARGIN_DP,
        toolbarOffsetYDp = yDp.takeIf { it.isFinite() } ?: FloatingToolbarPlacement.MARGIN_DP,
    )

    companion object {
        fun capture(settings: AppSettings) = ToolRailLayout(settings.showToolbar, settings.verticalToolbar,
            settings.toolbarDock.name, settings.toolbarOffsetXDp, settings.toolbarOffsetYDp)
    }
}

@Serializable
data class NamedPanelLayout(val name: String, val layout: EditorPanelLayout,
                            val toolRail: ToolRailLayout? = null)

@Serializable
data class NamedPanelLayouts(val entries: List<NamedPanelLayout> = emptyList()) {
    fun save(name: String, layout: EditorPanelLayout, toolRail: ToolRailLayout? = null): NamedPanelLayouts {
        val normalized = name.trim().take(40)
        if (normalized.isEmpty() || !EditorPanelLayouts.isValid(layout)) return this
        val withoutOld = entries.filterNot { it.name.equals(normalized, ignoreCase = true) }
        return copy(entries = (withoutOld + NamedPanelLayout(normalized, layout, toolRail)).takeLast(20))
    }

    fun rename(oldName: String, newName: String): NamedPanelLayouts {
        val old = entries.firstOrNull { it.name == oldName } ?: return this
        val normalized = newName.trim().take(40)
        if (normalized.isEmpty() || entries.any { it.name.equals(normalized, ignoreCase = true) && it.name != oldName }) return this
        return copy(entries = entries.map { if (it.name == oldName) old.copy(name = normalized) else it })
    }

    fun delete(name: String): NamedPanelLayouts = copy(entries = entries.filterNot { it.name == name })
}
