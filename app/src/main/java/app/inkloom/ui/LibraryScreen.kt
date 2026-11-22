package app.inkloom.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.inkloom.document.*
import app.inkloom.model.*
import app.inkloom.settings.LibraryLayout
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LibraryScreen(onPrepareImagePaper: suspend (Uri, String) -> app.inkloom.document.ImagePaperDraft, onSaveImagePaper: (PageTemplate) -> PageTemplate, onCreateCustomTemplate: (String, Int, Int, PageBackground.Paper) -> PageTemplate?, documents: List<InkDocument>, folders: List<InkFolder>, pageTemplates: PageTemplateLibrary, layout: LibraryLayout, onLayout: (LibraryLayout) -> Unit, onOpen: (InkDocument) -> Unit, onNewNote: (String, CoverStyle, String, Long, String?) -> Boolean, onImportCover: (Uri, String, PageCrop) -> NotebookCoverTemplate, onDeleteCover: (String) -> Unit, onChangeCover: (String, CoverStyle, String?) -> Boolean, onImport: () -> Unit, onFavorite: (String) -> Unit, onArchive: (String) -> Unit, onTags: (String, List<String>) -> Unit, onTrash: (String) -> Unit, onRestore: (String) -> Unit, onDelete: (String) -> Unit, onRename: (String, String) -> Unit, onDuplicate: (String) -> Unit, onNotebookOutput: (InkDocument, NotebookOutput) -> Unit, onCreateFolder: (String, String?) -> Unit, onRenameFolder: (String, String) -> Unit, onMoveToFolder: (String, String?) -> Unit, onMoveNotesToFolder: (Set<String>, String?) -> Boolean, onRestoreFolder: (String) -> Boolean, onDeleteFolder: (String) -> Unit, onMoveFolders: (Set<String>, String?) -> Boolean, onDeleteFolders: (Set<String>) -> Boolean, onSettings: () -> Unit) {
    val folderContext=LocalContext.current
    val activeFolders=remember(folders){folders.filter{it.trashedAt==null}}
    var compactFoldersOpen by remember{mutableStateOf(false)}

    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var search by rememberSaveable { mutableStateOf("") }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    val noteListState = rememberLazyListState()
    val noteGridState = rememberLazyGridState()
    var newNoteOpen by remember { mutableStateOf(false) }
    var newNoteCreateFailed by remember { mutableStateOf(false) }
    var managingNotes by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(emptySet<String>()) }
    var batchMoveOpen by remember { mutableStateOf(false) }
    var batchMoveError by remember { mutableStateOf(false) }
    var draggingDocumentId by remember { mutableStateOf<String?>(null) }
    var documentDragPosition by remember { mutableStateOf<Offset?>(null) }
    val documentCardBounds = remember { mutableStateMapOf<String, Rect>() }
    val folderDropBounds = remember { mutableStateMapOf<String, Rect>() }
    val libraryLanguage = LocalUiLanguage.current
    val untitledNoteLabel = tr("Untitled Note", "未命名笔记")
    var newNoteTitle by remember { mutableStateOf(untitledNoteLabel) }
    var newNoteCover by remember { mutableStateOf(CoverStyle.MINIMAL) }
    var newNoteCoverId by remember { mutableStateOf<String?>(null) }
    var coverImporting by remember { mutableStateOf(false) }
    var coverImportError by remember { mutableStateOf(false) }
    var editingCoverDocumentId by remember { mutableStateOf<String?>(null) }
    var editingCoverStyle by remember { mutableStateOf(CoverStyle.MINIMAL) }
    var editingCoverId by remember { mutableStateOf<String?>(null) }
    var coverImportTargetId by remember { mutableStateOf<String?>(null) }
    var coverCropCandidate by remember { mutableStateOf<Triple<Uri, String, String?>?>(null) }
    val coverScope = rememberCoroutineScope()
    val coverContext = LocalContext.current
    val currentCoverImport by rememberUpdatedState(onImportCover)
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = runCatching { coverContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.substringBeforeLast('.') }.getOrNull() ?: libraryLanguage.text("Cover", "封面")
            coverCropCandidate = Triple(uri, name, coverImportTargetId)
        }
    }

    var customNoteTemplateOpen by remember { mutableStateOf(false) }
    var customNoteTemplatesSelected by remember { mutableStateOf(false) }
    var newNoteTemplateId by remember { mutableStateOf(PageTemplateCatalog.blank.id) }
    var newNotePaperColor by remember { mutableLongStateOf(PaperColorCatalog.WHITE) }
    val imagePaperImporter = if (newNoteOpen) rememberImagePaperImporter(onPrepareImagePaper, onSaveImagePaper) { saved ->
        newNoteTemplateId = saved.id
        customNoteTemplatesSelected = true
    } else null
    LaunchedEffect(activeFolders,folderId){if(folderId!=null && activeFolders.none{it.id==folderId})folderId=null}
    val visible = remember(documents, filter, folderId, search) {
        LibraryDocumentQuery.visible(documents, filter, folderId, search)
    }
    LaunchedEffect(filter, folderId, search) { selectedNoteIds = emptySet() }
    // Choosing another section or folder ends search entry, so the keyboard stops covering the
    // results; the search text itself is kept.
    val libraryFocus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(filter, folderId) { libraryFocus.clearFocus() }
    fun finishDocumentDrag() {
        val documentId = draggingDocumentId
        val position = documentDragPosition
        val targetFolderId = position?.let { point -> folderDropBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(point) }?.key }
        if (documentId != null && targetFolderId != null) onMoveToFolder(documentId, targetFolderId)
        draggingDocumentId = null
        documentDragPosition = null
    }
    fun Modifier.libraryDocumentDrag(document: InkDocument): Modifier = this
        .then(if (managingNotes) Modifier else Modifier
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
        })
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val compact = maxWidth < 700.dp
        Row(Modifier.fillMaxSize()) {
            if (!compact) {
        LibraryRail(
            filter,
            { filter = it; folderId = null },
            activeFolders,
            folderId,
            { folderId = it; filter = LibraryFilter.ALL },
            onCreateFolder,
            onRenameFolder,
            onDeleteFolder,
            onMoveFolders,
            onDeleteFolders,
            onSettings,
            draggingDocument = draggingDocumentId != null,
            dragPosition = documentDragPosition,
            onFolderBounds = { id, bounds -> folderDropBounds[id] = bounds },
        )
                VerticalDivider(color = OutlineSoft)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (compact) 16.dp else 32.dp, vertical = 22.dp)) {
            if (compact) {
                TextButton({compactFoldersOpen=true},modifier=Modifier.semantics{contentDescription="Manage folders"}){Icon(Icons.Outlined.FolderOpen,null);Spacer(Modifier.width(6.dp));Text(tr("Folders","文件夹"))}
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { StudioFilterChip(selected = filter == LibraryFilter.ALL, onClick = { filter = LibraryFilter.ALL }, label = { Text(tr("Library", "资料库")) }) }
                    item { StudioFilterChip(selected = filter == LibraryFilter.RECENT, onClick = { filter = LibraryFilter.RECENT }, label = { Text(tr("Recents", "最近使用")) }) }
                    item { StudioFilterChip(selected = filter == LibraryFilter.FAVORITES, onClick = { filter = LibraryFilter.FAVORITES }, label = { Text(tr("Favorites", "收藏")) }) }
                    item { StudioFilterChip(selected = filter == LibraryFilter.ARCHIVED, onClick = { filter = LibraryFilter.ARCHIVED }, label = { Text(tr("Archive", "归档")) }) }
                    item { StudioFilterChip(selected = filter == LibraryFilter.TRASH, onClick = { filter = LibraryFilter.TRASH }, label = { Text(tr("Trash", "废纸篓")) }) }
                    item { StudioFilterChip(selected = filter == LibraryFilter.PDF, onClick = { filter = LibraryFilter.PDF }, label = { Text("PDF") }) }
                }
            }
            // Compact windows (narrow or large fonts) give the title its own line instead of
            // squeezing it beside five actions until only "You…" remains.
            val libraryActions: @Composable RowScope.() -> Unit = {
                // Wide windows keep Settings in the sidebar; only compact windows, whose sidebar is
                // folded away, need it here.
                if (compact) IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, tr("Settings", "设置")) }
                TextButton(onClick = { managingNotes = !managingNotes; selectedNoteIds = emptySet() }) {
                    Text(if (managingNotes) tr("Done", "完成") else tr("Select", "选择"))
                }
                if(compact) {
                    IconButton(onClick=onImport){Icon(Icons.Outlined.FileDownload,tr("Import","导入"))}
                    IconButton(onClick={newNoteCreateFailed=false;newNoteOpen=true}){Icon(Icons.Default.Add,tr("New Note","新建笔记"))}
                } else {
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onClick = onImport, shape = RoundedCornerShape(50)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(tr("Import", "导入")) }
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = { newNoteCreateFailed = false; newNoteOpen = true }, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("New Note", "新建笔记"))
                    }
                }
            }
            // The title is where you are (the rail item or the open folder), so no second heading repeats it.
            val placeTitle = folderId?.let { id -> activeFolders.firstOrNull { it.id == id }?.name } ?: when (filter) {
                LibraryFilter.ALL -> tr("Library", "资料库")
                LibraryFilter.RECENT -> tr("Recents", "最近使用")
                LibraryFilter.FAVORITES -> tr("Favorites", "收藏")
                LibraryFilter.ARCHIVED -> tr("Archive", "归档")
                LibraryFilter.TRASH -> tr("Trash", "废纸篓")
                LibraryFilter.PDF -> "PDF"
            }
            if (compact) {
                Text(placeTitle, Modifier.semantics { heading() }, fontSize = 22.sp, color = Ink)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = libraryActions)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text(placeTitle, Modifier.weight(1f).semantics { heading() }, fontSize = 32.sp, maxLines=1, overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = Ink)
                libraryActions()
            }
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudioOutlinedTextField(value = search, onValueChange = { search = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(tr("Search notes", "搜索笔记")) }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                listOf(
                    Triple(LibraryLayout.GRID, Icons.Outlined.GridView, tr("Grid layout", "网格布局")),
                    Triple(LibraryLayout.LIST, Icons.Outlined.ViewList, tr("List layout", "列表布局")),
                    Triple(LibraryLayout.COMPACT_GRID, Icons.Outlined.Apps, tr("Compact grid", "紧凑网格")),
                ).forEach { (candidate, icon, description) ->
                    IconButton(
                        onClick = { onLayout(candidate) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (layout == candidate) SageContainer else Color.Transparent, RoundedCornerShape(8.dp))
                            .semantics { selected = layout == candidate },
                    ) { Icon(icon, description, Modifier.size(19.dp), tint = if (layout == candidate) Ink else MutedInk) }
                }
            }
            Spacer(Modifier.height(12.dp))
            if(filter==LibraryFilter.TRASH) {
                val trashedRoots=folders.filter{f->f.trashedAt!=null && folders.firstOrNull{it.id==f.parentId}?.trashBatchId!=f.trashBatchId}
                if(trashedRoots.isNotEmpty()) {
                    Text(tr("Folders in Trash","废纸篓中的文件夹"),style=MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max=180.dp)){
                        items(trashedRoots,key={it.id}){f->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Folder,null);Text(f.name,Modifier.weight(1f).padding(horizontal=8.dp),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            TextButton({if(!onRestoreFolder(f.id))Toast.makeText(folderContext,libraryLanguage.text("Could not restore folder","无法恢复文件夹"),Toast.LENGTH_LONG).show()},modifier=Modifier.semantics{contentDescription="Restore folder ${f.name}"}){Text(tr("Restore","恢复"))}
                        }}
                    }
                }
            }
            if (managingNotes) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("${selectedNoteIds.size} selected", "已选择 ${selectedNoteIds.size} 本"), Modifier.weight(1f))
                    TextButton(onClick = { selectedNoteIds = visible.mapTo(linkedSetOf(), InkDocument::id) }) { Text(tr("All", "全选")) }
                    TextButton(enabled = selectedNoteIds.isNotEmpty() && filter != LibraryFilter.TRASH, onClick = { batchMoveError = false; batchMoveOpen = true }) { Text(tr("Move", "移动")) }
                    TextButton(enabled = selectedNoteIds.isNotEmpty(), modifier = Modifier.semantics { contentDescription = if (filter == LibraryFilter.TRASH) "Restore selected notebooks" else "Trash selected notebooks" }, onClick = {
                        selectedNoteIds.forEach { id ->
                            if (filter == LibraryFilter.TRASH) onRestore(id) else onTrash(id)
                        }
                        selectedNoteIds = emptySet(); managingNotes = false
                    }) { Text(if (filter == LibraryFilter.TRASH) tr("Restore", "恢复") else tr("Trash", "废纸篓")) }
                }
            }
            if (visible.isEmpty()) LibraryEmptyState(libraryEmptyReason(filter, folderId != null, search.isNotBlank()), search)
            else when (layout) {
                LibraryLayout.LIST -> LazyColumn(state = noteListState, modifier = Modifier.semantics { contentDescription = "Notebook list" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.id }) { NotebookCard(it, Modifier.libraryDocumentDrag(it).fillMaxWidth().height(104.dp), { note -> if (managingNotes) selectedNoteIds = if (note.id in selectedNoteIds) selectedNoteIds - note.id else selectedNoteIds + note.id else onOpen(note) }, onFavorite, onArchive, onTags, onTrash, onRestore, onDelete, onRename, onDuplicate, onNotebookOutput, activeFolders, onMoveToFolder, selectionMode = managingNotes, selected = it.id in selectedNoteIds, row = true, onEditCover = { document ->
                        editingCoverDocumentId = document.id
                        editingCoverStyle = document.coverStyle
                        editingCoverId = pageTemplates.customCovers.firstOrNull { it.assetPath == document.coverImagePath }?.id
                        coverImportError = false
                    }) }
                }
                LibraryLayout.GRID, LibraryLayout.COMPACT_GRID -> LazyVerticalGrid(
                    state = noteGridState,
                    modifier = Modifier.semantics { contentDescription = "Notebook grid" },
                    columns = GridCells.Adaptive(if (layout == LibraryLayout.COMPACT_GRID) 140.dp else 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (layout == LibraryLayout.COMPACT_GRID) 10.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (layout == LibraryLayout.COMPACT_GRID) 10.dp else 16.dp),
                ) {
                    items(visible, key = { it.id }) {
                        NotebookCard(it, Modifier.libraryDocumentDrag(it).height(if (layout == LibraryLayout.COMPACT_GRID) 210.dp else 250.dp), { note -> if (managingNotes) selectedNoteIds = if (note.id in selectedNoteIds) selectedNoteIds - note.id else selectedNoteIds + note.id else onOpen(note) }, onFavorite, onArchive, onTags, onTrash, onRestore, onDelete, onRename, onDuplicate, onNotebookOutput, activeFolders, onMoveToFolder, selectionMode = managingNotes, selected = it.id in selectedNoteIds, compact = layout == LibraryLayout.COMPACT_GRID, onEditCover = { document ->
                            editingCoverDocumentId = document.id
                            editingCoverStyle = document.coverStyle
                            editingCoverId = pageTemplates.customCovers.firstOrNull { it.assetPath == document.coverImagePath }?.id
                            coverImportError = false
                        })
                    }
                }
            }
        }
        }
    }
    if(compactFoldersOpen) androidx.compose.ui.window.Dialog(onDismissRequest={compactFoldersOpen=false}) {
        Surface(Modifier.widthIn(min=280.dp,max=420.dp).fillMaxHeight(.9f),shape=RoundedCornerShape(16.dp)) {
            Column {
                TextButton({compactFoldersOpen=false},Modifier.align(Alignment.End)){Text(tr("Done","完成"))}
                LibraryRail(filter,{filter=it;folderId=null;compactFoldersOpen=false},activeFolders,folderId,
                    {folderId=it;filter=LibraryFilter.ALL;compactFoldersOpen=false},onCreateFolder,onRenameFolder,onDeleteFolder,onMoveFolders,onDeleteFolders,
                    {compactFoldersOpen=false;onSettings()},false,null,{_,_->},Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
    if (batchMoveOpen) StudioDialog(
        onDismissRequest = { batchMoveOpen = false },
        title = { Text(tr("Move selected notebooks", "移动所选笔记本")) },
        text = { Column {
          if (batchMoveError) Text(tr("The destination is unavailable; no notebooks were moved.", "目标文件夹不可用；笔记本未移动。"), color = MaterialTheme.colorScheme.error)
          LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { ListItem(headlineContent = { Text(tr("Library root", "资料库根目录")) }, modifier = Modifier.clickable {
                if (onMoveNotesToFolder(selectedNoteIds, null)) { selectedNoteIds = emptySet(); managingNotes = false; batchMoveOpen = false } else batchMoveError = true
            }) }
            items(activeFolders, key = { it.id }) { folder -> ListItem(headlineContent = { Text(folder.name) }, modifier = Modifier.clickable {
                if (onMoveNotesToFolder(selectedNoteIds, folder.id)) { selectedNoteIds = emptySet(); managingNotes = false; batchMoveOpen = false } else batchMoveError = true
            }) }
          }
        } },
        confirmButton = { TextButton(onClick = { batchMoveOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (customNoteTemplateOpen) CustomPageTemplateDialog(
        language = LocalUiLanguage.current,
        base = pageTemplates.template(newNoteTemplateId) ?: PageTemplateCatalog.blank,
        onDismiss = { customNoteTemplateOpen = false },
        onCreate = onCreateCustomTemplate,
        onCreated = {
            newNoteTemplateId = it.id
            newNotePaperColor = (it.background as? PageBackground.Paper)?.colorArgb ?: PaperColorCatalog.WHITE
            customNoteTemplatesSelected = true; customNoteTemplateOpen = false
        },
    )
    if (newNoteOpen && imagePaperImporter?.previewOpen != true) Dialog(
        onDismissRequest = { newNoteOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val creationFocus = remember { FocusRequester() }
        val nameKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val finishName = { creationFocus.requestFocus(); nameKeyboard?.hide(); Unit }
        // A keyboard session returning from the system picker must not refocus the title
        // in a subsequently opened notebook dialog. Typing begins when the user taps it.
        LaunchedEffect(Unit) { finishName() }
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
        StudioDialogFrame(Modifier.width(StudioDialogDefaults.width(wide = true)).heightIn(max = StudioDialogDefaults.maxHeight()),
            title = { Text(tr("Create notebook", "新建笔记本")) },
            footer = {
                TextButton(onClick = { finishName(); newNoteOpen = false }) { Text(tr("Cancel", "取消")) }
                Spacer(Modifier.width(4.dp))
                TextButton(enabled = !coverImporting, onClick = {
                    finishName()
                    if (onNewNote(newNoteTitle, newNoteCover, newNoteTemplateId, newNotePaperColor, newNoteCoverId)) newNoteOpen = false
                    else newNoteCreateFailed = true
                }) { Text(tr("Create", "创建")) }
            }) {
        Column(Modifier.focusRequester(creationFocus).focusable(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StudioOutlinedTextField(newNoteTitle, { newNoteTitle = it.take(120); newNoteCreateFailed = false }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { finishName() }))
                NotebookCoverPicker(newNoteTitle, pageTemplates.customCovers, newNoteCover, newNoteCoverId, coverImporting, LocalUiLanguage.current,
                    onSelect = { style, id -> finishName(); newNoteCover = style; newNoteCoverId = id },
                    onImport = { finishName(); coverImportTargetId = null; coverPicker.launch(arrayOf("image/*")) },
                    onBrowse = finishName,
                    onDelete = { id ->
                        onDeleteCover(id)
                        if (newNoteCoverId == id) { newNoteCoverId = null; newNoteCover = CoverStyle.MINIMAL }
                    })
                if (coverImportError) Text(tr("Could not import this cover image", "无法导入此封面图片"), color = Ink, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Page template", "页面模板"), Modifier.weight(1f), color = MutedInk, fontSize = 12.sp)
                    TextButton(onClick = { finishName(); customNoteTemplateOpen = true }) { Text(tr("Custom paper", "自定义纸张")) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioFilterChip(!customNoteTemplatesSelected, { finishName(); customNoteTemplatesSelected = false }, label = { Text(tr("Presets", "预设")) })
                    StudioFilterChip(customNoteTemplatesSelected, { finishName(); customNoteTemplatesSelected = true }, label = { Text(tr("My templates", "我的模板")) })
                }
                if (customNoteTemplatesSelected && imagePaperImporter != null) {
                    TextButton(enabled = !imagePaperImporter.loading, onClick = { finishName(); imagePaperImporter.launch() }) {
                        Text(if (imagePaperImporter.loading) tr("Importing…", "正在导入…") else tr("Import paper image", "导入纸张图片"))
                    }
                    if (imagePaperImporter.failed) Text(tr("Could not import this paper image", "无法导入纸张图片，请检查格式与尺寸"), color = MaterialTheme.colorScheme.error)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(if (customNoteTemplatesSelected) pageTemplates.customTemplates else PageTemplateCatalog.builtIns, key = { it.id }) { template ->
                        val selected = template.id == newNoteTemplateId
                        val name = pageTemplateName(template)
                        val templateDescription = tr("New note template $name", "新笔记模板 $name")
                        Column(
                            Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(if (selected) 2.dp else 1.dp, if (selected) Ink else OutlineSoft, RoundedCornerShape(8.dp))
                                .clickable {
                                    newNoteTemplateId = template.id
                                    if (!PageTemplateCatalog.isBuiltIn(template.id)) newNotePaperColor = (template.background as? PageBackground.Paper)?.colorArgb ?: PaperColorCatalog.WHITE
                                }
                                .semantics { contentDescription = templateDescription }
                                .padding(7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PageTemplatePreview(if (selected || PageTemplateCatalog.isBuiltIn(template.id)) PageTemplateOperations.withPaperColor(template, newNotePaperColor) else template, selected)
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

            }
            if (newNoteCreateFailed) Text(tr("Could not create this notebook. Check the selected cover and try again.", "无法创建笔记，请检查所选封面后重试。"), style = MaterialTheme.typography.bodySmall)
        }
        }
        }
    }
    editingCoverDocumentId?.let { documentId ->
        val document = documents.firstOrNull { it.id == documentId }
        if (document != null && coverCropCandidate == null) StudioDialog(
            onDismissRequest = { if (!coverImporting) editingCoverDocumentId = null },
            title = { Text(tr("Change cover", "更换封面")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotebookCoverPicker(document.title, pageTemplates.customCovers, editingCoverStyle, editingCoverId,
                        coverImporting, LocalUiLanguage.current,
                        onSelect = { style, id -> editingCoverStyle = style; editingCoverId = id },
                        onImport = { coverImportTargetId = documentId; coverPicker.launch(arrayOf("image/*")) },
                        onBrowse = {},
                        onDelete = { id ->
                            onDeleteCover(id)
                            if (editingCoverId == id) { editingCoverId = null; editingCoverStyle = CoverStyle.MINIMAL }
                        })
                    if (coverImportError) Text(tr("Could not save this cover", "无法保存此封面"), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(enabled = !coverImporting, onClick = {
                if (onChangeCover(documentId, editingCoverStyle, editingCoverId)) editingCoverDocumentId = null
                else coverImportError = true
            }) { Text(tr("Apply", "应用")) } },
            dismissButton = { TextButton(enabled = !coverImporting, onClick = { editingCoverDocumentId = null }) { Text(tr("Cancel", "取消")) } },
        )
    }
    coverCropCandidate?.let { candidate ->
        NotebookCoverCropDialog(candidate.first, LocalUiLanguage.current,
            onDismiss = { coverCropCandidate = null },
            onConfirm = { crop ->
                coverCropCandidate = null
                coverImporting = true; coverImportError = false
                coverScope.launch {
                    try {
                        val cover = withContext(Dispatchers.IO) { currentCoverImport(candidate.first, candidate.second, crop) }
                        val targetDocumentId = candidate.third
                        if (targetDocumentId == null) {
                            newNoteCover = CoverStyle.CUSTOM_IMAGE; newNoteCoverId = cover.id
                        } else {
                            editingCoverStyle = CoverStyle.CUSTOM_IMAGE; editingCoverId = cover.id
                            if (onChangeCover(targetDocumentId, CoverStyle.CUSTOM_IMAGE, cover.id)) editingCoverDocumentId = null
                            else coverImportError = true
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { coverImportError = true }
                    finally { coverImporting = false }
                }
            })
    }
}

@Composable
private fun LibraryRail(filter: LibraryFilter, onFilter: (LibraryFilter) -> Unit, folders: List<InkFolder>, folderId: String?, onFolder: (String?) -> Unit, onCreateFolder: (String, String?) -> Unit, onRenameFolder: (String, String) -> Unit, onDeleteFolder: (String) -> Unit, onMoveFolders: (Set<String>, String?) -> Boolean, onDeleteFolders: (Set<String>) -> Boolean, onSettings: () -> Unit, draggingDocument: Boolean, dragPosition: Offset?, onFolderBounds: (String, Rect) -> Unit, modifier:Modifier=Modifier.width(198.dp)) {
    var newFolderOpen by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var folderMenuId by remember { mutableStateOf<String?>(null) }
    var renameFolderId by remember { mutableStateOf<String?>(null) }
    var renameFolderName by remember { mutableStateOf("") }
    var managingFolders by remember { mutableStateOf(false) }
    var selectedFolderIds by remember { mutableStateOf(emptySet<String>()) }
    var moveFoldersOpen by remember { mutableStateOf(false) }
    var deleteFoldersOpen by remember { mutableStateOf(false) }
    var folderActionError by remember { mutableStateOf(false) }
    val folderRows = remember(folders) { libraryFolderRows(folders) }
    LaunchedEffect(folders) { selectedFolderIds = selectedFolderIds.intersect(folders.mapTo(hashSetOf(), InkFolder::id)) }
    val language = LocalUiLanguage.current
    Column(modifier.fillMaxHeight().padding(16.dp)) {
        BrandMark()
        Spacer(Modifier.height(34.dp))
        RailItem(Icons.Outlined.Book, tr("Library", "资料库"), filter == LibraryFilter.ALL) { onFilter(LibraryFilter.ALL) }
        RailItem(Icons.Outlined.History, tr("Recents", "最近使用"), filter == LibraryFilter.RECENT) { onFilter(LibraryFilter.RECENT) }
        RailItem(Icons.Outlined.StarBorder, tr("Favorites", "收藏"), filter == LibraryFilter.FAVORITES) { onFilter(LibraryFilter.FAVORITES) }
        RailItem(Icons.Outlined.PictureAsPdf, "PDF", filter == LibraryFilter.PDF) { onFilter(LibraryFilter.PDF) }
        RailItem(Icons.Outlined.Archive, tr("Archive", "归档"), filter == LibraryFilter.ARCHIVED) { onFilter(LibraryFilter.ARCHIVED) }
        RailItem(Icons.Outlined.Delete, tr("Trash", "废纸篓"), filter == LibraryFilter.TRASH) { onFilter(LibraryFilter.TRASH) }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = OutlineSoft)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("FOLDERS", "文件夹"), fontSize = 11.sp, color = MutedInk, modifier = Modifier.weight(1f))
            TextButton(onClick = { managingFolders = !managingFolders; selectedFolderIds = emptySet() }, modifier = Modifier.height(30.dp).semantics { contentDescription = if (managingFolders) "Finish folder selection" else "Select folders" }) {
                Text(if (managingFolders) tr("Done", "完成") else tr("Select", "选择"), fontSize = 10.sp)
            }
            IconButton(onClick = { newFolderOpen = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Add, tr("New folder", "新建文件夹")) }
        }
        if (managingFolders) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${selectedFolderIds.size}", Modifier.weight(1f), color = MutedInk, fontSize = 11.sp)
            TextButton(enabled = selectedFolderIds.isNotEmpty(), onClick = { folderActionError = false; moveFoldersOpen = true }) { Text(tr("Move", "移动"), fontSize = 10.sp) }
            TextButton(enabled = selectedFolderIds.isNotEmpty(), modifier = Modifier.semantics { contentDescription = "Delete selected folders" }, onClick = { folderActionError = false; deleteFoldersOpen = true }) { Text(tr("Delete", "删除"), fontSize = 10.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            folderRows.forEach { (folder, depth) -> key(folder.id) {
                Row(Modifier.padding(start = (depth * 12).coerceAtMost(48).dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        var bounds by remember(folder.id) { mutableStateOf<Rect?>(null) }
                        // A folder row that leaves the rail (collapsed or deleted) must stop accepting drops.
                        DisposableEffect(folder.id) { onDispose { onFolderBounds(folder.id, Rect.Zero) } }
                        val hovered = draggingDocument && dragPosition?.let { bounds?.contains(it) } == true
                        val selected = folder.id in selectedFolderIds
                        RailItem(
                            if (depth == 0) Icons.Outlined.Folder else Icons.Outlined.FolderOpen,
                            folder.name,
                            selected || folderId == folder.id || hovered,
                            modifier = Modifier
                                .onGloballyPositioned { coordinates -> coordinates.boundsInWindow().also { measured -> bounds = measured; onFolderBounds(folder.id, measured) } }
                                .semantics { contentDescription = if (managingFolders) language.text("Select folder ${folder.name}", "选择文件夹 ${folder.name}") else language.text("Folder ${folder.name}. Drop notebook here.", "文件夹 ${folder.name}。拖放笔记到这里。") },
                        ) {
                            if (managingFolders) selectedFolderIds = if (selected) selectedFolderIds - folder.id else selectedFolderIds + folder.id
                            else onFolder(folder.id)
                        }
                    }
                    if (!managingFolders) Box {
                        IconButton(onClick = { folderMenuId = folder.id }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.MoreVert, tr("Folder actions", "文件夹操作"), modifier = Modifier.size(14.dp)) }
                        DropdownMenu(expanded = folderMenuId == folder.id, onDismissRequest = { folderMenuId = null }) {
                            StudioMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { folderMenuId = null; renameFolderId = folder.id; renameFolderName = folder.name })
                            StudioMenuItem(text = { Text(tr("Delete folder", "删除文件夹")) }, onClick = { folderMenuId=null;selectedFolderIds=setOf(folder.id);deleteFoldersOpen=true })
                        }
                    }
                }
            } }
        }
        RailItem(Icons.Outlined.Settings, tr("Settings", "设置"), false, onClick = onSettings)
    }
    if (moveFoldersOpen) StudioDialog(
        onDismissRequest = { moveFoldersOpen = false },
        title = { Text(tr("Move selected folders", "移动所选文件夹")) },
        text = { Column {
            if (folderActionError) Text(tr("This destination is unavailable; no folders were moved.", "目标位置不可用；文件夹均未移动。"), color = MaterialTheme.colorScheme.error)
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                val targets = listOf(null) + folderRows.map { it.first.id }
                targets.forEach { targetId ->
                    val target = folders.firstOrNull { it.id == targetId }
                    val allowed = LibraryFolderOperations.moveFolders(folders, selectedFolderIds, targetId) != null
                    TextButton(enabled = allowed, modifier = Modifier.semantics { contentDescription = "Move folders to ${target?.name ?: "Library root"}" }, onClick = {
                        if (onMoveFolders(selectedFolderIds, targetId)) { moveFoldersOpen = false; managingFolders = false; selectedFolderIds = emptySet() }
                        else folderActionError = true
                    }) { Text(target?.name ?: tr("Library root", "资料库根目录")) }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { moveFoldersOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (deleteFoldersOpen) StudioDialog(
        onDismissRequest = { deleteFoldersOpen = false },
        title = { Text(tr("Delete selected folders?", "删除所选文件夹？")) },
        text = { Column {
            Text(tr("Their notebooks move to Trash too.", "其中的笔记本也会移入废纸篓。"))
            if (folderActionError) Text(tr("Folders could not be deleted; nothing was changed.", "删除失败；未更改文件夹。"), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(modifier = Modifier.semantics { contentDescription = "Confirm delete selected folders" }, onClick = {
            if (onDeleteFolders(selectedFolderIds)) { deleteFoldersOpen = false; managingFolders = false; selectedFolderIds = emptySet() }
            else folderActionError = true
        }) { Text(tr("Delete", "删除")) } },
        dismissButton = { TextButton(onClick = { deleteFoldersOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (newFolderOpen) StudioDialog(onDismissRequest = { newFolderOpen = false }, title = { Text(tr("New folder", "新建文件夹")) }, text = { StudioOutlinedTextField(value = folderName, onValueChange = { folderName = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onCreateFolder(folderName, folderId); folderName = ""; newFolderOpen = false }) { Text(tr("Create", "创建")) } }, dismissButton = { TextButton(onClick = { newFolderOpen = false }) { Text(tr("Cancel", "取消")) } })
    if (renameFolderId != null) StudioDialog(
        onDismissRequest = { renameFolderId = null },
        title = { Text(tr("Rename folder", "重命名文件夹")) },
        text = { StudioOutlinedTextField(renameFolderName, { renameFolderName = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { renameFolderId?.let { onRenameFolder(it, renameFolderName) }; renameFolderId = null }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = { renameFolderId = null }) { Text(tr("Cancel", "取消")) } },
    )
}

private fun libraryFolderRows(folders: List<InkFolder>): List<Pair<InkFolder, Int>> {
    val ids = folders.mapTo(hashSetOf(), InkFolder::id)
    val children = folders.groupBy(InkFolder::parentId)
    val visited = hashSetOf<String>()
    val rows = mutableListOf<Pair<InkFolder, Int>>()
    fun visit(folder: InkFolder, depth: Int) {
        if (!visited.add(folder.id)) return
        rows += folder to depth
        children[folder.id].orEmpty().forEach { visit(it, depth + 1) }
    }
    folders.filter { it.parentId == null || it.parentId !in ids }.forEach { visit(it, 0) }
    folders.forEach { visit(it, 0) } // Orphan/cyclic legacy entries remain accessible for repair.
    return rows
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) SageContainer else Color.Transparent).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) Ink else MutedInk); Spacer(Modifier.width(12.dp)); Text(label, color = if (selected) Ink else MutedInk)
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Sage, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, tint = Color.White) } }
        Spacer(Modifier.width(12.dp))
        Text("Inkloom", color = MutedInk, fontSize = 25.sp)
    }
}

@Composable
private fun NotebookCard(document: InkDocument, modifier: Modifier, onOpen: (InkDocument) -> Unit, onFavorite: (String) -> Unit, onArchive: (String) -> Unit, onTags: (String, List<String>) -> Unit, onTrash: (String) -> Unit, onRestore: (String) -> Unit, onDelete: (String) -> Unit, onRename: (String, String) -> Unit, onDuplicate: (String) -> Unit, onOutput: (InkDocument, NotebookOutput) -> Unit, folders: List<InkFolder>, onMoveToFolder: (String, String?) -> Unit, selectionMode: Boolean = false, selected: Boolean = false, row: Boolean = false, compact: Boolean = false, onEditCover: (InkDocument) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(false) }
    var title by remember(document.title) { mutableStateOf(document.title) }
    var tags by remember(document.tags) { mutableStateOf(document.tags.joinToString(", ")) }
    // Shared by the cover card and the list row so both expose the same actions.
    @Composable
    fun Actions(modifier: Modifier, overlay: Boolean) {
        if (selectionMode) Surface(modifier.padding(8.dp), shape = CircleShape, color = if (selected) Ink else Paper, border = if (overlay) null else androidx.compose.foundation.BorderStroke(1.dp, OutlineSoft)) {
            Icon(if (selected) Icons.Outlined.Check else Icons.Outlined.RadioButtonUnchecked, tr("Select notebook", "选择笔记本"), Modifier.padding(4.dp), tint = if (selected) Paper else Ink)
        } else Box(modifier) {
            IconButton(
                onClick = { menu = true },
                modifier = Modifier.padding(4.dp),
            ) {
                // The scrim sits inside the 48 dp touch target so it stays clear of the card corner.
                Box(Modifier.size(if (compact) 28.dp else 32.dp).then(if (overlay) Modifier.background(Color.Black.copy(alpha = .48f), CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MoreVert, tr("Document actions", "笔记操作"), Modifier.size(20.dp), tint = if (overlay) Color.White else MutedInk)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (document.trashedAt == null) {
                    StudioMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { menu = false; renameOpen = true })
                    StudioMenuItem(text = { Text(tr("Change cover", "更换封面")) }, onClick = { menu = false; onEditCover(document) })
                    StudioMenuItem(text = { Text(tr("Edit tags", "编辑标签")) }, onClick = { menu = false; tagsOpen = true })
                    StudioMenuItem(text = { Text(tr("Duplicate", "复制")) }, onClick = { menu = false; onDuplicate(document.id) })
                    StudioMenuItem(text = { Text(tr("Share PDF…", "分享 PDF…")) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menu = false; onOutput(document, NotebookOutput.SHARE_PDF) })
                    StudioMenuItem(text = { Text(tr("Export PDF…", "导出 PDF…")) }, onClick = { menu = false; onOutput(document, NotebookOutput.EXPORT_PDF) })
                    StudioMenuItem(text = { Text(tr("Export Inkloom package…", "导出 Inkloom 文件…")) }, onClick = { menu = false; onOutput(document, NotebookOutput.EXPORT_PACKAGE) })
                    StudioMenuItem(text = { Text(tr("3D book preview", "3D 书本预览")) }, onClick = { menu = false; onOutput(document, NotebookOutput.BOOK_PREVIEW) })
                    folders.forEach { folder -> StudioMenuItem(text = { Text("${tr("Move to", "移动到")} ${folder.name}") }, onClick = { menu = false; onMoveToFolder(document.id, folder.id) }) }
                    if (document.folderId != null) StudioMenuItem(text = { Text(tr("Remove from folder", "移出文件夹")) }, onClick = { menu = false; onMoveToFolder(document.id, null) })
                    StudioMenuItem(text = { Text(if (document.favorite) tr("Remove favorite", "取消收藏") else tr("Favorite", "收藏")) }, onClick = { menu = false; onFavorite(document.id) })
                    StudioMenuItem(text = { Text(if (document.archivedAt == null) tr("Archive", "归档") else tr("Unarchive", "取消归档")) }, onClick = { menu = false; onArchive(document.id) })
                    StudioMenuItem(text = { Text(tr("Move to Trash", "移到废纸篓")) }, onClick = { menu = false; onTrash(document.id) })
                } else {
                    StudioMenuItem(text = { Text(tr("Restore", "恢复")) }, onClick = { menu = false; onRestore(document.id) })
                    StudioMenuItem(text = { Text(tr("Delete permanently", "永久删除")) }, onClick = { menu = false; onDelete(document.id) })
                }
            }
        }
    }
    val pdf = document.pages.any { it.background is PageBackground.Pdf }
    val cardModifier = modifier.then(if (selected) Modifier.border(2.dp, Ink, RoundedCornerShape(12.dp)) else Modifier).clickable { onOpen(document) }
    if (row) Card(cardModifier, colors = CardDefaults.cardColors(containerColor = Paper), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NotebookCover(document, Modifier.fillMaxHeight().aspectRatio(.75f).clip(RoundedCornerShape(6.dp)).border(1.dp, OutlineSoft, RoundedCornerShape(6.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(document.title, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (document.favorite) Icon(Icons.Default.Star, null, tint = Ink, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    if (pdf) Surface(Modifier.padding(start = 8.dp), shape = RoundedCornerShape(4.dp), color = Workspace) { Text("PDF", Modifier.padding(6.dp, 2.dp), fontSize = 10.sp) }
                }
                Spacer(Modifier.height(4.dp)); Text(pageCount(document.pages.size), color = MutedInk, fontSize = 12.sp)
                if (document.tags.isNotEmpty()) Text(document.tags.take(3).joinToString("  ") { "#$it" }, color = MutedInk, fontSize = 11.sp, maxLines = 1)
            }
            Actions(Modifier, overlay = false)
        }
    } else Card(cardModifier, colors = CardDefaults.cardColors(containerColor = Paper), border = CardDefaults.outlinedCardBorder()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            NotebookCover(document, Modifier.fillMaxSize())
            if (pdf) Surface(Modifier.padding(12.dp), shape = RoundedCornerShape(4.dp), color = Paper) { Text("PDF", Modifier.padding(7.dp, 4.dp), fontSize = 10.sp) }
            Actions(Modifier.align(Alignment.TopEnd), overlay = true)
        }
        // Every card reserves the same title height so neighbouring covers line up.
        Column(Modifier.padding(if (compact) 10.dp else 14.dp)) {
            Row { Text(document.title, fontSize = if (compact) 15.sp else 17.sp, maxLines = if (compact) 1 else 2, minLines = if (compact) 1 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); if (document.favorite) Icon(Icons.Default.Star, null, tint = Ink, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(if (compact) 4.dp else 6.dp)); Text(pageCount(document.pages.size), color = MutedInk, fontSize = 11.sp)
            if (document.tags.isNotEmpty()) Text(document.tags.take(3).joinToString("  ") { "#$it" }, color = MutedInk, fontSize = 10.sp, maxLines = 1)
        }
    }
    if (renameOpen) StudioDialog(onDismissRequest = { renameOpen = false }, title = { Text(tr("Rename note", "重命名笔记")) }, text = { StudioOutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onRename(document.id, title); renameOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(tr("Cancel", "取消")) } })
    if (tagsOpen) StudioDialog(onDismissRequest = { tagsOpen = false }, title = { Text(tr("Note tags", "笔记标签")) }, text = { StudioOutlinedTextField(value = tags, onValueChange = { tags = it }, supportingText = { Text(tr("Separate tags with commas", "使用逗号分隔标签")) }) }, confirmButton = { TextButton(onClick = { onTags(document.id, tags.split(',', '，')); tagsOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { tagsOpen = false }) { Text(tr("Cancel", "取消")) } })
}

@Composable
internal fun NotebookCover(document: InkDocument, modifier: Modifier = Modifier) {
    val coverBitmapState = produceState<android.graphics.Bitmap?>(null, document.coverImagePath) {
        value = document.coverImagePath?.let { path -> withContext(Dispatchers.IO) {
            runCatching { CoverImageDecoder.decode(java.io.File(path)) }.getOrNull()
        } }
    }
    val coverBitmap = coverBitmapState.value
    // Rendering may replay one more frame after this card leaves composition.
    // Previews are bounded to 512 px; allow GC to release them after that frame.
    val light = document.coverStyle in setOf(CoverStyle.MINIMAL, CoverStyle.PAPER, CoverStyle.GRID, CoverStyle.WHITE, CoverStyle.WHITE_GRID)
    Box(modifier.background(if (light) Color(0xFFF4F4F4) else Color(document.coverArgb))) {
        if (document.coverStyle == CoverStyle.CUSTOM_IMAGE && coverBitmap != null) {
            Image(requireNotNull(coverBitmap).asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
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
