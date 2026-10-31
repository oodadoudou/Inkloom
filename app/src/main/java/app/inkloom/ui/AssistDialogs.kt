package app.inkloom.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.inkloom.document.AssistLayerEdits
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.LayerHierarchy
import app.inkloom.document.PreparedPageGeometry
import app.inkloom.document.isPaintSurfaceLayer
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.tools.ColorizeSettings
import app.inkloom.engine.v5.tools.LineArtColorize
import app.inkloom.engine.v5.tools.ShadingAssist
import app.inkloom.engine.v5.tools.ShadingPreset
import app.inkloom.engine.v5.tools.ShadingSettings
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val PREVIEW_WIDTH = 420

private fun PixelTileSurfaceSnapshot.bitmap(): Bitmap =
    app.inkloom.engine.v5.android.PixelTileBitmapAdapter.render(this, PixelRect(0, 0, logicalWidth, logicalHeight))

private fun pixelsBitmap(pixels: IntArray, width: Int, height: Int): Bitmap =
    androidx.core.graphics.createBitmap(width, height).also { it.setPixels(pixels, 0, width, 0, 0, width, height); it.isPremultiplied = true }

/** The preview: the layer under, and the result drawn over it (multiplied for shading), fitted in a fixed box. */
@Composable
private fun AssistPreview(base: Bitmap?, over: Bitmap?, multiply: Boolean, lines: Bitmap?) {
    Box(Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.dp, MaterialTheme.colorScheme.outline)) {
        Canvas(Modifier.fillMaxSize()) {
            val reference = base ?: over ?: lines ?: return@Canvas
            val scale = minOf(size.width / reference.width, size.height / reference.height)
            val target = IntSize((reference.width * scale).toInt(), (reference.height * scale).toInt())
            val offset = IntOffset(((size.width - target.width) / 2f).toInt(), ((size.height - target.height) / 2f).toInt())
            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(offset.x.toFloat(), offset.y.toFloat()),
                size = androidx.compose.ui.geometry.Size(target.width.toFloat(), target.height.toFloat()))
            base?.let { drawImage(it.asImageBitmap(), dstOffset = offset, dstSize = target) }
            over?.let { drawImage(it.asImageBitmap(), dstOffset = offset, dstSize = target, blendMode = if (multiply) BlendMode.Multiply else BlendMode.SrcOver) }
            lines?.let { drawImage(it.asImageBitmap(), dstOffset = offset, dstSize = target) }
        }
    }
}

