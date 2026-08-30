package app.inkloom.settings

/** Layout presets for the one editor surface shared by notes, PDFs and drawing. */
enum class BuiltInWorkspace { STUDIO, READING, MINIMAL }

object BuiltInWorkspaces {
    /** Upgrades both orientations without touching input, brush or document preferences. */
    fun unified(settings: AppSettings): AppSettings {
        val studio = snapshot(BuiltInWorkspace.STUDIO)
        return settings.applyToolboxWorkspace(studio).copy(
            workspaceLayouts = WorkspaceLayoutProfiles(landscape = studio, portrait = studio),
        )
    }

    fun snapshot(workspace: BuiltInWorkspace): ToolboxWorkspaceSnapshot {
        val defaults = AppSettings()
        return when (workspace) {
            BuiltInWorkspace.STUDIO -> defaults.copy(
                showPageRail = false,
                showBrushPanel = false,
                showLayerPanel = false,
                showToolbar = true,
                pagePanelDock = WorkspacePanelDock.FREE,
                brushPanelDock = WorkspacePanelDock.FREE,
                layerPanelDock = WorkspacePanelDock.FREE,
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
        }.captureToolboxWorkspace()
    }
}
