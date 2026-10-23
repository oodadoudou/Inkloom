package app.inkloom.ui

import android.graphics.Bitmap
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import app.inkloom.document.BookBinding
import app.inkloom.document.BookCamera
import app.inkloom.document.BookFace
import app.inkloom.document.BookGeometry
import app.inkloom.document.BookLayout
import app.inkloom.document.BookLeaf
import app.inkloom.document.BookStitch
import app.inkloom.document.CoverImageDecoder
import app.inkloom.document.CoverStyle
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkDocument
import app.inkloom.document.PageCropTransform
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private const val DEFAULT_PITCH = -28f
/** Degrees the book turns per pixel dragged: a full-width drag on the tablet is about one turn. */
private const val DRAG_DEGREES = .16f
private const val TEXTURE_WIDTH = 720
private const val PAPER = 0xFFFDFCF9.toInt()
private const val PAGE_EDGE = 0xFFEFEBE3.toInt()

/**
 * The notebook as a bound book in 3D (CSP's 3D preview for binding): tap a page's outer side or use
 * the arrows to turn it, drag to turn the book, pinch to zoom, double-tap to close or open it.
 * Binding side, saddle or side stitch, the cover and where the first page falls can be changed.
 */
@Composable
internal fun BookPreviewDialog(document: InkDocument, initialPage: Int?, assets: DocumentAssetService, language: UiLanguage, onDismiss: () -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var binding by rememberSaveable { mutableStateOf(BookBinding.LEFT) }
    var stitch by rememberSaveable { mutableStateOf(BookStitch.SIDE) }
    var cover by rememberSaveable { mutableStateOf(true) }
    var firstOnLeft by rememberSaveable { mutableStateOf(false) }
    var yaw by rememberSaveable { mutableFloatStateOf(0f) }
    var pitch by rememberSaveable { mutableFloatStateOf(DEFAULT_PITCH) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    val first = document.pages.first()
    val aspect = (PageCropTransform.visibleHeight(first) / PageCropTransform.visibleWidth(first).coerceAtLeast(1f)).coerceIn(.3f, 3f)
    val leaves = remember(document.pages.size, cover, firstOnLeft) { BookLayout.leaves(document.pages.size, cover, firstOnLeft) }
    val geometry = remember(aspect, leaves.size, stitch) { BookGeometry(aspect, leaves.size, stitch) }
    var target by rememberSaveable { mutableIntStateOf(initialPage?.let { BookLayout.turnedFor(leaves, it) } ?: 0) }
    var position by remember { mutableFloatStateOf(target.toFloat()) }
    var lag by remember { mutableFloatStateOf(1f) }
    var lastOpen by remember { mutableIntStateOf(1) }
    LaunchedEffect(target, leaves) {
        // Long jumps skip straight to the last leaf, which then turns.
        if (abs(target - position) > 1f) position = target - sign(target - position)
        if (position == target.toFloat()) return@LaunchedEffect
        lag = sign(target - position)
        Animatable(position).animateTo(target.toFloat(), tween((560 * abs(target - position)).toInt().coerceAtLeast(180), easing = FastOutSlowInEasing)) { position = value }
    }
    fun relayout(change: () -> Unit) {
        val page = BookLayout.pagesAt(leaves, target).firstOrNull()
        change()
        val next = BookLayout.leaves(document.pages.size, cover, firstOnLeft)
        target = if (target == 0 || page == null) 0 else BookLayout.turnedFor(next, page)
        position = target.toFloat()
    }
    fun turnBy(step: Int) { target = (target + step).coerceIn(0, leaves.size) }
    // The page on the right turns over to the left: forwards for left binding, back for right binding.
    fun turnRightPage() = turnBy(if (binding == BookBinding.LEFT) 1 else -1)
    fun turnLeftPage() = turnBy(if (binding == BookBinding.LEFT) -1 else 1)
    fun toggleOpen() {
        if (target in 1 until leaves.size) { lastOpen = target; target = 0 } else target = lastOpen.coerceIn(1, (leaves.size - 1).coerceAtLeast(1))
    }

    val images = rememberBookImages(document, leaves, target, assets, aspect)
    val shown = BookLayout.pagesAt(leaves, target)
    val spread = when {
        target == 0 -> tr("Front cover", "封面")
        target == leaves.size -> tr("Back cover", "封底")
        shown.isEmpty() -> tr("Inside cover", "封二")
        else -> tr("Page ", "第 ") + shown.joinToString("–") { "${it + 1}" } + tr(" of ${document.pages.size}", " 页，共 ${document.pages.size} 页")
    }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        StudioDialogFrame(Modifier.fillMaxWidth(.94f).fillMaxHeight(.92f), title = { Text(tr("3D book preview — ${document.title}", "3D 书本预览 — ${document.title}")) }, footer = {
            IconButton(::turnLeftPage) { Icon(Icons.Outlined.ChevronLeft, tr("Turn the left page", "翻左页")) }
            IconButton(::turnRightPage) { Icon(Icons.Outlined.ChevronRight, tr("Turn the right page", "翻右页")) }
            Text(spread, Modifier.width(150.dp))
            BookPageField(document.pages.size, language) { page -> target = BookLayout.turnedFor(leaves, page) }
            Spacer(Modifier.weight(1f))
            TextButton(onDismiss) { Text(tr("Close", "关闭")) }
        }) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    StudioSegmented(listOf(tr("Left binding", "左侧装订"), tr("Right binding", "右侧装订")), binding.ordinal, { binding = BookBinding.entries[it] }, Modifier.width(250.dp))
                    StudioSegmented(listOf(tr("Side stitch", "平订"), tr("Saddle stitch", "骑马订")), stitch.ordinal, { stitch = BookStitch.entries[it] }, Modifier.width(250.dp))
                    StudioCheckRow(tr("Cover", "封面"), cover, { relayout { cover = it } })
                    StudioCheckRow(tr("First page on the left", "首页在左"), firstOnLeft, { relayout { firstOnLeft = it } })
                    StudioButton(tr("Reset view", "重置视角"), { yaw = 0f; pitch = DEFAULT_PITCH; zoom = 1f })
                }
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(Color(0xFF3A3A3A))
                    .semantics { contentDescription = tr("3D book", "3D 书本"); stateDescription = spread }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, change, _ ->
                            yaw = (yaw + pan.x * DRAG_DEGREES) % 360f; pitch = (pitch + pan.y * DRAG_DEGREES).coerceIn(-85f, 85f); zoom = (zoom * change).coerceIn(.4f, 4f)
                        }
                    }
                    .pointerInput(binding, leaves.size) {
                        detectTapGestures(onDoubleTap = { toggleOpen() }, onTap = { tap ->
                            if (tap.x > size.width * .6f) turnRightPage() else if (tap.x < size.width * .4f) turnLeftPage()
                        })
                    }) {
                    Canvas(Modifier.fillMaxSize()) {
                        val scene = BookScene(geometry, leaves, binding, position, lag, BookCamera(yaw, pitch), zoom)
                        drawIntoCanvas { BookPreviewRenderer.draw(it.nativeCanvas, size.width, size.height, scene, images::face, PAGE_EDGE, images.coverArgb) }
                    }
                }
                StudioNote(tr("Tap the outer side of a page or use the arrows to turn it. Drag to turn the book, pinch to zoom, double-tap to close or open it.",
                    "点按页面外侧或使用箭头翻页。拖动旋转书本，双指缩放，双击合上或打开。"))
            }
        }
    }
}

