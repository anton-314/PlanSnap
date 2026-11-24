package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Angle
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.traverse.CornerInput
import app.plansnap.core.geometry.traverse.DiagonalInput
import app.plansnap.core.geometry.traverse.ShapeInput
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Room
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap

/**
 * Returns the first violation of the value limits of `GeometryTolerances` (DOMAIN section 4, step 1), or `null`.
 *
 * The wall count is checked first and yields `RuleViolation(TOO_MANY_WALLS)`, because `InvalidValue` has no field for
 * it. Then each wall in index order: its length must lie in `MIN_WALL_MM..MAX_WALL_MM`, its thickness must not exceed
 * `MAX_THICKNESS_MM`. All other limits are enforced by the model constructors.
 */
internal fun Room.limitViolation(): DomainError? {
    if (walls.size > GeometryTolerances.MAX_WALLS_PER_ROOM) return DomainError.RuleViolation(DomainRule.TOO_MANY_WALLS)
    return walls.firstNotNullOfOrNull { wall ->
        val length = wall.length.mm
        when {
            length < GeometryTolerances.MIN_WALL_MM -> {
                invalid(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL)
            }

            length > GeometryTolerances.MAX_WALL_MM -> {
                invalid(ValueField.WALL_LENGTH, InvalidValueReason.TOO_LARGE)
            }

            wall.thickness.mm > GeometryTolerances.MAX_THICKNESS_MM -> {
                invalid(ValueField.WALL_THICKNESS, InvalidValueReason.TOO_LARGE)
            }

            else -> {
                null
            }
        }
    }
}

private fun invalid(
    field: ValueField,
    reason: InvalidValueReason,
): DomainError = DomainError.InvalidValue(field, reason)

/** Maps a closed room within the value limits to the model-free input of the geometry core. */
internal fun Room.toShapeInput(): ShapeInput =
    ShapeInput(
        lengthsMm = walls.map { it.length.mm },
        corners =
            corners.map {
                when (it) {
                    is CornerSpec.DeclaredRight -> CornerInput.DeclaredRight(it.direction)
                    is CornerSpec.Measured -> CornerInput.Measured(it.direction, it.angle)
                    CornerSpec.Unspecified -> CornerInput.Free
                }
            },
        diagonals = diagonals.map { DiagonalInput(it.fromCorner, it.toCorner, it.length.mm) },
    )

/**
 * Entered turns `τ_i` in radians of an open chain, one per wall (DOMAIN section 3). The entry at index 0 is never used
 * by a walk, an unspecified corner yields 0 there.
 */
internal fun Room.enteredTurnsRad(): List<Double> =
    corners.map {
        when (it) {
            is CornerSpec.DeclaredRight -> Angle.RIGHT.toTurn(it.direction).toRadians()
            is CornerSpec.Measured -> it.angle.toTurn(it.direction).toRadians()
            CornerSpec.Unspecified -> 0.0
        }
    }

/**
 * Derived status of every opening against the effective wall lengths (DOMAIN section 9), in the order of
 * [Room.openings]. An opening fits while `offset + width ≤ L_effective + EXACT_CLOSURE_MM`. Offset and width are
 * already non-negative and positive by construction of the model.
 *
 * @param effectiveLengthsMm one effective length per wall: the displayed shape, otherwise the raw lengths.
 */
internal fun Room.openingStatuses(effectiveLengthsMm: List<Double>): ImmutableMap<OpeningId, OpeningStatus> {
    val wallIndex = walls.withIndex().associate { (index, wall) -> wall.id to index }
    val statuses = LinkedHashMap<OpeningId, OpeningStatus>(openings.size)
    for (opening in openings) {
        val limit = effectiveLengthsMm[wallIndex.getValue(opening.wallId)] + GeometryTolerances.EXACT_CLOSURE_MM
        statuses[opening.id] =
            if (opening.endOffset.mm.toDouble() <= limit) OpeningStatus.VALID else OpeningStatus.OUT_OF_BOUNDS
    }
    return statuses.toImmutableMap()
}
