package app.inkloom.settings

enum class BuiltInWorkspace { NOTE, DRAWING, READING, MINIMAL, RESET }

object BuiltInWorkspaces {
    fun apply(settings: AppSettings, workspace: BuiltInWorkspace): AppSettings =
        settings.applyToolboxWorkspace(snapshot(workspace))

    fun snapshot(workspace: BuiltInWorkspace): ToolboxWorkspaceSnapshot {
        val defaults = AppSettings()
        return when (workspace) {
            BuiltInWorkspace.NOTE -> defaults.copy(
                showPageRail = true,
                showBrushPanel = false,
                showLayerPanel = false,
                showToolbar = true,
                pagePanelDock = WorkspacePanelDock.LEFT,
                toolbarDock = ToolbarDock.TOP,
                verticalToolbar = false,
            )
            BuiltInWorkspace.DRAWING -> defaults.copy(
                showPageRail = false,
                showBrushPanel = true,
                showLayerPanel = true,
                showToolbar = true,
                brushPanelDock = WorkspacePanelDock.LEFT,
                layerPanelDock = WorkspacePanelDock.RIGHT,
                toolbarDock = ToolbarDock.LEFT,
                verticalToolbar = true,
            )
            BuiltInWorkspace.READING -> defaults.copy(
                showPageRail = true,
                showBrushPanel = false,
                showLayerPanel = false,
                showToolbar = false,
                pagePanelDock = WorkspacePanelDock.LEFT,
            )
            BuiltInWorkspace.MINIMAL -> defaults.copy(
                showPageRail = false,
                showBrushPanel = false,
                showLayerPanel = false,
                showToolbar = true,
                toolbarDock = ToolbarDock.TOP,
                verticalToolbar = false,
            )
            BuiltInWorkspace.RESET -> defaults
        }.captureToolboxWorkspace()
    }
}
