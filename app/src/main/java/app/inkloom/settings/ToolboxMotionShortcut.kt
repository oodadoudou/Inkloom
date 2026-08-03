package app.inkloom.settings

import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

enum class ToolboxAxis(val axisCode: Int) {
    ROTARY(MotionEvent.AXIS_SCROLL),
    VERTICAL_SCROLL(MotionEvent.AXIS_VSCROLL),
    HORIZONTAL_SCROLL(MotionEvent.AXIS_HSCROLL),
}

enum class ToolboxAxisMode {
    BRUSH_SIZE,
    OPACITY,
    ZOOM,
}

data class AxisShortcutBinding(
    val axis: ToolboxAxis,
    val mode: ToolboxAxisMode,
    val reversed: Boolean = false,
    val sensitivity: Float = 1f,
    val deadZone: Float = .01f,
    val deviceDescriptor: String? = null,
    val deviceName: String? = null,
) {
    fun normalized(): AxisShortcutBinding = copy(
        sensitivity = sensitivity.takeIf(Float::isFinite)?.coerceIn(.25f, 8f) ?: 1f,
        deadZone = deadZone.takeIf(Float::isFinite)?.coerceIn(0f, .95f) ?: .01f,
        deviceDescriptor = deviceDescriptor?.trim()?.takeIf(String::isNotEmpty)?.take(512),
        deviceName = deviceName?.trim()?.takeIf(String::isNotEmpty)?.take(512),
    )
}

data class ButtonShortcutBinding(
    val button: Int,
    val action: ShortcutAction,
    val trigger: ShortcutTrigger = ShortcutTrigger.PRESS,
    val deviceDescriptor: String? = null,
    val deviceName: String? = null,
) {
    fun normalized(): ButtonShortcutBinding = copy(
        deviceDescriptor = deviceDescriptor?.trim()?.takeIf(String::isNotEmpty)?.take(512),
        deviceName = deviceName?.trim()?.takeIf(String::isNotEmpty)?.take(512),
    )
}

data class MotionInputDiagnostic(
    val action: Int,
    val axisCode: Int? = null,
    val axisValue: Float = 0f,
    val actionButton: Int = 0,
    val buttonState: Int = 0,
    val deviceId: Int,
    val deviceName: String?,
    val deviceDescriptor: String?,
    val source: Int,
    val matchedAction: ShortcutAction?,
)

class ShortcutAxisAccumulator {
    private val remainderByInput = mutableMapOf<String, Float>()

    fun activations(binding: AxisShortcutBinding, rawValue: Float, inputDeviceDescriptor: String? = null): List<ShortcutActivation> {
        val normalized = binding.normalized()
        val value = rawValue.takeIf(Float::isFinite) ?: return emptyList()
        if (abs(value) <= normalized.deadZone) return emptyList()
        val key = "${inputDeviceDescriptor ?: normalized.deviceDescriptor.orEmpty()}:${normalized.axis.name}"
        val direction = if (normalized.reversed) -1f else 1f
        val accumulated = (remainderByInput[key] ?: 0f) + value * normalized.sensitivity * direction
        val wholeSteps = if (accumulated >= 0f) floor(accumulated).toInt() else ceil(accumulated).toInt()
        if (wholeSteps == 0) {
            remainderByInput[key] = accumulated.coerceIn(-.9999f, .9999f)
            return emptyList()
        }
        val boundedSteps = wholeSteps.coerceIn(-MAX_STEPS_PER_EVENT, MAX_STEPS_PER_EVENT)
        remainderByInput[key] = (accumulated - wholeSteps).coerceIn(-.9999f, .9999f)
        val action = actionFor(normalized.mode, boundedSteps > 0)
        return List(abs(boundedSteps)) { ShortcutActivation(action, ShortcutPhase.PRESS, ShortcutTrigger.PRESS) }
    }

    fun clear() = remainderByInput.clear()

    companion object {
        const val MAX_STEPS_PER_EVENT = 32

        fun actionFor(mode: ToolboxAxisMode, positive: Boolean): ShortcutAction = when (mode) {
            ToolboxAxisMode.BRUSH_SIZE -> if (positive) ShortcutAction.BRUSH_SIZE_INCREASE else ShortcutAction.BRUSH_SIZE_DECREASE
            ToolboxAxisMode.OPACITY -> if (positive) ShortcutAction.OPACITY_INCREASE else ShortcutAction.OPACITY_DECREASE
            ToolboxAxisMode.ZOOM -> if (positive) ShortcutAction.ZOOM_IN else ShortcutAction.ZOOM_OUT
        }
    }
}

