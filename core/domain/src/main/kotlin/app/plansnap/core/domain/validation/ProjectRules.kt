package app.plansnap.core.domain.validation

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room

/**
 * Checks what model constructors deliberately leave to the domain (DOMAIN section 11): limits of
 * `GeometryTolerances`, existence of dock targets, host openings on their host wall, and dock cycles.
 *
 * `:core:data` calls it on every imported project before accepting it.
 */
fun interface ProjectValidator {
    /** Returns success for a valid project, otherwise the first violation in room and index order. */
    fun validate(project: Project): DomainResult<Unit>
}

/** Rules for openings on their wall (DOMAIN section 9). */
interface OpeningRules {
    /** Returns the derived status of the stored [opening] in [room] against the effective wall length. */
    fun statusOf(
        room: Room,
        opening: Opening,
    ): OpeningStatus

    /**
     * Checks whether [opening] may be added to or replace its version in [room].
     *
     * @return success, or `OPENING_OUT_OF_BOUNDS`, `OPENING_OVERLAP` or `NotFound` for a missing wall.
     */
    fun check(
        room: Room,
        opening: Opening,
    ): DomainResult<Unit>
}
