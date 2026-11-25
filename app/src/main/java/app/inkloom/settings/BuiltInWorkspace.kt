package app.inkloom.settings

/** Presets use the same tree and rail model as saved layouts. */
enum class BuiltInWorkspace {
    STUDIO, READING, PAINTING,
    /** For painters coming from other apps: their default layouts, on the same panels. */
    SAI, PHOTOSHOP, PROCREATE,
}

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
            // PaintTool SAI: two columns left of the canvas, navigator over layers outside, colour over
            // the tool panel beside the canvas; tools along the top like SAI's quick bar.
            BuiltInWorkspace.SAI -> {
                val navigator = EditorPanelLayouts.move(defaults, "navigator", null, PanelDropTarget("canvas", PanelEdge.LEFT), PanelRect(0f, 0f, 220f, 260f))
                val layers = EditorPanelLayouts.move(navigator, "layers", null, PanelDropTarget("navigator", PanelEdge.BOTTOM), PanelRect(0f, 0f, 220f, 440f))
                val colors = EditorPanelLayouts.move(layers, "colors", null, PanelDropTarget("canvas", PanelEdge.LEFT), PanelRect(0f, 0f, 260f, 300f))
                EditorPanelLayouts.move(colors, "brushes", null, PanelDropTarget("colors", PanelEdge.BOTTOM), PanelRect(0f, 0f, 260f, 400f))
                    .copy(open = setOf(EditorPanel.NAVIGATOR, EditorPanel.LAYERS, EditorPanel.WHEEL, EditorPanel.BRUSHES))
            }
            // Photoshop's Essentials: tools down the left; one column on the right with Color, then
            // Properties, then Layers.
            BuiltInWorkspace.PHOTOSHOP -> {
                val colors = EditorPanelLayouts.move(defaults, "colors", null, PanelDropTarget("canvas", PanelEdge.RIGHT), PanelRect(0f, 0f, 300f, 260f))
                val layers = EditorPanelLayouts.move(colors, "layers", null, PanelDropTarget("colors", PanelEdge.BOTTOM), PanelRect(0f, 0f, 300f, 420f))
                EditorPanelLayouts.move(layers, "layers", EditorPanel.PROPERTIES, PanelDropTarget("layers", PanelEdge.TOP), PanelRect(0f, 0f, 300f, 200f))
                    .copy(open = setOf(EditorPanel.WHEEL, EditorPanel.PROPERTIES, EditorPanel.LAYERS))
            }
            // Procreate: nothing docked; the tools run across the top and layers and colour open on demand.
            BuiltInWorkspace.PROCREATE -> defaults
            BuiltInWorkspace.STUDIO -> defaults
        }
        val rail = when (workspace) {
            BuiltInWorkspace.SAI, BuiltInWorkspace.PROCREATE ->
                ToolRailLayout(visible = true, vertical = false, dock = ToolbarDock.TOP.name, xDp = 16f, yDp = 16f)
            else -> ToolRailLayout(visible = workspace != BuiltInWorkspace.READING, vertical = true, dock = ToolbarDock.LEFT.name, xDp = 16f, yDp = 16f)
        }
        return NamedPanelLayout(workspace.name, layout, rail)
    }
}
