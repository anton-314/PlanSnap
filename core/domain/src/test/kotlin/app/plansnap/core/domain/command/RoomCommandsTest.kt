package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Table tests of the room and project commands (DOMAIN sections 10 and 11). */
class RoomCommandsTest {
    private val factory = DefaultProjectCommandFactory(FakeOpeningRules())
    private val a = RoomId("a")
    private val b = RoomId("b")
    private val c = RoomId("c")
    private val opening = door("d1", WallId("a-w0"))
    private val roomA = rectangle("a").copy(openings = persistentListOf(opening))
    private val project = projectOf(roomA, rectangle("b"), rectangle("c"))

    private fun link(
        host: String,
        hostWall: String = "$host-w0",
        own: String,
        openingId: OpeningId? = null,
    ) = DockLink(RoomId(host), WallId(hostWall), WallId("$own-w1"), Length(500), openingId)

    private fun run(
        edit: ProjectEdit,
        on: Project = project,
    ) = factory.create(edit).apply(on)

    private fun docked(
        room: Room,
        link: DockLink,
    ) = room.copy(placement = RoomPlacement(10.0, 20.0, 0.5, link))

    @Test
    fun `given a new room when added then it is appended and a duplicate id is rejected`() {
        val extra = rectangle("d")

        assertEquals(listOf("a", "b", "c", "d"), valueOf(run(ProjectEdit.AddRoom(extra))).rooms.map { it.id.value })
        assertEquals(violation(DomainRule.DUPLICATE_ID), errorOf(run(ProjectEdit.AddRoom(rectangle("b")))))
    }

    @Test
    fun `given a new room with a dock link when added then the link is checked`() {
        val good = docked(rectangle("d"), link("a", own = "d"))
        val missingHost = docked(rectangle("d"), link("zz", own = "d"))

        assertEquals(good, valueOf(run(ProjectEdit.AddRoom(good))).room("d"))
        assertEquals(missing(EntityKind.ROOM, "zz"), errorOf(run(ProjectEdit.AddRoom(missingHost))))
    }

    @Test
    fun `given a host room when removed then docked rooms keep their world position and lose the link`() {
        val withGuest = projectOf(roomA, docked(rectangle("b"), link("a", own = "b")), rectangle("c"))

        val result = valueOf(run(ProjectEdit.RemoveRoom(a), withGuest))

        assertEquals(listOf("b", "c"), result.rooms.map { it.id.value })
        assertEquals(RoomPlacement(10.0, 20.0, 0.5, null), result.room("b").placement)
        assertEquals(missing(EntityKind.ROOM, "x"), errorOf(run(ProjectEdit.RemoveRoom(RoomId("x")))))
    }

    @Test
    fun `given a docked room when moved then it is placed freely without a link and keeps its dimensions`() {
        val withGuest = projectOf(roomA, docked(rectangle("b"), link("a", own = "b")))

        val moved = valueOf(run(ProjectEdit.MoveRoom(b, 5.0, 6.0, 1.5), withGuest)).room("b")

        assertEquals(RoomPlacement(5.0, 6.0, 1.5, null), moved.placement)
        assertEquals(rectangle("b").walls, moved.walls)
        assertEquals(missing(EntityKind.ROOM, "x"), errorOf(run(ProjectEdit.MoveRoom(RoomId("x"), 0.0, 0.0, 0.0))))
    }

    @Test
    fun `given valid targets when a room is docked then only the link changes`() {
        val toWall = link("a", own = "b")
        val toOpening = link("a", own = "b", openingId = opening.id)

        val plain = valueOf(run(ProjectEdit.DockRoom(b, toWall))).room("b")
        val atOpening = valueOf(run(ProjectEdit.DockRoom(b, toOpening))).room("b")

        assertEquals(toWall, plain.placement.dock)
        assertEquals(toOpening, atOpening.placement.dock)
        assertEquals(rectangle("b").walls, plain.walls)
    }

