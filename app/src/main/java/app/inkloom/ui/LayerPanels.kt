package app.inkloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import app.inkloom.document.LayerDrop
import app.inkloom.document.LayerDropPosition
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.document.*
import app.inkloom.model.*
import app.inkloom.settings.AppSettings
import app.inkloom.settings.LayerRowDensity
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LayerMaskMenuItems(
    layer: InkLayer,
    transferTargets: List<InkLayer> = emptyList(),
    onTransfer: (targetLayerId: String, copy: Boolean) -> Unit = { _, _ -> },
    onExpression: () -> Unit = {},
    onCommand: (LayerMaskCommand) -> Unit,
) {
    if (layer.role == LayerRole.BACKGROUND) return
    val mask = layer.mask
    if (mask == null) {
        StudioMenuItem(
            text = { Text(tr("Mask selection", "蒙版选区")) },
            leadingIcon = { Icon(Icons.Outlined.FilterAlt, null) },
            onClick = { onCommand(LayerMaskCommand.CREATE_MASK_SELECTION) },
        )
        StudioMenuItem(
            text = { Text(tr("Mask outside selection", "蒙版选区外")) },
            leadingIcon = { Icon(Icons.Outlined.FilterAltOff, null) },
            onClick = { onCommand(LayerMaskCommand.CREATE_MASK_OUTSIDE_SELECTION) },
        )
    } else {
        StudioMenuItem(
            text = { Text(tr("Edit layer mask", "编辑图层蒙版")) },
            leadingIcon = { Icon(Icons.Outlined.Brush, null) },
            onClick = { onCommand(LayerMaskCommand.EDIT) },
        )
        StudioMenuItem(text = { Text(tr("Reveal all", "全部显示")) }, onClick = { onCommand(LayerMaskCommand.FILL_WHITE) })
        StudioMenuItem(text = { Text(tr("Hide all", "全部隐藏")) }, onClick = { onCommand(LayerMaskCommand.FILL_BLACK) })
        StudioMenuItem(text = { Text(tr("Hide current selection", "隐藏当前选区")) }, onClick = { onCommand(LayerMaskCommand.HIDE_SELECTION) })
        StudioMenuItem(text = { Text(tr("Reveal current selection", "显示当前选区")) }, onClick = { onCommand(LayerMaskCommand.REVEAL_SELECTION) })
        StudioMenuItem(
            text = { Text(if (mask.enabled) tr("Disable layer mask", "停用图层蒙版") else tr("Enable layer mask", "启用图层蒙版")) },
            onClick = { onCommand(LayerMaskCommand.TOGGLE_ENABLED) },
        )
        StudioMenuItem(
            text = { Text(if (mask.linked) tr("Unlink mask from layer", "解除蒙版与图层链接") else tr("Link mask to layer", "链接蒙版与图层")) },
            leadingIcon = { Icon(if (mask.linked) Icons.Outlined.LinkOff else Icons.Outlined.Link, null) },
            onClick = { onCommand(LayerMaskCommand.TOGGLE_LINKED) },
        )
        StudioMenuItem(
            text = { Text(tr("Show or hide mask overlay", "显示或隐藏蒙版覆盖")) },
            onClick = { onCommand(LayerMaskCommand.TOGGLE_OVERLAY) },
        )
        StudioMenuItem(
            text = { Text(tr("Mask gradients and threshold", "蒙版渐变与阈值")) },
            onClick = onExpression,
        )
        StudioMenuItem(text = { Text(tr("Invert layer mask", "反相图层蒙版")) }, onClick = { onCommand(LayerMaskCommand.INVERT) })
        if (mask.surface != null) StudioMenuItem(
            text = { Text(tr("Load mask as selection", "从蒙版载入选区")) },
            onClick = { onCommand(LayerMaskCommand.LOAD_AS_SELECTION) },
        )
        if (layer.role != LayerRole.ADJUSTMENT) StudioMenuItem(
            text = { Text(tr("Apply layer mask", "应用图层蒙版")) },
            onClick = { onCommand(LayerMaskCommand.APPLY) },
        )
        transferTargets.forEach { target ->
            StudioMenuItem(
                text = { Text("${tr("Copy mask to", "复制蒙版到")} ${target.name}") },
                onClick = { onTransfer(target.id, true) },
            )
            StudioMenuItem(
                text = { Text("${tr("Move mask to", "移动蒙版到")} ${target.name}") },
                onClick = { onTransfer(target.id, false) },
            )
        }
        StudioMenuItem(text = { Text(tr("Delete layer mask", "删除图层蒙版")) }, onClick = { onCommand(LayerMaskCommand.DELETE) })
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
    val expressed = remember(snapshot, mask.showGradients, mask.expressionThreshold) {
        snapshot?.let(mask::expressed)
    }
    if (snapshot == null) {
        Icon(Icons.Outlined.FilterAlt, tr("Layer mask", "图层蒙版"), modifier, tint = if (mask.enabled) Ink else MutedInk)
        return
    }
    Canvas(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(SageContainer)
            .border(1.dp, OutlineSoft, RoundedCornerShape(2.dp)),
    ) {
        drawIntoCanvas { composeCanvas ->
            app.inkloom.engine.v5.android.MaskTileCanvasRenderer.draw(
                requireNotNull(expressed),
                composeCanvas.nativeCanvas,
                android.graphics.RectF(0f, 0f, size.width, size.height),
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = if (mask.enabled) 255 else 96
                },
            )
        }
    }
}

private fun layerPaletteColor(color: LayerPaletteColor): Color = when (color) {
    LayerPaletteColor.NONE -> Color.Transparent
    LayerPaletteColor.GRAY -> Color(0xFF858585)
    LayerPaletteColor.RED -> Color(0xFFB95B5B)
    LayerPaletteColor.ORANGE -> Color(0xFFC17A42)
    LayerPaletteColor.YELLOW -> Color(0xFFB39A45)
    LayerPaletteColor.GREEN -> Color(0xFF5F956C)
    LayerPaletteColor.BLUE -> Color(0xFF627FA6)
    LayerPaletteColor.VIOLET -> Color(0xFF8A6D9E)
}

