package app.plansnap.feature.editor.opening

import app.plansnap.core.common.units.LengthUnit

/**
 * The draft after [change] of the form.
 *
 * @param isNew whether the form creates an opening instead of changing a stored one.
 * @param unit unit of the typed lengths.
 */
internal fun OpeningDraft.changedBy(
    change: OpeningIntent.FormChange,
    isNew: Boolean,
    unit: LengthUnit,
): OpeningDraft =
    when (change) {
        is OpeningIntent.TypeChanged -> OpeningDrafts.retype(this, change.type, isNew, unit)
        is OpeningIntent.WallChanged -> copy(wallId = change.wallId)
        is OpeningIntent.OffsetChanged -> copy(offset = change.text)
        is OpeningIntent.WidthChanged -> copy(width = change.text)
        is OpeningIntent.HeightChanged -> copy(height = change.text)
        is OpeningIntent.SillChanged -> copy(sill = change.text)
        is OpeningIntent.SwingChanged -> copy(swing = change.swing)
    }
