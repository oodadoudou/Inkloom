package app.inkloom.settings

import android.content.Context
import androidx.core.content.edit

data class AppSettings(
    val language: UiLanguage = UiLanguage.ENGLISH,
    val fingerDrawing: Boolean = false,
    val showPageRail: Boolean = true,
    val showBrushPanel: Boolean = false,
    val showLayerPanel: Boolean = false,
    val pagePanelDock: WorkspacePanelDock = WorkspacePanelDock.LEFT,
    val brushPanelDock: WorkspacePanelDock = WorkspacePanelDock.RIGHT,
    val layerPanelDock: WorkspacePanelDock = WorkspacePanelDock.RIGHT,
    val pagePanelXDp: Float = 16f,
    val pagePanelYDp: Float = 16f,
    val pagePanelWidthDp: Float = 160f,
    val pagePanelHeightDp: Float = 640f,
    val brushPanelXDp: Float = 720f,
    val brushPanelYDp: Float = 16f,
    val brushPanelWidthDp: Float = 330f,
    val brushPanelHeightDp: Float = 680f,
    val layerPanelXDp: Float = 720f,
    val layerPanelYDp: Float = 16f,
    val layerPanelWidthDp: Float = 300f,
    val layerPanelHeightDp: Float = 620f,
    val showToolbar: Boolean = true,
    val toolbarOffsetXDp: Float = 16f,
    val toolbarOffsetYDp: Float = 16f,
    val verticalToolbar: Boolean = false,
    val toolbarDock: ToolbarDock = ToolbarDock.FREE,
    val twoFingerDoubleTapUndo: Boolean = true,
    val threeFingerTapRedo: Boolean = true,
    val twoFingerRotation: Boolean = false,
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
    val shortcutBrushGroupTargets: ShortcutBrushGroupTargets = ShortcutBrushGroupTargets(),
    val toolboxQuickSlots: List<ToolboxQuickSlot> = ToolboxQuickSlots.normalize(emptyList()),
    val workspaceLayouts: WorkspaceLayoutProfiles = WorkspaceLayoutProfiles(),
    val customWorkspaces: List<CustomWorkspaceProfile> = emptyList(),
)

enum class StylusButtonAction {
    ERASER,
    LASSO,
    HAND,
    EYEDROPPER,
    UNDO,
    REDO,
    NONE;

    val isCommand: Boolean get() = this == UNDO || this == REDO
}

