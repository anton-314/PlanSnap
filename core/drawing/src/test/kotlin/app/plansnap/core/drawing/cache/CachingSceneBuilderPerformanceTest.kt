package app.plansnap.core.drawing.cache

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory

/**
 * Measures the scene of the load of REQ-NFR-001, 200 walls in 30 rooms. Times are only printed, because they depend on
 * the machine. Assertions use the allocated bytes of the thread and the number of rooms that are built.
 */
class CachingSceneBuilderPerformanceTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val options = SceneOptions()

    private data class Sample(
        val medianMicros: Long,
        val p95Micros: Long,
        val bytes: Long,
    )

    private data class Input(
        val project: Project,
        val evaluations: ImmutableMap<RoomId, RoomEvaluation>,
    )

    @Test
    fun `given 200 walls in 30 rooms when cached then a build allocates a fraction of an uncached build`() {
        val base = Input(LargeProject.project(), LargeProject.evaluations(LargeProject.project().rooms))
        assertEquals(LargeProject.ROOMS, base.project.rooms.size)
        assertEquals(LargeProject.WALLS, base.project.rooms.sumOf { it.walls.size })
        // Equal content in new instances, as after loading or evaluating again: the cache compares by value.
        val copy = Input(LargeProject.project(), LargeProject.evaluations(LargeProject.project().rooms))
        // The same project with one longer wall pair in the first room.
        val rooms = base.project.rooms
        val longer =
            rooms[0].copy(
                walls =
                    rooms[0]
                        .walls
                        .map { it.copy(thickness = Length(it.thickness.mm + 10)) }
                        .toImmutableList(),
            )
        val changedProject = base.project.copy(rooms = (listOf(longer) + rooms.drop(1)).toImmutableList())
        val changed = Input(changedProject, LargeProject.evaluations(changedProject.rooms))

        val counting = CountingSceneBuilder()
        val cache = CachingSceneBuilder(counting)
        val uncached = DefaultSceneBuilder()
        val full = measure(base, base) { uncached.build(it.project, it.evaluations, options) }
        val unchanged = measure(base, base) { cache.build(it.project, it.evaluations, options) }
        val equalCopy = measure(base, copy) { cache.build(it.project, it.evaluations, options) }
        counting.drain()
        val oneRoom = measure(base, changed) { cache.build(it.project, it.evaluations, options) }
        val builtRooms = counting.drain()

        val elements = uncached.build(base.project, base.evaluations, options).rooms.sumOf { it.elements.size }
        println("SCENE walls=${LargeProject.WALLS} rooms=${LargeProject.ROOMS} elements=$elements")
        println("SCENE uncached  $full")
        println("SCENE unchanged $unchanged")
        println("SCENE equalCopy $equalCopy")
        println("SCENE oneRoom   $oneRoom")

        assertEquals(setOf(rooms[0].id), builtRooms.toSet())
        assertEquals((WARM_UP + RUNS) * 2 - 1, builtRooms.size, "one room per switch, the first state is cached")
        assertTrue(unchanged.bytes < full.bytes / UNCHANGED_SHARE, "$unchanged vs $full")
        assertTrue(equalCopy.bytes < full.bytes / UNCHANGED_SHARE, "$equalCopy vs $full")
        assertTrue(oneRoom.bytes < full.bytes / ONE_ROOM_SHARE, "$oneRoom vs $full")
    }

    /** Times and allocates [RUNS] builds that alternate between [first] and [second], per build. */
    private fun measure(
        first: Input,
        second: Input,
        build: (Input) -> DrawingScene,
    ): Sample {
        repeat(WARM_UP) {
            build(first)
            build(second)
        }
        val times = LongArray(RUNS * 2)
        val before = threads.currentThreadAllocatedBytes
        for (i in times.indices) {
            val start = System.nanoTime()
            build(if (i % 2 == 0) first else second)
            times[i] = System.nanoTime() - start
        }
        val bytes = (threads.currentThreadAllocatedBytes - before) / times.size
        times.sort()
        return Sample(times[times.size / 2] / NANOS_PER_MICRO, times[times.size * 95 / 100] / NANOS_PER_MICRO, bytes)
    }

    private companion object {
        const val WARM_UP = 200
        const val RUNS = 200
        const val NANOS_PER_MICRO = 1_000L
        const val UNCHANGED_SHARE = 20
        const val ONE_ROOM_SHARE = 5
    }
}
