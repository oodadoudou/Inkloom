package app.inkloom.settings

/** Presets use the same tree and rail model as saved layouts. */
enum class BuiltInWorkspace { STUDIO, READING, PAINTING }

object BuiltInWorkspaces {
    fun snapshot(workspace: BuiltInWorkspace): NamedPanelLayout {
        val defaults = EditorPanelLayout()
        val layout = when (workspace) {
            BuiltInWorkspace.READING ->
                EditorPanelLayouts.move(defaults, "pages", null, PanelDropTarget("canvas", PanelEdge.LEFT), PanelRect(0f, 0f, 220f, 700f))
                    .copy(open = setOf(EditorPanel.PAGES))
            // Clip Studio style: colors above layers in a docked right-hand column, so neither covers
            // the page; brushes stay the window that opens on demand.
            BuiltInWorkspace.PAINTING -> {
                val colors = EditorPanelLayouts.move(defaults, "colors", null, PanelDropTarget("canvas", PanelEdge.RIGHT), PanelRect(0f, 0f, 320f, 300f))
                EditorPanelLayouts.move(colors, "layers", null, PanelDropTarget("colors", PanelEdge.BOTTOM), PanelRect(0f, 0f, 320f, 400f))
                    // The colour wheel, as the colour chips open it, plus layers; other colour palettes join as tabs.
                    .copy(open = setOf(EditorPanel.WHEEL, EditorPanel.LAYERS))
            }
            BuiltInWorkspace.STUDIO -> defaults
        }
        return NamedPanelLayout(workspace.name, layout,
            ToolRailLayout(visible = workspace != BuiltInWorkspace.READING, vertical = true, dock = ToolbarDock.LEFT.name, xDp = 16f, yDp = 16f))
    }
}
