package app.inkloom.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.inkloom.settings.ColorSetImport
import kotlinx.coroutines.withTimeoutOrNull
import app.inkloom.settings.ColorSetImportMode
import app.inkloom.settings.ColorSetSwatch
import app.inkloom.settings.ImagePaletteExtractor
import app.inkloom.settings.ImportedColorSet
import app.inkloom.settings.ColorSetLibrary
import app.inkloom.settings.ColorSetLibraryStore
import app.inkloom.settings.StarterColorSet
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

private data class PendingColorSetImport(val mode: ColorSetImportMode, val targetSetId: String?)

/** Color-set identity and selection survive tab disposal; editing never sorts by color. */
@Composable
internal fun ColorSetPanel(color: Int, language: UiLanguage, availableHeight: Dp, onPick: (Int) -> Unit, onOpenQuickPicker: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ColorSetLibraryStore(context) }
    val density = LocalDensity.current
    var gridWidth by remember { mutableStateOf(320.dp) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    var error by remember { mutableStateOf<String?>(null) }
    var library by remember { mutableStateOf(runCatching { store.load(tr("Saved colors", "已存颜色")) }.getOrElse {
        error = tr("Color sets could not be opened", "无法打开色板"); ColorSetLibrary()
    }) }
    fun edit(operation: (ColorSetLibrary) -> ColorSetLibrary) {
        runCatching { store.update(tr("Saved colors", "已存颜色"), operation) }
            .onSuccess { library = it; error = null }
            .onFailure { error = tr("Color set could not be changed", "无法修改色板") }
    }
    var chooser by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var starterPicker by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var deletion by remember { mutableStateOf(false) }
    var tileToEdit by remember { mutableStateOf<String?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importSource by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<PendingColorSetImport?>(null) }
    fun commitImport(imported: ImportedColorSet, request: PendingColorSetImport) {
        runCatching { store.update(tr("Saved colors", "已存颜色")) { ColorSetImport.applyTo(it, imported, request.mode, request.targetSetId) } }
            .onSuccess { library = it; error = null }
            .onFailure { error = it.message ?: tr("Color palette could not be imported", "无法导入色板") }
    }
    val set = library.activeSet
    val bounds = remember { mutableMapOf<String, Rect>() }
    val gridState = key(set?.id) { rememberLazyGridState() }
    val activeSetRequester = remember { BringIntoViewRequester() }
    RevealSelectedItem(gridState, set?.id, set?.selectedSwatchId, set?.swatches?.indexOfFirst { it.id == set.selectedSwatchId } ?: -1)
    var dragging by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableStateOf(Offset.Zero) }
    val palettePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pendingImport.also { pendingImport = null }
        if (uri != null && request != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch {
                importBusy = true
                runCatching {
                    withContext(Dispatchers.IO) {
                        val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "Imported palette.aco" }
                        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "The selected palette could not be opened" }
                            .use { it.readPaletteBytes() }
                        ColorSetImport.decode(displayName, bytes)
                    }
                }.onSuccess { imported -> commitImport(imported, request) }
                    .onFailure { failure -> error = failure.message ?: tr("Color palette could not be imported", "无法导入色板") }
                importBusy = false
            }
        }
    }
    val imagePalettePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pendingImport.also { pendingImport = null }
        if (uri != null && request != null) scope.launch {
            importBusy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: tr("Image palette", "图片色板")
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "The selected image could not be opened" }
                        .use { it.readPaletteBytes() }
                    val colors = ImagePaletteExtractor.extractEncoded(bytes)
                    ImportedColorSet(
                        displayName.substringBeforeLast('.').ifBlank { tr("Image palette", "图片色板") },
                        colors.mapIndexed { index, value -> ColorSetSwatch(UUID.randomUUID().toString(), value, tr("Color ${index + 1}", "颜色 ${index + 1}")) },
                        "Image",
                    )
                }
            }.onSuccess { imported -> commitImport(imported, request) }
                .onFailure { failure -> error = failure.message ?: tr("Colors could not be extracted from the image", "无法从图片提取颜色") }
            importBusy = false
        }
    }
    fun startName(action: String, initial: String) { naming = action; name = initial; menu = false }
    Column(Modifier.fillMaxWidth().onSizeChanged { gridWidth = with(density) { it.width.toDp() } }) {
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().clickable { chooser = true }.semantics { contentDescription = tr("Choose color set", "选择色板") }, verticalAlignment = Alignment.CenterVertically) {
                    Text(set?.name ?: tr("Color sets", "色板"), Modifier.weight(1f).padding(horizontal = 6.dp), fontSize = 12.sp, color = Ink, maxLines = 1)
                    Icon(Icons.Outlined.UnfoldMore, null, Modifier.size(16.dp), tint = MutedInk)
                }
                ColorPaletteDropdownMenu(chooser, { chooser = false }) {
                    LaunchedEffect(set?.id) {
                        withFrameNanos { }
                        activeSetRequester.bringIntoView()
                    }
                    library.sets.forEach { candidate ->
                        val isActive = set?.id == candidate.id
                        DropdownMenuItem(text = { Text(candidate.name) },
                            modifier = (if (isActive) Modifier.bringIntoViewRequester(activeSetRequester) else Modifier).semantics { selected = isActive },
                            trailingIcon = { if (isActive) Icon(Icons.Outlined.Check, null) },
                            onClick = { edit { it.selectSet(candidate.id) }; chooser = false })
                    }
                }
            }
            Box {
                SetAction(Icons.Outlined.MoreHoriz, tr("Color set menu", "色板菜单")) { menu = true }
                ColorPaletteDropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("New color set", "新建色板")) }, onClick = { startName("new", "") })
                    DropdownMenuItem(
                        text = { Text(tr("Import color set from file", "从文件导入色板")) },
                        enabled = !importBusy,
                        onClick = { menu = false; importSource = "file" },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("New color set from image", "从图片新建色板")) },
                        enabled = !importBusy,
                        onClick = { menu = false; importSource = "image" },
                    )
                    DropdownMenuItem(text = { Text(tr("Add starter color set", "添加基础色板")) }, onClick = { menu = false; starterPicker = true })
                    if (set != null) {
                        DropdownMenuItem(text = { Text(tr("Rename color set", "重命名色板")) }, onClick = { startName("rename", set.name) })
                        DropdownMenuItem(text = { Text(tr("Duplicate color set", "复制色板")) }, onClick = { edit { it.duplicateSet(set.id, "${set.name} ${tr("copy", "副本")}".take(120)) }; menu = false })
                        DropdownMenuItem(text = { Text(tr("Delete color set", "删除色板")) }, onClick = { deletion = true; menu = false })
                    }
                }
            }
        }
        HorizontalDivider(color = OutlineSoft, thickness = .5.dp)
        LazyVerticalGrid(GridCells.Adaptive(30.dp), Modifier.width(gridWidth).height(minOf(280.dp, (availableHeight - 68.dp).coerceAtLeast(30.dp))), state = gridState, contentPadding = PaddingValues(vertical = 2.dp)) {
            itemsIndexed(set?.swatches.orEmpty(), key = { _, tile -> tile.id }) { index, tile ->
                DisposableEffect(tile.id) { onDispose { bounds.remove(tile.id) } }
                Box(Modifier.aspectRatio(1f).padding(.5.dp)
                    .onGloballyPositioned { bounds[tile.id] = it.boundsInRoot() }
                    .background(Color(tile.color).copy(alpha = if (dragging == tile.id) .45f else Color(tile.color).alpha))
                    .border(if (target == tile.id || set?.selectedSwatchId == tile.id) 2.dp else .5.dp, if (target == tile.id || set?.selectedSwatchId == tile.id) Ink else OutlineSoft)
                    .semantics { contentDescription = tr("Color tile ${index + 1}", "色块 ${index + 1}"); selected = set?.selectedSwatchId == tile.id }
                    .pointerInput(set?.id, set?.swatches, tile.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val aborted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop)
                                        break
                                }
                                true
                            }
                            if (aborted == true) return@awaitEachGesture
                            dragging = tile.id
                            target = tile.id
                            var moved = false
                            var released = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                                    change.consume()
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop / 2f) moved = true
                                    position = (bounds[tile.id]?.topLeft ?: Offset.Zero) + change.position
                                    target = bounds.entries.firstOrNull { (id, rect) -> gridState.layoutInfo.visibleItemsInfo.any { it.key == id } && rect.contains(position) }?.key
                                    if (!change.pressed) { released = true; break }
                                }
                                if (released && moved && set != null) {
                                    val destination = set.swatches.indexOfFirst { it.id == target }
                                    if (destination >= 0) edit { it.moveSwatch(set.id, tile.id, destination) }
                                } else if (released && !moved) {
                                    if (set != null) edit { it.selectSwatch(set.id, tile.id) }
                                    tileToEdit = tile.id
                                }
                            } finally { dragging = null; target = null }
                        }
                    }
                    .clickable { if (set != null) edit { it.selectSwatch(set.id, tile.id) }; onPick(tile.color) }) {
                    // A black-and-white badge stays legible on both very light and very dark colors.
                    if (set?.selectedSwatchId == tile.id) Icon(Icons.Outlined.Check, null,
                        Modifier.align(Alignment.BottomEnd).size(14.dp).background(Color.Black).padding(1.dp), tint = Color.White)
                }
            }
        }
        HorizontalDivider(color = OutlineSoft, thickness = .5.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).padding(6.dp).background(Color(color)).border(.5.dp, OutlineSoft)
                .semantics { contentDescription = tr("Edit current color", "编辑当前颜色") }
                .combinedClickable(onClick = { onOpenQuickPicker(color) }, onLongClick = { onOpenQuickPicker(color) }))
            Text(set?.selectedSwatch?.name.orEmpty(), Modifier.weight(1f), maxLines = 1, color = MutedInk, fontSize = 11.sp)
            SetAction(Icons.Outlined.Add, tr("Save current color", "保存当前颜色")) {
                edit { current ->
                    val ready = if (current.activeSet == null) current.createSet(tr("Saved colors", "已存颜色")) else current
                    ready.insertSwatch(requireNotNull(ready.activeSetId), color)
                }
            }
            SetAction(Icons.Outlined.Edit, tr("Rename selected color", "重命名所选颜色"), set?.selectedSwatch != null) {
                set?.selectedSwatch?.let { startName("tile", it.name) }
            }
            SetAction(Icons.Outlined.SwapHoriz, tr("Replace selected color", "替换所选颜色"), set?.selectedSwatch != null) { if (set?.selectedSwatchId != null) edit { it.replaceSwatch(set.id, set.selectedSwatchId, color) } }
            SetAction(Icons.Outlined.DeleteOutline, tr("Delete selected color", "删除所选颜色"), set?.selectedSwatch != null) { if (set?.selectedSwatchId != null) edit { it.removeSwatch(set.id, set.selectedSwatchId) } }
        }
        if (importBusy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Ink, trackColor = OutlineSoft)
        error?.let { Text(it, color = Ink, fontSize = 11.sp) }
    }
    tileToEdit?.let { tileId -> set?.swatches?.firstOrNull { it.id == tileId }?.let { tile ->
        StandardColorPickerDialog(tile.color, language, onConfirm = { chosen ->
            edit { it.replaceSwatch(set.id, tileId, chosen) }
            onPick(chosen)
            tileToEdit = null
        }, onDismiss = { tileToEdit = null })
    } }
    if (importSource != null) AlertDialog(
        onDismissRequest = { importSource = null },
        title = { Text(tr("Import into color set", "导入到色板")) },
        text = { Text(set?.name ?: tr("A new color set will be created", "将创建新色板")) },
        confirmButton = {
            TextButton(onClick = {
                val source = importSource
                pendingImport = PendingColorSetImport(ColorSetImportMode.APPEND, set?.id)
                importSource = null
                if (source == "image") imagePalettePicker.launch(arrayOf("image/*"))
                else palettePicker.launch(arrayOf("application/octet-stream", "application/zip", "application/json", "text/xml", "*/*"))
            }) { Text(tr("Append colors", "追加颜色")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { importSource = null }) { Text(tr("Cancel", "取消")) }
                if (set != null) TextButton(onClick = {
                    val source = importSource
                    pendingImport = PendingColorSetImport(ColorSetImportMode.REPLACE, set.id)
                    importSource = null
                    if (source == "image") imagePalettePicker.launch(arrayOf("image/*"))
                    else palettePicker.launch(arrayOf("application/octet-stream", "application/zip", "application/json", "text/xml", "*/*"))
                }) { Text(tr("Replace colors", "替换颜色")) }
            }
        },
    )
    if (starterPicker) AlertDialog(onDismissRequest = { starterPicker = false },
        title = { Text(tr("Inkloom color sets", "Inkloom 基础色板")) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StarterColorSet.entries.forEach { preset ->
                    Column(Modifier.fillMaxWidth().clickable {
                        edit { preset.addTo(it, language) }; starterPicker = false
                    }.padding(vertical = 8.dp).semantics { contentDescription = "${tr("Add", "添加")} ${preset.displayName(language)}" }) {
                        Text(preset.displayName(language), color = Ink, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().height(24.dp)) {
                            preset.swatches(language).forEach { (value, _) ->
                                Box(Modifier.weight(1f).fillMaxHeight().background(Color(value)).border(.5.dp, OutlineSoft))
                            }
                        }
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { starterPicker = false }) { Text(tr("Cancel", "取消")) } })
    if (naming != null) AlertDialog(onDismissRequest = { naming = null }, title = { Text(tr("Name", "名称")) }, text = {
        OutlinedTextField(name, { name = it.take(120) }, singleLine = true, modifier = Modifier.semantics { contentDescription = tr("Name", "名称") })
    }, confirmButton = {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        TextButton(enabled = name.isNotBlank(), onClick = {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        when (naming) {
            "new" -> edit { it.createSet(name) }
            "rename" -> if (set != null) edit { it.renameSet(set.id, name) }
            "tile" -> if (set?.selectedSwatchId != null) edit { it.renameSwatch(set.id, set.selectedSwatchId, name) }
        }; naming = null
    }) { Text(tr("Save", "保存")) } }, dismissButton = {
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        TextButton(onClick = { focusManager.clearFocus(force = true); keyboard?.hide(); naming = null }) { Text(tr("Cancel", "取消")) }
    })
    if (deletion && set != null) AlertDialog(onDismissRequest = { deletion = false }, title = { Text(tr("Delete color set", "删除色板")) }, text = { Text(set.name) }, confirmButton = {
        TextButton(onClick = { edit { it.deleteSet(set.id) }; deletion = false }) { Text(tr("Delete", "删除")) }
    }, dismissButton = { TextButton(onClick = { deletion = false }) { Text(tr("Cancel", "取消")) } })
}

private fun InputStream.readPaletteBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= ColorSetImport.MAX_FILE_BYTES) { "Color palette file exceeds 16 MB" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

@Composable
private fun SetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(32.dp), enabled = enabled) { Icon(icon, label, Modifier.size(17.dp), tint = if (enabled) Ink else MutedInk.copy(alpha = .35f)) }
}
