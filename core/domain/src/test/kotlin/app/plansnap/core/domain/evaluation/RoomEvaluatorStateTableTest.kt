package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.result.errorOrNull
import app.plansnap.core.common.units.Length
import app.plansnap.core.geometry.adjustment.AdjustedShape
import app.plansnap.core.geometry.adjustment.AdjustmentMode
import app.plansnap.core.geometry.adjustment.AdjustmentOutcome
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.solver.SolverOutcome
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.inputFingerprint
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One test per status and reason transition of DOMAIN sections 4 and 5.5 (T-DOM-001). */
class RoomEvaluatorStateTableTest {
    // Step 1: value limits and open rooms.

    @Test
    fun `given a wall below the minimum when evaluated then the length is too small`() {
        assertInvalid(room("9 3000 4000", "F L L"), ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL)
    }

    @Test
    fun `given a wall above the maximum when evaluated then the length is too large`() {
        assertInvalid(room("100001 3000 4000", "F L L"), ValueField.WALL_LENGTH, InvalidValueReason.TOO_LARGE)
    }

    @Test
    fun `given a wall thicker than the maximum when evaluated then the thickness is too large`() {
        val base = room("4000 3000 4000 3000", "F L L L")
        val thick = base.walls[2].copy(thickness = Length(GeometryTolerances.MAX_THICKNESS_MM + 1))
        val invalid = base.copy(walls = base.walls.toPersistentList().replacingAt(2, thick))
        assertInvalid(invalid, ValueField.WALL_THICKNESS, InvalidValueReason.TOO_LARGE)
    }

    @Test
    fun `given more walls than permitted when evaluated then too many walls is reported`() {
        val walls = List(GeometryTolerances.MAX_WALLS_PER_ROOM + 1) { "1000" }.joinToString(" ")
        val corners = (listOf("F") + List(GeometryTolerances.MAX_WALLS_PER_ROOM) { "L" }).joinToString(" ")
        val result =
            runBlocking { DefaultRoomEvaluator(DirectDispatchers).evaluate(room(walls, corners), DEFAULT_TOLERANCE) }
        assertEquals(DomainError.RuleViolation(DomainRule.TOO_MANY_WALLS), result.errorOrNull())
    }

    @Test
    fun `given an open room with an invalid wall when evaluated then the value check comes first`() {
        assertInvalid(room("5", "F", closed = false), ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL)
    }

    @Test
    fun `given walls exactly on the limits when evaluated then both are accepted`() {
        assertEquals(ShapeStatus.CONFIRMED, evaluated(ReferenceRooms.TINY.room).status)
        assertEquals(ShapeStatus.CONFIRMED, evaluated(ReferenceRooms.HUGE.room).status)
    }

    @Test
    fun `given a room without walls when evaluated then it is open at the origin`() {
        val evaluation = evaluated(room("", "", closed = false)) as RoomEvaluation.Open
        assertEquals(listOf(Vec2.ZERO), evaluation.chain)
        assertNull(evaluation.shape)
    }

    @Test
    fun `given a room that is not closed when evaluated then it is open with the walked chain`() {
        val evaluation = evaluated(room("4000 3000 2000", "F L R", closed = false)) as RoomEvaluation.Open
        val expected = listOf(Vec2(0.0, 0.0), Vec2(4000.0, 0.0), Vec2(4000.0, 3000.0), Vec2(6000.0, 3000.0))
        assertEquals(expected.size, evaluation.chain.size)
        expected.zip(evaluation.chain).forEach { (e, a) -> assertTrue(a.isCloseTo(e, POINT_TOLERANCE_MM), "$a") }
        assertEquals(ShapeStatus.OPEN, evaluation.status)
    }

    // Step 2: stored overlay.

    @Test
    fun `given a matching overlay when evaluated then it is adjusted without any recomputation`() {
        val recording = RecordingComponents()
        val adjusted = accepted(OverdeterminedCases.GAP_30_MM.room)
        val evaluation = evaluated(adjusted, evaluator = recording.evaluator()) as RoomEvaluation.Adjusted
        assertEquals(emptyList<String>(), recording.calls)
        assertEquals(adjusted.adjustment!!.adjustedLengthsMm, evaluation.shape.lengthsMm)
        assertEquals(adjusted.adjustment!!.adjustedTurnsRad, evaluation.shape.turnsRad)
    }

