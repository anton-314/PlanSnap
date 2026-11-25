package app.plansnap.core.domain.placement

import app.plansnap.core.domain.command.DefaultProjectCommandFactory
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.testing.fixtures.random.SeededRandom
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Properties of docking and free movement (DOMAIN sections 2 and 10): the docking geometry holds for random trees of
 * random outlines, it is invariant under translation and rotation of the root, resolving is idempotent and a free move
 * preserves every length and angle.
 */
class DockPlacementPropertyTest {
    @Test
    fun `given random dock trees when the root is moved then relative placements and gaps are invariant`() =
        runBlocking<Unit> {
            val iterations = AtomicInteger()
            checkAll(ITERATIONS, config(), Arb.long()) { seed ->
                iterations.incrementAndGet()
                val random = SeededRandom(seed)
                val tree = randomTree(random)
                val resolver = DefaultDockPlacementResolver(FakeShapeEvaluator(tree.outlines))
                val root = tree.rooms.first()
                val movedRoot = root.copy(placement = randomPlacement(random))

                val first = resolver.resolve(projectOf(tree.rooms), TOLERANCE).value()
                val second = resolver.resolve(projectOf(listOf(movedRoot) + tree.rooms.drop(1)), TOLERANCE).value()

                for (room in tree.rooms.drop(1)) {
                    val id = room.id.value
                    assertDocked(first, tree.outlines, id, tree.alongMm.getValue(room.id))
                    assertDocked(second, tree.outlines, id, tree.alongMm.getValue(room.id))
                    val outline = tree.outlines.getValue(room.id)
                    val inFirst = worldOutline(first.room(id), outline).map { root.placement.toLocal(it) }
                    val inSecond = worldOutline(second.room(id), outline).map { movedRoot.placement.toLocal(it) }
                    inFirst.zip(inSecond).forEach { (a, b) -> assertTrue(a.isCloseTo(b, EPS_MM), "$a vs $b") }
                }
            }
            assertEquals(ITERATIONS, iterations.get())
        }

