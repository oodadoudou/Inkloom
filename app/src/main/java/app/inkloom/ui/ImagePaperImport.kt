package app.inkloom.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.inkloom.ui.theme.Paper
import app.inkloom.ui.theme.Ink
import app.inkloom.document.*
import app.inkloom.settings.text
import kotlinx.coroutines.*

@Composable
internal fun ImagePaperThumbnail(background: PageBackground.Image, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, background) {
        value = withContext(Dispatchers.IO) { runCatching { ImagePaperRenderer.render(background, 384) }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.Fit) }
}

internal data class ImagePaperImporter(val launch: () -> Unit, val loading: Boolean, val failed: Boolean, val previewOpen: Boolean)

@Composable
internal fun rememberImagePaperImporter(
    prepare: suspend (Uri, String) -> ImagePaperDraft,
    save: (PageTemplate) -> PageTemplate,
    onSaved: (PageTemplate) -> Unit,
): ImagePaperImporter {
    val language = LocalUiLanguage.current
    fun tr(en: String, zh: String) = language.text(en, zh)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<ImagePaperDraft?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val currentPrepare by rememberUpdatedState(prepare)
    DisposableEffect(Unit) { onDispose { draft?.close() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loading = true; failed = false
            var pending: ImagePaperDraft? = null
            try {
                // Assign before the dispatcher resumes: cancellation must still release the lease.
                withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0).substringBeforeLast('.') else null
                    } ?: tr("Image paper", "图片纸张")
                    pending = currentPrepare(uri, name)
                }
                draft = pending; pending = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { pending?.close(); loading = false }
        }
    }
    draft?.let { current ->
        ImagePaperPreviewDialog(current.template, saveFailed = failed,
            onDismiss = { failed = false; current.close(); draft = null },
            onSave = { template ->
                try { val saved = save(template); onSaved(saved); current.close(); draft = null }
                catch (_: Exception) { failed = true }
            })
    }
    return ImagePaperImporter({ picker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }, loading, failed, draft != null)
}

@Composable
internal fun ImagePaperPreviewDialog(template: PageTemplate, saveFailed: Boolean = false, onDismiss: () -> Unit, onSave: (PageTemplate) -> Unit) {
    val language = LocalUiLanguage.current
    fun tr(en: String, zh: String) = language.text(en, zh)
    var name by remember(template.id) { mutableStateOf(template.name) }
    var width by remember(template.id) { mutableStateOf(template.width.toInt().toString()) }
    var height by remember(template.id) { mutableStateOf(template.height.toInt().toString()) }
    var locked by remember { mutableStateOf(true) }
    var crop by remember { mutableStateOf(false) }
    var paperColor by remember { mutableLongStateOf((template.background as PageBackground.Image).paperColorArgb) }
    val w = width.toIntOrNull(); val h = height.toIntOrNull()
    val valid = w != null && h != null && w in 32..20_000 && h in 32..20_000 && name.isNotBlank()
    val background = (template.background as PageBackground.Image).copy(
        paperAspectRatio = if (valid) w!!.toFloat() / h!! else template.width / template.height, cropToFill = crop, paperColorArgb = paperColor)
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val finishEditing = { focus.requestFocus(); keyboard?.hide(); Unit }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LaunchedEffect(Unit) { finishEditing() }
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 480.dp), color = Paper, contentColor = Ink, shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.focusRequester(focus).focusable().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("Image paper", "图片纸张"), style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f, fill = false).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ImagePaperThumbnail(background, Modifier.fillMaxWidth().height(180.dp))
                StudioOutlinedTextField(name, { name = it.take(80) }, label = { Text(tr("Template name", "模板名称")) }, singleLine = true)
                StudioOutlinedTextField(width, { input ->
                    width = input.filter(Char::isDigit).take(5)
                    if (locked) width.toIntOrNull()?.let { height = (it * template.height / template.width).toInt().coerceAtLeast(1).toString() }
                }, label = { Text(tr("Width (px)", "宽度 (px)")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { finishEditing() }))
                StudioOutlinedTextField(height, { input ->
                    height = input.filter(Char::isDigit).take(5)
                    if (locked) height.toIntOrNull()?.let { width = (it * template.width / template.height).toInt().coerceAtLeast(1).toString() }
                }, label = { Text(tr("Height (px)", "高度 (px)")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { finishEditing() }))
                Row { StudioCheckbox(locked, { locked = it }); Text(tr("Keep proportions", "保持比例"), Modifier.padding(top = 12.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PaperColorCatalog.WHITE to tr("White", "白色"),
                        PaperColorCatalog.WARM_IVORY to tr("Ivory", "象牙色"),
                        PaperColorCatalog.SOFT_GRAY to tr("Gray", "灰色")).forEach { (value, label) ->
                        StudioFilterChip(paperColor == value.toLong(), { paperColor = value.toLong() }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioFilterChip(!crop, { crop = false }, label = { Text(tr("Fit image", "完整显示")) })
                    StudioFilterChip(crop, { crop = true }, label = { Text(tr("Fill and crop", "铺满裁切")) })
                }
            }

                    if (saveFailed) Text(tr("Could not save this template. Please try again.", "无法保存此模板，请重试。"), color = MaterialTheme.colorScheme.error)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) }
                        TextButton(enabled = valid, onClick = { finishEditing(); onSave(template.copy(name = name.trim(), width = w!!.toFloat(), height = h!!.toFloat(), background = background)) }) { Text(tr("Save template", "保存模板")) }
                    }
                }
            }
        }
    }
}