/** CSP's Shading Assist: shadows for the active flat-colour layer on a new multiply layer. */
@Composable
internal fun ShadingAssistDialog(page: InkPage, layerId: String, assets: DocumentAssetService, language: UiLanguage, drawingColor: Int,
                                 onDismiss: () -> Unit, runEdit: ((InkPage) -> PreparedPageGeometry) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var settings by remember { mutableStateOf(ShadingSettings()) }
    var preset by remember { mutableStateOf(ShadingPreset.STANDARD) }
    var presetsOpen by remember { mutableStateOf(false) }
    val hasReferences = page.layers.any { it.id != layerId && LayerHierarchy.effectiveReference(page.layers, it) }
    var useLineArt by remember { mutableStateOf(hasReferences) }
    val inputs by produceState<Pair<PixelTileSurfaceSnapshot, PixelTileSurfaceSnapshot?>?>(null, page.id, layerId, useLineArt) {
        value = withContext(Dispatchers.IO) { assets.renderRasterLayerSurface(page, layerId, PREVIEW_WIDTH).snapshot() to
            (if (useLineArt) AssistLayerEdits.referenceLines(assets, page, layerId, PREVIEW_WIDTH) else null) }
    }
    val base = remember(inputs) { inputs?.first?.bitmap() }
    val lineBitmap = remember(inputs) { inputs?.second?.bitmap() }
    var shade by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(inputs, settings) {
        val (colours, lines) = inputs ?: return@LaunchedEffect
        delay(120)
        shade = withContext(Dispatchers.Default) { pixelsBitmap(ShadingAssist.render(colours, lines, settings), colours.logicalWidth, colours.logicalHeight) }
    }
    StudioDialog(onDismissRequest = onDismiss, placement = StudioDialogPlacement.SIDE, title = { Text(tr("Shading assist", "阴影辅助")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AssistPreview(base, shade, multiply = true, lines = lineBitmap)
            StudioSection(tr("Preset", "预设"))
            StudioDropdownField(shadingPresetName(preset, language), presetsOpen, { presetsOpen = it }, Modifier.fillMaxWidth()) {
                ShadingPreset.entries.forEach { p -> StudioMenuItem(text = { Text(shadingPresetName(p, language)) }, onClick = { preset = p; settings = p.settings; presetsOpen = false }) }
            }
            StudioSection(tr("Light source", "光源"))
            StudioSliderRow(tr("Direction", "方向"), settings.angleDegrees, 0f..360f, { settings = settings.copy(angleDegrees = it) }, unit = "°")
            StudioSliderRow(tr("Height", "高度"), settings.elevation * 100f, 0f..100f, { settings = settings.copy(elevation = it / 100f) }, unit = "%")
            StudioCheckRow(tr("Reverse shadows (backlight)", "反转阴影（逆光）"), settings.reverse, { settings = settings.copy(reverse = it) })
            StudioSection(tr("Shading", "阴影"))
            StudioSegmented(listOf(tr("Smooth", "平滑"), tr("Cel", "赛璐璐")), if (settings.cel) 1 else 0, { settings = settings.copy(cel = it == 1) })
            StudioSliderRow(tr("Amount", "阴影量"), settings.amount * 100f, 0f..100f, { settings = settings.copy(amount = it / 100f) }, unit = "%")
            if (!settings.cel) StudioSliderRow(tr("Softness", "柔和度"), settings.softness * 100f, 0f..100f, { settings = settings.copy(softness = it / 100f) }, unit = "%")
            StudioSliderRow(tr("Opacity", "不透明度"), settings.opacity * 100f, 0f..100f, { settings = settings.copy(opacity = it / 100f) }, unit = "%")
            StudioButton(tr("Shadow colour: drawing colour", "阴影颜色：描绘色"), { settings = settings.copy(shadowColorArgb = drawingColor or 0xFF000000.toInt()) })
            StudioCheckRow(tr("Line art from reference layers", "以参考图层为线稿"), useLineArt, { useLineArt = it }, enabled = hasReferences)
        }
    }, confirmButton = { TextButton({
        runEdit { current -> AssistLayerEdits.shading(assets, current, layerId, settings, useLineArt, tr("Shading", "阴影")) }
        onDismiss()
    }) { Text(tr("OK", "确定")) } }, dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "取消")) } })
}

private fun shadingPresetName(preset: ShadingPreset, language: UiLanguage) = when (preset) {
    ShadingPreset.STANDARD -> language.text("Standard", "标准")
    ShadingPreset.MORNING -> language.text("Morning", "早晨")
    ShadingPreset.EVENING -> language.text("Evening", "傍晚")
    ShadingPreset.NIGHT -> language.text("Night", "夜晚")
    ShadingPreset.SIMPLE_LIGHT -> language.text("Simple light", "简单光照")
    ShadingPreset.FAINT_BACKLIGHT -> language.text("Faint backlight", "微弱逆光")
    ShadingPreset.BACKLIGHT -> language.text("Backlight", "逆光")
    ShadingPreset.STAGE_LIGHT -> language.text("Stage light", "舞台灯光")
    ShadingPreset.COLOR_LIGHT -> language.text("Colour light", "彩色光")
    ShadingPreset.HIGH_CONTRAST -> language.text("High contrast", "高对比度")
}

