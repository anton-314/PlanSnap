package app.plansnap.core.domain.opening

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.domain.evaluation.EffectiveWallLength
import app.plansnap.core.domain.evaluation.ShapeBuilder
import app.plansnap.core.domain.evaluation.acceptedOverlay
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.traverse.DefaultTraverse
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Room

/**
 * Opening rules of DOMAIN section 9.
 *
 * An opening fits if `offset + width <= L_effective + EXACT_CLOSURE_MM`. `L_effective` comes from
 * [EffectiveWallLength], the same definition the room evaluator uses (ADR 0021): the adjusted length while the room
 * carries an overlay that DOMAIN section 4, step 2 accepts, otherwise the raw length. A stale or structurally invalid
 * overlay is ignored, a preview is never used. Openings on the same wall must not overlap, openings that merely touch
 * do not.
 */
class DefaultOpeningRules : OpeningRules {
    private val shapes = ShapeBuilder(DefaultTraverse(), DefaultPolygonAnalyzer())

    override fun statusOf(
        room: Room,
        opening: Opening,
    ): OpeningStatus = EffectiveWallLength.statusOf(room, shapes.acceptedOverlay(room), opening)

    override fun check(
        room: Room,
        opening: Opening,
    ): DomainResult<Unit> {
        val error =
            when {
                room.walls.none { it.id == opening.wallId } -> {
                    DomainError.NotFound(EntityKind.WALL, opening.wallId.value)
                }

                statusOf(room, opening) == OpeningStatus.OUT_OF_BOUNDS -> {
                    DomainError.RuleViolation(DomainRule.OPENING_OUT_OF_BOUNDS)
                }

                room.overlapsOther(opening) -> {
                    DomainError.RuleViolation(DomainRule.OPENING_OVERLAP)
                }

                else -> {
                    null
                }
            }
        return if (error == null) DomainResult.Success(Unit) else DomainResult.Failure(error)
    }

    private fun Room.overlapsOther(opening: Opening): Boolean =
        openings.any {
            it.id != opening.id &&
                it.wallId == opening.wallId &&
                opening.offset < it.endOffset &&
                it.offset < opening.endOffset
        }
}