    @Test
    fun `given random dock trees when resolved twice then the second resolution changes nothing`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(), Arb.long()) { seed ->
                val tree = randomTree(SeededRandom(seed))
                val resolver = DefaultDockPlacementResolver(FakeShapeEvaluator(tree.outlines))

                val once = resolver.resolve(projectOf(tree.rooms), TOLERANCE).value()
                val twice = resolver.resolve(once, TOLERANCE).value()

                assertEquals(once, twice)
                assertEquals(resolver.resolve(projectOf(tree.rooms), TOLERANCE).value(), once, "deterministic")
            }
        }

    @Test
    fun `given random rooms when moved freely then all lengths and angles are preserved and the link is removed`() =
        runBlocking<Unit> {
            val factory = DefaultProjectCommandFactory(AcceptingOpeningRules())
            checkAll(ITERATIONS, config(), Arb.long()) { seed ->
                val random = SeededRandom(seed)
                val outline = randomOutline(random)
                val host = rawRoom("h", 4)
                val room =
                    rawRoom("r", outline.size, placement = randomPlacement(random)).dockedBy(link("h", 0, "r", 0, 0))
                val target = randomPlacement(random)
                val edit = ProjectEdit.MoveRoom(room.id, target.originX, target.originY, target.rotationRad)

                val moved =
                    factory
                        .create(edit)
                        .apply(projectOf(listOf(host, room)))
                        .value()
                        .room("r")

                assertNull(moved.placement.dock)
                assertEquals(room.copy(placement = moved.placement), moved, "only the placement may change")
                assertRigid(outline, worldOutline(moved, outline))
                val resolved =
                    DefaultDockPlacementResolver(FakeShapeEvaluator(mapOf(RoomId("r") to outline)))
                        .resolve(projectOf(listOf(host, moved)), TOLERANCE)
                        .value()
                assertEquals(moved, resolved.room("r"), "a free room is not re-docked")
            }
        }

    /** All pairwise distances and all turns agree between [local] and [world]. */
    private fun assertRigid(
        local: List<Vec2>,
        world: List<Vec2>,
    ) {
        for (i in local.indices) {
            for (j in local.indices) {
                assertEquals((local[j] - local[i]).length(), (world[j] - world[i]).length(), EPS_MM)
            }
        }
        val localShape = shapeOf(local)
        val worldShape = shapeOf(world)
        localShape.turnsRad.zip(worldShape.turnsRad).forEach { (a, b) -> assertEquals(a, b, ANGLE_EPS) }
        assertEquals(localShape.winding, worldShape.winding)
    }

    private class Tree(
        val rooms: List<Room>,
        val outlines: Map<RoomId, List<Vec2>>,
        val alongMm: Map<RoomId, Double>,
    )

    /** Random tree: room `i > 0` docks to a random earlier room, possibly through an opening. */
    private fun randomTree(random: SeededRandom): Tree {
        val count = random.nextInt(2..MAX_ROOMS)
        val outlines = HashMap<RoomId, List<Vec2>>()
        val alongMm = HashMap<RoomId, Double>()
        val rooms = ArrayList<Room>()
        for (i in 0 until count) {
            val id = "r$i"
            val outline = randomOutline(random)
            outlines[RoomId(id)] = outline
            val thickness = random.nextInt(0..MAX_THICKNESS).toLong()
            val openingWall = random.nextInt(outline.size)
            val openingOffset = random.nextInt(0..MAX_OFFSET).toLong()
            val openings = listOf(door("$id-d", wallId(id, openingWall), openingOffset))
            var room = rawRoom(id, outline.size, { thickness }, randomPlacement(random), openings)
            if (i > 0) {
                val host = rooms[random.nextInt(i)]
                val viaOpening = random.nextBoolean()
                val hostWall =
                    if (viaOpening) {
                        host.walls.indexOfFirst { it.id == host.openings.single().wallId }
                    } else {
                        random.nextInt(host.walls.size)
                    }
                val offset = random.nextInt(-MAX_OFFSET..MAX_OFFSET).toLong()
                val openingId =
                    if (viaOpening) {
                        host.openings
                            .single()
                            .id.value
                    } else {
                        null
                    }
                room =
                    room.dockedBy(
                        link(host.id.value, hostWall, id, random.nextInt(outline.size), offset).viaOpening(openingId),
                    )
                val base =
                    if (viaOpening) {
                        host.openings
                            .single()
                            .offset.mm
                    } else {
                        0L
                    }
                alongMm[room.id] = (base + offset).toDouble()
            }
            rooms += room
        }
        return Tree(rooms, outlines, alongMm)
    }

    /** Simple star-shaped counter-clockwise outline with 3 to 8 vertices around a random center. */
    private fun randomOutline(random: SeededRandom): List<Vec2> {
        val n = random.nextInt(3..MAX_VERTICES)
        val center = Vec2(random.nextDouble() * SPAN - SPAN / 2, random.nextDouble() * SPAN - SPAN / 2)
        return List(n) { k ->
            val angle = 2.0 * Math.PI * (k + random.nextDouble() * JITTER) / n
            val radius = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS)
            center + Vec2(StrictMath.cos(angle), StrictMath.sin(angle)) * radius
        }
    }

    private fun randomPlacement(random: SeededRandom): RoomPlacement =
        RoomPlacement(
            random.nextDouble() * WORLD_SPAN - WORLD_SPAN / 2,
            random.nextDouble() * WORLD_SPAN - WORLD_SPAN / 2,
            random.nextDouble() * 4.0 * Math.PI - 2.0 * Math.PI,
            null,
        )

    private companion object {
        const val ITERATIONS = 1_000
        const val SEED = 20_261_124L
        const val MAX_ROOMS = 6
        const val MAX_VERTICES = 8
        const val MAX_THICKNESS = 1_000
        const val MAX_OFFSET = 5_000
        const val JITTER = 0.5
        const val MIN_RADIUS = 1_000.0
        const val MAX_RADIUS = 6_000.0
        const val SPAN = 4_000.0
        const val WORLD_SPAN = 100_000.0
        const val ANGLE_EPS = 1e-9

        @OptIn(ExperimentalKotest::class)
        fun config() = PropTestConfig(seed = SEED)
    }
}
