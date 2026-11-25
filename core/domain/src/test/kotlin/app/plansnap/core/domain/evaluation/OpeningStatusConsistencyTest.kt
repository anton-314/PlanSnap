package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.opening.DefaultOpeningRules
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.inputFingerprint
import app.plansnap.testing.fixtures.random.SeededRandom
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
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
import kotlin.math.ceil
import kotlin.math.floor

/**
 * One effective wall length for openings (DOMAIN section 9, ADR 0021, CR-W2-06): the opening rules behind
 * `AddOpening` and the evaluation the scene draws from report the same status for every opening.
 */
class OpeningStatusConsistencyTest {
    private val rules = DefaultOpeningRules()

    private fun door(
        room: Room,
        id: String,
        wallIndex: Int,
        offset: Long,
        width: Long,
    ): Opening =
        Opening(
            id = OpeningId(id),
            wallId = room.walls[wallIndex].id,
            type = OpeningType.DOOR,
            offset = Length(offset),
            width = Length(width),
            height = null,
            sillHeight = null,
            swing = null,
        )

    private fun Room.withOpenings(openings: List<Opening>): Room = copy(openings = openings.toImmutableList())

    private fun assertBoth(
        expected: OpeningStatus,
        room: Room,
        evaluation: RoomEvaluation = evaluated(room),
    ) {
        room.openings.forEach {
            assertEquals(expected, evaluation.openingStatuses[it.id], "evaluator ${it.id}")
            assertEquals(expected, rules.statusOf(room, it), "rules ${it.id}")
        }
    }

    // Regression cases of the wave 2 gate review.

    @Test
    fun `given a measurement error preview when a door passes the preview wall then the raw length decides`() {
        val base = OverdeterminedCases.GAP_30_MM.room
        val door = door(base, "door", 0, 3200, 831)
        val room = base.withOpenings(listOf(door))
        val evaluation = evaluated(room)
        assertEquals(ShapeStatus.NEEDS_ADJUSTMENT, evaluation.status)
        val preview = evaluation.shape!!.lengthsMm[0]
        assertTrue(preview + GeometryTolerances.EXACT_CLOSURE_MM < door.endOffset.mm, "$preview")
        assertBoth(OpeningStatus.VALID, room, evaluation)
        assertEquals(DomainResult.Success(Unit), rules.check(base, door))
    }

    @Test
    fun `given the accepted preview when the overlay shortens the wall then the door is out of bounds`() {
        val base = OverdeterminedCases.GAP_30_MM.room
        val accepted = base.copy(adjustment = (evaluated(base) as RoomEvaluation.NeedsAdjustment).proposal.adjustment)
        val room = accepted.withOpenings(listOf(door(base, "door", 0, 3200, 831)))
        val evaluation = evaluated(room)
        assertEquals(ShapeStatus.ADJUSTED, evaluation.status)
        assertBoth(OpeningStatus.OUT_OF_BOUNDS, room, evaluation)
    }

    @Test
    fun `given a current but structurally invalid overlay when an opening fits the raw wall then it is valid`() {
        val base = room("4000 3000 4000 3000", "F L L L")
        val open =
            ClosureAdjustment(
                inputFingerprint = base.inputFingerprint(),
                solverVersion = 1,
                adjustedLengthsMm = listOf(3000.0, 3000.0, 4000.0, 3000.0).toImmutableList(),
                adjustedTurnsRad = List(4) { Math.PI / 2 }.toImmutableList(),
            )
        val room = base.copy(adjustment = open).withOpenings(listOf(door(base, "door", 0, 3000, 1000)))
        val evaluation = evaluated(room)
        assertEquals(ShapeStatus.CONFIRMED, evaluation.status)
        assertBoth(OpeningStatus.VALID, room, evaluation)
        assertEquals(DomainResult.Success(Unit), rules.check(base.copy(adjustment = open), room.openings[0]))
    }

    @Test
    fun `given an accepted overlay when a door ends near the adjusted wall end then rules and evaluator agree`() {
        val base = OverdeterminedCases.GAP_30_MM.room
        val overlay = (evaluated(base) as RoomEvaluation.NeedsAdjustment).proposal.adjustment
        val adjusted = overlay.adjustedLengthsMm[0]
        val accepted = base.copy(adjustment = overlay)
        val ends = floor(adjusted).toLong() - 2..ceil(adjusted).toLong() + 2
        val room = accepted.withOpenings(ends.map { door(accepted, "d$it", 0, it - 800, 800) })
        val evaluation = evaluated(room)
        room.openings.forEach {
            val fits = it.endOffset.mm <= adjusted + GeometryTolerances.EXACT_CLOSURE_MM
            val expected = if (fits) OpeningStatus.VALID else OpeningStatus.OUT_OF_BOUNDS
            assertEquals(expected, evaluation.openingStatuses[it.id], "evaluator ${it.id}")
            assertEquals(expected, rules.statusOf(room, it), "rules ${it.id}")
        }
    }

