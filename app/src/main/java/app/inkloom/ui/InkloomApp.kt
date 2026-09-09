package app.inkloom.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Point
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.stylusHoverIcon
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import app.inkloom.document.*
import app.inkloom.brushpresetv5.OwnedBrushPreset
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.brushpresetv5.BrushPresetV5
import app.inkloom.brushpresetv5.BrushPresetV5Codec
import app.inkloom.brushpresetv5.BrushFamily
import app.inkloom.brushpresetv5.BrushOutput
import app.inkloom.brushpresetv5.DepositionSettings
import app.inkloom.brushpresetv5.DynamicsSettings
import app.inkloom.brushpresetv5.PresetIdentity
import app.inkloom.brushpresetv5.PressureMappings
import app.inkloom.brushpresetv5.TiltMappings
import app.inkloom.brushpresetv5.TipSettings
import app.inkloom.brushpresetv5.CompiledBrushProgramV5
import app.inkloom.brushpresetv5.PresetOwnership
import app.inkloom.brushpresetv5.PresetOwnershipOperations
import app.inkloom.importer.BrushImportRepository
import app.inkloom.importer.BrushImportBatch
import app.inkloom.importer.BrushImportLedger
import app.inkloom.importer.BrushLibraryInventory
import app.inkloom.importer.BrushGroupOperations
import app.inkloom.importer.BrushGroupStore
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.importer.BrushRoleClassifier
import app.inkloom.importer.BrushSemanticMapper
import app.inkloom.importer.BrushVersion
import app.inkloom.importer.BrushVersionHistoryStore
import app.inkloom.model.*
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.engine.v5.diagnostics.ColorPickerInteractionTrace
import app.inkloom.engine.v5.tools.RasterLayerPatch
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsBackupCodec
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.AxisShortcutBinding
import app.inkloom.settings.BuiltInWorkspace
import app.inkloom.settings.BuiltInWorkspaces
import app.inkloom.settings.ButtonShortcutBinding
import app.inkloom.settings.ColorPaletteStore
import app.inkloom.settings.EraserPreset
import app.inkloom.settings.FloatingToolbarPlacement
import app.inkloom.settings.SelectionFilter
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.ReferenceTarget
import app.inkloom.settings.BrushMasterSettingsStore
import app.inkloom.settings.BrushUsageState
import app.inkloom.settings.BrushUsageStore
import app.inkloom.settings.DrawingColorStore
import app.inkloom.settings.DrawingColors
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutActivation
import app.inkloom.settings.ShortcutCategory
import app.inkloom.settings.CspShortcutResolver
import app.inkloom.settings.KeyInputDiagnostic
import app.inkloom.settings.LibraryLayout
import app.inkloom.settings.MotionInputDiagnostic
import app.inkloom.settings.ShortcutChord
import app.inkloom.settings.ShortcutMatchMode
import app.inkloom.settings.ShortcutPhase
import app.inkloom.settings.ShortcutProfile
import app.inkloom.settings.ShortcutProfileCodec
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.ShortcutBrushGroup
import app.inkloom.settings.ShortcutBrushSelection
import app.inkloom.settings.category
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.ToolbarDock
import app.inkloom.settings.ToolboxAxis
import app.inkloom.settings.ToolboxAxisMode
import app.inkloom.settings.ToolboxMotionResolver
import app.inkloom.settings.ToolboxQuickSlot
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.ToolboxQuickSlots
import app.inkloom.settings.applyToolboxWorkspace
import app.inkloom.settings.captureToolboxWorkspace
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.WorkspacePanelDock
import app.inkloom.settings.WorkspacePanelDocking
import app.inkloom.settings.WorkspacePanelGrouping
import app.inkloom.settings.WorkspacePanelKind
import app.inkloom.settings.WorkspacePanelMode
import app.inkloom.settings.WorkspacePanelTabs
import app.inkloom.settings.WorkspacePanelBounds
import app.inkloom.settings.WorkspacePanelPlacement
import app.inkloom.settings.WorkspaceOrientation
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private enum class AppDestination { LIBRARY, EDITOR }
private enum class BrushBatchGroupOperation { MOVE, COPY }
private const val UNGROUPED_BRUSH_FILTER = "\u0000ungrouped"
private val LocalUiLanguage = staticCompositionLocalOf { UiLanguage.ENGLISH }

private class ColorDropDragShadow(private val color: Int) : View.DragShadowBuilder() {
    private val diameter = 56
    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(diameter, diameter)
        outShadowTouchPoint.set(diameter / 2, diameter / 2)
    }
    override fun onDrawShadow(canvas: AndroidCanvas) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = this@ColorDropDragShadow.color }
        canvas.drawCircle(diameter / 2f, diameter / 2f, diameter * .39f, paint)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(diameter / 2f, diameter / 2f, diameter * .39f, paint)
    }
}

@Composable
private fun tr(english: String, simplifiedChinese: String): String = LocalUiLanguage.current.text(english, simplifiedChinese)

@Composable
private fun layerBlendModeLabel(mode: LayerBlendMode): String =
    LayerBlendModeDisplayName.of(mode, LocalUiLanguage.current)

@Composable
private fun localizedShapeKind(kind: ShapeKind): String = when (kind) {
    ShapeKind.ELLIPSE -> tr("Ellipse", "椭圆")
    ShapeKind.RECTANGLE -> tr("Rectangle", "矩形")
    ShapeKind.TRIANGLE -> tr("Triangle", "三角形")
    ShapeKind.DIAMOND -> tr("Diamond", "菱形")
    ShapeKind.LINE -> tr("Line", "直线")
    ShapeKind.ARROW -> tr("Arrow", "箭头")
    ShapeKind.FREEFORM -> tr("Freeform", "自由形状")
}

@Composable
private fun localizedTextAlignment(alignment: InkTextAlignment): String = when (alignment) {
    InkTextAlignment.START -> tr("Start", "左对齐")
    InkTextAlignment.CENTER -> tr("Center", "居中")
    InkTextAlignment.END -> tr("End", "右对齐")
}

@Composable
private fun localizedListStyle(style: TextListStyle): String = when (style) {
    TextListStyle.NONE -> tr("None", "无")
    TextListStyle.BULLET -> tr("Bullet", "项目符号")
    TextListStyle.NUMBERED -> tr("Numbered", "编号")
    TextListStyle.CHECKBOX -> tr("Checkbox", "复选框")
}

@Composable
private fun localizedImageMask(mask: ImageMask): String = when (mask) {
    ImageMask.RECTANGLE -> tr("Rectangle", "矩形")
    ImageMask.ROUNDED -> tr("Rounded", "圆角")
    ImageMask.OVAL -> tr("Oval", "椭圆")
}

@Composable
private fun localizedTaperMode(mode: BrushTaperMode): String = when (mode) {
    BrushTaperMode.DISTANCE -> tr("Distance", "距离")
    BrushTaperMode.PERCENTAGE -> tr("Percentage", "百分比")
    BrushTaperMode.FADE -> tr("Fade", "淡出")
}

@Composable
private fun localizedBrushLabel(raw: String): String {
    if (LocalUiLanguage.current == UiLanguage.ENGLISH) return raw
    return when (raw) {
        "Size" -> "大小"
        "Stabilization" -> "防抖"
        "Adjust stabilization by speed" -> "根据速度调整防抖"
        "Uses the imported CSP correction behavior when available." -> "可用时采用导入的 CSP 修正行为。"
        "Default opacity" -> "默认不透明度"
        "Use as eraser" -> "用作橡皮擦"
        "Selects the eraser tool and uses this tip shape to remove ink." -> "选择橡皮擦工具，并使用此笔尖形状擦除墨迹。"
        "Pressure controls size" -> "压力控制大小"
        "Uses the imported CSP curve when available." -> "可用时采用导入的 CSP 曲线。"
        "Minimum size" -> "最小大小"
        "Size pressure" -> "大小压力"
        "Pressure controls opacity" -> "压力控制不透明度"
        "Minimum opacity" -> "最小不透明度"
        "Opacity pressure" -> "不透明度压力"
        "Opacity" -> "不透明度"
        "Pressure controls flow" -> "压力控制流量"
        "Minimum flow" -> "最小流量"
        "Flow pressure" -> "流量压力"
        "Flow" -> "流量"
        "Pressure controls spacing" -> "压力控制间距"
        "Imported from CSP Interval dynamics when available." -> "可用时从 CSP 间隔动态导入。"
        "Minimum spacing" -> "最小间距"
        "Spacing pressure" -> "间距压力"
        "Spacing" -> "间距"
        "Pressure controls thickness" -> "压力控制厚度"
        "Minimum thickness" -> "最小厚度"
        "Thickness pressure" -> "厚度压力"
        "Thickness" -> "厚度"
        "Brush blur" -> "笔刷模糊"
        "Pressure controls blur" -> "压力控制模糊"
        "Minimum blur" -> "最小模糊"
        "Blur pressure" -> "模糊压力"
        "Blur" -> "模糊"
        "Pressure controls particle size" -> "压力控制粒子大小"
        "Minimum particle size" -> "最小粒子大小"
        "Particle size pressure" -> "粒子大小压力"
        "Pressure controls particle density" -> "压力控制粒子密度"
        "Minimum particle density" -> "最小粒子密度"
        "Particle density pressure" -> "粒子密度压力"
        "Link blur to brush size" -> "模糊跟随笔刷大小"
        "Scales the imported CSP blur when brush size changes." -> "笔刷大小变化时同比缩放导入的 CSP 模糊。"
        "Starting taper" -> "起笔收尖"
        "Gradually grows from the configured minimum over the start of the stroke." -> "笔画起始处从设定的最小值逐渐增粗。"
        "Start length" -> "起笔长度"
        "Start minimum size" -> "起笔最小大小"
        "Fade" -> "淡出"
        "Ending taper" -> "收笔收尖"
        "Fades from full size toward the configured minimum." -> "从完整大小逐渐淡出到设定的最小值。"
        "Gradually narrows toward the configured minimum at stroke end." -> "笔画末端逐渐收窄到设定的最小值。"
        "Fade length" -> "淡出长度"
        "End length" -> "收笔长度"
        "End minimum size" -> "收笔最小大小"
        "Hardness" -> "硬度"
        "Anti-alias" -> "抗锯齿"
        "Secondary anti-alias" -> "辅助笔尖抗锯齿"
        "Paper texture density" -> "纸张纹理密度"
        "Paper texture scale" -> "纸张纹理缩放"
        "Paper texture rotation" -> "纸张纹理旋转"
        "Paper texture brightness" -> "纸张纹理亮度"
        "Paper texture contrast" -> "纸张纹理对比度"
        "Invert paper texture" -> "反转纸张纹理"
        "Emphasize paper texture" -> "增强纸张纹理"
        "Jitter grain origin per stroke" -> "每笔抖动纹理起点"
        "Keeps each stroke deterministic while avoiding an identical repeated grain start." -> "保持每笔结果稳定，同时避免纹理总从相同位置开始。"
        "Vertical thickness" -> "垂直厚度"
        "Applies thickness along the imported tip's vertical axis." -> "沿导入笔尖的垂直轴应用厚度。"
        "Ribbon stroke" -> "带状笔触"
        "Preserves the tip aspect ratio and continuously follows the stroke direction." -> "保持笔尖宽高比并持续跟随笔画方向。"
        "Rotation" -> "旋转"
        "Follow stylus orientation" -> "跟随触控笔方向"
        "Uses the pen direction reported by Android." -> "使用 Android 报告的触控笔方向。"
        "Follow stroke direction" -> "跟随笔画方向"
        "Rotates the tip along the drawn path." -> "让笔尖沿绘制路径旋转。"
        "Random rotation" -> "随机旋转"
        "Random start rotation" -> "随机起始旋转"
        "Stamp count" -> "笔印数量"
        "Stamp count jitter" -> "笔印数量抖动"
        "Color mix" -> "颜色混合"
        "Pressure controls color mix" -> "压力控制颜色混合"
        "Minimum color mix" -> "最小颜色混合"
        "Color mix pressure" -> "颜色混合压力"
        "Paint mix" -> "颜料混合"
        "Pressure controls paint load" -> "压力控制颜料量"
        "Minimum paint load" -> "最小颜料量"
        "Paint load pressure" -> "颜料量压力"
        "Paint load" -> "颜料量"
        "Watercolor approximation" -> "水彩近似"
        "Uses translucent layered stamps." -> "使用半透明的分层笔印。"
        "Watercolor edge" -> "水彩边缘"
        "Adds the CSP-style translucent edge pass before the main stamp." -> "在主笔印前添加 CSP 风格的半透明边缘。"
        "Edge radius" -> "边缘半径"
        "Edge opacity" -> "边缘不透明度"
        "Edge darkness" -> "边缘深度"
        "Edge blur" -> "边缘模糊"
        "Spray/scatter" -> "喷枪／散布"
        "Particle size" -> "粒子大小"
        "Particle size follows brush" -> "粒子大小跟随笔刷"
        "When enabled, particle size is a percentage of the current brush size." -> "开启后，粒子大小按当前笔刷大小的百分比计算。"
        "Particle density" -> "粒子密度"
        "Spray center bias" -> "喷枪中心偏移"
        "Scatter spread" -> "散布范围"
        "Particle rotation" -> "粒子旋转"
        "Particles follow stylus" -> "粒子跟随触控笔"
        "Uses the independent CSP particle-direction setting." -> "使用独立的 CSP 粒子方向设置。"
        "Particles follow stroke" -> "粒子跟随笔画"
        "Aligns particles with the local path direction." -> "让粒子与局部路径方向对齐。"
        "Particle random rotation" -> "粒子随机旋转"
        "Dual brush" -> "双重笔刷"
        "Uses the imported secondary tip as an independent stamp stream." -> "将导入的辅助笔尖作为独立笔印流。"
        "Secondary size" -> "辅助笔刷大小"
        "Secondary spacing" -> "辅助笔刷间距"
        "Secondary flow" -> "辅助笔刷流量"
        "Secondary hardness" -> "辅助笔刷硬度"
        "Secondary thickness" -> "辅助笔刷厚度"
        "Secondary vertical thickness" -> "辅助笔刷垂直厚度"
        "Secondary rotation" -> "辅助笔刷旋转"
        "Secondary follows stylus" -> "辅助笔刷跟随触控笔"
        "Secondary follows stroke" -> "辅助笔刷跟随笔画"
        "Secondary random rotation" -> "辅助笔刷随机旋转"
        "Secondary random start rotation" -> "辅助笔刷随机起始旋转"
        "Secondary stamp count" -> "辅助笔印数量"
        "Secondary count jitter" -> "辅助笔印数量抖动"
        "Secondary Ribbon" -> "辅助带状笔触"
        "Secondary spray/scatter" -> "辅助喷枪／散布"
        "Secondary particle size" -> "辅助粒子大小"
        "Secondary particles follow brush" -> "辅助粒子大小跟随笔刷"
        "Secondary particle density" -> "辅助粒子密度"
        "Secondary center bias" -> "辅助中心偏移"
        "Secondary scatter spread" -> "辅助散布范围"
        "Secondary particle rotation" -> "辅助粒子旋转"
        "Secondary particles follow stylus" -> "辅助粒子跟随触控笔"
        "Secondary particles follow stroke" -> "辅助粒子跟随笔画"
        "Secondary particle random rotation" -> "辅助粒子随机旋转"
        "Tilt" -> "倾斜"
        "Tilt minimum" -> "最小倾斜响应"
        "Velocity" -> "速度"
        "Velocity minimum" -> "最小速度响应"
        "Velocity response" -> "速度响应"
        "Random" -> "随机"
        "Deterministic per stamp, so export matches the canvas." -> "每个笔印都可重复计算，使导出结果与画布一致。"
        "Random minimum" -> "最小随机响应"
        "Text size" -> "文本大小"
        "Line spacing" -> "行距"
        "Collapsed" -> "折叠"
        "Show only the first line." -> "只显示第一行。"
        "Stroke width" -> "描边宽度"
        "Fill" -> "填充"
        "Adds a translucent shape fill." -> "添加半透明形状填充。"
        "Corner radius" -> "圆角半径"
        "Left" -> "左侧"
        "Top" -> "顶部"
        "Right" -> "右侧"
        "Bottom" -> "底部"
        "Width" -> "宽度"
        "Height" -> "高度"
        else -> raw
    }
}

@Composable
fun InkloomApp(initialIntent: Intent?) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.LIBRARY) }
    var importError by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    val viewModel: InkloomViewModel = viewModel()
    val libraryState by viewModel.state.collectAsState()
    val pageTemplates by viewModel.pageTemplates.collectAsState()
    val repository = remember { BrushImportRepository(context) }
    var pendingBrushExport by remember { mutableStateOf<ImportedBrush?>(null) }
    val brushStore = remember { BrushMetadataStore(context) }
    val brushGroupStore = remember { BrushGroupStore(context) }
    val settingsStore = remember { AppSettingsStore(context) }
    val colorPaletteStore = remember { ColorPaletteStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    // The persisted setting is the single source of truth. Keeping a second mutable flag here
    // allowed workspace changes and the toolbar click to observe different open states in the
    // same frame, so a click could close an invisible panel instead of opening Brushes.
    val brushesOpen = settings.showBrushPanel
    fun updateSettings(value: AppSettings) { settings = settingsStore.save(value) }
    var importedBrushes by remember { mutableStateOf(brushStore.load()) }
    var brushGroups by remember { mutableStateOf(BrushGroupOperations.reconcileRegisteredGroups(brushGroupStore.load(), importedBrushes)) }
    var pendingBrushImportGroup by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val refreshed = withContext(Dispatchers.IO) {
            repository.refreshMetadataLibrary(importedBrushes)
                .filter { brush -> brush.compatibility != Compatibility.FAILED && brush.v5PresetJson != null }
        }.distinctBy(ImportedBrush::id)
        if (refreshed != importedBrushes) { importedBrushes = refreshed; brushStore.save(refreshed) }
        val reconciledGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, refreshed)
        if (reconciledGroups != brushGroups) { brushGroups = reconciledGroups; brushGroupStore.save(reconciledGroups) }
    }

    val brushPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val targetGroup = pendingBrushImportGroup
        pendingBrushImportGroup = null
        importScope.launch {
            val results = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    repository.importDetailed(uri)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                val batches = results.mapNotNull { it.getOrNull() }
                val additions = batches.flatMap { it.renderableBrushes }.let { brushes ->
                    targetGroup?.let { group ->
                        brushes.map { brush ->
                            val nestedGroup = BrushGroupOperations.nest(group, brush.groupName)
                            BrushGroupOperations.assign(brush, nestedGroup)
                        }
                    } ?: brushes
                }
                val existingGroupKeys = brushGroups.map { it.trim().lowercase() }.toSet()
                val groupsCreated = additions.map(ImportedBrush::groupName).map(String::trim)
                    .filter(String::isNotEmpty).distinctBy(String::lowercase)
                    .count { it.lowercase() !in existingGroupKeys }
                importedBrushes = BrushGroupOperations.mergeImports(importedBrushes, additions)
                brushStore.save(importedBrushes)
                brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, importedBrushes)
                brushGroupStore.save(brushGroups)
                val importLedger = BrushImportLedger.combine(batches.map(BrushImportBatch::ledger))
                    .withGroupsCreated(groupsCreated)
                val importSummary = importLedger.summary
                val fileFailures = results.count { it.isFailure }
                val summary = settings.language.text(
                    "Source files: ${importSummary.sourceFilesScanned}; containers: ${importSummary.sourceContainers}; " +
                        "brushes discovered: ${importSummary.brushesDiscovered}; parsed: ${importSummary.parsed}; " +
                        "mapped: ${importSummary.mapped}; renderable: ${importSummary.renderable}; " +
                        "imported usable: ${importSummary.importedUsable}; rejected: ${importSummary.rejectedUnsupported}; " +
                        "failed: ${importSummary.failed}; groups discovered: ${importSummary.groupsDiscovered}; " +
                        "groups created: ${importSummary.groupsCreated}; file errors: $fileFailures.",
                    "源文件：${importSummary.sourceFilesScanned}；容器：${importSummary.sourceContainers}；" +
                        "发现笔刷：${importSummary.brushesDiscovered}；已解析：${importSummary.parsed}；" +
                        "已映射：${importSummary.mapped}；可渲染：${importSummary.renderable}；" +
                        "已导入可用：${importSummary.importedUsable}；已拒绝：${importSummary.rejectedUnsupported}；" +
                        "失败：${importSummary.failed}；发现分组：${importSummary.groupsDiscovered}；" +
                        "创建分组：${importSummary.groupsCreated}；文件错误：$fileFailures。",
                )
                if (importSummary.rejectedUnsupported > 0 || importSummary.failed > 0 || fileFailures > 0) {
                    val reasons = (importLedger.records.flatMap { record -> record.rejectionReasons } +
                        results.mapNotNull { it.exceptionOrNull()?.message })
                        .distinct().take(5)
                    importError = listOf(summary, reasons.joinToString("\n")).filter(String::isNotBlank).joinToString("\n\n")
                } else {
                    Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: settings.language.text("Imported document", "导入的文档")
            when {
                name.endsWith(".inkloom.zip", true) || name.endsWith(".inkloom", true) -> viewModel.importBundle(uri)
                name.endsWith(".pdf", true) -> viewModel.importPdf(uri, name)
                else -> viewModel.importImage(uri, name)
            }
            destination = AppDestination.EDITOR
        }.onFailure { importError = it.message }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) runCatching { viewModel.exportCurrent(uri) }.onFailure { importError = it.message }
    }
    val imageExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri -> if (uri != null) runCatching { viewModel.exportCurrentPage(uri) }.onFailure { importError = it.message } }
    val bundleExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) runCatching { viewModel.exportCurrentBundle(uri) }.onFailure { importError = it.message } }
    val libraryBackupExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val preferences = runCatching { AppSettingsBackupCodec.encode(settings, colorPaletteStore.load()) }
                .getOrElse { error -> importError = error.message; return@rememberLauncherForActivityResult }
            viewModel.exportLibraryBackup(uri, importedBrushes, brushGroups, preferences) { result ->
                result.onSuccess { Toast.makeText(context, settings.language.text("Library backup saved.", "资料库备份已保存。"), Toast.LENGTH_LONG).show() }
                    .onFailure { importError = it.message }
            }
        }
    }
    val libraryBackupImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreLibraryBackup(uri) { result ->
            result.onSuccess { restored ->
                importedBrushes = (importedBrushes + restored.brushes).distinctBy(ImportedBrush::id)
                brushStore.save(importedBrushes)
                brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups + restored.brushGroups, importedBrushes)
                brushGroupStore.save(brushGroups)
                restored.preferences?.let { bytes ->
                    AppSettingsBackupCodec.decode(bytes).onSuccess { preferences ->
                        val restoredSettings = preferences.settings.copy(
                            toolboxQuickSlots = ToolboxQuickSlots.remapBrushIds(preferences.settings.toolboxQuickSlots, restored.brushIdMappings),
                        )
                        updateSettings(restoredSettings)
                        colorPaletteStore.replace(preferences.colorPalette)
                    }.onFailure { error ->
                        importError = settings.language.text(
                            "Notes and brushes were restored, but app settings were ignored: ${error.message}",
                            "笔记和笔刷已恢复，但应用设置因损坏而被忽略：${error.message}",
                        )
                    }
                }
                Toast.makeText(
                    context,
                    settings.language.text(
                        "Restored ${restored.documents.size} notes and ${restored.brushes.size} brushes.",
                        "已恢复 ${restored.documents.size} 份笔记和 ${restored.brushes.size} 支笔刷。",
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { importError = it.message }
        }
    }
    val shortcutProfileExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ShortcutProfileCodec.MIME_TYPE)) { uri ->
        if (uri != null) runCatching {
            val profile = ShortcutProfile(settings.shortcutBindings, settings.shortcutBrushGroupTargets, settings.toolboxQuickSlots, settings.shortcutAxisBindings, settings.shortcutButtonBindings)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output -> output.write(ShortcutProfileCodec.encode(profile)) }
                ?: error("Unable to open the shortcut profile destination.")
            Toast.makeText(context, settings.language.text("Shortcut profile saved.", "快捷键配置已保存。"), Toast.LENGTH_LONG).show()
        }.onFailure { importError = it.message }
    }
    val shortcutProfileImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val imported = context.contentResolver.openInputStream(uri)?.use { ShortcutProfileCodec.decodeProfile(it).getOrThrow() }
                ?: error("Unable to open the shortcut profile.")
            updateSettings(settings.copy(shortcutBindings = imported.bindings, shortcutAxisBindings = imported.axisBindings, shortcutButtonBindings = imported.buttonBindings, shortcutBrushGroupTargets = imported.brushGroupTargets, toolboxQuickSlots = imported.quickSlots))
            Toast.makeText(context, settings.language.text("Shortcut profile restored.", "快捷键配置已恢复。"), Toast.LENGTH_LONG).show()
        }.onFailure { importError = it.message }
    }
    val brushExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.inkloom.brush+zip")) { uri ->
        val brush = pendingBrushExport
        pendingBrushExport = null
        if (uri != null && brush != null) repository.export(brush, uri).onFailure { importError = it.message }
    }
    val pageImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.insertImage(uri)
        }.onFailure { importError = it.message }
    }
    var pendingPageScan by remember { mutableStateOf<Pair<Uri, java.io.File>?>(null) }
    val pageScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val pending = pendingPageScan
        pendingPageScan = null
        if (captured && pending != null) runCatching { viewModel.insertImage(pending.first) }.onFailure { importError = it.message }
        pending?.second?.delete()
    }
    fun launchPageScanner() {
        runCatching {
            val directory = java.io.File(context.cacheDir, "page-scans").apply { mkdirs() }
            val file = java.io.File.createTempFile("scan-", ".jpg", directory)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            pendingPageScan = uri to file
            pageScanLauncher.launch(uri)
        }.onFailure { importError = it.message }
    }
    val pageTemplatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: settings.language.text("Imported template", "导入的模板")
            val imported = viewModel.importPageTemplates(uri, name)
            Toast.makeText(
                context,
                settings.language.text("Imported ${imported.size} page template(s).", "已导入 ${imported.size} 个页面模板。"),
                Toast.LENGTH_SHORT,
            ).show()
        }.onFailure { importError = it.message }
    }
    var deletionFailed by remember { mutableStateOf(false) }
    var pendingCoverTitle by remember { mutableStateOf("Untitled Note") }
    var pendingCoverTemplateId by remember { mutableStateOf(PageTemplateCatalog.blank.id) }
    var pendingCoverPaperColor by remember { mutableLongStateOf(PaperColorCatalog.WHITE) }
    val coverImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.createNoteWithCover(uri, pendingCoverTitle, pendingCoverTemplateId, pendingCoverPaperColor)
            destination = AppDestination.EDITOR
        }.onFailure { importError = it.message }
    }

    LaunchedEffect(initialIntent) {
        val uris = buildList<Uri> {
            initialIntent?.data?.let(::add)
            initialIntent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
        }
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val type = context.contentResolver.getType(uri).orEmpty()
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
                when {
                    name.endsWith(".inkloom.zip", true) || name.endsWith(".inkloom", true) -> viewModel.importBundle(uri)
                    type == "application/pdf" || name.endsWith(".pdf", true) -> viewModel.importPdf(uri, name)
                    type.startsWith("image/") -> viewModel.importImage(uri, name)
                    else -> repository.importAll(uri).getOrNull()?.let { brushes -> importedBrushes = BrushGroupOperations.mergeImports(importedBrushes, brushes) }
                }
            }
            brushStore.save(importedBrushes)
            brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, importedBrushes)
            brushGroupStore.save(brushGroups)
            updateSettings(settings.copy(showBrushPanel = true))
            destination = AppDestination.EDITOR
        }
    }

    CompositionLocalProvider(LocalUiLanguage provides settings.language) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
    if (libraryState.migratingLegacyFills) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(tr("Converting old fills to editable paint…", "正在将旧填充转换为可编辑的绘画内容…"))
            }
        }
    } else when (destination) {
        AppDestination.LIBRARY -> LibraryScreen(
            documents = libraryState.documents,
            folders = libraryState.folders,
            pageTemplates = pageTemplates,
            layout = settings.libraryLayout,
            onLayout = { updateSettings(settings.copy(libraryLayout = it)) },
            onOpen = { viewModel.select(it.id); destination = AppDestination.EDITOR },
            onNewNote = { title, style, templateId, paperColor -> viewModel.createNote(title, style, templateId, paperColor); destination = AppDestination.EDITOR },
            onImportCover = { title, templateId, paperColor ->
                pendingCoverTitle = title
                pendingCoverTemplateId = templateId
                pendingCoverPaperColor = paperColor
                coverImagePicker.launch(arrayOf("image/*"))
            },
            onImport = { documentPicker.launch(arrayOf("application/pdf", "image/*", "application/zip")) },
            onFavorite = viewModel::toggleFavorite,
            onArchive = viewModel::toggleArchive,
            onTags = viewModel::setDocumentTags,
            onTrash = viewModel::trash,
            onRestore = viewModel::restore,
            onDelete = { documentId -> deletionFailed = !viewModel.deletePermanently(documentId) },
            onRename = viewModel::rename,
            onDuplicate = viewModel::duplicateDocument,
            onCreateFolder = viewModel::createFolder,
            onRenameFolder = viewModel::renameFolder,
            onMoveToFolder = viewModel::moveToFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onSettings = { settingsOpen = true },
        )
        AppDestination.EDITOR -> EditorScreen(
            document = libraryState.documents.firstOrNull { it.id == libraryState.selectedDocumentId } ?: viewModel.currentDocument(),
            documents = libraryState.documents,
            selectedPageId = libraryState.selectedPageId,
            brushesOpen = brushesOpen,
            brushes = importedBrushes,
            brushGroups = brushGroups,
            importError = importError,
            onBack = { destination = AppDestination.LIBRARY },
            onToggleBrushes = {
                updateSettings(settings.copy(showBrushPanel = !settings.showBrushPanel))
            },
            onImportBrushes = { group ->
                pendingBrushImportGroup = group?.let(BrushGroupOperations::normalize)?.takeIf(String::isNotBlank)
                brushPicker.launch(arrayOf("application/octet-stream", "application/zip", "application/vnd.inkloom.brush+zip", "image/*"))
            },
            onExportBrush = { brush ->
                pendingBrushExport = brush
                val safeName = brush.name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { "Inkloom-Brush" }
                brushExportPicker.launch("$safeName.inkbrush")
            },
            onUpdateBrush = { updated ->
                importedBrushes = importedBrushes.map { if (it.id == updated.id) updated else it }
                brushStore.save(importedBrushes)
                brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, importedBrushes)
                brushGroupStore.save(brushGroups)
            },
            onReplaceBrushes = { updated ->
                importedBrushes = updated
                brushStore.save(updated)
                brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, updated)
                brushGroupStore.save(brushGroups)
            },
            onReplaceBrushGroups = { updated -> brushGroups = updated; brushGroupStore.save(updated) },
            onDeleteBrush = { brushId -> importedBrushes = importedBrushes.filterNot { it.id == brushId }; brushStore.save(importedBrushes) },
            onReorderBrush = { brushId, delta -> importedBrushes = BrushGroupOperations.move(importedBrushes, brushId, delta); brushStore.save(importedBrushes) },
            onDismissError = { importError = null },
            onSelectPage = viewModel::selectPage,
            pageTemplates = pageTemplates,
            onAppendCurrentPage = { pageId -> libraryState.selectedDocumentId?.let { viewModel.appendPageUsingTemplate(it, pageId) } },
            onAddPage = { templateId, paperColor -> libraryState.selectedDocumentId?.let { viewModel.addPage(it, templateId, paperColor) } },
            onDuplicatePage = { documentId, pageId -> viewModel.duplicatePage(documentId, pageId) },
            onDeletePage = { documentId, pageId -> viewModel.deletePage(documentId, pageId) },
            onMovePage = viewModel::movePage,
            onReorderPages = viewModel::reorderPages,
            onTransferPage = viewModel::transferPage,
            onApplyPageTemplate = viewModel::applyPageTemplate,
            onApplyPageTemplates = viewModel::applyPageTemplateToPages,
            onImportPageTemplates = { pageTemplatePicker.launch(arrayOf("application/pdf", "image/*")) },
            onTogglePageTemplateFavorite = viewModel::togglePageTemplateFavorite,
            onSetNotebookDefaultTemplate = viewModel::setNotebookDefaultTemplate,
            onSavePageAsTemplate = { documentId, pageId, name -> viewModel.savePageAsTemplate(documentId, pageId, name) },
            onDeletePageTemplate = viewModel::deletePageTemplate,
            onToggleBookmark = viewModel::toggleBookmark,
            onChangePageOutlineLevel = viewModel::changePageOutlineLevel,
            onTogglePageOutlineCollapsed = viewModel::togglePageOutlineCollapsed,
            onRenamePage = viewModel::renamePage,
            onSetPageMetadata = viewModel::setPageMetadata,
            onClearPageInk = viewModel::clearPageInk,
            onSetPageCrop = viewModel::setPageCrop,
            onRotatePage = viewModel::rotatePageClockwise,
            onAddLayer = viewModel::addLayer,
            onAddLayerFolder = viewModel::addLayerFolder,
            onRenameLayer = viewModel::renameLayer,
            onToggleLayerVisibility = viewModel::toggleLayerVisibility,
            onToggleLayerLock = viewModel::toggleLayerLock,
            onSetLayerOpacity = viewModel::setLayerOpacity,
            onSetLayerBlendMode = viewModel::setLayerBlendMode,
            onSetLayerRasterTileSurface = viewModel::setLayerRasterTileSurface,
            onToggleLayerAlphaLock = viewModel::toggleLayerAlphaLock,
            onToggleLayerClippedToBelow = viewModel::toggleLayerClippedToBelow,
            onToggleLayerReference = viewModel::toggleLayerReference,
            onToggleLayerDraft = viewModel::toggleLayerDraft,
            onLayerMaskCommand = viewModel::updateLayerMask,
            onMoveLayerToFolder = viewModel::moveLayerToFolder,
            onToggleLayerFolderCollapsed = viewModel::toggleLayerFolderCollapsed,
            onMoveLayer = viewModel::moveLayer,
            onDuplicateLayer = viewModel::duplicateLayer,
            onMergeLayerDown = viewModel::mergeLayerDown,
            onDeleteLayer = viewModel::deleteLayer,
            onElement = viewModel::addElement,
            onRemoveElement = viewModel::removeElement,
            onDuplicateElement = viewModel::duplicateElement,
            onInsertImage = { pageImagePicker.launch(arrayOf("image/*")) },
            onScanImage = ::launchPageScanner,
            onExport = { exportPicker.launch("${viewModel.currentDocument()?.title ?: "Inkloom"}.pdf") },
            onExportPage = { imageExportPicker.launch("${viewModel.currentDocument()?.title ?: "Inkloom"}-page.png") },
            onExportBundle = { bundleExportPicker.launch("${viewModel.currentDocument()?.title ?: "Inkloom"}.inkloom.zip") },
            assets = viewModel.assets,
            selectionRuntime = viewModel.selectionRuntime,
            settings = settings,
            pdfIndexing = libraryState.selectedDocumentId in libraryState.pdfIndexingDocumentIds,
            onIndexPdfText = viewModel::indexPdfText,
            imageOcrRecognizing = libraryState.selectedPageId in libraryState.imageOcrPageIds,
            onRecognizeImages = viewModel::recognizePageImages,
            documentRecognizing = libraryState.selectedDocumentId in libraryState.textRecognitionDocumentIds,
            onRecognizeDocument = viewModel::recognizeDocumentText,
            onConvertHandwriting = viewModel::convertRecognizedHandwritingToText,
            onOpenInternalLink = { documentId, pageId -> viewModel.select(documentId); viewModel.selectPage(pageId) },
            onSettings = { settingsOpen = true },
            onSettingsChange = ::updateSettings,
        )
    }
    }
    libraryState.legacyFillMigrationError?.let { reason ->
        AlertDialog(onDismissRequest = viewModel::dismissLegacyFillMigrationError,
            title = { Text(tr("Some old fills could not be converted", "部分旧填充暂时无法转换")) },
            text = { Text(tr("Your original notebook has been preserved.\n\n", "原笔记内容已保留。\n\n") + reason) },
            confirmButton = { TextButton(onClick = viewModel::dismissLegacyFillMigrationError) { Text(tr("OK", "确定")) } })
    }
    if (deletionFailed) AlertDialog(
        onDismissRequest = { deletionFailed = false },
        title = { Text(tr("Couldn’t delete notebook", "无法删除笔记")) },
        text = { Text(tr("The notebook is still in Trash. Your pages and saved colors have been kept. Please try again.", "笔记仍在废纸篓中，页面和已保存的颜色均已保留。请重试。")) },
        confirmButton = { TextButton(onClick = { deletionFailed = false }) { Text(tr("OK", "确定")) } },
    )
    if (settingsOpen) SettingsDialog(
        settings,
        brushes = importedBrushes,
        brushGroups = brushGroups,
        onChange = ::updateSettings,
        onExportBackup = { libraryBackupExportPicker.launch("Inkloom-library-backup.inkloom-backup.zip") },
        onRestoreBackup = { libraryBackupImportPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
        onExportShortcuts = { shortcutProfileExportPicker.launch("Inkloom-toolbox.${ShortcutProfileCodec.FILE_EXTENSION}") },
        onImportShortcuts = { shortcutProfileImportPicker.launch(arrayOf(ShortcutProfileCodec.MIME_TYPE, "text/plain", "application/octet-stream")) },
        onDismiss = { settingsOpen = false },
    )
    }
}

@Composable
private fun LibraryScreen(documents: List<InkDocument>, folders: List<InkFolder>, pageTemplates: PageTemplateLibrary, layout: LibraryLayout, onLayout: (LibraryLayout) -> Unit, onOpen: (InkDocument) -> Unit, onNewNote: (String, CoverStyle, String, Long) -> Unit, onImportCover: (String, String, Long) -> Unit, onImport: () -> Unit, onFavorite: (String) -> Unit, onArchive: (String) -> Unit, onTags: (String, List<String>) -> Unit, onTrash: (String) -> Unit, onRestore: (String) -> Unit, onDelete: (String) -> Unit, onRename: (String, String) -> Unit, onDuplicate: (String) -> Unit, onCreateFolder: (String, String?) -> Unit, onRenameFolder: (String, String) -> Unit, onMoveToFolder: (String, String?) -> Unit, onDeleteFolder: (String) -> Unit, onSettings: () -> Unit) {
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var search by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf<String?>(null) }
    var newNoteOpen by remember { mutableStateOf(false) }
    var draggingDocumentId by remember { mutableStateOf<String?>(null) }
    var documentDragPosition by remember { mutableStateOf<Offset?>(null) }
    val documentCardBounds = remember { mutableStateMapOf<String, Rect>() }
    val folderDropBounds = remember { mutableStateMapOf<String, Rect>() }
    val libraryLanguage = LocalUiLanguage.current
    val untitledNoteLabel = tr("Untitled Note", "未命名笔记")
    var newNoteTitle by remember { mutableStateOf(untitledNoteLabel) }
    var newNoteCover by remember { mutableStateOf(CoverStyle.MINIMAL) }
    var newNoteTemplateId by remember { mutableStateOf(PageTemplateCatalog.blank.id) }
    var newNotePaperColor by remember { mutableLongStateOf(PaperColorCatalog.WHITE) }
    val visible = remember(documents, filter, folderId, search) {
        LibraryDocumentQuery.visible(documents, filter, folderId, search)
    }
    fun finishDocumentDrag() {
        val documentId = draggingDocumentId
        val position = documentDragPosition
        val targetFolderId = position?.let { point -> folderDropBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(point) }?.key }
        if (documentId != null && targetFolderId != null) onMoveToFolder(documentId, targetFolderId)
        draggingDocumentId = null
        documentDragPosition = null
    }
    fun Modifier.libraryDocumentDrag(document: InkDocument): Modifier = this
        .onGloballyPositioned { coordinates -> documentCardBounds[document.id] = coordinates.boundsInWindow() }
        .graphicsLayer { alpha = if (draggingDocumentId == document.id) .66f else 1f }
        .semantics {
            contentDescription = libraryLanguage.text(
                "Notebook ${document.title}. Long press to move.",
                "笔记本 ${document.title}。长按移动。",
            )
        }
        .pointerInput(document.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    draggingDocumentId = document.id
                    documentDragPosition = documentCardBounds[document.id]?.topLeft?.plus(local)
                },
                onDrag = { change, _ ->
                    change.consume()
                    documentDragPosition = documentCardBounds[document.id]?.topLeft?.plus(change.position)
                },
                onDragEnd = ::finishDocumentDrag,
                onDragCancel = {
                    draggingDocumentId = null
                    documentDragPosition = null
                },
            )
        }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val compact = maxWidth < 700.dp
        Row(Modifier.fillMaxSize()) {
            if (!compact) {
        LibraryRail(
            filter,
            { filter = it; folderId = null },
            folders,
            folderId,
            { folderId = it; filter = LibraryFilter.ALL },
            onCreateFolder,
            onRenameFolder,
            onDeleteFolder,
            onSettings,
            draggingDocument = draggingDocumentId != null,
            dragPosition = documentDragPosition,
            onFolderBounds = { id, bounds -> folderDropBounds[id] = bounds },
        )
                VerticalDivider(color = OutlineSoft)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (compact) 16.dp else 32.dp, vertical = 22.dp)) {
            if (compact) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filter == LibraryFilter.ALL, onClick = { filter = LibraryFilter.ALL }, label = { Text(tr("Library", "资料库")) }) }
                    item { FilterChip(selected = filter == LibraryFilter.RECENT, onClick = { filter = LibraryFilter.RECENT }, label = { Text(tr("Recents", "最近使用")) }) }
                    item { FilterChip(selected = filter == LibraryFilter.FAVORITES, onClick = { filter = LibraryFilter.FAVORITES }, label = { Text(tr("Favorites", "收藏")) }) }
                    item { FilterChip(selected = filter == LibraryFilter.ARCHIVED, onClick = { filter = LibraryFilter.ARCHIVED }, label = { Text(tr("Archive", "归档")) }) }
                    item { FilterChip(selected = filter == LibraryFilter.TRASH, onClick = { filter = LibraryFilter.TRASH }, label = { Text(tr("Trash", "废纸篓")) }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Your notebooks", "你的笔记本"), fontSize = 32.sp, color = Ink)
                    Text(tr("Continue a note or start with a fresh page.", "继续书写，或从空白页开始。"), color = MutedInk)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, tr("Settings", "设置")) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = onImport, shape = RoundedCornerShape(50)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(tr("Import", "导入")) }
                Spacer(Modifier.width(10.dp))
                Button(onClick = { newNoteOpen = true }, shape = RoundedCornerShape(50)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("New Note", "新建笔记"))
                }
            }
            Spacer(Modifier.height(26.dp))
            OutlinedTextField(value = search, onValueChange = { search = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(tr("Search notes", "搜索笔记")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (filter) {
                    LibraryFilter.ALL -> tr("All Notes", "全部笔记")
                    LibraryFilter.RECENT -> tr("Recents", "最近使用")
                    LibraryFilter.FAVORITES -> tr("Favorites", "收藏")
                    LibraryFilter.ARCHIVED -> tr("Archive", "归档")
                    LibraryFilter.TRASH -> tr("Trash", "废纸篓")
                    LibraryFilter.PDF -> "PDF"
                }, fontSize = 22.sp, modifier = Modifier.weight(1f))
                FilterChip(selected = filter == LibraryFilter.ALL, onClick = { filter = LibraryFilter.ALL }, label = { Text(tr("All", "全部")) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = filter == LibraryFilter.FAVORITES, onClick = { filter = LibraryFilter.FAVORITES }, label = { Text(tr("Favorites", "收藏")) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = filter == LibraryFilter.PDF, onClick = { filter = LibraryFilter.PDF }, label = { Text("PDF") })
                Spacer(Modifier.width(12.dp))
                listOf(
                    Triple(LibraryLayout.GRID, Icons.Outlined.GridView, tr("Grid layout", "网格布局")),
                    Triple(LibraryLayout.LIST, Icons.Outlined.ViewList, tr("List layout", "列表布局")),
                    Triple(LibraryLayout.COMPACT_GRID, Icons.Outlined.DensitySmall, tr("Compact grid", "紧凑网格")),
                ).forEach { (candidate, icon, description) ->
                    IconButton(
                        onClick = { onLayout(candidate) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (layout == candidate) Workspace else Color.Transparent, RoundedCornerShape(8.dp)),
                    ) { Icon(icon, description, Modifier.size(19.dp), tint = if (layout == candidate) Sage else Ink) }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (layout) {
                LibraryLayout.LIST -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visible, key = { it.id }) { NotebookCard(it, Modifier.libraryDocumentDrag(it).fillMaxWidth().height(220.dp), onOpen, onFavorite, onArchive, onTags, onTrash, onRestore, onDelete, onRename, onDuplicate, folders, onMoveToFolder) }
                }
                LibraryLayout.GRID, LibraryLayout.COMPACT_GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (layout == LibraryLayout.COMPACT_GRID) 140.dp else 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (layout == LibraryLayout.COMPACT_GRID) 10.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (layout == LibraryLayout.COMPACT_GRID) 10.dp else 16.dp),
                ) {
                    items(visible, key = { it.id }) {
                        NotebookCard(it, Modifier.libraryDocumentDrag(it).height(if (layout == LibraryLayout.COMPACT_GRID) 210.dp else 250.dp), onOpen, onFavorite, onArchive, onTags, onTrash, onRestore, onDelete, onRename, onDuplicate, folders, onMoveToFolder)
                    }
                }
            }
        }
        }
    }
    if (newNoteOpen) AlertDialog(
        onDismissRequest = { newNoteOpen = false },
        title = { Text(tr("Create notebook", "新建笔记本")) },
        text = {
            Column(Modifier.widthIn(min = 380.dp, max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(newNoteTitle, { newNoteTitle = it.take(120) }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(tr("Cover template", "封面模板"), color = MutedInk, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(NotebookCoverCatalog.creationStyles) { style ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Card(
                                Modifier.size(92.dp, 120.dp).clickable { newNoteCover = style },
                                border = if (newNoteCover == style) androidx.compose.foundation.BorderStroke(2.dp, Ink) else CardDefaults.outlinedCardBorder(),
                            ) { NotebookCover(InkDocument(title = newNoteTitle, coverStyle = style, coverArgb = NotebookCoverCatalog.defaultArgb(style)), Modifier.fillMaxSize()) }
                            Spacer(Modifier.height(4.dp))
                            Text(NotebookCoverCatalog.displayName(style, LocalUiLanguage.current), fontSize = 9.sp)
                        }
                    }
                }
                Text(tr("Page template", "页面模板"), color = MutedInk, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pageTemplates.allTemplates(), key = { it.id }) { template ->
                        val selected = template.id == newNoteTemplateId
                        val name = pageTemplateName(template)
                        val templateDescription = tr("New note template $name", "新笔记模板 $name")
                        Column(
                            Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(if (selected) 2.dp else 1.dp, if (selected) Ink else OutlineSoft, RoundedCornerShape(8.dp))
                                .clickable { newNoteTemplateId = template.id }
                                .semantics { contentDescription = templateDescription }
                                .padding(7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PageTemplatePreview(PageTemplateOperations.withPaperColor(template, newNotePaperColor), selected)
                            Spacer(Modifier.height(4.dp))
                            Text(name, fontSize = 9.sp, minLines = 2, maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                if (pageTemplates.template(newNoteTemplateId)?.background is PageBackground.Paper) {
                    Text(tr("Paper color", "纸张颜色"), color = MutedInk, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaperColorCatalog.options.forEach { option ->
                            val label = paperColorName(option)
                            val colorDescription = tr("New note paper color $label", "新笔记纸张颜色 $label")
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .background(Color(option.colorArgb), CircleShape)
                                    .border(if (newNotePaperColor == option.colorArgb) 2.dp else 1.dp, if (newNotePaperColor == option.colorArgb) Ink else OutlineSoft, CircleShape)
                                    .clickable { newNotePaperColor = option.colorArgb }
                                    .semantics { contentDescription = colorDescription },
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { newNoteOpen = false; onImportCover(newNoteTitle, newNoteTemplateId, newNotePaperColor) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Import image as cover", "导入图片作为封面"))
                }
            }
        },
        confirmButton = { Button(onClick = { newNoteOpen = false; onNewNote(newNoteTitle, newNoteCover, newNoteTemplateId, newNotePaperColor) }) { Text(tr("Create", "创建")) } },
        dismissButton = { TextButton(onClick = { newNoteOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun LibraryRail(filter: LibraryFilter, onFilter: (LibraryFilter) -> Unit, folders: List<InkFolder>, folderId: String?, onFolder: (String?) -> Unit, onCreateFolder: (String, String?) -> Unit, onRenameFolder: (String, String) -> Unit, onDeleteFolder: (String) -> Unit, onSettings: () -> Unit, draggingDocument: Boolean, dragPosition: Offset?, onFolderBounds: (String, Rect) -> Unit) {
    var newFolderOpen by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var folderMenuId by remember { mutableStateOf<String?>(null) }
    var renameFolderId by remember { mutableStateOf<String?>(null) }
    var renameFolderName by remember { mutableStateOf("") }
    val language = LocalUiLanguage.current
    Column(Modifier.width(198.dp).fillMaxHeight().padding(16.dp)) {
        BrandMark(tr("Library", "资料库"))
        Spacer(Modifier.height(34.dp))
        RailItem(Icons.Outlined.Book, tr("Library", "资料库"), filter == LibraryFilter.ALL) { onFilter(LibraryFilter.ALL) }
        RailItem(Icons.Outlined.History, tr("Recents", "最近使用"), filter == LibraryFilter.RECENT) { onFilter(LibraryFilter.RECENT) }
        RailItem(Icons.Outlined.StarBorder, tr("Favorites", "收藏"), filter == LibraryFilter.FAVORITES) { onFilter(LibraryFilter.FAVORITES) }
        RailItem(Icons.Outlined.Archive, tr("Archive", "归档"), filter == LibraryFilter.ARCHIVED) { onFilter(LibraryFilter.ARCHIVED) }
        RailItem(Icons.Outlined.Delete, tr("Trash", "废纸篓"), filter == LibraryFilter.TRASH) { onFilter(LibraryFilter.TRASH) }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = OutlineSoft)
        Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("FOLDERS", "文件夹"), fontSize = 11.sp, color = MutedInk, modifier = Modifier.weight(1f)); IconButton(onClick = { newFolderOpen = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Add, tr("New folder", "新建文件夹")) } }
        Spacer(Modifier.height(10.dp))
        folders.filter { it.parentId == null }.forEach { folder ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    var bounds by remember(folder.id) { mutableStateOf<Rect?>(null) }
                    val hovered = draggingDocument && dragPosition?.let { bounds?.contains(it) } == true
                    RailItem(
                        Icons.Outlined.Folder,
                        folder.name,
                        folderId == folder.id || hovered,
                        modifier = Modifier
                            .onGloballyPositioned { coordinates -> coordinates.boundsInWindow().also { measured -> bounds = measured; onFolderBounds(folder.id, measured) } }
                            .semantics { contentDescription = language.text("Folder ${folder.name}. Drop notebook here.", "文件夹 ${folder.name}。拖放笔记到这里。") },
                    ) { onFolder(folder.id) }
                }
                Box {
                    IconButton(onClick = { folderMenuId = folder.id }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.MoreVert, tr("Folder actions", "文件夹操作"), modifier = Modifier.size(14.dp)) }
                    DropdownMenu(expanded = folderMenuId == folder.id, onDismissRequest = { folderMenuId = null }) {
                        DropdownMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { folderMenuId = null; renameFolderId = folder.id; renameFolderName = folder.name })
                        DropdownMenuItem(text = { Text(tr("Delete folder", "删除文件夹")) }, onClick = { folderMenuId = null; onDeleteFolder(folder.id) })
                    }
                }
            }
            folders.filter { it.parentId == folder.id }.forEach { child ->
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        var bounds by remember(child.id) { mutableStateOf<Rect?>(null) }
                        val hovered = draggingDocument && dragPosition?.let { bounds?.contains(it) } == true
                        RailItem(
                            Icons.Outlined.FolderOpen,
                            child.name,
                            folderId == child.id || hovered,
                            modifier = Modifier
                                .onGloballyPositioned { coordinates -> coordinates.boundsInWindow().also { measured -> bounds = measured; onFolderBounds(child.id, measured) } }
                                .semantics { contentDescription = language.text("Folder ${child.name}. Drop notebook here.", "文件夹 ${child.name}。拖放笔记到这里。") },
                        ) { onFolder(child.id) }
                    }
                    Box {
                        IconButton(onClick = { folderMenuId = child.id }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.MoreVert, tr("Folder actions", "文件夹操作"), modifier = Modifier.size(14.dp)) }
                        DropdownMenu(expanded = folderMenuId == child.id, onDismissRequest = { folderMenuId = null }) {
                            DropdownMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { folderMenuId = null; renameFolderId = child.id; renameFolderName = child.name })
                            DropdownMenuItem(text = { Text(tr("Delete folder", "删除文件夹")) }, onClick = { folderMenuId = null; onDeleteFolder(child.id) })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        RailItem(Icons.Outlined.Settings, tr("Settings", "设置"), false, onClick = onSettings)
    }
    if (newFolderOpen) AlertDialog(onDismissRequest = { newFolderOpen = false }, title = { Text(tr("New folder", "新建文件夹")) }, text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onCreateFolder(folderName, folderId); folderName = ""; newFolderOpen = false }) { Text(tr("Create", "创建")) } }, dismissButton = { TextButton(onClick = { newFolderOpen = false }) { Text(tr("Cancel", "取消")) } })
    if (renameFolderId != null) AlertDialog(
        onDismissRequest = { renameFolderId = null },
        title = { Text(tr("Rename folder", "重命名文件夹")) },
        text = { OutlinedTextField(renameFolderName, { renameFolderName = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { renameFolderId?.let { onRenameFolder(it, renameFolderName) }; renameFolderId = null }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = { renameFolderId = null }) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Workspace else Color.Transparent).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) Sage else Ink); Spacer(Modifier.width(12.dp)); Text(label, color = if (selected) Sage else Ink)
    }
}

@Composable
private fun BrandMark(subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Sage, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, tint = Color.White) } }
        Spacer(Modifier.width(12.dp))
        Column { Text("Inkloom", color = Sage, fontSize = 25.sp); if (subtitle != null) Text(subtitle, color = MutedInk, fontSize = 11.sp) }
    }
}

@Composable
private fun NotebookCard(document: InkDocument, modifier: Modifier, onOpen: (InkDocument) -> Unit, onFavorite: (String) -> Unit, onArchive: (String) -> Unit, onTags: (String, List<String>) -> Unit, onTrash: (String) -> Unit, onRestore: (String) -> Unit, onDelete: (String) -> Unit, onRename: (String, String) -> Unit, onDuplicate: (String) -> Unit, folders: List<InkFolder>, onMoveToFolder: (String, String?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(false) }
    var title by remember(document.title) { mutableStateOf(document.title) }
    var tags by remember(document.tags) { mutableStateOf(document.tags.joinToString(", ")) }
    Card(modifier.clickable { onOpen(document) }, colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            NotebookCover(document, Modifier.fillMaxSize())
            if (document.pages.any { it.background is PageBackground.Pdf }) Surface(Modifier.padding(12.dp), shape = RoundedCornerShape(4.dp), color = Color.White) { Text("PDF", Modifier.padding(7.dp, 4.dp), fontSize = 10.sp) }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.padding(6.dp).size(36.dp).background(Color.Black.copy(alpha = .48f), CircleShape),
                ) { Icon(Icons.Default.MoreVert, tr("Document actions", "笔记操作"), tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (document.trashedAt == null) {
                        DropdownMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { menu = false; renameOpen = true })
                        DropdownMenuItem(text = { Text(tr("Edit tags", "编辑标签")) }, onClick = { menu = false; tagsOpen = true })
                        DropdownMenuItem(text = { Text(tr("Duplicate", "复制")) }, onClick = { menu = false; onDuplicate(document.id) })
                        folders.forEach { folder -> DropdownMenuItem(text = { Text("${tr("Move to", "移动到")} ${folder.name}") }, onClick = { menu = false; onMoveToFolder(document.id, folder.id) }) }
                        if (document.folderId != null) DropdownMenuItem(text = { Text(tr("Remove from folder", "移出文件夹")) }, onClick = { menu = false; onMoveToFolder(document.id, null) })
                        DropdownMenuItem(text = { Text(if (document.favorite) tr("Remove favorite", "取消收藏") else tr("Favorite", "收藏")) }, onClick = { menu = false; onFavorite(document.id) })
                        DropdownMenuItem(text = { Text(if (document.archivedAt == null) tr("Archive", "归档") else tr("Unarchive", "取消归档")) }, onClick = { menu = false; onArchive(document.id) })
                        DropdownMenuItem(text = { Text(tr("Move to Trash", "移到废纸篓")) }, onClick = { menu = false; onTrash(document.id) })
                    } else {
                        DropdownMenuItem(text = { Text(tr("Restore", "恢复")) }, onClick = { menu = false; onRestore(document.id) })
                        DropdownMenuItem(text = { Text(tr("Delete permanently", "永久删除")) }, onClick = { menu = false; onDelete(document.id) })
                    }
                }
            }
        }
        Column(Modifier.padding(14.dp)) {
            Row { Text(document.title, fontSize = 17.sp, maxLines = 2, modifier = Modifier.weight(1f)); if (document.favorite) Icon(Icons.Default.Star, null, tint = Sage, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(6.dp)); Text("${document.pages.size} ${tr("pages · saved locally", "页 · 已保存到本机")}", color = MutedInk, fontSize = 11.sp)
            if (document.tags.isNotEmpty()) Text(document.tags.take(3).joinToString("  ") { "#$it" }, color = Sage, fontSize = 10.sp, maxLines = 1)
        }
    }
    if (renameOpen) AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text(tr("Rename note", "重命名笔记")) }, text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onRename(document.id, title); renameOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(tr("Cancel", "取消")) } })
    if (tagsOpen) AlertDialog(onDismissRequest = { tagsOpen = false }, title = { Text(tr("Note tags", "笔记标签")) }, text = { OutlinedTextField(value = tags, onValueChange = { tags = it }, supportingText = { Text(tr("Separate tags with commas", "使用逗号分隔标签")) }) }, confirmButton = { TextButton(onClick = { onTags(document.id, tags.split(',')); tagsOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { tagsOpen = false }) { Text(tr("Cancel", "取消")) } })
}

@Composable
private fun NotebookCover(document: InkDocument, modifier: Modifier = Modifier) {
    val coverBitmap = remember(document.coverImagePath) { document.coverImagePath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() } }
    DisposableEffect(coverBitmap) { onDispose { coverBitmap?.recycle() } }
    val light = document.coverStyle in setOf(CoverStyle.MINIMAL, CoverStyle.PAPER, CoverStyle.GRID, CoverStyle.WHITE, CoverStyle.WHITE_GRID)
    Box(modifier.background(if (light) Color(0xFFF4F4F4) else Color(document.coverArgb))) {
        if (document.coverStyle == CoverStyle.CUSTOM_IMAGE && coverBitmap != null) {
            Image(coverBitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val line = if (light) Color.Black.copy(alpha = .42f) else Color.White.copy(alpha = .7f)
            when (document.coverStyle) {
                CoverStyle.MINIMAL -> {
                    drawLine(line, Offset(size.width * .18f, size.height * .38f), Offset(size.width * .82f, size.height * .38f), strokeWidth = 1.5f)
                    drawLine(line.copy(alpha = .24f), Offset(size.width * .30f, size.height * .48f), Offset(size.width * .70f, size.height * .48f), strokeWidth = 1f)
                }
                CoverStyle.DARK -> {
                    drawRoundRect(Color.White.copy(alpha = .14f), topLeft = Offset(size.width * .08f, size.height * .12f), size = androidx.compose.ui.geometry.Size(size.width * .84f, size.height * .76f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f), style = Stroke(width = 2f))
                }
                CoverStyle.PAPER -> {
                    var y = 16f
                    while (y < size.height) { drawLine(line.copy(alpha = .28f), Offset(0f, y), Offset(size.width, y), 1f); y += 18f }
                    drawLine(Color(0xFFB46A6A).copy(alpha = .45f), Offset(18f, 0f), Offset(18f, size.height), 1f)
                }
                CoverStyle.GRID -> {
                    var x = 0f
                    while (x < size.width) { drawLine(line.copy(alpha = .25f), Offset(x, 0f), Offset(x, size.height), 1f); x += 18f }
                    var y = 0f
                    while (y < size.height) { drawLine(line.copy(alpha = .25f), Offset(0f, y), Offset(size.width, y), 1f); y += 18f }
                }
                CoverStyle.SOLID -> drawCircle(Color.White.copy(alpha = .2f), radius = size.minDimension * .16f, center = center)
                CoverStyle.BLACK, CoverStyle.WHITE -> repeat(3) { index -> drawLine(line, Offset(0f, 18f + index * 12f), Offset(size.width * (.9f - index * .08f), 18f + index * 12f), strokeWidth = 2f, cap = StrokeCap.Round) }
                CoverStyle.BLACK_BAND -> { drawRect(Color.White, Offset(0f, size.height * .58f), androidx.compose.ui.geometry.Size(size.width, size.height * .18f)); drawLine(Color.Black, Offset(12f, size.height * .67f), Offset(size.width - 12f, size.height * .67f), strokeWidth = 2f) }
                CoverStyle.WHITE_GRID -> { var x = 0f; while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += 18f }; var y = 0f; while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += 18f } }
                CoverStyle.CUSTOM_IMAGE -> Unit
            }
        }
    }
}

@Composable
private fun EditorScreen(
    document: InkDocument?,
    documents: List<InkDocument>,
    selectedPageId: String?,
    brushesOpen: Boolean,
    brushes: List<ImportedBrush>,
    brushGroups: List<String>,
    importError: String?,
    onBack: () -> Unit,
    onToggleBrushes: () -> Unit,
    onImportBrushes: (String?) -> Unit,
    onExportBrush: (ImportedBrush) -> Unit,
    onUpdateBrush: (ImportedBrush) -> Unit,
    onReplaceBrushes: (List<ImportedBrush>) -> Unit,
    onReplaceBrushGroups: (List<String>) -> Unit,
    onDeleteBrush: (String) -> Unit,
    onReorderBrush: (String, Int) -> Unit,
    onDismissError: () -> Unit,
    onSelectPage: (String) -> Unit,
    pageTemplates: PageTemplateLibrary,
    onAppendCurrentPage: (String) -> Unit,
    onAddPage: (String, Long?) -> Unit,
    onDuplicatePage: (String, String) -> Unit,
    onDeletePage: (String, String) -> Unit,
    onMovePage: (String, String, Int) -> Unit,
    onReorderPages: (String, List<String>) -> Unit,
    onTransferPage: (String, String, String, Boolean) -> Unit,
    onApplyPageTemplate: (String, String, String, Long?) -> Unit,
    onApplyPageTemplates: (String, Set<String>, String, Long?) -> Unit,
    onImportPageTemplates: () -> Unit,
    onTogglePageTemplateFavorite: (String) -> Unit,
    onSetNotebookDefaultTemplate: (String, String?, Long?) -> Unit,
    onSavePageAsTemplate: (String, String, String) -> PageTemplate?,
    onDeletePageTemplate: (String) -> Unit,
    onToggleBookmark: (String, String) -> Unit,
    onChangePageOutlineLevel: (String, String, Int) -> Unit,
    onTogglePageOutlineCollapsed: (String, String) -> Unit,
    onRenamePage: (String, String, String) -> Unit,
    onSetPageMetadata: (String, String, String?, List<String>) -> Unit,
    onClearPageInk: (String, String) -> Unit,
    onSetPageCrop: (String, String, PageCrop) -> Unit,
    onRotatePage: (String, String) -> Unit,
    onAddLayer: (String, String, String) -> Unit,
    onAddLayerFolder: (String, String) -> Unit,
    onRenameLayer: (String, String, String, String) -> Unit,
    onToggleLayerVisibility: (String, String, String) -> Unit,
    onToggleLayerLock: (String, String, String) -> Unit,
    onSetLayerOpacity: (String, String, String, Float) -> Unit,
    onSetLayerBlendMode: (String, String, String, LayerBlendMode) -> Unit,
    onSetLayerRasterTileSurface: (String, String, String, RasterTileSurfaceRef?, Int) -> Unit,
    onToggleLayerAlphaLock: (String, String, String) -> Unit,
    onToggleLayerClippedToBelow: (String, String, String) -> Unit,
    onToggleLayerReference: (String, String, String) -> Unit,
    onToggleLayerDraft: (String, String, String) -> Unit,
    onLayerMaskCommand: (String, String, String, LayerMaskCommand, app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot?) -> Unit,
    onMoveLayerToFolder: (String, String, String, String?) -> Unit,
    onToggleLayerFolderCollapsed: (String, String, String) -> Unit,
    onMoveLayer: (String, String, String, Int) -> Unit,
    onDuplicateLayer: (String, String, String) -> String?,
    onMergeLayerDown: (String, String, String) -> String?,
    onDeleteLayer: (String, String, String) -> Unit,
    onElement: (String, String, PageElement) -> Unit,
    onRemoveElement: (String, String, String) -> Unit,
    onDuplicateElement: (String, String, String) -> Unit,
    onInsertImage: () -> Unit,
    onScanImage: () -> Unit,
    onExport: () -> Unit,
    onExportPage: () -> Unit,
    onExportBundle: () -> Unit,
    assets: DocumentAssetService,
    selectionRuntime: DocumentSelectionRuntime,
    settings: AppSettings,
    pdfIndexing: Boolean,
    onIndexPdfText: (String) -> Unit,
    imageOcrRecognizing: Boolean,
    onRecognizeImages: (String, String, String, (Result<Int>) -> Unit) -> Unit,
    documentRecognizing: Boolean,
    onRecognizeDocument: (String, String, (Result<Int>) -> Unit) -> Unit,
    onConvertHandwriting: (String, String, String, Boolean) -> Unit,
    onOpenInternalLink: (String, String) -> Unit,
    onSettings: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
) {
    if (document == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val context = LocalContext.current
    val editorHostView = LocalView.current
    val brushMasterSettingsStore = remember { BrushMasterSettingsStore(context) }
    val brushUsageStore = remember { BrushUsageStore(context) }
    var brushUsage by remember { mutableStateOf(brushUsageStore.load()) }
    fun changeBrushUsage(next: BrushUsageState) {
        brushUsage = next
        brushUsageStore.save(next)
    }
    val selectedPage = document.pages.firstOrNull { it.id == selectedPageId } ?: document.pages.first()
    val availableBrushes = remember(brushes) { (systemBrushCatalog() + brushes).distinctBy(ImportedBrush::id) }
    val persistedBrush = availableBrushes.firstOrNull {
        it.id == settings.persistentBrushPresetId && !it.hidden
    }
    val persistedTool = persistedBrush?.let(BrushRoleClassifier::editorTool) ?: settings.persistentDrawingTool
    var tool by remember(document.id) { mutableStateOf(persistedTool) }
    var canvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    var selectedBrushId by remember(document.id) { mutableStateOf(persistedBrush?.id) }
    var activeBrush by remember(document.id) { mutableStateOf(persistedBrush) }
    var brushSize by remember(document.id) {
        mutableFloatStateOf(
            brushMasterSettingsStore.loadSize(
                persistedTool,
                persistedBrush?.id ?: "system:${persistedTool.name.lowercase()}",
                persistedBrush?.defaultSize ?: when (persistedTool) {
                    EditorTool.ERASER -> settings.eraserSize
                    EditorTool.HIGHLIGHTER -> settings.highlighterSize
                    else -> settings.penSize
                },
            ),
        )
    }
    var brushOpacity by remember(document.id) {
        mutableFloatStateOf(brushMasterSettingsStore.loadOpacity(
            persistedTool, persistedBrush?.id ?: "system:${persistedTool.name.lowercase()}",
            persistedBrush?.defaultOpacity ?: when (persistedTool) {
                EditorTool.ERASER -> settings.eraserPreset.opacity
                EditorTool.HIGHLIGHTER -> settings.highlighterOpacity
                else -> settings.penOpacity
            },
        ))
    }
    var selectionFeather by remember { mutableFloatStateOf(0f) }
    var selectionActive by remember(document.id, selectedPage.id) { mutableStateOf(false) }
    var selectionTransformState by remember(document.id, selectedPage.id) { mutableStateOf<SelectionTransformUiState?>(null) }
    BackHandler(enabled = selectionTransformState != null) { canvasView?.finishSelectionTransform(apply = false) }
    fun performLayerMaskCommand(layerId: String, command: LayerMaskCommand) {
        if (command == LayerMaskCommand.EDIT) {
            canvasView?.toggleLayerMaskEditing(layerId)
            return
        }
        if (command == LayerMaskCommand.FILL_WHITE || command == LayerMaskCommand.FILL_BLACK) {
            canvasView?.fillLayerMask(layerId, if (command == LayerMaskCommand.FILL_WHITE) 255 else 0)
            return
        }
        if (command == LayerMaskCommand.LOAD_AS_SELECTION) {
            val reference = selectedPage.layers.firstOrNull { it.id == layerId }?.mask?.surface ?: return
            val snapshot = runCatching { DocumentAssetService(context).loadMaskTileSurface(reference).snapshot() }.getOrNull() ?: return
            canvasView?.replaceSelectionFromMask(snapshot)
            return
        }
        onLayerMaskCommand(document.id, selectedPage.id, layerId, command, canvasView?.selectionMaskSurfaceSnapshot())
    }
    fun brushMasterKey(brush: ImportedBrush?, selectedTool: EditorTool = tool): String = brush?.id ?: "system:${selectedTool.name.lowercase()}"
    fun savedBrushSize(brush: ImportedBrush?, fallback: Float, selectedTool: EditorTool = tool): Float =
        brushMasterSettingsStore.loadSize(selectedTool, brushMasterKey(brush, selectedTool), fallback)
    fun savedBrushOpacity(brush: ImportedBrush?, fallback: Float, selectedTool: EditorTool = tool): Float =
        brushMasterSettingsStore.loadOpacity(selectedTool, brushMasterKey(brush, selectedTool), fallback)
    fun changeCurrentBrushSize(raw: Float) {
        val value = brushMasterSettingsStore.saveSize(tool, brushMasterKey(activeBrush), raw)
        brushSize = value
        canvasView?.let { view ->
            view.baseWidth = value
            if (tool == EditorTool.ERASER) view.eraserWidth = value
            view.notifyHoverCursorStyleChanged()
        }
        when (tool) {
            EditorTool.PEN -> if (activeBrush == null) onSettingsChange(settings.copy(penSize = value))
            EditorTool.HIGHLIGHTER -> onSettingsChange(settings.copy(highlighterSize = value))
            EditorTool.ERASER -> if (activeBrush == null) onSettingsChange(settings.copy(eraserSize = value))
            else -> Unit
        }
    }
    fun changeCurrentBrushOpacity(raw: Float) {
        val value = brushMasterSettingsStore.saveOpacity(tool, brushMasterKey(activeBrush), raw)
        brushOpacity = value
        // The sidebar and pen-down can occur in the same display frame. V5 resolves its immutable
        // PaintOp at contact start, so publish the live master opacity without waiting for the
        // AndroidView update/recomposition pass.
        canvasView?.let { view ->
            view.inkOpacity = value
            if (tool == EditorTool.ERASER) view.eraserOpacity = value * (activeBrush?.flow ?: 1f)
        }
        when (tool) {
            EditorTool.PEN -> if (activeBrush == null) onSettingsChange(settings.copy(penOpacity = value))
            EditorTool.HIGHLIGHTER -> onSettingsChange(settings.copy(highlighterOpacity = value))
            else -> Unit
        }
    }
    val drawingColorStore = remember { DrawingColorStore(context) }
    var drawingColors by remember(document.id) { mutableStateOf(drawingColorStore.load(document.id)) }
    val brushColor = drawingColors.foreground
    val backgroundColor = drawingColors.background
    var offeredContinueFillColor by remember { mutableStateOf<Int?>(null) }
    var continueFilling by remember { mutableStateOf(false) }
    var continueFillStartedWithTool by remember { mutableStateOf<EditorTool?>(null) }
    fun commitDrawingColors(colors: DrawingColors) {
        drawingColors = colors
        // The next pen sample can arrive before AndroidView recomposes.
        canvasView?.inkColor = colors.foreground
        canvasView?.secondaryInkColor = colors.background
        drawingColorStore.save(document.id, colors)
    }
    fun changeBrushColor(value: Int) = commitDrawingColors(drawingColors.copy(foreground = value))
    fun changeBackgroundColor(value: Int) = commitDrawingColors(drawingColors.copy(background = value))
    fun swapDrawingColors() = commitDrawingColors(
        drawingColors.let { it.copy(foreground = it.background, background = it.foreground) },
    )
    fun stopContinueFilling() {
        continueFilling = false
        offeredContinueFillColor = null
        continueFillStartedWithTool = null
        canvasView?.setContinueFilling(false)
    }
    LaunchedEffect(tool) {
        if (continueFilling && continueFillStartedWithTool != null && tool != continueFillStartedWithTool) stopContinueFilling()
    }
    var drawingReturnTarget by remember(document.id) {
        mutableStateOf(DrawingToolReturnTarget(persistedTool, persistedBrush, brushSize, brushOpacity))
    }
    var handToolEnabled by remember { mutableStateOf(false) }
    var handToolHeld by remember { mutableStateOf(false) }
    var rotateToolEnabled by remember { mutableStateOf(false) }
    var rotateToolHeld by remember { mutableStateOf(false) }
    fun exitCanvasNavigation() { handToolEnabled = false; handToolHeld = false; rotateToolEnabled = false; rotateToolHeld = false }
    var exportMenu by remember { mutableStateOf(false) }
    var selectionMenuOpen by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    var workspaceMenu by remember { mutableStateOf(false) }
    var colorPanelOpen by remember { mutableStateOf(false) }
    var editSecondaryColor by remember { mutableStateOf(false) }
    var colorPanelWidth by rememberSaveable { mutableFloatStateOf(420f) }
    var zoom by remember(document.id) { mutableFloatStateOf(1f) }
    var panX by remember(document.id) { mutableFloatStateOf(0f) }
    var panY by remember(document.id) { mutableFloatStateOf(0f) }
    var viewRotation by remember(document.id) { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    fun currentViewportGeometry(): ViewportGeometry? {
        val view = canvasView ?: return null
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f || view.width <= 0 || view.height <= 0) return null
        return ViewportGeometry(viewportWidthPx, viewportHeightPx, view.width.toFloat(), view.height.toFloat())
    }
    fun applyViewport(next: ViewportState) { zoom = next.scale; panX = next.panX; panY = next.panY; viewRotation = next.rotationDegrees }
    val pageViewportStore = remember(document.id) { PageViewportStore() }
    var viewportPageId by remember(document.id) { mutableStateOf(selectedPage.id) }
    fun selectPageWithViewport(pageId: String) {
        if (pageId == viewportPageId) { onSelectPage(pageId); return }
        pageViewportStore.save(viewportPageId, ViewportState(zoom, panX, panY, viewRotation))
        applyViewport(pageViewportStore.restore(pageId))
        viewportPageId = pageId
        onSelectPage(pageId)
    }
    LaunchedEffect(selectedPage.id) {
        if (selectedPage.id != viewportPageId) {
            pageViewportStore.save(viewportPageId, ViewportState(zoom, panX, panY, viewRotation))
            applyViewport(pageViewportStore.restore(selectedPage.id))
            viewportPageId = selectedPage.id
        }
    }
    LaunchedEffect(document.pages.map(InkPage::id)) { pageViewportStore.retainPages(document.pages.map(InkPage::id).toSet()) }
    fun fitPage() { applyViewport(ViewportState(1f, 0f, 0f, 0f)); exitCanvasNavigation() }
    fun actualSize() {
        val view = canvasView ?: return
        if (view.width <= 0) return
        applyViewport(ViewportTransform.actualSize(ViewportState(zoom, panX, panY, viewRotation), PageCropTransform.visibleWidth(selectedPage), view.width.toFloat(), currentViewportGeometry()))
    }
    fun fitWidth() {
        val view = canvasView ?: return
        if (view.width <= 0 || viewportWidthPx <= 0f) return
        applyViewport(ViewportTransform.fitWidth(ViewportState(zoom, panX, panY, viewRotation), viewportWidthPx, view.width.toFloat(), currentViewportGeometry()))
    }
    fun resetViewRotation() { applyViewport(ViewportTransform.resetRotation(ViewportState(zoom, panX, panY, viewRotation), currentViewportGeometry())); exitCanvasNavigation() }
    fun rotateViewByShortcut(degrees: Float) {
        applyViewport(ViewportTransform.rotateBy(ViewportState(zoom, panX, panY, viewRotation), degrees, currentViewportGeometry()))
        exitCanvasNavigation()
    }
    var toolbarX by remember { mutableFloatStateOf(settings.toolbarOffsetXDp) }
    var toolbarY by remember { mutableFloatStateOf(settings.toolbarOffsetYDp) }
    var pagePanelBounds by remember {
        mutableStateOf(WorkspacePanelBounds(settings.pagePanelXDp, settings.pagePanelYDp, settings.pagePanelWidthDp, settings.pagePanelHeightDp))
    }
    var brushPanelBounds by remember {
        mutableStateOf(WorkspacePanelBounds(settings.brushPanelXDp, settings.brushPanelYDp, settings.brushPanelWidthDp, settings.brushPanelHeightDp))
    }
    var layerPanelBounds by remember {
        mutableStateOf(WorkspacePanelBounds(settings.layerPanelXDp, settings.layerPanelYDp, settings.layerPanelWidthDp, settings.layerPanelHeightDp))
    }
    var activeFloatingPanel by remember { mutableStateOf("brushes") }
    var draggingPanel by remember { mutableStateOf<WorkspacePanelKind?>(null) }
    var panelDragOrigin by remember { mutableStateOf<WorkspacePanelBounds?>(null) }
    var panelDockPreview by remember { mutableStateOf<WorkspacePanelDock?>(null) }
    var panelGroupPreviewTarget by remember { mutableStateOf<WorkspacePanelKind?>(null) }
    var panelGroupDragOrigins by remember { mutableStateOf<Map<WorkspacePanelKind, WorkspacePanelBounds>>(emptyMap()) }
    var dockedPanelOriginDock by remember { mutableStateOf<WorkspacePanelDock?>(null) }
    var dockedPanelPreviewBounds by remember { mutableStateOf<WorkspacePanelBounds?>(null) }
    var dockedPanelTargetDock by remember { mutableStateOf<WorkspacePanelDock?>(null) }
    var panelDragDeltaXDp by remember { mutableFloatStateOf(0f) }
    var panelDragDeltaYDp by remember { mutableFloatStateOf(0f) }
    val editorDensity = LocalDensity.current
    LaunchedEffect(settings.toolbarOffsetXDp, settings.toolbarOffsetYDp, settings.toolbarDock) {
        toolbarX = settings.toolbarOffsetXDp
        toolbarY = settings.toolbarOffsetYDp
    }
    LaunchedEffect(settings.pagePanelXDp, settings.pagePanelYDp, settings.pagePanelWidthDp, settings.pagePanelHeightDp) {
        pagePanelBounds = WorkspacePanelBounds(settings.pagePanelXDp, settings.pagePanelYDp, settings.pagePanelWidthDp, settings.pagePanelHeightDp)
    }
    LaunchedEffect(settings.brushPanelXDp, settings.brushPanelYDp, settings.brushPanelWidthDp, settings.brushPanelHeightDp) {
        brushPanelBounds = WorkspacePanelBounds(settings.brushPanelXDp, settings.brushPanelYDp, settings.brushPanelWidthDp, settings.brushPanelHeightDp)
    }
    LaunchedEffect(settings.layerPanelXDp, settings.layerPanelYDp, settings.layerPanelWidthDp, settings.layerPanelHeightDp) {
        layerPanelBounds = WorkspacePanelBounds(settings.layerPanelXDp, settings.layerPanelYDp, settings.layerPanelWidthDp, settings.layerPanelHeightDp)
    }
    fun persistPagePanel(bounds: WorkspacePanelBounds) = onSettingsChange(settings.copy(
        pagePanelXDp = bounds.xDp,
        pagePanelYDp = bounds.yDp,
        pagePanelWidthDp = bounds.widthDp,
        pagePanelHeightDp = bounds.heightDp,
    ))
    fun persistBrushPanel(bounds: WorkspacePanelBounds) = onSettingsChange(settings.copy(
        brushPanelXDp = bounds.xDp,
        brushPanelYDp = bounds.yDp,
        brushPanelWidthDp = bounds.widthDp,
        brushPanelHeightDp = bounds.heightDp,
    ))
    fun persistLayerPanel(bounds: WorkspacePanelBounds) = onSettingsChange(settings.copy(
        layerPanelXDp = bounds.xDp,
        layerPanelYDp = bounds.yDp,
        layerPanelWidthDp = bounds.widthDp,
        layerPanelHeightDp = bounds.heightDp,
    ))
    val waitingForInputText = tr("Waiting for input", "等待输入")
    val unknownInputDeviceText = tr("Unknown device", "未知设备")
    val unmappedShortcutText = tr("unmapped", "未映射")
    var inputDiagnostic by remember { mutableStateOf(waitingForInputText) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchFocus by remember(document.id) { mutableStateOf<PageSearchHit?>(null) }
    var pdfLinkFocus by remember(document.id) { mutableStateOf<Pair<String, PdfTextBounds>?>(null) }
    var outlineOpen by remember { mutableStateOf(false) }
    var outlineMenuPageId by remember { mutableStateOf<String?>(null) }
    var outlineRenamePageId by remember { mutableStateOf<String?>(null) }
    var outlineRenameText by remember { mutableStateOf("") }
    var documentQuery by remember { mutableStateOf("") }
    var recognitionLanguageTag by remember(document.id) { mutableStateOf(if (settings.language == app.inkloom.settings.UiLanguage.SIMPLIFIED_CHINESE) "zh-Hans" else "en-US") }
    var recognitionMessage by remember(document.id, selectedPage.id) { mutableStateOf<String?>(null) }
    var handwritingConversionHit by remember { mutableStateOf<Pair<InkPage, PageSearchHit>?>(null) }
    var textInsertOpen by remember { mutableStateOf(false) }
    var stickyInsertOpen by remember { mutableStateOf(false) }
    var linkInsertOpen by remember { mutableStateOf(false) }
    var shapeInsertOpen by remember { mutableStateOf(false) }
    var imageInsertOpen by remember { mutableStateOf(false) }
    var insertText by remember { mutableStateOf("") }
    var insertLinkTitle by remember { mutableStateOf("") }
    var insertLinkUrl by remember { mutableStateOf("") }
    var insertInternalLink by remember { mutableStateOf(false) }
    var insertLinkDocumentId by remember(document.id) { mutableStateOf(document.id) }
    var insertLinkPageId by remember(document.id) { mutableStateOf(selectedPage.id) }
    var insertShape by remember { mutableStateOf(ShapeKind.RECTANGLE) }
    var selectedElementId by remember(selectedPage.id) { mutableStateOf<String?>(null) }
    var imageTransformId by remember(selectedPage.id) { mutableStateOf<String?>(null) }
    var imageCanvasTransform by remember(selectedPage.id) { mutableStateOf<ImageCanvasTransform?>(null) }
    var imageCanvasPreview by remember(selectedPage.id) { mutableStateOf<PageElement.Image?>(null) }
    var imageTransformMode by remember { mutableStateOf(ImageTransformMode.FREEFORM) }
    val paintingPage = imageCanvasPreview?.let { preview -> selectedPage.copy(elements = selectedPage.elements.map {
        if (it.id == preview.id) PageCropTransform.unproject(preview, selectedPage.crop) else it
    }) } ?: selectedPage
    SideEffect { canvasView?.imageCanvasTransform = imageCanvasTransform }
    fun finishImageTransform(commit: Boolean) {
        val session = imageCanvasTransform ?: return
        val layer = selectedPage.layers.firstOrNull { it.id == session.original.layerId }
        if (commit && layer != null && !LayerHierarchy.effectiveLocked(selectedPage.layers, layer) && session.current != session.original) {
            val original = PageCropTransform.unproject(session.original, selectedPage.crop)
            val changed = PageCropTransform.unproject(session.current, selectedPage.crop)
            val targetDocument = document.id
            val targetPage = selectedPage.id
            onElement(targetDocument, targetPage, changed)
            canvasView?.recordExternalEdit(undo = { onElement(targetDocument, targetPage, original) },
                redo = { onElement(targetDocument, targetPage, changed) })
        }
        imageCanvasTransform = null; imageCanvasPreview = null; imageTransformId = null
        canvasView?.imageCanvasTransform = null
        canvasView?.invalidate()
    }
    BackHandler(enabled = imageCanvasTransform != null && imageTransformId == null) { finishImageTransform(false) }
    (selectedPage.elements.firstOrNull { it.id == imageTransformId } as? PageElement.Image)?.let { original ->
        val image = imageCanvasTransform?.current?.takeIf { it.id == original.id } ?: original
        ElementEditorDialog(image, onDismiss = { imageTransformId = null }, onSave = { changed ->
            val session = imageCanvasTransform
            if (session != null && changed is PageElement.Image) session.replace(changed)
            else {
                val layer = selectedPage.layers.firstOrNull { it.id == image.layerId }
                if (layer != null && !LayerHierarchy.effectiveLocked(selectedPage.layers, layer)) onElement(document.id, selectedPage.id, changed)
            }
            imageTransformId = null
        })
    }
    var layersOpen by remember { mutableStateOf(settings.showLayerPanel) }
    LaunchedEffect(settings.showLayerPanel) { layersOpen = settings.showLayerPanel }
    LaunchedEffect(brushesOpen, settings.brushPanelDock, settings.brushPanelMode, settings.brushPanelLocked) {
        if (brushesOpen && (settings.brushPanelDock != WorkspacePanelDock.FREE || settings.brushPanelMode != WorkspacePanelMode.EXPANDED || settings.brushPanelLocked)) {
            onSettingsChange(settings.copy(
                brushPanelDock = WorkspacePanelDock.FREE,
                brushPanelMode = WorkspacePanelMode.EXPANDED,
                brushPanelLocked = false,
            ))
        }
    }
    var activeLayerId by remember(selectedPage.id) { mutableStateOf(LayerIds.INK) }
    fun syncCanvasInput(prepareAssets: Boolean = false) {
        canvasView?.let { view ->
            view.activeLayerId = activeLayerId
            view.applyBrushConfiguration(tool, settings, activeBrush, brushSize, brushOpacity, brushColor, settings.eraserPreset, backgroundColor)
            if (prepareAssets) view.prepareBrushAssetsNow()
            view.requestFocus()
        }
    }
    fun rememberDrawingSelection() {
        drawingReturnTarget = DrawingToolReturnTarget(tool, activeBrush, brushSize, brushOpacity)
    }
    fun rememberSelectionReturnTarget() {
        if (tool != EditorTool.SELECT) rememberDrawingSelection()
    }
    fun restorePreviousDrawingTool(finishingSelection: Boolean = false) {
        val target = drawingReturnTarget
        tool = if (finishingSelection && target.tool == EditorTool.SELECT) {
            BrushRoleClassifier.editorTool(target.brush)
        } else target.tool
        activeBrush = target.brush
        selectedBrushId = target.brush?.id
        brushSize = target.size
        brushOpacity = target.opacity
        syncCanvasInput(prepareAssets = target.brush != null)
    }
    fun finishSelectionTool(deselect: Boolean) {
        selectionMenuOpen = false
        canvasView?.cancelSelectionInput()
        if (deselect) {
            selectedElementId = null
            if (canvasView?.clearSelection() == true) selectionFeather = 0f
        }
        restorePreviousDrawingTool(finishingSelection = true)
    }
    fun activateWorkspaceBrush(brush: ImportedBrush, persistSelection: Boolean) {
        if (!brush.toolBinding.isSupported) {
            android.widget.Toast.makeText(context, brush.toolBinding.unsupportedReason ?: "Unsupported brush tool", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        exitCanvasNavigation()
        val usedBrush = brush.copy(lastUsedAt = System.currentTimeMillis())
        onUpdateBrush(usedBrush)
        selectedBrushId = usedBrush.id
        activeBrush = usedBrush
        val chosenTool = BrushRoleClassifier.editorTool(usedBrush)
        val changedTool = tool != chosenTool
        brushSize = savedBrushSize(usedBrush, usedBrush.defaultSize, chosenTool)
        brushOpacity = savedBrushOpacity(usedBrush, usedBrush.defaultOpacity, chosenTool)
        tool = chosenTool
        if (persistSelection) {
            changeBrushUsage(brushUsage.used(usedBrush.id, when (chosenTool) {
                EditorTool.PEN, EditorTool.HIGHLIGHTER -> false
                EditorTool.ERASER -> true
                else -> null
            }).let { usage ->
                if (changedTool && usage.category != BrushUsageState.RECENT_CATEGORY) usage.copy(category = usedBrush.groupName)
                else usage
            })
            rememberDrawingSelection()
            onSettingsChange(
                settings.copy(
                    persistentDrawingTool = chosenTool,
                    persistentBrushPresetId = usedBrush.id,
                ),
            )
        }
        if (chosenTool == EditorTool.PEN && activeLayerId in setOf(LayerIds.HIGHLIGHT, LayerIds.OBJECTS)) activeLayerId = LayerIds.INK
        // Apply the complete descriptor synchronously. A panel click and the next pen-down can
        // occur before Compose's AndroidView update block; partial field assignment here used to
        // leave the input configuration in a stale state and made docked brush selection appear
        // to disable writing.
        syncCanvasInput(prepareAssets = true)
    }
    fun selectWorkspaceBrush(brush: ImportedBrush) = activateWorkspaceBrush(brush, persistSelection = true)

    LaunchedEffect(settings.persistentBrushPresetId, brushes) {
        if (activeBrush?.id == settings.persistentBrushPresetId) return@LaunchedEffect
        val restored = (systemBrushCatalog() + brushes).firstOrNull {
            it.id == settings.persistentBrushPresetId && !it.hidden
        } ?: return@LaunchedEffect
        val restoredTool = BrushRoleClassifier.editorTool(restored)
        activeBrush = restored
        selectedBrushId = restored.id
        tool = restoredTool
        brushSize = savedBrushSize(restored, restored.defaultSize, restoredTool)
        brushOpacity = savedBrushOpacity(restored, restored.defaultOpacity, restoredTool)
        syncCanvasInput(prepareAssets = true)
    }
    fun setLayersPanelOpen(open: Boolean, pageId: String? = null) {
        pageId?.let(::selectPageWithViewport)
        if (open && brushesOpen) onToggleBrushes()
        layersOpen = open
        onSettingsChange(settings.copy(showLayerPanel = open, showBrushPanel = if (open) false else settings.showBrushPanel))
    }
    var panelsHiddenByShortcut by remember { mutableStateOf(false) }
    var pageRailBeforeShortcut by remember { mutableStateOf(settings.showPageRail) }
    var toolbarBeforeShortcut by remember { mutableStateOf(settings.showToolbar) }
    var brushesBeforeShortcut by remember { mutableStateOf(false) }
    var layersBeforeShortcut by remember { mutableStateOf(false) }
    var rulerBeforeShortcut by remember { mutableStateOf(false) }
    var gridBeforeShortcut by remember { mutableStateOf(false) }
    var guidesBeforeShortcut by remember { mutableStateOf(false) }
    LaunchedEffect(selectedPage.layers) {
        val active = selectedPage.layers.firstOrNull { it.id == activeLayerId }
        if (active == null || LayerHierarchy.effectiveLocked(selectedPage.layers, active) || !LayerHierarchy.effectiveVisible(selectedPage.layers, active) || active.role in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) activeLayerId = selectedPage.layers.firstOrNull { LayerHierarchy.effectiveVisible(selectedPage.layers, it) && !LayerHierarchy.effectiveLocked(selectedPage.layers, it) && it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) }?.id ?: LayerIds.INK
    }
    fun applyWorkspaceSettings(restored: AppSettings) {
        layersOpen = restored.showLayerPanel
        onSettingsChange(restored)
    }
    fun applyWorkspaceSnapshot(snapshot: app.inkloom.settings.ToolboxWorkspaceSnapshot) =
        applyWorkspaceSettings(settings.applyToolboxWorkspace(snapshot))
    fun applyBuiltInWorkspace(workspace: BuiltInWorkspace) {
        workspaceMenu = false
        applyWorkspaceSnapshot(BuiltInWorkspaces.snapshot(workspace))
    }
    val allChangesSavedMessage = tr("All changes are saved.", "所有更改均已保存。")
    val inkOnlyClipboardMessage = tr("Clipboard actions currently apply to ink selections.", "剪贴板操作目前仅适用于墨迹选区。")
    val selectBeforeCutMessage = tr("Select ink before cutting.", "请先选择墨迹再剪切。")
    val selectBeforeCopyMessage = tr("Select ink before copying.", "请先选择墨迹再复制。")
    val noInkClipboardMessage = tr("The clipboard does not contain Inkloom ink.", "剪贴板中没有 Inkloom 墨迹。")
    val colorsDescription = tr("Colors", "颜色")
    var notebookSwitcherOpen by remember { mutableStateOf(false) }
    val switchNoteDescription = tr("Switch notebook", "切换笔记")
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.height(52.dp).fillMaxWidth().background(Color(0xFFF8F8F8)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(tr("Gallery", "图库"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Box {
                Text(
                    document.title,
                    Modifier.widthIn(max = 180.dp).clip(RoundedCornerShape(6.dp))
                        .clickable { notebookSwitcherOpen = true }
                        .semantics { contentDescription = switchNoteDescription }.padding(horizontal = 8.dp, vertical = 10.dp),
                    maxLines = 1, fontSize = 12.sp, color = MutedInk,
                )
                DropdownMenu(expanded = notebookSwitcherOpen, onDismissRequest = { notebookSwitcherOpen = false },
                    modifier = Modifier.width(320.dp).heightIn(max = 440.dp)) {
                    Text(tr("Switch notebook", "切换笔记"), Modifier.padding(16.dp), color = Sage)
                    documents.filter { it.trashedAt == null }.sortedByDescending { it.updatedAt }.forEach { note ->
                        DropdownMenuItem(
                            text = { Column { Text(note.title, maxLines = 2); Text(tr("${note.pages.size} pages", "${note.pages.size} 页"), fontSize = 11.sp, color = MutedInk) } },
                            leadingIcon = { if (note.id == document.id) Icon(Icons.Outlined.Check, null) },
                            onClick = {
                                notebookSwitcherOpen = false
                                if (note.id != document.id) note.pages.firstOrNull()?.let { onOpenInternalLink(note.id, it.id) }
                            },
                        )
                    }
                }
            }
            ProcreateTopButton(Icons.Outlined.Undo, tr("Undo", "撤销"), onClick = { canvasView?.undo() })
            ProcreateTopButton(Icons.Outlined.Redo, tr("Redo", "重做"), onClick = { canvasView?.redo() })
            Box {
                ProcreateTopButton(Icons.Outlined.Build, tr("Actions", "操作"), selected = workspaceMenu, onClick = { workspaceMenu = true })
                DropdownMenu(expanded = workspaceMenu, onDismissRequest = { workspaceMenu = false }, modifier = Modifier.widthIn(min = 260.dp, max = 360.dp)) {
                    DropdownMenuItem(text = { Text(tr("Search this notebook", "搜索当前笔记")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, onClick = { workspaceMenu = false; searchOpen = true })
                    DropdownMenuItem(text = { Text(tr("Notebook settings", "笔记设置")) }, leadingIcon = { Icon(Icons.Outlined.Settings, null) }, onClick = { workspaceMenu = false; onSettings() })
                    DropdownMenuItem(text = { Text(tr("Page outline", "页面大纲")) }, leadingIcon = { Icon(Icons.Outlined.FormatListBulleted, null) }, onClick = { workspaceMenu = false; outlineOpen = true })
                    DropdownMenuItem(text = { Text(tr("Export PDF", "导出 PDF")) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { workspaceMenu = false; onExport() })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Column { Text(tr("Drawing workspace", "绘画工作区")); Text(tr("Canvas with pop-over pages, brushes and layers", "画布与弹出式页面、笔刷和图层"), fontSize = 10.sp, color = MutedInk) } },
                        leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.STUDIO) },
                    )
                    DropdownMenuItem(
                        text = { Column { Text(tr("Reading workspace", "阅读工作区")); Text(tr("Pages with drawing controls hidden", "保留页面并隐藏绘画控制"), fontSize = 10.sp, color = MutedInk) } },
                        leadingIcon = { Icon(Icons.Outlined.Visibility, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.READING) },
                    )

                    HorizontalDivider()
                }
            }
            ProcreateTopButton(Icons.Outlined.AutoFixHigh, tr("Adjustments", "调整"), onClick = onSettings)
            Box {
                ProcreateTopButton(Icons.Outlined.Gesture, tr("Selection", "选择"), selected = tool == EditorTool.SELECT, onClick = {
                    exitCanvasNavigation()
                    rememberSelectionReturnTarget()
                    tool = EditorTool.SELECT
                    syncCanvasInput()
                    selectionMenuOpen = true
                })
                DropdownMenu(
                    expanded = selectionMenuOpen,
                    onDismissRequest = { selectionMenuOpen = false },
                    modifier = Modifier.width(304.dp).semantics { contentDescription = "CSP selection subtools" },
                ) {
                    CspSelectionSubtoolPalette(
                        settings = settings,
                        onShape = { shape ->
                            canvasView?.beginSelectionInput(shape)
                            onSettingsChange(settings.copy(selectionShape = shape))
                            selectionMenuOpen = false
                        },
                        onMode = {
                            onSettingsChange(settings.copy(selectionMode = it))
                            selectionMenuOpen = false
                        },
                        onBrushSize = { onSettingsChange(settings.copy(selectionBrushSize = it)) },
                        onThreshold = { onSettingsChange(settings.copy(selectionThreshold = it)) },
                        onDone = { finishSelectionTool(deselect = false) },
                        onDeselect = { finishSelectionTool(deselect = true) },
                    )
                }
            }
            ProcreateTopButton(Icons.Outlined.OpenWith, tr("Move canvas", "移动画布"), selected = handToolEnabled, onClick = { handToolEnabled = !handToolEnabled; rotateToolEnabled = false })
            ProcreateTopButton(
                if (settings.showPageRail) Icons.Outlined.ViewSidebar else Icons.Outlined.TableRows,
                tr("Toggle pages", "显示或隐藏页面栏"),
                selected = settings.showPageRail,
                onClick = { onSettingsChange(settings.copy(showPageRail = !settings.showPageRail)) },
            )
            Spacer(Modifier.weight(1f))
            offeredContinueFillColor?.let { fillColor ->
                AssistChip(
                    onClick = {
                        if (continueFilling) stopContinueFilling() else {
                            continueFilling = true
                            continueFillStartedWithTool = tool
                            canvasView?.setContinueFilling(true, fillColor)
                        }
                    },
                    label = { Text(if (continueFilling) tr("Done filling", "完成填充") else tr("Continue filling", "继续填充"), fontSize = 10.sp) },
                    leadingIcon = { Icon(if (continueFilling) Icons.Outlined.Check else Icons.Outlined.FormatColorFill, null, Modifier.size(16.dp)) },
                    modifier = Modifier.height(32.dp),
                )
            }
            ProcreateTopButton(Icons.Outlined.Brush, tr("Brushes", "笔刷"), selected = tool == EditorTool.PEN, onClick = {
                exitCanvasNavigation()
                val alreadySelected = tool == EditorTool.PEN
                if (alreadySelected && activeBrush != null) {
                    // Opening the library is not a brush selection. Keep the live masters.
                    onToggleBrushes()
                    return@ProcreateTopButton
                }
                tool = EditorTool.PEN
                val paintBrushes = availableBrushes.filter { !it.hidden && it.toolBinding.isSupported &&
                    BrushRoleClassifier.editorTool(it) in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) }
                val remembered = activeBrush?.takeIf { active -> paintBrushes.any { it.id == active.id } }
                    ?: paintBrushes.firstOrNull { it.id == brushUsage.lastPaintBrushId }
                    ?: paintBrushes.maxByOrNull(ImportedBrush::lastUsedAt)
                activeBrush = remembered
                selectedBrushId = remembered?.id
                brushSize = savedBrushSize(remembered, remembered?.defaultSize ?: settings.penSize, EditorTool.PEN)
                brushOpacity = savedBrushOpacity(remembered, remembered?.defaultOpacity ?: settings.penOpacity, EditorTool.PEN)
                remembered?.let { selected ->
                    changeBrushUsage(brushUsage.used(selected.id, eraser = false).let { usage ->
                        if (usage.category == BrushUsageState.RECENT_CATEGORY) usage
                        else usage.copy(category = selected.groupName)
                    })
                    onSettingsChange(settings.copy(persistentDrawingTool = EditorTool.PEN, persistentBrushPresetId = selected.id))
                }
                syncCanvasInput(prepareAssets = remembered != null)
                if (alreadySelected || !brushesOpen) onToggleBrushes()
            })
            ProcreateTopButton(InkloomIcons.Eraser, tr("Eraser", "橡皮"), selected = tool == EditorTool.ERASER, onClick = {
                exitCanvasNavigation()
                val eraser = activeBrush?.takeIf(BrushRoleClassifier::isEraser)
                    ?: availableBrushes.firstOrNull { it.id == brushUsage.lastEraserBrushId && !it.hidden && BrushRoleClassifier.isEraser(it) }
                    ?: brushes.filter(BrushRoleClassifier::isEraser).maxByOrNull(ImportedBrush::lastUsedAt)
                    ?: systemBrushCatalog().first { BrushRoleClassifier.isEraser(it) }
                selectWorkspaceBrush(eraser)
                if (!brushesOpen) onToggleBrushes()
            })
            ProcreateTopButton(Icons.Outlined.Layers, tr("Layers", "图层"), selected = layersOpen, onClick = {
                setLayersPanelOpen(!layersOpen)
            })
            ProcreateTopButton(Icons.Outlined.SwapHoriz, tr("Quick swap foreground and background colors", "快捷交换前景色和背景色"),
                onClick = ::swapDrawingColors)
            Box {
                Box(Modifier.size(44.dp).semantics { contentDescription = colorsDescription }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp)) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(21.dp).clip(CircleShape).background(Color(backgroundColor)).border(1.5.dp, if (colorPanelOpen && editSecondaryColor) Color(0xFF1688F8) else OutlineSoft, CircleShape).clickable { editSecondaryColor = true; colorPanelOpen = true },
                        )
                        Box(
                            Modifier.align(Alignment.BottomStart).size(23.dp).clip(CircleShape).background(Color(brushColor)).border(2.dp, if (colorPanelOpen && !editSecondaryColor) Color(0xFF1688F8) else OutlineSoft, CircleShape).combinedClickable(
                                onClick = { editSecondaryColor = false; colorPanelOpen = true },
                                onLongClick = { editorHostView.startDragAndDrop(ClipData.newPlainText("Inkloom color", brushColor.toUInt().toString()), ColorDropDragShadow(brushColor), CanvasColorDrop(brushColor), 0) },
                            ),
                        )
                    }
                }
                DropdownMenu(
                    expanded = colorPanelOpen,
                    onDismissRequest = { colorPanelOpen = false },
                    modifier = Modifier.width(colorPanelWidth.dp).background(Color.White).padding(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (editSecondaryColor) tr("Secondary color", "副色") else tr("Primary color", "主色"), Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp), color = Ink, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = ::swapDrawingColors, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.SwapHoriz, tr("Swap foreground and background colors", "交换前景色和背景色"), Modifier.size(18.dp))
                        }
                    }
                    CspColorPanel(
                        color = if (editSecondaryColor) backgroundColor else brushColor,
                        onColorPreview = { preview ->
                            if (editSecondaryColor) canvasView?.secondaryInkColor = preview
                            else canvasView?.inkColor = preview
                        },
                        onColorCommit = if (editSecondaryColor) ::changeBackgroundColor else ::changeBrushColor,
                        onResizeWidth = { delta -> colorPanelWidth = (colorPanelWidth + delta).coerceIn(320f, 680f) },
                    )
                }
            }
            Box {
                ProcreateTopButton(Icons.Outlined.MoreHoriz, tr("More", "更多"), selected = exportMenu, onClick = { exportMenu = true })
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    if (tool == EditorTool.SELECT) {
                        SelectionShape.entries.forEach { shape ->
                            DropdownMenuItem(
                                text = { Text(when (shape) { SelectionShape.SMART_BORDER -> tr("Smart border", "智能边框"); SelectionShape.MAGIC_WAND -> tr("Magic wand", "魔棒"); SelectionShape.FREEFORM -> tr("Freeform", "自由形"); SelectionShape.RECTANGLE -> tr("Rectangle", "矩形"); SelectionShape.ELLIPSE -> tr("Ellipse", "椭圆"); SelectionShape.BRUSH -> tr("Selection brush", "选区画笔") }) },
                                trailingIcon = { if (settings.selectionShape == shape) Icon(Icons.Outlined.Check, null) },
                                onClick = { exportMenu = false; onSettingsChange(settings.copy(selectionShape = shape)) },
                            )
                        }
                        if (settings.selectionShape == SelectionShape.BRUSH) DropdownMenuItem(
                            text = {
                                Column(Modifier.width(230.dp)) {
                                    Text(tr("Selection brush size · ${settings.selectionBrushSize.toInt()}", "选区画笔大小 · ${settings.selectionBrushSize.toInt()}"), fontSize = 11.sp)
                                    Slider(settings.selectionBrushSize, { onSettingsChange(settings.copy(selectionBrushSize = it)) }, valueRange = 4f..300f)
                                }
                            },
                            onClick = {},
                        )
                        if (settings.selectionShape in setOf(SelectionShape.SMART_BORDER, SelectionShape.MAGIC_WAND)) DropdownMenuItem(
                            text = {
                                Column(Modifier.width(230.dp)) {
                                    Text(tr("Recognition threshold · ${(settings.selectionThreshold * 100).toInt()}%", "识别阈值 · ${(settings.selectionThreshold * 100).toInt()}%"), fontSize = 11.sp)
                                    Slider(settings.selectionThreshold, { onSettingsChange(settings.copy(selectionThreshold = it)) }, valueRange = 0f..1f)
                                }
                            },
                            onClick = {},
                        )
                        HorizontalDivider()
                    }
                    listOf(
                        Triple(EditorTool.EYEDROPPER, Icons.Outlined.Colorize, tr("Eyedropper", "吸管")),
                        Triple(EditorTool.FILL, Icons.Outlined.FormatColorFill, tr("Fill", "填充")),
                        Triple(EditorTool.GRADIENT, Icons.Outlined.Gradient, tr("Gradient", "渐变")),
                        Triple(EditorTool.SMUDGE, Icons.Outlined.BlurOn, tr("Blend", "混色")),
                        Triple(EditorTool.LIQUIFY, Icons.Outlined.Waves, tr("Liquify", "液化")),
                        Triple(EditorTool.CORRECT_LINE, Icons.Outlined.AutoFixHigh, tr("Correct line", "线条修正")),
                        Triple(EditorTool.SHAPE, Icons.Outlined.Category, tr("Shape", "形状")),
                        Triple(EditorTool.TEXT, Icons.Outlined.Title, tr("Text", "文字")),
                        Triple(EditorTool.NOTE, Icons.Outlined.StickyNote2, tr("Note", "便笺")),
                        Triple(EditorTool.LINK, Icons.Outlined.Link, tr("Link", "链接")),
                        Triple(EditorTool.IMAGE, Icons.Outlined.Image, tr("Image", "图片")),
                        Triple(EditorTool.LASER, Icons.Outlined.TrackChanges, tr("Laser pointer", "激光笔")),
                    ).forEach { (chosen, icon, label) ->
                        DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = {
                            exportMenu = false
                            exitCanvasNavigation()
                            tool = chosen
                            canvasView?.tool = chosen
                            when (chosen) {
                                EditorTool.SHAPE -> shapeInsertOpen = true
                                EditorTool.TEXT -> { insertText = ""; textInsertOpen = true }
                                EditorTool.NOTE -> { insertText = ""; stickyInsertOpen = true }
                                EditorTool.LINK -> { insertLinkTitle = ""; insertLinkUrl = ""; linkInsertOpen = true }
                                EditorTool.IMAGE -> { imageInsertOpen = true; tool = EditorTool.SELECT; canvasView?.tool = EditorTool.SELECT }
                                EditorTool.SMUDGE -> systemBrushCatalog().firstOrNull(BrushRoleClassifier::isBlend)?.let(::selectWorkspaceBrush)
                                EditorTool.LIQUIFY -> systemBrushCatalog().firstOrNull(BrushRoleClassifier::isLiquify)?.let(::selectWorkspaceBrush)
                                else -> Unit
                            }
                            syncCanvasInput(prepareAssets = activeBrush != null)
                        })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(tr(if (settings.rulerEnabled) "Hide ruler" else "Show ruler", if (settings.rulerEnabled) "隐藏直尺" else "显示直尺")) },
                        leadingIcon = { Icon(Icons.Outlined.Straighten, null) },
                        trailingIcon = { if (settings.rulerEnabled) Icon(Icons.Outlined.Check, null) },
                        onClick = { exportMenu = false; onSettingsChange(settings.copy(rulerEnabled = !settings.rulerEnabled)) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr(if (settings.gridSnapEnabled) "Hide snap grid" else "Show snap grid", if (settings.gridSnapEnabled) "隐藏吸附网格" else "显示吸附网格")) },
                        leadingIcon = { Icon(Icons.Outlined.GridOn, null) },
                        trailingIcon = { if (settings.gridSnapEnabled) Icon(Icons.Outlined.Check, null) },
                        onClick = { exportMenu = false; onSettingsChange(settings.copy(gridSnapEnabled = !settings.gridSnapEnabled)) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr(if (settings.alignmentGuidesEnabled) "Hide alignment guides" else "Show alignment guides", if (settings.alignmentGuidesEnabled) "隐藏对齐参考线" else "显示对齐参考线")) },
                        leadingIcon = { Icon(Icons.Outlined.AlignHorizontalCenter, null) },
                        trailingIcon = { if (settings.alignmentGuidesEnabled) Icon(Icons.Outlined.Check, null) },
                        onClick = { exportMenu = false; onSettingsChange(settings.copy(alignmentGuidesEnabled = !settings.alignmentGuidesEnabled)) },
                    )
                    HorizontalDivider()
                    Text(tr("Perspective guide", "透视辅助线"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MutedInk, fontSize = 11.sp)
                    PerspectiveGuideMode.entries.forEach { mode ->
                        val label = when (mode) {
                            PerspectiveGuideMode.NONE -> tr("Off", "关闭")
                            PerspectiveGuideMode.ONE_POINT -> tr("One-point perspective", "一点透视")
                            PerspectiveGuideMode.TWO_POINT -> tr("Two-point perspective", "两点透视")
                            PerspectiveGuideMode.THREE_POINT -> tr("Three-point perspective", "三点透视")
                        }
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(Icons.Outlined.ViewInAr, null) },
                            trailingIcon = { if (settings.perspectiveGuideMode == mode) Icon(Icons.Outlined.Check, null) },
                            onClick = {
                                exportMenu = false
                                val guide = PerspectiveGuideState.defaultFor(mode)
                                val points = guide.points
                                onSettingsChange(settings.copy(
                                    perspectiveGuideMode = mode,
                                    perspectiveGuideEditing = mode != PerspectiveGuideMode.NONE,
                                    specialRulerMode = if (mode == PerspectiveGuideMode.NONE) settings.specialRulerMode else SpecialRulerMode.NONE,
                                    specialRulerEditing = if (mode == PerspectiveGuideMode.NONE) settings.specialRulerEditing else false,
                                    perspectivePoint1X = points[0].x,
                                    perspectivePoint1Y = points[0].y,
                                    perspectivePoint2X = points[1].x,
                                    perspectivePoint2Y = points[1].y,
                                    perspectivePoint3X = points[2].x,
                                    perspectivePoint3Y = points[2].y,
                                ))
                            },
                        )
                    }
                    if (settings.perspectiveGuideMode != PerspectiveGuideMode.NONE) {
                        DropdownMenuItem(
                            text = { Text(tr("Drawing Assist", "绘图辅助")) },
                            trailingIcon = { if (settings.perspectiveGuideAssisted) Icon(Icons.Outlined.Check, null) },
                            onClick = { exportMenu = false; onSettingsChange(settings.copy(perspectiveGuideAssisted = !settings.perspectiveGuideAssisted)) },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Edit vanishing points", "编辑消失点")) },
                            trailingIcon = { if (settings.perspectiveGuideEditing) Icon(Icons.Outlined.Check, null) },
                            onClick = { exportMenu = false; onSettingsChange(settings.copy(perspectiveGuideEditing = !settings.perspectiveGuideEditing)) },
                        )
                    }
                    HorizontalDivider()
                    Text(tr("Special ruler", "特殊尺"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MutedInk, fontSize = 11.sp)
                    SpecialRulerMode.entries.forEach { mode ->
                        val label = when (mode) {
                            SpecialRulerMode.NONE -> tr("Off", "关闭")
                            SpecialRulerMode.PARALLEL -> tr("Parallel ruler", "平行线尺")
                            SpecialRulerMode.RADIAL -> tr("Radial ruler", "放射线尺")
                            SpecialRulerMode.CONCENTRIC -> tr("Concentric ruler", "同心圆尺")
                            SpecialRulerMode.ISOMETRIC -> tr("Isometric guide", "等距辅助线")
                        }
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(if (mode == SpecialRulerMode.CONCENTRIC) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.LineAxis, null) },
                            trailingIcon = { if (settings.specialRulerMode == mode) Icon(Icons.Outlined.Check, null) },
                            onClick = {
                                exportMenu = false
                                onSettingsChange(settings.copy(
                                    specialRulerMode = mode,
                                    specialRulerEditing = mode != SpecialRulerMode.NONE,
                                    specialRulerCenterX = .5f,
                                    specialRulerCenterY = .5f,
                                    specialRulerAngleDegrees = 0f,
                                    perspectiveGuideMode = if (mode == SpecialRulerMode.NONE) settings.perspectiveGuideMode else PerspectiveGuideMode.NONE,
                                    perspectiveGuideEditing = if (mode == SpecialRulerMode.NONE) settings.perspectiveGuideEditing else false,
                                ))
                            },
                        )
                    }
                    if (settings.specialRulerMode != SpecialRulerMode.NONE) {
                        DropdownMenuItem(
                            text = { Text(tr("Drawing Assist", "绘图辅助")) },
                            trailingIcon = { if (settings.specialRulerAssisted) Icon(Icons.Outlined.Check, null) },
                            onClick = { exportMenu = false; onSettingsChange(settings.copy(specialRulerAssisted = !settings.specialRulerAssisted)) },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Edit ruler", "编辑尺子")) },
                            trailingIcon = { if (settings.specialRulerEditing) Icon(Icons.Outlined.Check, null) },
                            onClick = { exportMenu = false; onSettingsChange(settings.copy(specialRulerEditing = !settings.specialRulerEditing)) },
                        )
                        DropdownMenuItem(
                            text = {
                                Column(Modifier.width(230.dp)) {
                                    Text(tr("Guide spacing · ${settings.specialRulerSpacing.toInt()}", "辅助线间距 · ${settings.specialRulerSpacing.toInt()}"), fontSize = 11.sp)
                                    Slider(settings.specialRulerSpacing, { onSettingsChange(settings.copy(specialRulerSpacing = it)) }, valueRange = 16f..240f)
                                }
                            },
                            onClick = {},
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(tr("Fit page", "适合页面")) }, leadingIcon = { Icon(Icons.Outlined.CenterFocusStrong, null) }, onClick = { exportMenu = false; fitPage() })
                    DropdownMenuItem(text = { Text(tr("Actual size", "实际大小")) }, onClick = { exportMenu = false; actualSize() })
                    DropdownMenuItem(text = { Text(tr("Fit width", "适合宽度")) }, onClick = { exportMenu = false; fitWidth() })
                    DropdownMenuItem(text = { Text(tr("Reset rotation", "重置旋转")) }, onClick = { exportMenu = false; resetViewRotation() })
                    HorizontalDivider()
                    document.pages.forEachIndexed { index, page -> DropdownMenuItem(text = { Text(tr("Go to page ${index + 1}", "前往第 ${index + 1} 页")) }, onClick = { exportMenu = false; selectPageWithViewport(page.id) }) }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(tr("Export current page PNG", "将当前页导出为 PNG")) }, onClick = { exportMenu = false; onExportPage() })
                    DropdownMenuItem(text = { Text(tr("Export editable Inkloom package", "导出可编辑 Inkloom 文件")) }, onClick = { exportMenu = false; onExportBundle() })
                }
            }
        }
        HorizontalDivider(color = OutlineSoft)
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .clipToBounds(),
        ) {
        val editorWidth = maxWidth
        val workspaceWidthDp = maxWidth.value
        val workspaceHeightDp = maxHeight.value
        val bottomNavigationPadding = 0.dp
        val usableWorkspaceHeightDp = workspaceHeightDp.coerceAtLeast(1f)
        val visiblePagePanelBounds = WorkspacePanelPlacement.clamp(pagePanelBounds, workspaceWidthDp, usableWorkspaceHeightDp, minimumWidthDp = 96f)
        val visibleBrushPanelBounds = WorkspacePanelPlacement.clamp(brushPanelBounds, workspaceWidthDp, usableWorkspaceHeightDp, minimumWidthDp = 176f)
        val visibleLayerPanelBounds = WorkspacePanelPlacement.clamp(layerPanelBounds, workspaceWidthDp, usableWorkspaceHeightDp, minimumWidthDp = 176f)
        LaunchedEffect(visiblePagePanelBounds) { if (pagePanelBounds != visiblePagePanelBounds) pagePanelBounds = visiblePagePanelBounds }
        LaunchedEffect(visibleBrushPanelBounds) { if (brushPanelBounds != visibleBrushPanelBounds) brushPanelBounds = visibleBrushPanelBounds }
        LaunchedEffect(visibleLayerPanelBounds) { if (layerPanelBounds != visibleLayerPanelBounds) layerPanelBounds = visibleLayerPanelBounds }
        val orderedPanelTabs = WorkspacePanelTabs.normalizeOrder(settings.panelTabOrder)
        fun visibleTabsAt(dock: WorkspacePanelDock, includePages: Boolean = true) = orderedPanelTabs.filter { panel ->
            when (panel) {
                WorkspacePanelKind.PAGES -> includePages && settings.showPageRail && settings.pagePanelDock == dock
                // Brushes are a Procreate-style pop-over. They never consume canvas width or
                // participate in dock groups, which also keeps the input viewport stable.
                WorkspacePanelKind.BRUSHES -> false
                WorkspacePanelKind.LAYERS -> layersOpen && settings.layerPanelDock == dock
            }
        }
        val leftPanelTabs = visibleTabsAt(WorkspacePanelDock.LEFT, editorWidth >= 700.dp)
        val rightPanelTabs = visibleTabsAt(WorkspacePanelDock.RIGHT, editorWidth >= 700.dp)
        val bottomPanelTabs = visibleTabsAt(WorkspacePanelDock.BOTTOM)
        val freePanelTabs = visibleTabsAt(WorkspacePanelDock.FREE)
        fun resolvedActive(dock: WorkspacePanelDock, tabs: List<WorkspacePanelKind>): WorkspacePanelKind? =
            WorkspacePanelTabs.active(settings, dock).takeIf(tabs::contains) ?: tabs.firstOrNull()
        val leftActivePanel = resolvedActive(WorkspacePanelDock.LEFT, leftPanelTabs)
        val rightActivePanel = resolvedActive(WorkspacePanelDock.RIGHT, rightPanelTabs)
        val bottomActivePanel = resolvedActive(WorkspacePanelDock.BOTTOM, bottomPanelTabs)
        val freeActivePanel = resolvedActive(WorkspacePanelDock.FREE, freePanelTabs)
        val leftPanelsStacked = WorkspacePanelTabs.isStacked(settings, WorkspacePanelDock.LEFT) && leftPanelTabs.size > 1
        val rightPanelsStacked = WorkspacePanelTabs.isStacked(settings, WorkspacePanelDock.RIGHT) && rightPanelTabs.size > 1
        val bottomPanelsStacked = WorkspacePanelTabs.isStacked(settings, WorkspacePanelDock.BOTTOM) && bottomPanelTabs.size > 1
        val freePanelsStacked = WorkspacePanelTabs.isStacked(settings, WorkspacePanelDock.FREE) && freePanelTabs.size > 1
        fun showPanel(panel: WorkspacePanelKind, dock: WorkspacePanelDock): Boolean = when (dock) {
            WorkspacePanelDock.LEFT -> !leftPanelsStacked || leftActivePanel == panel
            WorkspacePanelDock.RIGHT -> !rightPanelsStacked || rightActivePanel == panel
            WorkspacePanelDock.BOTTOM -> !bottomPanelsStacked || bottomActivePanel == panel
            WorkspacePanelDock.FREE -> !freePanelsStacked || freeActivePanel == panel
        }
        fun setDockStacked(dock: WorkspacePanelDock, stacked: Boolean, tabs: List<WorkspacePanelKind>) {
            var next = WorkspacePanelTabs.withStacked(settings, dock, stacked)
            if (stacked && WorkspacePanelTabs.active(next, dock) !in tabs) tabs.firstOrNull()?.let { next = WorkspacePanelTabs.withActive(next, dock, it) }
            onSettingsChange(next)
        }
        fun setActivePanel(dock: WorkspacePanelDock, panel: WorkspacePanelKind) =
            onSettingsChange(WorkspacePanelTabs.withActive(settings, dock, panel))
        fun movePanelTab(panel: WorkspacePanelKind, delta: Int) =
            onSettingsChange(settings.copy(panelTabOrder = WorkspacePanelTabs.move(settings.panelTabOrder, panel, delta)))
        fun minimumPanelWidth(panel: WorkspacePanelKind): Float = when (panel) {
            WorkspacePanelKind.PAGES -> 96f
            WorkspacePanelKind.BRUSHES -> 176f
            WorkspacePanelKind.LAYERS -> 176f
        }
        fun panelName(panel: WorkspacePanelKind): String = when (panel) {
            WorkspacePanelKind.PAGES -> "pages"
            WorkspacePanelKind.BRUSHES -> "brushes"
            WorkspacePanelKind.LAYERS -> "layers"
        }
        fun setPanelBounds(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds) {
            when (panel) {
                WorkspacePanelKind.PAGES -> pagePanelBounds = bounds
                WorkspacePanelKind.BRUSHES -> brushPanelBounds = bounds
                WorkspacePanelKind.LAYERS -> layerPanelBounds = bounds
            }
        }
        fun panelBounds(panel: WorkspacePanelKind): WorkspacePanelBounds = when (panel) {
            WorkspacePanelKind.PAGES -> pagePanelBounds
            WorkspacePanelKind.BRUSHES -> brushPanelBounds
            WorkspacePanelKind.LAYERS -> layerPanelBounds
        }
        fun dockedPanelWidth(panel: WorkspacePanelKind): Float = AdaptivePanelLayout.dockedSidePanelWidthDp(
            panelBounds(panel).widthDp,
            workspaceWidthDp,
            minimumPanelWidth(panel),
        )
        fun resizeDockedPanel(panel: WorkspacePanelKind, dock: WorkspacePanelDock, deltaXPx: Float) {
            val signedDeltaDp = deltaXPx / editorDensity.density * if (dock == WorkspacePanelDock.LEFT) 1f else -1f
            val current = panelBounds(panel)
            val nextWidth = AdaptivePanelLayout.dockedSidePanelWidthDp(
                current.widthDp + signedDeltaDp,
                workspaceWidthDp,
                minimumPanelWidth(panel),
            )
            setPanelBounds(panel, current.copy(widthDp = nextWidth))
        }
        fun settingsWithPanelBounds(base: AppSettings, panel: WorkspacePanelKind, bounds: WorkspacePanelBounds): AppSettings = when (panel) {
            WorkspacePanelKind.PAGES -> base.copy(pagePanelXDp = bounds.xDp, pagePanelYDp = bounds.yDp, pagePanelWidthDp = bounds.widthDp, pagePanelHeightDp = bounds.heightDp)
            WorkspacePanelKind.BRUSHES -> base.copy(brushPanelXDp = bounds.xDp, brushPanelYDp = bounds.yDp, brushPanelWidthDp = bounds.widthDp, brushPanelHeightDp = bounds.heightDp)
            WorkspacePanelKind.LAYERS -> base.copy(layerPanelXDp = bounds.xDp, layerPanelYDp = bounds.yDp, layerPanelWidthDp = bounds.widthDp, layerPanelHeightDp = bounds.heightDp)
        }
        fun settingsWithPanelDock(base: AppSettings, panel: WorkspacePanelKind, dock: WorkspacePanelDock): AppSettings = when (panel) {
            WorkspacePanelKind.PAGES -> base.copy(pagePanelDock = dock)
            WorkspacePanelKind.BRUSHES -> base.copy(brushPanelDock = dock)
            WorkspacePanelKind.LAYERS -> base.copy(layerPanelDock = dock)
        }
        fun changePanelDock(panel: WorkspacePanelKind, dock: WorkspacePanelDock) {
            var next = settingsWithPanelDock(settings, panel, dock)
            if (dock == WorkspacePanelDock.FREE && panel !in freePanelTabs && settings.freeDockStacked && freePanelTabs.isNotEmpty()) {
                val members = (freePanelTabs + panel).distinct()
                val groupMinimum = members.maxOf(::minimumPanelWidth)
                val anchor = panelBounds(freeActivePanel ?: freePanelTabs.first())
                val shared = WorkspacePanelPlacement.clamp(anchor.copy(widthDp = anchor.widthDp.coerceAtLeast(groupMinimum)), workspaceWidthDp, usableWorkspaceHeightDp, groupMinimum)
                members.forEach { member ->
                    setPanelBounds(member, shared)
                    next = settingsWithPanelBounds(next, member, shared)
                }
                next = WorkspacePanelTabs.withActive(next, WorkspacePanelDock.FREE, panel)
                activeFloatingPanel = panelName(panel)
            } else if (dock != WorkspacePanelDock.FREE) {
                if (WorkspacePanelTabs.isStacked(next, dock)) next = WorkspacePanelTabs.withActive(next, dock, panel)
                if (settings.freeDockActivePanel == panel) freePanelTabs.firstOrNull { it != panel }?.let {
                    next = WorkspacePanelTabs.withActive(next, WorkspacePanelDock.FREE, it)
                }
            }
            onSettingsChange(next)
        }
        fun persistFloatingPanels(panels: List<WorkspacePanelKind>) {
            var next = settings
            panels.distinct().forEach { member -> next = settingsWithPanelBounds(next, member, panelBounds(member)) }
            onSettingsChange(next)
        }
        fun persistDockedPanel(panel: WorkspacePanelKind) = persistFloatingPanels(listOf(panel))
        fun beginPanelDrag(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds) {
            draggingPanel = panel
            panelDragOrigin = bounds
            panelGroupDragOrigins = if (freePanelsStacked && panel in freePanelTabs) freePanelTabs.associateWith(::panelBounds) else mapOf(panel to bounds)
            panelDockPreview = null
            panelGroupPreviewTarget = null
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
        }
        fun moveFloatingPanel(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds, deltaXDp: Float, deltaYDp: Float): WorkspacePanelBounds {
            val next = WorkspacePanelPlacement.move(bounds, deltaXDp, deltaYDp, workspaceWidthDp, usableWorkspaceHeightDp, minimumPanelWidth(panel))
            if (freePanelsStacked && panel in freePanelTabs) {
                val actualX = next.xDp - bounds.xDp
                val actualY = next.yDp - bounds.yDp
                freePanelTabs.forEach { member ->
                    val memberBounds = panelBounds(member)
                    setPanelBounds(member, WorkspacePanelPlacement.move(memberBounds, actualX, actualY, workspaceWidthDp, usableWorkspaceHeightDp, minimumPanelWidth(member)))
                }
            } else {
                setPanelBounds(panel, next)
            }
            return next
        }
        fun resizeFloatingPanel(
            panel: WorkspacePanelKind,
            bounds: WorkspacePanelBounds,
            leftDeltaDp: Float,
            topDeltaDp: Float,
            rightDeltaDp: Float,
            bottomDeltaDp: Float,
        ): WorkspacePanelBounds {
            val groupMinimum = if (freePanelsStacked && panel in freePanelTabs) freePanelTabs.maxOf(::minimumPanelWidth) else minimumPanelWidth(panel)
            val next = WorkspacePanelPlacement.resizeEdges(
                bounds,
                leftDeltaDp,
                topDeltaDp,
                rightDeltaDp,
                bottomDeltaDp,
                workspaceWidthDp,
                usableWorkspaceHeightDp,
                groupMinimum,
            )
            if (freePanelsStacked && panel in freePanelTabs) freePanelTabs.forEach { setPanelBounds(it, next) } else setPanelBounds(panel, next)
            return next
        }
        fun updatePanelDockPreview(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds, deltaXDp: Float, deltaYDp: Float) {
            panelDragDeltaXDp += deltaXDp
            panelDragDeltaYDp += deltaYDp
            panelDockPreview = WorkspacePanelDocking.target(bounds, workspaceWidthDp, usableWorkspaceHeightDp, panelDragDeltaXDp, panelDragDeltaYDp)
            panelGroupPreviewTarget = if (panelDockPreview == null && !freePanelsStacked) {
                WorkspacePanelGrouping.target(panel, bounds, freePanelTabs.associateWith(::panelBounds))
            } else null
        }
        fun finishPanelDrag(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds, persist: (WorkspacePanelBounds) -> Unit) {
            val target = panelDockPreview
            val groupTarget = panelGroupPreviewTarget
            val movingGroup = freePanelsStacked && panel in freePanelTabs
            draggingPanel = null
            panelDragOrigin = null
            panelGroupDragOrigins = emptyMap()
            panelDockPreview = null
            panelGroupPreviewTarget = null
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
            if (target != null) {
                val members = if (movingGroup) freePanelTabs else listOf(panel)
                var next = settings
                members.forEach { member -> next = settingsWithPanelDock(next, member, target) }
                if (movingGroup) next = WorkspacePanelTabs.withStacked(next.copy(freeDockStacked = false), target, true)
                if (WorkspacePanelTabs.isStacked(next, target)) next = WorkspacePanelTabs.withActive(next, target, panel)
                onSettingsChange(next)
                return
            }
            if (groupTarget != null) {
                val members = (freePanelTabs + panel).distinct()
                val groupMinimum = members.maxOf(::minimumPanelWidth)
                val anchor = panelBounds(groupTarget)
                val shared = WorkspacePanelPlacement.clamp(
                    anchor.copy(widthDp = anchor.widthDp.coerceAtLeast(groupMinimum)),
                    workspaceWidthDp,
                    usableWorkspaceHeightDp,
                    groupMinimum,
                )
                members.forEach { setPanelBounds(it, shared) }
                var next = settings.copy(freeDockStacked = true, freeDockActivePanel = panel)
                members.forEach { member -> next = settingsWithPanelBounds(next, member, shared) }
                activeFloatingPanel = panelName(panel)
                onSettingsChange(next)
                return
            }
            if (movingGroup) persistFloatingPanels(freePanelTabs) else persist(bounds)
        }
        fun cancelPanelDrag(panel: WorkspacePanelKind, restore: (WorkspacePanelBounds) -> Unit) {
            if (draggingPanel == panel) {
                if (panelGroupDragOrigins.isNotEmpty()) panelGroupDragOrigins.forEach(::setPanelBounds)
                else panelDragOrigin?.let(restore)
            }
            draggingPanel = null
            panelDragOrigin = null
            panelGroupDragOrigins = emptyMap()
            panelDockPreview = null
            panelGroupPreviewTarget = null
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
        }
        fun selectFloatingPanel(panel: WorkspacePanelKind) {
            activeFloatingPanel = panelName(panel)
            onSettingsChange(WorkspacePanelTabs.withActive(settings, WorkspacePanelDock.FREE, panel))
        }
        fun separateFloatingPanels() {
            var next = settings.copy(freeDockStacked = false)
            freePanelTabs.forEachIndexed { index, panel ->
                val base = panelBounds(panel)
                val separated = WorkspacePanelPlacement.move(base, index * 24f, index * 24f, workspaceWidthDp, usableWorkspaceHeightDp, minimumPanelWidth(panel))
                setPanelBounds(panel, separated)
                next = settingsWithPanelBounds(next, panel, separated)
            }
            onSettingsChange(next)
        }
        fun clearDockedPanelDrag() {
            draggingPanel = null
            panelDragOrigin = null
            dockedPanelOriginDock = null
            dockedPanelPreviewBounds = null
            dockedPanelTargetDock = null
            panelGroupPreviewTarget = null
            panelGroupDragOrigins = emptyMap()
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
        }
        fun beginDockedPanelDrag(panel: WorkspacePanelKind, dock: WorkspacePanelDock, savedBounds: WorkspacePanelBounds) {
            if (dock == WorkspacePanelDock.FREE) return
            val initialBounds = WorkspacePanelDocking.initialFloatingBounds(
                dock,
                savedBounds,
                workspaceWidthDp,
                usableWorkspaceHeightDp,
                minimumPanelWidth(panel),
            )
            draggingPanel = panel
            panelDragOrigin = initialBounds
            dockedPanelOriginDock = dock
            dockedPanelPreviewBounds = initialBounds
            dockedPanelTargetDock = null
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
        }
        fun updateDockedPanelDrag(deltaXPx: Float, deltaYPx: Float) {
            val panel = draggingPanel ?: return
            if (dockedPanelOriginDock == null) return
            val deltaXDp = deltaXPx / editorDensity.density
            val deltaYDp = deltaYPx / editorDensity.density
            panelDragDeltaXDp += deltaXDp
            panelDragDeltaYDp += deltaYDp
            val current = dockedPanelPreviewBounds ?: panelDragOrigin ?: return
            val next = WorkspacePanelPlacement.move(
                current,
                deltaXDp,
                deltaYDp,
                workspaceWidthDp,
                usableWorkspaceHeightDp,
                minimumPanelWidth(panel),
            )
            dockedPanelPreviewBounds = next
            dockedPanelTargetDock = WorkspacePanelDocking.target(
                next,
                workspaceWidthDp,
                usableWorkspaceHeightDp,
                panelDragDeltaXDp,
                panelDragDeltaYDp,
            )?.takeIf { it != dockedPanelOriginDock }
        }
        fun finishDockedPanelDrag(panel: WorkspacePanelKind, originDock: WorkspacePanelDock) {
            val preview = dockedPanelPreviewBounds
            val target = dockedPanelTargetDock
            val detached = draggingPanel == panel && dockedPanelOriginDock == originDock &&
                WorkspacePanelDocking.shouldDetach(originDock, panelDragDeltaXDp, panelDragDeltaYDp)
            clearDockedPanelDrag()
            if (!detached || preview == null) return
            activeFloatingPanel = panelName(panel)
            if (target != null) {
                var next = when (panel) {
                    WorkspacePanelKind.PAGES -> settings.copy(pagePanelDock = target)
                    WorkspacePanelKind.BRUSHES -> settings.copy(brushPanelDock = target)
                    WorkspacePanelKind.LAYERS -> settings.copy(layerPanelDock = target)
                }
                if (WorkspacePanelTabs.isStacked(next, target)) next = WorkspacePanelTabs.withActive(next, target, panel)
                onSettingsChange(next)
                return
            }
            val joinsFloatingGroup = settings.freeDockStacked && freePanelTabs.isNotEmpty()
            val floatingMembers = if (joinsFloatingGroup) (freePanelTabs + panel).distinct() else listOf(panel)
            val floatingBounds = if (joinsFloatingGroup) {
                val groupMinimum = floatingMembers.maxOf(::minimumPanelWidth)
                val anchor = panelBounds(freeActivePanel ?: freePanelTabs.first())
                WorkspacePanelPlacement.clamp(anchor.copy(widthDp = anchor.widthDp.coerceAtLeast(groupMinimum)), workspaceWidthDp, usableWorkspaceHeightDp, groupMinimum)
            } else preview
            floatingMembers.forEach { setPanelBounds(it, floatingBounds) }
            var next = when (panel) {
                WorkspacePanelKind.PAGES -> settings.copy(
                    pagePanelDock = WorkspacePanelDock.FREE,
                    pagePanelXDp = floatingBounds.xDp,
                    pagePanelYDp = floatingBounds.yDp,
                    pagePanelWidthDp = floatingBounds.widthDp,
                    pagePanelHeightDp = floatingBounds.heightDp,
                    freeDockActivePanel = if (joinsFloatingGroup) panel else settings.freeDockActivePanel,
                )
                WorkspacePanelKind.BRUSHES -> settings.copy(
                    brushPanelDock = WorkspacePanelDock.FREE,
                    brushPanelXDp = floatingBounds.xDp,
                    brushPanelYDp = floatingBounds.yDp,
                    brushPanelWidthDp = floatingBounds.widthDp,
                    brushPanelHeightDp = floatingBounds.heightDp,
                    freeDockActivePanel = if (joinsFloatingGroup) panel else settings.freeDockActivePanel,
                )
                WorkspacePanelKind.LAYERS -> settings.copy(
                    layerPanelDock = WorkspacePanelDock.FREE,
                    layerPanelXDp = floatingBounds.xDp,
                    layerPanelYDp = floatingBounds.yDp,
                    layerPanelWidthDp = floatingBounds.widthDp,
                    layerPanelHeightDp = floatingBounds.heightDp,
                    freeDockActivePanel = if (joinsFloatingGroup) panel else settings.freeDockActivePanel,
                )
            }
            if (joinsFloatingGroup) floatingMembers.forEach { member -> next = settingsWithPanelBounds(next, member, floatingBounds) }
            onSettingsChange(next)
        }
        val bottomPanelCount = listOf(
            settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.BOTTOM,
            layersOpen && settings.layerPanelDock == WorkspacePanelDock.BOTTOM,
        ).count { it }
        val bottomHasExpandedPanel =
            (WorkspacePanelKind.PAGES in bottomPanelTabs && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.BOTTOM) && settings.pagePanelMode == WorkspacePanelMode.EXPANDED) ||
            (WorkspacePanelKind.LAYERS in bottomPanelTabs && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.BOTTOM) && settings.layerPanelMode == WorkspacePanelMode.EXPANDED)
        val bottomPanelContentHeight = if (bottomHasExpandedPanel) (workspaceHeightDp * .38f).coerceIn(180f, 320f).dp else 82.dp
        val bottomPanelHeight = bottomPanelContentHeight + bottomNavigationPadding
        val bottomPanelsDescription = tr("Bottom workspace panels", "底部工作区面板")
        Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (leftPanelTabs.size > 1) DockedPanelTabStrip(
                dock = WorkspacePanelDock.LEFT,
                panels = leftPanelTabs,
                active = leftActivePanel,
                stacked = leftPanelsStacked,
                onSelect = { setActivePanel(WorkspacePanelDock.LEFT, it) },
                onMove = ::movePanelTab,
                onStacked = { setDockStacked(WorkspacePanelDock.LEFT, it, leftPanelTabs) },
            )
            if (editorWidth >= 700.dp && settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.LEFT && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.LEFT)) PageRail(
                document, documents, selectedPage.id, settings.pagePanelDock, assets,
                onDock = { changePanelDock(WorkspacePanelKind.PAGES, it) },
                onClose = { onSettingsChange(settings.copy(showPageRail = false)) },
                pageTemplates = pageTemplates,
                onSelect = ::selectPageWithViewport, onOpenLayers = { pageId -> setLayersPanelOpen(true, pageId) }, onAdd = onAddPage, onDuplicate = onDuplicatePage, onDelete = onDeletePage, onMove = onMovePage, onReorder = onReorderPages, onTransfer = onTransferPage,
                onApplyTemplate = onApplyPageTemplate, onApplyTemplates = onApplyPageTemplates, onImportTemplates = onImportPageTemplates, onToggleTemplateFavorite = onTogglePageTemplateFavorite,
                onSetDefaultTemplate = onSetNotebookDefaultTemplate, onSaveAsTemplate = onSavePageAsTemplate, onDeleteTemplate = onDeletePageTemplate,
                onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata,
                onClearPageInk, onSetPageCrop, onRotatePage,
                panelMode = settings.pagePanelMode,
                panelLocked = settings.pagePanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(pagePanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(pagePanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.LEFT, pagePanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.LEFT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
                modifier = Modifier.width(dockedPanelWidth(WorkspacePanelKind.PAGES).dp).fillMaxHeight().padding(6.dp),
            )
            if (editorWidth >= 700.dp && settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.LEFT && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.LEFT) && settings.pagePanelMode == WorkspacePanelMode.EXPANDED && !settings.pagePanelLocked) DockedSidebarResizeHandle(
                tr("Resize pages sidebar", "调整页面侧栏宽度"),
                onResize = { resizeDockedPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.LEFT, it) },
                onResizeEnd = { persistDockedPanel(WorkspacePanelKind.PAGES) },
            )
            if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.LEFT && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.LEFT)) LayerPanel(
                page = selectedPage,
                activeLayerId = activeLayerId,
                brushColor = brushColor,
                dock = settings.layerPanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.LAYERS, it) },
                onClose = { layersOpen = false; onSettingsChange(settings.copy(showLayerPanel = false)) },
                onColor = ::changeBrushColor,
                onSelect = { activeLayerId = it },
                onAdd = { onAddLayer(document.id, selectedPage.id, "Layer") },
                onAddFolder = { onAddLayerFolder(document.id, selectedPage.id) },
                onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                onOpacity = { layerId, value -> onSetLayerOpacity(document.id, selectedPage.id, layerId, value) },
                onBlendMode = { layerId, mode -> onSetLayerBlendMode(document.id, selectedPage.id, layerId, mode) },
                onToggleAlphaLock = { onToggleLayerAlphaLock(document.id, selectedPage.id, it) },
                onToggleClippedToBelow = { onToggleLayerClippedToBelow(document.id, selectedPage.id, it) },
                onToggleReference = { onToggleLayerReference(document.id, selectedPage.id, it) },
                onToggleDraft = { onToggleLayerDraft(document.id, selectedPage.id, it) },
                onMaskCommand = ::performLayerMaskCommand,
                onMoveToFolder = { layerId, folderId -> onMoveLayerToFolder(document.id, selectedPage.id, layerId, folderId) },
                onToggleFolderCollapsed = { onToggleLayerFolderCollapsed(document.id, selectedPage.id, it) },
                onMove = { layerId, delta -> onMoveLayer(document.id, selectedPage.id, layerId, delta) },
                onDuplicate = { layerId -> onDuplicateLayer(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onMergeDown = { layerId -> onMergeLayerDown(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onDelete = { onDeleteLayer(document.id, selectedPage.id, it) },
                panelMode = settings.layerPanelMode,
                panelLocked = settings.layerPanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(layerPanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(layerPanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.LEFT, layerPanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.LEFT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
                modifier = Modifier.width(dockedPanelWidth(WorkspacePanelKind.LAYERS).dp).fillMaxHeight().background(Color(0xFFF4F4F4)).border(1.dp, OutlineSoft).padding(2.dp),
            )
            if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.LEFT && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.LEFT) && settings.layerPanelMode == WorkspacePanelMode.EXPANDED && !settings.layerPanelLocked) DockedSidebarResizeHandle(
                tr("Resize layers sidebar", "调整图层侧栏宽度"),
                onResize = { resizeDockedPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.LEFT, it) },
                onResizeEnd = { persistDockedPanel(WorkspacePanelKind.LAYERS) },
            )
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(Workspace)
                    .onGloballyPositioned { coordinates ->
                        viewportWidthPx = coordinates.size.width.toFloat()
                        viewportHeightPx = coordinates.size.height.toFloat()
                    },
                contentAlignment = Alignment.Center,
            ) {
                val toolbarAreaWidth = maxWidth
                val toolbarAreaHeight = maxHeight
                PaperCanvas(
                    modifier = Modifier.fillMaxHeight(.88f).aspectRatio((PageCropTransform.visibleWidth(selectedPage) / PageCropTransform.visibleHeight(selectedPage).coerceAtLeast(1f)).coerceIn(.35f, 2.8f)),
                    tool = tool,
                    onView = { view ->
                        canvasView = view
                        view.onSelectionTransformChanged = { selectionTransformState = it }
                        view.onLayerMaskSurfaceCommitted = { layerId, snapshot ->
                            onLayerMaskCommand(document.id, selectedPage.id, layerId, LayerMaskCommand.REPLACE_SURFACE, snapshot)
                        }
                    },
                    page = paintingPage,
                    documentId = document.id,
                    onSetLayerRasterTileSurface = onSetLayerRasterTileSurface,
                    onElement = onElement,
                    onRemoveElement = onRemoveElement,
                    onDuplicateElement = onDuplicateElement,
                    selectedElementId = selectedElementId,
                    onSelectElement = { selectedElementId = it },
                    assets = assets,
                    selectionRuntime = selectionRuntime,
                    settings = settings,
                    viewportState = ViewportState(zoom, panX, panY, viewRotation),
                    onZoom = { factor, focusX, focusY ->
                        val view = canvasView ?: return@PaperCanvas
                        val current = ViewportState(view.viewportScale, view.viewportPanX, view.viewportPanY, view.viewportRotationDegrees)
                        val next = ViewportTransform.zoomAroundScreen(
                            current,
                            factor,
                            focusX - view.width / 2f,
                            focusY - view.height / 2f,
                            geometry = currentViewportGeometry(),
                        )
                        applyViewport(next)
                        view.apply {
                            viewportScale = next.scale
                            viewportPanX = next.panX
                            viewportPanY = next.panY
                            viewportRotationDegrees = next.rotationDegrees
                        }
                    },
                    onPinch = { factor, previousFocusX, previousFocusY, currentFocusX, currentFocusY ->
                        val view = canvasView ?: return@PaperCanvas
                        val centerOffsetX = view.width / 2f
                        val centerOffsetY = view.height / 2f
                        val next = ViewportTransform.pinchByScreen(
                            ViewportState(view.viewportScale, view.viewportPanX, view.viewportPanY, view.viewportRotationDegrees),
                            factor,
                            previousFocusX - centerOffsetX,
                            previousFocusY - centerOffsetY,
                            currentFocusX - centerOffsetX,
                            currentFocusY - centerOffsetY,
                            geometry = currentViewportGeometry(),
                        )
                        applyViewport(next)
                        view.apply {
                            viewportScale = next.scale
                            viewportPanX = next.panX
                            viewportPanY = next.panY
                            viewportRotationDegrees = next.rotationDegrees
                        }
                    },
                    onPan = { dx, dy ->
                        val view = canvasView ?: return@PaperCanvas
                        val current = ViewportState(view.viewportScale, view.viewportPanX, view.viewportPanY, view.viewportRotationDegrees)
                        val next = ViewportTransform.panByScreen(current, dx, dy, geometry = currentViewportGeometry())
                        applyViewport(next)
                        view.apply { viewportPanX = next.panX; viewportPanY = next.panY }
                    },
                    onRotate = { delta ->
                        val view = canvasView ?: return@PaperCanvas
                        val current = ViewportState(view.viewportScale, view.viewportPanX, view.viewportPanY, view.viewportRotationDegrees)
                        val next = ViewportTransform.rotateBy(current, delta, currentViewportGeometry())
                        applyViewport(next)
                        view.apply {
                            viewportRotationDegrees = next.rotationDegrees
                            viewportPanX = next.panX
                            viewportPanY = next.panY
                        }
                    },
                    onUndoGesture = { canvasView?.undo() },
                    onRedoGesture = { canvasView?.redo() },
                    onCanvasContact = { if (brushesOpen) onToggleBrushes() },
                    onColorDropCommitted = { dropped ->
                        offeredContinueFillColor = dropped
                        continueFilling = false
                    },
                    activeBrush = activeBrush,
                    brushSize = brushSize,
                    brushOpacity = brushOpacity,
                    brushColor = brushColor,
                    secondaryBrushColor = backgroundColor,
                    shapeKind = insertShape,
                    activeLayerId = activeLayerId,
                    eraserPreset = settings.eraserPreset,
                    forceNavigation = handToolEnabled || handToolHeld || rotateToolEnabled || rotateToolHeld,
                    rotateNavigation = rotateToolEnabled || rotateToolHeld,
                    searchBounds = searchFocus?.takeIf { it.pageId == selectedPage.id }?.bounds ?: pdfLinkFocus?.takeIf { it.first == selectedPage.id }?.second,
                    onPdfGotoLink = { link ->
                        document.pages.firstOrNull { it.id == link.destinationPageId }?.let { target ->
                            searchFocus = null
                            pdfLinkFocus = target.id to PdfGotoLinkNavigation.destinationBounds(link)
                            selectPageWithViewport(target.id)
                            if (link.destinationZoom > 0f) zoom = link.destinationZoom.coerceIn(ViewportTransform.MINIMUM_SCALE, ViewportTransform.MAXIMUM_SCALE)
                        }
                    },
                    documents = documents,
                    onOpenInternalLink = onOpenInternalLink,
                    onInputDiagnostic = { kind, pressure, tilt, buttons -> inputDiagnostic = "$kind  pressure ${"%.3f".format(pressure)}  tilt ${"%.2f".format(Math.toDegrees(tilt.toDouble()))}°  buttons $buttons" },
                    onColorSampled = ::changeBrushColor,
                    onBrushSizeAdjusted = { raw ->
                        changeCurrentBrushSize(raw)
                    },
                    onKeyInputDiagnostic = { diagnostic ->
                        val keyName = KeyEvent.keyCodeToString(diagnostic.keyCode).removePrefix("KEYCODE_")
                        val device = diagnostic.deviceName ?: unknownInputDeviceText
                        val descriptor = diagnostic.deviceDescriptor ?: "—"
                        val action = diagnostic.matchedAction?.name?.lowercase()?.replace('_', ' ') ?: unmappedShortcutText
                        inputDiagnostic = "$device · $keyName · scan ${diagnostic.scanCode} · meta 0x${diagnostic.metaState.toString(16)} · source 0x${diagnostic.source.toString(16)} · id ${diagnostic.deviceId} · $descriptor · $action · ${diagnostic.phase.name.lowercase()}"
                    },
                    onMotionInputDiagnostic = { diagnostic ->
                        val device = diagnostic.deviceName ?: unknownInputDeviceText
                        val descriptor = diagnostic.deviceDescriptor ?: "—"
                        val action = diagnostic.matchedAction?.name?.lowercase()?.replace('_', ' ') ?: unmappedShortcutText
                        val axis = diagnostic.axisCode?.let { "axis $it ${"%.3f".format(diagnostic.axisValue)}" } ?: "button ${diagnostic.actionButton}"
                        inputDiagnostic = "$device · motion ${diagnostic.action} · $axis · state ${diagnostic.buttonState} · source 0x${diagnostic.source.toString(16)} · id ${diagnostic.deviceId} · $descriptor · $action"
                    },
                    onShortcutAction = { activation ->
                        val action = activation.action
                        fun selectImportedBrush(brush: ImportedBrush, persistent: Boolean = true) {
                            activateWorkspaceBrush(brush, persistSelection = persistent)
                        }

                        fun updateBrushSize(multiplier: Float) {
                            changeCurrentBrushSize(brushSize * multiplier)
                        }

                        fun updateBrushOpacity(delta: Float) {
                            changeCurrentBrushOpacity(brushOpacity + delta)
                        }

                        fun enterEraser() {
                            rememberDrawingSelection()
                            val eraser = activeBrush?.takeIf(BrushRoleClassifier::isEraser)
                                ?: brushes.filter(BrushRoleClassifier::isEraser).maxByOrNull(ImportedBrush::lastUsedAt)
                            activeBrush = eraser
                            selectedBrushId = eraser?.id
                            tool = EditorTool.ERASER
                            brushSize = savedBrushSize(eraser, eraser?.defaultSize ?: settings.eraserSize, EditorTool.ERASER)
                            brushOpacity = savedBrushOpacity(eraser, eraser?.defaultOpacity ?: settings.eraserPreset.opacity, EditorTool.ERASER)
                            syncCanvasInput(prepareAssets = eraser != null)
                        }

                        fun selectBrushGroup(
                            group: ShortcutBrushGroup,
                            fallbackToSystemPen: Boolean = false,
                            persistent: Boolean = true,
                        ) {
                            val targetPath = settings.shortcutBrushGroupTargets.forGroup(group)
                            ShortcutBrushSelection.preferred(availableBrushes, group, targetPath)?.let {
                                selectImportedBrush(it, persistent)
                            } ?: if (fallbackToSystemPen) {
                                requireNotNull(SystemBrushCatalog.byId("system-g-pen")).let {
                                    selectImportedBrush(it, persistent)
                                }
                            } else {
                                availableBrushes.filterNot { it.hidden }.maxByOrNull { it.lastUsedAt }?.let {
                                    selectImportedBrush(it, persistent)
                                }
                            }
                        }

                        fun applyQuickSlot(index: Int, temporary: Boolean) {
                            val slot = ToolboxQuickSlots.normalize(settings.toolboxQuickSlots)[index]
                            when (slot.kind) {
                                ToolboxQuickSlotKind.EMPTY -> Unit
                                ToolboxQuickSlotKind.BRUSH, ToolboxQuickSlotKind.BRUSH_GROUP -> {
                                    if (temporary) rememberDrawingSelection()
                                    ToolboxQuickSlots.preferredBrush(slot, availableBrushes)?.let {
                                        selectImportedBrush(it, persistent = !temporary)
                                    }
                                }
                                ToolboxQuickSlotKind.TOOL_PRESET -> {
                                    if (temporary) rememberDrawingSelection()
                                    val selected = ToolboxQuickSlots.preferredBrush(slot, brushes)
                                    if (slot.brushId.isBlank()) {
                                        selectedBrushId = null
                                        activeBrush = null
                                        tool = EditorTool.PEN
                                        activeLayerId = LayerIds.INK
                                    } else selected?.let { selectImportedBrush(it, persistent = !temporary) } ?: return
                                    brushSize = slot.size
                                    brushOpacity = slot.opacity
                                    changeBrushColor(slot.colorArgb)
                                    syncCanvasInput(prepareAssets = activeBrush != null)
                                }
                            }
                        }

                        if (activation.phase == ShortcutPhase.RELEASE) {
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_RULER) onSettingsChange(settings.copy(rulerEnabled = rulerBeforeShortcut))
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_GRID_SNAP) onSettingsChange(settings.copy(gridSnapEnabled = gridBeforeShortcut))
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_ALIGNMENT_GUIDES) onSettingsChange(settings.copy(alignmentGuidesEnabled = guidesBeforeShortcut))
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.HAND_TOOL) handToolHeld = false
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.ROTATE_VIEW_TOOL) rotateToolHeld = false
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_ERASER && tool == EditorTool.ERASER) restorePreviousDrawingTool()
                            if (activation.trigger == ShortcutTrigger.HOLD && action in setOf(ShortcutAction.PEN_OR_PENCIL_GROUP, ShortcutAction.PEN_GROUP, ShortcutAction.PENCIL_GROUP, ShortcutAction.BRUSH_GROUP, ShortcutAction.HIGHLIGHTER_TOOL, ShortcutAction.SELECT_TOOL, ShortcutAction.EYEDROPPER_TOOL, ShortcutAction.LASER_TOOL)) restorePreviousDrawingTool()
                            if (activation.trigger == ShortcutTrigger.HOLD) ToolboxQuickSlots.indexOf(action)?.let { index ->
                                val slot = ToolboxQuickSlots.normalize(settings.toolboxQuickSlots)[index]
                                when (slot.kind) {
                                    ToolboxQuickSlotKind.BRUSH, ToolboxQuickSlotKind.BRUSH_GROUP, ToolboxQuickSlotKind.TOOL_PRESET -> restorePreviousDrawingTool()
                                    ToolboxQuickSlotKind.EMPTY -> Unit
                                }
                            }
                        } else when (action) {
                            ShortcutAction.PEN_OR_PENCIL_GROUP -> {
                                exitCanvasNavigation()
                                if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection()
                                val target = if (ShortcutBrushSelection.matches(activeBrush, ShortcutBrushGroup.PEN)) ShortcutBrushGroup.PENCIL else ShortcutBrushGroup.PEN
                                selectBrushGroup(target, fallbackToSystemPen = true, persistent = activation.trigger != ShortcutTrigger.HOLD)
                            }
                            ShortcutAction.PEN_GROUP -> { exitCanvasNavigation(); if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection(); selectBrushGroup(ShortcutBrushGroup.PEN, fallbackToSystemPen = true, persistent = activation.trigger != ShortcutTrigger.HOLD) }
                            ShortcutAction.PENCIL_GROUP -> { exitCanvasNavigation(); if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection(); selectBrushGroup(ShortcutBrushGroup.PENCIL, fallbackToSystemPen = true, persistent = activation.trigger != ShortcutTrigger.HOLD) }
                            ShortcutAction.BRUSH_GROUP -> { exitCanvasNavigation(); if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection(); selectBrushGroup(ShortcutBrushGroup.BRUSH, persistent = activation.trigger != ShortcutTrigger.HOLD) }
                            ShortcutAction.TOGGLE_ERASER -> if (activation.trigger == ShortcutTrigger.HOLD) {
                                exitCanvasNavigation()
                                if (tool != EditorTool.ERASER) enterEraser()
                            } else { exitCanvasNavigation(); if (tool == EditorTool.ERASER) restorePreviousDrawingTool() else enterEraser() }
                            ShortcutAction.HIGHLIGHTER_TOOL -> {
                                exitCanvasNavigation()
                                if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection()
                                selectedBrushId = null
                                activeBrush = null
                                tool = EditorTool.HIGHLIGHTER
                                brushSize = savedBrushSize(null, settings.highlighterSize, EditorTool.HIGHLIGHTER)
                                brushOpacity = savedBrushOpacity(null, settings.highlighterOpacity, EditorTool.HIGHLIGHTER)
                                activeLayerId = LayerIds.HIGHLIGHT
                                syncCanvasInput()
                            }
                            ShortcutAction.SELECT_TOOL -> { exitCanvasNavigation(); if (tool != EditorTool.SELECT) rememberDrawingSelection(); tool = EditorTool.SELECT; syncCanvasInput() }
                            ShortcutAction.HAND_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) { rotateToolEnabled = false; rotateToolHeld = false; handToolHeld = true } else { val enable = !handToolEnabled; exitCanvasNavigation(); handToolEnabled = enable }
                            ShortcutAction.ROTATE_VIEW_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) { handToolEnabled = false; handToolHeld = false; rotateToolHeld = true } else { val enable = !rotateToolEnabled; exitCanvasNavigation(); rotateToolEnabled = enable }
                            ShortcutAction.EYEDROPPER_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) {
                                exitCanvasNavigation()
                                rememberDrawingSelection()
                                tool = EditorTool.EYEDROPPER
                            } else {
                                exitCanvasNavigation()
                                if (tool == EditorTool.EYEDROPPER) restorePreviousDrawingTool() else { rememberDrawingSelection(); tool = EditorTool.EYEDROPPER }
                            }
                            ShortcutAction.SHAPE_TOOL -> { exitCanvasNavigation(); tool = EditorTool.SHAPE; shapeInsertOpen = true }
                            ShortcutAction.TEXT_TOOL -> { exitCanvasNavigation(); tool = EditorTool.TEXT; insertText = ""; textInsertOpen = true }
                            ShortcutAction.NOTE_TOOL -> { exitCanvasNavigation(); tool = EditorTool.NOTE; insertText = ""; stickyInsertOpen = true }
                            ShortcutAction.LINK_TOOL -> { exitCanvasNavigation(); tool = EditorTool.LINK; insertLinkTitle = ""; insertLinkUrl = ""; linkInsertOpen = true }
                            ShortcutAction.IMAGE_TOOL -> { exitCanvasNavigation(); imageInsertOpen = true; tool = EditorTool.SELECT }
                            ShortcutAction.LASER_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) {
                                rememberDrawingSelection()
                                exitCanvasNavigation()
                                tool = EditorTool.LASER
                            } else if (tool == EditorTool.LASER) restorePreviousDrawingTool() else {
                                rememberDrawingSelection()
                                exitCanvasNavigation()
                                tool = EditorTool.LASER
                            }
                            ShortcutAction.BRUSH_SIZE_DECREASE -> updateBrushSize(1f / 1.1f)
                            ShortcutAction.BRUSH_SIZE_INCREASE -> updateBrushSize(1.1f)
                            ShortcutAction.OPACITY_DECREASE -> updateBrushOpacity(-.05f)
                            ShortcutAction.OPACITY_INCREASE -> updateBrushOpacity(.05f)
                            ShortcutAction.PREVIOUS_BRUSH -> ShortcutBrushSelection.adjacent(brushes, activeBrush?.id, -1)?.let(::selectImportedBrush)
                            ShortcutAction.NEXT_BRUSH -> ShortcutBrushSelection.adjacent(brushes, activeBrush?.id, 1)?.let(::selectImportedBrush)
                            ShortcutAction.CUT_SELECTION -> if (selectedElementId != null) Toast.makeText(context, inkOnlyClipboardMessage, Toast.LENGTH_SHORT).show() else if (canvasView?.cutSelectionToClipboard() != true) Toast.makeText(context, selectBeforeCutMessage, Toast.LENGTH_SHORT).show()
                            ShortcutAction.COPY_SELECTION -> if (selectedElementId != null) Toast.makeText(context, inkOnlyClipboardMessage, Toast.LENGTH_SHORT).show() else if (canvasView?.copySelectionToClipboard() != true) Toast.makeText(context, selectBeforeCopyMessage, Toast.LENGTH_SHORT).show()
                            ShortcutAction.PASTE_SELECTION -> { selectedElementId = null; if (canvasView?.pasteSelectionFromClipboard() != true) Toast.makeText(context, noInkClipboardMessage, Toast.LENGTH_SHORT).show() }
                            ShortcutAction.DELETE_SELECTION -> selectedElementId?.let { onRemoveElement(document.id, selectedPage.id, it); selectedElementId = null } ?: canvasView?.deleteSelection()
                            ShortcutAction.SELECT_ALL -> { selectedElementId = null; tool = EditorTool.SELECT; canvasView?.selectAllEditableStrokes() }
                            ShortcutAction.DESELECT -> finishSelectionTool(deselect = true)
                            ShortcutAction.UNDO -> canvasView?.undo()
                            ShortcutAction.REDO -> canvasView?.redo()
                            ShortcutAction.NEW_LAYER -> onAddLayer(document.id, selectedPage.id, "Layer")
                            ShortcutAction.MERGE_LAYER_DOWN -> onMergeLayerDown(document.id, selectedPage.id, activeLayerId)?.let { activeLayerId = it }
                            ShortcutAction.SELECT_LAYER_ABOVE -> LayerShortcutSelection.adjacentEditable(selectedPage.layers, activeLayerId, 1)?.let { activeLayerId = it }
                            ShortcutAction.SELECT_LAYER_BELOW -> LayerShortcutSelection.adjacentEditable(selectedPage.layers, activeLayerId, -1)?.let { activeLayerId = it }
                            ShortcutAction.ZOOM_OUT -> { val next = ViewportTransform.zoomAround(ViewportState(zoom, panX, panY, viewRotation), .9f, 0f, 0f, geometry = currentViewportGeometry()); zoom = next.scale; panX = next.panX; panY = next.panY }
                            ShortcutAction.ZOOM_IN -> { val next = ViewportTransform.zoomAround(ViewportState(zoom, panX, panY, viewRotation), 1.1f, 0f, 0f, geometry = currentViewportGeometry()); zoom = next.scale; panX = next.panX; panY = next.panY }
                            ShortcutAction.ROTATE_VIEW_LEFT -> rotateViewByShortcut(-5f)
                            ShortcutAction.ROTATE_VIEW_RIGHT -> rotateViewByShortcut(5f)
                            ShortcutAction.FIT_PAGE -> fitPage()
                            ShortcutAction.ACTUAL_SIZE -> actualSize()
                            ShortcutAction.FIT_WIDTH -> fitWidth()
                            ShortcutAction.RESET_VIEW_ROTATION -> resetViewRotation()
                            ShortcutAction.TOGGLE_RULER -> {
                                if (activation.trigger == ShortcutTrigger.HOLD) {
                                    rulerBeforeShortcut = settings.rulerEnabled
                                    onSettingsChange(settings.copy(rulerEnabled = true))
                                } else onSettingsChange(settings.copy(rulerEnabled = !settings.rulerEnabled))
                            }
                            ShortcutAction.TOGGLE_GRID_SNAP -> {
                                if (activation.trigger == ShortcutTrigger.HOLD) {
                                    gridBeforeShortcut = settings.gridSnapEnabled
                                    onSettingsChange(settings.copy(gridSnapEnabled = true))
                                } else onSettingsChange(settings.copy(gridSnapEnabled = !settings.gridSnapEnabled))
                            }
                            ShortcutAction.TOGGLE_ALIGNMENT_GUIDES -> {
                                if (activation.trigger == ShortcutTrigger.HOLD) {
                                    guidesBeforeShortcut = settings.alignmentGuidesEnabled
                                    onSettingsChange(settings.copy(alignmentGuidesEnabled = true))
                                } else onSettingsChange(settings.copy(alignmentGuidesEnabled = !settings.alignmentGuidesEnabled))
                            }
                            ShortcutAction.PREVIOUS_PAGE -> document.pages.indexOfFirst { it.id == selectedPage.id }.takeIf { it > 0 }?.let { selectPageWithViewport(document.pages[it - 1].id) }
                            ShortcutAction.NEXT_PAGE -> document.pages.indexOfFirst { it.id == selectedPage.id }.takeIf { it in 0 until document.pages.lastIndex }?.let { selectPageWithViewport(document.pages[it + 1].id) }
                            ShortcutAction.TOGGLE_BRUSH_PANEL -> onToggleBrushes()
                            ShortcutAction.TOGGLE_LAYER_PANEL -> {
                                setLayersPanelOpen(!layersOpen)
                            }
                            ShortcutAction.TOGGLE_PANELS -> if (!panelsHiddenByShortcut) {
                                pageRailBeforeShortcut = settings.showPageRail
                                toolbarBeforeShortcut = settings.showToolbar
                                brushesBeforeShortcut = brushesOpen
                                layersBeforeShortcut = layersOpen
                                onSettingsChange(settings.copy(showPageRail = false, showToolbar = false))
                                if (brushesOpen) onToggleBrushes()
                                layersOpen = false
                                panelsHiddenByShortcut = true
                            } else {
                                onSettingsChange(settings.copy(showPageRail = pageRailBeforeShortcut, showToolbar = toolbarBeforeShortcut))
                                if (brushesBeforeShortcut && !brushesOpen) onToggleBrushes()
                                layersOpen = layersBeforeShortcut
                                panelsHiddenByShortcut = false
                            }
                            ShortcutAction.SAVE_CHECKPOINT -> Toast.makeText(context, allChangesSavedMessage, Toast.LENGTH_SHORT).show()
                            ShortcutAction.QUICK_SLOT_1, ShortcutAction.QUICK_SLOT_2, ShortcutAction.QUICK_SLOT_3, ShortcutAction.QUICK_SLOT_4,
                            ShortcutAction.QUICK_SLOT_5, ShortcutAction.QUICK_SLOT_6, ShortcutAction.QUICK_SLOT_7, ShortcutAction.QUICK_SLOT_8 -> ToolboxQuickSlots.indexOf(action)?.let { applyQuickSlot(it, activation.trigger == ShortcutTrigger.HOLD) }
                        }
                    },
                    onRulerGuideChange = { guide ->
                        onSettingsChange(settings.copy(
                            rulerCenterX = guide.centerX,
                            rulerCenterY = guide.centerY,
                            rulerAngleDegrees = guide.angleDegrees,
                            rulerSnapDegrees = guide.snapDegrees,
                        ))
                    },
                    onCanvasGuideChange = { guide ->
                        onSettingsChange(settings.copy(
                            gridSnapEnabled = guide.gridSnapEnabled,
                            gridSpacing = guide.gridSpacing,
                            alignmentGuidesEnabled = guide.alignmentGuidesEnabled,
                            verticalGuideX = guide.verticalGuideX,
                            horizontalGuideY = guide.horizontalGuideY,
                        ))
                    },
                    onPerspectiveGuideChange = { guide ->
                        val points = guide.normalized().points
                        onSettingsChange(settings.copy(
                            perspectiveGuideMode = guide.mode,
                            perspectiveGuideAssisted = guide.assisted,
                            perspectiveGuideEditing = guide.editing,
                            perspectivePoint1X = points[0].x,
                            perspectivePoint1Y = points[0].y,
                            perspectivePoint2X = points[1].x,
                            perspectivePoint2Y = points[1].y,
                            perspectivePoint3X = points[2].x,
                            perspectivePoint3Y = points[2].y,
                        ))
                    },
                    onSpecialRulerChange = { ruler ->
                        onSettingsChange(settings.copy(
                            specialRulerMode = ruler.mode,
                            specialRulerAssisted = ruler.assisted,
                            specialRulerEditing = ruler.editing,
                            specialRulerCenterX = ruler.centerX,
                            specialRulerCenterY = ruler.centerY,
                            specialRulerAngleDegrees = ruler.angleDegrees,
                            specialRulerSpacing = ruler.spacing,
                        ))
                    },
                    onSelectionActiveChanged = { selectionActive = it; selectionFeather = canvasView?.selectionFeatherPixels ?: 0f },
                )
                // One stable, untransformed input plane owns every pen and navigation packet.
                // A transformed AndroidView reports feedback-dependent coordinates while it is
                // itself moving under a pinch, which is the source of selection-mode jitter and
                // runaway pan. The proxy maps drawing contacts into the page exactly once.
                AndroidView(
                    factory = { InkViewportTouchProxy(it) },
                    update = { proxy ->
                        proxy.target = canvasView
                        proxy.imageAtPoint = { x, y ->
                            if (imageCanvasTransform != null || handToolEnabled || handToolHeld || rotateToolEnabled || rotateToolHeld || tool == EditorTool.ERASER) null
                            else ImageElementHitTest.find(PageCropTransform.project(selectedPage), x, y)?.id
                        }
                        proxy.onImageLongPress = { id ->
                            stopContinueFilling()
                            val projected = PageCropTransform.project(selectedPage)
                            (projected.elements.firstOrNull { it.id == id } as? PageElement.Image)?.let { image ->
                                selectedElementId = null
                                canvasView?.clearSelection()
                                imageCanvasPreview = image
                                imageCanvasTransform = ImageCanvasTransform(image, projected.width, projected.height, proxy.resources.displayMetrics.density) { preview ->
                                    imageCanvasPreview = preview
                                    canvasView?.invalidate()
                                }.also { it.mode = imageTransformMode }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Page objects need their own transformed interaction plane above the stable
                // full-viewport input proxy. Rendering remains in the canvas/layer compositor;
                // this transparent plane owns only object hit testing, drag and edit gestures.
                val interactionPage = remember(selectedPage) { PageCropTransform.project(selectedPage) }
                BoxWithConstraints(
                    Modifier
                        .fillMaxHeight(.88f)
                        .aspectRatio((interactionPage.width / interactionPage.height.coerceAtLeast(1f)).coerceIn(.35f, 2.8f))
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = panX,
                            translationY = panY,
                            rotationZ = viewRotation,
                        )
                        .zIndex(1f),
                ) {
                    val interactionDensity = LocalDensity.current
                    val interactionWidthPx = with(interactionDensity) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val interactionHeightPx = with(interactionDensity) { maxHeight.toPx() }.coerceAtLeast(1f)
                    val visibleInteractionLayers = interactionPage.layers.filter { LayerHierarchy.effectiveVisible(interactionPage.layers, it) && it.role != LayerRole.FOLDER }.mapTo(mutableSetOf(), InkLayer::id)
                    val lockedInteractionLayers = interactionPage.layers.filter { LayerHierarchy.effectiveLocked(interactionPage.layers, it) || it.role == LayerRole.FOLDER }.mapTo(mutableSetOf(), InkLayer::id)
                    val interactionLayerOrder = interactionPage.layers.mapIndexed { index, layer -> layer.id to index }.toMap()
                    val interactionLayerOpacities = interactionPage.layers.associate { it.id to it.opacity }
                    interactionPage.elements
                        .filter { it.layerId in visibleInteractionLayers }
                        .sortedBy { interactionLayerOrder[it.layerId] ?: Int.MAX_VALUE }
                        .forEach { element ->
                            ObjectElementView(
                                element = element,
                                page = interactionPage,
                                layerOpacity = interactionLayerOpacities[element.layerId] ?: 1f,
                                selected = selectedElementId == element.id,
                                editable = imageCanvasTransform == null && tool == EditorTool.SELECT && element.layerId !in lockedInteractionLayers,
                                canvasWidth = maxWidth,
                                canvasHeight = maxHeight,
                                canvasWidthPx = interactionWidthPx,
                                canvasHeightPx = interactionHeightPx,
                                onSelect = { selectedElementId = element.id },
                                onChange = { onElement(document.id, selectedPage.id, PageCropTransform.unproject(it, selectedPage.crop)) },
                                onDuplicate = { onDuplicateElement(document.id, selectedPage.id, element.id) },
                                onDelete = { onRemoveElement(document.id, selectedPage.id, element.id); selectedElementId = null },
                                internalLinkLabel = (element as? PageElement.Link)?.takeIf(InternalLinkTarget::isInternal)?.let { InternalLinkTarget.label(it, documents) },
                                onOpenInternalLink = onOpenInternalLink,
                                settings = settings,
                                activeBrush = activeBrush,
                                brushSize = brushSize,
                                brushOpacity = brushOpacity,
                                brushColor = brushColor,
                                eraserPreset = settings.eraserPreset,
                                showContent = false,
                                modifier = Modifier.align(Alignment.TopStart),
                            )
                        }
                }
                val currentPageIndex = document.pages.indexOfFirst { it.id == selectedPage.id }.coerceAtLeast(0)
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).zIndex(2f),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = CardDefaults.outlinedCardBorder(),
                    shadowElevation = 5.dp,
                ) {
                    Row(Modifier.height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { document.pages.getOrNull(currentPageIndex - 1)?.let { selectPageWithViewport(it.id) } },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier.size(38.dp),
                        ) { Icon(Icons.Outlined.KeyboardArrowUp, tr("Previous page", "上一页"), Modifier.size(20.dp)) }
                        Text("${currentPageIndex + 1}/${document.pages.size}", fontSize = 10.sp, color = MutedInk)
                        IconButton(
                            onClick = {
                                val next = document.pages.getOrNull(currentPageIndex + 1)
                                if (next != null) selectPageWithViewport(next.id)
                                else onAppendCurrentPage(selectedPage.id)
                            },
                            modifier = Modifier.size(38.dp),
                        ) {
                            val atLastPage = currentPageIndex == document.pages.lastIndex
                            Icon(
                                if (atLastPage) Icons.Outlined.Add else Icons.Outlined.KeyboardArrowDown,
                                if (atLastPage) tr("Add page using current template", "按当前模板新增页") else tr("Next page", "下一页"),
                                Modifier.size(20.dp),
                            )
                        }
                    }
                }
                val dockVertical = settings.toolbarDock in setOf(ToolbarDock.LEFT, ToolbarDock.RIGHT) || (settings.toolbarDock == ToolbarDock.FREE && settings.verticalToolbar)
                val dockAlignment = when (settings.toolbarDock) {
                    ToolbarDock.TOP -> Alignment.TopCenter
                    ToolbarDock.BOTTOM -> Alignment.BottomCenter
                    ToolbarDock.LEFT -> Alignment.CenterStart
                    ToolbarDock.RIGHT -> Alignment.CenterEnd
                    ToolbarDock.FREE -> Alignment.TopStart
                }
                val dockPadding = when (settings.toolbarDock) {
                    ToolbarDock.TOP -> PaddingValues(top = 16.dp)
                    ToolbarDock.BOTTOM -> PaddingValues(bottom = 16.dp)
                    ToolbarDock.LEFT -> PaddingValues(start = 16.dp)
                    ToolbarDock.RIGHT -> PaddingValues(end = 16.dp)
                    ToolbarDock.FREE -> PaddingValues(0.dp)
                }
                val boundedToolbarPosition = FloatingToolbarPlacement.clamp(toolbarX, toolbarY, toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical)
                LaunchedEffect(toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical, settings.toolbarDock) {
                    if (settings.toolbarDock == ToolbarDock.FREE && (toolbarX != boundedToolbarPosition.xDp || toolbarY != boundedToolbarPosition.yDp)) {
                        toolbarX = boundedToolbarPosition.xDp
                        toolbarY = boundedToolbarPosition.yDp
                        onSettingsChange(settings.copy(toolbarOffsetXDp = toolbarX, toolbarOffsetYDp = toolbarY))
                    }
                }
                if (settings.showToolbar) key(tool, selectedBrushId) { ToolDock(tool = tool, onTool = { chosen ->
                    exitCanvasNavigation()
                    if (chosen == EditorTool.SELECT) rememberSelectionReturnTarget()
                    if (chosen == EditorTool.EYEDROPPER && tool != EditorTool.EYEDROPPER) {
                        rememberDrawingSelection()
                    }
                    tool = chosen; canvasView?.tool = chosen
                    when (chosen) {
                        EditorTool.PEN -> {
                            val remembered = activeBrush?.takeUnless(BrushRoleClassifier::isEraser)
                                ?: brushes.filterNot(BrushRoleClassifier::isEraser).maxByOrNull(ImportedBrush::lastUsedAt)
                            activeBrush = remembered
                            selectedBrushId = remembered?.id
                            brushOpacity = savedBrushOpacity(remembered, remembered?.defaultOpacity ?: settings.penOpacity, EditorTool.PEN)
                            brushSize = savedBrushSize(remembered, remembered?.defaultSize ?: settings.penSize, EditorTool.PEN)
                            if (activeLayerId == LayerIds.HIGHLIGHT || activeLayerId == LayerIds.OBJECTS) activeLayerId = LayerIds.INK
                        }
                        EditorTool.HIGHLIGHTER -> { activeBrush = null; selectedBrushId = null; brushOpacity = savedBrushOpacity(null, settings.highlighterOpacity, EditorTool.HIGHLIGHTER); brushSize = savedBrushSize(null, settings.highlighterSize, EditorTool.HIGHLIGHTER); activeLayerId = LayerIds.HIGHLIGHT }
                        EditorTool.ERASER -> {
                            val remembered = activeBrush?.takeIf(BrushRoleClassifier::isEraser)
                                ?: brushes.filter(BrushRoleClassifier::isEraser).maxByOrNull(ImportedBrush::lastUsedAt)
                            activeBrush = remembered
                            selectedBrushId = remembered?.id
                            brushOpacity = savedBrushOpacity(remembered, remembered?.defaultOpacity ?: settings.eraserPreset.opacity, EditorTool.ERASER)
                            brushSize = savedBrushSize(remembered, remembered?.defaultSize ?: settings.eraserSize, EditorTool.ERASER)
                        }
                        EditorTool.SMUDGE -> {
                            val remembered = activeBrush?.takeIf(BrushRoleClassifier::isBlend) ?: systemBrushCatalog().first(BrushRoleClassifier::isBlend)
                            activeBrush = remembered; selectedBrushId = remembered.id
                            brushOpacity = savedBrushOpacity(remembered, remembered.defaultOpacity, EditorTool.SMUDGE)
                            brushSize = savedBrushSize(remembered, remembered.defaultSize, EditorTool.SMUDGE)
                        }
                        EditorTool.LIQUIFY -> {
                            val remembered = activeBrush?.takeIf(BrushRoleClassifier::isLiquify) ?: systemBrushCatalog().first(BrushRoleClassifier::isLiquify)
                            activeBrush = remembered; selectedBrushId = remembered.id
                            brushOpacity = savedBrushOpacity(remembered, 1f, EditorTool.LIQUIFY)
                            brushSize = savedBrushSize(remembered, remembered.defaultSize, EditorTool.LIQUIFY)
                        }
                        EditorTool.SHAPE -> shapeInsertOpen = true
                        EditorTool.TEXT -> { insertText = ""; textInsertOpen = true }
                        EditorTool.NOTE -> { insertText = ""; stickyInsertOpen = true }
                        EditorTool.LINK -> { insertLinkTitle = ""; insertLinkUrl = ""; linkInsertOpen = true }
                        EditorTool.IMAGE -> { imageInsertOpen = true; tool = EditorTool.SELECT }
                        else -> Unit
                    }
                    syncCanvasInput(prepareAssets = activeBrush != null)
                }, onUndo = { canvasView?.undo() }, onRedo = { canvasView?.redo() }, onScale = { selectedElementId?.let { id -> selectedPage.elements.firstOrNull { it.id == id }?.let { onElement(document.id, selectedPage.id, resizeElement(it, 1.15f)) } } ?: canvasView?.scaleSelection(1.15f) }, onRotate = { selectedElementId?.let { id -> selectedPage.elements.firstOrNull { it.id == id }?.let { onElement(document.id, selectedPage.id, rotateElement(it, 15f)) } } ?: canvasView?.rotateSelection(15f) }, onCopy = { selectedElementId?.let { onDuplicateElement(document.id, selectedPage.id, it) } ?: canvasView?.copySelection() }, onCut = { if (selectedElementId != null) Toast.makeText(context, inkOnlyClipboardMessage, Toast.LENGTH_SHORT).show() else if (canvasView?.cutSelectionToClipboard() != true) Toast.makeText(context, selectBeforeCutMessage, Toast.LENGTH_SHORT).show() }, onClipboardCopy = { if (selectedElementId != null) Toast.makeText(context, inkOnlyClipboardMessage, Toast.LENGTH_SHORT).show() else if (canvasView?.copySelectionToClipboard() != true) Toast.makeText(context, selectBeforeCopyMessage, Toast.LENGTH_SHORT).show() }, onPaste = { selectedElementId = null; if (canvasView?.pasteSelectionFromClipboard() != true) Toast.makeText(context, noInkClipboardMessage, Toast.LENGTH_SHORT).show() }, onDelete = { selectedElementId?.let { onRemoveElement(document.id, selectedPage.id, it); selectedElementId = null } ?: canvasView?.deleteSelection() }, onSelectAll = { selectedElementId = null; tool = EditorTool.SELECT; canvasView?.selectAllEditableStrokes() }, onInvertSelection = { selectedElementId = null; tool = EditorTool.SELECT; canvasView?.invertSelection() }, onDeselect = { finishSelectionTool(deselect = true) }, onFreeTransform = { tool = EditorTool.SELECT; canvasView?.enterFreeTransform() }, selectionShape = settings.selectionShape, selectionMode = settings.selectionMode, selectionFilter = settings.selectionFilter, onSelectionShape = { shape -> canvasView?.beginSelectionInput(shape); onSettingsChange(settings.copy(selectionShape = shape)) }, onSelectionMode = { onSettingsChange(settings.copy(selectionMode = it)) }, onSelectionFilter = { onSettingsChange(settings.copy(selectionFilter = it)) }, referenceTarget = settings.referenceTarget, onReferenceTarget = { onSettingsChange(settings.copy(referenceTarget = it)) }, correctLineMode = settings.correctLineMode, correctLineAmount = settings.correctLineAmount, onCorrectLineMode = { onSettingsChange(settings.copy(correctLineMode = it)) }, onCorrectLineAmount = { onSettingsChange(settings.copy(correctLineAmount = it.coerceIn(0f, 1f))) }, eraserPreset = settings.eraserPreset, onEraserPreset = { preset -> onSettingsChange(settings.copy(eraserPreset = preset, nonDestructiveEraser = preset.usesMask)) }, rulerEnabled = settings.rulerEnabled, rulerSnapDegrees = settings.rulerSnapDegrees, onToggleRuler = { onSettingsChange(settings.copy(rulerEnabled = !settings.rulerEnabled)) }, onRulerSnap = { onSettingsChange(settings.copy(rulerSnapDegrees = it)) }, onResetRuler = { onSettingsChange(settings.copy(rulerCenterX = .5f, rulerCenterY = .5f, rulerAngleDegrees = 0f)) }, gridSnapEnabled = settings.gridSnapEnabled, gridSpacing = settings.gridSpacing, alignmentGuidesEnabled = settings.alignmentGuidesEnabled, onToggleGridSnap = { onSettingsChange(settings.copy(gridSnapEnabled = !settings.gridSnapEnabled)) }, onGridSpacing = { onSettingsChange(settings.copy(gridSpacing = it)) }, onToggleAlignmentGuides = { onSettingsChange(settings.copy(alignmentGuidesEnabled = !settings.alignmentGuidesEnabled)) }, onResetAlignmentGuides = { onSettingsChange(settings.copy(verticalGuideX = .5f, horizontalGuideY = .5f)) }, onDrag = { dx, dy ->
                    val next = FloatingToolbarPlacement.clamp(toolbarX + dx / editorDensity.density, toolbarY + dy / editorDensity.density, toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical)
                    toolbarX = next.xDp
                    toolbarY = next.yDp
                }, onDragEnd = { onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = toolbarX, toolbarOffsetYDp = toolbarY, verticalToolbar = dockVertical)) }, vertical = dockVertical, onToggleOrientation = { onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE, verticalToolbar = !dockVertical)) }, onResetPosition = {
                    val reset = FloatingToolbarPlacement.defaultPosition(toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical)
                    toolbarX = reset.xDp
                    toolbarY = reset.yDp
                    onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = toolbarX, toolbarOffsetYDp = toolbarY))
                }, onDock = { dock -> onSettingsChange(settings.copy(toolbarDock = dock, verticalToolbar = when (dock) { ToolbarDock.LEFT, ToolbarDock.RIGHT -> true; ToolbarDock.TOP, ToolbarDock.BOTTOM -> false; ToolbarDock.FREE -> dockVertical })) }, brushSize = brushSize, brushOpacity = brushOpacity, brushColor = brushColor, brushes = brushes, selectedBrushId = selectedBrushId, onSystemPreset = { presetTool ->
                    exitCanvasNavigation()
                    val presetId = if (presetTool == EditorTool.ERASER) "system-hard-eraser" else "system-g-pen"
                    selectWorkspaceBrush(requireNotNull(SystemBrushCatalog.byId(presetId)))
                }, onBrushPreset = ::selectWorkspaceBrush, onSize = { raw ->
                    changeCurrentBrushSize(raw)
                }, onOpacity = { raw ->
                    changeCurrentBrushOpacity(raw)
                }, onColor = ::changeBrushColor, symmetryMode = settings.symmetryMode, onSymmetryMode = { onSettingsChange(settings.copy(symmetryMode = it)) }, gradientControls = { GradientToolControls(settings, brushColor, ::changeBrushColor, onSettingsChange) }, modifier = Modifier.align(dockAlignment).padding(dockPadding).graphicsLayer(translationX = if (settings.toolbarDock == ToolbarDock.FREE) with(editorDensity) { boundedToolbarPosition.xDp.dp.toPx() } else 0f, translationY = if (settings.toolbarDock == ToolbarDock.FREE) with(editorDensity) { boundedToolbarPosition.yDp.dp.toPx() } else 0f)) }
                if (tool == EditorTool.SELECT && imageCanvasTransform == null) SelectionInputBar(
                    shape = settings.selectionShape,
                    mode = settings.selectionMode,
                    hasSelection = selectionActive,
                    onShape = { shape ->
                        canvasView?.beginSelectionInput(shape)
                        onSettingsChange(settings.copy(selectionShape = shape))
                    },
                    onMode = { mode -> onSettingsChange(settings.copy(selectionMode = mode)) },
                    onDone = { finishSelectionTool(deselect = false) },
                    onDeselect = { finishSelectionTool(deselect = true) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                )
                if (tool == EditorTool.SELECT && selectionActive && selectionTransformState == null) SelectionLauncher(
                    featherPixels = selectionFeather,
                    interpolation = settings.selectionInterpolation,
                    onInterpolation = { onSettingsChange(settings.copy(selectionInterpolation = it)) },
                    onDone = { finishSelectionTool(deselect = false) },
                    onDeselect = { finishSelectionTool(deselect = true) },
                    onInvert = { selectedElementId = null; canvasView?.invertSelection() },
                    onCut = { canvasView?.cutSelectionToClipboard() },
                    onCopy = { canvasView?.copySelectionToClipboard() },
                    onDelete = { canvasView?.deleteSelection() },
                    onExpand = { canvasView?.expandSelection(it) },
                    onShrink = { canvasView?.shrinkSelection(it) },
                    onBorder = { canvasView?.borderSelection(it) },
                    onFeather = { value -> if (canvasView?.featherSelection(value) == true) selectionFeather = value },
                    onBeginFeather = {
                        selectionFeather = canvasView?.selectionFeatherPixels ?: 0f
                        canvasView?.beginSelectionFeather() == true
                    },
                    onFinishFeather = { apply ->
                        canvasView?.finishSelectionFeather(apply)
                        selectionFeather = canvasView?.selectionFeatherPixels ?: 0f
                    },
                    onFill = { canvasView?.fillSelection() },
                    onTransform = { canvasView?.enterFreeTransform() },
                    onFlipHorizontal = { canvasView?.flipSelection(horizontal = true) },
                    onFlipVertical = { canvasView?.flipSelection(horizontal = false) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 52.dp),
                )
                selectionTransformState?.let { state ->
                    SelectionTransformToolbar(state, settings.language,
                        interpolation = settings.selectionInterpolation,
                        onInterpolation = { onSettingsChange(settings.copy(selectionInterpolation = it)) },
                        onMode = { canvasView?.setSelectionTransformMode(it) },
                        onDimensions = { w, h, a -> canvasView?.setSelectionTransformDimensions(w, h, a) },
                        onFlip = { canvasView?.flipSelection(it) },
                        onReset = { canvasView?.resetSelectionTransform() },
                        onFinish = { canvasView?.finishSelectionTransform(apply = it) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 54.dp).zIndex(4f),
                    )
                }
                imageCanvasTransform?.let { session ->
                    Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).zIndex(4f),
                        shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 5.dp) {
                        Row(Modifier.padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { finishImageTransform(false) }) { Text(tr("Cancel transform", "取消变换")) }
                            ImageTransformMode.entries.forEach { mode ->
                                FilterChip(selected = imageTransformMode == mode, onClick = { imageTransformMode = mode; session.mode = mode },
                                    label = { Text(if (mode == ImageTransformMode.FREEFORM) tr("Freeform", "自由变换") else tr("Uniform", "等比变换")) })
                            }
                            Text("${session.current.width.toInt()} × ${session.current.height.toInt()} · ${session.current.rotationDegrees.toInt()}°", Modifier.padding(8.dp), fontSize = 11.sp)
                            TextButton(onClick = { imageTransformId = session.original.id }) { Text(tr("Properties", "属性")) }
                            TextButton(onClick = session::reset) { Text(tr("Reset transform", "重置变换")) }
                            TextButton(onClick = { finishImageTransform(true) }) { Text(tr("Apply transform", "应用变换")) }
                        }
                    }
                }
                if (tool == EditorTool.FILL) FillOptionsLauncher(
                    subTool = settings.fillSubTool,
                    sensitivity = settings.fillSensitivity,
                    closeGapPixels = settings.fillCloseGapPixels,
                    areaScalingPixels = settings.fillAreaScalingPixels,
                    onSensitivity = { onSettingsChange(settings.copy(fillSensitivity = it)) },
                    onCloseGap = { onSettingsChange(settings.copy(fillCloseGapPixels = it)) },
                    onAreaScaling = { onSettingsChange(settings.copy(fillAreaScalingPixels = it)) },
                    onSubTool = { onSettingsChange(settings.copy(fillSubTool = it)) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
                if (settings.showInputDiagnostics) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), shape = RoundedCornerShape(20.dp), color = Color.White, border = CardDefaults.outlinedCardBorder(), shadowElevation = 3.dp) { Text(inputDiagnostic, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), color = Ink, fontSize = 11.sp) }
            }
            if (rightPanelTabs.size > 1) DockedPanelTabStrip(
                dock = WorkspacePanelDock.RIGHT,
                panels = rightPanelTabs,
                active = rightActivePanel,
                stacked = rightPanelsStacked,
                onSelect = { setActivePanel(WorkspacePanelDock.RIGHT, it) },
                onMove = ::movePanelTab,
                onStacked = { setDockStacked(WorkspacePanelDock.RIGHT, it, rightPanelTabs) },
            )
            if (editorWidth >= 700.dp && settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.RIGHT && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.RIGHT) && settings.pagePanelMode == WorkspacePanelMode.EXPANDED && !settings.pagePanelLocked) DockedSidebarResizeHandle(
                tr("Resize pages sidebar", "调整页面侧栏宽度"),
                onResize = { resizeDockedPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.RIGHT, it) },
                onResizeEnd = { persistDockedPanel(WorkspacePanelKind.PAGES) },
            )
            if (editorWidth >= 700.dp && settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.RIGHT && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.RIGHT)) PageRail(
                document, documents, selectedPage.id, settings.pagePanelDock, assets,
                onDock = { changePanelDock(WorkspacePanelKind.PAGES, it) },
                onClose = { onSettingsChange(settings.copy(showPageRail = false)) },
                pageTemplates = pageTemplates,
                onSelect = ::selectPageWithViewport, onOpenLayers = { pageId -> setLayersPanelOpen(true, pageId) }, onAdd = onAddPage, onDuplicate = onDuplicatePage, onDelete = onDeletePage, onMove = onMovePage, onReorder = onReorderPages, onTransfer = onTransferPage,
                onApplyTemplate = onApplyPageTemplate, onApplyTemplates = onApplyPageTemplates, onImportTemplates = onImportPageTemplates, onToggleTemplateFavorite = onTogglePageTemplateFavorite,
                onSetDefaultTemplate = onSetNotebookDefaultTemplate, onSaveAsTemplate = onSavePageAsTemplate, onDeleteTemplate = onDeletePageTemplate,
                onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata,
                onClearPageInk, onSetPageCrop, onRotatePage,
                panelMode = settings.pagePanelMode,
                panelLocked = settings.pagePanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(pagePanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(pagePanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.RIGHT, pagePanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.RIGHT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
                modifier = Modifier.width(dockedPanelWidth(WorkspacePanelKind.PAGES).dp).fillMaxHeight().padding(6.dp),
            )
            if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.RIGHT && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.RIGHT) && settings.layerPanelMode == WorkspacePanelMode.EXPANDED && !settings.layerPanelLocked) DockedSidebarResizeHandle(
                tr("Resize layers sidebar", "调整图层侧栏宽度"),
                onResize = { resizeDockedPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.RIGHT, it) },
                onResizeEnd = { persistDockedPanel(WorkspacePanelKind.LAYERS) },
            )
            if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.RIGHT && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.RIGHT)) LayerPanel(
                page = selectedPage,
                activeLayerId = activeLayerId,
                brushColor = brushColor,
                dock = settings.layerPanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.LAYERS, it) },
                onClose = { layersOpen = false; onSettingsChange(settings.copy(showLayerPanel = false)) },
                onColor = ::changeBrushColor,
                onSelect = { activeLayerId = it },
                onAdd = { onAddLayer(document.id, selectedPage.id, "Layer") },
                onAddFolder = { onAddLayerFolder(document.id, selectedPage.id) },
                onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                onOpacity = { layerId, value -> onSetLayerOpacity(document.id, selectedPage.id, layerId, value) },
                onBlendMode = { layerId, mode -> onSetLayerBlendMode(document.id, selectedPage.id, layerId, mode) },
                onToggleAlphaLock = { onToggleLayerAlphaLock(document.id, selectedPage.id, it) },
                onToggleClippedToBelow = { onToggleLayerClippedToBelow(document.id, selectedPage.id, it) },
                onToggleReference = { onToggleLayerReference(document.id, selectedPage.id, it) },
                onToggleDraft = { onToggleLayerDraft(document.id, selectedPage.id, it) },
                onMaskCommand = ::performLayerMaskCommand,
                onMoveToFolder = { layerId, folderId -> onMoveLayerToFolder(document.id, selectedPage.id, layerId, folderId) },
                onToggleFolderCollapsed = { onToggleLayerFolderCollapsed(document.id, selectedPage.id, it) },
                onMove = { layerId, delta -> onMoveLayer(document.id, selectedPage.id, layerId, delta) },
                onDuplicate = { layerId -> onDuplicateLayer(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onMergeDown = { layerId -> onMergeLayerDown(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onDelete = { onDeleteLayer(document.id, selectedPage.id, it) },
                panelMode = settings.layerPanelMode,
                panelLocked = settings.layerPanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(layerPanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(layerPanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.RIGHT, layerPanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.RIGHT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
                modifier = Modifier.width(dockedPanelWidth(WorkspacePanelKind.LAYERS).dp).fillMaxHeight().background(Color(0xFFF4F4F4)).border(1.dp, OutlineSoft).padding(2.dp),
            )
        }
        if (bottomPanelCount > 0) {
            HorizontalDivider(color = OutlineSoft)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(bottomPanelHeight)
                    .background(Color(0xFFF7F7F7))
                    .padding(bottom = bottomNavigationPadding)
                    .semantics { contentDescription = bottomPanelsDescription },
            ) {
                if (bottomPanelTabs.size > 1) DockedPanelTabStrip(
                    dock = WorkspacePanelDock.BOTTOM,
                    panels = bottomPanelTabs,
                    active = bottomActivePanel,
                    stacked = bottomPanelsStacked,
                    onSelect = { setActivePanel(WorkspacePanelDock.BOTTOM, it) },
                    onMove = ::movePanelTab,
                    onStacked = { setDockStacked(WorkspacePanelDock.BOTTOM, it, bottomPanelTabs) },
                )
                if (settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.BOTTOM && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.BOTTOM)) PageRail(
                    document, documents, selectedPage.id, settings.pagePanelDock, assets,
                    onDock = { changePanelDock(WorkspacePanelKind.PAGES, it) },
                    onClose = { onSettingsChange(settings.copy(showPageRail = false)) },
                    pageTemplates = pageTemplates,
                    onSelect = ::selectPageWithViewport, onOpenLayers = { pageId -> setLayersPanelOpen(true, pageId) }, onAdd = onAddPage, onDuplicate = onDuplicatePage, onDelete = onDeletePage, onMove = onMovePage, onReorder = onReorderPages, onTransfer = onTransferPage,
                    onApplyTemplate = onApplyPageTemplate, onApplyTemplates = onApplyPageTemplates, onImportTemplates = onImportPageTemplates, onToggleTemplateFavorite = onTogglePageTemplateFavorite,
                    onSetDefaultTemplate = onSetNotebookDefaultTemplate, onSaveAsTemplate = onSavePageAsTemplate, onDeleteTemplate = onDeletePageTemplate,
                    onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata,
                    onClearPageInk, onSetPageCrop, onRotatePage,
                    panelMode = settings.pagePanelMode,
                    panelLocked = settings.pagePanelLocked,
                    onPanelMode = { onSettingsChange(settings.copy(pagePanelMode = it)) },
                    onPanelLocked = { onSettingsChange(settings.copy(pagePanelLocked = it)) },
                    onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.BOTTOM, pagePanelBounds) },
                    onPanelDrag = ::updateDockedPanelDrag,
                    onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.BOTTOM) },
                    onPanelDragCancel = ::clearDockedPanelDrag,
                    modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, OutlineSoft).padding(12.dp),
                )
                if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.BOTTOM && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.BOTTOM)) LayerPanel(
                    page = selectedPage,
                    activeLayerId = activeLayerId,
                    brushColor = brushColor,
                    dock = settings.layerPanelDock,
                    onDock = { changePanelDock(WorkspacePanelKind.LAYERS, it) },
                    onClose = { layersOpen = false; onSettingsChange(settings.copy(showLayerPanel = false)) },
                    onColor = ::changeBrushColor,
                    onSelect = { activeLayerId = it },
                    onAdd = { onAddLayer(document.id, selectedPage.id, "Layer") },
                    onAddFolder = { onAddLayerFolder(document.id, selectedPage.id) },
                    onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                    onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                    onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                    onOpacity = { layerId, value -> onSetLayerOpacity(document.id, selectedPage.id, layerId, value) },
                onBlendMode = { layerId, mode -> onSetLayerBlendMode(document.id, selectedPage.id, layerId, mode) },
                    onToggleAlphaLock = { onToggleLayerAlphaLock(document.id, selectedPage.id, it) },
                    onToggleClippedToBelow = { onToggleLayerClippedToBelow(document.id, selectedPage.id, it) },
                    onToggleReference = { onToggleLayerReference(document.id, selectedPage.id, it) },
                    onToggleDraft = { onToggleLayerDraft(document.id, selectedPage.id, it) },
                    onMaskCommand = ::performLayerMaskCommand,
                onMoveToFolder = { layerId, folderId -> onMoveLayerToFolder(document.id, selectedPage.id, layerId, folderId) },
                    onToggleFolderCollapsed = { onToggleLayerFolderCollapsed(document.id, selectedPage.id, it) },
                    onMove = { layerId, delta -> onMoveLayer(document.id, selectedPage.id, layerId, delta) },
                    onDuplicate = { layerId -> onDuplicateLayer(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                    onMergeDown = { layerId -> onMergeLayerDown(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                    onDelete = { onDeleteLayer(document.id, selectedPage.id, it) },
                    panelMode = settings.layerPanelMode,
                    panelLocked = settings.layerPanelLocked,
                    onPanelMode = { onSettingsChange(settings.copy(layerPanelMode = it)) },
                    onPanelLocked = { onSettingsChange(settings.copy(layerPanelLocked = it)) },
                    onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.BOTTOM, layerPanelBounds) },
                    onPanelDrag = ::updateDockedPanelDrag,
                    onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.BOTTOM) },
                    onPanelDragCancel = ::clearDockedPanelDrag,
                    modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, OutlineSoft).background(Color(0xFFF7F7F7)).padding(10.dp),
                )
            }
        }
        }
        panelDockPreview?.let { dock ->
            WorkspacePanelDocking.previewBounds(dock, workspaceWidthDp, usableWorkspaceHeightDp)?.let { preview ->
                WorkspacePanelDockPreview(dock, preview)
            }
        }
        panelGroupPreviewTarget?.let { target ->
            WorkspacePanelGroupPreview(panelBounds(target), target)
        }
        val dockedOrigin = dockedPanelOriginDock
        val dockedPreview = dockedPanelPreviewBounds
        if (
            dockedOrigin != null && dockedPreview != null &&
            WorkspacePanelDocking.shouldDetach(dockedOrigin, panelDragDeltaXDp, panelDragDeltaYDp)
        ) {
            val target = dockedPanelTargetDock
            if (target != null) {
                WorkspacePanelDocking.previewBounds(target, workspaceWidthDp, usableWorkspaceHeightDp)?.let { preview ->
                    WorkspacePanelDockPreview(target, preview)
                }
            } else {
                WorkspacePanelFloatPreview(dockedPreview)
            }
        }
        if (settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.FREE && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.FREE)) FloatingWorkspacePanel(
            bounds = visiblePagePanelBounds,
            mode = settings.pagePanelMode,
            locked = settings.pagePanelLocked,
            zIndex = if (activeFloatingPanel == "pages") 6f else 3f,
            resizeDescription = tr("Resize pages panel", "调整页面面板大小"),
            onActivate = { activeFloatingPanel = "pages" },
            onResize = { left, top, right, bottom ->
                activeFloatingPanel = "pages"
                pagePanelBounds = resizeFloatingPanel(WorkspacePanelKind.PAGES, pagePanelBounds, left / editorDensity.density, top / editorDensity.density, right / editorDensity.density, bottom / editorDensity.density)
            },
            onResizeEnd = { if (freePanelsStacked) persistFloatingPanels(freePanelTabs) else persistPagePanel(pagePanelBounds) },
            tabs = if (freePanelsStacked) freePanelTabs else emptyList(),
            activeTab = freeActivePanel,
            onTab = ::selectFloatingPanel,
            onMoveTab = ::movePanelTab,
            onSeparateTabs = ::separateFloatingPanels,
        ) {
            PageRail(
                document, documents, selectedPage.id, settings.pagePanelDock, assets,
                onDock = { changePanelDock(WorkspacePanelKind.PAGES, it) },
                onClose = { onSettingsChange(settings.copy(showPageRail = false)) },
                pageTemplates = pageTemplates,
                onSelect = ::selectPageWithViewport, onOpenLayers = { pageId -> setLayersPanelOpen(true, pageId) }, onAdd = onAddPage, onDuplicate = onDuplicatePage, onDelete = onDeletePage, onMove = onMovePage, onReorder = onReorderPages, onTransfer = onTransferPage,
                onApplyTemplate = onApplyPageTemplate, onApplyTemplates = onApplyPageTemplates, onImportTemplates = onImportPageTemplates, onToggleTemplateFavorite = onTogglePageTemplateFavorite,
                onSetDefaultTemplate = onSetNotebookDefaultTemplate, onSaveAsTemplate = onSavePageAsTemplate, onDeleteTemplate = onDeletePageTemplate,
                onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata,
                onClearPageInk, onSetPageCrop, onRotatePage,
                panelMode = settings.pagePanelMode,
                panelLocked = settings.pagePanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(pagePanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(pagePanelLocked = it)) },
                modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7), RoundedCornerShape(3.dp)).padding(6.dp),
                onPanelDragStart = { activeFloatingPanel = "pages"; beginPanelDrag(WorkspacePanelKind.PAGES, pagePanelBounds) },
                onPanelDrag = { dx, dy ->
                    activeFloatingPanel = "pages"
                    val deltaXDp = dx / editorDensity.density
                    val deltaYDp = dy / editorDensity.density
                    val next = moveFloatingPanel(WorkspacePanelKind.PAGES, pagePanelBounds, deltaXDp, deltaYDp)
                    pagePanelBounds = next
                    updatePanelDockPreview(WorkspacePanelKind.PAGES, next, deltaXDp, deltaYDp)
                },
                onPanelDragEnd = { finishPanelDrag(WorkspacePanelKind.PAGES, pagePanelBounds, ::persistPagePanel) },
                onPanelDragCancel = { cancelPanelDrag(WorkspacePanelKind.PAGES) { pagePanelBounds = it } },
            )
        }
        if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.FREE && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.FREE)) FloatingWorkspacePanel(
            bounds = visibleLayerPanelBounds,
            mode = settings.layerPanelMode,
            locked = settings.layerPanelLocked,
            zIndex = if (activeFloatingPanel == "layers") 6f else 4f,
            resizeDescription = tr("Resize layers panel", "调整图层面板大小"),
            onActivate = { activeFloatingPanel = "layers" },
            onResize = { left, top, right, bottom ->
                activeFloatingPanel = "layers"
                layerPanelBounds = resizeFloatingPanel(WorkspacePanelKind.LAYERS, layerPanelBounds, left / editorDensity.density, top / editorDensity.density, right / editorDensity.density, bottom / editorDensity.density)
            },
            onResizeEnd = { if (freePanelsStacked) persistFloatingPanels(freePanelTabs) else persistLayerPanel(layerPanelBounds) },
            tabs = if (freePanelsStacked) freePanelTabs else emptyList(),
            activeTab = freeActivePanel,
            onTab = ::selectFloatingPanel,
            onMoveTab = ::movePanelTab,
            onSeparateTabs = ::separateFloatingPanels,
        ) {
            LayerPanel(
                page = selectedPage,
                activeLayerId = activeLayerId,
                brushColor = brushColor,
                dock = settings.layerPanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.LAYERS, it) },
                onClose = { layersOpen = false; onSettingsChange(settings.copy(showLayerPanel = false)) },
                onColor = ::changeBrushColor,
                onSelect = { activeLayerId = it },
                onAdd = { onAddLayer(document.id, selectedPage.id, "Layer") },
                onAddFolder = { onAddLayerFolder(document.id, selectedPage.id) },
                onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                onOpacity = { layerId, value -> onSetLayerOpacity(document.id, selectedPage.id, layerId, value) },
                onBlendMode = { layerId, mode -> onSetLayerBlendMode(document.id, selectedPage.id, layerId, mode) },
                onToggleAlphaLock = { onToggleLayerAlphaLock(document.id, selectedPage.id, it) },
                onToggleClippedToBelow = { onToggleLayerClippedToBelow(document.id, selectedPage.id, it) },
                onToggleReference = { onToggleLayerReference(document.id, selectedPage.id, it) },
                onToggleDraft = { onToggleLayerDraft(document.id, selectedPage.id, it) },
                onMaskCommand = ::performLayerMaskCommand,
                onMoveToFolder = { layerId, folderId -> onMoveLayerToFolder(document.id, selectedPage.id, layerId, folderId) },
                onToggleFolderCollapsed = { onToggleLayerFolderCollapsed(document.id, selectedPage.id, it) },
                onMove = { layerId, delta -> onMoveLayer(document.id, selectedPage.id, layerId, delta) },
                onDuplicate = { layerId -> onDuplicateLayer(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onMergeDown = { layerId -> onMergeLayerDown(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onDelete = { onDeleteLayer(document.id, selectedPage.id, it) },
                panelMode = settings.layerPanelMode,
                panelLocked = settings.layerPanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(layerPanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(layerPanelLocked = it)) },
                modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7), RoundedCornerShape(3.dp)).padding(6.dp),
                onPanelDragStart = { activeFloatingPanel = "layers"; beginPanelDrag(WorkspacePanelKind.LAYERS, layerPanelBounds) },
                onPanelDrag = { dx, dy ->
                    activeFloatingPanel = "layers"
                    val deltaXDp = dx / editorDensity.density
                    val deltaYDp = dy / editorDensity.density
                    val next = moveFloatingPanel(WorkspacePanelKind.LAYERS, layerPanelBounds, deltaXDp, deltaYDp)
                    layerPanelBounds = next
                    updatePanelDockPreview(WorkspacePanelKind.LAYERS, next, deltaXDp, deltaYDp)
                },
                onPanelDragEnd = { finishPanelDrag(WorkspacePanelKind.LAYERS, layerPanelBounds, ::persistLayerPanel) },
                onPanelDragCancel = { cancelPanelDrag(WorkspacePanelKind.LAYERS) { layerPanelBounds = it } },
            )
        }
        if (brushesOpen) FloatingWorkspacePanel(
            bounds = visibleBrushPanelBounds,
            mode = WorkspacePanelMode.EXPANDED,
            locked = false,
            zIndex = if (activeFloatingPanel == "brushes") 6f else 5f,
            resizeDescription = tr("Resize brushes panel", "调整笔刷面板大小"),
            onActivate = { activeFloatingPanel = "brushes" },
            onResize = { left, top, right, bottom ->
                activeFloatingPanel = "brushes"
                brushPanelBounds = resizeFloatingPanel(WorkspacePanelKind.BRUSHES, brushPanelBounds, left / editorDensity.density, top / editorDensity.density, right / editorDensity.density, bottom / editorDensity.density)
            },
            onResizeEnd = { persistBrushPanel(brushPanelBounds) },
            tabs = emptyList(),
            activeTab = null,
            onTab = ::selectFloatingPanel,
            onMoveTab = ::movePanelTab,
            onSeparateTabs = ::separateFloatingPanels,
        ) {
            BrushPanel(
                brushes = brushes,
                usage = brushUsage,
                onUsage = ::changeBrushUsage,
                registeredGroups = brushGroups,
                selectedBrushId = selectedBrushId,
                requestedGroup = activeBrush?.groupName ?: if (tool == EditorTool.ERASER) "Erasers" else null,
                settings = settings,
                dock = WorkspacePanelDock.FREE,
                onDock = {},
                onClose = { if (brushesOpen) onToggleBrushes() },
                onBrushSelected = ::selectWorkspaceBrush,
                onImportBrushes = onImportBrushes,
                onExportBrush = onExportBrush,
                onQuickBrushCommit = { updated ->
                    val committed = updated.copy(lastUsedAt = System.currentTimeMillis())
                    val currentId = activeBrush?.id
                    onReplaceBrushes(if (brushes.any { it.id == committed.id }) {
                        brushes.map { if (it.id == committed.id) committed else it }
                    } else brushes + committed)
                    // A quick author edit keeps the live master size/opacity and foreground.
                    if (currentId != committed.id) brushMasterSettingsStore.save(
                        BrushRoleClassifier.editorTool(committed), committed.id, brushSize, brushOpacity,
                    )
                    exitCanvasNavigation()
                    activeBrush = committed
                    selectedBrushId = committed.id
                    tool = BrushRoleClassifier.editorTool(committed)
                    rememberDrawingSelection()
                    onSettingsChange(settings.copy(persistentDrawingTool = tool, persistentBrushPresetId = committed.id))
                    syncCanvasInput(prepareAssets = true)
                },
                onUpdateBrush = { updated ->
                    onUpdateBrush(updated)
                    if (activeBrush?.id == updated.id) {
                        activeBrush = updated
                        brushSize = savedBrushSize(updated, updated.defaultSize)
                        brushOpacity = savedBrushOpacity(updated, updated.defaultOpacity)
                        syncCanvasInput(prepareAssets = true)
                    }
                },
                onReplaceBrushes = { updated ->
                    onReplaceBrushes(updated)
                    activeBrush = activeBrush?.let { current -> updated.firstOrNull { it.id == current.id } }
                    if (activeBrush == null) selectedBrushId = null
                },
                onReplaceGroups = onReplaceBrushGroups,
                onDeleteBrush = { brushId -> onDeleteBrush(brushId); if (activeBrush?.id == brushId) { activeBrush = null; selectedBrushId = null } },
                onReorderBrush = onReorderBrush,
                panelMode = WorkspacePanelMode.EXPANDED,
                panelLocked = false,
                onPanelMode = {},
                onPanelLocked = {},
                modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7), RoundedCornerShape(3.dp)).padding(6.dp),
                onPanelDragStart = { activeFloatingPanel = "brushes" },
                onPanelDrag = { dx, dy ->
                    activeFloatingPanel = "brushes"
                    val deltaXDp = dx / editorDensity.density
                    val deltaYDp = dy / editorDensity.density
                    val next = moveFloatingPanel(WorkspacePanelKind.BRUSHES, brushPanelBounds, deltaXDp, deltaYDp)
                    brushPanelBounds = next
                },
                onPanelDragEnd = { persistBrushPanel(brushPanelBounds) },
                onPanelDragCancel = {},
            )
        }
        }
    }
    val defaultStickyText = tr("New note", "新便签")
    if (textInsertOpen) AlertDialog(
        onDismissRequest = { textInsertOpen = false },
        title = { Text(tr("Insert text", "插入文本")) },
        text = { OutlinedTextField(insertText, { insertText = it }, minLines = 3, label = { Text(tr("Text", "文本")) }) },
        confirmButton = { Button(onClick = { if (insertText.isNotBlank()) onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Text(x = .18f, y = .2f, text = insertText.trim()), selectedPage.crop)); textInsertOpen = false; tool = EditorTool.SELECT }) { Text(tr("Insert", "插入")) } },
        dismissButton = { TextButton(onClick = { textInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (stickyInsertOpen) AlertDialog(
        onDismissRequest = { stickyInsertOpen = false },
        title = { Text(tr("New sticky note", "新建便签")) },
        text = { OutlinedTextField(insertText, { insertText = it }, minLines = 4, label = { Text(tr("Note", "便签")) }) },
        confirmButton = { Button(onClick = { onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Sticky(x = .55f, y = .25f, text = insertText.trim().ifBlank { defaultStickyText }), selectedPage.crop)); stickyInsertOpen = false; tool = EditorTool.SELECT }) { Text(tr("Insert", "插入")) } },
        dismissButton = { TextButton(onClick = { stickyInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (linkInsertOpen) {
        val normalizedLink = LinkTarget.normalize(insertLinkUrl)
        val targetDocument = documents.firstOrNull { it.id == insertLinkDocumentId } ?: document
        val targetPage = targetDocument.pages.firstOrNull { it.id == insertLinkPageId } ?: targetDocument.pages.firstOrNull()
        AlertDialog(
            onDismissRequest = { linkInsertOpen = false },
            title = { Text(tr("Insert link", "插入链接")) },
            text = { Column(Modifier.widthIn(min = 360.dp, max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(insertLinkTitle, { insertLinkTitle = it }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !insertInternalLink, onClick = { insertInternalLink = false }, label = { Text(tr("Web link", "网页链接")) })
                    FilterChip(selected = insertInternalLink, onClick = { insertInternalLink = true }, label = { Text(tr("Notebook page", "笔记页面")) })
                }
                if (!insertInternalLink) {
                    OutlinedTextField(insertLinkUrl, { insertLinkUrl = it }, label = { Text(tr("Web, email, or phone address", "网页、邮箱或电话号码")) }, placeholder = { Text("https://example.com") }, isError = insertLinkUrl.isNotBlank() && normalizedLink == null, supportingText = { Text(if (insertLinkUrl.isNotBlank() && normalizedLink == null) tr("Use a valid http, https, mailto, or tel address.", "请输入有效的 http、https、mailto 或 tel 地址。") else tr("Addresses without a scheme open with HTTPS.", "未填写协议的地址将通过 HTTPS 打开。")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(tr("Notebook", "笔记本"), color = MutedInk, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(documents.filter { it.trashedAt == null }, key = InkDocument::id) { candidate ->
                            FilterChip(selected = candidate.id == targetDocument.id, onClick = {
                                insertLinkDocumentId = candidate.id
                                insertLinkPageId = candidate.pages.firstOrNull()?.id.orEmpty()
                            }, label = { Text(candidate.title, maxLines = 1) })
                        }
                    }
                    Text(tr("Page", "页面"), color = MutedInk, fontSize = 11.sp)
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(targetDocument.pages, key = InkPage::id) { candidate ->
                            ListItem(
                                headlineContent = { Text(candidate.title, maxLines = 1) },
                                supportingContent = { Text(tr("Page ${targetDocument.pages.indexOf(candidate) + 1}", "第 ${targetDocument.pages.indexOf(candidate) + 1} 页"), color = MutedInk) },
                                leadingContent = { if (candidate.bookmarked) Icon(Icons.Default.Bookmark, null) else Icon(Icons.Outlined.Description, null) },
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { insertLinkPageId = candidate.id },
                                colors = ListItemDefaults.colors(containerColor = if (candidate.id == targetPage?.id) Workspace else Color.Transparent),
                            )
                        }
                    }
                }
            } },
            confirmButton = { Button(enabled = if (insertInternalLink) targetPage != null else normalizedLink != null, onClick = {
                val link = if (insertInternalLink) {
                    val page = targetPage ?: return@Button
                    PageElement.Link(
                        x = .18f,
                        y = .2f,
                        title = insertLinkTitle.trim().ifBlank { page.title },
                        targetDocumentId = targetDocument.id,
                        targetPageId = page.id,
                    )
                } else {
                    val url = normalizedLink ?: return@Button
                    PageElement.Link(x = .18f, y = .2f, title = insertLinkTitle.trim().ifBlank { LinkTarget.displayDestination(url, 48) }, url = url)
                }
                onElement(document.id, selectedPage.id, PageCropTransform.unproject(link, selectedPage.crop))
                linkInsertOpen = false
                tool = EditorTool.SELECT
            }) { Text(tr("Insert", "插入")) } },
            dismissButton = { TextButton(onClick = { linkInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
        )
    }
    if (shapeInsertOpen) AlertDialog(
        onDismissRequest = { shapeInsertOpen = false },
        title = { Text(tr("Draw shape", "绘制形状")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(tr("Choose a shape, then drag on the page with the pen. Lines and arrows snap to nearby 45° angles; Freeform follows the pen and closes the polygon automatically.", "选择形状后用笔在页面上拖动。直线和箭头会吸附到邻近的 45° 角；自由形状会跟随笔迹并自动闭合。"), color = MutedInk, fontSize = 12.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShapeKind.entries.forEach { kind -> item { FilterChip(selected = insertShape == kind, onClick = { insertShape = kind }, label = { Text(localizedShapeKind(kind)) }) } } } } },
        confirmButton = { Button(onClick = { shapeInsertOpen = false; tool = EditorTool.SHAPE; canvasView?.tool = EditorTool.SHAPE }) { Text(tr("Draw", "绘制")) } },
        dismissButton = { TextButton(onClick = { shapeInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (imageInsertOpen) AlertDialog(
        onDismissRequest = { imageInsertOpen = false },
        title = { Text(tr("Add page image", "添加页面图片")) },
        text = { Text(tr("Import an existing image or capture a paper page with the device camera. Image text can then be indexed from Search this note.", "导入现有图片，或用设备相机拍摄纸质页面。随后可在“搜索此笔记”中识别图片文字。")) },
        confirmButton = { Button(onClick = { imageInsertOpen = false; onScanImage() }) { Icon(Icons.Outlined.DocumentScanner, null); Spacer(Modifier.width(6.dp)); Text(tr("Scan page", "扫描页面")) } },
        dismissButton = { Row { TextButton(onClick = { imageInsertOpen = false }) { Text(tr("Cancel", "取消")) }; TextButton(onClick = { imageInsertOpen = false; onInsertImage() }) { Text(tr("Import image", "导入图片")) } } },
    )
    if (outlineOpen) {
        val visibleOutlinePages = PageOutline.visiblePages(document.pages)
        AlertDialog(
            onDismissRequest = { outlineOpen = false },
            title = { Text(tr("Page outline", "页面大纲")) },
            text = {
                LazyColumn(Modifier.widthIn(min = 360.dp, max = 560.dp).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(visibleOutlinePages, key = { it.id }) { page ->
                        val index = document.pages.indexOf(page)
                        val hasChildren = PageOutline.hasChildren(document.pages, index)
                        ListItem(
                            headlineContent = { Text(page.title, maxLines = 1, fontWeight = if (page.outlineLevel == 0) FontWeight.SemiBold else FontWeight.Normal) },
                            supportingContent = { Text(listOfNotNull(page.customNumber, page.tags.takeIf { it.isNotEmpty() }?.joinToString(" · ")).joinToString(" · ").ifBlank { tr("Page ${index + 1}", "第 ${index + 1} 页") }, color = MutedInk, maxLines = 1) },
                            leadingContent = { if (page.bookmarked) Icon(Icons.Default.Bookmark, tr("Bookmarked", "已收藏"), tint = Sage) else Text((index + 1).toString(), color = MutedInk) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (hasChildren) IconButton(onClick = { onTogglePageOutlineCollapsed(document.id, page.id) }) { Icon(if (page.outlineCollapsed) Icons.Default.ArrowRight else Icons.Default.ArrowDropDown, if (page.outlineCollapsed) tr("Expand", "展开") else tr("Collapse", "折叠")) }
                                    Box {
                                        IconButton(onClick = { outlineMenuPageId = page.id }) { Icon(Icons.Outlined.MoreVert, tr("Edit outline item", "编辑大纲项目")) }
                                        DropdownMenu(expanded = outlineMenuPageId == page.id, onDismissRequest = { outlineMenuPageId = null }) {
                                            DropdownMenuItem(
                                                text = { Text(tr("Rename", "重命名")) },
                                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                                onClick = { outlineMenuPageId = null; outlineRenamePageId = page.id; outlineRenameText = page.title },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(tr("Move up", "上移")) },
                                                leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                                                enabled = index > 0,
                                                onClick = { outlineMenuPageId = null; onMovePage(document.id, page.id, -1) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(tr("Move down", "下移")) },
                                                leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                                                enabled = index < document.pages.lastIndex,
                                                onClick = { outlineMenuPageId = null; onMovePage(document.id, page.id, 1) },
                                            )
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text(tr("Promote level", "提升层级")) },
                                                leadingIcon = { Icon(Icons.Outlined.FormatIndentDecrease, null) },
                                                enabled = page.outlineLevel > 0,
                                                onClick = { outlineMenuPageId = null; onChangePageOutlineLevel(document.id, page.id, -1) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(tr("Demote level", "降低层级")) },
                                                leadingIcon = { Icon(Icons.Outlined.FormatIndentIncrease, null) },
                                                enabled = index > 0 && page.outlineLevel < 3,
                                                onClick = { outlineMenuPageId = null; onChangePageOutlineLevel(document.id, page.id, 1) },
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.padding(start = (page.outlineLevel * 18).dp).clickable { selectPageWithViewport(page.id); outlineOpen = false },
                            colors = ListItemDefaults.colors(containerColor = if (page.id == selectedPage.id) SageContainer else Color.Transparent),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { outlineOpen = false }) { Text(tr("Done", "完成")) } },
        )
    }
    outlineRenamePageId?.let { pageId ->
        AlertDialog(
            onDismissRequest = { outlineRenamePageId = null },
            title = { Text(tr("Rename outline item", "重命名大纲项目")) },
            text = { OutlinedTextField(outlineRenameText, { outlineRenameText = it }, label = { Text(tr("Page title", "页面标题")) }, singleLine = true) },
            confirmButton = { Button(enabled = outlineRenameText.isNotBlank(), onClick = { onRenamePage(document.id, pageId, outlineRenameText); outlineRenamePageId = null }) { Text(tr("Save", "保存")) } },
            dismissButton = { TextButton(onClick = { outlineRenamePageId = null }) { Text(tr("Cancel", "取消")) } },
        )
    }
    if (searchOpen) {
        val results = if (documentQuery.isBlank()) emptyList() else document.pages.mapNotNull { page -> PageSearch.find(page, documentQuery)?.let { page to it } }
        val unindexedPdfPages = document.pages.count { it.background is PageBackground.Pdf && it.pdfTextIndexVersion == 0 }
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            title = { Text(tr("Search this note", "搜索此笔记")) },
            text = { Column(Modifier.widthIn(min = 340.dp, max = 560.dp)) {
                OutlinedTextField(documentQuery, { documentQuery = it; searchFocus = null }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(tr("Page, handwriting, image, or PDF text", "页面、手写、图片或 PDF 文字")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = recognitionLanguageTag == "en-US", onClick = { recognitionLanguageTag = "en-US" }, label = { Text("English") })
                    FilterChip(selected = recognitionLanguageTag == "zh-Hans", onClick = { recognitionLanguageTag = "zh-Hans" }, label = { Text("简体中文") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !imageOcrRecognizing,
                        onClick = {
                            recognitionMessage = null
                            onRecognizeImages(document.id, selectedPage.id, recognitionLanguageTag) { result ->
                                recognitionMessage = result.fold(
                                    onSuccess = { count -> if (settings.language == app.inkloom.settings.UiLanguage.SIMPLIFIED_CHINESE) "已识别 $count 行图片文字。" else "Recognized $count image text lines." },
                                    onFailure = { error -> error.message ?: if (settings.language == app.inkloom.settings.UiLanguage.SIMPLIFIED_CHINESE) "图片文字识别失败。" else "Image text recognition failed." },
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (imageOcrRecognizing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.DocumentScanner, null)
                        Spacer(Modifier.width(6.dp)); Text(tr("Recognize images", "识别图片"))
                    }
                }
                OutlinedButton(
                    enabled = !documentRecognizing && !imageOcrRecognizing,
                    onClick = {
                        recognitionMessage = null
                        onRecognizeDocument(document.id, recognitionLanguageTag) { result ->
                            recognitionMessage = result.fold(
                                onSuccess = { count -> if (settings.language == app.inkloom.settings.UiLanguage.SIMPLIFIED_CHINESE) "整本笔记已识别 $count 行内容。" else "Indexed $count text lines across this notebook." },
                                onFailure = { error -> error.message ?: if (settings.language == app.inkloom.settings.UiLanguage.SIMPLIFIED_CHINESE) "整本笔记识别失败。" else "Notebook recognition failed." },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (documentRecognizing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.AutoStories, null)
                    Spacer(Modifier.width(6.dp)); Text(tr("Index the whole notebook", "识别整本笔记"))
                }
                recognitionMessage?.let { Text(it, Modifier.padding(top = 4.dp), color = MutedInk, fontSize = 12.sp) }
                if (unindexedPdfPages > 0) Surface(Modifier.fillMaxWidth().padding(top = 10.dp), color = Workspace, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (pdfIndexing) tr("Indexing PDF text…", "正在索引 PDF 文字…") else tr("$unindexedPdfPages PDF pages need a text index.", "$unindexedPdfPages 个 PDF 页面需要建立文字索引。"), Modifier.weight(1f), color = MutedInk, fontSize = 12.sp)
                        if (pdfIndexing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else TextButton(onClick = { onIndexPdfText(document.id) }) { Text(tr("Index now", "立即索引")) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (documentQuery.isNotBlank() && results.isEmpty()) Text(tr("No matching page titles, tags, object text, recognized handwriting, image text, or indexed PDF text.", "没有匹配的页面标题、标签、对象文字、已识别手写、图片文字或已索引 PDF 文字。"), color = MutedInk)
                else LazyColumn(Modifier.heightIn(max = 340.dp)) { items(results, key = { (page, _) -> page.id }) { (page, hit) ->
                    ListItem(
                        headlineContent = { Text(page.title) },
                        supportingContent = { Text(hit.snippet, maxLines = 2) },
                        leadingContent = { Text(when (hit.source) { PageSearchSource.PDF -> "PDF"; PageSearchSource.OBJECT -> "OBJ"; PageSearchSource.PAGE -> "PAGE"; PageSearchSource.HANDWRITING -> "INK"; PageSearchSource.IMAGE_OCR -> "OCR" }, color = Sage, fontSize = 9.sp) },
                        trailingContent = if (hit.source == PageSearchSource.HANDWRITING && hit.recognizedBlockId != null) {{
                            TextButton(onClick = { handwritingConversionHit = page to hit }) { Text(tr("To text", "转文字")) }
                        }} else null,
                        modifier = Modifier.clickable { searchFocus = hit; selectPageWithViewport(page.id); searchOpen = false },
                    )
                } }
            } },
            confirmButton = { TextButton(onClick = { searchOpen = false }) { Text(tr("Done", "完成")) } },
        )
    }
    handwritingConversionHit?.let { (page, hit) ->
        AlertDialog(
            onDismissRequest = { handwritingConversionHit = null },
            title = { Text(tr("Convert handwriting to text", "手写转文字")) },
            text = { Text(tr("Keep the original ink, or replace those strokes with editable text?", "保留原始笔迹，还是用可编辑文字替换这些笔画？")) },
            confirmButton = {
                Button(onClick = {
                    hit.recognizedBlockId?.let { onConvertHandwriting(document.id, page.id, it, true) }
                    handwritingConversionHit = null
                    searchOpen = false
                    selectPageWithViewport(page.id)
                }) { Text(tr("Replace ink", "替换笔迹")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { handwritingConversionHit = null }) { Text(tr("Cancel", "取消")) }
                    TextButton(onClick = {
                        hit.recognizedBlockId?.let { onConvertHandwriting(document.id, page.id, it, false) }
                        handwritingConversionHit = null
                        searchOpen = false
                        selectPageWithViewport(page.id)
                    }) { Text(tr("Keep ink", "保留笔迹")) }
                }
            },
        )
    }
    if (importError != null) AlertDialog(onDismissRequest = onDismissError, confirmButton = { TextButton(onClick = onDismissError) { Text(tr("OK", "确定")) } }, title = { Text(tr("Import issue", "导入问题")) }, text = { Text(importError) })
}

@Composable
private fun DockedPanelTabStrip(
    dock: WorkspacePanelDock,
    panels: List<WorkspacePanelKind>,
    active: WorkspacePanelKind?,
    stacked: Boolean,
    onSelect: (WorkspacePanelKind) -> Unit,
    onMove: (WorkspacePanelKind, Int) -> Unit,
    onStacked: (Boolean) -> Unit,
) {
    @Composable fun title(panel: WorkspacePanelKind): String = when (panel) {
        WorkspacePanelKind.PAGES -> tr("Pages", "页面")
        WorkspacePanelKind.BRUSHES -> tr("Brushes", "笔刷")
        WorkspacePanelKind.LAYERS -> tr("Layers", "图层")
    }
    fun icon(panel: WorkspacePanelKind): ImageVector = when (panel) {
        WorkspacePanelKind.PAGES -> Icons.Outlined.ViewCarousel
        WorkspacePanelKind.BRUSHES -> Icons.Outlined.Brush
        WorkspacePanelKind.LAYERS -> Icons.Outlined.Layers
    }
    val dockName = when (dock) {
        WorkspacePanelDock.LEFT -> tr("left", "左侧")
        WorkspacePanelDock.RIGHT -> tr("right", "右侧")
        WorkspacePanelDock.BOTTOM -> tr("bottom", "底部")
        WorkspacePanelDock.FREE -> tr("floating", "浮动")
    }
    val stripDescription = tr("$dockName panel tabs", "$dockName 面板标签")
    Surface(
        Modifier.width(34.dp).fillMaxHeight().semantics {
            contentDescription = stripDescription
        },
        color = Color(0xFFF1F1F1),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!stacked) {
                IconButton(
                    onClick = { onStacked(true) },
                    modifier = Modifier.size(32.dp),
                ) { Icon(Icons.Outlined.ViewAgenda, tr("Stack panels in $dockName dock", "堆叠$dockName 面板")) }
            } else {
                panels.forEach { panel ->
                    var dragDistance by remember(panel, panels) { mutableFloatStateOf(0f) }
                    val selected = active == panel
                    val panelTitle = title(panel)
                    val tabDescription = tr("$panelTitle tab. Drag to reorder.", "$panelTitle 标签，可拖动排序")
                    Column(
                        Modifier
                            .width(32.dp)
                            .height(34.dp)
                            .background(if (selected) Color(0xFFD6DCE8) else Color.Transparent)
                            .semantics { contentDescription = tabDescription }
                            .clickable { onSelect(panel) }
                            .pointerInput(panel, panels) {
                                detectDragGestures(
                                    onDragStart = { dragDistance = 0f },
                                    onDragCancel = { dragDistance = 0f },
                                    onDragEnd = {
                                        when {
                                            dragDistance < -20f -> onMove(panel, -1)
                                            dragDistance > 20f -> onMove(panel, 1)
                                        }
                                        dragDistance = 0f
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    dragDistance += amount.y
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon(panel), panelTitle, Modifier.size(18.dp), tint = Ink)
                    }
                }
                IconButton(onClick = { onStacked(false) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.CallSplit, tr("Separate $dockName panel tabs", "拆分$dockName 面板标签"))
                }
            }
        }
    }
}

@Composable
private fun WorkspacePanelDockMenu(
    dock: WorkspacePanelDock,
    onDock: (WorkspacePanelDock) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    mode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    locked: Boolean = false,
    onMode: (WorkspacePanelMode) -> Unit = {},
    onLocked: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = modifier) {
            Icon(
                if (locked) Icons.Outlined.Lock else when (dock) {
                    WorkspacePanelDock.LEFT -> Icons.Outlined.ChevronLeft
                    WorkspacePanelDock.FREE -> Icons.Outlined.OpenWith
                    WorkspacePanelDock.RIGHT -> Icons.Outlined.ChevronRight
                    WorkspacePanelDock.BOTTOM -> Icons.Outlined.KeyboardArrowDown
                },
                contentDescription,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WorkspacePanelMode.entries.forEach { target ->
                DropdownMenuItem(
                    text = { Text(when (target) {
                        WorkspacePanelMode.EXPANDED -> tr("Expanded", "展开")
                        WorkspacePanelMode.COLLAPSED -> tr("Collapsed", "收起")
                        WorkspacePanelMode.ICON_ONLY -> tr("Icon only", "仅图标")
                    }) },
                    leadingIcon = { Icon(when (target) {
                        WorkspacePanelMode.EXPANDED -> Icons.Outlined.UnfoldMore
                        WorkspacePanelMode.COLLAPSED -> Icons.Outlined.UnfoldLess
                        WorkspacePanelMode.ICON_ONLY -> Icons.Outlined.Apps
                    }, null) },
                    trailingIcon = { if (target == mode) Icon(Icons.Outlined.Check, null) },
                    onClick = { expanded = false; onMode(target) },
                )
            }
            DropdownMenuItem(
                text = { Text(if (locked) tr("Unlock position and size", "解锁位置与尺寸") else tr("Lock position and size", "锁定位置与尺寸")) },
                leadingIcon = { Icon(if (locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null) },
                onClick = { expanded = false; onLocked(!locked) },
            )
            HorizontalDivider()
            WorkspacePanelDock.entries.forEach { target ->
                DropdownMenuItem(
                    text = {
                        Text(when (target) {
                            WorkspacePanelDock.LEFT -> tr("Dock left", "停靠左侧")
                            WorkspacePanelDock.FREE -> tr("Float", "自由浮动")
                            WorkspacePanelDock.RIGHT -> tr("Dock right", "停靠右侧")
                            WorkspacePanelDock.BOTTOM -> tr("Dock bottom", "停靠底部")
                        })
                    },
                    leadingIcon = {
                        Icon(
                            when (target) {
                                WorkspacePanelDock.LEFT -> Icons.Outlined.ChevronLeft
                                WorkspacePanelDock.FREE -> Icons.Outlined.OpenWith
                                WorkspacePanelDock.RIGHT -> Icons.Outlined.ChevronRight
                                WorkspacePanelDock.BOTTOM -> Icons.Outlined.KeyboardArrowDown
                            },
                            null,
                        )
                    },
                    trailingIcon = { if (target == dock) Icon(Icons.Outlined.Check, null) },
                    enabled = !locked,
                    onClick = { expanded = false; onDock(target) },
                )
            }
        }
    }
}

@Composable
private fun CompactWorkspacePanel(
    title: String,
    icon: ImageVector,
    dock: WorkspacePanelDock,
    mode: WorkspacePanelMode,
    locked: Boolean,
    onMode: (WorkspacePanelMode) -> Unit,
    onLocked: (Boolean) -> Unit,
    onDock: (WorkspacePanelDock) -> Unit,
    onClose: () -> Unit,
    dockDescription: String,
    modifier: Modifier,
    onPanelDragStart: () -> Unit = {},
    onPanelDrag: (Float, Float) -> Unit = { _, _ -> },
    onPanelDragEnd: () -> Unit = {},
    onPanelDragCancel: () -> Unit = {},
) {
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    val compactDragDescription = tr("Drag $title panel", "拖动$title 面板")
    val draggableModifier = modifier.then(if (!locked) {
        Modifier
            .semantics { contentDescription = compactDragDescription }
            .pointerInput(dock, mode) {
                detectDragGestures(
                    onDragStart = { currentPanelDragStart() },
                    onDragEnd = { currentPanelDragEnd() },
                    onDragCancel = { currentPanelDragCancel() },
                ) { change, amount ->
                    change.consume()
                    currentPanelDrag(amount.x, amount.y)
                }
            }
    } else Modifier)
    Surface(draggableModifier, color = Color(0xFFF7F7F7), border = CardDefaults.outlinedCardBorder()) {
        if (mode == WorkspacePanelMode.ICON_ONLY && dock == WorkspacePanelDock.BOTTOM) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMode(WorkspacePanelMode.EXPANDED) }, modifier = Modifier.size(26.dp)) { Icon(icon, tr("Expand $title panel", "展开$title 面板"), Modifier.size(16.dp)) }
                Spacer(Modifier.weight(1f))
                if (locked) Icon(Icons.Outlined.Lock, tr("Panel locked", "面板已锁定"), Modifier.size(15.dp), tint = MutedInk)
                WorkspacePanelDockMenu(dock, onDock, dockDescription, Modifier.size(26.dp), mode, locked, onMode, onLocked)
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) { Icon(Icons.Outlined.Close, tr("Close $title panel", "关闭$title 面板"), Modifier.size(15.dp)) }
            }
        } else if (mode == WorkspacePanelMode.ICON_ONLY) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { onMode(WorkspacePanelMode.EXPANDED) }, modifier = Modifier.size(32.dp)) { Icon(icon, tr("Expand $title panel", "展开$title 面板"), Modifier.size(18.dp)) }
                WorkspacePanelDockMenu(dock, onDock, dockDescription, Modifier.size(32.dp), mode, locked, onMode, onLocked)
                if (locked) Icon(Icons.Outlined.Lock, tr("Panel locked", "面板已锁定"), Modifier.size(15.dp), tint = MutedInk)
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, tr("Close $title panel", "关闭$title 面板"), Modifier.size(16.dp)) }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(27.dp).background(Color(0xFFE3E3E3)).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMode(WorkspacePanelMode.EXPANDED) }, modifier = Modifier.size(23.dp)) { Icon(icon, tr("Expand $title panel", "展开$title 面板"), Modifier.size(14.dp)) }
                Text(title, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                if (locked) Icon(Icons.Outlined.Lock, tr("Panel locked", "面板已锁定"), Modifier.size(15.dp), tint = MutedInk)
                WorkspacePanelDockMenu(dock, onDock, dockDescription, Modifier.size(23.dp), mode, locked, onMode, onLocked)
                IconButton(onClick = onClose, modifier = Modifier.size(23.dp)) { Icon(Icons.Outlined.Close, tr("Close $title panel", "关闭$title 面板"), Modifier.size(14.dp)) }
            }
        }
    }
}

@Composable
private fun WorkspacePanelDockPreview(dock: WorkspacePanelDock, bounds: WorkspacePanelBounds) {
    val density = LocalDensity.current
    val label = when (dock) {
        WorkspacePanelDock.LEFT -> tr("Release to dock left", "松开停靠到左侧")
        WorkspacePanelDock.RIGHT -> tr("Release to dock right", "松开停靠到右侧")
        WorkspacePanelDock.BOTTOM -> tr("Release to dock bottom", "松开停靠到底部")
        WorkspacePanelDock.FREE -> tr("Keep floating", "保持浮动")
    }
    Box(
        Modifier
            .width(bounds.widthDp.dp)
            .height(bounds.heightDp.dp)
            .graphicsLayer(
                translationX = with(density) { bounds.xDp.dp.toPx() },
                translationY = with(density) { bounds.yDp.dp.toPx() },
            )
            .zIndex(7.5f)
            .background(Color(0x1A000000))
            .border(1.dp, Ink)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = Color(0xFFE3E3E3), shape = RoundedCornerShape(0.dp), border = CardDefaults.outlinedCardBorder()) {
            Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Ink, fontSize = 10.sp)
        }
    }
}

@Composable
private fun WorkspacePanelFloatPreview(bounds: WorkspacePanelBounds) {
    val density = LocalDensity.current
    val label = tr("Release to float", "松开转为浮动面板")
    Box(
        Modifier
            .width(bounds.widthDp.dp)
            .height(bounds.heightDp.dp)
            .graphicsLayer(
                translationX = with(density) { bounds.xDp.dp.toPx() },
                translationY = with(density) { bounds.yDp.dp.toPx() },
            )
            .zIndex(7.5f)
            .background(Color(0x1A000000))
            .border(1.dp, Ink)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = Color(0xFFE3E3E3), shape = RoundedCornerShape(0.dp), border = CardDefaults.outlinedCardBorder()) {
            Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Ink, fontSize = 10.sp)
        }
    }
}

@Composable
private fun WorkspacePanelGroupPreview(bounds: WorkspacePanelBounds, target: WorkspacePanelKind) {
    val density = LocalDensity.current
    val targetName = when (target) {
        WorkspacePanelKind.PAGES -> tr("Pages", "页面")
        WorkspacePanelKind.BRUSHES -> tr("Brushes", "笔刷")
        WorkspacePanelKind.LAYERS -> tr("Layers", "图层")
    }
    val label = tr("Release to group with $targetName", "松开与$targetName 组合")
    Box(
        Modifier
            .width(bounds.widthDp.dp)
            .height(bounds.heightDp.dp)
            .graphicsLayer(
                translationX = with(density) { bounds.xDp.dp.toPx() },
                translationY = with(density) { bounds.yDp.dp.toPx() },
            )
            .zIndex(7.5f)
            .background(Color(0x1A000000))
            .border(1.dp, Ink)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = Color(0xFFE3E3E3), shape = RoundedCornerShape(0.dp), border = CardDefaults.outlinedCardBorder()) {
            Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Ink, fontSize = 10.sp)
        }
    }
}

@Composable
private fun FloatingWorkspacePanel(
    bounds: WorkspacePanelBounds,
    mode: WorkspacePanelMode,
    locked: Boolean,
    zIndex: Float,
    resizeDescription: String,
    onActivate: () -> Unit,
    onResize: (Float, Float, Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    tabs: List<WorkspacePanelKind> = emptyList(),
    activeTab: WorkspacePanelKind? = null,
    onTab: (WorkspacePanelKind) -> Unit = {},
    onMoveTab: (WorkspacePanelKind, Int) -> Unit = { _, _ -> },
    onSeparateTabs: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val currentActivate by rememberUpdatedState(onActivate)
    val currentResize by rememberUpdatedState(onResize)
    val currentResizeEnd by rememberUpdatedState(onResizeEnd)
    val visibleWidth = when (mode) {
        WorkspacePanelMode.EXPANDED -> bounds.widthDp
        WorkspacePanelMode.COLLAPSED -> bounds.widthDp.coerceAtMost(240f)
        WorkspacePanelMode.ICON_ONLY -> 56f
    }
    val visibleHeight = when (mode) {
        WorkspacePanelMode.EXPANDED -> bounds.heightDp
        WorkspacePanelMode.COLLAPSED -> 58f
        WorkspacePanelMode.ICON_ONLY -> 148f
    }
    Box(
        Modifier
            .width(visibleWidth.dp)
            .height(visibleHeight.dp)
            .graphicsLayer(
                translationX = with(density) { bounds.xDp.dp.toPx() },
                translationY = with(density) { bounds.yDp.dp.toPx() },
            )
            .zIndex(zIndex)
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (tabs.size > 1) FloatingPanelTabStrip(tabs, activeTab, locked, onTab, onMoveTab, onSeparateTabs)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
                if (!locked && mode == WorkspacePanelMode.EXPANDED) {
                    PanelResizeEdge(
                        alignment = Alignment.CenterStart,
                        horizontal = false,
                        pointerIconType = android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW,
                        description = "$resizeDescription · ${tr("left edge", "左边框")}",
                        onActivate = currentActivate,
                        onResizeEnd = currentResizeEnd,
                    ) { dx, _ -> currentResize(dx, 0f, 0f, 0f) }
                    PanelResizeEdge(
                        alignment = Alignment.CenterEnd,
                        horizontal = false,
                        pointerIconType = android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW,
                        description = "$resizeDescription · ${tr("right edge", "右边框")}",
                        onActivate = currentActivate,
                        onResizeEnd = currentResizeEnd,
                    ) { dx, _ -> currentResize(0f, 0f, dx, 0f) }
                    PanelResizeEdge(
                        alignment = Alignment.TopCenter,
                        horizontal = true,
                        pointerIconType = android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW,
                        description = "$resizeDescription · ${tr("top edge", "上边框")}",
                        onActivate = currentActivate,
                        onResizeEnd = currentResizeEnd,
                    ) { _, dy -> currentResize(0f, dy, 0f, 0f) }
                    PanelResizeEdge(
                        alignment = Alignment.BottomCenter,
                        horizontal = true,
                        pointerIconType = android.view.PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW,
                        description = "$resizeDescription · ${tr("bottom edge", "下边框")}",
                        onActivate = currentActivate,
                        onResizeEnd = currentResizeEnd,
                    ) { _, dy -> currentResize(0f, 0f, 0f, dy) }
                    PanelResizeCorner(
                        alignment = Alignment.TopStart,
                        pointerIconType = android.view.PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW,
                        description = "$resizeDescription · ${tr("top left", "左上")}",
                        onActivate = currentActivate,
                        onResizeEnd = currentResizeEnd,
                    ) { dx, dy -> currentResize(dx, dy, 0f, 0f) }
                    PanelResizeCorner(
                        alignment = Alignment.TopEnd,
                        pointerIconType = android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW,
                        description = "$resizeDescription · ${tr("top right", "右上")}",
                        onActivate = currentActivate,
                        onResizeEnd = currentResizeEnd,
                    ) { dx, dy -> currentResize(0f, dy, dx, 0f) }
                    PanelResizeCorner(
                        alignment = Alignment.BottomStart,
                        pointerIconType = android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW,
                        description = "$resizeDescription · ${tr("bottom left", "左下")}",
                        onActivate = currentActivate,
                        onResizeEnd = currentResizeEnd,
                    ) { dx, dy -> currentResize(dx, 0f, 0f, dy) }
                    PanelResizeCorner(
                        alignment = Alignment.BottomEnd,
                        pointerIconType = android.view.PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW,
                        description = resizeDescription,
                        onActivate = currentActivate,
                        onResizeEnd = currentResizeEnd,
                    ) { dx, dy -> currentResize(0f, 0f, dx, dy) }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PanelResizeCorner(
    alignment: Alignment,
    pointerIconType: Int,
    description: String,
    onActivate: () -> Unit,
    onResizeEnd: () -> Unit,
    onResize: (Float, Float) -> Unit,
) {
    val currentActivate by rememberUpdatedState(onActivate)
    val currentResize by rememberUpdatedState(onResize)
    val currentResizeEnd by rememberUpdatedState(onResizeEnd)
    val resizePointerIcon = remember(pointerIconType) { androidx.compose.ui.input.pointer.PointerIcon(pointerIconType) }
    Box(
        Modifier
            .align(alignment)
            .size(16.dp)
            .zIndex(3f)
            .pointerHoverIcon(resizePointerIcon, overrideDescendants = true)
            .stylusHoverIcon(resizePointerIcon, overrideDescendants = true)
            .semantics { contentDescription = description }
            .pointerInput(alignment) {
                detectDragGestures(
                    onDragStart = { currentActivate() },
                    onDragEnd = { currentResizeEnd() },
                    onDragCancel = { currentResizeEnd() },
                ) { change, amount ->
                    change.consume()
                    currentResize(amount.x, amount.y)
                }
            },
    )
}

@Composable
private fun BoxScope.PanelResizeEdge(
    alignment: Alignment,
    horizontal: Boolean,
    pointerIconType: Int,
    description: String,
    onActivate: () -> Unit,
    onResizeEnd: () -> Unit,
    onResize: (Float, Float) -> Unit,
) {
    val currentActivate by rememberUpdatedState(onActivate)
    val currentResize by rememberUpdatedState(onResize)
    val currentResizeEnd by rememberUpdatedState(onResizeEnd)
    val resizePointerIcon = remember(pointerIconType) { androidx.compose.ui.input.pointer.PointerIcon(pointerIconType) }
    Box(
        Modifier
            .align(alignment)
            .then(if (horizontal) Modifier.fillMaxWidth().height(12.dp) else Modifier.width(12.dp).fillMaxHeight())
            .zIndex(2f)
            .pointerHoverIcon(resizePointerIcon, overrideDescendants = true)
            .stylusHoverIcon(resizePointerIcon, overrideDescendants = true)
            .semantics { contentDescription = description }
            .pointerInput(description) {
                detectDragGestures(
                    onDragStart = { currentActivate() },
                    onDragEnd = { currentResizeEnd() },
                    onDragCancel = { currentResizeEnd() },
                ) { change, amount ->
                    change.consume()
                    currentResize(amount.x, amount.y)
                }
            },
    )
}

@Composable
private fun DockedSidebarResizeHandle(
    description: String,
    onResize: (Float) -> Unit,
    onResizeEnd: () -> Unit,
) {
    val currentResize by rememberUpdatedState(onResize)
    val currentResizeEnd by rememberUpdatedState(onResizeEnd)
    val resizePointerIcon = remember { androidx.compose.ui.input.pointer.PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW) }
    Box(
        Modifier
            .width(10.dp)
            .fillMaxHeight()
            .zIndex(4f)
            .background(Color.Transparent)
            .pointerHoverIcon(resizePointerIcon, overrideDescendants = true)
            .stylusHoverIcon(resizePointerIcon, overrideDescendants = true)
            .semantics { contentDescription = description }
            .pointerInput(description) {
                detectDragGestures(
                    onDragEnd = { currentResizeEnd() },
                    onDragCancel = { currentResizeEnd() },
                ) { change, amount ->
                    change.consume()
                    currentResize(amount.x)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(OutlineSoft))
    }
}

@Composable
private fun FloatingPanelTabStrip(
    panels: List<WorkspacePanelKind>,
    active: WorkspacePanelKind?,
    locked: Boolean,
    onSelect: (WorkspacePanelKind) -> Unit,
    onMove: (WorkspacePanelKind, Int) -> Unit,
    onSeparate: () -> Unit,
) {
    @Composable fun title(panel: WorkspacePanelKind): String = when (panel) {
        WorkspacePanelKind.PAGES -> tr("Pages", "页面")
        WorkspacePanelKind.BRUSHES -> tr("Brushes", "笔刷")
        WorkspacePanelKind.LAYERS -> tr("Layers", "图层")
    }
    val stripDescription = tr("Floating panel tabs", "浮动面板标签")
    Surface(
        Modifier.fillMaxWidth().height(31.dp).semantics { contentDescription = stripDescription },
        color = Color(0xFFF1F1F1),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            panels.forEach { panel ->
                var dragDistance by remember(panel, panels) { mutableFloatStateOf(0f) }
                val selected = active == panel
                val panelTitle = title(panel)
                val tabDescription = tr("$panelTitle floating tab. Drag to reorder.", "$panelTitle 浮动标签，可拖动排序")
                Box(
                    Modifier
                        .weight(1f)
                        .height(27.dp)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) Color(0xFFE1E1E1) else Color.Transparent)
                        .semantics { contentDescription = tabDescription }
                        .clickable { onSelect(panel) }
                        .then(if (!locked) Modifier.pointerInput(panel, panels) {
                            detectDragGestures(
                                onDragStart = { dragDistance = 0f },
                                onDragCancel = { dragDistance = 0f },
                                onDragEnd = {
                                    when {
                                        dragDistance < -20f -> onMove(panel, -1)
                                        dragDistance > 20f -> onMove(panel, 1)
                                    }
                                    dragDistance = 0f
                                },
                            ) { change, amount -> change.consume(); dragDistance += amount.x }
                        } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(panelTitle, fontSize = 10.sp, maxLines = 1, color = Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            IconButton(onClick = onSeparate, enabled = !locked, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.CallSplit, tr("Separate floating panel tabs", "拆分浮动面板标签"), Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageRail(
    document: InkDocument,
    documents: List<InkDocument>,
    selectedPageId: String,
    dock: WorkspacePanelDock,
    assets: DocumentAssetService,
    onDock: (WorkspacePanelDock) -> Unit,
    onClose: () -> Unit,
    pageTemplates: PageTemplateLibrary,
    onSelect: (String) -> Unit,
    onOpenLayers: (String) -> Unit,
    onAdd: (String, Long?) -> Unit,
    onDuplicate: (String, String) -> Unit,
    onDelete: (String, String) -> Unit,
    onMove: (String, String, Int) -> Unit,
    onReorder: (String, List<String>) -> Unit,
    onTransfer: (String, String, String, Boolean) -> Unit,
    onApplyTemplate: (String, String, String, Long?) -> Unit,
    onApplyTemplates: (String, Set<String>, String, Long?) -> Unit,
    onImportTemplates: () -> Unit,
    onToggleTemplateFavorite: (String) -> Unit,
    onSetDefaultTemplate: (String, String?, Long?) -> Unit,
    onSaveAsTemplate: (String, String, String) -> PageTemplate?,
    onDeleteTemplate: (String) -> Unit,
    onToggleBookmark: (String, String) -> Unit,
    onOutlineLevel: (String, String, Int) -> Unit,
    onOutlineCollapsed: (String, String) -> Unit,
    onRename: (String, String, String) -> Unit,
    onMetadata: (String, String, String?, List<String>) -> Unit,
    onClearInk: (String, String) -> Unit,
    onCrop: (String, String, PageCrop) -> Unit,
    onRotate: (String, String) -> Unit,
    panelMode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    panelLocked: Boolean = false,
    onPanelMode: (WorkspacePanelMode) -> Unit = {},
    onPanelLocked: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier.width(112.dp).fillMaxHeight().padding(6.dp),
    onPanelDragStart: () -> Unit = {},
    onPanelDrag: (Float, Float) -> Unit = { _, _ -> },
    onPanelDragEnd: () -> Unit = {},
    onPanelDragCancel: () -> Unit = {},
) {
    if (panelMode != WorkspacePanelMode.EXPANDED) {
        val compactModifier = when (dock) {
            WorkspacePanelDock.LEFT, WorkspacePanelDock.RIGHT -> Modifier.width(if (panelMode == WorkspacePanelMode.ICON_ONLY) 56.dp else 176.dp).fillMaxHeight()
            WorkspacePanelDock.FREE -> Modifier.fillMaxSize()
            WorkspacePanelDock.BOTTOM -> modifier
        }
        CompactWorkspacePanel(
            tr("Pages", "页面"), Icons.Outlined.ViewCarousel, dock, panelMode, panelLocked,
            onPanelMode, onPanelLocked, onDock, onClose,
            tr("Change pages panel docking", "更改页面面板停靠方式"), compactModifier,
            onPanelDragStart, onPanelDrag, onPanelDragEnd, onPanelDragCancel,
        )
        return
    }
    val transferTargets = documents.filter { it.id != document.id && it.trashedAt == null }
    val dragDescription = tr("Drag pages panel", "拖动页面面板")
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    var addTemplateOpen by remember(document.id) { mutableStateOf(false) }
    var batchTemplateOpen by remember(document.id) { mutableStateOf(false) }
    var managingPages by remember(document.id) { mutableStateOf(false) }
    var selectedPageIds by remember(document.id) { mutableStateOf(emptySet<String>()) }
    val currentSelectedPageIds by rememberUpdatedState(selectedPageIds)
    val pageManagementDescription = tr("Page management: ${selectedPageIds.size} selected", "页面管理：已选择 ${selectedPageIds.size} 页")
    LazyColumn(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Pages", "页面"),
                Modifier.weight(1f).then(
                    if (!panelLocked) Modifier.semantics { contentDescription = dragDescription }.pointerInput(dock) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var last = down.position
                            var moved = false
                            currentPanelDragStart()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val amount = change.position - last
                                if (amount != Offset.Zero) {
                                    moved = true
                                    change.consume()
                                    currentPanelDrag(amount.x, amount.y)
                                    last = change.position
                                }
                            }
                            if (moved) currentPanelDragEnd() else currentPanelDragCancel()
                        }
                    } else Modifier,
                ),
                color = Ink,
                maxLines = 1,
            )
            WorkspacePanelDockMenu(dock, onDock, tr("Change pages panel docking", "更改页面面板停靠方式"), Modifier.size(28.dp), panelMode, panelLocked, onPanelMode, onPanelLocked)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, tr("Close pages panel", "关闭页面面板")) }
            IconButton(onClick = { addTemplateOpen = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, tr("Add page", "添加页面")) }
        }
            if (addTemplateOpen) PageTemplatePickerDialog(
                library = pageTemplates,
                documentId = document.id,
                title = tr("Choose template for new page", "选择新页面模板"),
                currentBackground = null,
                onDismiss = { addTemplateOpen = false },
                onChoose = { template, paperColor -> onAdd(template.id, paperColor); addTemplateOpen = false },
                onImport = onImportTemplates,
                onToggleFavorite = onToggleTemplateFavorite,
                onSetDefault = { templateId, paperColor -> onSetDefaultTemplate(document.id, templateId, paperColor) },
                onDelete = onDeleteTemplate,
            )
        }
        if (managingPages) stickyHeader(key = "page-management") {
            Column(Modifier.fillMaxWidth().background(Color(0xFFF3F3F3), RoundedCornerShape(4.dp)).padding(4.dp)) {
                Row(Modifier.semantics { contentDescription = pageManagementDescription }, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { managingPages = false; selectedPageIds = emptySet() }, modifier = Modifier.size(26.dp)) { Icon(Icons.Outlined.Close, tr("Exit page management", "退出页面管理"), Modifier.size(16.dp)) }
                    Text("${selectedPageIds.size} ${tr("selected", "已选择")}", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { selectedPageIds = document.pages.mapTo(linkedSetOf(), InkPage::id) }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(tr("All", "全选"), fontSize = 9.sp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { batchTemplateOpen = true }, enabled = selectedPageIds.isNotEmpty(), modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Description, tr("Replace selected page templates", "批量替换所选页面模板"), Modifier.size(16.dp))
                    }
                    IconButton(onClick = { document.pages.filter { it.id in selectedPageIds }.forEach { onDuplicate(document.id, it.id) }; managingPages = false; selectedPageIds = emptySet() }, enabled = selectedPageIds.isNotEmpty(), modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.ContentCopy, tr("Duplicate selected pages", "复制所选页面"), Modifier.size(16.dp)) }
                    IconButton(onClick = {
                        document.pages.filter { it.id in selectedPageIds }.take((document.pages.size - 1).coerceAtLeast(0)).forEach { onDelete(document.id, it.id) }
                        managingPages = false; selectedPageIds = emptySet()
                    }, enabled = selectedPageIds.isNotEmpty() && document.pages.size > 1, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, tr("Delete selected pages", "删除所选页面"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        items(document.pages, key = { it.id }) { page ->
            val pageIndex = document.pages.indexOf(page)
            val hasOutlineChildren = PageOutline.hasChildren(document.pages, pageIndex)
            var menu by remember { mutableStateOf(false) }
            var renameOpen by remember { mutableStateOf(false) }
            var metadataOpen by remember { mutableStateOf(false) }
            var clearOpen by remember { mutableStateOf(false) }
            var transferOpen by remember { mutableStateOf(false) }
            var templateOpen by remember { mutableStateOf(false) }
            var saveTemplateNameOpen by remember { mutableStateOf(false) }
            var templateName by remember(page.id) { mutableStateOf(page.title) }
            var cropOpen by remember { mutableStateOf(false) }
            var cropDraft by remember(page.crop) { mutableStateOf(page.crop) }
            var title by remember(page.title) { mutableStateOf(page.title) }
            var customNumber by remember(page.customNumber) { mutableStateOf(page.customNumber.orEmpty()) }
            var tags by remember(page.tags) { mutableStateOf(page.tags.joinToString(", ")) }
            var pageDragDistance by remember(page.id) { mutableFloatStateOf(0f) }
            var pageDragging by remember(page.id) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val pageBatchSelected = page.id in selectedPageIds
                val pageDescription = if (pageBatchSelected) {
                    tr("Page ${pageIndex + 1}. Selected for management.", "第 ${pageIndex + 1} 页，已选择管理")
                } else {
                    tr("Page ${pageIndex + 1}. Long press to manage.", "第 ${pageIndex + 1} 页，长按管理")
                }
                val selectPage: () -> Unit = {
                    if (managingPages) selectedPageIds = if (page.id in currentSelectedPageIds) currentSelectedPageIds - page.id else currentSelectedPageIds + page.id
                    else onSelect(page.id)
                }
                val managePage: () -> Unit = {
                    managingPages = true
                    selectedPageIds = currentSelectedPageIds + page.id
                }
                fun finishPageDrag() {
                    val moved = kotlin.math.abs(pageDragDistance) >= 18.dp.value
                    if (moved) {
                        val delta = (pageDragDistance / 96.dp.value).toInt().let { if (it == 0) if (pageDragDistance > 0f) 1 else -1 else it }
                        val selected = if (managingPages && selectedPageIds.isNotEmpty()) selectedPageIds + page.id else setOf(page.id)
                        val target = (pageIndex + delta).coerceIn(0, document.pages.lastIndex)
                        onReorder(document.id, PageBatchOperations.moveBlock(document.pages.map(InkPage::id), selected, target))
                    } else {
                        managePage()
                    }
                    pageDragDistance = 0f
                    pageDragging = false
                }
                fun Modifier.pageReorderDrag(): Modifier = pointerInput(page.id, managingPages, document.pages.map(InkPage::id)) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pageDragDistance = 0f; pageDragging = true },
                        onDragEnd = ::finishPageDrag,
                        onDragCancel = { pageDragDistance = 0f; pageDragging = false },
                    ) { change, amount ->
                        change.consume()
                        pageDragDistance += amount.y / density
                    }
                }
                fun Modifier.pageHandleDrag(): Modifier = pointerInput(page.id, managingPages, document.pages.map(InkPage::id)) {
                    detectDragGestures(
                        onDragStart = { pageDragDistance = 0f; pageDragging = true },
                        onDragEnd = ::finishPageDrag,
                        onDragCancel = { pageDragDistance = 0f; pageDragging = false },
                    ) { change, amount ->
                        change.consume()
                        pageDragDistance += amount.y / density
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            (PageCropTransform.visibleWidth(page) / PageCropTransform.visibleHeight(page).coerceAtLeast(1f))
                                .coerceIn(.5f, 1.8f),
                        )
                        .zIndex(if (pageDragging) 4f else 0f)
                        .graphicsLayer {
                            translationY = pageDragDistance * density
                            scaleX = if (pageDragging) 1.06f else 1f
                            scaleY = if (pageDragging) 1.06f else 1f
                            shadowElevation = if (pageDragging) 12.dp.toPx() else 0f
                        }
                        .background(if (pageBatchSelected) Color(0xFFDDE8F7) else Color.White, RoundedCornerShape(5.dp))
                        .border(if (page.id == selectedPageId || pageBatchSelected) 2.dp else 1.dp, if (page.id == selectedPageId || pageBatchSelected) Sage else OutlineSoft, RoundedCornerShape(5.dp))
                        .padding(4.dp),
                ) {
                    PageInkPreview(page, assets, Modifier.matchParentSize().clip(RoundedCornerShape(2.dp)))
                    if (page.background is PageBackground.Pdf) Text("PDF", fontSize = 9.sp, color = Sage)
                    if (page.bookmarked) Icon(Icons.Default.Bookmark, tr("Bookmarked", "已收藏"), tint = Sage, modifier = Modifier.size(16.dp).align(Alignment.TopEnd))
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(24.dp).background(Color(0xFFE9E9E9), RoundedCornerShape(2.dp)).clickable { onOpenLayers(page.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Layers, tr("Open layers for this page", "打开此页面的图层"), Modifier.size(15.dp))
                    }
                    Box(Modifier.matchParentSize().pointerInput(page.id, managingPages) {
                        val layerHotspot = 24.dp.toPx()
                        detectTapGestures(onTap = { position ->
                            if (position.x >= size.width - layerHotspot && position.y >= size.height - layerHotspot) onOpenLayers(page.id)
                            else selectPage()
                        })
                    }.pageReorderDrag().semantics {
                        contentDescription = pageDescription
                        onClick { selectPage(); true }
                        onLongClick { managePage(); true }
                    })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        tr("Drag to reorder page", "拖动调整页面顺序"),
                        Modifier.size(36.dp).padding(6.dp).pageHandleDrag(),
                        tint = MutedInk,
                    )
                    Text(page.customNumber ?: (document.pages.indexOf(page) + 1).toString(), color = if (page.id == selectedPageId) Sage else MutedInk, modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).clickable { onSelect(page.id) }.padding(vertical = 6.dp), maxLines = 1)
                    if (page.id == selectedPageId) {
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Workspace).clickable { menu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreVert, tr("Page menu", "页面菜单"), modifier = Modifier.size(16.dp)) }
                    } else {
                        Spacer(Modifier.size(26.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Duplicate", "复制")) }, onClick = { menu = false; onDuplicate(document.id, page.id) })
                        DropdownMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { menu = false; renameOpen = true })
                        DropdownMenuItem(text = { Text(tr("Number and tags", "页码与标签")) }, onClick = { menu = false; metadataOpen = true })
                        DropdownMenuItem(text = { Text(tr("Move up", "上移")) }, enabled = document.pages.first().id != page.id, onClick = { menu = false; onMove(document.id, page.id, -1) })
                        DropdownMenuItem(text = { Text(tr("Move down", "下移")) }, enabled = document.pages.last().id != page.id, onClick = { menu = false; onMove(document.id, page.id, 1) })
                        DropdownMenuItem(text = { Text(tr("Move or copy to note", "移动或复制到笔记")) }, enabled = transferTargets.isNotEmpty(), onClick = { menu = false; transferOpen = true })
                        DropdownMenuItem(text = { Text(tr("Increase outline indent", "增加大纲缩进")) }, enabled = pageIndex > 0 && page.outlineLevel < 3 && page.outlineLevel <= document.pages[pageIndex - 1].outlineLevel, onClick = { menu = false; onOutlineLevel(document.id, page.id, 1) })
                        DropdownMenuItem(text = { Text(tr("Decrease outline indent", "减少大纲缩进")) }, enabled = page.outlineLevel > 0, onClick = { menu = false; onOutlineLevel(document.id, page.id, -1) })
                        if (hasOutlineChildren) DropdownMenuItem(text = { Text(if (page.outlineCollapsed) tr("Expand outline children", "展开子页面") else tr("Collapse outline children", "折叠子页面")) }, onClick = { menu = false; onOutlineCollapsed(document.id, page.id) })
                        DropdownMenuItem(text = { Text(tr("Page template", "页面模板")) }, onClick = { menu = false; templateOpen = true })
                        DropdownMenuItem(text = { Text(tr("Crop page", "裁剪页面")) }, onClick = { menu = false; cropDraft = page.crop; cropOpen = true })
                        DropdownMenuItem(text = { Text(tr("Rotate 90° clockwise", "顺时针旋转 90°")) }, onClick = { menu = false; onRotate(document.id, page.id) })
                        DropdownMenuItem(text = { Text(if (page.bookmarked) tr("Remove bookmark", "取消收藏") else tr("Bookmark", "收藏")) }, onClick = { menu = false; onToggleBookmark(document.id, page.id) })
                        DropdownMenuItem(
                            text = { Text(tr("Clear unlocked paint", "清除未锁定绘画内容")) },
                            enabled = page.layers.any { layer ->
                                layer.isPaintSurfaceLayer() &&
                                    !LayerHierarchy.effectiveLocked(page.layers, layer) &&
                                    layer.rasterTileSurface != null
                            },
                            onClick = { menu = false; clearOpen = true },
                        )
                        DropdownMenuItem(text = { Text(tr("Delete", "删除")) }, enabled = document.pages.size > 1, onClick = { menu = false; onDelete(document.id, page.id) })
                    }
                }
            }
            if (renameOpen) AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text(tr("Rename page", "重命名页面")) }, text = { OutlinedTextField(title, { title = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onRename(document.id, page.id, title); renameOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(tr("Cancel", "取消")) } })
            if (metadataOpen) AlertDialog(onDismissRequest = { metadataOpen = false }, title = { Text(tr("Page metadata", "页面信息")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(customNumber, { customNumber = it }, label = { Text(tr("Custom page number", "自定义页码")) }, singleLine = true); OutlinedTextField(tags, { tags = it }, label = { Text(tr("Tags", "标签")) }, supportingText = { Text(tr("Separate tags with commas", "用逗号分隔标签")) }) } }, confirmButton = { TextButton(onClick = { onMetadata(document.id, page.id, customNumber, tags.split(',')); metadataOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { metadataOpen = false }) { Text(tr("Cancel", "取消")) } })
            if (templateOpen) PageTemplatePickerDialog(
                library = pageTemplates,
                documentId = document.id,
                title = tr("Choose page template", "选择页面模板"),
                currentBackground = page.background,
                onDismiss = { templateOpen = false },
                onChoose = { template, paperColor -> onApplyTemplate(document.id, page.id, template.id, paperColor); templateOpen = false },
                onImport = onImportTemplates,
                onToggleFavorite = onToggleTemplateFavorite,
                onSetDefault = { templateId, paperColor -> onSetDefaultTemplate(document.id, templateId, paperColor) },
                onSaveCurrent = { templateName = page.title; templateOpen = false; saveTemplateNameOpen = true },
                onDelete = onDeleteTemplate,
            )
            if (saveTemplateNameOpen) AlertDialog(
                onDismissRequest = { saveTemplateNameOpen = false },
                title = { Text(tr("Save page as template", "将页面保存为模板")) },
                text = { OutlinedTextField(templateName, { templateName = it }, label = { Text(tr("Template name", "模板名称")) }, singleLine = true) },
                confirmButton = { TextButton(onClick = { onSaveAsTemplate(document.id, page.id, templateName); saveTemplateNameOpen = false }) { Text(tr("Save", "保存")) } },
                dismissButton = { TextButton(onClick = { saveTemplateNameOpen = false }) { Text(tr("Cancel", "取消")) } },
            )
            if (clearOpen) AlertDialog(onDismissRequest = { clearOpen = false }, title = { Text(tr("Clear page paint?", "清除页面绘画内容？")) }, text = { Text(tr("All pixels on unlocked raster layers will be removed. Locked layers are preserved.", "将删除所有未锁定栅格图层中的像素，锁定图层会保留。")) }, confirmButton = { TextButton(onClick = { onClearInk(document.id, page.id); clearOpen = false }) { Text(tr("Clear", "清除")) } }, dismissButton = { TextButton(onClick = { clearOpen = false }) { Text(tr("Cancel", "取消")) } })
            if (cropOpen) AlertDialog(
                onDismissRequest = { cropOpen = false },
                title = { Text(tr("Crop page", "裁剪页面")) },
                text = {
                    LazyColumn(Modifier.widthIn(min = 340.dp, max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { Text(tr("The source PDF/image and all page content remain unchanged. Resetting the crop restores the full page.", "源 PDF、图片及页面内容不会改变；重置裁剪即可恢复完整页面。"), color = MutedInk, fontSize = 12.sp) }
                        item { BrushSettingSlider("${tr("Left", "左")}  ${(cropDraft.left * 100).toInt()}%", cropDraft.left, 0f..0.95f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(left = it)) } }
                        item { BrushSettingSlider("${tr("Top", "上")}  ${(cropDraft.top * 100).toInt()}%", cropDraft.top, 0f..0.95f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(top = it)) } }
                        item { BrushSettingSlider("${tr("Right", "右")}  ${(cropDraft.right * 100).toInt()}%", cropDraft.right, .05f..1f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(right = it)) } }
                        item { BrushSettingSlider("${tr("Bottom", "下")}  ${(cropDraft.bottom * 100).toInt()}%", cropDraft.bottom, .05f..1f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(bottom = it)) } }
                        item { OutlinedButton(onClick = { cropDraft = PageCrop() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(tr("Reset to full page", "重置为完整页面")) } }
                    }
                },
                confirmButton = { Button(onClick = { onCrop(document.id, page.id, cropDraft); cropOpen = false }) { Text(tr("Apply", "应用")) } },
                dismissButton = { TextButton(onClick = { cropOpen = false }) { Text(tr("Cancel", "取消")) } },
            )
            if (transferOpen) AlertDialog(
                onDismissRequest = { transferOpen = false },
                title = { Text(tr("Move or copy page", "移动或复制页面")) },
                text = {
                    LazyColumn(Modifier.widthIn(min = 360.dp, max = 560.dp).heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(transferTargets, key = { it.id }) { target ->
                            ListItem(
                                headlineContent = { Text(target.title, maxLines = 1) },
                                supportingContent = { Text(tr("${target.pages.size} pages", "${target.pages.size} 页"), color = MutedInk) },
                                trailingContent = { Row { TextButton(onClick = { onTransfer(document.id, page.id, target.id, false); transferOpen = false }) { Text(tr("Move", "移动")) }; TextButton(onClick = { onTransfer(document.id, page.id, target.id, true); transferOpen = false }) { Text(tr("Copy", "复制")) } } },
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { transferOpen = false }) { Text(tr("Cancel", "取消")) } },
            )
        }
    }
    if (batchTemplateOpen) PageTemplatePickerDialog(
        library = pageTemplates,
        documentId = document.id,
        title = tr("Replace template on ${selectedPageIds.size} pages", "替换 ${selectedPageIds.size} 页的模板"),
        currentBackground = null,
        onDismiss = { batchTemplateOpen = false },
        onChoose = { template, paperColor ->
            onApplyTemplates(document.id, selectedPageIds, template.id, paperColor)
            batchTemplateOpen = false
            managingPages = false
            selectedPageIds = emptySet()
        },
        onImport = onImportTemplates,
        onToggleFavorite = onToggleTemplateFavorite,
        onSetDefault = { templateId, paperColor -> onSetDefaultTemplate(document.id, templateId, paperColor) },
        onDelete = onDeleteTemplate,
    )
}

@Composable
private fun PageInkPreview(page: InkPage, assets: DocumentAssetService, modifier: Modifier = Modifier) {
    val thumbnail by produceState<android.graphics.Bitmap?>(initialValue = null, page) {
        value = withContext(Dispatchers.IO) { runCatching { assets.renderPageBitmap(page, 512) }.getOrNull() }
    }
    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!.asImageBitmap(),
            contentDescription = tr("Full page preview", "整页预览"),
            modifier = modifier.background(Color.White),
            contentScale = ContentScale.Fit,
        )
        return
    }
    Canvas(modifier.background(Color.White)) {
        (page.background as? PageBackground.Paper)?.let { paper ->
            drawIntoCanvas { canvas -> PaperPatternRenderer.draw(canvas.nativeCanvas, paper.pattern, paper.density, size.width, size.height) }
        }
    }
}

@Composable
private fun PageTemplatePickerDialog(
    library: PageTemplateLibrary,
    documentId: String,
    title: String,
    currentBackground: PageBackground?,
    onDismiss: () -> Unit,
    onChoose: (PageTemplate, Long?) -> Unit,
    onImport: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSetDefault: (String?, Long?) -> Unit,
    onDelete: (String) -> Unit,
    onSaveCurrent: (() -> Unit)? = null,
) {
    val all = library.allTemplates()
    val ordered = all.filter { it.id in library.favoriteIds } + all.filterNot { it.id in library.favoriteIds }
    val defaultId = library.notebookDefaults[documentId] ?: PageTemplateCatalog.blank.id
    var paperColor by remember(documentId, currentBackground) {
        mutableLongStateOf(
            (currentBackground as? PageBackground.Paper)?.colorArgb
                ?: library.notebookPaperColors[documentId]
                ?: PaperColorCatalog.WHITE,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.widthIn(min = 440.dp, max = 640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr(
                        "Choose a reusable paper, image, or PDF background. Existing ink and objects are preserved.",
                        "选择可复用的纸张、图片或 PDF 背景；现有墨迹与对象会完整保留。",
                    ),
                    color = MutedInk,
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImport) { Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(6.dp)); Text(tr("Import image/PDF", "导入图片/PDF")) }
                    if (onSaveCurrent != null) OutlinedButton(onClick = onSaveCurrent) { Icon(Icons.Outlined.BookmarkAdd, null); Spacer(Modifier.width(6.dp)); Text(tr("Save current", "保存当前页")) }
                }
                Text(tr("Paper color", "纸张颜色"), color = MutedInk, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaperColorCatalog.options.forEach { option ->
                        val label = paperColorName(option)
                        val description = tr("Paper color $label", "纸张颜色 $label")
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(option.colorArgb), CircleShape)
                                .border(if (paperColor == option.colorArgb) 2.dp else 1.dp, if (paperColor == option.colorArgb) Ink else OutlineSoft, CircleShape)
                                .clickable { paperColor = option.colorArgb }
                                .semantics { contentDescription = description },
                        )
                    }
                }
                LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(ordered, key = { it.id }) { template ->
                        val displayedTemplate = PageTemplateOperations.withPaperColor(template, paperColor)
                        val selected = currentBackground == displayedTemplate.background
                        val builtIn = PageTemplateCatalog.isBuiltIn(template.id)
                        val isDefault = template.id == defaultId
                        val displayName = pageTemplateName(template)
                        val favoriteDescription = tr("Favorite $displayName template", "收藏 $displayName 模板")
                        val defaultDescription = tr("Set $displayName as notebook default", "将 $displayName 设为笔记默认模板")
                        val useDescription = tr("Use $displayName template", "使用 $displayName 模板")
                        ListItem(
                            headlineContent = { Text(displayName, maxLines = 1) },
                            supportingContent = {
                                Text(
                                    "${pageTemplateKind(template)} · ${template.width.toInt()} × ${template.height.toInt()}" + if (isDefault) tr(" · notebook default", " · 笔记默认") else "",
                                    color = MutedInk,
                                    fontSize = 11.sp,
                                )
                            },
                            leadingContent = { PageTemplatePreview(displayedTemplate, selected) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onToggleFavorite(template.id) }, modifier = Modifier.size(34.dp).semantics { contentDescription = favoriteDescription }) {
                                        Icon(if (template.id in library.favoriteIds) Icons.Filled.Star else Icons.Outlined.StarBorder, null, tint = Ink)
                                    }
                                    IconButton(onClick = { onSetDefault(if (isDefault) null else template.id, (displayedTemplate.background as? PageBackground.Paper)?.colorArgb) }, modifier = Modifier.size(34.dp).semantics { contentDescription = defaultDescription }) {
                                        Icon(if (isDefault) Icons.Filled.Home else Icons.Outlined.Home, null, tint = Ink)
                                    }
                                    if (!builtIn) IconButton(onClick = { onDelete(template.id) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Outlined.Delete, tr("Delete template", "删除模板"), tint = MutedInk)
                                    }
                                    TextButton(onClick = { onChoose(template, (displayedTemplate.background as? PageBackground.Paper)?.colorArgb) }, modifier = Modifier.semantics { contentDescription = useDescription }) { Text(if (selected) tr("Selected", "已选择") else tr("Use", "使用")) }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = if (selected) Workspace else Color.Transparent),
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun PageTemplatePreview(template: PageTemplate, selected: Boolean) {
    val borderColor = if (selected) Ink else OutlineSoft
    val pageColor = (template.background as? PageBackground.Paper)?.colorArgb?.let(::Color) ?: Color.White
    Box(
        Modifier.size(42.dp, 54.dp).background(pageColor, RoundedCornerShape(4.dp)).border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (val background = template.background) {
            is PageBackground.Paper -> Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                drawIntoCanvas { canvas -> PaperPatternRenderer.draw(canvas.nativeCanvas, background.pattern, background.density, size.width, size.height) }
            }
            is PageBackground.Image -> Icon(Icons.Outlined.Image, null, tint = MutedInk)
            is PageBackground.Pdf -> Text("PDF", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun paperColorName(option: PaperColorOption): String = when (option.id) {
    "white" -> tr("White", "白色")
    "warm-ivory" -> tr("Warm ivory", "暖象牙色")
    "soft-green" -> tr("Soft green", "柔和绿色")
    "soft-blue" -> tr("Soft blue", "柔和蓝色")
    "soft-gray" -> tr("Soft gray", "柔和灰色")
    else -> option.id
}

@Composable
private fun pageTemplateName(template: PageTemplate): String {
    if (!PageTemplateCatalog.isBuiltIn(template.id)) return template.name
    val paper = template.background as? PageBackground.Paper ?: return template.name
    val name = when (paper.pattern) {
        PaperPattern.PLAIN -> return tr("Blank", "空白")
        PaperPattern.RULED -> tr("Ruled", "横线")
        PaperPattern.GRID -> tr("Grid", "方格")
        PaperPattern.DOT -> tr("Dot grid", "点阵")
        PaperPattern.CORNELL -> tr("Cornell notes", "康奈尔笔记")
    }
    val spacing = when (paper.density) {
        PaperDensity.DENSE -> tr("Dense", "密集")
        PaperDensity.MEDIUM -> tr("Medium", "适中")
        PaperDensity.SPACIOUS -> tr("Spacious", "宽松")
    }
    return "$name · $spacing"
}

@Composable
private fun pageTemplateKind(template: PageTemplate): String = when (template.background) {
    is PageBackground.Paper -> tr("Paper", "纸张")
    is PageBackground.Image -> tr("Image", "图片")
    is PageBackground.Pdf -> tr("PDF page", "PDF 页面")
}

@Composable
private fun PaperCanvas(modifier: Modifier, tool: EditorTool, onView: (InkCanvasView) -> Unit, page: InkPage, documentId: String, onSetLayerRasterTileSurface: (String, String, String, RasterTileSurfaceRef?, Int) -> Unit, onElement: (String, String, PageElement) -> Unit, onRemoveElement: (String, String, String) -> Unit, onDuplicateElement: (String, String, String) -> Unit, selectedElementId: String?, onSelectElement: (String?) -> Unit, assets: DocumentAssetService, selectionRuntime: DocumentSelectionRuntime, settings: AppSettings, viewportState: ViewportState, onZoom: (Float, Float, Float) -> Unit, onPinch: (Float, Float, Float, Float, Float) -> Unit, onPan: (Float, Float) -> Unit, onRotate: (Float) -> Unit, onUndoGesture: () -> Unit, onRedoGesture: () -> Unit, onCanvasContact: () -> Unit, onColorDropCommitted: (Int) -> Unit, activeBrush: ImportedBrush?, brushSize: Float, brushOpacity: Float, brushColor: Int, secondaryBrushColor: Int, shapeKind: ShapeKind, activeLayerId: String, eraserPreset: EraserPreset, forceNavigation: Boolean, rotateNavigation: Boolean, searchBounds: PdfTextBounds?, onPdfGotoLink: (PdfGotoLink) -> Unit, documents: List<InkDocument>, onOpenInternalLink: (String, String) -> Unit, onInputDiagnostic: (String, Float, Float, Int) -> Unit, onColorSampled: (Int) -> Unit, onBrushSizeAdjusted: (Float) -> Unit, onKeyInputDiagnostic: (KeyInputDiagnostic) -> Unit, onMotionInputDiagnostic: (MotionInputDiagnostic) -> Unit, onShortcutAction: (ShortcutActivation) -> Unit, onRulerGuideChange: (RulerGuideState) -> Unit, onCanvasGuideChange: (CanvasGuideState) -> Unit, onPerspectiveGuideChange: (PerspectiveGuideState) -> Unit, onSpecialRulerChange: (SpecialRulerState) -> Unit, onSelectionActiveChanged: (Boolean) -> Unit) {
    val renderedPage = remember(page) { PageCropTransform.project(page) }
    val selectionMaskSize = remember(renderedPage.width, renderedPage.height) {
        requireNotNull(SelectionMaskCompositor.maskSize(renderedPage.width, renderedPage.height))
    }
    val documentSelectionState = remember(documentId, page.id, selectionMaskSize) {
        selectionRuntime.stateFor(documentId, page.id, selectionMaskSize.width, selectionMaskSize.height)
    }
    val layerMaskSurfaces = remember(page.layers.map { it.id to it.mask?.surface }) {
        page.layers.mapNotNull { layer ->
            layer.mask?.surface?.let { reference ->
                runCatching { assets.loadMaskTileSurface(reference).snapshot() }.getOrNull()?.let { layer.id to it }
            }
        }.toMap()
    }
    val context = LocalContext.current
    val gradientCommitScope = rememberCoroutineScope()
    val gradientCommitMutex = remember(documentId, page.id) { Mutex() }
    val rasterCommitMutex = remember(documentId, page.id) { Mutex() }
    val rasterSurfaceRuntime = remember(documentId, page.id) {
        RasterLayerSurfaceRuntime(renderWidth = 1200, loader = assets::renderRasterLayerSurface)
    }
    val latestRasterAssets = remember(documentId, page.id) {
        page.layers.associate { layer ->
            layer.id to (layer.rasterTileSurface to layer.rasterRotationQuarterTurns)
        }.toMutableMap()
    }
    LaunchedEffect(page.layers) {
        page.layers.forEach { layer ->
            latestRasterAssets[layer.id] = layer.rasterTileSurface to layer.rasterRotationQuarterTurns
        }
    }
    val gradientSaveFailure = tr("The gradient mask could not be saved", "渐变蒙版保存失败")
    val commitGradient: (Float, Float, Float, Float, GradientDefinition, app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot?) -> Unit = commit@{ sx, sy, ex, ey, definition, selectionMask ->
        if (gradientCommitScope.coroutineContext[Job]?.isActive != true) {
            return@commit
        }
        val stops = definition.normalized().stops
        val capturedPage = renderedPage
        val capturedCrop = page.crop
        val capturedLayerId = activeLayerId
        // A completed gesture is durable work: page navigation may dispose this composition, but
        // must not leave a reserved mask behind or silently drop the committed gradient.
        gradientCommitScope.launch(NonCancellable) {
            var reservedMaskPath: String? = null
            try {
                gradientCommitMutex.withLock {
                    val maskPath = if (selectionMask == null) null else withContext(Dispatchers.IO) {
                        assets.saveFillMask(selectionMask)
                    }
                    reservedMaskPath = maskPath
                    val gradient = PageElement.Shape(
                        x = 0f, y = 0f, width = capturedPage.width, height = capturedPage.height,
                        layerId = capturedLayerId, kind = ShapeKind.RECTANGLE,
                        colorArgb = stops.first().colorArgb, fillArgb = stops.first().colorArgb, strokeWidth = 0f,
                        gradientEndArgb = stops.last().colorArgb,
                        gradientStartX = sx, gradientStartY = sy,
                        gradientEndX = ex, gradientEndY = ey,
                        gradientKind = definition.kind, gradientEdgeMode = definition.edgeMode,
                        gradientReverse = definition.reverse, gradientDither = definition.dither,
                        gradientEllipseRatio = definition.ellipseRatio, gradientStops = stops,
                        maskAssetPath = maskPath,
                    )
                    onElement(documentId, page.id, PageCropTransform.unproject(gradient, capturedCrop))
                }
            } catch (error: Exception) {
                if (error !is kotlinx.coroutines.CancellationException) Toast.makeText(context, gradientSaveFailure, Toast.LENGTH_SHORT).show()
            } finally {
                assets.releaseAssetReservation(reservedMaskPath)
            }
        }
    }
    fun installFillCommit(view: InkCanvasView) {
        view.onSparseFillCommitted = { mask, color, opacity, edge -> view.paintFillIntoActiveRaster(mask, color, opacity, edge) }
    }
    fun installRasterCommit(view: InkCanvasView) {
        view.onRasterLayerCommitted = commit@{ commit ->
            val layerId = commit.layerId
            val capturedPage = page
            val layer = capturedPage.layers.firstOrNull { it.id == layerId && it.isPaintSurfaceLayer() }
            if (layer == null || gradientCommitScope.coroutineContext[Job]?.isActive != true) {
                return@commit
            }
            gradientCommitScope.launch(NonCancellable) {
                var newReference: RasterTileSurfaceRef? = null
                var patch: RasterLayerPatch? = null
                try {
                    rasterCommitMutex.withLock {
                        patch = withContext(Dispatchers.Default) { commit.capturePatch() }
                        val capturedPatch = requireNotNull(patch)
                        val previous = latestRasterAssets[layerId] ?:
                            (layer.rasterTileSurface to layer.rasterRotationQuarterTurns)
                        val effectivePage = capturedPage.copy(layers = capturedPage.layers.map { candidate ->
                            if (candidate.id != layerId) candidate else candidate.copy(
                                rasterTileSurface = previous.first,
                                rasterRotationQuarterTurns = previous.second,
                            )
                        })
                        val surface = withContext(Dispatchers.Default) {
                            assets.composeVisibleRasterTilePatch(
                                page = effectivePage,
                                layerId = layerId,
                                patch = capturedPatch.bitmap,
                                patchLeft = capturedPatch.left,
                                patchTop = capturedPatch.top,
                                visibleSourceWidth = capturedPatch.sourceWidth,
                                visibleSourceHeight = capturedPatch.sourceHeight,
                            )
                        }
                        newReference = withContext(Dispatchers.IO) {
                            assets.saveRasterTileSurface(surface, previous.first)
                        }
                        val savedReference = requireNotNull(newReference)
                        withContext(Dispatchers.Main.immediate) {
                            latestRasterAssets[layerId] = savedReference to previous.second
                            // The live surface can already contain later gestures. Associate this
                            // checkpoint with it before publishing the document reference so the
                            // resulting Compose update cannot replace newer pixels with older disk
                            // projection data. Document/Compose state is always published on main,
                            // even when a test or future host supplies a non-main parent scope.
                            rasterSurfaceRuntime.acceptPublished(capturedPage, layerId, savedReference, previous.second)
                            onSetLayerRasterTileSurface(documentId, capturedPage.id, layerId, savedReference, previous.second)
                        }
                        assets.releaseRasterTileReservation(savedReference)
                        newReference = null
                    }
                } finally {
                    assets.releaseRasterTileReservation(newReference)
                    patch?.close()
                }
            }
        }
    }
    val backgroundVisible = page.layers.firstOrNull { it.role == LayerRole.BACKGROUND }?.visible != false
    val background = remember(page.id, page.background, page.backgroundRotationQuarterTurns, page.crop, backgroundVisible) { if (backgroundVisible) runCatching { assets.renderBackground(page.background, 1200, page.backgroundRotationQuarterTurns, page.crop) }.getOrNull() else null }
    DisposableEffect(background) { onDispose { background?.recycle() } }
    val paperColor = (page.background as? PageBackground.Paper)?.colorArgb?.let(::Color) ?: Color.White
    val visibleLayerIds = page.layers.filter { LayerHierarchy.effectiveVisible(page.layers, it) && it.role != LayerRole.FOLDER }.mapTo(mutableSetOf()) { it.id }
    val lockedLayerIds = page.layers.filter { LayerHierarchy.effectiveLocked(page.layers, it) || it.role == LayerRole.FOLDER }.mapTo(mutableSetOf()) { it.id }
    val layerOpacities = page.layers.associate { it.id to it.opacity }
    val layerBlendModes = page.layers.associate { it.id to it.blendMode }
    val layerOrder = page.layers.mapIndexed { index, layer -> layer.id to index }.toMap()
    val layerElementBitmaps = remember(page.elements, page.layers, page.crop, page.backgroundRotationQuarterTurns) {
        page.layers.mapNotNull { layer ->
            val elementIds = page.elements.filter { it.layerId == layer.id }.mapTo(linkedSetOf(), PageElement::id)
            if (elementIds.isEmpty() || !LayerHierarchy.effectiveVisible(page.layers, layer)) return@mapNotNull null
            val capturedIds = layer.alphaLockBaseContentIds.toSet()
            val splitAtAlphaLock = layer.alphaLock && capturedIds.isNotEmpty()
            val baseElementIds = if (splitAtAlphaLock) elementIds.intersect(capturedIds) else elementIds
            val addedElementIds = if (splitAtAlphaLock) elementIds - capturedIds else emptySet()
            val base = baseElementIds.takeIf(Set<String>::isNotEmpty)?.let { assets.renderElementLayerBitmap(page, layer.id, 1200, it) }
            val additions = addedElementIds.takeIf(Set<String>::isNotEmpty)?.let { assets.renderElementLayerBitmap(page, layer.id, 1200, it) }
            layer.id to LayerElementBitmaps(base, additions)
        }.toMap()
    }
    DisposableEffect(layerElementBitmaps) { onDispose { layerElementBitmaps.values.forEach(LayerElementBitmaps::recycle) } }
    // This page-scoped runtime, rather than a disk-derived Compose value, owns editable pixels.
    // Brush imports, preset switches and asynchronous checkpoint publication are UI changes and
    // must never reconstruct or replace the live painting surface.
    val rasterLayerSurfaces = rasterSurfaceRuntime.reconcile(page)
    val rulerGuide = RulerGuideState(settings.rulerCenterX, settings.rulerCenterY, settings.rulerAngleDegrees, settings.rulerSnapDegrees).normalized()
    val canvasGuide = CanvasGuideState(settings.gridSnapEnabled, settings.gridSpacing, settings.alignmentGuidesEnabled, settings.verticalGuideX, settings.horizontalGuideY).normalized()
    val perspectiveGuide = PerspectiveGuideState(
        mode = settings.perspectiveGuideMode,
        assisted = settings.perspectiveGuideAssisted,
        editing = settings.perspectiveGuideEditing,
        points = listOf(
            PerspectiveGuidePoint(settings.perspectivePoint1X, settings.perspectivePoint1Y),
            PerspectiveGuidePoint(settings.perspectivePoint2X, settings.perspectivePoint2Y),
            PerspectiveGuidePoint(settings.perspectivePoint3X, settings.perspectivePoint3Y),
        ),
    ).normalized()
    val specialRuler = SpecialRulerState(
        mode = settings.specialRulerMode,
        assisted = settings.specialRulerAssisted,
        editing = settings.specialRulerEditing,
        centerX = settings.specialRulerCenterX,
        centerY = settings.specialRulerCenterY,
        angleDegrees = settings.specialRulerAngleDegrees,
        spacing = settings.specialRulerSpacing,
    ).normalized()
    val gradientDefinition = GradientDefinition(
        kind = settings.gradientKind,
        edgeMode = settings.gradientEdgeMode,
        reverse = settings.gradientReverse,
        dither = settings.gradientDither,
        ellipseRatio = settings.gradientEllipseRatio,
        stops = GradientColors.normalize(settings.gradientStops).mapIndexed { index, stop ->
            if (index == 0) stop.copy(colorArgb = brushColor.toUInt().toLong()) else stop
        },
    ).normalized()
    BoxWithConstraints(
        modifier
            .graphicsLayer(
                scaleX = viewportState.scale,
                scaleY = viewportState.scale,
                translationX = viewportState.panX,
                translationY = viewportState.panY,
                rotationZ = viewportState.rotationDegrees,
            )
            .shadow(3.dp, RoundedCornerShape(4.dp))
            .background(paperColor),
    ) {
        val density = LocalDensity.current
        val canvasWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val canvasHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val paperMaxWidth = maxWidth
        val paperMaxHeight = maxHeight
        AndroidView(
            factory = { context -> CanvasHost(context).also { host ->
                val view = host.canvasView
                view.bindDocumentSelectionState(documentSelectionState)
                view.tool = tool
                view.forceNavigation = forceNavigation
                view.rotateNavigation = rotateNavigation
                view.externalViewportGestures = false
                view.viewportScale = viewportState.scale
                view.viewportPanX = viewportState.panX
                view.viewportPanY = viewportState.panY
                view.viewportRotationDegrees = viewportState.rotationDegrees
                view.onShapeCommitted = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onGradientCommitted = commitGradient
                installFillCommit(view)
                installRasterCommit(view)
                view.onViewportScale = onZoom
                view.onViewportPinch = onPinch
                view.onViewportPan = onPan
                view.onViewportRotate = onRotate
                view.pdfGotoLinks = renderedPage.pdfGotoLinks
                view.onPdfGotoLink = onPdfGotoLink
                view.onUndoGesture = onUndoGesture
                view.onRedoGesture = onRedoGesture
                view.onCanvasContact = onCanvasContact
                view.onColorDropCommitted = onColorDropCommitted
                view.onSelectionActiveChanged = onSelectionActiveChanged
                view.singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
                view.fillSensitivity = settings.fillSensitivity
                view.fillSubTool = settings.fillSubTool
                view.fillCloseGapPixels = settings.fillCloseGapPixels
                view.fillAreaScalingPixels = settings.fillAreaScalingPixels
                view.symmetryMode = settings.symmetryMode
                view.twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
                view.threeFingerTapRedo = settings.threeFingerTapRedo
                view.twoFingerRotationEnabled = settings.twoFingerRotation
                view.onInputDiagnostic = onInputDiagnostic
                view.showStrokeTraceOverlay = settings.showStrokeTraceOverlay
                view.onColorSampled = onColorSampled
                view.onBrushSizeAdjusted = onBrushSizeAdjusted
                view.onKeyInputDiagnostic = onKeyInputDiagnostic
                view.onMotionInputDiagnostic = onMotionInputDiagnostic
                view.onShortcutAction = onShortcutAction
                view.rulerEnabled = settings.rulerEnabled
                view.rulerGuide = rulerGuide
                view.onRulerGuideChanged = onRulerGuideChange
                view.canvasGuideState = canvasGuide
                view.onCanvasGuideChanged = onCanvasGuideChange
                view.perspectiveGuideState = perspectiveGuide
                view.onPerspectiveGuideChanged = onPerspectiveGuideChange
                view.specialRulerState = specialRuler
                view.onSpecialRulerChanged = onSpecialRulerChange
                view.gradientDefinition = gradientDefinition
                view.gradientAngleSnapEnabled = settings.gradientAngleSnapEnabled
                view.gradientAngleSnapDegrees = settings.gradientAngleSnapDegrees
                view.editableGradient = (renderedPage.elements.firstOrNull { it.id == selectedElementId } as? PageElement.Shape)?.takeIf { it.gradientDefinition() != null && tool == EditorTool.SELECT }
                view.onGradientHandleChanged = { updated -> onElement(documentId, page.id, PageCropTransform.unproject(updated, page.crop)) }
                view.shortcutBindings = settings.shortcutBindings
                view.shortcutAxisBindings = settings.shortcutAxisBindings
                view.shortcutButtonBindings = settings.shortcutButtonBindings
                view.post { view.requestFocus() }
                onView(view)
            } },
            update = { host ->
                val view = host.canvasView
                view.bindDocumentSelectionState(documentSelectionState)
                view.tool = tool
                view.forceNavigation = forceNavigation
                view.rotateNavigation = rotateNavigation
                view.externalViewportGestures = false
                view.viewportScale = viewportState.scale
                view.viewportPanX = viewportState.panX
                view.viewportPanY = viewportState.panY
                view.viewportRotationDegrees = viewportState.rotationDegrees
                view.setProtectedLayerIds(lockedLayerIds)
                view.setPageBackground(background)
                view.setLayerElementBitmaps(layerElementBitmaps)
                view.setRasterLayerSurfaces(rasterLayerSurfaces)
                view.activeLayerId = activeLayerId
                view.layerOpacities = layerOpacities
                view.layerBlendModes = layerBlendModes
                view.alphaLockedLayers = page.layers.filter { it.alphaLock }.mapTo(mutableSetOf()) { it.id }
                view.alphaLockBaseContentIds = page.layers.filter { it.alphaLock }.associate { it.id to it.alphaLockBaseContentIds.toSet() }
                view.clippedLayers = page.layers.filter { it.clippedToBelow }.mapTo(mutableSetOf()) { it.id }
                view.referenceLayerIds = page.layers.filter { it.reference }.mapTo(mutableSetOf()) { it.id }
                view.draftLayerIds = page.layers.filter { it.draft }.mapTo(mutableSetOf()) { it.id }
                view.layerMasks = page.layers.mapNotNull { layer -> layer.mask?.let { layer.id to it } }.toMap()
                view.layerMaskSurfaces = layerMaskSurfaces
                view.layerOrder = page.layers.filter { it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) && LayerHierarchy.effectiveVisible(page.layers, it) }.map(InkLayer::id)
                view.pagePaperColor = if (renderedPage.background is PageBackground.Paper) (if (backgroundVisible) renderedPage.background.colorArgb.toInt() else AndroidColor.WHITE) else null
                view.pagePaperDensity = (renderedPage.background as? PageBackground.Paper)?.density ?: PaperDensity.DENSE
                view.pagePaperPattern = (renderedPage.background as? PageBackground.Paper)?.pattern?.takeIf { backgroundVisible }
                view.shapeKind = shapeKind
                view.pageModelWidth = renderedPage.width
                view.pageModelHeight = renderedPage.height
                view.onShapeCommitted = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onGradientCommitted = commitGradient
                installFillCommit(view)
                installRasterCommit(view)
                view.applyBrushConfiguration(tool, settings, activeBrush, brushSize, brushOpacity, brushColor, eraserPreset, secondaryBrushColor)
                view.onViewportScale = onZoom
                view.onViewportPinch = onPinch
                view.onViewportPan = onPan
                view.onViewportRotate = onRotate
                view.pdfGotoLinks = renderedPage.pdfGotoLinks
                view.onPdfGotoLink = onPdfGotoLink
                view.onUndoGesture = onUndoGesture
                view.onRedoGesture = onRedoGesture
                view.onCanvasContact = onCanvasContact
                view.onColorDropCommitted = onColorDropCommitted
                view.onSelectionActiveChanged = onSelectionActiveChanged
                view.singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
                view.fillSensitivity = settings.fillSensitivity
                view.fillSubTool = settings.fillSubTool
                view.fillCloseGapPixels = settings.fillCloseGapPixels
                view.fillAreaScalingPixels = settings.fillAreaScalingPixels
                view.symmetryMode = settings.symmetryMode
                view.twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
                view.threeFingerTapRedo = settings.threeFingerTapRedo
                view.twoFingerRotationEnabled = settings.twoFingerRotation
                view.onInputDiagnostic = onInputDiagnostic
                view.showStrokeTraceOverlay = settings.showStrokeTraceOverlay
                view.onColorSampled = onColorSampled
                view.onBrushSizeAdjusted = onBrushSizeAdjusted
                view.onKeyInputDiagnostic = onKeyInputDiagnostic
                view.onMotionInputDiagnostic = onMotionInputDiagnostic
                view.onShortcutAction = onShortcutAction
                view.rulerEnabled = settings.rulerEnabled
                view.rulerGuide = rulerGuide
                view.onRulerGuideChanged = onRulerGuideChange
                view.canvasGuideState = canvasGuide
                view.onCanvasGuideChanged = onCanvasGuideChange
                view.perspectiveGuideState = perspectiveGuide
                view.onPerspectiveGuideChanged = onPerspectiveGuideChange
                view.specialRulerState = specialRuler
                view.onSpecialRulerChanged = onSpecialRulerChange
                view.gradientDefinition = gradientDefinition
                view.gradientAngleSnapEnabled = settings.gradientAngleSnapEnabled
                view.gradientAngleSnapDegrees = settings.gradientAngleSnapDegrees
                view.editableGradient = (renderedPage.elements.firstOrNull { it.id == selectedElementId } as? PageElement.Shape)?.takeIf { it.gradientDefinition() != null && tool == EditorTool.SELECT }
                view.onGradientHandleChanged = { updated -> onElement(documentId, page.id, PageCropTransform.unproject(updated, page.crop)) }
                view.shortcutBindings = settings.shortcutBindings
                view.shortcutAxisBindings = settings.shortcutAxisBindings
                view.shortcutButtonBindings = settings.shortcutButtonBindings
            },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Ink canvas" },
        )
        Box(Modifier.fillMaxSize()) {
            searchBounds?.let { PageCropTransform.project(it, page.crop) }?.let { bounds ->
                Canvas(Modifier.fillMaxSize()) {
                    val topLeft = Offset(bounds.left * size.width, bounds.top * size.height)
                    val highlightSize = androidx.compose.ui.geometry.Size((bounds.right - bounds.left) * size.width, (bounds.bottom - bounds.top) * size.height)
                    drawRect(Color(0x33111111), topLeft = topLeft, size = highlightSize)
                    drawRect(Color(0xFF111111), topLeft = topLeft, size = highlightSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

internal fun InkCanvasView.applyBrushPresetV5Configuration(
    preset: BrushPresetV5,
    settings: AppSettings,
    brushSize: Float = preset.tip.size,
    brushOpacity: Float = preset.deposition.opacity,
    brushColor: Int = AndroidColor.rgb(17, 17, 17),
    prepareAssets: Boolean = true,
) {
    val compiled = CompiledBrushProgramV5.compile(preset)
    feedbackLanguage = settings.language
    globalStrokeStabilization = settings.globalStrokeStabilization
    switchInputConfiguration(EditorTool.PEN, preset.identity.id)
    setCompiledBrushProgramV5(compiled, prepareAssets = prepareAssets)
    fingerDrawingEnabled = settings.fingerDrawing
    drawAndHoldEnabled = settings.drawAndHoldEnabled
    drawAndHoldDelayMillis = settings.drawAndHoldDelayMillis
    singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
    twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
    threeFingerTapRedo = settings.threeFingerTapRedo
    pressureSensitivity = settings.pressureSensitivity
    pressureMinimumInput = settings.pressureMinimumInput
    pressureMaximumInput = settings.pressureMaximumInput
    pressureResponseCurve = settings.pressureResponseCurve
    primaryButtonAction = settings.stylusPrimaryButton
    secondaryButtonAction = settings.stylusSecondaryButton
    primaryButtonTrigger = settings.stylusPrimaryTrigger
    secondaryButtonTrigger = settings.stylusSecondaryTrigger
    baseWidth = brushSize
    inkOpacity = brushOpacity
    inkColor = brushColor
    brushId = preset.identity.renderBrushId
    brushRenderSeed = preset.renderSeed
    notifyHoverCursorStyleChanged()
}

internal fun InkCanvasView.applyBrushConfiguration(
    selectedTool: EditorTool,
    settings: AppSettings,
    selectedBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    eraserPreset: EraserPreset,
    secondaryBrushColor: Int = AndroidColor.WHITE,
) {
    // A delayed Compose frame or hardware-button transition must never carry a normal paint
    // descriptor into eraser mode. Imported erasers remain full brush presets; other brushes are
    // discarded at this boundary and the selected system eraser preset is used instead.
    val activeBrush = if (selectedTool == EditorTool.ERASER) selectedBrush?.takeIf(BrushRoleClassifier::isEraser) else selectedBrush
    val paintTool = selectedTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER)
    feedbackLanguage = settings.language
    val defaultPreset = if (paintTool && activeBrush == null) BrushPresetV5(
            identity = PresetIdentity(
                id = when (selectedTool) {
                    EditorTool.ERASER -> "system-default-eraser"
                    EditorTool.HIGHLIGHTER -> "system-default-highlighter"
                    else -> "system-default-pen"
                },
                name = when (selectedTool) {
                    EditorTool.ERASER -> "Default Eraser"
                    EditorTool.HIGHLIGHTER -> "Default Highlighter"
                    else -> "Default Pen"
                },
                family = BrushFamily.INK,
                renderBrushId = when (selectedTool) {
                    EditorTool.ERASER -> "system-default-eraser"
                    EditorTool.HIGHLIGHTER -> "system-highlighter"
                    else -> "system-pen"
                },
            ),
            renderSeed = when (selectedTool) {
                EditorTool.ERASER -> 0x45A5E
                EditorTool.HIGHLIGHTER -> 0x41A11
                else -> 0x1A9E
            },
            output = if (selectedTool == EditorTool.ERASER) BrushOutput.ERASE else BrushOutput.PAINT,
            tip = TipSettings(
                size = brushSize,
                hardness = if (selectedTool == EditorTool.ERASER) eraserPreset.hardness else 1f,
            ),
            deposition = DepositionSettings(
                opacity = if (selectedTool == EditorTool.ERASER) eraserPreset.opacity else brushOpacity,
                flow = 1f,
            ),
            dynamics = DynamicsSettings(
                pressure = PressureMappings(
                    widthEnabled = if (selectedTool == EditorTool.ERASER) eraserPreset.pressureSize else selectedTool == EditorTool.PEN,
                    widthMinimum = .35f,
                ),
                tilt = TiltMappings(),
            ),
        ) else null
    val compiledProgram = when {
        !paintTool || activeBrush?.toolBinding?.isSupported == false -> null
        activeBrush != null -> compiledBrushProgramV5Cache.resolveEncoded(activeBrush.v5PresetJson)
        else -> compiledBrushProgramV5Cache.resolvePreset(requireNotNull(defaultPreset))
    }
    setCompiledBrushProgramV5(
        compiledProgram,
        required = paintTool,
        sourceBrush = activeBrush,
    )
    switchInputConfiguration(selectedTool, activeBrush?.id)
    fingerDrawingEnabled = settings.fingerDrawing
    drawAndHoldEnabled = settings.drawAndHoldEnabled
    drawAndHoldDelayMillis = settings.drawAndHoldDelayMillis
    singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
    twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
    threeFingerTapRedo = settings.threeFingerTapRedo
    globalStrokeStabilization = settings.globalStrokeStabilization
    pressureSensitivity = settings.pressureSensitivity
    pressureMinimumInput = settings.pressureMinimumInput
    pressureMaximumInput = settings.pressureMaximumInput
    pressureResponseCurve = settings.pressureResponseCurve
    primaryButtonAction = settings.stylusPrimaryButton
    secondaryButtonAction = settings.stylusSecondaryButton
    primaryButtonTrigger = settings.stylusPrimaryTrigger
    secondaryButtonTrigger = settings.stylusSecondaryTrigger
    selectionShape = settings.selectionShape
    selectionBrushSize = settings.selectionBrushSize
    selectionThreshold = settings.selectionThreshold
    selectionInterpolation = settings.selectionInterpolation
    liquifyMode = activeBrush?.toolBinding?.liquifyMode ?: LiquifyMode.PUSH
    liquifyStrength = activeBrush?.flow ?: .55f
    liquifyHardness = activeBrush?.hardness ?: .5f
    selectionMode = settings.selectionMode
    selectionFilter = settings.selectionFilter
    referenceTarget = settings.referenceTarget
    fillSensitivity = settings.fillSensitivity
    fillSubTool = settings.fillSubTool
    fillCloseGapPixels = settings.fillCloseGapPixels
    fillAreaScalingPixels = settings.fillAreaScalingPixels
    correctLineMode = settings.correctLineMode
    correctLineAmount = settings.correctLineAmount
    baseWidth = brushSize
    brushSizeSyncViewScale = activeBrush?.sizeSyncViewScale ?: false
    val importedEraser = activeBrush?.takeIf { selectedTool == EditorTool.ERASER && BrushRoleClassifier.isEraser(it) }
    eraserWidth = if (importedEraser != null) brushSize else settings.eraserSize
    eraserHardness = importedEraser?.hardness ?: eraserPreset.hardness
    eraserOpacity = importedEraser?.let { brushOpacity * it.flow }?.coerceIn(.01f, 1f) ?: eraserPreset.opacity
    eraserPressureSize = importedEraser?.sizePressureEnabled ?: eraserPreset.pressureSize
    eraserTipAssetPath = importedEraser?.tipPreviewPath
    inkOpacity = brushOpacity
    inkColor = brushColor
    secondaryInkColor = secondaryBrushColor
    brushId = activeBrush?.renderBrushId ?: activeBrush?.id
        ?: if (selectedTool == EditorTool.HIGHLIGHTER) "system-highlighter" else "system-pen"
    brushRenderSeed = activeBrush?.renderSeed ?: brushId.hashCode()
    brushSpacing = activeBrush?.spacing ?: .12f
    brushSpacingMode = activeBrush?.spacingMode ?: CspBrushGap.FIXED
    brushAntiAliasMode = activeBrush?.antiAliasMode ?: CspAntiAlias.MEDIUM
    brushColorMix = activeBrush?.colorMix ?: 0f
    smudgeHardness = activeBrush?.hardness ?: .5f
    rasterToolStrokeStabilization = if (selectedTool in setOf(EditorTool.SMUDGE, EditorTool.LIQUIFY)) {
        compiledBrushProgramV5Cache.resolveEncoded(activeBrush?.v5PresetJson)?.strokeShaping?.smoothing ?: 0f
    } else 0f
    notifyHoverCursorStyleChanged()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ObjectElementView(
    element: PageElement,
    page: InkPage,
    layerOpacity: Float,
    selected: Boolean,
    editable: Boolean,
    canvasWidth: androidx.compose.ui.unit.Dp,
    canvasHeight: androidx.compose.ui.unit.Dp,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onSelect: () -> Unit,
    onChange: (PageElement) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    internalLinkLabel: String?,
    onOpenInternalLink: (String, String) -> Unit,
    settings: AppSettings,
    activeBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    eraserPreset: EraserPreset,
    showContent: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Content is already painted by InkCanvasView. A disabled clickable still participates
    // in Compose hit testing and used to cover the entire page after a full-page fill.
    // Only actual object editing needs this transparent interaction plane.
    if (!showContent && !editable && !selected) return
    val context = LocalContext.current
    val elementAssets = remember(context) { DocumentAssetService(context) }
    val language = LocalUiLanguage.current
    var dragX by remember(element.id) { mutableFloatStateOf(0f) }
    var dragY by remember(element.id) { mutableFloatStateOf(0f) }
    var editing by remember(element.id) { mutableStateOf(false) }
    val objectWidth = (canvasWidth * (element.width / page.width)).coerceIn(54.dp, canvasWidth)
    val objectHeight = (canvasHeight * (element.height / page.height)).coerceIn(42.dp, canvasHeight)
    val gradientForHandles = (element as? PageElement.Shape)?.takeIf { selected && editable && it.gradientDefinition() != null }
    val gestureModifier = if (editable) Modifier.pointerInput(element.id, canvasWidthPx, canvasHeightPx, gradientForHandles != null) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val passToCanvas = gradientForHandles != null && run {
                val handles = listOf(
                    (gradientForHandles.gradientStartX * canvasWidthPx) to (gradientForHandles.gradientStartY * canvasHeightPx),
                    (gradientForHandles.gradientEndX * canvasWidthPx) to (gradientForHandles.gradientEndY * canvasHeightPx),
                )
                val radius = viewConfiguration.touchSlop * 3f
                handles.any { (hx, hy) ->
                    val dx = down.position.x - hx; val dy = down.position.y - hy
                    (dx * dx + dy * dy) <= radius * radius
                }
            }
            if (passToCanvas) {
                return@awaitEachGesture
            }
            onSelect()
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.size > 1) {
                    change.consume()
                    dragX = 0f; dragY = 0f
                    break
                }
                dx += change.positionChange().x
                dy += change.positionChange().y
                change.consume()
                dragX = dx; dragY = dy
                if (!change.pressed) break
            }
            if (dx != 0f || dy != 0f) {
                onChange(moveElement(element, dx / canvasWidthPx, dy / canvasHeightPx))
            }
            dragX = 0f; dragY = 0f
        }
    } else Modifier
    Box(
        modifier
            .offset(x = canvasWidth * element.x, y = canvasHeight * element.y)
            .size(objectWidth, objectHeight)
            .graphicsLayer(rotationZ = element.rotationDegrees, alpha = (element.opacity * layerOpacity).coerceIn(0f, 1f), translationX = dragX, translationY = dragY)
            .then(if (selected) Modifier.border(2.dp, Sage, RoundedCornerShape(5.dp)) else Modifier)
            .then(gestureModifier)
            .combinedClickable(
                enabled = editable,
                onClick = onSelect,
                onDoubleClick = {
                    onSelect()
                    when (element) {
                        is PageElement.Sticky -> editing = true
                        is PageElement.Link -> if (InternalLinkTarget.isInternal(element)) {
                            val documentId = element.targetDocumentId
                            val pageId = element.targetPageId
                            if (documentId != null && pageId != null) onOpenInternalLink(documentId, pageId)
                        } else openLink(context, element.url, language)
                        else -> Unit
                    }
                },
            ),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer(alpha = if (showContent) 1f else 0f)) {
            when (element) {
            is PageElement.Text -> {
                val scaledTextSize = (element.textSize * canvasWidth.value / page.width).coerceIn(10f, 40f)
                Text(
                    RichTextFormatter.displayText(element.text, element.listStyle),
                    color = Color(element.colorArgb),
                    fontSize = scaledTextSize.sp,
                    lineHeight = (scaledTextSize * element.lineSpacing.coerceIn(.8f, 2f)).sp,
                    fontWeight = if (element.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (element.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (element.underline) TextDecoration.Underline else TextDecoration.None,
                    textAlign = when (element.alignment) { InkTextAlignment.START -> TextAlign.Start; InkTextAlignment.CENTER -> TextAlign.Center; InkTextAlignment.END -> TextAlign.End },
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                )
            }
            is PageElement.Sticky -> Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(element.colorArgb))) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        if (element.collapsed) element.text.lineSequence().firstOrNull().orEmpty() else element.text,
                        Modifier.padding(10.dp),
                        maxLines = if (element.collapsed) 1 else 12,
                    )
                }
            }
            is PageElement.Shape -> Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val color = Color(element.colorArgb)
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(element.strokeWidth.coerceIn(1f, 40f))
                val triangle = androidx.compose.ui.graphics.Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                val diamond = androidx.compose.ui.graphics.Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width / 2f, size.height); lineTo(0f, size.height / 2f); close() }
                val freeform = androidx.compose.ui.graphics.Path().apply {
                    element.path.firstOrNull()?.let { first ->
                        moveTo(first.x * size.width, first.y * size.height)
                        for (index in 1 until element.path.size) { val vertex = element.path[index]; lineTo(vertex.x * size.width, vertex.y * size.height) }
                        close()
                    }
                }
                element.fillArgb?.let { fill ->
                    val gradientDefinition = element.gradientDefinition()
                    if (element.kind == ShapeKind.RECTANGLE && gradientDefinition != null) {
                        drawIntoCanvas { composeCanvas ->
                            val nativeCanvas = composeCanvas.nativeCanvas
                            val nativePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                isDither = gradientDefinition.dither
                                shader = GradientShaderFactory.create(
                                    gradientDefinition,
                                    element.gradientStartX * size.width,
                                    element.gradientStartY * size.height,
                                    element.gradientEndX * size.width,
                                    element.gradientEndY * size.height,
                                )
                            }
                            val maskPath = element.maskAssetPath
                            if (maskPath == null) {
                                nativeCanvas.drawRect(0f, 0f, size.width, size.height, nativePaint)
                            } else {
                                val checkpoint = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                                val maskDrawn = elementAssets.drawFillMask(
                                    nativeCanvas,
                                    maskPath,
                                    android.graphics.RectF(0f, 0f, size.width, size.height),
                                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG),
                                )
                                if (maskDrawn) {
                                    nativePaint.blendMode = android.graphics.BlendMode.SRC_IN
                                    nativeCanvas.drawRect(0f, 0f, size.width, size.height, nativePaint)
                                    nativePaint.blendMode = null
                                }
                                nativeCanvas.restoreToCount(checkpoint)
                            }
                        }
                    } else when (element.kind) { ShapeKind.ELLIPSE -> drawOval(Color(fill)); ShapeKind.RECTANGLE -> drawRect(Color(fill)); ShapeKind.TRIANGLE -> drawPath(triangle, Color(fill)); ShapeKind.DIAMOND -> drawPath(diamond, Color(fill)); ShapeKind.FREEFORM -> drawPath(freeform, Color(fill)); else -> Unit }
                }
                if (element.strokeWidth > 0f) when (element.kind) {
                    ShapeKind.ELLIPSE -> drawOval(color, style = stroke)
                    ShapeKind.RECTANGLE -> drawRect(color, style = stroke)
                    ShapeKind.TRIANGLE -> drawPath(triangle, color, style = stroke)
                    ShapeKind.DIAMOND -> drawPath(diamond, color, style = stroke)
                    ShapeKind.LINE -> drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = element.strokeWidth)
                    ShapeKind.ARROW -> { val y = size.height / 2f; drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = element.strokeWidth); drawLine(color, Offset(size.width, y), Offset(size.width - 24f, y - 16f), strokeWidth = element.strokeWidth); drawLine(color, Offset(size.width, y), Offset(size.width - 24f, y + 16f), strokeWidth = element.strokeWidth) }
                    ShapeKind.FREEFORM -> drawPath(freeform, color, style = stroke)
                }
            }
            is PageElement.Image -> {
                val normalizedCrop = element.crop.normalized()
                val bitmap = remember(element.assetPath, normalizedCrop) { ImageAssetDecoder.decodeCropped(element.assetPath, normalizedCrop) }
                DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
                val maskModifier = when (element.mask) {
                    ImageMask.RECTANGLE -> Modifier
                    ImageMask.ROUNDED -> Modifier.clip(RoundedCornerShape((element.cornerRadius * canvasWidth.value / page.width).coerceIn(0f, 120f).dp))
                    ImageMask.OVAL -> Modifier.clip(CircleShape)
                }
                if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), tr("Page image", "页面图片"), Modifier.fillMaxSize().then(maskModifier), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Workspace), contentAlignment = Alignment.Center) { Text(tr("Image unavailable", "图片不可用"), color = MutedInk, fontSize = 11.sp) }
            }
            is PageElement.Fill -> {
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas { composeCanvas ->
                        elementAssets.drawFillMask(
                            composeCanvas.nativeCanvas,
                            element.maskAssetPath,
                            android.graphics.RectF(0f, 0f, size.width, size.height),
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                                colorFilter = android.graphics.PorterDuffColorFilter(
                                    element.colorArgb.toInt(),
                                    android.graphics.PorterDuff.Mode.SRC_IN,
                                )
                            },
                        )
                    }
                }
            }
            is PageElement.Link -> Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7E7E7))) {
                Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White) { Icon(Icons.Outlined.Link, null, Modifier.padding(10.dp), tint = Color(0xFF111111)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(element.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(internalLinkLabel ?: LinkTarget.displayDestination(element.url), color = MutedInk, fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = {
                        val documentId = element.targetDocumentId
                        val pageId = element.targetPageId
                        if (InternalLinkTarget.isInternal(element) && documentId != null && pageId != null) onOpenInternalLink(documentId, pageId)
                        else openLink(context, element.url, language)
                    }) { Icon(if (InternalLinkTarget.isInternal(element)) Icons.Outlined.SubdirectoryArrowRight else Icons.Outlined.OpenInNew, tr("Open link", "打开链接")) }
                }
            }
            }
        }
        if (selected && editable) Surface(Modifier.align(Alignment.TopEnd).offset(y = (-38).dp), shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 4.dp) {
            Row {
                if (element is PageElement.Sticky) {
                    IconButton(onClick = { onChange(element.copy(collapsed = !element.collapsed)) }, modifier = Modifier.size(36.dp)) { Icon(if (element.collapsed) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess, if (element.collapsed) tr("Expand sticky note", "展开便签") else tr("Collapse sticky note", "折叠便签"), Modifier.size(18.dp)) }
                }
                if (element is PageElement.Link) IconButton(onClick = {
                    val documentId = element.targetDocumentId
                    val pageId = element.targetPageId
                    if (InternalLinkTarget.isInternal(element) && documentId != null && pageId != null) onOpenInternalLink(documentId, pageId)
                    else openLink(context, element.url, language)
                }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.OpenInNew, tr("Open link", "打开链接"), Modifier.size(18.dp)) }
                IconButton(onClick = { editing = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Tune, tr("Edit object", "编辑对象"), Modifier.size(18.dp)) }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.ContentCopy, tr("Duplicate object", "复制对象"), Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Delete, tr("Delete object", "删除对象"), Modifier.size(18.dp)) }
            }
        }
    }
    if (editing) ElementEditorDialog(element, onDismiss = { editing = false }, onSave = { onChange(it); editing = false })
}
@Composable
private fun ImageTransformPreview(image: PageElement.Image) {
    val crop = image.crop.normalized()
    val bitmap = remember(image.assetPath, crop) { ImageAssetDecoder.decodeCropped(image.assetPath, crop) }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(Workspace), contentAlignment = Alignment.Center) {
        val ratio = (image.width / image.height.coerceAtLeast(1f)).coerceIn(.01f, 100f)
        val previewWidth = minOf(maxWidth.value * .8f, maxHeight.value * .8f * ratio)
        val shape = when (image.mask) {
            ImageMask.RECTANGLE -> androidx.compose.ui.graphics.RectangleShape
            ImageMask.ROUNDED -> RoundedCornerShape((image.cornerRadius / image.width.coerceAtLeast(1f) * previewWidth).dp)
            ImageMask.OVAL -> CircleShape
        }
        bitmap?.let { pixels ->
            Image(pixels.asImageBitmap(), tr("Image transform preview", "图片变换预览"), contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(previewWidth.dp, (previewWidth / ratio).dp).graphicsLayer(rotationZ = image.rotationDegrees, alpha = image.opacity).clip(shape))
        }
    }
}

@Composable
private fun ElementEditorDialog(element: PageElement, onDismiss: () -> Unit, onSave: (PageElement) -> Unit) {
    var draft by remember(element.id) { mutableStateOf(element) }
    val defaultLinkedPageTitle = tr("Linked page", "链接页面")
    val normalizedDraftLink = (draft as? PageElement.Link)?.takeUnless(InternalLinkTarget::isInternal)?.let { LinkTarget.normalize(it.url) }
    val canSave = (draft as? PageElement.Link)?.let { InternalLinkTarget.isInternal(it) || normalizedDraftLink != null } ?: true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (element is PageElement.Image) tr("Transform image", "变换图片") else tr("Object properties", "对象属性")) },
        text = { LazyColumn(Modifier.widthIn(min = 340.dp, max = 520.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draft is PageElement.Image) {
                val image = draft as PageElement.Image
                item { ImageTransformPreview(image) }
                item { BrushSettingSlider("Width", image.width, 40f..3200f) { draft = image.copy(width = it) } }
                item { BrushSettingSlider("Height", image.height, 40f..4526f) { draft = image.copy(height = it) } }
                item { BrushSettingSlider("Rotation", image.rotationDegrees, -180f..180f) { draft = image.copy(rotationDegrees = it) } }
            }
            when (val current = draft) {
                is PageElement.Text -> {
                    item { OutlinedTextField(current.text, { draft = current.copy(text = it) }, label = { Text(tr("Text", "文本")) }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
                    item { BrushSettingSlider("Text size", current.textSize, 16f..160f) { draft = current.copy(textSize = it) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = current.bold, onClick = { draft = current.copy(bold = !current.bold) }, label = { Text(tr("Bold", "粗体"), fontWeight = FontWeight.Bold) })
                        FilterChip(selected = current.italic, onClick = { draft = current.copy(italic = !current.italic) }, label = { Text(tr("Italic", "斜体"), fontStyle = FontStyle.Italic) })
                        FilterChip(selected = current.underline, onClick = { draft = current.copy(underline = !current.underline) }, label = { Text(tr("Underline", "下划线"), textDecoration = TextDecoration.Underline) })
                    } }
                    item { Text(tr("Alignment", "对齐方式"), color = MutedInk, fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { InkTextAlignment.entries.forEach { alignment -> FilterChip(selected = current.alignment == alignment, onClick = { draft = current.copy(alignment = alignment) }, label = { Text(localizedTextAlignment(alignment)) }) } } }
                    item { Text(tr("List style", "列表样式"), color = MutedInk, fontSize = 11.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextListStyle.entries.forEach { style -> item { FilterChip(selected = current.listStyle == style, onClick = { draft = current.copy(listStyle = style) }, label = { Text(localizedListStyle(style)) }) } } } }
                    item { BrushSettingSlider("Line spacing", current.lineSpacing, .8f..2f) { draft = current.copy(lineSpacing = it) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Color", "颜色"), Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt(), onColor = { draft = current.copy(colorArgb = it.toUInt().toLong()) }) } }
                }
                is PageElement.Sticky -> {
                    item { OutlinedTextField(current.text, { draft = current.copy(text = it) }, label = { Text(tr("Note", "便签")) }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
                    item { SettingSwitch("Collapsed", "Show only the first line.", current.collapsed) { draft = current.copy(collapsed = it) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Color", "颜色"), Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt(), onColor = { draft = current.copy(colorArgb = it.toUInt().toLong()) }) } }
                }
                is PageElement.Shape -> {
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShapeKind.entries.forEach { kind -> item { FilterChip(selected = current.kind == kind, onClick = {
                        val path = if (kind == ShapeKind.FREEFORM && current.path.size < 3) listOf(ShapeVertex(0f, 0f), ShapeVertex(1f, 0f), ShapeVertex(1f, 1f), ShapeVertex(0f, 1f)) else current.path
                        draft = current.copy(kind = kind, path = path)
                    }, label = { Text(localizedShapeKind(kind)) }) } } } }
                    item { BrushSettingSlider("Stroke width", current.strokeWidth, 1f..40f) { draft = current.copy(strokeWidth = it) } }
                    item { SettingSwitch("Fill", "Adds a translucent shape fill.", current.fillArgb != null) { draft = current.copy(fillArgb = if (it) 0x33111111 else null) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Stroke color", "描边颜色"), Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt(), onColor = { draft = current.copy(colorArgb = it.toUInt().toLong()) }) } }
                }
                is PageElement.Image -> {
                    item { Text(tr("Image source is retained unchanged. Crop and mask remain reversible and are included in editable backups.", "图片源文件保持不变。裁剪和蒙版均可撤销，并会包含在可编辑备份中。"), color = MutedInk, fontSize = 12.sp) }
                    item { Text(tr("Mask", "蒙版"), color = MutedInk, fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ImageMask.entries.forEach { mask -> FilterChip(selected = current.mask == mask, onClick = { draft = current.copy(mask = mask) }, label = { Text(localizedImageMask(mask)) }) } } }
                    if (current.mask == ImageMask.ROUNDED) item { BrushSettingSlider("Corner radius", current.cornerRadius, 0f..200f) { draft = current.copy(cornerRadius = it) } }
                    item { Text(tr("Crop edges", "裁剪边缘"), color = MutedInk, fontSize = 11.sp) }
                    item { BrushSettingSlider("Left", current.crop.left, 0f..0.95f) { draft = current.copy(crop = current.crop.copy(left = it).normalized()) } }
                    item { BrushSettingSlider("Top", current.crop.top, 0f..0.95f) { draft = current.copy(crop = current.crop.copy(top = it).normalized()) } }
                    item { BrushSettingSlider("Right", current.crop.right, 0.05f..1f) { draft = current.copy(crop = current.crop.copy(right = it).normalized()) } }
                    item { BrushSettingSlider("Bottom", current.crop.bottom, 0.05f..1f) { draft = current.copy(crop = current.crop.copy(bottom = it).normalized()) } }
                    item { OutlinedButton(onClick = { draft = current.copy(crop = ImageCrop()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(tr("Reset crop", "重置裁剪")) } }
                }
                is PageElement.Link -> {
                    item { OutlinedTextField(current.title, { draft = current.copy(title = it) }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    if (InternalLinkTarget.isInternal(current)) item {
                        Text(tr("This object links to a notebook page. Create a new link to change its destination.", "此对象链接到笔记页面。如需更改目标，请新建链接。"), color = MutedInk, fontSize = 12.sp)
                    } else item { OutlinedTextField(current.url, { draft = current.copy(url = it) }, label = { Text(tr("Web, email, or phone address", "网页、邮箱或电话号码")) }, isError = LinkTarget.normalize(current.url) == null, supportingText = { Text(if (LinkTarget.normalize(current.url) == null) tr("Use a valid http, https, mailto, or tel address.", "请输入有效的 http、https、mailto 或 tel 地址。") else LinkTarget.displayDestination(current.url)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                }
                is PageElement.Fill -> {
                    item { Text(tr("Fill color", "填充颜色") + ": #" + "%08X".format(current.colorArgb), color = MutedInk, fontSize = 11.sp) }
                }
            }
            item { BrushSettingSlider("Opacity", draft.opacity, .05f..1f) { draft = setElementOpacity(draft, it) } }
            if (draft !is PageElement.Image) {
                item { BrushSettingSlider("Rotation", draft.rotationDegrees, -180f..180f) { draft = setElementRotation(draft, it) } }
                item { BrushSettingSlider("Width", draft.width, 40f..1600f) { draft = setElementSize(draft, it, draft.height) } }
                item { BrushSettingSlider("Height", draft.height, 40f..2263f) { draft = setElementSize(draft, draft.width, it) } }
            }
        } },
        confirmButton = { Button(enabled = canSave, onClick = {
            val saved = (draft as? PageElement.Link)?.let { link ->
                if (InternalLinkTarget.isInternal(link)) link.copy(title = link.title.trim().ifBlank { defaultLinkedPageTitle })
                else {
                    val url = normalizedDraftLink ?: return@Button
                    link.copy(title = link.title.trim().ifBlank { LinkTarget.displayDestination(url, 48) }, url = url)
                }
            } ?: draft
            onSave(saved)
        }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

private fun moveElement(element: PageElement, dx: Float, dy: Float): PageElement = when (element) {
    is PageElement.Text -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Sticky -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Shape -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Image -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Fill -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Link -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
}

private fun resizeElement(element: PageElement, factor: Float): PageElement = setElementSize(element, (element.width * factor).coerceIn(40f, 1600f), (element.height * factor).coerceIn(40f, 2263f))
private fun rotateElement(element: PageElement, delta: Float): PageElement = setElementRotation(element, ((element.rotationDegrees + delta + 180f) % 360f) - 180f)
private fun setElementSize(element: PageElement, width: Float, height: Float): PageElement = when (element) { is PageElement.Text -> element.copy(width = width, height = height); is PageElement.Sticky -> element.copy(width = width, height = height); is PageElement.Shape -> element.copy(width = width, height = height); is PageElement.Image -> element.copy(width = width, height = height); is PageElement.Fill -> element.copy(width = width, height = height); is PageElement.Link -> element.copy(width = width, height = height) }
private fun setElementRotation(element: PageElement, value: Float): PageElement = when (element) { is PageElement.Text -> element.copy(rotationDegrees = value); is PageElement.Sticky -> element.copy(rotationDegrees = value); is PageElement.Shape -> element.copy(rotationDegrees = value); is PageElement.Image -> element.copy(rotationDegrees = value); is PageElement.Fill -> element.copy(rotationDegrees = value); is PageElement.Link -> element.copy(rotationDegrees = value) }
private fun setElementOpacity(element: PageElement, value: Float): PageElement = when (element) { is PageElement.Text -> element.copy(opacity = value); is PageElement.Sticky -> element.copy(opacity = value); is PageElement.Shape -> element.copy(opacity = value); is PageElement.Image -> element.copy(opacity = value); is PageElement.Fill -> element.copy(opacity = value); is PageElement.Link -> element.copy(opacity = value) }

private fun openLink(context: android.content.Context, rawUrl: String, language: UiLanguage) {
    val url = LinkTarget.normalize(rawUrl)
    if (url == null) {
        Toast.makeText(context, language.text("This link is not valid or supported.", "此链接无效或不受支持。"), Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
    }.onFailure {
        Toast.makeText(context, language.text("No app can open this link.", "没有应用可以打开此链接。"), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun EraserPreset.displayName(): String = when (this) {
    EraserPreset.NORMAL_FREE -> tr("Free", "自由橡皮")
    EraserPreset.HARD -> tr("Hard", "硬橡皮")
    EraserPreset.SOFT -> tr("Soft", "软橡皮")
    EraserPreset.HARD_EDGE -> tr("Hard edge", "硬边缘")
    EraserPreset.SOFT_EDGE -> tr("Soft edge", "软边缘")
    EraserPreset.STROKE -> tr("Whole stroke", "整笔删除")
}

@Composable
private fun EraserPresetMenu(selected: EraserPreset, onSelect: (EraserPreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(selected.displayName()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EraserPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.displayName()) },
                    leadingIcon = { if (preset == selected) Icon(Icons.Outlined.Check, null) },
                    onClick = { expanded = false; onSelect(preset) },
                )
            }
        }
    }
}

@Composable
private fun EraserPresetPicker(selected: EraserPreset, onSelect: (EraserPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EraserPreset.entries.toList().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { preset ->
                    FilterChip(
                        selected = selected == preset,
                        onClick = { onSelect(preset) },
                        label = { Text(preset.displayName(), fontSize = 10.sp, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GradientToolControls(settings: AppSettings, brushColor: Int, onBrushColor: (Int) -> Unit, onSettingsChange: (AppSettings) -> Unit) {
    val stops = GradientColors.normalize(settings.gradientStops).mapIndexed { index, stop ->
        if (index == 0) stop.copy(colorArgb = brushColor.toUInt().toLong()) else stop
    }
    fun updateStops(changed: List<GradientColorStop>) = onSettingsChange(settings.copy(gradientStops = GradientColors.normalize(changed)))

    Text(tr("Shape", "形状"), color = MutedInk, fontSize = 11.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GradientKind.entries.forEach { kind ->
            FilterChip(
                selected = settings.gradientKind == kind,
                onClick = { onSettingsChange(settings.copy(gradientKind = kind)) },
                label = { Text(when (kind) {
                    GradientKind.LINEAR -> tr("Linear", "线性")
                    GradientKind.RADIAL -> tr("Radial", "圆形")
                    GradientKind.ELLIPSE -> tr("Ellipse", "椭圆")
                    GradientKind.ANGULAR -> tr("Angular", "角度")
                    GradientKind.REFLECTED -> tr("Reflected", "对称")
                    GradientKind.DIAMOND -> tr("Diamond", "菱形")
                }, fontSize = 9.sp) },
            )
        }
    }
    Text(tr("Edge process", "边缘处理"), color = MutedInk, fontSize = 11.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GradientEdgeMode.entries.forEach { edge ->
            FilterChip(
                selected = settings.gradientEdgeMode == edge,
                onClick = { onSettingsChange(settings.copy(gradientEdgeMode = edge)) },
                label = { Text(when (edge) {
                    GradientEdgeMode.CLAMP -> tr("No repeat", "不重复")
                    GradientEdgeMode.REPEAT -> tr("Repeat", "重复")
                    GradientEdgeMode.MIRROR -> tr("Reverse repeat", "反向重复")
                    GradientEdgeMode.TRANSPARENT -> tr("Do not draw", "不绘制")
                }, fontSize = 9.sp) },
            )
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Reverse", "反转"), Modifier.weight(1f), color = MutedInk, fontSize = 11.sp)
        Switch(settings.gradientReverse, { onSettingsChange(settings.copy(gradientReverse = it)) })
        Text(tr("Dither", "抖动"), Modifier.padding(start = 8.dp), color = MutedInk, fontSize = 11.sp)
        Switch(settings.gradientDither, { onSettingsChange(settings.copy(gradientDither = it)) })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Angle snap", "角度吸附"), Modifier.weight(1f), color = MutedInk, fontSize = 11.sp)
        Switch(settings.gradientAngleSnapEnabled, { onSettingsChange(settings.copy(gradientAngleSnapEnabled = it)) })
    }
    if (settings.gradientAngleSnapEnabled) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(5f, 15f, 30f, 45f).forEach { step ->
            FilterChip(selected = settings.gradientAngleSnapDegrees == step, onClick = { onSettingsChange(settings.copy(gradientAngleSnapDegrees = step)) }, label = { Text("${step.toInt()}°", fontSize = 9.sp) })
        }
    }
    if (settings.gradientKind == GradientKind.ELLIPSE) {
        Text("${tr("Ellipse thickness", "椭圆厚度")} · ${(settings.gradientEllipseRatio * 100).toInt()}%", color = MutedInk, fontSize = 11.sp)
        Slider(settings.gradientEllipseRatio, { onSettingsChange(settings.copy(gradientEllipseRatio = it)) }, valueRange = .1f..1f)
    }
    HorizontalDivider()
    Text(tr("Color stops", "颜色节点"), color = MutedInk, fontSize = 11.sp)
    stops.forEachIndexed { index, stop ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${(stop.position * 100).toInt()}%", Modifier.width(34.dp), fontSize = 9.sp, color = MutedInk)
            Slider(
                value = stop.position,
                onValueChange = { position ->
                    if (index in 1 until stops.lastIndex) updateStops(stops.toMutableList().also { it[index] = stop.copy(position = position) })
                },
                enabled = index in 1 until stops.lastIndex,
                modifier = Modifier.weight(1f),
            )
            BrushColorPicker(stop.colorArgb.toInt(), { color ->
                if (index == 0) onBrushColor(color)
                updateStops(stops.toMutableList().also { it[index] = stop.copy(colorArgb = color.toUInt().toLong()) })
            }, square = true)
            IconButton(
                onClick = { updateStops(stops.filterIndexed { candidate, _ -> candidate != index }) },
                enabled = index in 1 until stops.lastIndex,
                modifier = Modifier.size(28.dp),
            ) { Icon(Icons.Outlined.Delete, tr("Delete color stop", "删除颜色节点"), Modifier.size(15.dp)) }
        }
    }
    OutlinedButton(
        onClick = {
            if (stops.size >= 8) return@OutlinedButton
            val gap = stops.zipWithNext().maxByOrNull { (left, right) -> right.position - left.position } ?: return@OutlinedButton
            val position = (gap.first.position + gap.second.position) / 2f
            val color = GradientColors.sample(stops, position, GradientEdgeMode.CLAMP).toUInt().toLong()
            updateStops(stops + GradientColorStop(position, color))
        },
        enabled = stops.size < 8,
        modifier = Modifier.fillMaxWidth(),
    ) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Add color stop", "添加颜色节点")) }
}

@Composable
private fun ProcreateTopButton(
    icon: ImageVector,
    description: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(
            icon,
            description,
            Modifier.size(23.dp),
            tint = if (selected) Color(0xFF1688F8) else Ink,
        )
    }
}

@Composable
private fun ToolDock(tool: EditorTool, onTool: (EditorTool) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onScale: () -> Unit, onRotate: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onClipboardCopy: () -> Unit, onPaste: () -> Unit, onDelete: () -> Unit, onSelectAll: () -> Unit, onInvertSelection: () -> Unit, onDeselect: () -> Unit, onFreeTransform: () -> Unit, selectionShape: SelectionShape, selectionMode: SelectionMode, selectionFilter: SelectionFilter, onSelectionShape: (SelectionShape) -> Unit, onSelectionMode: (SelectionMode) -> Unit, onSelectionFilter: (SelectionFilter) -> Unit, referenceTarget: ReferenceTarget, onReferenceTarget: (ReferenceTarget) -> Unit, correctLineMode: CorrectLineMode, correctLineAmount: Float, onCorrectLineMode: (CorrectLineMode) -> Unit, onCorrectLineAmount: (Float) -> Unit, eraserPreset: EraserPreset, onEraserPreset: (EraserPreset) -> Unit, rulerEnabled: Boolean, rulerSnapDegrees: Float, onToggleRuler: () -> Unit, onRulerSnap: (Float) -> Unit, onResetRuler: () -> Unit, gridSnapEnabled: Boolean, gridSpacing: Float, alignmentGuidesEnabled: Boolean, onToggleGridSnap: () -> Unit, onGridSpacing: (Float) -> Unit, onToggleAlignmentGuides: () -> Unit, onResetAlignmentGuides: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit, vertical: Boolean, onToggleOrientation: () -> Unit, onResetPosition: () -> Unit, onDock: (ToolbarDock) -> Unit, brushSize: Float, brushOpacity: Float, brushColor: Int, brushes: List<ImportedBrush>, selectedBrushId: String?, onSystemPreset: (EditorTool) -> Unit, onBrushPreset: (ImportedBrush) -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit, onColor: (Int) -> Unit, symmetryMode: SymmetryMode, onSymmetryMode: (SymmetryMode) -> Unit, gradientControls: @Composable ColumnScope.() -> Unit, modifier: Modifier = Modifier) {
    var moreToolsOpen by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(false) }
    val dragToolbarDescription = tr("Drag toolbar", "拖动工具栏")
    val dragModifier = (if (vertical) Modifier.width(32.dp).height(10.dp) else Modifier.width(10.dp).height(32.dp)).pointerInput(vertical) {
        detectDragGestures(
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd,
        ) { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
    }
    Surface(
        modifier.shadow(if (vertical) 5.dp else 1.dp, if (vertical) RoundedCornerShape(18.dp) else RoundedCornerShape(0.dp)),
        shape = if (vertical) RoundedCornerShape(18.dp) else RoundedCornerShape(0.dp),
        color = Color.White,
        contentColor = Ink,
        border = CardDefaults.outlinedCardBorder(),
        tonalElevation = 0.dp,
    ) {
        val content: @Composable () -> Unit = {
            ToolDockItems(
                tool = tool,
                onTool = onTool,
                onUndo = onUndo,
                onRedo = onRedo,
                onScale = onScale,
                onRotate = onRotate,
                onCopy = onCopy,
                onCut = onCut,
                onClipboardCopy = onClipboardCopy,
                onPaste = onPaste,
                onDelete = onDelete,
                onSelectAll = onSelectAll,
                onInvertSelection = onInvertSelection,
                onDeselect = onDeselect,
                onFreeTransform = onFreeTransform,
                selectionShape = selectionShape,
                selectionMode = selectionMode,
                selectionFilter = selectionFilter,
                onSelectionShape = onSelectionShape,
                onSelectionMode = onSelectionMode,
                onSelectionFilter = onSelectionFilter,
                referenceTarget = referenceTarget,
                onReferenceTarget = onReferenceTarget,
                correctLineMode = correctLineMode,
                correctLineAmount = correctLineAmount,
                onCorrectLineMode = onCorrectLineMode,
                onCorrectLineAmount = onCorrectLineAmount,
                eraserPreset = eraserPreset,
                onEraserPreset = onEraserPreset,
                rulerEnabled = rulerEnabled,
                rulerSnapDegrees = rulerSnapDegrees,
                onToggleRuler = onToggleRuler,
                onRulerSnap = onRulerSnap,
                onResetRuler = onResetRuler,
                gridSnapEnabled = gridSnapEnabled,
                gridSpacing = gridSpacing,
                alignmentGuidesEnabled = alignmentGuidesEnabled,
                onToggleGridSnap = onToggleGridSnap,
                onGridSpacing = onGridSpacing,
                onToggleAlignmentGuides = onToggleAlignmentGuides,
                onResetAlignmentGuides = onResetAlignmentGuides,
                dragModifier = dragModifier,
                vertical = vertical,
                moreToolsOpen = moreToolsOpen,
                onMoreToolsOpen = { moreToolsOpen = it },
                controlsOpen = controlsOpen,
                onControlsOpen = { controlsOpen = it },
                onToggleOrientation = onToggleOrientation,
                onResetPosition = onResetPosition,
                onDock = onDock,
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                brushColor = brushColor,
                brushes = brushes,
                selectedBrushId = selectedBrushId,
                onSystemPreset = onSystemPreset,
                onBrushPreset = onBrushPreset,
                onSize = onSize,
                onOpacity = onOpacity,
                onColor = onColor,
                symmetryMode = symmetryMode,
                onSymmetryMode = onSymmetryMode,
                gradientControls = gradientControls,
            )
        }
        if (vertical) ProcreateSidebar(
            brushSize = brushSize,
            brushOpacity = brushOpacity,
            onSize = onSize,
            onOpacity = onOpacity,
            onModify = { onTool(EditorTool.EYEDROPPER) },
            onUndo = onUndo,
            onRedo = onRedo,
            showGradientControls = tool == EditorTool.GRADIENT,
            gradientControls = gradientControls,
            dragModifier = dragModifier.semantics { contentDescription = dragToolbarDescription },
        ) else Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun ProcreateSidebar(
    brushSize: Float,
    brushOpacity: Float,
    onSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onModify: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    showGradientControls: Boolean,
    gradientControls: @Composable ColumnScope.() -> Unit,
    dragModifier: Modifier,
) {
    var gradientOpen by remember { mutableStateOf(false) }
    val sizeProgress = kotlin.math.sqrt(((brushSize.coerceIn(.5f, 400f) - .5f) / 399.5f).coerceIn(0f, 1f))
    Column(
        Modifier.width(48.dp).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(dragModifier) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(Color(0xFF777777), Offset(size.width * .22f, size.height * .35f), androidx.compose.ui.geometry.Size(size.width * .56f, 2f), androidx.compose.ui.geometry.CornerRadius(1f))
            }
        }
        BrushMasterSlider(
            progress = sizeProgress,
            description = tr("Brush size", "笔刷大小"),
            display = { progress ->
                val value = .5f + progress * progress * 399.5f
                (if (value < 10f) "%.1f".format(value) else value.toInt().toString()) + " px"
            },
            onProgress = { onSize(.5f + it * it * 399.5f) },
        )
        IconButton(onClick = onModify, modifier = Modifier.size(32.dp)) {
            Surface(Modifier.size(17.dp), shape = RoundedCornerShape(4.dp), color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.5.dp, Ink)) {}
        }
        if (showGradientControls) Box {
            IconButton(onClick = { gradientOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Tune, tr("Gradient controls", "渐变参数"), Modifier.size(18.dp))
            }
            DropdownMenu(expanded = gradientOpen, onDismissRequest = { gradientOpen = false }, modifier = Modifier.widthIn(min = 300.dp, max = 360.dp)) {
                Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Gradient controls", "渐变参数"), fontWeight = FontWeight.SemiBold)
                    gradientControls()
                }
            }
        }
        BrushMasterSlider(
            progress = ((brushOpacity.coerceIn(.05f, 1f) - .05f) / .95f),
            description = tr("Brush opacity", "笔刷不透明度"),
            display = { "${((.05f + it * .95f) * 100).toInt()}%" },
            onProgress = { onOpacity(.05f + it * .95f) },
        )
        IconButton(onClick = onUndo, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Undo, tr("Undo", "撤销"), Modifier.size(20.dp), tint = Ink) }
        IconButton(onClick = onRedo, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Redo, tr("Redo", "重做"), Modifier.size(20.dp), tint = Ink) }
    }
}

@Composable
private fun ToolDockItems(tool: EditorTool, onTool: (EditorTool) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onScale: () -> Unit, onRotate: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onClipboardCopy: () -> Unit, onPaste: () -> Unit, onDelete: () -> Unit, onSelectAll: () -> Unit, onInvertSelection: () -> Unit, onDeselect: () -> Unit, onFreeTransform: () -> Unit, selectionShape: SelectionShape, selectionMode: SelectionMode, selectionFilter: SelectionFilter, onSelectionShape: (SelectionShape) -> Unit, onSelectionMode: (SelectionMode) -> Unit, onSelectionFilter: (SelectionFilter) -> Unit, referenceTarget: ReferenceTarget, onReferenceTarget: (ReferenceTarget) -> Unit, correctLineMode: CorrectLineMode, correctLineAmount: Float, onCorrectLineMode: (CorrectLineMode) -> Unit, onCorrectLineAmount: (Float) -> Unit, eraserPreset: EraserPreset, onEraserPreset: (EraserPreset) -> Unit, rulerEnabled: Boolean, rulerSnapDegrees: Float, onToggleRuler: () -> Unit, onRulerSnap: (Float) -> Unit, onResetRuler: () -> Unit, gridSnapEnabled: Boolean, gridSpacing: Float, alignmentGuidesEnabled: Boolean, onToggleGridSnap: () -> Unit, onGridSpacing: (Float) -> Unit, onToggleAlignmentGuides: () -> Unit, onResetAlignmentGuides: () -> Unit, dragModifier: Modifier, vertical: Boolean, moreToolsOpen: Boolean, onMoreToolsOpen: (Boolean) -> Unit, controlsOpen: Boolean, onControlsOpen: (Boolean) -> Unit, onToggleOrientation: () -> Unit, onResetPosition: () -> Unit, onDock: (ToolbarDock) -> Unit, brushSize: Float, brushOpacity: Float, brushColor: Int, brushes: List<ImportedBrush>, selectedBrushId: String?, onSystemPreset: (EditorTool) -> Unit, onBrushPreset: (ImportedBrush) -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit, onColor: (Int) -> Unit, symmetryMode: SymmetryMode, onSymmetryMode: (SymmetryMode) -> Unit, gradientControls: @Composable ColumnScope.() -> Unit) {
    val dragToolbarDescription = tr("Drag toolbar", "拖动工具栏")
    var toolLibraryLayout by rememberSaveable { mutableStateOf(ToolLibraryLayout.LIST) }
    var toolLibrarySort by rememberSaveable { mutableStateOf(ToolLibrarySort.DEFAULT) }
    val libraryPresets = remember(brushes) {
        val defaults = systemBrushCatalog()
        defaults + brushes.filterNot { imported -> defaults.any { it.id == imported.id } }
    }
    Canvas(dragModifier.semantics { contentDescription = dragToolbarDescription }) {
        val lineColor = Color(0xFF8A8A8A)
        if (vertical) {
            repeat(3) { index ->
                val y = 3f + index * 3f
                drawLine(lineColor, Offset(size.width * .28f, y), Offset(size.width * .72f, y), 1f)
            }
        } else {
            repeat(3) { index ->
                val x = 3f + index * 3f
                drawLine(lineColor, Offset(x, size.height * .28f), Offset(x, size.height * .72f), 1f)
            }
        }
    }
    BrushPresetToolButton(EditorTool.PEN, Icons.Outlined.Brush, tr("Brush", "笔刷"), tool, libraryPresets.filterNot(BrushRoleClassifier::isEraser), selectedBrushId, eraserPreset, onTool, onSystemPreset, onEraserPreset, onBrushPreset)
    ToolButton(EditorTool.HIGHLIGHTER, Icons.Outlined.BorderColor, tr("Marker", "标记笔"), tool, onTool)
    BrushPresetToolButton(EditorTool.ERASER, InkloomIcons.Eraser, tr("Eraser", "橡皮"), tool, libraryPresets.filter(BrushRoleClassifier::isEraser), selectedBrushId, eraserPreset, onTool, onSystemPreset, onEraserPreset, onBrushPreset)
    ToolButton(EditorTool.SELECT, Icons.Outlined.Gesture, tr("Select", "选择"), tool, onTool)
    if (vertical) {
        HorizontalDivider(Modifier.width(30.dp).padding(vertical = 2.dp), color = OutlineSoft)
        ToolButton(EditorTool.EYEDROPPER, Icons.Outlined.Colorize, tr("Eyedropper", "吸管"), tool, onTool)
        ToolButton(EditorTool.SHAPE, Icons.Outlined.Category, tr("Shape", "形状"), tool, onTool)
        ToolButton(EditorTool.TEXT, Icons.Outlined.Title, tr("Text", "文字"), tool, onTool)
        ToolButton(EditorTool.NOTE, Icons.Outlined.StickyNote2, tr("Note", "便笺"), tool, onTool)
        ToolButton(EditorTool.IMAGE, Icons.Outlined.Image, tr("Image", "图片"), tool, onTool)
        ToolButton(EditorTool.LASER, Icons.Outlined.TrackChanges, tr("Laser pointer", "激光笔"), tool, onTool)
        HorizontalDivider(Modifier.width(30.dp).padding(vertical = 2.dp), color = OutlineSoft)
    }
    Box {
        CompactToolbarIconButton(onClick = { onMoreToolsOpen(true) }, description = tr("More tools", "更多工具"), selected = rulerEnabled || gridSnapEnabled || alignmentGuidesEnabled || tool in setOf(EditorTool.EYEDROPPER, EditorTool.FILL, EditorTool.GRADIENT, EditorTool.SMUDGE, EditorTool.LIQUIFY, EditorTool.CORRECT_LINE, EditorTool.SHAPE, EditorTool.TEXT, EditorTool.NOTE, EditorTool.LINK, EditorTool.IMAGE, EditorTool.LASER)) { Icon(Icons.Outlined.Apps, null, Modifier.size(20.dp)) }
        DropdownMenu(expanded = moreToolsOpen, onDismissRequest = { onMoreToolsOpen(false) }) {
            val unsortedTools = listOf(
                Triple(EditorTool.SELECT, Icons.Outlined.TouchApp, tr("Operation", "对象操作")),
                Triple(EditorTool.EYEDROPPER, Icons.Outlined.Colorize, tr("Eyedropper", "吸管")),
                Triple(EditorTool.FILL, Icons.Outlined.FormatColorFill, tr("Paint bucket", "油漆桶")),
                Triple(EditorTool.GRADIENT, Icons.Outlined.Gradient, tr("Gradient", "渐变")),
                Triple(EditorTool.SMUDGE, Icons.Outlined.BlurOn, tr("Blend", "混色")),
                Triple(EditorTool.LIQUIFY, Icons.Outlined.Waves, tr("Liquify", "液化")),
                Triple(EditorTool.CORRECT_LINE, Icons.Outlined.AutoFixHigh, tr("Correct line", "线条修正")),
                Triple(EditorTool.SHAPE, Icons.Outlined.Category, tr("Figure", "图形")),
                Triple(EditorTool.TEXT, Icons.Outlined.Title, tr("Text", "文字")),
                Triple(EditorTool.NOTE, Icons.Outlined.StickyNote2, tr("Note", "便笺")),
                Triple(EditorTool.LINK, Icons.Outlined.Link, tr("Link", "链接")),
                Triple(EditorTool.IMAGE, Icons.Outlined.Image, tr("Image", "图片")),
                Triple(EditorTool.LASER, Icons.Outlined.TrackChanges, tr("Laser pointer", "激光笔")),
            )
            val displayedTools = if (toolLibrarySort == ToolLibrarySort.NAME) unsortedTools.sortedBy { it.third.lowercase() } else unsortedTools
            Row(Modifier.width(292.dp).height(40.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Tools", "工具"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                listOf(
                    ToolLibraryLayout.LIST to Icons.Outlined.ViewList,
                    ToolLibraryLayout.GRID to Icons.Outlined.GridView,
                    ToolLibraryLayout.COMPACT to Icons.Outlined.DensitySmall,
                ).forEach { (layout, icon) ->
                    IconButton(
                        onClick = { toolLibraryLayout = layout },
                        modifier = Modifier.size(30.dp).background(if (toolLibraryLayout == layout) Color(0xFFE2E2E2) else Color.Transparent, RoundedCornerShape(4.dp)),
                    ) { Icon(icon, layout.name, Modifier.size(16.dp)) }
                }
                IconButton(onClick = { toolLibrarySort = if (toolLibrarySort == ToolLibrarySort.DEFAULT) ToolLibrarySort.NAME else ToolLibrarySort.DEFAULT }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.SortByAlpha, tr("Toggle tool sorting", "切换工具排序"), Modifier.size(16.dp), tint = if (toolLibrarySort == ToolLibrarySort.NAME) Ink else MutedInk)
                }
            }
            HorizontalDivider()
            if (toolLibraryLayout == ToolLibraryLayout.LIST) displayedTools.forEach { (value, icon, label) ->
                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = {
                    onMoreToolsOpen(false)
                    onTool(value)
                    if (value == EditorTool.CORRECT_LINE) onControlsOpen(true)
                })
            } else {
                val columns = if (toolLibraryLayout == ToolLibraryLayout.COMPACT) 5 else 3
                Column(Modifier.width(292.dp).padding(5.dp)) {
                    displayedTools.chunked(columns).forEach { rowTools ->
                        Row(Modifier.fillMaxWidth()) {
                            rowTools.forEach { (value, icon, label) ->
                                Column(
                                    Modifier.weight(1f).height(if (toolLibraryLayout == ToolLibraryLayout.COMPACT) 44.dp else 68.dp)
                                        .clickable {
                                            onMoreToolsOpen(false)
                                            onTool(value)
                                            if (value == EditorTool.CORRECT_LINE) onControlsOpen(true)
                                        }
                                        .background(if (tool == value) Color(0xFFE0E0E0) else Color.Transparent, RoundedCornerShape(4.dp))
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(icon, label, Modifier.size(if (toolLibraryLayout == ToolLibraryLayout.COMPACT) 22.dp else 25.dp))
                                    if (toolLibraryLayout == ToolLibraryLayout.GRID) Text(label, fontSize = 9.sp, maxLines = 1)
                                }
                            }
                            repeat(columns - rowTools.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(tr(if (rulerEnabled) "Hide ruler" else "Show ruler", if (rulerEnabled) "隐藏直尺" else "显示直尺")) },
                leadingIcon = { Icon(Icons.Outlined.Straighten, null) },
                trailingIcon = { if (rulerEnabled) Icon(Icons.Outlined.Check, null) },
                onClick = { onMoreToolsOpen(false); onToggleRuler() },
            )
            DropdownMenuItem(
                text = { Text(tr(if (gridSnapEnabled) "Hide snap grid" else "Show snap grid", if (gridSnapEnabled) "隐藏吸附网格" else "显示吸附网格")) },
                leadingIcon = { Icon(Icons.Outlined.GridOn, null) },
                trailingIcon = { if (gridSnapEnabled) Icon(Icons.Outlined.Check, null) },
                onClick = { onMoreToolsOpen(false); onToggleGridSnap() },
            )
            DropdownMenuItem(
                text = { Text(tr(if (alignmentGuidesEnabled) "Hide alignment guides" else "Show alignment guides", if (alignmentGuidesEnabled) "隐藏对齐参考线" else "显示对齐参考线")) },
                leadingIcon = { Icon(Icons.Outlined.AlignHorizontalCenter, null) },
                trailingIcon = { if (alignmentGuidesEnabled) Icon(Icons.Outlined.Check, null) },
                onClick = { onMoreToolsOpen(false); onToggleAlignmentGuides() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(tr("Symmetry", "对称") + ": " + when (symmetryMode) { SymmetryMode.NONE -> tr("Off", "关"); SymmetryMode.HORIZONTAL -> tr("Horizontal", "水平"); SymmetryMode.VERTICAL -> tr("Vertical", "垂直"); SymmetryMode.QUADRANT -> tr("Quadrant", "四象限"); SymmetryMode.RADIAL_4 -> tr("Radial 4", "径向 4"); SymmetryMode.RADIAL_6 -> tr("Radial 6", "径向 6"); SymmetryMode.RADIAL_8 -> tr("Radial 8", "径向 8") }) },
                leadingIcon = { Icon(Icons.Outlined.Flip, null) },
                onClick = {
                    val next = SymmetryMode.entries[(symmetryMode.ordinal + 1) % SymmetryMode.entries.size]
                    onSymmetryMode(next)
                },
            )
        }
    }
    Box {
        CompactToolbarIconButton(onClick = { onControlsOpen(true) }, description = tr("Tool controls", "工具参数")) { Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp)) }
        DropdownMenu(expanded = controlsOpen, onDismissRequest = { onControlsOpen(false) }, modifier = Modifier.widthIn(min = 300.dp, max = 360.dp)) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${when (tool) { EditorTool.PEN -> tr("Pen", "钢笔"); EditorTool.HIGHLIGHTER -> tr("Marker", "标记笔"); EditorTool.ERASER -> tr("Eraser", "橡皮"); EditorTool.SMUDGE -> tr("Blend", "混色"); EditorTool.LIQUIFY -> tr("Liquify", "液化"); EditorTool.CORRECT_LINE -> tr("Correct line", "线条修正"); EditorTool.GRADIENT -> tr("Gradient", "渐变"); EditorTool.SELECT -> tr("Selection", "选择"); else -> tool.name.lowercase().replaceFirstChar(Char::uppercase) }} ${tr("controls", "参数")}", fontWeight = FontWeight.SemiBold)
                if (tool == EditorTool.GRADIENT) gradientControls()
                if (tool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SMUDGE, EditorTool.LIQUIFY)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Size", "大小"), Modifier.weight(1f), color = MutedInk)
                        IconButton(onClick = { onSize(brushSize - maxOf(.5f, brushSize * .12f)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Remove, tr("Smaller", "减小")) }
                        Text(if (brushSize < 10f) "%.1f".format(brushSize) else brushSize.toInt().toString(), Modifier.width(38.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { onSize(brushSize + maxOf(.5f, brushSize * .12f)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Add, tr("Larger", "增大")) }
                    }
                    if (tool !in setOf(EditorTool.ERASER, EditorTool.LIQUIFY)) {
                        Text("${tr("Opacity", "不透明度")}  ${(brushOpacity * 100).toInt()}%", color = MutedInk, fontSize = 12.sp)
                        Slider(value = brushOpacity, onValueChange = onOpacity, valueRange = .05f..1f)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Color", "颜色"), Modifier.weight(1f), color = MutedInk); BrushColorPicker(brushColor, onColor) }
                    }
                }
                if (tool == EditorTool.ERASER) {
                    Text(tr("Eraser brush", "橡皮笔刷"), color = MutedInk, fontSize = 12.sp)
                    EraserPresetPicker(eraserPreset, onEraserPreset)
                }
                if (rulerEnabled) {
                    HorizontalDivider()
                    Text(tr("Ruler angle snap", "直尺角度吸附"), color = MutedInk, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(1f, 5f, 15f, 45f).forEach { step ->
                            FilterChip(selected = rulerSnapDegrees == step, onClick = { onRulerSnap(step) }, label = { Text("${step.toInt()}°", fontSize = 10.sp) })
                        }
                    }
                    OutlinedButton(onClick = onResetRuler, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text(tr("Reset ruler", "重置直尺")) }
                }
                if (gridSnapEnabled) {
                    HorizontalDivider()
                    Text(tr("Snap grid spacing", "吸附网格间距"), color = MutedInk, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(32f, 64f, 96f, 128f).forEach { spacing ->
                            FilterChip(selected = gridSpacing == spacing, onClick = { onGridSpacing(spacing) }, label = { Text(spacing.toInt().toString(), fontSize = 10.sp) })
                        }
                    }
                }
                if (alignmentGuidesEnabled) {
                    HorizontalDivider()
                    Text(tr("Alignment guides", "对齐参考线"), color = MutedInk, fontSize = 12.sp)
                    Text(tr("Drag either blue line with one finger. Pen strokes and shape points snap when they start nearby.", "用单指拖动任一蓝色参考线；笔迹落点和形状端点靠近时会自动吸附。"), color = MutedInk, fontSize = 10.sp)
                    OutlinedButton(onClick = onResetAlignmentGuides, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text(tr("Center alignment guides", "参考线居中")) }
                }
                if (tool == EditorTool.SELECT) {
                    Text(tr("Selection", "选择"), color = MutedInk, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SelectionShape.entries.forEach { shape -> FilterChip(selected = selectionShape == shape, onClick = { onSelectionShape(shape) }, label = { Text(when (shape) { SelectionShape.SMART_BORDER -> tr("Smart", "智能"); SelectionShape.MAGIC_WAND -> tr("Wand", "魔棒"); SelectionShape.FREEFORM -> tr("Freeform", "自由套索"); SelectionShape.RECTANGLE -> tr("Rectangle", "矩形"); SelectionShape.ELLIPSE -> tr("Ellipse", "椭圆"); SelectionShape.BRUSH -> tr("Brush", "画笔") }, fontSize = 10.sp) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SelectionMode.entries.forEach { mode -> FilterChip(selected = selectionMode == mode, onClick = { onSelectionMode(mode) }, label = { Text(when (mode) { SelectionMode.REPLACE -> tr("New", "新建"); SelectionMode.ADD -> tr("Add", "添加"); SelectionMode.SUBTRACT -> tr("Subtract", "减去"); SelectionMode.INTERSECT -> tr("Intersect", "交集") }, fontSize = 10.sp) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SelectionFilter.entries.forEach { filter -> FilterChip(selected = selectionFilter == filter, onClick = { onSelectionFilter(filter) }, label = { Text(when (filter) { SelectionFilter.ALL -> tr("All", "全部"); SelectionFilter.ACTIVE_LAYER -> tr("Layer", "图层"); SelectionFilter.CURRENT_BRUSH -> tr("Brush", "笔刷"); SelectionFilter.CURRENT_COLOR -> tr("Color", "颜色") }, fontSize = 10.sp) }) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CompactToolbarIconButton(onScale, tr("Scale selection", "缩放所选内容")) { Icon(Icons.Outlined.ZoomOutMap, null) }
                        CompactToolbarIconButton(onRotate, tr("Rotate selection", "旋转所选内容")) { Icon(Icons.Outlined.RotateRight, null) }
                        CompactToolbarIconButton(onCopy, tr("Duplicate selection", "复制所选内容")) { Icon(Icons.Outlined.ControlPointDuplicate, null) }
                        CompactToolbarIconButton(onDelete, tr("Delete selection", "删除所选内容")) { Icon(Icons.Outlined.Delete, null) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CompactToolbarIconButton(onCut, tr("Cut ink selection", "剪切所选墨迹")) { Icon(Icons.Outlined.ContentCut, null) }
                        CompactToolbarIconButton(onClipboardCopy, tr("Copy ink selection to clipboard", "复制所选墨迹到剪贴板")) { Icon(Icons.Outlined.ContentCopy, null) }
                        CompactToolbarIconButton(onPaste, tr("Paste ink selection", "粘贴所选墨迹")) { Icon(Icons.Outlined.ContentPaste, null) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = onSelectAll) { Text(tr("All", "全选"), fontSize = 10.sp) }
                        TextButton(onClick = onInvertSelection) { Text(tr("Invert", "反选"), fontSize = 10.sp) }
                        TextButton(onClick = onDeselect) { Text(tr("Deselect", "取消选区"), fontSize = 10.sp) }
                        TextButton(onClick = onFreeTransform) { Text(tr("Transform", "自由变换"), fontSize = 10.sp) }
                    }
                }
                if (tool in setOf(EditorTool.SELECT, EditorTool.FILL)) {
                    Text(tr("Reference", "参照范围"), color = MutedInk, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ReferenceTarget.entries.forEach { target ->
                            FilterChip(
                                selected = referenceTarget == target,
                                onClick = { onReferenceTarget(target) },
                                label = {
                                    Text(
                                        when (target) {
                                            ReferenceTarget.ALL_VISIBLE -> tr("All visible", "全部可见")
                                            ReferenceTarget.ACTIVE_LAYER -> tr("Current", "当前图层")
                                            ReferenceTarget.REFERENCE_LAYERS -> tr("Reference", "参考图层")
                                        },
                                        fontSize = 10.sp,
                                    )
                                },
                            )
                        }
                    }
                    Text(tr("Draft layers are excluded. If no reference layer is marked, the current layer is used.", "草稿图层会被排除；若未标记参考图层，则使用当前图层。"), color = MutedInk, fontSize = 9.sp)
                }
                if (tool == EditorTool.CORRECT_LINE) {
                    Text(tr("Correct line", "线条修正"), color = MutedInk, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(CorrectLineMode.entries, key = { it.name }) { mode ->
                            val correctionDescription = when (mode) {
                                CorrectLineMode.SMOOTH -> tr("Smooth line correction", "平滑线条修正")
                                CorrectLineMode.SIMPLIFY -> tr("Simplify line correction", "简化线条修正")
                                CorrectLineMode.ADJUST_WIDTH -> tr("Adjust line width correction", "调整线宽修正")
                                CorrectLineMode.REDRAW_WIDTH -> tr("Redraw vector line width", "重绘矢量线宽")
                                CorrectLineMode.CONNECT -> tr("Connect vector lines", "连接矢量线")
                                CorrectLineMode.CONTROL_POINTS -> tr("Move vector control points", "移动矢量控制点")
                            }
                            FilterChip(
                                selected = correctLineMode == mode,
                                onClick = { onCorrectLineMode(mode) },
                                modifier = Modifier.semantics { contentDescription = correctionDescription },
                                label = {
                                    Text(
                                        when (mode) {
                                            CorrectLineMode.SMOOTH -> tr("Smooth", "平滑")
                                            CorrectLineMode.SIMPLIFY -> tr("Simplify", "简化")
                                            CorrectLineMode.ADJUST_WIDTH -> tr("Width", "线宽")
                                            CorrectLineMode.REDRAW_WIDTH -> tr("Redraw", "重绘线宽")
                                            CorrectLineMode.CONNECT -> tr("Connect", "连接")
                                            CorrectLineMode.CONTROL_POINTS -> tr("Points", "控制点")
                                        },
                                        fontSize = 10.sp,
                                    )
                                },
                            )
                        }
                    }
                    Text("${tr("Correction strength", "修正强度")}  ${(correctLineAmount * 100).toInt()}%", color = MutedInk, fontSize = 12.sp)
                    val correctionStrengthDescription = tr("Line correction strength", "线条修正强度")
                    Slider(
                        value = correctLineAmount,
                        onValueChange = onCorrectLineAmount,
                        valueRange = 0f..1f,
                        modifier = Modifier.semantics { contentDescription = correctionStrengthDescription },
                    )
                    Text(
                        when (correctLineMode) {
                            CorrectLineMode.SMOOTH -> tr("Drag across an existing line to reduce shake while preserving its endpoints.", "在已有线条上拖动，减少抖动并保留首尾端点。")
                            CorrectLineMode.SIMPLIFY -> tr("Drag across a vector line to reduce redundant control points.", "在矢量线条上拖动，减少多余控制点。")
                            CorrectLineMode.ADJUST_WIDTH -> tr("Drag across a line to change its entire width by the selected strength.", "在线条上拖动，按所选强度调整整条线宽。")
                            CorrectLineMode.REDRAW_WIDTH -> tr("Drag horizontally over a vector line to redraw its pressure-based width.", "在矢量线条上横向拖动，重绘基于压力的局部线宽。")
                            CorrectLineMode.CONNECT -> tr("Drag from one vector line endpoint to another to connect them.", "从一条矢量线端点拖到另一条端点以连接线条。")
                            CorrectLineMode.CONTROL_POINTS -> tr("Drag the nearest vector control point to reshape the line.", "拖动最近的矢量控制点以调整线条形状。")
                        } + tr(" Undo restores the original vector line.", " 撤销可恢复原始矢量线条。"),
                        color = MutedInk,
                        fontSize = 10.sp,
                    )
                }
                HorizontalDivider()
                Text(tr("Palette position", "工具栏位置"), color = MutedInk, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ToolbarDock.entries.forEach { dock -> AssistChip(onClick = { onDock(dock) }, label = { Text(when (dock) { ToolbarDock.FREE -> tr("Free", "自由"); ToolbarDock.TOP -> tr("Top", "顶部"); ToolbarDock.BOTTOM -> tr("Bottom", "底部"); ToolbarDock.LEFT -> tr("Left", "左侧"); ToolbarDock.RIGHT -> tr("Right", "右侧") }, fontSize = 10.sp) }) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToggleOrientation, modifier = Modifier.weight(1f)) { Icon(if (vertical) Icons.Outlined.SwapHoriz else Icons.Outlined.SwapVert, null); Spacer(Modifier.width(5.dp)); Text(if (vertical) tr("Horizontal", "水平") else tr("Vertical", "垂直")) }
                    OutlinedButton(onClick = onResetPosition, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CenterFocusStrong, null); Spacer(Modifier.width(5.dp)); Text(tr("Reset", "重置")) }
                }
            }
        }
    }
    if (vertical) {
        CspToolbarColorSwatches(brushColor, onColor)
        HorizontalDivider(Modifier.width(30.dp).padding(vertical = 2.dp), color = OutlineSoft)
    } else {
        BrushColorPicker(brushColor, onColor, square = true)
    }
    CompactToolbarIconButton(onClick = onUndo, description = tr("Undo", "撤销")) { Icon(Icons.Outlined.Undo, null, Modifier.size(20.dp)) }
    CompactToolbarIconButton(onClick = onRedo, description = tr("Redo", "重做")) { Icon(Icons.Outlined.Redo, null, Modifier.size(20.dp)) }
}

@Composable
private fun CompactToolbarIconButton(onClick: () -> Unit, description: String, selected: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.size(32.dp).background(if (selected) Color(0xFFD6DCE8) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides Ink) { Box(Modifier.semantics { contentDescription = description }) { content() } }
    }
}

@Composable
private fun BrushColorPicker(color: Int, onColor: (Int) -> Unit, square: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val paletteStore = remember { ColorPaletteStore(context) }
    var customPalette by remember { mutableStateOf(paletteStore.load()) }
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(0f) }
    val presets = listOf(0xFF111111, 0xFF5B4B8A, 0xFFB33A3A, 0xFF235A9F, 0xFFB56A21, 0xFF2E7D60, 0xFFFFFFFF).map { it.toInt() }
    val colors = (presets + customPalette).distinct()
    LaunchedEffect(editorOpen, color) {
        if (editorOpen) {
            val values = FloatArray(3)
            AndroidColor.colorToHSV(color, values)
            hue = values[0]; saturation = values[1]; brightness = values[2]
        }
    }
    val customColor = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
    Box {
        val swatchShape = if (square) RoundedCornerShape(2.dp) else CircleShape
        Box(Modifier.size(28.dp).clip(swatchShape).background(Color(color)).border(1.dp, OutlineSoft, swatchShape).clickable { customPalette = paletteStore.load(); open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.widthIn(min = 280.dp, max = 420.dp).padding(10.dp)) {
                Text(tr("Colors", "颜色"), fontSize = 12.sp, color = MutedInk)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { items(colors) { value -> Box(Modifier.size(30.dp).clip(CircleShape).background(Color(value)).border(if (value == color) 3.dp else 1.dp, if (value == color) Sage else OutlineSoft, CircleShape).clickable { onColor(value); open = false }) } }
                TextButton(onClick = { open = false; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Palette, null); Spacer(Modifier.width(8.dp)); Text(tr("Custom color and palette", "自定义颜色与色板")) }
            }
        }
    }
    if (editorOpen) AlertDialog(
        onDismissRequest = { editorOpen = false },
        title = { Text(tr("Color and palette", "颜色与色板")) },
        text = {
            LazyColumn(Modifier.widthIn(min = 340.dp, max = 520.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(54.dp).clip(CircleShape).background(Color(customColor)).border(1.dp, OutlineSoft, CircleShape)); Spacer(Modifier.width(12.dp)); Text("#${customColor.toUInt().toString(16).padStart(8, '0').uppercase()}") } }
                item { Text("${tr("Hue", "色相")}  ${hue.toInt()}°"); Slider(hue, { hue = it }, valueRange = 0f..360f) }
                item { Text("${tr("Saturation", "饱和度")}  ${(saturation * 100).toInt()}%"); Slider(saturation, { saturation = it }, valueRange = 0f..1f) }
                item { Text("${tr("Brightness", "明度")}  ${(brightness * 100).toInt()}%"); Slider(brightness, { brightness = it }, valueRange = 0f..1f) }
                item { OutlinedButton(onClick = { customPalette = paletteStore.add(customColor) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("Add current color to palette", "将当前颜色加入色板")) } }
                if (customPalette.isNotEmpty()) item {
                    Text(tr("Saved palette", "已保存色板"), color = MutedInk, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(customPalette, key = { it }) { value ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(value)).border(1.dp, OutlineSoft, CircleShape).clickable { val values = FloatArray(3); AndroidColor.colorToHSV(value, values); hue = values[0]; saturation = values[1]; brightness = values[2] })
                                IconButton(onClick = { customPalette = paletteStore.remove(value) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, tr("Remove color", "移除颜色"), modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onColor(customColor); editorOpen = false }) { Text(tr("Use color", "使用此颜色")) } },
        dismissButton = { TextButton(onClick = { editorOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun CspToolbarColorSwatches(color: Int, onColor: (Int) -> Unit) {
    val description = tr("Foreground and background colors", "前景色和背景色")
    Box(
        Modifier.size(width = 38.dp, height = 43.dp).semantics { contentDescription = description },
    ) {
        Box(
            Modifier.align(Alignment.BottomEnd).size(27.dp).background(Color.White).border(1.dp, Ink, RoundedCornerShape(2.dp)),
        )
        Box(Modifier.align(Alignment.TopStart)) { BrushColorPicker(color, onColor, square = true) }
        Box(
            Modifier.align(Alignment.BottomStart).size(width = 32.dp, height = 7.dp).border(1.dp, OutlineSoft).background(
                Brush.horizontalGradient(listOf(Color.White, Color(0xFFD0D0D0), Color.White, Color(0xFFD0D0D0))),
            ),
        )
    }
}

private data class HslColor(val hue: Float, val saturation: Float, val lightness: Float)

private fun colorToHsl(color: Int): HslColor {
    val red = AndroidColor.red(color) / 255f
    val green = AndroidColor.green(color) / 255f
    val blue = AndroidColor.blue(color) / 255f
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    val saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    val hueSector = when {
        delta == 0f -> 0f
        maximum == red -> ((green - blue) / delta) % 6f
        maximum == green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    }
    return HslColor((hueSector * 60f).let { if (it < 0f) it + 360f else it }, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
}

private fun hslToColor(alpha: Int, hue: Float, saturation: Float, lightness: Float): Int {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val chroma = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
    val section = normalizedHue / 60f
    val intermediate = chroma * (1f - kotlin.math.abs(section % 2f - 1f))
    val (rawRed, rawGreen, rawBlue) = when (section.toInt()) {
        0 -> Triple(chroma, intermediate, 0f)
        1 -> Triple(intermediate, chroma, 0f)
        2 -> Triple(0f, chroma, intermediate)
        3 -> Triple(0f, intermediate, chroma)
        4 -> Triple(intermediate, 0f, chroma)
        else -> Triple(chroma, 0f, intermediate)
    }
    val match = lightness - chroma / 2f
    return AndroidColor.argb(
        alpha,
        ((rawRed + match) * 255f).toInt().coerceIn(0, 255),
        ((rawGreen + match) * 255f).toInt().coerceIn(0, 255),
        ((rawBlue + match) * 255f).toInt().coerceIn(0, 255),
    )
}

@Composable
private fun CspColorPanel(
    color: Int,
    onColorPreview: (Int) -> Unit,
    onColorCommit: (Int) -> Unit,
    initiallyExpanded: Boolean = true,
    onResizeWidth: ((Float) -> Unit)? = null,
) {
    SideEffect { ColorPickerInteractionTrace.panelComposition() }
    var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    var channelMode by rememberSaveable { mutableStateOf("HSV") }
    var pickerHeight by rememberSaveable { mutableFloatStateOf(148f) }
    var draft by remember { mutableStateOf(ColorPickerDraft.fromColor(color)) }
    val draftColor = draft.color
    val latestOnColorPreview by rememberUpdatedState(onColorPreview)
    val latestOnColorCommit by rememberUpdatedState(onColorCommit)
    LaunchedEffect(color) { draft = draft.withColor(color) }
    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(draft.hue, 1f, 1f)))
    val hueRingBrush = remember {
        ColorPickerInteractionTrace.hueRingAllocation()
        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
    }
    val saturationBrush = remember(hueColor) {
        ColorPickerInteractionTrace.saturationGradientAllocation()
        Brush.horizontalGradient(listOf(Color.White, hueColor))
    }
    val valueBrush = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
    }
    val hueRingDescription = tr("Hue ring", "色相环")
    val saturationValueDescription = tr("Saturation and brightness field", "饱和度和明度区域")
    val resizeColorPickerDescription = tr("Resize color picker", "调整色环大小")
    fun updateDraft(next: ColorPickerDraft) {
        val colorChanged = draft.color != next.color
        draft = next
        if (colorChanged) {
            ColorPickerInteractionTrace.previewWrite()
            latestOnColorPreview(next.color)
        }
    }
    fun updateColor(next: Int) = updateDraft(draft.withColor(next))
    fun publish(hue: Float = draft.hue, saturation: Float = draft.saturation, value: Float = draft.value) {
        updateDraft(draft.withHsv(hue, saturation, value))
    }
    fun commitDraft() {
        ColorPickerInteractionTrace.workspaceCommit()
        latestOnColorCommit(draft.color)
    }
    Surface(
        Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(3.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(34.dp).clickable { expanded = !expanded }.padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Color wheel", "色环"), Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.size(18.dp).background(Color(draftColor)).border(1.dp, OutlineSoft))
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) tr("Collapse color panel", "收起颜色面板") else tr("Expand color panel", "展开颜色面板"), Modifier.size(18.dp))
            }
            if (expanded) {
                Row(
                    Modifier.fillMaxWidth().height(pickerHeight.dp).padding(horizontal = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Canvas(
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .semantics { contentDescription = hueRingDescription }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val dx = down.position.x - size.width / 2f
                                    val dy = down.position.y - size.height / 2f
                                    val distance = kotlin.math.hypot(dx, dy)
                                    val outer = minOf(size.width, size.height) / 2f
                                    val inner = outer * .66f
                                    if (distance !in inner..outer * 1.12f) return@awaitEachGesture
                                    var gestureDraft = draft
                                    fun update(point: Offset) {
                                        ColorPickerInteractionTrace.pointerSample()
                                        var degrees = Math.toDegrees(kotlin.math.atan2(
                                            (point.y - size.height / 2f).toDouble(),
                                            (point.x - size.width / 2f).toDouble(),
                                        )).toFloat()
                                        if (degrees < 0f) degrees += 360f
                                        gestureDraft = gestureDraft.withHsv(hue = degrees)
                                        updateDraft(gestureDraft)
                                    }
                                    down.consume()
                                    update(down.position)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            ColorPickerInteractionTrace.workspaceCommit()
                                            latestOnColorCommit(gestureDraft.color)
                                            break
                                        }
                                        change.consume()
                                        update(change.position)
                                    }
                                }
                            },
                    ) {
                        val ring = size.minDimension * .15f
                        val radius = size.minDimension / 2f - ring / 2f
                        drawCircle(
                            hueRingBrush,
                            radius = radius,
                            style = Stroke(ring),
                        )
                        val radians = Math.toRadians(draft.hue.toDouble())
                        val markerRadius = radius
                        val marker = Offset(center.x + kotlin.math.cos(radians).toFloat() * markerRadius, center.y + kotlin.math.sin(radians).toFloat() * markerRadius)
                        drawCircle(Color.White, size.minDimension * .045f, marker)
                        drawCircle(Ink, size.minDimension * .045f, marker, style = Stroke(size.minDimension * .012f))
                        drawCircle(Color(draftColor), radius = (radius - ring / 1.5f).coerceAtLeast(1f))
                    }
                    Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { contentDescription = saturationValueDescription }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var gestureDraft = draft
                                    fun update(point: Offset) {
                                        ColorPickerInteractionTrace.pointerSample()
                                        gestureDraft = gestureDraft.withHsv(
                                            saturation = point.x / size.width.coerceAtLeast(1),
                                            value = 1f - point.y / size.height.coerceAtLeast(1),
                                        )
                                        updateDraft(gestureDraft)
                                    }
                                    down.consume()
                                    update(down.position)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            ColorPickerInteractionTrace.workspaceCommit()
                                            latestOnColorCommit(gestureDraft.color)
                                            break
                                        }
                                        change.consume()
                                        update(change.position)
                                    }
                                }
                            },
                    ) {
                        // The rendered field and the inverse pointer mapping intentionally use the
                        // same rectangular HSV model: X is saturation, Y is inverse value. The old
                        // triangular barycentric mapping was applied to a rectangular gradient,
                        // so the selected S/V numbers could not match the color under the cursor.
                        drawRect(saturationBrush)
                        drawRect(valueBrush)
                        drawRect(Ink, style = Stroke(1f))
                        val marker = Offset(draft.saturation * size.width, (1f - draft.value) * size.height)
                        drawCircle(Color.White, 5f, marker)
                        drawCircle(Ink, 5f, marker, style = Stroke(1.5f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("HSV", "RGB").forEach { mode ->
                        FilterChip(
                            selected = channelMode == mode,
                            onClick = { channelMode = mode },
                            label = { Text(mode, fontSize = 9.sp) },
                            modifier = Modifier.height(25.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    BrushColorPicker(draftColor, { selected ->
                        updateColor(selected)
                        ColorPickerInteractionTrace.workspaceCommit()
                        latestOnColorCommit(selected)
                    }, square = true)
                }
                if (channelMode == "RGB") {
                    CspColorChannel("R", AndroidColor.red(draftColor), { updateColor(AndroidColor.argb(AndroidColor.alpha(draftColor), it, AndroidColor.green(draftColor), AndroidColor.blue(draftColor))) }, ::commitDraft)
                    CspColorChannel("G", AndroidColor.green(draftColor), { updateColor(AndroidColor.argb(AndroidColor.alpha(draftColor), AndroidColor.red(draftColor), it, AndroidColor.blue(draftColor))) }, ::commitDraft)
                    CspColorChannel("B", AndroidColor.blue(draftColor), { updateColor(AndroidColor.argb(AndroidColor.alpha(draftColor), AndroidColor.red(draftColor), AndroidColor.green(draftColor), it)) }, ::commitDraft)
                } else {
                    CspFloatColorChannel("H", draft.hue, 0f..360f, "${draft.hue.toInt()}°", { publish(hue = it) }, ::commitDraft)
                    CspFloatColorChannel("S", draft.saturation, 0f..1f, "${(draft.saturation * 100f).toInt()}%", { publish(saturation = it) }, ::commitDraft)
                    CspFloatColorChannel("V", draft.value, 0f..1f, "${(draft.value * 100f).toInt()}%", { publish(value = it) }, ::commitDraft)
                }
                Row(
                    Modifier.fillMaxWidth().height(26.dp).semantics { contentDescription = resizeColorPickerDescription }
                        .pointerInput(Unit) {
                            detectDragGestures { change, amount ->
                                change.consume()
                                pickerHeight = (pickerHeight + amount.y / density).coerceIn(112f, 280f)
                                onResizeWidth?.invoke(amount.x / density)
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFB7B7B7)))
                }
            }
        }
    }
}

@Composable
private fun CspFloatColorChannel(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onValue: (Float) -> Unit, onFinished: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(18.dp), color = MutedInk, fontSize = 9.sp)
        Slider(value.coerceIn(range.start, range.endInclusive), onValue, Modifier.weight(1f).height(19.dp), onValueChangeFinished = onFinished, valueRange = range)
        Text(display, Modifier.width(34.dp), color = MutedInk, fontSize = 9.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun CspColorChannel(label: String, value: Int, onValue: (Int) -> Unit, onFinished: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(18.dp), color = MutedInk, fontSize = 9.sp)
        Slider(value.toFloat(), { onValue(it.toInt().coerceIn(0, 255)) }, Modifier.weight(1f).height(19.dp), onValueChangeFinished = onFinished, valueRange = 0f..255f)
        Text(value.toString(), Modifier.width(28.dp), color = MutedInk, fontSize = 9.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun ToolButton(value: EditorTool, icon: ImageVector, label: String, selected: EditorTool, onTool: (EditorTool) -> Unit) {
    Box(Modifier.size(32.dp).background(if (value == selected) Color(0xFFD6DCE8) else Color.Transparent).clickable { onTool(value) }, contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(19.dp), tint = Ink)
    }
}

@Composable
private fun BrushPresetToolButton(
    value: EditorTool,
    icon: ImageVector,
    label: String,
    selected: EditorTool,
    presets: List<ImportedBrush>,
    selectedBrushId: String?,
    eraserPreset: EraserPreset,
    onTool: (EditorTool) -> Unit,
    onSystemPreset: (EditorTool) -> Unit,
    onEraserPreset: (EraserPreset) -> Unit,
    onPreset: (ImportedBrush) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(32.dp)
                .background(if (value == selected) Color(0xFFD6DCE8) else Color.Transparent)
                .clickable {
                    if (value == selected) open = true else onTool(value)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(19.dp), tint = Ink)
            Canvas(Modifier.align(Alignment.BottomEnd).size(8.dp).padding(1.dp)) {
                val path = Path().apply { moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width, 0f); close() }
                drawPath(path, MutedInk)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = 172.dp, max = 224.dp).background(Color(0xFFF4F4F4)),
        ) {
            Text(
                if (value == EditorTool.ERASER) tr("Eraser sub tools", "橡皮子工具") else tr("Brush sub tools", "笔刷子工具"),
                Modifier.fillMaxWidth().height(25.dp).background(Color(0xFFE4E4E4)).padding(horizontal = 7.dp).wrapContentHeight(Alignment.CenterVertically),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (value == EditorTool.PEN) {
                Row(
                    Modifier.fillMaxWidth().height(38.dp).background(if (selectedBrushId == null && selected == value) Color(0xFFD6DCE8) else Color.Transparent)
                        .clickable { open = false; onSystemPreset(EditorTool.PEN) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("System pen", "系统钢笔"), fontSize = 11.sp)
                }
            } else {
                EraserPreset.entries.forEach { preset ->
                    Row(
                        Modifier.fillMaxWidth().height(36.dp).background(if (selectedBrushId == null && eraserPreset == preset && selected == value) Color(0xFFD6DCE8) else Color.Transparent)
                            .clickable { open = false; onSystemPreset(EditorTool.ERASER); onEraserPreset(preset) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AutoFixOff, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(preset.displayName(), fontSize = 11.sp)
                    }
                }
            }
            if (presets.isNotEmpty()) {
                HorizontalDivider()
                Text(tr("Brush library", "笔刷库"), Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 7.dp).wrapContentHeight(Alignment.CenterVertically), color = MutedInk, fontSize = 9.sp)
            }
            presets.sortedWith(compareBy<ImportedBrush> { it.groupName }.thenBy { it.sortOrder }).forEach { brush ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .background(if (brush.id == selectedBrushId) Color(0xFFD6DCE8) else Color.Transparent)
                        .clickable { open = false; onPreset(brush) }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrushTipPreview(brush, Modifier.width(58.dp).height(28.dp))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(brush.name, maxLines = 1, fontSize = 11.sp)
                        if (brush.groupName.isNotBlank()) Text(BrushGroupOperations.displayName(brush.groupName), maxLines = 1, fontSize = 9.sp, color = MutedInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerMaskMenuItems(layer: InkLayer, onCommand: (LayerMaskCommand) -> Unit) {
    if (layer.role in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) return
    val mask = layer.mask
    if (mask == null) {
        DropdownMenuItem(
            text = { Text(tr("Mask selection", "蒙版选区")) },
            leadingIcon = { Icon(Icons.Outlined.FilterAlt, null) },
            onClick = { onCommand(LayerMaskCommand.CREATE_MASK_SELECTION) },
        )
        DropdownMenuItem(
            text = { Text(tr("Mask outside selection", "蒙版选区外")) },
            leadingIcon = { Icon(Icons.Outlined.FilterAltOff, null) },
            onClick = { onCommand(LayerMaskCommand.CREATE_MASK_OUTSIDE_SELECTION) },
        )
    } else {
        DropdownMenuItem(
            text = { Text(tr("Edit layer mask", "编辑图层蒙版")) },
            leadingIcon = { Icon(Icons.Outlined.Brush, null) },
            onClick = { onCommand(LayerMaskCommand.EDIT) },
        )
        DropdownMenuItem(text = { Text(tr("Reveal all", "全部显示")) }, onClick = { onCommand(LayerMaskCommand.FILL_WHITE) })
        DropdownMenuItem(text = { Text(tr("Hide all", "全部隐藏")) }, onClick = { onCommand(LayerMaskCommand.FILL_BLACK) })
        DropdownMenuItem(text = { Text(tr("Hide current selection", "隐藏当前选区")) }, onClick = { onCommand(LayerMaskCommand.HIDE_SELECTION) })
        DropdownMenuItem(text = { Text(tr("Reveal current selection", "显示当前选区")) }, onClick = { onCommand(LayerMaskCommand.REVEAL_SELECTION) })
        DropdownMenuItem(
            text = { Text(if (mask.enabled) tr("Disable layer mask", "停用图层蒙版") else tr("Enable layer mask", "启用图层蒙版")) },
            onClick = { onCommand(LayerMaskCommand.TOGGLE_ENABLED) },
        )
        DropdownMenuItem(
            text = { Text(if (mask.linked) tr("Unlink mask from layer", "解除蒙版与图层链接") else tr("Link mask to layer", "链接蒙版与图层")) },
            leadingIcon = { Icon(if (mask.linked) Icons.Outlined.LinkOff else Icons.Outlined.Link, null) },
            onClick = { onCommand(LayerMaskCommand.TOGGLE_LINKED) },
        )
        DropdownMenuItem(text = { Text(tr("Invert layer mask", "反相图层蒙版")) }, onClick = { onCommand(LayerMaskCommand.INVERT) })
        if (mask.surface != null) DropdownMenuItem(
            text = { Text(tr("Load mask as selection", "从蒙版载入选区")) },
            onClick = { onCommand(LayerMaskCommand.LOAD_AS_SELECTION) },
        )
        DropdownMenuItem(text = { Text(tr("Delete layer mask", "删除图层蒙版")) }, onClick = { onCommand(LayerMaskCommand.DELETE) })
    }
}

@Composable
private fun LayerMaskThumbnailView(mask: LayerMask, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snapshot = remember(mask.surface) {
        mask.surface?.let { reference ->
            runCatching { DocumentAssetService(context).loadMaskTileSurface(reference).snapshot() }.getOrNull()
        }
    }
    if (snapshot == null) {
        Icon(Icons.Outlined.FilterAlt, tr("Layer mask", "图层蒙版"), modifier, tint = if (mask.enabled) Ink else MutedInk)
        return
    }
    Canvas(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFFE1E1E1))
            .border(1.dp, OutlineSoft, RoundedCornerShape(2.dp)),
    ) {
        drawIntoCanvas { composeCanvas ->
            app.inkloom.engine.v5.android.MaskTileCanvasRenderer.draw(
                snapshot,
                composeCanvas.nativeCanvas,
                android.graphics.RectF(0f, 0f, size.width, size.height),
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = if (mask.enabled) 255 else 96
                },
            )
        }
    }
}

@Composable
private fun LayerPanel(
    page: InkPage,
    activeLayerId: String,
    brushColor: Int,
    dock: WorkspacePanelDock,
    onDock: (WorkspacePanelDock) -> Unit,
    onClose: () -> Unit,
    onColor: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onAddFolder: () -> Unit,
    onRename: (String, String) -> Unit,
    onVisibility: (String) -> Unit,
    onLock: (String) -> Unit,
    onOpacity: (String, Float) -> Unit,
    onBlendMode: (String, LayerBlendMode) -> Unit,
    onToggleAlphaLock: (String) -> Unit,
    onToggleClippedToBelow: (String) -> Unit,
    onToggleReference: (String) -> Unit,
    onToggleDraft: (String) -> Unit,
    onMaskCommand: (String, LayerMaskCommand) -> Unit,
    onMoveToFolder: (String, String?) -> Unit,
    onToggleFolderCollapsed: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDuplicate: (String) -> Unit,
    onMergeDown: (String) -> Unit,
    onDelete: (String) -> Unit,
    panelMode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    panelLocked: Boolean = false,
    onPanelMode: (WorkspacePanelMode) -> Unit = {},
    onPanelLocked: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier.width(240.dp).fillMaxHeight().background(Color(0xFFF4F4F4)).border(1.dp, OutlineSoft).padding(2.dp),
    onPanelDragStart: () -> Unit = {},
    onPanelDrag: (Float, Float) -> Unit = { _, _ -> },
    onPanelDragEnd: () -> Unit = {},
    onPanelDragCancel: () -> Unit = {},
) {
    if (panelMode != WorkspacePanelMode.EXPANDED) {
        val compactModifier = when (dock) {
            WorkspacePanelDock.LEFT, WorkspacePanelDock.RIGHT -> Modifier.width(if (panelMode == WorkspacePanelMode.ICON_ONLY) 56.dp else 176.dp).fillMaxHeight()
            WorkspacePanelDock.FREE -> Modifier.fillMaxSize()
            WorkspacePanelDock.BOTTOM -> modifier
        }
        CompactWorkspacePanel(
            tr("Layers", "图层"), Icons.Outlined.Layers, dock, panelMode, panelLocked,
            onPanelMode, onPanelLocked, onDock, onClose,
            tr("Change layers panel docking", "更改图层面板停靠方式"), compactModifier,
            onPanelDragStart, onPanelDrag, onPanelDragEnd, onPanelDragCancel,
        )
        return
    }
    if (dock == WorkspacePanelDock.FREE) {
        ProcreateLayerPanel(
            page = page,
            activeLayerId = activeLayerId,
            dock = dock,
            onDock = onDock,
            panelMode = panelMode,
            panelLocked = panelLocked,
            onPanelMode = onPanelMode,
            onPanelLocked = onPanelLocked,
            onClose = onClose,
            onSelect = onSelect,
            onAdd = onAdd,
            onAddFolder = onAddFolder,
            onRename = onRename,
            onVisibility = onVisibility,
            onLock = onLock,
            onOpacity = onOpacity,
            onBlendMode = onBlendMode,
            onToggleAlphaLock = onToggleAlphaLock,
            onToggleClippedToBelow = onToggleClippedToBelow,
            onToggleReference = onToggleReference,
            onToggleDraft = onToggleDraft,
            onMaskCommand = onMaskCommand,
            onMove = onMove,
            onDuplicate = onDuplicate,
            onMergeDown = onMergeDown,
            onDelete = onDelete,
            modifier = modifier,
            onPanelDragStart = onPanelDragStart,
            onPanelDrag = onPanelDrag,
            onPanelDragEnd = onPanelDragEnd,
            onPanelDragCancel = onPanelDragCancel,
        )
        return
    }
    var renameLayer by remember { mutableStateOf<InkLayer?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var blendModeMenu by remember { mutableStateOf(false) }
    var layerSearch by rememberSaveable { mutableStateOf("") }
    var selectedLayerIds by remember { mutableStateOf(emptySet<String>()) }
    val filteredLayers = LayerHierarchy.panelOrder(page.layers).filter { layerSearch.isBlank() || it.name.contains(layerSearch.trim(), ignoreCase = true) }
    LaunchedEffect(page.layers.map(InkLayer::id)) { selectedLayerIds = selectedLayerIds.intersect(page.layers.mapTo(mutableSetOf(), InkLayer::id)) }
    val dragDescription = tr("Drag layers panel", "拖动图层面板")
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    Column(modifier) {
        CspColorPanel(
            color = brushColor,
            onColorPreview = onColor,
            onColorCommit = onColor,
            initiallyExpanded = dock != WorkspacePanelDock.BOTTOM,
        )
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth().height(27.dp).background(Color(0xFFE3E3E3)).border(1.dp, OutlineSoft), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Menu, null, Modifier.size(19.dp).padding(3.dp), tint = MutedInk)
            Column(
                Modifier.weight(1f).then(
                    if (!panelLocked) Modifier.semantics { contentDescription = dragDescription }.pointerInput(dock) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var last = down.position
                            var moved = false
                            currentPanelDragStart()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val amount = change.position - last
                                if (amount != Offset.Zero) {
                                    moved = true
                                    change.consume()
                                    currentPanelDrag(amount.x, amount.y)
                                    last = change.position
                                }
                            }
                            if (moved) currentPanelDragEnd() else currentPanelDragCancel()
                        }
                    } else Modifier,
                ),
            ) {
                Text(tr("Layers", "图层"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            WorkspacePanelDockMenu(dock, onDock, tr("Change layers panel docking", "更改图层面板停靠方式"), Modifier.size(24.dp), panelMode, panelLocked, onPanelMode, onPanelLocked)
            IconButton(onClick = onAddFolder, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.CreateNewFolder, tr("Add layer folder", "添加图层文件夹"), Modifier.size(14.dp)) }
            IconButton(onClick = onAdd, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, tr("Add layer", "添加图层"), Modifier.size(14.dp)) }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Close, tr("Close layers panel", "关闭图层面板"), Modifier.size(14.dp)) }
        }
        Spacer(Modifier.height(2.dp))
        page.layers.firstOrNull { it.id == activeLayerId }?.takeIf { it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) }?.let { activeLayer ->
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Opacity", "不透明度"), Modifier.width(48.dp), color = MutedInk, fontSize = 9.sp)
                Slider(activeLayer.opacity, { onOpacity(activeLayer.id, it) }, Modifier.weight(1f).height(26.dp), valueRange = 0f..1f)
                Text("${(activeLayer.opacity * 100f).toInt()}%", Modifier.width(34.dp), color = MutedInk, fontSize = 9.sp, textAlign = TextAlign.End)
            }
            Box {
                Row(
                    Modifier.fillMaxWidth().height(27.dp).border(1.dp, OutlineSoft).clickable { blendModeMenu = true }.padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("Blend", "混合"), Modifier.width(48.dp), color = MutedInk, fontSize = 9.sp)
                    Text(layerBlendModeLabel(activeLayer.blendMode), Modifier.weight(1f), fontSize = 10.sp)
                    Icon(Icons.Outlined.ExpandMore, tr("Choose blend mode", "选择混合模式"), Modifier.size(16.dp))
                }
                DropdownMenu(blendModeMenu, { blendModeMenu = false }) {
                    LayerBlendMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(layerBlendModeLabel(mode)) },
                            trailingIcon = { if (mode == activeLayer.blendMode) Icon(Icons.Outlined.Check, null) },
                            onClick = { blendModeMenu = false; onBlendMode(activeLayer.id, mode) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        OutlinedTextField(layerSearch, { layerSearch = it }, Modifier.fillMaxWidth().height(40.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 10.sp), placeholder = { Text(tr("Search layers", "搜索图层"), fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(15.dp)) })
        if (selectedLayerIds.isNotEmpty()) Row(Modifier.fillMaxWidth().height(34.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
            Text("${selectedLayerIds.size} ${tr("selected", "项已选")}", Modifier.weight(1f).padding(start = 6.dp), fontSize = 9.sp)
            IconButton({ selectedLayerIds.forEach(onVisibility) }, Modifier.size(28.dp)) { Icon(Icons.Outlined.Visibility, tr("Toggle selected layer visibility", "切换所选图层可见性"), Modifier.size(14.dp)) }
            IconButton({ selectedLayerIds.forEach(onLock) }, Modifier.size(28.dp)) { Icon(Icons.Outlined.Lock, tr("Toggle selected layer lock", "切换所选图层锁定"), Modifier.size(14.dp)) }
            IconButton({ selectedLayerIds.mapNotNull { id -> page.layers.firstOrNull { it.id == id } }.filter { it.role in setOf(LayerRole.CUSTOM, LayerRole.FOLDER) }.forEach { onDelete(it.id) }; selectedLayerIds = emptySet() }, Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, tr("Delete selected custom layers", "删除所选自定义图层"), Modifier.size(14.dp)) }
            IconButton({ selectedLayerIds = emptySet() }, Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, tr("Exit layer selection", "退出图层多选"), Modifier.size(14.dp)) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.border(1.dp, OutlineSoft)) {
            items(filteredLayers, key = { it.id }) { layer ->
                var menu by remember { mutableStateOf(false) }
                var folderMenu by remember { mutableStateOf(false) }
                val effectivelyVisible = LayerHierarchy.effectiveVisible(page.layers, layer)
                val effectivelyLocked = LayerHierarchy.effectiveLocked(page.layers, layer)
                val editable = effectivelyVisible && !effectivelyLocked && layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)
                val depth = LayerHierarchy.depth(page.layers, layer)
                val folderToggleDescription = if (layer.collapsed) tr("Expand folder", "展开文件夹") else tr("Collapse folder", "折叠文件夹")
                val layerActionsDescription = "${tr("Layer actions", "图层操作")} ${layer.name}"
                val folderTargets = LayerHierarchy.availableFolders(page.layers, layer.id)
                val folderMenuDescription = "${tr("Move layer to folder", "移动图层到文件夹")} ${layer.name}"
                Card(
                    modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp).combinedClickable(
                        onClick = {
                            if (selectedLayerIds.isNotEmpty()) selectedLayerIds = if (layer.id in selectedLayerIds) selectedLayerIds - layer.id else selectedLayerIds + layer.id
                            else if (editable) onSelect(layer.id)
                        },
                        onLongClick = { selectedLayerIds = if (layer.id in selectedLayerIds) selectedLayerIds - layer.id else selectedLayerIds + layer.id },
                    ),
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = if (layer.id == activeLayerId || layer.id in selectedLayerIds) Color(0xFFD6DCE8) else Color(0xFFF4F4F4)),
                    border = CardDefaults.outlinedCardBorder().copy(width = 1.dp, brush = androidx.compose.ui.graphics.SolidColor(OutlineSoft)),
                ) {
                    Row(Modifier.height(39.dp).padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val visibilityDescription = if (layer.visible) tr("Hide ${layer.name}", "隐藏 ${layer.name}") else tr("Show ${layer.name}", "显示 ${layer.name}")
                        Box(Modifier.size(28.dp).clickable { onVisibility(layer.id) }.semantics { contentDescription = visibilityDescription }, contentAlignment = Alignment.Center) {
                            Icon(if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null, Modifier.size(16.dp))
                        }
                        if (layer.role == LayerRole.FOLDER) Box(
                            Modifier.size(22.dp).clickable { onToggleFolderCollapsed(layer.id) }.semantics {
                                contentDescription = folderToggleDescription
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (layer.collapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, Modifier.size(18.dp), tint = if (effectivelyVisible) Ink else MutedInk)
                        } else Icon(
                            when (layer.role) { LayerRole.BACKGROUND -> Icons.Outlined.Image; LayerRole.HIGHLIGHT -> Icons.Outlined.BorderColor; LayerRole.OBJECTS -> Icons.Outlined.Widgets; else -> Icons.Outlined.Layers },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (effectivelyVisible) Ink else MutedInk,
                        )
                        Spacer(Modifier.width(5.dp))
                        Column(Modifier.weight(1f)) {
                            Text(layer.name, maxLines = 1, color = if (effectivelyVisible) Ink else MutedInk, fontSize = 11.sp)
                            Text(
                                when (layer.role) {
                                    LayerRole.BACKGROUND -> tr("Background", "背景")
                                    LayerRole.FOLDER -> tr("Folder", "文件夹")
                                    else -> tr("Paint layer", "绘画图层")
                                },
                                fontSize = 8.sp,
                                color = MutedInk,
                            )
                        }
                        if (layer.locked) Icon(Icons.Outlined.Lock, tr("Locked", "已锁定"), Modifier.size(14.dp), tint = MutedInk)
                        if (layer.reference) Icon(Icons.Outlined.Lightbulb, tr("Reference layer", "参考图层"), Modifier.size(14.dp), tint = MutedInk)
                        if (layer.draft) Icon(Icons.Outlined.EditNote, tr("Draft layer", "草稿图层"), Modifier.size(14.dp), tint = MutedInk)
                        layer.mask?.let { mask -> LayerMaskThumbnailView(mask, Modifier.size(18.dp)) }
                        if (folderTargets.isNotEmpty() || layer.parentId != null) Box {
                            Box(
                                Modifier.size(28.dp).clickable {
                                    if (layer.parentId == null && folderTargets.size == 1) onMoveToFolder(layer.id, folderTargets.single().id)
                                    else folderMenu = true
                                }.semantics { contentDescription = folderMenuDescription },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.DriveFileMove, null, Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                                if (layer.parentId != null) DropdownMenuItem(text = { Text(tr("Move out of folder", "移出文件夹")) }, onClick = { folderMenu = false; onMoveToFolder(layer.id, null) })
                                folderTargets.forEach { folder -> DropdownMenuItem(text = { Text("${tr("Move into", "移入")} ${folder.name}") }, onClick = { folderMenu = false; onMoveToFolder(layer.id, folder.id) }) }
                            }
                        }
                        Box {
                            Box(Modifier.size(28.dp).clickable { menu = true }.semantics { contentDescription = layerActionsDescription }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MoreVert, tr("Layer actions", "图层操作"), Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(if (layer.visible) tr("Hide layer", "隐藏图层") else tr("Show layer", "显示图层")) }, leadingIcon = { Icon(if (layer.visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) }, onClick = { menu = false; onVisibility(layer.id) })
                                DropdownMenuItem(text = { Text(if (layer.locked) tr("Unlock layer", "解锁图层") else tr("Lock layer", "锁定图层")) }, leadingIcon = { Icon(if (layer.locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null) }, onClick = { menu = false; onLock(layer.id) })
                                if (layer.role == LayerRole.FOLDER) DropdownMenuItem(text = { Text(if (layer.collapsed) tr("Expand folder", "展开文件夹") else tr("Collapse folder", "折叠文件夹")) }, onClick = { menu = false; onToggleFolderCollapsed(layer.id) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(if (layer.alphaLock) tr("Unlock alpha", "解锁透明度") else tr("Lock alpha", "锁定透明度")) }, leadingIcon = { Icon(if (layer.alphaLock) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null) }, onClick = { menu = false; onToggleAlphaLock(layer.id) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(if (layer.clippedToBelow) tr("Stop clipping", "取消剪贴") else tr("Clipping mask", "剪贴蒙版")) }, leadingIcon = { Icon(if (layer.clippedToBelow) Icons.Outlined.LinkOff else Icons.Outlined.Link, null) }, onClick = { menu = false; onToggleClippedToBelow(layer.id) })
                                if (layer.role != LayerRole.BACKGROUND) DropdownMenuItem(text = { Text(if (layer.reference) tr("Unset reference layer", "取消参考图层") else tr("Set as reference layer", "设为参考图层")) }, leadingIcon = { Icon(Icons.Outlined.Lightbulb, null) }, onClick = { menu = false; onToggleReference(layer.id) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(if (layer.draft) tr("Unset draft layer", "取消草稿图层") else tr("Set as draft layer", "设为草稿图层")) }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) }, onClick = { menu = false; onToggleDraft(layer.id) })
                                LayerMaskMenuItems(layer) { command -> menu = false; onMaskCommand(layer.id, command) }
                                DropdownMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { menu = false; renameLayer = layer; renameValue = layer.name })
                                if (layer.parentId != null) DropdownMenuItem(text = { Text(tr("Move out of folder", "移出文件夹")) }, onClick = { menu = false; onMoveToFolder(layer.id, null) })
                                folderTargets.forEach { folder -> DropdownMenuItem(text = { Text("${tr("Move into", "移入")} ${folder.name}") }, onClick = { menu = false; onMoveToFolder(layer.id, folder.id) }) }
                                DropdownMenuItem(text = { Text(tr("Move up", "上移")) }, enabled = page.layers.last().id != layer.id, onClick = { menu = false; onMove(layer.id, 1) })
                                DropdownMenuItem(text = { Text(tr("Move down", "下移")) }, enabled = page.layers.first().id != layer.id, onClick = { menu = false; onMove(layer.id, -1) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(tr("Duplicate layer", "复制图层")) }, onClick = { menu = false; onDuplicate(layer.id) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(tr("Merge down", "向下合并")) }, enabled = LayerOperations.canMergeDown(page, layer.id), onClick = { menu = false; onMergeDown(layer.id) })
                                if (layer.role in setOf(LayerRole.CUSTOM, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(if (layer.role == LayerRole.FOLDER) tr("Delete folder", "删除文件夹") else tr("Delete layer", "删除图层")) }, onClick = { menu = false; onDelete(layer.id) })
                            }
                        }
                    }
                }
            }
        }
    }
    renameLayer?.let { layer ->
        AlertDialog(
            onDismissRequest = { renameLayer = null },
            title = { Text(tr("Rename layer", "重命名图层")) },
            text = { OutlinedTextField(renameValue, { renameValue = it }, singleLine = true, label = { Text(tr("Layer name", "图层名称")) }) },
            confirmButton = { TextButton(onClick = { onRename(layer.id, renameValue); renameLayer = null }) { Text(tr("Save", "保存")) } },
            dismissButton = { TextButton(onClick = { renameLayer = null }) { Text(tr("Cancel", "取消")) } },
        )
    }
}

@Composable
private fun ProcreateLayerPanel(
    page: InkPage,
    activeLayerId: String,
    dock: WorkspacePanelDock,
    onDock: (WorkspacePanelDock) -> Unit,
    panelMode: WorkspacePanelMode,
    panelLocked: Boolean,
    onPanelMode: (WorkspacePanelMode) -> Unit,
    onPanelLocked: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onAddFolder: () -> Unit,
    onRename: (String, String) -> Unit,
    onVisibility: (String) -> Unit,
    onLock: (String) -> Unit,
    onOpacity: (String, Float) -> Unit,
    onBlendMode: (String, LayerBlendMode) -> Unit,
    onToggleAlphaLock: (String) -> Unit,
    onToggleClippedToBelow: (String) -> Unit,
    onToggleReference: (String) -> Unit,
    onToggleDraft: (String) -> Unit,
    onMaskCommand: (String, LayerMaskCommand) -> Unit,
    onMove: (String, Int) -> Unit,
    onDuplicate: (String) -> Unit,
    onMergeDown: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier,
    onPanelDragStart: () -> Unit,
    onPanelDrag: (Float, Float) -> Unit,
    onPanelDragEnd: () -> Unit,
    onPanelDragCancel: () -> Unit,
) {
    var renameLayer by remember { mutableStateOf<InkLayer?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var blendMenu by remember { mutableStateOf(false) }
    var layerSearch by rememberSaveable { mutableStateOf("") }
    var selectedLayerIds by remember { mutableStateOf(emptySet<String>()) }
    val activeLayer = page.layers.firstOrNull { it.id == activeLayerId }
    val filteredLayers = LayerHierarchy.panelOrder(page.layers).filter { layerSearch.isBlank() || it.name.contains(layerSearch.trim(), ignoreCase = true) }
    LaunchedEffect(page.layers.map(InkLayer::id)) {
        selectedLayerIds = selectedLayerIds.intersect(page.layers.mapTo(mutableSetOf(), InkLayer::id))
    }
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    val dragDescription = tr("Drag layers panel", "拖动图层面板")
    val shape = RoundedCornerShape(14.dp)
    Surface(modifier.shadow(12.dp, shape), shape = shape, color = Color.White, contentColor = Ink, border = CardDefaults.outlinedCardBorder()) {
        Column {
            Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 15.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Layers", "图层"),
                    Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically).semantics { contentDescription = dragDescription }.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var last = down.position
                            var moved = false
                            currentPanelDragStart()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val amount = change.position - last
                                if (amount != Offset.Zero) {
                                    moved = true
                                    change.consume()
                                    currentPanelDrag(amount.x, amount.y)
                                    last = change.position
                                }
                            }
                            if (moved) currentPanelDragEnd() else currentPanelDragCancel()
                        }
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                WorkspacePanelDockMenu(dock, onDock, tr("Change layers panel docking", "更改图层面板停靠方式"), Modifier.size(36.dp), panelMode, panelLocked, onPanelMode, onPanelLocked)
                IconButton(onClick = onAddFolder, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.CreateNewFolder, tr("Add layer group", "添加图层组"), tint = Ink) }
                IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Add, tr("Add layer", "添加图层"), tint = Ink) }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, tr("Close layers panel", "关闭图层面板"), tint = Ink) }
            }
            HorizontalDivider(color = OutlineSoft)
            OutlinedTextField(
                value = layerSearch,
                onValueChange = { layerSearch = it },
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp, vertical = 3.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                placeholder = { Text(tr("Search layers", "搜索图层"), fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(17.dp)) },
                trailingIcon = { if (layerSearch.isNotEmpty()) IconButton({ layerSearch = "" }) { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) } },
            )
            if (selectedLayerIds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(38.dp).background(Color(0xFFF1F1F1)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedLayerIds.size} ${tr("selected", "项已选")}", Modifier.weight(1f), fontSize = 10.sp)
                    IconButton({ selectedLayerIds.forEach(onVisibility) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Visibility, tr("Toggle selected layer visibility", "切换所选图层可见性"), Modifier.size(17.dp)) }
                    IconButton({ selectedLayerIds.forEach(onLock) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Lock, tr("Toggle selected layer lock", "切换所选图层锁定"), Modifier.size(17.dp)) }
                    IconButton({ selectedLayerIds.mapNotNull { id -> page.layers.firstOrNull { it.id == id } }.filter { it.role in setOf(LayerRole.CUSTOM, LayerRole.FOLDER) }.forEach { onDelete(it.id) }; selectedLayerIds = emptySet() }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, tr("Delete selected custom layers", "删除所选自定义图层"), Modifier.size(17.dp)) }
                    IconButton({ selectedLayerIds = emptySet() }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, tr("Exit layer selection", "退出图层多选"), Modifier.size(17.dp)) }
                }
            }
            activeLayer?.takeIf { it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) }?.let { layer ->
                Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Opacity", "不透明度"), Modifier.width(58.dp), color = MutedInk, fontSize = 10.sp)
                    Slider(layer.opacity, { onOpacity(layer.id, it) }, Modifier.weight(1f), valueRange = 0f..1f)
                    Text("${(layer.opacity * 100).toInt()}%", Modifier.width(38.dp), color = MutedInk, fontSize = 9.sp, textAlign = TextAlign.End)
                }
                Box {
                    Row(Modifier.fillMaxWidth().height(34.dp).clickable { blendMenu = true }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Blend mode", "混合模式"), Modifier.weight(1f), color = MutedInk, fontSize = 10.sp)
                        Text(layerBlendModeLabel(layer.blendMode), fontSize = 10.sp)
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = Ink)
                    }
                    DropdownMenu(blendMenu, { blendMenu = false }) {
                        LayerBlendMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(layerBlendModeLabel(mode)) }, onClick = { blendMenu = false; onBlendMode(layer.id, mode) }) }
                    }
                }
                HorizontalDivider(color = OutlineSoft)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filteredLayers, key = { it.id }) { layer ->
                    var menu by remember { mutableStateOf(false) }
                    val selected = layer.id == activeLayerId || layer.id in selectedLayerIds
                    val depth = LayerHierarchy.depth(page.layers, layer)
                    val canSelect = layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) && !LayerHierarchy.effectiveLocked(page.layers, layer)
                    Row(
                        Modifier.fillMaxWidth().padding(start = (8 + depth * 14).dp, end = 8.dp).height(58.dp).clip(RoundedCornerShape(7.dp)).background(if (selected) Color(0xFFE1E1E1) else Color.White).border(1.dp, if (selected) Ink else OutlineSoft, RoundedCornerShape(7.dp)).combinedClickable(
                            onClick = {
                                if (selectedLayerIds.isNotEmpty()) selectedLayerIds = if (layer.id in selectedLayerIds) selectedLayerIds - layer.id else selectedLayerIds + layer.id
                                else if (canSelect) onSelect(layer.id)
                            },
                            onLongClick = { selectedLayerIds = if (layer.id in selectedLayerIds) selectedLayerIds - layer.id else selectedLayerIds + layer.id },
                        ).padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { onVisibility(layer.id) }, modifier = Modifier.size(30.dp)) { Icon(if (layer.visible) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, tr("Toggle layer visibility", "切换图层可见性"), Modifier.size(18.dp), tint = Ink) }
                        Surface(Modifier.size(42.dp), shape = RoundedCornerShape(5.dp), color = Color(0xFFF4F4F4)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(when (layer.role) { LayerRole.BACKGROUND -> Icons.Outlined.Image; LayerRole.HIGHLIGHT -> Icons.Outlined.BorderColor; LayerRole.OBJECTS -> Icons.Outlined.Widgets; LayerRole.FOLDER -> Icons.Outlined.Folder; else -> Icons.Outlined.Layers }, null, Modifier.size(22.dp), tint = Ink)
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(layer.name, maxLines = 1, fontSize = 11.sp, color = Ink)
                            Text(
                                when {
                                    layer.role == LayerRole.FOLDER -> tr("Group", "组")
                                    layer.role == LayerRole.BACKGROUND -> tr("Background", "背景")
                                    else -> tr("Paint · ", "绘画 · ") + layer.blendMode.name.first()
                                },
                                fontSize = 9.sp,
                                color = MutedInk,
                            )
                        }
                        if (layer.locked) Icon(Icons.Outlined.Lock, tr("Locked", "已锁定"), Modifier.size(15.dp), tint = MutedInk)
                        if (layer.reference) Icon(Icons.Outlined.Lightbulb, tr("Reference layer", "参考图层"), Modifier.size(15.dp), tint = MutedInk)
                        if (layer.draft) Icon(Icons.Outlined.EditNote, tr("Draft layer", "草稿图层"), Modifier.size(15.dp), tint = MutedInk)
                        layer.mask?.let { mask -> LayerMaskThumbnailView(mask, Modifier.size(24.dp)) }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, tr("Layer actions", "图层操作"), tint = Ink) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(if (layer.locked) tr("Unlock", "解锁") else tr("Lock", "锁定")) }, onClick = { menu = false; onLock(layer.id) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(if (layer.alphaLock) tr("Unlock alpha", "解锁透明度") else tr("Lock alpha", "锁定透明度")) }, onClick = { menu = false; onToggleAlphaLock(layer.id) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(if (layer.clippedToBelow) tr("Stop clipping", "取消剪贴") else tr("Clipping mask", "剪贴蒙版")) }, onClick = { menu = false; onToggleClippedToBelow(layer.id) })
                                if (layer.role != LayerRole.BACKGROUND) DropdownMenuItem(text = { Text(if (layer.reference) tr("Unset reference layer", "取消参考图层") else tr("Set as reference layer", "设为参考图层")) }, onClick = { menu = false; onToggleReference(layer.id) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(if (layer.draft) tr("Unset draft layer", "取消草稿图层") else tr("Set as draft layer", "设为草稿图层")) }, onClick = { menu = false; onToggleDraft(layer.id) })
                                LayerMaskMenuItems(layer) { command -> menu = false; onMaskCommand(layer.id, command) }
                                DropdownMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { menu = false; renameLayer = layer; renameValue = layer.name })
                                DropdownMenuItem(text = { Text(tr("Move up", "上移")) }, onClick = { menu = false; onMove(layer.id, 1) })
                                DropdownMenuItem(text = { Text(tr("Move down", "下移")) }, onClick = { menu = false; onMove(layer.id, -1) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(tr("Duplicate", "复制")) }, onClick = { menu = false; onDuplicate(layer.id) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(tr("Merge down", "向下合并")) }, enabled = LayerOperations.canMergeDown(page, layer.id), onClick = { menu = false; onMergeDown(layer.id) })
                                if (layer.role in setOf(LayerRole.CUSTOM, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(tr("Delete", "删除")) }, onClick = { menu = false; onDelete(layer.id) })
                            }
                        }
                    }
                }
            }
        }
    }
    renameLayer?.let { layer -> AlertDialog(
        onDismissRequest = { renameLayer = null },
        title = { Text(tr("Rename layer", "重命名图层")) },
        text = { OutlinedTextField(renameValue, { renameValue = it }, label = { Text(tr("Layer name", "图层名称")) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(layer.id, renameValue); renameLayer = null }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = { renameLayer = null }) { Text(tr("Cancel", "取消")) } },
    ) }
}

@Composable
private fun BrushPanel(
    brushes: List<ImportedBrush>,
    usage: BrushUsageState,
    onUsage: (BrushUsageState) -> Unit,
    registeredGroups: List<String>,
    selectedBrushId: String?,
    requestedGroup: String?,
    settings: AppSettings,
    dock: WorkspacePanelDock,
    onDock: (WorkspacePanelDock) -> Unit,
    onClose: () -> Unit,
    onBrushSelected: (ImportedBrush) -> Unit,
    onImportBrushes: (String?) -> Unit,
    onExportBrush: (ImportedBrush) -> Unit,
    onQuickBrushCommit: (ImportedBrush) -> Unit,
    onUpdateBrush: (ImportedBrush) -> Unit,
    onReplaceBrushes: (List<ImportedBrush>) -> Unit,
    onReplaceGroups: (List<String>) -> Unit,
    onDeleteBrush: (String) -> Unit,
    onReorderBrush: (String, Int) -> Unit,
    panelMode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    panelLocked: Boolean = false,
    onPanelMode: (WorkspacePanelMode) -> Unit = {},
    onPanelLocked: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onPanelDragStart: () -> Unit = {},
    onPanelDrag: (Float, Float) -> Unit = { _, _ -> },
    onPanelDragEnd: () -> Unit = {},
    onPanelDragCancel: () -> Unit = {},
) {
    if (panelMode != WorkspacePanelMode.EXPANDED) {
        val compactModifier = when (dock) {
            WorkspacePanelDock.LEFT, WorkspacePanelDock.RIGHT -> Modifier.width(if (panelMode == WorkspacePanelMode.ICON_ONLY) 56.dp else 176.dp).fillMaxHeight()
            WorkspacePanelDock.FREE -> Modifier.fillMaxSize()
            WorkspacePanelDock.BOTTOM -> modifier
        }
        CompactWorkspacePanel(
            tr("Brush Library", "笔刷库"), Icons.Outlined.Brush, dock, panelMode, panelLocked,
            onPanelMode, onPanelLocked, onDock, onClose,
            tr("Change brushes panel docking", "更改笔刷面板停靠方式"), compactModifier,
            onPanelDragStart, onPanelDrag, onPanelDragEnd, onPanelDragCancel,
        )
        return
    }
    if (dock == WorkspacePanelDock.FREE) {
        ProcreateBrushLibrary(
            brushes = brushes,
            usage = usage,
            onUsage = onUsage,
            registeredGroups = registeredGroups,
            selectedBrushId = selectedBrushId,
            requestedGroup = requestedGroup,
            settings = settings,
            dock = dock,
            onDock = onDock,
            panelMode = panelMode,
            panelLocked = panelLocked,
            onPanelMode = onPanelMode,
            onPanelLocked = onPanelLocked,
            onClose = onClose,
            onBrushSelected = onBrushSelected,
            onImportBrushes = onImportBrushes,
            onExportBrush = onExportBrush,
            onQuickBrushCommit = onQuickBrushCommit,
            onUpdateBrush = onUpdateBrush,
            onReplaceBrushes = onReplaceBrushes,
            onReplaceGroups = onReplaceGroups,
            onDeleteBrush = onDeleteBrush,
            onReorderBrush = onReorderBrush,
            modifier = modifier,
            onPanelDragStart = onPanelDragStart,
            onPanelDrag = onPanelDrag,
            onPanelDragEnd = onPanelDragEnd,
            onPanelDragCancel = onPanelDragCancel,
        )
        return
    }
    val context = LocalContext.current
    val historyStore = remember { BrushVersionHistoryStore(context) }
    var reportBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var editBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var creatingBrushId by remember { mutableStateOf<String?>(null) }
    var historyBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var moveBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var manageGroups by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var format by remember { mutableStateOf<BrushFormat?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var requestedLayout by rememberSaveable { mutableStateOf(BrushLibraryLayout.AUTO) }
    var sortMode by rememberSaveable { mutableStateOf(BrushLibrarySort.CUSTOM) }
    var sortMenu by remember { mutableStateOf(false) }
    var multiSelecting by remember { mutableStateOf(false) }
    var selectedBrushIds by remember { mutableStateOf(emptySet<String>()) }
    val brushListState = rememberLazyListState()
    val brushGridState = rememberLazyGridState()
    var batchGroupOperation by remember { mutableStateOf<BrushBatchGroupOperation?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var collapsedGroupPaths by remember { mutableStateOf(emptySet<String>()) }
    val copiedBrushSuffix = tr("copy", "副本")
    val systemBrushes = remember { systemBrushCatalog() }
    val libraryBrushes = remember(brushes, systemBrushes) { BrushLibraryInventory.selectable(systemBrushes, brushes) }
    val inventorySummary = remember(brushes, systemBrushes) { BrushLibraryInventory.summarize(systemBrushes, brushes) }
    val systemGroups = remember(systemBrushes) { BrushGroupOperations.names(systemBrushes) }
    val groups = BrushGroupOperations.names(libraryBrushes)
    val groupTree = BrushGroupOperations.treePaths(libraryBrushes, registeredGroups + systemGroups)
    val currentBrushIds = brushes.mapTo(linkedSetOf()) { it.id }
    LaunchedEffect(currentBrushIds) {
        selectedBrushIds = selectedBrushIds.intersect(currentBrushIds)
        if (selectedBrushIds.isEmpty() && brushes.isEmpty()) multiSelecting = false
    }
    LaunchedEffect(groupTree, selectedGroup) {
        collapsedGroupPaths = collapsedGroupPaths.filterTo(linkedSetOf()) { collapsed -> groupTree.any { it.equals(collapsed, true) } }
        if (selectedGroup != null && selectedGroup != UNGROUPED_BRUSH_FILTER && groupTree.none { it.equals(selectedGroup, true) }) selectedGroup = null
    }
    val filteredBrushes = libraryBrushes.filter { brush ->
        val normalizedGroup = BrushGroupOperations.normalize(brush.groupName)
        val groupMatches = when (selectedGroup) {
            null -> true
            UNGROUPED_BRUSH_FILTER -> normalizedGroup.isBlank()
            else -> BrushGroupOperations.isWithin(normalizedGroup, selectedGroup.orEmpty())
        }
        (showHidden || !brush.hidden) && (!favoritesOnly || brush.favorite) && groupMatches && (format == null || brush.format == format) && (search.isBlank() || brush.name.contains(search, true) || normalizedGroup.contains(search, true) || brush.tags.any { it.contains(search, true) })
    }
    val visibleBrushes = when (sortMode) {
        BrushLibrarySort.CUSTOM -> filteredBrushes.sortedWith(compareBy<ImportedBrush> { it.sortOrder }.thenByDescending { it.favorite }.thenByDescending { it.lastUsedAt })
        BrushLibrarySort.RECENT -> filteredBrushes.sortedWith(compareByDescending<ImportedBrush> { it.lastUsedAt }.thenBy { it.name.lowercase() })
        BrushLibrarySort.NAME -> filteredBrushes.sortedBy { it.name.lowercase() }
        BrushLibrarySort.SIZE -> filteredBrushes.sortedWith(compareByDescending<ImportedBrush> { it.defaultSize }.thenBy { it.name.lowercase() })
    }
    LaunchedEffect(search, format, favoritesOnly, showHidden, selectedGroup, sortMode, requestedLayout) {
        brushListState.scrollToItem(0)
        brushGridState.scrollToItem(0)
    }
    val groupOrder = groups.mapIndexed { index, group -> group.lowercase() to index }.toMap()
    val brushSections = if (sortMode == BrushLibrarySort.CUSTOM) {
        visibleBrushes.groupBy { BrushGroupOperations.normalize(it.groupName).ifBlank { "Ungrouped" } }
            .toList().sortedBy { (group, _) -> groupOrder[group.lowercase()] ?: Int.MAX_VALUE }
    } else listOf("" to visibleBrushes)
    val dragDescription = tr("Drag brushes panel", "拖动笔刷面板")
    val newBrushName = tr("New brush", "新笔刷")
    val createdInInkloomReport = tr("Created in Inkloom", "在 Inkloom 中创建")
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    val bottomDocked = dock == WorkspacePanelDock.BOTTOM
    val resolvedModifier = when (dock) {
        WorkspacePanelDock.LEFT, WorkspacePanelDock.RIGHT -> modifier.fillMaxHeight()
        WorkspacePanelDock.BOTTOM -> modifier.fillMaxSize()
        WorkspacePanelDock.FREE -> modifier.fillMaxSize()
    }.background(Color(0xFFF4F4F4)).border(1.dp, OutlineSoft).padding(2.dp)
    val visibleGroupPaths = groupTree.filter { path ->
        generateSequence(BrushGroupOperations.parent(path), BrushGroupOperations::parent).none { ancestor -> collapsedGroupPaths.any { it.equals(ancestor, true) } }
    }
    Column(resolvedModifier) {
        Row(Modifier.fillMaxWidth().height(27.dp).background(Color(0xFFE3E3E3)).border(1.dp, OutlineSoft), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Menu, null, Modifier.size(19.dp).padding(3.dp), tint = MutedInk)
            Text(
                tr("Sub tool [Brush]", "子工具 [笔刷]"),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically).then(
                    if (!panelLocked) Modifier.semantics { contentDescription = dragDescription }.pointerInput(dock) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var last = down.position
                            var moved = false
                            currentPanelDragStart()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val amount = change.position - last
                                if (amount != Offset.Zero) {
                                    moved = true
                                    change.consume()
                                    currentPanelDrag(amount.x, amount.y)
                                    last = change.position
                                }
                            }
                            if (moved) currentPanelDragEnd() else currentPanelDragCancel()
                        }
                    } else Modifier,
                ),
            )
            WorkspacePanelDockMenu(dock, onDock, tr("Change brushes panel docking", "更改笔刷面板停靠方式"), Modifier.size(24.dp), panelMode, panelLocked, onPanelMode, onPanelLocked)
            IconButton(onClick = {
                multiSelecting = !multiSelecting
                if (!multiSelecting) selectedBrushIds = emptySet()
            }, modifier = Modifier.size(24.dp)) { Icon(if (multiSelecting) Icons.Outlined.Close else Icons.Outlined.Checklist, if (multiSelecting) tr("Cancel brush selection", "取消选择笔刷") else tr("Select multiple brushes", "批量选择笔刷"), Modifier.size(14.dp)) }
            Box {
                IconButton(onClick = { addMenu = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Add, tr("Add brush or group", "添加笔刷或分组"), Modifier.size(14.dp)) }
                DropdownMenu(addMenu, { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Create blank brush", "新建空白笔刷")) },
                        leadingIcon = { Icon(Icons.Outlined.Brush, null) },
                        onClick = {
                            addMenu = false
                            val created = BrushPresetV5Import.createUserRecord(ImportedBrush(
                                name = newBrushName,
                                format = BrushFormat.INKLOOM,
                                compatibility = Compatibility.COMPLETE,
                                sizeBytes = 0L,
                                report = listOf(createdInInkloomReport),
                                groupName = selectedGroup?.takeUnless { it == UNGROUPED_BRUSH_FILTER }.orEmpty(),
                                sortOrder = (brushes.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                            ))
                            creatingBrushId = created.id
                            editBrush = created
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            val destination = selectedGroup?.takeUnless { it == UNGROUPED_BRUSH_FILTER }
                            Text(if (destination == null) tr("Import brushes", "导入笔刷") else tr("Import into $destination", "导入到 $destination"))
                        },
                        leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                        onClick = { addMenu = false; onImportBrushes(selectedGroup?.takeUnless { it == UNGROUPED_BRUSH_FILTER }) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Manage groups", "管理分组")) },
                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                        onClick = { addMenu = false; manageGroups = true },
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Close, tr("Close brushes panel", "关闭笔刷面板"), Modifier.size(14.dp)) }
        }
        if (multiSelecting) Surface(
            color = Color.White,
            shape = RoundedCornerShape(2.dp),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(34.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selectedBrushIds.size} ${tr("selected", "已选择")}", Modifier.weight(1f), fontSize = 10.sp, color = MutedInk)
                TextButton(onClick = { batchGroupOperation = BrushBatchGroupOperation.MOVE }, enabled = selectedBrushIds.isNotEmpty(), contentPadding = PaddingValues(horizontal = 7.dp)) { Text(tr("Move", "移动"), fontSize = 10.sp) }
                TextButton(onClick = { batchGroupOperation = BrushBatchGroupOperation.COPY }, enabled = selectedBrushIds.isNotEmpty(), contentPadding = PaddingValues(horizontal = 7.dp)) { Text(tr("Copy", "复制"), fontSize = 10.sp) }
            }
        }
        if (!bottomDocked) {
            Text(tr("Brush sets · compact view", "笔刷组 · 紧凑视图"), fontSize = 11.sp, color = MutedInk)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            search,
            { search = it },
            leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(15.dp)) },
            placeholder = { Text(tr("Search brushes", "搜索笔刷"), fontSize = 10.sp) },
            textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(38.dp),
        )
        if (bottomDocked) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 3.dp)) {
                item("all") { FilterChip(selected = selectedGroup == null, onClick = { selectedGroup = null }, label = { Text(tr("All", "全部"), fontSize = 10.sp) }) }
                item("ungrouped") { FilterChip(selected = selectedGroup == UNGROUPED_BRUSH_FILTER, onClick = { selectedGroup = UNGROUPED_BRUSH_FILTER }, label = { Text(tr("Ungrouped", "未分组"), fontSize = 10.sp) }) }
                items(groupTree, key = { "bottom:$it" }) { path ->
                    FilterChip(selected = selectedGroup?.equals(path, true) == true, onClick = { selectedGroup = path }, label = { Text(BrushGroupOperations.displayName(path), fontSize = 10.sp, maxLines = 1) })
                }
                item("favorites") { FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text(tr("Favorites", "收藏"), fontSize = 10.sp) }) }
                item("manage") { IconButton(onClick = { manageGroups = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.DriveFileRenameOutline, tr("Manage brush groups", "管理笔刷分组"), Modifier.size(17.dp)) } }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                item { FilterChip(selected = format == null, onClick = { format = null }, label = { Text(tr("All", "全部")) }) }
                BrushFormat.entries.filter { it != BrushFormat.UNKNOWN }.forEach { source -> item { FilterChip(selected = format == source, onClick = { format = source }, label = { Text(source.label) }) } }
                item { FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text(tr("Favorites", "收藏")) }, leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(16.dp)) }) }
                item { FilterChip(selected = showHidden, onClick = { showHidden = !showHidden }, label = { Text(tr("Hidden", "隐藏")) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("GROUPS", "分组"), fontSize = 10.sp, color = MutedInk, modifier = Modifier.weight(1f))
                IconButton(onClick = { manageGroups = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.DriveFileRenameOutline, tr("Manage brush groups", "管理笔刷分组"), Modifier.size(17.dp)) }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(0.dp), border = CardDefaults.outlinedCardBorder(), modifier = Modifier.fillMaxWidth().heightIn(max = 148.dp).padding(bottom = 2.dp)) {
                LazyColumn(contentPadding = PaddingValues(vertical = 3.dp)) {
                item("all") {
                    Row(Modifier.fillMaxWidth().clickable { selectedGroup = null }.background(if (selectedGroup == null) SageContainer else Color.Transparent).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AllInclusive, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text(tr("All brushes", "全部笔刷"), fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(libraryBrushes.size.toString(), color = MutedInk, fontSize = 10.sp)
                    }
                }
                item("ungrouped") {
                    Row(Modifier.fillMaxWidth().clickable { selectedGroup = UNGROUPED_BRUSH_FILTER }.background(if (selectedGroup == UNGROUPED_BRUSH_FILTER) SageContainer else Color.Transparent).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FolderOff, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text(tr("Ungrouped", "未分组"), fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(libraryBrushes.count { BrushGroupOperations.normalize(it.groupName).isBlank() }.toString(), color = MutedInk, fontSize = 10.sp)
                    }
                }
                items(visibleGroupPaths, key = { "tree:$it" }) { path ->
                    val hasChildren = groupTree.any { candidate -> BrushGroupOperations.parent(candidate)?.equals(path, true) == true }
                    val collapsed = collapsedGroupPaths.any { it.equals(path, true) }
                    val selected = selectedGroup?.equals(path, true) == true
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedGroup = path }.background(if (selected) SageContainer else Color.Transparent).padding(start = (6 + BrushGroupOperations.depth(path) * 16).dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasChildren) IconButton(onClick = { collapsedGroupPaths = if (collapsed) collapsedGroupPaths - path else collapsedGroupPaths + path }, modifier = Modifier.size(22.dp)) {
                            Icon(if (collapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore, "${if (collapsed) tr("Expand group", "展开分组") else tr("Collapse group", "折叠分组")} $path", Modifier.size(16.dp))
                        } else Spacer(Modifier.width(22.dp))
                        Icon(if (hasChildren) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(BrushGroupOperations.displayName(path), maxLines = 1, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(libraryBrushes.count { BrushGroupOperations.isWithin(it.groupName, path) }.toString(), color = MutedInk, fontSize = 10.sp)
                    }
                }
                }
            }
        }
        if (multiSelecting) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(9.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    val visibleIds = visibleBrushes.filterNot { it.format == BrushFormat.SYSTEM }.mapTo(linkedSetOf()) { it.id }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(top = 4.dp)) {
                        item {
                            AssistChip(
                                onClick = {
                                    selectedBrushIds = if (visibleIds.isNotEmpty() && selectedBrushIds.containsAll(visibleIds)) selectedBrushIds - visibleIds else selectedBrushIds + visibleIds
                                },
                                label = { Text(if (visibleIds.isNotEmpty() && selectedBrushIds.containsAll(visibleIds)) tr("Clear visible", "取消当前列表") else tr("Select visible", "选择当前列表"), fontSize = 10.sp) },
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    onReplaceBrushes(BrushGroupOperations.setFavorite(brushes, selectedBrushIds, true))
                                    selectedBrushIds = emptySet(); multiSelecting = false
                                },
                                enabled = selectedBrushIds.isNotEmpty(),
                                label = { Text(tr("Favorite", "收藏"), fontSize = 10.sp) },
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    onReplaceBrushes(BrushGroupOperations.setHidden(brushes, selectedBrushIds, true))
                                    selectedBrushIds = emptySet(); multiSelecting = false
                                },
                                enabled = selectedBrushIds.isNotEmpty(),
                                label = { Text(tr("Hide", "隐藏"), fontSize = 10.sp) },
                            )
                        }
                        item { AssistChip(onClick = { confirmBatchDelete = true }, enabled = selectedBrushIds.isNotEmpty(), label = { Text(tr("Remove", "移除"), fontSize = 10.sp) }) }
                    }
                }
            }
        }
        if (libraryBrushes.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${tr("BRUSHES", "笔刷")} (${visibleBrushes.size}/${inventorySummary.total} · ${tr("imported", "已导入")} ${inventorySummary.imported})",
                    fontSize = 11.sp,
                    color = MutedInk,
                    modifier = Modifier.weight(1f),
                )
                listOf(
                    BrushLibraryLayout.AUTO to (Icons.Outlined.AutoAwesome to tr("Automatic layout", "自动布局")),
                    BrushLibraryLayout.COMPACT to (Icons.Outlined.DensitySmall to tr("Compact layout", "紧凑布局")),
                    BrushLibraryLayout.LIST to (Icons.Outlined.ViewList to tr("List layout", "列表布局")),
                    BrushLibraryLayout.GRID to (Icons.Outlined.GridView to tr("Grid layout", "网格布局")),
                ).forEach { (layout, iconAndLabel) ->
                    IconButton(
                        onClick = { requestedLayout = layout },
                        modifier = Modifier.size(30.dp).background(if (requestedLayout == layout) Color(0xFFE0E0E0) else Color.Transparent, RoundedCornerShape(4.dp)),
                    ) { Icon(iconAndLabel.first, iconAndLabel.second, Modifier.size(16.dp)) }
                }
                Box {
                    IconButton(onClick = { sortMenu = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.Sort, tr("Sort brushes", "笔刷排序"), Modifier.size(17.dp)) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        listOf(
                            BrushLibrarySort.CUSTOM to tr("Custom order", "自定义顺序"),
                            BrushLibrarySort.RECENT to tr("Recently used", "最近使用"),
                            BrushLibrarySort.NAME to tr("Name", "名称"),
                            BrushLibrarySort.SIZE to tr("Default size", "默认尺寸"),
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { if (sortMode == mode) Icon(Icons.Outlined.Check, null) },
                                onClick = { sortMode = mode; sortMenu = false },
                            )
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().border(1.dp, OutlineSoft)) {
                val effectiveLayout = AdaptivePanelLayout.brushLayout(requestedLayout, maxWidth.value, bottomDocked)
                when (effectiveLayout) {
                    BrushLibraryLayout.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (maxWidth < 330.dp) 104.dp else 128.dp),
                        state = brushGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        items(items = visibleBrushes, key = { it.id }) { brush ->
                            ImportedBrushTile(
                                brush = brush,
                                editable = brush.format != BrushFormat.SYSTEM,
                                selected = brush.id == selectedBrushId,
                                multiSelecting = multiSelecting,
                                multiSelected = brush.id in selectedBrushIds,
                                onToggleSelection = { if (brush.format != BrushFormat.SYSTEM) selectedBrushIds = if (brush.id in selectedBrushIds) selectedBrushIds - brush.id else selectedBrushIds + brush.id },
                                onSelect = { onBrushSelected(brush) },
                                onReport = { reportBrush = brush },
                                onEdit = { editBrush = brush },
                                onHistory = { historyBrush = brush },
                                onMove = { moveBrush = brush },
                                onExport = { onExportBrush(brush) },
                                onFavorite = { onUpdateBrush(brush.copy(favorite = !brush.favorite)) },
                                onHide = { onUpdateBrush(brush.copy(hidden = !brush.hidden)) },
                                onDelete = { historyStore.clear(brush.id); historyRevision++; onDeleteBrush(brush.id) },
                                onReorder = { delta -> onReorderBrush(brush.id, delta) },
                            )
                        }
                    }
                    else -> LazyColumn(
                        state = brushListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 1.dp),
                    ) {
                        brushSections.forEach { (group, sectionBrushes) ->
                            if (group.isNotBlank()) item(key = "group:$group") {
                                Text(
                                    if (group == "Ungrouped") tr("UNGROUPED", "未分组") else group.uppercase(),
                                    color = MutedInk,
                                    fontSize = 9.sp,
                                    modifier = Modifier.fillMaxWidth().height(22.dp).background(Color(0xFFE9E9E9)).padding(horizontal = 6.dp).wrapContentHeight(Alignment.CenterVertically),
                                )
                            }
                            items(items = sectionBrushes, key = { it.id }) { brush ->
                                ImportedBrushRow(
                                    brush = brush,
                                    editable = brush.format != BrushFormat.SYSTEM,
                                    compact = effectiveLayout == BrushLibraryLayout.COMPACT,
                                    selected = brush.id == selectedBrushId,
                                    multiSelecting = multiSelecting,
                                    multiSelected = brush.id in selectedBrushIds,
                                    onToggleSelection = { if (brush.format != BrushFormat.SYSTEM) selectedBrushIds = if (brush.id in selectedBrushIds) selectedBrushIds - brush.id else selectedBrushIds + brush.id },
                                    onSelect = { onBrushSelected(brush) },
                                    onReport = { reportBrush = brush },
                                    onEdit = { editBrush = brush },
                                    onHistory = { historyBrush = brush },
                                    onMove = { moveBrush = brush },
                                    onExport = { onExportBrush(brush) },
                                    onFavorite = { onUpdateBrush(brush.copy(favorite = !brush.favorite)) },
                                    onHide = { onUpdateBrush(brush.copy(hidden = !brush.hidden)) },
                                    onDelete = { historyStore.clear(brush.id); historyRevision++; onDeleteBrush(brush.id) },
                                    onReorder = { delta -> onReorderBrush(brush.id, delta) },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(18.dp)); Text(tr("SOURCES", "来源"), fontSize = 11.sp, color = MutedInk)
            Spacer(Modifier.height(10.dp)); listOf(tr("System Brushes", "系统笔刷") to 12, "ABR" to 0, "CSP / SUT" to 0, "Procreate" to 0, tr("My Brushes", "我的笔刷") to 0).forEach { (name, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(name, Modifier.weight(1f)); Text(count.toString(), color = MutedInk, fontSize = 11.sp) }
            }
            Spacer(Modifier.weight(1f))
        }

    }
    reportBrush?.let { brush ->
        AlertDialog(
            onDismissRequest = { reportBrush = null },
            confirmButton = { TextButton(onClick = { reportBrush = null }) { Text(tr("Done", "完成")) } },
            title = { Text(brush.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${brush.format.label} · ${brush.compatibility.label}", color = Sage)
                    Spacer(Modifier.height(12.dp))
                    brush.report.forEach { Text("• $it", modifier = Modifier.padding(vertical = 3.dp)) }
                    if (brush.semanticMappings.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(tr("ENGINE V5 SEMANTIC MAP", "ENGINE V5 语义映射"), fontSize = 11.sp, color = MutedInk)
                        brush.semanticMappings.forEach { mapping ->
                            val fidelity = when (mapping.fidelity) {
                                app.inkloom.model.SemanticFidelity.EXACT -> tr("Exact", "完整")
                                app.inkloom.model.SemanticFidelity.APPROXIMATE -> tr("Approximate", "近似")
                                app.inkloom.model.SemanticFidelity.UNSUPPORTED -> tr("Unsupported", "不支持")
                            }
                            val disposition = when (mapping.executionDisposition) {
                                app.inkloom.model.SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED -> tr("Executed", "已执行")
                                app.inkloom.model.SemanticExecutionDisposition.UNSUPPORTED_WITH_EXPLICIT_REASON -> tr("Rejected", "已明确拒绝")
                                app.inkloom.model.SemanticExecutionDisposition.PRESERVED_SOURCE_ONLY -> tr("Source only", "仅保留源参数")
                            }
                            Text("• ${mapping.sourceSemantic} → ${mapping.targetRuntime} · $fidelity · $disposition", modifier = Modifier.padding(top = 5.dp))
                            if (mapping.note.isNotBlank()) Text(mapping.note, color = MutedInk, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(tr("The original source remains unchanged and can be reconverted by a future parser.", "原始文件保持不变，未来可使用更新的解析器重新转换。"), color = MutedInk, fontSize = 12.sp)
                }
            },
        )
    }
    editBrush?.let { brush ->
        val owned = remember(brush.id) {
            if (brush.format == BrushFormat.SYSTEM) {
                OwnedBrushPreset(
                    preset = BrushPresetV5Import.requirePreset(brush),
                    ownership = PresetOwnership.Factory(brush.id),
                )
            } else {
                val derivedFactoryId = brush.derivedFromFactoryId
                    ?: brush.renderBrushId?.takeIf { renderId -> systemBrushes.any { it.id == renderId } }
                OwnedBrushPreset(
                    preset = BrushPresetV5Import.requirePreset(brush),
                    ownership = PresetOwnership.User(userId = brush.id, derivedFromFactoryId = derivedFactoryId),
                )
            }
        }
        BrushEditorV1Dialog(
                source = owned,
                settings = settings,
                onDismiss = { creatingBrushId = null; editBrush = null },
                onSaveAs = { v5 ->
                    val updated = BrushPresetV5Import.writePreset(brush, v5, BrushFormat.INKLOOM)
                    if (creatingBrushId == brush.id) {
                        onReplaceBrushes(brushes + updated)
                    } else {
                        historyStore.record(brush)
                        historyRevision++
                        onReplaceBrushes(brushes + updated)
                    }
                    creatingBrushId = null
                    editBrush = null
                },
                onSaveInPlace = { v5 ->
                    val updated = BrushPresetV5Import.writePreset(brush, v5, BrushFormat.INKLOOM)
                    if (creatingBrushId == brush.id) {
                        onReplaceBrushes(brushes + updated)
                    } else if (updated != brush) {
                        historyStore.record(brush)
                        historyRevision++
                        onUpdateBrush(updated)
                    }
                    creatingBrushId = null
                    editBrush = null
                },
                onResetToFactory = (owned.ownership as? PresetOwnership.User)?.derivedFromFactoryId
                    ?.let { factoryId -> systemBrushes.firstOrNull { it.id == factoryId } }
                    ?.let(BrushPresetV5Import::requirePreset)
                    ?.let { factoryPreset ->
                        {
                            PresetOwnershipOperations.resetToFactory(owned, factoryPreset)?.let { reset ->
                                historyStore.record(brush)
                                historyRevision++
                                onUpdateBrush(
                                    BrushPresetV5Import.writePreset(brush, reset.preset, BrushFormat.INKLOOM),
                                )
                                editBrush = null
                            }
                        }
                    },
                onDelete = {
                    onDeleteBrush(brush.id)
                    creatingBrushId = null
                    editBrush = null
                },
        )
    }
    historyBrush?.let { selected ->
        val current = brushes.firstOrNull { it.id == selected.id } ?: selected
        val versions = remember(selected.id, historyRevision) { historyStore.versions(selected.id) }
        BrushVersionHistoryDialog(
            brush = current,
            versions = versions,
            onDismiss = { historyBrush = null },
            onRestore = { version ->
                historyStore.record(current)
                onUpdateBrush(historyStore.restore(current, version))
                historyRevision++
                historyBrush = null
            },
            onDelete = { version -> historyStore.delete(version.versionId); historyRevision++ },
        )
    }
    moveBrush?.let { selected ->
        val current = brushes.firstOrNull { it.id == selected.id } ?: selected
        BrushMoveToGroupDialog(
            brush = current,
            groups = groupTree,
            onDismiss = { moveBrush = null },
            onMove = { group ->
                val updated = BrushGroupOperations.assign(current, group)
                if (updated != current) onUpdateBrush(updated)
                selectedGroup = updated.groupName.takeIf(String::isNotBlank) ?: UNGROUPED_BRUSH_FILTER
                moveBrush = null
            },
        )
    }
    batchGroupOperation?.let { operation ->
        BrushBatchGroupDialog(
            count = selectedBrushIds.size,
            groups = groupTree,
            operation = operation,
            onDismiss = { batchGroupOperation = null },
            onApply = { group ->
                val updated = when (operation) {
                    BrushBatchGroupOperation.MOVE -> BrushGroupOperations.assignMany(brushes, selectedBrushIds, group)
                    BrushBatchGroupOperation.COPY -> BrushGroupOperations.duplicateMany(brushes, selectedBrushIds, group, copiedBrushSuffix)
                }
                onReplaceBrushes(updated)
                selectedGroup = BrushGroupOperations.normalize(group).takeIf(String::isNotBlank) ?: UNGROUPED_BRUSH_FILTER
                selectedBrushIds = emptySet()
                multiSelecting = false
                batchGroupOperation = null
            },
        )
    }
    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text(tr("Remove selected brushes?", "移除所选笔刷？")) },
            text = { Text(tr("This removes ${selectedBrushIds.size} presets from the brush library. Existing strokes keep their saved brush snapshots and will not change.", "这会从笔刷库移除 ${selectedBrushIds.size} 个预设。已有笔画会保留已保存的笔刷快照，不会发生变化。")) },
            confirmButton = {
                Button(onClick = {
                    selectedBrushIds.forEach(historyStore::clear)
                    historyRevision++
                    onReplaceBrushes(BrushGroupOperations.removeMany(brushes, selectedBrushIds))
                    selectedBrushIds = emptySet()
                    multiSelecting = false
                    confirmBatchDelete = false
                }) { Text(tr("Remove", "移除")) }
            },
            dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text(tr("Cancel", "取消")) } },
        )
    }
    if (manageGroups) {
        BrushGroupManagerDialog(
            groups = groupTree,
            onDismiss = { manageGroups = false },
            onRename = { oldName, newName ->
                val updated = BrushGroupOperations.rename(brushes, oldName, newName)
                onReplaceBrushes(updated)
                onReplaceGroups(BrushGroupOperations.reconcileRegisteredGroups(BrushGroupOperations.renameRegisteredGroups(registeredGroups, oldName, newName), updated))
                val normalized = BrushGroupOperations.normalize(newName)
                selectedGroup = normalized.takeIf(String::isNotBlank) ?: UNGROUPED_BRUSH_FILTER
                manageGroups = false
            },
            onMove = { group, delta ->
                onReplaceBrushes(BrushGroupOperations.moveGroup(brushes, group, delta))
                onReplaceGroups(BrushGroupOperations.moveRegisteredGroup(registeredGroups, group, delta))
            },
            onCreate = { path ->
                val normalized = BrushGroupOperations.normalize(path)
                if (normalized.isNotBlank()) {
                    onReplaceGroups(BrushGroupOperations.reconcileRegisteredGroups(registeredGroups + normalized, brushes))
                    selectedGroup = normalized
                }
            },
        )
    }
}

@Composable
private fun BrushMoveToGroupDialog(brush: ImportedBrush, groups: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var groupName by remember(brush.id) { mutableStateOf(brush.groupName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Move brush", "移动笔刷")) },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 440.dp)) {
                Text(brush.name, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                    label = { Text(tr("Group", "分组")) },
                    supportingText = { Text(tr("Enter a new name or choose an existing group", "输入新名称或选择已有分组")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (groups.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp)) {
                    groups.forEach { group -> item(group) { FilterChip(selected = groupName.equals(group, true), onClick = { groupName = group }, label = { Text(group, maxLines = 1) }) } }
                }
                TextButton(onClick = { onMove("") }) { Text(tr("Move to Ungrouped", "移到未分组")) }
            }
        },
        confirmButton = { Button(onClick = { onMove(groupName) }, enabled = BrushGroupOperations.normalize(groupName).isNotBlank()) { Text(tr("Move", "移动")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun BrushBatchGroupDialog(
    count: Int,
    groups: List<String>,
    operation: BrushBatchGroupOperation,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var groupName by remember(operation) { mutableStateOf("") }
    val moving = operation == BrushBatchGroupOperation.MOVE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (moving) tr("Move selected brushes", "移动所选笔刷") else tr("Copy selected brushes", "复制所选笔刷")) },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 440.dp)) {
                Text("$count ${tr("brushes selected", "支笔刷已选择")}", color = MutedInk, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                    label = { Text(tr("Destination group", "目标分组")) },
                    supportingText = { Text(tr("Enter a new name or choose an existing group", "输入新名称或选择已有分组")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (groups.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp)) {
                    groups.forEach { group -> item(group) { FilterChip(selected = groupName.equals(group, true), onClick = { groupName = group }, label = { Text(group, maxLines = 1) }) } }
                }
                TextButton(onClick = { onApply("") }) { Text(if (moving) tr("Move to Ungrouped", "移到未分组") else tr("Copy to Ungrouped", "复制到未分组")) }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(groupName) }, enabled = BrushGroupOperations.normalize(groupName).isNotBlank()) {
                Text(if (moving) tr("Move selected", "移动所选") else tr("Copy selected", "复制所选"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

private fun systemBrushCatalog(): List<ImportedBrush> = SystemBrushCatalog.brushes

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProcreateBrushLibrary(
    brushes: List<ImportedBrush>,
    usage: BrushUsageState,
    onUsage: (BrushUsageState) -> Unit,
    registeredGroups: List<String>,
    selectedBrushId: String?,
    requestedGroup: String?,
    settings: AppSettings,
    dock: WorkspacePanelDock,
    onDock: (WorkspacePanelDock) -> Unit,
    panelMode: WorkspacePanelMode,
    panelLocked: Boolean,
    onPanelMode: (WorkspacePanelMode) -> Unit,
    onPanelLocked: (Boolean) -> Unit,
    onClose: () -> Unit,
    onBrushSelected: (ImportedBrush) -> Unit,
    onImportBrushes: (String?) -> Unit,
    onExportBrush: (ImportedBrush) -> Unit,
    onQuickBrushCommit: (ImportedBrush) -> Unit,
    onUpdateBrush: (ImportedBrush) -> Unit,
    onReplaceBrushes: (List<ImportedBrush>) -> Unit,
    onReplaceGroups: (List<String>) -> Unit,
    onDeleteBrush: (String) -> Unit,
    onReorderBrush: (String, Int) -> Unit,
    modifier: Modifier,
    onPanelDragStart: () -> Unit,
    onPanelDrag: (Float, Float) -> Unit,
    onPanelDragEnd: () -> Unit,
    onPanelDragCancel: () -> Unit,
) {
    val systemBrushes = remember { systemBrushCatalog() }
    val systemCategories = listOf("Pen", "Pencil", "Brush", "Erasers", "Airbrush", "Decoration", "Blend", "Liquify")
    val usableImportedBrushes = remember(brushes) { BrushGroupOperations.orderForDisplay(BrushLibraryInventory.importedUsable(brushes)) }
    val importedCategories = BrushGroupOperations.treePaths(usableImportedBrushes, registeredGroups)
    val manageableCategories = importedCategories.filterNot { category -> systemCategories.any { it.equals(category, true) } }
    var collapsedGroupPaths by remember { mutableStateOf(emptySet<String>()) }
    val visibleImportedCategories = importedCategories.filter { path ->
        generateSequence(BrushGroupOperations.parent(path), BrushGroupOperations::parent)
            .none { ancestor -> collapsedGroupPaths.any { it.equals(ancestor, true) } }
    }
    val ungroupedLabel = tr("Imported", "已导入")
    val newBrushName = tr("New brush", "新笔刷")
    val createdInInkloom = tr("Created in Inkloom", "在 Inkloom 中创建")
    // User groups created inside a factory category are real children of that category, not a
    // second folder block appended after every system category. Keep each subtree together so a
    // freshly-created Pencil/Sketching row appears directly below Pencil, matching the brush-set
    // hierarchy used by Procreate and CSP.
    val categoriesInsideSystemRoots = systemCategories.flatMap { systemCategory ->
        listOf(systemCategory) + visibleImportedCategories.filter { importedCategory ->
            !importedCategory.equals(systemCategory, true) && BrushGroupOperations.isWithin(importedCategory, systemCategory)
        }
    }
    val standaloneImportedCategories = visibleImportedCategories.filter { importedCategory ->
        systemCategories.none { systemCategory -> BrushGroupOperations.isWithin(importedCategory, systemCategory) }
    }
    val recentCategory = BrushUsageState.RECENT_CATEGORY
    val allCategories = listOf(recentCategory, tr("All", "全部")) + categoriesInsideSystemRoots + standaloneImportedCategories + ungroupedLabel
    val activeInventoryBrush = (systemBrushes + usableImportedBrushes).firstOrNull { it.id == selectedBrushId }
    val activeGroup = activeInventoryBrush?.groupName?.ifBlank { ungroupedLabel } ?: requestedGroup
    // Tool return owns the durable category. A restored Compose category must not overwrite
    // a newer Paint/Eraser selection when this window is recreated or reopened.
    var selectedCategory by remember(usage.category) {
        mutableStateOf(usage.category?.takeIf { it in allCategories } ?: activeGroup?.takeIf { it in allCategories } ?: systemCategories.first())
    }
    var lastObservedSelection by remember { mutableStateOf(selectedBrushId) }
    var pendingCategoryReveal by remember { mutableStateOf<String?>(null) }
    val categoryListState = rememberLazyListState()
    var addMenu by remember { mutableStateOf(false) }
    var manageGroups by remember { mutableStateOf(false) }
    var editBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var menuBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var moveBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var gridLayout by rememberSaveable { mutableStateOf(false) }
    var menuGroup by remember { mutableStateOf<String?>(null) }
    var renameGroup by remember { mutableStateOf<String?>(null) }
    var renameGroupValue by remember { mutableStateOf("") }
    var deleteGroup by remember { mutableStateOf<String?>(null) }
    var groupDropBounds by remember { mutableStateOf(emptyMap<String, Rect>()) }
    var brushDragBounds by remember { mutableStateOf(emptyMap<String, Rect>()) }
    var draggingBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var brushDragPosition by remember { mutableStateOf<Offset?>(null) }
    var draggingGroup by remember { mutableStateOf<String?>(null) }
    var groupDragPosition by remember { mutableStateOf<Offset?>(null) }
    var managingBrushes by remember { mutableStateOf(false) }
    var selectedBrushIds by remember { mutableStateOf(emptySet<String>()) }
    val currentSelectedBrushIds by rememberUpdatedState(selectedBrushIds)
    var batchGroupOperation by remember { mutableStateOf<BrushBatchGroupOperation?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    val dragDescription = tr("Drag brushes panel", "拖动笔刷面板")
    val brushManagementDescription = tr("Brush management: ${selectedBrushIds.size} selected", "笔刷管理：已选择 ${selectedBrushIds.size} 支")
    val brushListDescription = tr("Brush presets", "笔刷预设列表")
    val allLabel = tr("All", "全部")
    val groupCopySuffix = tr("copy", "副本")
    @Composable fun categoryLabel(category: String): String = when (category) {
        recentCategory -> tr("Recent", "最近使用")
        "Pen" -> tr("Pen", "钢笔")
        "Pencil" -> tr("Pencil", "铅笔")
        "Brush" -> tr("Brush", "画笔")
        "Decoration" -> tr("Decoration", "装饰")
        "Blend" -> tr("Blend", "混色")
        "Liquify" -> tr("Liquify", "液化")
        "Airbrush" -> tr("Airbrush", "喷枪")
        "Erasers" -> tr("Erasers", "橡皮")
        else -> BrushGroupOperations.displayName(category).ifBlank { category }
    }
    fun finishBrushManagement() {
        managingBrushes = false
        selectedBrushIds = emptySet()
        draggingBrush = null
        brushDragPosition = null
    }
    fun applyBrushGroupOperation(ids: Set<String>, group: String, copy: Boolean) {
        val normalizedGroup = BrushGroupOperations.normalize(group)
        val source = (systemBrushes + brushes).filter { it.id in ids }
        val customIds = source.filterNot { it.format == BrushFormat.SYSTEM }.mapTo(linkedSetOf(), ImportedBrush::id)
        val retained = if (copy) brushes else BrushGroupOperations.assignMany(brushes, customIds, normalizedGroup)
        val copies = source.filter { copy || it.format == BrushFormat.SYSTEM }.mapIndexed { index, brush ->
            brush.copy(
                id = UUID.randomUUID().toString(),
                name = if (copy) "${brush.name} $groupCopySuffix" else brush.name,
                format = BrushFormat.INKLOOM,
                groupName = normalizedGroup,
                lastUsedAt = 0L,
                sortOrder = (brushes.maxOfOrNull(ImportedBrush::sortOrder) ?: -1) + index + 1,
            )
        }
        onReplaceBrushes(retained + copies)
        selectedCategory = normalizedGroup.ifBlank { ungroupedLabel }
        finishBrushManagement()
    }
    fun moveBrushIntoGroup(brush: ImportedBrush, group: String) {
        applyBrushGroupOperation(setOf(brush.id), group, copy = false)
    }
    @Composable
    fun Modifier.brushDropTarget(brush: ImportedBrush): Modifier {
        DisposableEffect(brush.id) {
            onDispose { brushDragBounds = brushDragBounds - brush.id }
        }
        return this.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            if (brushDragBounds[brush.id] != bounds) brushDragBounds = brushDragBounds + (brush.id to bounds)
        }
    }

    fun brushDropHovered(brush: ImportedBrush): Boolean {
        val moving = draggingBrush ?: return false
        val point = brushDragPosition ?: return false
        return moving.id != brush.id && moving.format != BrushFormat.SYSTEM &&
            BrushGroupOperations.normalize(moving.groupName).equals(BrushGroupOperations.normalize(brush.groupName), true) &&
            brushDragBounds[brush.id]?.contains(point) == true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BrushMoveHandle(brush: ImportedBrush) {
        var origin by remember { mutableStateOf(Offset.Zero) }
        val description = tr("Move brush ${brush.name}", "移动笔刷 ${brush.name}")
        val tooltipState = rememberTooltipState()
        val tooltipScope = rememberCoroutineScope()
        val start by rememberUpdatedState<(Offset) -> Unit>({ local ->
            draggingBrush = brush
            brushDragPosition = origin + local
        })
        val move by rememberUpdatedState<(Offset) -> Unit>({ local -> brushDragPosition = origin + local })
        val finish by rememberUpdatedState<() -> Unit>({
            val position = brushDragPosition
            val target = position?.let { point -> groupDropBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(point) }?.key }
            val targetBrushId = position?.let { point -> brushDragBounds.entries.firstOrNull { (id, bounds) -> id != brush.id && bounds.contains(point) }?.key }
            if (target != null) {
                applyBrushGroupOperation(setOf(brush.id), target, copy = false)
            } else if (brush.format != BrushFormat.SYSTEM && targetBrushId != null) {
                val peers = BrushGroupOperations.orderForDisplay(brushes.filter { candidate ->
                    BrushGroupOperations.normalize(candidate.groupName).equals(BrushGroupOperations.normalize(brush.groupName), true)
                })
                val from = peers.indexOfFirst { it.id == brush.id }
                val to = peers.indexOfFirst { it.id == targetBrushId }
                if (from >= 0 && to >= 0 && from != to) onReorderBrush(brush.id, to - from)
            }
            draggingBrush = null
            brushDragPosition = null
        })
        val cancel by rememberUpdatedState<() -> Unit>({
            draggingBrush = null
            brushDragPosition = null
        })
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(tr("Hold and drag to move; tap for destinations", "按住拖动以移动；轻点选择目标分组")) } },
            state = tooltipState,
            enableUserInput = false,
        ) {
            Icon(Icons.Outlined.DragIndicator, description, tint = MutedInk,
                modifier = Modifier.size(48.dp)
                    .onGloballyPositioned { origin = it.localToWindow(Offset.Zero) }
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { moveBrush = brush }
                    .pointerInput(brush.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start(it) },
                            onDrag = { change, _ -> change.consume(); move(change.position) },
                            onDragEnd = { finish() },
                            onDragCancel = { cancel() },
                        )
                    }
                    .pointerInput(tooltipState) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                when (event.type) {
                                    PointerEventType.Enter, PointerEventType.Move -> if (event.changes.none { it.pressed } && !tooltipState.isVisible) {
                                        tooltipScope.launch { tooltipState.show() }
                                    }
                                    PointerEventType.Exit -> tooltipState.dismiss()
                                    else -> Unit
                                }
                            }
                        }
                    }
                    .padding(12.dp),
            )
        }
    }
    LaunchedEffect(allCategories, pendingCategoryReveal) {
        if (pendingCategoryReveal == null && selectedCategory !in allCategories) selectedCategory = allCategories.first()
    }
    LaunchedEffect(importedCategories, pendingCategoryReveal) {
        val pending = pendingCategoryReveal ?: return@LaunchedEffect
        val created = importedCategories.firstOrNull { it.equals(pending, true) } ?: return@LaunchedEffect
        selectedCategory = created
        collapsedGroupPaths = collapsedGroupPaths - generateSequence(BrushGroupOperations.parent(created), BrushGroupOperations::parent).toSet()
        pendingCategoryReveal = null
    }
    LaunchedEffect(selectedCategory, allCategories) {
        allCategories.distinct().indexOf(selectedCategory).takeIf { it >= 0 }?.let { categoryListState.animateScrollToItem(it) }
    }
    LaunchedEffect(activeGroup, selectedBrushId) {
        if (lastObservedSelection != selectedBrushId && selectedCategory != recentCategory) {
            activeGroup?.let { requested -> allCategories.firstOrNull { it.equals(requested, true) }?.let { selectedCategory = it } }
        }
        lastObservedSelection = selectedBrushId
    }
    LaunchedEffect(selectedCategory) {
        if (usage.category != selectedCategory) onUsage(usage.copy(category = selectedCategory))
    }
    val usableLibraryBrushes = remember(systemBrushes, usableImportedBrushes) {
        BrushLibraryInventory.selectable(systemBrushes, usableImportedBrushes)
    }
    val visibleLibraryBrushes = usableLibraryBrushes.filterNot(ImportedBrush::hidden)
    val recentBrushIds = usage.displayedIds(visibleLibraryBrushes.mapTo(linkedSetOf(), ImportedBrush::id))
    val displayed = if (selectedCategory == recentCategory) {
        val byId = visibleLibraryBrushes.associateBy(ImportedBrush::id)
        recentBrushIds.mapNotNull(byId::get)
    } else visibleLibraryBrushes.filter { brush ->
        when (selectedCategory) {
            allLabel -> true
            ungroupedLabel -> brush.format != BrushFormat.SYSTEM
            else -> if (brush.format == BrushFormat.SYSTEM) brush.groupName == selectedCategory else BrushGroupOperations.isWithin(brush.groupName, selectedCategory)
        }
    }
    LaunchedEffect(brushes) {
        val availableIds = usableLibraryBrushes.mapTo(linkedSetOf(), ImportedBrush::id)
        selectedBrushIds = selectedBrushIds.intersect(availableIds)
        if (managingBrushes && selectedBrushIds.isEmpty()) managingBrushes = false
    }
    var quickPropertiesOpen by remember { mutableStateOf(false) }
    val presetListState = rememberLazyListState()
    val presetGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(selectedBrushId, selectedCategory, gridLayout) {
        val selectedIndex = displayed.indexOfFirst { it.id == selectedBrushId }
        if (selectedIndex >= 0) {
            if (gridLayout) {
                if (presetGridState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) presetGridState.scrollToItem(selectedIndex)
            } else if (presetListState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
                presetListState.scrollToItem(selectedIndex)
            }
        }
    }

    val quickBrush = usableLibraryBrushes.firstOrNull { it.id == selectedBrushId }
    @Composable fun RecentActions(brush: ImportedBrush, content: @Composable () -> Unit) {
        RecentBrushActions(
            enabled = selectedCategory == recentCategory && !managingBrushes,
            pinned = brush.id in usage.pinnedIds, language = settings.language,
            onPin = { onUsage(if (brush.id in usage.pinnedIds) usage.unpin(brush.id) else usage.pin(brush.id)) },
            onFind = {
                onBrushSelected(brush)
                selectedCategory = allCategories.firstOrNull { it.equals(brush.groupName, true) } ?: ungroupedLabel
            },
            onClear = { onUsage(usage.clear(brush.id)) }, content = content,
        )
    }
    val panelShape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier.shadow(12.dp, panelShape),
        shape = panelShape,
        color = Color.White,
        contentColor = Ink,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().height(46.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("Brushes", "笔刷"),
                    Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically).semantics { contentDescription = dragDescription }.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var last = down.position
                            var moved = false
                            currentPanelDragStart()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val amount = change.position - last
                                if (amount != Offset.Zero) {
                                    moved = true
                                    change.consume()
                                    currentPanelDrag(amount.x, amount.y)
                                    last = change.position
                                }
                            }
                            if (moved) currentPanelDragEnd() else currentPanelDragCancel()
                        }
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Box {
                    IconButton(onClick = { quickPropertiesOpen = !quickPropertiesOpen }, enabled = quickBrush != null,
                        modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.Tune, tr("Quick brush properties", "笔刷快捷参数"), tint = Ink)
                    }
                    DropdownMenu(expanded = quickPropertiesOpen && quickBrush != null,
                        onDismissRequest = { quickPropertiesOpen = false }) {
                        quickBrush?.let { selected ->
                            BrushQuickProperties(selected, settings.language, onCommit = onQuickBrushCommit,
                                onEdit = { quickPropertiesOpen = false; editBrush = selected })
                        }
                    }
                }
                IconButton(onClick = { gridLayout = !gridLayout }, modifier = Modifier.size(38.dp)) {
                    Icon(if (gridLayout) Icons.Outlined.ViewList else Icons.Outlined.GridView, if (gridLayout) tr("List view", "列表视图") else tr("Grid view", "网格视图"), tint = Ink)
                }
                IconButton(
                    onClick = { if (managingBrushes) finishBrushManagement() else managingBrushes = true },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        if (managingBrushes) Icons.Outlined.Close else Icons.Outlined.Checklist,
                        if (managingBrushes) tr("Exit brush management", "退出笔刷管理") else tr("Select multiple brushes", "批量选择笔刷"),
                        tint = Ink,
                    )
                }
                IconButton(onClick = { manageGroups = true }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Outlined.CreateNewFolder, tr("Manage brush groups", "管理笔刷分组"), tint = Ink)
                }
                Box {
                    IconButton(onClick = { addMenu = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Outlined.Add, tr("Add brush or group", "添加笔刷或分组"), tint = Ink) }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text(tr("Create new brush", "新建笔刷")) }, leadingIcon = { Icon(Icons.Outlined.Brush, null) }, onClick = {
                            addMenu = false
                            val group = selectedCategory.takeIf { it in systemCategories || it in importedCategories }
                            val created = BrushPresetV5Import.createUserRecord(ImportedBrush(name = newBrushName, format = BrushFormat.INKLOOM, compatibility = Compatibility.COMPLETE, sizeBytes = 0L, report = listOf(createdInInkloom), groupName = group.orEmpty()))
                            onReplaceBrushes(brushes + created)
                            editBrush = created
                        })
                        DropdownMenuItem(text = { Text(tr("Import from files", "从文件导入")) }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }, onClick = {
                            addMenu = false
                            onImportBrushes(selectedCategory.takeIf { it != allLabel && it != ungroupedLabel && it != recentCategory })
                        })
                        DropdownMenuItem(text = { Text(tr("Manage brush sets", "管理笔刷组")) }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = { addMenu = false; manageGroups = true })
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) { Icon(Icons.Outlined.Close, tr("Close brushes panel", "关闭笔刷面板"), tint = Ink) }
            }
            HorizontalDivider(color = OutlineSoft)
            if (managingBrushes) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp).background(Color(0xFFF5F5F5)).padding(horizontal = 8.dp)
                        .semantics { contentDescription = brushManagementDescription },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::finishBrushManagement, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, tr("Exit brush management", "退出笔刷管理")) }
                    Text("${selectedBrushIds.size} ${tr("selected", "已选择")}", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { selectedBrushIds = displayed.mapTo(linkedSetOf(), ImportedBrush::id) }) { Text(tr("All", "全选"), fontSize = 11.sp) }
                    TextButton(onClick = { batchGroupOperation = BrushBatchGroupOperation.MOVE }, enabled = selectedBrushIds.isNotEmpty()) { Text(tr("Move", "移动"), fontSize = 11.sp) }
                    TextButton(onClick = { batchGroupOperation = BrushBatchGroupOperation.COPY }, enabled = selectedBrushIds.isNotEmpty()) { Text(tr("Copy", "复制"), fontSize = 11.sp) }
                    IconButton(
                        onClick = { confirmBatchDelete = true },
                        enabled = selectedBrushIds.any { id -> brushes.any { it.id == id } },
                        modifier = Modifier.size(36.dp),
                    ) { Icon(Icons.Outlined.Delete, tr("Delete selected brushes", "删除所选笔刷"), tint = MaterialTheme.colorScheme.error) }
                }
                HorizontalDivider(color = OutlineSoft)
            }
            Row(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.width(120.dp).fillMaxHeight().background(Color.White).border(1.dp, OutlineSoft),
                    state = categoryListState,
                    contentPadding = PaddingValues(vertical = 7.dp),
                ) {
                    items(allCategories.distinct(), key = { it }) { category ->
                        val selected = category == selectedCategory
                        val systemGroup = category in systemCategories
                        val customGroup = category in importedCategories && !systemGroup
                        val dropGroup = systemGroup || customGroup
                        DisposableEffect(category) {
                            onDispose { groupDropBounds = groupDropBounds - category }
                        }
                        val hasChildren = importedCategories.any { BrushGroupOperations.parent(it)?.equals(category, true) == true }
                        val visibleCategoryLabel = categoryLabel(category)
                        val categoryDescription = if (customGroup) tr("Brush group $visibleCategoryLabel", "笔刷分组 $visibleCategoryLabel") else tr("Brush category $visibleCategoryLabel", "笔刷类别 $visibleCategoryLabel")
                        val groupDragModifier = if (!customGroup) Modifier else Modifier.pointerInput(category, groupDropBounds) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { local ->
                                    draggingGroup = category
                                    groupDragPosition = groupDropBounds[category]?.topLeft?.plus(local)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    groupDragPosition = groupDropBounds[category]?.topLeft?.plus(change.position)
                                },
                                onDragEnd = {
                                    val target = groupDragPosition?.let { point ->
                                        groupDropBounds.entries.firstOrNull { (path, bounds) -> path != category && bounds.contains(point) }?.key
                                    }
                                    val parent = BrushGroupOperations.parent(category)
                                    val siblings = importedCategories.filter { path ->
                                        BrushGroupOperations.parent(path)?.equals(parent, true) ?: (parent == null)
                                    }
                                    val from = siblings.indexOfFirst { it.equals(category, true) }
                                    val to = siblings.indexOfFirst { it.equals(target, true) }
                                    if (from >= 0 && to >= 0 && from != to) {
                                        onReplaceBrushes(BrushGroupOperations.moveGroup(brushes, category, to - from))
                                        onReplaceGroups(BrushGroupOperations.moveRegisteredGroup(registeredGroups, category, to - from))
                                    }
                                    draggingGroup = null
                                    groupDragPosition = null
                                },
                                onDragCancel = { draggingGroup = null; groupDragPosition = null },
                            )
                        }
                        Box {
                            Row(
                                Modifier.fillMaxWidth().height(39.dp)
                                    .semantics { contentDescription = categoryDescription }
                                    .onGloballyPositioned { coordinates ->
                                        if (dropGroup) {
                                            val bounds = coordinates.boundsInWindow()
                                            if (groupDropBounds[category] != bounds) groupDropBounds = groupDropBounds + (category to bounds)
                                        }
                                    }
                                    .background(if (draggingGroup == category) Color(0xFFDDE8F7) else if (draggingBrush != null && dropGroup && brushDragPosition?.let { groupDropBounds[category]?.contains(it) } == true) Color(0xFFDDE8F7) else if (draggingBrush != null && dropGroup) Color(0xFFF0F4FA) else if (selected) Color(0xFFE1E1E1) else Color.Transparent)
                                    .clickable { selectedCategory = category }
                                    .then(groupDragModifier)
                                    .padding(start = (10 + BrushGroupOperations.depth(category) * 12).dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (hasChildren) IconButton(onClick = {
                                    collapsedGroupPaths = if (category in collapsedGroupPaths) collapsedGroupPaths - category else collapsedGroupPaths + category
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        if (category in collapsedGroupPaths) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore,
                                        if (category in collapsedGroupPaths) tr("Expand group $visibleCategoryLabel", "展开分组 $visibleCategoryLabel") else tr("Collapse group $visibleCategoryLabel", "收起分组 $visibleCategoryLabel"),
                                        Modifier.size(16.dp),
                                        tint = MutedInk,
                                    )
                                } else Spacer(Modifier.width(24.dp))
                                Text(visibleCategoryLabel, color = Ink, fontSize = 11.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(
                                    when (category) {
                                        recentCategory -> recentBrushIds.size
                                        allLabel -> usableLibraryBrushes.size
                                        ungroupedLabel -> usableImportedBrushes.size
                                        else -> systemBrushes.count { it.groupName.equals(category, true) } +
                                            usableImportedBrushes.count { BrushGroupOperations.isWithin(it.groupName, category) }
                                    }.toString(),
                                    color = MutedInk,
                                    fontSize = 9.sp,
                                )
                                if (customGroup) IconButton(onClick = { menuGroup = category }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Outlined.MoreVert, tr("Brush group actions $visibleCategoryLabel", "笔刷分组操作 $visibleCategoryLabel"), Modifier.size(17.dp), tint = MutedInk)
                                }
                            }
                            DropdownMenu(expanded = menuGroup == category, onDismissRequest = { menuGroup = null }) {
                                DropdownMenuItem(text = { Text(tr("Rename group", "重命名分组")) }, onClick = {
                                    menuGroup = null
                                    renameGroup = category
                                    renameGroupValue = category
                                })
                                DropdownMenuItem(text = { Text(tr("Duplicate group", "复制分组")) }, onClick = {
                                    menuGroup = null
                                    val parent = BrushGroupOperations.parent(category)
                                    val base = "${BrushGroupOperations.displayName(category)} $groupCopySuffix"
                                    var candidate = listOfNotNull(parent, base).joinToString("/")
                                    var suffix = 2
                                    while (importedCategories.any { it.equals(candidate, true) }) {
                                        candidate = listOfNotNull(parent, "$base $suffix").joinToString("/")
                                        suffix++
                                    }
                                    val updated = BrushGroupOperations.duplicateGroup(brushes, category, candidate, groupCopySuffix)
                                    onReplaceBrushes(updated)
                                    onReplaceGroups(BrushGroupOperations.reconcileRegisteredGroups(registeredGroups + candidate, updated))
                                    selectedCategory = candidate
                                })
                                DropdownMenuItem(text = { Text(tr("Delete group", "删除分组"), color = MaterialTheme.colorScheme.error) }, onClick = {
                                    menuGroup = null
                                    deleteGroup = category
                                })
                            }
                        }
                    }
                }
                if (selectedCategory == recentCategory && displayed.isEmpty()) Box(
                    Modifier.weight(1f).fillMaxHeight().padding(20.dp), contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Your recently used brushes appear here. Swipe a brush left to pin it.", "使用过的笔刷会显示在这里。向左滑动笔刷即可置顶。"),
                        color = MutedInk, fontSize = 12.sp)
                } else if (gridLayout) LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = presetGridState,
                    modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = brushListDescription },
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(displayed, key = { it.id }) { brush ->
                        val selected = selectedBrushId == brush.id
                        val batchSelected = brush.id in selectedBrushIds
                        val brushPresetDescription = tr("Brush preset ${brush.name}", "笔刷预设 ${brush.name}")
                        val interaction = if (managingBrushes) Modifier.clickable {
                            selectedBrushIds = if (brush.id in currentSelectedBrushIds) currentSelectedBrushIds - brush.id else currentSelectedBrushIds + brush.id
                        } else Modifier.combinedClickable(
                            // A completed tap must select before the next canvas contact.
                            // Double-click detection delays onClick and lets the old preset draw.
                            onClick = { onBrushSelected(brush) },
                            onLongClick = { editBrush = brush },
                        )
                        RecentActions(brush) { Column(
                            Modifier.fillMaxWidth().height(112.dp).semantics { contentDescription = brushPresetDescription }.brushDropTarget(brush).then(interaction).clip(RoundedCornerShape(6.dp)).background(if (batchSelected || draggingBrush?.id == brush.id || brushDropHovered(brush)) Color(0xFFDDE8F7) else if (selected) Color(0xFFE1E1E1) else Color.White).border(1.dp, if (batchSelected || selected) Ink else OutlineSoft, RoundedCornerShape(6.dp)).padding(7.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(brush.name, Modifier.weight(1f), color = Ink, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (brush.id in usage.pinnedIds) Icon(Icons.Outlined.PushPin, tr("Pinned brush", "已置顶笔刷"), Modifier.size(16.dp))
                                if (!managingBrushes && selectedCategory != recentCategory) BrushMoveHandle(brush)
                            }
                            Spacer(Modifier.height(4.dp))
                            Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(4.dp), color = Color(0xFFF3F3F3)) {
                                BrushTipPreview(brush, Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                            if (batchSelected) Icon(Icons.Outlined.CheckCircle, null, Modifier.align(Alignment.End).size(17.dp), tint = Ink)
                        } }
                    }
                } else LazyColumn(
                    state = presetListState,
                    modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = brushListDescription },
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(displayed, key = { it.id }) { brush ->
                        val selected = selectedBrushId == brush.id
                        val batchSelected = brush.id in selectedBrushIds
                        val brushPresetDescription = tr("Brush preset ${brush.name}", "笔刷预设 ${brush.name}")
                        val interaction = if (managingBrushes) Modifier.clickable {
                            selectedBrushIds = if (brush.id in currentSelectedBrushIds) currentSelectedBrushIds - brush.id else currentSelectedBrushIds + brush.id
                        } else Modifier.combinedClickable(
                            // A completed tap must select before the next canvas contact.
                            // Double-click detection delays onClick and lets the old preset draw.
                            onClick = { onBrushSelected(brush) },
                            onLongClick = { editBrush = brush },
                        )
                        RecentActions(brush) { Column(
                            Modifier.fillMaxWidth().height(84.dp).semantics { contentDescription = brushPresetDescription }.brushDropTarget(brush).then(interaction).background(if (batchSelected || draggingBrush?.id == brush.id || brushDropHovered(brush)) Color(0xFFDDE8F7) else if (selected) Color(0xFFE0EBE3) else Color.White).padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(brush.name, Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (batchSelected) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Ink)
                                if (brush.id in usage.pinnedIds) Icon(Icons.Outlined.PushPin, tr("Pinned brush", "已置顶笔刷"), Modifier.size(16.dp))
                                if (!managingBrushes && selectedCategory != recentCategory) BrushMoveHandle(brush)
                            }
                            BrushTipPreview(brush, Modifier.fillMaxWidth().weight(1f))
                        } }
                    }
                }
            }
        }
    }
    batchGroupOperation?.let { operation ->
        BrushBatchGroupDialog(
            count = selectedBrushIds.size,
            groups = importedCategories,
            operation = operation,
            onDismiss = { batchGroupOperation = null },
            onApply = { group ->
                applyBrushGroupOperation(selectedBrushIds, group, copy = operation == BrushBatchGroupOperation.COPY)
                batchGroupOperation = null
            },
        )
    }
    if (confirmBatchDelete) AlertDialog(
        onDismissRequest = { confirmBatchDelete = false },
        title = { Text(tr("Delete selected brushes?", "删除所选笔刷？")) },
        text = { Text(tr("System brushes stay in their built-in sets. Selected imported brushes will be removed; existing strokes remain unchanged.", "系统笔刷会保留在默认分组中；所选导入笔刷将被删除，已有笔画不受影响。")) },
        confirmButton = { Button(onClick = {
            onReplaceBrushes(brushes.filterNot { it.id in selectedBrushIds })
            confirmBatchDelete = false
            finishBrushManagement()
        }) { Text(tr("Delete", "删除")) } },
        dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text(tr("Cancel", "取消")) } },
    )
    menuBrush?.let { brush ->
        AlertDialog(
            onDismissRequest = { menuBrush = null },
            title = { Text(brush.name) },
            text = { Text(tr("Edit, move, export or remove this brush.", "编辑、移动、导出或移除此笔刷。")) },
            confirmButton = { TextButton(onClick = { menuBrush = null; if (brush.format != BrushFormat.SYSTEM) editBrush = brush }) { Text(tr("Edit", "编辑")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { menuBrush = null; moveBrush = brush }) { Text(tr("Move", "移动")) }
                    TextButton(onClick = { menuBrush = null; onExportBrush(brush) }) { Text(tr("Export", "导出")) }
                    if (brush.format != BrushFormat.SYSTEM) TextButton(onClick = { menuBrush = null; onDeleteBrush(brush.id) }) { Text(tr("Remove", "移除"), color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
    moveBrush?.let { brush ->
        BrushMoveToGroupDialog(
            brush = brush,
            groups = importedCategories,
            onDismiss = { moveBrush = null },
            onMove = { group ->
                moveBrushIntoGroup(brush, group)
                moveBrush = null
            },
        )
    }
    renameGroup?.let { oldName ->
        AlertDialog(
            onDismissRequest = { renameGroup = null },
            title = { Text(tr("Rename brush group", "重命名笔刷分组")) },
            text = { OutlinedTextField(renameGroupValue, { renameGroupValue = it.take(BrushGroupOperations.MAX_PATH_LENGTH) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = {
                val updated = BrushGroupOperations.rename(brushes, oldName, renameGroupValue)
                onReplaceBrushes(updated)
                onReplaceGroups(BrushGroupOperations.reconcileRegisteredGroups(BrushGroupOperations.renameRegisteredGroups(registeredGroups, oldName, renameGroupValue), updated))
                selectedCategory = BrushGroupOperations.normalize(renameGroupValue).takeIf(String::isNotBlank) ?: allLabel
                renameGroup = null
            }, enabled = BrushGroupOperations.normalize(renameGroupValue).isNotBlank()) { Text(tr("Rename", "重命名")) } },
            dismissButton = { TextButton(onClick = { renameGroup = null }) { Text(tr("Cancel", "取消")) } },
        )
    }
    deleteGroup?.let { group ->
        val count = brushes.count { BrushGroupOperations.isWithin(it.groupName, group) }
        AlertDialog(
            onDismissRequest = { deleteGroup = null },
            title = { Text(tr("Delete brush group?", "删除笔刷分组？")) },
            text = { Text(tr("This removes the group and its $count brushes. Existing strokes are unchanged.", "这会移除该分组及其中的 $count 支笔刷。已有笔画不会改变。")) },
            confirmButton = { Button(onClick = {
                val updated = BrushGroupOperations.deleteGroup(brushes, group)
                onReplaceBrushes(updated)
                onReplaceGroups(BrushGroupOperations.reconcileRegisteredGroups(BrushGroupOperations.deleteRegisteredGroup(registeredGroups, group), updated))
                selectedCategory = allLabel
                deleteGroup = null
            }) { Text(tr("Delete", "删除")) } },
            dismissButton = { TextButton(onClick = { deleteGroup = null }) { Text(tr("Cancel", "取消")) } },
        )
    }
    editBrush?.let { brush ->
        val owned = remember(brush.id) {
            if (brush.format == BrushFormat.SYSTEM) {
                OwnedBrushPreset(
                    preset = BrushPresetV5Import.requirePreset(brush),
                    ownership = PresetOwnership.Factory(brush.id),
                )
            } else {
                val derivedFactoryId = brush.derivedFromFactoryId
                    ?: brush.renderBrushId?.takeIf { renderId -> systemBrushes.any { it.id == renderId } }
                OwnedBrushPreset(
                    preset = BrushPresetV5Import.requirePreset(brush),
                    ownership = PresetOwnership.User(userId = brush.id, derivedFromFactoryId = derivedFactoryId),
                )
            }
        }
        BrushEditorV1Dialog(
            source = owned,
            settings = settings,
            onDismiss = { editBrush = null },
            onSaveAs = { v5 ->
                val updated = BrushPresetV5Import.writePreset(brush, v5, BrushFormat.INKLOOM)
                if (brushes.any { it.id == updated.id }) onUpdateBrush(updated) else onReplaceBrushes(brushes + updated)
                editBrush = null
            },
            onSaveInPlace = { v5 ->
                val updated = BrushPresetV5Import.writePreset(brush, v5, BrushFormat.INKLOOM)
                if (brushes.any { it.id == updated.id }) onUpdateBrush(updated) else onReplaceBrushes(brushes + updated)
                editBrush = null
            },
            onResetToFactory = (owned.ownership as? PresetOwnership.User)?.derivedFromFactoryId
                ?.let { factoryId -> systemBrushes.firstOrNull { it.id == factoryId } }
                ?.let(BrushPresetV5Import::requirePreset)
                ?.let { factoryPreset ->
                    {
                        PresetOwnershipOperations.resetToFactory(owned, factoryPreset)?.let { reset ->
                            onUpdateBrush(
                                BrushPresetV5Import.writePreset(brush, reset.preset, BrushFormat.INKLOOM),
                            )
                            editBrush = null
                        }
                    }
                },
            onDelete = {
                onDeleteBrush(brush.id)
                editBrush = null
            },
        )
    }
    if (manageGroups) BrushGroupManagerDialog(
        groups = manageableCategories,
        initialParent = selectedCategory.takeIf { category -> category in systemCategories || category in importedCategories },
        onDismiss = { manageGroups = false },
        onRename = { oldName, newName ->
            val updated = BrushGroupOperations.rename(brushes, oldName, newName)
            onReplaceBrushes(updated)
            onReplaceGroups(BrushGroupOperations.reconcileRegisteredGroups(BrushGroupOperations.renameRegisteredGroups(registeredGroups, oldName, newName), updated))
        },
        onMove = { group, delta ->
            onReplaceBrushes(BrushGroupOperations.moveGroup(brushes, group, delta))
            onReplaceGroups(BrushGroupOperations.moveRegisteredGroup(registeredGroups, group, delta))
        },
        onCreate = { path ->
            val normalized = BrushGroupOperations.normalize(path)
            if (normalized.isNotBlank()) {
                pendingCategoryReveal = normalized
                selectedCategory = normalized
                collapsedGroupPaths = collapsedGroupPaths - generateSequence(BrushGroupOperations.parent(normalized), BrushGroupOperations::parent).toSet()
                onReplaceGroups(BrushGroupOperations.reconcileRegisteredGroups(registeredGroups + normalized, brushes))
            }
        },
    )
}

@Composable
private fun BrushGroupManagerDialog(groups: List<String>, onDismiss: () -> Unit, onRename: (String, String) -> Unit, onMove: (String, Int) -> Unit, onCreate: (String) -> Unit, initialParent: String? = null) {
    var selected by remember { mutableStateOf(groups.firstOrNull().orEmpty()) }
    LaunchedEffect(groups) { if (groups.none { it == selected }) selected = groups.firstOrNull().orEmpty() }
    var replacement by remember(selected) { mutableStateOf(selected) }
    var newGroupPath by remember(initialParent) { mutableStateOf("") }
    val normalizedParent = BrushGroupOperations.normalize(initialParent.orEmpty())
    val normalizedInputGroup = BrushGroupOperations.normalize(newGroupPath)
    val normalizedNewGroup = if (normalizedParent.isNotBlank() && '/' !in newGroupPath) {
        BrushGroupOperations.normalize("$normalizedParent/$normalizedInputGroup")
    } else normalizedInputGroup
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Manage groups", "管理分组")) },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 440.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newGroupPath,
                        onValueChange = { newGroupPath = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                        label = { Text(tr("New group path", "新建分组路径")) },
                        supportingText = {
                            Text(
                                if (normalizedParent.isBlank()) tr("Use / to create nested groups", "使用 / 创建嵌套分组")
                                else tr("Creates inside ${BrushGroupOperations.displayName(normalizedParent)}; use / for an absolute path", "将在 ${BrushGroupOperations.displayName(normalizedParent)} 内创建；使用 / 可输入完整路径"),
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "New brush group path" },
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(normalizedNewGroup); selected = normalizedNewGroup; newGroupPath = "" },
                        enabled = normalizedInputGroup.isNotBlank() && !newGroupPath.trimEnd().endsWith('/') && groups.none { it.equals(normalizedNewGroup, true) },
                    ) { Text(tr("Create", "创建")) }
                }
                Spacer(Modifier.height(8.dp))
                if (groups.isEmpty()) {
                    Text(tr("No groups yet. Create an empty group above or move a brush into a new path.", "还没有分组。可在上方创建空组，或将笔刷移动到新路径。"), color = MutedInk)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        groups.forEach { group -> item(group) { FilterChip(selected = selected == group, onClick = { selected = group }, label = { Text(group, maxLines = 1) }) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        val selectedIndex = groups.indexOf(selected)
                        OutlinedButton(onClick = { onMove(selected, -1) }, enabled = selectedIndex > 0) { Icon(Icons.Outlined.ArrowBack, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(tr("Earlier", "前移")) }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { onMove(selected, 1) }, enabled = selectedIndex in 0 until groups.lastIndex) { Text(tr("Later", "后移")); Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
                    }
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                        label = { Text(tr("Rename selected group", "重命名所选分组")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(tr("Renaming to an existing group merges both groups.", "重命名为已有分组会合并两个分组。"), color = MutedInk, fontSize = 11.sp)
                    TextButton(onClick = { onRename(selected, "") }) { Text(tr("Remove group and keep brushes", "移除分组并保留笔刷")) }
                }
            }
        },
        confirmButton = { Button(onClick = { onRename(selected, replacement) }, enabled = selected.isNotBlank() && BrushGroupOperations.normalize(replacement).isNotBlank()) { Text(tr("Rename", "重命名")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
}

@Composable
private fun BrushVersionHistoryDialog(
    brush: ImportedBrush,
    versions: List<BrushVersion>,
    onDismiss: () -> Unit,
    onRestore: (BrushVersion) -> Unit,
    onDelete: (BrushVersion) -> Unit,
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Version history", "版本历史")) },
        text = {
            Column(Modifier.widthIn(min = 340.dp, max = 480.dp)) {
                Text(brush.name, fontSize = 13.sp)
                Text(tr("Inkloom saves the previous preset before every brush edit.", "Inkloom 会在每次编辑笔刷前保存旧预设。"), color = MutedInk, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                if (versions.isEmpty()) {
                    Text(tr("No previous versions yet. Edit and save this brush to create one.", "还没有历史版本。编辑并保存此笔刷即可创建。"), color = MutedInk)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(versions, key = { it.versionId }) { version ->
                            OutlinedCard {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    BrushTipPreview(version.snapshot, Modifier.size(42.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(version.snapshot.name, maxLines = 1, fontSize = 13.sp)
                                        Text(formatter.format(Date(version.savedAt)), color = MutedInk, fontSize = 10.sp)
                                        Text(
                                            "${version.snapshot.defaultSize.oneDecimal()} px · ${(version.snapshot.defaultOpacity * 100f).toInt()}% ${tr("opacity", "不透明度")} · ${version.snapshot.spacing.oneDecimal()} ${tr("spacing", "间距")}",
                                            color = MutedInk,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                        )
                                    }
                                    TextButton(onClick = { onRestore(version) }) { Text(tr("Restore", "恢复")) }
                                    IconButton(onClick = { onDelete(version) }) { Icon(Icons.Outlined.Delete, tr("Delete this brush version", "删除此笔刷版本")) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
}

private fun Float.oneDecimal(): String = String.format(java.util.Locale.ROOT, "%.1f", this)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportedBrushTile(
    brush: ImportedBrush,
    editable: Boolean,
    selected: Boolean,
    multiSelecting: Boolean,
    multiSelected: Boolean,
    onToggleSelection: () -> Unit,
    onSelect: () -> Unit,
    onReport: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dragX by remember(brush.id) { mutableFloatStateOf(0f) }
    var dragY by remember(brush.id) { mutableFloatStateOf(0f) }
    val active = selected || multiSelected
    val tileDescription = "${tr("Brush preset", "笔刷预设")} ${brush.name}"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .semantics { contentDescription = tileDescription }
            .combinedClickable(
                onClick = { if (multiSelecting && editable) onToggleSelection() else onSelect() },
                onLongClick = onEdit,
            ),
        colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFFE1E1E1) else Color(0xFFF8F8F8)),
        border = CardDefaults.outlinedCardBorder().copy(
            width = if (active) 2.dp else 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(if (active) Ink else OutlineSoft),
        ),
        shape = RoundedCornerShape(5.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                BrushTipPreview(brush, Modifier.fillMaxSize())
                if (multiSelecting) {
                    Icon(
                        if (multiSelected) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        if (multiSelected) tr("Selected", "已选择") else tr("Not selected", "未选择"),
                        Modifier.align(Alignment.TopStart).padding(2.dp).size(17.dp).background(Color.White.copy(alpha = .85f), RoundedCornerShape(3.dp)),
                        tint = Ink,
                    )
                } else if (brush.favorite) {
                    Icon(Icons.Default.Star, tr("Favorite", "收藏"), Modifier.align(Alignment.TopStart).padding(2.dp).size(15.dp), tint = Ink)
                }
                if (editable) Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(24.dp).background(Color.White.copy(alpha = .86f), RoundedCornerShape(4.dp))) {
                        Icon(Icons.Default.MoreVert, tr("Brush actions", "笔刷操作"), Modifier.size(15.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Sub tool detail", "子工具详情")) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(if (brush.favorite) tr("Remove favorite", "取消收藏") else tr("Favorite", "收藏")) }, onClick = { menu = false; onFavorite() })
                        DropdownMenuItem(text = { Text(tr("Move to group", "移到分组")) }, onClick = { menu = false; onMove() })
                        DropdownMenuItem(text = { Text(tr("Version history", "版本历史")) }, onClick = { menu = false; onHistory() })
                        DropdownMenuItem(text = { Text(tr("Conversion report", "转换报告")) }, onClick = { menu = false; onReport() })
                        DropdownMenuItem(text = { Text(tr("Export Inkloom preset", "导出 Inkloom 预设")) }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text(if (brush.hidden) tr("Unhide", "取消隐藏") else tr("Hide", "隐藏")) }, onClick = { menu = false; onHide() })
                        DropdownMenuItem(text = { Text(tr("Remove from library", "从笔刷库移除")) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!multiSelecting && editable) Icon(
                    Icons.Outlined.DragIndicator,
                    tr("Drag to reorder brush", "拖动调整笔刷顺序"),
                    Modifier.size(18.dp).pointerInput(brush.id) {
                        detectDragGestures(
                            onDragStart = { dragX = 0f; dragY = 0f },
                            onDragEnd = {
                                val distance = if (kotlin.math.abs(dragX) > kotlin.math.abs(dragY)) dragX else dragY
                                val delta = (distance / 52.dp.toPx()).toInt()
                                if (delta != 0) onReorder(delta)
                                dragX = 0f; dragY = 0f
                            },
                            onDragCancel = { dragX = 0f; dragY = 0f },
        ) { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y }
                    },
                    tint = MutedInk,
                )
                Text(brush.name, maxLines = 2, fontSize = 9.sp, lineHeight = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportedBrushRow(
    brush: ImportedBrush,
    editable: Boolean,
    compact: Boolean = false,
    selected: Boolean,
    multiSelecting: Boolean,
    multiSelected: Boolean,
    onToggleSelection: () -> Unit,
    onSelect: () -> Unit,
    onReport: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dragDistance by remember(brush.id) { mutableFloatStateOf(0f) }
    val rowDescription = "${tr("Brush preset", "笔刷预设")} ${brush.name}"
    Card(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = rowDescription }.combinedClickable(
            onClick = { if (multiSelecting && editable) onToggleSelection() else onSelect() },
            onLongClick = onEdit,
        ),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected || multiSelected) Color(0xFFD6DCE8) else Color(0xFFF4F4F4)),
        border = CardDefaults.outlinedCardBorder().copy(width = 1.dp, brush = androidx.compose.ui.graphics.SolidColor(OutlineSoft)),
    ) {
        Row(Modifier.height(if (compact) 40.dp else 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (multiSelecting && editable) {
                Icon(if (multiSelected) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, if (multiSelected) tr("Selected", "已选择") else tr("Not selected", "未选择"), Modifier.size(24.dp), tint = if (multiSelected) Sage else MutedInk)
            } else if (editable) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    tr("Drag to reorder brush", "拖动调整笔刷顺序"),
                    Modifier.size(18.dp).pointerInput(brush.id) {
                        detectDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onDragEnd = {
                                val delta = (dragDistance / 56.dp.toPx()).toInt()
                                if (delta != 0) onReorder(delta)
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f },
                        ) { change, amount -> change.consume(); dragDistance += amount.y }
                    },
                    tint = MutedInk,
                )
            }
            Spacer(Modifier.width(3.dp))
            BrushTipPreview(brush, Modifier.width(if (compact) 42.dp else 52.dp).height(if (compact) 30.dp else 40.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(brush.name, maxLines = 1, fontSize = 11.sp)
                if (!compact) Text(listOf(brush.format.label, brush.groupName.takeIf(String::isNotBlank)).filterNotNull().joinToString(" · "), color = MutedInk, fontSize = 9.sp, maxLines = 1)
                Canvas(Modifier.fillMaxWidth().height(if (compact) 6.dp else 8.dp)) { drawLine(Ink, Offset(1f, size.height / 2), Offset(size.width - 1f, size.height / 2), strokeWidth = (brush.defaultSize / 9f).coerceIn(1.5f, 7f), cap = StrokeCap.Round) }
            }
            if (!multiSelecting && editable) {
                if (!compact) IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Tune, tr("Edit brush", "编辑笔刷"), modifier = Modifier.size(15.dp)) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, tr("Brush actions", "笔刷操作"), modifier = Modifier.size(15.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Conversion report", "转换报告")) }, onClick = { menu = false; onReport() })
                        DropdownMenuItem(text = { Text(tr("Version history", "版本历史")) }, onClick = { menu = false; onHistory() })
                        DropdownMenuItem(text = { Text(tr("Move to group", "移到分组")) }, onClick = { menu = false; onMove() })
                        DropdownMenuItem(text = { Text(tr("Export Inkloom preset", "导出 Inkloom 预设")) }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text(if (brush.hidden) tr("Unhide", "取消隐藏") else tr("Hide", "隐藏")) }, onClick = { menu = false; onHide() })
                        DropdownMenuItem(text = { Text(tr("Remove from library", "从笔刷库移除")) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun BrushTipPreview(brush: ImportedBrush, modifier: Modifier = Modifier) {
    BrushStrokePreview(brush, LocalUiLanguage.current, modifier)
}

@Composable
private fun BrushSettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    val name = localizedBrushLabel(label)
    val description = tr("$name slider", "$name 滑块")
    Column { Row { Text(name, Modifier.weight(1f), fontSize = 12.sp); Text(if (range.endInclusive > 10f) "%.1f".format(value) else "%.2f".format(value), color = MutedInk, fontSize = 11.sp) }; Slider(value, onValue, valueRange = range, modifier = Modifier.semantics { contentDescription = description }) }
}
@Composable
private fun SettingsDialog(
    settings: AppSettings,
    brushes: List<ImportedBrush>,
    brushGroups: List<String>,
    onChange: (AppSettings) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onExportShortcuts: () -> Unit,
    onImportShortcuts: () -> Unit,
    onDismiss: () -> Unit,
) {
    var shortcutEditorOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
        title = { Text(tr("Inkloom Settings", "Inkloom 设置")) },
        text = {
            LazyColumn(Modifier.widthIn(min = 320.dp, max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(tr("LANGUAGE", "语言"), color = Sage, fontSize = 12.sp) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = settings.language == UiLanguage.ENGLISH, onClick = { onChange(settings.copy(language = UiLanguage.ENGLISH)) }, label = { Text("English") })
                    FilterChip(selected = settings.language == UiLanguage.SIMPLIFIED_CHINESE, onClick = { onChange(settings.copy(language = UiLanguage.SIMPLIFIED_CHINESE)) }, label = { Text("简体中文") })
                } }
                item { Text(tr("INPUT", "输入"), color = Sage, fontSize = 12.sp) }
                item { SettingSwitch(tr("Draw with finger", "手指书写"), tr("Off by default. Fingers navigate and zoom.", "默认关闭；手指用于移动和缩放。"), settings.fingerDrawing) { onChange(settings.copy(fingerDrawing = it)) } }
                item { SettingSwitch(tr("Draw and hold", "停笔规整"), tr("Hold the pen down to regularize a line or curve. Keep holding to adjust its endpoint.", "画完线条后停住笔尖，规整成直线或曲线；继续按住可调整端点。"), settings.drawAndHoldEnabled) { onChange(settings.copy(drawAndHoldEnabled = it)) } }
                if (settings.drawAndHoldEnabled) item {
                    Text(tr("Hold delay · ${settings.drawAndHoldDelayMillis} ms", "停笔时间 · ${settings.drawAndHoldDelayMillis} 毫秒"), fontSize = 12.sp)
                    Slider(settings.drawAndHoldDelayMillis.toFloat(), { onChange(settings.copy(drawAndHoldDelayMillis = it.toLong())) },
                        valueRange = 300f..1500f, steps = 23, modifier = Modifier.semantics { contentDescription = "Draw and hold delay" })
                }
                item { SettingSwitch(tr("Single-finger double tap to undo", "单指双击撤销"), tr("Enabled by default. Only blank-canvas taps trigger it; drawing, dragging and links are excluded.", "默认开启；仅空白画布短触生效，书写、拖动和链接均不会触发。"), settings.singleFingerDoubleTapUndo) { onChange(settings.copy(singleFingerDoubleTapUndo = it)) } }
                item { SettingSwitch(tr("Two-finger tap to undo", "双指轻点撤销"), tr("Matches Procreate and Clip Studio Paint.", "与 Procreate 和 Clip Studio Paint 的手势一致。"), settings.twoFingerDoubleTapUndo) { onChange(settings.copy(twoFingerDoubleTapUndo = it)) } }
                item { SettingSwitch(tr("Three-finger tap to redo", "三指轻点重做"), tr("Matches common drawing-app gestures.", "符合常见绘画应用手势。"), settings.threeFingerTapRedo) { onChange(settings.copy(threeFingerTapRedo = it)) } }
                item { SettingSwitch(tr("Two-finger canvas rotation", "双指旋转画布"), tr("Pinch, pan, and twist the canvas naturally.", "用双指自然地缩放、平移和旋转画布。"), settings.twoFingerRotation) { onChange(settings.copy(twoFingerRotation = it)) } }
                item { SettingSwitch(tr("Show input diagnostics", "显示输入诊断"), tr("Displays stylus data plus Toolbox keys, scan codes, rotary axes, pointer buttons, source and device identity.", "显示手写笔数据，以及 Toolbox 的按键、扫描码、旋钮轴、侧键、来源和设备标识。"), settings.showInputDiagnostics) { onChange(settings.copy(showInputDiagnostics = it)) } }
                if (app.inkloom.BuildConfig.PAINTING_DIAGNOSTICS) {
                    item { SettingSwitch(tr("Show V5 stroke diagnostics", "显示 V5 落笔诊断"), tr("Debug only: shows the active V5 transaction, stable generation, dirty tiles and frame counters.", "仅调试版：显示 V5 活动事务、稳定代数、脏瓦片与帧计数。"), settings.showStrokeTraceOverlay) { onChange(settings.copy(showStrokeTraceOverlay = it)) } }
                }
                item {
                    Text(tr("Global brush stabilization", "全局画笔防抖"), fontWeight = FontWeight.SemiBold)
                    Text(tr("Applies before each brush’s own stabilization. Set to 0 to use only the brush setting.",
                        "先应用全局防抖，再应用每支笔的防抖。设为 0 时，只使用笔刷自身的设置。"), fontSize = 12.sp, color = MutedInk)
                    BrushSettingSlider(tr("Global stabilization (%)", "全局防抖强度（%）"), settings.globalStrokeStabilization * 100f, 0f..100f) {
                        onChange(settings.copy(globalStrokeStabilization = it / 100f))
                    }
                }
                item { Text("${tr("Pressure sensitivity", "压感灵敏度")}  ${"%.2f".format(settings.pressureSensitivity)}×") }
                item { Slider(settings.pressureSensitivity, { onChange(settings.copy(pressureSensitivity = it)) }, valueRange = .25f..2.5f) }
                item { Text("${tr("Pressure onset", "起始压力")}  ${(settings.pressureMinimumInput * 100).toInt()}%", color = MutedInk, fontSize = 12.sp) }
                item { Slider(settings.pressureMinimumInput, { value -> onChange(settings.copy(pressureMinimumInput = value.coerceAtMost(settings.pressureMaximumInput - .05f))) }, valueRange = 0f..0.95f) }
                item { Text("${tr("Full pressure point", "满压位置")}  ${(settings.pressureMaximumInput * 100).toInt()}%", color = MutedInk, fontSize = 12.sp) }
                item { Slider(settings.pressureMaximumInput, { value -> onChange(settings.copy(pressureMaximumInput = value.coerceAtLeast(settings.pressureMinimumInput + .05f))) }, valueRange = 0.05f..1f) }
                item { PressureCurveEditor(settings.pressureResponseCurve) { onChange(settings.copy(pressureResponseCurve = it)) } }
                item {
                    PressureScratchpad(settings)
                    TextButton(onClick = { onChange(settings.copy(pressureSensitivity = 1f, pressureMinimumInput = 0f, pressureMaximumInput = 1f, pressureResponseCurve = PressureResponseCurve.LINEAR)) }) {
                        Text(tr("Reset pressure settings", "重置压感设置"))
                    }
                }
                item { Text(tr("PEN BUTTONS", "笔按键"), color = Sage, fontSize = 12.sp) }
                item { Text(tr("Hold temporarily switches tools and restores the previous brush, size, opacity and color on release. Tap keeps the selected tool active. The pen-tail eraser always erases only while touching the screen.", "“按住”会临时切换工具，松开后恢复原笔刷、大小、不透明度和颜色；“单击”会保持切换后的工具。笔尾橡皮只在接触屏幕时擦除。"), color = MutedInk, fontSize = 11.sp) }
                item { StylusActionRow(tr("Primary button", "主按键"), settings.stylusPrimaryButton, settings.stylusPrimaryTrigger, { onChange(settings.copy(stylusPrimaryButton = it)) }, { onChange(settings.copy(stylusPrimaryTrigger = it)) }) }
                item { StylusActionRow(tr("Secondary button", "副按键"), settings.stylusSecondaryButton, settings.stylusSecondaryTrigger, { onChange(settings.copy(stylusSecondaryButton = it)) }, { onChange(settings.copy(stylusSecondaryTrigger = it)) }) }
                item { Text(tr("TOOLBOX & KEYBOARD", "TOOLBOX 与键盘"), color = Sage, fontSize = 12.sp) }
                item { Text(tr("CSP-compatible defaults are active. Customize them for Wacom ExpressKeys, a keyboard, or another Toolbox controller.", "已启用兼容 CSP 的默认键位。可为 Wacom ExpressKey、键盘或其他 Toolbox 控制器自定义。"), color = MutedInk, fontSize = 12.sp) }
                item { OutlinedButton(onClick = { shortcutEditorOpen = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Keyboard, null); Spacer(Modifier.width(8.dp)); Text(tr("Customize shortcuts", "自定义快捷键")) } }
                item { Text(tr("INTERFACE", "界面"), color = Sage, fontSize = 12.sp) }
                item { SettingSwitch(tr("Show page sidebar", "显示页面侧栏"), null, settings.showPageRail) { onChange(settings.copy(showPageRail = it)) } }
                item { SettingSwitch(tr("Show floating toolbar", "显示浮动工具栏"), null, settings.showToolbar) { onChange(settings.copy(showToolbar = it)) } }
                item { Text(tr("Toolbar docking", "工具栏停靠"), color = MutedInk, fontSize = 11.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ToolbarDock.entries.forEach { dock -> item { FilterChip(selected = settings.toolbarDock == dock, onClick = { onChange(settings.copy(toolbarDock = dock, verticalToolbar = if (dock in setOf(ToolbarDock.LEFT, ToolbarDock.RIGHT)) true else if (dock in setOf(ToolbarDock.TOP, ToolbarDock.BOTTOM)) false else settings.verticalToolbar)) }, label = { Text(when (dock) { ToolbarDock.FREE -> tr("Free", "自由"); ToolbarDock.TOP -> tr("Top", "顶部"); ToolbarDock.BOTTOM -> tr("Bottom", "底部"); ToolbarDock.LEFT -> tr("Left", "左侧"); ToolbarDock.RIGHT -> tr("Right", "右侧") }) }) } } } }
                if (settings.toolbarDock == ToolbarDock.FREE) item { SettingSwitch(tr("Vertical toolbar", "垂直工具栏"), tr("Useful on a wide tablet canvas.", "适合宽屏平板画布。"), settings.verticalToolbar) { onChange(settings.copy(verticalToolbar = it)) } }
                item { OutlinedButton(onClick = { onChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = 16f, toolbarOffsetYDp = 16f)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CenterFocusStrong, null); Spacer(Modifier.width(8.dp)); Text(tr("Reset toolbar position", "重置工具栏位置")) } }
                item { Text(tr("DATA & BACKUP", "数据与备份"), color = Sage, fontSize = 12.sp) }
                item { Text(tr("A full backup includes notes, folders, imported brushes and sources, PDFs, images, input settings, panel layouts, Toolbox shortcuts, quick slots and the custom color palette. Content is restored with new IDs so existing work is never overwritten.", "完整备份包含笔记、文件夹、导入笔刷及源文件、PDF、图片、输入设置、面板布局、Toolbox 快捷键、快速槽与自定义色板。内容恢复时会生成新 ID，不覆盖现有作品。"), color = MutedInk, fontSize = 12.sp) }
                item { Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Backup, null); Spacer(Modifier.width(8.dp)); Text(tr("Export full library backup", "导出完整资料库备份")) } }
                item { OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(8.dp)); Text(tr("Restore library backup", "恢复资料库备份")) } }
            }
        },
    )
    if (shortcutEditorOpen) ShortcutEditorDialog(settings, brushes, brushGroups, onChange, onExportShortcuts, onImportShortcuts, onDismiss = { shortcutEditorOpen = false })
}

@Composable
private fun ShortcutEditorDialog(settings: AppSettings, brushes: List<ImportedBrush>, brushGroups: List<String>, onChange: (AppSettings) -> Unit, onExportShortcuts: () -> Unit, onImportShortcuts: () -> Unit, onDismiss: () -> Unit) {
    var listeningFor by remember { mutableStateOf<ShortcutAction?>(null) }
    var captureMode by remember { mutableStateOf(ShortcutMatchMode.KEY_CODE) }
    var captureTrigger by remember { mutableStateOf(ShortcutTrigger.PRESS) }
    var deviceSpecific by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ShortcutCategory?>(null) }
    var quickSlotsOpen by remember { mutableStateOf(false) }
    val language = LocalUiLanguage.current
    val groupedActions = ShortcutCategory.entries.mapNotNull { category ->
        if (selectedCategory != null && selectedCategory != category) return@mapNotNull null
        val matches = ShortcutAction.entries.filter { action ->
            action.category() == category && (search.isBlank() || listOf(
                shortcutActionDisplayLabel(action, settings, language),
                shortcutCategoryLabel(category, language),
                action.name,
                settings.shortcutBindings.filter { it.action == action }.joinToString(" ") { CspShortcutResolver.display(it.chord) },
            ).any { it.contains(search.trim(), ignoreCase = true) })
        }
        matches.takeIf(List<ShortcutAction>::isNotEmpty)?.let { category to it }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(listeningFor) { if (listeningFor != null) focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Toolbox shortcuts", "Toolbox 快捷键")) },
        text = {
            LazyColumn(
                Modifier.widthIn(min = 400.dp, max = 580.dp).heightIn(max = 540.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val action = listeningFor ?: return@onPreviewKeyEvent false
                        val native = event.nativeKeyEvent
                        if (native.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                        if (native.keyCode in setOf(android.view.KeyEvent.KEYCODE_CTRL_LEFT, android.view.KeyEvent.KEYCODE_CTRL_RIGHT, android.view.KeyEvent.KEYCODE_ALT_LEFT, android.view.KeyEvent.KEYCODE_ALT_RIGHT, android.view.KeyEvent.KEYCODE_SHIFT_LEFT, android.view.KeyEvent.KEYCODE_SHIFT_RIGHT)) return@onPreviewKeyEvent true
                        if (native.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE || native.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                            listeningFor = null
                            return@onPreviewKeyEvent true
                        }
                        val effectiveMode = if (captureMode == ShortcutMatchMode.SCAN_CODE && native.scanCode > 0) ShortcutMatchMode.SCAN_CODE else ShortcutMatchMode.KEY_CODE
                        val chord = ShortcutChord(native.keyCode, native.isCtrlPressed, native.isAltPressed, native.isShiftPressed, native.scanCode, effectiveMode)
                        val device = native.device.takeIf { deviceSpecific }
                        onChange(settings.copy(shortcutBindings = CspShortcutResolver.replace(settings.shortcutBindings, action, chord, captureTrigger, device?.descriptor, device?.name)))
                        listeningFor = null
                        true
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { Text(tr("Tap Change, then press a Toolbox or keyboard combination. Rotary, touch-ring, wheel and pointer-button mappings are configured below.", "点击“更改”，然后按下 Toolbox 或键盘组合键。旋钮、触控环、滚轮与侧键映射可在下方配置。"), color = MutedInk, fontSize = 12.sp) }
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        placeholder = { Text(tr("Search actions or keys", "搜索操作或按键")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text(tr("All", "全部")) }) }
                        items(ShortcutCategory.entries, key = { it.name }) { category ->
                            FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = if (selectedCategory == category) null else category }, label = { Text(shortcutCategoryLabel(category, language)) })
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("Capture mode", "捕获方式"), color = MutedInk, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = captureMode == ShortcutMatchMode.KEY_CODE, onClick = { captureMode = ShortcutMatchMode.KEY_CODE }, label = { Text(tr("Logical key", "逻辑键")) })
                            FilterChip(selected = captureMode == ShortcutMatchMode.SCAN_CODE, onClick = { captureMode = ShortcutMatchMode.SCAN_CODE }, label = { Text(tr("Physical scan code", "物理扫描码")) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = captureTrigger == ShortcutTrigger.PRESS, onClick = { captureTrigger = ShortcutTrigger.PRESS }, label = { Text(tr("Tap to switch", "单击切换")) })
                            FilterChip(selected = captureTrigger == ShortcutTrigger.HOLD, onClick = { captureTrigger = ShortcutTrigger.HOLD }, label = { Text(tr("Hold, release to return", "按住，松开返回")) })
                        }
                        SettingSwitch(tr("Only this input device", "仅此输入设备"), tr("Keeps the global CSP key while adding a Toolbox-specific mapping.", "保留全局 CSP 键位，同时添加此 Toolbox 的专用映射。"), deviceSpecific) { deviceSpecific = it }
                    }
                }
                item { Text(tr("ROTARY & SCROLL", "旋钮与滚轮"), color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                ToolboxAxis.entries.forEach { axis ->
                    item("axis-${axis.name}") { ToolboxAxisBindingRow(axis, settings, onChange) }
                }
                item { Text(tr("POINTER / TOOLBOX BUTTONS", "鼠标／TOOLBOX 按钮"), color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                listOf(android.view.MotionEvent.BUTTON_PRIMARY, android.view.MotionEvent.BUTTON_SECONDARY, android.view.MotionEvent.BUTTON_TERTIARY, android.view.MotionEvent.BUTTON_BACK, android.view.MotionEvent.BUTTON_FORWARD).forEachIndexed { index, button ->
                    item("motion-button-$button") { ToolboxButtonBindingRow(index + 1, button, settings, onChange) }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("BRUSH GROUP TARGETS", "笔刷组目标"), color = MutedInk, fontSize = 11.sp)
                        Text(tr("Choose the real groups used by CSP-style P and B shortcuts. Automatic keeps name-based detection.", "选择 CSP 风格 P、B 快捷键实际使用的笔刷组；“自动”会继续按名称识别。"), color = MutedInk, fontSize = 11.sp)
                        ShortcutBrushGroup.entries.forEach { group ->
                            ShortcutBrushGroupTargetRow(
                                group = group,
                                selectedPath = settings.shortcutBrushGroupTargets.forGroup(group),
                                groupPaths = brushGroups,
                                onSelect = { path ->
                                    onChange(settings.copy(shortcutBrushGroupTargets = settings.shortcutBrushGroupTargets.withGroup(group, path)))
                                },
                            )
                        }
                        OutlinedButton(onClick = { quickSlotsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.DashboardCustomize, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Configure 8 quick slots", "配置 8 个快速槽"))
                        }
                    }
                }
                groupedActions.forEach { (category, actions) ->
                    item("category-${category.name}") { Text(shortcutCategoryLabel(category, language).uppercase(), color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                    items(actions, key = { it.name }) { action ->
                        val actionBindings = settings.shortcutBindings.filter { it.action == action }
                        val defaultBindings = CspShortcutResolver.defaultBindings.filter { it.action == action }
                        val chords = actionBindings.map { binding ->
                            buildString {
                                append(CspShortcutResolver.display(binding.chord))
                                if (binding.trigger == ShortcutTrigger.HOLD) append(tr(" · hold", " · 按住"))
                                binding.deviceName?.let { append(" · "); append(it) }
                            }
                        }
                        ListItem(
                            headlineContent = { Text(shortcutActionDisplayLabel(action, settings, language), fontSize = 13.sp) },
                            supportingContent = { Text(if (listeningFor == action) tr("Press a key combination…", "请按组合键…") else chords.joinToString(" / ").ifBlank { tr("Not assigned", "未分配") }, color = if (listeningFor == action) Sage else MutedInk) },
                            trailingContent = {
                                Row {
                                    if (chords.isNotEmpty()) IconButton(onClick = { onChange(settings.copy(shortcutBindings = settings.shortcutBindings.filterNot { it.action == action })) }) { Icon(Icons.Outlined.LinkOff, tr("Remove shortcut", "移除快捷键")) }
                                    if (actionBindings != defaultBindings) IconButton(onClick = { onChange(settings.copy(shortcutBindings = CspShortcutResolver.restoreActionDefaults(settings.shortcutBindings, action))) }) { Icon(Icons.Outlined.RestartAlt, tr("Reset this action", "重置此操作")) }
                                    TextButton(onClick = {
                                        captureMode = ShortcutMatchMode.SCAN_CODE
                                        captureTrigger = ShortcutTrigger.PRESS
                                        deviceSpecific = true
                                        listeningFor = action
                                    }) { Text(tr("Add device", "添加设备")) }
                                    TextButton(onClick = {
                                        actionBindings.firstOrNull()?.let { existing ->
                                            captureMode = existing.chord.matchMode
                                            captureTrigger = existing.trigger
                                            deviceSpecific = existing.deviceDescriptor != null
                                        }
                                        listeningFor = action
                                    }) { Text(tr("Change", "更改")) }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = if (listeningFor == action) SageContainer else Color.Transparent),
                        )
                    }
                }
                item { OutlinedButton(onClick = { onChange(settings.copy(shortcutBindings = CspShortcutResolver.defaultBindings)); listeningFor = null }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(tr("Restore CSP defaults", "恢复 CSP 默认键位")) } }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImportShortcuts, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(6.dp)); Text(tr("Import profile", "导入配置")) }
                        OutlinedButton(onClick = onExportShortcuts, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.SaveAlt, null); Spacer(Modifier.width(6.dp)); Text(tr("Export profile", "导出配置")) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
    if (quickSlotsOpen) ToolboxQuickSlotEditorDialog(settings, brushes, brushGroups, onChange, onDismiss = { quickSlotsOpen = false })
}

@Composable
private fun ToolboxAxisBindingRow(axis: ToolboxAxis, settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val binding = settings.shortcutAxisBindings.firstOrNull { it.axis == axis && it.deviceDescriptor == null }
    val deviceBindings = settings.shortcutAxisBindings.count { it.axis == axis && it.deviceDescriptor != null }
    val label = when (axis) {
        ToolboxAxis.ROTARY -> tr("Rotary / touch ring", "旋钮／触控环")
        ToolboxAxis.VERTICAL_SCROLL -> tr("Vertical wheel", "垂直滚轮")
        ToolboxAxis.HORIZONTAL_SCROLL -> tr("Horizontal wheel", "水平滚轮")
    }
    val modeLabel = when (binding?.mode) {
        ToolboxAxisMode.BRUSH_SIZE -> tr("Brush size", "笔刷大小")
        ToolboxAxisMode.OPACITY -> tr("Opacity", "不透明度")
        ToolboxAxisMode.ZOOM -> tr("Canvas zoom", "画布缩放")
        null -> tr("Not assigned", "未分配")
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToolboxQuickSlotDropdown(
            label = label,
            selected = modeLabel,
            choices = listOf(
                "" to tr("Not assigned", "未分配"),
                ToolboxAxisMode.BRUSH_SIZE.name to tr("Brush size", "笔刷大小"),
                ToolboxAxisMode.OPACITY.name to tr("Opacity", "不透明度"),
                ToolboxAxisMode.ZOOM.name to tr("Canvas zoom", "画布缩放"),
            ),
        ) { selected ->
            val retained = settings.shortcutAxisBindings.filterNot { it.axis == axis && it.deviceDescriptor == null }
            val updated = selected.takeIf(String::isNotBlank)?.let { mode ->
                retained + AxisShortcutBinding(axis, enumValueOf(mode), reversed = binding?.reversed ?: false, sensitivity = binding?.sensitivity ?: 1f, deadZone = binding?.deadZone ?: .01f)
            } ?: retained
            onChange(settings.copy(shortcutAxisBindings = updated))
        }
        if (binding != null) {
            SettingSwitch(tr("Reverse direction", "反转方向"), null, binding.reversed) {
                onChange(settings.copy(shortcutAxisBindings = ToolboxMotionResolver.replaceAxis(settings.shortcutAxisBindings, binding.copy(reversed = it))))
            }
            BrushSettingSlider(tr("Axis sensitivity", "轴灵敏度"), binding.sensitivity, .25f..4f) {
                onChange(settings.copy(shortcutAxisBindings = ToolboxMotionResolver.replaceAxis(settings.shortcutAxisBindings, binding.copy(sensitivity = it))))
            }
        }
        if (deviceBindings > 0) Text(tr("$deviceBindings device-specific mapping(s) retained", "已保留 $deviceBindings 个设备专用映射"), color = MutedInk, fontSize = 10.sp)
    }
}

@Composable
private fun ToolboxButtonBindingRow(index: Int, button: Int, settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val language = LocalUiLanguage.current
    val binding = settings.shortcutButtonBindings.firstOrNull { it.button == button && it.deviceDescriptor == null }
    val deviceBindings = settings.shortcutButtonBindings.count { it.button == button && it.deviceDescriptor != null }
    val label = tr("Button $index", "按钮 $index")
    val selected = binding?.let { shortcutActionDisplayLabel(it.action, settings, language) } ?: tr("Not assigned", "未分配")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToolboxQuickSlotDropdown(
            label = label,
            selected = selected,
            choices = listOf("" to tr("Not assigned", "未分配")) + ShortcutAction.entries.map { action -> action.name to shortcutActionDisplayLabel(action, settings, language) },
        ) { selectedAction ->
            val retained = settings.shortcutButtonBindings.filterNot { it.button == button && it.deviceDescriptor == null }
            val updated = selectedAction.takeIf(String::isNotBlank)?.let { action ->
                retained + ButtonShortcutBinding(button, enumValueOf(action), binding?.trigger ?: ShortcutTrigger.PRESS)
            } ?: retained
            onChange(settings.copy(shortcutButtonBindings = updated))
        }
        if (binding != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = binding.trigger == ShortcutTrigger.PRESS, onClick = { onChange(settings.copy(shortcutButtonBindings = ToolboxMotionResolver.replaceButton(settings.shortcutButtonBindings, binding.copy(trigger = ShortcutTrigger.PRESS)))) }, label = { Text(tr("Tap", "单击")) })
                FilterChip(selected = binding.trigger == ShortcutTrigger.HOLD, onClick = { onChange(settings.copy(shortcutButtonBindings = ToolboxMotionResolver.replaceButton(settings.shortcutButtonBindings, binding.copy(trigger = ShortcutTrigger.HOLD)))) }, label = { Text(tr("Hold / release", "按住／松开")) })
            }
        }
        if (deviceBindings > 0) Text(tr("$deviceBindings device-specific mapping(s) retained", "已保留 $deviceBindings 个设备专用映射"), color = MutedInk, fontSize = 10.sp)
    }
}

@Composable
private fun ShortcutBrushGroupTargetRow(group: ShortcutBrushGroup, selectedPath: String, groupPaths: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (group) {
        ShortcutBrushGroup.PEN -> tr("Pen", "钢笔")
        ShortcutBrushGroup.PENCIL -> tr("Pencil", "铅笔")
        ShortcutBrushGroup.BRUSH -> "Brushes"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(84.dp), fontSize = 12.sp)
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedPath.ifBlank { tr("Automatic", "自动") }, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(tr("Automatic", "自动")) },
                    onClick = { onSelect(""); expanded = false },
                )
                groupPaths.forEach { path ->
                    DropdownMenuItem(
                        text = { Text(path) },
                        onClick = { onSelect(path); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolboxQuickSlotEditorDialog(
    settings: AppSettings,
    brushes: List<ImportedBrush>,
    brushGroups: List<String>,
    onChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val slots = ToolboxQuickSlots.normalize(settings.toolboxQuickSlots)
    val slot = slots[selectedIndex]
    fun updateSlot(value: ToolboxQuickSlot) {
        val updated = slots.toMutableList().also { it[selectedIndex] = ToolboxQuickSlots.normalize(value) }
        onChange(settings.copy(toolboxQuickSlots = updated))
    }
    var colorText by remember(selectedIndex, slot.colorArgb) { mutableStateOf("#%06X".format(slot.colorArgb and 0xFFFFFF)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Toolbox quick slots", "Toolbox 快速槽")) },
        text = {
            LazyColumn(Modifier.widthIn(min = 380.dp, max = 560.dp).heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        tr("Bind a Toolbox key to Quick slot 1–8 in the shortcut list, then choose what the slot restores here.", "先在快捷键列表中为快速槽 1–8 绑定 Toolbox 按键，再在此选择快速槽恢复的内容。"),
                        color = MutedInk,
                        fontSize = 12.sp,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ToolboxQuickSlots.COUNT) { index ->
                            FilterChip(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                label = { Text(tr("Slot ${index + 1}", "槽 ${index + 1}")) },
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = slot.label,
                        onValueChange = { updateSlot(slot.copy(label = it)) },
                        label = { Text(tr("Slot label", "快速槽名称")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(tr("RESTORE", "恢复内容"), color = MutedInk, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ToolboxQuickSlotKind.entries, key = { it.name }) { kind ->
                            FilterChip(
                                selected = slot.kind == kind,
                                onClick = { updateSlot(slot.copy(kind = kind)) },
                                label = { Text(toolboxQuickSlotKindLabel(kind)) },
                            )
                        }
                    }
                }
                when (slot.kind) {
                    ToolboxQuickSlotKind.EMPTY -> item { Text(tr("This slot is empty. Its shortcut is ignored safely.", "此快速槽为空；对应快捷键会被安全忽略。"), color = MutedInk, fontSize = 12.sp) }
                    ToolboxQuickSlotKind.BRUSH -> item {
                        ToolboxQuickSlotDropdown(
                            label = tr("Brush", "笔刷"),
                            selected = brushes.firstOrNull { it.id == slot.brushId }?.name.orEmpty(),
                            choices = brushes.filterNot(ImportedBrush::hidden).map { it.id to "${it.groupName.ifBlank { tr("Ungrouped", "未分组") }} / ${it.name}" },
                            onSelect = { updateSlot(slot.copy(brushId = it)) },
                        )
                    }
                    ToolboxQuickSlotKind.BRUSH_GROUP -> item {
                        ToolboxQuickSlotDropdown(
                            label = tr("Brush group", "笔刷组"),
                            selected = slot.groupPath,
                            choices = brushGroups.map { it to it },
                            onSelect = { updateSlot(slot.copy(groupPath = it)) },
                        )
                    }
                    ToolboxQuickSlotKind.TOOL_PRESET -> {
                        item {
                            ToolboxQuickSlotDropdown(
                                label = tr("Brush", "笔刷"),
                                selected = brushes.firstOrNull { it.id == slot.brushId }?.name ?: tr("System pen", "系统钢笔"),
                                choices = listOf("" to tr("System pen", "系统钢笔")) + brushes.filterNot(ImportedBrush::hidden).map { it.id to "${it.groupName.ifBlank { tr("Ungrouped", "未分组") }} / ${it.name}" },
                                onSelect = { updateSlot(slot.copy(brushId = it)) },
                            )
                        }
                        item { BrushSettingSlider(tr("Size", "大小"), slot.size, .5f..400f) { updateSlot(slot.copy(size = it)) } }
                        item { BrushSettingSlider(tr("Opacity", "不透明度"), slot.opacity, .05f..1f) { updateSlot(slot.copy(opacity = it)) } }
                        item {
                            OutlinedTextField(
                                value = colorText,
                                onValueChange = { raw ->
                                    colorText = raw.take(9)
                                    val hex = raw.removePrefix("#")
                                    if (hex.length == 6) hex.toLongOrNull(16)?.let { rgb -> updateSlot(slot.copy(colorArgb = (0xFF000000L or rgb).toInt())) }
                                    if (hex.length == 8) hex.toLongOrNull(16)?.let { argb -> updateSlot(slot.copy(colorArgb = argb.toInt())) }
                                },
                                label = { Text(tr("Color (#RRGGBB)", "颜色（#RRGGBB）")) },
                                singleLine = true,
                                leadingIcon = { Box(Modifier.size(20.dp).background(Color(slot.colorArgb), CircleShape).border(1.dp, OutlineSoft, CircleShape)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { updateSlot(ToolboxQuickSlot()) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Clear, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Clear this slot", "清空此快速槽"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
}

@Composable
private fun ToolboxQuickSlotDropdown(label: String, selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = choices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label quick slot value" },
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = MutedInk, fontSize = 10.sp)
                Text(selected.ifBlank { tr("Choose…", "请选择…") }, maxLines = 1)
            }
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (value, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun toolboxQuickSlotKindLabel(kind: ToolboxQuickSlotKind): String = when (kind) {
    ToolboxQuickSlotKind.EMPTY -> tr("Empty", "空")
    ToolboxQuickSlotKind.BRUSH -> tr("Brush", "笔刷")
    ToolboxQuickSlotKind.BRUSH_GROUP -> tr("Group", "笔刷组")
    ToolboxQuickSlotKind.TOOL_PRESET -> tr("Tool preset", "工具预设")
}

private fun shortcutCategoryLabel(category: ShortcutCategory, language: UiLanguage): String = when (category) {
    ShortcutCategory.QUICK_ACCESS -> language.text("Quick access", "快速访问")
    ShortcutCategory.TOOLS -> language.text("Tools", "工具")
    ShortcutCategory.BRUSH_GROUPS -> language.text("Brush groups", "笔刷组")
    ShortcutCategory.BRUSH_ADJUSTMENTS -> language.text("Brush adjustments", "笔刷调节")
    ShortcutCategory.EDITING -> language.text("Editing", "编辑")
    ShortcutCategory.CANVAS -> language.text("Canvas", "画布")
    ShortcutCategory.PAGES -> language.text("Pages", "页面")
    ShortcutCategory.LAYERS -> language.text("Layers", "图层")
    ShortcutCategory.PANELS -> language.text("Panels", "面板")
    ShortcutCategory.FILE -> language.text("File", "文件")
}

private fun shortcutActionLabel(action: ShortcutAction, language: UiLanguage): String = when (action) {
    ShortcutAction.PEN_OR_PENCIL_GROUP -> language.text("Switch pen / pencil group", "切换钢笔／铅笔组")
    ShortcutAction.PEN_GROUP -> language.text("Switch directly to pen group", "直接切换到钢笔组")
    ShortcutAction.PENCIL_GROUP -> language.text("Switch directly to pencil group", "直接切换到铅笔组")
    ShortcutAction.BRUSH_GROUP -> language.text("Switch brush group", "切换 Brushes 组")
    ShortcutAction.TOGGLE_ERASER -> language.text("Toggle eraser and previous tool", "橡皮与上一工具切换")
    ShortcutAction.HIGHLIGHTER_TOOL -> language.text("Marker / highlighter", "标记笔／荧光笔")
    ShortcutAction.SELECT_TOOL -> language.text("Selection tool", "选择工具")
    ShortcutAction.HAND_TOOL -> language.text("Hand / canvas navigation", "抓手／画布导航")
    ShortcutAction.ROTATE_VIEW_TOOL -> language.text("Rotate canvas view", "旋转画布视图")
    ShortcutAction.EYEDROPPER_TOOL -> language.text("Eyedropper", "吸管")
    ShortcutAction.SHAPE_TOOL -> language.text("Shape tool", "形状工具")
    ShortcutAction.TEXT_TOOL -> language.text("Text tool", "文字工具")
    ShortcutAction.NOTE_TOOL -> language.text("Sticky note", "便笺")
    ShortcutAction.LINK_TOOL -> language.text("Link", "链接")
    ShortcutAction.IMAGE_TOOL -> language.text("Insert image", "插入图片")
    ShortcutAction.LASER_TOOL -> language.text("Laser pointer", "激光笔")
    ShortcutAction.BRUSH_SIZE_DECREASE -> language.text("Decrease brush size", "减小笔刷大小")
    ShortcutAction.BRUSH_SIZE_INCREASE -> language.text("Increase brush size", "增大笔刷大小")
    ShortcutAction.OPACITY_DECREASE -> language.text("Decrease opacity", "降低不透明度")
    ShortcutAction.OPACITY_INCREASE -> language.text("Increase opacity", "提高不透明度")
    ShortcutAction.PREVIOUS_BRUSH -> language.text("Previous brush", "上一个笔刷")
    ShortcutAction.NEXT_BRUSH -> language.text("Next brush", "下一个笔刷")
    ShortcutAction.CUT_SELECTION -> language.text("Cut selection", "剪切选区")
    ShortcutAction.COPY_SELECTION -> language.text("Copy selection", "复制选区")
    ShortcutAction.PASTE_SELECTION -> language.text("Paste selection", "粘贴选区")
    ShortcutAction.DELETE_SELECTION -> language.text("Delete selection", "删除选区")
    ShortcutAction.SELECT_ALL -> language.text("Select all ink strokes", "选择全部墨迹")
    ShortcutAction.DESELECT -> language.text("Deselect", "取消选择")
    ShortcutAction.UNDO -> language.text("Undo", "撤销")
    ShortcutAction.REDO -> language.text("Redo", "重做")
    ShortcutAction.NEW_LAYER -> language.text("New raster layer", "新建栅格图层")
    ShortcutAction.MERGE_LAYER_DOWN -> language.text("Merge with layer below", "与下一图层合并")
    ShortcutAction.SELECT_LAYER_ABOVE -> language.text("Select layer above", "选择上一图层")
    ShortcutAction.SELECT_LAYER_BELOW -> language.text("Select layer below", "选择下一图层")
    ShortcutAction.ZOOM_OUT -> language.text("Zoom out", "缩小")
    ShortcutAction.ZOOM_IN -> language.text("Zoom in", "放大")
    ShortcutAction.ROTATE_VIEW_LEFT -> language.text("Rotate view left 5°", "视图向左旋转 5°")
    ShortcutAction.ROTATE_VIEW_RIGHT -> language.text("Rotate view right 5°", "视图向右旋转 5°")
    ShortcutAction.FIT_PAGE -> language.text("Fit page", "适合页面")
    ShortcutAction.ACTUAL_SIZE -> language.text("Actual size (100%)", "实际大小（100%）")
    ShortcutAction.FIT_WIDTH -> language.text("Fit width", "适合宽度")
    ShortcutAction.RESET_VIEW_ROTATION -> language.text("Reset view rotation", "重置视图旋转")
    ShortcutAction.TOGGLE_RULER -> language.text("Show / hide ruler", "显示／隐藏直尺")
    ShortcutAction.TOGGLE_GRID_SNAP -> language.text("Show / hide snap grid", "显示／隐藏吸附网格")
    ShortcutAction.TOGGLE_ALIGNMENT_GUIDES -> language.text("Show / hide alignment guides", "显示／隐藏对齐参考线")
    ShortcutAction.PREVIOUS_PAGE -> language.text("Previous page", "上一页")
    ShortcutAction.NEXT_PAGE -> language.text("Next page", "下一页")
    ShortcutAction.TOGGLE_BRUSH_PANEL -> language.text("Show / hide brush panel", "显示／隐藏笔刷栏")
    ShortcutAction.TOGGLE_LAYER_PANEL -> language.text("Show / hide layer panel", "显示／隐藏图层栏")
    ShortcutAction.TOGGLE_PANELS -> language.text("Hide / show panels", "隐藏／显示面板")
    ShortcutAction.SAVE_CHECKPOINT -> language.text("Save checkpoint", "保存检查点")
    ShortcutAction.QUICK_SLOT_1, ShortcutAction.QUICK_SLOT_2, ShortcutAction.QUICK_SLOT_3, ShortcutAction.QUICK_SLOT_4,
    ShortcutAction.QUICK_SLOT_5, ShortcutAction.QUICK_SLOT_6, ShortcutAction.QUICK_SLOT_7, ShortcutAction.QUICK_SLOT_8 -> {
        val number = app.inkloom.settings.ToolboxQuickSlots.indexOf(action)?.plus(1) ?: 1
        language.text("Quick slot $number", "快速槽 $number")
    }
}

private fun shortcutActionDisplayLabel(action: ShortcutAction, settings: AppSettings, language: UiLanguage): String {
    val base = shortcutActionLabel(action, language)
    val index = ToolboxQuickSlots.indexOf(action) ?: return base
    val custom = ToolboxQuickSlots.normalize(settings.toolboxQuickSlots)[index].label
    return if (custom.isBlank()) base else "$base · $custom"
}

@Composable
private fun PressureCurveEditor(values: List<Float>, onChange: (List<Float>) -> Unit) {
    val curve = PressureResponseCurve.normalize(values)
    var draft by remember { mutableStateOf<List<Float>?>(null) }
    var selected by remember { mutableIntStateOf(-1) }
    val shown = draft ?: curve
    val currentCurve by rememberUpdatedState(curve)
    val currentOnChange by rememberUpdatedState(onChange)
    val graphDescription = tr("Editable pressure curve", "可编辑压感曲线")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Pressure response curve", "压感响应曲线"), color = MutedInk, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { currentOnChange(PressureResponseCurve.SOFT) }) { Text(tr("Soft", "柔和"), fontSize = 11.sp) }
            TextButton(onClick = { currentOnChange(PressureResponseCurve.LINEAR) }) { Text(tr("Linear", "线性"), fontSize = 11.sp) }
            TextButton(onClick = { currentOnChange(PressureResponseCurve.FIRM) }) { Text(tr("Firm", "硬朗"), fontSize = 11.sp) }
        }
        Canvas(
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(10.dp)).background(Workspace).padding(22.dp)
                .semantics { contentDescription = graphDescription }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var working = currentCurve
                        val pointCount = working.size / 2
                        var index = (0 until pointCount).minBy { i ->
                            val dx = down.position.x - size.width * working[i * 2]
                            val dy = down.position.y - size.height * (1f - working[i * 2 + 1])
                            dx * dx + dy * dy
                        }
                        val nearest = Offset(size.width * working[index * 2], size.height * (1f - working[index * 2 + 1]))
                        if ((down.position - nearest).getDistance() > 24.dp.toPx()) {
                            if (pointCount >= 8) return@awaitEachGesture
                            val x = (down.position.x / size.width.coerceAtLeast(1)).coerceIn(.015f, .985f)
                            index = (1 until pointCount).first { working[it * 2] >= x }
                            if (x - working[(index - 1) * 2] < .015f || working[index * 2] - x < .015f) return@awaitEachGesture
                            val y = (1f - down.position.y / size.height.coerceAtLeast(1))
                                .coerceIn(working[index * 2 - 1], working[index * 2 + 1])
                            working = working.toMutableList().apply { addAll(index * 2, listOf(x, y)) }
                        }
                        if (index == 0 || index == working.size / 2 - 1) return@awaitEachGesture
                        selected = index
                        down.consume()
                        val graphWidth = size.width.coerceAtLeast(1)
                        val graphHeight = size.height.coerceAtLeast(1)
                        fun update(position: Offset) {
                            working = working.toMutableList().apply {
                                val lowerX = this[(index - 1) * 2] + .015f
                                val upperX = maxOf(lowerX, this[(index + 1) * 2] - .015f)
                                this[index * 2] = (position.x / graphWidth).coerceIn(lowerX, upperX)
                                this[index * 2 + 1] = (1f - position.y / graphHeight)
                                    .coerceIn(this[index * 2 - 1], this[index * 2 + 3])
                            }
                            draft = working
                        }
                        update(down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                update(change.position)
                                if (!change.pressed) {
                                    val margin = 20.dp.toPx()
                                    val p = change.position
                                    if (p.x < -margin || p.y < -margin || p.x > size.width + margin || p.y > size.height + margin) {
                                        working = working.toMutableList().apply { removeAt(index * 2); removeAt(index * 2) }
                                    }
                                    currentOnChange(working)
                                    break
                                }
                            }
                        } finally { draft = null; selected = -1 }
                    }
                },
        ) {
            for (i in 0..4) {
                val x = size.width * i / 4f; val y = size.height * i / 4f
                drawLine(OutlineSoft.copy(alpha = .6f), Offset(x, 0f), Offset(x, size.height), 1f)
                drawLine(OutlineSoft.copy(alpha = .6f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            drawLine(OutlineSoft, Offset(0f, size.height), Offset(size.width, 0f), 1f)
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..240) {
                val x = i / 240f
                val y = size.height * (1f - PressureResponseCurve.apply(x, shown))
                if (i == 0) path.moveTo(0f, y) else path.lineTo(x * size.width, y)
            }
            drawPath(path, Sage, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            for (i in 0 until shown.size / 2) {
                val point = Offset(shown[i * 2] * size.width, (1f - shown[i * 2 + 1]) * size.height)
                drawCircle(Color.White, if (i == selected) 10.dp.toPx() else 7.dp.toPx(), point)
                drawCircle(Sage, if (i == selected) 7.dp.toPx() else 4.5.dp.toPx(), point)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("Input pressure →", "输入压力 →"), fontSize = 10.sp, color = MutedInk)
            if (selected >= 0 && selected * 2 + 1 < shown.size) Text(
                "${(shown[selected * 2] * 100).toInt()}% → ${(shown[selected * 2 + 1] * 100).toInt()}%",
                fontSize = 11.sp, color = Sage,
            ) else Text(tr("Output ↑", "输出 ↑"), fontSize = 10.sp, color = MutedInk)
        }
        Text(tr("Tap to add a point. Drag in both directions; drag outside to remove. Test below after releasing.",
            "点击加点，横向或纵向拖动调节；拖出图框删除。松手后可在下方试画。"), color = MutedInk, fontSize = 11.sp)
    }
}

@Composable
private fun FillOptionsLauncher(
    subTool: FillSubTool,
    sensitivity: Float,
    closeGapPixels: Float,
    areaScalingPixels: Float,
    onSensitivity: (Float) -> Unit,
    onCloseGap: (Float) -> Unit,
    onAreaScaling: (Float) -> Unit,
    onSubTool: (FillSubTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(10.dp), border = CardDefaults.outlinedCardBorder(), shadowElevation = 5.dp) {
        Row(Modifier.height(42.dp).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FormatColorFill, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(when (subTool) { FillSubTool.BUCKET -> tr("Bucket", "油漆桶"); FillSubTool.ENCLOSE_AND_FILL -> tr("Enclose", "包围并填充"); FillSubTool.LASSO_FILL -> tr("Lasso", "套索填充"); FillSubTool.LEFTOVER_PEN -> tr("Leftover pen", "漏涂笔") }, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Text("${tr("Close gap", "闭合缝隙")} ${closeGapPixels.toInt()} px", color = MutedInk, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Text("${tr("Area scaling", "区域缩放")} ${areaScalingPixels.toInt()} px", color = MutedInk, fontSize = 10.sp)
            Box {
                CompactToolbarIconButton({ expanded = true }, tr("Advanced fill settings", "高级填充设置")) { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) }
                DropdownMenu(expanded, { expanded = false }, Modifier.width(300.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(tr("Fill sub tool", "填充子工具"), fontSize = 11.sp)
                        FillSubTool.entries.forEach { option ->
                            Row(Modifier.fillMaxWidth().height(34.dp).clickable { onSubTool(option) }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(subTool == option, { onSubTool(option) })
                                Text(when (option) { FillSubTool.BUCKET -> tr("Refer and fill", "参照并填充"); FillSubTool.ENCLOSE_AND_FILL -> tr("Enclose and fill", "包围并填充"); FillSubTool.LASSO_FILL -> tr("Lasso fill", "套索填充"); FillSubTool.LEFTOVER_PEN -> tr("Leftover pen", "漏涂笔") }, fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider()
                        Text("${tr("Tolerance", "容差")} ${(sensitivity * 100).toInt()}%", fontSize = 11.sp)
                        Slider(sensitivity, onSensitivity, valueRange = 0f..1f)
                        Text("${tr("Close gap", "闭合缝隙")} ${closeGapPixels.toInt()} px", fontSize = 11.sp)
                        Slider(closeGapPixels, onCloseGap, valueRange = 0f..64f)
                        Text("${tr("Area scaling", "区域缩放")} ${areaScalingPixels.toInt()} px", fontSize = 11.sp)
                        Slider(areaScalingPixels, onAreaScaling, valueRange = -64f..64f)
                    }
                }
            }
        }
    }
}

@Composable
private fun CspSelectionSubtoolPalette(
    settings: AppSettings,
    onShape: (SelectionShape) -> Unit,
    onMode: (SelectionMode) -> Unit,
    onBrushSize: (Float) -> Unit,
    onThreshold: (Float) -> Unit,
    onDone: () -> Unit,
    onDeselect: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(Color(0xFFF1F1F1)).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Gesture, null, Modifier.size(15.dp), tint = Ink)
            Spacer(Modifier.width(6.dp))
            Text(tr("Selection area", "选区"), Modifier.weight(1f), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            IconButton(
                onClick = onDone,
                modifier = Modifier.size(28.dp).semantics { contentDescription = "Close selection palette and return to drawing" },
            ) { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Ink) }
        }
        SelectionShape.entries.forEach { shape ->
            val selected = settings.selectionShape == shape
            val label = when (shape) {
                SelectionShape.FREEFORM -> tr("Freehand lasso", "自由套索")
                SelectionShape.RECTANGLE -> tr("Rectangle selection", "矩形选区")
                SelectionShape.ELLIPSE -> tr("Ellipse selection", "椭圆选区")
                SelectionShape.BRUSH -> tr("Selection brush", "选区画笔")
                SelectionShape.SMART_BORDER -> tr("Smart border", "智能边框")
                SelectionShape.MAGIC_WAND -> tr("Magic wand", "魔棒")
            }
            val icon = when (shape) {
                SelectionShape.FREEFORM -> Icons.Outlined.Gesture
                SelectionShape.RECTANGLE -> Icons.Outlined.CropSquare
                SelectionShape.ELLIPSE -> Icons.Outlined.Circle
                SelectionShape.BRUSH -> Icons.Outlined.Brush
                SelectionShape.SMART_BORDER -> Icons.Outlined.AutoFixHigh
                SelectionShape.MAGIC_WAND -> Icons.Outlined.Flare
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(if (selected) Color(0xFFE5E5E5) else Color.White)
                    .clickable { onShape(shape) }
                    .semantics { contentDescription = label },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(if (selected) Ink else Color.Transparent))
                Spacer(Modifier.width(8.dp))
                Icon(icon, null, Modifier.size(16.dp), tint = if (selected) Ink else MutedInk)
                Spacer(Modifier.width(8.dp))
                Text(label, color = Ink, fontSize = 10.sp)
            }
        }
        HorizontalDivider(color = OutlineSoft)
        Text(
            tr("Selection mode", "选择模式"),
            Modifier.padding(start = 9.dp, top = 7.dp, bottom = 4.dp),
            color = MutedInk,
            fontSize = 9.sp,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SelectionMode.entries.forEach { mode ->
                val label = when (mode) {
                    SelectionMode.REPLACE -> tr("New", "新建")
                    SelectionMode.ADD -> tr("Add", "添加")
                    SelectionMode.SUBTRACT -> tr("Subtract", "减去")
                    SelectionMode.INTERSECT -> tr("Intersect", "交集")
                }
                val icon = when (mode) {
                    SelectionMode.REPLACE -> Icons.Outlined.CropFree
                    SelectionMode.ADD -> Icons.Outlined.AddBox
                    SelectionMode.SUBTRACT -> Icons.Outlined.IndeterminateCheckBox
                    SelectionMode.INTERSECT -> Icons.Outlined.GridOn
                }
                CspSelectionModeButton(
                    icon = icon,
                    label = label,
                    selected = settings.selectionMode == mode,
                    modifier = Modifier.weight(1f),
                ) { onMode(mode) }
            }
        }
        when (settings.selectionShape) {
            SelectionShape.BRUSH -> CspSelectionParameter(
                tr("Brush size", "画笔大小"),
                settings.selectionBrushSize,
                "${settings.selectionBrushSize.toInt()} px",
                4f..300f,
                onBrushSize,
            )
            SelectionShape.SMART_BORDER, SelectionShape.MAGIC_WAND -> CspSelectionParameter(
                tr("Recognition threshold", "识别阈值"),
                settings.selectionThreshold,
                "${(settings.selectionThreshold * 100).toInt()}%",
                0f..1f,
                onThreshold,
            )
            else -> Spacer(Modifier.height(6.dp))
        }
        HorizontalDivider(color = OutlineSoft)
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = onDone,
                modifier = Modifier.weight(1f).height(32.dp).semantics { contentDescription = "Finish selection tool" },
                shape = RoundedCornerShape(2.dp),
            ) {
                Icon(Icons.Outlined.Check, null, Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("Done", "完成"), fontSize = 10.sp)
            }
            TextButton(
                onClick = onDeselect,
                modifier = Modifier.weight(1f).height(32.dp).semantics { contentDescription = "Deselect and exit" },
                shape = RoundedCornerShape(2.dp),
            ) {
                Icon(Icons.Outlined.Close, null, Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("Deselect", "取消选区"), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CspSelectionModeButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(42.dp).semantics { contentDescription = "$label selection mode" },
        shape = RoundedCornerShape(2.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) Color(0xFFE2E2E2) else Color.Transparent,
            contentColor = Ink,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(label, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CspSelectionParameter(
    label: String,
    value: Float,
    formattedValue: String,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), color = Ink, fontSize = 9.sp)
            Text(formattedValue, color = MutedInk, fontSize = 9.sp)
        }
        Slider(value, onValue, Modifier.height(22.dp), valueRange = range)
    }
}

@Composable
private fun SelectionInputBar(
    shape: SelectionShape,
    mode: SelectionMode,
    hasSelection: Boolean,
    onShape: (SelectionShape) -> Unit,
    onMode: (SelectionMode) -> Unit,
    onDone: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = "Selection input toolbar" },
        color = Color.White,
        shape = RoundedCornerShape(3.dp),
        border = CardDefaults.outlinedCardBorder(),
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.height(38.dp).widthIn(max = 720.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionShape.entries.forEach { option ->
                val icon = when (option) {
                    SelectionShape.FREEFORM -> Icons.Outlined.Gesture
                    SelectionShape.RECTANGLE -> Icons.Outlined.CropSquare
                    SelectionShape.ELLIPSE -> Icons.Outlined.Circle
                    SelectionShape.BRUSH -> Icons.Outlined.Brush
                    SelectionShape.SMART_BORDER -> Icons.Outlined.AutoFixHigh
                    SelectionShape.MAGIC_WAND -> Icons.Outlined.Flare
                }
                val label = when (option) {
                    SelectionShape.FREEFORM -> tr("Freehand", "自由套索")
                    SelectionShape.RECTANGLE -> tr("Rectangle", "矩形")
                    SelectionShape.ELLIPSE -> tr("Ellipse", "椭圆")
                    SelectionShape.BRUSH -> tr("Brush", "选区画笔")
                    SelectionShape.SMART_BORDER -> tr("Smart border", "智能边框")
                    SelectionShape.MAGIC_WAND -> tr("Magic wand", "魔棒")
                }
                IconButton(
                    onClick = { onShape(option) },
                    modifier = Modifier.size(34.dp).background(if (shape == option) Color(0xFFE5E5E5) else Color.Transparent)
                        .semantics { contentDescription = "$label selection tool" },
                ) { Icon(icon, null, Modifier.size(17.dp), tint = Ink) }
            }
            VerticalDivider(Modifier.height(24.dp), color = OutlineSoft)
            SelectionMode.entries.forEach { option ->
                val icon = when (option) {
                    SelectionMode.REPLACE -> Icons.Outlined.CropFree
                    SelectionMode.ADD -> Icons.Outlined.AddBox
                    SelectionMode.SUBTRACT -> Icons.Outlined.IndeterminateCheckBox
                    SelectionMode.INTERSECT -> Icons.Outlined.GridOn
                }
                val label = when (option) {
                    SelectionMode.REPLACE -> tr("New", "新建")
                    SelectionMode.ADD -> tr("Add", "添加")
                    SelectionMode.SUBTRACT -> tr("Subtract", "减去")
                    SelectionMode.INTERSECT -> tr("Intersect", "交集")
                }
                IconButton(
                    onClick = { onMode(option) },
                    modifier = Modifier.size(34.dp).background(if (mode == option) Color(0xFFE5E5E5) else Color.Transparent)
                        .semantics { contentDescription = "$label selection mode" },
                ) { Icon(icon, null, Modifier.size(17.dp), tint = Ink) }
            }
            VerticalDivider(Modifier.height(24.dp), color = OutlineSoft)
            SelectionLauncherButton(Icons.Outlined.Check, tr("Exit selection tool", "退出选区工具"), onDone)
            SelectionLauncherButton(Icons.Outlined.Close, tr("Deselect and exit", "取消选区并退出")) {
                if (hasSelection) onDeselect() else onDone()
            }
        }
    }
}

@Composable
private fun SelectionLauncher(
    featherPixels: Float,
    interpolation: app.inkloom.engine.v5.tools.SelectionInterpolation,
    onInterpolation: (app.inkloom.engine.v5.tools.SelectionInterpolation) -> Unit,
    onDone: () -> Unit,
    onDeselect: () -> Unit,
    onInvert: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onExpand: (Float) -> Unit,
    onShrink: (Float) -> Unit,
    onBorder: (Float) -> Unit,
    onFeather: (Float) -> Unit,
    onBeginFeather: () -> Boolean,
    onFinishFeather: (Boolean) -> Unit,
    onFill: () -> Unit,
    onTransform: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var featherOpen by remember { mutableStateOf(false) }
    if (featherOpen) {
        val finish by rememberUpdatedState(onFinishFeather)
        DisposableEffect(Unit) { onDispose { finish(false) } }
        AlertDialog(
            onDismissRequest = { onFinishFeather(false); featherOpen = false },
            title = { Text(tr("Feather selection", "羽化选区")) },
            text = { Column {
                Text("${featherPixels.toInt()} px")
                Slider(featherPixels.coerceIn(0f, 128f), onFeather, valueRange = 0f..128f,
                    modifier = Modifier.semantics { contentDescription = "Selection feather radius" })
                Text(tr("Preview soft edges on the canvas. Cancel restores the original selection.", "在画布上预览柔化边缘，取消会恢复原选区。"))
            } },
            confirmButton = { TextButton(onClick = { onFinishFeather(true); featherOpen = false }) { Text(tr("Apply feather", "应用羽化")) } },
            dismissButton = { TextButton(onClick = { onFinishFeather(false); featherOpen = false }) { Text(tr("Cancel feather", "取消羽化")) } },
        )
    }
    var amount by rememberSaveable { mutableFloatStateOf(8f) }
    var dragX by rememberSaveable { mutableFloatStateOf(0f) }
    var dragY by rememberSaveable { mutableFloatStateOf(0f) }
    val dragDescription = tr("Drag selection launcher", "拖动选区快捷栏")
    Surface(
        modifier = modifier
            .graphicsLayer(translationX = dragX, translationY = dragY)
            .semantics { contentDescription = "Active selection launcher" },
        color = Color.White,
        shape = RoundedCornerShape(3.dp),
        border = CardDefaults.outlinedCardBorder(),
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.height(34.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(18.dp).fillMaxHeight().semantics { contentDescription = dragDescription }.pointerInput(Unit) {
                detectDragGestures { change, delta -> change.consume(); dragX += delta.x; dragY += delta.y }
            }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DragIndicator, null, Modifier.size(14.dp), tint = MutedInk)
            }
            VerticalDivider(Modifier.height(22.dp), color = OutlineSoft)
            SelectionLauncherButton(Icons.Outlined.Check, tr("Done", "完成"), onDone)
            SelectionLauncherButton(Icons.Outlined.Close, tr("Deselect", "取消选区"), onDeselect)
            SelectionLauncherButton(Icons.Outlined.Flip, tr("Invert selection", "反选"), onInvert)
            SelectionLauncherButton(Icons.Outlined.FormatColorFill, tr("Fill selection", "填充选区"), onFill)
            VerticalDivider(Modifier.height(22.dp), color = OutlineSoft)
            SelectionLauncherButton(Icons.Outlined.ContentCut, tr("Cut selection", "剪切选区"), onCut)
            SelectionLauncherButton(Icons.Outlined.ContentCopy, tr("Copy selection", "复制选区"), onCopy)
            SelectionLauncherButton(Icons.Outlined.DeleteOutline, tr("Delete selection", "删除选区"), onDelete)
            VerticalDivider(Modifier.height(22.dp), color = OutlineSoft)
            SelectionLauncherButton(Icons.Outlined.Transform, tr("Free transform", "自由变换"), onTransform)
            SelectionLauncherButton(Icons.Outlined.Flip, tr("Flip horizontal", "水平翻转"), onFlipHorizontal)
            SelectionLauncherButton(Icons.Outlined.Flip, tr("Flip vertical", "垂直翻转"), onFlipVertical)
            Box {
                SelectionLauncherButton(Icons.Outlined.Tune, tr("Selection settings", "选区设置")) { settingsOpen = true }
                DropdownMenu(settingsOpen, { settingsOpen = false }, Modifier.width(264.dp)) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(tr("Transform interpolation", "变换插值"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        app.inkloom.engine.v5.tools.SelectionInterpolation.entries.forEach { method ->
                            Row(Modifier.fillMaxWidth().clickable { onInterpolation(method) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = interpolation == method, onClick = { onInterpolation(method) })
                                Text(when (method) {
                                    app.inkloom.engine.v5.tools.SelectionInterpolation.NEAREST -> tr("Nearest neighbor", "最近邻")
                                    app.inkloom.engine.v5.tools.SelectionInterpolation.BILINEAR -> tr("Bilinear", "双线性")
                                    app.inkloom.engine.v5.tools.SelectionInterpolation.BICUBIC -> tr("Bicubic", "双三次")
                                }, fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider()
                        Text(tr("Selection modification", "选区修改"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider()
                        Text("${tr("Amount", "调整量")}  ${amount.toInt()} px", color = MutedInk, fontSize = 10.sp)
                        Slider(amount, { amount = it }, valueRange = 1f..128f)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            SelectionSettingsAction(Icons.Outlined.ZoomOutMap, tr("Expand", "扩大"), Modifier.weight(1f)) { onExpand(amount) }
                            SelectionSettingsAction(Icons.Outlined.ZoomInMap, tr("Shrink", "缩小"), Modifier.weight(1f)) { onShrink(amount) }
                            SelectionSettingsAction(Icons.Outlined.BorderStyle, tr("Border", "边界"), Modifier.weight(1f)) { onBorder(amount) }
                        }
                        HorizontalDivider()
                        Text("${tr("Feather", "羽化")}  ${featherPixels.toInt()} px", color = MutedInk, fontSize = 10.sp)
                        TextButton(onClick = {
                            settingsOpen = false
                            featherOpen = onBeginFeather()
                        }) { Text(tr("Feather selection", "羽化选区")) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionLauncherButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val tooltipState = rememberTooltipState()
    val tooltipScope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description, fontSize = 11.sp) } },
        state = tooltipState,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(32.dp)
                .semantics { contentDescription = description }
                .pointerInput(tooltipState) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Enter, PointerEventType.Move -> if (event.changes.none { it.pressed } && !tooltipState.isVisible) {
                                    tooltipScope.launch { tooltipState.show() }
                                }
                                PointerEventType.Exit -> tooltipState.dismiss()
                                else -> Unit
                            }
                        }
                    }
                },
        ) { Icon(icon, null, Modifier.size(17.dp), tint = Ink) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionSettingsAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label, fontSize = 11.sp) } },
        state = rememberTooltipState(),
    ) {
        TextButton(
            onClick = onClick,
            modifier = modifier.height(30.dp),
            shape = RoundedCornerShape(2.dp),
            contentPadding = PaddingValues(horizontal = 3.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Ink),
        ) {
            Icon(icon, null, Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text(label, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(localizedBrushLabel(title)); if (subtitle != null) Text(localizedBrushLabel(subtitle), color = MutedInk, fontSize = 11.sp) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun StylusActionRow(title: String, selected: StylusButtonAction, trigger: ShortcutTrigger, onSelected: (StylusButtonAction) -> Unit, onTrigger: (ShortcutTrigger) -> Unit) {
    Column {
        Text(title, fontSize = 13.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(StylusButtonAction.entries, key = { it.name }) { action ->
                FilterChip(selected = selected == action, onClick = { onSelected(action) }, label = { Text(when (action) {
                    StylusButtonAction.ERASER -> tr("Eraser", "橡皮")
                    StylusButtonAction.LASSO -> tr("Lasso", "套索")
                    StylusButtonAction.HAND -> tr("Hand", "抓手")
                    StylusButtonAction.EYEDROPPER -> tr("Eyedropper", "吸管")
                    StylusButtonAction.UNDO -> tr("Undo", "撤销")
                    StylusButtonAction.REDO -> tr("Redo", "重做")
                    StylusButtonAction.NONE -> tr("None", "无")
                }) })
            }
        }
        if (selected !in setOf(StylusButtonAction.UNDO, StylusButtonAction.REDO, StylusButtonAction.NONE)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = trigger == ShortcutTrigger.HOLD, onClick = { onTrigger(ShortcutTrigger.HOLD) }, label = { Text(tr("Hold temporarily", "按住临时切换")) })
                FilterChip(selected = trigger == ShortcutTrigger.PRESS, onClick = { onTrigger(ShortcutTrigger.PRESS) }, label = { Text(tr("Tap to switch", "单击切换")) })
            }
        } else if (selected in setOf(StylusButtonAction.UNDO, StylusButtonAction.REDO)) {
            Text(tr("Runs once when pressed", "按下时执行一次"), color = MutedInk, fontSize = 10.sp)
        }
    }
}
