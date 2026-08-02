package app.inkloom.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import app.inkloom.document.*
import app.inkloom.importer.BrushImportRepository
import app.inkloom.importer.BrushGroupOperations
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.importer.BrushVersion
import app.inkloom.importer.BrushVersionHistoryStore
import app.inkloom.model.*
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.ColorPaletteStore
import app.inkloom.settings.FloatingToolbarPlacement
import app.inkloom.settings.SelectionFilter
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.ToolbarDock
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class AppDestination { LIBRARY, EDITOR }
private const val UNGROUPED_BRUSH_FILTER = "\u0000ungrouped"

private fun List<ImportedBrush>.distinctImportedBrushes(): List<ImportedBrush> =
    associateBy { brush ->
        val sourceKey = brush.sha256 ?: "${brush.name}:${brush.sizeBytes}"
        "$sourceKey:${brush.tipPreviewPath.orEmpty()}"
    }.values.toList()

@Composable
fun InkloomApp(initialIntent: Intent?) {
    var destination by remember { mutableStateOf(AppDestination.LIBRARY) }
    var brushesOpen by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val viewModel: InkloomViewModel = viewModel()
    val libraryState by viewModel.state.collectAsState()
    val repository = remember { BrushImportRepository(context) }
    var pendingBrushExport by remember { mutableStateOf<ImportedBrush?>(null) }
    val brushStore = remember { BrushMetadataStore(context) }
    val settingsStore = remember { AppSettingsStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    fun updateSettings(value: AppSettings) { settings = value; settingsStore.save(value) }
    var importedBrushes by remember { mutableStateOf(brushStore.load()) }

    LaunchedEffect(Unit) {
        val refreshed = withContext(Dispatchers.IO) { importedBrushes.flatMap(repository::refreshMetadataAll) }.distinctImportedBrushes()
        if (refreshed != importedBrushes) { importedBrushes = refreshed; brushStore.save(refreshed) }
    }

    val brushPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val results = uris.map { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            repository.importAll(uri)
        }
        importedBrushes = (importedBrushes + results.flatMap { it.getOrNull().orEmpty() }).distinctImportedBrushes()
        brushStore.save(importedBrushes)
        importError = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Imported document"
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
                    else -> repository.importAll(uri).getOrNull()?.let { brushes -> importedBrushes = (importedBrushes + brushes).distinctImportedBrushes() }
                }
            }
            brushStore.save(importedBrushes)
            brushesOpen = true
            destination = AppDestination.EDITOR
        }
    }

    when (destination) {
        AppDestination.LIBRARY -> LibraryScreen(
            documents = libraryState.documents,
            folders = libraryState.folders,
            onOpen = { viewModel.select(it.id); destination = AppDestination.EDITOR },
            onNewNote = { viewModel.createNote(); destination = AppDestination.EDITOR },
            onImport = { documentPicker.launch(arrayOf("application/pdf", "image/*", "application/zip")) },
            onFavorite = viewModel::toggleFavorite,
            onArchive = viewModel::toggleArchive,
            onTags = viewModel::setDocumentTags,
            onTrash = viewModel::trash,
            onRestore = viewModel::restore,
            onDelete = viewModel::deletePermanently,
            onRename = viewModel::rename,
            onDuplicate = viewModel::duplicateDocument,
            onCreateFolder = viewModel::createFolder,
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
            importError = importError,
            onBack = { destination = AppDestination.LIBRARY },
            onToggleBrushes = { brushesOpen = !brushesOpen },
            onImportBrushes = { brushPicker.launch(arrayOf("application/octet-stream", "application/zip", "application/vnd.inkloom.brush+zip", "image/*")) },
            onExportBrush = { brush ->
                pendingBrushExport = brush
                val safeName = brush.name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { "Inkloom-Brush" }
                brushExportPicker.launch("$safeName.inkbrush")
            },
            onUpdateBrush = { updated -> importedBrushes = importedBrushes.map { if (it.id == updated.id) updated else it }; brushStore.save(importedBrushes) },
            onDeleteBrush = { brushId -> importedBrushes = importedBrushes.filterNot { it.id == brushId }; brushStore.save(importedBrushes) },
            onDismissError = { importError = null },
            onSelectPage = viewModel::selectPage,
            onAddPage = { libraryState.selectedDocumentId?.let(viewModel::addPage) },
            onDuplicatePage = { documentId, pageId -> viewModel.duplicatePage(documentId, pageId) },
            onDeletePage = { documentId, pageId -> viewModel.deletePage(documentId, pageId) },
            onMovePage = viewModel::movePage,
            onTransferPage = viewModel::transferPage,
            onCycleTemplate = viewModel::cycleTemplate,
            onToggleBookmark = viewModel::toggleBookmark,
            onChangePageOutlineLevel = viewModel::changePageOutlineLevel,
            onTogglePageOutlineCollapsed = viewModel::togglePageOutlineCollapsed,
            onRenamePage = viewModel::renamePage,
            onSetPageMetadata = viewModel::setPageMetadata,
            onClearPageInk = viewModel::clearPageInk,
            onSetPageCrop = viewModel::setPageCrop,
            onRotatePage = viewModel::rotatePageClockwise,
            onAddLayer = viewModel::addLayer,
            onRenameLayer = viewModel::renameLayer,
            onToggleLayerVisibility = viewModel::toggleLayerVisibility,
            onToggleLayerLock = viewModel::toggleLayerLock,
            onMoveLayer = viewModel::moveLayer,
            onDuplicateLayer = viewModel::duplicateLayer,
            onMergeLayerDown = viewModel::mergeLayerDown,
            onDeleteLayer = viewModel::deleteLayer,
            onStroke = viewModel::addStroke,
            onRemoveStroke = viewModel::removeStroke,
            onStrokesReplaced = viewModel::replaceStrokes,
            onElement = viewModel::addElement,
            onRemoveElement = viewModel::removeElement,
            onDuplicateElement = viewModel::duplicateElement,
            onInsertImage = { pageImagePicker.launch(arrayOf("image/*")) },
            onExport = { exportPicker.launch("${viewModel.currentDocument()?.title ?: "Inkloom"}.pdf") },
            onExportPage = { imageExportPicker.launch("${viewModel.currentDocument()?.title ?: "Inkloom"}-page.png") },
            onExportBundle = { bundleExportPicker.launch("${viewModel.currentDocument()?.title ?: "Inkloom"}.inkloom.zip") },
            assets = viewModel.assets,
            settings = settings,
            pdfIndexing = libraryState.selectedDocumentId in libraryState.pdfIndexingDocumentIds,
            onIndexPdfText = viewModel::indexPdfText,
            onSettings = { settingsOpen = true },
            onSettingsChange = ::updateSettings,
        )
    }
    if (settingsOpen) SettingsDialog(settings, onChange = ::updateSettings, onDismiss = { settingsOpen = false })
}

private enum class LibraryFilter { ALL, FAVORITES, ARCHIVED, TRASH, PDF }

@Composable
private fun LibraryScreen(documents: List<InkDocument>, folders: List<InkFolder>, onOpen: (InkDocument) -> Unit, onNewNote: () -> Unit, onImport: () -> Unit, onFavorite: (String) -> Unit, onArchive: (String) -> Unit, onTags: (String, List<String>) -> Unit, onTrash: (String) -> Unit, onRestore: (String) -> Unit, onDelete: (String) -> Unit, onRename: (String, String) -> Unit, onDuplicate: (String) -> Unit, onCreateFolder: (String, String?) -> Unit, onMoveToFolder: (String, String?) -> Unit, onDeleteFolder: (String) -> Unit, onSettings: () -> Unit) {
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var search by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf<String?>(null) }
    val visible = documents.filter { document ->
        val matchesSearch = search.isBlank() || document.title.contains(search, true) || document.tags.any { it.contains(search, true) } || document.pages.any { PageSearch.matches(it, search) }
        matchesSearch && (folderId == null || document.folderId == folderId) && when (filter) {
            LibraryFilter.ALL -> document.trashedAt == null && document.archivedAt == null
            LibraryFilter.FAVORITES -> document.favorite && document.trashedAt == null && document.archivedAt == null
            LibraryFilter.ARCHIVED -> document.archivedAt != null && document.trashedAt == null
            LibraryFilter.TRASH -> document.trashedAt != null
            LibraryFilter.PDF -> document.pages.any { it.background is PageBackground.Pdf } && document.trashedAt == null && document.archivedAt == null
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val compact = maxWidth < 700.dp
        Row(Modifier.fillMaxSize()) {
            if (!compact) {
        LibraryRail(filter, { filter = it; folderId = null }, folders, folderId, { folderId = it; filter = LibraryFilter.ALL }, onCreateFolder, onDeleteFolder, onSettings)
                VerticalDivider(color = OutlineSoft)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (compact) 16.dp else 32.dp, vertical = 22.dp)) {
            if (compact) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filter == LibraryFilter.ALL, onClick = { filter = LibraryFilter.ALL }, label = { Text("Library") }) }
                    item { FilterChip(selected = filter == LibraryFilter.FAVORITES, onClick = { filter = LibraryFilter.FAVORITES }, label = { Text("Favorites") }) }
                    item { FilterChip(selected = filter == LibraryFilter.ARCHIVED, onClick = { filter = LibraryFilter.ARCHIVED }, label = { Text("Archive") }) }
                    item { FilterChip(selected = filter == LibraryFilter.TRASH, onClick = { filter = LibraryFilter.TRASH }, label = { Text("Trash") }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your notebooks", fontSize = 32.sp, color = Ink)
                    Text("Continue a note or start with a fresh page.", color = MutedInk)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = onImport, shape = RoundedCornerShape(50)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Import") }
                Spacer(Modifier.width(10.dp))
                Button(onClick = onNewNote, shape = RoundedCornerShape(50)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New Note")
                }
            }
            Spacer(Modifier.height(26.dp))
            OutlinedTextField(value = search, onValueChange = { search = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search notes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Text("Recents", fontSize = 22.sp)
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(visible.take(4)) { NotebookCard(it, Modifier.width(230.dp).height(215.dp), onOpen, onFavorite, onArchive, onTags, onTrash, onRestore, onDelete, onRename, onDuplicate, folders, onMoveToFolder) }
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All Notes", fontSize = 22.sp, modifier = Modifier.weight(1f))
                FilterChip(selected = filter == LibraryFilter.ALL, onClick = { filter = LibraryFilter.ALL }, label = { Text("All") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = filter == LibraryFilter.FAVORITES, onClick = { filter = LibraryFilter.FAVORITES }, label = { Text("Favorites") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = filter == LibraryFilter.PDF, onClick = { filter = LibraryFilter.PDF }, label = { Text("PDFs") })
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(visible, key = { it.id }) { NotebookCard(it, Modifier.height(250.dp), onOpen, onFavorite, onArchive, onTags, onTrash, onRestore, onDelete, onRename, onDuplicate, folders, onMoveToFolder) }
            }
        }
        }
    }
}

@Composable
private fun LibraryRail(filter: LibraryFilter, onFilter: (LibraryFilter) -> Unit, folders: List<InkFolder>, folderId: String?, onFolder: (String?) -> Unit, onCreateFolder: (String, String?) -> Unit, onDeleteFolder: (String) -> Unit, onSettings: () -> Unit) {
    var newFolderOpen by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    Column(Modifier.width(198.dp).fillMaxHeight().padding(16.dp)) {
        BrandMark("Library")
        Spacer(Modifier.height(34.dp))
        RailItem(Icons.Outlined.Book, "Library", filter == LibraryFilter.ALL) { onFilter(LibraryFilter.ALL) }
        RailItem(Icons.Outlined.History, "Recents", filter == LibraryFilter.ALL) { onFilter(LibraryFilter.ALL) }
        RailItem(Icons.Outlined.StarBorder, "Favorites", filter == LibraryFilter.FAVORITES) { onFilter(LibraryFilter.FAVORITES) }
        RailItem(Icons.Outlined.Archive, "Archive", filter == LibraryFilter.ARCHIVED) { onFilter(LibraryFilter.ARCHIVED) }
        RailItem(Icons.Outlined.Delete, "Trash", filter == LibraryFilter.TRASH) { onFilter(LibraryFilter.TRASH) }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = OutlineSoft)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("FOLDERS", fontSize = 11.sp, color = MutedInk, modifier = Modifier.weight(1f)); IconButton(onClick = { newFolderOpen = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Add, "New folder") } }
        Spacer(Modifier.height(10.dp))
        folders.filter { it.parentId == null }.forEach { folder ->
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { RailItem(Icons.Outlined.Folder, folder.name, folderId == folder.id) { onFolder(folder.id) } }; IconButton(onClick = { onDeleteFolder(folder.id) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, "Delete folder", modifier = Modifier.size(14.dp)) } }
            folders.filter { it.parentId == folder.id }.forEach { child -> Box(Modifier.padding(start = 16.dp)) { RailItem(Icons.Outlined.FolderOpen, child.name, folderId == child.id) { onFolder(child.id) } } }
        }
        Spacer(Modifier.weight(1f))
        RailItem(Icons.Outlined.Settings, "Settings", false, onSettings)
    }
    if (newFolderOpen) AlertDialog(onDismissRequest = { newFolderOpen = false }, title = { Text("New folder") }, text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onCreateFolder(folderName, folderId); folderName = ""; newFolderOpen = false }) { Text("Create") } }, dismissButton = { TextButton(onClick = { newFolderOpen = false }) { Text("Cancel") } })
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Workspace else Color.Transparent).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(document.coverArgb))) {
            if (document.pages.any { it.background is PageBackground.Pdf }) Surface(Modifier.padding(12.dp), shape = RoundedCornerShape(4.dp), color = Color.White) { Text("PDF", Modifier.padding(7.dp, 4.dp), fontSize = 10.sp) }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Document actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (document.trashedAt == null) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameOpen = true })
                        DropdownMenuItem(text = { Text("Edit tags") }, onClick = { menu = false; tagsOpen = true })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate(document.id) })
                        folders.forEach { folder -> DropdownMenuItem(text = { Text("Move to ${folder.name}") }, onClick = { menu = false; onMoveToFolder(document.id, folder.id) }) }
                        if (document.folderId != null) DropdownMenuItem(text = { Text("Remove from folder") }, onClick = { menu = false; onMoveToFolder(document.id, null) })
                        DropdownMenuItem(text = { Text(if (document.favorite) "Remove favorite" else "Favorite") }, onClick = { menu = false; onFavorite(document.id) })
                        DropdownMenuItem(text = { Text(if (document.archivedAt == null) "Archive" else "Unarchive") }, onClick = { menu = false; onArchive(document.id) })
                        DropdownMenuItem(text = { Text("Move to Trash") }, onClick = { menu = false; onTrash(document.id) })
                    } else {
                        DropdownMenuItem(text = { Text("Restore") }, onClick = { menu = false; onRestore(document.id) })
                        DropdownMenuItem(text = { Text("Delete permanently") }, onClick = { menu = false; onDelete(document.id) })
                    }
                }
            }
            Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                repeat(3) { index -> drawLine(Color.White.copy(alpha = .65f), Offset(0f, 18f + index * 12f), Offset(size.width * (.9f - index * .08f), 18f + index * 12f), strokeWidth = 2f, cap = StrokeCap.Round) }
            }
        }
        Column(Modifier.padding(14.dp)) {
            Row { Text(document.title, fontSize = 17.sp, maxLines = 2, modifier = Modifier.weight(1f)); if (document.favorite) Icon(Icons.Default.Star, null, tint = Sage, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(6.dp)); Text("${document.pages.size} pages · saved locally", color = MutedInk, fontSize = 11.sp)
            if (document.tags.isNotEmpty()) Text(document.tags.take(3).joinToString("  ") { "#$it" }, color = Sage, fontSize = 10.sp, maxLines = 1)
        }
    }
    if (renameOpen) AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text("Rename note") }, text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onRename(document.id, title); renameOpen = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } })
    if (tagsOpen) AlertDialog(onDismissRequest = { tagsOpen = false }, title = { Text("Note tags") }, text = { OutlinedTextField(value = tags, onValueChange = { tags = it }, supportingText = { Text("Separate tags with commas") }) }, confirmButton = { TextButton(onClick = { onTags(document.id, tags.split(',')); tagsOpen = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { tagsOpen = false }) { Text("Cancel") } })
}