    // Consistency over the catalogs.

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `given catalog rooms with random openings near the wall ends when evaluated then rules and evaluator agree`() =
        runBlocking<Unit> {
            val bases = catalogRooms()
            assertTrue(ITERATIONS >= 2 * bases.size, "every base at least twice: ${bases.size}")
            var iterations = 0
            var compared = 0
            checkAll(ITERATIONS, PropTestConfig(seed = SEED), Arb.long()) { seed ->
                val random = SeededRandom(seed)
                val base = bases[iterations++ % bases.size]
                val baseEvaluation = evaluated(base)
                val room = base.withOpenings(openingsNearWallEnds(base, baseEvaluation, random))
                val evaluation = evaluated(room)
                assertEquals(baseEvaluation.status, evaluation.status, base.id.value)
                room.openings.forEach {
                    assertEquals(evaluation.openingStatuses[it.id], rules.statusOf(room, it), "${base.id} ${it.id}")
                    compared++
                }
            }
            assertEquals(ITERATIONS, iterations)
            assertEquals(ITERATIONS * OPENINGS_PER_ROOM, compared)
        }

    @Test
    fun `given the catalogs when building the bases then every status of DOMAIN section 4 is covered`() {
        val statuses = catalogRooms().map { evaluated(it).status }.toSet()
        assertEquals(ShapeStatus.entries.toSet(), statuses)
    }

    /**
     * Openings whose end lies within a few millimeters of the raw length and of the displayed length of a random wall,
     * so a disagreement between both definitions of the effective length would show.
     */
    private fun openingsNearWallEnds(
        room: Room,
        evaluation: RoomEvaluation,
        random: SeededRandom,
    ): List<Opening> =
        List(OPENINGS_PER_ROOM) { index ->
            val wall = random.nextInt(room.walls.size)
            val raw =
                room.walls[wall]
                    .length.mm
                    .toDouble()
            val shown = evaluation.shape?.lengthsMm?.get(wall) ?: raw
            val anchor = if (random.nextBoolean()) raw else shown
            val end = (floor(anchor).toLong() + random.nextInt(-NEAR_MM..NEAR_MM)).coerceAtLeast(1L)
            val width = random.nextInt(1..MAX_WIDTH_MM).toLong().coerceAtMost(end)
            door(room, "o$index", wall, end - width, width)
        }

    /**
     * Every closed catalog room, plus the accepted overlay of every `NEEDS_ADJUSTMENT` case and a current overlay that
     * does not close, so all statuses and the ignored overlay of DOMAIN section 6 are covered.
     */
    private fun catalogRooms(): List<Room> {
        val plain = OverdeterminedCases.ALL.map { it.room } + ReferenceRooms.ALL.map { it.room }
        val evaluator = DefaultRoomEvaluator(DirectDispatchers)
        val valid = plain.filter { runBlocking { evaluator.evaluate(it, DEFAULT_TOLERANCE) }.isSuccess() }
        val accepted =
            valid.mapNotNull { room ->
                (evaluated(room) as? RoomEvaluation.NeedsAdjustment)?.let {
                    room.copy(adjustment = it.proposal.adjustment)
                }
            }
        val notClosing =
            valid.filter { it.isClosed }.map { room ->
                room.copy(
                    adjustment =
                        ClosureAdjustment(
                            inputFingerprint = room.inputFingerprint(),
                            solverVersion = 1,
                            adjustedLengthsMm = room.walls.map { it.length.mm.toDouble() }.toImmutableList(),
                            adjustedTurnsRad = room.walls.map { 0.0 }.toImmutableList(),
                        ),
                )
            }
        val open = valid.filter { it.walls.size > 2 }.map { it.copy(isClosed = false, adjustment = null) }
        return valid + accepted + notClosing + open
    }

    private fun DomainResult<*>.isSuccess(): Boolean = this is DomainResult.Success

    private companion object {
        const val ITERATIONS = 300
        const val SEED = 20_261_125L
        const val OPENINGS_PER_ROOM = 8
        const val NEAR_MM = 4
        const val MAX_WIDTH_MM = 1200
    }
}
