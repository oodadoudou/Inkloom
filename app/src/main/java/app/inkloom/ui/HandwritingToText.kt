package app.inkloom.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import app.inkloom.document.OnDeviceTextRecognitionService
import app.inkloom.document.PageElement
import app.inkloom.document.PdfTextBounds
import app.inkloom.document.RecognizedTextBlock
import app.inkloom.settings.UiLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lassoed ink: coverage (0..255) cropped to the ink, its page bounds and the layer it came from. */
internal class SelectedInk(val coverage: IntArray, val width: Int, val height: Int, val bounds: PdfTextBounds, val layerId: String)

/**
 * Prepares ink for the bundled text recogniser, which is trained on print: thin pen lines at full
 * page size often read as nothing. Each candidate scales the ink so its height is a few text lines
 * tall and thickens the lines first, so they stay a few pixels wide after scaling.
 */
internal object HandwritingImage {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /** Output heights tried in order, until one reads as text. */
    val TARGET_HEIGHTS = listOf(96, 48, 192)
    private const val PADDING = 16
    private const val STROKE_PX = 3f

    fun inkBounds(coverage: IntArray, width: Int, height: Int): Box? {
        var left = width; var top = height; var right = -1; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) if (coverage[y * width + x] > 24) {
            if (x < left) left = x; if (x > right) right = x; if (y < top) top = y; if (y > bottom) bottom = y
        }
        return if (right < 0) null else Box(left, top, right + 1, bottom + 1)
    }

    fun crop(coverage: IntArray, width: Int, box: Box): IntArray =
        IntArray(box.width * box.height) { index -> coverage[(box.top + index / box.width) * width + box.left + index % box.width] }

    /** Square max filter: every pixel takes the strongest coverage within [radius]. */
    fun dilate(coverage: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius <= 0) return coverage
        val rows = IntArray(coverage.size)
        for (y in 0 until height) for (x in 0 until width) {
            var best = 0
            for (dx in -radius..radius) { val sx = x + dx; if (sx in 0 until width) best = maxOf(best, coverage[y * width + sx]) }
            rows[y * width + x] = best
        }
        val out = IntArray(coverage.size)
        for (y in 0 until height) for (x in 0 until width) {
            var best = 0
            for (dy in -radius..radius) { val sy = y + dy; if (sy in 0 until height) best = maxOf(best, rows[sy * width + x]) }
            out[y * width + x] = best
        }
        return out
    }

    /**
     * Grey pixels (ARGB, dark ink on white) of the ink scaled to [targetHeight], padded. Returns the
     * pixels with their width and height.
     */
    fun prepare(coverage: IntArray, width: Int, height: Int, targetHeight: Int): Triple<IntArray, Int, Int> {
        val scale = (targetHeight.toFloat() / height).coerceAtMost(1f)
        val thick = dilate(coverage, width, height, ((STROKE_PX / scale - 1f) / 2f).toInt().coerceIn(0, 24))
        val outWidth = (width * scale).toInt().coerceAtLeast(1)
        val outHeight = (height * scale).toInt().coerceAtLeast(1)
        val paddedWidth = outWidth + PADDING * 2
        val pixels = IntArray(paddedWidth * (outHeight + PADDING * 2)) { 0xFFFFFFFF.toInt() }
        for (y in 0 until outHeight) for (x in 0 until outWidth) {
            // Area average of the source pixels this output pixel covers.
            val x0 = (x / scale).toInt(); val x1 = ((x + 1) / scale).toInt().coerceIn(x0 + 1, width)
            val y0 = (y / scale).toInt(); val y1 = ((y + 1) / scale).toInt().coerceIn(y0 + 1, height)
            var sum = 0
            for (sy in y0 until y1) for (sx in x0 until x1) sum += thick[sy * width + sx]
            val gray = 255 - sum / ((x1 - x0) * (y1 - y0))
            pixels[(y + PADDING) * paddedWidth + x + PADDING] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        return Triple(pixels, paddedWidth, outHeight + PADDING * 2)
    }
}

