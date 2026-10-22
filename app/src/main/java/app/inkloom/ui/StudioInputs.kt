package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Compact drop-ins for Material inputs, with the same parameters in the same order, so dialogs
 * and panels read like CSP and Photoshop: 28–30 dp fields, thin borders, small labels.
 */

private val InputShape = RoundedCornerShape(3.dp)

/** A compact text box with a small label above it; replaces Material's OutlinedTextField. */
@Composable
internal fun StudioOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.TextFieldColors? = null,
    /** When set, the label sits to the left at this width, as in CSP's property rows. */
    inlineLabelWidth: Dp? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val style = studioFieldTextStyle(textStyle, enabled)
    BasicTextField(value, onValueChange, modifier, enabled = enabled, readOnly = readOnly, textStyle = style,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        maxLines = maxLines, minLines = minLines, visualTransformation = visualTransformation,
        interactionSource = source, cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner -> StudioFieldDecoration(inner, value.isEmpty(), source, style, label, placeholder, leadingIcon,
            trailingIcon, prefix, suffix, supportingText, isError, inlineLabelWidth) })
}

/** [TextFieldValue] form, for fields that keep a selection or cursor. */
@Composable
internal fun StudioOutlinedTextField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.TextFieldColors? = null,
    /** When set, the label sits to the left at this width, as in CSP's property rows. */
    inlineLabelWidth: Dp? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val style = studioFieldTextStyle(textStyle, enabled)
    BasicTextField(value, onValueChange, modifier, enabled = enabled, readOnly = readOnly, textStyle = style,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine,
        maxLines = maxLines, minLines = minLines, visualTransformation = visualTransformation,
        interactionSource = source, cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner -> StudioFieldDecoration(inner, value.text.isEmpty(), source, style, label, placeholder, leadingIcon,
            trailingIcon, prefix, suffix, supportingText, isError, inlineLabelWidth) })
}

@Composable
private fun studioFieldTextStyle(textStyle: TextStyle, enabled: Boolean): TextStyle {
    val colors = MaterialTheme.colorScheme
    return MaterialTheme.typography.bodyMedium.merge(textStyle).copy(color = if (enabled) colors.onSurface else colors.onSurfaceVariant)
}

@Composable
private fun StudioFieldDecoration(inner: @Composable () -> Unit, empty: Boolean, source: MutableInteractionSource, text: TextStyle,
    label: (@Composable () -> Unit)?, placeholder: (@Composable () -> Unit)?, leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?, prefix: (@Composable () -> Unit)?, suffix: (@Composable () -> Unit)?,
    supportingText: (@Composable () -> Unit)?, isError: Boolean, inlineLabelWidth: Dp?) {
    val colors = MaterialTheme.colorScheme
    val focused by source.collectIsFocusedAsState()
    val small = MaterialTheme.typography.labelSmall
    @Composable fun box(modifier: Modifier) = Row(modifier.heightIn(min = 30.dp).background(colors.surfaceContainerLowest, InputShape)
        .border(1.dp, if (isError || focused) colors.onSurface else colors.outline, InputShape)
        .padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
            leadingIcon?.let { Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { it() } }
            prefix?.let { ProvideTextStyle(text) { it() } }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (empty && placeholder != null) CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                ProvideTextStyle(text.copy(color = colors.onSurfaceVariant)) { placeholder() }
            }
            inner()
        }
        CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
            suffix?.let { ProvideTextStyle(text.copy(color = colors.onSurfaceVariant)) { it() } }
            trailingIcon?.let { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { it() } }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (inlineLabelWidth != null) Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            label?.let { Box(Modifier.width(inlineLabelWidth)) {
                CompositionLocalProvider(LocalContentColor provides colors.onSurface) { ProvideTextStyle(MaterialTheme.typography.bodySmall) { it() } } } }
            box(Modifier.weight(1f))
        } else {
            label?.let { CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) { ProvideTextStyle(small) { it() } } }
            box(Modifier.fillMaxWidth())
        }
        supportingText?.let { CompositionLocalProvider(LocalContentColor provides if (isError) colors.onSurface else colors.onSurfaceVariant) {
            ProvideTextStyle(small) { it() } } }
    }
}

/** An 18 dp check box with a 36 dp touch target; replaces Material's Checkbox. */
@Composable
internal fun StudioCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.CheckboxColors? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.size(36.dp).let { if (onCheckedChange != null) it.toggleable(checked, enabled, Role.Checkbox, onCheckedChange) else it },
        contentAlignment = Alignment.Center) {
        Icon(if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, Modifier.size(18.dp), tint = tint)
    }
}

/** A 16 dp radio dot with a 36 dp touch target; replaces Material's RadioButton. */
@Composable
internal fun StudioRadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.RadioButtonColors? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null) {
    val colors = MaterialTheme.colorScheme
    val tint = if (enabled) colors.onSurface else colors.onSurfaceVariant
    Box(modifier.size(36.dp).let { if (onClick != null) it.selectable(selected, enabled, Role.RadioButton, onClick) else it },
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(16.dp).border(1.5.dp, tint, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(8.dp).background(tint, CircleShape))
        }
    }
}

/** A 28 dp toggle chip; replaces Material's FilterChip. Selected chips are filled, as CSP's toggle buttons. */
@Composable
internal fun StudioFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.SelectableChipColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.material3.SelectableChipElevation? = null,
    @Suppress("UNUSED_PARAMETER") border: androidx.compose.foundation.BorderStroke? = null,
    @Suppress("UNUSED_PARAMETER") interactionSource: MutableInteractionSource? = null,
) {
    val colors = MaterialTheme.colorScheme
    val content = when { !enabled -> colors.onSurfaceVariant.copy(alpha = .6f); selected -> colors.onSurface; else -> colors.onSurfaceVariant }
    // Built like Material's chip (a selectable Surface with a check box role), so tests and
    // accessibility services see the same node structure.
    androidx.compose.material3.Surface(selected = selected, onClick = onClick,
        modifier = modifier.padding(end = 4.dp, top = 2.dp, bottom = 2.dp).semantics { role = Role.Checkbox },
        enabled = enabled, shape = InputShape, color = if (selected) colors.surfaceContainerHighest else Color.Transparent,
        contentColor = content, border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.onSurfaceVariant else colors.outline)) {
        Row(Modifier.heightIn(min = 28.dp).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color = content)) {
                leadingIcon?.let { Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { it() } }
                label()
                trailingIcon?.let { Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { it() } }
            }
        }
    }
}

/** Shared muted tone for helper text in studio dialogs. */
@Composable internal fun studioMuted(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** Keeps a single text style for a block of helper text. */
@Composable internal fun StudioNote(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = studioMuted())
