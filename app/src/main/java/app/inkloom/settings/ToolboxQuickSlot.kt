package app.inkloom.settings

import app.inkloom.model.ImportedBrush
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class ToolboxQuickSlotKind { EMPTY, BRUSH, BRUSH_GROUP, TOOL_PRESET, WORKSPACE }

data class ToolboxWorkspaceSnapshot(
    val showPageRail: Boolean,
    val showBrushPanel: Boolean,
    val showLayerPanel: Boolean,
    val showToolbar: Boolean,
    val pagePanelDock: WorkspacePanelDock,
    val brushPanelDock: WorkspacePanelDock,
    val layerPanelDock: WorkspacePanelDock,
    val pagePanelMode: WorkspacePanelMode,
    val brushPanelMode: WorkspacePanelMode,
    val layerPanelMode: WorkspacePanelMode,
    val pagePanelLocked: Boolean,
    val brushPanelLocked: Boolean,
    val layerPanelLocked: Boolean,
    val leftDockStacked: Boolean,
    val rightDockStacked: Boolean,
    val bottomDockStacked: Boolean,
    val freeDockStacked: Boolean,
    val leftDockActivePanel: WorkspacePanelKind,
    val rightDockActivePanel: WorkspacePanelKind,
    val bottomDockActivePanel: WorkspacePanelKind,
    val freeDockActivePanel: WorkspacePanelKind,
    val panelTabOrder: String,
    val pagePanelXDp: Float,
    val pagePanelYDp: Float,
    val pagePanelWidthDp: Float,
    val pagePanelHeightDp: Float,
    val brushPanelXDp: Float,
    val brushPanelYDp: Float,
    val brushPanelWidthDp: Float,
    val brushPanelHeightDp: Float,
    val layerPanelXDp: Float,
    val layerPanelYDp: Float,
    val layerPanelWidthDp: Float,
    val layerPanelHeightDp: Float,
    val toolbarOffsetXDp: Float,
    val toolbarOffsetYDp: Float,
    val verticalToolbar: Boolean,
    val toolbarDock: ToolbarDock,
)

data class ToolboxQuickSlot(
    val kind: ToolboxQuickSlotKind = ToolboxQuickSlotKind.EMPTY,
    val label: String = "",
    val brushId: String = "",
    val groupPath: String = "",
    val size: Float = 5f,
    val opacity: Float = 1f,
    val colorArgb: Int = 0xFF111111.toInt(),
    val workspace: ToolboxWorkspaceSnapshot? = null,
)

object ToolboxQuickSlots {
    const val COUNT = 8
    private val actions = listOf(
        ShortcutAction.QUICK_SLOT_1, ShortcutAction.QUICK_SLOT_2, ShortcutAction.QUICK_SLOT_3, ShortcutAction.QUICK_SLOT_4,
        ShortcutAction.QUICK_SLOT_5, ShortcutAction.QUICK_SLOT_6, ShortcutAction.QUICK_SLOT_7, ShortcutAction.QUICK_SLOT_8,
    )

    fun indexOf(action: ShortcutAction): Int? = actions.indexOf(action).takeIf { it >= 0 }
    fun actionAt(index: Int): ShortcutAction = actions[index.coerceIn(0, COUNT - 1)]

    fun remapBrushIds(values: List<ToolboxQuickSlot>, mappings: Map<String, String>): List<ToolboxQuickSlot> = normalize(values).map { slot ->
        slot.brushId.takeIf(String::isNotEmpty)?.let(mappings::get)?.let { restoredId -> slot.copy(brushId = restoredId) } ?: slot
    }

