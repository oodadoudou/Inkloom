package app.inkloom.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.tools.RasterImageFilter
import app.inkloom.settings.RasterFilterPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Preset thumbnails: each saved filter applied to one small built-in sample (a colour sweep with
 * line art), so presets can be told apart before they are chosen. Rendering runs off the UI
 * thread and results are cached by the preset's settings.
 */
internal object RasterFilterThumbnails {
    private const val SIZE = 72
    private val sample: PixelTileSurfaceSnapshot by lazy {
        PixelTileSurface(SIZE, SIZE).apply {
            beginWrite("filter sample").use { w ->
                for (y in 0 until SIZE) for (x in 0 until SIZE) {
                    val hue = x * 360f / SIZE
                    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, .55f, 1f - y * .5f / SIZE))
                    val line = (x - y) in -1..1 || (x + y - SIZE) in -1..1 || (x - SIZE / 2) * (x - SIZE / 2) + (y - SIZE / 2) * (y - SIZE / 2) in 380..450
                    w.setPixel(x, y, if (line) 0xFF111111.toInt() else color)
                }
                w.commit().close()
            }
        }.snapshot()
    }
    // Evicted thumbnails may still be on screen (the preset menu shows every preset), so they are
    // left to the garbage collector rather than recycled.
    private val cache = LruCache<RasterFilterPreset, Bitmap>(48)

    /** Settings that change the picture; names and favourites do not. */
    private fun key(preset: RasterFilterPreset) = preset.normalized().copy(id = "", name = "", favorite = false)

    fun render(preset: RasterFilterPreset): Bitmap? = cache.get(key(preset)) ?: runCatching {
        PixelTileBitmapAdapter.render(RasterImageFilter.apply(sample, preset.effect()).snapshot())
    }.getOrNull()?.also { cache.put(key(preset), it) }
}

@Composable
internal fun RasterFilterThumbnail(preset: RasterFilterPreset) {
    val bitmap by produceState<Bitmap?>(null, preset) { value = withContext(Dispatchers.Default) { RasterFilterThumbnails.render(preset) } }
    Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.dp, MaterialTheme.colorScheme.outline)) {
        bitmap?.takeUnless { it.isRecycled }?.let { Image(it.asImageBitmap(), null, Modifier.size(28.dp)) }
            ?: Box(Modifier.size(28.dp).background(Color.Transparent))
    }
}
