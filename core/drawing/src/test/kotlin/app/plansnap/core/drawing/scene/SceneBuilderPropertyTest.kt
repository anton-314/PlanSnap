package app.plansnap.core.drawing.scene

import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.hit.DefaultHitTester
import app.plansnap.core.drawing.scene.SceneFixtures.assertTranslated
import app.plansnap.core.drawing.scene.SceneFixtures.evaluate
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.testing.fixtures.generators.ProjectGenerator
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SceneBuilderPropertyTest {
    private val builder = DefaultSceneBuilder()
    private val tester = DefaultHitTester()

    /**
     * Generated project with its evaluations, a seed for shuffling and a shift by whole millimeters.
     */
    private data class Case(
        val project: Project,
        val evaluations: ImmutableMap<RoomId, RoomEvaluation>,
        val shuffleSeed: Long,
        val shift: Vec2,
    ) {
        override fun toString(): String = "Case(project=${project.id.value}, rooms=${project.rooms.size}, shift=$shift)"
    }

    private val cases: Arb<Case> =
        arbitrary { rs ->
            val project = ProjectGenerator(rs.random.nextLong()).project()
            Case(
                project = project,
                evaluations = project.rooms.associate { it.id to evaluate(it) }.toImmutableMap(),
                shuffleSeed = rs.random.nextLong(),
                shift =
                    Vec2(
                        rs.random.nextInt(-SHIFT_MM, SHIFT_MM).toDouble(),
                        rs.random.nextInt(-SHIFT_MM, SHIFT_MM).toDouble(),
                    ),
            )
        }

    @Test
    fun `equal input gives an equal scene`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_DETERMINISM), cases) { case ->
                val first = builder.build(case.project, case.evaluations, OPTIONS)
                assertEquals(first, DefaultSceneBuilder().build(case.project, case.evaluations, OPTIONS), "$case")
            }
        }

    @Test
    fun `reordering the rooms reorders the room scenes and keeps each of them and the bounds`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_ORDER), cases) { case ->
                val scene = builder.build(case.project, case.evaluations, OPTIONS)
                val shuffledRooms =
                    case.project.rooms
                        .shuffled(Random(case.shuffleSeed))
                        .toImmutableList()
                val shuffled = builder.build(case.project.copy(rooms = shuffledRooms), case.evaluations, OPTIONS)
                assertEquals(shuffledRooms.map { it.id }, shuffled.rooms.map { it.roomId }, "$case")
                assertEquals(scene.rooms.associateBy { it.roomId }, shuffled.rooms.associateBy { it.roomId }, "$case")
                assertEquals(scene.bounds, shuffled.bounds, "$case")
            }
        }

    @Test
    fun `translating every placement translates every element`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_TRANSLATION), cases) { case ->
                val moved =
                    case.project.copy(
                        rooms =
                            case.project.rooms
                                .map {
                                    it.copy(
                                        placement =
                                            it.placement.copy(
                                                originX = it.placement.originX + case.shift.x,
                                                originY = it.placement.originY + case.shift.y,
                                            ),
                                    )
                                }.toImmutableList(),
                    )
                val original = builder.build(case.project, case.evaluations, OPTIONS)
                val translated = builder.build(moved, case.evaluations, OPTIONS)
                original.rooms.zip(translated.rooms).forEach { (a, b) ->
                    assertTranslated(a, b, case.shift, TOLERANCE_MM)
                }
            }
        }

    @Test
    fun `every corner and opening of a generated project is hittable at its position`() =
        runBlocking<Unit> {
            var openings = 0
            checkAll(ITERATIONS, config(SEED_HITS), cases) { case ->
                val scene = builder.build(case.project, case.evaluations, OPTIONS)
                case.project.rooms.forEach { room ->
                    val shape = case.evaluations.getValue(room.id).shape ?: return@forEach
                    val world =
                        shape.outline.vertices.map {
                            it.rotated(room.placement.rotationRad) +
                                origin(room.placement)
                        }
                    world.forEachIndexed { i, vertex ->
                        assertEquals(SceneTarget.Corner(room.id, i), tester.hitTest(scene, vertex, RADIUS_MM), "$case")
                    }
                    room.openings.forEach { opening ->
                        val i = room.walls.indexOfFirst { it.id == opening.wallId }
                        val edge = world[(i + 1) % world.size] - world[i]
                        val point = world[i] + edge * ((opening.offset.mm + opening.width.mm / 2.0) / edge.length())
                        assertEquals(
                            SceneTarget.Opening(room.id, opening.id),
                            tester.hitTest(scene, point, RADIUS_MM),
                            "$case",
                        )
                        openings++
                    }
                }
            }
            assertTrue(openings > ITERATIONS, "only $openings openings were hit")
        }

    private fun origin(placement: RoomPlacement) = Vec2(placement.originX, placement.originY)

    private companion object {
        const val ITERATIONS = 1_000
        const val SHIFT_MM = 1_000_000
        const val TOLERANCE_MM = 1e-6
        const val RADIUS_MM = 0.5
        const val SEED_DETERMINISM = 7_101L
        const val SEED_ORDER = 7_102L
        const val SEED_TRANSLATION = 7_103L
        const val SEED_HITS = 7_104L
        val OPTIONS = SceneOptions(includeNotes = true)

        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)
    }
}
