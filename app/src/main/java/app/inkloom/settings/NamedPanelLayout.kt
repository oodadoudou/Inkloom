package app.inkloom.settings

import kotlinx.serialization.Serializable

@Serializable
data class ToolRailLayout(
    val visible: Boolean,
    val vertical: Boolean,
    val dock: String,
    val xDp: Float,
    val yDp: Float,
    val paletteLayout: String = ToolPaletteLayout.encode(ToolPaletteLayout.defaultEntries),
) {
    fun applyTo(settings: AppSettings): AppSettings = settings.copy(
        showToolbar = visible,
        verticalToolbar = vertical,
        toolbarDock = ToolbarDock.entries.firstOrNull { it.name == dock } ?: ToolbarDock.FREE,
        toolbarOffsetXDp = xDp.takeIf { it.isFinite() } ?: FloatingToolbarPlacement.MARGIN_DP,
        toolbarOffsetYDp = yDp.takeIf { it.isFinite() } ?: FloatingToolbarPlacement.MARGIN_DP,
        toolPaletteLayout = ToolPaletteLayout.encode(ToolPaletteLayout.decode(paletteLayout)),
    )

    companion object {
        fun capture(settings: AppSettings) = ToolRailLayout(settings.showToolbar, settings.verticalToolbar,
            settings.toolbarDock.name, settings.toolbarOffsetXDp, settings.toolbarOffsetYDp,
            ToolPaletteLayout.encode(ToolPaletteLayout.decode(settings.toolPaletteLayout)))
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

/** The same layout tree is used for current state, named layouts and library backup. */
@Serializable
data class PanelWorkspaceBackup(
    val layout: EditorPanelLayout,
    val namedLayouts: NamedPanelLayouts,
    val toolRail: ToolRailLayout? = null,
    val orientationLayouts: Map<String, NamedPanelLayout> = emptyMap(),
    val activeOrientation: String? = null,
) {
    /** Every saved layout with the panels added since it was saved. */
    fun withAllPanels(): PanelWorkspaceBackup = copy(layout = EditorPanelLayouts.withAllPanels(layout),
        namedLayouts = NamedPanelLayouts(namedLayouts.entries.map { it.copy(layout = EditorPanelLayouts.withAllPanels(it.layout)) }),
        orientationLayouts = orientationLayouts.mapValues { (_, saved) -> saved.copy(layout = EditorPanelLayouts.withAllPanels(saved.layout)) })

    fun validate() {
        require(orientationLayouts.size <= 2 && orientationLayouts.keys.all { it == "LANDSCAPE" || it == "PORTRAIT" }) { "Invalid orientation layouts" }
        require(activeOrientation == null || activeOrientation == "LANDSCAPE" || activeOrientation == "PORTRAIT")
        (orientationLayouts.values + listOfNotNull(toolRail?.let { NamedPanelLayout("Current", layout, it) })).forEach { saved ->
            require(EditorPanelLayouts.isValid(saved.layout))
            saved.toolRail?.let { rail -> require(rail.xDp.isFinite() && rail.yDp.isFinite() && ToolbarDock.entries.any { it.name == rail.dock }) }
        }
        require(EditorPanelLayouts.isValid(layout)) { "Invalid current panel layout" }
        require(namedLayouts.entries.size <= 20) { "Too many named panel layouts" }
        require(namedLayouts.entries.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == namedLayouts.entries.size) {
            "Duplicate named panel layouts"
        }
        namedLayouts.entries.forEach {
            require(it.name.isNotBlank() && it.name.length <= 40 && EditorPanelLayouts.isValid(it.layout)) { "Invalid named panel layout" }
            it.toolRail?.let { rail ->
                require(rail.xDp.isFinite() && rail.yDp.isFinite() && ToolbarDock.entries.any { dock -> dock.name == rail.dock }) { "Invalid toolbar layout" }
                require(ToolPaletteLayout.decode(rail.paletteLayout).filterIsInstance<ToolPaletteEntry.Tool>().size == app.inkloom.model.EditorTool.entries.size) { "Invalid tool palette layout" }
            }
        }
    }
}
