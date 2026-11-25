package app.plansnap.core.drawing.scene

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.abs

/** Rooms of every status and helpers for the scene tests. Evaluations come from the real [DefaultRoomEvaluator]. */
internal object SceneFixtures {
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher get() = Dispatchers.Unconfined
            override val default: CoroutineDispatcher get() = Dispatchers.Unconfined
            override val io: CoroutineDispatcher get() = Dispatchers.Unconfined
        }
    private val evaluator = DefaultRoomEvaluator(dispatchers)

    fun evaluate(room: Room): RoomEvaluation =
        runBlocking {
            when (val result = evaluator.evaluate(room, ClosureTolerance())) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> error("Evaluation failed: ${result.error}")
            }
        }

    /** The reference rectangle 4 m × 3 m, `CONFIRMED`. */
    val confirmed: Room = ReferenceRooms.RECTANGLE.room

    /** The rectangle with its third wall 30 mm too long: a gap below `T = 140 mm`, so `NEEDS_ADJUSTMENT`. */
    val needsAdjustment: Room =
        confirmed.copy(
            id = RoomId("gap"),
            walls =
                confirmed.walls
                    .mapIndexed { i, wall -> if (i == 2) wall.copy(length = Length(4030)) else wall }
                    .toImmutableList(),
        )

    /** [needsAdjustment] with its proposal accepted, `ADJUSTED`. */
    val adjusted: Room by lazy {
        val proposal = (evaluate(needsAdjustment) as RoomEvaluation.NeedsAdjustment).proposal
        needsAdjustment.copy(id = RoomId("adjusted"), adjustment = proposal.adjustment)
    }

    /** The trapezoid 3 × 3 × 3 × 5 m, `PROPOSED`. */
    val proposed: Room = ReferenceRooms.TRAPEZOID_3_3_3_5.room

    /** Triangle 1 m, 1 m, 5 m: the long wall exceeds the others, `CONFLICT(WALL_TOO_LONG)`. */
    val conflict: Room =
        Room(
            id = RoomId("conflict"),
            walls = walls("conflict", 1000, 1000, 5000),
            corners =
                persistentListOf(
                    CornerSpec.Unspecified,
                    CornerSpec.DeclaredRight(TurnDirection.LEFT),
                    CornerSpec.DeclaredRight(TurnDirection.LEFT),
                ),
            isClosed = true,
        )

    /** Two walls 3 m and 2 m with a left turn, not closed, `OPEN`. */
    val open: Room =
        Room(
            id = RoomId("open"),
            walls = walls("open", 3000, 2000),
            corners = persistentListOf(CornerSpec.Unspecified, CornerSpec.DeclaredRight(TurnDirection.LEFT)),
        )

    fun walls(
        prefix: String,
        vararg lengthsMm: Long,
    ) = lengthsMm
        .mapIndexed { i, mm -> Wall(WallId("$prefix-w$i"), Length(mm), Length(120)) }
        .toImmutableList()

    fun assertClose(
        expected: Vec2,
        actual: Vec2,
        toleranceMm: Double = 1e-6,
    ) {
        assertTrue(expected.isCloseTo(actual, toleranceMm), "expected $expected but was $actual")
    }

    /**
     * Asserts that [moved] equals [original] with every point shifted by [shift]: same kinds, styles, targets, texts,
     * sizes and angles, and points within [toleranceMm].
     */
    fun assertTranslated(
        original: RoomScene,
        moved: RoomScene,
        shift: Vec2,
        toleranceMm: Double,
    ) {
        assertEquals(original.elements.size, moved.elements.size)
        original.elements.zip(moved.elements).forEach { (a, b) ->
            assertEquals(a.style, b.style)
            assertEquals(a.target, b.target)
            assertEquals(pointsOf(a).size, pointsOf(b).size)
            pointsOf(a).zip(pointsOf(b)).forEach { (p, q) -> assertClose(p + shift, q, toleranceMm) }
            assertTrue(abs(scalarOf(a) - scalarOf(b)) <= toleranceMm, "$a vs $b")
            if (a is SceneElement.Label) assertEquals(a.text, (b as SceneElement.Label).text)
        }
    }

    private fun pointsOf(element: SceneElement): List<Vec2> =
        when (element) {
            is SceneElement.Fill -> element.polygon.vertices
            is SceneElement.Polyline -> element.points
            is SceneElement.Arc -> listOf(element.center)
            is SceneElement.Label -> listOf(element.anchor)
        }

    /** Sizes and angles, which a translation keeps. */
    private fun scalarOf(element: SceneElement): Double =
        when (element) {
            is SceneElement.Arc -> element.radiusMm + element.startRad + element.sweepRad
            is SceneElement.Label -> element.heightMm + element.rotationRad
            else -> 0.0
        }
}