enum class ToolbarDock { FREE, TOP, BOTTOM, LEFT, RIGHT }
enum class WorkspacePanelDock { LEFT, FREE, RIGHT, BOTTOM }
enum class SelectionShape { FREEFORM, RECTANGLE }
enum class SelectionMode { REPLACE, ADD, SUBTRACT, INTERSECT }
enum class SelectionFilter { ALL, ACTIVE_LAYER, CURRENT_BRUSH, CURRENT_COLOR }

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
    private val systemLanguage = UiLanguage.fromLanguageTag(context.resources.configuration.locales[0]?.toLanguageTag())
    private val orientation = WorkspaceOrientation.from(context.resources.configuration)

    fun load(): AppSettings {
        val pressureMinimum = preferences.getFloat("pressureMinimumInput", 0f).coerceIn(0f, .95f)
        val pressureMaximum = preferences.getFloat("pressureMaximumInput", 1f).coerceIn(pressureMinimum + .05f, 1f)
        val loaded = AppSettings(
            language = enumValue(preferences.getString("language", null), systemLanguage),
            fingerDrawing = preferences.getBoolean("fingerDrawing", false),
            showPageRail = preferences.getBoolean("showPageRail", true),
            showBrushPanel = preferences.getBoolean("showBrushPanel", false),
            showLayerPanel = preferences.getBoolean("showLayerPanel", false),
            pagePanelDock = enumValue(preferences.getString("pagePanelDock", null), WorkspacePanelDock.LEFT),
            brushPanelDock = enumValue(preferences.getString("brushPanelDock", null), WorkspacePanelDock.RIGHT),
            layerPanelDock = enumValue(preferences.getString("layerPanelDock", null), WorkspacePanelDock.RIGHT),
            pagePanelXDp = preferences.getFloat("pagePanelXDp", 16f).coerceIn(-2400f, 2400f),
            pagePanelYDp = preferences.getFloat("pagePanelYDp", 16f).coerceIn(-2400f, 2400f),
            pagePanelWidthDp = preferences.getFloat("pagePanelWidthDp", 160f).coerceIn(80f, 1200f),
            pagePanelHeightDp = preferences.getFloat("pagePanelHeightDp", 640f).coerceIn(120f, 1600f),
            brushPanelXDp = preferences.getFloat("brushPanelXDp", 720f).coerceIn(-2400f, 2400f),
            brushPanelYDp = preferences.getFloat("brushPanelYDp", 16f).coerceIn(-2400f, 2400f),
            brushPanelWidthDp = preferences.getFloat("brushPanelWidthDp", 330f).coerceIn(80f, 1200f),
            brushPanelHeightDp = preferences.getFloat("brushPanelHeightDp", 680f).coerceIn(120f, 1600f),
            layerPanelXDp = preferences.getFloat("layerPanelXDp", 720f).coerceIn(-2400f, 2400f),
            layerPanelYDp = preferences.getFloat("layerPanelYDp", 16f).coerceIn(-2400f, 2400f),
            layerPanelWidthDp = preferences.getFloat("layerPanelWidthDp", 300f).coerceIn(80f, 1200f),
            layerPanelHeightDp = preferences.getFloat("layerPanelHeightDp", 620f).coerceIn(120f, 1600f),
            showToolbar = preferences.getBoolean("showToolbar", true),
            toolbarOffsetXDp = preferences.getFloat("toolbarOffsetXDp", 16f).coerceIn(-1200f, 1200f),
            toolbarOffsetYDp = preferences.getFloat("toolbarOffsetYDp", 16f).coerceIn(-1200f, 1200f),
            verticalToolbar = preferences.getBoolean("verticalToolbar", false),
            toolbarDock = enumValue(preferences.getString("toolbarDock", null), ToolbarDock.FREE),
            twoFingerDoubleTapUndo = preferences.getBoolean("twoFingerDoubleTapUndo", true),
            threeFingerTapRedo = preferences.getBoolean("threeFingerTapRedo", true),
            twoFingerRotation = preferences.getBoolean("twoFingerRotation", false),
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
            shortcutBrushGroupTargets = ShortcutBrushGroupTargets(
                pen = preferences.getString("shortcutPenGroup", "").orEmpty(),
                pencil = preferences.getString("shortcutPencilGroup", "").orEmpty(),
                brush = preferences.getString("shortcutBrushGroup", "").orEmpty(),
            ).normalized(),
            toolboxQuickSlots = ToolboxQuickSlotCodec.decode(preferences.getString("toolboxQuickSlots", null)),
            workspaceLayouts = WorkspaceLayoutProfilesCodec.decode(preferences.getString("workspaceLayouts", null)),
            customWorkspaces = CustomWorkspaceCodec.decodeLibrary(preferences.getString("customWorkspaces", null)),
        )
        val selected = loaded.withWorkspaceFor(orientation)
        if (selected.workspaceLayouts != loaded.workspaceLayouts) {
            preferences.edit { putString("workspaceLayouts", WorkspaceLayoutProfilesCodec.encode(selected.workspaceLayouts)) }
        }
        return selected
    }

    fun save(value: AppSettings): AppSettings {
        val persisted = value.recordWorkspaceFor(orientation)
        preferences.edit()
            .putString("language", persisted.language.name)
            .putBoolean("fingerDrawing", persisted.fingerDrawing)
            .putBoolean("showPageRail", persisted.showPageRail)
            .putBoolean("showBrushPanel", persisted.showBrushPanel)
            .putBoolean("showLayerPanel", persisted.showLayerPanel)
            .putString("pagePanelDock", persisted.pagePanelDock.name)
            .putString("brushPanelDock", persisted.brushPanelDock.name)
            .putString("layerPanelDock", persisted.layerPanelDock.name)
            .putFloat("pagePanelXDp", persisted.pagePanelXDp)
            .putFloat("pagePanelYDp", persisted.pagePanelYDp)
            .putFloat("pagePanelWidthDp", persisted.pagePanelWidthDp)
            .putFloat("pagePanelHeightDp", persisted.pagePanelHeightDp)
            .putFloat("brushPanelXDp", persisted.brushPanelXDp)
            .putFloat("brushPanelYDp", persisted.brushPanelYDp)
            .putFloat("brushPanelWidthDp", persisted.brushPanelWidthDp)
            .putFloat("brushPanelHeightDp", persisted.brushPanelHeightDp)
            .putFloat("layerPanelXDp", persisted.layerPanelXDp)
            .putFloat("layerPanelYDp", persisted.layerPanelYDp)
            .putFloat("layerPanelWidthDp", persisted.layerPanelWidthDp)
            .putFloat("layerPanelHeightDp", persisted.layerPanelHeightDp)
            .putBoolean("showToolbar", persisted.showToolbar)
            .putFloat("toolbarOffsetXDp", persisted.toolbarOffsetXDp)
            .putFloat("toolbarOffsetYDp", persisted.toolbarOffsetYDp)
            .putBoolean("verticalToolbar", persisted.verticalToolbar)
            .putString("toolbarDock", persisted.toolbarDock.name)
            .putBoolean("twoFingerDoubleTapUndo", persisted.twoFingerDoubleTapUndo)
            .putBoolean("threeFingerTapRedo", persisted.threeFingerTapRedo)
            .putBoolean("twoFingerRotation", persisted.twoFingerRotation)
            .putString("stylusPrimaryButton", persisted.stylusPrimaryButton.name)
            .putString("stylusSecondaryButton", persisted.stylusSecondaryButton.name)
            .putFloat("pressureSensitivity", persisted.pressureSensitivity)
            .putFloat("pressureMinimumInput", persisted.pressureMinimumInput)
            .putFloat("pressureMaximumInput", persisted.pressureMaximumInput)
            .putFloat("stabilization", persisted.stabilization)
            .putBoolean("showInputDiagnostics", persisted.showInputDiagnostics)
            .putFloat("penSize", persisted.penSize)
            .putFloat("penOpacity", persisted.penOpacity)
            .putFloat("highlighterSize", persisted.highlighterSize)
            .putFloat("highlighterOpacity", persisted.highlighterOpacity)
            .putFloat("eraserSize", persisted.eraserSize)
            .putString("eraserPreset", persisted.eraserPreset.name)
            .putString("selectionShape", persisted.selectionShape.name)
            .putString("selectionMode", persisted.selectionMode.name)
            .putString("selectionFilter", persisted.selectionFilter.name)
            .putBoolean("nonDestructiveEraser", persisted.nonDestructiveEraser)
            .putString("shortcutBindings", ShortcutBindingCodec.encode(persisted.shortcutBindings))
            .putString("shortcutPenGroup", persisted.shortcutBrushGroupTargets.pen)
            .putString("shortcutPencilGroup", persisted.shortcutBrushGroupTargets.pencil)
            .putString("shortcutBrushGroup", persisted.shortcutBrushGroupTargets.brush)
            .putString("toolboxQuickSlots", ToolboxQuickSlotCodec.encode(persisted.toolboxQuickSlots))
            .putString("workspaceLayouts", WorkspaceLayoutProfilesCodec.encode(persisted.workspaceLayouts))
            .putString("customWorkspaces", CustomWorkspaceCodec.encodeLibrary(persisted.customWorkspaces))
            .apply()
        return persisted
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
        return decoded.takeIf(List<ShortcutBinding>::isNotEmpty)?.let(CspShortcutResolver::migrateLegacyDefaults) ?: CspShortcutResolver.defaultBindings
    }

    private fun encodeText(value: String?): String = value?.toByteArray(Charsets.UTF_8)?.let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it) }.orEmpty()
    private fun decodeText(value: String): String? = value.takeIf(String::isNotEmpty)?.let { String(java.util.Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
}