@Composable
private fun BookPageField(pageCount: Int, language: UiLanguage, onPage: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        val page = text.toIntOrNull() ?: return@LaunchedEffect
        delay(450)
        if (page in 1..pageCount) onPage(page - 1)
    }
    StudioTextField(text, { value -> text = value.filter(Char::isDigit).take(5) }, language.text("Go to page", "转到页"), Modifier.width(110.dp))
}

/** Textures for the book's faces: the cover, plain sides and the pages near the opening, rendered off the main thread. */
private class BookImages(val coverArgb: Int, val cover: Bitmap, val back: Bitmap, val inside: Bitmap, val blank: Bitmap, val pages: Map<Int, Bitmap>) {
    fun face(face: BookFace): Bitmap = when (face) {
        BookFace.FrontCover -> cover
        BookFace.BackCover -> back
        BookFace.InsideCover -> inside
        BookFace.Blank -> blank
        is BookFace.Page -> pages[face.index] ?: blank
    }
}

@Composable
private fun rememberBookImages(document: InkDocument, leaves: List<BookLeaf>, target: Int, assets: DocumentAssetService, aspect: Float): BookImages {
    val width = TEXTURE_WIDTH; val height = (TEXTURE_WIDTH * aspect).roundToInt()
    val light = document.coverStyle in setOf(CoverStyle.MINIMAL, CoverStyle.PAPER, CoverStyle.GRID, CoverStyle.WHITE, CoverStyle.WHITE_GRID)
    val coverArgb = if (light) 0xFFF4F4F4.toInt() else document.coverArgb.toInt()
    val plain = remember(width, height, coverArgb) {
        fun filled(argb: Int) = createBitmap(width, height).apply { eraseColor(argb) }
        Triple(filled(PAPER), filled(0xFFF4F1EA.toInt()), filled(coverArgb))
    }
    val cover by produceState(plain.third, document.id, document.title, document.coverImagePath, coverArgb) {
        value = withContext(Dispatchers.IO) { runCatching { renderCover(document, coverArgb, light, width, height) }.getOrDefault(plain.third) }
    }
    val pages = remember(document.id) { mutableStateMapOf<Int, Bitmap>() }
    LaunchedEffect(document, leaves, target) {
        val wanted = ((target - 3)..(target + 2)).mapNotNull(leaves::getOrNull).flatMap { listOf(it.front, it.back) }
            .filterIsInstance<BookFace.Page>().map { it.index }.toSet()
        pages.keys.filter { it !in wanted }.forEach { pages.remove(it) }
        wanted.sortedBy { abs(it - (BookLayout.pagesAt(leaves, target).firstOrNull() ?: 0)) }.forEach { index ->
            if (index in pages) return@forEach
            val page = document.pages.getOrNull(index) ?: return@forEach
            withContext(Dispatchers.IO) { runCatching { fitted(assets.renderPageBitmap(page, width), width, height) }.getOrNull() }?.let { pages[index] = it }
        }
    }
    return BookImages(coverArgb, cover, plain.third, plain.second, plain.first, pages)
}

