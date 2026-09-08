package app.inkloom.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.runtime.RawDocumentSample
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.BrushToolRole
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.AppSettings
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

private object BrushStrokePreviewCache {
    val workers = Semaphore(2)
    val images = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
}

@Composable
internal fun BrushStrokePreview(brush: ImportedBrush, language: UiLanguage, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = brush.v5PresetJson.orEmpty() + ":" + brush.toolBinding.toString()
    var failure by remember(key, language) { mutableStateOf<String?>(null) }
    val bitmap by produceState(BrushStrokePreviewCache.images.get(key), key, language) {
        if (value != null) return@produceState
        if (brush.toolBinding.role !in setOf(BrushToolRole.PAINT, BrushToolRole.ERASE)) {
            failure = language.text("Canvas-dependent tool", "依赖画布内容的工具")
            return@produceState
        }
        try {
            BrushStrokePreviewCache.workers.withPermit {
                val cached = BrushStrokePreviewCache.images.get(key)
                if (cached != null) { value = cached; return@withPermit }
                val preset = BrushPresetV5Import.requirePreset(brush)
                val view = withContext(Dispatchers.Main.immediate) {
                    InkCanvasView(context).apply {
                        layout(0, 0, 512, 112)
                        activeLayerId = "preview"
                        pageModelWidth = 512f; pageModelHeight = 112f
                        layerOrder = listOf("preview")
                        applyBrushPresetV5Configuration(preset, AppSettings(),
                            brushSize = preset.tip.size.coerceIn(40f, 160f), prepareAssets = false)
                    }
                }
                val rendered = withContext(Dispatchers.Default) {
                    val surface = if (brush.toolBinding.role == BrushToolRole.ERASE) {
                        Bitmap.createBitmap(512, 112, Bitmap.Config.ARGB_8888).let { backdrop ->
                            try {
                                backdrop.eraseColor(AndroidColor.rgb(90, 90, 90))
                                PixelTileBitmapAdapter.import(backdrop)
                            } finally { backdrop.recycle() }
                        }
                    } else PixelTileSurface(512, 112)
                    view.setRasterLayerSurfaces(mapOf("preview" to surface))
                    view.prepareBrushAssetsNow()
                    val samples = (0..80).map { index ->
                        val t = index / 80f
                        RawDocumentSample(x = 28f + 456f * t,
                            y = 56f + 25f * sin((t * 1.6f - .3f) * PI).toFloat(),
                            eventTimeNanos = (index + 1) * 8_000_000L,
                            enqueuedTimeNanos = (index + 1) * 8_000_000L,
                            pressure = .2f + .8f * sin(t * PI).toFloat().coerceAtLeast(0f),
                            tiltRadians = .25f, orientationRadians = 0f,
                            tool = app.inkloom.engine.v5.input.InputTool.STYLUS)
                    }
                    view.renderIsolatedBrushPreview(samples)
                }
                BrushStrokePreviewCache.images.put(key, rendered)
                value = rendered
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            failure = language.text("Preview unavailable", "暂时无法生成笔迹预览")
            android.util.Log.w("BrushStrokePreview", "${brush.id}: ${error.message}")
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), brush.name, Modifier.fillMaxSize()) }
            ?: failure?.let { Text(it, fontSize = 10.sp) }
    }
}