@Composable
private fun layerPaletteColorName(color: LayerPaletteColor): String = when (color) {
    LayerPaletteColor.NONE -> tr("None", "无")
    LayerPaletteColor.GRAY -> tr("Gray", "灰色")
    LayerPaletteColor.RED -> tr("Red", "红色")
    LayerPaletteColor.ORANGE -> tr("Orange", "橙色")
    LayerPaletteColor.YELLOW -> tr("Yellow", "黄色")
    LayerPaletteColor.GREEN -> tr("Green", "绿色")
    LayerPaletteColor.BLUE -> tr("Blue", "蓝色")
    LayerPaletteColor.VIOLET -> tr("Violet", "紫色")
}

@Composable
internal fun LayerPanel(
    page: InkPage,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    activeLayerId: String,
    brushColor: Int,
    secondaryBrushColor: Int,
    onClose: () -> Unit,
    onColor: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onSelectContents: (String, app.inkloom.engine.v5.surface.MaskComposeMode) -> Unit,
    hasSelection: Boolean,
    onAdd: () -> Unit,
    onAddFolder: () -> Unit,
    onAddAdjustment: (LayerAdjustmentKind) -> Unit,
    onEditAdjustment: (String) -> Unit,
    onAddGenerated: (GeneratedLayerKind) -> Unit = {},
    onEditGenerated: (String) -> Unit = {},
    onRasterize: (String) -> Unit = {},
    onConvertLayer: (String) -> Unit = {},
    onRename: (String, String) -> Unit,
    onVisibility: (String) -> Unit,
    onSolo: (String) -> Unit,
    layerPickMode: Boolean, onLayerPickMode: (Boolean) -> Unit,
    onLock: (String) -> Unit,
    onOpacity: (String, LayerOpacityAction) -> Unit,
    onBlendMode: (String, LayerBlendMode) -> Unit,
    onThrough: (String) -> Unit,
    onPaletteColor: (String, LayerPaletteColor) -> Unit,
    onLayerColor: (String, app.inkloom.document.LayerColorReplacement?) -> Unit,
    onToggleAlphaLock: (String) -> Unit,
    onToggleClippedToBelow: (String) -> Unit,
    onToggleReference: (String) -> Unit,
    onToggleDraft: (String) -> Unit,
    onToggleDrawingAssist: (String) -> Unit,
    onMaskCommand: (String, LayerMaskCommand) -> Unit,
    onMaskExpression: (String, Boolean, Int) -> Unit,
    onMaskTransfer: (String, String, Boolean) -> Unit,
    editingMaskId: String? = null,
    onRelocate: (String, Set<String>, LayerDrop) -> Unit,
    secondarySelection: Set<String>,
    onSecondarySelection: (Set<String>) -> Unit,
    onToggleFolderCollapsed: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onMergeDown: (String) -> Unit,
    onMerge: (RasterLayerMergeRequest) -> Unit,
    onDelete: (String) -> Unit,
    onGroupSelected: (String, Set<String>) -> Unit,
    onUngroup: (String) -> Unit,
    modifier: Modifier = Modifier.width(240.dp).fillMaxHeight().background(Paper).border(1.dp, OutlineSoft).padding(2.dp),
) {

    PaintingLayerPanel(
            page = page,
            settings = settings,
            onSettingsChange = onSettingsChange,
            activeLayerId = activeLayerId,
            brushColor = brushColor,
            secondaryBrushColor = secondaryBrushColor,
            onClose = onClose,
            onSelect = onSelect,
            onSelectContents = onSelectContents,
            hasSelection = hasSelection,
            onAdd = onAdd,
            onAddFolder = onAddFolder,
            onAddAdjustment = onAddAdjustment,
            onEditAdjustment = onEditAdjustment,
            onAddGenerated = onAddGenerated,
            onEditGenerated = onEditGenerated,
            onRasterize = onRasterize,
            onConvertLayer = onConvertLayer,
            onRename = onRename,
            onVisibility = onVisibility,
            onSolo = onSolo,
            layerPickMode = layerPickMode, onLayerPickMode = onLayerPickMode,
            onLock = onLock,
            onOpacity = onOpacity,
            onBlendMode = onBlendMode,
            onThrough = onThrough,
            onPaletteColor = onPaletteColor,
            onLayerColor = onLayerColor,
            onToggleAlphaLock = onToggleAlphaLock,
            onToggleClippedToBelow = onToggleClippedToBelow,
            onToggleReference = onToggleReference,
            onToggleDraft = onToggleDraft,
            onToggleDrawingAssist = onToggleDrawingAssist,
            onMaskCommand = onMaskCommand, onMaskExpression = onMaskExpression,
            onMaskTransfer = onMaskTransfer, editingMaskId = editingMaskId,
            onRelocate = onRelocate, secondarySelection = secondarySelection, onSecondarySelection = onSecondarySelection,
            onToggleFolderCollapsed = onToggleFolderCollapsed,
            onDuplicate = onDuplicate,
            onMergeDown = onMergeDown,
            onMerge = onMerge,
            onDelete = onDelete,
            onGroupSelected = onGroupSelected,
            onUngroup = onUngroup,
            modifier = modifier,
        )
}

@Composable
private fun LayerContentPreview(page: InkPage, layer: InkLayer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val assets = remember(context) { DocumentAssetService(context.applicationContext) }
    // The isolated thumbnail ignores compositing properties. An opacity drag must not decode
    // and redraw the entire layer on the IO pool for every intermediate value.
    val thumbnailLayer = layer.copy(opacity = 1f, visible = true, parentId = null,
        blendMode = LayerBlendMode.NORMAL, clippedToBelow = false)
    val thumbnail by produceState<android.graphics.Bitmap?>(null, page.id, page.crop, page.width, page.height,
        page.backgroundRotationQuarterTurns, thumbnailLayer,
        page.elements.filter { it.layerId == layer.id }) {
        value = withContext(Dispatchers.IO) { runCatching { assets.renderLayerThumbnail(page, layer.id) }.getOrNull() }
    }
    Box(modifier.layerCheckerboard(), contentAlignment = Alignment.Center) {
        thumbnail?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit) }
    }
}

