package app.inkloom.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.get
import app.inkloom.document.ReferenceImage
import app.inkloom.document.ReferenceImageStore
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CSP's Sub view: reference pictures beside the canvas, kept by the app and never part of a
 * notebook. Step through them, pan and pinch to look closer, flip, and pick the drawing colour
 * from them with the eyedropper.
 */
@Composable
internal fun SubViewPanel(language: UiLanguage, onPickColor: (Int) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { ReferenceImageStore(context.filesDir) }
    var images by remember { mutableStateOf(store.list()) }
    var index by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val image = images.getOrNull(index.coerceIn(0, (images.size - 1).coerceAtLeast(0)))
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val failures = withContext(Dispatchers.IO) {
                uris.count { uri ->
                    runCatching {
                        val name = uri.lastPathSegment.orEmpty().substringAfterLast('/').substringBeforeLast('.').ifBlank { "Image" }
                        val extension = context.contentResolver.getType(uri)?.substringAfter('/') ?: "img"
                        context.contentResolver.openInputStream(uri)!!.use { store.add(name, extension, it) }
                    }.isFailure
                }
            }
            val before = images.size
            images = store.list()
            if (images.size > before) index = before
            message = if (failures > 0) tr("$failures image(s) could not be added.", "有 $failures 张图片无法添加。") else null
        }
    }
    Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ index = (index - 1).mod(images.size.coerceAtLeast(1)) }, Modifier.size(32.dp), enabled = images.size > 1) {
                Icon(Icons.Outlined.ChevronLeft, tr("Previous image", "上一张"))
            }
            Text(image?.let { "${it.name} · ${index + 1}/${images.size}" } ?: tr("No images", "没有图片"), Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton({ index = (index + 1).mod(images.size.coerceAtLeast(1)) }, Modifier.size(32.dp), enabled = images.size > 1) {
                Icon(Icons.Outlined.ChevronRight, tr("Next image", "下一张"))
            }
            IconButton({ importer.launch(arrayOf("image/*")) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.AddPhotoAlternate, tr("Import images", "导入图片")) }
            IconButton({ image?.let { images = store.remove(it.id); index = index.coerceAtMost((images.size - 1).coerceAtLeast(0)) } },
                Modifier.size(32.dp), enabled = image != null) { Icon(Icons.Outlined.Delete, tr("Remove image", "移除图片")) }
        }
        if (image == null) {
            StudioButton(tr("Import images…", "导入图片…"), { importer.launch(arrayOf("image/*")) })
        } else ReferenceViewer(image, store, language, onPickColor, Modifier.weight(1f))
        message?.let { StudioNote(it) }
    }
}

@Composable
private fun ReferenceViewer(image: ReferenceImage, store: ReferenceImageStore, language: UiLanguage, onPickColor: (Int) -> Unit, modifier: Modifier) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var bitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(image.id) { mutableStateOf(false) }
    LaunchedEffect(image.id) {
        bitmap = withContext(Dispatchers.IO) { runCatching { decodeReference(store, image) }.getOrNull() }
        failed = bitmap == null
    }
    var zoom by remember(image.id) { mutableFloatStateOf(1f) }
    var pan by remember(image.id) { mutableStateOf(Offset.Zero) }
    var flipped by remember(image.id) { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val current by rememberUpdatedState(bitmap)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(Color(0xFF2A2A2A))
            .semantics { contentDescription = tr("Sub view image", "子视图图片"); stateDescription = if (picking) tr("Eyedropper", "取色") else "" }
            .pointerInput(image.id) {
                detectTransformGestures { _, move, scale, _ -> zoom = (zoom * scale).coerceIn(.1f, 32f); pan += move }
            }
            .pointerInput(image.id) {
                detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }, onTap = { tap ->
                    val source = current ?: return@detectTapGestures
                    if (!picking) return@detectTapGestures
                    val fit = minOf(size.width / source.width.toFloat(), size.height / source.height.toFloat()) * zoom
                    // Undo the view: pan from the centre, the flip, then the fitted scale.
                    var x = (tap.x - size.width / 2f - pan.x) / fit
                    val y = (tap.y - size.height / 2f - pan.y) / fit
                    if (flipped) x = -x
                    val px = (x + source.width / 2f).roundToInt(); val py = (y + source.height / 2f).roundToInt()
                    if (px !in 0 until source.width || py !in 0 until source.height) return@detectTapGestures
                    val color = source[px, py]
                    if (color ushr 24 == 0) return@detectTapGestures
                    picked = color or (0xFF shl 24)
                    onPickColor(color or (0xFF shl 24))
                })
            }) {
            bitmap?.let { source ->
                Canvas(Modifier.fillMaxSize()) {
                    val fit = minOf(size.width / source.width, size.height / source.height)
                    withTransform({
                        translate(size.width / 2f + pan.x, size.height / 2f + pan.y)
                        scale(if (flipped) -fit * zoom else fit * zoom, fit * zoom, Offset.Zero)
                    }) {
                        drawImage(source.asImageBitmap(), dstOffset = IntOffset(-source.width / 2, -source.height / 2), dstSize = IntSize(source.width, source.height))
                    }
                }
            }
            if (failed) StudioNote(tr("This image could not be opened.", "无法打开此图片。"), Modifier.align(Alignment.Center))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ picking = !picking }, Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Colorize, tr("Obtain color", "获取颜色"), tint = if (picking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            picked?.let { Box(Modifier.size(18.dp).background(Color(it))) }
            StudioButton(tr("Fit", "适合"), { zoom = 1f; pan = Offset.Zero })
            IconButton({ flipped = !flipped }, Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Flip, tr("Flip horizontally", "左右翻转"), tint = if (flipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A software bitmap no larger than 2048 px on its longest side, so colours can be read back. */
private fun decodeReference(store: ReferenceImageStore, image: ReferenceImage): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(store.file(image))) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        if (longest > 2048) decoder.setTargetSize(info.size.width * 2048 / longest, info.size.height * 2048 / longest)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
