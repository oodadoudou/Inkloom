package app.inkloom.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.inkloom.document.CoverCropGeometry
import app.inkloom.document.CoverImageDecoder
import app.inkloom.document.PageCrop
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun NotebookCoverCropDialog(
    uri: Uri,
    language: UiLanguage,
    onDismiss: () -> Unit,
    onConfirm: (PageCrop) -> Unit,
) {
    val context = LocalContext.current
    var imageFailed by remember(uri) { mutableStateOf(false) }
    val image by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { CoverImageDecoder.decode(context, uri) }.getOrNull() }
        imageFailed = value == null
    }
    // Compose may still replay the last draw command after the dialog leaves composition.
    // Let GC reclaim this bounded preview instead of recycling it before the frame retires.
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var centerX by remember(uri) { mutableFloatStateOf(.5f) }
    var centerY by remember(uri) { mutableFloatStateOf(.5f) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    val previewImage = image
    val crop = previewImage?.let { CoverCropGeometry.rect(it.width, it.height, zoom, centerX, centerY) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Paper, contentColor = Ink, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(language.text("Crop cover", "裁剪封面"), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (previewImage == null) {
                        Box(Modifier.width(230.dp).height(300.dp).background(Workspace)) {
                            Text(if (imageFailed) language.text("This image could not be opened", "无法打开这张图片")
                                else language.text("Loading image…", "正在加载图片…"), Modifier.padding(18.dp))
                        }
                    } else {
                        val bitmap = previewImage
                        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                        Canvas(Modifier.width(230.dp).aspectRatio(CoverCropGeometry.ASPECT)
                            .background(Workspace).border(1.dp, OutlineSoft)
                            .onSizeChanged { frameSize = it }
                            .pointerInput(bitmap, zoom) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val current = CoverCropGeometry.rect(bitmap.width, bitmap.height, zoom, centerX, centerY)
                                    val moved = CoverCropGeometry.drag(current, drag.x, drag.y,
                                        frameSize.width.toFloat(), frameSize.height.toFloat())
                                    centerX = moved.first; centerY = moved.second
                                }
                            }) {
                            val selected = CoverCropGeometry.rect(bitmap.width, bitmap.height, zoom, centerX, centerY)
                            val left = (selected.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                            val top = (selected.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                            val right = (selected.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
                            val bottom = (selected.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
                            drawImage(imageBitmap, srcOffset = IntOffset(left, top),
                                srcSize = IntSize(right - left, bottom - top),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                        }
                        Text(language.text("Drag the image to position it", "拖动图片调整位置"), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(language.text("Zoom", "缩放"))
                            Slider(zoom, { zoom = it }, valueRange = 1f..4f, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text(language.text("Cancel", "取消")) }
                    Button(onClick = { crop?.let(onConfirm) }, enabled = crop != null) {
                        Text(language.text("Use cover", "使用封面"))
                    }
                }
            }
        }
    }
}
