package app.plansnap.feature.editor.shape

import androidx.annotation.StringRes
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.feature.editor.R

// String resources of the enumerations behind the texts of the shape sheets: one branch per value, so a new value of
// an enumeration does not compile until it has a text.

@StringRes
internal fun unitRes(unit: LengthUnit): Int =
    when (unit) {
        LengthUnit.MILLIMETER -> R.string.input_unit_mm
        LengthUnit.CENTIMETER -> R.string.input_unit_cm
        LengthUnit.METER -> R.string.input_unit_m
    }

@StringRes
internal fun unitSpokenRes(unit: LengthUnit): Int =
    when (unit) {
        LengthUnit.MILLIMETER -> R.string.input_unit_mm_spoken
        LengthUnit.CENTIMETER -> R.string.input_unit_cm_spoken
        LengthUnit.METER -> R.string.input_unit_m_spoken
    }

@StringRes
internal fun fieldRes(field: ValueField): Int =
    when (field) {
        ValueField.WALL_LENGTH -> R.string.error_field_wall_length
        ValueField.WALL_THICKNESS -> R.string.error_field_wall_thickness
        ValueField.CORNER_ANGLE -> R.string.error_field_corner_angle
        ValueField.DIAGONAL_LENGTH -> R.string.error_field_diagonal_length
        ValueField.OPENING_OFFSET -> R.string.error_field_opening_offset
        ValueField.OPENING_WIDTH -> R.string.error_field_opening_width
        ValueField.OPENING_HEIGHT -> R.string.error_field_opening_height
        ValueField.SILL_HEIGHT -> R.string.error_field_sill_height
        ValueField.ROOM_HEIGHT -> R.string.error_field_room_height
        ValueField.AREA_FACTOR -> R.string.error_field_area_factor
        ValueField.TOLERANCE -> R.string.error_field_tolerance
        ValueField.NAME -> R.string.error_field_name
    }

@StringRes
internal fun reasonRes(reason: InvalidValueReason): Int =
    when (reason) {
        InvalidValueReason.MALFORMED -> R.string.error_reason_malformed
        InvalidValueReason.TOO_SMALL -> R.string.error_reason_too_small
        InvalidValueReason.TOO_LARGE -> R.string.error_reason_too_large
        InvalidValueReason.NOT_REPRESENTABLE -> R.string.error_reason_not_representable
        InvalidValueReason.NOT_APPLICABLE -> R.string.error_reason_not_applicable
    }

@StringRes
internal fun entityRes(entity: EntityKind): Int =
    when (entity) {
        EntityKind.PROJECT -> R.string.error_entity_project
        EntityKind.ROOM -> R.string.error_entity_room
        EntityKind.WALL -> R.string.error_entity_wall
        EntityKind.CORNER -> R.string.error_entity_corner
        EntityKind.DIAGONAL -> R.string.error_entity_diagonal
        EntityKind.OPENING -> R.string.error_entity_opening
    }

@StringRes
internal fun storageRes(problem: StorageProblem): Int =
    when (problem) {
        StorageProblem.READ_FAILED -> R.string.error_storage_read
        StorageProblem.WRITE_FAILED -> R.string.error_storage_write
        StorageProblem.NO_SPACE -> R.string.error_storage_no_space
    }

@StringRes
@Suppress("CyclomaticComplexMethod") // One branch per rule of the domain: a table, not logic.
internal fun ruleRes(rule: DomainRule): Int =
    when (rule) {
        DomainRule.TOO_MANY_WALLS -> R.string.error_rule_too_many_walls
        DomainRule.TOO_FEW_WALLS -> R.string.error_rule_too_few_walls
        DomainRule.ROOM_ALREADY_CLOSED -> R.string.error_rule_room_already_closed
        DomainRule.ROOM_NOT_CLOSED -> R.string.error_rule_room_not_closed
        DomainRule.CORNER_MUST_BE_SPECIFIED -> R.string.error_rule_corner_must_be_specified
        DomainRule.DIAGONAL_INVALID_CORNERS -> R.string.error_rule_diagonal_invalid_corners
        DomainRule.OPENING_OUT_OF_BOUNDS -> R.string.error_rule_opening_out_of_bounds
        DomainRule.OPENING_OVERLAP -> R.string.error_rule_opening_overlap
        DomainRule.DOCK_CYCLE -> R.string.error_rule_dock_cycle
        DomainRule.DOCK_OPENING_NOT_ON_WALL -> R.string.error_rule_dock_opening_not_on_wall
        DomainRule.ADJUSTMENT_NOT_AVAILABLE -> R.string.error_rule_adjustment_not_available
        DomainRule.DUPLICATE_ID -> R.string.error_rule_duplicate_id
        DomainRule.DOCK_TO_SELF -> R.string.error_rule_dock_to_self
        DomainRule.NO_WALL_TO_REMOVE -> R.string.error_rule_no_wall_to_remove
        DomainRule.WALL_HAS_OPENINGS -> R.string.error_rule_wall_has_openings
        DomainRule.WALL_HAS_DOCKED_ROOMS -> R.string.error_rule_wall_has_docked_rooms
        DomainRule.CORNER_HAS_DIAGONALS -> R.string.error_rule_corner_has_diagonals
        DomainRule.OPENING_HAS_DOCKED_ROOMS -> R.string.error_rule_opening_has_docked_rooms
        DomainRule.EXPLICIT_CONFIRMATION_REQUIRED -> R.string.error_rule_explicit_confirmation_required
    }