/** A page image letterboxed onto paper at the book's page shape. */
private fun fitted(source: Bitmap, width: Int, height: Int): Bitmap =
    createBitmap(width, height).apply { eraseColor(PAPER); drawFitted(android.graphics.Canvas(this), source, width, height) }

/** Draws [source] centred and scaled to fit, then releases it. */
private fun drawFitted(canvas: android.graphics.Canvas, source: Bitmap, width: Int, height: Int) {
    val scale = minOf(width / source.width.toFloat(), height / source.height.toFloat())
    val w = source.width * scale; val h = source.height * scale
    canvas.drawBitmap(source, null, android.graphics.RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f),
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    source.recycle()
}

private fun renderCover(document: InkDocument, argb: Int, light: Boolean, width: Int, height: Int): Bitmap {
    val out = createBitmap(width, height).apply { eraseColor(argb) }
    val canvas = android.graphics.Canvas(out)
    val image = document.coverImagePath?.takeIf { document.coverStyle == CoverStyle.CUSTOM_IMAGE }?.let { CoverImageDecoder.decode(java.io.File(it), 1024) }
    if (image != null) { drawFitted(canvas, image, width, height); return out }
    val ink = if (light) 0xDD222222.toInt() else 0xEEFFFFFF.toInt()
    val rule = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = width * .004f; alpha = 110 }
    canvas.drawLine(width * .14f, height * .36f, width * .86f, height * .36f, rule)
    val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = width * .075f; textAlign = android.graphics.Paint.Align.CENTER }
    val title = TextUtils.ellipsize(document.title, paint, width * .76f, TextUtils.TruncateAt.END).toString()
    canvas.drawText(title, width / 2f, height * .32f, paint)
    return out
}
