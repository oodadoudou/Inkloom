package app.inkloom.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class ShortcutProfile(
    val bindings: List<ShortcutBinding>,
    val brushGroupTargets: ShortcutBrushGroupTargets = ShortcutBrushGroupTargets(),
    val quickSlots: List<ToolboxQuickSlot> = ToolboxQuickSlots.normalize(emptyList()),
)

object ShortcutProfileCodec {
    const val FILE_EXTENSION = "inkloom-shortcuts.json"
    const val MIME_TYPE = "application/json"
    const val MAX_FILE_BYTES = 256 * 1024
    private const val FORMAT = "inkloom-shortcuts"
    private const val VERSION = 3
    private const val MAX_BINDINGS = 512
    private const val MAX_DEVICE_TEXT_LENGTH = 512
    private val json = Json { prettyPrint = true }

    fun encode(bindings: List<ShortcutBinding>): ByteArray = encode(ShortcutProfile(bindings))

    fun encode(profile: ShortcutProfile): ByteArray {
        require(profile.bindings.size in 1..MAX_BINDINGS)
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("bindings", buildJsonArray {
                profile.bindings.forEach { binding ->
                    add(buildJsonObject {
                        put("action", binding.action.name)
                        put("keyCode", binding.chord.keyCode)
                        put("ctrl", binding.chord.ctrl)
                        put("alt", binding.chord.alt)
                        put("shift", binding.chord.shift)
                        put("scanCode", binding.chord.scanCode)
                        put("matchMode", binding.chord.matchMode.name)
                        put("trigger", binding.trigger.name)
                        binding.deviceDescriptor?.let { put("deviceDescriptor", it) }
                        binding.deviceName?.let { put("deviceName", it) }
                    })
                }
            })
            put("brushGroupTargets", buildJsonObject {
                put("pen", profile.brushGroupTargets.pen)
                put("pencil", profile.brushGroupTargets.pencil)
                put("brush", profile.brushGroupTargets.brush)
            })
            put("quickSlots", json.parseToJsonElement(ToolboxQuickSlotCodec.encode(profile.quickSlots)))
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root).toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): Result<List<ShortcutBinding>> = decodeProfile(bytes).map(ShortcutProfile::bindings)

    fun decodeProfile(bytes: ByteArray): Result<ShortcutProfile> = runCatching {
        require(bytes.isNotEmpty()) { "Shortcut profile is empty." }
        require(bytes.size <= MAX_FILE_BYTES) { "Shortcut profile is too large." }
        val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(root.requiredText("format") == FORMAT) { "Not an Inkloom shortcut profile." }
        val version = root.requiredInt("version")
        require(version in 1..VERSION) { "Unsupported shortcut profile version." }
        val values = root.getValue("bindings").jsonArray
        require(values.size in 1..MAX_BINDINGS) { "Shortcut profile has an invalid binding count." }
        val bindings = values.map { value ->
            val item = value.jsonObject
            val descriptor = item.optionalText("deviceDescriptor")
            val name = item.optionalText("deviceName")
            require(descriptor == null || descriptor.length <= MAX_DEVICE_TEXT_LENGTH) { "Device descriptor is too long." }
            require(name == null || name.length <= MAX_DEVICE_TEXT_LENGTH) { "Device name is too long." }
            val keyCode = item.requiredInt("keyCode")
            val scanCode = item.requiredInt("scanCode")
            require(keyCode in 0..65_535 && scanCode in 0..65_535) { "Shortcut key code is outside the supported range." }
            ShortcutBinding(
                action = enumValueOf(item.requiredText("action")),
                chord = ShortcutChord(
                    keyCode = keyCode,
                    ctrl = item.requiredBoolean("ctrl"),
                    alt = item.requiredBoolean("alt"),
                    shift = item.requiredBoolean("shift"),
                    scanCode = scanCode,
                    matchMode = enumValueOf(item.requiredText("matchMode")),
                ),
                trigger = enumValueOf(item.requiredText("trigger")),
                deviceDescriptor = descriptor,
                deviceName = name,
            )
        }.distinct()
        val targets = if (version >= 2) root["brushGroupTargets"]?.jsonObject?.let { item ->
            ShortcutBrushGroupTargets(
                pen = item.optionalText("pen").orEmpty(),
                pencil = item.optionalText("pencil").orEmpty(),
                brush = item.optionalText("brush").orEmpty(),
            ).normalized()
        } ?: ShortcutBrushGroupTargets() else ShortcutBrushGroupTargets()
        val quickSlots = if (version >= 3) root["quickSlots"]?.let { ToolboxQuickSlotCodec.decode(it.toString()) }
            ?: ToolboxQuickSlots.normalize(emptyList()) else ToolboxQuickSlots.normalize(emptyList())
        ShortcutProfile(bindings, targets, quickSlots)
    }

    fun decode(input: InputStream): Result<List<ShortcutBinding>> = runCatching {
        decodeProfile(input).getOrThrow().bindings
    }

    fun decodeProfile(input: InputStream): Result<ShortcutProfile> = runCatching {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_FILE_BYTES) { "Shortcut profile is too large." }
            output.write(buffer, 0, count)
        }
        decodeProfile(output.toByteArray()).getOrThrow()
    }

    private fun kotlinx.serialization.json.JsonObject.requiredText(key: String): String = getValue(key).jsonPrimitive.content
    private fun kotlinx.serialization.json.JsonObject.optionalText(key: String): String? = get(key)?.jsonPrimitive?.content
    private fun kotlinx.serialization.json.JsonObject.requiredInt(key: String): Int = getValue(key).jsonPrimitive.int
    private fun kotlinx.serialization.json.JsonObject.requiredBoolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean
}