    @Test
    fun `given a matching overlay when version and tolerance change then it stays adjusted`() {
        val adjusted = accepted(OverdeterminedCases.RECTANGLE_LARGE_EXPLICIT.room)
        val newer = adjusted.copy(adjustment = adjusted.adjustment!!.copy(solverVersion = 99))
        for (tolerance in listOf(DEFAULT_TOLERANCE, ClosureTolerance(1, 0.0), ClosureTolerance(10_000, 1.0))) {
            assertEquals(ShapeStatus.ADJUSTED, evaluated(newer, tolerance).status, "$tolerance")
        }
    }

    @Test
    fun `given a matching overlay on raw values that now conflict when evaluated then it stays adjusted`() {
        val overlay = accepted(OverdeterminedCases.RECTANGLE_LARGE_EXPLICIT.room).adjustment!!
        val conflicting = OverdeterminedCases.RECTANGLE_LARGE_CONFLICT.room
        assertEquals(ShapeStatus.CONFLICT, evaluated(conflicting).status)
        val stored = conflicting.copy(adjustment = overlay.copy(inputFingerprint = conflicting.inputFingerprint()))
        assertEquals(ShapeStatus.ADJUSTED, evaluated(stored).status)
    }

    @Test
    fun `given an overlay with a stale fingerprint when evaluated then it is ignored`() {
        val adjusted = accepted(OverdeterminedCases.GAP_30_MM.room)
        val edited =
            adjusted.copy(
                walls = adjusted.walls.toPersistentList().replacingAt(0, adjusted.walls[0].copy(length = Length(4031))),
            )
        val recording = RecordingComponents()
        val evaluation = evaluated(edited, evaluator = recording.evaluator())
        assertEquals(ShapeStatus.NEEDS_ADJUSTMENT, evaluation.status)
        assertTrue("adjust" in recording.calls)
    }

    @Test
    fun `given a matching overlay that does not close when evaluated then it is ignored`() {
        val adjusted = accepted(OverdeterminedCases.GAP_30_MM.room)
        val lengths = adjusted.adjustment!!.adjustedLengthsMm
        val open = lengths.toPersistentList().replacingAt(0, lengths[0] + 2 * GeometryTolerances.EXACT_CLOSURE_MM)
        val invalid = adjusted.copy(adjustment = adjusted.adjustment!!.copy(adjustedLengthsMm = open))
        assertEquals(ShapeStatus.NEEDS_ADJUSTMENT, evaluated(invalid).status)
    }

    @Test
    fun `given a matching overlay that crosses itself when evaluated then it is ignored`() {
        val raw = OverdeterminedCases.SELF_INTERSECTING_EXACT.room
        val crossing =
            overlay(
                raw,
                lengths = listOf(2000.0, 2000.0, 1000.0, 3000.0, 1000.0, 1000.0),
                turnsDeg = listOf(90.0, 90.0, 90.0, 90.0, -90.0, -90.0),
            )
        assertEquals(ShapeStatus.CONFLICT, evaluated(crossing).status)
    }

    @Test
    fun `given a matching overlay whose walk overflows when evaluated then it is ignored`() {
        val raw = room("4000 3000 4000 3000", "F L L L")
        val huge = overlay(raw, List(4) { Double.MAX_VALUE / 2 }, listOf(90.0, 0.0, 0.0, 0.0))
        assertEquals(ShapeStatus.CONFIRMED, evaluated(huge).status)
    }

    // Step 3: necessary conditions.

    @Test
    fun `given a wall as long as the others less the feature size when evaluated then the wall is too long`() {
        assertConflict(room("1000 1001 2000", "F L L"), ConflictReason.WALL_TOO_LONG)
        assertTrue(conflictOf(evaluated(room("1000 1001 1999", "F L L"))) != ConflictReason.WALL_TOO_LONG)
    }

    @Test
    fun `given a diagonal longer than its chain when evaluated then it is impossible`() {
        val evaluation =
            evaluated(
                room("4000 3000 4000 3000", "F L L L", "0-2=5000 1-3=7001"),
            ) as RoomEvaluation.Conflict
        assertEquals(ConflictReason.DIAGONAL_IMPOSSIBLE, evaluation.reason)
        assertEquals("room-d1", evaluation.diagonalId?.value)
        val fitting = evaluated(room("4000 3000 4000 3000", "F L L L", "1-3=7000"))
        assertTrue(conflictOf(fitting) != ConflictReason.DIAGONAL_IMPOSSIBLE)
    }

