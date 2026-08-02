package app.inkloom.settings

import android.content.Context

data class AppSettings(
    val language: UiLanguage = UiLanguage.ENGLISH,
    val fingerDrawing: Boolean = false,
    val showPageRail: Boolean = true,
    val showBrushPanel: Boolean = false,
    val showLayerPanel: Boolean = false,
    val pagePanelDock: WorkspacePanelDock = WorkspacePanelDock.LEFT,
    val brushPanelDock: WorkspacePanelDock = WorkspacePanelDock.RIGHT,
    val layerPanelDock: WorkspacePanelDock = WorkspacePanelDock.RIGHT,
    val showToolbar: Boolean = true,
    val toolbarOffsetXDp: Float = 16f,
    val toolbarOffsetYDp: Float = 16f,
    val verticalToolbar: Boolean = false,
    val toolbarDock: ToolbarDock = ToolbarDock.FREE,
    val twoFingerDoubleTapUndo: Boolean = true,
    val stylusPrimaryButton: StylusButtonAction = StylusButtonAction.ERASER,
    val stylusSecondaryButton: StylusButtonAction = StylusButtonAction.LASSO,
    val pressureSensitivity: Float = 1f,
    val pressureMinimumInput: Float = 0f,
    val pressureMaximumInput: Float = 1f,
    val stabilization: Float = .12f,
    val showInputDiagnostics: Boolean = false,
    val penSize: Float = 5f,
    val penOpacity: Float = 1f,
    val highlighterSize: Float = 28f,
    val highlighterOpacity: Float = .35f,
    val eraserSize: Float = 24f,
    val eraserPreset: EraserPreset = EraserPreset.NORMAL_FREE,
    val selectionShape: SelectionShape = SelectionShape.FREEFORM,
    val selectionMode: SelectionMode = SelectionMode.REPLACE,
    val selectionFilter: SelectionFilter = SelectionFilter.ALL,
    val nonDestructiveEraser: Boolean = false,
    val shortcutBindings: List<ShortcutBinding> = CspShortcutResolver.defaultBindings,
)

enum class StylusButtonAction { ERASER, LASSO, NONE }
enum class ToolbarDock { FREE, TOP, BOTTOM, LEFT, RIGHT }
enum class WorkspacePanelDock { LEFT, RIGHT }
enum class SelectionShape { FREEFORM, RECTANGLE }
enum class SelectionMode { REPLACE, ADD, SUBTRACT, INTERSECT }
enum class SelectionFilter { ALL, ACTIVE_LAYER, CURRENT_BRUSH, CURRENT_COLOR }

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
    private val systemLanguage = UiLanguage.fromLanguageTag(context.resources.configuration.locales[0]?.toLanguageTag())

    fun load(): AppSettings {
        val pressureMinimum = preferences.getFloat("pressureMinimumInput", 0f).coerceIn(0f, .95f)
        val pressureMaximum = preferences.getFloat("pressureMaximumInput", 1f).coerceIn(pressureMinimum + .05f, 1f)
        return AppSettings(
            language = enumValue(preferences.getString("language", null), systemLanguage),
            fingerDrawing = preferences.getBoolean("fingerDrawing", false),
            showPageRail = preferences.getBoolean("showPageRail", true),
            showBrushPanel = preferences.getBoolean("showBrushPanel", false),
            showLayerPanel = preferences.getBoolean("showLayerPanel", false),
            pagePanelDock = enumValue(preferences.getString("pagePanelDock", null), WorkspacePanelDock.LEFT),
            brushPanelDock = enumValue(preferences.getString("brushPanelDock", null), WorkspacePanelDock.RIGHT),
            layerPanelDock = enumValue(preferences.getString("layerPanelDock", null), WorkspacePanelDock.RIGHT),
            showToolbar = preferences.getBoolean("showToolbar", true),
            toolbarOffsetXDp = preferences.getFloat("toolbarOffsetXDp", 16f).coerceIn(-1200f, 1200f),
            toolbarOffsetYDp = preferences.getFloat("toolbarOffsetYDp", 16f).coerceIn(-1200f, 1200f),
            verticalToolbar = preferences.getBoolean("verticalToolbar", false),
            toolbarDock = enumValue(preferences.getString("toolbarDock", null), ToolbarDock.FREE),
            twoFingerDoubleTapUndo = preferences.getBoolean("twoFingerDoubleTapUndo", true),
            stylusPrimaryButton = enumValue(preferences.getString("stylusPrimaryButton", null), StylusButtonAction.ERASER),
            stylusSecondaryButton = enumValue(preferences.getString("stylusSecondaryButton", null), StylusButtonAction.LASSO),
            pressureSensitivity = preferences.getFloat("pressureSensitivity", 1f).coerceIn(.25f, 2.5f),
            pressureMinimumInput = pressureMinimum,
            pressureMaximumInput = pressureMaximum,
            stabilization = preferences.getFloat("stabilization", .12f).coerceIn(0f, .9f),
            showInputDiagnostics = preferences.getBoolean("showInputDiagnostics", false),
            penSize = preferences.getFloat("penSize", 5f).coerceIn(.5f, 400f),
            penOpacity = preferences.getFloat("penOpacity", 1f).coerceIn(.05f, 1f),
            highlighterSize = preferences.getFloat("highlighterSize", 28f).coerceIn(.5f, 400f),
            highlighterOpacity = preferences.getFloat("highlighterOpacity", .35f).coerceIn(.05f, 1f),
            eraserSize = preferences.getFloat("eraserSize", 24f).coerceIn(.5f, 400f),
            eraserPreset = enumValue(preferences.getString("eraserPreset", null), EraserPreset.NORMAL_FREE),
            selectionShape = enumValue(preferences.getString("selectionShape", null), SelectionShape.FREEFORM),
            selectionMode = enumValue(preferences.getString("selectionMode", null), SelectionMode.REPLACE),
            selectionFilter = enumValue(preferences.getString("selectionFilter", null), SelectionFilter.ALL),
            nonDestructiveEraser = preferences.getBoolean("nonDestructiveEraser", false),
            shortcutBindings = ShortcutBindingCodec.decode(preferences.getString("shortcutBindings", null)),
        )
    }

    fun save(value: AppSettings) {
        preferences.edit()
            .putString("language", value.language.name)
            .putBoolean("fingerDrawing", value.fingerDrawing)
            .putBoolean("showPageRail", value.showPageRail)
            .putBoolean("showBrushPanel", value.showBrushPanel)
            .putBoolean("showLayerPanel", value.showLayerPanel)
            .putString("pagePanelDock", value.pagePanelDock.name)
            .putString("brushPanelDock", value.brushPanelDock.name)
            .putString("layerPanelDock", value.layerPanelDock.name)
            .putBoolean("showToolbar", value.showToolbar)
            .putFloat("toolbarOffsetXDp", value.toolbarOffsetXDp)
            .putFloat("toolbarOffsetYDp", value.toolbarOffsetYDp)
            .putBoolean("verticalToolbar", value.verticalToolbar)
            .putString("toolbarDock", value.toolbarDock.name)
            .putBoolean("twoFingerDoubleTapUndo", value.twoFingerDoubleTapUndo)
            .putString("stylusPrimaryButton", value.stylusPrimaryButton.name)
            .putString("stylusSecondaryButton", value.stylusSecondaryButton.name)
            .putFloat("pressureSensitivity", value.pressureSensitivity)
            .putFloat("pressureMinimumInput", value.pressureMinimumInput)
            .putFloat("pressureMaximumInput", value.pressureMaximumInput)
            .putFloat("stabilization", value.stabilization)
            .putBoolean("showInputDiagnostics", value.showInputDiagnostics)
            .putFloat("penSize", value.penSize)
            .putFloat("penOpacity", value.penOpacity)
            .putFloat("highlighterSize", value.highlighterSize)
            .putFloat("highlighterOpacity", value.highlighterOpacity)
            .putFloat("eraserSize", value.eraserSize)
            .putString("eraserPreset", value.eraserPreset.name)
            .putString("selectionShape", value.selectionShape.name)
            .putString("selectionMode", value.selectionMode.name)
            .putString("selectionFilter", value.selectionFilter.name)
            .putBoolean("nonDestructiveEraser", value.nonDestructiveEraser)
            .putString("shortcutBindings", ShortcutBindingCodec.encode(value.shortcutBindings))
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T = runCatching { enumValueOf<T>(raw.orEmpty()) }.getOrDefault(fallback)
}

