package app.inkloom.ui

import android.content.ClipData
import android.content.Intent
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import app.inkloom.document.*
import app.inkloom.brushpresetv5.BrushToolTipReuse
import app.inkloom.importer.BrushImportRepository
import app.inkloom.importer.BrushImportBatch
import app.inkloom.importer.BrushImportFailure
import app.inkloom.importer.BrushImportFailureStore
import app.inkloom.importer.BrushImportFinalState
import app.inkloom.importer.BrushImportLedger
import app.inkloom.importer.BrushGroupOperations
import app.inkloom.importer.BrushGroupStore
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.importer.BrushRoleClassifier
import app.inkloom.model.*
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsBackupCodec
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.GradientPresetLibraryCodec
import app.inkloom.settings.BuiltInWorkspace
import app.inkloom.settings.BuiltInWorkspaces
import app.inkloom.settings.ColorSetLibraryStore
import app.inkloom.settings.ColorSetLibrary
import app.inkloom.settings.FloatingToolbarPlacement
import app.inkloom.settings.FloatingToolbarPosition
import app.inkloom.settings.SelectionLauncherLayout
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.BrushMasterSettingsStore
import app.inkloom.settings.BrushUsageState
import app.inkloom.settings.BrushUsageStore
import app.inkloom.settings.DrawingColorStore
import app.inkloom.settings.DrawingColors
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutPhase
import app.inkloom.settings.ShortcutProfile
import app.inkloom.settings.ShortcutProfileCodec
import app.inkloom.settings.WorkspaceLayoutCodec
import app.inkloom.settings.ToolRailLayout
import app.inkloom.settings.PanelWorkspaceBackup
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.ShortcutBrushGroup
import app.inkloom.settings.ShortcutBrushSelection
import app.inkloom.settings.ToolbarDock
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.ToolboxQuickSlots
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.EditorPanel
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppDestination { LIBRARY, EDITOR }
internal enum class BrushBatchGroupOperation { MOVE, COPY }
private const val UNGROUPED_BRUSH_FILTER = "\u0000ungrouped"
internal val LocalUiLanguage = staticCompositionLocalOf { UiLanguage.ENGLISH }

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
internal fun tr(english: String, simplifiedChinese: String): String = LocalUiLanguage.current.text(english, simplifiedChinese)

@Composable
internal fun pageCount(count: Int): String = tr(if (count == 1) "1 page" else "$count pages", "$count 页")

@Composable
private fun layerBlendModeLabel(mode: LayerBlendMode): String =
    LayerBlendModeDisplayName.of(mode, LocalUiLanguage.current)

@Composable
internal fun localizedShapeKind(kind: ShapeKind): String = when (kind) {
    ShapeKind.ELLIPSE -> tr("Ellipse", "椭圆")
    ShapeKind.RECTANGLE -> tr("Rectangle", "矩形")
    ShapeKind.TRIANGLE -> tr("Triangle", "三角形")
    ShapeKind.DIAMOND -> tr("Diamond", "菱形")
    ShapeKind.LINE -> tr("Line", "直线")
    ShapeKind.ARROW -> tr("Arrow", "箭头")
    ShapeKind.FREEFORM -> tr("Freeform", "自由形状")
}

@Composable
internal fun localizedTextAlignment(alignment: InkTextAlignment): String = when (alignment) {
    InkTextAlignment.START -> tr("Start", "左对齐")
    InkTextAlignment.CENTER -> tr("Center", "居中")
    InkTextAlignment.END -> tr("End", "右对齐")
}

@Composable
internal fun localizedListStyle(style: TextListStyle): String = when (style) {
    TextListStyle.NONE -> tr("None", "无")
    TextListStyle.BULLET -> tr("Bullet", "项目符号")
    TextListStyle.NUMBERED -> tr("Numbered", "编号")
    TextListStyle.CHECKBOX -> tr("Checkbox", "复选框")
}

