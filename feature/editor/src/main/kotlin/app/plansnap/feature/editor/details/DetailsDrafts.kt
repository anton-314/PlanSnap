package app.plansnap.feature.editor.details

import app.plansnap.core.common.units.DecimalLengthFormatter
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.command.RoomDetails
import app.plansnap.core.model.Room
import java.util.Locale

// The drafts the sheets start with, and the stored values they are compared with when they are applied.

/** The draft of the details sheet that shows the stored values of [room], with the height in [unit]. */
internal fun roomDraft(
    room: Room,
    unit: LengthUnit,
): DetailsSheet.RoomDetails =
    DetailsSheet.RoomDetails(
        roomId = room.id,
        name = room.name,
        typeKey = room.typeKey,
        notes = room.notes,
        heightText = room.heightOverride?.let { lengthInput(it, unit) }.orEmpty(),
        factorText = room.areaFactor.percent.toString(),
    )

/** The overview with the stored default [height] of the project in [unit]. */
internal fun overviewDraft(
    height: Length,
    unit: LengthUnit,
): DetailsSheet.Overview = DetailsSheet.Overview(lengthInput(height, unit))

/** The stored details of this room, in the form the command takes them. */
internal fun Room.storedDetails(): RoomDetails = RoomDetails(name, typeKey, notes, heightOverride, areaFactor)

private fun lengthInput(
    length: Length,
    unit: LengthUnit,
): String = DecimalLengthFormatter.format(length, unit, Locale.getDefault())
