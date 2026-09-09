package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Fine visual track; Material retains the full touch, keyboard and accessibility target. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrushParameterSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Slider(value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(48.dp), valueRange = valueRange, steps = steps, enabled = enabled,
        onValueChangeFinished = onValueChangeFinished, interactionSource = interactionSource,
        thumb = {
            Box(Modifier.size(18.dp).shadow(1.dp, CircleShape)
                .background(Color.White, CircleShape).border(.75.dp, Color(0xFFC9CFCC), CircleShape))
        },
        track = { state ->
            SliderDefaults.Track(state, Modifier.height(3.dp),
                colors = SliderDefaults.colors(activeTrackColor = app.inkloom.ui.theme.Sage,
                    inactiveTrackColor = Color(0xFFE3E6EB),
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                thumbTrackGapSize = 0.dp, trackInsideCornerSize = 1.5.dp,
                drawStopIndicator = null)
        })
}
