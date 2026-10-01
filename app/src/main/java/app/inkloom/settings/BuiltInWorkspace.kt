package app.inkloom.settings

/** Presets use the same tree and rail model as saved layouts. */
enum class BuiltInWorkspace { STUDIO, READING }

object BuiltInWorkspaces {
    fun snapshot(workspace: BuiltInWorkspace): NamedPanelLayout {
        val defaults = EditorPanelLayout()
        val layout = if (workspace == BuiltInWorkspace.READING)
            EditorPanelLayouts.move(defaults, "pages", null, PanelDropTarget("canvas", PanelEdge.LEFT), PanelRect(0f, 0f, 220f, 700f))
                .copy(open = setOf(EditorPanel.PAGES)) else defaults
        return NamedPanelLayout(workspace.name, layout,
            ToolRailLayout(visible = workspace == BuiltInWorkspace.STUDIO, vertical = true, dock = ToolbarDock.LEFT.name, xDp = 16f, yDp = 16f))
    }
}
