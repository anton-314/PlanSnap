package app.plansnap.core.drawing.cache

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.isExportable
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.testing.fixtures.generators.ProjectGenerator
import app.plansnap.testing.fixtures.generators.RoomGenerator
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * One cache lives through random edit sequences. After every step its result must equal the uncached one, and it must
 * have built exactly the rooms whose input changed.
 */
class CachingSceneBuilderPropertyTest {
    /** The kinds of change between two builds. */
    private enum class Step {
        NOTHING,
        WALL_LENGTH,
        THICKNESS,
        DETAILS,
        MOVE,
        DOCK,
        REMOVE_OPENING,
        ADD_ROOM,
        REMOVE_ROOM,
        REPLACE_ROOM,
        REORDER,
        OPTIONS,
        TOLERANCE,
    }

    private data class State(
        val project: Project,
        val options: SceneOptions,
        val tolerance: ClosureTolerance,
    )

    private data class Key(
        val room: Room,
        val evaluation: RoomEvaluation,
        val options: SceneOptions,
    )

    private class Coverage {
        val steps = HashMap<Step, Int>()
        var dockedFollowers = 0
        var reused = 0

        fun count(step: Step) {
            steps.merge(step, 1, Int::plus)
        }
    }

    @Test
    fun `a cache over any edit sequence equals the uncached builder and builds only changed rooms`() =
        runBlocking<Unit> {
            val coverage = Coverage()
            // Shared by all cases on purpose: the generated ids repeat with other content.
            val counting = CountingSceneBuilder()
            val cache = CachingSceneBuilder(counting)
            val known = HashMap<RoomId, Key>()
            checkAll(ITERATIONS, config(), Arb.long()) { seed ->
                val random = Random(seed)
                val generator = ProjectGenerator(random.nextLong())
                var state = State(generator.project(random.nextInt(2, 6)), SceneOptions(), ClosureTolerance())
                repeat(STEPS) { index ->
                    val step = if (index == 0) Step.NOTHING else Step.entries.random(random)
                    val before = state
                    state = change(state, step, random)
                    coverage.count(step)
                    val evaluations = CacheDomain.evaluations(state.project, state.tolerance)
                    val expected = DefaultSceneBuilder().build(state.project, evaluations, state.options)

                    val actual = cache.build(state.project, evaluations, state.options)

                    val context = "seed=$seed step=$index $step"
                    assertEquals(expected, actual, context)
                    val rebuilt = expectedBuilds(state, evaluations, known)
                    assertEquals(rebuilt, counting.drain(), context)
                    coverage.reused += actual.rooms.count { it.roomId !in rebuilt }
                    coverage.dockedFollowers += dockedFollowers(before.project, state.project)
                }
            }
            Step.entries.forEach { assertTrue((coverage.steps[it] ?: 0) > ITERATIONS / 4, "$it: ${coverage.steps}") }
            assertTrue(coverage.reused > ITERATIONS, "reused only ${coverage.reused}")
            assertTrue(coverage.dockedFollowers > ITERATIONS / 10, "followers ${coverage.dockedFollowers}")
        }

    /** The rooms a cache must build, and the update of the model [known] of its content. */
    private fun expectedBuilds(
        state: State,
        evaluations: Map<RoomId, RoomEvaluation>,
        known: HashMap<RoomId, Key>,
    ): List<RoomId> {
        known.keys.retainAll(
            state.project.rooms
                .map { it.id }
                .toSet(),
        )
        return state.project.rooms.mapNotNull { room ->
            val evaluation = evaluations.getValue(room.id)
            if (state.options.exportableOnly && !evaluation.status.isExportable) return@mapNotNull null
            val key = Key(room, evaluation, state.options)
            if (known.put(room.id, key) == key) null else room.id
        }
    }

    /** Docked rooms whose placement changed although the step did not move or link them directly. */
    private fun dockedFollowers(
        before: Project,
        after: Project,
    ): Int {
        val old = before.rooms.associateBy { it.id }
        return after.rooms.count { room ->
            val previous = old[room.id]
            previous?.placement?.dock != null &&
                previous.placement.dock == room.placement.dock &&
                previous.placement != room.placement
        }
    }