@Composable
private fun PaintingLayerPanel(
    page: InkPage,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    activeLayerId: String,
    brushColor: Int,
    secondaryBrushColor: Int,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onSelectContents: (String, app.inkloom.engine.v5.surface.MaskComposeMode) -> Unit,
    hasSelection: Boolean,
    onAdd: () -> Unit,
    onAddFolder: () -> Unit,
    onAddAdjustment: (LayerAdjustmentKind) -> Unit,
    onEditAdjustment: (String) -> Unit,
    onAddGenerated: (GeneratedLayerKind) -> Unit = {},
    onEditGenerated: (String) -> Unit = {},
    onRasterize: (String) -> Unit = {},
    onConvertLayer: (String) -> Unit = {},
    onRename: (String, String) -> Unit,
    onVisibility: (String) -> Unit,
    onSolo: (String) -> Unit,
    layerPickMode: Boolean, onLayerPickMode: (Boolean) -> Unit,
    onLock: (String) -> Unit,
    onOpacity: (String, LayerOpacityAction) -> Unit,
    onBlendMode: (String, LayerBlendMode) -> Unit,
    onThrough: (String) -> Unit,
    onPaletteColor: (String, LayerPaletteColor) -> Unit,
    onLayerColor: (String, app.inkloom.document.LayerColorReplacement?) -> Unit,
    onToggleAlphaLock: (String) -> Unit,
    onToggleClippedToBelow: (String) -> Unit,
    onToggleReference: (String) -> Unit,
    onToggleDraft: (String) -> Unit,
    onToggleDrawingAssist: (String) -> Unit,
    onMaskCommand: (String, LayerMaskCommand) -> Unit,
    onMaskExpression: (String, Boolean, Int) -> Unit,
    onMaskTransfer: (String, String, Boolean) -> Unit,
    editingMaskId: String? = null,
    onRelocate: (String, Set<String>, LayerDrop) -> Unit,
    secondarySelection: Set<String>,
    onSecondarySelection: (Set<String>) -> Unit,
    onToggleFolderCollapsed: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onMergeDown: (String) -> Unit,
    onMerge: (RasterLayerMergeRequest) -> Unit,
    onDelete: (String) -> Unit,
    onGroupSelected: (String, Set<String>) -> Unit,
    onUngroup: (String) -> Unit,
    modifier: Modifier,
) {
    var renameLayer by remember { mutableStateOf<InkLayer?>(null) }
    var colorLayer by remember { mutableStateOf<InkLayer?>(null) }
    var expressionLayer by remember { mutableStateOf<InkLayer?>(null) }
    var expressionGradients by remember { mutableStateOf(true) }
    var expressionThreshold by remember { mutableFloatStateOf(128f) }
    var colorDark by remember { mutableIntStateOf(brushColor) }
    var colorLight by remember { mutableIntStateOf(secondaryBrushColor) }
    var renameValue by remember { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var mergeMenuOpen by remember { mutableStateOf(false) }
    var layerSearch by rememberSaveable { mutableStateOf("") }
    val selectedLayerIds = secondarySelection
    val primaryListState = rememberLazyListState()
    val secondaryListState = rememberLazyListState()
    LaunchedEffect(page.id, activeLayerId) {
        page.layers.firstOrNull { it.id == activeLayerId }?.let { active ->
            LayerHierarchy.ancestors(page.layers, active).filter(InkLayer::collapsed).forEach { onToggleFolderCollapsed(it.id) }
        }
    }
    val primaryDrag = rememberLayerPanelDrag(page, activeLayerId, selectedLayerIds, primaryListState,
        with(LocalDensity.current) { 48.dp.toPx() },
        { onSecondarySelection(emptySet()); onSelect(it) }, onRelocate, onToggleFolderCollapsed)
    val secondaryDrag = rememberLayerPanelDrag(page, activeLayerId, selectedLayerIds, secondaryListState,
        with(LocalDensity.current) { 48.dp.toPx() },
        { onSecondarySelection(emptySet()); onSelect(it) }, onRelocate, onToggleFolderCollapsed)
    fun moveInto(layer: InkLayer, folder: InkLayer?) {
        val destination = folder?.let { LayerDrop(it.id, LayerDropPosition.INSIDE) }
            ?: layer.parentId?.let { LayerDrop(it, LayerDropPosition.ABOVE) } ?: return
        onRelocate(layer.id, setOf(layer.id), destination)
    }
    fun siblingDrop(layer: InkLayer, delta: Int): LayerDrop? {
        val siblings = page.layers.filter { it.parentId == layer.parentId }
        val target = siblings.getOrNull(siblings.indexOfFirst { it.id == layer.id } + delta) ?: return null
        if (target.role == LayerRole.BACKGROUND) return null
        return LayerDrop(target.id, if (delta > 0) LayerDropPosition.ABOVE else LayerDropPosition.BELOW)
    }
    val layerListDescription = tr("Layer list", "图层列表")
    val secondaryLayerListDescription = tr("Second layer list", "第二图层列表")
    val resizeLayerListsDescription = tr("Resize layer lists", "调整双图层列表高度")
    var displayMenuOpen by remember { mutableStateOf(false) }
    Surface(modifier, shape = RoundedCornerShape(0.dp), color = Paper, contentColor = Ink) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box {
                    IconButton(onClick = { displayMenuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Tune, tr("Layer list display", "图层列表显示"), tint = Ink)
                    }
                    DropdownMenu(displayMenuOpen, { displayMenuOpen = false }) {
                        StudioMenuItem(
                            text = { Text(tr("Two layer lists", "双图层列表")) },
                            leadingIcon = { StudioCheckbox(settings.layerPanelDualList, null) },
                            onClick = { onSettingsChange(settings.copy(layerPanelDualList = !settings.layerPanelDualList)) },
                        )
                        StudioMenuItem(
                            text = { Text(tr("Show thumbnails", "显示缩略图")) },
                            leadingIcon = { StudioCheckbox(settings.layerShowThumbnails, null) },
                            onClick = { onSettingsChange(settings.copy(layerShowThumbnails = !settings.layerShowThumbnails)) },
                        )
                        StudioMenuItem(
                            text = { Text(tr("Show opacity and blend mode", "显示透明度和混合模式")) },
                            leadingIcon = { StudioCheckbox(settings.layerShowDetails, null) },
                            onClick = { onSettingsChange(settings.copy(layerShowDetails = !settings.layerShowDetails)) },
                        )
                        StudioMenuItem(text = { Text(tr("Show properties bar", "显示属性栏")) },
                            leadingIcon = { StudioCheckbox(settings.layerShowProperties, null) },
                            onClick = { onSettingsChange(settings.copy(layerShowProperties = !settings.layerShowProperties)) })
                        StudioMenuItem(text = { Text(tr("Show command bar", "显示命令栏")) },
                            leadingIcon = { StudioCheckbox(settings.layerShowCommands, null) },
                            onClick = { onSettingsChange(settings.copy(layerShowCommands = !settings.layerShowCommands)) })
                        StudioMenuItem(text = { Text(tr("Commands at bottom", "命令栏置于底部")) },
                            leadingIcon = { StudioCheckbox(settings.layerCommandsAtBottom, null) },
                            onClick = { onSettingsChange(settings.copy(layerCommandsAtBottom = !settings.layerCommandsAtBottom)) })
                        HorizontalDivider()
                        LayerRowDensity.entries.forEach { density ->
                            StudioMenuItem(
                                text = { Text(when (density) {
                                    LayerRowDensity.COMPACT -> tr("Compact rows", "紧凑行")
                                    LayerRowDensity.COMFORTABLE -> tr("Comfortable rows", "标准行")
                                    LayerRowDensity.LARGE -> tr("Large rows", "大号行")
                                }) },
                                leadingIcon = { StudioRadioButton(settings.layerRowDensity == density, null) },
                                onClick = { onSettingsChange(settings.copy(layerRowDensity = density)); displayMenuOpen = false },
                            )
                        }
                    }
                }
                IconButton(onClick = { onLayerPickMode(!layerPickMode) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.TouchApp, tr("Pick layer from canvas", "从画布点取图层"), tint = if (layerPickMode) Ink else MutedInk)
                }
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) layerSearch = "" }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Search, tr("Search layers", "搜索图层"), tint = Ink) }
                Box {
                    IconButton(onClick = { mergeMenuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.MoreHoriz, tr("Layer merge commands", "图层合并命令"), tint = Ink)
                    }
                    DropdownMenu(mergeMenuOpen, { mergeMenuOpen = false }) {
                        fun request(operation: RasterLayerMergeOperation) {
                            mergeMenuOpen = false
                            onMerge(RasterLayerMergeRequest(operation, activeLayerId, selectedLayerIds + activeLayerId))
                        }
                        StudioMenuItem(text = { Text(tr("Transfer to lower layer", "转移到下一图层")) }, onClick = { request(RasterLayerMergeOperation.TRANSFER_TO_LOWER) })
                        StudioMenuItem(text = { Text(tr("Merge selected layers", "合并所选图层")) }, enabled = selectedLayerIds.isNotEmpty(), onClick = { request(RasterLayerMergeOperation.MERGE_SELECTED) })
                        StudioMenuItem(text = { Text(tr("Merge visible layers", "合并可见图层")) }, onClick = { request(RasterLayerMergeOperation.MERGE_VISIBLE) })
                        StudioMenuItem(text = { Text(tr("Merge visible to new layer", "合并可见图层副本")) }, onClick = { request(RasterLayerMergeOperation.MERGE_VISIBLE_COPY) })
                        StudioMenuItem(text = { Text(tr("Flatten image", "平面化图像")) }, onClick = { request(RasterLayerMergeOperation.FLATTEN) })
                    }
                }
            }
            val propertyLayer = page.layers.firstOrNull { it.id == activeLayerId }
            if (settings.layerShowProperties) LayerPropertiesBar(propertyLayer,
                editable = propertyLayer != null && propertyLayer.role != LayerRole.BACKGROUND &&
                    !LayerHierarchy.effectiveLocked(page.layers, propertyLayer),
                language = LocalUiLanguage.current,
                onOpacity = { onOpacity(activeLayerId, it) },
                onMode = { onBlendMode(activeLayerId, it) }, onThrough = { onThrough(activeLayerId) })
            val commands: @Composable () -> Unit = { LayerQuickActions(propertyLayer, page.layers, LocalUiLanguage.current, onAdd, onAddFolder, onAddAdjustment,
                onLock = { onLock(activeLayerId) }, onAlphaLock = { onToggleAlphaLock(activeLayerId) },
                onClip = { onToggleClippedToBelow(activeLayerId) }, onReference = { onToggleReference(activeLayerId) },
                onDraft = { onToggleDraft(activeLayerId) }, onMask = { onMaskCommand(activeLayerId, it) },
                onDuplicate = { onDuplicate(activeLayerId) }, onMerge = { onMergeDown(activeLayerId) },
                onDelete = { onDelete(activeLayerId) }, onGenerated = onAddGenerated) }
            if (settings.layerShowCommands && !settings.layerCommandsAtBottom) commands()
            HorizontalDivider(color = OutlineSoft)
            if (searchOpen) StudioOutlinedTextField(
                value = layerSearch,
                onValueChange = { layerSearch = it },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp, vertical = 3.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                placeholder = { Text(tr("Search layers", "搜索图层"), fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(17.dp)) },
                trailingIcon = { if (layerSearch.isNotEmpty()) IconButton({ layerSearch = "" }) { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) } },
            )
            if (selectedLayerIds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(38.dp).background(Paper).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedLayerIds.size} ${tr("selected", "项已选")}", Modifier.weight(1f), fontSize = 10.sp)
                    IconButton({ selectedLayerIds.forEach(onVisibility) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Visibility, tr("Toggle selected layer visibility", "切换所选图层可见性"), Modifier.size(17.dp)) }
                    IconButton({ selectedLayerIds.forEach(onLock) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Lock, tr("Toggle selected layer lock", "切换所选图层锁定"), Modifier.size(17.dp)) }
                    IconButton({ onGroupSelected(activeLayerId, selectedLayerIds + activeLayerId); onSecondarySelection(emptySet()) }, Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.CreateNewFolder, tr("Group selected layers", "将所选图层编组"), Modifier.size(17.dp))
                    }
                    IconButton({
                        onMerge(RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_SELECTED, activeLayerId, selectedLayerIds + activeLayerId))
                        onSecondarySelection(emptySet())
                    }, Modifier.size(32.dp)) { Icon(Icons.Outlined.MergeType, tr("Merge selected layers", "合并所选图层"), Modifier.size(17.dp)) }
                    IconButton({ selectedLayerIds.mapNotNull { id -> page.layers.firstOrNull { it.id == id } }.filter { it.role in setOf(LayerRole.CUSTOM, LayerRole.FOLDER, LayerRole.ADJUSTMENT) }.forEach { onDelete(it.id) }; onSecondarySelection(emptySet()) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, tr("Delete selected custom layers", "删除所选自定义图层"), Modifier.size(17.dp)) }
                    IconButton({ onSecondarySelection(emptySet()) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, tr("Exit layer selection", "退出图层多选"), Modifier.size(17.dp)) }
                }
            }
            val rowHeight = when (settings.layerRowDensity) {
                LayerRowDensity.COMPACT -> 46.dp
                LayerRowDensity.COMFORTABLE -> 62.dp
                LayerRowDensity.LARGE -> 76.dp
            }
            val thumbnailSize = when (settings.layerRowDensity) {
                LayerRowDensity.COMPACT -> 30.dp
                LayerRowDensity.COMFORTABLE -> 42.dp
                LayerRowDensity.LARGE -> 54.dp
            }
            val renderLayerList: @Composable (Modifier, androidx.compose.foundation.lazy.LazyListState, LayerPanelDrag, String) -> Unit = { listModifier, listState, drag, description ->
            val filteredLayers = LayerHierarchy.panelOrder(page.layers.map { if (it.id in drag.expanded) it.copy(collapsed = false) else it })
                .filter { layerSearch.isBlank() || it.name.contains(layerSearch.trim(), ignoreCase = true) }
            RevealSelectedItem(listState, page.id to filteredLayers.map(InkLayer::id), activeLayerId,
                filteredLayers.indexOfFirst { it.id == activeLayerId }, enabled = drag.origin == null)
            LazyColumn(listModifier.onGloballyPositioned { drag.viewport = it.boundsInRoot() }.layerPanelDrag(drag).semantics { contentDescription = description }, state = listState, contentPadding = PaddingValues(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                items(filteredLayers, key = { it.id }) { layer ->
                    var menu by remember { mutableStateOf(false) }
                    var maskMenu by remember { mutableStateOf(false) }
                    var contentsMenu by remember { mutableStateOf(false) }
                    var paletteColorMenu by remember { mutableStateOf(false) }
                    DisposableEffect(layer.id) { onDispose { drag.rows.remove(layer.id) } }
                    val folderTargets = LayerHierarchy.availableFolders(page.layers, layer.id)
                    val selected = layer.id == activeLayerId || layer.id in selectedLayerIds
                    val depth = LayerHierarchy.depth(page.layers, layer)
                    val swipeActions = buildList<Pair<String, () -> Unit>> {
                        add((if (layer.locked) tr("Unlock", "解锁") else tr("Lock", "锁定")) to { onLock(layer.id) })
                        if (layer.role != LayerRole.BACKGROUND) add(tr("Duplicate", "复制") to { onDuplicate(layer.id) })
                        if (layer.role in setOf(LayerRole.CUSTOM, LayerRole.FOLDER, LayerRole.ADJUSTMENT)) add(tr("Delete", "删除") to { onDelete(layer.id) })
                    }
                    val layerRowDescription = tr("Select layer", "选择图层")
                    val rowInk = if (layer.id == activeLayerId) Color.White else Ink
                    LayerRowActions(swipeActions, destructiveLabel = tr("Delete", "删除"), onSelectSecondary = {
                        if (layer.id != activeLayerId && layer.role != LayerRole.BACKGROUND) {
                            onSecondarySelection(app.inkloom.document.LayerSecondarySelection.toggle(activeLayerId, selectedLayerIds, layer.id))
                        }
                    }) {
                    Row(
                        Modifier.fillMaxWidth().semantics { contentDescription = "${layerRowDescription} ${layer.name}"; this.selected = layer.id == activeLayerId }.onGloballyPositioned { drag.rows[layer.id] = it.boundsInRoot() }.height(rowHeight).background(if (drag.drop == LayerDrop(layer.id, LayerDropPosition.INSIDE)) SageContainer else if (layer.id == activeLayerId) Sage else if (selected) SageContainer else Paper).drawWithContent {
                            drawContent()
                            drawLine(OutlineSoft, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                            val destination = drag.drop
                            if (destination?.targetId == layer.id && destination.position != LayerDropPosition.INSIDE) {
                                val y = if (destination.position == LayerDropPosition.ABOVE) 1.dp.toPx() else size.height - 1.dp.toPx()
                                drawLine(Ink, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                            }
                        }.combinedClickable(
                            onClick = {
                                onSecondarySelection(emptySet())
                                onSelect(layer.id)
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(layerPaletteColor(layer.paletteColor)))
                        val visibilityDescription = tr("Toggle visibility; long press to solo", "点击切换可见性，长按独显")
                        Box(Modifier.width(30.dp).fillMaxHeight().background(Paper).combinedClickable(
                            onClick = { onVisibility(layer.id) }, onLongClick = { onSolo(layer.id) },
                            onLongClickLabel = tr("Solo layer or restore visibility", "独显图层或恢复可见性"),
                        ).semantics { contentDescription = visibilityDescription }, contentAlignment = Alignment.Center) {
                            Icon(if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                null, Modifier.size(18.dp), tint = MutedInk)
                        }
                        val secondaryDescription = "${tr("Secondary selection", "图层多选")} ${layer.name}"
                        IconButton(onClick = {
                            if (layer.id != activeLayerId && layer.role != LayerRole.BACKGROUND)
                                onSecondarySelection(app.inkloom.document.LayerSecondarySelection.toggle(activeLayerId, selectedLayerIds, layer.id))
                        }, modifier = Modifier.width(28.dp).fillMaxHeight().background(Paper).semantics {
                            contentDescription = secondaryDescription
                        }, enabled = layer.id == activeLayerId || layer.role != LayerRole.BACKGROUND) {
                            Icon(if (layer.id == activeLayerId || layer.id in selectedLayerIds) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                null, Modifier.size(18.dp), tint = if (selected) Ink else MutedInk)
                        }
                        Spacer(Modifier.width((4 + depth * 16).dp))
                        if (settings.layerShowThumbnails) {
                            val thumbnailDescription = when (layer.role) {
                                LayerRole.FOLDER -> "${tr("Toggle folder", "折叠或展开文件夹")} ${layer.name}"
                                LayerRole.ADJUSTMENT -> "${tr("Edit adjustment", "编辑调整参数")} ${layer.name}"
                                else -> if (layer.generator != null) "${tr("Edit layer settings", "编辑图层设置")} ${layer.name}"
                                    else "${tr("Layer content", "图层内容")} ${layer.name}"
                            }
                            Surface(Modifier.size(thumbnailSize).semantics { contentDescription = thumbnailDescription }.clickable {
                                when (layer.role) {
                                    LayerRole.FOLDER -> onToggleFolderCollapsed(layer.id)
                                    LayerRole.ADJUSTMENT -> if (!LayerHierarchy.effectiveLocked(page.layers, layer)) { onSecondarySelection(emptySet()); onSelect(layer.id); onEditAdjustment(layer.id) }
                                    else -> { onSecondarySelection(emptySet()); onSelect(layer.id)
                                        if (layer.generator != null && !LayerHierarchy.effectiveLocked(page.layers, layer)) onEditGenerated(layer.id) }
                                }
                            }, shape = RoundedCornerShape(1.dp), color = Paper, border = androidx.compose.foundation.BorderStroke(
                                if (layer.id == activeLayerId && editingMaskId != layer.id) 2.dp else 1.dp, if (selected) Ink else MutedInk)) {
                                if (layer.role == LayerRole.ADJUSTMENT) Box(Modifier.fillMaxSize().background(Workspace), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Tune, null, Modifier.size(24.dp), tint = Ink)
                                }
                                else if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) LayerContentPreview(page, layer, Modifier.fillMaxSize())
                                else if (layer.role == LayerRole.BACKGROUND) Box(Modifier.fillMaxSize().background(Color(
                                    (page.background as? PageBackground.Paper)?.colorArgb?.toInt() ?: android.graphics.Color.WHITE)))
                                else Box(contentAlignment = Alignment.Center) {
                                    Icon(if (layer.collapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, Modifier.size(24.dp), tint = Ink)
                                }
                            }
                        layer.mask?.let { mask ->
                            val maskDescription = "${tr("Edit layer mask", "编辑图层蒙版")} ${layer.name}"
                            Box(Modifier.size(40.dp).semantics { contentDescription = maskDescription }
                                .clickable { onMaskCommand(layer.id, LayerMaskCommand.EDIT) }, contentAlignment = Alignment.Center) {
                                LayerMaskThumbnailView(mask, Modifier.size(28.dp).border(
                                    if (editingMaskId == layer.id) 2.dp else 1.dp,
                                    if (editingMaskId == layer.id) Ink else MutedInk.copy(alpha = .45f), RoundedCornerShape(2.dp)))
                            }
                        }
                        }
                        Spacer(Modifier.width(7.dp))
                        val reorderDescription = "${tr("Reorder layer", "拖动排序图层")} ${layer.name}"
                        Column(Modifier.weight(1f).semantics { contentDescription = reorderDescription }) {
                            if (settings.layerShowDetails && layer.role != LayerRole.BACKGROUND) Text(
                                "${(layer.opacity * 100).roundToInt()}%  ${LayerBlendModeDisplayName.of(layer, LocalUiLanguage.current)}",
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontSize = 10.sp, color = MutedInk,
                            )
                            Text(if (drag.origin == layer.id) "↕ ${layer.name}" else layer.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 13.sp, color = rowInk)

                        }
                        if (layer.locked) Icon(Icons.Outlined.Lock, tr("Locked", "已锁定"), Modifier.size(15.dp), tint = MutedInk)
                        if (layer.reference) Icon(Icons.Outlined.Lightbulb, tr("Reference layer", "参考图层"), Modifier.size(15.dp), tint = MutedInk)
                        if (layer.draft) Icon(Icons.Outlined.EditNote, tr("Draft layer", "草稿图层"), Modifier.size(15.dp), tint = MutedInk)
                        if (layer.drawingAssist) Icon(Icons.Outlined.AutoFixHigh, tr("Drawing Assist", "绘图辅助"), Modifier.size(15.dp), tint = MutedInk)
                        val layerActionsDescription = "${tr("Layer actions", "图层操作")} ${layer.name}"
                        Box(Modifier.semantics { contentDescription = layerActionsDescription }) {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, tr("Layer actions", "图层操作"), tint = rowInk) }
                            DropdownMenu(maskMenu, { maskMenu = false }) {
                                LayerMaskMenuItems(
                                    layer = layer,
                                    transferTargets = page.layers.filter { candidate ->
                                        candidate.id != layer.id && candidate.role != LayerRole.BACKGROUND && candidate.mask == null &&
                                            !LayerHierarchy.effectiveLocked(page.layers, candidate)
                                    },
                                    onTransfer = { targetId, copy ->
                                        maskMenu = false
                                        onMaskTransfer(layer.id, targetId, copy)
                                    },
                                    onExpression = {
                                        maskMenu = false
                                        expressionLayer = layer
                                        expressionGradients = layer.mask?.showGradients ?: true
                                        expressionThreshold = (layer.mask?.expressionThreshold ?: 128).toFloat()
                                    },
                                ) { command -> maskMenu = false; onMaskCommand(layer.id, command) }
                            }
                            DropdownMenu(paletteColorMenu, { paletteColorMenu = false }) {
                                LayerPaletteColor.entries.forEach { color ->
                                    StudioMenuItem(
                                        text = { Text(layerPaletteColorName(color)) },
                                        leadingIcon = {
                                            Box(Modifier.size(16.dp).background(layerPaletteColor(color), CircleShape)
                                                .border(1.dp, OutlineSoft, CircleShape))
                                        },
                                        trailingIcon = { if (layer.paletteColor == color) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) },
                                        onClick = { paletteColorMenu = false; onPaletteColor(layer.id, color) },
                                    )
                                }
                            }
                            DropdownMenu(contentsMenu, { contentsMenu = false }) {
                                listOf(
                                    app.inkloom.engine.v5.surface.MaskComposeMode.REPLACE to tr("Create selection", "创建选区"),
                                    app.inkloom.engine.v5.surface.MaskComposeMode.ADD to tr("Add to selection", "添加到选区"),
                                    app.inkloom.engine.v5.surface.MaskComposeMode.SUBTRACT to tr("Subtract from selection", "从选区减去"),
                                    app.inkloom.engine.v5.surface.MaskComposeMode.INTERSECT to tr("Intersect selection", "与选区相交"),
                                ).forEach { (mode, label) ->
                                    StudioMenuItem(text = { Text(label) },
                                        enabled = mode == app.inkloom.engine.v5.surface.MaskComposeMode.REPLACE || hasSelection,
                                        onClick = { contentsMenu = false; onSelectContents(layer.id, mode) })
                                }
                            }
                            DropdownMenu(menu, { menu = false }) {
                                StudioMenuItem(text = { Text(if (layer.locked) tr("Unlock", "解锁") else tr("Lock", "锁定")) }, onClick = { menu = false; onLock(layer.id) })
                                if (layer.role == LayerRole.ADJUSTMENT) StudioMenuItem(text = { Text(tr("Edit adjustment", "编辑调整参数")) }, enabled = !LayerHierarchy.effectiveLocked(page.layers, layer), onClick = { menu = false; onEditAdjustment(layer.id) })
                                if (layer.generator != null) {
                                    StudioMenuItem(text = { Text(tr("Edit layer settings", "编辑图层设置")) }, enabled = !LayerHierarchy.effectiveLocked(page.layers, layer), onClick = { menu = false; onEditGenerated(layer.id) })
                                    StudioMenuItem(text = { Text(tr("Rasterize", "栅格化")) }, enabled = !LayerHierarchy.effectiveLocked(page.layers, layer), onClick = { menu = false; onRasterize(layer.id) })
                                }
                                if (layer.isPaintSurfaceLayer()) StudioMenuItem(text = { Text(if (layer.alphaLock) tr("Unlock alpha", "解锁透明度") else tr("Lock alpha", "锁定透明度")) }, onClick = { menu = false; onToggleAlphaLock(layer.id) })
                                if (layer.isPaintSurfaceLayer() && layer.generator == null) StudioMenuItem(text = { Text(tr("Convert layer…", "转换图层…")) },
                                    enabled = !LayerHierarchy.effectiveLocked(page.layers, layer), onClick = { menu = false; onConvertLayer(layer.id) })
                                if (layer.canClipToBelow()) StudioMenuItem(text = { Text(if (layer.clippedToBelow) tr("Stop clipping", "取消剪贴") else tr("Clipping mask", "剪贴蒙版")) }, onClick = { menu = false; onToggleClippedToBelow(layer.id) })
                                if (layer.role != LayerRole.BACKGROUND) StudioMenuItem(text = { Text(if (layer.reference) tr("Unset reference layer", "取消参考图层") else tr("Set as reference layer", "设为参考图层")) }, onClick = { menu = false; onToggleReference(layer.id) })
                                if (layer.role != LayerRole.BACKGROUND) StudioMenuItem(text = { Text(if (layer.draft) tr("Unset draft layer", "取消草稿图层") else tr("Set as draft layer", "设为草稿图层")) }, onClick = { menu = false; onToggleDraft(layer.id) })
                                if (layer.isPaintSurfaceLayer()) StudioMenuItem(text = { Text(if (layer.drawingAssist) tr("Turn off Drawing Assist", "关闭绘图辅助") else tr("Turn on Drawing Assist", "开启绘图辅助")) }, onClick = { menu = false; onToggleDrawingAssist(layer.id) })
                                if (layer.isPaintSurfaceLayer() || layer.role == LayerRole.FOLDER) StudioMenuItem(text = { Text(tr("Select contents", "选取内容")) }, trailingIcon = { Icon(Icons.Outlined.ChevronRight, null) }, onClick = { menu = false; contentsMenu = true })
                                if (layer.role != LayerRole.BACKGROUND) StudioMenuItem(text = { Text(tr("Mask", "蒙版")) }, trailingIcon = { Icon(Icons.Outlined.ChevronRight, null) }, onClick = { menu = false; maskMenu = true })
                                StudioMenuItem(text = { Text(tr("Palette color", "图层颜色标签")) }, trailingIcon = { Icon(Icons.Outlined.ChevronRight, null) }, onClick = { menu = false; paletteColorMenu = true })
                                if (layer.isPaintSurfaceLayer()) StudioMenuItem(
                                    text = { Text(tr("Layer color replacement", "图层着色")) },
                                    onClick = {
                                        menu = false
                                        colorDark = layer.colorReplacement?.darkArgb?.toInt() ?: brushColor
                                        colorLight = layer.colorReplacement?.lightArgb?.toInt() ?: secondaryBrushColor
                                        colorLayer = layer
                                    },
                                )
                                StudioMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { menu = false; renameLayer = layer; renameValue = layer.name })
                                if (layer.role == LayerRole.FOLDER) StudioMenuItem(text = { Text(if (layer.collapsed) tr("Expand folder", "展开文件夹") else tr("Collapse folder", "折叠文件夹")) }, onClick = { menu = false; onToggleFolderCollapsed(layer.id) })
                                if (layer.role != LayerRole.BACKGROUND) StudioMenuItem(text = { Text(tr("Group layer", "编组图层")) }, onClick = { menu = false; onGroupSelected(layer.id, setOf(layer.id)) })
                                if (layer.role == LayerRole.FOLDER) StudioMenuItem(text = { Text(tr("Ungroup layer folder", "解除图层分组")) }, onClick = { menu = false; onUngroup(layer.id) })
                                if (layer.parentId != null) StudioMenuItem(text = { Text(tr("Move out of folder", "移出文件夹")) }, onClick = { menu = false; moveInto(layer, null) })
                                folderTargets.forEach { folder -> StudioMenuItem(text = { Text("${tr("Move into", "移入")} ${folder.name}") }, onClick = { menu = false; moveInto(layer, folder) }) }
                                StudioMenuItem(text = { Text(tr("Move up", "上移")) }, enabled = siblingDrop(layer, 1) != null, onClick = { menu = false; siblingDrop(layer, 1)?.let { onRelocate(layer.id, setOf(layer.id), it) } })
                                StudioMenuItem(text = { Text(tr("Move down", "下移")) }, enabled = siblingDrop(layer, -1) != null, onClick = { menu = false; siblingDrop(layer, -1)?.let { onRelocate(layer.id, setOf(layer.id), it) } })
                                if (layer.role != LayerRole.BACKGROUND) StudioMenuItem(text = { Text(tr("Duplicate", "复制")) }, onClick = { menu = false; onDuplicate(layer.id) })
                                if (layer.isPaintSurfaceLayer()) StudioMenuItem(text = { Text(tr("Merge down", "向下合并")) }, onClick = { menu = false; onMergeDown(layer.id) })
                                if (layer.role in setOf(LayerRole.CUSTOM, LayerRole.FOLDER, LayerRole.ADJUSTMENT)) StudioMenuItem(text = { Text(tr("Delete", "删除")) }, onClick = { menu = false; onDelete(layer.id) })
                            }
                        }
                    }
                    }
                }
            }
            }
            if (settings.layerPanelDualList) {
                var splitFraction by remember(settings.layerPanelSplitFraction) { mutableFloatStateOf(settings.layerPanelSplitFraction) }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val availableHeight = (maxHeight - 10.dp).coerceAtLeast(80.dp)
                    val containerHeightPx = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)
                    Column(Modifier.fillMaxSize()) {
                        renderLayerList(Modifier.fillMaxWidth().height(availableHeight * splitFraction), primaryListState, primaryDrag, layerListDescription)
                        Box(Modifier.fillMaxWidth().height(10.dp).semantics { contentDescription = resizeLayerListsDescription }
                            .pointerInput(containerHeightPx) {
                                detectDragGestures(
                                    onDragEnd = { onSettingsChange(settings.copy(layerPanelSplitFraction = splitFraction)) },
                                    onDragCancel = { splitFraction = settings.layerPanelSplitFraction },
                                ) { change, amount ->
                                    change.consume()
                                    splitFraction = (splitFraction + amount.y / containerHeightPx).coerceIn(.2f, .8f)
                                }
                            }.drawWithContent {
                                drawLine(OutlineSoft, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
                            })
                        renderLayerList(Modifier.fillMaxWidth().weight(1f), secondaryListState, secondaryDrag, secondaryLayerListDescription)
                    }
                }
            } else {
                renderLayerList(Modifier.weight(1f), primaryListState, primaryDrag, layerListDescription)
            }
            if (settings.layerShowCommands && settings.layerCommandsAtBottom) {
                HorizontalDivider(color = OutlineSoft)
                commands()
            }
        }
    }
    renameLayer?.let { layer -> StudioDialog(
        onDismissRequest = { renameLayer = null },
        title = { Text(tr("Rename layer", "重命名图层")) },
        text = { StudioOutlinedTextField(renameValue, { renameValue = it }, label = { Text(tr("Layer name", "图层名称")) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(layer.id, renameValue); renameLayer = null }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = { renameLayer = null }) { Text(tr("Cancel", "取消")) } },
    ) }
    colorLayer?.let { layer -> StudioDialog(
        onDismissRequest = { colorLayer = null },
        title = { Text(tr("Layer color replacement", "图层着色")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Black and white tones map to these colors. Source pixels stay editable.",
                "黑白明度映射到下面两色，原始像素保持可编辑。"), fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Dark / primary", "暗部／主色"), Modifier.weight(1f))
                BrushColorPicker(colorDark, { colorDark = it or 0xff000000.toInt() }, square = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Light / secondary", "亮部／副色"), Modifier.weight(1f))
                BrushColorPicker(colorLight, { colorLight = it or 0xff000000.toInt() }, square = true)
            }
        } },
        confirmButton = { TextButton(onClick = {
            onLayerColor(layer.id, app.inkloom.document.LayerColorReplacement(colorDark.toUInt().toLong(), colorLight.toUInt().toLong()))
            colorLayer = null
        }) { Text(tr("Apply", "应用")) } },
        dismissButton = { Row {
            if (layer.colorReplacement != null) TextButton(onClick = { onLayerColor(layer.id, null); colorLayer = null }) {
                Text(tr("Reset", "重置"))
            }
            TextButton(onClick = { colorLayer = null }) { Text(tr("Cancel", "取消")) }
        } },
    ) }
    expressionLayer?.let { layer -> StudioDialog(
        onDismissRequest = { expressionLayer = null },
        title = { Text(tr("Mask expression", "蒙版表达")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val switchDescription = tr("Show mask gradients switch", "显示蒙版渐变开关")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Show gradients", "显示渐变"), Modifier.weight(1f))
                Switch(expressionGradients, { expressionGradients = it },
                    modifier = Modifier.semantics { contentDescription = switchDescription })
            }
            if (!expressionGradients) {
                Text("${tr("Threshold", "阈值")} ${expressionThreshold.roundToInt()} / 255")
                Slider(expressionThreshold, { expressionThreshold = it }, valueRange = 0f..255f,
                    modifier = Modifier.width(260.dp))
            }
        } },
        confirmButton = { TextButton(onClick = {
            onMaskExpression(layer.id, expressionGradients, expressionThreshold.roundToInt())
            expressionLayer = null
        }) { Text(tr("Apply", "应用")) } },
        dismissButton = { TextButton(onClick = { expressionLayer = null }) { Text(tr("Cancel", "取消")) } },
    ) }
}
