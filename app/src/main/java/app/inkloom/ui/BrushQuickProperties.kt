package app.inkloom.ui

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.inkloom.brushpresetv5.QuickBrushEditSession
import app.inkloom.model.BrushFormat
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import java.util.Locale

@Composable
internal fun BrushQuickProperties(
    brush: ImportedBrush,
    language: UiLanguage,
    onCommit: (ImportedBrush) -> Unit,
    onEdit: () -> Unit,
) {
    key(brush.id, brush.v5PresetJson) {
        val initial = remember { runCatching { QuickBrushEditSession.start(brush) } }
        var session by remember { mutableStateOf(initial.getOrNull()) }
        var error by remember { mutableStateOf(initial.exceptionOrNull()?.message) }
        val latestCommit by rememberUpdatedState(onCommit)
        val source = initial.getOrNull()
        fun cancel() { session = source }
        fun commit() {
            val draft = session ?: return
            if (!draft.changed) return
            runCatching { draft.commit(language.text("${brush.name} - Modified", "${brush.name} - 修改")) }
                .onSuccess { latestCommit(it) }
                .onFailure { error = it.message; cancel() }
        }
        Column(Modifier.width(320.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(brush.name, style = MaterialTheme.typography.titleMedium)
            if (brush.format == BrushFormat.SYSTEM) {
                Text(language.text("Changes create your own copy.", "修改后将创建你的笔刷副本。"), style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            session?.let { draft ->
                draft.options.forEach { option ->
                    val label = when (option.id) {
                        "flow" -> language.text("Flow", "流量")
                        "pressureSizeEnabled" -> language.text("Pressure controls size", "笔压控制尺寸")
                        "smoothing" -> language.text("Stabilization", "防抖")
                        "rotation" -> language.text("Direction", "方向")
                        "hardness" -> language.text("Hardness", "硬度")
                        "paperGrainDensity" -> language.text("Paper density", "纸纹浓度")
                        else -> option.label
                    }
                    val value = option.get(draft.current)
                    if (option.id == "pressureSizeEnabled") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f))
                            Switch(value >= .5f, onCheckedChange = {
                                session = requireNotNull(session).change(option.id, if (it) 1f else 0f)
                                commit()
                            }, modifier = Modifier.semantics { contentDescription = label })
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f))
                            Text(if (option.id == "rotation") String.format(Locale.ROOT, "%.0f°", value)
                                else String.format(Locale.ROOT, "%.0f%%", value * 100f))
                        }
                        CommittedBrushSlider(value, option.valueRange, label, onChange = {
                            session = requireNotNull(session).change(option.id, it)
                        }, onCommit = ::commit, onCancel = ::cancel)
                    }
                }
            }
            TextButton(onClick = { cancel(); onEdit() }, modifier = Modifier.fillMaxWidth()) {
                Text(language.text("Open full brush editor", "打开完整笔刷编辑器"))
            }
        }
    }
}

/** Keep Material's tap, drag, keyboard and accessibility behavior, but distinguish CANCEL from UP. */
@Composable
private fun CommittedBrushSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    val gesture = remember { BrushSliderGesture() }
    val interactionSource = remember {
        val delegate = MutableInteractionSource()
        object : MutableInteractionSource by delegate {
            fun observe(interaction: Interaction) {
                when (interaction) {
                    is DragInteraction.Start -> gesture.start()
                    is DragInteraction.Stop -> gesture.stop(commit)
                    is DragInteraction.Cancel -> gesture.cancel(cancel)
                }
            }
            override suspend fun emit(interaction: Interaction) { observe(interaction); delegate.emit(interaction) }
            override fun tryEmit(interaction: Interaction): Boolean { observe(interaction); return delegate.tryEmit(interaction) }
        }
    }
    Slider(value.coerceIn(range.start, range.endInclusive), onValueChange = {
        gesture.changed()
        onChange(it)
    }, onValueChangeFinished = { gesture.finished(commit) }, valueRange = range,
        interactionSource = interactionSource, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
}

internal class BrushSliderGesture {
    private var dragging = false
    private var endedByInteraction = false
    fun start() { dragging = true; endedByInteraction = false }
    fun changed() { endedByInteraction = false }
    fun stop(commit: () -> Unit) { dragging = false; endedByInteraction = true; commit() }
    fun cancel(rollback: () -> Unit) { dragging = false; endedByInteraction = true; rollback() }
    fun finished(commit: () -> Unit) {
        if (!dragging && !endedByInteraction) commit()
        endedByInteraction = false
    }
}
