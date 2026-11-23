package app.plansnap.core.domain

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.command.DockPlacementResolver
import app.plansnap.core.domain.command.HistoryState
import app.plansnap.core.domain.command.ProjectCommand
import app.plansnap.core.domain.command.ProjectCommandFactory
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.command.ReconcileOutcome
import app.plansnap.core.domain.command.RoomDetails
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.domain.evaluation.AdjustmentProposal
import app.plansnap.core.domain.evaluation.CorrectionSummary
import app.plansnap.core.domain.evaluation.EvaluatedShape
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.currentCancellationSignal
import app.plansnap.core.domain.evaluation.isExportable
import app.plansnap.core.domain.evaluation.isUnconfirmed
import app.plansnap.core.domain.report.AreaMetrics
import app.plansnap.core.domain.report.AreaReport
import app.plansnap.core.domain.report.RoomAreaEntry
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorDraft
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.domain.repository.ProjectSummary
import app.plansnap.core.domain.snap.SnapKind
import app.plansnap.core.domain.snap.SnapQuery
import app.plansnap.core.domain.snap.SnapResult
import app.plansnap.core.domain.snap.SnapSegment
import app.plansnap.core.domain.snap.SnapTargets
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.cancellation.CancellationSignal
import app.plansnap.core.geometry.determinacy.Determinacy
import app.plansnap.core.geometry.determinacy.MeasurementControl
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.geometry.traverse.ClosureError
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DomainContractTest {
    private val roomId = RoomId("r1")
    private val noOpenings: ImmutableMap<OpeningId, OpeningStatus> = persistentMapOf()
    private val project =
        Project(ProjectId("p1"), "Flat", Length(2500), persistentListOf(), createdAtEpochMs = 1, updatedAtEpochMs = 2)
    private val shape =
        EvaluatedShape(
            outline = Polygon2(listOf(Vec2.ZERO, Vec2(1000.0, 0.0), Vec2(0.0, 1000.0))),
            winding = Winding.COUNTER_CLOCKWISE,
            lengthsMm = persistentListOf(1000.0, 1414.2, 1000.0),
            turnsRad = persistentListOf(1.5, 2.3, 2.3),
            areaMm2 = 500_000.0,
            perimeterMm = 3414.2,
        )

    private val rawLengths = persistentListOf(1000L, 1414L, 1000L)
    private val solvedShape = shape.copy(lengthsMm = persistentListOf(1000.0, 1414.0, 1000.0))
    private val control = MeasurementControl(redundancy = 2, uncontrolled = emptyList())

    private fun assertRejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun `given domain section 11 when edits are listed then every command exists as a type`() {
        val expected =
            setOf(
                "AddWall",
                "EditWall",
                "RemoveLastWall",
                "SetCorner",
                "AddDiagonal",
                "RemoveDiagonal",
                "CloseRoom",
                "AcceptAdjustment",
                "RevertAdjustment",
                "SetThickness",
                "AddOpening",
                "EditOpening",
                "RemoveOpening",
                "AddRoom",
                "RemoveRoom",
                "MoveRoom",
                "DockRoom",
                "UpdateRoomDetails",
                "SetProjectHeight",
            )
        assertEquals(
            expected,
            ProjectEdit::class.java.permittedSubclasses
                .map { it.simpleName }
                .toSet(),
        )
    }

    @Test
    fun `given move edit when a coordinate is not finite then it is rejected`() {
        assertEquals(1.0, ProjectEdit.MoveRoom(roomId, 1.0, 2.0, 0.5).originX)
        assertRejected { ProjectEdit.MoveRoom(roomId, Double.NaN, 0.0, 0.0) }
        assertRejected { ProjectEdit.MoveRoom(roomId, 0.0, Double.POSITIVE_INFINITY, 0.0) }
        assertRejected { ProjectEdit.MoveRoom(roomId, 0.0, 0.0, Double.NaN) }
    }

    @Test
    fun `given dock edit to itself when created then the rule is left to the command`() {
        val link = DockLink(roomId, WallId("host"), WallId("own"), Length.ZERO, OpeningId("door"))
        assertEquals(OpeningId("door"), ProjectEdit.DockRoom(roomId, link).link.hostOpeningId)
    }

    @Test
    fun `given functional contracts when implemented then they carry the specified signatures`() =
        runBlocking<Unit> {
            val command = ProjectCommand { DomainResult.Success(it) }
            val factory = ProjectCommandFactory { command }
            val resolver = DockPlacementResolver { value, _ -> DomainResult.Success(value) }
            val edit = ProjectEdit.SetProjectHeight(Length(2600))
            assertSame(command, factory.create(edit))
            assertEquals(DomainResult.Success(project), command.apply(project))
            assertEquals(DomainResult.Success(project), resolver.resolve(project, ClosureTolerance()))
            assertEquals(listOf("UNCHANGED", "PLACEMENTS_UPDATED"), ReconcileOutcome.entries.map { it.name })
        }

    @Test
    fun `given a running job when the signal is checked then it passes until the job is cancelled`() =
        runBlocking<Unit> {
            val ready = CompletableDeferred<CancellationSignal>()
            val job =
                launch {
                    ready.complete(currentCancellationSignal())
                    awaitCancellation()
                }
            val signal = ready.await()

            assertDoesNotThrow { signal.checkpoint() }
            job.cancelAndJoin()

            assertThrows(CancellationException::class.java) { signal.checkpoint() }
        }

    @Test
    fun `given history state when depths vary then flags follow and negatives are rejected`() {
        assertFalse(HistoryState(project, 0, 0).canUndo)
        assertFalse(HistoryState(project, 0, 0).canRedo)
        assertTrue(HistoryState(project, 1, 0).canUndo)
        assertTrue(HistoryState(project, 0, 1).canRedo)
        assertRejected { HistoryState(project, -1, 0) }
        assertRejected { HistoryState(project, 0, -1) }
    }

    @Test
    fun `given shape statuses when classified then export table of domain section 4 holds`() {
        assertEquals(
            setOf(ShapeStatus.CONFIRMED, ShapeStatus.NEEDS_ADJUSTMENT, ShapeStatus.ADJUSTED, ShapeStatus.PROPOSED),
            ShapeStatus.entries.filter { it.isExportable }.toSet(),
        )
        assertEquals(
            setOf(ShapeStatus.NEEDS_ADJUSTMENT, ShapeStatus.PROPOSED),
            ShapeStatus.entries.filter { it.isUnconfirmed }.toSet(),
        )
    }

    @Test
    fun `given evaluated shape when values are invalid then it is rejected`() {
        assertRejected { shape.copy(lengthsMm = persistentListOf(1.0)) }
        assertRejected { shape.copy(turnsRad = persistentListOf(1.0)) }
        assertRejected { shape.copy(lengthsMm = persistentListOf(1.0, 0.0, 1.0)) }
        assertRejected { shape.copy(turnsRad = persistentListOf(1.0, Double.NaN, 1.0)) }
        assertRejected { shape.copy(areaMm2 = -1.0) }
        assertRejected { shape.copy(areaMm2 = Double.NaN) }
        assertRejected { shape.copy(perimeterMm = 0.0) }
        assertRejected { shape.copy(perimeterMm = Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `given each evaluation variant when read then status and shape match`() {
        val adjustment = adjustmentOf(3)
        val error = ClosureError(10.0, 0.0, 0.0)
        val determinacy = Determinacy(freeVariables = 3, rank = 2, recommendation = null)
        val evaluations =
            listOf(
                RoomEvaluation.Open(roomId, persistentListOf(Vec2.ZERO), noOpenings),
                RoomEvaluation.Confirmed(roomId, shape, noOpenings),
                RoomEvaluation.NeedsAdjustment(roomId, shape, error, proposalOf(adjustment), noOpenings, control),
                RoomEvaluation.Adjusted(roomId, shape, noOpenings),
                RoomEvaluation.Proposed(roomId, solvedShape, determinacy, rawLengths, null, noOpenings),
                RoomEvaluation.Conflict(roomId, ConflictReason.WALL_TOO_LONG, null, null, noOpenings),
            )
        assertEquals(ShapeStatus.entries.toList(), evaluations.map { it.status })
        evaluations.forEach { assertEquals(it.status.isExportable, it.shape != null) }
    }

    @Test
    fun `given proposal when confirmation is compared then explicit is required only where demanded`() {
        val simple = proposalOf(adjustmentOf(3))
        val explicit = simple.copy(requiredConfirmation = AdjustmentConfirmation.EXPLICIT)
        assertEquals(true, simple.isConfirmedBy(AdjustmentConfirmation.SIMPLE))
        assertEquals(true, simple.isConfirmedBy(AdjustmentConfirmation.EXPLICIT))
        assertEquals(false, explicit.isConfirmedBy(AdjustmentConfirmation.SIMPLE))
        assertEquals(true, explicit.isConfirmedBy(AdjustmentConfirmation.EXPLICIT))
    }

    @Test
    fun `given proposal when correction values are invalid then it is rejected`() {
        val valid = proposalOf(adjustmentOf(3))
        assertRejected { valid.copy(maxCorrectionMm = -1.0) }
        assertRejected { valid.copy(maxCorrectionMm = Double.NaN) }
        assertRejected { valid.copy(correctionSumMm = 0.5) }
        assertRejected { valid.copy(correctionSumMm = Double.POSITIVE_INFINITY) }
    }

    private fun proposalOf(adjustment: ClosureAdjustment): AdjustmentProposal =
        AdjustmentProposal(adjustment, 2.0, 1.0, ObservationRef.WallLength(0), AdjustmentConfirmation.SIMPLE)

    @Test
    fun `given evaluation variants when invariants are violated then they are rejected`() {
        assertRejected { RoomEvaluation.Open(roomId, persistentListOf(), noOpenings) }
        assertRejected {
            RoomEvaluation.NeedsAdjustment(
                roomId,
                shape,
                ClosureError(1.0, 0.0, 0.0),
                proposalOf(adjustmentOf(4)),
                noOpenings,
                control,
            )
        }
        assertRejected {
            RoomEvaluation.Proposed(roomId, solvedShape, Determinacy(2, 2, null), rawLengths, null, noOpenings)
        }
    }

    @Test
    fun `given proposal with adjusted lengths when correction is missing then it is rejected`() {
        val determinacy = Determinacy(freeVariables = 3, rank = 2, recommendation = null)
        val adjustedRaw = persistentListOf(1000L, 1400L, 1000L)
        val summary = CorrectionSummary(14.2, 8.1, 14.2, ObservationRef.WallLength(1))

        assertRejected { RoomEvaluation.Proposed(roomId, shape, determinacy, adjustedRaw, null, noOpenings) }
        assertRejected {
            RoomEvaluation.Proposed(roomId, shape, determinacy, persistentListOf(1000L), summary, noOpenings)
        }
        assertEquals(
            summary,
            RoomEvaluation.Proposed(roomId, shape, determinacy, adjustedRaw, summary, noOpenings).correction,
        )
    }

    @Test
    fun `given correction summary when values are invalid then it is rejected`() {
        val wall = ObservationRef.WallLength(0)
        assertEquals(null, CorrectionSummary(0.0, null, 0.0, null).normalizedSquareSum)
        assertRejected { CorrectionSummary(1.0, 1.0, -1.0, wall) }
        assertRejected { CorrectionSummary(1.0, 1.0, Double.NaN, wall) }
        assertRejected { CorrectionSummary(0.5, 1.0, 1.0, wall) }
        assertRejected { CorrectionSummary(Double.POSITIVE_INFINITY, 1.0, 1.0, wall) }
        assertRejected { CorrectionSummary(1.0, -1.0, 1.0, wall) }
        assertRejected { CorrectionSummary(1.0, Double.NaN, 1.0, wall) }
        assertRejected { CorrectionSummary(1.0, 1.0, 1.0, null) }
    }

    @Test
    fun `given conflict when subject does not match its reason then it is rejected`() {
        val diagonal = DiagonalId("d1")
        val observation = ObservationRef.WallLength(0)
        val valid =
            RoomEvaluation.Conflict(roomId, ConflictReason.DIAGONAL_IMPOSSIBLE, diagonal, null, noOpenings)
        assertEquals(diagonal, valid.diagonalId)
        assertEquals(
            observation,
            RoomEvaluation
                .Conflict(roomId, ConflictReason.MEASUREMENTS_INCONSISTENT, null, observation, noOpenings)
                .worstObservation,
        )
        assertRejected {
            RoomEvaluation.Conflict(roomId, ConflictReason.DIAGONAL_IMPOSSIBLE, null, null, noOpenings)
        }
        assertRejected {
            RoomEvaluation.Conflict(roomId, ConflictReason.WALL_TOO_LONG, diagonal, null, noOpenings)
        }
        assertRejected {
            RoomEvaluation.Conflict(roomId, ConflictReason.NO_SIMPLE_SHAPE, null, observation, noOpenings)
        }
    }

    @Test
    fun `given area values when invalid then metrics and entries reject them`() {
        val metrics = AreaMetrics(1.0, 1.0, 1.0, 1.0)
        assertEquals(0.0, AreaMetrics.ZERO.areaMm2)
        assertRejected { AreaMetrics(-1.0, 0.0, 0.0, 0.0) }
        assertRejected { AreaMetrics(0.0, Double.NaN, 0.0, 0.0) }
        assertRejected { AreaMetrics(0.0, 0.0, Double.POSITIVE_INFINITY, 0.0) }
        assertRejected { AreaMetrics(0.0, 0.0, 0.0, -0.5) }
        assertNull(RoomAreaEntry(roomId, "Bath", null, ShapeStatus.OPEN, null).metrics)
        assertNull(RoomAreaEntry(roomId, "Bath", null, ShapeStatus.CONFLICT, null).metrics)
        assertEquals(metrics, RoomAreaEntry(roomId, "Bath", null, ShapeStatus.PROPOSED, metrics).metrics)
        assertRejected { RoomAreaEntry(roomId, "Bath", null, ShapeStatus.OPEN, metrics) }
        assertRejected { RoomAreaEntry(roomId, "Bath", null, ShapeStatus.CONFIRMED, null) }
    }

    @Test
    fun `given snap query when distance or grid is invalid then it is rejected`() {
        assertEquals(listOf(SnapKind.CORNER, SnapKind.WALL, SnapKind.GRID), SnapKind.entries.toList())
        assertNull(SnapQuery(Vec2.ZERO, 10.0, null, true).gridStepMm)
        assertEquals(50.0, SnapQuery(Vec2.ZERO, 10.0, 50.0, false).gridStepMm)
        assertRejected { SnapQuery(Vec2.ZERO, 0.0, null, true) }
        assertRejected { SnapQuery(Vec2.ZERO, Double.NaN, null, true) }
        assertRejected { SnapQuery(Vec2.ZERO, 1.0, 0.0, true) }
        assertRejected { SnapQuery(Vec2.ZERO, 1.0, Double.POSITIVE_INFINITY, true) }
    }

    @Test
    fun `given settings when created then defaults follow the specification and limits hold`() {
        val defaults = AppSettings()
        assertEquals(GeometryTolerances.DEFAULT_THICKNESS_MM, defaults.lastThickness.mm)
        assertEquals(ClosureTolerance(), defaults.tolerance)
        assertTrue(defaults.dismissedHints.isEmpty())
        assertEquals(5, EditorHint.entries.size)
        assertEquals(0L, AppSettings(lastThickness = Length.ZERO).lastThickness.mm)
        assertRejected { AppSettings(lastThickness = Length(-1)) }
        assertRejected { AppSettings(lastThickness = Length(GeometryTolerances.MAX_THICKNESS_MM + 1)) }
    }

    @Test
    fun `given project summary when room count is negative then it is rejected`() {
        assertEquals(0, ProjectSummary(ProjectId("p"), "P", 0, 1, 2).roomCount)
        assertRejected { ProjectSummary(ProjectId("p"), "P", -1, 1, 2) }
    }

    @Test
    fun `given add wall edit when created then it keeps its corner input`() {
        val corner = CornerSpec.DeclaredRight(TurnDirection.LEFT)
        val wall = Wall(WallId("w"), Length(1000), Length(120))
        assertEquals(corner, ProjectEdit.AddWall(roomId, wall, corner).startCorner)
    }

    @Test
    fun `given one instance of every edit when listed then each command type is constructible`() {
        val edits = everyEdit()
        assertEquals(
            ProjectEdit::class.java.permittedSubclasses.toSet(),
            edits.map { it.javaClass as Class<*> }.toSet(),
        )
        assertEquals(edits.size, edits.toSet().size)
        assertEquals(edits, everyEdit())
    }

    @Test
    fun `given repository and report values when created then they keep their data`() {
        val draft = EditorDraft(ProjectId("p"), roomId, "34,5")
        val entry = RoomAreaEntry(roomId, "Bath", RoomTypeKey("bath"), ShapeStatus.CONFIRMED, AreaMetrics.ZERO)
        val report = AreaReport(persistentListOf(entry), AreaMetrics.ZERO)
        val segment = SnapSegment(Vec2.ZERO, Vec2(1.0, 0.0))
        val targets = SnapTargets(persistentListOf(Vec2.ZERO), persistentListOf(segment))
        assertEquals("34,5", draft.inputText)
        assertEquals(entry, report.entries.single())
        assertEquals(RoomTypeKey("bath"), report.entries.single().typeKey)
        assertEquals(segment, targets.walls.single())
        assertEquals(SnapKind.GRID, SnapResult(Vec2.ZERO, SnapKind.GRID).kind)
    }

    private fun everyEdit(): List<ProjectEdit> {
        val wallId = WallId("w")
        val wall = Wall(wallId, Length(1000), Length(120))
        val corner = CornerSpec.DeclaredRight(TurnDirection.LEFT)
        val diagonal = Diagonal(DiagonalId("d"), 0, 2, Length(1500))
        val opening =
            Opening(OpeningId("o"), wallId, OpeningType.PASSAGE, Length.ZERO, Length(800), null, null, null)
        val link = DockLink(RoomId("host"), WallId("h"), wallId, Length.ZERO)
        val details = RoomDetails("Bath", RoomTypeKey("bath"), "", Length(2400), AreaFactor.FULL)
        return listOf(
            ProjectEdit.AddWall(roomId, wall, corner),
            ProjectEdit.EditWall(roomId, wallId, Length(1100)),
            ProjectEdit.RemoveLastWall(roomId),
            ProjectEdit.SetCorner(roomId, 1, corner),
            ProjectEdit.AddDiagonal(roomId, diagonal),
            ProjectEdit.RemoveDiagonal(roomId, diagonal.id),
            ProjectEdit.CloseRoom(roomId),
            ProjectEdit.AcceptAdjustment(roomId, proposalOf(adjustmentOf(3)), AdjustmentConfirmation.SIMPLE),
            ProjectEdit.RevertAdjustment(roomId),
            ProjectEdit.SetThickness(roomId, wallId, Length(240)),
            ProjectEdit.AddOpening(roomId, opening),
            ProjectEdit.EditOpening(roomId, opening),
            ProjectEdit.RemoveOpening(roomId, opening.id),
            ProjectEdit.AddRoom(Room(roomId)),
            ProjectEdit.RemoveRoom(roomId),
            ProjectEdit.MoveRoom(roomId, 1.0, 2.0, 0.5),
            ProjectEdit.DockRoom(roomId, link),
            ProjectEdit.UpdateRoomDetails(roomId, details),
            ProjectEdit.SetProjectHeight(Length(2600)),
        )
    }

    private fun adjustmentOf(size: Int): ClosureAdjustment =
        ClosureAdjustment(
            inputFingerprint = 1L,
            solverVersion = 1,
            adjustedLengthsMm = List(size) { 1000.0 }.toImmutableList(),
            adjustedTurnsRad = List(size) { 1.0 }.toImmutableList(),
        )
}
