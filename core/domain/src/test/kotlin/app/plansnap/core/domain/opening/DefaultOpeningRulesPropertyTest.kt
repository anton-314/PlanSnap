package app.plansnap.core.domain.opening

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.Length
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.testing.fixtures.random.SeededRandom
import app.plansnap.testing.fixtures.rooms.roomFromVertices
import app.plansnap.testing.fixtures.rooms.vertex
import app.plansnap.testing.fixtures.rooms.wallIdOf
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Property tests for the opening rules with fixed seeds (TESTING section 3). */
class DefaultOpeningRulesPropertyTest {
    private val rules = DefaultOpeningRules()
    private val wallLength = 6000L
    private val room =
        roomFromVertices("p", listOf(vertex(0, 0), vertex(6000, 0), vertex(6000, 3000), vertex(0, 3000)))
    private val wall = wallIdOf("p", 0)

    private fun opening(
        index: Int,
        offset: Long,
        width: Long,
    ) = Opening(OpeningId("o$index"), wall, OpeningType.PASSAGE, Length(offset), Length(width), null, null, null)

    /** Disjoint openings inside the wall, separated by gaps of zero (touching) or more millimeters. */
    private fun disjointOpenings(random: SeededRandom): List<Opening> {
        val result = mutableListOf<Opening>()
        var cursor = 0L
        repeat(random.nextInt(1..MAX_OPENINGS)) { index ->
            val gap = if (random.nextBoolean()) 0L else random.nextInt(0..MAX_GAP).toLong()
            val width = random.nextInt(1..MAX_WIDTH).toLong()
            if (cursor + gap + width <= wallLength) {
                result += opening(index, cursor + gap, width)
                cursor += gap + width
            }
        }
        return result
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `given disjoint openings inside a wall when checking each then all are accepted and valid`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, PropTestConfig(seed = SEED), Arb.long()) { seed ->
                val openings = disjointOpenings(SeededRandom(seed))
                val full = room.copy(openings = openings.toImmutableList())
                openings.forEach {
                    assertEquals(DomainResult.Success(Unit), rules.check(full, it))
                    assertEquals(OpeningStatus.VALID, rules.statusOf(full, it))
                }
            }
        }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `given an arbitrary opening when checking then bounds failures match the status`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, PropTestConfig(seed = SEED), Arb.long()) { seed ->
                val random = SeededRandom(seed)
                val candidate = opening(0, random.nextInt(0..WIDE).toLong(), random.nextInt(1..WIDE).toLong())
                val inBounds = rules.statusOf(room, candidate) == OpeningStatus.VALID
                assertEquals(inBounds, candidate.endOffset.mm <= wallLength + 1)
                assertEquals(inBounds, rules.check(room, candidate) is DomainResult.Success)
            }
        }

    private companion object {
        const val ITERATIONS = 200
        const val SEED = 20_261_124L
        const val MAX_OPENINGS = 6
        const val MAX_GAP = 500
        const val MAX_WIDTH = 1500
        const val WIDE = 8000
    }
}
