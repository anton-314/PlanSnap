package app.plansnap.core.domain.validation

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.command.door
import app.plansnap.core.domain.command.projectOf
import app.plansnap.core.domain.command.rectangle
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Table tests of [DefaultProjectValidator], one valid and one violating case per rule. */
class DefaultProjectValidatorTest {
    private val validator = DefaultProjectValidator()
    private val ok = DomainResult.Success(Unit)

    private fun fail(error: DomainError) = DomainResult.Failure(error)

    private fun rule(rule: DomainRule) = fail(DomainError.RuleViolation(rule))

    private fun Room.withWall(
        index: Int,
        wall: Wall,
    ): Room = copy(walls = walls.toMutableList().apply { this[index] = wall }.toImmutableList())

    private fun Room.dockedTo(
        host: String,
        hostWall: String = "$host-w0",
        ownWall: String = "${id.value}-w0",
        opening: OpeningId? = null,
    ): Room =
        copy(
            placement =
                placement.copy(
                    dock = DockLink(RoomId(host), WallId(hostWall), WallId(ownWall), Length(0), opening),
                ),
        )

    @Test
    fun `given a valid project when validating then it succeeds`() {
        val a = rectangle("a").copy(openings = persistentListOf(door("o", WallId("a-w0"))))
        val b = rectangle("b").dockedTo("a")

        assertEquals(ok, validator.validate(projectOf(a, b)))
        assertEquals(ok, validator.validate(projectOf()))
    }

