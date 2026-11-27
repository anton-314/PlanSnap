package app.plansnap.feature.editor.details

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.report.AreaMetrics
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.ShapeStatus
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.shape.lengthText
import app.plansnap.feature.editor.shape.rememberResources
import app.plansnap.feature.editor.shape.unitRes
import app.plansnap.feature.editor.state.EditorIntent
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale

/**
 * Content of the area overview sheet: the figures of every room, the totals, the legal note and the default height of
 * the project. Rooms without a shape are marked and missing in the totals.
 *
 * @param overview rows and totals.
 * @param sheet the open sheet, which holds the draft of the default height.
 * @param unit unit of lengths and of the default height.
 * @param onIntent receives the taps.
 */
@Composable
internal fun AreaOverviewContent(
    overview: AreaOverview,
    sheet: DetailsSheet.Overview,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    val format = OverviewFormat(unit, ComposeLocale.current.platformLocale, rememberResources())
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PlanSnapTheme.spacing.l)
                .padding(bottom = PlanSnapTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.s),
    ) {
        Text(
            text = stringResource(R.string.details_overview_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (overview.rows.isEmpty()) {
            Text(text = stringResource(R.string.details_overview_empty), style = MaterialTheme.typography.bodyMedium)
        }
        overview.rows.forEachIndexed { index, row ->
            RoomRow(row, index, format, onIntent)
        }
        Totals(overview, format)
        Text(
            text = stringResource(R.string.details_legal_note),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(TAG_LEGAL),
        )
        HorizontalDivider()
        ProjectHeight(sheet, unit, onIntent)
        PlanSnapOutlinedButton(
            text = stringResource(R.string.details_close),
            onClick = { onIntent(DetailsIntent.Dismiss) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** How the numbers and names of the overview are written. */
private class OverviewFormat(
    private val unit: LengthUnit,
    private val locale: Locale,
    private val resources: Resources,
) {
    fun area(mm2: Double): String = DetailsFormat.area(mm2, locale)

    fun volume(mm3: Double): String = DetailsFormat.volume(mm3, locale)

    fun length(mm: Double): String = resources.lengthText(mm, unit)

    fun typeName(key: RoomTypeKey): String = RoomTypeCatalog.displayName(resources, key)
}

@Composable
private fun RoomRow(
    row: OverviewRow,
    index: Int,
    format: OverviewFormat,
    onIntent: (EditorIntent) -> Unit,
) {
    val name = row.name.ifBlank { stringResource(R.string.details_room_fallback_name, index + 1) }
    val title =
        row.typeKey?.let {
            stringResource(R.string.details_row_title, name, format.typeName(it))
        } ?: name
    Surface(
        onClick = { onIntent(DetailsIntent.OpenRoomDetails(row.roomId)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PlanSnapTheme.sizes.minTouchTarget)
                .testTag("$TAG_ROW_PREFIX${row.roomId.value}")
                .semantics(mergeDescendants = true) { role = Role.Button },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(PlanSnapTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            val metrics = row.metrics
            when {
                metrics != null -> MetricLines(metrics, row, format)
                row.pending -> Text(text = stringResource(R.string.details_row_pending))
                else -> NoShapeNote(row.status)
            }
        }
    }
}

@Composable
private fun MetricLines(
    metrics: AreaMetrics,
    row: OverviewRow,
    format: OverviewFormat,
) {
    Text(stringResource(R.string.details_row_area, format.area(metrics.areaMm2)))
    Text(stringResource(R.string.details_row_perimeter, format.length(metrics.perimeterMm)))
    Text(stringResource(R.string.details_row_factor, row.factor.percent))
    Text(stringResource(R.string.details_row_credited, format.area(metrics.creditedAreaMm2)))
    Text(stringResource(R.string.details_row_volume, format.volume(metrics.volumeMm3)))
    if (row.status == ShapeStatus.PROPOSED || row.status == ShapeStatus.NEEDS_ADJUSTMENT) {
        Row(horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs)) {
            Icon(imageVector = PlanSnapIcons.Warning, contentDescription = null, tint = PlanSnapTheme.colors.warning)
            Text(text = stringResource(R.string.details_row_unconfirmed), color = PlanSnapTheme.colors.warning)
        }
    }
}

/** The mark of a room without a shape: a symbol and a sentence that says it is missing in the totals. */
@Composable
private fun NoShapeNote(status: ShapeStatus?) {
    val text =
        when (status) {
            ShapeStatus.OPEN -> R.string.details_row_no_shape_open
            ShapeStatus.CONFLICT -> R.string.details_row_no_shape_conflict
            else -> R.string.details_row_no_shape_failed
        }
    Row(horizontalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs), verticalAlignment = Alignment.Top) {
        Icon(imageVector = PlanSnapIcons.Warning, contentDescription = null, tint = PlanSnapTheme.colors.warning)
        Text(text = stringResource(text), color = PlanSnapTheme.colors.warning)
    }
}

@Composable
private fun Totals(
    overview: AreaOverview,
    format: OverviewFormat,
) {
    val totals = overview.totals
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TAG_TOTALS).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(PlanSnapTheme.spacing.xs),
    ) {
        Text(text = stringResource(R.string.details_totals_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.details_total_area, format.area(totals.areaMm2)))
        Text(stringResource(R.string.details_total_credited, format.area(totals.creditedAreaMm2)))
        Text(stringResource(R.string.details_total_perimeter, format.length(totals.perimeterMm)))
        Text(stringResource(R.string.details_total_volume, format.volume(totals.volumeMm3)))
        if (overview.omitted > 0) {
            Text(
                text = pluralStringResource(R.plurals.details_total_omitted, overview.omitted, overview.omitted),
                color = PlanSnapTheme.colors.warning,
            )
        }
    }
}

@Composable
private fun ProjectHeight(
    sheet: DetailsSheet.Overview,
    unit: LengthUnit,
    onIntent: (EditorIntent) -> Unit,
) {
    Text(
        text = stringResource(R.string.details_project_height_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    DetailsTextField(
        value = sheet.projectHeightText,
        spec =
            DetailsFieldSpec(
                label = stringResource(R.string.details_height_label, stringResource(unitRes(unit))),
                tag = TAG_PROJECT_HEIGHT,
                hint = stringResource(R.string.details_project_height_hint),
                keyboardType = KeyboardType.Decimal,
            ),
        error = sheet.errors[DetailsField.PROJECT_HEIGHT],
        onChange = { onIntent(DetailsIntent.ProjectHeightChanged(it)) },
    )
    PlanSnapButton(
        text = stringResource(R.string.details_project_height_apply),
        onClick = { onIntent(DetailsIntent.ApplyProjectHeight) },
        modifier = Modifier.fillMaxWidth(),
        enabled = sheet.canApply,
    )
}

internal const val TAG_ROW_PREFIX = "overview-row-"
internal const val TAG_TOTALS = "overview-totals"
internal const val TAG_LEGAL = "overview-legal"
