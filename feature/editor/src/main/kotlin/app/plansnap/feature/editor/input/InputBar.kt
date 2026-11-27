package app.plansnap.feature.editor.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.units.DecimalAngleFormatter
import app.plansnap.core.common.units.DecimalLengthFormatter
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.CornerSpec
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.shape.CornerTarget
import app.plansnap.feature.editor.shape.ShapeIntent
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorLoadState
import app.plansnap.feature.editor.state.EditorUiState
import java.text.DecimalFormatSymbols
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale

private const val MIN_WALLS_TO_CLOSE = 3
private const val ANGLE_KEY_WEIGHT = 0.8f
private val CORNER_PADDING = PaddingValues(horizontal = 4.dp, vertical = 8.dp)

/**
 * Input bar at the bottom of the editor (UX sections 2 and 3): the live value of the wall being typed, the error
 * that explains an invalid value, the choice of the next corner and the numeric keypad. It shows [state] and
 * reports every action as an [EditorIntent]; it holds no state and never opens the system keyboard.
 *
 * @param state state of the editor.
 * @param onIntent receives the intents of the keys.
 * @param modifier modifier of the bar.
 * @param extraKeys up to two further keys of the keypad, for example thickness and diagonal.
 */
@Composable
fun InputBar(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier,
    extraKeys: List<ExtraKey> = emptyList(),
) {
    val locale = ComposeLocale.current.platformLocale
    val separator = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    val room = state.selectedRoom
    // Without any room the first wall creates one, so the keys are usable then too.
    val startsFirstRoom = state.project?.rooms?.isEmpty() == true
    val open = state.load == EditorLoadState.Loaded && (startsFirstRoom || room?.isClosed == false)
    val canConfirm = state.inputText.isNotBlank() && state.inputError == null
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PlanSnapTheme.spacing.l, vertical = PlanSnapTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s)) {
            InputDisplay(
                state = state,
                locale = locale,
                wallNumber = (room?.walls?.size ?: 0) + 1,
                roomClosed = room?.isClosed == true,
                modifier = Modifier.weight(1f),
            )
            if (open && room != null && room.walls.size >= MIN_WALLS_TO_CLOSE) {
                CloseButton(onClick = { onIntent(ShapeIntent.CloseRoom) })
            }
        }
        state.inputError?.let { InputError(it) }
        CornerChoice(
            selected = state.nextCorner.direction,
            enabled = open,
            onSelect = { onIntent(CoreIntent.SetNextCorner(CornerSpec.DeclaredRight(it))) },
        ) {
            AngleButton(
                corner = state.nextCorner,
                enabled = open && room?.walls?.isNotEmpty() == true,
                locale = locale,
                onClick = { onIntent(ShapeIntent.OpenCorner(CornerTarget.Next)) },
            )
        }
        Keypad(
            separator = separator,
            enabled = open,
            canConfirm = canConfirm,
            extraKeys = extraKeys,
            actions =
                KeypadActions(
                    onKey = { key ->
                        val next = KeypadText.apply(state.inputText, key, separator)
                        if (next != state.inputText) onIntent(CoreIntent.InputChanged(next))
                    },
                    onConfirm = { onIntent(CoreIntent.ConfirmWall) },
                ),
        )
    }
}

@Composable
private fun InputDisplay(
    state: EditorUiState,
    locale: Locale,
    wallNumber: Int,
    roomClosed: Boolean,
    modifier: Modifier = Modifier,
) {
    val unit = state.settings.lengthUnit
    val wallLabel = stringResource(R.string.input_wall_label, wallNumber)
    val unitLabel = stringResource(unitLabelRes(unit))
    val unitSpoken = stringResource(unitSpokenRes(unit))
    val thickness = DecimalLengthFormatter.format(state.thickness, unit, locale)
    val empty = state.inputText.isEmpty()
    val spoken =
        if (empty) {
            stringResource(R.string.input_value_empty_spoken, wallLabel)
        } else {
            stringResource(R.string.input_value_spoken, wallLabel, state.inputText, unitSpoken)
        }
    Column(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s)) {
            Text(
                text = wallLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.input_thickness_label, thickness),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = if (empty) "0" else state.inputText,
                style = MaterialTheme.typography.headlineLarge,
                color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = unitLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InputHint(state = state, roomClosed = roomClosed)
    }
}

