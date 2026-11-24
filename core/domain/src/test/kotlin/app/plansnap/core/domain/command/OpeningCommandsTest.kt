package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Project
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Table tests of the opening commands (DOMAIN sections 9 to 11). */
class OpeningCommandsTest {
    private val factory = DefaultProjectCommandFactory(FakeOpeningRules())
    private val a = RoomId("a")
    private val w0 = WallId("a-w0")
    private val w1 = WallId("a-w1")
    private val existing = door("d1", w0, offset = 100, width = 900)
    private val base = rectangle("a").let { it.copy(openings = persistentListOf(existing)) }
    private val host = base
    private val guest =
        rectangle("b").copy(
            placement =
                RoomPlacement(
                    0.0,
                    0.0,
                    0.0,
                    DockLink(a, w0, WallId("b-w0"), hostOffset = Length(250), hostOpeningId = existing.id),
                ),
        )
    private val project = projectOf(host, guest)

    private fun run(
        edit: ProjectEdit,
        on: Project = project,
    ) = factory.create(edit).apply(on)

    @Test
    fun `given a fitting opening when added then it is appended`() {
        val opening = door("d2", w0, offset = 1100)

        val room = valueOf(run(ProjectEdit.AddOpening(a, opening))).room("a")

        assertEquals(listOf(existing, opening), room.openings.toList())
    }

    @Test
    fun `given invalid openings when added then the rules and the id check reject them`() {
        fun rejected(
            expected: DomainError,
            edit: ProjectEdit,
        ) = assertEquals(expected, errorOf(run(edit)))

        rejected(violation(DomainRule.OPENING_OVERLAP), ProjectEdit.AddOpening(a, door("d2", w0, offset = 500)))
        rejected(violation(DomainRule.OPENING_OUT_OF_BOUNDS), ProjectEdit.AddOpening(a, door("d2", w0, offset = 3500)))
        rejected(violation(DomainRule.DUPLICATE_ID), ProjectEdit.AddOpening(a, existing.copy(offset = Length(2000))))
        rejected(missing(EntityKind.WALL, "zz"), ProjectEdit.AddOpening(a, door("d2", WallId("zz"))))
        rejected(missing(EntityKind.ROOM, "x"), ProjectEdit.AddOpening(RoomId("x"), door("d2", w0)))
    }

    @Test
    fun `given an opening without docked rooms when edited then it is replaced in place`() {
        val plain = projectOf(host)
        val edited = existing.copy(offset = Length(300), width = Length(800))

        val room = valueOf(run(ProjectEdit.EditOpening(a, edited), plain)).room("a")

        assertEquals(listOf(edited), room.openings.toList())
    }

    @Test
    fun `given a docked room when the opening keeps its wall then the edit is accepted`() {
        val edited = existing.copy(offset = Length(300))

        val room = valueOf(run(ProjectEdit.EditOpening(a, edited))).room("a")

        assertEquals(listOf(edited), room.openings.toList())
        assertEquals(guest, valueOf(run(ProjectEdit.EditOpening(a, edited))).room("b"))
    }

    @Test
    fun `given a docked room when the opening moves to another wall then it is rejected`() {
        val moved = existing.copy(wallId = w1)

        assertEquals(violation(DomainRule.OPENING_HAS_DOCKED_ROOMS), errorOf(run(ProjectEdit.EditOpening(a, moved))))
        assertEquals(
            moved,
            valueOf(run(ProjectEdit.EditOpening(a, moved), projectOf(host))).room("a").openings.single(),
        )
    }

    @Test
    fun `given invalid opening edits when applied then they are rejected`() {
        assertEquals(
            missing(EntityKind.OPENING, "nope"),
            errorOf(run(ProjectEdit.EditOpening(a, existing.copy(id = OpeningId("nope"))))),
        )
        assertEquals(
            missing(EntityKind.WALL, "zz"),
            errorOf(run(ProjectEdit.EditOpening(a, existing.copy(wallId = WallId("zz"))))),
        )
        assertEquals(
            violation(DomainRule.OPENING_OUT_OF_BOUNDS),
            errorOf(run(ProjectEdit.EditOpening(a, existing.copy(offset = Length(3500))), projectOf(host))),
        )
    }

    @Test
    fun `given a docked room when the opening is removed then the room keeps its world position`() {
        val result = valueOf(run(ProjectEdit.RemoveOpening(a, existing.id)))

        assertEquals(emptyList<Any>(), result.room("a").openings.toList())
        val dock = result.room("b").placement.dock
        assertEquals(null, dock?.hostOpeningId)
        assertEquals(Length(350), dock?.hostOffset)
        assertEquals(guest.placement.copy(dock = dock), result.room("b").placement)
        assertEquals(guest.placement.dock?.hostWallId, dock?.hostWallId)
    }

    @Test
    fun `given invalid removals when applied then they are rejected`() {
        assertEquals(missing(EntityKind.OPENING, "nope"), errorOf(run(ProjectEdit.RemoveOpening(a, OpeningId("nope")))))
        assertEquals(missing(EntityKind.ROOM, "x"), errorOf(run(ProjectEdit.RemoveOpening(RoomId("x"), existing.id))))
    }

    @Test
    fun `given an unrepresentable converted offset when the opening is removed then it is rejected`() {
        val far = guest.placement.dock!!.copy(hostOffset = Length(Long.MAX_VALUE))
        val overflowing = projectOf(host, guest.copy(placement = guest.placement.copy(dock = far)))

        assertEquals(
            DomainError.InvalidValue(ValueField.OPENING_OFFSET, InvalidValueReason.NOT_REPRESENTABLE),
            errorOf(run(ProjectEdit.RemoveOpening(a, existing.id), overflowing)),
        )
    }
}
