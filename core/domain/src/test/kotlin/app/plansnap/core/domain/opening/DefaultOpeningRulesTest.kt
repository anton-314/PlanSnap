package app.plansnap.core.domain.opening

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.units.Length
import app.plansnap.core.model.ClosureAdjustment
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Room
import app.plansnap.core.model.WallId
import app.plansnap.core.model.inputFingerprint
import app.plansnap.testing.fixtures.rooms.roomFromVertices
import app.plansnap.testing.fixtures.rooms.vertex
import app.plansnap.testing.fixtures.rooms.wallIdOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Table tests for the opening rules (DOMAIN section 9, REQ-MOD-001 to REQ-MOD-003). Wall w0 is 4000 mm long. */
class DefaultOpeningRulesTest {
    private val rules = DefaultOpeningRules()
    private val base = roomFromVertices("r", listOf(vertex(0, 0), vertex(4000, 0), vertex(4000, 3000), vertex(0, 3000)))
    private val wall = wallIdOf("r", 0)

    private fun opening(
        id: String,
        offset: Long,
        width: Long,
        wallId: WallId = wall,
    ): Opening = Opening(OpeningId(id), wallId, OpeningType.PASSAGE, Length(offset), Length(width), null, null, null)

    private fun Room.with(vararg stored: Opening): Room = copy(openings = persistentListOf(*stored))

    /** Current overlay of a closed rectangle `length0 × 3000`, structurally valid (DOMAIN section 6). */
    private fun Room.adjustedTo(length0: Double): Room =
        withOverlay(listOf(length0, 3000.0, length0, 3000.0), List(4) { Math.PI / 2 })

    private fun Room.withOverlay(
        lengths: List<Double>,
        turns: List<Double>,
    ): Room =
        copy(
            adjustment =
                ClosureAdjustment(
                    inputFingerprint = inputFingerprint(),
                    solverVersion = 1,
                    adjustedLengthsMm = lengths.toImmutableList(),
                    adjustedTurnsRad = turns.toImmutableList(),
                ),
        )

    private fun success(): DomainResult<Unit> = DomainResult.Success(Unit)

    private fun violation(rule: DomainRule): DomainResult<Unit> = DomainResult.Failure(DomainError.RuleViolation(rule))

    @ParameterizedTest(name = "offset {0} width {1} -> accepted {2}")
    @CsvSource(
        "0,1,true",
        "0,4000,true",
        "0,4001,true",
        "0,4002,false",
        "3000,1000,true",
        "3000,1001,true",
        "3000,1002,false",
        "4000,1,true",
        "4000,2,false",
        "3999,1,true",
    )
    fun `given a raw wall when checking bounds then the end may exceed the length by at most the closure tolerance`(
        offset: Long,
        width: Long,
        accepted: Boolean,
    ) {
        val expected = if (accepted) success() else violation(DomainRule.OPENING_OUT_OF_BOUNDS)
        assertEquals(expected, rules.check(base, opening("o", offset, width)))
        val status = if (accepted) OpeningStatus.VALID else OpeningStatus.OUT_OF_BOUNDS
        assertEquals(status, rules.statusOf(base, opening("o", offset, width)))
    }

    @ParameterizedTest(name = "new [{0}, {1}) next to stored [1000, 2000) -> accepted {2}")
    @CsvSource(
        "0,1000,true",
        "0,1001,false",
        "2000,500,true",
        "1999,500,false",
        "1200,300,false",
        "500,2000,false",
        "0,500,true",
    )
    fun `given a stored opening when checking another then touching is no overlap and one millimeter is`(
        offset: Long,
        width: Long,
        accepted: Boolean,
    ) {
        val room = base.with(opening("stored", 1000, 1000))
        val expected = if (accepted) success() else violation(DomainRule.OPENING_OVERLAP)
        assertEquals(expected, rules.check(room, opening("new", offset, width)))
    }

    @Test
    fun `given a stored opening when replacing it with its own id then it does not overlap itself`() {
        val room = base.with(opening("a", 1000, 1000), opening("b", 3000, 500))
        assertEquals(success(), rules.check(room, opening("a", 1500, 1000)))
        assertEquals(violation(DomainRule.OPENING_OVERLAP), rules.check(room, opening("a", 2600, 1000)))
    }

