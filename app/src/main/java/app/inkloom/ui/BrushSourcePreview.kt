package app.inkloom.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.brushpresetv5.*
import app.inkloom.engine.v5.material.*
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preview the actual shape/texture resource, independently from the full stroke drawing pad. */
internal sealed interface BrushSourcePreview {
    data class Shape(val request: BrushTipMaskRequest) : BrushSourcePreview
    data class Grain(val request: GrainMaskRequest) : BrushSourcePreview
    data class Procedural(val kind: ProceduralTipKind, val hardness: Float, val tooth: Float, val seed: Int) : BrushSourcePreview
    data class Paper(val settings: PaperGrainSettings, val seed: Int) : BrushSourcePreview

    fun render(): Bitmap? {
        val mask = when (this) {
            is Shape -> AndroidBrushTipMaskDecoder.decode(request)
            is Grain -> AndroidBrushTipMaskDecoder.decodeGrain(request)
            is Procedural -> when (kind) {
                ProceduralTipKind.ROUND -> BrushTipMask.solidCircle(96, hardness)
                ProceduralTipKind.GRAPHITE -> BrushTipMask.graphite(96, seed, tooth, hardness)
                ProceduralTipKind.BRISTLE -> BrushTipMask.bristle(96, seed, hardness)
            }
            is Paper -> {
                val paper = DocumentPaperGrain(settings.scale, settings.density, settings.strength, seed)
                BrushTipMask(128, 128, ByteArray(128 * 128) { index ->
                    (paper.coverageAt(index % 128 + .5f, index / 128 + .5f, paper.strengthAt(0f)) * 255f + .5f).toInt().toByte()
                })
            }
        } ?: return null
        val level = mask.nativeLevel(mask.levelForOutput(256f, 256f))
        val pixels = IntArray(level.coverage.size) { index ->
            ((level.coverage[index].toInt() and 255) shl 24) or 0x00ffffff
        }
        return Bitmap.createBitmap(pixels, level.width, level.height, Bitmap.Config.ARGB_8888)
    }
}

internal data class BrushSourceCard(val title: String, val source: BrushSourcePreview)

internal fun brushSourceCards(preset: BrushPresetV5, group: BrushOptionGroup, language: UiLanguage): List<BrushSourceCard> {
    if (group == BrushOptionGroup.DUAL_SHAPE) return brushSourceCards(preset, BrushOptionGroup.TIP, language).filter { it.title.startsWith(language.text("Secondary", "第二")) }
    if (group == BrushOptionGroup.DUAL_GRAIN) return brushSourceCards(preset, BrushOptionGroup.PAPER, language).filter { it.title.startsWith(language.text("Secondary", "第二")) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    val program = CompiledBrushProgramV5.compile(preset)
    fun grainSource(grain: TextureGrainSettings) = BrushSourcePreview.Grain(GrainMaskRequest(
        grain.assetPath, grain.brightness, grain.contrast, grain.reversed, grain.emphasized))
    return buildList {
        if (group == BrushOptionGroup.TIP) {
            val texture = program.tip.tipTexture
            val paths = texture?.assetPaths.orEmpty()
            if (paths.isEmpty()) {
                add(BrushSourceCard(tr("Generated shape", "程序生成形状"), BrushSourcePreview.Procedural(
                    texture?.proceduralKind ?: ProceduralTipKind.ROUND, program.tip.hardness.coerceIn(0f, 1f),
                    texture?.toothStrength?.coerceIn(0f, 1f) ?: .8f, program.renderSeed)))
            } else paths.forEachIndexed { index, path ->
                add(BrushSourceCard(tr("Shape source ${index + 1}", "形状来源 ${index + 1}"), BrushSourcePreview.Shape(
                    BrushTipMaskRequest(path, program.tip.hardness.coerceIn(0f, 1f),
                        alphaThresholdEnabled = texture?.alphaThreshold != null, alphaThreshold = texture?.alphaThreshold ?: 0f))))
            }
            program.dual?.let { dual -> dual.tipAssetPaths.forEachIndexed { index, path ->
                add(BrushSourceCard(tr("Secondary shape ${index + 1}", "第二笔尖来源 ${index + 1}"),
                    BrushSourcePreview.Shape(BrushTipMaskRequest(path, dual.hardness.coerceIn(0f, 1f)))))
            } }
        } else if (group == BrushOptionGroup.PAPER) {
            preset.textureGrain?.let { add(BrushSourceCard(tr("Grain source", "颗粒来源"), grainSource(it))) }
            preset.paperGrain?.let { add(BrushSourceCard(tr("Generated paper grain", "程序纸张颗粒"), BrushSourcePreview.Paper(it, program.renderSeed))) }
            preset.dual?.textureGrain?.let { add(BrushSourceCard(tr("Secondary grain source", "第二颗粒来源"), grainSource(it))) }
        }
    }
}

@Composable
internal fun BrushSourcePreviews(preset: BrushPresetV5, group: BrushOptionGroup, language: UiLanguage) {
    val cards = remember(preset, group, language) { brushSourceCards(preset, group, language) }
    if (cards.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(cards) { _, card ->
            var failure by remember(card.source) { mutableStateOf(false) }
            val preview by produceState<Bitmap?>(null, card.source) {
                value = null
                failure = false
                val result = withContext(Dispatchers.Default) { runCatching { card.source.render() }.getOrNull() }
                failure = result == null
                value = result
            }
            Column(Modifier.width(168.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(card.title, fontSize = 13.sp)
                Box(Modifier.size(168.dp).clip(RoundedCornerShape(8.dp)).background(app.inkloom.ui.theme.Workspace),
                    contentAlignment = Alignment.Center) {
                    if (failure) Text(language.text("Source unavailable", "无法读取来源图案"),
                        Modifier.padding(12.dp), color = Color.White, fontSize = 12.sp)
                    else preview?.let { Image(it.asImageBitmap(), card.title + language.text(" preview", "预览"),
                        Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
                        ?: CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
    }
}