@Composable
private fun EditorScreen(
    document: InkDocument?,
    documents: List<InkDocument>,
    selectedPageId: String?,
    brushesOpen: Boolean,
    brushes: List<ImportedBrush>,
    importError: String?,
    onBack: () -> Unit,
    onToggleBrushes: () -> Unit,
    onImportBrushes: () -> Unit,
    onExportBrush: (ImportedBrush) -> Unit,
    onUpdateBrush: (ImportedBrush) -> Unit,
    onDeleteBrush: (String) -> Unit,
    onDismissError: () -> Unit,
    onSelectPage: (String) -> Unit,
    onAddPage: () -> Unit,
    onDuplicatePage: (String, String) -> Unit,
    onDeletePage: (String, String) -> Unit,
    onMovePage: (String, String, Int) -> Unit,
    onTransferPage: (String, String, String, Boolean) -> Unit,
    onCycleTemplate: (String, String) -> Unit,
    onToggleBookmark: (String, String) -> Unit,
    onChangePageOutlineLevel: (String, String, Int) -> Unit,
    onTogglePageOutlineCollapsed: (String, String) -> Unit,
    onRenamePage: (String, String, String) -> Unit,
    onSetPageMetadata: (String, String, String?, List<String>) -> Unit,
    onClearPageInk: (String, String) -> Unit,
    onSetPageCrop: (String, String, PageCrop) -> Unit,
    onRotatePage: (String, String) -> Unit,
    onAddLayer: (String, String, String) -> Unit,
    onRenameLayer: (String, String, String, String) -> Unit,
    onToggleLayerVisibility: (String, String, String) -> Unit,
    onToggleLayerLock: (String, String, String) -> Unit,
    onMoveLayer: (String, String, String, Int) -> Unit,
    onDuplicateLayer: (String, String, String) -> String?,
    onMergeLayerDown: (String, String, String) -> String?,
    onDeleteLayer: (String, String, String) -> Unit,
    onStroke: (String, String, InkStroke) -> Unit,
    onRemoveStroke: (String, String, String) -> Unit,
    onStrokesReplaced: (String, String, List<String>, List<InkStroke>, List<Int>) -> Unit,
    onElement: (String, String, PageElement) -> Unit,
    onRemoveElement: (String, String, String) -> Unit,
    onDuplicateElement: (String, String, String) -> Unit,
    onInsertImage: () -> Unit,
    onExport: () -> Unit,
    onExportPage: () -> Unit,
    onExportBundle: () -> Unit,
    assets: DocumentAssetService,
    settings: AppSettings,
    pdfIndexing: Boolean,
    onIndexPdfText: (String) -> Unit,
    onSettings: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
) {
    if (document == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val context = LocalContext.current
    val audioRecorder = remember { AudioNoteRecorder(context) }
    val selectedPage = document.pages.firstOrNull { it.id == selectedPageId } ?: document.pages.first()
    var tool by remember { mutableStateOf(EditorTool.PEN) }
    var canvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    var selectedBrushId by remember { mutableStateOf<String?>(null) }
    var activeBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var brushSize by remember { mutableFloatStateOf(settings.penSize) }
    var brushOpacity by remember { mutableFloatStateOf(settings.penOpacity) }
    var brushColor by remember { mutableIntStateOf(AndroidColor.rgb(17, 17, 17)) }
    var localEraser by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var toolbarX by remember { mutableFloatStateOf(settings.toolbarOffsetXDp) }
    var toolbarY by remember { mutableFloatStateOf(settings.toolbarOffsetYDp) }
    val editorDensity = LocalDensity.current
    LaunchedEffect(settings.toolbarOffsetXDp, settings.toolbarOffsetYDp, settings.toolbarDock) {
        toolbarX = settings.toolbarOffsetXDp
        toolbarY = settings.toolbarOffsetYDp
    }
    var inputDiagnostic by remember { mutableStateOf("Waiting for input") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchFocus by remember(document.id) { mutableStateOf<PageSearchHit?>(null) }
    var pdfLinkFocus by remember(document.id) { mutableStateOf<Pair<String, PdfTextBounds>?>(null) }
    var outlineOpen by remember { mutableStateOf(false) }
    var documentQuery by remember { mutableStateOf("") }
    var textInsertOpen by remember { mutableStateOf(false) }
    var stickyInsertOpen by remember { mutableStateOf(false) }
    var linkInsertOpen by remember { mutableStateOf(false) }
    var shapeInsertOpen by remember { mutableStateOf(false) }
    var insertText by remember { mutableStateOf("") }
    var insertLinkTitle by remember { mutableStateOf("") }
    var insertLinkUrl by remember { mutableStateOf("") }
    var insertShape by remember { mutableStateOf(ShapeKind.RECTANGLE) }
    var selectedElementId by remember(selectedPage.id) { mutableStateOf<String?>(null) }
    var audioOpen by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var audioTitle by remember { mutableStateOf("Audio note") }
    var audioError by remember { mutableStateOf<String?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) audioOpen = true else audioError = "Microphone permission is required to record an audio note." }
    DisposableEffect(audioRecorder) { onDispose { audioRecorder.cancel() } }
    var layersOpen by remember { mutableStateOf(false) }
    var activeLayerId by remember(selectedPage.id) { mutableStateOf(LayerIds.INK) }
    LaunchedEffect(selectedPage.layers) {
        val active = selectedPage.layers.firstOrNull { it.id == activeLayerId }
        if (active == null || active.locked || !active.visible || active.role == LayerRole.BACKGROUND) activeLayerId = selectedPage.layers.firstOrNull { it.visible && !it.locked && it.role != LayerRole.BACKGROUND }?.id ?: LayerIds.INK
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.height(50.dp).fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(); VerticalDivider(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)); Text(document.title, Modifier.weight(1f))
            IconButton(onClick = { searchOpen = true }) { Icon(Icons.Outlined.Search, "Search this note") }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
            Surface(shape = CircleShape, color = Workspace) { Text("J", Modifier.padding(12.dp, 8.dp)) }
        }
        HorizontalDivider(color = OutlineSoft)
        Row(Modifier.height(44.dp).fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
            Text("${document.title} · ${selectedPage.title}", Modifier.weight(1f))
            IconButton(onClick = { layersOpen = false; onToggleBrushes() }) { Icon(Icons.Outlined.Brush, "Brushes") }
            IconButton(onClick = { if (brushesOpen) onToggleBrushes(); layersOpen = !layersOpen }) { Icon(Icons.Outlined.Layers, "Layers") }
            IconButton(onClick = { outlineOpen = true }) { Icon(Icons.Outlined.FormatListBulleted, "Page outline") }
            IconButton(onClick = { onSettingsChange(settings.copy(showPageRail = !settings.showPageRail)) }) { Icon(if (settings.showPageRail) Icons.Outlined.ViewSidebar else Icons.Outlined.TableRows, "Toggle pages") }
            IconButton(onClick = { onSettingsChange(settings.copy(showToolbar = !settings.showToolbar)) }) { Icon(if (settings.showToolbar) Icons.Outlined.VisibilityOff else Icons.Outlined.Build, "Toggle toolbar") }
            IconButton(onClick = { zoom = (zoom - .1f).coerceAtLeast(.6f) }) { Icon(Icons.Outlined.ZoomOut, "Zoom out") }
            IconButton(onClick = { zoom = (zoom + .1f).coerceAtMost(2.5f) }) { Icon(Icons.Outlined.ZoomIn, "Zoom in") }
            IconButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }) { Icon(Icons.Outlined.CenterFocusStrong, "Reset view") }
            IconButton(onClick = onExport) { Icon(Icons.Outlined.Share, "Export PDF") }
            Box {
                IconButton(onClick = { exportMenu = true }) { Icon(Icons.Outlined.MoreHoriz, "More exports") }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    document.pages.forEachIndexed { index, page -> DropdownMenuItem(text = { Text("Go to page ${index + 1}") }, onClick = { exportMenu = false; onSelectPage(page.id) }) }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Export current page PNG") }, onClick = { exportMenu = false; onExportPage() })
                    DropdownMenuItem(text = { Text("Export editable Inkloom package") }, onClick = { exportMenu = false; onExportBundle() })
                }
            }
        }
        HorizontalDivider(color = OutlineSoft)
        BoxWithConstraints(Modifier.weight(1f)) {
        val editorWidth = maxWidth
        Row(Modifier.fillMaxSize()) {
            if (editorWidth >= 700.dp && settings.showPageRail) PageRail(document, documents, selectedPage.id, onSelectPage, onAddPage, onDuplicatePage, onDeletePage, onMovePage, onTransferPage, onCycleTemplate, onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata, onClearPageInk, onSetPageCrop, onRotatePage)
            if ((!brushesOpen && !layersOpen) || editorWidth >= 900.dp) BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(Workspace), contentAlignment = Alignment.Center) {
                val toolbarAreaWidth = maxWidth
                val toolbarAreaHeight = maxHeight
                PaperCanvas(
                    modifier = Modifier.fillMaxHeight(.88f).aspectRatio((PageCropTransform.visibleWidth(selectedPage) / PageCropTransform.visibleHeight(selectedPage).coerceAtLeast(1f)).coerceIn(.35f, 2.8f)).graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = panX, translationY = panY),
                    tool = tool,
                    onView = { canvasView = it },
                    page = selectedPage,
                    documentId = document.id,
                    onStroke = onStroke,
                    onRemoveStroke = onRemoveStroke,
                    onStrokesReplaced = onStrokesReplaced,
                    onElement = onElement,
                    onRemoveElement = onRemoveElement,
                    onDuplicateElement = onDuplicateElement,
                    selectedElementId = selectedElementId,
                    onSelectElement = { selectedElementId = it },
                    assets = assets,
                    settings = settings,
                    onZoom = { factor, focusX, focusY ->
                        val next = ViewportTransform.zoomAround(ViewportState(zoom, panX, panY), factor, focusX, focusY)
                        zoom = next.scale; panX = next.panX; panY = next.panY
                    },
                    onPan = { dx, dy -> val next = ViewportTransform.panBy(ViewportState(zoom, panX, panY), dx, dy); panX = next.panX; panY = next.panY },
                    onUndoGesture = { canvasView?.undo() },
                    activeBrush = activeBrush,
                    brushSize = brushSize,
                    brushOpacity = brushOpacity,
                    brushColor = brushColor,
                    shapeKind = insertShape,
                    activeLayerId = activeLayerId,
                    localEraser = localEraser,
                    searchBounds = searchFocus?.takeIf { it.pageId == selectedPage.id }?.bounds ?: pdfLinkFocus?.takeIf { it.first == selectedPage.id }?.second,
                    onPdfGotoLink = { link ->
                        document.pages.firstOrNull { it.id == link.destinationPageId }?.let { target ->
                            searchFocus = null
                            pdfLinkFocus = target.id to PdfGotoLinkNavigation.destinationBounds(link)
                            onSelectPage(target.id)
                            if (link.destinationZoom > 0f) zoom = link.destinationZoom.coerceIn(.6f, 2.5f)
                        }
                    },
                    onInputDiagnostic = { kind, pressure, tilt, buttons -> inputDiagnostic = "$kind  pressure ${"%.3f".format(pressure)}  tilt ${"%.2f".format(Math.toDegrees(tilt.toDouble()))}°  buttons $buttons" },
                )
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
                if (settings.showToolbar) ToolDock(tool = tool, onTool = { chosen ->
                    tool = chosen; canvasView?.tool = chosen
                    when (chosen) {
                        EditorTool.PEN -> { activeBrush = null; brushOpacity = settings.penOpacity; brushSize = settings.penSize; if (activeLayerId == LayerIds.HIGHLIGHT || activeLayerId == LayerIds.OBJECTS) activeLayerId = LayerIds.INK }
                        EditorTool.HIGHLIGHTER -> { activeBrush = null; brushOpacity = settings.highlighterOpacity; brushSize = settings.highlighterSize; activeLayerId = LayerIds.HIGHLIGHT }
                        EditorTool.ERASER -> { brushOpacity = 1f; brushSize = settings.eraserSize }
                        EditorTool.SHAPE -> shapeInsertOpen = true
                        EditorTool.TEXT -> { insertText = ""; textInsertOpen = true }
                        EditorTool.NOTE -> { insertText = ""; stickyInsertOpen = true }
                        EditorTool.LINK -> { insertLinkTitle = ""; insertLinkUrl = ""; linkInsertOpen = true }
                        EditorTool.IMAGE -> { onInsertImage(); tool = EditorTool.SELECT }
                        EditorTool.AUDIO -> if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) audioOpen = true else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else -> Unit
                    }
                }, onUndo = { canvasView?.undo() }, onRedo = { canvasView?.redo() }, onScale = { selectedElementId?.let { id -> selectedPage.elements.firstOrNull { it.id == id }?.let { onElement(document.id, selectedPage.id, resizeElement(it, 1.15f)) } } ?: canvasView?.scaleSelection(1.15f) }, onRotate = { selectedElementId?.let { id -> selectedPage.elements.firstOrNull { it.id == id }?.let { onElement(document.id, selectedPage.id, rotateElement(it, 15f)) } } ?: canvasView?.rotateSelection(15f) }, onCopy = { selectedElementId?.let { onDuplicateElement(document.id, selectedPage.id, it) } ?: canvasView?.copySelection() }, onCut = { if (selectedElementId != null) Toast.makeText(context, "Clipboard actions currently apply to ink selections.", Toast.LENGTH_SHORT).show() else if (canvasView?.cutSelectionToClipboard() != true) Toast.makeText(context, "Select ink before cutting.", Toast.LENGTH_SHORT).show() }, onClipboardCopy = { if (selectedElementId != null) Toast.makeText(context, "Clipboard actions currently apply to ink selections.", Toast.LENGTH_SHORT).show() else if (canvasView?.copySelectionToClipboard() != true) Toast.makeText(context, "Select ink before copying.", Toast.LENGTH_SHORT).show() }, onPaste = { selectedElementId = null; if (canvasView?.pasteSelectionFromClipboard() != true) Toast.makeText(context, "The clipboard does not contain Inkloom ink.", Toast.LENGTH_SHORT).show() }, onDelete = { selectedElementId?.let { onRemoveElement(document.id, selectedPage.id, it); selectedElementId = null } ?: canvasView?.deleteSelection() }, selectionShape = settings.selectionShape, selectionMode = settings.selectionMode, selectionFilter = settings.selectionFilter, onSelectionShape = { onSettingsChange(settings.copy(selectionShape = it)) }, onSelectionMode = { onSettingsChange(settings.copy(selectionMode = it)) }, onSelectionFilter = { onSettingsChange(settings.copy(selectionFilter = it)) }, localEraser = localEraser, onToggleLocalEraser = { localEraser = !localEraser; canvasView?.localEraser = localEraser; if (localEraser) onSettingsChange(settings.copy(nonDestructiveEraser = false)) }, nonDestructiveEraser = settings.nonDestructiveEraser, onToggleNonDestructiveEraser = { val enabled = !settings.nonDestructiveEraser; if (enabled) localEraser = false; onSettingsChange(settings.copy(nonDestructiveEraser = enabled)) }, onDrag = { dx, dy ->
                    val next = FloatingToolbarPlacement.clamp(toolbarX + dx / editorDensity.density, toolbarY + dy / editorDensity.density, toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical)
                    toolbarX = next.xDp
                    toolbarY = next.yDp
                }, onDragEnd = { onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = toolbarX, toolbarOffsetYDp = toolbarY, verticalToolbar = dockVertical)) }, vertical = dockVertical, onToggleOrientation = { onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE, verticalToolbar = !dockVertical)) }, onResetPosition = {
                    val reset = FloatingToolbarPlacement.defaultPosition(toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical)
                    toolbarX = reset.xDp
                    toolbarY = reset.yDp
                    onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = toolbarX, toolbarOffsetYDp = toolbarY))
                }, onDock = { dock -> onSettingsChange(settings.copy(toolbarDock = dock, verticalToolbar = when (dock) { ToolbarDock.LEFT, ToolbarDock.RIGHT -> true; ToolbarDock.TOP, ToolbarDock.BOTTOM -> false; ToolbarDock.FREE -> dockVertical })) }, brushSize = brushSize, brushOpacity = brushOpacity, brushColor = brushColor, onSize = { raw ->
                    val value = raw.coerceIn(.5f, 400f)
                    brushSize = value
                    when (tool) {
                        EditorTool.PEN -> if (activeBrush == null) onSettingsChange(settings.copy(penSize = value))
                        EditorTool.HIGHLIGHTER -> onSettingsChange(settings.copy(highlighterSize = value))
                        EditorTool.ERASER -> onSettingsChange(settings.copy(eraserSize = value))
                        else -> Unit
                    }
                }, onOpacity = { raw ->
                    val value = raw.coerceIn(.05f, 1f)
                    brushOpacity = value
                    when (tool) {
                        EditorTool.PEN -> if (activeBrush == null) onSettingsChange(settings.copy(penOpacity = value))
                        EditorTool.HIGHLIGHTER -> onSettingsChange(settings.copy(highlighterOpacity = value))
                        else -> Unit
                    }
                }, onColor = { brushColor = it }, modifier = Modifier.align(dockAlignment).padding(dockPadding).graphicsLayer(translationX = if (settings.toolbarDock == ToolbarDock.FREE) with(editorDensity) { boundedToolbarPosition.xDp.dp.toPx() } else 0f, translationY = if (settings.toolbarDock == ToolbarDock.FREE) with(editorDensity) { boundedToolbarPosition.yDp.dp.toPx() } else 0f))
                if (settings.showInputDiagnostics) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), shape = RoundedCornerShape(20.dp), color = Color(0xE6111111)) { Text(inputDiagnostic, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), color = Color.White, fontSize = 11.sp) }
            }
            if (layersOpen) LayerPanel(
                page = selectedPage,
                activeLayerId = activeLayerId,
                onSelect = { activeLayerId = it },
                onAdd = { onAddLayer(document.id, selectedPage.id, "Layer") },
                onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                onMove = { layerId, delta -> onMoveLayer(document.id, selectedPage.id, layerId, delta) },
                onDuplicate = { layerId -> onDuplicateLayer(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onMergeDown = { layerId -> onMergeLayerDown(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onDelete = { onDeleteLayer(document.id, selectedPage.id, it) },
            )
            if (brushesOpen) BrushPanel(
                brushes = brushes,
                selectedBrushId = selectedBrushId,
                onBrushSelected = { brush ->
                    val usedBrush = brush.copy(lastUsedAt = System.currentTimeMillis())
                    onUpdateBrush(usedBrush)
                    selectedBrushId = usedBrush.id
                    activeBrush = usedBrush
                    brushSize = usedBrush.defaultSize
                    brushOpacity = usedBrush.defaultOpacity
                    brushColor = when (usedBrush.format) {
                        BrushFormat.ABR -> AndroidColor.rgb(17, 17, 17)
                        BrushFormat.SUT -> AndroidColor.rgb(17, 17, 17)
                        else -> AndroidColor.rgb(17, 17, 17)
                    }
                    tool = EditorTool.PEN
                    canvasView?.apply {
                        this.tool = EditorTool.PEN
                        inkOpacity = 1f
                        brushId = usedBrush.id
                        baseWidth = usedBrush.defaultSize
                        inkColor = brushColor
                    }
                },
                onImportBrushes = onImportBrushes,
                onExportBrush = onExportBrush,
                onUpdateBrush = { updated -> onUpdateBrush(updated); if (activeBrush?.id == updated.id) { activeBrush = updated; brushSize = updated.defaultSize } },
                onDeleteBrush = { brushId -> onDeleteBrush(brushId); if (activeBrush?.id == brushId) { activeBrush = null; selectedBrushId = null } },
            )
        }
        }
    }
    if (textInsertOpen) AlertDialog(
        onDismissRequest = { textInsertOpen = false },
        title = { Text("Insert text") },
        text = { OutlinedTextField(insertText, { insertText = it }, minLines = 3, label = { Text("Text") }) },
        confirmButton = { Button(onClick = { if (insertText.isNotBlank()) onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Text(x = .18f, y = .2f, text = insertText.trim()), selectedPage.crop)); textInsertOpen = false; tool = EditorTool.SELECT }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = { textInsertOpen = false }) { Text("Cancel") } },
    )
    if (stickyInsertOpen) AlertDialog(
        onDismissRequest = { stickyInsertOpen = false },
        title = { Text("New sticky note") },
        text = { OutlinedTextField(insertText, { insertText = it }, minLines = 4, label = { Text("Note") }) },
        confirmButton = { Button(onClick = { onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Sticky(x = .55f, y = .25f, text = insertText.trim().ifBlank { "New note" }), selectedPage.crop)); stickyInsertOpen = false; tool = EditorTool.SELECT }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = { stickyInsertOpen = false }) { Text("Cancel") } },
    )
    if (linkInsertOpen) {
        val normalizedLink = LinkTarget.normalize(insertLinkUrl)
        AlertDialog(
            onDismissRequest = { linkInsertOpen = false },
            title = { Text("Insert link") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(insertLinkTitle, { insertLinkTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(insertLinkUrl, { insertLinkUrl = it }, label = { Text("Web, email, or phone address") }, placeholder = { Text("https://example.com") }, isError = insertLinkUrl.isNotBlank() && normalizedLink == null, supportingText = { Text(if (insertLinkUrl.isNotBlank() && normalizedLink == null) "Use a valid http, https, mailto, or tel address." else "Addresses without a scheme open with HTTPS.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { Button(enabled = normalizedLink != null, onClick = {
                val url = normalizedLink ?: return@Button
                val title = insertLinkTitle.trim().ifBlank { LinkTarget.displayDestination(url, 48) }
                onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Link(x = .18f, y = .2f, title = title, url = url), selectedPage.crop))
                linkInsertOpen = false
                tool = EditorTool.SELECT
            }) { Text("Insert") } },
            dismissButton = { TextButton(onClick = { linkInsertOpen = false }) { Text("Cancel") } },
        )
    }
    if (shapeInsertOpen) AlertDialog(
        onDismissRequest = { shapeInsertOpen = false },
        title = { Text("Draw shape") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Choose a shape, then drag on the page with the pen. Lines and arrows snap to nearby 45° angles; Freeform follows the pen and closes the polygon automatically.", color = MutedInk, fontSize = 12.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShapeKind.entries.forEach { kind -> item { FilterChip(selected = insertShape == kind, onClick = { insertShape = kind }, label = { Text(kind.name.lowercase().replaceFirstChar(Char::uppercase)) }) } } } } },
        confirmButton = { Button(onClick = { shapeInsertOpen = false; tool = EditorTool.SHAPE; canvasView?.tool = EditorTool.SHAPE }) { Text("Draw") } },
        dismissButton = { TextButton(onClick = { shapeInsertOpen = false }) { Text("Cancel") } },
    )
    if (audioOpen) AlertDialog(
        onDismissRequest = { if (recording) audioRecorder.cancel(); recording = false; audioOpen = false },
        title = { Text(if (recording) "Recording audio…" else "Audio note") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(audioTitle, { audioTitle = it }, label = { Text("Title") }, singleLine = true, enabled = !recording); Text(if (recording) "Recording locally. Tap Stop and insert when finished." else "Audio remains on this device and is included in editable Inkloom backups.", color = MutedInk) } },
        confirmButton = {
            if (!recording) Button(onClick = { runCatching { audioRecorder.start() }.onSuccess { recording = true }.onFailure { audioError = it.message } }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("Start") }
            else Button(onClick = { runCatching { audioRecorder.stop(audioTitle) }.onSuccess { marker -> onElement(document.id, selectedPage.id, marker); recording = false; audioOpen = false; tool = EditorTool.SELECT }.onFailure { audioError = it.message; recording = false } }) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop and insert") }
        },
        dismissButton = { TextButton(onClick = { if (recording) audioRecorder.cancel(); recording = false; audioOpen = false }) { Text("Cancel") } },
    )
    audioError?.let { message -> AlertDialog(onDismissRequest = { audioError = null }, title = { Text("Audio issue") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { audioError = null }) { Text("OK") } }) }
    if (outlineOpen) {
        val visibleOutlinePages = PageOutline.visiblePages(document.pages)
        AlertDialog(
            onDismissRequest = { outlineOpen = false },
            title = { Text("Page outline") },
            text = {
                LazyColumn(Modifier.widthIn(min = 360.dp, max = 560.dp).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(visibleOutlinePages, key = { it.id }) { page ->
                        val index = document.pages.indexOf(page)
                        val hasChildren = PageOutline.hasChildren(document.pages, index)
                        ListItem(
                            headlineContent = { Text(page.title, maxLines = 1, fontWeight = if (page.outlineLevel == 0) FontWeight.SemiBold else FontWeight.Normal) },
                            supportingContent = { Text(listOfNotNull(page.customNumber, page.tags.takeIf { it.isNotEmpty() }?.joinToString(" · ")).joinToString(" · ").ifBlank { "Page ${index + 1}" }, color = MutedInk, maxLines = 1) },
                            leadingContent = { if (page.bookmarked) Icon(Icons.Default.Bookmark, "Bookmarked", tint = Sage) else Text((index + 1).toString(), color = MutedInk) },
                            trailingContent = { if (hasChildren) IconButton(onClick = { onTogglePageOutlineCollapsed(document.id, page.id) }) { Icon(if (page.outlineCollapsed) Icons.Default.ArrowRight else Icons.Default.ArrowDropDown, if (page.outlineCollapsed) "Expand" else "Collapse") } },
                            modifier = Modifier.padding(start = (page.outlineLevel * 18).dp).clickable { onSelectPage(page.id); outlineOpen = false },
                            colors = ListItemDefaults.colors(containerColor = if (page.id == selectedPage.id) SageContainer else Color.Transparent),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { outlineOpen = false }) { Text("Done") } },
        )
    }
    if (searchOpen) {
        val results = if (documentQuery.isBlank()) emptyList() else document.pages.mapNotNull { page -> PageSearch.find(page, documentQuery)?.let { page to it } }
        val unindexedPdfPages = document.pages.count { it.background is PageBackground.Pdf && it.pdfTextIndexVersion == 0 }
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            title = { Text("Search this note") },
            text = { Column(Modifier.widthIn(min = 340.dp, max = 560.dp)) {
                OutlinedTextField(documentQuery, { documentQuery = it; searchFocus = null }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Page, note, or PDF text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (unindexedPdfPages > 0) Surface(Modifier.fillMaxWidth().padding(top = 10.dp), color = Workspace, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (pdfIndexing) "Indexing PDF text…" else "$unindexedPdfPages PDF pages need a text index.", Modifier.weight(1f), color = MutedInk, fontSize = 12.sp)
                        if (pdfIndexing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else TextButton(onClick = { onIndexPdfText(document.id) }) { Text("Index now") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (documentQuery.isNotBlank() && results.isEmpty()) Text("No matching page titles, tags, object text, or indexed PDF text.", color = MutedInk)
                else LazyColumn(Modifier.heightIn(max = 340.dp)) { items(results, key = { (page, _) -> page.id }) { (page, hit) ->
                    ListItem(
                        headlineContent = { Text(page.title) },
                        supportingContent = { Text(hit.snippet, maxLines = 2) },
                        leadingContent = { Text(when (hit.source) { PageSearchSource.PDF -> "PDF"; PageSearchSource.OBJECT -> "OBJ"; PageSearchSource.PAGE -> "PAGE" }, color = Sage, fontSize = 9.sp) },
                        modifier = Modifier.clickable { searchFocus = hit; onSelectPage(page.id); searchOpen = false },
                    )
                } }
            } },
            confirmButton = { TextButton(onClick = { searchOpen = false }) { Text("Done") } },
        )
    }
    if (importError != null) AlertDialog(onDismissRequest = onDismissError, confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } }, title = { Text("Import issue") }, text = { Text(importError) })
}

@Composable
private fun PageRail(document: InkDocument, documents: List<InkDocument>, selectedPageId: String, onSelect: (String) -> Unit, onAdd: () -> Unit, onDuplicate: (String, String) -> Unit, onDelete: (String, String) -> Unit, onMove: (String, String, Int) -> Unit, onTransfer: (String, String, String, Boolean) -> Unit, onCycleTemplate: (String, String) -> Unit, onToggleBookmark: (String, String) -> Unit, onOutlineLevel: (String, String, Int) -> Unit, onOutlineCollapsed: (String, String) -> Unit, onRename: (String, String, String) -> Unit, onMetadata: (String, String, String?, List<String>) -> Unit, onClearInk: (String, String) -> Unit, onCrop: (String, String, PageCrop) -> Unit, onRotate: (String, String) -> Unit) {
    val transferTargets = documents.filter { it.id != document.id && it.trashedAt == null }
    LazyColumn(Modifier.width(112.dp).fillMaxHeight().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Pages", Modifier.weight(1f), color = Ink, maxLines = 1); IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, "Add page") } } }
        items(document.pages, key = { it.id }) { page ->
            val pageIndex = document.pages.indexOf(page)
            val hasOutlineChildren = PageOutline.hasChildren(document.pages, pageIndex)
            var menu by remember { mutableStateOf(false) }
            var renameOpen by remember { mutableStateOf(false) }
            var metadataOpen by remember { mutableStateOf(false) }
            var clearOpen by remember { mutableStateOf(false) }
            var transferOpen by remember { mutableStateOf(false) }
            var cropOpen by remember { mutableStateOf(false) }
            var cropDraft by remember(page.crop) { mutableStateOf(page.crop) }
            var title by remember(page.title) { mutableStateOf(page.title) }
            var customNumber by remember(page.customNumber) { mutableStateOf(page.customNumber.orEmpty()) }
            var tags by remember(page.tags) { mutableStateOf(page.tags.joinToString(", ")) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp, 74.dp).background(Color.White, RoundedCornerShape(5.dp)).border(if (page.id == selectedPageId) 2.dp else 1.dp, if (page.id == selectedPageId) Sage else OutlineSoft, RoundedCornerShape(5.dp)).clickable { onSelect(page.id) }.padding(4.dp)) {
                    if (page.background is PageBackground.Pdf) Text("PDF", fontSize = 9.sp, color = Sage)
                    if (page.bookmarked) Icon(Icons.Default.Bookmark, "Bookmarked", tint = Sage, modifier = Modifier.size(16.dp).align(Alignment.TopEnd))
                }
                Row(Modifier.width(70.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(page.customNumber ?: (document.pages.indexOf(page) + 1).toString(), color = if (page.id == selectedPageId) Sage else MutedInk, modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).clickable { onSelect(page.id) }.padding(vertical = 6.dp), maxLines = 1)
                    if (page.id == selectedPageId) {
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Workspace).clickable { menu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreVert, "Page menu", modifier = Modifier.size(16.dp)) }
                    } else {
                        Spacer(Modifier.size(26.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate(document.id, page.id) })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameOpen = true })
                        DropdownMenuItem(text = { Text("Number and tags") }, onClick = { menu = false; metadataOpen = true })
                        DropdownMenuItem(text = { Text("Move up") }, enabled = document.pages.first().id != page.id, onClick = { menu = false; onMove(document.id, page.id, -1) })
                        DropdownMenuItem(text = { Text("Move down") }, enabled = document.pages.last().id != page.id, onClick = { menu = false; onMove(document.id, page.id, 1) })
                        DropdownMenuItem(text = { Text("Move or copy to note") }, enabled = transferTargets.isNotEmpty(), onClick = { menu = false; transferOpen = true })
                        DropdownMenuItem(text = { Text("Increase outline indent") }, enabled = pageIndex > 0 && page.outlineLevel < 3 && page.outlineLevel <= document.pages[pageIndex - 1].outlineLevel, onClick = { menu = false; onOutlineLevel(document.id, page.id, 1) })
                        DropdownMenuItem(text = { Text("Decrease outline indent") }, enabled = page.outlineLevel > 0, onClick = { menu = false; onOutlineLevel(document.id, page.id, -1) })
                        if (hasOutlineChildren) DropdownMenuItem(text = { Text(if (page.outlineCollapsed) "Expand outline children" else "Collapse outline children") }, onClick = { menu = false; onOutlineCollapsed(document.id, page.id) })
                        DropdownMenuItem(text = { Text("Change template") }, enabled = page.background is PageBackground.Paper, onClick = { menu = false; onCycleTemplate(document.id, page.id) })
                        DropdownMenuItem(text = { Text("Crop page") }, onClick = { menu = false; cropDraft = page.crop; cropOpen = true })
                        DropdownMenuItem(text = { Text("Rotate 90° clockwise") }, onClick = { menu = false; onRotate(document.id, page.id) })
                        DropdownMenuItem(text = { Text(if (page.bookmarked) "Remove bookmark" else "Bookmark") }, onClick = { menu = false; onToggleBookmark(document.id, page.id) })
                        DropdownMenuItem(text = { Text("Clear unlocked ink") }, enabled = page.strokes.any { stroke -> page.layers.firstOrNull { it.id == stroke.layerId }?.locked != true }, onClick = { menu = false; clearOpen = true })
                        DropdownMenuItem(text = { Text("Delete") }, enabled = document.pages.size > 1, onClick = { menu = false; onDelete(document.id, page.id) })
                    }
                }
            }
            if (renameOpen) AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text("Rename page") }, text = { OutlinedTextField(title, { title = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onRename(document.id, page.id, title); renameOpen = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } })
            if (metadataOpen) AlertDialog(onDismissRequest = { metadataOpen = false }, title = { Text("Page metadata") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(customNumber, { customNumber = it }, label = { Text("Custom page number") }, singleLine = true); OutlinedTextField(tags, { tags = it }, label = { Text("Tags") }, supportingText = { Text("Separate tags with commas") }) } }, confirmButton = { TextButton(onClick = { onMetadata(document.id, page.id, customNumber, tags.split(',')); metadataOpen = false }) { Text("Save") } }, dismissButton = { TextButton(onClick = { metadataOpen = false }) { Text("Cancel") } })
            if (clearOpen) AlertDialog(onDismissRequest = { clearOpen = false }, title = { Text("Clear page ink?") }, text = { Text("All strokes on unlocked layers will be removed. Locked-layer ink is preserved.") }, confirmButton = { TextButton(onClick = { onClearInk(document.id, page.id); clearOpen = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("Cancel") } })
            if (cropOpen) AlertDialog(
                onDismissRequest = { cropOpen = false },
                title = { Text("Crop page") },
                text = {
                    LazyColumn(Modifier.widthIn(min = 340.dp, max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { Text("The source PDF/image and all page content remain unchanged. Resetting the crop restores the full page.", color = MutedInk, fontSize = 12.sp) }
                        item { BrushSettingSlider("Left  ${(cropDraft.left * 100).toInt()}%", cropDraft.left, 0f..0.95f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(left = it)) } }
                        item { BrushSettingSlider("Top  ${(cropDraft.top * 100).toInt()}%", cropDraft.top, 0f..0.95f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(top = it)) } }
                        item { BrushSettingSlider("Right  ${(cropDraft.right * 100).toInt()}%", cropDraft.right, .05f..1f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(right = it)) } }
                        item { BrushSettingSlider("Bottom  ${(cropDraft.bottom * 100).toInt()}%", cropDraft.bottom, .05f..1f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(bottom = it)) } }
                        item { OutlinedButton(onClick = { cropDraft = PageCrop() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("Reset to full page") } }
                    }
                },
                confirmButton = { Button(onClick = { onCrop(document.id, page.id, cropDraft); cropOpen = false }) { Text("Apply") } },
                dismissButton = { TextButton(onClick = { cropOpen = false }) { Text("Cancel") } },
            )
            if (transferOpen) AlertDialog(
                onDismissRequest = { transferOpen = false },
                title = { Text("Move or copy page") },
                text = {
                    LazyColumn(Modifier.widthIn(min = 360.dp, max = 560.dp).heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(transferTargets, key = { it.id }) { target ->
                            ListItem(
                                headlineContent = { Text(target.title, maxLines = 1) },
                                supportingContent = { Text("${target.pages.size} pages", color = MutedInk) },
                                trailingContent = { Row { TextButton(onClick = { onTransfer(document.id, page.id, target.id, false); transferOpen = false }) { Text("Move") }; TextButton(onClick = { onTransfer(document.id, page.id, target.id, true); transferOpen = false }) { Text("Copy") } } },
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { transferOpen = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun PaperCanvas(modifier: Modifier, tool: EditorTool, onView: (InkCanvasView) -> Unit, page: InkPage, documentId: String, onStroke: (String, String, InkStroke) -> Unit, onRemoveStroke: (String, String, String) -> Unit, onStrokesReplaced: (String, String, List<String>, List<InkStroke>, List<Int>) -> Unit, onElement: (String, String, PageElement) -> Unit, onRemoveElement: (String, String, String) -> Unit, onDuplicateElement: (String, String, String) -> Unit, selectedElementId: String?, onSelectElement: (String?) -> Unit, assets: DocumentAssetService, settings: AppSettings, onZoom: (Float, Float, Float) -> Unit, onPan: (Float, Float) -> Unit, onUndoGesture: () -> Unit, activeBrush: ImportedBrush?, brushSize: Float, brushOpacity: Float, brushColor: Int, shapeKind: ShapeKind, activeLayerId: String, localEraser: Boolean, searchBounds: PdfTextBounds?, onPdfGotoLink: (PdfGotoLink) -> Unit, onInputDiagnostic: (String, Float, Float, Int) -> Unit) {
    val renderedPage = remember(page) { PageCropTransform.project(page) }
    val backgroundVisible = page.layers.firstOrNull { it.role == LayerRole.BACKGROUND }?.visible != false
    val background = remember(page.id, page.background, page.backgroundRotationQuarterTurns, page.crop, backgroundVisible) { if (backgroundVisible) runCatching { assets.renderBackground(page.background, 1200, page.backgroundRotationQuarterTurns, page.crop) }.getOrNull() else null }
    DisposableEffect(background) { onDispose { background?.recycle() } }
    val paperColor = (page.background as? PageBackground.Paper)?.colorArgb?.let(::Color) ?: Color.White
    val visibleLayerIds = page.layers.filter { it.visible }.mapTo(mutableSetOf()) { it.id }
    val lockedLayerIds = page.layers.filter { it.locked }.mapTo(mutableSetOf()) { it.id }
    val layerOrder = page.layers.mapIndexed { index, layer -> layer.id to index }.toMap()
    BoxWithConstraints(modifier.shadow(3.dp, RoundedCornerShape(4.dp)).background(paperColor).padding(48.dp)) {
        val density = LocalDensity.current
        val canvasWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val canvasHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        if (backgroundVisible && renderedPage.background is PageBackground.Paper) Column {
            Text(renderedPage.title, fontSize = 28.sp)
            Text("Inkloom local document", color = MutedInk, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            when (renderedPage.background.pattern) {
                PaperPattern.PLAIN -> Unit
                PaperPattern.RULED -> repeat(8) { HorizontalDivider(color = Color(0xFFE3E3E3), modifier = Modifier.padding(vertical = 12.dp)) }
                PaperPattern.GRID -> Canvas(Modifier.fillMaxSize()) { var x = 0f; while (x < size.width) { drawLine(Color(0xFFE3E3E3), Offset(x, 0f), Offset(x, size.height)); x += 32f }; var y = 0f; while (y < size.height) { drawLine(Color(0xFFE3E3E3), Offset(0f, y), Offset(size.width, y)); y += 32f } }
                PaperPattern.DOT -> Canvas(Modifier.fillMaxSize()) { var x = 8f; while (x < size.width) { var y = 8f; while (y < size.height) { drawCircle(Color(0xFFD9D9D9), 1.4f, Offset(x, y)); y += 28f }; x += 28f } }
            }
        }
        AndroidView(
            factory = { context -> InkCanvasView(context).also { view ->
                view.tool = tool
                view.onStrokeCommitted = { onStroke(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onShapeCommitted = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onStrokeRemoved = { onRemoveStroke(documentId, page.id, it) }
                view.onStrokesReplaced = { removed, replacements, indices -> onStrokesReplaced(documentId, page.id, removed, replacements.map { PageCropTransform.unproject(it, page.crop) }, indices) }
                view.onViewportScale = onZoom
                view.onViewportPan = onPan
                view.pdfGotoLinks = renderedPage.pdfGotoLinks
                view.onPdfGotoLink = onPdfGotoLink
                view.onUndoGesture = onUndoGesture
                view.onInputDiagnostic = onInputDiagnostic
                onView(view)
            } },
            update = { view ->
                view.tool = tool
                view.setDocumentStrokes(renderedPage.strokes.filter { it.layerId in visibleLayerIds }.sortedBy { layerOrder[it.layerId] ?: Int.MAX_VALUE }, "$documentId:${page.id}")
                view.setProtectedStrokeIds(page.strokes.filter { it.layerId in lockedLayerIds }.mapTo(mutableSetOf()) { it.id })
                view.setProtectedLayerIds(lockedLayerIds)
                view.setPageBackground(background)
                view.activeLayerId = activeLayerId
                view.shapeKind = shapeKind
                view.pageModelWidth = renderedPage.width
                view.pageModelHeight = renderedPage.height
                view.onStrokeCommitted = { onStroke(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onShapeCommitted = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onStrokeRemoved = { onRemoveStroke(documentId, page.id, it) }
                view.onStrokesReplaced = { removed, replacements, indices -> onStrokesReplaced(documentId, page.id, removed, replacements.map { PageCropTransform.unproject(it, page.crop) }, indices) }
                view.applyBrushConfiguration(tool, settings, activeBrush, brushSize, brushOpacity, brushColor, localEraser)
                view.onViewportScale = onZoom
                view.onViewportPan = onPan
                view.pdfGotoLinks = renderedPage.pdfGotoLinks
                view.onPdfGotoLink = onPdfGotoLink
                view.onUndoGesture = onUndoGesture
                view.onInputDiagnostic = onInputDiagnostic
            },
            modifier = Modifier.fillMaxSize(),
        )
        searchBounds?.let { PageCropTransform.project(it, page.crop) }?.let { bounds ->
            Canvas(Modifier.fillMaxSize()) {
                val topLeft = Offset(bounds.left * size.width, bounds.top * size.height)
                val highlightSize = androidx.compose.ui.geometry.Size((bounds.right - bounds.left) * size.width, (bounds.bottom - bounds.top) * size.height)
                drawRect(Color(0x33111111), topLeft = topLeft, size = highlightSize)
                drawRect(Color(0xFF111111), topLeft = topLeft, size = highlightSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            }
        }
        renderedPage.elements.filter { it.layerId in visibleLayerIds }.sortedBy { layerOrder[it.layerId] ?: Int.MAX_VALUE }.forEach { element ->
            ObjectElementView(
                element = element,
                page = renderedPage,
                selected = selectedElementId == element.id,
                editable = tool == EditorTool.SELECT && element.layerId !in lockedLayerIds,
                canvasWidth = maxWidth,
                canvasHeight = maxHeight,
                canvasWidthPx = canvasWidthPx,
                canvasHeightPx = canvasHeightPx,
                onSelect = { onSelectElement(element.id) },
                onChange = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) },
                onDuplicate = { onDuplicateElement(documentId, page.id, element.id) },
                onDelete = { onRemoveElement(documentId, page.id, element.id); onSelectElement(null) },
                settings = settings,
                activeBrush = activeBrush,
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                brushColor = brushColor,
                localEraser = localEraser,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

private fun InkCanvasView.applyBrushConfiguration(
    selectedTool: EditorTool,
    settings: AppSettings,
    activeBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    localEraser: Boolean,
) {
    tool = selectedTool
    fingerDrawingEnabled = settings.fingerDrawing
    twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
    pressureSensitivity = settings.pressureSensitivity
    pressureMinimumInput = settings.pressureMinimumInput
    pressureMaximumInput = settings.pressureMaximumInput
    stabilization = maxOf(settings.stabilization, activeBrush?.stabilization ?: 0f)
    stabilizationBySpeed = activeBrush?.stabilizationBySpeed ?: false
    stabilizationBySpeedMode = activeBrush?.stabilizationBySpeedMode ?: StrokeStabilization.REDUCE_WHEN_FAST
    primaryButtonAction = settings.stylusPrimaryButton
    secondaryButtonAction = settings.stylusSecondaryButton
    selectionShape = settings.selectionShape
    selectionMode = settings.selectionMode
    selectionFilter = settings.selectionFilter
    baseWidth = brushSize
    eraserWidth = settings.eraserSize
    this.localEraser = localEraser
    nonDestructiveEraser = settings.nonDestructiveEraser
    inkOpacity = brushOpacity
    inkColor = brushColor
    brushId = activeBrush?.id ?: if (selectedTool == EditorTool.HIGHLIGHTER) "system-highlighter" else "system-pen"
    tipAssetPath = activeBrush?.tipPreviewPath
    tipAssetPaths = activeBrush?.tipPreviewPaths.orEmpty()
    dualTipAssetPaths = activeBrush?.dualTipPreviewPaths.orEmpty()
    grainAssetPath = activeBrush?.grainPreviewPath
    grainDensity = activeBrush?.grainDensity ?: 0f
    grainScale = activeBrush?.grainScale ?: 100f
    grainRotationDegrees = activeBrush?.grainRotationDegrees ?: 0f
    grainBrightness = activeBrush?.grainBrightness ?: 0f
    grainContrast = activeBrush?.grainContrast ?: 0f
    grainReversed = activeBrush?.grainReversed ?: false
    grainEmphasized = activeBrush?.grainEmphasized ?: false
    brushSpacing = activeBrush?.spacing ?: .12f
    brushFlow = activeBrush?.flow ?: 1f
    brushHardness = activeBrush?.hardness ?: 1f
    brushThickness = activeBrush?.thickness ?: 1f
    brushThicknessVertical = activeBrush?.thicknessVertical ?: false
    brushRotation = activeBrush?.rotationDegrees ?: 0f
    followStylusOrientation = activeBrush?.followStylusOrientation ?: false
    followStrokeDirection = activeBrush?.followStrokeDirection ?: false
    randomRotationDegrees = activeBrush?.randomRotationDegrees ?: 0f
    brushWatercolor = activeBrush?.watercolor ?: false
    brushColorMix = activeBrush?.colorMix ?: 0f
    brushPaintMix = activeBrush?.paintMix ?: 0f
    brushSpray = activeBrush?.spray ?: false
    sprayParticleSize = activeBrush?.sprayParticleSize ?: 100f
    sprayParticleSizeUnit = activeBrush?.sprayParticleSizeUnit ?: 0
    sprayParticleSizeSyncBrushSize = activeBrush?.sprayParticleSizeSyncBrushSize ?: true
    sprayDensity = activeBrush?.sprayDensity ?: 1f
    sprayBias = activeBrush?.sprayBias ?: 0f
    sprayParticleRotationDegrees = activeBrush?.sprayParticleRotationDegrees ?: 0f
    sprayParticleFollowStylusOrientation = activeBrush?.sprayParticleFollowStylusOrientation ?: false
    sprayParticleFollowStrokeDirection = activeBrush?.sprayParticleFollowStrokeDirection ?: false
    sprayParticleRandomRotationDegrees = activeBrush?.sprayParticleRandomRotationDegrees ?: 0f
    brushRibbon = activeBrush?.ribbon ?: false
    brushTipSequenceMode = activeBrush?.tipSequenceMode ?: 0
    brushDual = activeBrush?.dualBrush ?: false
    dualSizeScale = activeBrush?.dualSizeScale ?: 1f
    dualSpacing = activeBrush?.dualSpacing ?: .12f
    dualFlow = activeBrush?.dualFlow ?: 1f
    dualHardness = activeBrush?.dualHardness ?: 1f
    dualThickness = activeBrush?.dualThickness ?: 1f
    dualThicknessVertical = activeBrush?.dualThicknessVertical ?: false
    dualRotationDegrees = activeBrush?.dualRotationDegrees ?: 0f
    dualFollowStylusOrientation = activeBrush?.dualFollowStylusOrientation ?: false
    dualFollowStrokeDirection = activeBrush?.dualFollowStrokeDirection ?: false
    dualRandomRotationDegrees = activeBrush?.dualRandomRotationDegrees ?: 0f
    dualRibbon = activeBrush?.dualRibbon ?: false
    dualSpray = activeBrush?.dualSpray ?: false
    dualSprayParticleSize = activeBrush?.dualSprayParticleSize ?: 100f
    dualSprayParticleSizeUnit = activeBrush?.dualSprayParticleSizeUnit ?: 0
    dualSprayParticleSizeSyncBrushSize = activeBrush?.dualSprayParticleSizeSyncBrushSize ?: true
    dualSprayDensity = activeBrush?.dualSprayDensity ?: 1f
    dualSprayBias = activeBrush?.dualSprayBias ?: 0f
    dualSprayParticleRotationDegrees = activeBrush?.dualSprayParticleRotationDegrees ?: 0f
    dualSprayParticleFollowStylusOrientation = activeBrush?.dualSprayParticleFollowStylusOrientation ?: false
    dualSprayParticleFollowStrokeDirection = activeBrush?.dualSprayParticleFollowStrokeDirection ?: false
    dualSprayParticleRandomRotationDegrees = activeBrush?.dualSprayParticleRandomRotationDegrees ?: 0f
    dualTipSequenceMode = activeBrush?.dualTipSequenceMode ?: 0
    dualCompositeMode = activeBrush?.dualCompositeMode ?: 0
    taperMode = activeBrush?.taperMode ?: BrushTaperMode.PERCENTAGE
    taperStartEnabled = activeBrush?.taperStartEnabled ?: false
    taperStartLength = activeBrush?.taperStartLength ?: .2f
    taperStartLengthUnit = activeBrush?.taperStartLengthUnit ?: 0
    taperStartMinimum = activeBrush?.taperStartMinimum ?: .3f
    taperEndEnabled = activeBrush?.taperEndEnabled ?: false
    taperEndLength = activeBrush?.taperEndLength ?: .2f
    taperEndLengthUnit = activeBrush?.taperEndLengthUnit ?: 0
    taperEndMinimum = activeBrush?.taperEndMinimum ?: .3f
    waterEdgeEnabled = activeBrush?.waterEdgeEnabled ?: false
    waterEdgeRadius = activeBrush?.waterEdgeRadius ?: 3f
    waterEdgeRadiusUnit = activeBrush?.waterEdgeRadiusUnit ?: 0
    waterEdgeOpacity = activeBrush?.waterEdgeOpacity ?: .2f
    waterEdgeDarkness = activeBrush?.waterEdgeDarkness ?: 0f
    waterEdgeBlur = activeBrush?.waterEdgeBlur ?: 0f
    waterEdgeBlurUnit = activeBrush?.waterEdgeBlurUnit ?: 0
    sizePressureEnabled = activeBrush?.sizePressureEnabled ?: (selectedTool == EditorTool.PEN)
    sizePressureMinimum = activeBrush?.sizePressureMinimum ?: .35f
    sizePressureCurve = activeBrush?.sizePressureCurve.orEmpty()
    sizeSecondaryDynamics = activeBrush?.sizeSecondaryDynamics ?: BrushSecondaryDynamics()
    opacityPressureEnabled = activeBrush?.opacityPressureEnabled ?: false
    opacityPressureMinimum = activeBrush?.opacityPressureMinimum ?: 0f
    opacityPressureCurve = activeBrush?.opacityPressureCurve.orEmpty()
    opacitySecondaryDynamics = activeBrush?.opacitySecondaryDynamics ?: BrushSecondaryDynamics()
    flowPressureEnabled = activeBrush?.flowPressureEnabled ?: false
    flowPressureMinimum = activeBrush?.flowPressureMinimum ?: 0f
    flowPressureCurve = activeBrush?.flowPressureCurve.orEmpty()
    flowSecondaryDynamics = activeBrush?.flowSecondaryDynamics ?: BrushSecondaryDynamics()
    spacingPressureEnabled = activeBrush?.spacingPressureEnabled ?: false
    spacingPressureMinimum = activeBrush?.spacingPressureMinimum ?: 0f
    spacingPressureCurve = activeBrush?.spacingPressureCurve.orEmpty()
    spacingSecondaryDynamics = activeBrush?.spacingSecondaryDynamics ?: BrushSecondaryDynamics()
    thicknessPressureEnabled = activeBrush?.thicknessPressureEnabled ?: false
    thicknessPressureMinimum = activeBrush?.thicknessPressureMinimum ?: 0f
    thicknessPressureCurve = activeBrush?.thicknessPressureCurve.orEmpty()
    thicknessSecondaryDynamics = activeBrush?.thicknessSecondaryDynamics ?: BrushSecondaryDynamics()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ObjectElementView(
    element: PageElement,
    page: InkPage,
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
    settings: AppSettings,
    activeBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    localEraser: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var dragX by remember(element.id) { mutableFloatStateOf(0f) }
    var dragY by remember(element.id) { mutableFloatStateOf(0f) }
    var editing by remember(element.id) { mutableStateOf(false) }
    var stickyInkEditing by remember(element.id) { mutableStateOf(false) }
    val objectWidth = (canvasWidth * (element.width / page.width)).coerceIn(54.dp, canvasWidth)
    val objectHeight = (canvasHeight * (element.height / page.height)).coerceIn(42.dp, canvasHeight)
    val gestureModifier = if (editable) Modifier.pointerInput(element.id, canvasWidthPx, canvasHeightPx) {
        detectDragGestures(
            onDragStart = { onSelect() },
            onDragEnd = { onChange(moveElement(element, dragX / canvasWidthPx, dragY / canvasHeightPx)); dragX = 0f; dragY = 0f },
            onDragCancel = { dragX = 0f; dragY = 0f },
        ) { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y }
    } else Modifier
    Box(
        modifier
            .offset(x = canvasWidth * element.x, y = canvasHeight * element.y)
            .size(objectWidth, objectHeight)
            .graphicsLayer(rotationZ = element.rotationDegrees, alpha = element.opacity.coerceIn(.05f, 1f), translationX = dragX, translationY = dragY)
            .then(if (selected) Modifier.border(2.dp, Sage, RoundedCornerShape(5.dp)) else Modifier)
            .then(gestureModifier)
            .combinedClickable(
                enabled = editable,
                onClick = onSelect,
                onDoubleClick = {
                    onSelect()
                    when (element) {
                        is PageElement.Sticky -> stickyInkEditing = true
                        is PageElement.Link -> openLink(context, element.url)
                        else -> Unit
                    }
                },
            ),
    ) {
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
                    if (!element.collapsed && element.strokes.isNotEmpty()) AndroidView(
                        factory = { context -> InkCanvasView(context).apply { isEnabled = false; tool = EditorTool.SELECT } },
                        update = { preview ->
                            preview.setDocumentStrokes(element.strokes, "sticky-preview:${element.id}")
                            preview.setProtectedStrokeIds(element.strokes.mapTo(mutableSetOf(), InkStroke::id))
                        },
                        modifier = Modifier.fillMaxSize(),
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
                element.fillArgb?.let { fill -> when (element.kind) { ShapeKind.ELLIPSE -> drawOval(Color(fill)); ShapeKind.RECTANGLE -> drawRect(Color(fill)); ShapeKind.TRIANGLE -> drawPath(triangle, Color(fill)); ShapeKind.DIAMOND -> drawPath(diamond, Color(fill)); ShapeKind.FREEFORM -> drawPath(freeform, Color(fill)); else -> Unit } }
                when (element.kind) {
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
                if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), "Page image", Modifier.fillMaxSize().then(maskModifier), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Workspace), contentAlignment = Alignment.Center) { Text("Image unavailable", color = MutedInk, fontSize = 11.sp) }
            }
            is PageElement.AudioMarker -> Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7E7E7))) {
                Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playAudio(element.assetPath) }) { Icon(Icons.Default.PlayArrow, "Play audio") }
                    Column(Modifier.weight(1f)) { Text(element.title, maxLines = 1); Text("Audio · ${formatDuration(element.durationMs)}", color = MutedInk, fontSize = 11.sp) }
                }
            }
            is PageElement.Link -> Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7E7E7))) {
                Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White) { Icon(Icons.Outlined.Link, null, Modifier.padding(10.dp), tint = Color(0xFF111111)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(element.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(LinkTarget.displayDestination(element.url), color = MutedInk, fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = { openLink(context, element.url) }) { Icon(Icons.Outlined.OpenInNew, "Open link") }
                }
            }
        }
        if (selected && editable) Surface(Modifier.align(Alignment.TopEnd).offset(y = (-38).dp), shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 4.dp) {
            Row {
                if (element is PageElement.Sticky) {
                    IconButton(onClick = { stickyInkEditing = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Brush, "Write inside sticky note", Modifier.size(18.dp)) }
                    IconButton(onClick = { onChange(element.copy(collapsed = !element.collapsed)) }, modifier = Modifier.size(36.dp)) { Icon(if (element.collapsed) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess, if (element.collapsed) "Expand sticky note" else "Collapse sticky note", Modifier.size(18.dp)) }
                }
                if (element is PageElement.Link) IconButton(onClick = { openLink(context, element.url) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.OpenInNew, "Open link", Modifier.size(18.dp)) }
                IconButton(onClick = { editing = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Tune, "Edit object", Modifier.size(18.dp)) }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.ContentCopy, "Duplicate object", Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Delete, "Delete object", Modifier.size(18.dp)) }
            }
        }
    }
    if (editing) ElementEditorDialog(element, onDismiss = { editing = false }, onSave = { onChange(it); editing = false })
    if (stickyInkEditing && element is PageElement.Sticky) StickyInkEditorDialog(
        sticky = element,
        settings = settings,
        activeBrush = activeBrush,
        brushSize = brushSize,
        brushOpacity = brushOpacity,
        brushColor = brushColor,
        initialLocalEraser = localEraser,
        onChange = { onChange(it) },
        onDismiss = { stickyInkEditing = false },
    )
}

@Composable
private fun StickyInkEditorDialog(
    sticky: PageElement.Sticky,
    settings: AppSettings,
    activeBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    initialLocalEraser: Boolean,
    onChange: (PageElement.Sticky) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(sticky.id) { mutableStateOf(sticky) }
    var tool by remember(sticky.id) { mutableStateOf(EditorTool.PEN) }
    var localEraser by remember(sticky.id) { mutableStateOf(initialLocalEraser) }
    var canvasView by remember(sticky.id) { mutableStateOf<InkCanvasView?>(null) }
    LaunchedEffect(sticky.strokes, sticky.text, sticky.collapsed) { draft = sticky }

    fun persist(strokes: List<InkStroke> = draft.strokes, collapsed: Boolean = draft.collapsed) {
        val updated = draft.copy(strokes = strokes, collapsed = collapsed)
        draft = updated
        onChange(updated)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(.9f).fillMaxHeight(.86f),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Write inside sticky note", fontWeight = FontWeight.SemiBold)
                        Text(activeBrush?.name ?: "System pen", color = MutedInk, fontSize = 11.sp, maxLines = 1)
                    }
                    FilterChip(selected = tool == EditorTool.PEN, onClick = { tool = EditorTool.PEN }, label = { Text("Pen") }, leadingIcon = { Icon(Icons.Outlined.Brush, null, Modifier.size(16.dp)) })
                    FilterChip(selected = tool == EditorTool.HIGHLIGHTER, onClick = { tool = EditorTool.HIGHLIGHTER }, label = { Text("Marker") }, leadingIcon = { Icon(Icons.Outlined.BorderColor, null, Modifier.size(16.dp)) })
                    FilterChip(selected = tool == EditorTool.ERASER, onClick = { tool = EditorTool.ERASER }, label = { Text("Eraser") }, leadingIcon = { Icon(Icons.Outlined.AutoFixOff, null, Modifier.size(16.dp)) })
                    if (tool == EditorTool.ERASER) FilledIconToggleButton(
                        checked = localEraser,
                        onCheckedChange = { localEraser = it },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = Workspace, contentColor = MutedInk, checkedContainerColor = Sage, checkedContentColor = Color.White),
                    ) { Icon(Icons.Outlined.ContentCut, if (localEraser) "Local eraser on" else "Whole-stroke eraser on") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { canvasView?.undo() }) { Icon(Icons.Outlined.Undo, "Undo sticky ink") }
                    IconButton(onClick = { canvasView?.redo() }) { Icon(Icons.Outlined.Redo, "Redo sticky ink") }
                    TextButton(onClick = { persist(strokes = emptyList()) }, enabled = draft.strokes.isNotEmpty()) { Text("Clear ink") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { persist(collapsed = !draft.collapsed) }) { Text(if (draft.collapsed) "Expand on page" else "Collapse on page") }
                    Button(onClick = onDismiss) { Text("Done") }
                }
                HorizontalDivider()
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(draft.colorArgb)).border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(12.dp)),
                ) {
                    Text(draft.text, Modifier.padding(14.dp), color = Color(0xFF333333), maxLines = 10)
                    AndroidView(
                        factory = { context -> InkCanvasView(context).also { canvasView = it } },
                        update = { view ->
                            canvasView = view
                            view.applyBrushConfiguration(tool, settings, activeBrush, brushSize, brushOpacity, brushColor, localEraser)
                            view.activeLayerId = LayerIds.INK
                            view.pageModelWidth = sticky.width
                            view.pageModelHeight = sticky.height
                            view.setDocumentStrokes(draft.strokes, "sticky:${sticky.id}")
                            view.setProtectedStrokeIds(emptySet())
                            view.onStrokeCommitted = { stroke -> persist(strokes = draft.strokes + stroke) }
                            view.onStrokeRemoved = { strokeId -> persist(strokes = draft.strokes.filterNot { it.id == strokeId }) }
                            view.onStrokesReplaced = { removedIds, replacements, indices ->
                                persist(strokes = replaceStickyStrokes(draft.strokes, removedIds, replacements, indices))
                            }
                            view.onUndoGesture = { view.undo() }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text("Stylus writes. Fingers navigate unless finger drawing is enabled in Settings. Changes are saved after every completed stroke.", color = MutedInk, fontSize = 11.sp)
            }
        }
    }
}

