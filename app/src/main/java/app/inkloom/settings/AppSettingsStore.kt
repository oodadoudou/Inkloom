package app.inkloom.settings

import android.content.Context
import androidx.core.content.edit
import app.inkloom.model.SymmetryMode
import app.inkloom.document.CorrectLineMode
import app.inkloom.document.FillSubTool
import app.inkloom.document.PressureResponseCurve
import app.inkloom.document.PerspectiveGuideMode
import app.inkloom.document.SpecialRulerMode
import app.inkloom.document.GradientColorStop
import app.inkloom.document.GradientEdgeMode
import app.inkloom.document.GradientKind
import app.inkloom.document.GradientStopCodec
import app.inkloom.model.EditorTool

data class AppSettings(
    val language: UiLanguage = UiLanguage.ENGLISH,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val fingerDrawing: Boolean = false,
    val drawAndHoldEnabled: Boolean = true,
    val drawAndHoldDelayMillis: Long = 650L,
    val fillSensitivity: Float = .25f,
    val fillCloseGapPixels: Float = 0f,
    val fillAreaScalingPixels: Float = 0f,
    val fillSubTool: FillSubTool = FillSubTool.BUCKET,
    val gradientKind: GradientKind = GradientKind.LINEAR,
    val gradientEdgeMode: GradientEdgeMode = GradientEdgeMode.CLAMP,
    val gradientReverse: Boolean = false,
    val gradientDither: Boolean = true,
    val gradientEllipseRatio: Float = .6f,
    val gradientAngleSnapEnabled: Boolean = false,
    val gradientAngleSnapDegrees: Float = 45f,
    val gradientStops: List<GradientColorStop> = listOf(GradientColorStop(0f, 0xFF111111L), GradientColorStop(1f, 0xFFFFFFFFL)),
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
    val pagePanelXDp: Float = 88f,
    val pagePanelYDp: Float = 18f,
    val pagePanelWidthDp: Float = 176f,
    val pagePanelHeightDp: Float = 620f,
    val brushPanelXDp: Float = 1180f,
    val brushPanelYDp: Float = 18f,
    val brushPanelWidthDp: Float = 340f,
    val brushCategoryWidthDp: Float = 120f,
    val brushGridLayout: Boolean = false,
    val brushPanelHeightDp: Float = 620f,
    val layerPanelXDp: Float = 1440f,
    val layerPanelYDp: Float = 18f,
    val layerPanelWidthDp: Float = 270f,
    val layerPanelHeightDp: Float = 620f,
    val showToolbar: Boolean = true,
    val toolbarOffsetXDp: Float = 16f,
    val toolbarOffsetYDp: Float = 16f,
    val verticalToolbar: Boolean = true,
    val toolbarDock: ToolbarDock = ToolbarDock.LEFT,
    val singleFingerDoubleTapUndo: Boolean = true,
    val twoFingerDoubleTapUndo: Boolean = true,
    val threeFingerTapRedo: Boolean = true,
    val twoFingerRotation: Boolean = true,
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
    val perspectiveGuideMode: PerspectiveGuideMode = PerspectiveGuideMode.NONE,
    val perspectiveGuideAssisted: Boolean = true,
    val perspectiveGuideEditing: Boolean = false,
    val perspectivePoint1X: Float = .5f,
    val perspectivePoint1Y: Float = .42f,
    val perspectivePoint2X: Float = .88f,
    val perspectivePoint2Y: Float = .42f,
    val perspectivePoint3X: Float = .5f,
    val perspectivePoint3Y: Float = .12f,
    val specialRulerMode: SpecialRulerMode = SpecialRulerMode.NONE,
    val specialRulerAssisted: Boolean = true,
    val specialRulerEditing: Boolean = false,
    val specialRulerCenterX: Float = .5f,
    val specialRulerCenterY: Float = .5f,
    val specialRulerAngleDegrees: Float = 0f,
    val specialRulerSpacing: Float = 64f,
    val stylusPrimaryButton: StylusButtonAction = StylusButtonAction.ERASER,
    val stylusSecondaryButton: StylusButtonAction = StylusButtonAction.LASSO,
    val stylusPrimaryTrigger: ShortcutTrigger = ShortcutTrigger.HOLD,
    val stylusSecondaryTrigger: ShortcutTrigger = ShortcutTrigger.HOLD,
    val globalMotionFiltering: Float = 0f,
    val globalMotionExpression: Float = 0f,
    val globalStrokeStabilization: Float = 0f,
    val pressureSensitivity: Float = 1f,
    val pressureMinimumInput: Float = 0f,
    val pressureMaximumInput: Float = 1f,
    val pressureResponseCurve: List<Float> = PressureResponseCurve.LINEAR,
    val showInputDiagnostics: Boolean = false,
    val showStrokeTraceOverlay: Boolean = false,
    val penSize: Float = 5f,
    val penOpacity: Float = 1f,
    /** Durable drawing selection. Temporary stylus-button/tool overrides never mutate these. */
    val persistentDrawingTool: EditorTool = EditorTool.PEN,
    val persistentBrushPresetId: String = "system-g-pen",
    val highlighterSize: Float = 28f,
    val highlighterOpacity: Float = .35f,
    val eraserSize: Float = 24f,
    val eraserPreset: EraserPreset = EraserPreset.NORMAL_FREE,
    val selectionShape: SelectionShape = SelectionShape.FREEFORM,
    val selectionBrushSize: Float = 42f,
    val selectionThreshold: Float = .25f,
    val selectionMaskVisibility: Float = .25f,
    val selectionInterpolation: app.inkloom.engine.v5.tools.SelectionInterpolation = app.inkloom.engine.v5.tools.SelectionInterpolation.BILINEAR,
    val selectionMode: SelectionMode = SelectionMode.REPLACE,
    val selectionFilter: SelectionFilter = SelectionFilter.ALL,
    val referenceTarget: ReferenceTarget = ReferenceTarget.ALL_VISIBLE,
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

enum class LibraryLayout { GRID, LIST, COMPACT_GRID }

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
enum class ReferenceTarget { ALL_VISIBLE, ACTIVE_LAYER, REFERENCE_LAYERS }

class AppSettingsStore(context: Context) {
    private companion object {
        const val STUDIO_WORKSPACE_VERSION_KEY = "studioWorkspaceVersion"
        const val STUDIO_WORKSPACE_VERSION = 2
        const val INPUT_GESTURES_VERSION_KEY = "inputGesturesVersion"
        const val INPUT_GESTURES_VERSION = 3
    }

    private val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
    private val systemLanguage = UiLanguage.fromLanguageTag(context.resources.configuration.locales[0]?.toLanguageTag())
    private val orientation = WorkspaceOrientation.from(context.resources.configuration)

    fun load(): AppSettings {
        // Upgrade former defaults once. After this, every resize (even back to an old
        // default width) is retained; opening settings must never reset a custom size.
        if (!preferences.getBoolean("compactPanelDefaultsApplied", false)) {
            preferences.edit {
                for ((key, previous, compact) in listOf(
                    Triple("pagePanelWidthDp", setOf(224f, 300f), 176f),
                    Triple("brushPanelWidthDp", setOf(620f), 340f),
                    Triple("layerPanelWidthDp", setOf(420f), 270f),
                )) {
                    if (!preferences.contains(key) || preferences.getFloat(key, compact) in previous) putFloat(key, compact)
                }
                putBoolean("compactPanelDefaultsApplied", true)
            }
        }
        val savedInputVersion = preferences.getInt(INPUT_GESTURES_VERSION_KEY, 0)
        val migrateCanvasRotation = savedInputVersion < 2
        val pressureMinimum = preferences.getFloat("pressureMinimumInput", 0f).coerceIn(0f, .95f)
        val pressureMaximum = preferences.getFloat("pressureMaximumInput", 1f).coerceIn(pressureMinimum + .05f, 1f)
        val loaded = AppSettings(
            language = enumValue(preferences.getString("language", null), systemLanguage),
            libraryLayout = enumValue(preferences.getString("libraryLayout", null), LibraryLayout.GRID),
            fingerDrawing = preferences.getBoolean("fingerDrawing", false),
            drawAndHoldEnabled = preferences.getBoolean("drawAndHoldEnabled", true),
            drawAndHoldDelayMillis = preferences.getLong("drawAndHoldDelayMillis", 650L).coerceIn(300L, 1500L),
            fillSensitivity = preferences.getFloat("fillSensitivity", .25f),
            fillCloseGapPixels = preferences.getFloat("fillCloseGapPixels", 0f).coerceIn(0f, 64f),
            fillAreaScalingPixels = preferences.getFloat("fillAreaScalingPixels", 0f).coerceIn(-64f, 64f),
            fillSubTool = enumValue(preferences.getString("fillSubTool", null), FillSubTool.BUCKET),
            gradientKind = enumValue(preferences.getString("gradientKind", null), GradientKind.LINEAR),
            gradientEdgeMode = enumValue(preferences.getString("gradientEdgeMode", null), GradientEdgeMode.CLAMP),
            gradientReverse = preferences.getBoolean("gradientReverse", false),
            gradientDither = preferences.getBoolean("gradientDither", true),
            gradientEllipseRatio = preferences.getFloat("gradientEllipseRatio", .6f).takeIf(Float::isFinite)?.coerceIn(.1f, 1f) ?: .6f,
            gradientAngleSnapEnabled = preferences.getBoolean("gradientAngleSnapEnabled", false),
            gradientAngleSnapDegrees = preferences.getFloat("gradientAngleSnapDegrees", 45f).takeIf(Float::isFinite)?.coerceIn(1f, 90f) ?: 45f,
            gradientStops = GradientStopCodec.decode(preferences.getString("gradientStops", null)),
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
            pagePanelXDp = preferences.getFloat("pagePanelXDp", 88f).let { saved ->
                // The former 18dp default covered Procreate-style size/opacity handles.
                if (saved == 18f) 88f else saved
            }.coerceIn(-2400f, 2400f),
            pagePanelYDp = preferences.getFloat("pagePanelYDp", 18f).coerceIn(-2400f, 2400f),
            pagePanelWidthDp = preferences.getFloat("pagePanelWidthDp", 176f).coerceIn(80f, 1200f),
            pagePanelHeightDp = preferences.getFloat("pagePanelHeightDp", 620f).coerceIn(120f, 1600f),
            brushPanelXDp = preferences.getFloat("brushPanelXDp", 1180f).coerceIn(-2400f, 2400f),
            brushPanelYDp = preferences.getFloat("brushPanelYDp", 18f).coerceIn(-2400f, 2400f),
            brushGridLayout = preferences.getBoolean("brushGridLayout", false),
            brushPanelWidthDp = preferences.getFloat("brushPanelWidthDp", 340f).coerceIn(80f, 1200f),
            brushCategoryWidthDp = preferences.getFloat("brushCategoryWidthDp",120f).takeIf(Float::isFinite)?.coerceIn(80f,360f) ?: 120f,
            brushPanelHeightDp = preferences.getFloat("brushPanelHeightDp", 620f).coerceIn(120f, 1600f),
            layerPanelXDp = preferences.getFloat("layerPanelXDp", 1440f).coerceIn(-2400f, 2400f),
            layerPanelYDp = preferences.getFloat("layerPanelYDp", 18f).coerceIn(-2400f, 2400f),
            layerPanelWidthDp = preferences.getFloat("layerPanelWidthDp", 270f).coerceIn(80f, 1200f),
            layerPanelHeightDp = preferences.getFloat("layerPanelHeightDp", 620f).coerceIn(120f, 1600f),
            showToolbar = preferences.getBoolean("showToolbar", true),
            toolbarOffsetXDp = preferences.getFloat("toolbarOffsetXDp", 16f).coerceIn(-1200f, 1200f),
            toolbarOffsetYDp = preferences.getFloat("toolbarOffsetYDp", 16f).coerceIn(-1200f, 1200f),
            verticalToolbar = preferences.getBoolean("verticalToolbar", true),
            toolbarDock = enumValue(preferences.getString("toolbarDock", null), ToolbarDock.LEFT),
            singleFingerDoubleTapUndo = preferences.getBoolean("singleFingerDoubleTapUndo", true),
            twoFingerDoubleTapUndo = preferences.getBoolean("twoFingerDoubleTapUndo", true),
            threeFingerTapRedo = preferences.getBoolean("threeFingerTapRedo", true),
            twoFingerRotation = if (migrateCanvasRotation) true else preferences.getBoolean("twoFingerRotation", true),
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
            perspectiveGuideMode = enumValue(preferences.getString("perspectiveGuideMode", null), PerspectiveGuideMode.NONE),
            perspectiveGuideAssisted = preferences.getBoolean("perspectiveGuideAssisted", true),
            perspectiveGuideEditing = preferences.getBoolean("perspectiveGuideEditing", false),
            perspectivePoint1X = preferences.getFloat("perspectivePoint1X", .5f).takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .5f,
            perspectivePoint1Y = preferences.getFloat("perspectivePoint1Y", .42f).takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .42f,
            perspectivePoint2X = preferences.getFloat("perspectivePoint2X", .88f).takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .88f,
            perspectivePoint2Y = preferences.getFloat("perspectivePoint2Y", .42f).takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .42f,
            perspectivePoint3X = preferences.getFloat("perspectivePoint3X", .5f).takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .5f,
            perspectivePoint3Y = preferences.getFloat("perspectivePoint3Y", .12f).takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .12f,
            specialRulerMode = enumValue(preferences.getString("specialRulerMode", null), SpecialRulerMode.NONE),
            specialRulerAssisted = preferences.getBoolean("specialRulerAssisted", true),
            specialRulerEditing = preferences.getBoolean("specialRulerEditing", false),
            specialRulerCenterX = preferences.getFloat("specialRulerCenterX", .5f).takeIf(Float::isFinite)?.coerceIn(-2f, 3f) ?: .5f,
            specialRulerCenterY = preferences.getFloat("specialRulerCenterY", .5f).takeIf(Float::isFinite)?.coerceIn(-2f, 3f) ?: .5f,
            specialRulerAngleDegrees = app.inkloom.document.RulerGuideState.normalizeAngle(preferences.getFloat("specialRulerAngleDegrees", 0f)),
            specialRulerSpacing = preferences.getFloat("specialRulerSpacing", 64f).takeIf(Float::isFinite)?.coerceIn(16f, 400f) ?: 64f,
            stylusPrimaryButton = enumValue(preferences.getString("stylusPrimaryButton", null), StylusButtonAction.ERASER),
            stylusSecondaryButton = enumValue(preferences.getString("stylusSecondaryButton", null), StylusButtonAction.LASSO),
            stylusPrimaryTrigger = enumValue(preferences.getString("stylusPrimaryTrigger", null), ShortcutTrigger.HOLD),
            stylusSecondaryTrigger = enumValue(preferences.getString("stylusSecondaryTrigger", null), ShortcutTrigger.HOLD),
            globalMotionFiltering = preferences.getFloat("globalMotionFiltering", 0f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
            globalMotionExpression = preferences.getFloat("globalMotionExpression", 0f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
            globalStrokeStabilization = preferences.getFloat("globalStrokeStabilization", 0f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
            pressureSensitivity = preferences.getFloat("pressureSensitivity", 1f).coerceIn(.25f, 2.5f),
            pressureMinimumInput = pressureMinimum,
            pressureMaximumInput = pressureMaximum,
            pressureResponseCurve = PressureResponseCurve.decode(preferences.getString("pressureResponseCurve", null)),
            showInputDiagnostics = preferences.getBoolean("showInputDiagnostics", false),
            showStrokeTraceOverlay = preferences.getBoolean("showStrokeTraceOverlay", false),
            penSize = preferences.getFloat("penSize", 5f).coerceIn(.5f, 400f),
            penOpacity = preferences.getFloat("penOpacity", 1f).coerceIn(.05f, 1f),
            persistentDrawingTool = enumValue(
                preferences.getString("persistentDrawingTool", null),
                EditorTool.PEN,
            ),
            persistentBrushPresetId = preferences.getString("persistentBrushPresetId", "system-g-pen")
                ?.trim()?.take(160).orEmpty().ifBlank { "system-g-pen" },
            highlighterSize = preferences.getFloat("highlighterSize", 28f).coerceIn(.5f, 400f),
            highlighterOpacity = preferences.getFloat("highlighterOpacity", .35f).coerceIn(.05f, 1f),
            eraserSize = preferences.getFloat("eraserSize", 24f).coerceIn(.5f, 400f),
            eraserPreset = enumValue(preferences.getString("eraserPreset", null), EraserPreset.NORMAL_FREE),
            selectionShape = enumValue(preferences.getString("selectionShape", null), SelectionShape.FREEFORM),
            selectionBrushSize = preferences.getFloat("selectionBrushSize", 42f).coerceIn(4f, 300f),
            selectionMaskVisibility = preferences.getFloat("selectionMaskVisibility", .25f).coerceIn(0f, 1f),
            selectionThreshold = preferences.getFloat("selectionThreshold", .25f).coerceIn(0f, 1f),
            selectionInterpolation = enumValue(preferences.getString("selectionInterpolation", null), app.inkloom.engine.v5.tools.SelectionInterpolation.BILINEAR),
            selectionMode = enumValue(preferences.getString("selectionMode", null), SelectionMode.REPLACE),
            selectionFilter = enumValue(preferences.getString("selectionFilter", null), SelectionFilter.ALL),
            referenceTarget = enumValue(preferences.getString("referenceTarget", null), ReferenceTarget.ALL_VISIBLE),
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
        if (savedInputVersion < INPUT_GESTURES_VERSION) preferences.edit {
            if (migrateCanvasRotation) putBoolean("twoFingerRotation", true)
            putInt(INPUT_GESTURES_VERSION_KEY, INPUT_GESTURES_VERSION)
        }
        if (listOf("stabilization", "postCorrection", "postCorrectionAdjustBySpeed", "postCorrectionAdjustByScale")
                .any(preferences::contains)
        ) preferences.edit {
            remove("stabilization")
            remove("postCorrection")
            remove("postCorrectionAdjustBySpeed")
            remove("postCorrectionAdjustByScale")
        }
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
            .putString("libraryLayout", persisted.libraryLayout.name)
            .putInt(INPUT_GESTURES_VERSION_KEY, INPUT_GESTURES_VERSION)
            .putBoolean("fingerDrawing", persisted.fingerDrawing)
            .putBoolean("drawAndHoldEnabled", persisted.drawAndHoldEnabled)
            .putLong("drawAndHoldDelayMillis", persisted.drawAndHoldDelayMillis.coerceIn(300L, 1500L))
            .putFloat("fillSensitivity", persisted.fillSensitivity)
            .putFloat("fillCloseGapPixels", persisted.fillCloseGapPixels.coerceIn(0f, 64f))
            .putFloat("fillAreaScalingPixels", persisted.fillAreaScalingPixels.coerceIn(-64f, 64f))
            .putString("fillSubTool", persisted.fillSubTool.name)
            .putString("gradientKind", persisted.gradientKind.name)
            .putString("gradientEdgeMode", persisted.gradientEdgeMode.name)
            .putBoolean("gradientReverse", persisted.gradientReverse)
            .putBoolean("gradientDither", persisted.gradientDither)
            .putFloat("gradientEllipseRatio", persisted.gradientEllipseRatio.takeIf(Float::isFinite)?.coerceIn(.1f, 1f) ?: .6f)
            .putBoolean("gradientAngleSnapEnabled", persisted.gradientAngleSnapEnabled)
            .putFloat("gradientAngleSnapDegrees", persisted.gradientAngleSnapDegrees.takeIf(Float::isFinite)?.coerceIn(1f, 90f) ?: 45f)
            .putString("gradientStops", GradientStopCodec.encode(persisted.gradientStops))
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
            .putBoolean("brushGridLayout", persisted.brushGridLayout)
            .putFloat("brushPanelWidthDp", persisted.brushPanelWidthDp)
            .putFloat("brushCategoryWidthDp", persisted.brushCategoryWidthDp.takeIf(Float::isFinite)?.coerceIn(80f,360f) ?: 120f)
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
            .putString("perspectiveGuideMode", persisted.perspectiveGuideMode.name)
            .putBoolean("perspectiveGuideAssisted", persisted.perspectiveGuideAssisted)
            .putBoolean("perspectiveGuideEditing", persisted.perspectiveGuideEditing)
            .putFloat("perspectivePoint1X", persisted.perspectivePoint1X.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .5f)
            .putFloat("perspectivePoint1Y", persisted.perspectivePoint1Y.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .42f)
            .putFloat("perspectivePoint2X", persisted.perspectivePoint2X.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .88f)
            .putFloat("perspectivePoint2Y", persisted.perspectivePoint2Y.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .42f)
            .putFloat("perspectivePoint3X", persisted.perspectivePoint3X.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .5f)
            .putFloat("perspectivePoint3Y", persisted.perspectivePoint3Y.takeIf(Float::isFinite)?.coerceIn(-4f, 5f) ?: .12f)
            .putString("specialRulerMode", persisted.specialRulerMode.name)
            .putBoolean("specialRulerAssisted", persisted.specialRulerAssisted)
            .putBoolean("specialRulerEditing", persisted.specialRulerEditing)
            .putFloat("specialRulerCenterX", persisted.specialRulerCenterX.takeIf(Float::isFinite)?.coerceIn(-2f, 3f) ?: .5f)
            .putFloat("specialRulerCenterY", persisted.specialRulerCenterY.takeIf(Float::isFinite)?.coerceIn(-2f, 3f) ?: .5f)
            .putFloat("specialRulerAngleDegrees", app.inkloom.document.RulerGuideState.normalizeAngle(persisted.specialRulerAngleDegrees))
            .putFloat("specialRulerSpacing", persisted.specialRulerSpacing.takeIf(Float::isFinite)?.coerceIn(16f, 400f) ?: 64f)
            .putString("stylusPrimaryButton", persisted.stylusPrimaryButton.name)
            .putString("stylusSecondaryButton", persisted.stylusSecondaryButton.name)
            .putString("stylusPrimaryTrigger", persisted.stylusPrimaryTrigger.name)
            .putString("stylusSecondaryTrigger", persisted.stylusSecondaryTrigger.name)
            .putFloat("globalMotionFiltering", persisted.globalMotionFiltering)
            .putFloat("globalMotionExpression", persisted.globalMotionExpression)
            .putFloat("globalStrokeStabilization", persisted.globalStrokeStabilization.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f)
            .putFloat("pressureSensitivity", persisted.pressureSensitivity)
            .putFloat("pressureMinimumInput", persisted.pressureMinimumInput)
            .putFloat("pressureMaximumInput", persisted.pressureMaximumInput)
            .putString("pressureResponseCurve", PressureResponseCurve.encode(persisted.pressureResponseCurve))
            .putBoolean("showInputDiagnostics", persisted.showInputDiagnostics)
            .putBoolean("showStrokeTraceOverlay", persisted.showStrokeTraceOverlay)
            .putFloat("penSize", persisted.penSize)
            .putFloat("penOpacity", persisted.penOpacity)
            .putString("persistentDrawingTool", persisted.persistentDrawingTool.name)
            .putString("persistentBrushPresetId", persisted.persistentBrushPresetId.trim().take(160))
            .putFloat("highlighterSize", persisted.highlighterSize)
            .putFloat("highlighterOpacity", persisted.highlighterOpacity)
            .putFloat("eraserSize", persisted.eraserSize)
            .putString("eraserPreset", persisted.eraserPreset.name)
            .putString("selectionShape", persisted.selectionShape.name)
            .putFloat("selectionBrushSize", persisted.selectionBrushSize.coerceIn(4f, 300f))
            .putFloat("selectionMaskVisibility", persisted.selectionMaskVisibility.coerceIn(0f, 1f))
            .putFloat("selectionThreshold", persisted.selectionThreshold.coerceIn(0f, 1f))
            .putString("selectionInterpolation", persisted.selectionInterpolation.name)
            .putString("selectionMode", persisted.selectionMode.name)
            .putString("selectionFilter", persisted.selectionFilter.name)
            .putString("referenceTarget", persisted.referenceTarget.name)
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
