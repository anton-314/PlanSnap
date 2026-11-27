package app.plansnap.core.drawing.cache

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.SceneFixtures
import app.plansnap.core.drawing.scene.SceneFixtures.evaluate
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.testing.fixtures.rooms.roomFromVertices
import app.plansnap.testing.fixtures.rooms.vertex
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class CachingSceneBuilderTest {
    private val counting = CountingSceneBuilder()
    private val cache = CachingSceneBuilder(counting)
    private val uncached = DefaultSceneBuilder()
    private val options = SceneOptions()

    private val first = rectangle("first")
    private val second = rectangle("second")
    private val third = rectangle("third")
    private val project = project(first, second, third)

    @Test
    fun `given a built project when built again then no room is built and the scene is equal`() {
        val before = build(project)
        counting.drain()

        val after = build(project)

        assertEquals(emptyList<RoomId>(), counting.drain())
        assertEquals(before, after)
        before.rooms.zip(after.rooms).forEach { (a, b) -> assertSame(a, b) }
    }

    @Test
    fun `given equal but new rooms and evaluations when built then every scene is reused`() {
        build(project)
        counting.drain()

        build(project(rectangle("first"), rectangle("second"), rectangle("third")))

        assertEquals(emptyList<RoomId>(), counting.drain())
    }

    @Test
    fun `given one changed wall when built then only the scene of that room is built`() {
        val before = build(project)
        counting.drain()
        val longer =
            second.copy(
                walls =
                    second.walls
                        .mapIndexed { i, wall -> if (i % 2 == 0) wall.copy(length = Length(4500)) else wall }
                        .toImmutableList(),
            )
        val changed = project(first, longer, third)

        val after = build(changed)

        assertEquals(listOf(second.id), counting.drain())
        assertSame(before.rooms[0], after.rooms[0])
        assertNotEquals(before.rooms[1], after.rooms[1])
        assertSame(before.rooms[2], after.rooms[2])
        assertEquals(fresh(changed), after)
    }

    @Test
    fun `given a renamed room when built then only that room is built`() {
        build(project)
        counting.drain()
        val changed = project(first.copy(name = "Kitchen"), second, third)

        val after = build(changed)

        assertEquals(listOf(first.id), counting.drain())
        assertEquals(fresh(changed), after)
    }

    @Test
    fun `given a new evaluation of an unchanged room when built then the room is built again`() =
        runBlocking<Unit> {
            val gap = project(SceneFixtures.needsAdjustment)
            val strict = CacheDomain.evaluations(gap, ClosureTolerance(absoluteMm = 5, relative = 0.0))
            val lenient = CacheDomain.evaluations(gap)
            assertNotEquals(strict, lenient)
            cache.build(gap, lenient, options)
            counting.drain()

            val after = cache.build(gap, strict, options)

            assertEquals(listOf(SceneFixtures.needsAdjustment.id), counting.drain())
            assertEquals(uncached.build(gap, strict, options), after)
        }

    @Test
    fun `given changed options when built then every room is built with the new options`() {
        build(project)
        counting.drain()
        val notes = SceneOptions(includeNotes = true)

        val after = cache.build(project, LargeProject.evaluations(project.rooms), notes)

        assertEquals(project.rooms.map { it.id }, counting.drain())
        assertEquals(uncached.build(project, LargeProject.evaluations(project.rooms), notes), after)
    }

    @Test
    fun `given reordered rooms when built then no room is built and the order follows the project`() {
        build(project)
        counting.drain()
        val reordered = project(third, first, second)

        val after = build(reordered)

        assertEquals(emptyList<RoomId>(), counting.drain())
        assertEquals(fresh(reordered), after)
    }

    @Test
    fun `given a removed room when it returns then it is built again`() {
        build(project)
        val without = build(project(first, third))
        counting.drain()
        assertEquals(fresh(project(first, third)), without)

        build(project)

        assertEquals(listOf(second.id), counting.drain())
    }

    @Test
    fun `given retained rooms when the others are requested then only they are built again`() {
        val rooms = project.rooms
        rooms.forEach { cache.buildRoom(it, evaluate(it), options) }
        counting.drain()

        cache.retainRooms(setOf(first.id, third.id))
        rooms.forEach { cache.buildRoom(it, evaluate(it), options) }

        assertEquals(listOf(second.id), counting.drain())
    }

    @Test
    fun `given an empty set of retained rooms when rooms are requested then all are built again`() {
        build(project)
        counting.drain()

        cache.retainRooms(emptySet())
        build(project)

        assertEquals(project.rooms.map { it.id }, counting.drain())
    }

    @Test
    fun `given exportable only when built then open and conflict rooms are left out`() {
        val mixed = project(first, SceneFixtures.open, SceneFixtures.conflict, SceneFixtures.proposed)
        val evaluations = LargeProject.evaluations(mixed.rooms)
        val export = SceneOptions(exportableOnly = true)

        val scene = cache.build(mixed, evaluations, export)

        assertEquals(uncached.build(mixed, evaluations, export), scene)
        assertEquals(listOf(first.id, SceneFixtures.proposed.id), counting.drain())
    }

    @Test
    fun `given a room without evaluation when built then it fails like the uncached builder`() {
        build(project)
        counting.drain()

        val failure =
            assertThrows(IllegalArgumentException::class.java) { cache.build(project, persistentMapOf(), options) }

        val expected =
            assertThrows(IllegalArgumentException::class.java) { uncached.build(project, persistentMapOf(), options) }
        assertEquals(expected.message, failure.message)
        assertEquals(emptyList<RoomId>(), counting.drain())
    }

    @Test
    fun `given a foreign evaluation when a cached room is requested then it fails and the scene is kept`() {
        val scene = cache.buildRoom(first, evaluate(first), options)

        assertThrows(IllegalArgumentException::class.java) { cache.buildRoom(first, evaluate(second), options) }
        counting.drain()

        assertSame(scene, cache.buildRoom(first, evaluate(first), options))
        assertEquals(emptyList<RoomId>(), counting.drain())
    }

    @Test
    fun `given a changed host when docked rooms are resolved then host and docked room are built`() =
        runBlocking<Unit> {
            val link = DockLink(first.id, first.walls[1].id, second.walls[3].id, Length(0))
            val docked = CacheDomain.apply(project, ProjectEdit.DockRoom(second.id, link))
            assertNotEquals(second.placement, docked.rooms[1].placement)
            build(docked)
            counting.drain()
            val moved = CacheDomain.apply(docked, ProjectEdit.MoveRoom(first.id, 750.0, -250.0, 0.5))

            val after = build(moved)

            assertEquals(listOf(first.id, second.id), counting.drain())
            assertEquals(fresh(moved), after)
        }

    @Test
    fun `given concurrent callers when they build different states then each gets the scene of its own input`() {
        val states = List(STATES) { first.copy(name = "State $it") }
        val evaluations = states.map(::evaluate)
        val expected = states.indices.map { uncached.buildRoom(states[it], evaluations[it], options) }
        val shared = CachingSceneBuilder()
        val executor = Executors.newFixedThreadPool(THREADS)
        try {
            val tasks =
                List(CALLS) { call ->
                    Callable {
                        val i = call % STATES
                        if (call % RETAIN_EVERY == 0) shared.retainRooms(emptySet())
                        expected[i] == shared.buildRoom(states[i], evaluations[i], options)
                    }
                }

            val results = executor.invokeAll(tasks).map { it.get() }

            assertEquals(List(CALLS) { true }, results)
        } finally {
            executor.shutdown()
        }
    }

    private fun build(project: Project) = cache.build(project, LargeProject.evaluations(project.rooms), options)

    private fun fresh(project: Project) = uncached.build(project, LargeProject.evaluations(project.rooms), options)

    private fun project(vararg rooms: Room) =
        Project(ProjectId("p"), "P", Length(2500), persistentListOf(*rooms), 0L, 0L)

    private fun rectangle(id: String) =
        roomFromVertices(id, listOf(vertex(0, 0), vertex(4000, 0), vertex(4000, 3000), vertex(0, 3000)))

    private companion object {
        const val THREADS = 4
        const val STATES = 5
        const val CALLS = 2_000
        const val RETAIN_EVERY = 97
    }
}
