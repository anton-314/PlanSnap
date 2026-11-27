package app.plansnap.feature.editor.opening

import android.content.res.Resources
import androidx.annotation.StringRes
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.OpeningType
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.shape.unitRes

// String resources of the enumerations behind the opening sheets: one branch per value, so a new value does not
// compile until it has a text.

@StringRes
internal fun typeRes(type: OpeningType): Int =
    when (type) {
        OpeningType.DOOR -> R.string.opening_type_door
        OpeningType.WINDOW -> R.string.opening_type_window
        OpeningType.PASSAGE -> R.string.opening_type_passage
    }

@StringRes
internal fun swingRes(swing: DoorSwing?): Int =
    when (swing) {
        null -> R.string.opening_swing_none
        DoorSwing.HINGE_LEFT_INWARD -> R.string.opening_swing_left_inward
        DoorSwing.HINGE_LEFT_OUTWARD -> R.string.opening_swing_left_outward
        DoorSwing.HINGE_RIGHT_INWARD -> R.string.opening_swing_right_inward
        DoorSwing.HINGE_RIGHT_OUTWARD -> R.string.opening_swing_right_outward
    }

/** [length] with its unit, for example "90 cm". */
internal fun Resources.lengthLabel(
    length: Length,
    unit: LengthUnit,
): String = "${OpeningDrafts.format(length, unit)} ${getString(unitRes(unit))}"
