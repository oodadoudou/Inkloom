package app.inkloom.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import app.inkloom.document.PressureResponseCurve

data class AppPreferencesBackup(
    val settings: AppSettings,
    val colorPalette: List<Int>,
    val colorSetLibrary: ColorSetLibrary? = null,
    val panelWorkspace: PanelWorkspaceBackup? = null,
)

object AppSettingsBackupCodec {
    // Named sets can contain 16,384 named swatches; retain an explicit file-size bound.
    const val MAX_BYTES = 32 * 1024 * 1024
    private const val FORMAT = "inkloom-app-settings"
    private const val VERSION = 1
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun encode(settings: AppSettings, colorPalette: List<Int> = emptyList(), colorSetLibrary: ColorSetLibrary? = null, panelWorkspace: PanelWorkspaceBackup? = null): ByteArray {
        panelWorkspace?.validate()
        val value = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("settings", settings.toJson())
            panelWorkspace?.let { put("panelWorkspace", json.encodeToJsonElement(PanelWorkspaceBackup.serializer(), it)) }
            put("shortcutProfile", json.parseToJsonElement(ShortcutProfileCodec.encode(ShortcutProfile(settings.shortcutBindings, settings.shortcutBrushGroupTargets, settings.toolboxQuickSlots, settings.shortcutAxisBindings, settings.shortcutButtonBindings)).toString(Charsets.UTF_8)))
            if (colorSetLibrary != null) {
                put("colorSetLibrary", json.parseToJsonElement(ColorSetLibraryCodec.encode(colorSetLibrary)))
            } else {
                put("colorPalette", buildJsonArray { colorPalette.distinct().take(32).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }
        }
        return json.encodeToString(JsonObject.serializer(), value).encodeToByteArray().also {
            require(it.size <= MAX_BYTES) { "App settings backup is too large" }
        }
    }

    fun decode(bytes: ByteArray): Result<AppPreferencesBackup> = runCatching {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "App settings backup has an invalid size" }
        val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(root.text("format") == FORMAT && root.int("version", 0) == VERSION) { "Unsupported app settings backup" }
        val base = root.getValue("settings").jsonObject.toSettings()
        val profile = root["shortcutProfile"]?.toString()?.encodeToByteArray()?.let { ShortcutProfileCodec.decodeProfile(it).getOrThrow() }
            ?: ShortcutProfile(base.shortcutBindings)
        val palette = root["colorPalette"]?.jsonArray?.mapNotNull { runCatching { it.jsonPrimitive.int }.getOrNull() }?.distinct()?.take(32).orEmpty()
        val colorSets = root["colorSetLibrary"]?.let { ColorSetLibraryCodec.decode(it.toString()) }
        val workspace = root["panelWorkspace"]?.let {
            json.decodeFromJsonElement(PanelWorkspaceBackup.serializer(), it).also(PanelWorkspaceBackup::validate)
        } ?: root.getValue("settings").jsonObject.takeIf { settings -> settings.keys.any { it in LegacyWorkspaceImport.panelKeys } }
            ?.let(LegacyWorkspaceImport::fromSettings)
        AppPreferencesBackup(
            base.copy(
                shortcutBindings = profile.bindings,
                shortcutAxisBindings = profile.axisBindings,
                shortcutButtonBindings = profile.buttonBindings,
                shortcutBrushGroupTargets = profile.brushGroupTargets,
                toolboxQuickSlots = profile.quickSlots,
            ),
            palette,
            colorSets,
            workspace,
        )
    }

