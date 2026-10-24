package app.inkloom.settings

import java.io.InputStream
import java.io.ByteArrayOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Portable panel geometry and named layouts. Shortcut profiles are exported separately. */
@Serializable
data class WorkspaceLayoutDocument(
    val format: String,
    val version: Int,
    val workspace: PanelWorkspaceBackup,
    val toolRail: ToolRailLayout,
)

object WorkspaceLayoutCodec {
    const val FORMAT = "inkloom-workspace-layout"
    const val FILE_EXTENSION = "inkloom-layout.json"
    const val MIME_TYPE = "application/json"
    const val MAX_FILE_BYTES = 1024 * 1024
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    fun encode(workspace: PanelWorkspaceBackup, toolRail: ToolRailLayout): ByteArray {
        workspace.validate()
        validateRail(toolRail)
        return json.encodeToString(WorkspaceLayoutDocument(FORMAT, 1, workspace, toolRail))
            .encodeToByteArray().also { require(it.size <= MAX_FILE_BYTES) { "Workspace layout is too large" } }
    }

    fun decode(input: InputStream): Result<WorkspaceLayoutDocument> = runCatching {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_FILE_BYTES) { "Workspace layout is too large" }
            output.write(buffer, 0, count)
        }
        decode(output.toByteArray()).getOrThrow()
    }

    fun decode(bytes: ByteArray): Result<WorkspaceLayoutDocument> = runCatching {
        require(bytes.isNotEmpty() && bytes.size <= MAX_FILE_BYTES) { "Workspace layout has an invalid size" }
        val decoded = json.decodeFromString<WorkspaceLayoutDocument>(bytes.decodeToString())
        require(decoded.format == FORMAT && decoded.version == 1) { "Unsupported workspace layout" }
        val document = decoded.copy(workspace = decoded.workspace.withAllPanels())
        document.workspace.validate()
        validateRail(document.toolRail)
        document
    }

    fun mergeNamedLayouts(current: NamedPanelLayouts, imported: NamedPanelLayouts): NamedPanelLayouts {
        val importedNames = imported.entries.map { it.name.lowercase(java.util.Locale.ROOT) }.toSet()
        val merged = NamedPanelLayouts(current.entries.filterNot { it.name.lowercase(java.util.Locale.ROOT) in importedNames } + imported.entries)
        require(merged.entries.size <= 20) { "Too many named panel layouts; remove some before importing" }
        return merged
    }

    private fun validateRail(rail: ToolRailLayout) {
        require(rail.xDp.isFinite() && rail.yDp.isFinite() && ToolbarDock.entries.any { it.name == rail.dock }) {
            "Invalid toolbar layout"
        }
        require(ToolPaletteLayout.decode(rail.paletteLayout).filterIsInstance<ToolPaletteEntry.Tool>().size == app.inkloom.model.EditorTool.entries.size) {
            "Invalid tool palette layout"
        }
    }
}
