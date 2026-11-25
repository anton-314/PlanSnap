package app.plansnap.core.export.preflight

import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.isExportable
import app.plansnap.core.domain.evaluation.isUnconfirmed
import app.plansnap.core.export.ExportPreflight
import app.plansnap.core.export.ExportPreflightReport
import app.plansnap.core.export.RoomOpeningRef
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentSetOf

/**
 * Findings before an export, derived only from the room evaluations (DOMAIN section 12, REQ-EXP-006, ADR 0022).
 *
 * - A room is exportable exactly if its status is exportable (not `OPEN`, not `CONFLICT`), otherwise it is omitted.
 *   This is the rule every drawing exporter applies.
 * - An exportable room is unconfirmed if its status carries the mark "shape not confirmed".
 * - An opening is reported if its derived status in [RoomEvaluation.openingStatuses] is [OpeningStatus.OUT_OF_BOUNDS]
 *   and its room is exportable. Openings of omitted rooms are not drawn and so not reported. No length is recomputed
 *   here; the evaluation is the only source.
 *
 * All sets follow the order of the rooms in the project and of the openings in their room. Pure and thread safe.
 */
class DefaultExportPreflight : ExportPreflight {
    /** @throws IllegalArgumentException if a room of [project] has no evaluation, as for `ExportRequest`. */
    override fun check(
        project: Project,
        evaluations: ImmutableMap<RoomId, RoomEvaluation>,
    ): ExportPreflightReport {
        val exportable = persistentSetOf<RoomId>().builder()
        val omitted = persistentSetOf<RoomId>().builder()
        val unconfirmed = persistentSetOf<RoomId>().builder()
        val outOfBounds = persistentSetOf<RoomOpeningRef>().builder()
        project.rooms.forEach { room ->
            val evaluation = requireNotNull(evaluations[room.id]) { "Every room needs an evaluation" }
            if (!evaluation.status.isExportable) {
                omitted += room.id
                return@forEach
            }
            exportable += room.id
            if (evaluation.status.isUnconfirmed) unconfirmed += room.id
            room.openings.forEach { opening ->
                if (evaluation.openingStatuses[opening.id] == OpeningStatus.OUT_OF_BOUNDS) {
                    outOfBounds += RoomOpeningRef(room.id, opening.id)
                }
            }
        }
        return ExportPreflightReport(exportable.build(), omitted.build(), unconfirmed.build(), outOfBounds.build())
    }
}
