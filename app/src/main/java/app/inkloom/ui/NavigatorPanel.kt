package app.inkloom.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.PageCropTransform
import app.inkloom.document.ViewportGeometry
import app.inkloom.document.ViewportState
import app.inkloom.document.ViewportTransform
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt

/** What the navigator shows: the page, how it sits in the window, and the sizes that relate the two. */
internal class NavigatorTarget(val page: InkPage, val viewport: ViewportState, val viewWidth: Float, val viewHeight: Float,
                               val windowWidth: Float, val windowHeight: Float) {
    val geometry get() = ViewportGeometry(windowWidth, windowHeight, viewWidth, viewHeight)
    /** Zoom as a share of the page's own pixels, as the zoom menu shows it. */
    val pixelPercent get() = viewport.scale * viewWidth / PageCropTransform.visibleWidth(page).coerceAtLeast(1f) * 100f
}

/**
 * CSP's Navigator: the page in miniature with a frame round the part in the window. Tap or drag
 * in it to move there; zoom, rotate, flip the view, fit the page or show actual size below.
 */
@Composable
internal fun NavigatorPanel(target: NavigatorTarget?, assets: DocumentAssetService, language: UiLanguage,
                            onViewport: (ViewportState) -> Unit, onFit: () -> Unit, onActualSize: () -> Unit, onToggleMirror: () -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    if (target == null) return
    var thumbnail by remember(target.page.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(target.page) {
        // Pages change with every committed stroke; wait for a pause before re-rendering.
        if (thumbnail != null) delay(400)
        thumbnail = withContext(Dispatchers.IO) { runCatching { assets.renderPageBitmap(target.page, 360) }.getOrNull() } ?: thumbnail
    }
    val current by rememberUpdatedState(target)
    val frame = Color(0xFFE5483B)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp).clipToBounds().background(Color(0xFF2A2A2A))
            .semantics { contentDescription = tr("Navigator preview", "导航预览"); stateDescription = "${target.pixelPercent.roundToInt()}%" }) {
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                fun moveTo(point: Offset) {
                    val t = current
                    val (scale, offset) = navigatorFit(size.width.toFloat(), size.height.toFloat(), t.viewWidth, t.viewHeight)
                    onViewport(ViewportTransform.centerOn(t.viewport, (point.x - offset.x) / scale, (point.y - offset.y) / scale, t.viewWidth, t.viewHeight, t.geometry))
                }
                detectTapGestures { moveTo(it) }
            }.pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val t = current
                    val (scale, offset) = navigatorFit(size.width.toFloat(), size.height.toFloat(), t.viewWidth, t.viewHeight)
                    onViewport(ViewportTransform.centerOn(t.viewport, (change.position.x - offset.x) / scale, (change.position.y - offset.y) / scale, t.viewWidth, t.viewHeight, t.geometry))
                }
            }) {
                val (scale, offset) = navigatorFit(size.width, size.height, target.viewWidth, target.viewHeight)
                val width = (target.viewWidth * scale).toInt(); val height = (target.viewHeight * scale).toInt()
                thumbnail?.takeUnless { it.isRecycled }?.let {
                    drawImage(it.asImageBitmap(), dstOffset = IntOffset(offset.x.toInt(), offset.y.toInt()), dstSize = IntSize(width, height))
                } ?: drawRect(Color.White, offset, androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat()))
                val outline = ViewportTransform.visibleOutline(target.viewport, target.windowWidth, target.windowHeight, target.viewWidth, target.viewHeight)
                val path = Path().apply {
                    moveTo(offset.x + outline[0] * scale, offset.y + outline[1] * scale)
                    for (i in 1 until 4) lineTo(offset.x + outline[2 * i] * scale, offset.y + outline[2 * i + 1] * scale)
                    close()
                }
                drawPath(path, frame, style = Stroke(2.dp.toPx()))
            }
        }
        NavigatorZoomRow(target, language) { scale ->
            onViewport(ViewportTransform.zoomAroundScreen(target.viewport, scale / target.viewport.scale.coerceAtLeast(.0001f), 0f, 0f, geometry = target.geometry))
        }
        StudioSliderRow(tr("Rotation", "旋转"), target.viewport.rotationDegrees, -180f..180f, {
            onViewport(ViewportTransform.rotateBy(target.viewport, it - target.viewport.rotationDegrees, target.geometry))
        }, unit = "°", decimals = 0, labelWidth = 64.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            StudioButton(tr("Fit", "适合"), onFit)
            StudioButton("100%", onActualSize)
            StudioButton(tr("Reset rotation", "重置旋转"), { onViewport(ViewportTransform.resetRotation(target.viewport, target.geometry)) },
                enabled = target.viewport.rotationDegrees != 0f)
            IconButton(onToggleMirror, Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Flip, tr("Flip view horizontally", "左右翻转视图"),
                    tint = if (target.viewport.mirrored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Zoom on a logarithmic slider, labelled in page pixels, with step buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigatorZoomRow(target: NavigatorTarget, language: UiLanguage, onScale: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val low = ln(ViewportTransform.MINIMUM_SCALE); val high = ln(ViewportTransform.MAXIMUM_SCALE)
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onScale(target.viewport.scale / 1.25f) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Remove, language.text("Zoom out", "缩小")) }
        Slider(ln(target.viewport.scale.coerceIn(ViewportTransform.MINIMUM_SCALE, ViewportTransform.MAXIMUM_SCALE)), { onScale(exp(it)) },
            Modifier.weight(1f).height(28.dp).semantics { contentDescription = language.text("Zoom", "缩放") }, valueRange = low..high,
            thumb = { Box(Modifier.size(14.dp).background(colors.onSurface, CircleShape)) },
            track = { state -> SliderDefaults.Track(state, Modifier.height(2.dp),
                colors = SliderDefaults.colors(activeTrackColor = colors.onSurface, inactiveTrackColor = colors.outline)) })
        IconButton({ onScale(target.viewport.scale * 1.25f) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Add, language.text("Zoom in", "放大")) }
        Text("${target.pixelPercent.roundToInt()}%", Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/** Scale and offset that fit the page View into a box of [width] × [height]. */
internal fun navigatorFit(width: Float, height: Float, viewWidth: Float, viewHeight: Float): Pair<Float, Offset> {
    val scale = minOf(width / viewWidth.coerceAtLeast(1f), height / viewHeight.coerceAtLeast(1f)) * .92f
    return scale to Offset((width - viewWidth * scale) / 2f, (height - viewHeight * scale) / 2f)
}
