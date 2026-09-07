package app.inkloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.ui.theme.Ink

/** A local gesture draft prevents MOVE writes and discards interrupted adjustments. */
@Composable
internal fun BrushMasterSlider(
    progress: Float,
    description: String,
    display: (Float) -> String,
    onProgress: (Float) -> Unit,
) {
    val currentProgress by rememberUpdatedState(progress.coerceIn(0f, 1f))
    val commit by rememberUpdatedState(onProgress)
    var draft by remember { mutableStateOf<Float?>(null) }
    fun setValue(raw: Float): Boolean {
        if (!raw.isFinite()) return false
        val value = raw.coerceIn(0f, 1f)
        if (value == currentProgress) return false
        commit(value)
        return true
    }
    Box {
        Canvas(
            Modifier.width(34.dp).height(118.dp)
                .semantics {
                    contentDescription = description
                    stateDescription = display(draft ?: progress)
                    progressBarRangeInfo = ProgressBarRangeInfo(draft ?: progress, 0f..1f)
                    setProgress { setValue(it) }
                }
                .onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    val target = when (key.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> currentProgress + .01f
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT -> currentProgress - .01f
                        android.view.KeyEvent.KEYCODE_MOVE_HOME -> 0f
                        android.view.KeyEvent.KEYCODE_MOVE_END -> 1f
                        else -> return@onPreviewKeyEvent false
                    }
                    if (key.action == android.view.KeyEvent.ACTION_DOWN) setValue(target)
                    true
                }
                .focusable()
                .pointerInput(description) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        down.consume()
                        fun valueAt(y: Float) = (1f - (y - 10f) / (size.height - 20f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        draft = valueAt(down.position.y)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                                draft = valueAt(change.position.y)
                                val up = change.changedToUpIgnoreConsumed()
                                change.consume()
                                if (up) {
                                    val value = requireNotNull(draft)
                                    if (value != currentProgress) commit(value)
                                    break
                                }
                                if (!change.pressed) break
                            }
                        } finally {
                            // Android ACTION_CANCEL cancels the pointer coroutine. Never commit here.
                            draft = null
                        }
                    }
                },
        ) {
            val centerX = size.width / 2f
            val top = 10f
            val bottom = size.height - 10f
            drawRoundRect(Color(0xFFD4D4D4), Offset(centerX - 4f, top), Size(8f, bottom - top), CornerRadius(4f))
            val thumbY = bottom - (bottom - top) * (draft ?: progress).coerceIn(0f, 1f)
            drawRoundRect(Ink, Offset(centerX - 4f, thumbY), Size(8f, bottom - thumbY), CornerRadius(4f))
            drawCircle(Color.White, 7f, Offset(centerX, thumbY))
            drawCircle(Color(0xFF262626), 7f, Offset(centerX, thumbY), style = Stroke(1.5f))
        }
        draft?.let { value ->
            Surface(
                Modifier.align(Alignment.CenterEnd).graphicsLayer(translationX = 62f),
                shape = RoundedCornerShape(12.dp), color = Color.White,
                border = CardDefaults.outlinedCardBorder(),
            ) { Text(display(value), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Ink, fontSize = 11.sp) }
        }
    }
}
