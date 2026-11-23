package app.plansnap.testing.fixtures.generators

import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.testing.fixtures.IndependentGeometry
import app.plansnap.testing.fixtures.runtime.FakeEpochClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GeneratorsTest {
    private val seeds = (1L..150L)

    @Test
    fun `given the same seed when generating rooms then the data is identical`() {
        seeds.forEach { seed ->
            val a = RoomGenerator(seed)
            val b = RoomGenerator(seed)

            repeat(5) { assertEquals(a.any(), b.any(), "seed $seed") }
        }
    }

    @Test
    fun `given different seeds when generating rooms then the data differs`() {
        val distinct = seeds.map { RoomGenerator(it).any().vertices }.toSet()

        assertTrue(distinct.size > seeds.count() * 9 / 10)
    }

    @Test
    fun `given any seed when generating rooms then walls, closure and ids satisfy the invariants`() {
        seeds.forEach { seed ->
            val generator = RoomGenerator(seed)
            repeat(4) {
                val generated = generator.any()
                val room = generated.room

                assertTrue(room.isClosed)
                assertTrue(room.walls.size in 3..GeometryTolerances.MAX_WALLS_PER_ROOM)
                assertTrue(
                    room.walls.all {
                        it.length.mm in
                            GeometryTolerances.MIN_WALL_MM..GeometryTolerances.MAX_WALL_MM
                    },
                )
                assertEquals(generated.vertices.size, room.walls.size)
                assertEquals(
                    room.walls.size,
                    room.walls
                        .map { it.id }
                        .toSet()
                        .size,
                )
            }
        }
    }

    @Test
    fun `given any seed when generating rooms then the outline is a simple counter-clockwise polygon`() {
        seeds.forEach { seed ->
            val generator = RoomGenerator(seed)
            repeat(4) {
                val vertices = generator.any().vertices

                assertTrue(IndependentGeometry.signedArea(vertices) > 0.0, "seed $seed")
                assertTrue(IndependentGeometry.isSimple(vertices), "seed $seed")
            }
        }
    }

    @Test
    fun `given any seed when generating rooms then raw values reproduce the outline within rounding`() {
        seeds.forEach { seed ->
            val generated = RoomGenerator(seed).any()

            val edges = IndependentGeometry.edgeLengths(generated.vertices)

            generated.room.walls
                .zip(
                    edges,
                ).forEach { (wall, edge) -> assertEquals(edge, wall.length.mm.toDouble(), 0.5) }
        }
    }

    @Test
    fun `given a histogram room then its area is the sum of the column areas by shoelace`() {
        val generated = RoomGenerator(5L).histogram(columns = 3)

        val area = IndependentGeometry.shoelaceArea(generated.vertices)

        assertEquals(columnArea(generated), area, 1e-6)
        assertEquals(8, generated.room.walls.size)
    }

    @Test
    fun `given the largest column count then the room has 128 walls and the base wall stays under the limit`() {
        val generated = RoomGenerator(9L).histogram(RoomGenerator.MAX_COLUMNS)

        assertEquals(GeometryTolerances.MAX_WALLS_PER_ROOM, generated.room.walls.size)
        assertTrue(generated.room.walls.all { it.length.mm <= GeometryTolerances.MAX_WALL_MM })
        assertTrue(IndependentGeometry.isSimple(generated.vertices))
    }

    @Test
    fun `given a rectangle request then it has four walls and right angles only`() {
        val generated = RoomGenerator(3L).rectangle()

        assertEquals(4, generated.room.walls.size)
        assertEquals(listOf(90.0, 90.0, 90.0, 90.0), IndependentGeometry.interiorAnglesDeg(generated.vertices))
    }

    @Test
    fun `given out of range arguments then the generators reject them`() {
        val generator = RoomGenerator(1L)

        assertThrows<IllegalArgumentException> { generator.histogram(0) }
        assertThrows<IllegalArgumentException> { generator.histogram(RoomGenerator.MAX_COLUMNS + 1) }
        assertThrows<IllegalArgumentException> { generator.star(RoomGenerator.MIN_STAR_VERTICES - 1) }
        assertThrows<IllegalArgumentException> { generator.star(RoomGenerator.MAX_STAR_VERTICES + 1) }
    }

    @Test
    fun `given star rooms then they have the requested corner count and oblique corners`() {
        val generated = RoomGenerator(21L).star(RoomGenerator.MAX_STAR_VERTICES)

        assertEquals(RoomGenerator.MAX_STAR_VERTICES, generated.room.walls.size)
        assertTrue(IndependentGeometry.interiorAnglesDeg(generated.vertices).any { it !in listOf(90.0, 270.0) })
    }

    @Test
    fun `given the same seed when generating projects then the data is identical`() {
        seeds.forEach { seed ->
            val a = ProjectGenerator(seed)
            val b = ProjectGenerator(seed)

            repeat(3) { assertEquals(a.project(), b.project(), "seed $seed") }
        }
    }

    @Test
    fun `given different seeds when generating projects then the data differs`() {
        val distinct = seeds.map { ProjectGenerator(it).project() }.toSet()

        assertEquals(seeds.count(), distinct.size)
    }

    @Test
    fun `given successive projects of one generator then ids never repeat`() {
        val generator = ProjectGenerator(77L)

        val projects = List(10) { generator.project() }

        assertEquals(10, projects.map { it.id }.toSet().size)
        assertEquals(projects.sumOf { it.rooms.size }, projects.flatMap { p -> p.rooms.map { it.id } }.toSet().size)
    }

    @Test
    fun `given any seed when generating projects then timestamps are fixed and ordered without the real clock`() {
        seeds.forEach { seed ->
            val project = ProjectGenerator(seed).project()

            assertTrue(project.createdAtEpochMs >= FakeEpochClock.DEFAULT_START_EPOCH_MS)
            assertTrue(project.createdAtEpochMs < FakeEpochClock.DEFAULT_START_EPOCH_MS + 365L * 86_400_000L)
            assertTrue(project.updatedAtEpochMs >= project.createdAtEpochMs)
        }
    }

    @Test
    fun `given any seed when generating projects then openings fit, do not overlap and respect their type`() {
        val rooms = seeds.flatMap { ProjectGenerator(it).project().rooms }

        rooms.forEach { assertOpeningsValid(it) }

        assertTrue(rooms.sumOf { it.openings.size } > 0)
    }

    @Test
    fun `given any seed when generating projects then rooms are placed on distinct grid cells`() {
        seeds.forEach { seed ->
            val project = ProjectGenerator(seed).project(roomCount = 6)

            val origins = project.rooms.map { it.placement.originX to it.placement.originY }

            assertEquals(origins.toSet().size, origins.size)
            assertTrue(project.rooms.all { it.placement.dock == null })
        }
    }

    @Test
    fun `given a room count then the project has exactly that many rooms and negative counts are rejected`() {
        assertEquals(0, ProjectGenerator(1L).project(roomCount = 0).rooms.size)
        assertEquals(7, ProjectGenerator(1L).project(roomCount = 7).rooms.size)
        assertThrows<IllegalArgumentException> { ProjectGenerator(1L).project(roomCount = -1) }
    }

    @Test
    fun `given generated projects then the seed changes the content of at least one room`() {
        val a: Project = ProjectGenerator(1L).project(roomCount = 3)
        val b: Project = ProjectGenerator(2L).project(roomCount = 3)

        assertNotEquals(a.rooms, b.rooms)
    }

    private fun assertOpeningsValid(room: Room) {
        val wallLengths = room.walls.associate { it.id to it.length.mm }
        room.openings.forEach { opening ->
            assertNotNull(wallLengths[opening.wallId])
            assertTrue(opening.endOffset.mm <= wallLengths.getValue(opening.wallId))
            assertTypeFields(opening)
        }
        room.openings.groupBy { it.wallId }.values.forEach { onWall ->
            onWall.sortedBy { it.offset.mm }.zipWithNext().forEach { (left, right) ->
                assertTrue(left.endOffset.mm <= right.offset.mm)
            }
        }
    }

    private fun assertTypeFields(opening: Opening) {
        when (opening.type) {
            OpeningType.DOOR -> assertTrue(opening.swing != null && opening.sillHeight == null)
            OpeningType.WINDOW -> assertTrue(opening.swing == null && opening.sillHeight != null)
            OpeningType.PASSAGE -> assertTrue(opening.swing == null && opening.sillHeight == null)
        }
    }

    /** Sum of width times height of the columns, read from the generated coordinates of the upper boundary. */
    private fun columnArea(generated: GeneratedRoom): Double {
        val top = generated.vertices.drop(2)
        return top.chunked(2).sumOf { (right, left) -> (right.x - left.x) * right.y }
    }
}