/** CSP's Colorize, without AI: colour the active line art's regions from a hint layer or a palette. */
@Composable
internal fun ColorizeDialog(page: InkPage, lineArtId: String, assets: DocumentAssetService, language: UiLanguage, drawingColor: Int,
                            onDismiss: () -> Unit, runEdit: ((InkPage) -> PreparedPageGeometry) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val candidates = page.layers.filter { it.id != lineArtId && it.isPaintSurfaceLayer() && it.rasterTileSurface != null }
    var mode by remember { mutableIntStateOf(if (candidates.isEmpty()) 0 else 1) }
    var hintId by remember { mutableStateOf(candidates.lastOrNull()?.id) }
    var hintsOpen by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(ColorizeSettings()) }
    val lines by produceState<PixelTileSurfaceSnapshot?>(null, page.id, lineArtId) {
        value = withContext(Dispatchers.IO) { assets.renderRasterLayerSurface(page, lineArtId, PREVIEW_WIDTH).snapshot() }
    }
    val hint by produceState<PixelTileSurfaceSnapshot?>(null, page.id, hintId, mode) {
        value = if (mode == 0) null else hintId?.let { id -> withContext(Dispatchers.IO) { assets.renderRasterLayerSurface(page, id, PREVIEW_WIDTH).snapshot() } }
    }
    val hintColours = remember(hint) { hint?.let(LineArtColorize::hintColors).orEmpty() }
    var order by remember(hintColours) { mutableStateOf(hintColours) }
    val effective = when (mode) {
        0 -> settings.copy(palette = LineArtColorize.palette(drawingColor), priority = emptyList())
        1 -> settings.copy(palette = emptyList(), priority = emptyList(), analyzeColors = true)
        else -> settings.copy(palette = emptyList(), priority = order)
    }
    val lineBitmap = remember(lines) { lines?.bitmap() }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(lines, hint, effective) {
        val art = lines ?: return@LaunchedEffect
        if (mode != 0 && hint == null) { result = null; return@LaunchedEffect }
        delay(120)
        result = withContext(Dispatchers.Default) { pixelsBitmap(LineArtColorize.render(art, if (mode == 0) null else hint, effective), art.logicalWidth, art.logicalHeight) }
    }
    StudioDialog(onDismissRequest = onDismiss, placement = StudioDialogPlacement.SIDE, title = { Text(tr("Colorize", "自动上色")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AssistPreview(result, null, multiply = false, lines = lineBitmap)
            StudioSegmented(listOf(tr("Colorize all", "全部上色"), tr("Use hint layer", "使用提示图层"), tr("Advanced", "高级设置")), mode, { mode = it })
            when (mode) {
                0 -> Unit
                else -> {
                    StudioDropdownField(candidates.firstOrNull { it.id == hintId }?.name ?: tr("Choose the hint layer", "选择提示图层"), hintsOpen, { hintsOpen = it }, Modifier.fillMaxWidth()) {
                        candidates.forEach { layer -> StudioMenuItem(text = { Text(layer.name) }, onClick = { hintId = layer.id; hintsOpen = false }) }
                    }
                }
            }
            if (mode == 2) {
                StudioSection(tr("Colour priority", "颜色优先级"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    order.forEach { c -> Box(Modifier.size(26.dp).background(Color(c)).border(1.dp, MaterialTheme.colorScheme.outline)
                        .clickable { order = listOf(c) + order.filterNot { it == c } }) }
                }
                StudioCheckRow(tr("Analyse colours (largest area wins)", "分析颜色（面积最大优先）"), settings.analyzeColors, { settings = settings.copy(analyzeColors = it) })
                StudioCheckRow(tr("Colour areas without hints", "为无提示区域上色"), settings.fillUnhinted, { settings = settings.copy(fillUnhinted = it) })
            }
            StudioSection(tr("Lines", "线稿"))
            StudioSliderRow(tr("Close gaps", "封闭间隙"), settings.gapClosing.toFloat(), 0f..20f, { settings = settings.copy(gapClosing = it.toInt()) }, unit = "px", decimals = 0)
            StudioCheckRow(tr("Colour under the lines", "在线条下方上色"), settings.fillUnderLines, { settings = settings.copy(fillUnderLines = it) })
            StudioSliderRow(tr("Blur", "模糊"), settings.blur.toFloat(), 0f..30f, { settings = settings.copy(blur = it.toInt()) }, unit = "px", decimals = 0)
        }
    }, confirmButton = { TextButton({
        val chosenHint = if (mode == 0) null else hintId
        runEdit { current -> AssistLayerEdits.colorize(assets, current, lineArtId, chosenHint, effective, tr("Colors", "上色")) }
        onDismiss()
    }, enabled = mode == 0 || hintId != null) { Text(tr("OK", "确定")) } }, dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "取消")) } })
}
