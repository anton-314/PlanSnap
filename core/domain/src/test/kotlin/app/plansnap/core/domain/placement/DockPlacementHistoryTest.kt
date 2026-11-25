@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.plansnap.core.domain.placement

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.domain.command.DefaultCommandHistory
import app.plansnap.core.domain.command.DefaultProjectCommandFactory
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.command.ReconcileOutcome
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.testing.fixtures.runtime.TestDispatcherProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The resolver inside the merged [DefaultCommandHistory] (ADR 0015): commands, reconcile and the 30-room limit of
 * REQ-MOD-006 and ADR 0015.
 */
class DockPlacementHistoryTest {
    private val factory = DefaultProjectCommandFactory(AcceptingOpeningRules())
    private val rect = rectangle(4000, 3000)

    private fun history(
        rooms: List<Room>,
        evaluator: FakeShapeEvaluator,
    ) = DefaultCommandHistory(
        projectOf(rooms),
        evaluator,
        DefaultDockPlacementResolver(evaluator),
        TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    @Test
    fun `given dock command when executed then the placement is resolved in the same undo step`() =
        runBlocking<Unit> {
            val outlines = mapOf(RoomId("h") to rect, RoomId("g") to rect)
            val history = history(listOf(rawRoom("h", 4), rawRoom("g", 4)), FakeShapeEvaluator(outlines))

            val docked =
                history
                    .execute(
                        factory.create(ProjectEdit.DockRoom(RoomId("g"), link("h", 1, "g", 3, 0))),
                        TOLERANCE,
                    ).value()

            assertDocked(docked, outlines, "g", 0.0)
            assertEquals(1, history.state.undoDepth)
            assertEquals(ReconcileOutcome.UNCHANGED, history.reconcile(TOLERANCE).value())
            assertEquals(1, history.state.undoDepth)
        }

    @Test
    fun `given docked room when the host moves then it follows and moving it freely releases the link`() =
        runBlocking<Unit> {
            val outlines = mapOf(RoomId("h") to rect, RoomId("g") to rect)
            val rooms = listOf(rawRoom("h", 4), rawRoom("g", 4).dockedBy(link("h", 1, "g", 3, 0)))
            val history = history(rooms, FakeShapeEvaluator(outlines))

            val moved =
                history
                    .execute(
                        factory.create(ProjectEdit.MoveRoom(RoomId("h"), 100.0, 200.0, 0.0)),
                        TOLERANCE,
                    ).value()
            assertEquals(4220.0, moved.room("g").placement.originX, EPS_MM)
            assertEquals(200.0, moved.room("g").placement.originY, EPS_MM)

            val freed =
                history
                    .execute(
                        factory.create(ProjectEdit.MoveRoom(RoomId("g"), 9000.0, 0.0, 1.0)),
                        TOLERANCE,
                    ).value()
            assertNull(freed.room("g").placement.dock)
            assertEquals(9000.0, freed.room("g").placement.originX, EPS_MM)
            assertEquals(1.0, freed.room("g").placement.rotationRad, EPS_UNIT)
        }

    @Test
    fun `given cyclic dock command when executed then it is rejected and nothing changes`() =
        runBlocking<Unit> {
            val outlines = mapOf(RoomId("a") to rect, RoomId("b") to rect)
            val rooms = listOf(rawRoom("a", 4), rawRoom("b", 4).dockedBy(link("a", 1, "b", 3, 0)))
            val history = history(rooms, FakeShapeEvaluator(outlines))
            val before = history.state

            val result =
                history.execute(
                    factory.create(ProjectEdit.DockRoom(RoomId("a"), link("b", 1, "a", 3, 0))),
                    TOLERANCE,
                )

            assertEquals(DomainResult.Failure(DomainError.RuleViolation(DomainRule.DOCK_CYCLE)), result)
            assertEquals(before, history.state)
        }

    @Test
    fun `given room docked to a door when the door is removed then the world position is kept`() =
        runBlocking<Unit> {
            val outlines = mapOf(RoomId("h") to rect, RoomId("g") to rect)
            val host = rawRoom("h", 4, openings = listOf(door("d", wallId("h", 0), 1200)))
            val guest = rawRoom("g", 4).dockedBy(link("h", 0, "g", 2, 300).viaOpening("d"))
            val history = history(listOf(host, guest), FakeShapeEvaluator(outlines))
            val resolved = history.reconcile(TOLERANCE).value()
            assertEquals(ReconcileOutcome.PLACEMENTS_UPDATED, resolved)
            val before =
                history.state.project
                    .room("g")
                    .placement

            val after =
                history
                    .execute(
                        factory.create(ProjectEdit.RemoveOpening(RoomId("h"), OpeningId("d"))),
                        TOLERANCE,
                    ).value()

            val placement = after.room("g").placement
            assertNull(placement.dock?.hostOpeningId)
            assertEquals(1500L, placement.dock?.hostOffset?.mm)
            assertEquals(before.originX, placement.originX, EPS_MM)
            assertEquals(before.originY, placement.originY, EPS_MM)
            assertEquals(before.rotationRad, placement.rotationRad, EPS_UNIT)
        }

    @Test
    fun `given a chain of 30 rooms when the root moves then all rooms are resolved quickly and exactly`() =
        runBlocking<Unit> {
            val ids = List(ROOMS) { "r$it" }
            val outlines = ids.associate { RoomId(it) to rect }
            val rooms =
                ids.mapIndexed { i, id ->
                    val room = rawRoom(id, 4)
                    if (i == 0) room else room.dockedBy(link(ids[i - 1], 1, id, 3, 0))
                }
            val history = history(rooms, FakeShapeEvaluator(outlines))
            history.reconcile(TOLERANCE).value()

            val start = System.nanoTime()
            repeat(RUNS) { run ->
                history
                    .execute(
                        factory.create(ProjectEdit.MoveRoom(RoomId("r0"), run.toDouble(), 0.0, 0.0)),
                        TOLERANCE,
                    ).value()
            }
            val averageMs = (System.nanoTime() - start) / RUNS / NANOS_PER_MS

            val project = history.state.project
            val shift = (RUNS - 1).toDouble()
            ids.forEachIndexed { i, id ->
                assertEquals(shift + i * 4120.0, project.room(id).placement.originX, EPS_MM)
                if (i > 0) assertDocked(project, outlines, id, 0.0)
            }
            println("T-DOM-004: 30-room chain, execute with resolve averaged $averageMs ms over $RUNS runs")
            assertTrue(averageMs < MAX_AVERAGE_MS, "average $averageMs ms")
        }

    private companion object {
        const val ROOMS = 30
        const val RUNS = 200
        const val NANOS_PER_MS = 1_000_000.0
        const val MAX_AVERAGE_MS = 50.0
    }
}
