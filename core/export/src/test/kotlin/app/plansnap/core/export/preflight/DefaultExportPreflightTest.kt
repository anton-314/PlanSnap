package app.plansnap.core.export.preflight

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.export.RoomOpeningRef
import app.plansnap.core.export.svg.ExportFixtures
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultExportPreflightTest {
    private val preflight = DefaultExportPreflight()

    private fun opening(
        id: String,
        wall: WallId,
        offsetMm: Long,
        widthMm: Long,
    ) = Opening(OpeningId(id), wall, OpeningType.WINDOW, Length(offsetMm), Length(widthMm), null, null, null)

    private fun evaluations(vararg rooms: Room) = rooms.associate { it.id to ExportFixtures.evaluate(it) }

    /** Replaces the opening statuses of a closed evaluation, leaving everything else as evaluated. */
    private fun RoomEvaluation.withOpenings(statuses: Map<OpeningId, OpeningStatus>): RoomEvaluation {
        val map = statuses.toImmutableMap()
        return when (this) {
            is RoomEvaluation.Confirmed -> copy(openingStatuses = map)
            is RoomEvaluation.NeedsAdjustment -> copy(openingStatuses = map)
            is RoomEvaluation.Open -> copy(openingStatuses = map)
            is RoomEvaluation.Conflict -> copy(openingStatuses = map)
            is RoomEvaluation.Adjusted -> copy(openingStatuses = map)
            is RoomEvaluation.Proposed -> copy(openingStatuses = map)
        }
    }

    @Test
    fun `given rooms of every status when checked then they are split into exportable, omitted and unconfirmed`() {
        val project =
            ExportFixtures.project(
                ExportFixtures.open,
                ExportFixtures.confirmed,
                ExportFixtures.conflict,
                ExportFixtures.unconfirmed,
            )
        val evaluations = evaluations(*project.rooms.toTypedArray())
        assertEquals(ShapeStatus.NEEDS_ADJUSTMENT, evaluations.getValue(ExportFixtures.unconfirmed.id).status)

        val report = preflight.check(project, evaluations.toImmutableMap())

        assertEquals(listOf(RoomId("confirmed"), RoomId("gap")), report.exportableRooms.toList())
        assertEquals(listOf(RoomId("open"), RoomId("conflict")), report.omittedRooms.toList())
        assertEquals(listOf(RoomId("gap")), report.unconfirmedRooms.toList())
        assertTrue(report.outOfBoundsOpenings.isEmpty())
        assertFalse(report.isDrawingEmpty)
    }

    @Test
    fun `given only open and conflict rooms when checked then the drawing is empty`() {
        val project = ExportFixtures.project(ExportFixtures.open, ExportFixtures.conflict)
        val report = preflight.check(project, evaluations(*project.rooms.toTypedArray()).toImmutableMap())
        assertTrue(report.isDrawingEmpty)
        assertEquals(2, report.omittedRooms.size)
        assertTrue(report.unconfirmedRooms.isEmpty())
    }

    @Test
    fun `given opening statuses when checked then only out of bounds openings of exportable rooms are reported`() {
        val confirmed = ExportFixtures.confirmed
        val conflict =
            ExportFixtures.conflict.copy(openings = persistentListOf(opening("lost", WallId("x-w0"), 0, 500)))
        val project = ExportFixtures.project(confirmed, conflict)
        val door = OpeningId("door")
        val window = OpeningId("win")
        val evaluations =
            mapOf(
                confirmed.id to
                    ExportFixtures
                        .evaluate(confirmed)
                        .withOpenings(mapOf(door to OpeningStatus.OUT_OF_BOUNDS, window to OpeningStatus.VALID)),
                conflict.id to
                    RoomEvaluation.Conflict(
                        conflict.id,
                        ConflictReason.WALL_TOO_LONG,
                        null,
                        null,
                        persistentMapOf(OpeningId("lost") to OpeningStatus.OUT_OF_BOUNDS),
                    ),
            )

        val report = preflight.check(project, evaluations.toImmutableMap())

        assertEquals(setOf(RoomOpeningRef(confirmed.id, door)), report.outOfBoundsOpenings)
        assertEquals(setOf(conflict.id), report.omittedRooms)
    }

    @Test
    fun `given an opening that reaches the raw length of a wall shortened by the preview then it is not reported`() {
        // The third wall is 4030 mm raw; the adjustment preview shortens it. The window ends exactly at 4030 mm.
        val base = ExportFixtures.unconfirmed
        val room = base.copy(openings = persistentListOf(opening("edge", WallId("g-w2"), 3130, 900)))
        val evaluation = ExportFixtures.evaluate(room)
        check(evaluation is RoomEvaluation.NeedsAdjustment)
        assertTrue(evaluation.shape.lengthsMm[2] < 4030.0)
        assertEquals(OpeningStatus.VALID, evaluation.openingStatuses[OpeningId("edge")])

        val report = preflight.check(ExportFixtures.project(room), persistentMapOf(room.id to evaluation))

        assertTrue(report.outOfBoundsOpenings.isEmpty())
        assertEquals(setOf(room.id), report.unconfirmedRooms)
    }

    @Test
    fun `given an opening beyond its raw wall when evaluated then the real evaluator status is reported`() {
        val base = ExportFixtures.unconfirmed
        val room = base.copy(openings = persistentListOf(opening("beyond", WallId("g-w1"), 2500, 900)))
        val project = ExportFixtures.project(room)

        val report = preflight.check(project, evaluations(room).toImmutableMap())

        assertEquals(setOf(RoomOpeningRef(room.id, OpeningId("beyond"))), report.outOfBoundsOpenings)
    }

    @Test
    fun `given a room without evaluation when checked then it is rejected`() {
        val project = ExportFixtures.project(ExportFixtures.confirmed)
        assertThrows(IllegalArgumentException::class.java) { preflight.check(project, persistentMapOf()) }
    }

    @Test
    fun `given evaluations of unknown rooms when checked then they are ignored`() {
        val project = ExportFixtures.project(ExportFixtures.confirmed)
        val evaluations = evaluations(ExportFixtures.confirmed, ExportFixtures.open)
        val report = preflight.check(project, evaluations.toImmutableMap())
        assertEquals(setOf(ExportFixtures.confirmed.id), report.exportableRooms)
        assertTrue(report.omittedRooms.isEmpty())
    }
}