    private suspend fun change(
        state: State,
        step: Step,
        random: Random,
    ): State {
        val project = state.project
        val room = project.rooms.randomOrNull(random)
        return when (step) {
            Step.NOTHING -> {
                state
            }

            Step.OPTIONS -> {
                state.copy(options = SceneOptions(random.nextBoolean(), random.nextBoolean()))
            }

            Step.TOLERANCE -> {
                state.copy(tolerance = ClosureTolerance(random.nextLong(5, 200), 0.0))
            }

            Step.ADD_ROOM -> {
                state.copy(project = CacheDomain.apply(project, ProjectEdit.AddRoom(newRoom(random, project))))
            }

            Step.REORDER -> {
                state.copy(project = project.copy(rooms = project.rooms.shuffled(random).toImmutableList()))
            }

            else -> {
                if (room == null) state else state.copy(project = changeRoom(project, room, step, random))
            }
        }
    }

    private suspend fun changeRoom(
        project: Project,
        room: Room,
        step: Step,
        random: Random,
    ): Project =
        when (step) {
            Step.WALL_LENGTH -> {
                val index = random.nextInt(room.walls.size)
                val delta = random.nextLong(1, 120)
                val walls =
                    room.walls.mapIndexed { i, wall ->
                        if (i == index) wall.copy(length = Length(wall.length.mm + delta)) else wall
                    }
                replace(project, room.copy(walls = walls.toImmutableList()))
            }

            Step.THICKNESS -> {
                val thickness = Length(random.nextLong(0, 400))
                replace(project, room.copy(walls = room.walls.map { it.copy(thickness = thickness) }.toImmutableList()))
            }

            Step.DETAILS -> {
                replace(project, room.copy(name = "N${random.nextInt(3)}", notes = "Note ${random.nextInt(3)}"))
            }

            Step.MOVE -> {
                val x = random.nextInt(-5000, 5000).toDouble()
                CacheDomain.apply(project, ProjectEdit.MoveRoom(room.id, x, -x, random.nextDouble()))
            }

            Step.DOCK -> {
                dock(project, room, random)
            }

            Step.REMOVE_OPENING -> {
                room.openings.randomOrNull(random)?.let {
                    CacheDomain.apply(project, ProjectEdit.RemoveOpening(room.id, it.id))
                } ?: project
            }

            Step.REMOVE_ROOM -> {
                CacheDomain.apply(project, ProjectEdit.RemoveRoom(room.id))
            }

            Step.REPLACE_ROOM -> {
                // A different room under the same id, as after undo and a new room or in another project.
                val other = newRoom(random, project).copy(id = room.id)
                val removed = CacheDomain.apply(project, ProjectEdit.RemoveRoom(room.id))
                CacheDomain.apply(removed, ProjectEdit.AddRoom(other))
            }

            else -> {
                error("$step does not change a room")
            }
        }

    private suspend fun dock(
        project: Project,
        room: Room,
        random: Random,
    ): Project {
        val host = project.rooms.filter { it.id != room.id }.randomOrNull(random) ?: return project
        val hostWall = host.walls.random(random)
        val opening = host.openings.firstOrNull { it.wallId == hostWall.id }?.takeIf { random.nextBoolean() }
        val link =
            DockLink(
                host.id,
                hostWall.id,
                room.walls.random(random).id,
                Length(random.nextLong(-500, 500)),
                opening?.id,
            )
        return CacheDomain.apply(project, ProjectEdit.DockRoom(room.id, link))
    }

    private suspend fun replace(
        project: Project,
        room: Room,
    ): Project =
        CacheDomain.resolve(
            project.copy(rooms = project.rooms.map { if (it.id == room.id) room else it }.toImmutableList()),
        )

    private fun newRoom(
        random: Random,
        project: Project,
    ): Room {
        var number = random.nextInt(ID_RANGE)
        while (project.rooms.any { it.id.value == "added$number-0" }) number++
        return RoomGenerator(random.nextLong(), SequentialIdGenerator("added$number-0")).any().room.let {
            it.copy(id = RoomId("added$number-0"))
        }
    }

    private companion object {
        const val ITERATIONS = 1_000
        const val STEPS = 12
        const val ID_RANGE = 1_000
        const val SEED = 9_301L

        @OptIn(ExperimentalKotest::class)
        fun config() = PropTestConfig(seed = SEED)
    }
}