    @Test
    fun `given wall lengths at and beyond the limits when validating then only the beyond cases fail`() {
        val room = rectangle("a")
        val cases =
            listOf(
                GeometryTolerances.MIN_WALL_MM to ok,
                GeometryTolerances.MAX_WALL_MM to ok,
                GeometryTolerances.MIN_WALL_MM - 1 to
                    fail(DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL)),
                GeometryTolerances.MAX_WALL_MM + 1 to
                    fail(DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_LARGE)),
            )
        cases.forEach { (mm, expected) ->
            val changed = room.withWall(1, Wall(WallId("a-w1"), Length(mm), Length(120)))
            assertEquals(expected, validator.validate(projectOf(changed)), "length $mm")
        }
    }

    @Test
    fun `given wall thickness at and beyond the limit when validating then only the beyond case fails`() {
        val room = rectangle("a")
        val atLimit = room.withWall(0, Wall(WallId("a-w0"), Length(4000), Length(GeometryTolerances.MAX_THICKNESS_MM)))
        val beyond =
            room.withWall(
                0,
                Wall(WallId("a-w0"), Length(4000), Length(GeometryTolerances.MAX_THICKNESS_MM + 1)),
            )

        assertEquals(ok, validator.validate(projectOf(atLimit)))
        assertEquals(
            fail(DomainError.InvalidValue(ValueField.WALL_THICKNESS, InvalidValueReason.TOO_LARGE)),
            validator.validate(projectOf(beyond)),
        )
    }

    @Test
    fun `given too many walls when validating then the rule is violated`() {
        fun room(count: Int) =
            Room(
                id = RoomId("a"),
                walls = List(count) { Wall(WallId("a-w$it"), Length(1000), Length(120)) }.toImmutableList(),
                corners =
                    List(count) {
                        if (it ==
                            0
                        ) {
                            CornerSpec.Unspecified
                        } else {
                            CornerSpec.DeclaredRight(TurnDirection.LEFT)
                        }
                    }.toImmutableList(),
            )

        assertEquals(ok, validator.validate(projectOf(room(GeometryTolerances.MAX_WALLS_PER_ROOM))))
        assertEquals(
            rule(DomainRule.TOO_MANY_WALLS),
            validator.validate(projectOf(room(GeometryTolerances.MAX_WALLS_PER_ROOM + 1))),
        )
    }

    @Test
    fun `given touching and overlapping openings when validating then only the overlap fails`() {
        val wall = WallId("a-w0")
        val touching =
            rectangle(
                "a",
            ).copy(openings = persistentListOf(door("o1", wall, 0, 900), door("o2", wall, 900, 900)))
        val overlapping =
            rectangle(
                "a",
            ).copy(openings = persistentListOf(door("o1", wall, 0, 900), door("o2", wall, 899, 900)))
        val otherWall =
            rectangle(
                "a",
            ).copy(openings = persistentListOf(door("o1", wall, 0, 900), door("o2", WallId("a-w1"), 0, 900)))

        assertEquals(ok, validator.validate(projectOf(touching)))
        assertEquals(ok, validator.validate(projectOf(otherWall)))
        assertEquals(rule(DomainRule.OPENING_OVERLAP), validator.validate(projectOf(overlapping)))
    }

    @Test
    fun `given an out of bounds opening when validating then it is a derived status and no violation`() {
        val room = rectangle("a").copy(openings = persistentListOf(door("o", WallId("a-w0"), 3500, 900)))

        assertEquals(ok, validator.validate(projectOf(room)))
    }

    @Test
    fun `given an out of bounds opening that overlaps when validating then the overlap is reported`() {
        val wall = WallId("a-w0")
        val room =
            rectangle(
                "a",
            ).copy(openings = persistentListOf(door("o1", wall, 3500, 900), door("o2", wall, 3600, 100)))

        assertEquals(rule(DomainRule.OPENING_OVERLAP), validator.validate(projectOf(room)))
    }

    @Test
    fun `given dock links when validating then every target kind is checked`() {
        val host = rectangle("h").copy(openings = persistentListOf(door("o", WallId("h-w1"))))
        val cases =
            listOf(
                rectangle("b").dockedTo("h", "h-w0", "b-w0") to ok,
                rectangle("b").dockedTo("h", "h-w1", "b-w0", OpeningId("o")) to ok,
                rectangle("b").dockedTo("missing") to fail(DomainError.NotFound(EntityKind.ROOM, "missing")),
                rectangle("b").dockedTo("h", "h-w9") to fail(DomainError.NotFound(EntityKind.WALL, "h-w9")),
                rectangle("b").dockedTo("h", ownWall = "b-w9") to fail(DomainError.NotFound(EntityKind.WALL, "b-w9")),
                rectangle("b").dockedTo("h", "h-w1", opening = OpeningId("x")) to
                    fail(DomainError.NotFound(EntityKind.OPENING, "x")),
                rectangle("b").dockedTo("h", "h-w0", opening = OpeningId("o")) to
                    rule(DomainRule.DOCK_OPENING_NOT_ON_WALL),
            )
        cases.forEach { (room, expected) ->
            assertEquals(expected, validator.validate(projectOf(host, room)), "${room.placement.dock}")
        }
    }

    @Test
    fun `given dock cycles when validating then the cycle is reported`() {
        val a = rectangle("a").dockedTo("b", "b-w0", "a-w0")
        val b = rectangle("b").dockedTo("a", "a-w0", "b-w0")
        val c = rectangle("c").dockedTo("a", "a-w0", "c-w0")
        val chain = rectangle("d").dockedTo("c")

        assertEquals(rule(DomainRule.DOCK_CYCLE), validator.validate(projectOf(a, b)))
        assertEquals(rule(DomainRule.DOCK_CYCLE), validator.validate(projectOf(a, b, c, chain)))
    }

    @Test
    fun `given violations in different rooms when validating then the earlier room wins`() {
        val wall = WallId("b-w0")
        val overlapping =
            rectangle("b").copy(openings = persistentListOf(door("o1", wall, 0, 900), door("o2", wall, 100, 900)))
        val badWall = rectangle("a").withWall(2, Wall(WallId("a-w2"), Length(5), Length(120)))
        val badDock = rectangle("c").dockedTo("missing")

        assertEquals(
            rule(DomainRule.OPENING_OVERLAP),
            validator.validate(projectOf(overlapping, badWall, badDock)),
        )
        assertEquals(
            fail(DomainError.NotFound(EntityKind.ROOM, "missing")),
            validator.validate(projectOf(badDock, overlapping, badWall)),
        )
    }

    @Test
    fun `given violations in one room when validating then wall limits come before overlaps before dock links`() {
        val wall = WallId("a-w0")
        val room =
            rectangle("a")
                .withWall(1, Wall(WallId("a-w1"), Length(5), Length(120)))
                .copy(openings = persistentListOf(door("o1", wall, 0, 900), door("o2", wall, 100, 900)))
                .dockedTo("missing")
        val withoutWall = room.withWall(1, Wall(WallId("a-w1"), Length(3000), Length(120)))
        val withoutOverlap = withoutWall.copy(openings = persistentListOf())

        assertEquals(
            fail(DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL)),
            validator.validate(projectOf(room)),
        )
        assertEquals(rule(DomainRule.OPENING_OVERLAP), validator.validate(projectOf(withoutWall)))
        assertEquals(
            fail(DomainError.NotFound(EntityKind.ROOM, "missing")),
            validator.validate(projectOf(withoutOverlap)),
        )
    }
}
