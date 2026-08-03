package app.inkloom.settings

import android.content.res.Configuration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class WorkspaceOrientation {
    LANDSCAPE,
    PORTRAIT;

    companion object {
        fun from(configuration: Configuration): WorkspaceOrientation =
            if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) PORTRAIT else LANDSCAPE
    }
}

data class WorkspaceLayoutProfiles(
    val landscape: ToolboxWorkspaceSnapshot? = null,
    val portrait: ToolboxWorkspaceSnapshot? = null,
) {
    fun get(orientation: WorkspaceOrientation): ToolboxWorkspaceSnapshot? = when (orientation) {
        WorkspaceOrientation.LANDSCAPE -> landscape
        WorkspaceOrientation.PORTRAIT -> portrait
    }

    fun updated(orientation: WorkspaceOrientation, workspace: ToolboxWorkspaceSnapshot): WorkspaceLayoutProfiles = when (orientation) {
        WorkspaceOrientation.LANDSCAPE -> copy(landscape = workspace.normalizedWorkspace())
        WorkspaceOrientation.PORTRAIT -> copy(portrait = workspace.normalizedWorkspace())
    }

    val isEmpty: Boolean get() = landscape == null && portrait == null
}

fun AppSettings.withWorkspaceFor(orientation: WorkspaceOrientation): AppSettings {
    val selected = workspaceLayouts.get(orientation)
        ?: if (workspaceLayouts.isEmpty) captureToolboxWorkspace() else AppSettings().captureToolboxWorkspace()
    return applyToolboxWorkspace(selected).copy(workspaceLayouts = workspaceLayouts.updated(orientation, selected))
}

fun AppSettings.recordWorkspaceFor(orientation: WorkspaceOrientation): AppSettings =
    copy(workspaceLayouts = workspaceLayouts.updated(orientation, captureToolboxWorkspace()))

internal fun ToolboxWorkspaceSnapshot.normalizedWorkspace(): ToolboxWorkspaceSnapshot = copy(
    pagePanelXDp = pagePanelXDp.coerceIn(-2400f, 2400f),
    pagePanelYDp = pagePanelYDp.coerceIn(-2400f, 2400f),
    pagePanelWidthDp = pagePanelWidthDp.coerceIn(80f, 1200f),
    pagePanelHeightDp = pagePanelHeightDp.coerceIn(120f, 1600f),
    brushPanelXDp = brushPanelXDp.coerceIn(-2400f, 2400f),
    brushPanelYDp = brushPanelYDp.coerceIn(-2400f, 2400f),
    brushPanelWidthDp = brushPanelWidthDp.coerceIn(80f, 1200f),
    brushPanelHeightDp = brushPanelHeightDp.coerceIn(120f, 1600f),
    layerPanelXDp = layerPanelXDp.coerceIn(-2400f, 2400f),
    layerPanelYDp = layerPanelYDp.coerceIn(-2400f, 2400f),
    layerPanelWidthDp = layerPanelWidthDp.coerceIn(80f, 1200f),
    layerPanelHeightDp = layerPanelHeightDp.coerceIn(120f, 1600f),
    toolbarOffsetXDp = toolbarOffsetXDp.coerceIn(-1200f, 1200f),
    toolbarOffsetYDp = toolbarOffsetYDp.coerceIn(-1200f, 1200f),
)

object WorkspaceLayoutProfilesCodec {
    private const val MAX_CHARS = 64 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(value: WorkspaceLayoutProfiles): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            value.landscape?.let { put("landscape", it.toJson()) }
            value.portrait?.let { put("portrait", it.toJson()) }
        },
    )

    fun decode(raw: String?): WorkspaceLayoutProfiles {
        if (raw.isNullOrBlank() || raw.length > MAX_CHARS) return WorkspaceLayoutProfiles()
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            WorkspaceLayoutProfiles(
                landscape = root["landscape"]?.jsonObject?.toWorkspace(),
                portrait = root["portrait"]?.jsonObject?.toWorkspace(),
            )
        }.getOrDefault(WorkspaceLayoutProfiles())
    }

    private fun ToolboxWorkspaceSnapshot.toJson() = buildJsonObject {
        put("showPageRail", showPageRail); put("showBrushPanel", showBrushPanel); put("showLayerPanel", showLayerPanel); put("showToolbar", showToolbar)
        put("pagePanelDock", pagePanelDock.name); put("brushPanelDock", brushPanelDock.name); put("layerPanelDock", layerPanelDock.name)
        put("pagePanelXDp", pagePanelXDp); put("pagePanelYDp", pagePanelYDp); put("pagePanelWidthDp", pagePanelWidthDp); put("pagePanelHeightDp", pagePanelHeightDp)
        put("brushPanelXDp", brushPanelXDp); put("brushPanelYDp", brushPanelYDp); put("brushPanelWidthDp", brushPanelWidthDp); put("brushPanelHeightDp", brushPanelHeightDp)
        put("layerPanelXDp", layerPanelXDp); put("layerPanelYDp", layerPanelYDp); put("layerPanelWidthDp", layerPanelWidthDp); put("layerPanelHeightDp", layerPanelHeightDp)
        put("toolbarOffsetXDp", toolbarOffsetXDp); put("toolbarOffsetYDp", toolbarOffsetYDp); put("verticalToolbar", verticalToolbar); put("toolbarDock", toolbarDock.name)
    }

    private fun JsonObject.toWorkspace() = ToolboxWorkspaceSnapshot(
        boolean("showPageRail"), boolean("showBrushPanel"), boolean("showLayerPanel"), boolean("showToolbar", true),
        enum("pagePanelDock", WorkspacePanelDock.LEFT), enum("brushPanelDock", WorkspacePanelDock.RIGHT), enum("layerPanelDock", WorkspacePanelDock.RIGHT),
        float("pagePanelXDp", 16f), float("pagePanelYDp", 16f), float("pagePanelWidthDp", 160f), float("pagePanelHeightDp", 640f),
        float("brushPanelXDp", 720f), float("brushPanelYDp", 16f), float("brushPanelWidthDp", 330f), float("brushPanelHeightDp", 680f),
        float("layerPanelXDp", 720f), float("layerPanelYDp", 16f), float("layerPanelWidthDp", 300f), float("layerPanelHeightDp", 620f),
        float("toolbarOffsetXDp", 16f), float("toolbarOffsetYDp", 16f), boolean("verticalToolbar"), enum("toolbarDock", ToolbarDock.FREE),
    ).normalizedWorkspace()

    private fun JsonObject.text(key: String): String = get(key)?.jsonPrimitive?.content.orEmpty()
    private fun JsonObject.float(key: String, fallback: Float): Float = runCatching { get(key)?.jsonPrimitive?.float }.getOrNull() ?: fallback
    private fun JsonObject.boolean(key: String, fallback: Boolean = false): Boolean = runCatching { get(key)?.jsonPrimitive?.content?.toBooleanStrict() }.getOrNull() ?: fallback
    private inline fun <reified T : Enum<T>> JsonObject.enum(key: String, fallback: T): T = runCatching { enumValueOf<T>(text(key)) }.getOrDefault(fallback)
}
