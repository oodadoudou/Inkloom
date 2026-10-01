package app.inkloom

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class EditorWorkspaceStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings get() = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
    private val panels get() = context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE)
    @Before fun clear() { settings.edit().clear().commit();panels.edit().clear().commit() }
    @After fun cleanup() = clear()

    @Test fun existingTreeWinsOverConflictingLegacyVisibilityAndGeometry() {
        val tree=EditorPanelLayout(open=setOf(EditorPanel.NEARBY,EditorPanel.BRUSHES),locked=true)
        panels.edit().putString("layout",Json.encodeToString(EditorPanelLayout.serializer(),tree)).commit()
        settings.edit().putBoolean("showBrushPanel",false).putFloat("brushPanelWidthDp",999f).commit()
        assertEquals(tree,EditorWorkspaceStore(context).load().layout)
        assertFalse(settings.contains("showBrushPanel"));assertFalse(settings.contains("brushPanelWidthDp"));assertFalse(panels.contains("layout"))
        AppSettingsStore(context).save(AppSettings(penSize=26f))
        assertEquals(tree,EditorWorkspaceStore(context).load().layout)
        assertEquals(26f,AppSettingsStore(context).load().penSize)
    }
    @Test fun migrationPublishesGeometryTabsAndNamedLayoutsOnce() {
        settings.edit().putBoolean("showPageRail",true).putBoolean("showLayerPanel",true).putString("pagePanelDock","LEFT")
            .putString("layerPanelDock","LEFT").putBoolean("leftDockStacked",true).putString("leftDockActivePanel","LAYERS")
            .putFloat("brushPanelWidthDp",527f).putString("toolbarDock","RIGHT").commit()
        val named=NamedPanelLayouts().save("My drawing",EditorPanelLayout(open=setOf(EditorPanel.WHEEL)))
        panels.edit().putString("named_layouts",Json.encodeToString(NamedPanelLayouts.serializer(),named)).commit()
        val store=EditorWorkspaceStore(context);val migrated=store.load()
        assertEquals(named,migrated.namedLayouts);assertEquals("RIGHT",migrated.toolRail!!.dock)
        assertEquals(setOf(EditorPanel.PAGES,EditorPanel.LAYERS),migrated.layout.open)
        assertEquals(527f,migrated.layout.floating.single{EditorPanel.BRUSHES in EditorPanelLayouts.members(it.node)}.rect.width)
        assertTrue(EditorPanel.LAYERS in EditorPanelLayouts.members(migrated.layout.dock))
        assertTrue(LegacyWorkspaceImport.panelKeys.none(settings::contains));assertTrue(LegacyWorkspaceImport.railKeys.none(settings::contains))
        val updated=store.updateLayout(migrated.layout.copy(locked=true))
        assertEquals(updated,EditorWorkspaceStore(context).load())
    }
    @Test fun independentOwnersDoNotOverwriteTreeNamedLayoutsOrToolRail() {
        val first=EditorWorkspaceStore(context);val second=EditorWorkspaceStore(context)
        val tree=first.load().layout.copy(open=setOf(EditorPanel.MIX))
        first.updateLayout(tree)
        second.updateNamedLayouts(NamedPanelLayouts().save("Mix",tree))
        AppSettingsStore(context).save(AppSettings(toolbarDock=ToolbarDock.BOTTOM,toolPaletteLayout=ToolPaletteLayout.encode(ToolPaletteLayout.defaultEntries.reversed())))
        first.updateLayout(tree.copy(locked=true))
        val result=second.load()
        assertTrue(result.layout.locked);assertEquals(setOf(EditorPanel.MIX),result.layout.open)
        assertEquals("Mix",result.namedLayouts.entries.single().name);assertEquals("BOTTOM",result.toolRail!!.dock)
        val exported=WorkspaceLayoutCodec.encode(result,result.toolRail!!)
        assertEquals(result,WorkspaceLayoutCodec.decode(exported).getOrThrow().workspace)
    }
    @Test fun invalidRestoreIsRejectedWithoutChangingCurrentLayout() {
        val store=EditorWorkspaceStore(context);val before=store.load()
        val invalid=before.copy(layout=before.layout.copy(version=999))
        assertTrue(runCatching { store.restore(invalid) }.isFailure)
        assertEquals(before,store.load())
    }
    @Test fun damagedCurrentWorkspaceRestoresPreviousTransactionAndKeepsRecoveryBytes() {
        val store=EditorWorkspaceStore(context);val initial=store.load()
        val latest=store.updateLayout(initial.layout.copy(open=setOf(EditorPanel.MIX)))
        panels.edit().putString("workspace","broken layout").commit()
        val recovered=store.load()
        assertEquals(initial.layout,recovered.layout)
        assertEquals("broken layout",panels.getString("workspace.recovery",null))
        assertTrue(store.takeRecoveryNotice());assertFalse(store.takeRecoveryNotice())
        assertNotEquals(latest.layout,recovered.layout)
    }

    @Test fun malformedLegacyTreeAndNamedLayoutsDoNotPreventOpeningExistingNotebooks() {
        panels.edit().putString("layout", "broken old tree").putString("named_layouts", "broken old layouts").commit()
        settings.edit().putBoolean("showPageRail", true).commit()
        val store = EditorWorkspaceStore(context)
        assertEquals(setOf(EditorPanel.PAGES), store.load().layout.open)
        assertEquals("broken old tree", panels.getString("workspace.legacyRecovery.layout", null))
        assertEquals("broken old layouts", panels.getString("workspace.legacyRecovery.named_layouts", null))
        assertTrue(store.takeRecoveryNotice())
        assertFalse(panels.contains("layout"))
        assertFalse(panels.contains("named_layouts"))
    }

    @Test fun brushSizeMigrationKeepsNewRoleValuesAndDeletesReadFallbackKey() {
        val master=context.getSharedPreferences("brush_preset_sizes",0)
        master.edit().clear().putFloat("size:old:brush",13f).putFloat("size:PEN:old:brush",22f).commit()
        try {
            val store=BrushMasterSettingsStore(context)
            assertEquals(22f,store.loadSize(app.inkloom.model.EditorTool.PEN,"old:brush",1f))
            assertEquals(13f,store.loadSize(app.inkloom.model.EditorTool.ERASER,"old:brush",1f))
            assertFalse(master.contains("size:old:brush"))
        }finally{master.edit().clear().commit()}
    }
    @Test fun onceMigratedShortcutListIsNeverReinterpretedOnReload() {
        val old=CspShortcutResolver.defaultBindings.filterNot{it.action in setOf(ShortcutAction.TOGGLE_RULER,ShortcutAction.TOGGLE_GRID_SNAP,ShortcutAction.TOGGLE_ALIGNMENT_GUIDES)}
        settings.edit().putString("shortcutBindings",ShortcutBindingCodec.encode(old)).commit()
        assertEquals(CspShortcutResolver.defaultBindings,AppSettingsStore(context).load().shortcutBindings)
        AppSettingsStore(context).save(AppSettings(shortcutBindings=old))
        assertEquals(old,AppSettingsStore(context).load().shortcutBindings)
    }
}
