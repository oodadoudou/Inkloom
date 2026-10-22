package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Compact controls for studio dialogs: label on the left, value on the right, as in CSP's tool property rows. */
private val StudioFieldShape = RoundedCornerShape(3.dp)

@Composable
internal fun StudioSection(title: String, modifier: Modifier = Modifier) {
    Text(title.uppercase(Locale.ROOT), modifier.padding(top = 10.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One property row: label, thin slider and an editable number box. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier, unit: String = "", decimals: Int = if (range.endInclusive - range.start > 20f) 0 else 1,
    labelWidth: Dp = 96.dp, enabled: Boolean = true, sliderDescription: String = label) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(labelWidth), style = MaterialTheme.typography.bodySmall, color = colors.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Slider(value.coerceIn(range), onValueChange, Modifier.weight(1f).height(28.dp).semantics { contentDescription = sliderDescription },
            enabled = enabled, valueRange = range,
            thumb = { Box(Modifier.size(14.dp).background(colors.onSurface, CircleShape)) },
            track = { state -> SliderDefaults.Track(state, Modifier.height(2.dp), enabled = enabled,
                colors = SliderDefaults.colors(activeTrackColor = colors.onSurface, inactiveTrackColor = colors.outline,
                    activeTickColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent),
                thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
        StudioNumberField(value, range, onValueChange, Modifier.padding(start = 8.dp), decimals, label, enabled)
        // The unit column is always reserved, so number boxes line up in one column.
        Text(unit, Modifier.padding(start = 3.dp).width(18.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

/** A small numeric box; values in [range] apply as typed, and out-of-range input is clamped when editing ends. */
@Composable
internal fun StudioNumberField(value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier, decimals: Int = 0, label: String = "", enabled: Boolean = true) {
    fun format(v: Float) = if (decimals == 0) v.roundToInt().toString() else String.format(Locale.ROOT, "%.${decimals}f", v)
    var text by remember { mutableStateOf(format(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = format(value) }
    fun commit() { text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }?.let { typed ->
        val next = typed.coerceIn(range); if (abs(next - value) > 1e-6f) onValueChange(next); text = format(next) } ?: run { text = format(value) } }
    val colors = MaterialTheme.colorScheme
    // Valid numbers inside the range apply as they are typed, so a dialog button pressed right
    // after typing uses them; anything else is corrected when editing ends.
    BasicTextField(text, { typed -> text = typed.take(10)
        text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it in range && abs(it - value) > 1e-6f }?.let(onValueChange) },
        modifier.width(52.dp).height(26.dp)
        .background(colors.surfaceContainerLowest, StudioFieldShape).border(1.dp, if (focused) colors.onSurface else colors.outline, StudioFieldShape)
        .onFocusChanged { if (focused && !it.isFocused) commit(); focused = it.isFocused }
        .semantics { contentDescription = "$label value" },
        enabled = enabled, singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface, textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }), cursorBrush = SolidColor(colors.onSurface),
        decorationBox = { inner -> Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.CenterEnd) { inner() } })
}

/** CSP-style radio group drawn as joined segments. */
@Composable
internal fun StudioSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.weight(1f).height(28.dp).border(1.dp, colors.outline, StudioFieldShape)) {
            options.forEachIndexed { index, option ->
                Box(Modifier.weight(1f).fillMaxWidth().height(28.dp)
                    .background(if (index == selected) colors.surfaceContainerHighest else colors.surfaceContainerLowest)
                    .selectable(index == selected, role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(option, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (index == selected) colors.onSurface else colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun StudioCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp).toggleable(checked, enabled, Role.Checkbox, onCheckedChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, Modifier.size(18.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A bordered drop-down field: label on the left, current value and arrow on the right. */
@Composable
internal fun StudioDropdownField(value: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
    label: String? = null, menu: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().height(28.dp).background(colors.surfaceContainerLowest, StudioFieldShape)
                .border(1.dp, colors.outline, StudioFieldShape).clickable(role = Role.DropdownList) { onExpandedChange(true) }
                .padding(start = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
            }
            DropdownMenu(expanded, { onExpandedChange(false) }, content = menu)
        }
    }
}

/** Compact single-line text box. */
@Composable
internal fun StudioTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(value, onValueChange, modifier.height(28.dp).background(colors.surfaceContainerLowest, StudioFieldShape)
        .border(1.dp, colors.outline, StudioFieldShape).semantics { contentDescription = placeholder }, singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface), cursorBrush = SolidColor(colors.onSurface),
        decorationBox = { inner -> Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            inner()
        } })
}

/** CSP's bordered push button, for actions inside a dialog body. */
@Composable
internal fun StudioButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: (@Composable RowScope.() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.heightIn(min = 28.dp).background(colors.surfaceContainerHigh, StudioFieldShape)
        .border(1.dp, colors.outline, StudioFieldShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        content?.invoke(this)
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
    }
}

/**
 * Compact menu row with the same parameters as Material's DropdownMenuItem: 36 dp instead of
 * 48 dp, 13 sp text, as in CSP and Photoshop menus.
 */
@Composable
internal fun StudioMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.MenuItemColors? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    @Suppress("UNUSED_PARAMETER") interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
) {
    val content = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f)
    Row(modifier.fillMaxWidth().widthIn(min = 160.dp, max = 360.dp).heightIn(min = 36.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) {
            leadingIcon?.let { Box(Modifier.sizeIn(minWidth = 18.dp, minHeight = 18.dp), contentAlignment = Alignment.Center) { it() } }
            Box(Modifier.weight(1f)) {
                androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = content)) { text() }
            }
            trailingIcon?.let { Box(contentAlignment = Alignment.Center) { it() } }
        }
    }
}