object ShortcutBindingCodec {
    fun encode(bindings: List<ShortcutBinding>): String = bindings.joinToString("|") { binding ->
        listOf(
            binding.action.name,
            binding.chord.keyCode,
            binding.chord.ctrl,
            binding.chord.alt,
            binding.chord.shift,
            binding.chord.scanCode,
            binding.chord.matchMode.name,
            binding.trigger.name,
            encodeText(binding.deviceDescriptor),
            encodeText(binding.deviceName),
        ).joinToString(",")
    }

    fun decode(raw: String?): List<ShortcutBinding> {
        if (raw.isNullOrBlank()) return CspShortcutResolver.defaultBindings
        val decoded = raw.split('|').mapNotNull { entry ->
            val fields = entry.split(',')
            runCatching {
                if (fields.size == 5) {
                    ShortcutBinding(
                        action = enumValueOf(fields[0]),
                        chord = ShortcutChord(fields[1].toInt(), fields[2].toBooleanStrict(), fields[3].toBooleanStrict(), fields[4].toBooleanStrict()),
                    )
                } else {
                    require(fields.size == 10)
                    ShortcutBinding(
                        action = enumValueOf(fields[0]),
                        chord = ShortcutChord(
                            keyCode = fields[1].toInt(),
                            ctrl = fields[2].toBooleanStrict(),
                            alt = fields[3].toBooleanStrict(),
                            shift = fields[4].toBooleanStrict(),
                            scanCode = fields[5].toInt(),
                            matchMode = enumValueOf(fields[6]),
                        ),
                        trigger = enumValueOf(fields[7]),
                        deviceDescriptor = decodeText(fields[8]),
                        deviceName = decodeText(fields[9]),
                    )
                }
            }.getOrNull()
        }
        return decoded.takeIf(List<ShortcutBinding>::isNotEmpty) ?: CspShortcutResolver.defaultBindings
    }

    private fun encodeText(value: String?): String = value?.toByteArray(Charsets.UTF_8)?.let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it) }.orEmpty()
    private fun decodeText(value: String): String? = value.takeIf(String::isNotEmpty)?.let { String(java.util.Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
}