    @Test
    fun `given a diagonal shorter than twice the longest link less the chain when evaluated then it is impossible`() {
        val evaluation = evaluated(room("4000 3000 4000 3000", "F L L L", "0-2=999")) as RoomEvaluation.Conflict
        assertEquals(ConflictReason.DIAGONAL_IMPOSSIBLE, evaluation.reason)
        assertEquals("room-d0", evaluation.diagonalId?.value)
    }

    @Test
    fun `given a diagonal between adjacent corners when evaluated then it is not checked as a chain`() {
        val evaluation = evaluated(room("4000 3000 4000 3000", "F L L L", "0-1=9000"))
        assertTrue(conflictOf(evaluation) != ConflictReason.DIAGONAL_IMPOSSIBLE, "$evaluation")
    }

    // Steps 4 to 6: exact closure and measurement error.

    @Test
    fun `given an exactly closed rectangle when evaluated then it is confirmed without rank analysis`() {
        val evaluation = evaluated(room("4000 3000 4000 3000", "F L L L")) as RoomEvaluation.Confirmed
        assertNull(evaluation.measurementControl)
        assertEquals(12_000_000.0, evaluation.shape.areaMm2, AREA_TOLERANCE_MM2)
        assertEquals(14_000.0, evaluation.shape.perimeterMm, LENGTH_TOLERANCE_MM)
    }

    @Test
    fun `given a gap of exactly the exact closure limit when evaluated then it is confirmed`() {
        assertEquals(ShapeStatus.CONFIRMED, evaluated(room("4000 3000 4001 3000", "F L L L")).status)
    }

    @Test
    fun `given an exactly closed outline that crosses itself when evaluated then no simple shape exists`() {
        assertConflict(OverdeterminedCases.SELF_INTERSECTING_EXACT.room, ConflictReason.NO_SIMPLE_SHAPE)
    }

    @Test
    fun `given a gap just above the exact limit when evaluated then the adjustment needs simple confirmation`() {
        val evaluation = evaluated(room("4000 3000 4002 3000", "F L L L")) as RoomEvaluation.NeedsAdjustment
        assertEquals(AdjustmentConfirmation.SIMPLE, evaluation.proposal.requiredConfirmation)
        assertEquals(2.0, evaluation.closureError.errorMm, LENGTH_TOLERANCE_MM)
        assertEquals(2, evaluation.measurementControl.redundancy)
    }

    @Test
    fun `given a gap exactly at the limit when evaluated then it is a measurement error and above it a deviation`() {
        val tolerance = ClosureTolerance(absoluteMm = 100, relative = 0.0)
        val recording = RecordingComponents()
        assertEquals(
            ShapeStatus.NEEDS_ADJUSTMENT,
            evaluated(room("4000 3000 4100 3000", "F L L L"), tolerance, recording.evaluator()).status,
        )
        assertEquals(listOf(AdjustmentMode.MEASUREMENT_ERROR), recording.modes)
        val beyond = evaluated(room("4000 3000 4101 3000", "F L L L"), tolerance)
        assertEquals(ProposalReason.UNDERDETERMINED, proposalOf(beyond))
    }

    @Test
    fun `given a fixed closing corner and a diagonal when a measurement error is adjusted then r counts both`() {
        val evaluation = evaluated(room("4000 3000 4010 3000", "L L L L", "0-2=5006")) as RoomEvaluation.NeedsAdjustment
        assertEquals(4, evaluation.measurementControl.redundancy)
    }

    @Test
    fun `given a measurement error whose adjustment does not converge when evaluated then it conflicts`() {
        val recording = RecordingComponents(adjustOverride = { AdjustmentOutcome.NotConverged })
        val evaluation = evaluated(OverdeterminedCases.GAP_30_MM.room, evaluator = recording.evaluator())
        assertEquals(ConflictReason.ADJUSTMENT_NOT_CONVERGED, conflictOf(evaluation))
    }

    @Test
    fun `given a measurement error whose adjusted outline crosses itself when evaluated then it conflicts`() {
        val recording = RecordingComponents(adjustOverride = { AdjustmentOutcome.Adjusted(BOWTIE) })
        val evaluation = evaluated(OverdeterminedCases.GAP_30_MM.room, evaluator = recording.evaluator())
        assertEquals(ConflictReason.NO_SIMPLE_SHAPE, conflictOf(evaluation))
    }