object ToolboxMotionResolver {
    fun resolveAxis(axis: ToolboxAxis, bindings: List<AxisShortcutBinding>, deviceDescriptor: String?): AxisShortcutBinding? {
        val matching = bindings.map(AxisShortcutBinding::normalized).filter { binding ->
            binding.axis == axis && (binding.deviceDescriptor == null || binding.deviceDescriptor == deviceDescriptor)
        }
        return matching.firstOrNull { it.deviceDescriptor == deviceDescriptor && deviceDescriptor != null }
            ?: matching.firstOrNull { it.deviceDescriptor == null }
    }

    fun resolveButton(button: Int, bindings: List<ButtonShortcutBinding>, deviceDescriptor: String?): ButtonShortcutBinding? {
        val matching = bindings.map(ButtonShortcutBinding::normalized).filter { binding ->
            binding.button == button && (binding.deviceDescriptor == null || binding.deviceDescriptor == deviceDescriptor)
        }
        return matching.firstOrNull { it.deviceDescriptor == deviceDescriptor && deviceDescriptor != null }
            ?: matching.firstOrNull { it.deviceDescriptor == null }
    }

    fun replaceAxis(bindings: List<AxisShortcutBinding>, binding: AxisShortcutBinding): List<AxisShortcutBinding> {
        val normalized = binding.normalized()
        return bindings.filterNot { it.axis == normalized.axis && it.deviceDescriptor == normalized.deviceDescriptor } + normalized
    }

    fun replaceButton(bindings: List<ButtonShortcutBinding>, binding: ButtonShortcutBinding): List<ButtonShortcutBinding> {
        val normalized = binding.normalized()
        return bindings.filterNot { it.button == normalized.button && it.deviceDescriptor == normalized.deviceDescriptor } + normalized
    }

    fun buttonActivation(binding: ButtonShortcutBinding, phase: ShortcutPhase): ShortcutActivation? = when {
        phase == ShortcutPhase.PRESS -> ShortcutActivation(binding.action, phase, binding.trigger)
        binding.trigger == ShortcutTrigger.HOLD -> ShortcutActivation(binding.action, phase, binding.trigger)
        else -> null
    }
}

object ToolboxMotionBindingCodec {
    fun encodeAxes(bindings: List<AxisShortcutBinding>): String = bindings.map(AxisShortcutBinding::normalized).joinToString("|") { binding ->
        listOf(binding.axis.name, binding.mode.name, binding.reversed, binding.sensitivity, binding.deadZone, encodeText(binding.deviceDescriptor), encodeText(binding.deviceName)).joinToString(",")
    }

    fun decodeAxes(raw: String?): List<AxisShortcutBinding> = raw.orEmpty().split('|').mapNotNull { entry ->
        if (entry.isBlank()) return@mapNotNull null
        val fields = entry.split(',')
        runCatching {
            require(fields.size == 7)
            AxisShortcutBinding(enumValueOf(fields[0]), enumValueOf(fields[1]), fields[2].toBooleanStrict(), fields[3].toFloat(), fields[4].toFloat(), decodeText(fields[5]), decodeText(fields[6])).normalized()
        }.getOrNull()
    }.distinct()

    fun encodeButtons(bindings: List<ButtonShortcutBinding>): String = bindings.map(ButtonShortcutBinding::normalized).joinToString("|") { binding ->
        listOf(binding.button, binding.action.name, binding.trigger.name, encodeText(binding.deviceDescriptor), encodeText(binding.deviceName)).joinToString(",")
    }

    fun decodeButtons(raw: String?): List<ButtonShortcutBinding> = raw.orEmpty().split('|').mapNotNull { entry ->
        if (entry.isBlank()) return@mapNotNull null
        val fields = entry.split(',')
        runCatching {
            require(fields.size == 5)
            val button = fields[0].toInt()
            require(button > 0 && button.countOneBits() == 1)
            ButtonShortcutBinding(button, enumValueOf(fields[1]), enumValueOf(fields[2]), decodeText(fields[3]), decodeText(fields[4])).normalized()
        }.getOrNull()
    }.distinct()

    private fun encodeText(value: String?): String = value?.toByteArray(Charsets.UTF_8)?.let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it) }.orEmpty()
    private fun decodeText(value: String): String? = value.takeIf(String::isNotEmpty)?.let { String(java.util.Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
}
