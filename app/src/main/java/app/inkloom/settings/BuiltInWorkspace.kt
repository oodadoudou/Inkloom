package app.inkloom.settings

/**
 * STUDIO is the one editor surface for notes, PDFs and drawing. NOTE and DRAWING remain as
 * compatibility aliases for saved Toolbox profiles created by earlier versions.
 */
enum class BuiltInWorkspace { STUDIO, NOTE, DRAWING, READING, MINIMAL, RESET }

object BuiltInWorkspaces {
    fun apply(settings: AppSettings, workspace: BuiltInWorkspace): AppSettings =
        settings.applyToolboxWorkspace(snapshot(workspace))

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
            BuiltInWorkspace.STUDIO,
            // Existing shortcuts/profile files can still request either legacy name. They now
            // intentionally resolve to the same unified CSP-style desk.
            BuiltInWorkspace.NOTE,
            BuiltInWorkspace.DRAWING -> defaults.copy(
                showPageRail = true,
                showBrushPanel = true,
                showLayerPanel = true,
                showToolbar = true,
                pagePanelDock = WorkspacePanelDock.LEFT,
                brushPanelDock = WorkspacePanelDock.RIGHT,
                layerPanelDock = WorkspacePanelDock.RIGHT,
                brushPanelXDp = 1760f,
                brushPanelYDp = 12f,
                brushPanelWidthDp = 220f,
                brushPanelHeightDp = 680f,
                layerPanelXDp = 2000f,
                layerPanelYDp = 12f,
                layerPanelWidthDp = 240f,
                layerPanelHeightDp = 680f,
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