    @Test
    fun `given the rectangle of ADR 0019 when the tolerance changes then only the confirmation level differs`() {
        val room = OverdeterminedCases.RECTANGLE_LARGE_EXPLICIT.room
        val wide = evaluated(room, ClosureTolerance(absoluteMm = 250, relative = 0.0)) as RoomEvaluation.NeedsAdjustment
        val standard = evaluated(room) as RoomEvaluation.NeedsAdjustment
        assertEquals(AdjustmentConfirmation.SIMPLE, wide.proposal.requiredConfirmation)
        assertEquals(AdjustmentConfirmation.EXPLICIT, standard.proposal.requiredConfirmation)
        assertEquals(wide.proposal.adjustment, standard.proposal.adjustment)
        assertEquals(223.94, standard.proposal.correctionSumMm, CORRECTION_TOLERANCE_MM)
    }

    // Shape deviation, branch A.

    @Test
    fun `given the trapezoid 3 3 3 5 when evaluated then A2 proposes the shape with the raw lengths`() {
        val reference = ReferenceRooms.TRAPEZOID_3_3_3_5
        val evaluation = evaluated(reference.room) as RoomEvaluation.Proposed
        assertEquals(ProposalReason.UNDERDETERMINED, evaluation.reason)
        assertNull(evaluation.correction)
        assertNotNull(evaluation.recommendation)
        assertEquals(reference.expectedAreaMm2, evaluation.shape.areaMm2, SOLVED_AREA_TOLERANCE_MM2)
        assertEquals(reference.room.walls.map { it.length.mm }, evaluation.rawLengthsMm)
    }

    @Test
    fun `given a determined triangle against its directions when evaluated then A3 reports the violation`() {
        val recording = RecordingComponents()
        val evaluation = evaluated(OverdeterminedCases.TRIANGLE_WRONG_DIRECTION.room, evaluator = recording.evaluator())
        assertEquals(ConflictReason.TURN_DIRECTION_VIOLATED, conflictOf(evaluation))
        assertEquals(emptyList<AdjustmentMode>(), recording.modes)
    }

    @Test
    fun `given a second valid shape farther than T when evaluated then A4 proposes it as ambiguous`() {
        val recording = RecordingComponents()
        val evaluation =
            evaluated(OverdeterminedCases.AMBIGUOUS_SELECTED_OTHER_SHAPE.room, evaluator = recording.evaluator())
        val proposed = evaluation as RoomEvaluation.Proposed
        assertEquals(ProposalReason.AMBIGUOUS_SHAPE, proposed.reason)
        assertNull(proposed.correction)
        assertEquals(emptyList<AdjustmentMode>(), recording.modes)
    }

    @Test
    fun `given a determined trapezoid with a diagonal when evaluated then A5 confirms with r = 0`() {
        val recording = RecordingComponents()
        val evaluation = evaluated(ReferenceRooms.TRAPEZOID_WITH_DIAGONAL.room, evaluator = recording.evaluator())
        val confirmed = evaluation as RoomEvaluation.Confirmed
        assertEquals(MeasurementControl(redundancy = 0, uncontrolled = emptyList()), confirmed.measurementControl)
        assertEquals(
            ReferenceRooms.TRAPEZOID_WITH_DIAGONAL.expectedAreaMm2,
            confirmed.shape.areaMm2,
            SOLVED_AREA_TOLERANCE_MM2,
        )
        assertEquals(emptyList<AdjustmentMode>(), recording.modes)
    }

    @Test
    fun `given A1 without a simple shape when evaluated then B2 starts at the target turns`() {
        val recording = RecordingComponents(solveOverride = { SolverOutcome.NoSimpleShape })
        evaluated(ReferenceRooms.TRAPEZOID_WITH_DIAGONAL.room, evaluator = recording.evaluator())
        assertEquals(listOf(AdjustmentMode.SHAPE_DEVIATION), recording.modes)
        assertTrue(recording.calls.indexOf("solve") < recording.calls.indexOf("adjust"))
    }

    // Shape deviation, branch B.

    @Test
    fun `given B1 without a simple shape when evaluated then B2 starts at the target turns`() {
        val recording = RecordingComponents(solveOverride = { SolverOutcome.NoSimpleShape })
        val evaluation = evaluated(OverdeterminedCases.PROPOSED_WITH_CORRECTION.room, evaluator = recording.evaluator())
        assertEquals(listOf(AdjustmentMode.SHAPE_DEVIATION), recording.modes)
        assertTrue(evaluation.status != ShapeStatus.OPEN)
    }

