package app.inkloom.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which editor dialogs and menus are open. One remembered holder instead of a separately
 * remembered flag each keeps the editor screen's composition smaller.
 */
@Stable
internal class EditorOverlays {
    var colorManagementOpen by mutableStateOf(false)
    var canvasSizeOpen by mutableStateOf(false)
    var canvasOrientationOpen by mutableStateOf(false)
    var pageExportOpen by mutableStateOf(false)
    var pageShareOpen by mutableStateOf(false)
    var quickZoomMenu by mutableStateOf(false)
    var workspaceMenu by mutableStateOf(false)
    var viewManagerOpen by mutableStateOf(false)
    var savePanelLayoutOpen by mutableStateOf(false)
    var managePanelLayoutsOpen by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var outlineOpen by mutableStateOf(false)
    var bookmarksOpen by mutableStateOf(false)
    var textInsertOpen by mutableStateOf(false)
    var stickyInsertOpen by mutableStateOf(false)
    var linkInsertOpen by mutableStateOf(false)
    var shapeInsertOpen by mutableStateOf(false)
    var imageInsertOpen by mutableStateOf(false)
    var notebookSwitcherOpen by mutableStateOf(false)
    /** Insert link: notebook page rather than web address. */
    var insertInternalLink by mutableStateOf(false)
}

/**
 * Short-lived editor modes: canvas move/rotate tools toggled or held, panel and guide states saved
 * while a shortcut is held, the secondary-color target, and in-progress work flags.
 */
@Stable
internal class EditorModes {
    var continueFilling by mutableStateOf(false)
    var handToolEnabled by mutableStateOf(false)
    var handToolHeld by mutableStateOf(false)
    var rotateToolEnabled by mutableStateOf(false)
    var rotateToolHeld by mutableStateOf(false)
    var canvasSizeBusy by mutableStateOf(false)
    var editSecondaryColor by mutableStateOf(false)
    var panelsHiddenByShortcut by mutableStateOf(false)
    var rulerBeforeShortcut by mutableStateOf(false)
    var gridBeforeShortcut by mutableStateOf(false)
    var guidesBeforeShortcut by mutableStateOf(false)
}
