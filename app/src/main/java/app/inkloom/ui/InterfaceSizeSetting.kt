package app.inkloom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.settings.InterfaceScale
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.Ink
import app.inkloom.ui.theme.InterfaceScaling
import app.inkloom.ui.theme.MutedInk

/**
 * Settings › General: how large the interface is drawn. Automatic measures this screen; the
 * slider picks a size, applied when it is released so the controls never move under the finger.
 */
@Composable
internal fun InterfaceSizeSetting(setting: Float, language: UiLanguage, onChange: (Float) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val context = LocalContext.current
    // The screen as Android reports it, before this app's own scaling.
    val measured = remember(context) { InterfaceScaling.resolve(context, InterfaceScale.AUTO) }
    val current = if (setting <= InterfaceScale.AUTO) measured else setting
    var draft by remember(current) { mutableFloatStateOf(current) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Interface size", "界面大小"), Modifier.weight(1f), color = MutedInk, fontSize = 12.sp)
            Text("${InterfaceScale.percent(draft)}%", color = Ink, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StudioFilterChip(selected = setting <= InterfaceScale.AUTO, onClick = { onChange(InterfaceScale.AUTO) },
                modifier = Modifier.weight(1.4f).semantics { contentDescription = "Interface size automatic" },
                label = { Text(tr("Auto · ${InterfaceScale.percent(measured)}%", "自动 · ${InterfaceScale.percent(measured)}%"), fontSize = 12.sp) })
            listOf(.7f, .8f, .9f, 1f).forEach { size ->
                StudioFilterChip(selected = setting > InterfaceScale.AUTO && kotlin.math.abs(setting - size) < .01f, onClick = { onChange(size) },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Interface size ${InterfaceScale.percent(size)}" },
                    label = { Text("${InterfaceScale.percent(size)}%", fontSize = 12.sp) })
            }
        }
        BrushParameterSlider(draft, { draft = InterfaceScale.snap(it) }, valueRange = InterfaceScale.MIN..InterfaceScale.MAX,
            steps = ((InterfaceScale.MAX - InterfaceScale.MIN) / InterfaceScale.STEP).toInt() - 1,
            onValueChangeFinished = { onChange(InterfaceScale.stored(draft)) },
            modifier = Modifier.semantics { contentDescription = "Interface size" })
        Text(tr("Automatic sizes the interface for this screen's real size (${InterfaceScale.percent(measured)}% here).",
            "自动会按此屏幕的实际尺寸调整界面（此处为 ${InterfaceScale.percent(measured)}%）。"), color = MutedInk, fontSize = 11.sp, lineHeight = 15.sp)
    }
}
