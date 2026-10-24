package app.inkloom.settings

import android.content.Context
import androidx.core.content.edit
import app.inkloom.model.SymmetryMode
import app.inkloom.document.CorrectLineMode
import app.inkloom.document.RulerToolMode
import app.inkloom.document.FillSubTool
import app.inkloom.document.PressureResponseCurve
import app.inkloom.document.PerspectiveGuideMode
import app.inkloom.document.SpecialRulerMode
import app.inkloom.document.GradientColorStop
import app.inkloom.document.GradientEdgeMode
import app.inkloom.document.GradientKind
import app.inkloom.document.GradientStopCodec
import app.inkloom.model.EditorTool

enum class LayerRowDensity { COMPACT, COMFORTABLE, LARGE }

data class AppSettings(
    val language: UiLanguage = UiLanguage.ENGLISH,
    val uiGrayTheme: UiGrayTheme = UiGrayTheme.DEFAULT,
    /** [InterfaceScale.AUTO] measures the screen; otherwise the interface size, 0.6–1.2. */
    val interfaceScale: Float = InterfaceScale.AUTO,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val fingerDrawing: Boolean = false,
    val drawAndHoldEnabled: Boolean = true,
    val drawAndHoldDelayMillis: Long = 650L,
    val fillSensitivity: Float = .25f,
    val fillCloseGapPixels: Float = 0f,
    val fillAreaScalingPixels: Float = 0f,
    val fillTargetMode: FillTargetMode = FillTargetMode.TOUCHED_COLOR,
    val fillTargetColor: Int = android.graphics.Color.WHITE,
    val fillExpansionShape: FillExpansionShape = FillExpansionShape.ROUND,
    val fillAntiAlias: Boolean = true,
    val fillSubTool: FillSubTool = FillSubTool.BUCKET,
    val gradientKind: GradientKind = GradientKind.LINEAR,
    val gradientEdgeMode: GradientEdgeMode = GradientEdgeMode.CLAMP,
    val gradientReverse: Boolean = false,
    val gradientDither: Boolean = true,
    val gradientEllipseRatio: Float = .6f,
    val gradientAngleSnapEnabled: Boolean = false,
    val gradientAngleSnapDegrees: Float = 45f,
    val gradientStops: List<GradientColorStop> = listOf(GradientColorStop(0f, 0xFF111111L), GradientColorStop(1f, 0xFFFFFFFFL)),
    val gradientPresets: GradientPresetLibrary = GradientPresetLibrary(),
    val liquifyStrengthScale: Float = 1f,
    val liquifyDistortion: Float = 0f,
    val liquifyMomentum: Float = 0f,
    /** Copy stamp: keep the source offset between strokes. */
    val cloneAligned: Boolean = true,
    val brushCategoryWidthDp: Float = 120f,
    val brushGridLayout: Boolean = false,
    val layerPanelDualList: Boolean = false,
    val layerPanelSplitFraction: Float = .5f,
    val layerRowDensity: LayerRowDensity = LayerRowDensity.COMFORTABLE,
    val layerShowThumbnails: Boolean = true,
    /** CSP's vector eraser: remove whole lines instead of cutting away the touched part. */
    val vectorEraseWholeLine: Boolean = false,
    val layerShowDetails: Boolean = true,
    val layerShowProperties: Boolean = true,
    val layerShowCommands: Boolean = true,
    val layerCommandsAtBottom: Boolean = false,
    val showToolbar: Boolean = true,
    val toolbarOffsetXDp: Float = 16f,
    val toolbarOffsetYDp: Float = 16f,
    val selectionStudioXDp: Float = -1f,
    val selectionStudioYDp: Float = -1f,
    val selectionLauncherVisible: Boolean = true,
    val selectionLauncherOrder: String = SelectionLauncherLayout.encode(SelectionLauncherLayout.default),
    val verticalToolbar: Boolean = true,
    val toolbarDock: ToolbarDock = ToolbarDock.LEFT,
    val toolPaletteLayout: String = ToolPaletteLayout.encode(ToolPaletteLayout.defaultEntries),
    val singleFingerDoubleTapUndo: Boolean = true,
    val twoFingerDoubleTapUndo: Boolean = true,
    val threeFingerTapRedo: Boolean = true,
    val twoFingerRotation: Boolean = true,
    /** One-finger horizontal flick at fit zoom turns the page when the finger is not drawing. */
    val swipeToTurnPages: Boolean = true,
    /** Pages stacked in one vertical strip instead of one page at a time. */
    val continuousPages: Boolean = false,
    val symmetryMode: SymmetryMode = SymmetryMode.NONE,
    val symmetryCenterX: Float = .5f,
    val symmetryCenterY: Float = .5f,
    val symmetryAngleDegrees: Float = 0f,
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
    val excludeDraftLayersFromReference: Boolean = true,
    /** CSP "Exclude locked layers" for Fill/automatic selection references; off by default. */
    val excludeLockedLayersFromReference: Boolean = false,
    /** CSP "Exclude editing layer": refer to other layers while filling the current one. */
    val excludeActiveLayerFromReference: Boolean = false,
    val excludePaperFromReference: Boolean = false,
    val excludeImportedBackgroundFromReference: Boolean = false,
    val excludeTextFromReference: Boolean = false,
    val eyedropperReferenceTarget: ReferenceTarget = ReferenceTarget.ALL_VISIBLE,
    val eyedropperIncludePaper: Boolean = true,
    val correctLineMode: CorrectLineMode = CorrectLineMode.SMOOTH,
    val correctLineAmount: Float = .55f,
    val rulerToolMode: RulerToolMode = RulerToolMode.STRAIGHT,
    val rulerPolygonSides: Int = 6,
    /** CSP's Snap to ruler: strokes starting near a ruler follow it. */
    val snapToRulers: Boolean = true,
    /** CSP's Align/Distribute: text by its drawn pixels (off: its frame), vector layers by their paths (off: their pixels). */
    val alignToTextPixels: Boolean = true,
    val alignToVectorPaths: Boolean = true,
    val frameToolMode: app.inkloom.document.FrameToolMode = app.inkloom.document.FrameToolMode.DIVIDE,
    val frameDivision: app.inkloom.document.FrameDivision = app.inkloom.document.FrameDivision.BORDER_ONLY,
    /** Gutters between divided frames, in page units: left–right and top–bottom. */
    val frameHorizontalGutter: Float = 40f,
    val frameVerticalGutter: Float = 60f,
    val nonDestructiveEraser: Boolean = false,
    val shortcutBindings: List<ShortcutBinding> = CspShortcutResolver.defaultBindings,
    val shortcutAxisBindings: List<AxisShortcutBinding> = emptyList(),
    val shortcutButtonBindings: List<ButtonShortcutBinding> = emptyList(),
    val shortcutBrushGroupTargets: ShortcutBrushGroupTargets = ShortcutBrushGroupTargets(),
    val toolboxQuickSlots: List<ToolboxQuickSlot> = ToolboxQuickSlots.normalize(emptyList()),
    val rasterFilterPresets: List<RasterFilterPreset> = emptyList(),
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
enum class SelectionShape { FREEFORM, RECTANGLE, ELLIPSE, BRUSH, SMART_BORDER, MAGIC_WAND }
enum class SelectionMode { REPLACE, ADD, SUBTRACT, INTERSECT }
enum class SelectionFilter { ALL, ACTIVE_LAYER, CURRENT_BRUSH, CURRENT_COLOR }
enum class ReferenceTarget { ALL_VISIBLE, ACTIVE_LAYER, REFERENCE_LAYERS }
enum class FillTargetMode { TOUCHED_COLOR, TRANSPARENT_ONLY, SPECIFIED_COLOR }
enum class FillExpansionShape { ROUND, RECTANGLE }

class AppSettingsStore(context: Context) {
    private companion object {
        const val INPUT_GESTURES_VERSION_KEY = "inputGesturesVersion"
        const val INPUT_GESTURES_VERSION = 3
    }

    private val preferences = context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE)
    private val systemLanguage = UiLanguage.fromLanguageTag(context.resources.configuration.locales[0]?.toLanguageTag())
    private val workspace = EditorWorkspaceStore(context)
    private var lastToolRail: ToolRailLayout? = null

    fun load(): AppSettings {
        if (preferences.getInt("shortcutDefaultsMigrationVersion",0) < 1) {
            val bindings=ShortcutBindingCodec.decode(preferences.getString("shortcutBindings",null))
            check(preferences.edit().putString("shortcutBindings",ShortcutBindingCodec.encode(LegacyShortcutImport.migrateLegacyDefaults(bindings)))
                .putInt("shortcutDefaultsMigrationVersion",1).commit()) { "Could not migrate shortcuts" }
        }
        val savedInputVersion = preferences.getInt(INPUT_GESTURES_VERSION_KEY, 0)
        val migrateCanvasRotation = savedInputVersion < 2
        val pressureMinimum = preferences.getFloat("pressureMinimumInput", 0f).coerceIn(0f, .95f)
        val pressureMaximum = preferences.getFloat("pressureMaximumInput", 1f).coerceIn(pressureMinimum + .05f, 1f)
        val loaded = AppSettings(
            language = enumValue(preferences.getString("language", null), systemLanguage),
            uiGrayTheme = UiGrayTheme.decode(preferences.getString("uiGrayTheme", null)),
            interfaceScale = InterfaceScale.stored(preferences.getFloat(InterfaceScale.PREFERENCE_KEY, InterfaceScale.AUTO)),
            libraryLayout = enumValue(preferences.getString("libraryLayout", null), LibraryLayout.GRID),
            fingerDrawing = preferences.getBoolean("fingerDrawing", false),
            drawAndHoldEnabled = preferences.getBoolean("drawAndHoldEnabled", true),
            drawAndHoldDelayMillis = preferences.getLong("drawAndHoldDelayMillis", 650L).coerceIn(300L, 1500L),
            fillSensitivity = preferences.getFloat("fillSensitivity", .25f),
            fillCloseGapPixels = preferences.getFloat("fillCloseGapPixels", 0f).coerceIn(0f, 64f),
            fillAreaScalingPixels = preferences.getFloat("fillAreaScalingPixels", 0f).coerceIn(-64f, 64f),
            fillTargetMode = enumValue(preferences.getString("fillTargetMode", null), FillTargetMode.TOUCHED_COLOR),
            fillTargetColor = preferences.getInt("fillTargetColor", android.graphics.Color.WHITE),
            fillExpansionShape = enumValue(preferences.getString("fillExpansionShape", null), FillExpansionShape.ROUND),
            fillAntiAlias = preferences.getBoolean("fillAntiAlias", true),
            fillSubTool = enumValue(preferences.getString("fillSubTool", null), FillSubTool.BUCKET),
            gradientKind = enumValue(preferences.getString("gradientKind", null), GradientKind.LINEAR),
            gradientEdgeMode = enumValue(preferences.getString("gradientEdgeMode", null), GradientEdgeMode.CLAMP),
            gradientReverse = preferences.getBoolean("gradientReverse", false),
            gradientDither = preferences.getBoolean("gradientDither", true),
            gradientEllipseRatio = preferences.getFloat("gradientEllipseRatio", .6f).takeIf(Float::isFinite)?.coerceIn(.1f, 1f) ?: .6f,
            gradientAngleSnapEnabled = preferences.getBoolean("gradientAngleSnapEnabled", false),
            gradientAngleSnapDegrees = preferences.getFloat("gradientAngleSnapDegrees", 45f).takeIf(Float::isFinite)?.coerceIn(1f, 90f) ?: 45f,
            gradientStops = GradientStopCodec.decode(preferences.getString("gradientStops", null)),
            gradientPresets = preferences.getString("gradientPresets", null)?.let { encoded ->
                GradientPresetLibraryCodec.decode(encoded.encodeToByteArray()).getOrDefault(GradientPresetLibrary())
            } ?: GradientPresetLibrary(),
            liquifyStrengthScale = preferences.getFloat("liquifyStrengthScale", 1f).takeIf(Float::isFinite)?.coerceIn(0f, 2f) ?: 1f,
            liquifyDistortion = preferences.getFloat("liquifyDistortion", 0f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
            liquifyMomentum = preferences.getFloat("liquifyMomentum", 0f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
            cloneAligned = preferences.getBoolean("cloneAligned", true),
            brushGridLayout = preferences.getBoolean("brushGridLayout", false),
            brushCategoryWidthDp = preferences.getFloat("brushCategoryWidthDp",120f).takeIf(Float::isFinite)?.coerceIn(56f,360f) ?: 120f,
            layerPanelDualList = preferences.getBoolean("layerPanelDualList", false),
            layerPanelSplitFraction = preferences.getFloat("layerPanelSplitFraction", .5f).takeIf(Float::isFinite)?.coerceIn(.2f, .8f) ?: .5f,
            layerRowDensity = enumValue(preferences.getString("layerRowDensity", null), LayerRowDensity.COMFORTABLE),
            layerShowThumbnails = preferences.getBoolean("layerShowThumbnails", true),
            vectorEraseWholeLine = preferences.getBoolean("vectorEraseWholeLine", false),
            layerShowDetails = preferences.getBoolean("layerShowDetails", true),
            layerShowProperties = preferences.getBoolean("layerShowProperties", true),
            layerShowCommands = preferences.getBoolean("layerShowCommands", true),
            layerCommandsAtBottom = preferences.getBoolean("layerCommandsAtBottom", false),
            selectionStudioXDp = preferences.getFloat("selectionStudioXDp", -1f).takeIf(Float::isFinite)?.coerceIn(-1f, 2400f) ?: -1f,
            selectionStudioYDp = preferences.getFloat("selectionStudioYDp", -1f).takeIf(Float::isFinite)?.coerceIn(-1f, 2400f) ?: -1f,
            selectionLauncherVisible = preferences.getBoolean("selectionLauncherVisible", true),
            selectionLauncherOrder = SelectionLauncherLayout.encode(SelectionLauncherLayout.decode(preferences.getString("selectionLauncherOrder", null))),
            singleFingerDoubleTapUndo = preferences.getBoolean("singleFingerDoubleTapUndo", true),
            twoFingerDoubleTapUndo = preferences.getBoolean("twoFingerDoubleTapUndo", true),
            threeFingerTapRedo = preferences.getBoolean("threeFingerTapRedo", true),
            swipeToTurnPages = preferences.getBoolean("swipeToTurnPages", true),
            continuousPages = preferences.getBoolean("continuousPages", false),
            twoFingerRotation = if (migrateCanvasRotation) true else preferences.getBoolean("twoFingerRotation", true),
            symmetryMode = enumValue(preferences.getString("symmetryMode", null), SymmetryMode.NONE),
            symmetryCenterX = preferences.getFloat("symmetryCenterX", .5f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
            symmetryCenterY = preferences.getFloat("symmetryCenterY", .5f).takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f,
            symmetryAngleDegrees = app.inkloom.document.SymmetryGuideState.normalizeAngle(preferences.getFloat("symmetryAngleDegrees", 0f)),
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
            excludeDraftLayersFromReference = preferences.getBoolean("excludeDraftLayersFromReference", true),
            excludeLockedLayersFromReference = preferences.getBoolean("excludeLockedLayersFromReference", false),
            excludeActiveLayerFromReference = preferences.getBoolean("excludeActiveLayerFromReference", false),
            excludePaperFromReference = preferences.getBoolean("excludePaperFromReference", false),
            excludeImportedBackgroundFromReference = preferences.getBoolean("excludeImportedBackgroundFromReference", false),
            excludeTextFromReference = preferences.getBoolean("excludeTextFromReference", false),
            referenceTarget = enumValue(preferences.getString("referenceTarget", null), ReferenceTarget.ALL_VISIBLE),
            eyedropperReferenceTarget = enumValue(preferences.getString("eyedropperReferenceTarget", null), ReferenceTarget.ALL_VISIBLE),
            eyedropperIncludePaper = preferences.getBoolean("eyedropperIncludePaper", true),
            correctLineMode = enumValue(preferences.getString("correctLineMode", null), CorrectLineMode.SMOOTH),
            correctLineAmount = preferences.getFloat("correctLineAmount", .55f).coerceIn(0f, 1f),
            rulerToolMode = enumValue(preferences.getString("rulerToolMode", null), RulerToolMode.STRAIGHT),
            rulerPolygonSides = preferences.getInt("rulerPolygonSides", 6).coerceIn(3, 32),
            snapToRulers = preferences.getBoolean("snapToRulers", true),
            alignToTextPixels = preferences.getBoolean("alignToTextPixels", true),
            alignToVectorPaths = preferences.getBoolean("alignToVectorPaths", true),
            frameToolMode = enumValue(preferences.getString("frameToolMode", null), app.inkloom.document.FrameToolMode.DIVIDE),
            frameDivision = enumValue(preferences.getString("frameDivision", null), app.inkloom.document.FrameDivision.BORDER_ONLY),
            frameHorizontalGutter = preferences.getFloat("frameHorizontalGutter", 40f).coerceIn(0f, 500f),
            frameVerticalGutter = preferences.getFloat("frameVerticalGutter", 60f).coerceIn(0f, 500f),
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
            rasterFilterPresets = RasterFilterPresetsCodec.decode(preferences.getString("rasterFilterPresets",null)),
        )
        val selected = workspace.load().toolRail?.applyTo(loaded) ?: loaded
        lastToolRail = ToolRailLayout.capture(selected)
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
        return selected
    }

    fun save(value: AppSettings): AppSettings {
        val persisted = value
        val rail = ToolRailLayout.capture(value)
        if (rail != lastToolRail) { workspace.updateToolRail(rail); lastToolRail = rail }
        preferences.edit()
            .putString("language", persisted.language.name)
            .putString("uiGrayTheme", persisted.uiGrayTheme.name)
            .putFloat(InterfaceScale.PREFERENCE_KEY, InterfaceScale.stored(persisted.interfaceScale))
            .putString("libraryLayout", persisted.libraryLayout.name)
            .putInt(INPUT_GESTURES_VERSION_KEY, INPUT_GESTURES_VERSION)
            .putInt("shortcutDefaultsMigrationVersion", 1)
            .putBoolean("fingerDrawing", persisted.fingerDrawing)
            .putBoolean("drawAndHoldEnabled", persisted.drawAndHoldEnabled)
            .putLong("drawAndHoldDelayMillis", persisted.drawAndHoldDelayMillis.coerceIn(300L, 1500L))
            .putFloat("fillSensitivity", persisted.fillSensitivity)
            .putFloat("fillCloseGapPixels", persisted.fillCloseGapPixels.coerceIn(0f, 64f))
            .putFloat("fillAreaScalingPixels", persisted.fillAreaScalingPixels.coerceIn(-64f, 64f))
            .putString("fillTargetMode", persisted.fillTargetMode.name)
            .putInt("fillTargetColor", persisted.fillTargetColor)
            .putString("fillExpansionShape", persisted.fillExpansionShape.name)
            .putBoolean("fillAntiAlias", persisted.fillAntiAlias)
            .putString("fillSubTool", persisted.fillSubTool.name)
            .putString("gradientKind", persisted.gradientKind.name)
            .putString("gradientEdgeMode", persisted.gradientEdgeMode.name)
            .putBoolean("gradientReverse", persisted.gradientReverse)
            .putBoolean("gradientDither", persisted.gradientDither)
            .putFloat("gradientEllipseRatio", persisted.gradientEllipseRatio.takeIf(Float::isFinite)?.coerceIn(.1f, 1f) ?: .6f)
            .putBoolean("gradientAngleSnapEnabled", persisted.gradientAngleSnapEnabled)
            .putFloat("gradientAngleSnapDegrees", persisted.gradientAngleSnapDegrees.takeIf(Float::isFinite)?.coerceIn(1f, 90f) ?: 45f)
            .putString("gradientStops", GradientStopCodec.encode(persisted.gradientStops))
            .putString("gradientPresets", GradientPresetLibraryCodec.encode(persisted.gradientPresets).toString(Charsets.UTF_8))
            .putFloat("liquifyStrengthScale", persisted.liquifyStrengthScale.coerceIn(0f, 2f))
            .putFloat("liquifyDistortion", persisted.liquifyDistortion.coerceIn(0f, 1f))
            .putFloat("liquifyMomentum", persisted.liquifyMomentum.coerceIn(0f, 1f))
            .putBoolean("cloneAligned", persisted.cloneAligned)
            .putBoolean("brushGridLayout", persisted.brushGridLayout)
            .putFloat("brushCategoryWidthDp", persisted.brushCategoryWidthDp.takeIf(Float::isFinite)?.coerceIn(56f,360f) ?: 120f)
            .putBoolean("layerPanelDualList", persisted.layerPanelDualList)
            .putFloat("layerPanelSplitFraction", persisted.layerPanelSplitFraction.takeIf(Float::isFinite)?.coerceIn(.2f, .8f) ?: .5f)
            .putString("layerRowDensity", persisted.layerRowDensity.name)
            .putBoolean("layerShowThumbnails", persisted.layerShowThumbnails)
            .putBoolean("vectorEraseWholeLine", persisted.vectorEraseWholeLine)
            .putBoolean("layerShowDetails", persisted.layerShowDetails)
            .putBoolean("layerShowProperties", persisted.layerShowProperties)
            .putBoolean("layerShowCommands", persisted.layerShowCommands)
            .putBoolean("layerCommandsAtBottom", persisted.layerCommandsAtBottom)
            .putFloat("selectionStudioXDp", persisted.selectionStudioXDp.takeIf(Float::isFinite)?.coerceIn(-1f, 2400f) ?: -1f)
            .putFloat("selectionStudioYDp", persisted.selectionStudioYDp.takeIf(Float::isFinite)?.coerceIn(-1f, 2400f) ?: -1f)
            .putBoolean("selectionLauncherVisible", persisted.selectionLauncherVisible)
            .putString("selectionLauncherOrder", SelectionLauncherLayout.encode(SelectionLauncherLayout.decode(persisted.selectionLauncherOrder)))
            .putBoolean("singleFingerDoubleTapUndo", persisted.singleFingerDoubleTapUndo)
            .putBoolean("twoFingerDoubleTapUndo", persisted.twoFingerDoubleTapUndo)
            .putBoolean("threeFingerTapRedo", persisted.threeFingerTapRedo)
            .putBoolean("swipeToTurnPages", persisted.swipeToTurnPages)
            .putBoolean("continuousPages", persisted.continuousPages)
            .putBoolean("twoFingerRotation", persisted.twoFingerRotation)
            .putString("symmetryMode", persisted.symmetryMode.name)
            .putFloat("symmetryCenterX", persisted.symmetryCenterX.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f)
            .putFloat("symmetryCenterY", persisted.symmetryCenterY.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .5f)
            .putFloat("symmetryAngleDegrees", app.inkloom.document.SymmetryGuideState.normalizeAngle(persisted.symmetryAngleDegrees))
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
            .putBoolean("excludeDraftLayersFromReference", persisted.excludeDraftLayersFromReference)
            .putBoolean("excludeLockedLayersFromReference", persisted.excludeLockedLayersFromReference)
            .putBoolean("excludeActiveLayerFromReference", persisted.excludeActiveLayerFromReference)
            .putBoolean("excludePaperFromReference", persisted.excludePaperFromReference)
            .putBoolean("excludeImportedBackgroundFromReference", persisted.excludeImportedBackgroundFromReference)
            .putBoolean("excludeTextFromReference", persisted.excludeTextFromReference)
            .putString("referenceTarget", persisted.referenceTarget.name)
            .putString("eyedropperReferenceTarget", persisted.eyedropperReferenceTarget.name)
            .putBoolean("eyedropperIncludePaper", persisted.eyedropperIncludePaper)
            .putString("correctLineMode", persisted.correctLineMode.name)
            .putFloat("correctLineAmount", persisted.correctLineAmount.coerceIn(0f, 1f))
            .putString("rulerToolMode", persisted.rulerToolMode.name)
            .putInt("rulerPolygonSides", persisted.rulerPolygonSides.coerceIn(3, 32))
            .putBoolean("snapToRulers", persisted.snapToRulers)
            .putBoolean("alignToTextPixels", persisted.alignToTextPixels)
            .putBoolean("alignToVectorPaths", persisted.alignToVectorPaths)
            .putString("frameToolMode", persisted.frameToolMode.name)
            .putString("frameDivision", persisted.frameDivision.name)
            .putFloat("frameHorizontalGutter", persisted.frameHorizontalGutter.coerceIn(0f, 500f))
            .putFloat("frameVerticalGutter", persisted.frameVerticalGutter.coerceIn(0f, 500f))
            .putBoolean("nonDestructiveEraser", persisted.nonDestructiveEraser)
            .putString("shortcutBindings", ShortcutBindingCodec.encode(persisted.shortcutBindings))
            .putString("shortcutAxisBindings", ToolboxMotionBindingCodec.encodeAxes(persisted.shortcutAxisBindings))
            .putString("shortcutButtonBindings", ToolboxMotionBindingCodec.encodeButtons(persisted.shortcutButtonBindings))
            .putString("shortcutPenGroup", persisted.shortcutBrushGroupTargets.pen)
            .putString("shortcutPencilGroup", persisted.shortcutBrushGroupTargets.pencil)
            .putString("shortcutBrushGroup", persisted.shortcutBrushGroupTargets.brush)
            .putString("toolboxQuickSlots", ToolboxQuickSlotCodec.encode(persisted.toolboxQuickSlots))
            .putString("rasterFilterPresets",RasterFilterPresetsCodec.encode(persisted.rasterFilterPresets))
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
        return decoded.takeIf(List<ShortcutBinding>::isNotEmpty) ?: CspShortcutResolver.defaultBindings
    }

    private fun encodeText(value: String?): String = value?.toByteArray(Charsets.UTF_8)?.let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it) }.orEmpty()
    private fun decodeText(value: String): String? = value.takeIf(String::isNotEmpty)?.let { String(java.util.Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
}
