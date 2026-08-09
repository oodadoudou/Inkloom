package app.inkloom.settings

import android.content.Context
import androidx.core.content.edit
import app.inkloom.model.SymmetryMode
import app.inkloom.document.CorrectLineMode
import app.inkloom.document.PressureResponseCurve

data class AppSettings(
    val language: UiLanguage = UiLanguage.ENGLISH,
    val fingerDrawing: Boolean = false,
    val fillSensitivity: Float = .25f,
    val showPageRail: Boolean = false,
    val showBrushPanel: Boolean = false,
    val showLayerPanel: Boolean = false,
    val pagePanelDock: WorkspacePanelDock = WorkspacePanelDock.FREE,
    val brushPanelDock: WorkspacePanelDock = WorkspacePanelDock.FREE,
    val layerPanelDock: WorkspacePanelDock = WorkspacePanelDock.FREE,
    val pagePanelMode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    val brushPanelMode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    val layerPanelMode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    val pagePanelLocked: Boolean = false,
    val brushPanelLocked: Boolean = false,
    val layerPanelLocked: Boolean = false,
    val leftDockStacked: Boolean = false,
    val rightDockStacked: Boolean = false,
    val bottomDockStacked: Boolean = false,
    val freeDockStacked: Boolean = false,
    val leftDockActivePanel: WorkspacePanelKind = WorkspacePanelKind.PAGES,
    val rightDockActivePanel: WorkspacePanelKind = WorkspacePanelKind.BRUSHES,
    val bottomDockActivePanel: WorkspacePanelKind = WorkspacePanelKind.PAGES,
    val freeDockActivePanel: WorkspacePanelKind = WorkspacePanelKind.BRUSHES,
    val panelTabOrder: String = "PAGES,BRUSHES,LAYERS",
    val pagePanelXDp: Float = 18f,
    val pagePanelYDp: Float = 18f,
    val pagePanelWidthDp: Float = 300f,
    val pagePanelHeightDp: Float = 620f,
    val brushPanelXDp: Float = 1180f,
    val brushPanelYDp: Float = 18f,
    val brushPanelWidthDp: Float = 620f,
    val brushPanelHeightDp: Float = 620f,
    val layerPanelXDp: Float = 1440f,
    val layerPanelYDp: Float = 18f,
    val layerPanelWidthDp: Float = 420f,
    val layerPanelHeightDp: Float = 620f,
    val showToolbar: Boolean = true,
    val toolbarOffsetXDp: Float = 16f,
    val toolbarOffsetYDp: Float = 16f,
    val verticalToolbar: Boolean = true,
    val toolbarDock: ToolbarDock = ToolbarDock.LEFT,
    val singleFingerDoubleTapUndo: Boolean = false,
    val twoFingerDoubleTapUndo: Boolean = true,
    val threeFingerTapRedo: Boolean = true,
    val twoFingerRotation: Boolean = false,
    val symmetryMode: SymmetryMode = SymmetryMode.NONE,
    val rulerEnabled: Boolean = false,
    val rulerCenterX: Float = .5f,
    val rulerCenterY: Float = .5f,
    val rulerAngleDegrees: Float = 0f,
    val rulerSnapDegrees: Float = 15f,
    val gridSnapEnabled: Boolean = false,
    val gridSpacing: Float = 64f,
    val alignmentGuidesEnabled: Boolean = false,
    val verticalGuideX: Float = .5f,
    val horizontalGuideY: Float = .5f,
    val stylusPrimaryButton: StylusButtonAction = StylusButtonAction.ERASER,
    val stylusSecondaryButton: StylusButtonAction = StylusButtonAction.LASSO,
    val stylusPrimaryTrigger: ShortcutTrigger = ShortcutTrigger.HOLD,
    val stylusSecondaryTrigger: ShortcutTrigger = ShortcutTrigger.HOLD,
    val pressureSensitivity: Float = 1f,
    val pressureMinimumInput: Float = 0f,
    val pressureMaximumInput: Float = 1f,
    val pressureResponseCurve: List<Float> = PressureResponseCurve.LINEAR,
    val stabilization: Float = .12f,
    val postCorrection: Float = 0f,
    val postCorrectionAdjustBySpeed: Boolean = false,
    val postCorrectionAdjustByScale: Boolean = true,
    val showInputDiagnostics: Boolean = false,
    val penSize: Float = 5f,
    val penOpacity: Float = 1f,
    val highlighterSize: Float = 28f,
    val highlighterOpacity: Float = .35f,
    val eraserSize: Float = 24f,
    val eraserPreset: EraserPreset = EraserPreset.NORMAL_FREE,
    val selectionShape: SelectionShape = SelectionShape.FREEFORM,
    val selectionBrushSize: Float = 42f,
    val selectionThreshold: Float = .25f,
    val selectionMode: SelectionMode = SelectionMode.REPLACE,
    val selectionFilter: SelectionFilter = SelectionFilter.ALL,
    val correctLineMode: CorrectLineMode = CorrectLineMode.SMOOTH,
    val correctLineAmount: Float = .55f,
    val nonDestructiveEraser: Boolean = false,
    val shortcutBindings: List<ShortcutBinding> = CspShortcutResolver.defaultBindings,
    val shortcutAxisBindings: List<AxisShortcutBinding> = emptyList(),
    val shortcutButtonBindings: List<ButtonShortcutBinding> = emptyList(),
    val shortcutBrushGroupTargets: ShortcutBrushGroupTargets = ShortcutBrushGroupTargets(),
    val toolboxQuickSlots: List<ToolboxQuickSlot> = ToolboxQuickSlots.normalize(emptyList()),
    val workspaceLayouts: WorkspaceLayoutProfiles = WorkspaceLayoutProfiles(),
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
enum class WorkspacePanelMode { EXPANDED, COLLAPSED, ICON_ONLY }
enum class SelectionShape { FREEFORM, RECTANGLE, ELLIPSE, BRUSH, SMART_BORDER, MAGIC_WAND }
enum class SelectionMode { REPLACE, ADD, SUBTRACT, INTERSECT }
enum class SelectionFilter { ALL, ACTIVE_LAYER, CURRENT_BRUSH, CURRENT_COLOR }

class AppSettingsStore(context: Context) {
    private companion object {
        const val STUDIO_WORKSPACE_VERSION_KEY = "studioWorkspaceVersion"
        const val STUDIO_WORKSPACE_VERSION = 2
    }

    private val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
    private val systemLanguage = UiLanguage.fromLanguageTag(context.resources.configuration.locales[0]?.toLanguageTag())
    private val orientation = WorkspaceOrientation.from(context.resources.configuration)

    fun load(): AppSettings {
        val pressureMinimum = preferences.getFloat("pressureMinimumInput", 0f).coerceIn(0f, .95f)
        val pressureMaximum = preferences.getFloat("pressureMaximumInput", 1f).coerceIn(pressureMinimum + .05f, 1f)
        val loaded = AppSettings(
            language = enumValue(preferences.getString("language", null), systemLanguage),
            fingerDrawing = preferences.getBoolean("fingerDrawing", false),
            fillSensitivity = preferences.getFloat("fillSensitivity", .25f),
            showPageRail = preferences.getBoolean("showPageRail", false),
            showBrushPanel = preferences.getBoolean("showBrushPanel", false),
            showLayerPanel = preferences.getBoolean("showLayerPanel", false),
            pagePanelDock = enumValue(preferences.getString("pagePanelDock", null), WorkspacePanelDock.FREE),
            brushPanelDock = enumValue(preferences.getString("brushPanelDock", null), WorkspacePanelDock.FREE),
            layerPanelDock = enumValue(preferences.getString("layerPanelDock", null), WorkspacePanelDock.FREE),
            pagePanelMode = enumValue(preferences.getString("pagePanelMode", null), WorkspacePanelMode.EXPANDED),
            brushPanelMode = enumValue(preferences.getString("brushPanelMode", null), WorkspacePanelMode.EXPANDED),
            layerPanelMode = enumValue(preferences.getString("layerPanelMode", null), WorkspacePanelMode.EXPANDED),
            pagePanelLocked = preferences.getBoolean("pagePanelLocked", false),
            brushPanelLocked = preferences.getBoolean("brushPanelLocked", false),
            layerPanelLocked = preferences.getBoolean("layerPanelLocked", false),
            leftDockStacked = preferences.getBoolean("leftDockStacked", false),
            rightDockStacked = preferences.getBoolean("rightDockStacked", false),
            bottomDockStacked = preferences.getBoolean("bottomDockStacked", false),
            freeDockStacked = preferences.getBoolean("freeDockStacked", false),
            leftDockActivePanel = enumValue(preferences.getString("leftDockActivePanel", null), WorkspacePanelKind.PAGES),
            rightDockActivePanel = enumValue(preferences.getString("rightDockActivePanel", null), WorkspacePanelKind.BRUSHES),
            bottomDockActivePanel = enumValue(preferences.getString("bottomDockActivePanel", null), WorkspacePanelKind.PAGES),
            freeDockActivePanel = enumValue(preferences.getString("freeDockActivePanel", null), WorkspacePanelKind.BRUSHES),
            panelTabOrder = WorkspacePanelTabs.encodeOrder(WorkspacePanelTabs.normalizeOrder(preferences.getString("panelTabOrder", null).orEmpty())),
            pagePanelXDp = preferences.getFloat("pagePanelXDp", 18f).coerceIn(-2400f, 2400f),
            pagePanelYDp = preferences.getFloat("pagePanelYDp", 18f).coerceIn(-2400f, 2400f),
            pagePanelWidthDp = preferences.getFloat("pagePanelWidthDp", 300f).coerceIn(80f, 1200f),
            pagePanelHeightDp = preferences.getFloat("pagePanelHeightDp", 620f).coerceIn(120f, 1600f),
            brushPanelXDp = preferences.getFloat("brushPanelXDp", 1180f).coerceIn(-2400f, 2400f),
            brushPanelYDp = preferences.getFloat("brushPanelYDp", 18f).coerceIn(-2400f, 2400f),
            brushPanelWidthDp = preferences.getFloat("brushPanelWidthDp", 620f).coerceIn(80f, 1200f),
            brushPanelHeightDp = preferences.getFloat("brushPanelHeightDp", 620f).coerceIn(120f, 1600f),
            layerPanelXDp = preferences.getFloat("layerPanelXDp", 1440f).coerceIn(-2400f, 2400f),
            layerPanelYDp = preferences.getFloat("layerPanelYDp", 18f).coerceIn(-2400f, 2400f),
            layerPanelWidthDp = preferences.getFloat("layerPanelWidthDp", 420f).coerceIn(80f, 1200f),
            layerPanelHeightDp = preferences.getFloat("layerPanelHeightDp", 620f).coerceIn(120f, 1600f),
            showToolbar = preferences.getBoolean("showToolbar", true),
            toolbarOffsetXDp = preferences.getFloat("toolbarOffsetXDp", 16f).coerceIn(-1200f, 1200f),
            toolbarOffsetYDp = preferences.getFloat("toolbarOffsetYDp", 16f).coerceIn(-1200f, 1200f),
            verticalToolbar = preferences.getBoolean("verticalToolbar", true),
            toolbarDock = enumValue(preferences.getString("toolbarDock", null), ToolbarDock.LEFT),
            singleFingerDoubleTapUndo = preferences.getBoolean("singleFingerDoubleTapUndo", false),
            twoFingerDoubleTapUndo = preferences.getBoolean("twoFingerDoubleTapUndo", true),
            threeFingerTapRedo = preferences.getBoolean("threeFingerTapRedo", true),
            twoFingerRotation = preferences.getBoolean("twoFingerRotation", false),
            symmetryMode = enumValue(preferences.getString("symmetryMode", null), SymmetryMode.NONE),
            rulerEnabled = preferences.getBoolean("rulerEnabled", false),
            rulerCenterX = preferences.getFloat("rulerCenterX", .5f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
            rulerCenterY = preferences.getFloat("rulerCenterY", .5f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
            rulerAngleDegrees = app.inkloom.document.RulerGuideState.normalizeAngle(preferences.getFloat("rulerAngleDegrees", 0f)),
            rulerSnapDegrees = preferences.getFloat("rulerSnapDegrees", 15f).takeIf(Float::isFinite)?.coerceIn(1f, 90f) ?: 15f,
            gridSnapEnabled = preferences.getBoolean("gridSnapEnabled", false),
            gridSpacing = preferences.getFloat("gridSpacing", 64f).takeIf(Float::isFinite)?.coerceIn(16f, 400f) ?: 64f,
            alignmentGuidesEnabled = preferences.getBoolean("alignmentGuidesEnabled", false),
            verticalGuideX = preferences.getFloat("verticalGuideX", .5f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
            horizontalGuideY = preferences.getFloat("horizontalGuideY", .5f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
            stylusPrimaryButton = enumValue(preferences.getString("stylusPrimaryButton", null), StylusButtonAction.ERASER),
            stylusSecondaryButton = enumValue(preferences.getString("stylusSecondaryButton", null), StylusButtonAction.LASSO),
            stylusPrimaryTrigger = enumValue(preferences.getString("stylusPrimaryTrigger", null), ShortcutTrigger.HOLD),
            stylusSecondaryTrigger = enumValue(preferences.getString("stylusSecondaryTrigger", null), ShortcutTrigger.HOLD),
            pressureSensitivity = preferences.getFloat("pressureSensitivity", 1f).coerceIn(.25f, 2.5f),
            pressureMinimumInput = pressureMinimum,
            pressureMaximumInput = pressureMaximum,
            pressureResponseCurve = PressureResponseCurve.decode(preferences.getString("pressureResponseCurve", null)),
            stabilization = preferences.getFloat("stabilization", .12f).coerceIn(0f, .9f),
            postCorrection = preferences.getFloat("postCorrection", 0f).coerceIn(0f, 1f),
            postCorrectionAdjustBySpeed = preferences.getBoolean("postCorrectionAdjustBySpeed", false),
            postCorrectionAdjustByScale = preferences.getBoolean("postCorrectionAdjustByScale", true),
            showInputDiagnostics = preferences.getBoolean("showInputDiagnostics", false),
            penSize = preferences.getFloat("penSize", 5f).coerceIn(.5f, 400f),
            penOpacity = preferences.getFloat("penOpacity", 1f).coerceIn(.05f, 1f),
            highlighterSize = preferences.getFloat("highlighterSize", 28f).coerceIn(.5f, 400f),
            highlighterOpacity = preferences.getFloat("highlighterOpacity", .35f).coerceIn(.05f, 1f),
            eraserSize = preferences.getFloat("eraserSize", 24f).coerceIn(.5f, 400f),
            eraserPreset = enumValue(preferences.getString("eraserPreset", null), EraserPreset.NORMAL_FREE),
            selectionShape = enumValue(preferences.getString("selectionShape", null), SelectionShape.FREEFORM),
            selectionBrushSize = preferences.getFloat("selectionBrushSize", 42f).coerceIn(4f, 300f),
            selectionThreshold = preferences.getFloat("selectionThreshold", .25f).coerceIn(0f, 1f),
            selectionMode = enumValue(preferences.getString("selectionMode", null), SelectionMode.REPLACE),
            selectionFilter = enumValue(preferences.getString("selectionFilter", null), SelectionFilter.ALL),
            correctLineMode = enumValue(preferences.getString("correctLineMode", null), CorrectLineMode.SMOOTH),
            correctLineAmount = preferences.getFloat("correctLineAmount", .55f).coerceIn(0f, 1f),
            nonDestructiveEraser = preferences.getBoolean("nonDestructiveEraser", false),
            shortcutBindings = ShortcutBindingCodec.decode(preferences.getString("shortcutBindings", null)),
            shortcutAxisBindings = ToolboxMotionBindingCodec.decodeAxes(preferences.getString("shortcutAxisBindings", null)),
            shortcutButtonBindings = ToolboxMotionBindingCodec.decodeButtons(preferences.getString("shortcutButtonBindings", null)),
            shortcutBrushGroupTargets = ShortcutBrushGroupTargets(
                pen = preferences.getString("shortcutPenGroup", "").orEmpty(),
                pencil = preferences.getString("shortcutPencilGroup", "").orEmpty(),
                brush = preferences.getString("shortcutBrushGroup", "").orEmpty(),
            ).normalized(),
            toolboxQuickSlots = ToolboxQuickSlotCodec.decode(preferences.getString("toolboxQuickSlots", null)),
            workspaceLayouts = WorkspaceLayoutProfilesCodec.decode(preferences.getString("workspaceLayouts", null)),
        )
        val selected = loaded.withWorkspaceFor(orientation)
        if (preferences.getInt(STUDIO_WORKSPACE_VERSION_KEY, 0) < STUDIO_WORKSPACE_VERSION) {
            return save(BuiltInWorkspaces.unified(selected))
        }
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
            .putFloat("fillSensitivity", persisted.fillSensitivity)
            .putBoolean("showPageRail", persisted.showPageRail)
            .putBoolean("showBrushPanel", persisted.showBrushPanel)
            .putBoolean("showLayerPanel", persisted.showLayerPanel)
            .putString("pagePanelDock", persisted.pagePanelDock.name)
            .putString("brushPanelDock", persisted.brushPanelDock.name)
            .putString("layerPanelDock", persisted.layerPanelDock.name)
            .putString("pagePanelMode", persisted.pagePanelMode.name)
            .putString("brushPanelMode", persisted.brushPanelMode.name)
            .putString("layerPanelMode", persisted.layerPanelMode.name)
            .putBoolean("pagePanelLocked", persisted.pagePanelLocked)
            .putBoolean("brushPanelLocked", persisted.brushPanelLocked)
            .putBoolean("layerPanelLocked", persisted.layerPanelLocked)
            .putBoolean("leftDockStacked", persisted.leftDockStacked)
            .putBoolean("rightDockStacked", persisted.rightDockStacked)
            .putBoolean("bottomDockStacked", persisted.bottomDockStacked)
            .putBoolean("freeDockStacked", persisted.freeDockStacked)
            .putString("leftDockActivePanel", persisted.leftDockActivePanel.name)
            .putString("rightDockActivePanel", persisted.rightDockActivePanel.name)
            .putString("bottomDockActivePanel", persisted.bottomDockActivePanel.name)
            .putString("freeDockActivePanel", persisted.freeDockActivePanel.name)
            .putString("panelTabOrder", WorkspacePanelTabs.encodeOrder(WorkspacePanelTabs.normalizeOrder(persisted.panelTabOrder)))
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
            .putBoolean("singleFingerDoubleTapUndo", persisted.singleFingerDoubleTapUndo)
            .putBoolean("twoFingerDoubleTapUndo", persisted.twoFingerDoubleTapUndo)
            .putBoolean("threeFingerTapRedo", persisted.threeFingerTapRedo)
            .putBoolean("twoFingerRotation", persisted.twoFingerRotation)
            .putString("symmetryMode", persisted.symmetryMode.name)
            .putBoolean("rulerEnabled", persisted.rulerEnabled)
            .putFloat("rulerCenterX", persisted.rulerCenterX.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f)
            .putFloat("rulerCenterY", persisted.rulerCenterY.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f)
            .putFloat("rulerAngleDegrees", app.inkloom.document.RulerGuideState.normalizeAngle(persisted.rulerAngleDegrees))
            .putFloat("rulerSnapDegrees", persisted.rulerSnapDegrees.takeIf(Float::isFinite)?.coerceIn(1f, 90f) ?: 15f)
            .putBoolean("gridSnapEnabled", persisted.gridSnapEnabled)
            .putFloat("gridSpacing", persisted.gridSpacing.takeIf(Float::isFinite)?.coerceIn(16f, 400f) ?: 64f)
            .putBoolean("alignmentGuidesEnabled", persisted.alignmentGuidesEnabled)
            .putFloat("verticalGuideX", persisted.verticalGuideX.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f)
            .putFloat("horizontalGuideY", persisted.horizontalGuideY.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f)
            .putString("stylusPrimaryButton", persisted.stylusPrimaryButton.name)
            .putString("stylusSecondaryButton", persisted.stylusSecondaryButton.name)
            .putString("stylusPrimaryTrigger", persisted.stylusPrimaryTrigger.name)
            .putString("stylusSecondaryTrigger", persisted.stylusSecondaryTrigger.name)
            .putFloat("pressureSensitivity", persisted.pressureSensitivity)
            .putFloat("pressureMinimumInput", persisted.pressureMinimumInput)
            .putFloat("pressureMaximumInput", persisted.pressureMaximumInput)
            .putString("pressureResponseCurve", PressureResponseCurve.encode(persisted.pressureResponseCurve))
            .putFloat("stabilization", persisted.stabilization)
            .putFloat("postCorrection", persisted.postCorrection.coerceIn(0f, 1f))
            .putBoolean("postCorrectionAdjustBySpeed", persisted.postCorrectionAdjustBySpeed)
            .putBoolean("postCorrectionAdjustByScale", persisted.postCorrectionAdjustByScale)
            .putBoolean("showInputDiagnostics", persisted.showInputDiagnostics)
            .putFloat("penSize", persisted.penSize)
            .putFloat("penOpacity", persisted.penOpacity)
            .putFloat("highlighterSize", persisted.highlighterSize)
            .putFloat("highlighterOpacity", persisted.highlighterOpacity)
            .putFloat("eraserSize", persisted.eraserSize)
            .putString("eraserPreset", persisted.eraserPreset.name)
            .putString("selectionShape", persisted.selectionShape.name)
            .putFloat("selectionBrushSize", persisted.selectionBrushSize.coerceIn(4f, 300f))
            .putFloat("selectionThreshold", persisted.selectionThreshold.coerceIn(0f, 1f))
            .putString("selectionMode", persisted.selectionMode.name)
            .putString("selectionFilter", persisted.selectionFilter.name)
            .putString("correctLineMode", persisted.correctLineMode.name)
            .putFloat("correctLineAmount", persisted.correctLineAmount.coerceIn(0f, 1f))
            .putBoolean("nonDestructiveEraser", persisted.nonDestructiveEraser)
            .putString("shortcutBindings", ShortcutBindingCodec.encode(persisted.shortcutBindings))
            .putString("shortcutAxisBindings", ToolboxMotionBindingCodec.encodeAxes(persisted.shortcutAxisBindings))
            .putString("shortcutButtonBindings", ToolboxMotionBindingCodec.encodeButtons(persisted.shortcutButtonBindings))
            .putString("shortcutPenGroup", persisted.shortcutBrushGroupTargets.pen)
            .putString("shortcutPencilGroup", persisted.shortcutBrushGroupTargets.pencil)
            .putString("shortcutBrushGroup", persisted.shortcutBrushGroupTargets.brush)
            .putString("toolboxQuickSlots", ToolboxQuickSlotCodec.encode(persisted.toolboxQuickSlots))
            .putString("workspaceLayouts", WorkspaceLayoutProfilesCodec.encode(persisted.workspaceLayouts))
            .putInt(STUDIO_WORKSPACE_VERSION_KEY, STUDIO_WORKSPACE_VERSION)
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
