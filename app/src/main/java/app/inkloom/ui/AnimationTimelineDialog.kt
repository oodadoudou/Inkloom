package app.inkloom.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.inkloom.document.AnimationEdits
import app.inkloom.document.AnimationNames
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The timeline of an animation folder: cels in playing order with their durations, the frame
 * rate, the shown cel, and a playback preview. Every change is one undoable page edit.
 */
@Composable
internal fun AnimationTimelineDialog(page: InkPage, folderId: String, assets: DocumentAssetService, language: UiLanguage,
                                     onEdit: ((InkPage) -> InkPage?) -> Unit, onSelectCel: (String) -> Unit, onDismiss: () -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val folder = page.layers.firstOrNull { it.id == folderId }
    val animation = folder?.animation ?: run { onDismiss(); return }
    val names = AnimationNames(tr("Animation", "动画"), tr("Cel", "赛璐"))
    var playing by remember { mutableStateOf(false) }
    var frame by remember { mutableIntStateOf(0) }
    val frames by produceState<Map<String, Bitmap>>(emptyMap(), page, playing) {
        if (!playing) return@produceState
        value = withContext(Dispatchers.IO) {
            animation.cels.associate { cel ->
                val surface = assets.renderRasterLayerSurface(page, cel.layerId, 320).snapshot()
                cel.layerId to app.inkloom.engine.v5.android.PixelTileBitmapAdapter.render(surface, PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight))
            }
        }
    }
    LaunchedEffect(playing, animation.frameRate) {
        while (playing) { delay(1000L / animation.frameRate); frame = (frame + 1) % animation.totalFrames.coerceAtLeast(1) }
    }
    StudioDialog(onDismissRequest = onDismiss, placement = StudioDialogPlacement.SIDE, title = { Text(tr("Animation timeline — ${folder.name}", "动画时间轴 — ${folder.name}")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (playing) Box(Modifier.fillMaxWidth().height(220.dp).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outline)
                .semantics { contentDescription = tr("Animation preview", "动画预览") }, contentAlignment = Alignment.Center) {
                AnimationEdits.celAt(animation, frame)?.let { cel -> frames[cel.layerId]?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StudioButton(if (playing) tr("Stop", "停止") else tr("Play", "播放"), { playing = !playing; frame = 0 }, enabled = animation.cels.isNotEmpty())
                StudioButton(tr("New cel", "新建赛璐"), { onEdit { current -> AnimationEdits.addCel(current, folderId, names)?.first } })
                val seconds = String.format(java.util.Locale.ROOT, "%.1f", animation.totalFrames / animation.frameRate.toFloat())
                StudioNote(tr("${animation.totalFrames} frames · $seconds s", "${animation.totalFrames} 帧 · $seconds 秒"))
            }
            StudioSliderRow(tr("Frame rate", "帧率"), animation.frameRate.toFloat(), 1f..60f, { rate ->
                onEdit { current -> AnimationEdits.update(current, folderId) { it.copy(frameRate = rate.toInt().coerceIn(1, 60)) } } }, unit = "fps", decimals = 0)
            StudioSection(tr("Cels", "赛璐"))
            animation.cels.forEach { cel ->
                val layer = page.layers.firstOrNull { it.id == cel.layerId } ?: return@forEach
                val current = cel.layerId == animation.current
                Row(Modifier.fillMaxWidth().background(if (current) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .clickable { onEdit { AnimationEdits.show(it, folderId, cel.layerId) }; onSelectCel(cel.layerId) }.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(layer.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    StudioButton("−", { onEdit { AnimationEdits.setFrames(it, folderId, cel.layerId, cel.frames - 1) } }, enabled = cel.frames > 1)
                    Text("${cel.frames}", Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall)
                    StudioButton("+", { onEdit { AnimationEdits.setFrames(it, folderId, cel.layerId, cel.frames + 1) } })
                    StudioButton("↑", { onEdit { AnimationEdits.move(it, folderId, cel.layerId, -1) } })
                    StudioButton("↓", { onEdit { AnimationEdits.move(it, folderId, cel.layerId, 1) } })
                    StudioButton("×", { onEdit { AnimationEdits.removeFromTimeline(it, folderId, cel.layerId) } })
                }
            }
            StudioNote(tr("Tap a cel to show and draw on it; the other cels are hidden. Durations are in frames.",
                "点按赛璐以显示并在其上绘画，其他赛璐会隐藏。时长以帧为单位。"))
        }
    }, confirmButton = { TextButton(onDismiss) { Text(tr("Done", "完成")) } })
}

/** The animation folder that [layerId] is, or lies in. */
internal fun activeAnimationFolder(layers: List<app.inkloom.document.InkLayer>, layerId: String): app.inkloom.document.InkLayer? {
    var layer = layers.firstOrNull { it.id == layerId }
    while (layer != null) {
        if (layer.animation != null) return layer
        layer = layer.parentId?.let { id -> layers.firstOrNull { it.id == id } }
    }
    return null
}