private fun replaceStickyStrokes(
    current: List<InkStroke>,
    removedIds: List<String>,
    replacements: List<InkStroke>,
    replacementIndices: List<Int>,
): List<InkStroke> {
    val replacedIds = removedIds.toSet() + replacements.map(InkStroke::id)
    val result = current.filterNot { it.id in replacedIds }.toMutableList()
    if (replacementIndices.size == replacements.size) {
        replacements.zip(replacementIndices).sortedBy { (_, index) -> index }.forEach { (stroke, index) ->
            result.add(index.coerceIn(0, result.size), stroke)
        }
    } else {
        result += replacements
    }
    return result
}

@Composable
private fun ElementEditorDialog(element: PageElement, onDismiss: () -> Unit, onSave: (PageElement) -> Unit) {
    var draft by remember(element.id) { mutableStateOf(element) }
    val normalizedDraftLink = (draft as? PageElement.Link)?.let { LinkTarget.normalize(it.url) }
    val canSave = draft !is PageElement.Link || normalizedDraftLink != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Object properties") },
        text = { LazyColumn(Modifier.widthIn(min = 340.dp, max = 520.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val current = draft) {
                is PageElement.Text -> {
                    item { OutlinedTextField(current.text, { draft = current.copy(text = it) }, label = { Text("Text") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
                    item { BrushSettingSlider("Text size", current.textSize, 16f..160f) { draft = current.copy(textSize = it) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = current.bold, onClick = { draft = current.copy(bold = !current.bold) }, label = { Text("Bold", fontWeight = FontWeight.Bold) })
                        FilterChip(selected = current.italic, onClick = { draft = current.copy(italic = !current.italic) }, label = { Text("Italic", fontStyle = FontStyle.Italic) })
                        FilterChip(selected = current.underline, onClick = { draft = current.copy(underline = !current.underline) }, label = { Text("Underline", textDecoration = TextDecoration.Underline) })
                    } }
                    item { Text("Alignment", color = MutedInk, fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { InkTextAlignment.entries.forEach { alignment -> FilterChip(selected = current.alignment == alignment, onClick = { draft = current.copy(alignment = alignment) }, label = { Text(alignment.name.lowercase().replaceFirstChar(Char::uppercase)) }) } } }
                    item { Text("List style", color = MutedInk, fontSize = 11.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextListStyle.entries.forEach { style -> item { FilterChip(selected = current.listStyle == style, onClick = { draft = current.copy(listStyle = style) }, label = { Text(style.name.lowercase().replaceFirstChar(Char::uppercase)) }) } } } }
                    item { BrushSettingSlider("Line spacing", current.lineSpacing, .8f..2f) { draft = current.copy(lineSpacing = it) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Color", Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt()) { draft = current.copy(colorArgb = it.toUInt().toLong()) } } }
                }
                is PageElement.Sticky -> {
                    item { OutlinedTextField(current.text, { draft = current.copy(text = it) }, label = { Text("Note") }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
                    item { SettingSwitch("Collapsed", "Show only the first line.", current.collapsed) { draft = current.copy(collapsed = it) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Color", Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt()) { draft = current.copy(colorArgb = it.toUInt().toLong()) } } }
                }
                is PageElement.Shape -> {
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShapeKind.entries.forEach { kind -> item { FilterChip(selected = current.kind == kind, onClick = {
                        val path = if (kind == ShapeKind.FREEFORM && current.path.size < 3) listOf(ShapeVertex(0f, 0f), ShapeVertex(1f, 0f), ShapeVertex(1f, 1f), ShapeVertex(0f, 1f)) else current.path
                        draft = current.copy(kind = kind, path = path)
                    }, label = { Text(kind.name.lowercase().replaceFirstChar(Char::uppercase)) }) } } } }
                    item { BrushSettingSlider("Stroke width", current.strokeWidth, 1f..40f) { draft = current.copy(strokeWidth = it) } }
                    item { SettingSwitch("Fill", "Adds a translucent shape fill.", current.fillArgb != null) { draft = current.copy(fillArgb = if (it) 0x33111111 else null) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Stroke color", Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt()) { draft = current.copy(colorArgb = it.toUInt().toLong()) } } }
                }
                is PageElement.Image -> {
                    item { Text("Image source is retained unchanged. Crop and mask remain reversible and are included in editable backups.", color = MutedInk, fontSize = 12.sp) }
                    item { Text("Mask", color = MutedInk, fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ImageMask.entries.forEach { mask -> FilterChip(selected = current.mask == mask, onClick = { draft = current.copy(mask = mask) }, label = { Text(mask.name.lowercase().replaceFirstChar(Char::uppercase)) }) } } }
                    if (current.mask == ImageMask.ROUNDED) item { BrushSettingSlider("Corner radius", current.cornerRadius, 0f..200f) { draft = current.copy(cornerRadius = it) } }
                    item { Text("Crop edges", color = MutedInk, fontSize = 11.sp) }
                    item { BrushSettingSlider("Left", current.crop.left, 0f..0.95f) { draft = current.copy(crop = current.crop.copy(left = it).normalized()) } }
                    item { BrushSettingSlider("Top", current.crop.top, 0f..0.95f) { draft = current.copy(crop = current.crop.copy(top = it).normalized()) } }
                    item { BrushSettingSlider("Right", current.crop.right, 0.05f..1f) { draft = current.copy(crop = current.crop.copy(right = it).normalized()) } }
                    item { BrushSettingSlider("Bottom", current.crop.bottom, 0.05f..1f) { draft = current.copy(crop = current.crop.copy(bottom = it).normalized()) } }
                    item { OutlinedButton(onClick = { draft = current.copy(crop = ImageCrop()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("Reset crop") } }
                }
                is PageElement.AudioMarker -> {
                    item { OutlinedTextField(current.title, { draft = current.copy(title = it) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { Text("Duration ${formatDuration(current.durationMs)} · audio is included in editable backups", color = MutedInk, fontSize = 12.sp) }
                }
                is PageElement.Link -> {
                    item { OutlinedTextField(current.title, { draft = current.copy(title = it) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(current.url, { draft = current.copy(url = it) }, label = { Text("Web, email, or phone address") }, isError = LinkTarget.normalize(current.url) == null, supportingText = { Text(if (LinkTarget.normalize(current.url) == null) "Use a valid http, https, mailto, or tel address." else LinkTarget.displayDestination(current.url)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                }
            }
            item { BrushSettingSlider("Opacity", draft.opacity, .05f..1f) { draft = setElementOpacity(draft, it) } }
            item { BrushSettingSlider("Rotation", draft.rotationDegrees, -180f..180f) { draft = setElementRotation(draft, it) } }
            item { BrushSettingSlider("Width", draft.width, 40f..1600f) { draft = setElementSize(draft, it, draft.height) } }
            item { BrushSettingSlider("Height", draft.height, 40f..2263f) { draft = setElementSize(draft, draft.width, it) } }
        } },
        confirmButton = { Button(enabled = canSave, onClick = {
            val saved = (draft as? PageElement.Link)?.let { link ->
                val url = normalizedDraftLink ?: return@Button
                link.copy(title = link.title.trim().ifBlank { LinkTarget.displayDestination(url, 48) }, url = url)
            } ?: draft
            onSave(saved)
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun moveElement(element: PageElement, dx: Float, dy: Float): PageElement = when (element) {
    is PageElement.Text -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Sticky -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Shape -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Image -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.AudioMarker -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Link -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
}

private fun resizeElement(element: PageElement, factor: Float): PageElement = setElementSize(element, (element.width * factor).coerceIn(40f, 1600f), (element.height * factor).coerceIn(40f, 2263f))
private fun rotateElement(element: PageElement, delta: Float): PageElement = setElementRotation(element, ((element.rotationDegrees + delta + 180f) % 360f) - 180f)
private fun setElementSize(element: PageElement, width: Float, height: Float): PageElement = when (element) { is PageElement.Text -> element.copy(width = width, height = height); is PageElement.Sticky -> element.copy(width = width, height = height); is PageElement.Shape -> element.copy(width = width, height = height); is PageElement.Image -> element.copy(width = width, height = height); is PageElement.AudioMarker -> element.copy(width = width, height = height); is PageElement.Link -> element.copy(width = width, height = height) }
private fun setElementRotation(element: PageElement, value: Float): PageElement = when (element) { is PageElement.Text -> element.copy(rotationDegrees = value); is PageElement.Sticky -> element.copy(rotationDegrees = value); is PageElement.Shape -> element.copy(rotationDegrees = value); is PageElement.Image -> element.copy(rotationDegrees = value); is PageElement.AudioMarker -> element.copy(rotationDegrees = value); is PageElement.Link -> element.copy(rotationDegrees = value) }
private fun setElementOpacity(element: PageElement, value: Float): PageElement = when (element) { is PageElement.Text -> element.copy(opacity = value); is PageElement.Sticky -> element.copy(opacity = value); is PageElement.Shape -> element.copy(opacity = value); is PageElement.Image -> element.copy(opacity = value); is PageElement.AudioMarker -> element.copy(opacity = value); is PageElement.Link -> element.copy(opacity = value) }

private fun openLink(context: android.content.Context, rawUrl: String) {
    val url = LinkTarget.normalize(rawUrl)
    if (url == null) {
        Toast.makeText(context, "This link is not valid or supported.", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
    }.onFailure {
        Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show()
    }
}

private fun playAudio(path: String) {
    var player: android.media.MediaPlayer? = null
    runCatching {
        player = android.media.MediaPlayer().apply { setDataSource(path); prepare(); setOnCompletionListener { completed -> completed.release() }; start() }
    }.onFailure { player?.release() }
}

private fun formatDuration(durationMs: Long): String { val total = durationMs / 1000; return "%d:%02d".format(total / 60, total % 60) }

@Composable
private fun ToolDock(tool: EditorTool, onTool: (EditorTool) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onScale: () -> Unit, onRotate: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onClipboardCopy: () -> Unit, onPaste: () -> Unit, onDelete: () -> Unit, selectionShape: SelectionShape, selectionMode: SelectionMode, selectionFilter: SelectionFilter, onSelectionShape: (SelectionShape) -> Unit, onSelectionMode: (SelectionMode) -> Unit, onSelectionFilter: (SelectionFilter) -> Unit, localEraser: Boolean, onToggleLocalEraser: () -> Unit, nonDestructiveEraser: Boolean, onToggleNonDestructiveEraser: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit, vertical: Boolean, onToggleOrientation: () -> Unit, onResetPosition: () -> Unit, onDock: (ToolbarDock) -> Unit, brushSize: Float, brushOpacity: Float, brushColor: Int, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit, onColor: (Int) -> Unit, modifier: Modifier = Modifier) {
    var moreToolsOpen by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(false) }
    val dragModifier = Modifier.size(32.dp).pointerInput(vertical) {
        detectDragGestures(
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd,
        ) { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
    }.padding(6.dp)
    Surface(modifier.shadow(10.dp, RoundedCornerShape(18.dp)), shape = RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
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
                selectionShape = selectionShape,
                selectionMode = selectionMode,
                selectionFilter = selectionFilter,
                onSelectionShape = onSelectionShape,
                onSelectionMode = onSelectionMode,
                onSelectionFilter = onSelectionFilter,
                localEraser = localEraser,
                onToggleLocalEraser = onToggleLocalEraser,
                nonDestructiveEraser = nonDestructiveEraser,
                onToggleNonDestructiveEraser = onToggleNonDestructiveEraser,
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
                onSize = onSize,
                onOpacity = onOpacity,
                onColor = onColor,
            )
        }
        if (vertical) Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
        else Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun ToolDockItems(tool: EditorTool, onTool: (EditorTool) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onScale: () -> Unit, onRotate: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onClipboardCopy: () -> Unit, onPaste: () -> Unit, onDelete: () -> Unit, selectionShape: SelectionShape, selectionMode: SelectionMode, selectionFilter: SelectionFilter, onSelectionShape: (SelectionShape) -> Unit, onSelectionMode: (SelectionMode) -> Unit, onSelectionFilter: (SelectionFilter) -> Unit, localEraser: Boolean, onToggleLocalEraser: () -> Unit, nonDestructiveEraser: Boolean, onToggleNonDestructiveEraser: () -> Unit, dragModifier: Modifier, vertical: Boolean, moreToolsOpen: Boolean, onMoreToolsOpen: (Boolean) -> Unit, controlsOpen: Boolean, onControlsOpen: (Boolean) -> Unit, onToggleOrientation: () -> Unit, onResetPosition: () -> Unit, onDock: (ToolbarDock) -> Unit, brushSize: Float, brushOpacity: Float, brushColor: Int, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit, onColor: (Int) -> Unit) {
    Icon(Icons.Outlined.DragIndicator, "Drag toolbar", modifier = dragModifier, tint = MutedInk)
    ToolButton(EditorTool.PEN, Icons.Outlined.Edit, "Pen", tool, onTool)
    ToolButton(EditorTool.HIGHLIGHTER, Icons.Outlined.BorderColor, "Mark", tool, onTool)
    ToolButton(EditorTool.ERASER, Icons.Outlined.AutoFixOff, "Eraser", tool, onTool)
    ToolButton(EditorTool.SELECT, Icons.Outlined.Gesture, "Select", tool, onTool)
    Box {
        CompactToolbarIconButton(onClick = { onMoreToolsOpen(true) }, description = "More tools", selected = tool in setOf(EditorTool.SHAPE, EditorTool.TEXT, EditorTool.NOTE, EditorTool.LINK, EditorTool.IMAGE, EditorTool.AUDIO)) { Icon(Icons.Outlined.Apps, null, Modifier.size(20.dp)) }
        DropdownMenu(expanded = moreToolsOpen, onDismissRequest = { onMoreToolsOpen(false) }) {
            listOf(
                Triple(EditorTool.SHAPE, Icons.Outlined.Category, "Shape"),
                Triple(EditorTool.TEXT, Icons.Outlined.Title, "Text"),
                Triple(EditorTool.NOTE, Icons.Outlined.StickyNote2, "Note"),
                Triple(EditorTool.LINK, Icons.Outlined.Link, "Link"),
                Triple(EditorTool.IMAGE, Icons.Outlined.Image, "Image"),
                Triple(EditorTool.AUDIO, Icons.Outlined.Mic, "Audio"),
            ).forEach { (value, icon, label) ->
                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { onMoreToolsOpen(false); onTool(value) })
            }
        }
    }
    Box {
        CompactToolbarIconButton(onClick = { onControlsOpen(true) }, description = "Tool controls") { Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp)) }
        DropdownMenu(expanded = controlsOpen, onDismissRequest = { onControlsOpen(false) }, modifier = Modifier.widthIn(min = 300.dp, max = 360.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${tool.name.lowercase().replaceFirstChar(Char::uppercase)} controls", fontWeight = FontWeight.SemiBold)
                if (tool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Size", Modifier.weight(1f), color = MutedInk)
                        IconButton(onClick = { onSize(brushSize - maxOf(.5f, brushSize * .12f)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Remove, "Smaller") }
                        Text(if (brushSize < 10f) "%.1f".format(brushSize) else brushSize.toInt().toString(), Modifier.width(38.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { onSize(brushSize + maxOf(.5f, brushSize * .12f)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Add, "Larger") }
                    }
                    if (tool != EditorTool.ERASER) {
                        Text("Opacity  ${(brushOpacity * 100).toInt()}%", color = MutedInk, fontSize = 12.sp)
                        Slider(value = brushOpacity, onValueChange = onOpacity, valueRange = .05f..1f)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Color", Modifier.weight(1f), color = MutedInk); BrushColorPicker(brushColor, onColor) }
                    }
                }
                if (tool == EditorTool.ERASER) {
                    SettingSwitch("Local eraser", "Destructively split stroke segments.", localEraser) { onToggleLocalEraser() }
                    SettingSwitch("Mask eraser", "Hide ink without deleting or splitting the original stroke.", nonDestructiveEraser) { onToggleNonDestructiveEraser() }
                }
                if (tool == EditorTool.SELECT) {
                    Text("Selection", color = MutedInk, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SelectionShape.entries.forEach { shape -> FilterChip(selected = selectionShape == shape, onClick = { onSelectionShape(shape) }, label = { Text(if (shape == SelectionShape.FREEFORM) "Freeform" else "Rectangle", fontSize = 10.sp) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SelectionMode.entries.forEach { mode -> FilterChip(selected = selectionMode == mode, onClick = { onSelectionMode(mode) }, label = { Text(when (mode) { SelectionMode.REPLACE -> "New"; SelectionMode.ADD -> "Add"; SelectionMode.SUBTRACT -> "Subtract"; SelectionMode.INTERSECT -> "Intersect" }, fontSize = 10.sp) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SelectionFilter.entries.forEach { filter -> FilterChip(selected = selectionFilter == filter, onClick = { onSelectionFilter(filter) }, label = { Text(when (filter) { SelectionFilter.ALL -> "All"; SelectionFilter.ACTIVE_LAYER -> "Layer"; SelectionFilter.CURRENT_BRUSH -> "Brush"; SelectionFilter.CURRENT_COLOR -> "Color" }, fontSize = 10.sp) }) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CompactToolbarIconButton(onScale, "Scale selection") { Icon(Icons.Outlined.ZoomOutMap, null) }
                        CompactToolbarIconButton(onRotate, "Rotate selection") { Icon(Icons.Outlined.RotateRight, null) }
                        CompactToolbarIconButton(onCopy, "Duplicate selection") { Icon(Icons.Outlined.ControlPointDuplicate, null) }
                        CompactToolbarIconButton(onDelete, "Delete selection") { Icon(Icons.Outlined.Delete, null) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CompactToolbarIconButton(onCut, "Cut ink selection") { Icon(Icons.Outlined.ContentCut, null) }
                        CompactToolbarIconButton(onClipboardCopy, "Copy ink selection to clipboard") { Icon(Icons.Outlined.ContentCopy, null) }
                        CompactToolbarIconButton(onPaste, "Paste ink selection") { Icon(Icons.Outlined.ContentPaste, null) }
                    }
                }
                HorizontalDivider()
                Text("Palette position", color = MutedInk, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ToolbarDock.entries.forEach { dock -> AssistChip(onClick = { onDock(dock) }, label = { Text(dock.name.lowercase().replaceFirstChar(Char::uppercase), fontSize = 10.sp) }) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToggleOrientation, modifier = Modifier.weight(1f)) { Icon(if (vertical) Icons.Outlined.SwapHoriz else Icons.Outlined.SwapVert, null); Spacer(Modifier.width(5.dp)); Text(if (vertical) "Horizontal" else "Vertical") }
                    OutlinedButton(onClick = onResetPosition, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CenterFocusStrong, null); Spacer(Modifier.width(5.dp)); Text("Reset") }
                }
            }
        }
    }
    CompactToolbarIconButton(onClick = onUndo, description = "Undo") { Icon(Icons.Outlined.Undo, null, Modifier.size(20.dp)) }
    CompactToolbarIconButton(onClick = onRedo, description = "Redo") { Icon(Icons.Outlined.Redo, null, Modifier.size(20.dp)) }
}

@Composable
private fun CompactToolbarIconButton(onClick: () -> Unit, description: String, selected: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) Sage else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) Color.White else Ink) { Box(Modifier.semantics { contentDescription = description }) { content() } }
    }
}

@Composable
private fun BrushColorPicker(color: Int, onColor: (Int) -> Unit) {
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
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)).border(1.dp, OutlineSoft, CircleShape).clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.widthIn(min = 280.dp, max = 420.dp).padding(10.dp)) {
                Text("Colors", fontSize = 12.sp, color = MutedInk)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { items(colors) { value -> Box(Modifier.size(30.dp).clip(CircleShape).background(Color(value)).border(if (value == color) 3.dp else 1.dp, if (value == color) Sage else OutlineSoft, CircleShape).clickable { onColor(value); open = false }) } }
                TextButton(onClick = { open = false; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Palette, null); Spacer(Modifier.width(8.dp)); Text("Custom color and palette") }
            }
        }
    }
    if (editorOpen) AlertDialog(
        onDismissRequest = { editorOpen = false },
        title = { Text("Color and palette") },
        text = {
            LazyColumn(Modifier.widthIn(min = 340.dp, max = 520.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(54.dp).clip(CircleShape).background(Color(customColor)).border(1.dp, OutlineSoft, CircleShape)); Spacer(Modifier.width(12.dp)); Text("#${customColor.toUInt().toString(16).padStart(8, '0').uppercase()}") } }
                item { Text("Hue  ${hue.toInt()}°"); Slider(hue, { hue = it }, valueRange = 0f..360f) }
                item { Text("Saturation  ${(saturation * 100).toInt()}%"); Slider(saturation, { saturation = it }, valueRange = 0f..1f) }
                item { Text("Brightness  ${(brightness * 100).toInt()}%"); Slider(brightness, { brightness = it }, valueRange = 0f..1f) }
                item { OutlinedButton(onClick = { customPalette = paletteStore.add(customColor) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add current color to palette") } }
                if (customPalette.isNotEmpty()) item {
                    Text("Saved palette", color = MutedInk, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(customPalette, key = { it }) { value ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(value)).border(1.dp, OutlineSoft, CircleShape).clickable { val values = FloatArray(3); AndroidColor.colorToHSV(value, values); hue = values[0]; saturation = values[1]; brightness = values[2] })
                                IconButton(onClick = { customPalette = paletteStore.remove(value) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, "Remove color", modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onColor(customColor); editorOpen = false }) { Text("Use color") } },
        dismissButton = { TextButton(onClick = { editorOpen = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ToolButton(value: EditorTool, icon: ImageVector, label: String, selected: EditorTool, onTool: (EditorTool) -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (value == selected) Sage else Color.Transparent).clickable { onTool(value) }, contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(20.dp), tint = if (value == selected) Color.White else Ink)
    }
}

@Composable
private fun LayerPanel(
    page: InkPage,
    activeLayerId: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onRename: (String, String) -> Unit,
    onVisibility: (String) -> Unit,
    onLock: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDuplicate: (String) -> Unit,
    onMergeDown: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var renameLayer by remember { mutableStateOf<InkLayer?>(null) }
    var renameValue by remember { mutableStateOf("") }
    Column(Modifier.width(300.dp).fillMaxHeight().background(Color(0xFFF7F7F7)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Layers", fontSize = 20.sp)
                Text("Choose an unlocked layer to write into it", fontSize = 11.sp, color = MutedInk)
            }
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add layer") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(page.layers.asReversed(), key = { it.id }) { layer ->
                var menu by remember { mutableStateOf(false) }
                val editable = layer.visible && !layer.locked && layer.role != LayerRole.BACKGROUND
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = editable) { onSelect(layer.id) },
                    colors = CardDefaults.cardColors(containerColor = if (layer.id == activeLayerId) SageContainer else Color.White),
                    border = if (layer.id == activeLayerId) CardDefaults.outlinedCardBorder().copy(width = 2.dp, brush = androidx.compose.ui.graphics.SolidColor(Sage)) else CardDefaults.outlinedCardBorder(),
                ) {
                    Row(Modifier.heightIn(min = 54.dp).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (layer.role) { LayerRole.BACKGROUND -> Icons.Outlined.Image; LayerRole.HIGHLIGHT -> Icons.Outlined.BorderColor; LayerRole.OBJECTS -> Icons.Outlined.Widgets; else -> Icons.Outlined.Layers },
                            contentDescription = null,
                            tint = if (layer.visible) Ink else MutedInk,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(layer.name, maxLines = 1, color = if (layer.visible) Ink else MutedInk)
                            Text(layer.role.name.lowercase().replaceFirstChar(Char::uppercase), fontSize = 10.sp, color = MutedInk)
                        }
                        IconButton(onClick = { onVisibility(layer.id) }) { Icon(if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (layer.visible) "Hide ${layer.name}" else "Show ${layer.name}") }
                        IconButton(onClick = { onLock(layer.id) }) { Icon(if (layer.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, if (layer.locked) "Unlock ${layer.name}" else "Lock ${layer.name}") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Layer actions") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renameLayer = layer; renameValue = layer.name })
                                DropdownMenuItem(text = { Text("Move up") }, enabled = page.layers.last().id != layer.id, onClick = { menu = false; onMove(layer.id, 1) })
                                DropdownMenuItem(text = { Text("Move down") }, enabled = page.layers.first().id != layer.id, onClick = { menu = false; onMove(layer.id, -1) })
                                if (layer.role != LayerRole.BACKGROUND) DropdownMenuItem(text = { Text("Duplicate layer") }, onClick = { menu = false; onDuplicate(layer.id) })
                                if (layer.role != LayerRole.BACKGROUND) DropdownMenuItem(text = { Text("Merge down") }, enabled = LayerOperations.canMergeDown(page, layer.id), onClick = { menu = false; onMergeDown(layer.id) })
                                if (layer.role == LayerRole.CUSTOM) DropdownMenuItem(text = { Text("Delete layer") }, onClick = { menu = false; onDelete(layer.id) })
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
            title = { Text("Rename layer") },
            text = { OutlinedTextField(renameValue, { renameValue = it }, singleLine = true, label = { Text("Layer name") }) },
            confirmButton = { TextButton(onClick = { onRename(layer.id, renameValue); renameLayer = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameLayer = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BrushPanel(
    brushes: List<ImportedBrush>,
    selectedBrushId: String?,
    onBrushSelected: (ImportedBrush) -> Unit,
    onImportBrushes: () -> Unit,
    onExportBrush: (ImportedBrush) -> Unit,
    onUpdateBrush: (ImportedBrush) -> Unit,
    onDeleteBrush: (String) -> Unit,
) {
    val context = LocalContext.current
    val historyStore = remember { BrushVersionHistoryStore(context) }
    var reportBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var editBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var historyBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var moveBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var manageGroups by remember { mutableStateOf(false) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var format by remember { mutableStateOf<BrushFormat?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    val groups = BrushGroupOperations.names(brushes)
    LaunchedEffect(groups, selectedGroup) {
        if (selectedGroup != null && selectedGroup != UNGROUPED_BRUSH_FILTER && groups.none { it.equals(selectedGroup, true) }) selectedGroup = null
    }
    val visibleBrushes = brushes.filter { brush ->
        val normalizedGroup = BrushGroupOperations.normalize(brush.groupName)
        val groupMatches = when (selectedGroup) {
            null -> true
            UNGROUPED_BRUSH_FILTER -> normalizedGroup.isBlank()
            else -> normalizedGroup.equals(selectedGroup, true)
        }
        (showHidden || !brush.hidden) && (!favoritesOnly || brush.favorite) && groupMatches && (format == null || brush.format == format) && (search.isBlank() || brush.name.contains(search, true) || normalizedGroup.contains(search, true) || brush.tags.any { it.contains(search, true) })
    }.sortedWith(compareByDescending<ImportedBrush> { it.favorite }.thenByDescending { it.lastUsedAt }.thenBy { it.name.lowercase() })
    val brushSections = visibleBrushes.groupBy { BrushGroupOperations.normalize(it.groupName).ifBlank { "Ungrouped" } }
        .toList().sortedWith(compareBy<Pair<String, List<ImportedBrush>>> { it.first == "Ungrouped" }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first })
    Column(Modifier.width(330.dp).fillMaxHeight().background(Color(0xFFF7F7F7)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Brush Library", fontSize = 20.sp, modifier = Modifier.weight(1f)); IconButton(onClick = onImportBrushes) { Icon(Icons.Outlined.Add, "Import") } }
        Text("Imported presets · compact view", fontSize = 11.sp, color = MutedInk)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(search, { search = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Search brushes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            item { FilterChip(selected = format == null, onClick = { format = null }, label = { Text("All") }) }
            BrushFormat.entries.filter { it !in setOf(BrushFormat.SYSTEM, BrushFormat.UNKNOWN) }.forEach { source -> item { FilterChip(selected = format == source, onClick = { format = source }, label = { Text(source.label) }) } }
            item { FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("Favorites") }, leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(16.dp)) }) }
            item { FilterChip(selected = showHidden, onClick = { showHidden = !showHidden }, label = { Text("Hidden") }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GROUPS", fontSize = 10.sp, color = MutedInk, modifier = Modifier.weight(1f))
            IconButton(onClick = { manageGroups = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.DriveFileRenameOutline, "Manage brush groups", Modifier.size(17.dp)) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
            item { FilterChip(selected = selectedGroup == null, onClick = { selectedGroup = null }, label = { Text("All") }) }
            item { FilterChip(selected = selectedGroup == UNGROUPED_BRUSH_FILTER, onClick = { selectedGroup = UNGROUPED_BRUSH_FILTER }, label = { Text("Ungrouped") }) }
            groups.forEach { group -> item(group) { FilterChip(selected = selectedGroup?.equals(group, true) == true, onClick = { selectedGroup = group }, label = { Text(group, maxLines = 1) }) } }
        }
        if (brushes.isNotEmpty()) {
            Text("BRUSHES (${visibleBrushes.size}/${brushes.size})", fontSize = 11.sp, color = MutedInk)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                brushSections.forEach { (group, sectionBrushes) ->
                    item("group:$group") { Text(group.uppercase(), color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp, start = 2.dp)) }
                    items(sectionBrushes, key = { it.id }) { brush ->
                        ImportedBrushRow(
                            brush = brush,
                            selected = brush.id == selectedBrushId,
                            onSelect = { onBrushSelected(brush) },
                            onReport = { reportBrush = brush },
                            onEdit = { editBrush = brush },
                            onHistory = { historyBrush = brush },
                            onMove = { moveBrush = brush },
                            onExport = { onExportBrush(brush) },
                            onFavorite = { onUpdateBrush(brush.copy(favorite = !brush.favorite)) },
                            onHide = { onUpdateBrush(brush.copy(hidden = !brush.hidden)) },
                            onDelete = { historyStore.clear(brush.id); historyRevision++; onDeleteBrush(brush.id) },
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(18.dp)); Text("SOURCES", fontSize = 11.sp, color = MutedInk)
            Spacer(Modifier.height(10.dp)); listOf("System Brushes" to 12, "ABR" to 0, "CSP / SUT" to 0, "Procreate" to 0, "My Brushes" to 0).forEach { (name, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(name, Modifier.weight(1f)); Text(count.toString(), color = MutedInk, fontSize = 11.sp) }
            }
            Spacer(Modifier.weight(1f))
        }
        FilledTonalButton(onClick = onImportBrushes, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Import Brushes") }
    }
    reportBrush?.let { brush ->
        AlertDialog(
            onDismissRequest = { reportBrush = null },
            confirmButton = { TextButton(onClick = { reportBrush = null }) { Text("Done") } },
            title = { Text(brush.name) },
            text = {
                Column {
                    Text("${brush.format.label} · ${brush.compatibility.label}", color = Sage)
                    Spacer(Modifier.height(12.dp))
                    brush.report.forEach { Text("• $it", modifier = Modifier.padding(vertical = 3.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text("The original source remains unchanged and can be reconverted by a future parser.", color = MutedInk, fontSize = 12.sp)
                }
            },
        )
    }
    editBrush?.let { brush ->
        BrushEditorDialog(
            brush,
            onDismiss = { editBrush = null },
            onSave = { updated ->
                if (updated != brush) {
                    historyStore.record(brush)
                    historyRevision++
                    onUpdateBrush(updated)
                }
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
            groups = groups,
            onDismiss = { moveBrush = null },
            onMove = { group ->
                val updated = BrushGroupOperations.assign(current, group)
                if (updated != current) onUpdateBrush(updated)
                selectedGroup = updated.groupName.takeIf(String::isNotBlank) ?: UNGROUPED_BRUSH_FILTER
                moveBrush = null
            },
        )
    }
    if (manageGroups) {
        BrushGroupManagerDialog(
            groups = groups,
            onDismiss = { manageGroups = false },
            onRename = { oldName, newName ->
                val updated = BrushGroupOperations.rename(brushes, oldName, newName)
                updated.zip(brushes).filter { (next, previous) -> next != previous }.forEach { (next, _) -> onUpdateBrush(next) }
                val normalized = BrushGroupOperations.normalize(newName)
                selectedGroup = normalized.takeIf(String::isNotBlank) ?: UNGROUPED_BRUSH_FILTER
                manageGroups = false
            },
        )
    }
}

@Composable
private fun BrushRow(name: String, color: Color, selected: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(if (selected) 2.dp else 1.dp, if (selected) Sage else OutlineSoft, RoundedCornerShape(10.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(52.dp).height(18.dp)) { drawLine(color, Offset(4f, size.height / 2), Offset(size.width - 4f, size.height / 2), strokeWidth = if (name.contains("Marker")) 9f else 3f, cap = StrokeCap.Round) }
        Spacer(Modifier.width(12.dp)); Text(name, color = if (selected) Sage else Ink)
    }
}

@Composable
private fun BrushMoveToGroupDialog(brush: ImportedBrush, groups: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var groupName by remember(brush.id) { mutableStateOf(brush.groupName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move brush") },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 440.dp)) {
                Text(brush.name, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(BrushGroupOperations.MAX_NAME_LENGTH) },
                    label = { Text("Group") },
                    supportingText = { Text("Enter a new name or choose an existing group") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (groups.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp)) {
                    groups.forEach { group -> item(group) { FilterChip(selected = groupName.equals(group, true), onClick = { groupName = group }, label = { Text(group, maxLines = 1) }) } }
                }
                TextButton(onClick = { onMove("") }) { Text("Move to Ungrouped") }
            }
        },
        confirmButton = { Button(onClick = { onMove(groupName) }, enabled = BrushGroupOperations.normalize(groupName).isNotBlank()) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BrushGroupManagerDialog(groups: List<String>, onDismiss: () -> Unit, onRename: (String, String) -> Unit) {
    var selected by remember(groups) { mutableStateOf(groups.firstOrNull().orEmpty()) }
    var replacement by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage groups") },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 440.dp)) {
                if (groups.isEmpty()) {
                    Text("No groups yet. Move a brush to a new group or enter a group in Brush Studio.", color = MutedInk)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        groups.forEach { group -> item(group) { FilterChip(selected = selected == group, onClick = { selected = group }, label = { Text(group, maxLines = 1) }) } }
                    }
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it.take(BrushGroupOperations.MAX_NAME_LENGTH) },
                        label = { Text("Rename selected group") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Renaming to an existing group merges both groups.", color = MutedInk, fontSize = 11.sp)
                    TextButton(onClick = { onRename(selected, "") }) { Text("Remove group and keep brushes") }
                }
            }
        },
        confirmButton = { Button(onClick = { onRename(selected, replacement) }, enabled = selected.isNotBlank() && BrushGroupOperations.normalize(replacement).isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
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
        title = { Text("Version history") },
        text = {
            Column(Modifier.widthIn(min = 340.dp, max = 480.dp)) {
                Text(brush.name, fontSize = 13.sp)
                Text("Inkloom saves the previous preset before every brush edit.", color = MutedInk, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                if (versions.isEmpty()) {
                    Text("No previous versions yet. Edit and save this brush to create one.", color = MutedInk)
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
                                            "${version.snapshot.defaultSize.oneDecimal()} px · ${(version.snapshot.defaultOpacity * 100f).toInt()}% opacity · ${version.snapshot.spacing.oneDecimal()} spacing",
                                            color = MutedInk,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                        )
                                    }
                                    TextButton(onClick = { onRestore(version) }) { Text("Restore") }
                                    IconButton(onClick = { onDelete(version) }) { Icon(Icons.Outlined.Delete, "Delete this brush version") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun Float.oneDecimal(): String = String.format(java.util.Locale.ROOT, "%.1f", this)

@Composable
private fun ImportedBrushRow(brush: ImportedBrush, selected: Boolean, onSelect: () -> Unit, onReport: () -> Unit, onEdit: () -> Unit, onHistory: () -> Unit, onMove: () -> Unit, onExport: () -> Unit, onFavorite: () -> Unit, onHide: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = if (selected) SageContainer else Workspace),
        border = if (selected) CardDefaults.outlinedCardBorder().copy(width = 2.dp, brush = androidx.compose.ui.graphics.SolidColor(Sage)) else CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(8.dp).heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
            BrushTipPreview(brush, Modifier.size(52.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(brush.name, maxLines = 1, fontSize = 13.sp)
                Text(listOf(brush.format.label, brush.groupName.takeIf(String::isNotBlank), brush.compatibility.label).filterNotNull().joinToString(" · "), color = MutedInk, fontSize = 10.sp, maxLines = 1)
                Canvas(Modifier.fillMaxWidth().height(12.dp)) { drawLine(Sage, Offset(2f, size.height / 2), Offset(size.width - 2f, size.height / 2), strokeWidth = (brush.defaultSize / 8f).coerceIn(2f, 10f), cap = StrokeCap.Round) }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.Tune, "Edit brush", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onFavorite, modifier = Modifier.size(30.dp)) { Icon(if (brush.favorite) Icons.Default.Star else Icons.Outlined.StarBorder, if (brush.favorite) "Remove favorite" else "Favorite", modifier = Modifier.size(18.dp), tint = if (brush.favorite) Sage else Ink) }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.MoreVert, "Brush actions", modifier = Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Conversion report") }, onClick = { menu = false; onReport() })
                    DropdownMenuItem(text = { Text("Version history") }, onClick = { menu = false; onHistory() })
                    DropdownMenuItem(text = { Text("Move to group") }, onClick = { menu = false; onMove() })
                    DropdownMenuItem(text = { Text("Export Inkloom preset") }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text(if (brush.hidden) "Unhide" else "Hide") }, onClick = { menu = false; onHide() })
                    DropdownMenuItem(text = { Text("Remove from library") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun BrushTipPreview(brush: ImportedBrush, modifier: Modifier = Modifier) {
    val bitmap = remember(brush.tipPreviewPath) { brush.tipPreviewPath?.let { android.graphics.BitmapFactory.decodeFile(it) } }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, OutlineSoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), brush.name, Modifier.fillMaxSize().padding(4.dp))
        else Canvas(Modifier.fillMaxSize().padding(7.dp)) { drawLine(Ink, Offset(2f, size.height * .7f), Offset(size.width - 2f, size.height * .3f), strokeWidth = 7f, cap = StrokeCap.Round) }
    }
}

@Composable
private fun BrushEditorDialog(brush: ImportedBrush, onDismiss: () -> Unit, onSave: (ImportedBrush) -> Unit) {
    var draft by remember(brush.id) { mutableStateOf(brush) }
    var tagText by remember(brush.id) { mutableStateOf(brush.tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { BrushTipPreview(draft, Modifier.size(48.dp)); Spacer(Modifier.width(12.dp)); Column { Text(draft.name, maxLines = 1); Text("Brush Studio", color = MutedInk, fontSize = 11.sp) } } },
        text = {
            LazyColumn(Modifier.widthIn(min = 360.dp, max = 520.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text("Brush name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(draft.groupName, { draft = draft.copy(groupName = it.take(BrushGroupOperations.MAX_NAME_LENGTH)) }, label = { Text("Group") }, supportingText = { Text("Leave empty for Ungrouped") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(tagText, { tagText = it }, label = { Text("Tags") }, supportingText = { Text("Separate tags with commas") }, modifier = Modifier.fillMaxWidth()) }
                item { BrushSettingSlider("Size", draft.defaultSize, .5f..400f) { draft = draft.copy(defaultSize = it) } }
                item { BrushSettingSlider("Stabilization", draft.stabilization, 0f..0.9f) { draft = draft.copy(stabilization = it) } }
                item { SettingSwitch("Adjust stabilization by speed", "Uses the imported CSP correction behavior when available.", draft.stabilizationBySpeed) { draft = draft.copy(stabilizationBySpeed = it) } }
                if (draft.stabilizationBySpeed) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.stabilizationBySpeedMode == StrokeStabilization.INCREASE_WHEN_SLOW, onClick = { draft = draft.copy(stabilizationBySpeedMode = StrokeStabilization.INCREASE_WHEN_SLOW) }, label = { Text("More when slow") }); FilterChip(selected = draft.stabilizationBySpeedMode == StrokeStabilization.REDUCE_WHEN_FAST, onClick = { draft = draft.copy(stabilizationBySpeedMode = StrokeStabilization.REDUCE_WHEN_FAST) }, label = { Text("Less when fast") }) } }
                item { BrushSettingSlider("Default opacity", draft.defaultOpacity, .01f..1f) { draft = draft.copy(defaultOpacity = it) } }
                item { SettingSwitch("Pressure controls size", "Uses the imported CSP curve when available.", draft.sizePressureEnabled) { draft = draft.copy(sizePressureEnabled = it) } }
                if (draft.sizePressureEnabled) item { BrushSettingSlider("Minimum size", draft.sizePressureMinimum, 0f..1f) { draft = draft.copy(sizePressureMinimum = it) } }
                if (draft.sizePressureEnabled) item { PressureCurveEditor("Size pressure", draft.sizePressureCurve) { draft = draft.copy(sizePressureCurve = it) } }
                item { SecondaryDynamicsEditor("Size", draft.sizeSecondaryDynamics) { draft = draft.copy(sizeSecondaryDynamics = it) } }
                item { SettingSwitch("Pressure controls opacity", null, draft.opacityPressureEnabled) { draft = draft.copy(opacityPressureEnabled = it) } }
                if (draft.opacityPressureEnabled) item { BrushSettingSlider("Minimum opacity", draft.opacityPressureMinimum, 0f..1f) { draft = draft.copy(opacityPressureMinimum = it) } }
                if (draft.opacityPressureEnabled) item { PressureCurveEditor("Opacity pressure", draft.opacityPressureCurve) { draft = draft.copy(opacityPressureCurve = it) } }
                item { SecondaryDynamicsEditor("Opacity", draft.opacitySecondaryDynamics) { draft = draft.copy(opacitySecondaryDynamics = it) } }
                item { SettingSwitch("Pressure controls flow", null, draft.flowPressureEnabled) { draft = draft.copy(flowPressureEnabled = it) } }
                if (draft.flowPressureEnabled) item { BrushSettingSlider("Minimum flow", draft.flowPressureMinimum, 0f..1f) { draft = draft.copy(flowPressureMinimum = it) } }
                if (draft.flowPressureEnabled) item { PressureCurveEditor("Flow pressure", draft.flowPressureCurve) { draft = draft.copy(flowPressureCurve = it) } }
                item { SecondaryDynamicsEditor("Flow", draft.flowSecondaryDynamics) { draft = draft.copy(flowSecondaryDynamics = it) } }
                item { SettingSwitch("Pressure controls spacing", "Imported from CSP Interval dynamics when available.", draft.spacingPressureEnabled) { draft = draft.copy(spacingPressureEnabled = it) } }
                if (draft.spacingPressureEnabled) item { BrushSettingSlider("Minimum spacing", draft.spacingPressureMinimum, 0f..1f) { draft = draft.copy(spacingPressureMinimum = it) } }
                if (draft.spacingPressureEnabled) item { PressureCurveEditor("Spacing pressure", draft.spacingPressureCurve) { draft = draft.copy(spacingPressureCurve = it) } }
                item { SecondaryDynamicsEditor("Spacing", draft.spacingSecondaryDynamics) { draft = draft.copy(spacingSecondaryDynamics = it) } }
                item { SettingSwitch("Pressure controls thickness", null, draft.thicknessPressureEnabled) { draft = draft.copy(thicknessPressureEnabled = it) } }
                if (draft.thicknessPressureEnabled) item { BrushSettingSlider("Minimum thickness", draft.thicknessPressureMinimum, 0f..1f) { draft = draft.copy(thicknessPressureMinimum = it) } }
                if (draft.thicknessPressureEnabled) item { PressureCurveEditor("Thickness pressure", draft.thicknessPressureCurve) { draft = draft.copy(thicknessPressureCurve = it) } }
                item { SecondaryDynamicsEditor("Thickness", draft.thicknessSecondaryDynamics) { draft = draft.copy(thicknessSecondaryDynamics = it) } }
                item { Text("TAPER", color = Sage, fontSize = 11.sp) }
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { BrushTaperMode.entries.forEach { mode -> item { FilterChip(selected = draft.taperMode == mode, onClick = { val toDistance = mode == BrushTaperMode.DISTANCE && draft.taperMode != BrushTaperMode.DISTANCE; val fromDistance = mode != BrushTaperMode.DISTANCE && draft.taperMode == BrushTaperMode.DISTANCE; draft = draft.copy(taperMode = mode, taperStartEnabled = if (mode == BrushTaperMode.FADE) false else draft.taperStartEnabled, taperStartLength = when { toDistance -> draft.taperStartLength * 100f; fromDistance -> draft.taperStartLength / 100f; else -> draft.taperStartLength }, taperEndLength = when { toDistance -> draft.taperEndLength * 100f; fromDistance -> draft.taperEndLength / 100f; else -> draft.taperEndLength }) }, label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }) } } } }
                if (draft.taperMode != BrushTaperMode.FADE) item { SettingSwitch("Starting taper", "Gradually grows from the configured minimum over the start of the stroke.", draft.taperStartEnabled) { draft = draft.copy(taperStartEnabled = it) } }
                if (draft.taperStartEnabled) item { BrushSettingSlider("Start length", draft.taperStartLength, if (draft.taperMode == BrushTaperMode.DISTANCE) 1f..400f else .01f..1f) { draft = draft.copy(taperStartLength = it) } }
                if (draft.taperStartEnabled && draft.taperMode == BrushTaperMode.DISTANCE) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.taperStartLengthUnit == 0, onClick = { draft = draft.copy(taperStartLengthUnit = 0) }, label = { Text("px") }); FilterChip(selected = draft.taperStartLengthUnit == 2, onClick = { draft = draft.copy(taperStartLengthUnit = 2) }, label = { Text("mm") }) } }
                if (draft.taperStartEnabled) item { BrushSettingSlider("Start minimum size", draft.taperStartMinimum, 0f..1f) { draft = draft.copy(taperStartMinimum = it) } }
                item { SettingSwitch(if (draft.taperMode == BrushTaperMode.FADE) "Fade" else "Ending taper", if (draft.taperMode == BrushTaperMode.FADE) "Fades from full size toward the configured minimum." else "Gradually narrows toward the configured minimum at stroke end.", draft.taperEndEnabled) { draft = draft.copy(taperEndEnabled = it) } }
                if (draft.taperEndEnabled) item { BrushSettingSlider(if (draft.taperMode == BrushTaperMode.FADE) "Fade length" else "End length", draft.taperEndLength, if (draft.taperMode == BrushTaperMode.DISTANCE) 1f..400f else .01f..1f) { draft = draft.copy(taperEndLength = it) } }
                if (draft.taperEndEnabled && draft.taperMode == BrushTaperMode.DISTANCE) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.taperEndLengthUnit == 0, onClick = { draft = draft.copy(taperEndLengthUnit = 0) }, label = { Text("px") }); FilterChip(selected = draft.taperEndLengthUnit == 2, onClick = { draft = draft.copy(taperEndLengthUnit = 2) }, label = { Text("mm") }) } }
                if (draft.taperEndEnabled) item { BrushSettingSlider("End minimum size", draft.taperEndMinimum, 0f..1f) { draft = draft.copy(taperEndMinimum = it) } }
                item { BrushSettingSlider("Spacing", draft.spacing, .01f..2f) { draft = draft.copy(spacing = it) } }
                item { BrushSettingSlider("Flow", draft.flow, 0f..1f) { draft = draft.copy(flow = it) } }
                item { BrushSettingSlider("Hardness", draft.hardness, 0f..1f) { draft = draft.copy(hardness = it) } }
                if (draft.grainPreviewPath != null) item { BrushGrainPreview(draft.grainPreviewPath!!, draft.grainDensity) }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture density", draft.grainDensity, 0f..1f) { draft = draft.copy(grainDensity = it) } }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture scale", draft.grainScale, 1f..400f) { draft = draft.copy(grainScale = it) } }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture rotation", draft.grainRotationDegrees, -180f..180f) { draft = draft.copy(grainRotationDegrees = it) } }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture brightness", draft.grainBrightness, -100f..100f) { draft = draft.copy(grainBrightness = it) } }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture contrast", draft.grainContrast, -100f..100f) { draft = draft.copy(grainContrast = it) } }
                if (draft.grainPreviewPath != null) item { SettingSwitch("Invert paper texture", null, draft.grainReversed) { draft = draft.copy(grainReversed = it) } }
                if (draft.grainPreviewPath != null) item { SettingSwitch("Emphasize paper texture", null, draft.grainEmphasized) { draft = draft.copy(grainEmphasized = it) } }
                item { BrushSettingSlider("Thickness", draft.thickness, .05f..2f) { draft = draft.copy(thickness = it) } }
                item { SettingSwitch("Vertical thickness", "Applies thickness along the imported tip's vertical axis.", draft.thicknessVertical) { draft = draft.copy(thicknessVertical = it) } }
                item { SettingSwitch("Ribbon stroke", "Preserves the tip aspect ratio and continuously follows the stroke direction.", draft.ribbon) { draft = draft.copy(ribbon = it) } }
                item { BrushSettingSlider("Rotation", draft.rotationDegrees, -180f..180f) { draft = draft.copy(rotationDegrees = it) } }
                item { SettingSwitch("Follow stylus orientation", "Uses the pen direction reported by Android.", draft.followStylusOrientation) { draft = draft.copy(followStylusOrientation = it) } }
                item { SettingSwitch("Follow stroke direction", "Rotates the tip along the drawn path.", draft.followStrokeDirection) { draft = draft.copy(followStrokeDirection = it) } }
                item { BrushSettingSlider("Random rotation", draft.randomRotationDegrees, 0f..180f) { draft = draft.copy(randomRotationDegrees = it) } }
                item { BrushSettingSlider("Color mix", draft.colorMix, 0f..1f) { draft = draft.copy(colorMix = it) } }
                item { BrushSettingSlider("Paint mix", draft.paintMix, 0f..1f) { draft = draft.copy(paintMix = it) } }
                item { SettingSwitch("Watercolor approximation", "Uses translucent layered stamps.", draft.watercolor) { draft = draft.copy(watercolor = it) } }
                item { SettingSwitch("Watercolor edge", "Adds the CSP-style translucent edge pass before the main stamp.", draft.waterEdgeEnabled) { draft = draft.copy(waterEdgeEnabled = it) } }
                if (draft.waterEdgeEnabled) item { BrushSettingSlider("Edge radius", draft.waterEdgeRadius, 0f..80f) { draft = draft.copy(waterEdgeRadius = it) } }
                if (draft.waterEdgeEnabled) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.waterEdgeRadiusUnit == 0, onClick = { draft = draft.copy(waterEdgeRadiusUnit = 0) }, label = { Text("px") }); FilterChip(selected = draft.waterEdgeRadiusUnit == 2, onClick = { draft = draft.copy(waterEdgeRadiusUnit = 2) }, label = { Text("mm") }) } }
                if (draft.waterEdgeEnabled) item { BrushSettingSlider("Edge opacity", draft.waterEdgeOpacity, 0f..1f) { draft = draft.copy(waterEdgeOpacity = it) } }
                if (draft.waterEdgeEnabled) item { BrushSettingSlider("Edge darkness", draft.waterEdgeDarkness, 0f..1f) { draft = draft.copy(waterEdgeDarkness = it) } }
                if (draft.waterEdgeEnabled) item { BrushSettingSlider("Edge blur", draft.waterEdgeBlur, 0f..80f) { draft = draft.copy(waterEdgeBlur = it) } }
                if (draft.waterEdgeEnabled && draft.waterEdgeBlur > 0f) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.waterEdgeBlurUnit == 0, onClick = { draft = draft.copy(waterEdgeBlurUnit = 0) }, label = { Text("Blur px") }); FilterChip(selected = draft.waterEdgeBlurUnit == 2, onClick = { draft = draft.copy(waterEdgeBlurUnit = 2) }, label = { Text("Blur mm") }) } }
                item { SettingSwitch("Spray/scatter", null, draft.spray) { draft = draft.copy(spray = it) } }
                if (draft.spray) item { BrushSettingSlider("Particle size", draft.sprayParticleSize, .01f..400f) { draft = draft.copy(sprayParticleSize = it) } }
                if (draft.spray) item { SettingSwitch("Particle size follows brush", "When enabled, particle size is a percentage of the current brush size.", draft.sprayParticleSizeSyncBrushSize) { draft = draft.copy(sprayParticleSizeSyncBrushSize = it) } }
                if (draft.spray) item { BrushSettingSlider("Particle density", draft.sprayDensity, 1f..100f) { draft = draft.copy(sprayDensity = it) } }
                if (draft.spray) item { BrushSettingSlider("Spray center bias", draft.sprayBias, -100f..100f) { draft = draft.copy(sprayBias = it) } }
                if (draft.spray) item { BrushSettingSlider("Particle rotation", draft.sprayParticleRotationDegrees, -180f..180f) { draft = draft.copy(sprayParticleRotationDegrees = it) } }
                if (draft.spray) item { SettingSwitch("Particles follow stylus", "Uses the independent CSP particle-direction setting.", draft.sprayParticleFollowStylusOrientation) { draft = draft.copy(sprayParticleFollowStylusOrientation = it) } }
                if (draft.spray) item { SettingSwitch("Particles follow stroke", "Aligns particles with the local path direction.", draft.sprayParticleFollowStrokeDirection) { draft = draft.copy(sprayParticleFollowStrokeDirection = it) } }
                if (draft.spray) item { BrushSettingSlider("Particle random rotation", draft.sprayParticleRandomRotationDegrees, 0f..180f) { draft = draft.copy(sprayParticleRandomRotationDegrees = it) } }
                item { SettingSwitch("Dual brush", "Uses the imported secondary tip as an independent stamp stream.", draft.dualBrush) { draft = draft.copy(dualBrush = it) } }
                if (draft.dualBrush && draft.dualTipPreviewPaths.isNotEmpty()) item { BrushTipPreview("Secondary tip · ${draft.dualTipPreviewPaths.size} material${if (draft.dualTipPreviewPaths.size == 1) "" else "s"}", draft.dualTipPreviewPaths.first()) }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary size", draft.dualSizeScale, .01f..4f) { draft = draft.copy(dualSizeScale = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary spacing", draft.dualSpacing, .01f..2f) { draft = draft.copy(dualSpacing = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary flow", draft.dualFlow, 0f..1f) { draft = draft.copy(dualFlow = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary hardness", draft.dualHardness, 0f..1f) { draft = draft.copy(dualHardness = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary thickness", draft.dualThickness, .05f..2f) { draft = draft.copy(dualThickness = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary vertical thickness", null, draft.dualThicknessVertical) { draft = draft.copy(dualThicknessVertical = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary rotation", draft.dualRotationDegrees, -180f..180f) { draft = draft.copy(dualRotationDegrees = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary follows stylus", null, draft.dualFollowStylusOrientation) { draft = draft.copy(dualFollowStylusOrientation = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary follows stroke", null, draft.dualFollowStrokeDirection) { draft = draft.copy(dualFollowStrokeDirection = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary random rotation", draft.dualRandomRotationDegrees, 0f..180f) { draft = draft.copy(dualRandomRotationDegrees = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary Ribbon", null, draft.dualRibbon) { draft = draft.copy(dualRibbon = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary spray/scatter", null, draft.dualSpray) { draft = draft.copy(dualSpray = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary particle size", draft.dualSprayParticleSize, .01f..400f) { draft = draft.copy(dualSprayParticleSize = it) } }
                if (draft.dualBrush && draft.dualSpray) item { SettingSwitch("Secondary particles follow brush", null, draft.dualSprayParticleSizeSyncBrushSize) { draft = draft.copy(dualSprayParticleSizeSyncBrushSize = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary particle density", draft.dualSprayDensity, 1f..100f) { draft = draft.copy(dualSprayDensity = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary center bias", draft.dualSprayBias, -100f..100f) { draft = draft.copy(dualSprayBias = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary particle rotation", draft.dualSprayParticleRotationDegrees, -180f..180f) { draft = draft.copy(dualSprayParticleRotationDegrees = it) } }
                if (draft.dualBrush && draft.dualSpray) item { SettingSwitch("Secondary particles follow stylus", null, draft.dualSprayParticleFollowStylusOrientation) { draft = draft.copy(dualSprayParticleFollowStylusOrientation = it) } }
                if (draft.dualBrush && draft.dualSpray) item { SettingSwitch("Secondary particles follow stroke", null, draft.dualSprayParticleFollowStrokeDirection) { draft = draft.copy(dualSprayParticleFollowStrokeDirection = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary particle random rotation", draft.dualSprayParticleRandomRotationDegrees, 0f..180f) { draft = draft.copy(dualSprayParticleRandomRotationDegrees = it) } }
                if (draft.dualBrush) item { Text("Imported composite mode ${draft.dualCompositeMode} is retained. Inkloom currently renders it with normal source-over blending until that CSP mode is verified.", color = MutedInk, fontSize = 10.sp) }
            }
        },
        confirmButton = { Button(onClick = { onSave(draft.copy(name = draft.name.trim().ifBlank { brush.name }, groupName = BrushGroupOperations.normalize(draft.groupName), tags = tagText.split(',').map(String::trim).filter(String::isNotBlank).distinct().take(24))) }) { Text("Save preset") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BrushSettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column { Row { Text(label, Modifier.weight(1f), fontSize = 12.sp); Text(if (range.endInclusive > 10f) "%.1f".format(value) else "%.2f".format(value), color = MutedInk, fontSize = 11.sp) }; Slider(value, onValue, valueRange = range) }
}

@Composable
private fun SecondaryDynamicsEditor(label: String, value: BrushSecondaryDynamics, onValue: (BrushSecondaryDynamics) -> Unit) {
    val activeSources = buildList { if (value.tiltEnabled) add("Tilt"); if (value.velocityEnabled) add("Velocity"); if (value.randomEnabled) add("Random") }
    var expanded by remember(label) { mutableStateOf(activeSources.isNotEmpty()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, OutlineSoft, RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("$label input dynamics", color = Sage, fontSize = 11.sp); Text(activeSources.joinToString().ifBlank { "No secondary source" }, color = MutedInk, fontSize = 10.sp) }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse" else "Expand")
        }
        if (expanded) {
            SettingSwitch("Tilt", null, value.tiltEnabled) { onValue(value.copy(tiltEnabled = it)) }
            if (value.tiltEnabled) BrushSettingSlider("Tilt minimum", value.tiltMinimum, 0f..1f) { onValue(value.copy(tiltMinimum = it)) }
            SettingSwitch("Velocity", null, value.velocityEnabled) { onValue(value.copy(velocityEnabled = it)) }
            if (value.velocityEnabled) BrushSettingSlider("Velocity minimum", value.velocityMinimum, 0f..1f) { onValue(value.copy(velocityMinimum = it)) }
            if (value.velocityEnabled) PressureCurveEditor("Velocity response", value.velocityCurve) { onValue(value.copy(velocityCurve = it)) }
            SettingSwitch("Random", "Deterministic per stamp, so export matches the canvas.", value.randomEnabled) { onValue(value.copy(randomEnabled = it)) }
            if (value.randomEnabled) BrushSettingSlider("Random minimum", value.randomMinimum, 0f..1f) { onValue(value.copy(randomMinimum = it)) }
        }
    }
}

@Composable
private fun BrushGrainPreview(path: String, density: Float) {
    val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    Column { Text("Paper texture · ${(density * 100).toInt()}%", fontSize = 11.sp, color = MutedInk); if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), "Paper texture", Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
}

@Composable
private fun BrushTipPreview(label: String, path: String) {
    val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    Column {
        Text(label, fontSize = 11.sp, color = MutedInk)
        if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), label, Modifier.fillMaxWidth().height(90.dp).background(Color.White, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun PressureCurveEditor(label: String, points: List<BrushCurvePoint>, onPoints: (List<BrushCurvePoint>) -> Unit) {
    val shown = points.ifEmpty { listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f)) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("$label · ${shown.size} points", fontSize = 11.sp, color = MutedInk, modifier = Modifier.weight(1f)); TextButton(onClick = { onPoints(listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f))) }) { Text("Linear") }; TextButton(onClick = { onPoints(listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(.35f, .62f), BrushCurvePoint(1f, 1f))) }) { Text("Soft") }; TextButton(onClick = { onPoints(listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(.65f, .25f), BrushCurvePoint(1f, 1f))) }) { Text("Firm") } }
        Canvas(Modifier.fillMaxWidth().height(72.dp).background(Workspace, RoundedCornerShape(8.dp)).padding(8.dp)) {
            drawLine(OutlineSoft, Offset(0f, size.height), Offset(size.width, 0f), 1f)
            shown.zipWithNext().forEach { (start, end) -> drawLine(Sage, Offset(start.x * size.width, (1f - start.y) * size.height), Offset(end.x * size.width, (1f - end.y) * size.height), 3f, cap = StrokeCap.Round) }
            shown.forEach { drawCircle(Sage, 4f, Offset(it.x * size.width, (1f - it.y) * size.height)) }
        }
    }
}

@Composable
private fun SettingsDialog(settings: AppSettings, onChange: (AppSettings) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Inkloom Settings") },
        text = {
            LazyColumn(Modifier.widthIn(min = 320.dp, max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("INPUT", color = Sage, fontSize = 12.sp) }
                item { SettingSwitch("Draw with finger", "Off by default. Fingers navigate and zoom.", settings.fingerDrawing) { onChange(settings.copy(fingerDrawing = it)) } }
                item { SettingSwitch("Two-finger double tap to undo", "Matches common notebook gestures.", settings.twoFingerDoubleTapUndo) { onChange(settings.copy(twoFingerDoubleTapUndo = it)) } }
                item { SettingSwitch("Show stylus diagnostics", "Displays tool type, pressure, tilt and button state for device testing.", settings.showInputDiagnostics) { onChange(settings.copy(showInputDiagnostics = it)) } }
                item { Text("Pressure sensitivity  ${"%.2f".format(settings.pressureSensitivity)}×") }
                item { Slider(settings.pressureSensitivity, { onChange(settings.copy(pressureSensitivity = it)) }, valueRange = .25f..2.5f) }
                item { Text("Pressure onset  ${(settings.pressureMinimumInput * 100).toInt()}%", color = MutedInk, fontSize = 12.sp) }
                item { Slider(settings.pressureMinimumInput, { value -> onChange(settings.copy(pressureMinimumInput = value.coerceAtMost(settings.pressureMaximumInput - .05f))) }, valueRange = 0f..0.95f) }
                item { Text("Full pressure point  ${(settings.pressureMaximumInput * 100).toInt()}%", color = MutedInk, fontSize = 12.sp) }
                item { Slider(settings.pressureMaximumInput, { value -> onChange(settings.copy(pressureMaximumInput = value.coerceAtLeast(settings.pressureMinimumInput + .05f))) }, valueRange = 0.05f..1f) }
                item { PressureCurvePreview(settings.pressureMinimumInput, settings.pressureMaximumInput, settings.pressureSensitivity) }
                item { Text("Stroke stabilization  ${(settings.stabilization * 100).toInt()}%") }
                item { Slider(settings.stabilization, { onChange(settings.copy(stabilization = it)) }, valueRange = 0f..0.9f) }
                item { Text("DEFAULT TOOL PRESETS", color = Sage, fontSize = 12.sp) }
                item { BrushSettingSlider("Pen size", settings.penSize, .5f..400f) { onChange(settings.copy(penSize = it)) } }
                item { BrushSettingSlider("Pen opacity", settings.penOpacity, .05f..1f) { onChange(settings.copy(penOpacity = it)) } }
                item { BrushSettingSlider("Marker size", settings.highlighterSize, .5f..400f) { onChange(settings.copy(highlighterSize = it)) } }
                item { BrushSettingSlider("Marker opacity", settings.highlighterOpacity, .05f..1f) { onChange(settings.copy(highlighterOpacity = it)) } }
                item { BrushSettingSlider("Eraser size", settings.eraserSize, .5f..400f) { onChange(settings.copy(eraserSize = it)) } }
                item { OutlinedButton(onClick = { onChange(settings.copy(penSize = 5f, penOpacity = 1f, highlighterSize = 28f, highlighterOpacity = .35f, eraserSize = 24f)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("Reset tool presets") } }
                item { Text("PEN BUTTONS", color = Sage, fontSize = 12.sp) }
                item { StylusActionRow("Primary button", settings.stylusPrimaryButton) { onChange(settings.copy(stylusPrimaryButton = it)) } }
                item { StylusActionRow("Secondary button", settings.stylusSecondaryButton) { onChange(settings.copy(stylusSecondaryButton = it)) } }
                item { Text("INTERFACE", color = Sage, fontSize = 12.sp) }
                item { SettingSwitch("Show page sidebar", null, settings.showPageRail) { onChange(settings.copy(showPageRail = it)) } }
                item { SettingSwitch("Show floating toolbar", null, settings.showToolbar) { onChange(settings.copy(showToolbar = it)) } }
                item { Text("Toolbar docking", color = MutedInk, fontSize = 11.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ToolbarDock.entries.forEach { dock -> item { FilterChip(selected = settings.toolbarDock == dock, onClick = { onChange(settings.copy(toolbarDock = dock, verticalToolbar = if (dock in setOf(ToolbarDock.LEFT, ToolbarDock.RIGHT)) true else if (dock in setOf(ToolbarDock.TOP, ToolbarDock.BOTTOM)) false else settings.verticalToolbar)) }, label = { Text(dock.name.lowercase().replaceFirstChar(Char::uppercase)) }) } } } }
                if (settings.toolbarDock == ToolbarDock.FREE) item { SettingSwitch("Vertical toolbar", "Useful on a wide tablet canvas.", settings.verticalToolbar) { onChange(settings.copy(verticalToolbar = it)) } }
                item { OutlinedButton(onClick = { onChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = 16f, toolbarOffsetYDp = 16f)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CenterFocusStrong, null); Spacer(Modifier.width(8.dp)); Text("Reset toolbar position") } }
            }
        },
    )
}

@Composable
private fun PressureCurvePreview(minimumInput: Float, maximumInput: Float, sensitivity: Float) {
    Column {
        Text("Calibrated response", color = MutedInk, fontSize = 11.sp)
        Canvas(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp)).background(Workspace).padding(10.dp)) {
            drawLine(OutlineSoft, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            drawLine(OutlineSoft, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
            val path = androidx.compose.ui.graphics.Path()
            for (index in 0..100) {
                val input = index / 100f
                val output = PressureCalibration.apply(input, minimumInput, maximumInput, sensitivity)
                val x = input * size.width
                val y = size.height - output * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Sage, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); if (subtitle != null) Text(subtitle, color = MutedInk, fontSize = 11.sp) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun StylusActionRow(title: String, selected: StylusButtonAction, onSelected: (StylusButtonAction) -> Unit) {
    Column { Text(title, fontSize = 13.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { StylusButtonAction.entries.forEach { action -> FilterChip(selected = selected == action, onClick = { onSelected(action) }, label = { Text(action.name.lowercase().replaceFirstChar(Char::uppercase)) }) } } }
}
