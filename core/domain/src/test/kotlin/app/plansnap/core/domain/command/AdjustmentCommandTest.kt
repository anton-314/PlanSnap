package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.AdjustmentProposal
import app.plansnap.core.domain.evaluation.EvaluatedShape
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.geometry.traverse.ClosureError
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.core.model.inputFingerprint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Tests of `AcceptAdjustment` and `RevertAdjustment` (ADR 0019, DOMAIN sections 4, 5.5 and 11), including the numeric
 * case rectangle 4150 / 3000 / 4000 / 3000 with all corners measured.
 *
 * The evaluator is a fake that reproduces the scenario of the ADR: the closure error is `e = 150 mm` and the
 * perimeter is `U = 14 150 mm`. It returns a measurement error with a simple confirmation if `e <= T` and the
 * branch-B proposal with an explicit confirmation otherwise. The orchestrator re-checks the case with the real
 * evaluator.
 */
class AdjustmentCommandTest {
    private val factory = DefaultProjectCommandFactory(FakeOpeningRules())
    private val strict = ClosureTolerance()
    private val loose = ClosureTolerance(absoluteMm = 250L)
    private val id = RoomId("r")
    private val room =
        Room(
            id = id,
            walls =
                listOf(4150L, 3000L, 4000L, 3000L)
                    .mapIndexed { index, mm -> Wall(WallId("w$index"), Length(mm), Length(120)) }
                    .toImmutableList(),
            corners =
                persistentListOf(
                    app.plansnap.core.model.CornerSpec.Unspecified,
                    measuredRight(),
                    measuredRight(),
                    measuredRight(),
                ),
            isClosed = true,
        )
    private val project = projectOf(room)
    private val scenario = AdrScenarioEvaluator()

    private val simpleProposal = scenario.proposal(room, AdjustmentConfirmation.SIMPLE)
    private val explicitProposal = scenario.proposal(room, AdjustmentConfirmation.EXPLICIT)

    private fun accept(
        proposal: AdjustmentProposal,
        confirmation: AdjustmentConfirmation,
    ) = factory.create(ProjectEdit.AcceptAdjustment(id, proposal, confirmation))

    @Test
    fun `given the adr scenario when the evaluator runs then the required confirmation depends on the tolerance`() =
        runTest {
            assertEquals(AdjustmentConfirmation.SIMPLE, scenario.confirmationFor(room, loose))
            assertEquals(AdjustmentConfirmation.EXPLICIT, scenario.confirmationFor(room, strict))
            assertEquals(simpleProposal.adjustment, explicitProposal.adjustment)
        }

    @Test
    fun `given a loose-tolerance proposal when accepted under the default tolerance then it is rejected`() =
        runTest {
            val history = historyOf(project, scenario)

            val result = history.execute(accept(simpleProposal, AdjustmentConfirmation.SIMPLE), strict)

            assertEquals(violation(DomainRule.ADJUSTMENT_NOT_AVAILABLE), errorOf(result))
            assertEquals(project, history.state.project)
            assertEquals(0, history.state.undoDepth)
            assertEquals(listOf(strict), scenario.tolerances)
        }

    @Test
    fun `given an explicit proposal when accepted under the loose tolerance then it is rejected`() =
        runTest {
            val history = historyOf(project, scenario)

            val result = history.execute(accept(explicitProposal, AdjustmentConfirmation.EXPLICIT), loose)

            assertEquals(violation(DomainRule.ADJUSTMENT_NOT_AVAILABLE), errorOf(result))
            assertEquals(project, history.state.project)
        }

    @Test
    fun `given the simple proposal when accepted under the loose tolerance then it becomes the overlay`() =
        runTest {
            val history = historyOf(project, scenario)

            val result = history.execute(accept(simpleProposal, AdjustmentConfirmation.SIMPLE), loose)

            assertEquals(simpleProposal.adjustment, valueOf(result).room("r").adjustment)
            assertEquals(room.walls, valueOf(result).room("r").walls)
        }