    @Test
    fun `given all corners measured when evaluated then B1 starts at the observations without solver`() {
        val recording = RecordingComponents()
        evaluated(OverdeterminedCases.RECTANGLE_LARGE_EXPLICIT.room, evaluator = recording.evaluator())
        assertTrue("solve" !in recording.calls)
        assertEquals(listOf(AdjustmentMode.SHAPE_DEVIATION), recording.modes)
    }

    @Test
    fun `given a shape deviation whose adjustment does not converge when evaluated then B2 conflicts`() {
        val recording =
            RecordingComponents(adjustOverride = { mode ->
                AdjustmentOutcome.NotConverged.takeIf {
                    mode ==
                        AdjustmentMode.SHAPE_DEVIATION
                }
            })
        val evaluation = evaluated(OverdeterminedCases.RECTANGLE_LARGE_EXPLICIT.room, evaluator = recording.evaluator())
        assertEquals(ConflictReason.ADJUSTMENT_NOT_CONVERGED, conflictOf(evaluation))
    }

    @Test
    fun `given measured corners that cannot form a simple outline when evaluated then B3 conflicts`() {
        assertConflict(room("4000 3000 4000 3000", "M90000 M90000 M90000 m90000"), ConflictReason.NO_SIMPLE_SHAPE)
    }

    @Test
    fun `given a large inconsistent correction when evaluated then B5 names the worst observation`() {
        val evaluation = evaluated(OverdeterminedCases.RECTANGLE_LARGE_CONFLICT.room) as RoomEvaluation.Conflict
        assertEquals(ConflictReason.MEASUREMENTS_INCONSISTENT, evaluation.reason)
        assertNotNull(evaluation.worstObservation)
        assertNull(evaluation.diagonalId)
    }

    @Test
    fun `given an overdetermined triangle against a declared direction when evaluated then B6 reports it`() {
        val recording = RecordingComponents()
        val evaluation = evaluated(room("3000 4000 5000", "F M90000 R"), evaluator = recording.evaluator())
        assertEquals(ConflictReason.TURN_DIRECTION_VIOLATED, conflictOf(evaluation))
        assertEquals(listOf(AdjustmentMode.SHAPE_DEVIATION), recording.modes)
    }

    @Test
    fun `given an underdetermined adjusted shape when evaluated then B7 proposes it with corrections`() {
        val evaluation = evaluated(OverdeterminedCases.PROPOSED_WITH_CORRECTION.room) as RoomEvaluation.Proposed
        assertEquals(ProposalReason.UNDERDETERMINED, evaluation.reason)
        val correction = assertNotNull(evaluation.correction).let { evaluation.correction!! }
        assertTrue(correction.maxCorrectionMm > GeometryTolerances.SOLVER_RESIDUAL_MM)
        assertNotNull(correction.largestCorrection)
    }

    @Test
    fun `given an ambiguous adjusted shape when evaluated then B7 proposes it with corrections and no overlay`() {
        val evaluation = evaluated(OverdeterminedCases.AMBIGUOUS_NEEDS_ADJUSTMENT.room) as RoomEvaluation.Proposed
        assertEquals(ProposalReason.AMBIGUOUS_SHAPE, evaluation.reason)
        assertNotNull(evaluation.correction)
        assertNotNull(evaluation.ambiguity)
    }

    @Test
    fun `given a consistent additional measurement when evaluated then B8 confirms with controllability`() {
        val evaluation = evaluated(OverdeterminedCases.TRAPEZOID_TWO_DIAGONALS.room) as RoomEvaluation.Confirmed
        assertTrue(evaluation.measurementControl!!.redundancy >= 1)
    }

    @Test
    fun `given a large but plausible correction when evaluated then B9 asks for simple confirmation`() {
        val evaluation = evaluated(OverdeterminedCases.RECTANGLE_LARGE_SIMPLE.room) as RoomEvaluation.NeedsAdjustment
        assertEquals(AdjustmentConfirmation.SIMPLE, evaluation.proposal.requiredConfirmation)
        assertEquals(3, evaluation.measurementControl.redundancy)
    }

    @Test
    fun `given a large and suspicious correction when evaluated then B9 asks for explicit confirmation`() {
        val evaluation = evaluated(OverdeterminedCases.RECTANGLE_LARGE_EXPLICIT.room) as RoomEvaluation.NeedsAdjustment
        assertEquals(AdjustmentConfirmation.EXPLICIT, evaluation.proposal.requiredConfirmation)
        assertNotNull(evaluation.proposal.largestCorrection)
    }

    // Openings.

