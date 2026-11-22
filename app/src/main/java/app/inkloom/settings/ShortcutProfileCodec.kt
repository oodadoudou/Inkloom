package app.inkloom.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.float
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
    val axisBindings: List<AxisShortcutBinding> = emptyList(),
    val buttonBindings: List<ButtonShortcutBinding> = emptyList(),
)

object ShortcutProfileCodec {
    const val FILE_EXTENSION = "inkloom-shortcuts.json"
    const val MIME_TYPE = "application/json"
    const val MAX_FILE_BYTES = 256 * 1024
    private const val FORMAT = "inkloom-shortcuts"
    private const val VERSION = 4
    private const val MAX_BINDINGS = 512
    private const val MAX_MOTION_BINDINGS = 64
    private const val MAX_DEVICE_TEXT_LENGTH = 512
    private val json = Json { prettyPrint = true }

    fun encode(bindings: List<ShortcutBinding>): ByteArray = encode(ShortcutProfile(bindings))

    fun encode(profile: ShortcutProfile): ByteArray {
        require(profile.bindings.size in 1..MAX_BINDINGS)
        require(profile.axisBindings.size <= MAX_MOTION_BINDINGS)
        require(profile.buttonBindings.size <= MAX_MOTION_BINDINGS)
        require(profile.buttonBindings.all { it.button > 0 && it.button.countOneBits() == 1 })
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
            put("axisBindings", buildJsonArray {
                profile.axisBindings.map(AxisShortcutBinding::normalized).forEach { binding ->
                    add(buildJsonObject {
                        put("axis", binding.axis.name)
                        put("mode", binding.mode.name)
                        put("reversed", binding.reversed)
                        put("sensitivity", binding.sensitivity)
                        put("deadZone", binding.deadZone)
                        binding.deviceDescriptor?.let { put("deviceDescriptor", it) }
                        binding.deviceName?.let { put("deviceName", it) }
                    })
                }
            })
            put("buttonBindings", buildJsonArray {
                profile.buttonBindings.map(ButtonShortcutBinding::normalized).forEach { binding ->
                    add(buildJsonObject {
                        put("button", binding.button)
                        put("action", binding.action.name)
                        put("trigger", binding.trigger.name)
                        binding.deviceDescriptor?.let { put("deviceDescriptor", it) }
                        binding.deviceName?.let { put("deviceName", it) }
                    })
                }
            })
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
        // A binding for an action this version no longer has is dropped; the rest of the profile still imports.
        val bindings = values.mapNotNull { value ->
            val item = value.jsonObject
            val action = knownAction(item.requiredText("action")) ?: return@mapNotNull null
            val descriptor = item.optionalText("deviceDescriptor")
            val name = item.optionalText("deviceName")
            require(descriptor == null || descriptor.length <= MAX_DEVICE_TEXT_LENGTH) { "Device descriptor is too long." }
            require(name == null || name.length <= MAX_DEVICE_TEXT_LENGTH) { "Device name is too long." }
            val keyCode = item.requiredInt("keyCode")
            val scanCode = item.requiredInt("scanCode")
            require(keyCode in 0..65_535 && scanCode in 0..65_535) { "Shortcut key code is outside the supported range." }
            ShortcutBinding(
                action = action,
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
        val axisBindings = if (version >= 4) root["axisBindings"]?.jsonArray?.also { require(it.size <= MAX_MOTION_BINDINGS) }?.map { value ->
            val item = value.jsonObject
            val descriptor = item.optionalText("deviceDescriptor")
            val name = item.optionalText("deviceName")
            require(descriptor == null || descriptor.length <= MAX_DEVICE_TEXT_LENGTH) { "Device descriptor is too long." }
            require(name == null || name.length <= MAX_DEVICE_TEXT_LENGTH) { "Device name is too long." }
            AxisShortcutBinding(
                axis = enumValueOf(item.requiredText("axis")),
                mode = enumValueOf(item.requiredText("mode")),
                reversed = item.requiredBoolean("reversed"),
                sensitivity = item.requiredFloat("sensitivity"),
                deadZone = item.requiredFloat("deadZone"),
                deviceDescriptor = descriptor,
                deviceName = name,
            ).normalized()
        }?.distinct().orEmpty() else emptyList()
        val buttonBindings = if (version >= 4) root["buttonBindings"]?.jsonArray?.also { require(it.size <= MAX_MOTION_BINDINGS) }?.mapNotNull { value ->
            val item = value.jsonObject
            val action = knownAction(item.requiredText("action")) ?: return@mapNotNull null
            val descriptor = item.optionalText("deviceDescriptor")
            val name = item.optionalText("deviceName")
            require(descriptor == null || descriptor.length <= MAX_DEVICE_TEXT_LENGTH) { "Device descriptor is too long." }
            require(name == null || name.length <= MAX_DEVICE_TEXT_LENGTH) { "Device name is too long." }
            val button = item.requiredInt("button")
            require(button > 0 && button.countOneBits() == 1) { "Pointer button must contain one supported bit." }
            ButtonShortcutBinding(
                button = button,
                action = action,
                trigger = enumValueOf(item.requiredText("trigger")),
                deviceDescriptor = descriptor,
                deviceName = name,
            ).normalized()
        }?.distinct().orEmpty() else emptyList()
        ShortcutProfile(bindings, targets, quickSlots, axisBindings, buttonBindings)
    }

    private fun knownAction(name: String): ShortcutAction? = ShortcutAction.entries.firstOrNull { it.name == name }

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
    private fun kotlinx.serialization.json.JsonObject.requiredFloat(key: String): Float = getValue(key).jsonPrimitive.float
    private fun kotlinx.serialization.json.JsonObject.requiredBoolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean
}
