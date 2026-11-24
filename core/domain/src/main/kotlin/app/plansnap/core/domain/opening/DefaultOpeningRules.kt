package app.plansnap.core.domain.opening

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Room
import app.plansnap.core.model.isAdjustmentCurrent

/**
 * Opening rules of DOMAIN section 9.
 *
 * An opening fits if `offset + width <= L_effective + EXACT_CLOSURE_MM`. `L_effective` is the adjusted wall length
 * while the room carries a current closure adjustment, otherwise the raw length. A stale adjustment is ignored because
 * it no longer describes the shape. Openings on the same wall must not overlap, openings that merely touch do not.
 */
class DefaultOpeningRules : OpeningRules {
    override fun statusOf(
        room: Room,
        opening: Opening,
    ): OpeningStatus {
        val index = room.walls.indexOfFirst { it.id == opening.wallId }
        return if (index >= 0 && room.fits(opening, index)) OpeningStatus.VALID else OpeningStatus.OUT_OF_BOUNDS
    }

    override fun check(
        room: Room,
        opening: Opening,
    ): DomainResult<Unit> {
        val index = room.walls.indexOfFirst { it.id == opening.wallId }
        val error =
            when {
                index < 0 -> DomainError.NotFound(EntityKind.WALL, opening.wallId.value)
                !room.fits(opening, index) -> DomainError.RuleViolation(DomainRule.OPENING_OUT_OF_BOUNDS)
                room.overlapsOther(opening) -> DomainError.RuleViolation(DomainRule.OPENING_OVERLAP)
                else -> null
            }
        return if (error == null) DomainResult.Success(Unit) else DomainResult.Failure(error)
    }

    private fun Room.fits(
        opening: Opening,
        wallIndex: Int,
    ): Boolean {
        val adjusted = adjustment?.takeIf { isAdjustmentCurrent(it) }
        val end = opening.endOffset.mm
        return if (adjusted == null) {
            end - GeometryTolerances.EXACT_CLOSURE_MM.toLong() <= walls[wallIndex].length.mm
        } else {
            end.toDouble() - GeometryTolerances.EXACT_CLOSURE_MM <= adjusted.adjustedLengthsMm[wallIndex]
        }
    }

    private fun Room.overlapsOther(opening: Opening): Boolean =
        openings.any {
            it.id != opening.id &&
                it.wallId == opening.wallId &&
                opening.offset < it.endOffset &&
                it.offset < opening.endOffset
        }
}
