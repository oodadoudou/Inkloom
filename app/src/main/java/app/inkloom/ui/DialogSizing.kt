package app.inkloom.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Space a dialog body can use inside Material's dialog margins and padding. */
private const val DIALOG_CHROME_DP = 96

/**
 * Dialog content keeps its preferred tablet width but never exceeds a narrow window (split
 * screen, compact devices), where a fixed minimum would clip its right edge.
 */
@Composable
internal fun dialogContentWidth(min: Dp, max: Dp = min): Modifier {
    val available = (LocalConfiguration.current.screenWidthDp - DIALOG_CHROME_DP).coerceAtLeast(200).dp
    val lower = minOf(min, available)
    return Modifier.widthIn(min = lower, max = maxOf(lower, minOf(max, available)))
}