    @Test
    fun `given openings on the raw walls when evaluated then their fit uses the closure limit`() {
        val base = room("4000 3000 4000 3000", "F L L L")
        val openings = listOf(opening(base, "fits", 3400, 601), opening(base, "beyond", 3400, 602))
        val evaluation = evaluated(base.copy(openings = openings.toImmutableList()))
        assertEquals(
            listOf(OpeningId("fits") to OpeningStatus.VALID, OpeningId("beyond") to OpeningStatus.OUT_OF_BOUNDS),
            evaluation.openingStatuses.toList(),
        )
    }

    @Test
    fun `given an opening when the adjustment shortens its wall then it is out of bounds`() {
        val base = OverdeterminedCases.GAP_30_MM.room
        val evaluation = evaluated(base.copy(openings = persistentListOf(opening(base, "door", 3200, 831))))
        val shortened = evaluation.shape!!.lengthsMm[0]
        assertTrue(shortened + GeometryTolerances.EXACT_CLOSURE_MM < 4031.0, "$shortened")
        assertEquals(OpeningStatus.OUT_OF_BOUNDS, evaluation.openingStatuses[OpeningId("door")])
    }

    @Test
    fun `given a conflicting room when evaluated then openings are checked on the raw walls`() {
        val base = OverdeterminedCases.RECTANGLE_LARGE_CONFLICT.room
        val evaluation = evaluated(base.copy(openings = persistentListOf(opening(base, "door", 3500, 680))))
        assertEquals(OpeningStatus.VALID, evaluation.openingStatuses[OpeningId("door")])
    }

    private fun assertInvalid(
        room: Room,
        field: ValueField,
        reason: InvalidValueReason,
    ) {
        val result = runBlocking { DefaultRoomEvaluator(DirectDispatchers).evaluate(room, DEFAULT_TOLERANCE) }
        assertEquals(DomainError.InvalidValue(field, reason), result.errorOrNull())
    }

    private fun assertConflict(
        room: Room,
        reason: ConflictReason,
    ) {
        assertEquals(reason, conflictOf(evaluated(room)))
    }

    /** [room] with the overlay of its current `NeedsAdjustment` proposal, as `AcceptAdjustment` stores it. */
    private fun accepted(room: Room): Room =
        room.copy(adjustment = (evaluated(room) as RoomEvaluation.NeedsAdjustment).proposal.adjustment)

    /** [room] with a current overlay of [lengths] and [turnsDeg]. */
    private fun overlay(
        room: Room,
        lengths: List<Double>,
        turnsDeg: List<Double>,
    ): Room =
        room.copy(
            adjustment =
                ClosureAdjustment(
                    inputFingerprint = room.inputFingerprint(),
                    solverVersion = 1,
                    adjustedLengthsMm = lengths.toImmutableList(),
                    adjustedTurnsRad = turnsDeg.map(StrictMath::toRadians).toImmutableList(),
                ),
        )

    private fun opening(
        room: Room,
        id: String,
        offsetMm: Long,
        widthMm: Long,
    ): Opening =
        Opening(
            id = OpeningId(id),
            wallId = room.walls[0].id,
            type = OpeningType.DOOR,
            offset = Length(offsetMm),
            width = Length(widthMm),
            height = null,
            sillHeight = null,
            swing = null,
        )

    private companion object {
        const val POINT_TOLERANCE_MM = 1e-9
        const val LENGTH_TOLERANCE_MM = 1e-6
        const val AREA_TOLERANCE_MM2 = 1e-3
        const val SOLVED_AREA_TOLERANCE_MM2 = 1_000.0
        const val CORRECTION_TOLERANCE_MM = 0.01

        /** `atan2(3, −4)`, the turn of the bowtie (0, 0), (4000, 0), (0, 3000), (4000, 3000). */
        val BOWTIE_TURN_RAD: Double = StrictMath.atan2(3.0, -4.0)

        /** Adjusted shape of a crossing quadrilateral. */
        val BOWTIE: AdjustedShape =
            AdjustedShape(
                adjustedLengthsMm = listOf(4000.0, 5000.0, 4000.0, 5000.0),
                adjustedTurnsRad = listOf(BOWTIE_TURN_RAD, BOWTIE_TURN_RAD, -BOWTIE_TURN_RAD, -BOWTIE_TURN_RAD),
                adjustedDiagonalsMm = emptyList(),
                maxCorrectionMm = 0.0,
                worstObservation = null,
                correctionSumMm = 0.0,
                normalizedSquareSum = 0.0,
                largestCorrection = null,
                redundancies = emptyList(),
            )
    }
}