    @Test
    fun `given invalid dock requests when applied then each rule rejects them`() {
        val chain = projectOf(roomA, docked(rectangle("b"), link("a", own = "b")), rectangle("c"))
        val cases =
            listOf(
                Triple("self", ProjectEdit.DockRoom(b, link("b", own = "b")), violation(DomainRule.DOCK_TO_SELF)),
                Triple("room", ProjectEdit.DockRoom(RoomId("x"), link("a", own = "b")), missing(EntityKind.ROOM, "x")),
                Triple("host", ProjectEdit.DockRoom(b, link("zz", own = "b")), missing(EntityKind.ROOM, "zz")),
                Triple(
                    "host wall",
                    ProjectEdit.DockRoom(b, link("a", hostWall = "zz", own = "b")),
                    missing(EntityKind.WALL, "zz"),
                ),
                Triple("own wall", ProjectEdit.DockRoom(b, link("a", own = "zz")), missing(EntityKind.WALL, "zz-w1")),
                Triple(
                    "opening",
                    ProjectEdit.DockRoom(b, link("a", own = "b", openingId = OpeningId("zz"))),
                    missing(EntityKind.OPENING, "zz"),
                ),
                Triple(
                    "opening on another wall",
                    ProjectEdit.DockRoom(b, link("a", hostWall = "a-w1", own = "b", openingId = opening.id)),
                    violation(DomainRule.DOCK_OPENING_NOT_ON_WALL),
                ),
            )
        cases.forEach { (name, edit, expected) -> assertEquals(expected, errorOf(run(edit)), name) }
        assertEquals(
            violation(DomainRule.DOCK_CYCLE),
            errorOf(run(ProjectEdit.DockRoom(a, link("b", own = "a")), chain)),
        )
        val longer =
            projectOf(roomA, docked(rectangle("b"), link("a", own = "b")), docked(rectangle("c"), link("b", own = "c")))
        assertEquals(
            violation(DomainRule.DOCK_CYCLE),
            errorOf(run(ProjectEdit.DockRoom(a, link("c", own = "a")), longer)),
        )
    }

    @Test
    fun `given existing links when another room docks then a chain without cycle is accepted`() {
        val chain = projectOf(roomA, docked(rectangle("b"), link("a", own = "b")), rectangle("c"))

        val result = valueOf(run(ProjectEdit.DockRoom(c, link("b", own = "c")), chain))

        assertEquals(
            RoomId("b"),
            result
                .room("c")
                .placement.dock
                ?.hostRoomId,
        )
    }

    @Test
    fun `given details when updated then all five fields change and the height is checked`() {
        val details = RoomDetails("Kitchen", RoomTypeKey("kitchen"), "note", Length(2400), AreaFactor(50))

        val room = valueOf(run(ProjectEdit.UpdateRoomDetails(a, details))).room("a")
        val cleared = valueOf(run(ProjectEdit.UpdateRoomDetails(a, details.copy(heightOverride = null)))).room("a")

        assertEquals(
            roomA.copy(
                name = "Kitchen",
                typeKey = RoomTypeKey("kitchen"),
                notes = "note",
                heightOverride = Length(2400),
                areaFactor = AreaFactor(50),
            ),
            room,
        )
        assertEquals(null, cleared.heightOverride)
        assertEquals(
            DomainError.InvalidValue(ValueField.ROOM_HEIGHT, InvalidValueReason.TOO_SMALL),
            errorOf(run(ProjectEdit.UpdateRoomDetails(a, details.copy(heightOverride = Length(0))))),
        )
        assertEquals(missing(EntityKind.ROOM, "x"), errorOf(run(ProjectEdit.UpdateRoomDetails(RoomId("x"), details))))
    }

    @Test
    fun `given a default height when set then it must be positive and updatedAt stays untouched`() {
        val result = valueOf(run(ProjectEdit.SetProjectHeight(Length(2700))))

        assertEquals(project.copy(defaultHeight = Length(2700)), result)
        assertEquals(project.updatedAtEpochMs, result.updatedAtEpochMs)
        assertEquals(
            DomainError.InvalidValue(ValueField.ROOM_HEIGHT, InvalidValueReason.TOO_SMALL),
            errorOf(run(ProjectEdit.SetProjectHeight(Length(-5)))),
        )
    }
}
