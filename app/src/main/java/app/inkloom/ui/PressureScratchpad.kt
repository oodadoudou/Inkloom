package app.inkloom.ui

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.settings.UiLanguage

/** An isolated production canvas: no notebook callbacks, undo journal or document persistence. */
@Composable
internal fun PressureScratchpad(settings: AppSettings) {
    var reset by remember { mutableIntStateOf(0) }
    val chinese = settings.language == UiLanguage.SIMPLIFIED_CHINESE
    val label = if (chinese) "压感试画板" else "Pressure test pad"
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            TextButton(onClick = { reset++ }) { Text(if (chinese) "清空试画板" else "Clear test pad") }
        }
        Text(if (chinese) "用笔从轻到重试画，下一笔立即使用调整后的压感。" else "Draw lightly, then firmly. Changes apply to your next stroke.")
        key(reset) {
            AndroidView(
                factory = { context ->
                    InkCanvasView(context).apply {
                        contentDescription = label
                        setBackgroundColor(AndroidColor.WHITE)
                        activeLayerId = "pressure-test"
                        layerOrder = listOf(activeLayerId)
                        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            val w = right - left
                            val h = bottom - top
                            if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) {
                                setRasterLayerSurfaces(mapOf(activeLayerId to PixelTileSurface(w, h)))
                            }
                        }
                        // A stylus owns the scratchpad; fingers can continue scrolling settings.
                        setOnTouchListener { _, event ->
                            val stylus = event.getToolType(0) in listOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)
                            if (stylus) parent?.requestDisallowInterceptTouchEvent(true)
                            !stylus
                        }
                    }
                },
                update = { view ->
                    view.applyBrushConfiguration(EditorTool.PEN, settings.copy(fingerDrawing = false),
                        null, 28f * view.resources.displayMetrics.density, 1f, AndroidColor.rgb(35, 44, 38), EraserPreset.NORMAL_FREE)
                },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color.White)
                    .border(1.dp, Color(0xFFDADFD9), RoundedCornerShape(10.dp)),
            )
        }
    }
}
