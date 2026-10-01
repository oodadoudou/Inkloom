package app.inkloom.settings

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LegacyWorkspaceImportTest {
    @Test fun legacyDockWidthsUseTheViewportInsteadOfResettingToOneQuarter() {
        val imported=LegacyWorkspaceImport.fromPreferences(mapOf("showPageRail" to true,"pagePanelDock" to "LEFT", "pagePanelWidthDp" to 160f,
            "brushPanelDock" to "RIGHT","brushPanelWidthDp" to 240f,"layerPanelDock" to "RIGHT","layerPanelWidthDp" to 300f),1000f,800f)
        val pages=EditorPanelLayouts.slots(imported.layout,1000f,800f).single { it.node is PanelNode.Tabs && EditorPanel.PAGES in EditorPanelLayouts.members(it.node) }
        assertEquals(160f,pages.rect.width,.1f)
        val opened=imported.layout.copy(open=setOf(EditorPanel.PAGES,EditorPanel.BRUSHES,EditorPanel.LAYERS))
        val slots=EditorPanelLayouts.slots(opened,1000f,800f)
        assertEquals(300f,slots.single { it.node is PanelNode.Tabs && EditorPanel.BRUSHES in EditorPanelLayouts.members(it.node) }.rect.width,.1f)
        assertEquals(300f,slots.single { it.node is PanelNode.Tabs && EditorPanel.LAYERS in EditorPanelLayouts.members(it.node) }.rect.width,.1f)
    }

    @Test fun oldFreeGeometryAndVisibilityAreCopiedWithoutResettingCustomSizes() {
        val result=LegacyWorkspaceImport.fromPreferences(mapOf("showBrushPanel" to true,"brushPanelWidthDp" to 620f,"brushPanelXDp" to 333f))
        val brush=result.layout.floating.single { EditorPanel.BRUSHES in EditorPanelLayouts.members(it.node) }
        assertEquals(620f,brush.rect.width);assertEquals(333f,brush.rect.x)
        assertEquals(setOf(EditorPanel.BRUSHES),result.layout.open);assertTrue(EditorPanelLayouts.isValid(result.layout))
    }
    @Test fun defaultImportRetainsDefaultGroupsAndMissingPanelsStayAccessible() {
        val imported=LegacyWorkspaceImport.fromSettings(JsonObject(emptyMap()))
        assertEquals(EditorPanelLayout(),imported.layout)
        assertEquals(EditorPanel.entries.toSet(),(EditorPanelLayouts.members(imported.layout.dock)+imported.layout.floating.flatMap{EditorPanelLayouts.members(it.node)}).toSet())
    }
    @Test fun dockedStackedTabsKeepOrderAndActivePanel() {
        val result=LegacyWorkspaceImport.fromPreferences(mapOf("pagePanelDock" to "LEFT","brushPanelDock" to "LEFT",
            "leftDockStacked" to true,"leftDockActivePanel" to "PAGES","panelTabOrder" to "BRUSHES,PAGES,LAYERS","showPageRail" to true,"showBrushPanel" to true))
        fun tabs(node:PanelNode):List<PanelNode.Tabs> = when(node){is PanelNode.Tabs->listOf(node);is PanelNode.Split->tabs(node.first)+tabs(node.second);else->emptyList()}
        val group=tabs(result.layout.dock).single()
        assertTrue(group.panels.indexOf(EditorPanel.BRUSHES)<group.panels.indexOf(EditorPanel.PAGES));assertEquals(EditorPanel.PAGES,group.active)
        assertTrue(EditorPanelLayouts.isValid(result.layout))
    }
    @Test fun nonStackedDockFormsActualSplitsAndPreservesLock() {
        val result=LegacyWorkspaceImport.fromPreferences(mapOf("pagePanelDock" to "RIGHT","layerPanelDock" to "RIGHT","layerPanelLocked" to true))
        assertEquals(setOf(EditorPanel.PAGES,EditorPanel.LAYERS,EditorPanel.PROPERTIES),EditorPanelLayouts.members(result.layout.dock).toSet())
        assertTrue(result.layout.locked);assertTrue(EditorPanelLayouts.isValid(result.layout))
    }
    @Test fun orientationProfilesRemainSeparateAndPortable() {
        val root=Json.parseToJsonElement("""{"workspaceLayouts":{"landscape":{"showBrushPanel":true,"brushPanelWidthDp":527,"toolbarDock":"LEFT"},"portrait":{"showPageRail":true,"pagePanelDock":"LEFT","toolbarDock":"BOTTOM"}}}""").jsonObject
        val migrated=LegacyWorkspaceImport.fromSettings(root)
        assertEquals(setOf(EditorPanel.BRUSHES),migrated.orientationLayouts.getValue("LANDSCAPE").layout.open)
        assertEquals(setOf(EditorPanel.PAGES),migrated.orientationLayouts.getValue("PORTRAIT").layout.open)
        migrated.validate()
        val bytes=AppSettingsBackupCodec.encode(AppSettings(),panelWorkspace=migrated)
        assertEquals(migrated,AppSettingsBackupCodec.decode(bytes).getOrThrow().panelWorkspace)
        assertFalse(bytes.decodeToString().contains("brushPanelWidthDp"))
    }
    @Test fun oldBackupConvertsAtImportAndDoesNotDualWriteObsoleteFields() {
        val old="""{"format":"inkloom-app-settings","version":1,"settings":{"showLayerPanel":true,"layerPanelDock":"BOTTOM","layerPanelWidthDp":420,"penSize":42}}""".encodeToByteArray()
        val imported=AppSettingsBackupCodec.decode(old).getOrThrow()
        assertEquals(42f,imported.settings.penSize)
        assertEquals(setOf(EditorPanel.LAYERS),imported.panelWorkspace!!.layout.open)
        val upgraded=AppSettingsBackupCodec.encode(imported.settings,panelWorkspace=imported.panelWorkspace)
        assertFalse(upgraded.decodeToString().contains("layerPanelDock"))
        assertEquals(imported,AppSettingsBackupCodec.decode(upgraded).getOrThrow())
    }
    @Test fun malformedAndNonFiniteLegacyCoordinatesCannotPoisonTree() {
        val migrated=LegacyWorkspaceImport.fromPreferences(mapOf("brushPanelXDp" to Float.NaN,"pagePanelWidthDp" to -1f,"layerPanelHeightDp" to Float.POSITIVE_INFINITY))
        assertTrue(EditorPanelLayouts.isValid(migrated.layout));assertEquals(72f,migrated.layout.floating.first().rect.width)
    }
    @Test fun genericShortcutDecodingNoLongerReinterpretsAUserSavedOldDefaultList() {
        val custom=CspShortcutResolver.defaultBindings.filterNot { it.action in setOf(ShortcutAction.TOGGLE_RULER,ShortcutAction.TOGGLE_GRID_SNAP,ShortcutAction.TOGGLE_ALIGNMENT_GUIDES) }
        assertEquals(custom,ShortcutBindingCodec.decode(ShortcutBindingCodec.encode(custom)))
        assertEquals(CspShortcutResolver.defaultBindings,LegacyShortcutImport.migrateLegacyDefaults(custom))
    }
}