    @Test
    fun `given openings on another wall when checking then they are ignored`() {
        val other = opening("x", 1000, 1000, wallId = wallIdOf("r", 1))
        assertEquals(success(), rules.check(base.with(other), opening("o", 1000, 1000)))
    }

    @Test
    fun `given a missing wall when checking then the wall is reported as not found`() {
        val result = rules.check(base, opening("o", 0, 100, wallId = WallId("ghost")))
        assertEquals(DomainResult.Failure(DomainError.NotFound(EntityKind.WALL, "ghost")), result)
    }

    @Test
    fun `given a missing wall when asking for the status then it is out of bounds`() {
        assertEquals(OpeningStatus.OUT_OF_BOUNDS, rules.statusOf(base, opening("o", 0, 100, wallId = WallId("ghost"))))
    }

    @Test
    fun `given out of bounds and overlap at once when checking then bounds are reported first`() {
        val room = base.with(opening("stored", 3000, 500))
        assertEquals(violation(DomainRule.OPENING_OUT_OF_BOUNDS), rules.check(room, opening("o", 3200, 2000)))
    }

    @Test
    fun `given a shortened wall when asking for the status then the stored opening stays and is out of bounds`() {
        val stored = opening("a", 2000, 1500)
        val shortened =
            base.with(stored).let {
                it.copy(
                    walls =
                        it.walls
                            .mapIndexed { i, w ->
                                if (i ==
                                    0
                                ) {
                                    w.copy(length = Length(3000))
                                } else {
                                    w
                                }
                            }.toImmutableList(),
                )
            }
        assertEquals(listOf(stored), shortened.openings)
        assertEquals(OpeningStatus.OUT_OF_BOUNDS, rules.statusOf(shortened, stored))
        assertEquals(OpeningStatus.VALID, rules.statusOf(base.with(stored), stored))
    }

    @ParameterizedTest(name = "adjusted length {0} -> status {1}")
    @CsvSource(
        "4000.0,VALID",
        "3999.0,VALID",
        "3998.9,OUT_OF_BOUNDS",
        "3500.0,OUT_OF_BOUNDS",
        "4200.0,VALID",
    )
    fun `given a current adjustment when asking for the status then the adjusted length decides`(
        adjusted: Double,
        status: OpeningStatus,
    ) {
        val stored = opening("a", 3000, 1000)
        val room = base.with(stored).adjustedTo(adjusted)
        assertEquals(status, rules.statusOf(room, stored))
        val expected = if (status == OpeningStatus.VALID) success() else violation(DomainRule.OPENING_OUT_OF_BOUNDS)
        assertEquals(expected, rules.check(room, stored))
    }

    @Test
    fun `given a stale adjustment when asking for the status then the raw length decides`() {
        val stored = opening("a", 3000, 1000)
        val adjusted = base.with(stored).adjustedTo(3000.0)
        val stale =
            adjusted.copy(
                adjustment =
                    adjusted.adjustment?.copy(
                        inputFingerprint =
                            adjusted.inputFingerprint() + 1,
                    ),
            )
        assertEquals(OpeningStatus.VALID, rules.statusOf(stale, stored))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "outline does not close,3000.0,1.5707963267948966",
        "outline is a straight line,3500.0,0.0",
    )
    fun `given a current but structurally invalid adjustment when asking for the status then the raw length decides`(
        case: String,
        length0: Double,
        turn: Double,
    ) {
        val stored = opening("a", 3000, 1000)
        val invalid = base.with(stored).withOverlay(listOf(length0, 3000.0, 4000.0, 3000.0), List(4) { turn })
        assertEquals(OpeningStatus.VALID, rules.statusOf(invalid, stored), case)
        assertEquals(success(), rules.check(invalid, stored), case)
        assertEquals(OpeningStatus.OUT_OF_BOUNDS, rules.statusOf(invalid, opening("b", 3000, 1002)), case)
    }

    @Test
    fun `given optional fields when checking then they do not influence the result`() {
        val window =
            Opening(
                OpeningId("w"),
                wall,
                OpeningType.WINDOW,
                Length(100),
                Length(800),
                Length(1200),
                Length(900),
                null,
            )
        assertEquals(success(), rules.check(base, window))
        assertEquals(OpeningStatus.VALID, rules.statusOf(base, window))
    }
}
