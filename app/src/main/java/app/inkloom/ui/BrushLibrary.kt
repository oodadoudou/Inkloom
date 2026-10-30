package app.inkloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.scrollBy
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import app.inkloom.document.*
import app.inkloom.brushpresetv5.OwnedBrushPreset
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.brushpresetv5.PresetOwnership
import app.inkloom.brushpresetv5.PresetOwnershipOperations
import app.inkloom.importer.BrushImportFailure
import app.inkloom.importer.BrushLibraryInventory
import app.inkloom.importer.BrushGroupOperations
import app.inkloom.importer.BrushVersion
import app.inkloom.importer.BrushVersionHistoryStore
import app.inkloom.model.*
import app.inkloom.settings.AppSettings
import app.inkloom.settings.BrushUsageState
import app.inkloom.settings.category
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
internal fun BrushPanel(
    brushes: List<ImportedBrush>,
    usage: BrushUsageState,
    onUsage: (BrushUsageState) -> Unit,
    registeredGroups: List<String>,
    selectedBrushId: String?,
    requestedGroup: String?,
    settings: AppSettings,
    onClose: () -> Unit,
    onBrushSelected: (ImportedBrush) -> Unit,
    onImportBrushes: (String?) -> Unit,
    onImportBrushFolder: () -> Unit,
    brushImportFailureCount: Int,
    onOpenBrushImportFailures: () -> Unit,
    onBrushGridLayout: (Boolean) -> Unit,
    onBrushCategoryWidth: (Float) -> Unit,
    onExportBrush: (ImportedBrush) -> Unit,
    onQuickBrushCommit: (ImportedBrush) -> Unit,
    onUpdateBrush: (ImportedBrush) -> Unit,
    onReplaceBrushes: (List<ImportedBrush>) -> Unit,
    onReplaceGroups: (List<String>) -> Unit,
    onDeleteBrush: (String) -> Unit,
    onReorderBrush: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {


        ProcreateBrushLibrary(
            brushes = brushes,
            usage = usage,
            onUsage = onUsage,
            registeredGroups = registeredGroups,
            selectedBrushId = selectedBrushId,
            requestedGroup = requestedGroup,
            settings = settings,
            onClose = onClose,
            onBrushSelected = onBrushSelected,
            onImportBrushes = onImportBrushes,
            onImportBrushFolder = onImportBrushFolder,
            brushImportFailureCount = brushImportFailureCount,
            onOpenBrushImportFailures = onOpenBrushImportFailures,
            onBrushGridLayout = onBrushGridLayout,
            onBrushCategoryWidth = onBrushCategoryWidth,
            onExportBrush = onExportBrush,
            onQuickBrushCommit = onQuickBrushCommit,
            onUpdateBrush = onUpdateBrush,
            onReplaceBrushes = onReplaceBrushes,
            onReplaceGroups = onReplaceGroups,
            onDeleteBrush = onDeleteBrush,
            onReorderBrush = onReorderBrush,
            modifier = modifier,
        )

}

@Composable
internal fun BrushMoveToGroupDialog(brush: ImportedBrush, groups: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var groupName by remember(brush.id) { mutableStateOf(brush.groupName) }
    StudioDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Move brush", "移动笔刷")) },
        text = {
            Column(Modifier.dialogContentWidth(320.dp, 440.dp)) {
                Text(brush.name, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                StudioOutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                    label = { Text(tr("Group", "分组")) },
                    supportingText = { Text(tr("Enter a new name or choose an existing group", "输入新名称或选择已有分组")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (groups.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp)) {
                    groups.forEach { group -> item(group) { StudioFilterChip(selected = groupName.equals(group, true), onClick = { groupName = group }, label = { Text(group, maxLines = 1) }) } }
                }
                TextButton(onClick = { onMove("") }) { Text(tr("Move to Ungrouped", "移到未分组")) }
            }
        },
        confirmButton = { Button(onClick = { onMove(groupName) }, enabled = BrushGroupOperations.normalize(groupName).isNotBlank()) { Text(tr("Move", "移动")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
internal fun BrushBatchGroupDialog(
    count: Int,
    groups: List<String>,
    operation: BrushBatchGroupOperation,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var groupName by remember(operation) { mutableStateOf("") }
    val moving = operation == BrushBatchGroupOperation.MOVE
    StudioDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (moving) tr("Move selected brushes", "移动所选笔刷") else tr("Copy selected brushes", "复制所选笔刷")) },
        text = {
            Column(Modifier.dialogContentWidth(320.dp, 440.dp)) {
                Text("$count ${tr("brushes selected", "支笔刷已选择")}", color = MutedInk, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                StudioOutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                    label = { Text(tr("Destination group", "目标分组")) },
                    supportingText = { Text(tr("Enter a new name or choose an existing group", "输入新名称或选择已有分组")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (groups.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp)) {
                    groups.forEach { group -> item(group) { StudioFilterChip(selected = groupName.equals(group, true), onClick = { groupName = group }, label = { Text(group, maxLines = 1) }) } }
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

internal fun systemBrushCatalog(): List<ImportedBrush> = SystemBrushCatalog.brushes

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
    onClose: () -> Unit,
    onBrushSelected: (ImportedBrush) -> Unit,
    onImportBrushes: (String?) -> Unit,
    onImportBrushFolder: () -> Unit,
    brushImportFailureCount: Int,
    onOpenBrushImportFailures: () -> Unit,
    onBrushGridLayout: (Boolean) -> Unit,
    onBrushCategoryWidth: (Float) -> Unit,
    onExportBrush: (ImportedBrush) -> Unit,
    onQuickBrushCommit: (ImportedBrush) -> Unit,
    onUpdateBrush: (ImportedBrush) -> Unit,
    onReplaceBrushes: (List<ImportedBrush>) -> Unit,
    onReplaceGroups: (List<String>) -> Unit,
    onDeleteBrush: (String) -> Unit,
    onReorderBrush: (String, Int) -> Unit,
    modifier: Modifier,
) {
    val systemBrushes = remember { systemBrushCatalog() }
    val systemCategories = listOf("Pen", "Pencil", "Brush", "Erasers", "Airbrush", "Decoration", "Blend", "Liquify")
    val usableImportedBrushes = remember(brushes) { BrushGroupOperations.orderForDisplay(BrushLibraryInventory.importedUsable(brushes)) }
    val importedCategories = remember(usableImportedBrushes, registeredGroups) { BrushGroupOperations.treePaths(usableImportedBrushes, registeredGroups) }
    val manageableCategories = remember(importedCategories) { importedCategories.filterNot { category -> systemCategories.any { it.equals(category, true) } } }
    var collapsedGroupPaths by remember { mutableStateOf(emptySet<String>()) }
    val visibleImportedCategories = remember(importedCategories, collapsedGroupPaths) { importedCategories.filter { path ->
        generateSequence(BrushGroupOperations.parent(path), BrushGroupOperations::parent)
            .none { ancestor -> collapsedGroupPaths.any { it.equals(ancestor, true) } }
    }
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
    // Preserve browsing unless the active brush was changed outside this library while closed.
    val revealActiveOnOpen = remember { usage.category == null ||
        (usage.lastViewedBrushId != null && usage.lastViewedBrushId != selectedBrushId) }
    var selectedCategory by remember {
        mutableStateOf((if (revealActiveOnOpen) activeGroup?.takeIf { it in allCategories } else null)
            ?: usage.category?.takeIf { it in allCategories } ?: activeGroup?.takeIf { it in allCategories } ?: systemCategories.first())
    }
    var pendingCategoryReveal by remember { mutableStateOf<String?>(null) }
    val categoryListState = rememberLazyListState(usage.categoryScroll.index.coerceAtLeast(0), usage.categoryScroll.offset.coerceAtLeast(0))
    var addMenu by remember { mutableStateOf(false) }
    var manageGroups by remember { mutableStateOf(false) }
    var editBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var menuBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var moveBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    val gridLayout = settings.brushGridLayout
    var menuGroup by remember { mutableStateOf<String?>(null) }
    var renameGroup by remember { mutableStateOf<String?>(null) }
    var renameGroupValue by remember { mutableStateOf("") }
    var deleteGroup by remember { mutableStateOf<String?>(null) }
    val groupDropBounds = remember { mutableMapOf<String, Rect>() }
    val brushDragBounds = remember { mutableMapOf<String, Rect>() }
    val categoryViewport = remember { arrayOf<Rect?>(null) }
    val presetViewport = remember { arrayOf<Rect?>(null) }
    var draggingBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var draggingBrushIds by remember { mutableStateOf(emptySet<String>()) }
    var brushDragPosition by remember { mutableStateOf<Offset?>(null) }
    val liveBrushDragPoint by rememberUpdatedState(brushDragPosition)
    val edgeDensity = LocalDensity.current.density
    LaunchedEffect(draggingBrush?.id) {
        if (draggingBrush == null) return@LaunchedEffect
        while (true) {
            val point = liveBrushDragPoint
            val bounds = categoryViewport[0]
            if (point != null && bounds != null && point.x in bounds.left..bounds.right) {
                val edge = 36f * edgeDensity
                val velocity = when {
                    point.y < bounds.top + edge -> -8f * edgeDensity
                    point.y > bounds.bottom - edge -> 8f * edgeDensity
                    else -> 0f
                }
                if (velocity != 0f) categoryListState.scrollBy(velocity)
            }
            kotlinx.coroutines.delay(16)
        }
    }
    val hoveredBrushCategory = brushDragPosition?.let { point -> groupDropBounds.entries.firstOrNull { it.value.contains(point) }?.key }
    LaunchedEffect(draggingBrush?.id, hoveredBrushCategory) {
        if (draggingBrush == null || hoveredBrushCategory == null) return@LaunchedEffect
        kotlinx.coroutines.delay(550)
        selectedCategory = hoveredBrushCategory
        collapsedGroupPaths = collapsedGroupPaths - hoveredBrushCategory
    }
    var draggingGroup by remember { mutableStateOf<String?>(null) }
    var groupDragPosition by remember { mutableStateOf<Offset?>(null) }
    var managingBrushes by remember { mutableStateOf(false) }
    var selectedBrushIds by remember { mutableStateOf(emptySet<String>()) }
    val currentSelectedBrushIds by rememberUpdatedState(selectedBrushIds)
    var batchGroupOperation by remember { mutableStateOf<BrushBatchGroupOperation?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
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
        draggingBrushIds = emptySet()
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
            onDispose { brushDragBounds.remove(brush.id) }
        }
        return this.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            brushDragBounds[brush.id] = bounds
        }
    }

    fun brushDropHovered(brush: ImportedBrush): Boolean {
        val moving = draggingBrush ?: return false
        val point = brushDragPosition ?: return false
        return brush.id !in draggingBrushIds && moving.format != BrushFormat.SYSTEM &&
            BrushGroupOperations.normalize(moving.groupName).equals(BrushGroupOperations.normalize(brush.groupName), true) &&
            brushDragBounds[brush.id]?.contains(point) == true
    }

    fun finishBrushDrag() {
        val brush = draggingBrush ?: return
        val draggedIds = draggingBrushIds.ifEmpty { setOf(brush.id) }
        val batchDrag = managingBrushes
        val point = brushDragPosition
        val category = point?.let { p -> groupDropBounds.entries.firstOrNull { it.value.contains(p) }?.key }
        val targetId = point?.let { p -> brushDragBounds.entries.firstOrNull { it.key !in draggedIds && it.value.contains(p) }?.key }
        val targetBrush = (systemBrushes + brushes).firstOrNull { it.id == targetId }
        if (category != null) applyBrushGroupOperation(draggedIds, category, copy = !batchDrag)
        else if (targetBrush != null && !BrushGroupOperations.normalize(brush.groupName).equals(BrushGroupOperations.normalize(targetBrush.groupName), true)) {
            applyBrushGroupOperation(draggedIds, targetBrush.groupName, copy = false)
        } else if (targetId != null && draggedIds.size == 1 && brush.format != BrushFormat.SYSTEM) {
            val peers = BrushGroupOperations.orderForDisplay(brushes.filter {
                BrushGroupOperations.normalize(it.groupName).equals(BrushGroupOperations.normalize(brush.groupName), true)
            })
            val from = peers.indexOfFirst { it.id == brush.id }
            val to = peers.indexOfFirst { it.id == targetId }
            if (from >= 0 && to >= 0 && from != to) onReorderBrush(brush.id, to - from)
        }
        else if (point != null && presetViewport[0]?.contains(point) == true && selectedCategory in importedCategories && !BrushGroupOperations.normalize(brush.groupName).equals(selectedCategory, true)) {
            applyBrushGroupOperation(draggedIds, selectedCategory, copy = false)
        }
        draggingBrush = null
        draggingBrushIds = emptySet()
        brushDragPosition = null
    }
    @Composable
    fun BrushMoveHandle(brush: ImportedBrush) {
        // The library surface owns long-press/drag, including this handle.
        Icon(Icons.Outlined.DragIndicator, tr("Move brush ${brush.name}", "移动笔刷 ${brush.name}"),
            tint = MutedInk, modifier = Modifier.size(24.dp)
                .clickable { moveBrush = brush }.padding(3.dp))
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
    var revealedCategory by remember { mutableStateOf(if (revealActiveOnOpen) null else selectedCategory) }
    LaunchedEffect(selectedCategory, allCategories) {
        if (revealedCategory != selectedCategory) {
            if (draggingBrush == null) allCategories.distinct().indexOf(selectedCategory).takeIf { it >= 0 }?.let { categoryListState.animateScrollToItem(it) }
            revealedCategory = selectedCategory
        }
    }
    val usableLibraryBrushes = remember(systemBrushes, usableImportedBrushes) {
        BrushLibraryInventory.selectable(systemBrushes, usableImportedBrushes)
    }
    val visibleLibraryBrushes = remember(usableLibraryBrushes) { usableLibraryBrushes.filterNot(ImportedBrush::hidden) }
    val recentBrushIds = remember(visibleLibraryBrushes, usage) { usage.displayedIds(visibleLibraryBrushes.mapTo(linkedSetOf(), ImportedBrush::id)) }
    var brushSearchOpen by rememberSaveable { mutableStateOf(false) }
    var brushSearch by rememberSaveable { mutableStateOf("") }
    val displayed = remember(visibleLibraryBrushes, usage, selectedCategory, brushSearch) {
        val query = brushSearch.trim()
        if (query.isNotEmpty()) {
            visibleLibraryBrushes.filter { brush ->
                brush.name.contains(query, ignoreCase = true) ||
                    brush.groupName.contains(query, ignoreCase = true) ||
                    brush.format.label.contains(query, ignoreCase = true)
            }
        } else {
            val categoryBrushes = if (selectedCategory == recentCategory) {
                val byId = visibleLibraryBrushes.associateBy(ImportedBrush::id)
                recentBrushIds.mapNotNull(byId::get)
            } else visibleLibraryBrushes.filter { brush ->
                when (selectedCategory) {
                    allLabel -> true
                    ungroupedLabel -> brush.format != BrushFormat.SYSTEM
                    else -> if (brush.format == BrushFormat.SYSTEM) brush.groupName == selectedCategory else BrushGroupOperations.isWithin(brush.groupName, selectedCategory)
                }
            }
            if (selectedCategory == recentCategory) categoryBrushes else {
                val byId = categoryBrushes.associateBy(ImportedBrush::id)
                usage.orderInCategory(selectedCategory, categoryBrushes.map(ImportedBrush::id)).mapNotNull(byId::get)
            }
        }
    }
    LaunchedEffect(brushes) {
        val availableIds = usableLibraryBrushes.mapTo(linkedSetOf(), ImportedBrush::id)
        selectedBrushIds = selectedBrushIds.intersect(availableIds)
        if (managingBrushes && selectedBrushIds.isEmpty()) managingBrushes = false
    }
    var quickPropertiesOpen by remember { mutableStateOf(false) }
    val brushLibraryContext = LocalContext.current
    val versionHistoryStore = remember(brushLibraryContext) { BrushVersionHistoryStore(brushLibraryContext) }
    var historyBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    val brushSearchDescription = tr("Brush search field", "笔刷搜索框")
    val listKey = "list:$selectedCategory"
    val gridKey = "grid:$selectedCategory"
    val presetListState = remember(selectedCategory) {
        val saved = usage.scrollPositions[listKey]
        androidx.compose.foundation.lazy.LazyListState(saved?.index?.coerceAtLeast(0) ?: 0, saved?.offset?.coerceAtLeast(0) ?: 0)
    }
    val presetGridState = remember(selectedCategory) {
        val saved = usage.scrollPositions[gridKey]
        androidx.compose.foundation.lazy.grid.LazyGridState(saved?.index?.coerceAtLeast(0) ?: 0, saved?.offset?.coerceAtLeast(0) ?: 0)
    }
    val activeBrushIndex = displayed.indexOfFirst { it.id == selectedBrushId }
    if (gridLayout) RevealSelectedItem(presetGridState, Unit, selectedBrushId, activeBrushIndex, enabled = revealActiveOnOpen)
    else RevealSelectedItem(presetListState, Unit, selectedBrushId, activeBrushIndex, enabled = revealActiveOnOpen)
    LaunchedEffect(draggingBrush?.id, gridLayout, selectedCategory) {
        if (draggingBrush == null) return@LaunchedEffect
        while (true) {
            val point = liveBrushDragPoint
            val bounds = presetViewport[0]
            if (point != null && bounds != null && point.x in bounds.left..bounds.right) {
                val edge = 36f * edgeDensity
                val velocity = when {
                    point.y < bounds.top + edge -> -8f * edgeDensity
                    point.y > bounds.bottom - edge -> 8f * edgeDensity
                    else -> 0f
                }
                if (velocity != 0f) { if (gridLayout) presetGridState.scrollBy(velocity) else presetListState.scrollBy(velocity) }
            }
            kotlinx.coroutines.delay(16)
        }
    }
    val currentUsage by rememberUpdatedState(usage)
    val currentViewedBrush by rememberUpdatedState(selectedBrushId)
    val currentOnUsage by rememberUpdatedState(onUsage)
    DisposableEffect(selectedCategory, presetListState, presetGridState) {
        onDispose {
            currentOnUsage(currentUsage.copy(
                category = selectedCategory,
                lastViewedBrushId = currentViewedBrush,
                scrollPositions = currentUsage.scrollPositions + mapOf(
                    listKey to app.inkloom.settings.BrushLibraryScrollPosition(presetListState.firstVisibleItemIndex, presetListState.firstVisibleItemScrollOffset),
                    gridKey to app.inkloom.settings.BrushLibraryScrollPosition(presetGridState.firstVisibleItemIndex, presetGridState.firstVisibleItemScrollOffset),
                ),
                categoryScroll = app.inkloom.settings.BrushLibraryScrollPosition(categoryListState.firstVisibleItemIndex, categoryListState.firstVisibleItemScrollOffset),
            ))
        }
    }

    val quickBrush = usableLibraryBrushes.firstOrNull { it.id == selectedBrushId }
    @Composable fun RecentActions(brush: ImportedBrush, content: @Composable () -> Unit) {
        val recent = selectedCategory == recentCategory
        val pinned = brush.id in (if (recent) usage.pinnedIds else usage.categoryPins[selectedCategory].orEmpty())
        RecentBrushActions(
            enabled = !managingBrushes,
            pinned = pinned, language = settings.language,
            onPin = { onUsage(if (recent) {
                if (pinned) usage.unpin(brush.id) else usage.pin(brush.id)
            } else usage.toggleCategoryPin(selectedCategory, brush.id)) },
            onFind = if (recent) ({
                onBrushSelected(brush)
                selectedCategory = allCategories.firstOrNull { it.equals(brush.groupName, true) } ?: ungroupedLabel
            }) else null,
            onClear = if (!recent && brush.format == BrushFormat.SYSTEM) null else ({
                if (recent) onUsage(usage.clear(brush.id)) else onDeleteBrush(brush.id)
            }),
            clearText = if (recent) null else if (brush.format == BrushFormat.SYSTEM) tr("Hide", "隐藏") else tr("Delete", "删除"),
            content = content,
        )
    }
    val panelOrigin = remember { arrayOf(Offset.Zero) }
    Surface(
        modifier = modifier.onGloballyPositioned { panelOrigin[0] = it.localToWindow(Offset.Zero) }
            .brushLibraryDragSurface(
                hit = { local -> brushDragBounds.entries.firstOrNull { it.value.contains(panelOrigin[0] + local) }?.key },
                start = { id, local ->
                    draggingBrush = usableLibraryBrushes.firstOrNull { it.id == id }
                    draggingBrushIds = if (managingBrushes) (currentSelectedBrushIds + id) else setOf(id)
                    if (managingBrushes && id !in currentSelectedBrushIds) selectedBrushIds = currentSelectedBrushIds + id
                    brushDragPosition = panelOrigin[0] + local
                },
                move = { local -> brushDragPosition = panelOrigin[0] + local },
                finish = { finishBrushDrag() },
                cancel = { draggingBrush = null; draggingBrushIds = emptySet(); brushDragPosition = null },
            ),
        shape = RoundedCornerShape(0.dp),
        color = Paper,
        contentColor = Ink,
    ) {
        Column {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(46.dp).padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { quickPropertiesOpen = !quickPropertiesOpen }, enabled = quickBrush != null,
                        modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.Tune, tr("Quick brush properties", "笔刷快捷参数"), tint = Ink)
                    }
                    DropdownMenu(expanded = quickPropertiesOpen && quickBrush != null,
                        onDismissRequest = { quickPropertiesOpen = false }) {
                        quickBrush?.let { selected ->
                            BrushQuickProperties(selected, settings.language, onCommit = { updated ->
                                if (selected.format != BrushFormat.SYSTEM) versionHistoryStore.record(selected)
                                onQuickBrushCommit(updated)
                            },
                                onEdit = { quickPropertiesOpen = false; editBrush = selected })
                        }
                    }
                }
                IconButton(onClick = { onBrushGridLayout(!gridLayout) }, modifier = Modifier.size(38.dp)) {
                    Icon(if (gridLayout) Icons.Outlined.ViewList else Icons.Outlined.GridView, if (gridLayout) tr("List view", "列表视图") else tr("Grid view", "网格视图"), tint = Ink)
                }
                IconButton(onClick = {
                    brushSearchOpen = !brushSearchOpen
                    if (!brushSearchOpen) brushSearch = ""
                }, modifier = Modifier.size(38.dp)) {
                    Icon(if (brushSearchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                        if (brushSearchOpen) tr("Close brush search", "关闭笔刷搜索") else tr("Search brushes", "搜索笔刷"), tint = Ink)
                }
                IconButton(onClick = { quickBrush?.let { historyBrush = it } }, enabled = quickBrush != null && quickBrush.format != BrushFormat.SYSTEM,
                    modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Outlined.History, tr("Current brush version history", "当前笔刷版本历史"), tint = Ink)
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
                        StudioMenuItem(text = { Text(tr("Create new brush", "新建笔刷")) }, leadingIcon = { Icon(Icons.Outlined.Brush, null) }, onClick = {
                            addMenu = false
                            val group = selectedCategory.takeIf { it in systemCategories || it in importedCategories }
                            val created = BrushPresetV5Import.createUserRecord(ImportedBrush(name = newBrushName, format = BrushFormat.INKLOOM, compatibility = Compatibility.COMPLETE, sizeBytes = 0L, report = listOf(createdInInkloom), groupName = group.orEmpty()))
                            onReplaceBrushes(brushes + created)
                            editBrush = created
                        })
                        StudioMenuItem(text = { Text(tr("Import from files", "从文件导入")) }, leadingIcon = { Icon(Icons.Outlined.FileDownload, null) }, onClick = {
                            addMenu = false
                            onImportBrushes(selectedCategory.takeIf { it != allLabel && it != ungroupedLabel && it != recentCategory })
                        })
                        StudioMenuItem(text = { Text(tr("Import folder", "导入文件夹")) }, leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) }, onClick = { addMenu = false; onImportBrushFolder() })
                        StudioMenuItem(
                            text = { Text(tr("Import issues ($brushImportFailureCount)", "导入问题（$brushImportFailureCount）")) },
                            leadingIcon = { Icon(Icons.Outlined.ErrorOutline, null) },
                            enabled = brushImportFailureCount > 0,
                            onClick = { addMenu = false; onOpenBrushImportFailures() },
                        )
                        StudioMenuItem(text = { Text(tr("Manage brush sets", "管理笔刷组")) }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = { addMenu = false; manageGroups = true })
                    }
                }
            }
            HorizontalDivider(color = OutlineSoft)
            if (brushSearchOpen) {
                StudioOutlinedTextField(
                    value = brushSearch,
                    onValueChange = { brushSearch = it },
                    placeholder = { Text(tr("Brush name, set, or format", "搜索笔刷名称、分组或格式"), fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(17.dp)) },
                    trailingIcon = { if (brushSearch.isNotEmpty()) IconButton(onClick = { brushSearch = "" }) { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
                        .semantics { contentDescription = brushSearchDescription },
                )
                HorizontalDivider(color = OutlineSoft)
            }
            if (managingBrushes) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp).background(Paper).padding(horizontal = 8.dp)
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
            BrushLibrarySplitLayout(settings.brushCategoryWidthDp,onBrushCategoryWidth,
                tr("Resize brush categories", "调整笔刷分类栏宽度"),Modifier.weight(1f)) { categoryWidth, divider ->
                val brushCategoriesDescription = tr("Brush categories", "笔刷分类")
                LazyColumn(
                    Modifier.width(categoryWidth).fillMaxHeight()
                        .semantics { contentDescription = brushCategoriesDescription }
                        .onGloballyPositioned { categoryViewport[0] = it.boundsInWindow() }.background(Paper),
                    state = categoryListState,
                    contentPadding = PaddingValues(vertical = 7.dp),
                ) {
                    items(allCategories.distinct(), key = { it }) { category ->
                        val selected = category == selectedCategory
                        val systemGroup = category in systemCategories
                        val customGroup = category in importedCategories && !systemGroup
                        val dropGroup = systemGroup || customGroup
                        DisposableEffect(category) {
                            onDispose { groupDropBounds.remove(category) }
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
                                            groupDropBounds[category] = bounds
                                        }
                                    }
                                    .background(if (draggingGroup == category) SageContainer else if (draggingBrush != null && dropGroup && brushDragPosition?.let { groupDropBounds[category]?.contains(it) } == true) SageContainer else if (draggingBrush != null && dropGroup) Paper else if (selected) SageContainer else Color.Transparent)
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
                                StudioMenuItem(text = { Text(tr("Rename group", "重命名分组")) }, onClick = {
                                    menuGroup = null
                                    renameGroup = category
                                    renameGroupValue = category
                                })
                                StudioMenuItem(text = { Text(tr("Duplicate group", "复制分组")) }, onClick = {
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
                                StudioMenuItem(text = { Text(tr("Delete group", "删除分组"), color = MaterialTheme.colorScheme.error) }, onClick = {
                                    menuGroup = null
                                    deleteGroup = category
                                })
                            }
                        }
                    }
                }
                divider()
                if (selectedCategory == recentCategory && displayed.isEmpty()) Box(
                    Modifier.weight(1f).fillMaxHeight().padding(20.dp), contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Your recently used brushes appear here. Swipe a brush left to pin it.", "使用过的笔刷会显示在这里。向左滑动笔刷即可置顶。"),
                        color = MutedInk, fontSize = 12.sp)
                } else if (gridLayout) LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = presetGridState,
                    modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { presetViewport[0] = it.boundsInWindow() }.semantics { contentDescription = brushListDescription },
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
                        } else Modifier.clickable { if (selectedBrushId == brush.id) editBrush = brush else onBrushSelected(brush) }
                        RecentActions(brush) { Column(
                            Modifier.fillMaxWidth().height(112.dp).semantics { contentDescription = brushPresetDescription }.brushDropTarget(brush).then(interaction).clip(RoundedCornerShape(6.dp)).background(if (batchSelected || draggingBrush?.id == brush.id || brushDropHovered(brush)) SageContainer else if (selected) SageContainer else Paper).border(1.dp, if (batchSelected || selected) Ink else OutlineSoft, RoundedCornerShape(6.dp)).padding(7.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(brush.name, Modifier.weight(1f), color = Ink, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (brush.id in (if (selectedCategory == recentCategory) usage.pinnedIds else usage.categoryPins[selectedCategory].orEmpty())) Icon(Icons.Outlined.PushPin, tr("Pinned brush", "已置顶笔刷"), Modifier.size(16.dp))
                                if (!managingBrushes && selectedCategory != recentCategory) BrushMoveHandle(brush)
                            }
                            Spacer(Modifier.height(4.dp))
                            Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(4.dp), color = Paper) {
                                BrushTipPreview(brush, Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                            if (batchSelected) Icon(Icons.Outlined.CheckCircle, null, Modifier.align(Alignment.End).size(17.dp), tint = Ink)
                        } }
                    }
                } else LazyColumn(
                    state = presetListState,
                    modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { presetViewport[0] = it.boundsInWindow() }.semantics { contentDescription = brushListDescription },
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(displayed, key = { it.id }) { brush ->
                        val selected = selectedBrushId == brush.id
                        val batchSelected = brush.id in selectedBrushIds
                        val brushPresetDescription = tr("Brush preset ${brush.name}", "笔刷预设 ${brush.name}")
                        val interaction = if (managingBrushes) Modifier.clickable {
                            selectedBrushIds = if (brush.id in currentSelectedBrushIds) currentSelectedBrushIds - brush.id else currentSelectedBrushIds + brush.id
                        } else Modifier.clickable { if (selectedBrushId == brush.id) editBrush = brush else onBrushSelected(brush) }
                        RecentActions(brush) { Column(
                            Modifier.fillMaxWidth().height(84.dp).semantics { contentDescription = brushPresetDescription }.brushDropTarget(brush).then(interaction).background(if (batchSelected || draggingBrush?.id == brush.id || brushDropHovered(brush)) SageContainer else if (selected) SageContainer else Paper).padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(brush.name, Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (batchSelected) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = Ink)
                                if (brush.id in (if (selectedCategory == recentCategory) usage.pinnedIds else usage.categoryPins[selectedCategory].orEmpty())) Icon(Icons.Outlined.PushPin, tr("Pinned brush", "已置顶笔刷"), Modifier.size(16.dp))
                                if (!managingBrushes && selectedCategory != recentCategory) BrushMoveHandle(brush)
                            }
                            BrushTipPreview(brush, Modifier.fillMaxWidth().weight(1f))
                        } }
                    }
                }
            }
        }
        draggingBrush?.let { lifted ->
            val dragPreviewDescription = tr("Dragged brush preview", "拖动中的笔刷预览")
            val dragLabel = if (draggingBrushIds.size > 1) tr("${draggingBrushIds.size} brushes", "${draggingBrushIds.size} 支笔刷") else lifted.name
            Text(dragLabel, color = Ink, maxLines = 1, fontSize = 12.sp,
                modifier = Modifier.offset {
                    val point = (brushDragPosition ?: panelOrigin[0]) - panelOrigin[0]
                    IntOffset(point.x.roundToInt() + 20, point.y.roundToInt() - 48)
                }.wrapContentSize(Alignment.TopStart, unbounded = true).widthIn(max = 220.dp).heightIn(max = 40.dp)
                    .semantics { contentDescription = dragPreviewDescription }.shadow(8.dp, RoundedCornerShape(8.dp))
                    .background(SageContainer, RoundedCornerShape(8.dp)).padding(12.dp, 8.dp))
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
    if (confirmBatchDelete) StudioDialog(
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
        StudioDialog(
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
        StudioDialog(
            onDismissRequest = { renameGroup = null },
            title = { Text(tr("Rename brush group", "重命名笔刷分组")) },
            text = { StudioOutlinedTextField(renameGroupValue, { renameGroupValue = it.take(BrushGroupOperations.MAX_PATH_LENGTH) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
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
        StudioDialog(
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
                if (brushes.any { it.id == updated.id }) {
                    if (brush.format != BrushFormat.SYSTEM) versionHistoryStore.record(brush)
                    onUpdateBrush(updated)
                } else onReplaceBrushes(brushes + updated)
                editBrush = null
            },
            onSaveInPlace = { v5 ->
                val updated = BrushPresetV5Import.writePreset(brush, v5, BrushFormat.INKLOOM)
                if (brushes.any { it.id == updated.id }) {
                    if (brush.format != BrushFormat.SYSTEM) versionHistoryStore.record(brush)
                    onUpdateBrush(updated)
                } else onReplaceBrushes(brushes + updated)
                editBrush = null
            },
            onResetToFactory = (owned.ownership as? PresetOwnership.User)?.derivedFromFactoryId
                ?.let { factoryId -> systemBrushes.firstOrNull { it.id == factoryId } }
                ?.let(BrushPresetV5Import::requirePreset)
                ?.let { factoryPreset ->
                    {
                        PresetOwnershipOperations.resetToFactory(owned, factoryPreset)?.let { reset ->
                            versionHistoryStore.record(brush)
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
    historyBrush?.let { brush ->
        var versions by remember(brush.id) { mutableStateOf(versionHistoryStore.versions(brush.id)) }
        BrushVersionHistoryDialog(
            brush = brush,
            versions = versions,
            onDismiss = { historyBrush = null },
            onRestore = { version ->
                versionHistoryStore.record(brush)
                val restored = versionHistoryStore.restore(brush, version)
                onUpdateBrush(restored)
                historyBrush = restored
                versions = versionHistoryStore.versions(brush.id)
            },
            onDelete = { version ->
                versionHistoryStore.delete(version.versionId)
                versions = versionHistoryStore.versions(brush.id)
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
internal fun BrushGroupManagerDialog(groups: List<String>, onDismiss: () -> Unit, onRename: (String, String) -> Unit, onMove: (String, Int) -> Unit, onCreate: (String) -> Unit, initialParent: String? = null) {
    var selected by remember { mutableStateOf(groups.firstOrNull().orEmpty()) }
    LaunchedEffect(groups) { if (groups.none { it == selected }) selected = groups.firstOrNull().orEmpty() }
    var replacement by remember(selected) { mutableStateOf(selected) }
    var newGroupPath by remember(initialParent) { mutableStateOf("") }
    val normalizedParent = BrushGroupOperations.normalize(initialParent.orEmpty())
    val normalizedInputGroup = BrushGroupOperations.normalize(newGroupPath)
    val normalizedNewGroup = if (normalizedParent.isNotBlank() && '/' !in newGroupPath) {
        BrushGroupOperations.normalize("$normalizedParent/$normalizedInputGroup")
    } else normalizedInputGroup
    StudioDialog(
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { Text(tr("Manage groups", "管理分组")) },
        text = {
            Column(Modifier.dialogContentWidth(320.dp, 440.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioOutlinedTextField(
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
                        groups.forEach { group -> item(group) { StudioFilterChip(selected = selected == group, onClick = { selected = group }, label = { Text(group, maxLines = 1) }) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        val selectedIndex = groups.indexOf(selected)
                        OutlinedButton(onClick = { onMove(selected, -1) }, enabled = selectedIndex > 0) { Icon(Icons.Outlined.ArrowBack, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(tr("Earlier", "前移")) }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { onMove(selected, 1) }, enabled = selectedIndex in 0 until groups.lastIndex) { Text(tr("Later", "后移")); Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
                    }
                    StudioOutlinedTextField(
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
internal fun BrushImportFailuresDialog(
    failures: List<BrushImportFailure>,
    language: UiLanguage,
    onDismiss: () -> Unit,
    onRetry: (BrushImportFailure) -> Unit,
    onDelete: (BrushImportFailure) -> Unit,
    onClear: () -> Unit,
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    StudioDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("Brush import issues", "笔刷导入问题")) },
        text = {
            Column(Modifier.dialogContentWidth(360.dp, 520.dp)) {
                Text(
                    language.text(
                        "Failed sources stay here with their exact reason. Retry after updating Inkloom or restoring file access.",
                        "失败来源会保留在这里并显示具体原因。更新 Inkloom 或恢复文件权限后可重新尝试。",
                    ),
                    color = MutedInk,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (failures.isEmpty()) Text(language.text("No unresolved imports.", "没有未解决的导入问题。"), color = MutedInk)
                else LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(failures, key = BrushImportFailure::id) { failure ->
                        OutlinedCard {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(failure.displayName, maxLines = 2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        if (failure.targetGroup.isNotBlank()) Text(failure.targetGroup, color = MutedInk, fontSize = 10.sp)
                                        Text(
                                            language.text(
                                                "Attempt ${failure.attemptCount} · ${formatter.format(Date(failure.lastAttemptAt))}",
                                                "第 ${failure.attemptCount} 次 · ${formatter.format(Date(failure.lastAttemptAt))}",
                                            ),
                                            color = MutedInk,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    TextButton(onClick = { onRetry(failure) }) { Text(language.text("Retry", "重试")) }
                                    IconButton(onClick = { onDelete(failure) }) { Icon(Icons.Outlined.Delete, language.text("Forget this issue", "移除此问题")) }
                                }
                                Text(failure.reasons.joinToString("\n"), color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                if (failure.finalStates.isNotEmpty()) Text(failure.finalStates.joinToString(" · "), color = MutedInk, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(language.text("Done", "完成")) } },
        dismissButton = { if (failures.isNotEmpty()) TextButton(onClick = onClear) { Text(language.text("Clear all", "全部清除")) } },
    )
}

@Composable
internal fun BrushVersionHistoryDialog(
    brush: ImportedBrush,
    versions: List<BrushVersion>,
    onDismiss: () -> Unit,
    onRestore: (BrushVersion) -> Unit,
    onDelete: (BrushVersion) -> Unit,
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    StudioDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Version history", "版本历史")) },
        text = {
            Column(Modifier.dialogContentWidth(340.dp, 480.dp)) {
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

@Composable
internal fun BrushTipPreview(brush: ImportedBrush, modifier: Modifier = Modifier) {
    BrushStrokePreview(brush, LocalUiLanguage.current, modifier)
}

@Composable
internal fun BrushSettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    val name = localizedBrushLabel(label)
    val description = tr("$name slider", "$name 滑块")
    Column { Row { Text(name, Modifier.weight(1f), fontSize = 12.sp); Text(if (range.endInclusive > 10f) "%.1f".format(value) else "%.2f".format(value), color = MutedInk, fontSize = 11.sp) }; BrushParameterSlider(value, onValue, valueRange = range, modifier = Modifier.semantics { contentDescription = description }) }
}