    fun preferredBrush(slot: ToolboxQuickSlot, brushes: List<ImportedBrush>): ImportedBrush? = when (slot.kind) {
        ToolboxQuickSlotKind.BRUSH, ToolboxQuickSlotKind.TOOL_PRESET -> slot.brushId.takeIf(String::isNotEmpty)?.let { id ->
            brushes.firstOrNull { it.id == id && !it.hidden }
        }
        ToolboxQuickSlotKind.BRUSH_GROUP -> {
            val target = normalize(slot).groupPath
            brushes.asSequence().filterNot(ImportedBrush::hidden).filter { brush ->
                val path = brush.groupName.trim().trim('/')
                target.isNotEmpty() && (path.equals(target, true) || path.startsWith("$target/", true))
            }.maxWithOrNull(compareBy<ImportedBrush> { it.lastUsedAt }.thenBy { it.name })
        }
        ToolboxQuickSlotKind.EMPTY, ToolboxQuickSlotKind.WORKSPACE -> null
    }

    fun normalize(values: List<ToolboxQuickSlot>): List<ToolboxQuickSlot> = List(COUNT) { index ->
        normalize(values.getOrNull(index) ?: ToolboxQuickSlot())
    }

    fun normalize(value: ToolboxQuickSlot): ToolboxQuickSlot = value.copy(
        label = value.label.trim().take(64),
        brushId = value.brushId.trim().take(128),
        groupPath = value.groupPath.trim().trim('/').split('/').map(String::trim).filter(String::isNotEmpty).joinToString("/").take(240),
        size = value.size.coerceIn(.5f, 400f),
        opacity = value.opacity.coerceIn(.05f, 1f),
        workspace = value.workspace?.normalizedWorkspace(),
    )
}

fun AppSettings.captureToolboxWorkspace(): ToolboxWorkspaceSnapshot = ToolboxWorkspaceSnapshot(
    showPageRail, showBrushPanel, showLayerPanel, showToolbar,
    pagePanelDock, brushPanelDock, layerPanelDock,
    pagePanelMode, brushPanelMode, layerPanelMode,
    pagePanelLocked, brushPanelLocked, layerPanelLocked,
    leftDockStacked, rightDockStacked, bottomDockStacked, freeDockStacked,
    leftDockActivePanel, rightDockActivePanel, bottomDockActivePanel, freeDockActivePanel, panelTabOrder,
    pagePanelXDp, pagePanelYDp, pagePanelWidthDp, pagePanelHeightDp,
    brushPanelXDp, brushPanelYDp, brushPanelWidthDp, brushPanelHeightDp,
    layerPanelXDp, layerPanelYDp, layerPanelWidthDp, layerPanelHeightDp,
    toolbarOffsetXDp, toolbarOffsetYDp, verticalToolbar, toolbarDock,
)

fun AppSettings.applyToolboxWorkspace(value: ToolboxWorkspaceSnapshot): AppSettings = copy(
    showPageRail = value.showPageRail,
    showBrushPanel = value.showBrushPanel,
    showLayerPanel = value.showLayerPanel,
    showToolbar = value.showToolbar,
    pagePanelDock = value.pagePanelDock,
    brushPanelDock = value.brushPanelDock,
    layerPanelDock = value.layerPanelDock,
    pagePanelMode = value.pagePanelMode,
    brushPanelMode = value.brushPanelMode,
    layerPanelMode = value.layerPanelMode,
    pagePanelLocked = value.pagePanelLocked,
    brushPanelLocked = value.brushPanelLocked,
    layerPanelLocked = value.layerPanelLocked,
    leftDockStacked = value.leftDockStacked,
    rightDockStacked = value.rightDockStacked,
    bottomDockStacked = value.bottomDockStacked,
    freeDockStacked = value.freeDockStacked,
    leftDockActivePanel = value.leftDockActivePanel,
    rightDockActivePanel = value.rightDockActivePanel,
    bottomDockActivePanel = value.bottomDockActivePanel,
    freeDockActivePanel = value.freeDockActivePanel,
    panelTabOrder = WorkspacePanelTabs.encodeOrder(WorkspacePanelTabs.normalizeOrder(value.panelTabOrder)),
    pagePanelXDp = value.pagePanelXDp,
    pagePanelYDp = value.pagePanelYDp,
    pagePanelWidthDp = value.pagePanelWidthDp,
    pagePanelHeightDp = value.pagePanelHeightDp,
    brushPanelXDp = value.brushPanelXDp,
    brushPanelYDp = value.brushPanelYDp,
    brushPanelWidthDp = value.brushPanelWidthDp,
    brushPanelHeightDp = value.brushPanelHeightDp,
    layerPanelXDp = value.layerPanelXDp,
    layerPanelYDp = value.layerPanelYDp,
    layerPanelWidthDp = value.layerPanelWidthDp,
    layerPanelHeightDp = value.layerPanelHeightDp,
    toolbarOffsetXDp = value.toolbarOffsetXDp,
    toolbarOffsetYDp = value.toolbarOffsetYDp,
    verticalToolbar = value.verticalToolbar,
    toolbarDock = value.toolbarDock,
)

object ToolboxQuickSlotCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(values: List<ToolboxQuickSlot>): String = json.encodeToString(
        kotlinx.serialization.json.JsonArray.serializer(),
        buildJsonArray { ToolboxQuickSlots.normalize(values).forEach { add(it.toJson()) } },
    )

    fun decode(raw: String?): List<ToolboxQuickSlot> {
        if (raw.isNullOrBlank() || raw.length > 128 * 1024) return ToolboxQuickSlots.normalize(emptyList())
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.take(ToolboxQuickSlots.COUNT).map { it.jsonObject.toSlot() }
        }.getOrDefault(emptyList()).let(ToolboxQuickSlots::normalize)
    }

    private fun ToolboxQuickSlot.toJson() = buildJsonObject {
        put("kind", kind.name); put("label", label); put("brushId", brushId); put("groupPath", groupPath)
        put("size", size); put("opacity", opacity); put("colorArgb", colorArgb)
        workspace?.let { workspace -> put("workspace", workspace.toJson()) }
    }

    private fun kotlinx.serialization.json.JsonObject.toSlot() = ToolboxQuickSlot(
        kind = optionalEnum("kind", ToolboxQuickSlotKind.EMPTY),
        label = optionalText("label"),
        brushId = optionalText("brushId"),
        groupPath = optionalText("groupPath"),
        size = optionalFloat("size", 5f),
        opacity = optionalFloat("opacity", 1f),
        colorArgb = optionalInt("colorArgb", 0xFF111111.toInt()),
        workspace = get("workspace")?.jsonObject?.toWorkspace(),
    )

    private fun ToolboxWorkspaceSnapshot.toJson() = buildJsonObject {
        put("showPageRail", showPageRail); put("showBrushPanel", showBrushPanel); put("showLayerPanel", showLayerPanel); put("showToolbar", showToolbar)
        put("pagePanelDock", pagePanelDock.name); put("brushPanelDock", brushPanelDock.name); put("layerPanelDock", layerPanelDock.name)
        put("pagePanelMode", pagePanelMode.name); put("brushPanelMode", brushPanelMode.name); put("layerPanelMode", layerPanelMode.name)
        put("pagePanelLocked", pagePanelLocked); put("brushPanelLocked", brushPanelLocked); put("layerPanelLocked", layerPanelLocked)
        put("leftDockStacked", leftDockStacked); put("rightDockStacked", rightDockStacked); put("bottomDockStacked", bottomDockStacked); put("freeDockStacked", freeDockStacked)
        put("leftDockActivePanel", leftDockActivePanel.name); put("rightDockActivePanel", rightDockActivePanel.name); put("bottomDockActivePanel", bottomDockActivePanel.name); put("freeDockActivePanel", freeDockActivePanel.name)
        put("panelTabOrder", WorkspacePanelTabs.encodeOrder(WorkspacePanelTabs.normalizeOrder(panelTabOrder)))
        put("pagePanelXDp", pagePanelXDp); put("pagePanelYDp", pagePanelYDp); put("pagePanelWidthDp", pagePanelWidthDp); put("pagePanelHeightDp", pagePanelHeightDp)
        put("brushPanelXDp", brushPanelXDp); put("brushPanelYDp", brushPanelYDp); put("brushPanelWidthDp", brushPanelWidthDp); put("brushPanelHeightDp", brushPanelHeightDp)
        put("layerPanelXDp", layerPanelXDp); put("layerPanelYDp", layerPanelYDp); put("layerPanelWidthDp", layerPanelWidthDp); put("layerPanelHeightDp", layerPanelHeightDp)
        put("toolbarOffsetXDp", toolbarOffsetXDp); put("toolbarOffsetYDp", toolbarOffsetYDp); put("verticalToolbar", verticalToolbar); put("toolbarDock", toolbarDock.name)
    }

    private fun kotlinx.serialization.json.JsonObject.toWorkspace() = ToolboxWorkspaceSnapshot(
        optionalBoolean("showPageRail"), optionalBoolean("showBrushPanel"), optionalBoolean("showLayerPanel"), optionalBoolean("showToolbar", true),
        optionalEnum("pagePanelDock", WorkspacePanelDock.LEFT), optionalEnum("brushPanelDock", WorkspacePanelDock.RIGHT), optionalEnum("layerPanelDock", WorkspacePanelDock.RIGHT),
        optionalEnum("pagePanelMode", WorkspacePanelMode.EXPANDED), optionalEnum("brushPanelMode", WorkspacePanelMode.EXPANDED), optionalEnum("layerPanelMode", WorkspacePanelMode.EXPANDED),
        optionalBoolean("pagePanelLocked"), optionalBoolean("brushPanelLocked"), optionalBoolean("layerPanelLocked"),
        optionalBoolean("leftDockStacked"), optionalBoolean("rightDockStacked"), optionalBoolean("bottomDockStacked"), optionalBoolean("freeDockStacked"),
        optionalEnum("leftDockActivePanel", WorkspacePanelKind.PAGES), optionalEnum("rightDockActivePanel", WorkspacePanelKind.BRUSHES), optionalEnum("bottomDockActivePanel", WorkspacePanelKind.PAGES), optionalEnum("freeDockActivePanel", WorkspacePanelKind.BRUSHES),
        WorkspacePanelTabs.encodeOrder(WorkspacePanelTabs.normalizeOrder(optionalText("panelTabOrder"))),
        optionalFloat("pagePanelXDp", 16f), optionalFloat("pagePanelYDp", 16f), optionalFloat("pagePanelWidthDp", 160f), optionalFloat("pagePanelHeightDp", 640f),
        optionalFloat("brushPanelXDp", 720f), optionalFloat("brushPanelYDp", 16f), optionalFloat("brushPanelWidthDp", 330f), optionalFloat("brushPanelHeightDp", 680f),
        optionalFloat("layerPanelXDp", 720f), optionalFloat("layerPanelYDp", 16f), optionalFloat("layerPanelWidthDp", 300f), optionalFloat("layerPanelHeightDp", 620f),
        optionalFloat("toolbarOffsetXDp", 16f), optionalFloat("toolbarOffsetYDp", 16f), optionalBoolean("verticalToolbar"), optionalEnum("toolbarDock", ToolbarDock.FREE),
    )

    private fun kotlinx.serialization.json.JsonObject.optionalText(key: String): String = get(key)?.jsonPrimitive?.content.orEmpty()
    private fun kotlinx.serialization.json.JsonObject.optionalFloat(key: String, fallback: Float): Float = runCatching { get(key)?.jsonPrimitive?.float }.getOrNull() ?: fallback
    private fun kotlinx.serialization.json.JsonObject.optionalInt(key: String, fallback: Int): Int = runCatching { get(key)?.jsonPrimitive?.int }.getOrNull() ?: fallback
    private fun kotlinx.serialization.json.JsonObject.optionalBoolean(key: String, fallback: Boolean = false): Boolean = runCatching { get(key)?.jsonPrimitive?.content?.toBooleanStrict() }.getOrNull() ?: fallback
    private inline fun <reified T : Enum<T>> kotlinx.serialization.json.JsonObject.optionalEnum(key: String, fallback: T): T = runCatching { enumValueOf<T>(optionalText(key)) }.getOrDefault(fallback)
}