@Composable
internal fun localizedImageMask(mask: ImageMask): String = when (mask) {
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
internal fun localizedBrushLabel(raw: String): String {
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
    val librarySavedState = rememberSaveableStateHolder()
    var importError by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    val viewModel: InkloomViewModel = viewModel()
    val colorStudioState: ColorStudioState = viewModel()
    val libraryState by viewModel.state.collectAsState()
    val pdfImportProgress by viewModel.pdfImportProgress.collectAsState()
    val pageTemplates by viewModel.pageTemplates.collectAsState()
    val repository = remember { BrushImportRepository(context) }
    var pendingBrushExport by remember { mutableStateOf<ImportedBrush?>(null) }
    val brushStore = remember { BrushMetadataStore(context) }
    val brushGroupStore = remember { BrushGroupStore(context) }
    val brushImportFailureStore = remember { BrushImportFailureStore(context) }
    val settingsStore = remember { AppSettingsStore(context) }
    val colorPaletteStore = remember { ColorSetLibraryStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    val panelState = rememberEditorPanelState()
    fun updateSettings(value: AppSettings) { settings = settingsStore.save(value) }
    LaunchedEffect(Unit) {
        if(app.inkloom.settings.EditorWorkspaceStore(context).takeRecoveryNotice()) Toast.makeText(context,
            settings.language.text("The workspace was recovered. The damaged layout was preserved for recovery.","工作区已恢复；损坏的布局数据已保留以供恢复。"),Toast.LENGTH_LONG).show()
    }
    var importedBrushes by remember { mutableStateOf(brushStore.load()) }
    var brushGroups by remember { mutableStateOf(BrushGroupOperations.reconcileRegisteredGroups(brushGroupStore.load(), importedBrushes)) }
    var brushImportFailures by remember { mutableStateOf(brushImportFailureStore.load()) }
    var brushImportFailuresOpen by remember { mutableStateOf(false) }
    var pendingBrushImportGroup by remember { mutableStateOf<String?>(null) }

    fun brushSourceName(uri: Uri): String = runCatching {
        context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty().ifBlank { settings.language.text("Brush import", "笔刷导入") }

    fun persistBrushImportOutcome(uri: Uri, displayName: String, targetGroup: String, result: Result<BrushImportBatch>) {
        val batch = result.getOrNull()
        val failedRecords = batch?.ledger?.records?.filter { it.finalState != BrushImportFinalState.IMPORTED_USABLE }.orEmpty()
        if (result.isSuccess && failedRecords.isEmpty()) {
            brushImportFailureStore.removeSource(uri.toString(), targetGroup)
        } else {
            brushImportFailureStore.record(
                sourceUri = uri.toString(),
                displayName = displayName,
                targetGroup = targetGroup,
                reasons = failedRecords.flatMap { it.rejectionReasons }.ifEmpty {
                    listOf(result.exceptionOrNull()?.message ?: settings.language.text("Import did not produce a usable brush", "导入未生成可用笔刷"))
                },
                finalStates = failedRecords.map { it.finalState.name }.ifEmpty { listOf("FILE_ERROR") },
            )
        }
        brushImportFailures = brushImportFailureStore.load()
    }

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
                    uri to repository.importDetailed(uri)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                results.forEach { (uri, result) -> persistBrushImportOutcome(uri, brushSourceName(uri), targetGroup.orEmpty(), result) }
                val batchResults = results.map(Pair<Uri, Result<BrushImportBatch>>::second)
                val batches = batchResults.mapNotNull { it.getOrNull() }
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
                val fileFailures = batchResults.count { it.isFailure }
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
                        batchResults.mapNotNull { it.exceptionOrNull()?.message })
                        .distinct().take(5)
                    importError = listOf(summary, reasons.joinToString("\n")).filter(String::isNotBlank).joinToString("\n\n")
                } else {
                    Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    var folderImportProgress by remember { mutableStateOf<String?>(null) }
    var folderImportReport by remember { mutableStateOf<String?>(null) }
    val stopFolderImport = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val brushFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && folderImportProgress == null) {
            stopFolderImport.set(false)
            folderImportProgress = settings.language.text("Scanning brush folders…", "正在扫描笔刷文件夹…")
            importScope.launch {
                val issues=mutableListOf<String>()
                var imported=0; var duplicates=0; var rejected=0; var processed=0; var skipped=0; var total=0
                try {
                    val tree=app.inkloom.importer.BrushDocumentTree(context.contentResolver,uri)
                    val plan=withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        tree.scan(stopFolderImport::get)
                    }
                    issues += plan.errors; skipped=plan.skipped; total=plan.files.size
                    brushGroups=BrushGroupOperations.reconcileRegisteredGroups(brushGroups+plan.groups,importedBrushes)
                    withContext(Dispatchers.IO) { brushGroupStore.save(brushGroups) }
                    for(source in plan.files) {
                        if(stopFolderImport.get()) break
                        folderImportProgress="${processed+1} / $total\n${source.path}"
                        val sourceUri=tree.uri(source.id)
                        val result=withContext(Dispatchers.IO) { repository.importDetailed(sourceUri) }
                        persistBrushImportOutcome(sourceUri, source.path, source.group, result)
                        val batch=result.getOrNull()
                        if(batch == null) issues += "${source.path}: ${result.exceptionOrNull()?.message}"
                        else {
                            rejected += batch.ledger.summary.rejectedUnsupported+batch.ledger.summary.failed
                            batch.ledger.records.filter { it.rejectionReasons.isNotEmpty() }.forEach { record -> issues += "${source.path} · ${record.sourceBrushName}: ${record.rejectionReasons.joinToString()}" }
                            val additions=batch.renderableBrushes.mapNotNull { brush ->
                                runCatching { app.inkloom.importer.BrushFolderImport.assign(source,brush) }.getOrElse { issues += "${source.path}: ${it.message}"; null }
                            }
                            val merged=app.inkloom.importer.BrushFolderImport.merge(importedBrushes,additions)
                            imported += merged.size-importedBrushes.size; duplicates += additions.size-(merged.size-importedBrushes.size)
                            importedBrushes=merged
                            brushGroups=BrushGroupOperations.reconcileRegisteredGroups(brushGroups,merged)
                            // Persist each completed file before allowing Stop or another source.
                            withContext(Dispatchers.IO) { brushStore.save(merged); brushGroupStore.save(brushGroups) }
                        }
                        processed++
                    }
                } catch(failure: Exception) { issues += failure.message.orEmpty() }
                finally {
                    folderImportProgress=null
                    val summary=settings.language.text(
                        "${if(stopFolderImport.get()) "Stopped" else "Finished"}: $processed / $total files. Imported $imported brushes; $duplicates duplicates; $rejected rejected or failed brushes; $skipped non-brush files skipped.",
                        "${if(stopFolderImport.get()) "已停止" else "已完成"}：$processed / $total 个文件。新增 $imported 支笔刷，跳过 $duplicates 支重复笔刷，$rejected 支笔刷不支持或失败，跳过 $skipped 个非笔刷文件。")
                    folderImportReport=(listOf(summary)+issues).joinToString("\n\n")
                }
            }
        }
    }
    folderImportProgress?.let { progress ->
        BrushFolderImportProgressDialog(progress, settings.language) { stopFolderImport.set(true) }
    }
    folderImportReport?.let { report -> AlertDialog(onDismissRequest={folderImportReport=null},title={Text(settings.language.text("Folder import result", "文件夹导入结果"))},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {Text(report)}},confirmButton={TextButton(onClick={folderImportReport=null}) {Text(settings.language.text("Done", "完成"))}}) }
    fun retryBrushImport(failure: BrushImportFailure) {
        importScope.launch {
            val uri = Uri.parse(failure.sourceUri)
            val result = withContext(Dispatchers.IO) { repository.importDetailed(uri) }
            withContext(Dispatchers.Main.immediate) {
                result.getOrNull()?.let { batch ->
                    val additions = batch.renderableBrushes.map { brush ->
                        val nestedGroup = failure.targetGroup.takeIf(String::isNotBlank)
                            ?.let { BrushGroupOperations.nest(it, brush.groupName) }
                            ?: brush.groupName
                        BrushGroupOperations.assign(brush, nestedGroup)
                    }
                    importedBrushes = BrushGroupOperations.mergeImports(importedBrushes, additions)
                    brushStore.save(importedBrushes)
                    brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, importedBrushes)
                    brushGroupStore.save(brushGroups)
                }
                persistBrushImportOutcome(uri, failure.displayName, failure.targetGroup, result)
                if (result.isSuccess && result.getOrThrow().ledger.records.all { it.finalState == BrushImportFinalState.IMPORTED_USABLE }) {
                    Toast.makeText(context, settings.language.text("Brush import retry succeeded.", "笔刷重新导入成功。"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    if (brushImportFailuresOpen) BrushImportFailuresDialog(
        failures = brushImportFailures,
        language = settings.language,
        onDismiss = { brushImportFailuresOpen = false },
        onRetry = ::retryBrushImport,
        onDelete = { failure -> brushImportFailureStore.remove(failure.id); brushImportFailures = brushImportFailureStore.load() },
        onClear = { brushImportFailureStore.clear(); brushImportFailures = emptyList() },
    )
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: settings.language.text("Imported document", "导入的文档")
            when {
                name.endsWith(".inkloom.zip", true) || name.endsWith(".inkloom", true) -> viewModel.importBundle(uri)
                name.endsWith(".pdf", true) -> {
                    viewModel.importPdfAsync(uri, name) { result ->
                        result.onSuccess { destination = AppDestination.EDITOR }
                            .onFailure { if (it !is kotlinx.coroutines.CancellationException) importError = it.message }
                    }
                    return@runCatching
                }
                name.substringAfterLast('.').lowercase() in setOf("psd", "ora", "tif", "tiff", "zip") -> {
                    viewModel.importArtworkAsync(uri, name) { result ->
                        result.onSuccess { destination = AppDestination.EDITOR }.onFailure { importError = it.message }
                    }
                    return@runCatching
                }
                else -> viewModel.importImage(uri, name)
            }
            destination = AppDestination.EDITOR
        }.onFailure { importError = it.message }
    }
    // Export and share report in their own words; the import dialog used to announce export errors.
    fun exportFinished(result: Result<Unit>, onSuccess: () -> Unit = {
        Toast.makeText(context, settings.language.text("Export saved.", "已导出。"), Toast.LENGTH_SHORT).show()
    }) {
        result.onSuccess { onSuccess() }.onFailure { error ->
            android.util.Log.e("Inkloom", "Export failed", error)
            Toast.makeText(context, settings.language.text("Could not export: ${error.message ?: "unknown error"}",
                "无法导出：${error.message ?: "未知错误"}"), Toast.LENGTH_LONG).show()
        }
    }
    // The file picker returns after the editor or Library chose a notebook; remember which one.
    var pendingExportDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    fun takeExportDocumentId(): String? = (pendingExportDocumentId ?: viewModel.currentDocument()?.id).also { pendingExportDocumentId = null }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val documentId = takeExportDocumentId()
        if (uri != null && documentId != null) viewModel.exportDocumentPdf(documentId, uri) { exportFinished(it) }
    }
    var pendingRasterExport by remember { mutableStateOf(PageRasterExportOptions()) }
    val imageExportPicker = rememberLauncherForActivityResult(RasterExportContract()) { uri ->
        if (uri != null) viewModel.exportCurrentPage(uri, pendingRasterExport) { exportFinished(it) }
    }
    val bundleExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val documentId = takeExportDocumentId()
        if (uri != null && documentId != null) viewModel.exportDocumentBundle(documentId, uri) { exportFinished(it) }
    }
    fun shareNotebookPdf(document: InkDocument) {
        Toast.makeText(context, settings.language.text("Preparing PDF…", "正在准备 PDF…"), Toast.LENGTH_SHORT).show()
        val file = ShareSheet.newFile(context, document.title, "pdf")
        viewModel.writeDocumentPdf(document.id, file) { result ->
            exportFinished(result) { ShareSheet.open(context, file, "application/pdf", settings.language.text("Share PDF", "分享 PDF")) }
        }
    }
    fun shareCurrentPage(options: PageRasterExportOptions) {
        val title = viewModel.currentDocument()?.title ?: "Inkloom"
        val file = ShareSheet.newFile(context, "$title page", options.format.extension)
        viewModel.exportCurrentPage(Uri.fromFile(file), options) { result ->
            exportFinished(result) { ShareSheet.open(context, file, options.format.mime, settings.language.text("Share page", "分享页面")) }
        }
    }
    fun produceNotebookOutput(document: InkDocument, output: NotebookOutput) = when (output) {
        NotebookOutput.SHARE_PDF -> shareNotebookPdf(document)
        NotebookOutput.EXPORT_PDF -> { pendingExportDocumentId = document.id; exportPicker.launch(app.inkloom.document.ShareFileName.of(document.title, "pdf")) }
        NotebookOutput.EXPORT_PACKAGE -> { pendingExportDocumentId = document.id; bundleExportPicker.launch(app.inkloom.document.ShareFileName.of(document.title, "inkloom.zip")) }
    }
    val libraryBackupExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val preferences = runCatching { AppSettingsBackupCodec.encode(settings, colorSetLibrary = colorPaletteStore.load(settings.language.text("Saved colors", "已存颜色")), panelWorkspace = panelState.backup()) }
                .getOrElse { error -> importError = error.message; return@rememberLauncherForActivityResult }
            viewModel.exportLibraryBackup(uri, importedBrushes, brushGroups, preferences) { result ->
                result.onSuccess { Toast.makeText(context, settings.language.text("Library backup saved.", "资料库备份已保存。"), Toast.LENGTH_LONG).show() }
                    .onFailure { importError = it.message }
            }
        }
    }
    var damagedBackupUri by remember { mutableStateOf<Uri?>(null) }
    var damagedBackupIssues by remember { mutableStateOf(emptyList<String>()) }
    fun handleBackupRestore(uri: Uri, result: Result<app.inkloom.document.LibraryRestoreResult>, offerRecovery: Boolean) {
            result.onSuccess { restored ->
                damagedBackupUri = null
                importedBrushes = (importedBrushes + restored.brushes).distinctBy(ImportedBrush::id)
                brushStore.save(importedBrushes)
                brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups + restored.brushGroups, importedBrushes)
                brushGroupStore.save(brushGroups)
                restored.preferences?.let { bytes ->
                    AppSettingsBackupCodec.decode(bytes).onSuccess { preferences ->
                        val restoredSettings = preferences.settings.copy(
                            toolboxQuickSlots = ToolboxQuickSlots.remapBrushIds(preferences.settings.toolboxQuickSlots, restored.brushIdMappings),
                        )
                        val workspace = preferences.panelWorkspace
                        workspace?.let(panelState::restoreBackup)
                        updateSettings(workspace?.toolRail?.applyTo(restoredSettings) ?: restoredSettings)
                        colorPaletteStore.replace(preferences.colorSetLibrary ?: ColorSetLibrary.fromLegacy(preferences.colorPalette, settings.language.text("Saved colors", "已存颜色")))
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
                if (restored.skippedItems.isNotEmpty()) importError = settings.language.text(
                    "Recovered intact items; skipped ${restored.skippedItems.size} damaged items:\n${restored.skippedItems.joinToString("\n")}",
                    "已救回完好内容；跳过 ${restored.skippedItems.size} 个损坏项目：\n${restored.skippedItems.joinToString("\n")}",
                )
            }.onFailure { error ->
                if (offerRecovery && error is app.inkloom.document.LibraryBackupIntegrityException) {
                    damagedBackupUri = uri
                    damagedBackupIssues = error.issues
                } else {
                    damagedBackupUri = null
                    importError = error.message
                }
            }
    }
    val libraryBackupImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreLibraryBackup(uri) { result ->
            handleBackupRestore(uri, result, offerRecovery = true)
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
    var pendingAppendDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    val appendPageFilesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val targetId = pendingAppendDocumentId
        pendingAppendDocumentId = null
        if (targetId != null && uris.isNotEmpty()) runCatching {
            val files = uris.map { uri ->
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment ?: "Imported file"
                uri to name
            }
            viewModel.appendFilesAsync(targetId, files) { result ->
                result.onFailure { if (it !is kotlinx.coroutines.CancellationException) importError = it.message }
            }
        }.onFailure { importError = it.message }
    }
    fun launchAppendPageFiles(documentId: String) {
        pendingAppendDocumentId = documentId
        appendPageFilesPicker.launch(arrayOf("application/pdf", "image/*", "application/zip", "image/vnd.adobe.photoshop", "image/openraster", "text/plain", "text/markdown"))
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
                    type == "application/pdf" || name.endsWith(".pdf", true) -> viewModel.importPdfAsync(uri, name) { result ->
                        result.onSuccess { destination = AppDestination.EDITOR }
                            .onFailure { if (it !is kotlinx.coroutines.CancellationException) importError = it.message }
                    }
                    type.startsWith("image/") -> viewModel.importImage(uri, name)
                    else -> repository.importAll(uri).getOrNull()?.let { brushes -> importedBrushes = BrushGroupOperations.mergeImports(importedBrushes, brushes) }
                }
            }
            brushStore.save(importedBrushes)
            brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, importedBrushes)
            brushGroupStore.save(brushGroups)
            panelState.show(EditorPanel.BRUSHES, true)
            destination = AppDestination.EDITOR
        }
    }

    CompositionLocalProvider(LocalUiLanguage provides settings.language, LocalColorStudioState provides colorStudioState) {
    damagedBackupUri?.let { uri ->
        DamagedBackupRecoveryDialog(damagedBackupIssues, settings.language,
            onDismiss = { damagedBackupUri = null },
            onRecover = {
                damagedBackupUri = null
                viewModel.restoreLibraryBackup(uri, recoverIntactItems = true) { result -> handleBackupRestore(uri, result, offerRecovery = false) }
            })
    }
    pdfImportProgress?.let { progress -> AlertDialog(
        onDismissRequest = {},
        title = { Text(if (progress.addingPages) tr("Adding pages", "正在追加页面") else tr("Importing PDF", "正在导入 PDF")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (progress.addingPages && progress.fileCount > 1 && !progress.committing) Text(tr("File ${progress.fileIndex} of ${progress.fileCount}", "第 ${progress.fileIndex} / ${progress.fileCount} 个文件"))
            Text(if (progress.committing) tr("Saving notebook…", "正在保存笔记本…") else if (progress.total > 0) tr("Page ${progress.completed} of ${progress.total}", "第 ${progress.completed} / ${progress.total} 页") else tr("Copying file…", "正在复制文件…"))
            if (progress.total > 0) LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = !progress.committing, onClick = viewModel::cancelPdfImport) { Text(tr("Cancel", "取消")) } },
    ) }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
    if (libraryState.migratingLegacyFills) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(tr("Converting old fills to editable paint…", "正在将旧填充转换为可编辑的绘画内容…"))
            }
        }
    } else when (destination) {
        AppDestination.LIBRARY -> librarySavedState.SaveableStateProvider("library") { LibraryScreen(
            documents = libraryState.documents,
            folders = libraryState.folders,
            pageTemplates = pageTemplates,
            layout = settings.libraryLayout,
            onLayout = { updateSettings(settings.copy(libraryLayout = it)) },
            onOpen = { viewModel.select(it.id); destination = AppDestination.EDITOR },
            onCreateCustomTemplate = viewModel::createCustomPageTemplate,
            onNewNote = { title, style, templateId, paperColor, coverId ->
                runCatching {
                    if (coverId == null) viewModel.createNote(title, style, templateId, paperColor)
                    else viewModel.createNoteUsingCover(title, coverId, templateId, paperColor)
                    destination = AppDestination.EDITOR
                }.onFailure {
                    android.util.Log.e("Inkloom", "Could not create notebook", it)
                }.isSuccess
            },
            onPrepareImagePaper = { uri, name -> viewModel.assets.prepareImagePaper(uri, name) },
            onSaveImagePaper = viewModel::saveImagePaperTemplate,
            onImportCover = viewModel::importNotebookCover,
            onDeleteCover = viewModel::deleteNotebookCover,
            onChangeCover = viewModel::changeNotebookCover,
            onImport = { documentPicker.launch(arrayOf("application/pdf", "image/*", "application/zip")) },
            onFavorite = viewModel::toggleFavorite,
            onArchive = viewModel::toggleArchive,
            onTags = viewModel::setDocumentTags,
            onTrash = viewModel::trash,
            onRestore = viewModel::restore,
            onDelete = { documentId -> deletionFailed = !viewModel.deletePermanently(documentId) },
            onRename = viewModel::rename,
            onDuplicate = viewModel::duplicateDocument,
            onNotebookOutput = ::produceNotebookOutput,
            onCreateFolder = viewModel::createFolder,
            onRenameFolder = viewModel::renameFolder,
            onMoveToFolder = viewModel::moveToFolder,
            onMoveNotesToFolder = viewModel::moveNotesToFolder,
            onRestoreFolder = viewModel::restoreFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onMoveFolders = viewModel::moveFolders,
            onDeleteFolders = viewModel::deleteFolders,
            onSettings = { settingsOpen = true },
        ) }
        AppDestination.EDITOR -> EditorScreen(
            panelState = panelState,
            document = libraryState.documents.firstOrNull { it.id == libraryState.selectedDocumentId } ?: viewModel.currentDocument(),
            documents = libraryState.documents,
            selectedPageId = libraryState.selectedPageId,
            brushes = importedBrushes,
            brushGroups = brushGroups,
            importError = importError,
            onBack = { destination = AppDestination.LIBRARY },
            onImportBrushes = { group ->
                pendingBrushImportGroup = group?.let(BrushGroupOperations::normalize)?.takeIf(String::isNotBlank)
                brushPicker.launch(arrayOf("*/*"))
            },
            onImportBrushFolder = { brushFolderPicker.launch(null) },
            brushImportFailureCount = brushImportFailures.size,
            onOpenBrushImportFailures = { brushImportFailures = brushImportFailureStore.load(); brushImportFailuresOpen = true },
            onBrushGridLayout = { updateSettings(settings.copy(brushGridLayout = it)) },
            onBrushCategoryWidth = { updateSettings(settings.copy(brushCategoryWidthDp = it)) },
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
            onInsertPageNextTo = viewModel::insertPageNextTo,
            onAddPage = { templateId, paperColor -> libraryState.selectedDocumentId?.let { viewModel.addPage(it, templateId, paperColor) } },
            onAppendPageFiles = ::launchAppendPageFiles,
            onDuplicatePage = { documentId, pageId -> viewModel.duplicatePage(documentId, pageId) },
            onDeletePage = { documentId, pageId -> viewModel.deletePage(documentId, pageId) },
            onMovePage = viewModel::movePage,
            onReorderPages = viewModel::reorderPages,
            onTransferPage = viewModel::transferPage,
            onTransferPages = viewModel::transferPages,
            onApplyPageTemplate = viewModel::applyPageTemplate,
            onApplyPageTemplates = viewModel::applyPageTemplateToPages,
            onImportPageTemplates = { pageTemplatePicker.launch(arrayOf("application/pdf", "image/*")) },
            onTogglePageTemplateFavorite = viewModel::togglePageTemplateFavorite,
            onSetNotebookDefaultTemplate = viewModel::setNotebookDefaultTemplate,
            onSavePageAsTemplate = { documentId, pageId, name -> viewModel.savePageAsTemplate(documentId, pageId, name) },
            onDeletePageTemplate = viewModel::deletePageTemplate,
            onCreateCustomTemplate = viewModel::createCustomPageTemplate,
            onToggleBookmark = viewModel::toggleBookmark,
            onChangePageOutlineLevel = viewModel::changePageOutlineLevel,
            onTogglePageOutlineCollapsed = viewModel::togglePageOutlineCollapsed,
            onRenamePage = viewModel::renamePage,
            onSetPageMetadata = viewModel::setPageMetadata,
            onClearPageInk = viewModel::clearPageInk,
            onSetPageCrop = viewModel::setPageCrop,
            onApplyPageGeometry = viewModel::applyPageGeometry,
            onRotatePage = viewModel::rotatePageClockwise,
            onApplySelectionLayerTransfer = viewModel::applySelectionLayerTransfer,
            onSetSavedSelections = viewModel::setSavedSelections,
            onApplyLayerInsertion = viewModel::applyLayerInsertion,
            onRenameLayer = viewModel::renameLayer,
            onToggleLayerVisibility = viewModel::toggleLayerVisibility,
            onToggleLayerLock = viewModel::toggleLayerLock,
            onApplyLayerAppearanceEdit = viewModel::applyLayerAppearanceEdit,
            onSetLayerRasterTileSurface = viewModel::setLayerRasterTileSurface,
            onToggleLayerAlphaLock = viewModel::toggleLayerAlphaLock,
            onToggleLayerDrawingAssist = viewModel::toggleLayerDrawingAssist,
            onUpdateLayerDrawingGuide = viewModel::updateLayerDrawingGuide,
            onToggleLayerFolderCollapsed = viewModel::toggleLayerFolderCollapsed,
            onDuplicateLayer = viewModel::duplicateLayer,
            onApplyRasterLayerMerge = viewModel::applyRasterLayerMerge,
            onApplyRasterLayerFilter = viewModel::applyRasterLayerFilter,
            onApplyLayerTreeEdit = viewModel::applyLayerTreeEdit,
            onDeleteLayer = { documentId, pageId, layerId ->
                viewModel.deleteLayer(documentId, pageId, layerId)?.let { rejection ->
                    val message = if (rejection == app.inkloom.document.LayerGroupRejection.THROUGH_CLIPPING)
                        settings.language.text("Through folders cannot be a clipping base. Remove clipping first.", "穿透组不能作为剪贴底层，请先取消剪贴。")
                    else settings.language.text("The layer target is no longer available.", "目标图层已不可用。")
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
            onElement = viewModel::addElement,
            onRemoveElement = viewModel::removeElement,
            onDuplicateElement = viewModel::duplicateElement,
            onInsertImage = { pageImagePicker.launch(arrayOf("image/*")) },
            onScanImage = ::launchPageScanner,
            onExport = { viewModel.currentDocument()?.let { produceNotebookOutput(it, NotebookOutput.EXPORT_PDF) } },
            onSharePdf = { viewModel.currentDocument()?.let(::shareNotebookPdf) },
            onSharePage = ::shareCurrentPage,
            onExportPage = { options -> pendingRasterExport = options; imageExportPicker.launch(RasterExportDestination("${viewModel.currentDocument()?.title ?: "Inkloom"}-page.${options.format.extension}", options.format.mime)) },
            onExportBundle = { viewModel.currentDocument()?.let { produceNotebookOutput(it, NotebookOutput.EXPORT_PACKAGE) } },
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
            onOpenNotebook = viewModel::select,
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
        settings = settings,
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
private fun EditorScreen(
    panelState: EditorPanelState,
    document: InkDocument?,
    documents: List<InkDocument>,
    selectedPageId: String?,
    brushes: List<ImportedBrush>,
    brushGroups: List<String>,
    importError: String?,
    onBack: () -> Unit,
    onImportBrushes: (String?) -> Unit,
    onImportBrushFolder: () -> Unit,
    brushImportFailureCount: Int,
    onOpenBrushImportFailures: () -> Unit,
    onBrushGridLayout: (Boolean) -> Unit,
    onBrushCategoryWidth: (Float) -> Unit,
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
    onInsertPageNextTo: (String, String, Boolean) -> Unit,
    onAddPage: (String, Long?) -> Unit,
    onAppendPageFiles: (String) -> Unit,
    onDuplicatePage: (String, String) -> Unit,
    onDeletePage: (String, String) -> Unit,
    onMovePage: (String, String, Int) -> Unit,
    onReorderPages: (String, List<String>) -> Unit,
    onTransferPage: (String, String, String, Boolean) -> Unit,
    onTransferPages: (String, Set<String>, String, Boolean) -> Boolean,
    onApplyPageTemplate: (String, String, String, Long?) -> Unit,
    onApplyPageTemplates: (String, Set<String>, String, Long?) -> Unit,
    onImportPageTemplates: () -> Unit,
    onTogglePageTemplateFavorite: (String) -> Unit,
    onSetNotebookDefaultTemplate: (String, String?, Long?) -> Unit,
    onSavePageAsTemplate: (String, String, String) -> PageTemplate?,
    onDeletePageTemplate: (String) -> Unit,
    onCreateCustomTemplate: (String, Int, Int, PageBackground.Paper) -> PageTemplate?,
    onToggleBookmark: (String, String) -> Unit,
    onChangePageOutlineLevel: (String, String, Int) -> Unit,
    onTogglePageOutlineCollapsed: (String, String) -> Unit,
    onRenamePage: (String, String, String) -> Unit,
    onSetPageMetadata: (String, String, String?, List<String>) -> Unit,
    onClearPageInk: (String, String) -> Unit,
    onSetPageCrop: (String, String, PageCrop) -> Unit,
    onApplyPageGeometry: (String, String, PageGeometryEdit, Boolean) -> Boolean,
    onRotatePage: (String, String) -> Unit,
    onApplySelectionLayerTransfer: (String, String, String, RasterTileSurfaceRef?, String, InkLayer?) -> Unit,
    onSetSavedSelections: (String, String, List<app.inkloom.document.SavedSelection>) -> Unit,
    onApplyLayerInsertion: (String, String, app.inkloom.document.LayerInsertionEdit, Boolean) -> Boolean,
    onRenameLayer: (String, String, String, String) -> Unit,
    onToggleLayerVisibility: (String, String, String) -> Unit,
    onToggleLayerLock: (String, String, String) -> Unit,
    onApplyLayerAppearanceEdit: (String, String, LayerAppearanceEdit, Boolean) -> Boolean,
    onSetLayerRasterTileSurface: (String, String, String, RasterTileSurfaceRef?, Int, app.inkloom.document.LayerElementEdit?) -> Unit,
    onToggleLayerAlphaLock: (String, String, String) -> Unit,
    onToggleLayerDrawingAssist: (String, String, String, LayerDrawingGuide) -> Unit,
    onUpdateLayerDrawingGuide: (String, String, String, LayerDrawingGuide) -> Unit,
    onToggleLayerFolderCollapsed: (String, String, String) -> Unit,
    onDuplicateLayer: (String, String, String) -> String?,
    onApplyRasterLayerMerge: (String, String, RasterLayerMergeEdit, Boolean, InkPage?) -> Boolean,
    onApplyRasterLayerFilter: (String, String, RasterLayerFilterEdit, Boolean, InkPage?) -> Boolean,
    onApplyLayerTreeEdit: (String, String, LayerTreeEdit, Boolean, InkPage?) -> Boolean,
    onDeleteLayer: (String, String, String) -> Unit,
    onElement: (String, String, PageElement) -> Unit,
    onRemoveElement: (String, String, String) -> Unit,
    onDuplicateElement: (String, String, String) -> Unit,
    onInsertImage: () -> Unit,
    onScanImage: () -> Unit,
    onExport: () -> Unit,
    onSharePdf: () -> Unit,
    onSharePage: (PageRasterExportOptions) -> Unit,
    onExportPage: (PageRasterExportOptions) -> Unit,
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
    onOpenNotebook: (String) -> Unit,
    onSettings: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
) {
    if (document == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val context = LocalContext.current
    val editorHostView = LocalView.current
    val gradientImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= GradientPresetLibraryCodec.MAX_BYTES) {
                    val count = input.read(buffer, 0, minOf(buffer.size, GradientPresetLibraryCodec.MAX_BYTES + 1 - output.size()))
                    if (count < 0) break
                    require(count > 0) { "Gradient source stopped before the file ended" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("Unable to open gradient presets")
            val imported = GradientPresetLibraryCodec.decode(bytes).getOrThrow()
            onSettingsChange(settings.copy(gradientPresets = settings.gradientPresets.append(imported)))
            Toast.makeText(context, settings.language.text("Gradients imported", "渐变预设已导入"), Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, it.message ?: "Gradient import failed", Toast.LENGTH_LONG).show() }
    }
    val gradientExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GradientPresetLibraryCodec.MIME_TYPE)) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(GradientPresetLibraryCodec.encode(settings.gradientPresets))
            } ?: error("Unable to save gradient presets")
            Toast.makeText(context, settings.language.text("Gradients exported", "渐变预设已导出"), Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, it.message ?: "Gradient export failed", Toast.LENGTH_LONG).show() }
    }
    val brushMasterSettingsStore = remember { BrushMasterSettingsStore(context) }
    val brushUsageStore = remember { BrushUsageStore(context) }
    var brushUsage by remember { mutableStateOf(brushUsageStore.load()) }
    fun changeBrushUsage(next: BrushUsageState) {
        brushUsage = next
        brushUsageStore.save(next)
    }
    val storedPage = document.pages.firstOrNull { it.id == selectedPageId } ?: document.pages.first()
    var canvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    val appearanceScope = rememberCoroutineScope()
    val currentAppearancePage by rememberUpdatedState(storedPage)
    val currentAppearanceCanvas by rememberUpdatedState(canvasView)
    val currentAppearanceApply by rememberUpdatedState(onApplyLayerAppearanceEdit)
    val appearanceFailure = tr("Could not change layer properties; finish the current operation or unlock the layer and try again", "无法修改图层属性，请完成当前操作或解锁图层后重试")
    val throughFailure by rememberUpdatedState(tr("Through folders cannot use clipping or be a clipping base. Remove clipping first.", "穿透组不能使用剪贴或作为剪贴底层，请先取消剪贴。"))
    val appearanceEditor = remember(document.id, storedPage.id) {
        LayerAppearanceEditor(document.id, storedPage.id, { currentAppearancePage }, { currentAppearanceCanvas },
            { edit, undo -> currentAppearanceApply(document.id, storedPage.id, edit, undo) }, appearanceScope,
            { Toast.makeText(context, appearanceFailure, Toast.LENGTH_SHORT).show() },
            { Toast.makeText(context, throughFailure, Toast.LENGTH_LONG).show() })
    }
    DisposableEffect(appearanceEditor) { onDispose { appearanceEditor.close() } }
    val selectedPage = appearanceEditor.preview?.apply(storedPage) ?: storedPage
    var secondaryLayers by remember(document.id, storedPage.id) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(storedPage.layers.map(InkLayer::id)) { secondaryLayers = secondaryLayers.intersect(storedPage.layers.mapTo(hashSetOf(), InkLayer::id)) }
    val availableBrushes = remember(brushes) { (systemBrushCatalog() + brushes).distinctBy(ImportedBrush::id) }
    val persistedBrush = availableBrushes.firstOrNull {
        it.id == settings.persistentBrushPresetId && !it.hidden
    }
    val persistedTool = when {
        settings.persistentDrawingTool == EditorTool.ERASER && brushUsage.eraserUsesPaintTip &&
            persistedBrush?.id == brushUsage.lastEraserBrushId -> EditorTool.ERASER
        settings.persistentDrawingTool == EditorTool.SMUDGE && brushUsage.smudgeUsesPaintTip &&
            persistedBrush?.id == brushUsage.lastSmudgeBrushId -> EditorTool.SMUDGE
        else -> persistedBrush?.let(BrushRoleClassifier::editorTool) ?: settings.persistentDrawingTool
    }
    var tool by remember(document.id) { mutableStateOf(persistedTool) }
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
    var selectionColorFill by remember(selectedPage.id) { mutableStateOf(false) }
    var quickMaskActive by remember(selectedPage.id) { mutableStateOf(false) }
    LaunchedEffect(tool, selectedPage.id) {
        if (tool != EditorTool.SELECT) { quickMaskActive = false; canvasView?.quickMaskActive = false }
    }
    SideEffect { canvasView?.onSelectionThresholdChanged = { value -> onSettingsChange(settings.copy(selectionThreshold = value)) } }
    var savedSelectionsOpen by remember(selectedPage.id) { mutableStateOf(false) }
    val trSelectionName = tr("Selection", "选区")
    val selectionSaveError = tr("Could not save the selection", "无法保存选区")
    val selectionLoadError = tr("Could not load this selection for the current page", "无法在当前页面载入此选区")
    var selectionActive by remember(document.id, selectedPage.id) { mutableStateOf(false) }
    var selectionTransformState by remember(document.id, selectedPage.id) { mutableStateOf<SelectionTransformUiState?>(null) }
    var activeLayerId by rememberSaveable(document.id, selectedPage.id) { mutableStateOf(LayerIds.INK) }
    var adjustmentDialogId by remember(document.id, selectedPage.id) { mutableStateOf<String?>(null) }
    var rasterFilterDialogOpen by remember(document.id, selectedPage.id) { mutableStateOf(false) }
    var compositeFilterPreview by remember(document.id,selectedPage.id){mutableStateOf<android.graphics.Bitmap?>(null)}
    var compositeFilterBusy by remember{mutableStateOf(false)}
    var compositeFilterWorker by remember{mutableStateOf<kotlinx.coroutines.Job?>(null)}
    var compositeFilterGeneration by remember{mutableIntStateOf(0)}
    DisposableEffect(compositeFilterPreview){onDispose{compositeFilterPreview?.takeUnless{it.isRecycled}?.recycle()}}

    var editingMaskId by remember(document.id, selectedPage.id) { mutableStateOf<String?>(null) }
    val maskAssets = remember(context) { DocumentAssetService(context) }
    val maskFailurePrefix by rememberUpdatedState(tr("Could not edit mask", "无法编辑蒙版"))
    val maskEditor = remember(document.id, storedPage.id) {
        LayerMaskEditor(document.id, storedPage.id, { currentAppearancePage }, { currentAppearanceCanvas }, maskAssets,
            { edit, undo -> currentAppearanceApply(document.id, storedPage.id, edit, undo) }, appearanceScope,
            { reason -> Toast.makeText(context, "$maskFailurePrefix: ${layerMaskFailureLabel(reason, settings.language)}", Toast.LENGTH_LONG).show() },
            { activeLayerId = it })
    }
    DisposableEffect(maskEditor) { onDispose { maskEditor.close() } }
    fun createLayer(folder: Boolean) { canvasView?.onLayerCreationRequested?.invoke(activeLayerId, folder) }
    fun createAdjustmentLayer(kind: LayerAdjustmentKind) {
        canvasView?.onAdjustmentLayerCreationRequested?.invoke(activeLayerId, LayerAdjustment(kind)) { adjustmentDialogId = it }
    }
    fun performLayerMaskCommand(layerId: String, command: LayerMaskCommand) {
        maskEditor.execute(layerId, command)
    }
    fun performLayerMaskTransfer(sourceLayerId: String, targetLayerId: String, copy: Boolean) {
        maskEditor.transfer(sourceLayerId, targetLayerId, copy)
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
    var drawingColorHistory by remember(document.id) { mutableStateOf(drawingColorStore.loadHistory(document.id)) }
    val colorPreview = remember(document.id) { DrawingColorPreview() }
    val brushColor = drawingColors.foreground
    val backgroundColor = drawingColors.background
    var offeredContinueFillColor by remember { mutableStateOf<Int?>(null) }
    val modes = remember { EditorModes() }
    var continueFillStartedWithTool by remember { mutableStateOf<EditorTool?>(null) }
    fun commitDrawingColors(colors: DrawingColors, selectedColor: Int? = null) {
        colorPreview.clear()
        drawingColors = colors
        // The next pen sample can arrive before AndroidView recomposes.
        canvasView?.inkColor = colors.foreground
        canvasView?.secondaryInkColor = colors.background
        drawingColorStore.save(document.id, colors)
        if (selectedColor != null) drawingColorHistory = drawingColorStore.recordColor(document.id, selectedColor)
    }
    fun changeBrushColor(value: Int) = commitDrawingColors(drawingColors.copy(foreground = value), value)
    fun changeBackgroundColor(value: Int) = commitDrawingColors(drawingColors.copy(background = value), value)
    fun swapDrawingColors() = commitDrawingColors(
        drawingColors.let { it.copy(foreground = it.background, background = it.foreground) },
    )
    fun stopContinueFilling() {
        modes.continueFilling = false
        offeredContinueFillColor = null
        continueFillStartedWithTool = null
        canvasView?.setContinueFilling(false)
    }
    LaunchedEffect(tool) {
        if (modes.continueFilling && continueFillStartedWithTool != null && tool != continueFillStartedWithTool) stopContinueFilling()
    }
    var drawingReturnTarget by remember(document.id) {
        mutableStateOf(DrawingToolReturnTarget(persistedTool, persistedBrush, brushSize, brushOpacity))
    }
    fun exitCanvasNavigation() { modes.handToolEnabled = false; modes.handToolHeld = false; modes.rotateToolEnabled = false; modes.rotateToolHeld = false }
    var commandSection by rememberSaveable { mutableIntStateOf(0) }
    val overlays = remember { EditorOverlays() }
    var panelLayoutName by remember { mutableStateOf("") }
    var renamingPanelLayout by remember { mutableStateOf<String?>(null) }
    var deletingPanelLayout by remember { mutableStateOf<String?>(null) }
    val colorPanelOpen = panelState.layout.open.any { it.ordinal >= EditorPanel.WHEEL.ordinal }
    fun showColors() { panelState.colors(true) }
    fun setBrushPanelOpen(open: Boolean) {
        panelState.show(EditorPanel.BRUSHES, open)
        if (open) panelState.focus(EditorPanel.BRUSHES)
    }
    fun toggleBrushPanel() = setBrushPanelOpen(!panelState.isActive(EditorPanel.BRUSHES))
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
    var pageHistory by remember(document.id) { mutableStateOf(app.inkloom.document.PageNavigationHistory()) }
    fun selectPageWithViewport(pageId: String) {
        if (settings.continuousPages) {
            // In the strip the chosen page comes to the window centre at the current zoom.
            viewportPageId = pageId
            applyViewport(ViewportState(zoom, panX, 0f, 0f))
            onSelectPage(pageId)
            return
        }
        if (pageId == viewportPageId) { onSelectPage(pageId); return }
        pageViewportStore.save(viewportPageId, ViewportState(zoom, panX, panY, viewRotation))
        applyViewport(pageViewportStore.restore(pageId))
        viewportPageId = pageId
        onSelectPage(pageId)
    }
    // Continuous pages: each page's fit-to-window box as the canvas sizes it, in pixels before zoom.
    val stripGapPx = with(LocalDensity.current) { 24.dp.toPx() }
    fun stripFitSize(page: InkPage): androidx.compose.ui.geometry.Size {
        val ratio = (PageCropTransform.visibleWidth(page) / PageCropTransform.visibleHeight(page).coerceAtLeast(1f)).coerceAtLeast(.01f)
        val width = minOf(viewportWidthPx * .88f, viewportHeightPx * .88f * ratio).coerceAtLeast(1f)
        return androidx.compose.ui.geometry.Size(width, width / ratio)
    }
    fun stripActiveIndex() = document.pages.indexOfFirst { it.id == viewportPageId }
    /** Makes another strip page live without moving anything on screen. */
    fun activateStripPage(index: Int, rebasedPanY: Float) {
        val target = document.pages.getOrNull(index) ?: return
        if (target.id == viewportPageId) return
        viewportPageId = target.id
        applyViewport(ViewportState(zoom, panX, rebasedPanY, 0f))
        canvasView?.viewportPanY = rebasedPanY
        onSelectPage(target.id)
    }
    /** After the view moves, hands the live canvas to whichever page is now nearest the centre. */
    fun followStrip() {
        if (!settings.continuousPages) return
        ContinuousPageStrip.switchAfterScroll(document.pages.map { stripFitSize(it).height }, stripGapPx, stripActiveIndex(), zoom, panY)
            ?.let { activateStripPage(it.index, it.rebasedPanY) }
    }
    fun stripNeighbours(): List<ContinuousPageStrip.Placement> = if (!settings.continuousPages) emptyList() else
        ContinuousPageStrip.visibleNeighbours(document.pages.map { stripFitSize(it).height }, stripGapPx, stripActiveIndex(), zoom, panY, viewportHeightPx)
    /** A tap or pen press on a neighbouring page makes it live; it never paints. */
    fun stripActivateAt(position: Offset): Boolean {
        stripNeighbours().forEach { placement ->
            val size = stripFitSize(document.pages[placement.index])
            val centerX = viewportWidthPx / 2f + panX
            val centerY = viewportHeightPx / 2f + panY + placement.centerOffset * zoom
            if (kotlin.math.abs(position.x - centerX) <= size.width * zoom / 2f && kotlin.math.abs(position.y - centerY) <= size.height * zoom / 2f) {
                activateStripPage(placement.index, panY + placement.centerOffset * zoom)
                return true
            }
        }
        return false
    }
    /** A non-sequential navigation that Back can return from; previous/next page turns are not jumps. */
    fun jumpToPage(pageId: String) {
        if (document.pages.none { it.id == pageId }) return
        pageHistory = pageHistory.jumped(selectedPage.id, pageId)
        selectPageWithViewport(pageId)
    }
    // Links to another page of this notebook are jumps Back can undo; other notebooks open as before.
    val openInternalLink: (String, String) -> Unit = { documentId, pageId ->
        if (documentId == document.id) jumpToPage(pageId) else onOpenInternalLink(documentId, pageId)
    }
    fun stepPageHistory(forward: Boolean) {
        val existing = document.pages.map(InkPage::id).toSet()
        val step = if (forward) pageHistory.goForward(selectedPage.id, existing) else pageHistory.goBack(selectedPage.id, existing)
        step?.let { (target, next) -> pageHistory = next; selectPageWithViewport(target) }
    }
    LaunchedEffect(selectedPage.id) {
        if (selectedPage.id != viewportPageId) {
            pageViewportStore.save(viewportPageId, ViewportState(zoom, panX, panY, viewRotation))
            applyViewport(pageViewportStore.restore(selectedPage.id))
            viewportPageId = selectedPage.id
        }
    }
    LaunchedEffect(document.pages.map(InkPage::id)) {
        val existing = document.pages.map(InkPage::id).toSet()
        pageViewportStore.retainPages(existing)
        pageHistory = pageHistory.retain(existing)
    }
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
    var toolbarDragOrigin by remember { mutableStateOf<Triple<ToolbarDock, Float, Float>?>(null) }
    var toolbarDragTravel by remember { mutableFloatStateOf(0f) }
    val editorDensity = LocalDensity.current
    LaunchedEffect(settings.toolbarOffsetXDp, settings.toolbarOffsetYDp, settings.toolbarDock) {
        if (toolbarDragOrigin == null) {
            toolbarX = settings.toolbarOffsetXDp; toolbarY = settings.toolbarOffsetYDp
        }
    }
    val waitingForInputText = tr("Waiting for input", "等待输入")
    val unknownInputDeviceText = tr("Unknown device", "未知设备")
    val unmappedShortcutText = tr("unmapped", "未映射")
    var inputDiagnostic by remember { mutableStateOf(waitingForInputText) }
    var searchFocus by remember(document.id) { mutableStateOf<PageSearchHit?>(null) }
    var pdfLinkFocus by remember(document.id) { mutableStateOf<Pair<String, PdfTextBounds>?>(null) }
    var outlineMenuPageId by remember { mutableStateOf<String?>(null) }
    var outlineRenamePageId by remember { mutableStateOf<String?>(null) }
    var outlineRenameText by remember { mutableStateOf("") }
    var documentQuery by remember(document.id) { mutableStateOf("") }
    val documentSearchHits = remember(document.pages, documentQuery) {
        if(documentQuery.isBlank()) emptyList() else document.pages.flatMap { page -> PageSearch.findAll(page, documentQuery).map { page to it } }
    }
    var recognitionLanguageTag by remember(document.id) { mutableStateOf(if (settings.language == app.inkloom.settings.UiLanguage.SIMPLIFIED_CHINESE) "zh-Hans" else "en-US") }
    var recognitionMessage by remember(document.id, selectedPage.id) { mutableStateOf<String?>(null) }
    var handwritingConversionHit by remember { mutableStateOf<Pair<InkPage, PageSearchHit>?>(null) }
    var insertText by remember { mutableStateOf("") }
    var insertLinkTitle by remember { mutableStateOf("") }
    var insertLinkUrl by remember { mutableStateOf("") }
    var insertLinkDocumentId by remember(document.id) { mutableStateOf(document.id) }
    var insertLinkPageId by remember(document.id) { mutableStateOf(selectedPage.id) }
    var insertShape by remember { mutableStateOf(ShapeKind.RECTANGLE) }
    var selectedElementId by remember(selectedPage.id) { mutableStateOf<String?>(null) }
    fun updateActiveLayerDrawingGuide(transform: (LayerDrawingGuide) -> LayerDrawingGuide): Boolean {
        val layer = selectedPage.layers.firstOrNull { it.id == activeLayerId && it.drawingAssist } ?: return false
        val guide = layer.drawingGuide ?: return false
        onUpdateLayerDrawingGuide(document.id, selectedPage.id, layer.id, transform(guide))
        return true
    }
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
    val layersOpen = EditorPanel.LAYERS in panelState.layout.open
    var layerPickMode by remember(selectedPage.id) { mutableStateOf(false) }
    var layerPickCandidates by remember(selectedPage.id) { mutableStateOf<List<String>>(emptyList()) }
    var layerPickPreviewId by remember(selectedPage.id) { mutableStateOf<String?>(null) }
    fun syncCanvasInput(prepareAssets: Boolean = false) {
        canvasView?.let { view ->
            view.activeLayerId = activeLayerId
            view.applyBrushConfiguration(tool, settings, activeBrush, brushSize, brushOpacity, colorPreview.foreground ?: brushColor, settings.eraserPreset, colorPreview.secondary ?: backgroundColor,
                borrowPaintTipForEraser = tool == EditorTool.ERASER && brushUsage.eraserUsesPaintTip && activeBrush?.id == brushUsage.lastEraserBrushId,
                borrowPaintTipForSmudge = tool == EditorTool.SMUDGE && brushUsage.smudgeUsesPaintTip && activeBrush?.id == brushUsage.lastSmudgeBrushId)
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
        quickMaskActive = false
        canvasView?.quickMaskActive = false
        if (!deselect) canvasView?.commitSelectionContour()
        canvasView?.cancelSelectionInput()
        if (deselect) {
            selectedElementId = null
            if (canvasView?.clearSelection() == true) selectionFeather = 0f
        }
        restorePreviousDrawingTool(finishingSelection = true)
    }
    BackHandler(enabled = tool == EditorTool.SELECT || selectionTransformState != null) {
        if (canvasView?.selectionTransformUiState != null) canvasView?.finishSelectionTransform(apply = false)
        else finishSelectionTool(deselect = true)
    }
    fun activateWorkspaceBrush(brush: ImportedBrush, persistSelection: Boolean) {
        if (!brush.toolBinding.isSupported) {
            android.widget.Toast.makeText(context, brush.toolBinding.unsupportedReason ?: "Unsupported brush tool", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        exitCanvasNavigation()
        val usedBrush = brush.copy(lastUsedAt = System.currentTimeMillis())
        // Selection history lives in BrushUsageStore below. Rewriting the entire imported brush
        // JSON (including hundreds of large preset descriptors) on every tap blocked the UI
        // before the brush could become active. Preset edits still use onUpdateBrush explicitly.
        selectedBrushId = usedBrush.id
        activeBrush = usedBrush
        val chosenTool = BrushRoleClassifier.editorTool(usedBrush)
        brushSize = savedBrushSize(usedBrush, usedBrush.defaultSize, chosenTool)
        brushOpacity = savedBrushOpacity(usedBrush, usedBrush.defaultOpacity, chosenTool)
        tool = chosenTool
        if (persistSelection) {
            val nextUsage = brushUsage.used(usedBrush.id, when (chosenTool) {
                EditorTool.PEN, EditorTool.HIGHLIGHTER -> false
                EditorTool.ERASER -> true
                else -> null
            })
            changeBrushUsage(if (chosenTool == EditorTool.SMUDGE) nextUsage.useSmudgeBrush(usedBrush.id) else nextUsage)
            rememberDrawingSelection()
            onSettingsChange(
                settings.copy(
                    persistentDrawingTool = chosenTool,
                    persistentBrushPresetId = usedBrush.id,
                ),
            )
        }
        // Apply the complete descriptor synchronously. A panel click and the next pen-down can
        // occur before Compose's AndroidView update block; partial field assignment here used to
        // leave the input configuration in a stale state and made docked brush selection appear
        // to disable writing.
        syncCanvasInput(prepareAssets = true)
    }
    fun selectWorkspaceBrush(brush: ImportedBrush) = activateWorkspaceBrush(brush, persistSelection = true)

    fun borrowCurrentTipForSmudge() {
        val paint = activeBrush?.takeIf { BrushRoleClassifier.editorTool(it) == EditorTool.PEN }
            ?: availableBrushes.firstOrNull { it.id == brushUsage.lastPaintBrushId && !it.hidden }
        BrushToolTipReuse.forSmudge(paint?.v5PresetJson).onSuccess {
            val chosen = requireNotNull(paint)
            activeBrush = chosen; selectedBrushId = chosen.id; tool = EditorTool.SMUDGE
            brushSize = savedBrushSize(chosen, chosen.defaultSize, EditorTool.SMUDGE)
            brushOpacity = savedBrushOpacity(chosen, chosen.defaultOpacity, EditorTool.SMUDGE)
            changeBrushUsage(brushUsage.borrowPaintTipForSmudge(chosen.id))
            onSettingsChange(settings.copy(persistentDrawingTool = EditorTool.SMUDGE,
                persistentBrushPresetId = chosen.id))
            syncCanvasInput()
        }.onFailure { Toast.makeText(context, it.message ?: "Tip cannot be used for blending", Toast.LENGTH_LONG).show() }
    }
    fun activateRememberedSmudge() {
        val borrowed = availableBrushes.firstOrNull { brushUsage.smudgeUsesPaintTip &&
            it.id == brushUsage.lastSmudgeBrushId && !it.hidden &&
            BrushToolTipReuse.forSmudge(it.v5PresetJson).isSuccess }
        if (borrowed != null) {
            activeBrush = borrowed; selectedBrushId = borrowed.id; tool = EditorTool.SMUDGE
            brushSize = savedBrushSize(borrowed, borrowed.defaultSize, EditorTool.SMUDGE)
            brushOpacity = savedBrushOpacity(borrowed, borrowed.defaultOpacity, EditorTool.SMUDGE)
            syncCanvasInput()
        } else {
            val normal = availableBrushes.firstOrNull { it.id == brushUsage.lastSmudgeBrushId && BrushRoleClassifier.isBlend(it) }
                ?: systemBrushCatalog().firstOrNull(BrushRoleClassifier::isBlend)
            normal?.let(::selectWorkspaceBrush)
        }
    }

    LaunchedEffect(settings.persistentBrushPresetId, brushes) {
        if (activeBrush?.id == settings.persistentBrushPresetId) return@LaunchedEffect
        val restored = (systemBrushCatalog() + brushes).firstOrNull {
            it.id == settings.persistentBrushPresetId && !it.hidden
        } ?: return@LaunchedEffect
        val restoredTool = when {
            settings.persistentDrawingTool == EditorTool.ERASER && brushUsage.eraserUsesPaintTip &&
                restored.id == brushUsage.lastEraserBrushId -> EditorTool.ERASER
            settings.persistentDrawingTool == EditorTool.SMUDGE && brushUsage.smudgeUsesPaintTip &&
                restored.id == brushUsage.lastSmudgeBrushId -> EditorTool.SMUDGE
            else -> BrushRoleClassifier.editorTool(restored)
        }
        activeBrush = restored
        selectedBrushId = restored.id
        tool = restoredTool
        brushSize = savedBrushSize(restored, restored.defaultSize, restoredTool)
        brushOpacity = savedBrushOpacity(restored, restored.defaultOpacity, restoredTool)
        syncCanvasInput(prepareAssets = true)
    }
    fun setLayersPanelOpen(open: Boolean, pageId: String? = null) {
        pageId?.let(::selectPageWithViewport)
        panelState.show(EditorPanel.LAYERS, open)
        if (open) panelState.focus(EditorPanel.LAYERS)
    }
    fun setManagedPanelVisible(panel: EditorPanel, open: Boolean) {
        when (panel) {
            EditorPanel.PAGES -> {
                panelState.show(panel, open)
                if (open) panelState.focus(panel)
            }
            EditorPanel.BRUSHES -> setBrushPanelOpen(open)
            EditorPanel.LAYERS -> setLayersPanelOpen(open)
            else -> {
                panelState.show(panel, open)
                if (open) panelState.focus(panel)
                if (panel.ordinal >= EditorPanel.WHEEL.ordinal) {
                    if (panelState.layout.open.none { it.ordinal >= EditorPanel.WHEEL.ordinal }) {
                        colorPreview.clear()
                        canvasView?.inkColor = brushColor
                        canvasView?.secondaryInkColor = backgroundColor
                    }
                }
            }
        }
    }
    fun selectLayerContents(layerId: String, mode: app.inkloom.engine.v5.surface.MaskComposeMode) {
        exitCanvasNavigation()
        rememberSelectionReturnTarget()
        val ids = if (layerId == activeLayerId || layerId in secondaryLayers)
            secondaryLayers + activeLayerId + layerId else setOf(layerId)
        activeLayerId = layerId
        tool = EditorTool.SELECT
        syncCanvasInput()
        canvasView?.selectLayerContent(layerId)
        val contentIds = ids.flatMap { id ->
            val layer = selectedPage.layers.firstOrNull { it.id == id }
            if (layer?.role == LayerRole.FOLDER) selectedPage.layers.filter { LayerHierarchy.ancestors(selectedPage.layers, it).any { parent -> parent.id == id } }.map { it.id } else listOf(id)
        }.filterTo(linkedSetOf()) { id -> selectedPage.layers.any { it.id == id && it.isPaintSurfaceLayer() } }
        canvasView?.selectLayerContents(contentIds, mode)
        setLayersPanelOpen(false)
    }
    var openPanelsBeforeShortcut by remember { mutableStateOf(emptySet<EditorPanel>()) }
    var toolbarBeforeShortcut by remember { mutableStateOf(settings.showToolbar) }
    LaunchedEffect(selectedPage.layers) {
        activeLayerId = app.inkloom.document.LayerEditingTarget.retainSelection(selectedPage.layers, activeLayerId)
    }
    fun selectLayerTarget(layerId: String) {
        secondaryLayers = emptySet()
        activeLayerId = layerId
        canvasView?.selectLayerContent(layerId)
    }
    fun syncRestoredPanelSettings(saved: app.inkloom.settings.NamedPanelLayout) {
        saved.toolRail?.let { onSettingsChange(it.applyTo(settings)) }
    }
    val workspaceLayoutExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(WorkspaceLayoutCodec.MIME_TYPE)) { uri ->
        if (uri != null) runCatching {
            val bytes = WorkspaceLayoutCodec.encode(panelState.backup(), ToolRailLayout.capture(settings))
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("Unable to open the workspace layout destination")
            Toast.makeText(context, settings.language.text("Workspace layout saved.", "工作区布局已导出。"), Toast.LENGTH_LONG).show()
        }.onFailure { Toast.makeText(context, it.message ?: "Could not export workspace layout", Toast.LENGTH_LONG).show() }
    }
    val workspaceLayoutImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val imported = context.contentResolver.openInputStream(uri)?.use { WorkspaceLayoutCodec.decode(it).getOrThrow() }
                ?: error("Unable to open the workspace layout")
            val merged = WorkspaceLayoutCodec.mergeNamedLayouts(panelState.namedLayouts, imported.workspace.namedLayouts)
            val layout = imported.workspace.copy(namedLayouts = merged).also(PanelWorkspaceBackup::validate)
            panelState.restoreBackup(layout)
            syncRestoredPanelSettings(app.inkloom.settings.NamedPanelLayout("Imported", layout.layout, imported.toolRail))
            Toast.makeText(context, settings.language.text("Workspace layout restored.", "工作区布局已恢复。"), Toast.LENGTH_LONG).show()
        }.onFailure { Toast.makeText(context, it.message ?: "Could not import workspace layout", Toast.LENGTH_LONG).show() }
    }
    fun applyBuiltInWorkspace(workspace: BuiltInWorkspace) {
        overlays.workspaceMenu = false
        val saved = BuiltInWorkspaces.snapshot(workspace)
        panelState.commit(saved.layout)
        syncRestoredPanelSettings(saved)
    }
    val allChangesSavedMessage = tr("All changes are saved.", "所有更改均已保存。")
    val inkOnlyClipboardMessage = tr("Clipboard actions currently apply to ink selections.", "剪贴板操作目前仅适用于墨迹选区。")
    val selectBeforeCutMessage = tr("Select ink before cutting.", "请先选择墨迹再剪切。")
    val selectBeforeCopyMessage = tr("Select ink before copying.", "请先选择墨迹再复制。")
    val noInkClipboardMessage = tr("The clipboard does not contain Inkloom ink.", "剪贴板中没有 Inkloom 墨迹。")
    val colorsDescription = tr("Colors", "颜色")
    val switchNoteDescription = tr("Switch notebook", "切换笔记")
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BoxWithConstraints(Modifier.height(52.dp).fillMaxWidth().background(Paper).padding(horizontal = 10.dp)) {
            // Narrow windows and large fonts: the title yields first, the command row scrolls, and
            // Actions stays reachable at the end instead of being pushed off-screen.
            val titleMaxWidth = minOf(180.dp, maxWidth * .18f)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(tr("Gallery", "图库"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Box {
                    Text(
                        document.title,
                        Modifier.widthIn(max = titleMaxWidth).clip(RoundedCornerShape(6.dp))
                            .clickable { overlays.notebookSwitcherOpen = true }
                            .semantics { contentDescription = switchNoteDescription }.padding(horizontal = 8.dp, vertical = 10.dp),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 12.sp, color = MutedInk,
                    )
                    DropdownMenu(expanded = overlays.notebookSwitcherOpen, onDismissRequest = { overlays.notebookSwitcherOpen = false },
                        modifier = Modifier.width(320.dp).heightIn(max = 440.dp)) {
                        Text(tr("Switch notebook", "切换笔记"), Modifier.padding(16.dp), color = MutedInk)
                        documents.filter { it.trashedAt == null }.sortedByDescending { it.updatedAt }.forEach { note ->
                            DropdownMenuItem(
                                text = { Column { Text(note.title, maxLines = 2); Text(pageCount(note.pages.size), fontSize = 11.sp, color = MutedInk) } },
                                leadingIcon = { if (note.id == document.id) Icon(Icons.Outlined.Check, null) },
                                onClick = {
                                    overlays.notebookSwitcherOpen = false
                                    if (note.id != document.id) onOpenNotebook(note.id)
                                },
                            )
                        }
                    }
                }
                // Filling the remaining width (rather than wrapping) lets the spacer below push the brush,
                // layer and color group to the right edge; narrower windows scroll the row instead.
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    ProcreateTopButton(Icons.Outlined.Undo, tr("Undo", "撤销"), onClick = { canvasView?.undo() })
                    ProcreateTopButton(Icons.Outlined.Redo, tr("Redo", "重做"), onClick = { canvasView?.redo() })
                    ProcreateTopButton(Icons.Outlined.Settings, tr("Settings", "设置"), onClick = onSettings)
                    ProcreateTopButton(Icons.Outlined.Gesture, tr("Selection", "选择"),
                        selected = tool == EditorTool.SELECT || selectionActive,
                        onClick = {
                            if (tool == EditorTool.SELECT || selectionActive) finishSelectionTool(deselect = true)
                            else {
                                exitCanvasNavigation(); rememberSelectionReturnTarget()
                                tool = EditorTool.SELECT
                                onSettingsChange(settings.copy(selectionMode = SelectionMode.ADD))
                                syncCanvasInput()
                            }
                        },
                        onLongClick = {
                            exitCanvasNavigation(); rememberSelectionReturnTarget()
                            tool = EditorTool.SELECT; syncCanvasInput()
                            canvasView?.reselect()
                        })
                    if (selectionActive || selectionTransformState != null || tool == EditorTool.SELECT) {
                        ProcreateTopButton(Icons.Outlined.NearMe, tr("Transform selection", "变换选区"),
                            selected = selectionTransformState != null, onClick = {
                                exitCanvasNavigation()
                                if (selectionTransformState != null) canvasView?.finishSelectionTransform(apply = true)
                                else {
                                    rememberSelectionReturnTarget()
                                    selectedElementId = null
                                    tool = EditorTool.SELECT
                                    canvasView?.enterFreeTransform()
                                }
                            })
                    } else {
                        ProcreateTopButton(Icons.Outlined.OpenWith, tr("Move canvas", "移动画布"), selected = modes.handToolEnabled, onClick = { modes.handToolEnabled = !modes.handToolEnabled; modes.rotateToolEnabled = false })
                    }
                    ProcreateTopButton(
                        if ((EditorPanel.PAGES in panelState.layout.open)) Icons.Outlined.ViewSidebar else Icons.Outlined.TableRows,
                        tr("Toggle pages", "显示或隐藏页面栏"),
                        selected = (EditorPanel.PAGES in panelState.layout.open),
                        onClick = {
                            val open = !(EditorPanel.PAGES in panelState.layout.open)
                            panelState.show(EditorPanel.PAGES, open)
                            if (open) panelState.focus(EditorPanel.PAGES)
                            panelState.show(EditorPanel.PAGES, open)
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    offeredContinueFillColor?.let { fillColor ->
                        AssistChip(
                            onClick = {
                                if (modes.continueFilling) stopContinueFilling() else {
                                    modes.continueFilling = true
                                    continueFillStartedWithTool = tool
                                    canvasView?.setContinueFilling(true, fillColor)
                                }
                            },
                            label = { Text(if (modes.continueFilling) tr("Done filling", "完成填充") else tr("Continue filling", "继续填充"), fontSize = 10.sp) },
                            leadingIcon = { Icon(if (modes.continueFilling) Icons.Outlined.Check else Icons.Outlined.FormatColorFill, null, Modifier.size(16.dp)) },
                            modifier = Modifier.height(32.dp),
                        )
                    }
                    ProcreateTopButton(Icons.Outlined.Brush, tr("Brushes", "笔刷"), selected = tool == EditorTool.PEN, onClick = {
                        exitCanvasNavigation()
                        val alreadySelected = tool == EditorTool.PEN
                        if ((alreadySelected || tool == EditorTool.LIQUIFY) && activeBrush != null) {
                            // Opening the library is not a brush selection. Keep the live masters.
                            toggleBrushPanel()
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
                            changeBrushUsage(brushUsage.used(selected.id, eraser = false))
                            onSettingsChange(settings.copy(persistentDrawingTool = EditorTool.PEN, persistentBrushPresetId = selected.id))
                        }
                        syncCanvasInput(prepareAssets = remembered != null)
                        if (alreadySelected || EditorPanel.BRUSHES !in panelState.layout.open) toggleBrushPanel()
                    })
                    ProcreateTopButton(InkloomIcons.Eraser, tr("Eraser", "橡皮"), selected = tool == EditorTool.ERASER, onClick = {
                        exitCanvasNavigation()
                        val eraser = activeBrush?.takeIf(BrushRoleClassifier::isEraser)
                            ?: availableBrushes.firstOrNull { it.id == brushUsage.lastEraserBrushId && !it.hidden &&
                                (BrushRoleClassifier.isEraser(it) ||
                                    (brushUsage.eraserUsesPaintTip && BrushToolTipReuse.forEraser(it.v5PresetJson).isSuccess)) }
                            ?: brushes.filter(BrushRoleClassifier::isEraser).maxByOrNull(ImportedBrush::lastUsedAt)
                            ?: systemBrushCatalog().first { BrushRoleClassifier.isEraser(it) }
                        if (brushUsage.eraserUsesPaintTip && !BrushRoleClassifier.isEraser(eraser) &&
                            BrushToolTipReuse.forEraser(eraser.v5PresetJson).isSuccess) {
                            activeBrush = eraser; selectedBrushId = eraser.id; tool = EditorTool.ERASER
                            brushSize = savedBrushSize(eraser, eraser.defaultSize, EditorTool.ERASER)
                            brushOpacity = savedBrushOpacity(eraser, eraser.defaultOpacity, EditorTool.ERASER)
                            syncCanvasInput(prepareAssets = true)
                        } else selectWorkspaceBrush(eraser)
                        if (EditorPanel.BRUSHES !in panelState.layout.open) setBrushPanelOpen(true)
                    }, onLongClick = {
                        val paint = activeBrush?.takeIf { BrushRoleClassifier.editorTool(it) == EditorTool.PEN }
                            ?: availableBrushes.firstOrNull { it.id == brushUsage.lastPaintBrushId && !it.hidden }
                        val result = BrushToolTipReuse.forEraser(paint?.v5PresetJson)
                        result.onSuccess {
                            val chosen = requireNotNull(paint)
                            activeBrush = chosen; selectedBrushId = chosen.id; tool = EditorTool.ERASER
                            brushSize = savedBrushSize(chosen, chosen.defaultSize, EditorTool.ERASER)
                            brushOpacity = savedBrushOpacity(chosen, chosen.defaultOpacity, EditorTool.ERASER)
                            changeBrushUsage(brushUsage.borrowPaintTipForEraser(chosen.id))
                            onSettingsChange(settings.copy(persistentDrawingTool = EditorTool.ERASER,
                                persistentBrushPresetId = chosen.id))
                            syncCanvasInput(prepareAssets = true)
                        }.onFailure { Toast.makeText(context, it.message ?: "Tip cannot be used for erasing", Toast.LENGTH_LONG).show() }
                    })
                    ProcreateTopButton(Icons.Outlined.Layers, tr("Layers", "图层"), selected = layersOpen, onClick = {
                        setLayersPanelOpen(!panelState.isActive(EditorPanel.LAYERS))
                    })
                    ProcreateTopButton(Icons.Outlined.SwapHoriz, tr("Quick swap foreground and background colors", "快捷交换前景色和背景色"),
                        onClick = ::swapDrawingColors)
                    Box {
                        Box(Modifier.size(44.dp).semantics { contentDescription = colorsDescription }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(32.dp)) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).size(21.dp).clip(CircleShape).background(Color(backgroundColor)).border(1.5.dp, if (colorPanelOpen && modes.editSecondaryColor) Ink else OutlineSoft, CircleShape).clickable { modes.editSecondaryColor = true; showColors() },
                                )
                                Box(
                                    Modifier.align(Alignment.BottomStart).size(23.dp).clip(CircleShape).background(Color(brushColor)).border(2.dp, if (colorPanelOpen && !modes.editSecondaryColor) Ink else OutlineSoft, CircleShape).combinedClickable(
                                        onClick = { modes.editSecondaryColor = false; showColors() },
                                        onLongClick = { editorHostView.startDragAndDrop(ClipData.newPlainText("Inkloom color", brushColor.toUInt().toString()), ColorDropDragShadow(brushColor), CanvasColorDrop(brushColor), 0) },
                                    ),
                                )
                            }
                        }

                    }
                }
                ProcreateTopButton(Icons.Outlined.MoreHoriz, tr("Actions", "操作"),
                    selected = overlays.workspaceMenu, onClick = { overlays.workspaceMenu = true })
            }
        }

        if (overlays.workspaceMenu) EditorActionsDialog(settings.language, commandSection, { commandSection = it }, { overlays.workspaceMenu = false }) {
            when (commandSection) {
                0 -> {
                    DropdownMenuItem(text = { Text(tr("Search this notebook", "搜索当前笔记")) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, onClick = { overlays.workspaceMenu = false; overlays.searchOpen = true })
                    DropdownMenuItem(text = { Text(tr("Add PDF or image pages", "追加 PDF／图片页面")) }, leadingIcon = { Icon(Icons.Outlined.FileOpen, null) }, onClick = { overlays.workspaceMenu = false; onAppendPageFiles(document.id) })
                    DropdownMenuItem(text = { Text(if (selectedPage.bookmarked) tr("Remove page bookmark", "取消本页书签") else tr("Bookmark this page", "收藏本页")) }, leadingIcon = { Icon(if (selectedPage.bookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder, null) }, onClick = { overlays.workspaceMenu = false; onToggleBookmark(document.id, selectedPage.id) })
                    DropdownMenuItem(text = { Text(tr("Bookmarks", "书签")) }, leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) }, onClick = { overlays.workspaceMenu = false; overlays.bookmarksOpen = true })
                    DropdownMenuItem(text = { Text(tr("Page outline", "页面大纲")) }, leadingIcon = { Icon(Icons.Outlined.FormatListBulleted, null) }, onClick = { overlays.workspaceMenu = false; overlays.outlineOpen = true })

                }
                1 -> {
                    DropdownMenuItem(text = { Text(tr("Image filter…", "图像滤镜…")) },
                        leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                        onClick = { overlays.workspaceMenu = false; rasterFilterDialogOpen = true })
                    DropdownMenuItem(text = { Text(tr("Fit page", "适合页面")) }, leadingIcon = { Icon(Icons.Outlined.CenterFocusStrong, null) }, onClick = { overlays.workspaceMenu = false; fitPage() })
                    DropdownMenuItem(text = { Text(tr("Actual size", "实际大小")) }, onClick = { overlays.workspaceMenu = false; actualSize() })
                    DropdownMenuItem(text = { Text(tr("Fit width", "适合宽度")) }, onClick = { overlays.workspaceMenu = false; fitWidth() })
                    DropdownMenuItem(text = { Text(tr("Reset rotation", "重置旋转")) }, onClick = { overlays.workspaceMenu = false; resetViewRotation() })
                    DropdownMenuItem(text = { Text(tr("Continuous pages", "连续页面")) },
                        leadingIcon = { Icon(if (settings.continuousPages) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null) },
                        onClick = {
                            overlays.workspaceMenu = false
                            // The strip is vertical, so it starts unrotated with the current page centred.
                            onSettingsChange(settings.copy(continuousPages = !settings.continuousPages))
                            applyViewport(ViewportState(zoom, panX, 0f, 0f))
                        })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(tr("Color management", "色彩管理")) }, onClick = { overlays.workspaceMenu=false;overlays.colorManagementOpen=true })
                    DropdownMenuItem(text = { Text(tr("Canvas & resolution", "画布与分辨率")) }, onClick = { overlays.workspaceMenu = false; overlays.canvasSizeOpen = true })

                }
                2 -> {
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
                    ).let { tools ->
                        EditorToolActionGrid(tools) { chosen ->
                            overlays.workspaceMenu = false
                            exitCanvasNavigation()
                            tool = chosen
                            canvasView?.tool = chosen
                            when (chosen) {
                                EditorTool.SHAPE -> overlays.shapeInsertOpen = true
                                EditorTool.TEXT -> { insertText = ""; overlays.textInsertOpen = true }
                                EditorTool.NOTE -> { insertText = ""; overlays.stickyInsertOpen = true }
                                EditorTool.LINK -> { insertLinkTitle = ""; insertLinkUrl = ""; overlays.linkInsertOpen = true }
                                EditorTool.IMAGE -> { overlays.imageInsertOpen = true; tool = EditorTool.SELECT; canvasView?.tool = EditorTool.SELECT }
                                EditorTool.SMUDGE -> activateRememberedSmudge()
                                EditorTool.LIQUIFY -> systemBrushCatalog().firstOrNull(BrushRoleClassifier::isLiquify)?.let(::selectWorkspaceBrush)
                                else -> Unit
                            }
                            syncCanvasInput(prepareAssets = activeBrush != null)
                        }
                    }
                    DropdownMenuItem(text = { Text(tr("Blend with current brush tip", "用当前笔尖混色")) },
                        leadingIcon = { Icon(Icons.Outlined.BlurOn, null) }, onClick = {
                            overlays.workspaceMenu = false
                            borrowCurrentTipForSmudge()
                        })
                    HorizontalDivider()

                }
                3 -> {
                    DropdownMenuItem(
                        text = { Text(tr(if (settings.rulerEnabled) "Hide ruler" else "Show ruler", if (settings.rulerEnabled) "隐藏直尺" else "显示直尺")) },
                        leadingIcon = { Icon(Icons.Outlined.Straighten, null) },
                        trailingIcon = { if (settings.rulerEnabled) Icon(Icons.Outlined.Check, null) },
                        onClick = { overlays.workspaceMenu = false; onSettingsChange(settings.copy(rulerEnabled = !settings.rulerEnabled)) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr(if (settings.gridSnapEnabled) "Hide snap grid" else "Show snap grid", if (settings.gridSnapEnabled) "隐藏吸附网格" else "显示吸附网格")) },
                        leadingIcon = { Icon(Icons.Outlined.GridOn, null) },
                        trailingIcon = { if (settings.gridSnapEnabled) Icon(Icons.Outlined.Check, null) },
                        onClick = { overlays.workspaceMenu = false; onSettingsChange(settings.copy(gridSnapEnabled = !settings.gridSnapEnabled)) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr(if (settings.alignmentGuidesEnabled) "Hide alignment guides" else "Show alignment guides", if (settings.alignmentGuidesEnabled) "隐藏对齐参考线" else "显示对齐参考线")) },
                        leadingIcon = { Icon(Icons.Outlined.AlignHorizontalCenter, null) },
                        trailingIcon = { if (settings.alignmentGuidesEnabled) Icon(Icons.Outlined.Check, null) },
                        onClick = { overlays.workspaceMenu = false; onSettingsChange(settings.copy(alignmentGuidesEnabled = !settings.alignmentGuidesEnabled)) },
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
                                overlays.workspaceMenu = false
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
                            onClick = { overlays.workspaceMenu = false; onSettingsChange(settings.copy(perspectiveGuideAssisted = !settings.perspectiveGuideAssisted)) },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Edit vanishing points", "编辑消失点")) },
                            trailingIcon = { if (settings.perspectiveGuideEditing) Icon(Icons.Outlined.Check, null) },
                            onClick = { overlays.workspaceMenu = false; onSettingsChange(settings.copy(perspectiveGuideEditing = !settings.perspectiveGuideEditing)) },
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
                                overlays.workspaceMenu = false
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
                            onClick = { overlays.workspaceMenu = false; onSettingsChange(settings.copy(specialRulerAssisted = !settings.specialRulerAssisted)) },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Edit ruler", "编辑尺子")) },
                            trailingIcon = { if (settings.specialRulerEditing) Icon(Icons.Outlined.Check, null) },
                            onClick = { overlays.workspaceMenu = false; onSettingsChange(settings.copy(specialRulerEditing = !settings.specialRulerEditing)) },
                        )
                        DropdownMenuItem(
                            text = {
                                Column(Modifier.width(230.dp)) {
                                    Text(tr("Guide spacing · ${settings.specialRulerSpacing.toInt()}", "辅助线间距 · ${settings.specialRulerSpacing.toInt()}"), fontSize = 11.sp)
                                    BrushParameterSlider(settings.specialRulerSpacing, { onSettingsChange(settings.copy(specialRulerSpacing = it)) }, valueRange = 16f..240f)
                                }
                            },
                            onClick = {},
                        )
                    }
                    HorizontalDivider()

                }
                4 -> {
                    DropdownMenuItem(
                        text = { Text(tr("Drawing workspace", "绘画工作区")) },
                        leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.STUDIO) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Painting workspace", "绘画面板工作区")) },
                        leadingIcon = { Icon(Icons.Outlined.ViewSidebar, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.PAINTING) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Reading workspace", "阅读工作区")) },
                        leadingIcon = { Icon(Icons.Outlined.Visibility, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.READING) },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Views…", "视图…")) },
                        leadingIcon = { Icon(Icons.Outlined.DashboardCustomize, null) },
                        onClick = { overlays.workspaceMenu = false; overlays.viewManagerOpen = true },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (panelState.layout.locked) tr("Unlock layout", "解锁布局") else tr("Lock layout", "锁定布局")) },
                        leadingIcon = { Icon(if (panelState.layout.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, null) },
                        onClick = { overlays.workspaceMenu = false; panelState.commit(panelState.layout.copy(locked = !panelState.layout.locked)) },
                    )
                    DropdownMenuItem(text = { Text(tr("Save panel layout…", "保存面板布局…")) },
                        leadingIcon = { Icon(Icons.Outlined.Save, null) }, onClick = {
                            overlays.workspaceMenu = false; renamingPanelLayout = null; panelLayoutName = ""; overlays.savePanelLayoutOpen = true
                        })
                    DropdownMenuItem(text = { Text(tr("Export workspace layout…", "导出工作区布局…")) },
                        onClick = { overlays.workspaceMenu = false; workspaceLayoutExportPicker.launch("Inkloom-workspace.${WorkspaceLayoutCodec.FILE_EXTENSION}") })
                    DropdownMenuItem(text = { Text(tr("Import workspace layout…", "导入工作区布局…")) },
                        onClick = { overlays.workspaceMenu = false; workspaceLayoutImportPicker.launch(arrayOf(WorkspaceLayoutCodec.MIME_TYPE, "text/plain", "application/octet-stream")) })
                    panelState.namedLayouts.entries.forEach { saved ->
                        DropdownMenuItem(text = { Text(saved.name) },
                            leadingIcon = { Icon(Icons.Outlined.DashboardCustomize, null) }, onClick = {
                                overlays.workspaceMenu = false
                                panelState.restoreNamedLayout(saved.name)?.let(::syncRestoredPanelSettings)
                            })
                    }
                    if (panelState.namedLayouts.entries.isNotEmpty()) DropdownMenuItem(
                        text = { Text(tr("Manage panel layouts…", "管理面板布局…")) },
                        onClick = { overlays.workspaceMenu = false; overlays.managePanelLayoutsOpen = true },
                    )

                }
                5 -> {
                    // Share sends straight to another app; Export saves a file where the user chooses.
                    DropdownMenuItem(text = { Text(tr("Share PDF…", "分享 PDF…")) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { overlays.workspaceMenu = false; onSharePdf() })
                    DropdownMenuItem(text = { Text(tr("Share current page…", "分享当前页…")) }, leadingIcon = { Icon(Icons.Outlined.Image, null) }, onClick = { overlays.workspaceMenu = false; overlays.pageShareOpen = true })
                    HorizontalDivider(color = OutlineSoft)
                    DropdownMenuItem(text = { Text(tr("Export PDF", "导出 PDF")) }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }, onClick = { overlays.workspaceMenu = false; onExport() })
                    DropdownMenuItem(text = { Text(tr("Export current page", "导出当前页")) }, onClick = { overlays.workspaceMenu = false; overlays.pageExportOpen = true })
                    DropdownMenuItem(text = { Text(tr("Export editable Inkloom package", "导出可编辑 Inkloom 文件")) }, onClick = { overlays.workspaceMenu = false; onExportBundle() })
                }
            }
        }
        if (overlays.pageExportOpen) PageRasterExportDialog(storedPage, settings.language,
            onDismiss = { overlays.pageExportOpen = false }, onExport = { options -> overlays.pageExportOpen = false; onExportPage(options) })
        if (overlays.pageShareOpen) PageRasterExportDialog(storedPage, settings.language, sharing = true,
            onDismiss = { overlays.pageShareOpen = false }, onExport = { options -> overlays.pageShareOpen = false; onSharePage(options) })
        if(overlays.colorManagementOpen) ColorManagementDialog(storedPage,settings.language,modes.canvasSizeBusy,
            onDismiss={overlays.colorManagementOpen=false},onApply={ next ->
                val view=canvasView
                if(view!=null && !view.selectionLayerTransferPending) {
                    view.finishSelectionTransform(apply=true);view.selectionLayerTransferPending=true;modes.canvasSizeBusy=true
                    appearanceScope.launch {
                        var prepared:PreparedPageGeometry?=null;var published=false
                        try {
                            kotlinx.coroutines.withTimeout(15_000) { while(view.hasPendingPaintPublicationForMerge()) kotlinx.coroutines.delay(8) }
                            val before=currentAppearancePage
                            prepared=withContext(Dispatchers.IO){maskAssets.prepareColorManagement(before,next)}
                            val transaction=requireNotNull(prepared)
                            check(onApplyPageGeometry(document.id,before.id,transaction.edit,false)) { "Page changed during color setup" }
                            view.recordExternalEdit(undo={onApplyPageGeometry(document.id,before.id,transaction.edit,true)},
                                redo={onApplyPageGeometry(document.id,before.id,transaction.edit,false)},dispose=transaction::close)
                            published=true;overlays.colorManagementOpen=false
                        } catch(failure:Throwable) { Toast.makeText(context,failure.message?:"Color management failed",Toast.LENGTH_LONG).show() }
                        finally { if(!published)prepared?.close();view.selectionLayerTransferPending=false;modes.canvasSizeBusy=false }
                    }
                }
            })
        if (overlays.canvasSizeOpen) CanvasSizeDialog(storedPage, settings.language, modes.canvasSizeBusy,
            onDismiss = { overlays.canvasSizeOpen = false }, onApply = { request ->
                val view = canvasView
                if (view != null && !view.selectionLayerTransferPending) {
                    view.finishSelectionTransform(apply = true)
                    view.selectionLayerTransferPending = true; modes.canvasSizeBusy = true
                    appearanceScope.launch {
                        var prepared: PreparedPageGeometry? = null
                        var published = false
                        try {
                            kotlinx.coroutines.withTimeout(15_000) { while (view.hasPendingPaintPublicationForMerge()) kotlinx.coroutines.delay(8) }
                            val before = currentAppearancePage
                            prepared = withContext(Dispatchers.IO) { maskAssets.prepareCanvasSize(before, request) }
                            val transaction = requireNotNull(prepared)
                            check(onApplyPageGeometry(document.id, before.id, transaction.edit, false)) { "Page changed during resize" }
                            view.recordExternalEdit(
                                undo = { onApplyPageGeometry(document.id, before.id, transaction.edit, true) },
                                redo = { onApplyPageGeometry(document.id, before.id, transaction.edit, false) }, dispose = transaction::close)
                            published = true; overlays.canvasSizeOpen = false; fitPage()
                        } catch (failure: Throwable) {
                            android.util.Log.e("InkloomCanvas", "Canvas resize failed", failure)
                            Toast.makeText(context, failure.message ?: "Canvas resize failed", Toast.LENGTH_LONG).show()
                        } finally {
                            if (!published) prepared?.close()
                            view.selectionLayerTransferPending = false; modes.canvasSizeBusy = false
                        }
                    }
                }
            })
        if (overlays.viewManagerOpen) EditorViewManagerDialog(
            openPanels = panelState.layout.open,
            onToggle = ::setManagedPanelVisible,
            onDismiss = { overlays.viewManagerOpen = false },
        )
        if (overlays.savePanelLayoutOpen) AlertDialog(
            onDismissRequest = { overlays.savePanelLayoutOpen = false },
            title = { Text(if (renamingPanelLayout == null) tr("Save panel layout", "保存面板布局") else tr("Rename panel layout", "重命名面板布局")) },
            text = {
                Column {
                    OutlinedTextField(panelLayoutName, { panelLayoutName = it.take(40) },
                        label = { Text(tr("Name", "名称")) }, singleLine = true)
                    if (renamingPanelLayout == null && panelState.namedLayouts.entries.any { it.name.equals(panelLayoutName.trim(), ignoreCase = true) })
                        Text(tr("Saving will replace this layout", "保存后将替换同名布局"), color = MutedInk, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(enabled = panelLayoutName.isNotBlank(), onClick = {
                val old = renamingPanelLayout
                if (old == null) panelState.saveNamedLayout(panelLayoutName, settings)
                else panelState.renameNamedLayout(old, panelLayoutName)
                overlays.savePanelLayoutOpen = false; renamingPanelLayout = null
            }) { Text(if (renamingPanelLayout == null) tr("Save", "保存") else tr("Rename", "重命名")) } },
            dismissButton = { TextButton(onClick = { overlays.savePanelLayoutOpen = false; renamingPanelLayout = null }) { Text(tr("Cancel", "取消")) } },
        )
        if (overlays.managePanelLayoutsOpen) AlertDialog(
            onDismissRequest = { overlays.managePanelLayoutsOpen = false },
            title = { Text(tr("Panel layouts", "面板布局")) },
            text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                panelState.namedLayouts.entries.forEach { saved ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(modifier = Modifier.weight(1f), onClick = {
                            panelState.restoreNamedLayout(saved.name)?.let(::syncRestoredPanelSettings)
                            overlays.managePanelLayoutsOpen = false
                        }) { Text(saved.name) }
                        IconButton(onClick = {
                            overlays.managePanelLayoutsOpen = false; renamingPanelLayout = saved.name
                            panelLayoutName = saved.name; overlays.savePanelLayoutOpen = true
                        }) { Icon(Icons.Outlined.Edit, tr("Rename ${saved.name}", "重命名${saved.name}")) }
                        IconButton(onClick = { deletingPanelLayout = saved.name; overlays.managePanelLayoutsOpen = false }) {
                            Icon(Icons.Outlined.DeleteOutline, tr("Delete ${saved.name}", "删除${saved.name}"))
                        }
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { overlays.managePanelLayoutsOpen = false }) { Text(tr("Done", "完成")) } },
        )
        deletingPanelLayout?.let { name -> AlertDialog(
            onDismissRequest = { deletingPanelLayout = null },
            title = { Text(tr("Delete panel layout?", "删除面板布局？")) },
            text = { Text(name) },
            confirmButton = { TextButton(onClick = { panelState.deleteNamedLayout(name); deletingPanelLayout = null }) { Text(tr("Delete", "删除")) } },
            dismissButton = { TextButton(onClick = { deletingPanelLayout = null }) { Text(tr("Cancel", "取消")) } },
        ) }
        HorizontalDivider(color = OutlineSoft)
    Box(Modifier.weight(1f).clipToBounds()) {
        fun closeEditorPanel(panel: EditorPanel) {
            when (panel) {
                EditorPanel.PAGES -> panelState.show(EditorPanel.PAGES, false)
                EditorPanel.LAYERS -> panelState.show(EditorPanel.LAYERS, false)
                EditorPanel.BRUSHES -> setBrushPanelOpen(false)
                EditorPanel.SUBTOOLS, EditorPanel.PROPERTIES -> Unit
                else -> if (panelState.layout.open.none { it.ordinal >= EditorPanel.WHEEL.ordinal }) {
                    colorPreview.clear()
                    canvasView?.inkColor = brushColor; canvasView?.secondaryInkColor = backgroundColor
                }
            }
        }
        EditorPanels(panelState, onBackBeforePanels = {
            when {
                canvasView?.selectionTransformUiState != null -> { canvasView?.finishSelectionTransform(apply = false); true }
                imageCanvasTransform != null && imageTransformId == null -> { finishImageTransform(false); true }
                selectionActive -> { finishSelectionTool(deselect = true); true }
                else -> false
            }
        }, onReleaseInput = { canvasView?.requestFocus() }, onClose = ::closeEditorPanel, canvas = {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
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
                // Neighbouring pages of the continuous view, drawn beside the live canvas.
                stripNeighbours().forEach { placement ->
                    val neighbour = document.pages[placement.index]
                    val fit = stripFitSize(neighbour)
                    val density = LocalDensity.current
                    key(neighbour.id) {
                        ContinuousPageNeighbor(neighbour, tr("Page ${placement.index + 1}", "第 ${placement.index + 1} 页"), assets,
                            renderWidthPx = fit.width.toInt(), scale = zoom, translationX = panX,
                            translationY = panY + placement.centerOffset * zoom,
                            modifier = with(density) { Modifier.size(fit.width.toDp(), fit.height.toDp()) })
                    }
                }
                PaperCanvas(
                    onApplyRasterLayerMerge = onApplyRasterLayerMerge,
                    onApplyRasterLayerFilter = onApplyRasterLayerFilter,
                    onApplyLayerInsertion = onApplyLayerInsertion,
                    onApplyLayerTreeEdit = onApplyLayerTreeEdit,
                    onApplySelectionLayerTransfer = onApplySelectionLayerTransfer,
                    onSelectionTransferLayerChosen = { activeLayerId = it; canvasView?.activeLayerId = it },
                    modifier = Modifier.sizeIn(maxWidth = toolbarAreaWidth * .88f, maxHeight = toolbarAreaHeight * .88f).aspectRatio(PageCropTransform.visibleWidth(selectedPage) / PageCropTransform.visibleHeight(selectedPage).coerceAtLeast(1f)),
                    tool = tool,
                    layerPickMode = layerPickMode,
                    onLayerPickCandidates = { candidates ->
                        layerPickMode = false
                        layerPickPreviewId = null
                        when (candidates.size) {
                            0 -> Toast.makeText(context, settings.language.text("No visible layer at this point", "此处没有可见图层"), Toast.LENGTH_SHORT).show()
                            1 -> selectLayerTarget(candidates.first())
                            else -> layerPickCandidates = candidates
                        }
                    },
                    onLayerPickPreview = { layerPickPreviewId = it },
                    onView = { view ->
                        canvasView = view
                        view.onSelectionTransformChanged = { selectionTransformState = it }
                        view.onLayerMaskEditingChanged = { editingMaskId = it }
                        maskEditor.attach(view)
                        view.onLayerMaskStrokeCommitted = maskEditor::publishStroke
                    },
                    page = paintingPage,
                    documentId = document.id,
                    onSetLayerRasterTileSurface = onSetLayerRasterTileSurface,
                    onElement = onElement,
                    onRemoveElement = onRemoveElement,
                    onDuplicateElement = onDuplicateElement,
                    selectedElementId = selectedElementId,
                    onSelectElement = { selectedElementId = it },
                    onBeginQuickShapeEdit = {
                        tool = EditorTool.SELECT
                        canvasView?.tool = EditorTool.SELECT
                    },
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
                        followStrip()
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
                        followStrip()
                    },
                    onPan = { dx, dy ->
                        val view = canvasView ?: return@PaperCanvas
                        val current = ViewportState(view.viewportScale, view.viewportPanX, view.viewportPanY, view.viewportRotationDegrees)
                        val next = ViewportTransform.panByScreen(current, dx, dy, geometry = currentViewportGeometry())
                        applyViewport(next)
                        view.apply { viewportPanX = next.panX; viewportPanY = next.panY }
                        followStrip()
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
                    onCanvasContact = { if (EditorPanel.BRUSHES in panelState.layout.open && EditorPanel.BRUSHES !in panelState.layout.placed) setBrushPanelOpen(false) },
                    onBackKey = {
                        when {
                            imageCanvasTransform != null && imageTransformId == null -> { finishImageTransform(false); true }
                            panelState.closeTopPanel(::closeEditorPanel) -> true
                            selectionActive -> { finishSelectionTool(deselect = true); true }
                            else -> false
                        }
                    },
                    onColorDropCommitted = { dropped ->
                        offeredContinueFillColor = dropped
                        modes.continueFilling = false
                    },
                    activeBrush = activeBrush,
                    borrowPaintTipForEraser = tool == EditorTool.ERASER && brushUsage.eraserUsesPaintTip && activeBrush?.id == brushUsage.lastEraserBrushId,
                    borrowPaintTipForSmudge = tool == EditorTool.SMUDGE && brushUsage.smudgeUsesPaintTip && activeBrush?.id == brushUsage.lastSmudgeBrushId,
                    brushSize = brushSize,
                    brushOpacity = brushOpacity,
                    brushColor = brushColor,
                    secondaryBrushColor = backgroundColor, colorPreview = colorPreview,
                    shapeKind = insertShape,
                    activeLayerId = activeLayerId,
                    transformLayerIds = secondaryLayers + activeLayerId,
                    eraserPreset = settings.eraserPreset,
                    forceNavigation = modes.handToolEnabled || modes.handToolHeld || modes.rotateToolEnabled || modes.rotateToolHeld,
                    rotateNavigation = modes.rotateToolEnabled || modes.rotateToolHeld,
                    searchBounds = (searchFocus?.takeIf { it.pageId == selectedPage.id }?.bounds ?: pdfLinkFocus?.takeIf { it.first == selectedPage.id }?.second)?.let { PageCropTransform.project(it, storedPage.crop) },
                    onPdfGotoLink = { link ->
                        document.pages.firstOrNull { it.id == link.destinationPageId }?.let { target ->
                            searchFocus = null
                            pdfLinkFocus = target.id to PdfGotoLinkNavigation.destinationBounds(link)
                            jumpToPage(target.id)
                            if (link.destinationZoom > 0f) zoom = link.destinationZoom.coerceIn(ViewportTransform.MINIMUM_SCALE, ViewportTransform.MAXIMUM_SCALE)
                        }
                    },
                    // A page turn, like the navigator arrows: not a jump, and the last page adds nothing.
                    onPageSwipe = { next ->
                        val index = document.pages.indexOfFirst { it.id == selectedPage.id }
                        document.pages.getOrNull(if (next) index + 1 else index - 1)?.takeIf { index >= 0 }?.let { selectPageWithViewport(it.id) }
                    },
                    documents = documents,
                    onOpenInternalLink = openInternalLink,
                    onInputDiagnostic = { kind, pressure, tilt, buttons -> inputDiagnostic = "$kind  pressure ${"%.3f".format(pressure)}  tilt ${"%.2f".format(Math.toDegrees(tilt.toDouble()))}°  buttons $buttons" },
                    onColorSampled = { sampled ->
                        if (modes.editSecondaryColor) changeBackgroundColor(sampled) else changeBrushColor(sampled)
                    },
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
                                ?: availableBrushes.firstOrNull { it.id == brushUsage.lastEraserBrushId && !it.hidden &&
                                    (BrushRoleClassifier.isEraser(it) ||
                                        (brushUsage.eraserUsesPaintTip && BrushToolTipReuse.forEraser(it.v5PresetJson).isSuccess)) }
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
                                    } else selected?.let { selectImportedBrush(it, persistent = !temporary) } ?: return
                                    brushSize = slot.size
                                    brushOpacity = slot.opacity
                                    changeBrushColor(slot.colorArgb)
                                    syncCanvasInput(prepareAssets = activeBrush != null)
                                }
                            }
                        }

                        if (activation.phase == ShortcutPhase.RELEASE) {
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_RULER) onSettingsChange(settings.copy(rulerEnabled = modes.rulerBeforeShortcut))
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_GRID_SNAP) onSettingsChange(settings.copy(gridSnapEnabled = modes.gridBeforeShortcut))
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_ALIGNMENT_GUIDES) onSettingsChange(settings.copy(alignmentGuidesEnabled = modes.guidesBeforeShortcut))
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.HAND_TOOL) modes.handToolHeld = false
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.ROTATE_VIEW_TOOL) modes.rotateToolHeld = false
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
                                syncCanvasInput()
                            }
                            ShortcutAction.SELECT_TOOL -> { exitCanvasNavigation(); if (tool != EditorTool.SELECT) rememberDrawingSelection(); tool = EditorTool.SELECT; syncCanvasInput() }
                            ShortcutAction.HAND_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) { modes.rotateToolEnabled = false; modes.rotateToolHeld = false; modes.handToolHeld = true } else { val enable = !modes.handToolEnabled; exitCanvasNavigation(); modes.handToolEnabled = enable }
                            ShortcutAction.ROTATE_VIEW_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) { modes.handToolEnabled = false; modes.handToolHeld = false; modes.rotateToolHeld = true } else { val enable = !modes.rotateToolEnabled; exitCanvasNavigation(); modes.rotateToolEnabled = enable }
                            ShortcutAction.EYEDROPPER_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) {
                                exitCanvasNavigation()
                                rememberDrawingSelection()
                                tool = EditorTool.EYEDROPPER
                            } else {
                                exitCanvasNavigation()
                                if (tool == EditorTool.EYEDROPPER) restorePreviousDrawingTool() else { rememberDrawingSelection(); tool = EditorTool.EYEDROPPER }
                            }
                            ShortcutAction.SHAPE_TOOL -> { exitCanvasNavigation(); tool = EditorTool.SHAPE; overlays.shapeInsertOpen = true }
                            ShortcutAction.TEXT_TOOL -> { exitCanvasNavigation(); tool = EditorTool.TEXT; insertText = ""; overlays.textInsertOpen = true }
                            ShortcutAction.NOTE_TOOL -> { exitCanvasNavigation(); tool = EditorTool.NOTE; insertText = ""; overlays.stickyInsertOpen = true }
                            ShortcutAction.LINK_TOOL -> { exitCanvasNavigation(); tool = EditorTool.LINK; insertLinkTitle = ""; insertLinkUrl = ""; overlays.linkInsertOpen = true }
                            ShortcutAction.IMAGE_TOOL -> { exitCanvasNavigation(); overlays.imageInsertOpen = true; tool = EditorTool.SELECT }
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
                            ShortcutAction.PASTE_SELECTION_AT_VIEW -> { selectedElementId = null; if (canvasView?.pasteSelectionFromClipboard(atViewCenter = true) != true) Toast.makeText(context, noInkClipboardMessage, Toast.LENGTH_SHORT).show() }
                            ShortcutAction.PASTE_SELECTION -> { selectedElementId = null; if (canvasView?.pasteSelectionFromClipboard() != true) Toast.makeText(context, noInkClipboardMessage, Toast.LENGTH_SHORT).show() }
                            ShortcutAction.DELETE_SELECTION -> selectedElementId?.let { onRemoveElement(document.id, selectedPage.id, it); selectedElementId = null } ?: canvasView?.deleteSelection()
                            ShortcutAction.SELECT_ALL -> { selectedElementId = null; tool = EditorTool.SELECT; canvasView?.selectAllEditableStrokes() }
                            ShortcutAction.DESELECT -> finishSelectionTool(deselect = true)
                            ShortcutAction.UNDO -> canvasView?.undo()
                            ShortcutAction.REDO -> canvasView?.redo()
                            ShortcutAction.NEW_LAYER -> { createLayer(folder = false) }
                            ShortcutAction.MERGE_LAYER_DOWN -> canvasView?.requestLayerMerge(activeLayerId)
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
                                    modes.rulerBeforeShortcut = settings.rulerEnabled
                                    onSettingsChange(settings.copy(rulerEnabled = true))
                                } else onSettingsChange(settings.copy(rulerEnabled = !settings.rulerEnabled))
                            }
                            ShortcutAction.TOGGLE_GRID_SNAP -> {
                                if (activation.trigger == ShortcutTrigger.HOLD) {
                                    modes.gridBeforeShortcut = settings.gridSnapEnabled
                                    onSettingsChange(settings.copy(gridSnapEnabled = true))
                                } else onSettingsChange(settings.copy(gridSnapEnabled = !settings.gridSnapEnabled))
                            }
                            ShortcutAction.TOGGLE_ALIGNMENT_GUIDES -> {
                                if (activation.trigger == ShortcutTrigger.HOLD) {
                                    modes.guidesBeforeShortcut = settings.alignmentGuidesEnabled
                                    onSettingsChange(settings.copy(alignmentGuidesEnabled = true))
                                } else onSettingsChange(settings.copy(alignmentGuidesEnabled = !settings.alignmentGuidesEnabled))
                            }
                            ShortcutAction.PAGE_BACK -> stepPageHistory(forward = false)
                            ShortcutAction.PAGE_FORWARD -> stepPageHistory(forward = true)
                            ShortcutAction.PREVIOUS_PAGE -> document.pages.indexOfFirst { it.id == selectedPage.id }.takeIf { it > 0 }?.let { selectPageWithViewport(document.pages[it - 1].id) }
                            ShortcutAction.NEXT_PAGE -> document.pages.indexOfFirst { it.id == selectedPage.id }.takeIf { it in 0 until document.pages.lastIndex }?.let { selectPageWithViewport(document.pages[it + 1].id) }
                            ShortcutAction.TOGGLE_BRUSH_PANEL -> toggleBrushPanel()
                            ShortcutAction.TOGGLE_LAYER_PANEL -> {
                                setLayersPanelOpen(!layersOpen)
                            }
                            ShortcutAction.TOGGLE_PANELS -> if (!modes.panelsHiddenByShortcut) {
                                openPanelsBeforeShortcut = panelState.layout.open
                                toolbarBeforeShortcut = settings.showToolbar
                                panelState.commit(panelState.layout.copy(open = emptySet()))
                                onSettingsChange(settings.copy(showToolbar = false))
                                modes.panelsHiddenByShortcut = true
                            } else {
                                panelState.commit(panelState.layout.copy(open = openPanelsBeforeShortcut))
                                onSettingsChange(settings.copy(showToolbar = toolbarBeforeShortcut))
                                modes.panelsHiddenByShortcut = false
                            }
                            ShortcutAction.SAVE_CHECKPOINT -> Toast.makeText(context, allChangesSavedMessage, Toast.LENGTH_SHORT).show()
                            ShortcutAction.QUICK_SLOT_1, ShortcutAction.QUICK_SLOT_2, ShortcutAction.QUICK_SLOT_3, ShortcutAction.QUICK_SLOT_4,
                            ShortcutAction.QUICK_SLOT_5, ShortcutAction.QUICK_SLOT_6, ShortcutAction.QUICK_SLOT_7, ShortcutAction.QUICK_SLOT_8 -> ToolboxQuickSlots.indexOf(action)?.let { applyQuickSlot(it, activation.trigger == ShortcutTrigger.HOLD) }
                        }
                    },
                    onSymmetryGuideChange = { guide ->
                        if (!updateActiveLayerDrawingGuide { it.copy(
                            symmetryCenterX = guide.centerX,
                            symmetryCenterY = guide.centerY,
                            symmetryAngleDegrees = guide.angleDegrees,
                        ) }) onSettingsChange(settings.copy(symmetryCenterX = guide.centerX, symmetryCenterY = guide.centerY, symmetryAngleDegrees = guide.angleDegrees))
                    },
                    onRulerGuideChange = { guide ->
                        if (!updateActiveLayerDrawingGuide { it.copy(
                            rulerCenterX = guide.centerX,
                            rulerCenterY = guide.centerY,
                            rulerAngleDegrees = guide.angleDegrees,
                            rulerSnapDegrees = guide.snapDegrees,
                        ) }) onSettingsChange(settings.copy(rulerCenterX = guide.centerX, rulerCenterY = guide.centerY, rulerAngleDegrees = guide.angleDegrees, rulerSnapDegrees = guide.snapDegrees))
                    },
                    onCanvasGuideChange = { guide ->
                        if (!updateActiveLayerDrawingGuide { it.copy(
                            gridSnapEnabled = guide.gridSnapEnabled,
                            gridSpacing = guide.gridSpacing,
                            alignmentGuidesEnabled = guide.alignmentGuidesEnabled,
                            verticalGuideX = guide.verticalGuideX,
                            horizontalGuideY = guide.horizontalGuideY,
                        ) }) onSettingsChange(settings.copy(gridSnapEnabled = guide.gridSnapEnabled, gridSpacing = guide.gridSpacing, alignmentGuidesEnabled = guide.alignmentGuidesEnabled, verticalGuideX = guide.verticalGuideX, horizontalGuideY = guide.horizontalGuideY))
                    },
                    onPerspectiveGuideChange = { guide ->
                        val points = guide.normalized().points
                        if (!updateActiveLayerDrawingGuide { it.copy(
                            perspectiveMode = guide.mode.name,
                            perspectiveAssisted = guide.assisted,
                            perspectiveEditing = guide.editing,
                            perspectivePoint1X = points[0].x,
                            perspectivePoint1Y = points[0].y,
                            perspectivePoint2X = points[1].x,
                            perspectivePoint2Y = points[1].y,
                            perspectivePoint3X = points[2].x,
                            perspectivePoint3Y = points[2].y,
                        ) }) onSettingsChange(settings.copy(perspectiveGuideMode = guide.mode, perspectiveGuideAssisted = guide.assisted, perspectiveGuideEditing = guide.editing, perspectivePoint1X = points[0].x, perspectivePoint1Y = points[0].y, perspectivePoint2X = points[1].x, perspectivePoint2Y = points[1].y, perspectivePoint3X = points[2].x, perspectivePoint3Y = points[2].y))
                    },
                    onSpecialRulerChange = { ruler ->
                        if (!updateActiveLayerDrawingGuide { it.copy(
                            specialRulerMode = ruler.mode.name,
                            specialRulerAssisted = ruler.assisted,
                            specialRulerEditing = ruler.editing,
                            specialRulerCenterX = ruler.centerX,
                            specialRulerCenterY = ruler.centerY,
                            specialRulerAngleDegrees = ruler.angleDegrees,
                            specialRulerSpacing = ruler.spacing,
                        ) }) onSettingsChange(settings.copy(specialRulerMode = ruler.mode, specialRulerAssisted = ruler.assisted, specialRulerEditing = ruler.editing, specialRulerCenterX = ruler.centerX, specialRulerCenterY = ruler.centerY, specialRulerAngleDegrees = ruler.angleDegrees, specialRulerSpacing = ruler.spacing))
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
                        proxy.activatePageAt = { x, y -> settings.continuousPages && stripActivateAt(Offset(x, y)) }
                        proxy.imageAtPoint = { x, y ->
                            if (imageCanvasTransform != null || modes.handToolEnabled || modes.handToolHeld || modes.rotateToolEnabled || modes.rotateToolHeld || tool == EditorTool.ERASER) null
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
                // Page turn cue: a veil over the new page fades out. It takes no input, so drawing
                // can start at once, and the canvas itself is never faded.
                val pageTurnVeil = remember(document.id) { androidx.compose.animation.core.Animatable(0f) }
                var lastShownPageId by remember(document.id) { mutableStateOf(selectedPage.id) }
                LaunchedEffect(selectedPage.id) {
                    if (selectedPage.id == lastShownPageId || settings.continuousPages) { lastShownPageId = selectedPage.id; return@LaunchedEffect }
                    lastShownPageId = selectedPage.id
                    pageTurnVeil.snapTo(.55f)
                    pageTurnVeil.animateTo(0f, androidx.compose.animation.core.tween(180))
                }
                if (pageTurnVeil.value > 0f) Box(Modifier
                    .sizeIn(maxWidth = toolbarAreaWidth * .88f, maxHeight = toolbarAreaHeight * .88f)
                    .aspectRatio(PageCropTransform.visibleWidth(selectedPage) / PageCropTransform.visibleHeight(selectedPage).coerceAtLeast(1f))
                    .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = panX, translationY = panY, rotationZ = viewRotation)
                    .background(Workspace.copy(alpha = pageTurnVeil.value)))
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
                                onOpenInternalLink = openInternalLink,
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
                val navigatorPreferences = remember(context) { context.getSharedPreferences("inkloom_page_navigator", android.content.Context.MODE_PRIVATE) }
                var navigatorX by remember { mutableFloatStateOf(navigatorPreferences.getFloat("x", 1f).coerceIn(0f, 1f)) }
                var navigatorY by remember { mutableFloatStateOf(navigatorPreferences.getFloat("y", 1f).coerceIn(0f, 1f)) }
                var navigatorWidth by remember { mutableIntStateOf(0) }
                var navigatorHeight by remember { mutableIntStateOf(0) }
                var pageJumpOpen by remember(document.id) { mutableStateOf(false) }
                var navigatorBounds by remember { mutableStateOf(androidx.compose.ui.unit.IntRect.Zero) }
                val navigatorDensity = LocalDensity.current
                val navigatorMargin = with(navigatorDensity) { 14.dp.toPx() }
                val navigatorTravelX = (with(navigatorDensity) { maxWidth.toPx() } - navigatorWidth - navigatorMargin * 2f).coerceAtLeast(0f)
                val navigatorTravelY = (with(navigatorDensity) { maxHeight.toPx() } - navigatorHeight - navigatorMargin * 2f).coerceAtLeast(0f)
                val navigatorDescription = tr("Drag page navigator", "拖动页码导航")
                val pageJumpDescription = tr("Jump to page", "长按跳转页面")
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset((navigatorMargin + navigatorTravelX * navigatorX).roundToInt(), (navigatorMargin + navigatorTravelY * navigatorY).roundToInt()) }
                        .onGloballyPositioned {
                            navigatorWidth = it.size.width; navigatorHeight = it.size.height
                            val rect = it.boundsInWindow()
                            navigatorBounds = androidx.compose.ui.unit.IntRect(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt())
                        }
                        .pointerInput(navigatorTravelX, navigatorTravelY) {
                            detectDragGesturesAfterLongPress(
                                onDragEnd = { navigatorPreferences.edit().putFloat("x", navigatorX).putFloat("y", navigatorY).apply() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (navigatorTravelX > 0f) navigatorX = (navigatorX + amount.x / navigatorTravelX).coerceIn(0f, 1f)
                                    if (navigatorTravelY > 0f) navigatorY = (navigatorY + amount.y / navigatorTravelY).coerceIn(0f, 1f)
                                },
                            )
                        }
                        .zIndex(2f)
                        .semantics { contentDescription = navigatorDescription },
                    shape = RoundedCornerShape(22.dp),
                    color = Paper,
                    border = CardDefaults.outlinedCardBorder(),
                    shadowElevation = 5.dp,
                ) {
                    Row(Modifier.heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Back and Forward appear only after a jump, and name the page they lead to.
                        val existingPageIds = document.pages.map(InkPage::id).toSet()
                        fun pageLabel(pageId: String) = app.inkloom.document.PageNumberLookup.label(document.pages, document.pages.indexOfFirst { it.id == pageId })
                        val backTarget = pageHistory.backTarget(selectedPage.id, existingPageIds)
                        val forwardTarget = pageHistory.forwardTarget(selectedPage.id, existingPageIds)
                        if (backTarget != null) {
                            val label = pageLabel(backTarget)
                            val description = tr("Back to page $label", "返回第 $label 页")
                            TextButton(onClick = { stepPageHistory(forward = false) }, contentPadding = PaddingValues(start = 8.dp, end = 6.dp),
                                modifier = Modifier.heightIn(min = 38.dp).semantics(mergeDescendants = true) { contentDescription = description }) {
                                Icon(Icons.Outlined.ArrowBack, null, Modifier.size(16.dp), tint = Ink)
                                Spacer(Modifier.width(4.dp))
                                Text(label, fontSize = 11.sp, color = Ink, maxLines = 1)
                            }
                        }
                        if (forwardTarget != null) {
                            val label = pageLabel(forwardTarget)
                            IconButton(onClick = { stepPageHistory(forward = true) }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Outlined.ArrowForward, tr("Forward to page $label", "前进到第 $label 页"), Modifier.size(16.dp), tint = Ink)
                            }
                        }
                        if (backTarget != null || forwardTarget != null) VerticalDivider(Modifier.height(20.dp), color = OutlineSoft)
                        Box {
                            val ratio = zoom * (canvasView?.width ?: 0) / PageCropTransform.visibleWidth(selectedPage).coerceAtLeast(1f)
                            val zoomDescription = tr("Quick zoom", "快捷缩放")
                            TextButton(onClick = { overlays.quickZoomMenu = true }, modifier = Modifier.semantics { contentDescription = zoomDescription }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Text("${(ratio * 100f).roundToInt()}%", fontSize = 11.sp, color = Ink)
                            }
                            DropdownMenu(expanded = overlays.quickZoomMenu, onDismissRequest = { overlays.quickZoomMenu = false }) {
                                listOf(75, 90, 100, 110, 125).forEach { percent ->
                                    DropdownMenuItem(text = { Text("$percent%") }, onClick = {
                                        val view = canvasView
                                        if (view != null && view.width > 0) {
                                            val target = percent / 100f * PageCropTransform.visibleWidth(selectedPage) / view.width
                                            applyViewport(ViewportTransform.zoomAroundScreen(ViewportState(zoom, panX, panY, viewRotation), target / zoom.coerceAtLeast(.001f), 0f, 0f, geometry = currentViewportGeometry()))
                                        }
                                        overlays.quickZoomMenu = false
                                    })
                                }
                                DropdownMenuItem(text = { Text(tr("Fit page", "适合页面")) }, onClick = { fitPage(); overlays.quickZoomMenu = false })
                            }
                        }

                        IconButton(
                            onClick = { document.pages.getOrNull(currentPageIndex - 1)?.let { selectPageWithViewport(it.id) } },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier.size(38.dp),
                        ) { Icon(Icons.Outlined.KeyboardArrowUp, tr("Previous page", "上一页"), Modifier.size(20.dp)) }
                        Text(
                            "${currentPageIndex + 1}/${document.pages.size}",
                            Modifier
                                .combinedClickable(onClick = { pageJumpOpen = true }, onLongClick = { pageJumpOpen = true })
                                .semantics { contentDescription = pageJumpDescription }
                                .padding(horizontal = 6.dp, vertical = 11.dp),
                            fontSize = 10.sp,
                            color = MutedInk,
                        )
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
                if (pageJumpOpen) PageJumpPopup(document.pages, selectedPage.id, navigatorBounds, LocalUiLanguage.current,
                    onSelect = { jumpToPage(it); pageJumpOpen = false }, onDismiss = { pageJumpOpen = false })
                val dockVertical = settings.toolbarDock in setOf(ToolbarDock.LEFT, ToolbarDock.RIGHT) || (settings.toolbarDock == ToolbarDock.FREE && settings.verticalToolbar)
                var measuredToolbarWidth by remember(dockVertical) { mutableFloatStateOf(if (dockVertical) 88f else FloatingToolbarPlacement.LONG_EDGE_DP) }
                var measuredToolbarHeight by remember(dockVertical) { mutableFloatStateOf(if (dockVertical) 600f else FloatingToolbarPlacement.SHORT_EDGE_DP) }
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
                val boundedToolbarPosition = FloatingToolbarPlacement.clamp(toolbarX, toolbarY,
                    toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical,
                    measuredToolbarWidth, measuredToolbarHeight)
                // Rendering is clamped to the temporary viewport; retain the saved position
                // through keyboard appearance and rotation until the user actually moves it.
                val toolbarSnapPreview = if (toolbarDragOrigin != null && toolbarDragTravel >= 24f)
                    FloatingToolbarPlacement.snapTarget(FloatingToolbarPosition(toolbarX, toolbarY),
                        toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical,
                        measuredToolbarWidth, measuredToolbarHeight) else ToolbarDock.FREE
                if (toolbarSnapPreview != ToolbarDock.FREE) {
                    val preview = when (toolbarSnapPreview) {
                        ToolbarDock.LEFT -> Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight()
                        ToolbarDock.RIGHT -> Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight()
                        ToolbarDock.TOP -> Modifier.align(Alignment.TopCenter).height(2.dp).fillMaxWidth()
                        ToolbarDock.BOTTOM -> Modifier.align(Alignment.BottomCenter).height(2.dp).fillMaxWidth()
                        ToolbarDock.FREE -> Modifier
                    }
                    Box(preview.zIndex(4f).background(Ink))
                }
                if (settings.showToolbar) ToolDock(tool = tool, toolPaletteLayout = settings.toolPaletteLayout, onTool = choose@ { chosen ->
                    if (chosen == EditorTool.SELECT && tool == EditorTool.SELECT) {
                        finishSelectionTool(deselect = true)
                        return@choose
                    }
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
                        }
                        EditorTool.HIGHLIGHTER -> { activeBrush = null; selectedBrushId = null; brushOpacity = savedBrushOpacity(null, settings.highlighterOpacity, EditorTool.HIGHLIGHTER); brushSize = savedBrushSize(null, settings.highlighterSize, EditorTool.HIGHLIGHTER) }
                        EditorTool.ERASER -> {
                            val remembered = activeBrush?.takeIf(BrushRoleClassifier::isEraser)
                                ?: availableBrushes.firstOrNull { it.id == brushUsage.lastEraserBrushId && !it.hidden &&
                                    (BrushRoleClassifier.isEraser(it) ||
                                        (brushUsage.eraserUsesPaintTip && BrushToolTipReuse.forEraser(it.v5PresetJson).isSuccess)) }
                                ?: brushes.filter(BrushRoleClassifier::isEraser).maxByOrNull(ImportedBrush::lastUsedAt)
                            activeBrush = remembered
                            selectedBrushId = remembered?.id
                            brushOpacity = savedBrushOpacity(remembered, remembered?.defaultOpacity ?: settings.eraserPreset.opacity, EditorTool.ERASER)
                            brushSize = savedBrushSize(remembered, remembered?.defaultSize ?: settings.eraserSize, EditorTool.ERASER)
                        }
                        EditorTool.SMUDGE -> {
                            val remembered = availableBrushes.firstOrNull { brushUsage.smudgeUsesPaintTip &&
                                it.id == brushUsage.lastSmudgeBrushId && !it.hidden &&
                                BrushToolTipReuse.forSmudge(it.v5PresetJson).isSuccess }
                                ?: activeBrush?.takeIf(BrushRoleClassifier::isBlend)
                                ?: availableBrushes.firstOrNull { it.id == brushUsage.lastSmudgeBrushId && BrushRoleClassifier.isBlend(it) }
                                ?: systemBrushCatalog().first(BrushRoleClassifier::isBlend)
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
                        EditorTool.SHAPE -> overlays.shapeInsertOpen = true
                        EditorTool.TEXT -> { insertText = ""; overlays.textInsertOpen = true }
                        EditorTool.NOTE -> { insertText = ""; overlays.stickyInsertOpen = true }
                        EditorTool.LINK -> { insertLinkTitle = ""; insertLinkUrl = ""; overlays.linkInsertOpen = true }
                        EditorTool.IMAGE -> { overlays.imageInsertOpen = true; tool = EditorTool.SELECT }
                        else -> Unit
                    }
                    syncCanvasInput(prepareAssets = activeBrush != null)
                }, onUndo = { canvasView?.undo() }, onRedo = { canvasView?.redo() }, referenceTarget = settings.referenceTarget, onReferenceTarget = { onSettingsChange(settings.copy(referenceTarget = it)) }, correctLineMode = settings.correctLineMode, correctLineAmount = settings.correctLineAmount, onCorrectLineMode = { onSettingsChange(settings.copy(correctLineMode = it)) }, onCorrectLineAmount = { onSettingsChange(settings.copy(correctLineAmount = it.coerceIn(0f, 1f))) }, eraserPreset = settings.eraserPreset, onEraserPreset = { preset -> onSettingsChange(settings.copy(eraserPreset = preset, nonDestructiveEraser = preset.usesMask)) }, rulerEnabled = settings.rulerEnabled, rulerSnapDegrees = settings.rulerSnapDegrees, onToggleRuler = { onSettingsChange(settings.copy(rulerEnabled = !settings.rulerEnabled)) }, onRulerSnap = { onSettingsChange(settings.copy(rulerSnapDegrees = it)) }, onResetRuler = { onSettingsChange(settings.copy(rulerCenterX = .5f, rulerCenterY = .5f, rulerAngleDegrees = 0f)) }, gridSnapEnabled = settings.gridSnapEnabled, gridSpacing = settings.gridSpacing, alignmentGuidesEnabled = settings.alignmentGuidesEnabled, onToggleGridSnap = { onSettingsChange(settings.copy(gridSnapEnabled = !settings.gridSnapEnabled)) }, onGridSpacing = { onSettingsChange(settings.copy(gridSpacing = it)) }, onToggleAlignmentGuides = { onSettingsChange(settings.copy(alignmentGuidesEnabled = !settings.alignmentGuidesEnabled)) }, onResetAlignmentGuides = { onSettingsChange(settings.copy(verticalGuideX = .5f, horizontalGuideY = .5f)) }, onDragStart = {
                    toolbarDragOrigin = Triple(settings.toolbarDock, toolbarX, toolbarY)
                    toolbarDragTravel = 0f
                    if (settings.toolbarDock == ToolbarDock.FREE) {
                        toolbarX = boundedToolbarPosition.xDp; toolbarY = boundedToolbarPosition.yDp
                    }
                    if (settings.toolbarDock != ToolbarDock.FREE) {
                        val origin = FloatingToolbarPlacement.dockOrigin(settings.toolbarDock,
                            toolbarAreaWidth.value, toolbarAreaHeight.value, measuredToolbarWidth, measuredToolbarHeight)
                        toolbarX = origin.xDp; toolbarY = origin.yDp
                        onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE,
                            toolbarOffsetXDp = toolbarX, toolbarOffsetYDp = toolbarY))
                    }
                }, onDrag = { dx, dy ->
                    toolbarDragTravel += kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat() / editorDensity.density
                    val next = FloatingToolbarPlacement.clamp(toolbarX + dx / editorDensity.density, toolbarY + dy / editorDensity.density,
                        toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical, measuredToolbarWidth, measuredToolbarHeight)
                    toolbarX = next.xDp
                    toolbarY = next.yDp
                }, onDragEnd = {
                    val target = if (toolbarDragTravel >= 24f) FloatingToolbarPlacement.snapTarget(
                        FloatingToolbarPosition(toolbarX, toolbarY), toolbarAreaWidth.value, toolbarAreaHeight.value,
                        dockVertical, measuredToolbarWidth, measuredToolbarHeight) else ToolbarDock.FREE
                    onSettingsChange(settings.copy(toolbarDock = target, toolbarOffsetXDp = toolbarX,
                        toolbarOffsetYDp = toolbarY, verticalToolbar = dockVertical))
                    toolbarDragOrigin = null; toolbarDragTravel = 0f
                }, onDragCancel = {
                    toolbarDragOrigin?.let { (dock, x, y) ->
                        toolbarX = x; toolbarY = y
                        onSettingsChange(settings.copy(toolbarDock = dock, toolbarOffsetXDp = x, toolbarOffsetYDp = y))
                    }
                    toolbarDragOrigin = null; toolbarDragTravel = 0f
                }, layoutLocked = panelState.layout.locked, vertical = dockVertical, brushSize = brushSize, brushOpacity = brushOpacity, brushColor = brushColor, brushes = brushes, selectedBrushId = selectedBrushId, onSystemPreset = { presetTool ->
                    exitCanvasNavigation()
                    val presetId = if (presetTool == EditorTool.ERASER) "system-hard-eraser" else "system-g-pen"
                    selectWorkspaceBrush(requireNotNull(SystemBrushCatalog.byId(presetId)))
                }, onBrushPreset = ::selectWorkspaceBrush, onSize = { raw ->
                    changeCurrentBrushSize(raw)
                }, onOpacity = { raw ->
                    changeCurrentBrushOpacity(raw)
                }, onColor = ::changeBrushColor, symmetryMode = settings.symmetryMode, onSymmetryMode = { onSettingsChange(settings.copy(symmetryMode = it)) }, gradientControls = { GradientToolControls(settings, brushColor, backgroundColor, ::changeBrushColor, onSettingsChange, { gradientImportPicker.launch(arrayOf("application/json", GradientPresetLibraryCodec.MIME_TYPE, "*/*")) }, { gradientExportPicker.launch("Inkloom-gradients.json") }) }, liquifyControls = { LiquifyToolControls(settings, onSettingsChange) }, modifier = Modifier.align(dockAlignment).padding(dockPadding).graphicsLayer(translationX = if (settings.toolbarDock == ToolbarDock.FREE) with(editorDensity) { boundedToolbarPosition.xDp.dp.toPx() } else 0f, translationY = if (settings.toolbarDock == ToolbarDock.FREE) with(editorDensity) { boundedToolbarPosition.yDp.dp.toPx() } else 0f).onGloballyPositioned {
                    val width = it.size.width / editorDensity.density
                    val height = it.size.height / editorDensity.density
                    if (kotlin.math.abs(measuredToolbarWidth - width) > .5f) measuredToolbarWidth = width
                    if (kotlin.math.abs(measuredToolbarHeight - height) > .5f) measuredToolbarHeight = height
                })
                if (savedSelectionsOpen) SavedSelectionsPanel(
                    selectedPage.savedSelections, assets, settings.language, selectionActive,
                    onSave = {
                        canvasView?.commitSelectionContour()
                        canvasView?.selectionMaskSurfaceSnapshot()?.takeUnless { it.isEmpty }?.let { mask ->
                            runCatching {
                                val reference = assets.saveMaskTileSurface(mask)
                                try {
                                    val entry = app.inkloom.document.SavedSelection(name = trSelectionName + " ${selectedPage.savedSelections.size + 1}", surface = reference, crop = selectedPage.crop)
                                    val before = selectedPage.savedSelections
                                    val after = before + entry
                                    onSetSavedSelections(document.id, selectedPage.id, after)
                                    canvasView?.let { view ->
                                        val releaseHistoryAsset = assets.retainHistoryAssets(listOf(entry.surface.assetPath))
                                        view.recordExternalEdit(
                                            undo = { onSetSavedSelections(document.id, selectedPage.id, before) },
                                            redo = { onSetSavedSelections(document.id, selectedPage.id, after) },
                                            dispose = releaseHistoryAsset,
                                        )
                                    }
                                } finally { assets.releaseAssetReservation(reference.assetPath) }
                            }.onFailure { Toast.makeText(context, selectionSaveError, Toast.LENGTH_LONG).show() }
                        }
                    },
                    onLoad = { entry, mode ->
                        runCatching {
                            val current = requireNotNull(canvasView?.selectionMaskSurfaceSnapshot())
                            val mask = app.inkloom.document.SavedSelectionGeometry.projected(assets.loadMaskTileSurface(entry.surface).snapshot(), entry.rotationQuarterTurns, entry.crop, selectedPage.crop, current.logicalWidth, current.logicalHeight)
                            check(canvasView?.loadSavedSelection(mask, mode) == true)
                            savedSelectionsOpen = false
                        }.onFailure { Toast.makeText(context, selectionLoadError, Toast.LENGTH_LONG).show() }
                    },
                    onRename = { entry, name ->
                        val before = selectedPage.savedSelections
                        val after = before.map { if (it.id == entry.id) it.copy(name = name) else it }
                        if (before != after) {
                            onSetSavedSelections(document.id, selectedPage.id, after)
                            canvasView?.recordExternalEdit(
                                undo = { onSetSavedSelections(document.id, selectedPage.id, before) },
                                redo = { onSetSavedSelections(document.id, selectedPage.id, after) },
                            )
                        }
                    },
                    onDelete = { entry ->
                        val before = selectedPage.savedSelections
                        val after = before.filterNot { it.id == entry.id }
                        if (before != after) {
                            onSetSavedSelections(document.id, selectedPage.id, after)
                            canvasView?.let { view ->
                                val releaseHistoryAsset = assets.retainHistoryAssets(listOf(entry.surface.assetPath))
                                view.recordExternalEdit(
                                    undo = { onSetSavedSelections(document.id, selectedPage.id, before) },
                                    redo = { onSetSavedSelections(document.id, selectedPage.id, after) },
                                    dispose = releaseHistoryAsset,
                                )
                            }
                        }
                    },
                    onDismiss = { savedSelectionsOpen = false },
                )
                if (searchFocus != null && documentSearchHits.isNotEmpty()) {
                    val current = documentSearchHits.indexOfFirst { it.second == searchFocus }.coerceAtLeast(0)
                    Surface(Modifier.align(Alignment.TopCenter).padding(top = 12.dp), color = Workspace, shape = RoundedCornerShape(12.dp), shadowElevation = 3.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { PageSearch.nextIndex(current, documentSearchHits.size, -1)?.let { index ->
                                val (page, hit) = documentSearchHits[index]; searchFocus = hit; jumpToPage(page.id)
                            } }) { Icon(Icons.Outlined.KeyboardArrowUp, tr("Previous search match", "上一个搜索命中")) }
                            Text("${current + 1} / ${documentSearchHits.size}", fontSize = 12.sp)
                            IconButton(onClick = { PageSearch.nextIndex(current, documentSearchHits.size, 1)?.let { index ->
                                val (page, hit) = documentSearchHits[index]; searchFocus = hit; jumpToPage(page.id)
                            } }) { Icon(Icons.Outlined.KeyboardArrowDown, tr("Next search match", "下一个搜索命中")) }
                            IconButton(onClick = { searchFocus = null }) { Icon(Icons.Outlined.Close, tr("Close search navigation", "关闭搜索导航")) }
                        }
                    }
                }
                if (layerPickMode) Surface(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp), color = Paper, shadowElevation = 3.dp,
                ) {
                    val hovered = selectedPage.layers.firstOrNull { it.id == layerPickPreviewId }
                    Text(
                        hovered?.let { tr("Pick layer: ", "点取图层：") + it.name }
                            ?: tr("Tap visible artwork to choose its layer", "点击可见画面选择所属图层"),
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Ink,
                    )
                }
                if (layerPickCandidates.isNotEmpty()) AlertDialog(
                    onDismissRequest = { layerPickCandidates = emptyList() },
                    title = { Text(tr("Choose layer", "选择图层")) },
                    text = { Column { layerPickCandidates.forEach { id ->
                        selectedPage.layers.firstOrNull { it.id == id }?.let { layer ->
                            TextButton(onClick = { selectLayerTarget(id); layerPickCandidates = emptyList() }) {
                                Text(layer.name + if (LayerHierarchy.effectiveLocked(selectedPage.layers, layer))
                                    tr(" · locked", " · 已锁定") else "")
                            }
                        }
                    } } },
                    confirmButton = { TextButton(onClick = { layerPickCandidates = emptyList() }) { Text(tr("Cancel", "取消")) } },
                )
                if (tool == EditorTool.SELECT && imageCanvasTransform == null && selectionTransformState == null) SelectionStudioPanel(
                    shape = settings.selectionShape, mode = settings.selectionMode, hasSelection = selectionActive,
                    launcherVisible = settings.selectionLauncherVisible,
                    launcherOrder = SelectionLauncherLayout.decode(settings.selectionLauncherOrder),
                    onLauncherVisible = { onSettingsChange(settings.copy(selectionLauncherVisible = it)) },
                    onLauncherOrder = { onSettingsChange(settings.copy(selectionLauncherOrder = SelectionLauncherLayout.encode(it))) },
                    brushSize = settings.selectionBrushSize, onBrushSize = { onSettingsChange(settings.copy(selectionBrushSize = it)) },
                    threshold = settings.selectionThreshold, onThreshold = { onSettingsChange(settings.copy(selectionThreshold = it)) },
                    referenceTarget = settings.referenceTarget, onReferenceTarget = { onSettingsChange(settings.copy(referenceTarget = it)) },
                    maskVisibility = settings.selectionMaskVisibility,
                    onMaskVisibility = { onSettingsChange(settings.copy(selectionMaskVisibility = it)) },
                    onShape = { shape -> canvasView?.beginSelectionInput(shape); onSettingsChange(settings.copy(selectionShape = shape)) },
                    onMode = { mode -> canvasView?.beginSelectionMode(mode); onSettingsChange(settings.copy(selectionMode = mode)) },
                    onClear = { canvasView?.clearSelection() },
                    colorFill = selectionColorFill,
                    onColorFill = { if (canvasView?.setSelectionColorFill(!selectionColorFill) == true) selectionColorFill = !selectionColorFill },
                    quickMask = quickMaskActive,
                    onQuickMask = {
                        quickMaskActive = !quickMaskActive
                        canvasView?.quickMaskActive = quickMaskActive
                        if (quickMaskActive) {
                            canvasView?.beginSelectionInput(SelectionShape.BRUSH)
                            canvasView?.selectionMode = SelectionMode.ADD
                            onSettingsChange(settings.copy(selectionShape = SelectionShape.BRUSH, selectionMode = SelectionMode.ADD))
                        }
                    },
                    colorRangeTarget = brushColor,
                    onColorRange = { tolerance, includeAlpha -> canvasView?.selectColorRange(brushColor, tolerance, includeAlpha) == true },
                    onSaveLoad = { savedSelectionsOpen = true },
                    featherPixels = selectionFeather,
                    interpolation = settings.selectionInterpolation,
                    onInterpolation = { onSettingsChange(settings.copy(selectionInterpolation = it)) },
                    onDone = { finishSelectionTool(deselect = false) },
                    onDeselect = { finishSelectionTool(deselect = true) },
                    onInvert = { selectedElementId = null; canvasView?.invertSelection() },
                    onCut = { canvasView?.requestSelectionLayerTransfer(true) },
                    onCopy = { canvasView?.requestSelectionLayerTransfer(false) },
                    onDelete = { canvasView?.deleteSelection() },
                    onDeleteOutside = { canvasView?.deleteOutsideSelection() },
                    strokeColor = brushColor, strokeOpacity = brushOpacity,
                    onStroke = { width, placement, color, opacity -> canvasView?.strokeSelection(width, placement, color, opacity) == true },
                    onSelectAll = { canvasView?.selectAllEditableStrokes() },
                    onClipboardCopy = { canvasView?.copySelectionToClipboard() },
                    onPaste = { canvasView?.pasteSelectionFromClipboard() },
                    onPasteAtView = { canvasView?.pasteSelectionFromClipboard(atViewCenter = true) },
                    filter = settings.selectionFilter,
                    onFilter = { onSettingsChange(settings.copy(selectionFilter = it)) },
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
                    positionX = settings.selectionStudioXDp,
                    positionY = settings.selectionStudioYDp,
                    onPosition = { x,y -> onSettingsChange(settings.copy(selectionStudioXDp=x,selectionStudioYDp=y)) },
                    modifier = Modifier.fillMaxSize().zIndex(4f),
                )
                selectionTransformState?.let { state ->
                    SelectionTransformToolbar(state, settings.language,
                        interpolation = settings.selectionInterpolation,
                        onInterpolation = { onSettingsChange(settings.copy(selectionInterpolation = it)) },
                        onMode = { canvasView?.setSelectionTransformMode(it) },
                        onDimensions = { x, y, w, h, a -> canvasView?.setSelectionTransformGeometry(x, y, w, h, a) },
                        onResetPivot = { canvasView?.resetSelectionTransformPivot() },
                        onPivotEditing = { canvasView?.setSelectionPivotEditing(it) },
                        onSkewBy = { dx, dy -> canvasView?.skewSelectionBy(dx, dy) },
                        onMeshDensity = { columns, rows -> canvasView?.setSelectionMeshDensity(columns, rows) },
                        onClearPuppetPins = { canvasView?.clearSelectionPuppetPins() },
                        onReference = { canvasView?.setSelectionAlignmentReference(it) },
                        onAlign = { canvasView?.alignSelectionToCanvas(it) },
                        onDistribute = { horizontal, mode -> canvasView?.distributeSelectionLayers(horizontal, mode) },
                        onFlip = { canvasView?.flipSelection(it) },
                        onReset = { canvasView?.resetSelectionTransform() },
                        onSnapping = { snap, magnetic, distance -> canvasView?.configureSelectionSnapping(snap, magnetic, distance) },
                        onNudge = { x,y -> canvasView?.nudgeSelection(x,y) },
                        onFinish = { canvasView?.finishSelectionTransform(apply = it) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 54.dp).zIndex(4f),
                    )
                }
                imageCanvasTransform?.let { session ->
                    Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).zIndex(4f),
                        shape = RoundedCornerShape(14.dp), color = Paper, shadowElevation = 5.dp) {
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
                    targetMode = settings.fillTargetMode,
                    targetColor = settings.fillTargetColor,
                    expansionShape = settings.fillExpansionShape,
                    antiAlias = settings.fillAntiAlias,
                    onSensitivity = { onSettingsChange(settings.copy(fillSensitivity = it)) },
                    onCloseGap = { onSettingsChange(settings.copy(fillCloseGapPixels = it)) },
                    onAreaScaling = { onSettingsChange(settings.copy(fillAreaScalingPixels = it)) },
                    onTargetMode = { onSettingsChange(settings.copy(fillTargetMode = it)) },
                    onTargetColor = { onSettingsChange(settings.copy(fillTargetColor = it)) },
                    onExpansionShape = { onSettingsChange(settings.copy(fillExpansionShape = it)) },
                    onAntiAlias = { onSettingsChange(settings.copy(fillAntiAlias = it)) },
                    onSubTool = { onSettingsChange(settings.copy(fillSubTool = it)) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
                if (settings.showInputDiagnostics) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), shape = RoundedCornerShape(20.dp), color = Paper, border = CardDefaults.outlinedCardBorder(), shadowElevation = 3.dp) { Text(inputDiagnostic, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), color = Ink, fontSize = 11.sp) }
            }
        }, content = { panel ->
            CompositionLocalProvider(LocalEditorPanelHosted provides true) {
                when(panel) {
                    EditorPanel.PAGES -> {            PageRail(
                document, documents, selectedPage.id, assets,
                onClose = { panelState.show(EditorPanel.PAGES, false) },
                pageTemplates = pageTemplates,
                onSelect = ::jumpToPage, onOpenLayers = { pageId -> setLayersPanelOpen(true, pageId) }, onAdd = onAddPage, onInsertNextTo = onInsertPageNextTo, onAppendFiles = { onAppendPageFiles(document.id) }, onDuplicate = onDuplicatePage, onDelete = onDeletePage, onMove = onMovePage, onReorder = onReorderPages, onTransfer = onTransferPage, onTransferPages = onTransferPages,
                onApplyTemplate = onApplyPageTemplate, onApplyTemplates = onApplyPageTemplates, onImportTemplates = onImportPageTemplates, onToggleTemplateFavorite = onTogglePageTemplateFavorite,
                onSetDefaultTemplate = onSetNotebookDefaultTemplate, onSaveAsTemplate = onSavePageAsTemplate, onDeleteTemplate = onDeletePageTemplate, onCreateCustomTemplate = onCreateCustomTemplate,
                onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata,
                onClearPageInk, onSetPageCrop, onRotatePage,
                modifier = Modifier.fillMaxSize().background(Paper).padding(6.dp),
            )
                    }
                    EditorPanel.LAYERS -> {
            LayerPanel(
                page = selectedPage,
                settings = settings,
                onSettingsChange = onSettingsChange,
                activeLayerId = activeLayerId,
                brushColor = brushColor,
                secondaryBrushColor = backgroundColor,
                onClose = { panelState.show(EditorPanel.LAYERS, false); panelState.show(EditorPanel.LAYERS, false) },
                onColor = ::changeBrushColor,
                onSelect = ::selectLayerTarget,
                onSelectContents = ::selectLayerContents,
                hasSelection = selectionActive,
                onAdd = { createLayer(folder = false) },
                onAddFolder = { createLayer(folder = true) },
                onAddAdjustment = ::createAdjustmentLayer,
                onEditAdjustment = { adjustmentDialogId = it },
                onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                onSolo = appearanceEditor::toggleSolo,
                layerPickMode = layerPickMode,
                onLayerPickMode = { layerPickMode = it; layerPickCandidates = emptyList(); layerPickPreviewId = null },
                onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                onOpacity = appearanceEditor::handle,
                onBlendMode = appearanceEditor::setBlendMode,
                onThrough = appearanceEditor::setThrough,
                onPaletteColor = appearanceEditor::setPaletteColor,
                onLayerColor = appearanceEditor::setColorReplacement,
                onToggleAlphaLock = { onToggleLayerAlphaLock(document.id, selectedPage.id, it) },
                onToggleClippedToBelow = appearanceEditor::toggleClipping,
                onToggleReference = { appearanceEditor.toggleReferenceFlag(it, app.inkloom.document.LayerReferenceFlag.REFERENCE) },
                onToggleDraft = { appearanceEditor.toggleReferenceFlag(it, app.inkloom.document.LayerReferenceFlag.DRAFT) },
                onToggleDrawingAssist = { onToggleLayerDrawingAssist(document.id, selectedPage.id, it, settings.toLayerDrawingGuide()) },
                onMaskCommand = ::performLayerMaskCommand,
                onMaskExpression = maskEditor::setExpression,
                onMaskTransfer = ::performLayerMaskTransfer,
                editingMaskId = editingMaskId,
                onRelocate = appearanceEditor::move,
                secondarySelection = secondaryLayers, onSecondarySelection = { secondaryLayers = it },
                onToggleFolderCollapsed = { onToggleLayerFolderCollapsed(document.id, selectedPage.id, it) },

                onDuplicate = { layerId -> canvasView?.requestLayerTree(LayerTreeAction.DUPLICATE, layerId) },
                onMergeDown = { layerId -> canvasView?.requestLayerMerge(layerId) },
                onMerge = { request -> canvasView?.requestLayerMerge(request) },
                onDelete = { canvasView?.requestLayerTree(LayerTreeAction.DELETE, it) },
                onGroupSelected = { primary, selected -> canvasView?.requestLayerTree(LayerTreeAction.GROUP_SELECTED, primary, selected) },
                onUngroup = { canvasView?.requestLayerTree(LayerTreeAction.UNGROUP, it) },
                modifier = Modifier.fillMaxSize(),
            )
                    }
                    EditorPanel.BRUSHES -> {
            BrushPanel(
                brushes = brushes,
                usage = brushUsage,
                onUsage = ::changeBrushUsage,
                registeredGroups = brushGroups,
                selectedBrushId = selectedBrushId,
                requestedGroup = activeBrush?.groupName ?: if (tool == EditorTool.ERASER) "Erasers" else null,
                settings = settings,
                onClose = { setBrushPanelOpen(false) },
                onBrushSelected = ::selectWorkspaceBrush,
                onImportBrushes = onImportBrushes,
                onImportBrushFolder = onImportBrushFolder,
                brushImportFailureCount = brushImportFailureCount,
                onOpenBrushImportFailures = onOpenBrushImportFailures,
            onBrushGridLayout = onBrushGridLayout,
            onBrushCategoryWidth = onBrushCategoryWidth,
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
                modifier = Modifier.fillMaxSize(),
            )
                    }
                    EditorPanel.SUBTOOLS -> WorkspaceSubtoolsPanel(
                        tool = tool, brushes = availableBrushes, selectedBrushId = selectedBrushId,
                        settings = settings, onBrush = ::selectWorkspaceBrush,
                        onSelectionShape = { shape ->
                            canvasView?.beginSelectionInput(shape)
                            onSettingsChange(settings.copy(selectionShape = shape))
                        },
                        onFillSubTool = { onSettingsChange(settings.copy(fillSubTool = it)) },
                        onGradientKind = { onSettingsChange(settings.copy(gradientKind = it)) },
                        onCorrectLineMode = { onSettingsChange(settings.copy(correctLineMode = it)) },
                    )
                    EditorPanel.PROPERTIES -> WorkspaceToolPropertiesPanel(
                        tool = tool, brushSize = brushSize, brushOpacity = brushOpacity,
                        settings = settings, onBrushSize = ::changeCurrentBrushSize,
                        onBrushOpacity = ::changeCurrentBrushOpacity, onSettings = onSettingsChange,
                        shapeKind = insertShape, onShapeKind = { insertShape = it },
                    )
                    else -> BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
                    ColorStudioPanel(
                        language = LocalUiLanguage.current,
                        colorPair = ColorPairControls(brushColor, backgroundColor, modes.editSecondaryColor) { modes.editSecondaryColor = it },
                        color = if (modes.editSecondaryColor) backgroundColor else brushColor,
                        onColorPreview = { preview ->
                            if (modes.editSecondaryColor) {
                                colorPreview.secondary = preview
                                canvasView?.secondaryInkColor = preview
                            } else {
                                colorPreview.foreground = preview
                                canvasView?.inkColor = preview
                            }
                        },
                        onColorCommit = if (modes.editSecondaryColor) ::changeBackgroundColor else ::changeBrushColor,
                        onResizeWidth = null,
                        fixedTab = panel.ordinal - EditorPanel.WHEEL.ordinal,
                        onSwapColors = ::swapDrawingColors,
                        colorHistory = drawingColorHistory,
                        onClearColorHistory = {
                            drawingColorStore.clearHistory(document.id)
                            drawingColorHistory = emptyList()
                        },
                        availableHeight = maxHeight,
                    )
                    }
                }
            }
        })
    }
    }
    val defaultStickyText = tr("New note", "新便签")
    if (overlays.textInsertOpen) AlertDialog(
        onDismissRequest = { overlays.textInsertOpen = false },
        title = { Text(tr("Insert text", "插入文本")) },
        text = { OutlinedTextField(insertText, { insertText = it }, minLines = 3, label = { Text(tr("Text", "文本")) }) },
        confirmButton = { Button(onClick = { if (insertText.isNotBlank()) onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Text(x = .18f, y = .2f, text = insertText.trim()), selectedPage.crop)); overlays.textInsertOpen = false; tool = EditorTool.SELECT }) { Text(tr("Insert", "插入")) } },
        dismissButton = { TextButton(onClick = { overlays.textInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (overlays.stickyInsertOpen) AlertDialog(
        onDismissRequest = { overlays.stickyInsertOpen = false },
        title = { Text(tr("New sticky note", "新建便签")) },
        text = { OutlinedTextField(insertText, { insertText = it }, minLines = 4, label = { Text(tr("Note", "便签")) }) },
        confirmButton = { Button(onClick = { onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Sticky(x = .55f, y = .25f, text = insertText.trim().ifBlank { defaultStickyText }), selectedPage.crop)); overlays.stickyInsertOpen = false; tool = EditorTool.SELECT }) { Text(tr("Insert", "插入")) } },
        dismissButton = { TextButton(onClick = { overlays.stickyInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (overlays.linkInsertOpen) {
        val normalizedLink = LinkTarget.normalize(insertLinkUrl)
        val targetDocument = documents.firstOrNull { it.id == insertLinkDocumentId } ?: document
        val targetPage = targetDocument.pages.firstOrNull { it.id == insertLinkPageId } ?: targetDocument.pages.firstOrNull()
        val linkPageState = rememberLazyListState()
        RevealSelectedItem(linkPageState, targetDocument.id, targetPage?.id, targetDocument.pages.indexOfFirst { it.id == targetPage?.id })
        AlertDialog(
            modifier = Modifier.imePadding(),
            properties = DialogProperties(decorFitsSystemWindows = false),
            onDismissRequest = { overlays.linkInsertOpen = false },
            title = { Text(tr("Insert link", "插入链接")) },
            text = { Column(Modifier.dialogContentWidth(360.dp, 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Read inside the dialog window. Choosing a link type ends title entry, so the keyboard
                // no longer pushes the page list below the fold.
                val linkFocus = androidx.compose.ui.platform.LocalFocusManager.current
                val linkKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                fun chooseLinkKind(internal: Boolean) { linkFocus.clearFocus(); linkKeyboard?.hide(); overlays.insertInternalLink = internal }
                OutlinedTextField(insertLinkTitle, { insertLinkTitle = it }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !overlays.insertInternalLink, onClick = { chooseLinkKind(false) }, label = { Text(tr("Web link", "网页链接")) })
                    FilterChip(selected = overlays.insertInternalLink, onClick = { chooseLinkKind(true) }, label = { Text(tr("Notebook page", "笔记页面")) })
                }
                if (!overlays.insertInternalLink) {
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
                    LazyColumn(Modifier.heightIn(max = 220.dp).semantics { contentDescription = "Link target pages" }, state = linkPageState) {
                        items(targetDocument.pages, key = InkPage::id) { candidate ->
                            ListItem(
                                headlineContent = { Text(candidate.title, maxLines = 1) },
                                supportingContent = { Text(tr("Page ${targetDocument.pages.indexOf(candidate) + 1}", "第 ${targetDocument.pages.indexOf(candidate) + 1} 页"), color = MutedInk) },
                                leadingContent = { if (candidate.bookmarked) Icon(Icons.Default.Bookmark, null) else Icon(Icons.Outlined.Description, null) },
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).semantics { selected = candidate.id == targetPage?.id }.clickable { insertLinkPageId = candidate.id },
                                colors = ListItemDefaults.colors(containerColor = if (candidate.id == targetPage?.id) SageContainer else Color.Transparent),
                            )
                        }
                    }
                }
            } },
            confirmButton = { Button(enabled = if (overlays.insertInternalLink) targetPage != null else normalizedLink != null, onClick = {
                val link = if (overlays.insertInternalLink) {
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
                overlays.linkInsertOpen = false
                tool = EditorTool.SELECT
            }) { Text(tr("Insert", "插入")) } },
            dismissButton = { TextButton(onClick = { overlays.linkInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
        )
    }
    adjustmentDialogId?.let { layerId ->
        val adjustmentLayer = selectedPage.layers.firstOrNull { it.id == layerId && it.role == LayerRole.ADJUSTMENT }
        if (adjustmentLayer != null) AdjustmentLayerDialog(
            layer = adjustmentLayer,
            onDismiss = { appearanceEditor.cancelAdjustment(layerId); adjustmentDialogId = null },
            onPreview = { appearanceEditor.previewAdjustment(layerId, it) },
            onApply = { value ->
                appearanceEditor.previewAdjustment(layerId, value)
                appearanceEditor.commitAdjustment(layerId)
                adjustmentDialogId = null
            },
        )
    }
    if (rasterFilterDialogOpen) RasterFilterDialog(
        drawingColor=brushColor, presets=settings.rasterFilterPresets,onPresets={onSettingsChange(settings.copy(rasterFilterPresets=it))},
        compositePreview=compositeFilterPreview,copyBusy=compositeFilterBusy,
        onPreview={effect,copy ->
            compositeFilterWorker?.cancel();val generation=++compositeFilterGeneration
            compositeFilterBusy=false
            if(!copy) {
                compositeFilterPreview=null
                canvasView?.onRasterFilterPreviewRequested?.invoke(effect)
            } else {
                canvasView?.onRasterFilterFinished?.invoke(false)
                compositeFilterBusy=true
                compositeFilterWorker=appearanceScope.launch {
                    var bitmap:android.graphics.Bitmap?=null
                    try {
                        val view=canvasView ?: return@launch
                        kotlinx.coroutines.withTimeout(15_000){while(view.hasPendingPaintPublicationForMerge() || view.selectionLayerTransferPending) kotlinx.coroutines.delay(8)}
                        val before=currentAppearancePage
                        val selection=view.selectionMaskSurfaceSnapshot()
                        bitmap=withContext(Dispatchers.Default){
                            maskAssets.renderFilteredVisibleCopyPreview(before,effect,selection,cancelled={!isActive})
                        }
                        if(generation==compositeFilterGeneration && rasterFilterDialogOpen){compositeFilterPreview=bitmap;bitmap=null}
                    } catch(_:kotlinx.coroutines.CancellationException){} catch(failure:Throwable){Toast.makeText(context,failure.message?:"Preview failed",Toast.LENGTH_LONG).show()}
                    finally{bitmap?.recycle();if(generation==compositeFilterGeneration)compositeFilterBusy=false}
                }
            }
        },
        onApply={effect,copy ->
            if(!copy){canvasView?.onRasterFilterFinished?.invoke(true);rasterFilterDialogOpen=false}
            else {
                compositeFilterWorker?.cancel();++compositeFilterGeneration;compositeFilterBusy=true
                appearanceScope.launch {
                    var prepared:PreparedPageGeometry?=null;var published=false
                    val view=canvasView
                    try {
                        requireNotNull(view)
                        kotlinx.coroutines.withTimeout(15_000){while(view.hasPendingPaintPublicationForMerge() || view.selectionLayerTransferPending)kotlinx.coroutines.delay(8)}
                        view.selectionLayerTransferPending=true
                        val before=currentAppearancePage;val selection=view.selectionMaskSurfaceSnapshot()
                        prepared=withContext(Dispatchers.Default){maskAssets.prepareFilteredVisibleCopy(before,effect,selection){!isActive}}
                        val transaction=requireNotNull(prepared)
                        check(onApplyPageGeometry(document.id,before.id,transaction.edit,false)){"Page changed during filter"}
                        view.recordExternalEdit(undo={onApplyPageGeometry(document.id,before.id,transaction.edit,true)},
                            redo={onApplyPageGeometry(document.id,before.id,transaction.edit,false)},dispose=transaction::close)
                        activeLayerId=transaction.edit.after.layers.last().id
                        published=true;rasterFilterDialogOpen=false;compositeFilterPreview=null
                    } catch(failure:Throwable){Toast.makeText(context,failure.message?:"Filter failed",Toast.LENGTH_LONG).show()}
                    finally{if(!published)prepared?.close();view?.selectionLayerTransferPending=false;compositeFilterBusy=false}
                }
            }
        },
        onCancel={compositeFilterWorker?.cancel();++compositeFilterGeneration;compositeFilterBusy=false;compositeFilterPreview=null;canvasView?.onRasterFilterFinished?.invoke(false);rasterFilterDialogOpen=false},
    )

    if (overlays.shapeInsertOpen) AlertDialog(
        onDismissRequest = { overlays.shapeInsertOpen = false },
        title = { Text(tr("Draw shape", "绘制形状")) },
        // Shape choices wrap instead of hiding in a sideways strip; the body scrolls at large fonts.
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(tr("Choose a shape, then drag on the page with the pen. Lines and arrows snap to nearby 45° angles; Freeform follows the pen and closes the polygon automatically.", "选择形状后用笔在页面上拖动。直线和箭头会吸附到邻近的 45° 角；自由形状会跟随笔迹并自动闭合。"), color = MutedInk, fontSize = 12.sp); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShapeKind.entries.forEach { kind -> FilterChip(selected = insertShape == kind, onClick = { insertShape = kind }, label = { Text(localizedShapeKind(kind)) }) } } } },
        confirmButton = { Button(onClick = { overlays.shapeInsertOpen = false; tool = EditorTool.SHAPE; canvasView?.tool = EditorTool.SHAPE }) { Text(tr("Draw", "绘制")) } },
        dismissButton = { TextButton(onClick = { overlays.shapeInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (overlays.imageInsertOpen) AlertDialog(
        onDismissRequest = { overlays.imageInsertOpen = false },
        title = { Text(tr("Add page image", "添加页面图片")) },
        text = { Text(tr("Import an existing image or capture a paper page with the device camera. Image text can then be indexed from Search this note.", "导入现有图片，或用设备相机拍摄纸质页面。随后可在“搜索此笔记”中识别图片文字。")) },
        confirmButton = { Button(onClick = { overlays.imageInsertOpen = false; onScanImage() }) { Icon(Icons.Outlined.DocumentScanner, null); Spacer(Modifier.width(6.dp)); Text(tr("Scan page", "扫描页面")) } },
        dismissButton = { Row { TextButton(onClick = { overlays.imageInsertOpen = false }) { Text(tr("Cancel", "取消")) }; TextButton(onClick = { overlays.imageInsertOpen = false; onInsertImage() }) { Text(tr("Import image", "导入图片")) } } },
    )
    if (overlays.bookmarksOpen) AlertDialog(
        onDismissRequest = { overlays.bookmarksOpen = false },
        title = { Text(tr("Bookmarks", "书签")) },
        text = {
            val marked = document.pages.withIndex().filter { it.value.bookmarked }
            if (marked.isEmpty()) Text(tr("No bookmarked pages yet.", "还没有收藏的页面。"), color = MutedInk)
            else LazyColumn(Modifier.dialogContentWidth(320.dp, 520.dp).heightIn(max = 420.dp)) {
                items(marked, key = { it.value.id }) { (index, page) ->
                    ListItem(
                        headlineContent = { Text(page.title, maxLines = 1) },
                        supportingContent = { Text(tr("Page ${index + 1}", "第 ${index + 1} 页"), color = MutedInk) },
                        leadingContent = { Icon(Icons.Default.Bookmark, null) },
                        trailingContent = { IconButton(onClick = { onToggleBookmark(document.id, page.id) }) { Icon(Icons.Outlined.BookmarkRemove, tr("Remove bookmark", "删除书签")) } },
                        modifier = Modifier.clickable { jumpToPage(page.id); overlays.bookmarksOpen = false },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { overlays.bookmarksOpen = false }) { Text(tr("Done", "完成")) } },
    )
    if (overlays.outlineOpen) {
        val outlineState = rememberLazyListState()
        LaunchedEffect(document.id, selectedPage.id) {
            PageOutline.ancestors(document.pages, selectedPage.id).filter(InkPage::outlineCollapsed)
                .forEach { onTogglePageOutlineCollapsed(document.id, it.id) }
        }
        val visibleOutlinePages = PageOutline.visiblePages(document.pages)
        RevealSelectedItem(outlineState, document.id to visibleOutlinePages.map(InkPage::id), selectedPage.id,
            visibleOutlinePages.indexOfFirst { it.id == selectedPage.id })
        AlertDialog(
            onDismissRequest = { overlays.outlineOpen = false },
            title = { Text(tr("Page outline", "页面大纲")) },
            text = {
                LazyColumn(Modifier.dialogContentWidth(360.dp, 560.dp).heightIn(max = 480.dp).semantics { contentDescription = "Page outline list" }, state = outlineState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(visibleOutlinePages, key = { it.id }) { page ->
                        val index = document.pages.indexOf(page)
                        val hasChildren = PageOutline.hasChildren(document.pages, index)
                        ListItem(
                            headlineContent = { Text(page.title, maxLines = 1, fontWeight = if (page.outlineLevel == 0) FontWeight.SemiBold else FontWeight.Normal) },
                            supportingContent = { Text(listOfNotNull(page.customNumber, page.tags.takeIf { it.isNotEmpty() }?.joinToString(" · ")).joinToString(" · ").ifBlank { tr("Page ${index + 1}", "第 ${index + 1} 页") }, color = MutedInk, maxLines = 1) },
                            leadingContent = { if (page.bookmarked) Icon(Icons.Default.Bookmark, tr("Bookmarked", "已收藏"), tint = Ink) else Text((index + 1).toString(), color = MutedInk) },
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
                            modifier = Modifier.padding(start = (page.outlineLevel * 18).dp).semantics { selected = page.id == selectedPage.id }.clickable { jumpToPage(page.id); overlays.outlineOpen = false },
                            colors = ListItemDefaults.colors(containerColor = if (page.id == selectedPage.id) SageContainer else Color.Transparent),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { overlays.outlineOpen = false }) { Text(tr("Done", "完成")) } },
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
    if (overlays.searchOpen) {
        val results = documentSearchHits
        val unindexedPdfPages = document.pages.count { it.background is PageBackground.Pdf && it.pdfTextIndexVersion == 0 }
        AlertDialog(
            onDismissRequest = { overlays.searchOpen = false },
            title = { Text(tr("Search this note", "搜索此笔记")) },
            text = { Column(Modifier.dialogContentWidth(340.dp, 560.dp)) {
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
                        Spacer(Modifier.width(6.dp)); Text(tr("OCR current page", "识别当前页"))
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
                else LazyColumn(Modifier.heightIn(max = 340.dp)) { items(results, key = { (page, hit) -> "${page.id}:${hit.occurrenceIndex}" }) { (page, hit) ->
                    ListItem(
                        headlineContent = { Text(page.title) },
                        supportingContent = { Text(hit.snippet, maxLines = 2) },
                        leadingContent = { Text(when (hit.source) { PageSearchSource.PDF -> "PDF"; PageSearchSource.OBJECT -> "OBJ"; PageSearchSource.PAGE -> "PAGE"; PageSearchSource.HANDWRITING -> "INK"; PageSearchSource.IMAGE_OCR -> "OCR" }, color = MutedInk, fontSize = 9.sp) },
                        trailingContent = if (hit.source == PageSearchSource.HANDWRITING && hit.recognizedBlockId != null) {{
                            TextButton(onClick = { handwritingConversionHit = page to hit }) { Text(tr("To text", "转文字")) }
                        }} else null,
                        modifier = Modifier.clickable { searchFocus = hit; jumpToPage(page.id); overlays.searchOpen = false },
                    )
                } }
            } },
            confirmButton = { TextButton(onClick = { overlays.searchOpen = false }) { Text(tr("Done", "完成")) } },
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
                    overlays.searchOpen = false
                    jumpToPage(page.id)
                }) { Text(tr("Replace ink", "替换笔迹")) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { handwritingConversionHit = null }) { Text(tr("Cancel", "取消")) }
                    TextButton(onClick = {
                        hit.recognizedBlockId?.let { onConvertHandwriting(document.id, page.id, it, false) }
                        handwritingConversionHit = null
                        overlays.searchOpen = false
                        jumpToPage(page.id)
                    }) { Text(tr("Keep ink", "保留笔迹")) }
                }
            },
        )
    }
    if (importError != null) AlertDialog(onDismissRequest = onDismissError, confirmButton = { TextButton(onClick = onDismissError) { Text(tr("OK", "确定")) } }, title = { Text(tr("Import issue", "导入问题")) }, text = { Text(importError) })
}
