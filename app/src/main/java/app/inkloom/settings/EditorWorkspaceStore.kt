package app.inkloom.settings

import android.content.Context
import android.content.res.Configuration
import kotlinx.serialization.json.Json

/** One durable owner for panel trees, named layouts, orientation profiles and tool-rail layout. */
class EditorWorkspaceStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE)
    private val settings = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val orientation get() = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) "PORTRAIT" else "LANDSCAPE"

    fun load(): PanelWorkspaceBackup = synchronized(lock) { readSelected() }

    fun updateLayout(layout: EditorPanelLayout): PanelWorkspaceBackup = change { it.copy(layout = layout) }
    fun updateNamedLayouts(named: NamedPanelLayouts): PanelWorkspaceBackup = change { it.copy(namedLayouts = named) }
    fun updateToolRail(rail: ToolRailLayout): PanelWorkspaceBackup = change { it.copy(toolRail = rail) }
    fun restore(workspace: PanelWorkspaceBackup): PanelWorkspaceBackup = change { old ->
        workspace.copy(toolRail = workspace.toolRail ?: old.toolRail, activeOrientation = orientation)
    }

    private fun change(update: (PanelWorkspaceBackup) -> PanelWorkspaceBackup): PanelWorkspaceBackup = synchronized(lock) {
        val before = readSelected()
        val next = update(before).let { value -> value.copy(activeOrientation = orientation,
            orientationLayouts = value.orientationLayouts + (orientation to NamedPanelLayout(orientation, value.layout, value.toolRail))) }
        next.validate()
        if (next != before) write(next)
        next
    }

    private fun readSelected(): PanelWorkspaceBackup {
        val stored = preferences.getString("workspace", null)
        val value = if (stored != null) runCatching { decode(stored) }.getOrElse {
            val previous=preferences.getString("workspace.previous",null)?.let { runCatching { decode(it) }.getOrNull() }
                ?: PanelWorkspaceBackup(EditorPanelLayout(),NamedPanelLayouts(),ToolRailLayout.capture(AppSettings()))
            // Keep damaged bytes in private storage for recovery; prefer the last valid transaction.
            check(preferences.edit().putString("workspace.recovery",stored).putBoolean("recoveryNotice",true).commit())
            write(previous,durable=true)
            previous
        } else migrate()
        finishLegacyCleanup()
        val selected = if (value.activeOrientation != null && value.activeOrientation != orientation) {
            value.orientationLayouts[orientation]?.let { value.copy(layout = it.layout, toolRail = it.toolRail ?: value.toolRail) } ?: value
        } else value
        val normalized = selected.copy(activeOrientation = orientation,
            orientationLayouts = selected.orientationLayouts + (orientation to NamedPanelLayout(orientation, selected.layout, selected.toolRail)))
        if (normalized != value) write(normalized)
        return normalized
    }

    private fun migrate(): PanelWorkspaceBackup {
        val legacy = LegacyWorkspaceImport.fromPreferences(settings.all)
        fun <T> import(key: String, decode: (String) -> T): T? = preferences.getString(key, null)?.let { raw ->
            runCatching { decode(raw) }.getOrElse {
                check(preferences.edit().putString("workspace.legacyRecovery.$key", raw).putBoolean("recoveryNotice", true).commit())
                null
            }
        }
        val currentTree = import("layout") { raw ->
            require(raw.length < 64 * 1024) { "Panel layout data is too large" }
            json.decodeFromString(EditorPanelLayout.serializer(), raw).also { require(EditorPanelLayouts.isValid(it)) }
        }
        val named = import("named_layouts") { raw ->
            require(raw.length < WorkspaceLayoutCodec.MAX_FILE_BYTES) { "Named layout data is too large" }
            json.decodeFromString(NamedPanelLayouts.serializer(), raw).also {
                PanelWorkspaceBackup(EditorPanelLayout(), it).validate()
            }
        } ?: NamedPanelLayouts()
        val oldProfile = legacy.orientationLayouts[orientation]
        val migrated = legacy.copy(layout = currentTree ?: oldProfile?.layout ?: legacy.layout, namedLayouts = named,
            toolRail = oldProfile?.toolRail ?: legacy.toolRail, activeOrientation = orientation).let { value ->
            value.copy(orientationLayouts = value.orientationLayouts + (orientation to NamedPanelLayout(orientation, value.layout, value.toolRail)))
        }
        migrated.validate()
        // Commit complete replacement first. If process termination interrupts cleanup, its presence wins.
        write(migrated, durable = true)
        finishLegacyCleanup()
        return migrated
    }
    private fun finishLegacyCleanup() {
        if(preferences.getBoolean("legacyCleanupComplete",false)) return
        val cleanup=settings.edit()
        (LegacyWorkspaceImport.panelKeys+LegacyWorkspaceImport.railKeys).forEach(cleanup::remove)
        check(cleanup.commit()) { "Could not finish legacy settings migration" }
        check(preferences.edit().remove("layout").remove("named_layouts").putBoolean("legacyCleanupComplete",true).commit()) {
            "Could not finish workspace migration"
        }
    }
    fun takeRecoveryNotice(): Boolean = synchronized(lock) {
        val notice=preferences.getBoolean("recoveryNotice",false)
        if(notice) preferences.edit().remove("recoveryNotice").apply()
        notice
    }
    private fun decode(raw:String):PanelWorkspaceBackup {
        require(raw.length<=WorkspaceLayoutCodec.MAX_FILE_BYTES) { "Workspace data is too large" }
        return json.decodeFromString(PanelWorkspaceBackup.serializer(),raw).also(PanelWorkspaceBackup::validate)
    }
    private fun write(value: PanelWorkspaceBackup, durable: Boolean = false) {
        val encoded = json.encodeToString(PanelWorkspaceBackup.serializer(), value)
        require(encoded.length <= WorkspaceLayoutCodec.MAX_FILE_BYTES) { "Workspace data is too large" }
        val editor = preferences.edit().putString("workspace", encoded)
        preferences.getString("workspace",null)?.takeIf { runCatching { decode(it) }.isSuccess }?.let { editor.putString("workspace.previous",it) }
        if (durable) check(editor.commit()) { "Could not save workspace" } else editor.apply()
    }
    companion object { private val lock = Any() }
}