    @Test
    fun `given an explicit proposal when only a simple confirmation is given then explicit confirmation is required`() =
        runTest {
            val history = historyOf(project, scenario)

            val result = history.execute(accept(explicitProposal, AdjustmentConfirmation.SIMPLE), strict)

            assertEquals(violation(DomainRule.EXPLICIT_CONFIRMATION_REQUIRED), errorOf(result))
            assertEquals(project, history.state.project)
            assertEquals(0, history.state.undoDepth)
        }

    @Test
    fun `given an explicit confirmation when accepted then undo and redo restore the identical projects`() =
        runTest {
            val history = historyOf(project, scenario)

            val accepted = valueOf(history.execute(accept(explicitProposal, AdjustmentConfirmation.EXPLICIT), strict))
            val undone = history.undo()
            val redone = history.redo()

            assertEquals(explicitProposal.adjustment, accepted.room("r").adjustment)
            assertSame(project, undone)
            assertSame(accepted, redone)
            assertEquals(listOf(strict), scenario.tolerances)
        }

    @Test
    fun `given a proposal that differs in any value when accepted then it is rejected`() =
        runTest {
            val history = historyOf(project, scenario)
            val other = explicitProposal.copy(correctionSumMm = explicitProposal.correctionSumMm + 1.0)

            val result = history.execute(accept(other, AdjustmentConfirmation.EXPLICIT), strict)

            assertEquals(violation(DomainRule.ADJUSTMENT_NOT_AVAILABLE), errorOf(result))
        }

    @Test
    fun `given an evaluation that is not a needs-adjustment result when accepted then it is rejected`() =
        runTest {
            val shape = scenario.shape(room)
            val results =
                listOf(
                    RoomEvaluation.Open(id, persistentListOf(Vec2.ZERO), persistentMapOf()),
                    RoomEvaluation.Confirmed(id, shape, persistentMapOf()),
                    RoomEvaluation.Adjusted(id, shape, persistentMapOf()),
                    RoomEvaluation.Conflict(id, ConflictReason.WALL_TOO_LONG, null, null, persistentMapOf()),
                )
            results.forEach { evaluation ->
                val history = historyOf(project, RoomEvaluator { _, _ -> DomainResult.Success(evaluation) })

                val result = history.execute(accept(explicitProposal, AdjustmentConfirmation.EXPLICIT), strict)

                assertEquals(violation(DomainRule.ADJUSTMENT_NOT_AVAILABLE), errorOf(result), "$evaluation")
                assertEquals(project, history.state.project)
            }
        }

    @Test
    fun `given a failed evaluation when accepted then the failure is returned unchanged`() =
        runTest {
            val failure = DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_LARGE)
            val history = historyOf(project, RoomEvaluator { _, _ -> DomainResult.Failure(failure) })

            val result = history.execute(accept(explicitProposal, AdjustmentConfirmation.EXPLICIT), strict)

