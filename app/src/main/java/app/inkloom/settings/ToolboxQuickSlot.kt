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

enum class ToolboxQuickSlotKind { EMPTY, BRUSH, BRUSH_GROUP, TOOL_PRESET }

data class ToolboxQuickSlot(
    val kind: ToolboxQuickSlotKind = ToolboxQuickSlotKind.EMPTY,
    val label: String = "",
    val brushId: String = "",
    val groupPath: String = "",
    val size: Float = 5f,
    val opacity: Float = 1f,
    val colorArgb: Int = 0xFF111111.toInt(),
)

object ToolboxQuickSlots {
    const val COUNT = 8
    private val actions = listOf(
        ShortcutAction.QUICK_SLOT_1, ShortcutAction.QUICK_SLOT_2, ShortcutAction.QUICK_SLOT_3, ShortcutAction.QUICK_SLOT_4,
        ShortcutAction.QUICK_SLOT_5, ShortcutAction.QUICK_SLOT_6, ShortcutAction.QUICK_SLOT_7, ShortcutAction.QUICK_SLOT_8,
    )

    fun indexOf(action: ShortcutAction): Int? = actions.indexOf(action).takeIf { it >= 0 }

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
        ToolboxQuickSlotKind.EMPTY -> null
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
    )
}

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
    }

    private fun kotlinx.serialization.json.JsonObject.toSlot() = ToolboxQuickSlot(
        kind = optionalEnum("kind", ToolboxQuickSlotKind.EMPTY),
        label = optionalText("label"),
        brushId = optionalText("brushId"),
        groupPath = optionalText("groupPath"),
        size = optionalFloat("size", 5f),
        opacity = optionalFloat("opacity", 1f),
        colorArgb = optionalInt("colorArgb", 0xFF111111.toInt()),
    )

    private fun kotlinx.serialization.json.JsonObject.optionalText(key: String): String = get(key)?.jsonPrimitive?.content.orEmpty()
    private fun kotlinx.serialization.json.JsonObject.optionalFloat(key: String, fallback: Float): Float = runCatching { get(key)?.jsonPrimitive?.float }.getOrNull() ?: fallback
    private fun kotlinx.serialization.json.JsonObject.optionalInt(key: String, fallback: Int): Int = runCatching { get(key)?.jsonPrimitive?.int }.getOrNull() ?: fallback
    private inline fun <reified T : Enum<T>> kotlinx.serialization.json.JsonObject.optionalEnum(key: String, fallback: T): T = runCatching { enumValueOf<T>(optionalText(key)) }.getOrDefault(fallback)
}
