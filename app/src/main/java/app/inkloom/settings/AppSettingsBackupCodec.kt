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

data class AppPreferencesBackup(val settings: AppSettings, val colorPalette: List<Int>)

object AppSettingsBackupCodec {
    const val MAX_BYTES = 512 * 1024
    private const val FORMAT = "inkloom-app-settings"
    private const val VERSION = 1
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun encode(settings: AppSettings, colorPalette: List<Int>): ByteArray {
        val value = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("settings", settings.toJson())
            put("shortcutProfile", json.parseToJsonElement(ShortcutProfileCodec.encode(ShortcutProfile(settings.shortcutBindings, settings.shortcutBrushGroupTargets, settings.toolboxQuickSlots, settings.shortcutAxisBindings, settings.shortcutButtonBindings)).toString(Charsets.UTF_8)))
            put("colorPalette", buildJsonArray { colorPalette.distinct().take(32).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
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
        AppPreferencesBackup(
            base.copy(
                shortcutBindings = profile.bindings,
                shortcutAxisBindings = profile.axisBindings,
                shortcutButtonBindings = profile.buttonBindings,
                shortcutBrushGroupTargets = profile.brushGroupTargets,
                toolboxQuickSlots = profile.quickSlots,
            ),
            palette,
        )
    }

    private fun AppSettings.toJson() = buildJsonObject {
        put("language", language.name); put("fingerDrawing", fingerDrawing)
        put("showPageRail", showPageRail); put("showBrushPanel", showBrushPanel); put("showLayerPanel", showLayerPanel); put("showToolbar", showToolbar)
        put("fillSensitivity", fillSensitivity); put("fillCloseGapPixels", fillCloseGapPixels); put("fillAreaScalingPixels", fillAreaScalingPixels); put("fillSubTool", fillSubTool.name)
        put("pagePanelDock", pagePanelDock.name); put("brushPanelDock", brushPanelDock.name); put("layerPanelDock", layerPanelDock.name)
        put("pagePanelMode", pagePanelMode.name); put("brushPanelMode", brushPanelMode.name); put("layerPanelMode", layerPanelMode.name)
        put("pagePanelLocked", pagePanelLocked); put("brushPanelLocked", brushPanelLocked); put("layerPanelLocked", layerPanelLocked)
        put("leftDockStacked", leftDockStacked); put("rightDockStacked", rightDockStacked); put("bottomDockStacked", bottomDockStacked); put("freeDockStacked", freeDockStacked)
        put("leftDockActivePanel", leftDockActivePanel.name); put("rightDockActivePanel", rightDockActivePanel.name); put("bottomDockActivePanel", bottomDockActivePanel.name); put("freeDockActivePanel", freeDockActivePanel.name)
        put("panelTabOrder", WorkspacePanelTabs.encodeOrder(WorkspacePanelTabs.normalizeOrder(panelTabOrder)))
        put("pagePanelXDp", pagePanelXDp); put("pagePanelYDp", pagePanelYDp); put("pagePanelWidthDp", pagePanelWidthDp); put("pagePanelHeightDp", pagePanelHeightDp)
        put("brushPanelXDp", brushPanelXDp); put("brushPanelYDp", brushPanelYDp); put("brushPanelWidthDp", brushPanelWidthDp); put("brushPanelHeightDp", brushPanelHeightDp)
        put("layerPanelXDp", layerPanelXDp); put("layerPanelYDp", layerPanelYDp); put("layerPanelWidthDp", layerPanelWidthDp); put("layerPanelHeightDp", layerPanelHeightDp)
        put("toolbarOffsetXDp", toolbarOffsetXDp); put("toolbarOffsetYDp", toolbarOffsetYDp); put("verticalToolbar", verticalToolbar); put("toolbarDock", toolbarDock.name)
        put("singleFingerDoubleTapUndo", singleFingerDoubleTapUndo); put("twoFingerDoubleTapUndo", twoFingerDoubleTapUndo); put("threeFingerTapRedo", threeFingerTapRedo); put("twoFingerRotation", twoFingerRotation)
        put("rulerEnabled", rulerEnabled); put("rulerCenterX", rulerCenterX); put("rulerCenterY", rulerCenterY); put("rulerAngleDegrees", rulerAngleDegrees); put("rulerSnapDegrees", rulerSnapDegrees)
        put("gridSnapEnabled", gridSnapEnabled); put("gridSpacing", gridSpacing); put("alignmentGuidesEnabled", alignmentGuidesEnabled); put("verticalGuideX", verticalGuideX); put("horizontalGuideY", horizontalGuideY)
        put("perspectiveGuideMode", perspectiveGuideMode.name); put("perspectiveGuideAssisted", perspectiveGuideAssisted); put("perspectiveGuideEditing", perspectiveGuideEditing)
        put("perspectivePoint1X", perspectivePoint1X); put("perspectivePoint1Y", perspectivePoint1Y); put("perspectivePoint2X", perspectivePoint2X); put("perspectivePoint2Y", perspectivePoint2Y); put("perspectivePoint3X", perspectivePoint3X); put("perspectivePoint3Y", perspectivePoint3Y)
        put("stylusPrimaryButton", stylusPrimaryButton.name); put("stylusSecondaryButton", stylusSecondaryButton.name)
        put("stylusPrimaryTrigger", stylusPrimaryTrigger.name); put("stylusSecondaryTrigger", stylusSecondaryTrigger.name)
        put("pressureSensitivity", pressureSensitivity); put("pressureMinimumInput", pressureMinimumInput); put("pressureMaximumInput", pressureMaximumInput)
        put("pressureResponseCurve", buildJsonArray { PressureResponseCurve.normalize(pressureResponseCurve).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        put("stabilization", stabilization); put("postCorrection", postCorrection); put("postCorrectionAdjustBySpeed", postCorrectionAdjustBySpeed); put("postCorrectionAdjustByScale", postCorrectionAdjustByScale); put("showInputDiagnostics", showInputDiagnostics)
        put("penSize", penSize); put("penOpacity", penOpacity); put("highlighterSize", highlighterSize); put("highlighterOpacity", highlighterOpacity)
        put("eraserSize", eraserSize); put("eraserPreset", eraserPreset.name)
        put("selectionShape", selectionShape.name); put("selectionBrushSize", selectionBrushSize); put("selectionThreshold", selectionThreshold); put("selectionMode", selectionMode.name); put("selectionFilter", selectionFilter.name); put("referenceTarget", referenceTarget.name)
        put("correctLineMode", correctLineMode.name); put("correctLineAmount", correctLineAmount)
        put("nonDestructiveEraser", nonDestructiveEraser)
        put("workspaceLayouts", json.parseToJsonElement(WorkspaceLayoutProfilesCodec.encode(workspaceLayouts)))
    }

    private fun JsonObject.toSettings(): AppSettings {
        val defaults = AppSettings()
        val minimum = float("pressureMinimumInput", defaults.pressureMinimumInput).coerceIn(0f, .95f)
        val maximum = float("pressureMaximumInput", defaults.pressureMaximumInput).coerceIn(minimum + .05f, 1f)
        return defaults.copy(
            fillSensitivity = float("fillSensitivity", defaults.fillSensitivity).coerceIn(0f, 1f), fillCloseGapPixels = float("fillCloseGapPixels", defaults.fillCloseGapPixels).coerceIn(0f, 64f), fillAreaScalingPixels = float("fillAreaScalingPixels", defaults.fillAreaScalingPixels).coerceIn(-64f, 64f), fillSubTool = enum("fillSubTool", defaults.fillSubTool),
            language = enum("language", defaults.language), fingerDrawing = boolean("fingerDrawing", defaults.fingerDrawing),
            showPageRail = boolean("showPageRail", defaults.showPageRail), showBrushPanel = boolean("showBrushPanel", defaults.showBrushPanel),
            showLayerPanel = boolean("showLayerPanel", defaults.showLayerPanel), showToolbar = boolean("showToolbar", defaults.showToolbar),
            pagePanelDock = enum("pagePanelDock", defaults.pagePanelDock), brushPanelDock = enum("brushPanelDock", defaults.brushPanelDock), layerPanelDock = enum("layerPanelDock", defaults.layerPanelDock),
            pagePanelMode = enum("pagePanelMode", defaults.pagePanelMode), brushPanelMode = enum("brushPanelMode", defaults.brushPanelMode), layerPanelMode = enum("layerPanelMode", defaults.layerPanelMode),
            pagePanelLocked = boolean("pagePanelLocked", defaults.pagePanelLocked), brushPanelLocked = boolean("brushPanelLocked", defaults.brushPanelLocked), layerPanelLocked = boolean("layerPanelLocked", defaults.layerPanelLocked),
            leftDockStacked = boolean("leftDockStacked", defaults.leftDockStacked), rightDockStacked = boolean("rightDockStacked", defaults.rightDockStacked), bottomDockStacked = boolean("bottomDockStacked", defaults.bottomDockStacked), freeDockStacked = boolean("freeDockStacked", defaults.freeDockStacked),
            leftDockActivePanel = enum("leftDockActivePanel", defaults.leftDockActivePanel), rightDockActivePanel = enum("rightDockActivePanel", defaults.rightDockActivePanel), bottomDockActivePanel = enum("bottomDockActivePanel", defaults.bottomDockActivePanel), freeDockActivePanel = enum("freeDockActivePanel", defaults.freeDockActivePanel),
            panelTabOrder = WorkspacePanelTabs.encodeOrder(WorkspacePanelTabs.normalizeOrder(text("panelTabOrder").ifBlank { defaults.panelTabOrder })),
            pagePanelXDp = float("pagePanelXDp", defaults.pagePanelXDp).coerceIn(-2400f, 2400f), pagePanelYDp = float("pagePanelYDp", defaults.pagePanelYDp).coerceIn(-2400f, 2400f),
            pagePanelWidthDp = float("pagePanelWidthDp", defaults.pagePanelWidthDp).coerceIn(80f, 1200f), pagePanelHeightDp = float("pagePanelHeightDp", defaults.pagePanelHeightDp).coerceIn(120f, 1600f),
            brushPanelXDp = float("brushPanelXDp", defaults.brushPanelXDp).coerceIn(-2400f, 2400f), brushPanelYDp = float("brushPanelYDp", defaults.brushPanelYDp).coerceIn(-2400f, 2400f),
            brushPanelWidthDp = float("brushPanelWidthDp", defaults.brushPanelWidthDp).coerceIn(80f, 1200f), brushPanelHeightDp = float("brushPanelHeightDp", defaults.brushPanelHeightDp).coerceIn(120f, 1600f),
            layerPanelXDp = float("layerPanelXDp", defaults.layerPanelXDp).coerceIn(-2400f, 2400f), layerPanelYDp = float("layerPanelYDp", defaults.layerPanelYDp).coerceIn(-2400f, 2400f),
            layerPanelWidthDp = float("layerPanelWidthDp", defaults.layerPanelWidthDp).coerceIn(80f, 1200f), layerPanelHeightDp = float("layerPanelHeightDp", defaults.layerPanelHeightDp).coerceIn(120f, 1600f),
            toolbarOffsetXDp = float("toolbarOffsetXDp", defaults.toolbarOffsetXDp).coerceIn(-1200f, 1200f), toolbarOffsetYDp = float("toolbarOffsetYDp", defaults.toolbarOffsetYDp).coerceIn(-1200f, 1200f),
            verticalToolbar = boolean("verticalToolbar", defaults.verticalToolbar), toolbarDock = enum("toolbarDock", defaults.toolbarDock),
            singleFingerDoubleTapUndo = boolean("singleFingerDoubleTapUndo", defaults.singleFingerDoubleTapUndo), twoFingerDoubleTapUndo = boolean("twoFingerDoubleTapUndo", defaults.twoFingerDoubleTapUndo), threeFingerTapRedo = boolean("threeFingerTapRedo", defaults.threeFingerTapRedo),
            twoFingerRotation = boolean("twoFingerRotation", defaults.twoFingerRotation),
            rulerEnabled = boolean("rulerEnabled", defaults.rulerEnabled),
            rulerCenterX = float("rulerCenterX", defaults.rulerCenterX).coerceIn(0f, 1f), rulerCenterY = float("rulerCenterY", defaults.rulerCenterY).coerceIn(0f, 1f),
            rulerAngleDegrees = app.inkloom.document.RulerGuideState.normalizeAngle(float("rulerAngleDegrees", defaults.rulerAngleDegrees)), rulerSnapDegrees = float("rulerSnapDegrees", defaults.rulerSnapDegrees).coerceIn(1f, 90f),
            gridSnapEnabled = boolean("gridSnapEnabled", defaults.gridSnapEnabled), gridSpacing = float("gridSpacing", defaults.gridSpacing).coerceIn(16f, 400f),
            alignmentGuidesEnabled = boolean("alignmentGuidesEnabled", defaults.alignmentGuidesEnabled), verticalGuideX = float("verticalGuideX", defaults.verticalGuideX).coerceIn(0f, 1f), horizontalGuideY = float("horizontalGuideY", defaults.horizontalGuideY).coerceIn(0f, 1f),
            perspectiveGuideMode = enum("perspectiveGuideMode", defaults.perspectiveGuideMode), perspectiveGuideAssisted = boolean("perspectiveGuideAssisted", defaults.perspectiveGuideAssisted), perspectiveGuideEditing = boolean("perspectiveGuideEditing", defaults.perspectiveGuideEditing),
            perspectivePoint1X = float("perspectivePoint1X", defaults.perspectivePoint1X).coerceIn(-4f, 5f), perspectivePoint1Y = float("perspectivePoint1Y", defaults.perspectivePoint1Y).coerceIn(-4f, 5f),
            perspectivePoint2X = float("perspectivePoint2X", defaults.perspectivePoint2X).coerceIn(-4f, 5f), perspectivePoint2Y = float("perspectivePoint2Y", defaults.perspectivePoint2Y).coerceIn(-4f, 5f),
            perspectivePoint3X = float("perspectivePoint3X", defaults.perspectivePoint3X).coerceIn(-4f, 5f), perspectivePoint3Y = float("perspectivePoint3Y", defaults.perspectivePoint3Y).coerceIn(-4f, 5f),
            stylusPrimaryButton = enum("stylusPrimaryButton", defaults.stylusPrimaryButton), stylusSecondaryButton = enum("stylusSecondaryButton", defaults.stylusSecondaryButton),
            stylusPrimaryTrigger = enum("stylusPrimaryTrigger", defaults.stylusPrimaryTrigger), stylusSecondaryTrigger = enum("stylusSecondaryTrigger", defaults.stylusSecondaryTrigger),
            pressureSensitivity = float("pressureSensitivity", defaults.pressureSensitivity).coerceIn(.25f, 2.5f), pressureMinimumInput = minimum, pressureMaximumInput = maximum,
            pressureResponseCurve = PressureResponseCurve.normalize(floatList("pressureResponseCurve", defaults.pressureResponseCurve)),
            stabilization = float("stabilization", defaults.stabilization).coerceIn(0f, .9f), postCorrection = float("postCorrection", defaults.postCorrection).coerceIn(0f, 1f), postCorrectionAdjustBySpeed = boolean("postCorrectionAdjustBySpeed", defaults.postCorrectionAdjustBySpeed), postCorrectionAdjustByScale = boolean("postCorrectionAdjustByScale", defaults.postCorrectionAdjustByScale), showInputDiagnostics = boolean("showInputDiagnostics", defaults.showInputDiagnostics),
            penSize = float("penSize", defaults.penSize).coerceIn(.5f, 400f), penOpacity = float("penOpacity", defaults.penOpacity).coerceIn(.05f, 1f),
            highlighterSize = float("highlighterSize", defaults.highlighterSize).coerceIn(.5f, 400f), highlighterOpacity = float("highlighterOpacity", defaults.highlighterOpacity).coerceIn(.05f, 1f),
            eraserSize = float("eraserSize", defaults.eraserSize).coerceIn(.5f, 400f), eraserPreset = enum("eraserPreset", defaults.eraserPreset),
            selectionShape = enum("selectionShape", defaults.selectionShape), selectionBrushSize = float("selectionBrushSize", defaults.selectionBrushSize).coerceIn(4f, 300f), selectionThreshold = float("selectionThreshold", defaults.selectionThreshold).coerceIn(0f, 1f), selectionMode = enum("selectionMode", defaults.selectionMode), selectionFilter = enum("selectionFilter", defaults.selectionFilter), referenceTarget = enum("referenceTarget", defaults.referenceTarget),
            correctLineMode = enum("correctLineMode", defaults.correctLineMode), correctLineAmount = float("correctLineAmount", defaults.correctLineAmount).coerceIn(0f, 1f),
            nonDestructiveEraser = boolean("nonDestructiveEraser", defaults.nonDestructiveEraser),
            workspaceLayouts = WorkspaceLayoutProfilesCodec.decode(get("workspaceLayouts")?.toString()),
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