    private fun AppSettings.toJson() = buildJsonObject {
        put("uiGrayTheme", uiGrayTheme.name); put("language", language.name); put("fingerDrawing", fingerDrawing); put("showToolbar", showToolbar)
        put("fillSensitivity", fillSensitivity); put("fillCloseGapPixels", fillCloseGapPixels); put("fillAreaScalingPixels", fillAreaScalingPixels); put("fillTargetMode", fillTargetMode.name); put("fillTargetColor", fillTargetColor); put("fillExpansionShape", fillExpansionShape.name); put("fillAntiAlias", fillAntiAlias); put("fillSubTool", fillSubTool.name)
        put("gradientKind", gradientKind.name); put("gradientEdgeMode", gradientEdgeMode.name); put("gradientReverse", gradientReverse); put("gradientDither", gradientDither)
        put("gradientEllipseRatio", gradientEllipseRatio); put("gradientAngleSnapEnabled", gradientAngleSnapEnabled); put("gradientAngleSnapDegrees", gradientAngleSnapDegrees); put("gradientStops", app.inkloom.document.GradientStopCodec.encode(gradientStops))
        put("gradientPresets", json.parseToJsonElement(GradientPresetLibraryCodec.encode(gradientPresets).toString(Charsets.UTF_8)))
        put("liquifyStrengthScale", liquifyStrengthScale); put("liquifyDistortion", liquifyDistortion); put("liquifyMomentum", liquifyMomentum)
        put("brushGridLayout", brushGridLayout); put("brushCategoryWidthDp",brushCategoryWidthDp);
        put("layerPanelDualList", layerPanelDualList); put("layerPanelSplitFraction", layerPanelSplitFraction); put("layerRowDensity", layerRowDensity.name)
        put("layerShowThumbnails", layerShowThumbnails); put("layerShowDetails", layerShowDetails)
        put("layerShowProperties", layerShowProperties); put("layerShowCommands", layerShowCommands); put("layerCommandsAtBottom", layerCommandsAtBottom)
        put("toolbarOffsetXDp", toolbarOffsetXDp); put("toolbarOffsetYDp", toolbarOffsetYDp); put("selectionStudioXDp",selectionStudioXDp); put("selectionStudioYDp",selectionStudioYDp); put("verticalToolbar", verticalToolbar); put("toolbarDock", toolbarDock.name)
        put("toolPaletteLayout", ToolPaletteLayout.encode(ToolPaletteLayout.decode(toolPaletteLayout)))
        put("singleFingerDoubleTapUndo", singleFingerDoubleTapUndo); put("twoFingerDoubleTapUndo", twoFingerDoubleTapUndo); put("threeFingerTapRedo", threeFingerTapRedo); put("twoFingerRotation", twoFingerRotation)
        put("symmetryMode", symmetryMode.name); put("symmetryCenterX", symmetryCenterX); put("symmetryCenterY", symmetryCenterY); put("symmetryAngleDegrees", symmetryAngleDegrees)
        put("rulerEnabled", rulerEnabled); put("rulerCenterX", rulerCenterX); put("rulerCenterY", rulerCenterY); put("rulerAngleDegrees", rulerAngleDegrees); put("rulerSnapDegrees", rulerSnapDegrees)
        put("gridSnapEnabled", gridSnapEnabled); put("gridSpacing", gridSpacing); put("alignmentGuidesEnabled", alignmentGuidesEnabled); put("verticalGuideX", verticalGuideX); put("horizontalGuideY", horizontalGuideY)
        put("perspectiveGuideMode", perspectiveGuideMode.name); put("perspectiveGuideAssisted", perspectiveGuideAssisted); put("perspectiveGuideEditing", perspectiveGuideEditing)
        put("perspectivePoint1X", perspectivePoint1X); put("perspectivePoint1Y", perspectivePoint1Y); put("perspectivePoint2X", perspectivePoint2X); put("perspectivePoint2Y", perspectivePoint2Y); put("perspectivePoint3X", perspectivePoint3X); put("perspectivePoint3Y", perspectivePoint3Y)
        put("specialRulerMode", specialRulerMode.name); put("specialRulerAssisted", specialRulerAssisted); put("specialRulerEditing", specialRulerEditing)
        put("specialRulerCenterX", specialRulerCenterX); put("specialRulerCenterY", specialRulerCenterY); put("specialRulerAngleDegrees", specialRulerAngleDegrees); put("specialRulerSpacing", specialRulerSpacing)
        put("stylusPrimaryButton", stylusPrimaryButton.name); put("stylusSecondaryButton", stylusSecondaryButton.name)
        put("stylusPrimaryTrigger", stylusPrimaryTrigger.name); put("stylusSecondaryTrigger", stylusSecondaryTrigger.name)
        put("globalMotionFiltering", globalMotionFiltering)
        put("globalMotionExpression", globalMotionExpression)
        put("globalStrokeStabilization", globalStrokeStabilization)
        put("pressureSensitivity", pressureSensitivity); put("pressureMinimumInput", pressureMinimumInput); put("pressureMaximumInput", pressureMaximumInput)
        put("pressureResponseCurve", buildJsonArray { PressureResponseCurve.normalize(pressureResponseCurve).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        put("showInputDiagnostics", showInputDiagnostics); put("showStrokeTraceOverlay", showStrokeTraceOverlay)
        put("penSize", penSize); put("penOpacity", penOpacity); put("highlighterSize", highlighterSize); put("highlighterOpacity", highlighterOpacity)
        put("eraserSize", eraserSize); put("eraserPreset", eraserPreset.name)
        put("selectionShape", selectionShape.name); put("selectionBrushSize", selectionBrushSize); put("selectionThreshold", selectionThreshold); put("selectionMode", selectionMode.name); put("selectionFilter", selectionFilter.name); put("referenceTarget", referenceTarget.name); put("excludeDraftLayersFromReference", excludeDraftLayersFromReference); put("excludeLockedLayersFromReference", excludeLockedLayersFromReference); put("excludeActiveLayerFromReference", excludeActiveLayerFromReference); put("excludePaperFromReference", excludePaperFromReference); put("excludeImportedBackgroundFromReference", excludeImportedBackgroundFromReference); put("excludeTextFromReference", excludeTextFromReference); put("eyedropperReferenceTarget", eyedropperReferenceTarget.name); put("eyedropperIncludePaper", eyedropperIncludePaper)
        put("correctLineMode", correctLineMode.name); put("correctLineAmount", correctLineAmount)
        put("nonDestructiveEraser", nonDestructiveEraser)
        put("rasterFilterPresets",json.parseToJsonElement(RasterFilterPresetsCodec.encode(rasterFilterPresets)))
    }

    private fun JsonObject.toSettings(): AppSettings {
        val defaults = AppSettings()
        val minimum = float("pressureMinimumInput", defaults.pressureMinimumInput).coerceIn(0f, .95f)
        val maximum = float("pressureMaximumInput", defaults.pressureMaximumInput).coerceIn(minimum + .05f, 1f)
        return defaults.copy(
            fillSensitivity = float("fillSensitivity", defaults.fillSensitivity).coerceIn(0f, 1f), fillCloseGapPixels = float("fillCloseGapPixels", defaults.fillCloseGapPixels).coerceIn(0f, 64f), fillAreaScalingPixels = float("fillAreaScalingPixels", defaults.fillAreaScalingPixels).coerceIn(-64f, 64f), fillTargetMode = enum("fillTargetMode", defaults.fillTargetMode), fillTargetColor = int("fillTargetColor", defaults.fillTargetColor), fillExpansionShape = enum("fillExpansionShape", defaults.fillExpansionShape), fillAntiAlias = boolean("fillAntiAlias", defaults.fillAntiAlias), fillSubTool = enum("fillSubTool", defaults.fillSubTool),
            gradientKind = enum("gradientKind", defaults.gradientKind), gradientEdgeMode = enum("gradientEdgeMode", defaults.gradientEdgeMode), gradientReverse = boolean("gradientReverse", defaults.gradientReverse), gradientDither = boolean("gradientDither", defaults.gradientDither),
            gradientEllipseRatio = float("gradientEllipseRatio", defaults.gradientEllipseRatio).coerceIn(.1f, 1f), gradientAngleSnapEnabled = boolean("gradientAngleSnapEnabled", defaults.gradientAngleSnapEnabled), gradientAngleSnapDegrees = float("gradientAngleSnapDegrees", defaults.gradientAngleSnapDegrees).coerceIn(1f, 90f), gradientStops = app.inkloom.document.GradientStopCodec.decode(text("gradientStops")),
            gradientPresets = this["gradientPresets"]?.let { GradientPresetLibraryCodec.decode(it.toString().encodeToByteArray()).getOrThrow() } ?: GradientPresetLibrary(),
            liquifyStrengthScale = float("liquifyStrengthScale", defaults.liquifyStrengthScale).coerceIn(0f, 2f),
            liquifyDistortion = float("liquifyDistortion", defaults.liquifyDistortion).coerceIn(0f, 1f),
            liquifyMomentum = float("liquifyMomentum", defaults.liquifyMomentum).coerceIn(0f, 1f),
            uiGrayTheme = UiGrayTheme.decode(text("uiGrayTheme")),
            language = enum("language", defaults.language), fingerDrawing = boolean("fingerDrawing", defaults.fingerDrawing),

             showToolbar = boolean("showToolbar", defaults.showToolbar),









            brushGridLayout = boolean("brushGridLayout", defaults.brushGridLayout),
            brushCategoryWidthDp = float("brushCategoryWidthDp",defaults.brushCategoryWidthDp).coerceIn(56f,360f),



            layerPanelDualList = boolean("layerPanelDualList", defaults.layerPanelDualList), layerPanelSplitFraction = float("layerPanelSplitFraction", defaults.layerPanelSplitFraction).coerceIn(.2f, .8f),
            layerRowDensity = enum("layerRowDensity", defaults.layerRowDensity), layerShowThumbnails = boolean("layerShowThumbnails", defaults.layerShowThumbnails), layerShowDetails = boolean("layerShowDetails", defaults.layerShowDetails),
            layerShowProperties = boolean("layerShowProperties", defaults.layerShowProperties), layerShowCommands = boolean("layerShowCommands", defaults.layerShowCommands), layerCommandsAtBottom = boolean("layerCommandsAtBottom", defaults.layerCommandsAtBottom),
            toolbarOffsetXDp = float("toolbarOffsetXDp", defaults.toolbarOffsetXDp).coerceIn(-1200f, 1200f), toolbarOffsetYDp = float("toolbarOffsetYDp", defaults.toolbarOffsetYDp).coerceIn(-1200f, 1200f),
            selectionStudioXDp = float("selectionStudioXDp",defaults.selectionStudioXDp).coerceIn(-1f,2400f), selectionStudioYDp = float("selectionStudioYDp",defaults.selectionStudioYDp).coerceIn(-1f,2400f),
            verticalToolbar = boolean("verticalToolbar", defaults.verticalToolbar), toolbarDock = enum("toolbarDock", defaults.toolbarDock),
            toolPaletteLayout = ToolPaletteLayout.encode(ToolPaletteLayout.decode(text("toolPaletteLayout").ifBlank { defaults.toolPaletteLayout })),
            singleFingerDoubleTapUndo = boolean("singleFingerDoubleTapUndo", defaults.singleFingerDoubleTapUndo), twoFingerDoubleTapUndo = boolean("twoFingerDoubleTapUndo", defaults.twoFingerDoubleTapUndo), threeFingerTapRedo = boolean("threeFingerTapRedo", defaults.threeFingerTapRedo),
            twoFingerRotation = boolean("twoFingerRotation", defaults.twoFingerRotation),
            symmetryMode = enum("symmetryMode", defaults.symmetryMode), symmetryCenterX = float("symmetryCenterX", defaults.symmetryCenterX).coerceIn(0f, 1f), symmetryCenterY = float("symmetryCenterY", defaults.symmetryCenterY).coerceIn(0f, 1f),
            symmetryAngleDegrees = app.inkloom.document.SymmetryGuideState.normalizeAngle(float("symmetryAngleDegrees", defaults.symmetryAngleDegrees)),
            rulerEnabled = boolean("rulerEnabled", defaults.rulerEnabled),
            rulerCenterX = float("rulerCenterX", defaults.rulerCenterX).coerceIn(0f, 1f), rulerCenterY = float("rulerCenterY", defaults.rulerCenterY).coerceIn(0f, 1f),
            rulerAngleDegrees = app.inkloom.document.RulerGuideState.normalizeAngle(float("rulerAngleDegrees", defaults.rulerAngleDegrees)), rulerSnapDegrees = float("rulerSnapDegrees", defaults.rulerSnapDegrees).coerceIn(1f, 90f),
            gridSnapEnabled = boolean("gridSnapEnabled", defaults.gridSnapEnabled), gridSpacing = float("gridSpacing", defaults.gridSpacing).coerceIn(16f, 400f),
            alignmentGuidesEnabled = boolean("alignmentGuidesEnabled", defaults.alignmentGuidesEnabled), verticalGuideX = float("verticalGuideX", defaults.verticalGuideX).coerceIn(0f, 1f), horizontalGuideY = float("horizontalGuideY", defaults.horizontalGuideY).coerceIn(0f, 1f),
            perspectiveGuideMode = enum("perspectiveGuideMode", defaults.perspectiveGuideMode), perspectiveGuideAssisted = boolean("perspectiveGuideAssisted", defaults.perspectiveGuideAssisted), perspectiveGuideEditing = boolean("perspectiveGuideEditing", defaults.perspectiveGuideEditing),
            perspectivePoint1X = float("perspectivePoint1X", defaults.perspectivePoint1X).coerceIn(-4f, 5f), perspectivePoint1Y = float("perspectivePoint1Y", defaults.perspectivePoint1Y).coerceIn(-4f, 5f),
            perspectivePoint2X = float("perspectivePoint2X", defaults.perspectivePoint2X).coerceIn(-4f, 5f), perspectivePoint2Y = float("perspectivePoint2Y", defaults.perspectivePoint2Y).coerceIn(-4f, 5f),
            perspectivePoint3X = float("perspectivePoint3X", defaults.perspectivePoint3X).coerceIn(-4f, 5f), perspectivePoint3Y = float("perspectivePoint3Y", defaults.perspectivePoint3Y).coerceIn(-4f, 5f),
            specialRulerMode = enum("specialRulerMode", defaults.specialRulerMode), specialRulerAssisted = boolean("specialRulerAssisted", defaults.specialRulerAssisted), specialRulerEditing = boolean("specialRulerEditing", defaults.specialRulerEditing),
            specialRulerCenterX = float("specialRulerCenterX", defaults.specialRulerCenterX).coerceIn(-2f, 3f), specialRulerCenterY = float("specialRulerCenterY", defaults.specialRulerCenterY).coerceIn(-2f, 3f),
            specialRulerAngleDegrees = app.inkloom.document.RulerGuideState.normalizeAngle(float("specialRulerAngleDegrees", defaults.specialRulerAngleDegrees)), specialRulerSpacing = float("specialRulerSpacing", defaults.specialRulerSpacing).coerceIn(16f, 400f),
            stylusPrimaryButton = enum("stylusPrimaryButton", defaults.stylusPrimaryButton), stylusSecondaryButton = enum("stylusSecondaryButton", defaults.stylusSecondaryButton),
            stylusPrimaryTrigger = enum("stylusPrimaryTrigger", defaults.stylusPrimaryTrigger), stylusSecondaryTrigger = enum("stylusSecondaryTrigger", defaults.stylusSecondaryTrigger),
            globalMotionFiltering = float("globalMotionFiltering", defaults.globalMotionFiltering).coerceIn(0f, 1f),
            globalMotionExpression = float("globalMotionExpression", defaults.globalMotionExpression).coerceIn(0f, 1f),
            globalStrokeStabilization = float("globalStrokeStabilization", defaults.globalStrokeStabilization).coerceIn(0f, 1f),
            pressureSensitivity = float("pressureSensitivity", defaults.pressureSensitivity).coerceIn(.25f, 2.5f), pressureMinimumInput = minimum, pressureMaximumInput = maximum,
            pressureResponseCurve = PressureResponseCurve.normalize(floatList("pressureResponseCurve", defaults.pressureResponseCurve)),
            showInputDiagnostics = boolean("showInputDiagnostics", defaults.showInputDiagnostics), showStrokeTraceOverlay = boolean("showStrokeTraceOverlay", defaults.showStrokeTraceOverlay),
            penSize = float("penSize", defaults.penSize).coerceIn(.5f, 400f), penOpacity = float("penOpacity", defaults.penOpacity).coerceIn(.05f, 1f),
            highlighterSize = float("highlighterSize", defaults.highlighterSize).coerceIn(.5f, 400f), highlighterOpacity = float("highlighterOpacity", defaults.highlighterOpacity).coerceIn(.05f, 1f),
            eraserSize = float("eraserSize", defaults.eraserSize).coerceIn(.5f, 400f), eraserPreset = enum("eraserPreset", defaults.eraserPreset),
            selectionShape = enum("selectionShape", defaults.selectionShape), selectionBrushSize = float("selectionBrushSize", defaults.selectionBrushSize).coerceIn(4f, 300f), selectionThreshold = float("selectionThreshold", defaults.selectionThreshold).coerceIn(0f, 1f), selectionMode = enum("selectionMode", defaults.selectionMode), selectionFilter = enum("selectionFilter", defaults.selectionFilter), referenceTarget = enum("referenceTarget", defaults.referenceTarget), excludeDraftLayersFromReference = boolean("excludeDraftLayersFromReference", defaults.excludeDraftLayersFromReference), excludeLockedLayersFromReference = boolean("excludeLockedLayersFromReference", defaults.excludeLockedLayersFromReference), excludeActiveLayerFromReference = boolean("excludeActiveLayerFromReference", defaults.excludeActiveLayerFromReference), excludePaperFromReference = boolean("excludePaperFromReference", defaults.excludePaperFromReference), excludeImportedBackgroundFromReference = boolean("excludeImportedBackgroundFromReference", defaults.excludeImportedBackgroundFromReference), excludeTextFromReference = boolean("excludeTextFromReference", defaults.excludeTextFromReference), eyedropperReferenceTarget = enum("eyedropperReferenceTarget", defaults.eyedropperReferenceTarget), eyedropperIncludePaper = boolean("eyedropperIncludePaper", defaults.eyedropperIncludePaper),
            correctLineMode = enum("correctLineMode", defaults.correctLineMode), correctLineAmount = float("correctLineAmount", defaults.correctLineAmount).coerceIn(0f, 1f),
            nonDestructiveEraser = boolean("nonDestructiveEraser", defaults.nonDestructiveEraser),
            rasterFilterPresets = RasterFilterPresetsCodec.decode(get("rasterFilterPresets")?.toString()),

        )
    }

    private fun JsonObject.text(key: String): String = get(key)?.jsonPrimitive?.content.orEmpty()
    private fun JsonObject.float(key: String, fallback: Float): Float = runCatching { get(key)?.jsonPrimitive?.float }.getOrNull() ?: fallback
    private fun JsonObject.int(key: String, fallback: Int): Int = runCatching { get(key)?.jsonPrimitive?.int }.getOrNull() ?: fallback
    private fun JsonObject.boolean(key: String, fallback: Boolean): Boolean = runCatching { get(key)?.jsonPrimitive?.boolean }.getOrNull() ?: fallback
    private fun JsonObject.floatList(key: String, fallback: List<Float>): List<Float> =
        runCatching { get(key)?.jsonArray?.map { it.jsonPrimitive.float } }.getOrNull() ?: fallback
    private inline fun <reified T : Enum<T>> JsonObject.enum(key: String, fallback: T): T = runCatching { enumValueOf<T>(text(key)) }.getOrDefault(fallback)
}
