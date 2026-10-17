package app.inkloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.inkloom.document.*
import app.inkloom.model.*
import app.inkloom.settings.AppSettings
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun PageInsertionIcon(after: Boolean, description: String) {
    Box(Modifier.size(20.dp).semantics { contentDescription = description }) {
        Icon(Icons.Outlined.NoteAdd, null, Modifier.size(16.dp).align(Alignment.Center))
        Canvas(Modifier.fillMaxSize()) {
            val y = if (after) size.height - 1.dp.toPx() else 1.dp.toPx()
            drawLine(Ink, Offset(2.dp.toPx(), y), Offset(size.width - 2.dp.toPx(), y), 1.dp.toPx())
        }
    }
}

@Composable
internal fun PageRail(
    document: InkDocument,
    documents: List<InkDocument>,
    selectedPageId: String,
    assets: DocumentAssetService,
    onClose: () -> Unit,
    pageTemplates: PageTemplateLibrary,
    onSelect: (String) -> Unit,
    onOpenLayers: (String) -> Unit,
    onAdd: (String, Long?) -> Unit,
    onInsertNextTo: (String, String, Boolean) -> Unit,
    onAppendFiles: () -> Unit,
    onDuplicate: (String, String) -> Unit,
    onDelete: (String, String) -> Unit,
    onMove: (String, String, Int) -> Unit,
    onReorder: (String, List<String>) -> Unit,
    onTransfer: (String, String, String, Boolean) -> Unit,
    onTransferPages: (String, Set<String>, String, Boolean) -> Boolean,
    onApplyTemplate: (String, String, String, Long?) -> Unit,
    onApplyTemplates: (String, Set<String>, String, Long?) -> Unit,
    onImportTemplates: () -> Unit,
    onToggleTemplateFavorite: (String) -> Unit,
    onSetDefaultTemplate: (String, String?, Long?) -> Unit,
    onSaveAsTemplate: (String, String, String) -> PageTemplate?,
    onDeleteTemplate: (String) -> Unit,
    onCreateCustomTemplate: (String, Int, Int, PageBackground.Paper) -> PageTemplate?,
    onToggleBookmark: (String, String) -> Unit,
    onOutlineLevel: (String, String, Int) -> Unit,
    onOutlineCollapsed: (String, String) -> Unit,
    onRename: (String, String, String) -> Unit,
    onMetadata: (String, String, String?, List<String>) -> Unit,
    onClearInk: (String, String) -> Unit,
    onCrop: (String, String, PageCrop) -> Unit,
    onRotate: (String, String) -> Unit,
    modifier: Modifier = Modifier.width(112.dp).fillMaxHeight().padding(6.dp),
) {

    val pageDensity = LocalDensity.current.density
    var navigationTab by rememberSaveable(document.id) { mutableIntStateOf(0) }
    var selectedTag by rememberSaveable(document.id) { mutableStateOf<String?>(null) }
    var currentMetadataOpen by remember(document.id) { mutableStateOf(false) }
    val tagGroups = remember(document.pages) { app.inkloom.document.PageTagIndex.groups(document.pages) }
    val tagGroup = tagGroups.firstOrNull { it.name.equals(selectedTag, ignoreCase = true) }
    val visiblePages = remember(document.pages, navigationTab, tagGroup) {
        when (navigationTab) {
            1 -> document.pages.filter { it.bookmarked }
            2 -> if (tagGroup == null) emptyList() else document.pages.filter { it.id in tagGroup.pageIds }
            else -> document.pages
        }
    }
    val pageListState = rememberLazyListState()
    LaunchedEffect(document.id, selectedPageId, navigationTab, selectedTag, visiblePages.map { it.id }) {
        val index = visiblePages.indexOfFirst { it.id == selectedPageId }
        if (index >= 0 && pageListState.layoutInfo.visibleItemsInfo.none { it.key == selectedPageId }) pageListState.scrollToItem(index)
        else if (navigationTab == 2 && tagGroup == null) pageListState.scrollToItem(0)
    }
    val measuredPageBounds = remember(document.id) { mutableMapOf<String, Rect>() }
    val pagePointerCoordinates = remember(document.id) { mutableMapOf<String, androidx.compose.ui.layout.LayoutCoordinates>() }
    val transferTargets = documents.filter { it.id != document.id && it.trashedAt == null }
    var addTemplateOpen by remember(document.id) { mutableStateOf(false) }
    var batchTemplateOpen by remember(document.id) { mutableStateOf(false) }
    var batchTransferOpen by remember(document.id) { mutableStateOf(false) }
    var managingPages by remember(document.id) { mutableStateOf(false) }
    var selectedPageIds by remember(document.id) { mutableStateOf(emptySet<String>()) }
    val currentSelectedPageIds by rememberUpdatedState(selectedPageIds)
    val pageManagementDescription = tr("Page management: ${selectedPageIds.size} selected", "页面管理：已选择 ${selectedPageIds.size} 页")
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            val labels = listOf(tr("Pages", "页面"), tr("Saved", "收藏"), tr("Tags", "标签"))
            val descriptions = listOf(tr("Show all pages", "显示所有页面"), tr("Show bookmarked pages", "显示收藏页"), tr("Show page tag categories", "显示页面标签分类"))
            val icons = listOf(Icons.Outlined.Description, Icons.Outlined.BookmarkBorder, Icons.Outlined.Label)
            labels.forEachIndexed { index, label ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                    .background(if (navigationTab == index) SageContainer else Color.Transparent)
                    .clickable { navigationTab = index; managingPages = false; selectedPageIds = emptySet() }
                    .semantics { contentDescription = descriptions[index]; selected = navigationTab == index },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(icons[index], null, Modifier.size(15.dp), tint = if (navigationTab == index) Ink else MutedInk)
                    Text(label, fontSize = 10.sp, maxLines = 1, color = if (navigationTab == index) Ink else MutedInk)
                }
            }
        }
        HorizontalDivider(color = OutlineSoft)
        if (navigationTab == 1) Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${visiblePages.size}", Modifier.weight(1f).padding(start = 6.dp), fontSize = 11.sp, color = MutedInk)
            val current = document.pages.firstOrNull { it.id == selectedPageId }
            IconButton(onClick = { current?.let { onToggleBookmark(document.id, it.id) } }, Modifier.size(32.dp)) {
                Icon(if (current?.bookmarked == true) Icons.Default.Bookmark else Icons.Outlined.BookmarkAdd,
                    tr("Toggle current page bookmark", "切换当前页收藏"), Modifier.size(17.dp))
            }
        }
        if (navigationTab == 2) Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tagGroup != null) IconButton(onClick = { selectedTag = null }, Modifier.size(28.dp)) {
                Icon(Icons.Outlined.ArrowBack, tr("Back to tag categories", "返回标签分类"), Modifier.size(16.dp))
            }
            Text(tagGroup?.name ?: tr("Categories", "分类"), Modifier.weight(1f).padding(start = 4.dp), fontSize = 11.sp, maxLines = 1, color = MutedInk)
            IconButton(onClick = { currentMetadataOpen = true }, Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Label, tr("Edit current page tags", "编辑当前页标签"), Modifier.size(16.dp))
            }
        }
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onInsertNextTo(document.id, selectedPageId, false) }, modifier = Modifier.size(28.dp)) { PageInsertionIcon(false, tr("Insert page before current", "在当前页前插入页面")) }
                IconButton(onClick = { onInsertNextTo(document.id, selectedPageId, true) }, modifier = Modifier.size(28.dp)) { PageInsertionIcon(true, tr("Insert page after current", "在当前页后插入页面")) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onAppendFiles, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.FileOpen, tr("Add PDF or image pages", "追加 PDF／图片页面"), Modifier.size(18.dp)) }
                IconButton(onClick = { addTemplateOpen = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, tr("Add page", "添加页面"), Modifier.size(20.dp)) }
            }
        }
        val pageListDescription = tr("Page list", "页面列表")
        LazyColumn(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = pageListDescription }, state = pageListState, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (managingPages) stickyHeader(key = "page-management") {
            Column(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(4.dp)).padding(4.dp)) {
                Row(Modifier.semantics { contentDescription = pageManagementDescription }, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { managingPages = false; selectedPageIds = emptySet() }, modifier = Modifier.size(26.dp)) { Icon(Icons.Outlined.Close, tr("Exit page management", "退出页面管理"), Modifier.size(16.dp)) }
                    Text("${selectedPageIds.size} ${tr("selected", "已选择")}", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { selectedPageIds = document.pages.mapTo(linkedSetOf(), InkPage::id) }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(tr("All", "全选"), fontSize = 9.sp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { batchTemplateOpen = true }, enabled = selectedPageIds.isNotEmpty(), modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Description, tr("Replace selected page templates", "批量替换所选页面模板"), Modifier.size(16.dp))
                    }
                    IconButton(onClick = { batchTransferOpen = true }, enabled = selectedPageIds.isNotEmpty() && transferTargets.isNotEmpty(), modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.DriveFileMove, tr("Move or copy selected pages to another notebook", "移动或复制所选页面到其他笔记本"), Modifier.size(16.dp))
                    }
                    IconButton(onClick = { document.pages.filter { it.id in selectedPageIds }.forEach { onDuplicate(document.id, it.id) }; managingPages = false; selectedPageIds = emptySet() }, enabled = selectedPageIds.isNotEmpty(), modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.ContentCopy, tr("Duplicate selected pages", "复制所选页面"), Modifier.size(16.dp)) }
                    IconButton(onClick = {
                        document.pages.filter { it.id in selectedPageIds }.take((document.pages.size - 1).coerceAtLeast(0)).forEach { onDelete(document.id, it.id) }
                        managingPages = false; selectedPageIds = emptySet()
                    }, enabled = selectedPageIds.isNotEmpty() && document.pages.size > 1, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, tr("Delete selected pages", "删除所选页面"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (navigationTab == 2 && tagGroup == null) {
            if (tagGroups.isEmpty()) item { Text(tr("No page tags", "还没有页面标签"), Modifier.padding(8.dp), color = MutedInk, fontSize = 11.sp) }
            items(tagGroups, key = { "tag:${it.name.lowercase()}" }) { group ->
                val description = tr("Page tag ${group.name}", "页面标签 ${group.name}")
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(5.dp))
                    .background(Workspace).clickable { selectedTag = group.name }
                    .semantics { contentDescription = description }
                    .padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.name, Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 2)
                    Text(group.pageIds.size.toString(), color = MutedInk, fontSize = 10.sp)
                }
            }
        }
        if (navigationTab == 1 && visiblePages.isEmpty()) item {
            Text(tr("No bookmarked pages", "还没有收藏页"), Modifier.padding(8.dp), color = MutedInk, fontSize = 11.sp)
        }
        items(visiblePages, key = { it.id }) { page ->
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
            var pageDragDistance by remember(page.id) { mutableFloatStateOf(0f) }
            var pageDragTravel by remember(page.id) { mutableFloatStateOf(0f) }
            var pageDragging by remember(page.id) { mutableStateOf(false) }
            DisposableEffect(page.id) { onDispose { measuredPageBounds.remove(page.id); pagePointerCoordinates.remove(page.id); pagePointerCoordinates.remove(page.id + ":handle") } }
            var pageDragStartY by remember(page.id) { mutableFloatStateOf(0f) }
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
                    val moved = pageDragTravel >= 18f
                    if (moved) {
                        val selected = if (managingPages && selectedPageIds.isNotEmpty()) selectedPageIds + page.id else setOf(page.id)
                        val order = document.pages.map(InkPage::id)
                        val target = app.inkloom.document.PageDropPosition.insertionIndex(order, selected,
                            measuredPageBounds.mapValues { it.value.center.y }, pageDragStartY + pageDragDistance * pageDensity)
                        if (target != null) {
                            val reordered = PageBatchOperations.moveBlock(order, selected, target)
                            if (reordered != order) onReorder(document.id, reordered)
                        }
                    } else {
                        managePage()
                    }
                    pageDragDistance = 0f
                    pageDragging = false
                }
                fun Modifier.pageReorderDrag(): Modifier = pointerInput(page.id, managingPages, document.pages.map(InkPage::id)) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pageDragTravel = 0f; pageDragDistance = 0f; pageDragStartY = pagePointerCoordinates[page.id]?.localToRoot(it)?.y ?: 0f; pageDragging = true },
                        onDragEnd = ::finishPageDrag,
                        onDragCancel = { pageDragDistance = 0f; pageDragging = false },
                    ) { change, amount ->
                        change.consume()
                        pageDragTravel += kotlin.math.abs(amount.y) / pageDensity
                        pagePointerCoordinates[page.id]?.localToRoot(change.position)?.let { pageDragDistance = (it.y - pageDragStartY) / pageDensity }
                    }
                }
                fun Modifier.pageHandleDrag(): Modifier = pointerInput(page.id, managingPages, document.pages.map(InkPage::id)) {
                    detectDragGestures(
                        onDragStart = { pageDragTravel = 0f; pageDragDistance = 0f; pageDragStartY = pagePointerCoordinates[page.id + ":handle"]?.localToRoot(it)?.y ?: 0f; pageDragging = true },
                        onDragEnd = ::finishPageDrag,
                        onDragCancel = { pageDragDistance = 0f; pageDragging = false },
                    ) { change, amount ->
                        change.consume()
                        pageDragTravel += kotlin.math.abs(amount.y) / pageDensity
                        pagePointerCoordinates[page.id + ":handle"]?.localToRoot(change.position)?.let { pageDragDistance = (it.y - pageDragStartY) / pageDensity }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            (PageCropTransform.visibleWidth(page) / PageCropTransform.visibleHeight(page).coerceAtLeast(1f))
                                .coerceIn(.5f, 1.8f),
                        )
                        .onGloballyPositioned { measuredPageBounds[page.id] = it.boundsInRoot() }
                        .zIndex(if (pageDragging) 4f else 0f)
                        .graphicsLayer {
                            translationY = pageDragDistance * density
                            scaleX = if (pageDragging) 1.06f else 1f
                            scaleY = if (pageDragging) 1.06f else 1f
                            shadowElevation = if (pageDragging) 12.dp.toPx() else 0f
                        }
                        .background(if (page.id == selectedPageId || pageBatchSelected) SageContainer else Paper, RoundedCornerShape(5.dp))
                        .border(if (page.id == selectedPageId) 3.dp else if (pageBatchSelected) 2.dp else 1.dp, if (page.id == selectedPageId) Ink else if (pageBatchSelected) MutedInk else OutlineSoft, RoundedCornerShape(5.dp))
                        .padding(4.dp),
                ) {
                    PageInkPreview(page, assets, Modifier.matchParentSize().clip(RoundedCornerShape(2.dp)))
                    if (page.background is PageBackground.Pdf) Text("PDF", fontSize = 9.sp, color = MutedInk)
                    if (page.bookmarked) Icon(Icons.Default.Bookmark, tr("Bookmarked", "已收藏"), tint = Ink, modifier = Modifier.size(16.dp).align(Alignment.TopEnd))
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(24.dp).background(Paper, RoundedCornerShape(2.dp)).clickable { onOpenLayers(page.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Layers, tr("Open layers for this page", "打开此页面的图层"), Modifier.size(15.dp))
                    }
                    Box(Modifier.matchParentSize().onGloballyPositioned { pagePointerCoordinates[page.id] = it }.pointerInput(page.id, managingPages) {
                        val layerHotspot = 24.dp.toPx()
                        detectTapGestures(onTap = { position ->
                            if (position.x >= size.width - layerHotspot && position.y >= size.height - layerHotspot) onOpenLayers(page.id)
                            else selectPage()
                        })
                    }.pageReorderDrag().semantics {
                        contentDescription = pageDescription
                        selected = page.id == selectedPageId || pageBatchSelected
                        onClick { selectPage(); true }
                        onLongClick { managePage(); true }
                    })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        tr("Drag to reorder page", "拖动调整页面顺序"),
                        Modifier.size(36.dp).padding(6.dp).onGloballyPositioned { pagePointerCoordinates[page.id + ":handle"] = it }.pageHandleDrag(),
                        tint = MutedInk,
                    )
                    Text(page.customNumber ?: (document.pages.indexOf(page) + 1).toString(), color = if (page.id == selectedPageId) Ink else MutedInk, fontWeight = if (page.id == selectedPageId) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(if (page.id == selectedPageId) SageContainer else Color.Transparent).clickable { onSelect(page.id) }.padding(vertical = 6.dp), maxLines = 1)
                    if (page.id == selectedPageId) {
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Workspace).clickable { menu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreVert, tr("Page menu", "页面菜单"), modifier = Modifier.size(16.dp)) }
                    } else {
                        Spacer(Modifier.size(26.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Insert page before", "在此页前插入页面")) }, onClick = { menu = false; onInsertNextTo(document.id, page.id, false) })
                        DropdownMenuItem(text = { Text(tr("Insert page after", "在此页后插入页面")) }, onClick = { menu = false; onInsertNextTo(document.id, page.id, true) })
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
            if (metadataOpen) PageMetadataDialog(page, LocalUiLanguage.current,
                onSave = { number, changed -> onMetadata(document.id, page.id, number, changed) }, onDismiss = { metadataOpen = false })
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
                onDelete = onDeleteTemplate, onCreateCustomTemplate = onCreateCustomTemplate,
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
                    LazyColumn(Modifier.dialogContentWidth(340.dp, 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    LazyColumn(Modifier.dialogContentWidth(360.dp, 560.dp).heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(transferTargets, key = { it.id }) { target ->
                            ListItem(
                                headlineContent = { Text(target.title, maxLines = 1) },
                                supportingContent = { Text(pageCount(target.pages.size), color = MutedInk) },
                                trailingContent = { Row { TextButton(onClick = { onTransfer(document.id, page.id, target.id, false); transferOpen = false }) { Text(tr("Move", "移动")) }; TextButton(onClick = { onTransfer(document.id, page.id, target.id, true); transferOpen = false }) { Text(tr("Copy", "复制")) } } },
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { transferOpen = false }) { Text(tr("Cancel", "取消")) } },
            )
        }
    }
    }
    if (currentMetadataOpen) document.pages.firstOrNull { it.id == selectedPageId }?.let { page ->
        PageMetadataDialog(page, LocalUiLanguage.current,
            onSave = { number, changed -> onMetadata(document.id, page.id, number, changed) }, onDismiss = { currentMetadataOpen = false })
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
        onDelete = onDeleteTemplate, onCreateCustomTemplate = onCreateCustomTemplate,
    )
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
        onDelete = onDeleteTemplate, onCreateCustomTemplate = onCreateCustomTemplate,
    )
    if (batchTransferOpen) AlertDialog(
        onDismissRequest = { batchTransferOpen = false },
        title = { Text(tr("Move or copy ${selectedPageIds.size} pages", "移动或复制 ${selectedPageIds.size} 页")) },
        text = { LazyColumn(Modifier.dialogContentWidth(360.dp, 560.dp).heightIn(max = 360.dp)) {
            items(transferTargets, key = { it.id }) { target -> ListItem(
                headlineContent = { Text(target.title, maxLines = 1) },
                supportingContent = { Text(pageCount(target.pages.size)) },
                trailingContent = { Row {
                    TextButton(onClick = { if (onTransferPages(document.id, selectedPageIds, target.id, false)) { batchTransferOpen = false; managingPages = false; selectedPageIds = emptySet() } }) { Text(tr("Move", "移动")) }
                    TextButton(onClick = { if (onTransferPages(document.id, selectedPageIds, target.id, true)) { batchTransferOpen = false; managingPages = false; selectedPageIds = emptySet() } }) { Text(tr("Copy", "复制")) }
                } },
            ) }
        } },
        confirmButton = { TextButton(onClick = { batchTransferOpen = false }) { Text(tr("Cancel", "取消")) } },
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
internal fun PageTemplatePickerDialog(
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
    onCreateCustomTemplate: (String, Int, Int, PageBackground.Paper) -> PageTemplate?,
    onSaveCurrent: (() -> Unit)? = null,
) {
    var customSizeOpen by remember { mutableStateOf(false) }
    var paperColorOverridden by remember(documentId) { mutableStateOf(false) }
    if (customSizeOpen) CustomPageTemplateDialog(
        language = LocalUiLanguage.current,
        base = library.defaultFor(documentId),
        onDismiss = { customSizeOpen = false },
        onCreate = onCreateCustomTemplate,
        onCreated = { customSizeOpen = false },
    )
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
            // One scroll owner: on narrow windows or with large fonts the header scrolls away with
            // the template list instead of being clipped above a nested list.
            LazyColumn(Modifier.dialogContentWidth(440.dp, 640.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        Text(
                            tr(
                                "Choose a reusable paper, image, or PDF background. Existing ink and objects are preserved.",
                                "选择可复用的纸张、图片或 PDF 背景；现有墨迹与对象会完整保留。",
                            ),
                            color = MutedInk,
                            fontSize = 12.sp,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = onImport) { Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(6.dp)); Text(tr("Import image/PDF", "导入图片/PDF")) }
                            if (onSaveCurrent != null) OutlinedButton(onClick = onSaveCurrent) { Icon(Icons.Outlined.BookmarkAdd, null); Spacer(Modifier.width(6.dp)); Text(tr("Save current", "保存当前页")) }
                        }
                        TextButton(onClick = { customSizeOpen = true }) { Icon(Icons.Outlined.Add, null); Text(tr("Custom paper", "自定义纸张")) }
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
                                        .clickable { paperColor = option.colorArgb; paperColorOverridden = true }
                                        .semantics { contentDescription = description },
                                )
                            }
                        }
                    }
                }
                items(ordered, key = { it.id }) { template ->
                    val displayedTemplate = if (PageTemplateCatalog.isBuiltIn(template.id) || paperColorOverridden) PageTemplateOperations.withPaperColor(template, paperColor) else template
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
internal fun PageTemplatePreview(template: PageTemplate, selected: Boolean) {
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
            is PageBackground.Image -> ImagePaperThumbnail(background, Modifier.fillMaxSize())
            is PageBackground.Pdf -> Text("PDF", color = Color(0xFF111111), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun paperColorName(option: PaperColorOption): String = when (option.id) {
    "white" -> tr("White", "白色")
    "warm-ivory" -> tr("Warm ivory", "暖象牙色")
    "soft-green" -> tr("Soft green", "柔和绿色")
    "soft-blue" -> tr("Soft blue", "柔和蓝色")
    "soft-gray" -> tr("Soft gray", "柔和灰色")
    else -> option.id
}

@Composable
internal fun pageTemplateName(template: PageTemplate): String {
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

internal fun AppSettings.toLayerDrawingGuide() = LayerDrawingGuide(
    symmetryMode = symmetryMode.name,
    symmetryCenterX = symmetryCenterX, symmetryCenterY = symmetryCenterY, symmetryAngleDegrees = symmetryAngleDegrees,
    rulerEnabled = rulerEnabled, rulerCenterX = rulerCenterX, rulerCenterY = rulerCenterY,
    rulerAngleDegrees = rulerAngleDegrees, rulerSnapDegrees = rulerSnapDegrees,
    gridSnapEnabled = gridSnapEnabled, gridSpacing = gridSpacing, alignmentGuidesEnabled = alignmentGuidesEnabled,
    verticalGuideX = verticalGuideX, horizontalGuideY = horizontalGuideY,
    perspectiveMode = perspectiveGuideMode.name, perspectiveAssisted = perspectiveGuideAssisted,
    perspectiveEditing = perspectiveGuideEditing, perspectivePoint1X = perspectivePoint1X,
    perspectivePoint1Y = perspectivePoint1Y, perspectivePoint2X = perspectivePoint2X,
    perspectivePoint2Y = perspectivePoint2Y, perspectivePoint3X = perspectivePoint3X, perspectivePoint3Y = perspectivePoint3Y,
    specialRulerMode = specialRulerMode.name, specialRulerAssisted = specialRulerAssisted,
    specialRulerEditing = specialRulerEditing, specialRulerCenterX = specialRulerCenterX,
    specialRulerCenterY = specialRulerCenterY, specialRulerAngleDegrees = specialRulerAngleDegrees,
    specialRulerSpacing = specialRulerSpacing,
)