            assertEquals(failure, errorOf(result))
        }

    @Test
    fun `given a missing room when accepted then it is not found and nothing is evaluated`() =
        runTest {
            val history = historyOf(projectOf(), scenario)

            val result = history.execute(accept(explicitProposal, AdjustmentConfirmation.EXPLICIT), strict)

            assertEquals(missing(EntityKind.ROOM, "r"), errorOf(result))
            assertEquals(emptyList<ClosureTolerance>(), scenario.tolerances)
        }

    @Test
    fun `given a stale fingerprint when applied then the adjustment is not available`() {
        val stale = explicitProposal.copy(adjustment = explicitProposal.adjustment.copy(inputFingerprint = 1L))
        val shorter =
            explicitProposal.adjustment.copy(
                adjustedLengthsMm = persistentListOf(1.0),
                adjustedTurnsRad = persistentListOf(0.0),
            )

        assertEquals(
            violation(DomainRule.ADJUSTMENT_NOT_AVAILABLE),
            errorOf(accept(stale, AdjustmentConfirmation.EXPLICIT).apply(project)),
        )
        assertEquals(
            violation(DomainRule.ADJUSTMENT_NOT_AVAILABLE),
            errorOf(
                accept(explicitProposal.copy(adjustment = shorter), AdjustmentConfirmation.EXPLICIT).apply(project),
            ),
        )
        val open = projectOf(room.copy(isClosed = false))
        assertEquals(
            violation(DomainRule.ADJUSTMENT_NOT_AVAILABLE),
            errorOf(accept(explicitProposal, AdjustmentConfirmation.EXPLICIT).apply(open)),
        )
        assertEquals(
            missing(EntityKind.ROOM, "r"),
            errorOf(accept(explicitProposal, AdjustmentConfirmation.EXPLICIT).apply(projectOf())),
        )
    }

    @Test
    fun `given an overlay when reverted then only the overlay is removed and raw values stay identical`() {
        val adjusted = projectOf(room.copy(adjustment = explicitProposal.adjustment))
        val revert = factory.create(ProjectEdit.RevertAdjustment(id))

        val result = valueOf(revert.apply(adjusted))

        assertNull(result.room("r").adjustment)
        assertEquals(room, result.room("r"))
        assertEquals(room.inputFingerprint(), result.room("r").inputFingerprint())
        assertEquals(
            missing(EntityKind.ROOM, "x"),
            errorOf(factory.create(ProjectEdit.RevertAdjustment(RoomId("x"))).apply(adjusted)),
        )
    }

    /** Fake of the evaluator that reproduces the numeric case of ADR 0019 for the room of this test. */
    private class AdrScenarioEvaluator : RoomEvaluator {
        val tolerances = mutableListOf<ClosureTolerance>()

        fun confirmationFor(
            room: Room,
            tolerance: ClosureTolerance,
        ): AdjustmentConfirmation {
            val limit = tolerance.limitMm(room.walls.sumOf { it.length.mm }.toDouble())
            return if (CLOSURE_ERROR_MM <= limit) AdjustmentConfirmation.SIMPLE else AdjustmentConfirmation.EXPLICIT
        }

        fun proposal(
            room: Room,
            confirmation: AdjustmentConfirmation,
        ) = AdjustmentProposal(
            adjustment =
                ClosureAdjustment(
                    inputFingerprint = room.inputFingerprint(),
                    solverVersion = 1,
                    adjustedLengthsMm = persistentListOf(4148.656, 3000.033, 4001.344, 3000.033),
                    adjustedTurnsRad = persistentListOf(1.59535, 1.59535, 1.54624, 1.54624),
                ),
            correctionSumMm = 223.94,
            maxCorrectionMm = 55.297,
            largestCorrection = ObservationRef.CornerAngle(0),
            requiredConfirmation = confirmation,
        )

        fun shape(room: Room): EvaluatedShape {
            val lengths = proposal(room, AdjustmentConfirmation.SIMPLE).adjustment.adjustedLengthsMm
            return EvaluatedShape(
                outline =
                    Polygon2(
                        listOf(Vec2(0.0, 0.0), Vec2(4148.656, 0.0), Vec2(4148.656, 3000.0), Vec2(0.0, 3000.0)),
                    ),
                winding = Winding.COUNTER_CLOCKWISE,
                lengthsMm = lengths,
                turnsRad = persistentListOf(1.59535, 1.59535, 1.54624, 1.54624),
                areaMm2 = 12_445_968.0,
                perimeterMm = lengths.sum(),
            )
        }

        override suspend fun evaluate(
            room: Room,
            tolerance: ClosureTolerance,
        ): DomainResult<RoomEvaluation> {
            tolerances += tolerance
            return DomainResult.Success(
                RoomEvaluation.NeedsAdjustment(
                    roomId = room.id,
                    shape = shape(room),
                    closureError = ClosureError(CLOSURE_ERROR_MM, 0.0, 0.0),
                    proposal = proposal(room, confirmationFor(room, tolerance)),
                    openingStatuses = persistentMapOf(),
                    measurementControl = MeasurementControl(redundancy = 1, uncontrolled = emptyList()),
                ),
            )
        }

        private companion object {
            const val CLOSURE_ERROR_MM = 150.0
        }
    }
}