/** Recognized lines read top to bottom, then left to right on the same line. */
internal object HandwritingText {
    fun join(blocks: List<RecognizedTextBlock>): String {
        val sorted = blocks.sortedBy { it.bounds.top }
        val lines = mutableListOf<MutableList<RecognizedTextBlock>>()
        sorted.forEach { block ->
            val line = lines.lastOrNull()
            val sameLine = line != null && block.bounds.top < line.maxOf { it.bounds.bottom } - (block.bounds.bottom - block.bounds.top) / 2f
            if (sameLine) line!!.add(block) else lines.add(mutableListOf(block))
        }
        return lines.joinToString("\n") { line -> line.sortedBy { it.bounds.left }.joinToString(" ") { it.text } }.trim()
    }

    /**
     * A text object over the handwriting it replaces: same place and width, the type size taken from
     * the handwriting's line height.
     */
    fun element(text: String, bounds: PdfTextBounds, pageWidth: Float, pageHeight: Float, layerId: String): PageElement.Text {
        val lineCount = text.lines().size.coerceAtLeast(1)
        val height = (bounds.bottom - bounds.top) * pageHeight
        return PageElement.Text(
            x = bounds.left, y = bounds.top,
            width = ((bounds.right - bounds.left) * pageWidth).coerceAtLeast(160f),
            height = height.coerceAtLeast(72f),
            text = text,
            textSize = (height / lineCount * .62f).coerceIn(20f, 96f),
            layerId = layerId,
        )
    }
}

/**
 * GoodNotes / Notability style "convert handwriting to text" for the lassoed ink: recognised on the
 * device with the bundled recogniser, editable before it is placed, then either kept beside the ink
 * or replacing it (the lassoed ink is erased in the same step).
 */
@Composable
internal fun HandwritingToTextDialog(host: EditorBridge, language: UiLanguage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ink = remember { host.canvas?.selectedInkForRecognition() }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(ink != null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(ink) {
        val source = ink ?: return@LaunchedEffect
        val tag = if (language == UiLanguage.SIMPLIFIED_CHINESE) "zh" else "en"
        val result = runCatching {
            withContext(Dispatchers.Default) {
                val recognizer = OnDeviceTextRecognitionService(context)
                HandwritingImage.TARGET_HEIGHTS.firstNotNullOfOrNull { target ->
                    val (pixels, width, height) = HandwritingImage.prepare(source.coverage, source.width, source.height, target)
                    val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                    try { HandwritingText.join(recognizer.recognizeBitmap(bitmap, "selection", tag)).takeIf(String::isNotBlank) }
                    finally { bitmap.recycle() }
                }
            }
        }
        text = result.getOrNull().orEmpty()
        failed = result.isFailure
        busy = false
    }
    fun place(replace: Boolean) {
        val source = ink ?: return
        val (width, height) = host.pageSize() ?: return
        host.insertText(HandwritingText.element(text.trim(), source.bounds, width, height, source.layerId))
        if (replace) host.canvas?.deleteSelection()
        onDismiss()
    }
    StudioDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Convert to text", "转换为文字")) },
        text = {
            Column {
                when {
                    ink == null -> Text(tr("Lasso some handwriting on the current layer first.", "请先在当前图层圈选手写内容。"))
                    busy -> { Text(tr("Reading the handwriting…", "正在识别手写…"), fontSize = 12.sp); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    else -> {
                        if (text.isBlank()) Text(tr(if (failed) "The text could not be read. Type it below." else "No text found. Type it below.",
                            if (failed) "无法识别文字，请在下方输入。" else "未识别到文字，请在下方输入。"), fontSize = 12.sp)
                        StudioOutlinedTextField(text, { text = it }, minLines = 3, label = { Text(tr("Text", "文本")) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { place(replace = true) }, enabled = ink != null && !busy && text.isNotBlank()) { Text(tr("Replace handwriting", "替换手写")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) }
                TextButton(onClick = { place(replace = false) }, enabled = ink != null && !busy && text.isNotBlank()) { Text(tr("Keep both", "保留手写")) }
            }
        },
    )
}