@Composable
private fun InputError(error: DomainError) {
    val text = inputErrorText(error)
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = PlanSnapIcons.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The choice of the direction of the next wall: left and right, followed by [trailing], which sub-functions use for
 * further corner keys such as the angle.
 */
@Composable
internal fun CornerChoice(
    selected: TurnDirection?,
    enabled: Boolean,
    onSelect: (TurnDirection) -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val group = stringResource(R.string.input_corner_group)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .selectableGroup()
                .semantics { contentDescription = group },
        horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        CornerButton(
            label = stringResource(R.string.input_corner_left),
            description = stringResource(R.string.input_corner_left_spoken),
            isSelected = selected == TurnDirection.LEFT,
            enabled = enabled,
            onClick = { onSelect(TurnDirection.LEFT) },
        )
        CornerButton(
            label = stringResource(R.string.input_corner_right),
            description = stringResource(R.string.input_corner_right_spoken),
            isSelected = selected == TurnDirection.RIGHT,
            enabled = enabled,
            onClick = { onSelect(TurnDirection.RIGHT) },
        )
        trailing()
    }
}

@Composable
private fun RowScope.AngleButton(
    corner: CornerSpec,
    enabled: Boolean,
    locale: Locale,
    onClick: () -> Unit,
) {
    val measured = corner as? CornerSpec.Measured
    val degrees = measured?.let { DecimalAngleFormatter.format(it.angle, locale) }
    val description =
        if (degrees == null) {
            stringResource(R.string.input_key_angle)
        } else {
            stringResource(R.string.input_key_angle_set, degrees)
        }
    val label = stringResource(R.string.input_key_angle_label) + if (degrees == null) "" else " $degrees°"
    val modifier =
        Modifier
            .weight(ANGLE_KEY_WEIGHT)
            .fillMaxHeight()
            .sizeIn(minWidth = PlanSnapTheme.sizes.numericKey, minHeight = PlanSnapTheme.sizes.numericKey)
            .semantics { contentDescription = description }
    val content: @Composable RowScope.() -> Unit =
        { Text(text = label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center) }
    if (measured == null) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, content = content)
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = modifier, content = content)
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val description = stringResource(R.string.input_key_close)
    OutlinedButton(
        onClick = onClick,
        modifier =
            Modifier
                .sizeIn(minHeight = PlanSnapTheme.sizes.numericKey)
                .semantics { contentDescription = description },
    ) {
        Text(text = stringResource(R.string.input_key_close_label), textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowScope.CornerButton(
    label: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val modifier =
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .sizeIn(minHeight = PlanSnapTheme.sizes.minTouchTarget)
            .semantics {
                contentDescription = description
                role = Role.RadioButton
                selected = isSelected
            }
    val content: @Composable RowScope.() -> Unit = {
        if (isSelected) {
            Icon(imageVector = PlanSnapIcons.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
        }
        Text(text = label, textAlign = TextAlign.Center)
    }
    if (isSelected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = CORNER_PADDING,
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            contentPadding = CORNER_PADDING,
            content = content,
        )
    }
}

private fun unitLabelRes(unit: LengthUnit): Int =
    when (unit) {
        LengthUnit.MILLIMETER -> R.string.input_unit_mm
        LengthUnit.CENTIMETER -> R.string.input_unit_cm
        LengthUnit.METER -> R.string.input_unit_m
    }

private fun unitSpokenRes(unit: LengthUnit): Int =
    when (unit) {
        LengthUnit.MILLIMETER -> R.string.input_unit_mm_spoken
        LengthUnit.CENTIMETER -> R.string.input_unit_cm_spoken
        LengthUnit.METER -> R.string.input_unit_m_spoken
    }

@Composable
private fun InputHint(
    state: EditorUiState,
    roomClosed: Boolean,
) {
    val hint =
        when {
            roomClosed -> R.string.input_room_closed_hint
            state.inputText.isEmpty() && state.selectedRoom?.walls.isNullOrEmpty() -> R.string.editor_empty_hint
            else -> null
        }
    hint?.let {
        Text(
            text = stringResource(it),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
